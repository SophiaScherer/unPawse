package com.example.unpawse.data.export

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The manifest, always the bundle's first entry — see [readBundle]. */
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
 * Reads the bundle in one pass, handing each entry straight to a callback so only one JPEG is in
 * memory at a time. [onManifest] is guaranteed to run before any [onPhoto], which is what lets the
 * caller decide whether to proceed before a single byte is written anywhere.
 */
suspend fun readBundle(
    input: InputStream,
    onManifest: suspend (String) -> Unit,
    onPhoto: suspend (fileName: String, bytes: ByteArray) -> Unit,
) {
    val zip = ZipInputStream(input)
    var entry = zip.nextEntry
    while (entry != null) {
        if (!entry.isDirectory) {
            when {
                entry.name == MANIFEST_ENTRY -> onManifest(zip.readBytes().decodeToString())
                else -> photoFileNameOf(entry.name)?.let { onPhoto(it, zip.readBytes()) }
            }
        }
        zip.closeEntry()
        entry = zip.nextEntry
    }
}
