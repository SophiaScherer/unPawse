package com.example.unpawse.data.export

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The manifest, written as the first entry; [BundleReader] looks it up by name. */
const val MANIFEST_ENTRY = "export.json"

const val PHOTOS_DIR = "photos/"

const val BUNDLE_MIME_TYPE = "application/zip"

/** Legacy: a plain v5-or-older document, still accepted on import. */
const val LEGACY_MIME_TYPE = "application/json"

private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/**
 * Whether [header] starts a ZIP. Content is sniffed rather than trusting the picked document's mime
 * type, which providers routinely report as `application/octet-stream` either way.
 */
internal fun looksLikeZip(header: ByteArray): Boolean =
    header.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { header[it] == ZIP_MAGIC[it] }

/**
 * The simple file name for a `photos/` entry, or null for anything else or anything unsafe.
 *
 * The archive is user-supplied, so a traversing name (`photos/../../…`) must never reach the
 * filesystem. `PhotoStorage.save` generates its own name and so can't be steered by one anyway, but
 * the reader is the right place to refuse rather than the last line of defence.
 */
internal fun photoFileNameOf(entryName: String): String? {
    if (!entryName.startsWith(PHOTOS_DIR)) return null
    val name = entryName.removePrefix(PHOTOS_DIR)
    if (name.isEmpty() || name == "." || name == "..") return null
    if (name.contains('/') || name.contains('\\')) return null
    return name
}

/** One JPEG to put in the bundle. Opened lazily so the whole library is never in memory at once. */
class PhotoSource(val fileName: String, val open: () -> InputStream)

/**
 * Writes the bundle: the manifest first, then the photos. A source whose [PhotoSource.open] throws is
 * skipped — a JPEG lost from disk shouldn't cost the user the entire export, and the manifest still
 * names it so the importer can report it as skipped.
 */
fun writeBundle(out: OutputStream, manifestJson: String, photos: List<PhotoSource>) {
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(manifestJson.toByteArray())
        zip.closeEntry()

        for (photo in photos) {
            runCatching {
                photo.open().use { input ->
                    zip.putNextEntry(ZipEntry(PHOTOS_DIR + photo.fileName))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }
}

/**
 * Random-access reader over a bundle saved to disk. Opening it reads the central directory, which a
 * ZIP keeps at its very end, so a truncated file is refused here instead of passing as a smaller
 * archive — a streaming reader can't tell a cut on an entry boundary from the real end.
 */
class BundleReader(file: File) : Closeable {

    private val zip = ZipFile(file)

    /** The manifest's text, or null when the archive isn't one of ours. */
    fun manifest(): String? = zip.getEntry(MANIFEST_ENTRY)?.let { entry ->
        ByteArrayOutputStream().also { copyVerified(entry, it) }.toByteArray().decodeToString()
    }

    /**
     * Copies the photo the manifest calls [fileName] to [target]. False when the bundle doesn't
     * carry it (the export skipped a JPEG it couldn't read); throws if it is there but damaged.
     */
    fun copyPhoto(fileName: String, target: File): Boolean {
        val name = photoFileNameOf(PHOTOS_DIR + fileName) ?: return false
        val entry = zip.getEntry(PHOTOS_DIR + name) ?: return false
        target.outputStream().use { copyVerified(entry, it) }
        return true
    }

    // ZipFile doesn't check an entry's CRC on read, and a corrupt photo must fail before the wipe.
    private fun copyVerified(entry: ZipEntry, out: OutputStream) {
        val crc = CRC32()
        val copied = CheckedInputStream(zip.getInputStream(entry), crc).use { it.copyTo(out) }
        if (copied != entry.size || crc.value != entry.crc) {
            throw ZipException("${entry.name} is damaged")
        }
    }

    override fun close() = zip.close()
}
