package com.example.unpawse.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class ExportArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun bundle(
        manifest: String = """{"formatVersion":6}""",
        photos: List<PhotoSource> = emptyList(),
    ): ByteArray = ByteArrayOutputStream().also { writeBundle(it, manifest, photos) }.toByteArray()

    private fun photo(name: String, bytes: ByteArray) = PhotoSource(name) { ByteArrayInputStream(bytes) }

    @Test
    fun `a bundle is recognised by its magic bytes`() {
        assertTrue(looksLikeZip(bundle()))
        assertFalse(looksLikeZip("""{"formatVersion":5}""".toByteArray()))
        assertFalse(looksLikeZip(byteArrayOf(0x50)))
        assertFalse(looksLikeZip(byteArrayOf()))
    }

    @Test
    fun `only bare names under photos are accepted`() {
        assertEquals("a.jpg", photoFileNameOf("photos/a.jpg"))
        assertNull(photoFileNameOf("export.json"))
        assertNull(photoFileNameOf("photos/"))
        assertNull(photoFileNameOf("elsewhere/a.jpg"))
    }

    /** The archive is user-supplied, so a traversing name must never reach the filesystem. */
    @Test
    fun `traversing entry names are refused`() {
        assertNull(photoFileNameOf("photos/../../evil.jpg"))
        assertNull(photoFileNameOf("photos/nested/evil.jpg"))
        assertNull(photoFileNameOf("photos/..\\evil.jpg"))
        assertNull(photoFileNameOf("../photos/evil.jpg"))
    }

    private fun reader(bytes: ByteArray) = BundleReader(tmp.newFile().apply { writeBytes(bytes) })

    private fun BundleReader.photo(name: String): ByteArray? =
        tmp.newFile().takeIf { copyPhoto(name, it) }?.readBytes()

    @Test
    fun `photos round-trip through the bundle`() {
        val bytes = bundle(
            photos = listOf(photo("a.jpg", byteArrayOf(1, 2)), photo("b.jpg", byteArrayOf(3))),
        )

        reader(bytes).use { bundle ->
            assertEquals(listOf<Byte>(1, 2), bundle.photo("a.jpg")?.toList())
            assertEquals(listOf<Byte>(3), bundle.photo("b.jpg")?.toList())
            assertNull("a photo the bundle doesn't carry", bundle.photo("c.jpg"))
        }
    }

    /** A JPEG lost from disk shouldn't cost the user the rest of the export. */
    @Test
    fun `an unreadable photo is skipped rather than failing the write`() {
        val bytes = bundle(
            photos = listOf(
                PhotoSource("missing.jpg") { throw IOException("gone") },
                photo("ok.jpg", byteArrayOf(7)),
            ),
        )

        reader(bytes).use { bundle ->
            assertNull(bundle.photo("missing.jpg"))
            assertEquals(listOf<Byte>(7), bundle.photo("ok.jpg")?.toList())
        }
    }

    /** The manifest names files, so it mustn't be able to steer a read outside `photos/`. */
    @Test
    fun `a traversing name in the manifest reads nothing`() {
        reader(bundle(photos = listOf(photo("a.jpg", byteArrayOf(1))))).use { bundle ->
            assertNull(bundle.photo("../export.json"))
        }
    }

    @Test
    fun `the manifest survives the round trip verbatim`() {
        val manifest = buildExportJson(
            ExportSnapshot(
                exportedAtMillis = 1, appVersion = "1.0 (1)",
                settings = ExportSettings("Sophia", "DARK", 0.5f, 0.7f, 15, 30, false),
                monitoredApps = emptyList(), schedules = emptyList(),
                usage = emptyList(), unlocks = emptyList(), captures = emptyList(),
            ),
        )

        assertEquals(manifest, reader(bundle(manifest)).use { it.manifest() })
    }

    @Test
    fun `a zip with no manifest has none to give`() {
        val foreign = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("notes.txt"))
                zip.write("hello".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        assertNull(reader(foreign).use { it.manifest() })
    }

    /**
     * The central directory sits at the end, so any cut loses it — including one that lands
     * cleanly between two entries, which a streaming reader would take for the end of the archive.
     */
    @Test
    fun `a truncated bundle is refused when opened`() {
        val whole = bundle(photos = listOf(photo("a.jpg", Random(1).nextBytes(4_000))))

        for (cut in listOf(whole.size - 1, whole.size / 2, 40)) {
            assertThrows(IOException::class.java) { reader(whole.copyOf(cut)) }
        }
    }

    @Test
    fun `a photo whose bytes were altered fails its check`() {
        val original = Random(2).nextBytes(4_000)
        val whole = bundle(photos = listOf(photo("a.jpg", original)))
        // Somewhere inside the photo's compressed data, past its local header.
        val damaged = whole.copyOf().also { bytes ->
            val at = String(bytes, Charsets.ISO_8859_1).indexOf("photos/a.jpg") + 2_000
            bytes[at] = (bytes[at].toInt() xor 0xFF).toByte()
        }

        reader(damaged).use { bundle ->
            assertThrows(IOException::class.java) { bundle.photo("a.jpg") }
        }
    }
}
