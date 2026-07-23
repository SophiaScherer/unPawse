package com.example.unpawse.data.capture

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Writes/reads capture JPEGs in app-internal storage (`filesDir/captures/`). Private to the app, no
 * runtime permission, wiped on uninstall. Swapping to public MediaStore later touches only this class.
 *
 * Takes the base directory rather than a [Context] so file operations are unit-testable against a
 * temp folder; the app uses the [Context] convenience constructor, which resolves to `filesDir`.
 */
class PhotoStorage(private val baseDir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val dir: File
        get() = File(baseDir, CAPTURES_DIR).apply { mkdirs() }

    /** Persists [bytes] as a new JPEG and returns its absolute path. */
    suspend fun save(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { it.write(bytes) }
        file.absolutePath
    }

    /** Best-effort delete; missing files are ignored. */
    suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { runCatching { File(path).delete() } }
    }

    private companion object {
        const val CAPTURES_DIR = "captures"
    }
}
