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

    /** Whether the JPEG a capture row points at is still on disk. */
    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).isFile }

    /**
     * Total bytes the stored JPEGs occupy, for the Photo storage screen. Reads the directory rather
     * than summing anything recorded in the database, so it reflects what is really on disk even if
     * a file and its row ever disagree.
     */
    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Removes every stored JPEG, including any left without a database row.
     *
     * Orphans are possible: a crash between writing the file and inserting its row leaves one, and
     * a destructive Room migration drops the rows for all of them. Deleting only what the database
     * knows about would leave "Delete all photos" reporting freed space that is still occupied.
     */
    suspend fun deleteAll() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { runCatching { it.delete() } } }
    }

    private companion object {
        const val CAPTURES_DIR = "captures"
    }
}
