package com.example.unpawse.data.export

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class ExportArchiveTest {

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

    @Test
    fun `photos round-trip through the bundle`() = runBlocking {
        val bytes = bundle(
            photos = listOf(photo("a.jpg", byteArrayOf(1, 2)), photo("b.jpg", byteArrayOf(3))),
        )

        val read = mutableMapOf<String, ByteArray>()
        readBundle(ByteArrayInputStream(bytes), onManifest = {}, onPhoto = { n, b -> read[n] = b })

        assertEquals(setOf("a.jpg", "b.jpg"), read.keys)
        assertEquals(listOf<Byte>(1, 2), read.getValue("a.jpg").toList())
    }

    /**
     * The reader is single-pass over a `content://` stream, so the importer can only decide whether
     * to proceed before any photo arrives if the manifest is written first.
     */
    @Test
    fun `the manifest always arrives before any photo`() = runBlocking {
        val bytes = bundle(photos = listOf(photo("a.jpg", byteArrayOf(1))))

        val order = mutableListOf<String>()
        readBundle(
            input = ByteArrayInputStream(bytes),
            onManifest = { order += "manifest" },
            onPhoto = { name, _ -> order += name },
        )

        assertEquals(listOf("manifest", "a.jpg"), order)
    }

    /** A JPEG lost from disk shouldn't cost the user the rest of the export. */
    @Test
    fun `an unreadable photo is skipped rather than failing the write`() = runBlocking {
        val bytes = bundle(
            photos = listOf(
                PhotoSource("missing.jpg") { throw IOException("gone") },
                photo("ok.jpg", byteArrayOf(7)),
            ),
        )

        val read = mutableListOf<String>()
        readBundle(ByteArrayInputStream(bytes), onManifest = {}, onPhoto = { n, _ -> read += n })

        assertEquals(listOf("ok.jpg"), read)
    }

    @Test
    fun `the manifest survives the round trip verbatim`() = runBlocking {
        val manifest = buildExportJson(
            ExportSnapshot(
                exportedAtMillis = 1, appVersion = "1.0 (1)",
                settings = ExportSettings("Sophia", "DARK", 0.5f, 0.7f, 15, 30, false),
                monitoredApps = emptyList(), schedules = emptyList(),
                usage = emptyList(), unlocks = emptyList(), captures = emptyList(),
            ),
        )

        var read: String? = null
        readBundle(ByteArrayInputStream(bundle(manifest)), onManifest = { read = it }, onPhoto = { _, _ -> })

        assertEquals(manifest, read)
    }
}
