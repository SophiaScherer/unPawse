package com.example.unpawse.data.export

import android.net.Uri
import com.example.unpawse.data.ResetRepository
import com.example.unpawse.data.Transactor
import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.data.usage.appCategoryFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * What an import did. Several cases rather than a boolean for the same reason `RewardOutcome` has four:
 * a refusal the user can't explain reads as the app being broken.
 */
sealed interface ImportResult {

    /**
     * Everything in the document was restored. [skippedCaptures] carried no photo to restore, and
     * [settingsRestored] is false only if the preferences write failed after the data committed.
     */
    data class Restored(
        val captures: Int,
        val skippedCaptures: Int,
        val settingsRestored: Boolean = true,
    ) : ImportResult

    /** Not an unPawse export. Nothing was touched. */
    data object Unreadable : ImportResult

    /** Ours, but cut short or corrupt — a partial download, say. Nothing was touched. */
    data object Damaged : ImportResult

    /** A document from a newer build, whose meaning we'd only be guessing at. Nothing was touched. */
    data class TooNew(val formatVersion: Int) : ImportResult

    /** The document read fine but the restore failed; the transaction rolled it back. */
    data object Failed : ImportResult
}

/**
 * Restores an export bundle, replacing everything already stored.
 *
 * Mirrors [ExportRepository]: reads and writes go through repositories rather than DAOs, and the
 * `ContentResolver` half is split from the rest so the interesting logic is testable without one.
 */
class ImportRepository(
    private val usage: UsageRepository,
    private val unlocks: UnlockRepository,
    private val schedules: ScheduleRepository,
    private val captures: CaptureRepository,
    private val reset: ResetRepository,
    /** The wipe and the restore commit together or not at all. */
    private val transactor: Transactor,
    /**
     * Replaces every preference with the imported ones in a single write. Injected as a function for the same reason as
     * [ResetRepository]'s `clearSettings`: `SettingsRepository` needs a `Context`.
     */
    private val applySettings: suspend (ExportSettings) -> Unit,
    /**
     * Opens the picked document. A lambda rather than a `ContentResolver` for the same reason
     * `applySettings` is one: it keeps the whole class constructible in a JVM unit test.
     */
    private val openDocument: (Uri) -> InputStream?,
    /** Holds a bundle and its photos until all of it has been read; emptied after each import. */
    private val stagingDir: File,
) {

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val stream = runCatching { openDocument(uri) }.getOrNull()
            ?: return@withContext ImportResult.Unreadable
        stream.use { importFrom(it) }
    }

    /**
     * Reads [input], which may be a v6 bundle or a bare legacy document, and restores it.
     *
     * Everything is read and checked *before* anything is erased — the manifest, the format version
     * and every photo. A wipe followed by a failed read would destroy the user's data on behalf of a
     * corrupt file, so that ordering is the one thing in here that must not be rearranged.
     */
    suspend fun importFrom(input: InputStream): ImportResult {
        val buffered = BufferedInputStream(input)
        val header = ByteArray(ZIP_HEADER_BYTES)
        buffered.mark(ZIP_HEADER_BYTES)
        val read = runCatching { buffered.read(header) }.getOrDefault(-1)
        if (read <= 0) return ImportResult.Unreadable
        buffered.reset()

        return if (looksLikeZip(header.copyOf(read))) {
            importBundle(buffered)
        } else {
            importLegacyJson(buffered)
        }
    }

    private suspend fun importBundle(input: InputStream): ImportResult {
        // Cleared before as well as after, so an import killed mid-way can't strand its photos here.
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        try {
            val staged = runCatching { stage(input) }
                .getOrElse { Staging.Refused(ImportResult.Failed) }
            return when (staged) {
                is Staging.Refused -> staged.result
                is Staging.Ready -> replaceWith(staged.snapshot, staged.photos)
            }
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private sealed interface Staging {
        /** [photos] maps each name the manifest uses to its fully read, CRC-checked copy. */
        class Ready(val snapshot: ExportSnapshot, val photos: Map<String, File>) : Staging
        class Refused(val result: ImportResult) : Staging
    }

    private fun stage(input: InputStream): Staging {
        val archive = File(stagingDir, STAGED_BUNDLE)
        return try {
            archive.outputStream().use { input.copyTo(it) }
            BundleReader(archive).use { reader ->
                val json = reader.manifest() ?: return Staging.Refused(ImportResult.Unreadable)
                val snapshot = when (val outcome = readManifest(json)) {
                    is ManifestOutcome.Ok -> outcome.snapshot
                    is ManifestOutcome.Rejected -> return Staging.Refused(outcome.result)
                }
                val names = snapshot.captures.mapNotNull { it.fileName }.distinct()
                val photos = names.withIndex().mapNotNull { (index, name) ->
                    val staged = File(stagingDir, "photo-$index")
                    if (reader.copyPhoto(name, staged)) name to staged else null
                }.toMap()
                Staging.Ready(snapshot, photos)
            }
        } catch (_: IOException) {
            // ZipFile refuses a file missing its central directory, and a photo that fails its CRC
            // throws too: either way the bundle is incomplete, and nothing has been touched yet.
            Staging.Refused(ImportResult.Damaged)
        } finally {
            archive.delete()
        }
    }

    /**
     * Swaps the stored data for [snapshot]. Every photo in [photos] has already been read, so
     * nothing past this point depends on the file the user picked.
     *
     * The wipe and every row write share one transaction, so a failure there leaves the old data
     * exactly as it was. Only after the commit come the steps that can't be rolled back — the old
     * JPEGs, the staged moves, the sessions — and the preferences last of all.
     */
    private suspend fun replaceWith(
        snapshot: ExportSnapshot,
        photos: Map<String, File>,
    ): ImportResult {
        val paths = photos.mapValues { captures.reservePhotoPath() }
        val restorable = snapshot.captures.mapNotNull { capture ->
            paths[capture.fileName]?.let { path -> capture.toDomain(path) }
        }

        val committed = runCatching {
            transactor.inTransaction {
                reset.eraseRows()
                restoreRows(snapshot, restorable)
            }
        }
        if (committed.isFailure) return ImportResult.Failed

        reset.afterRowsErased()
        val unplaced = captures.placePhotos(
            paths.entries.associate { (name, path) -> path to photos.getValue(name) },
        )
        // A row whose photo couldn't be moved in would be a permanently broken tile.
        val (placed, broken) = restorable.partition { it.filePath !in unplaced }
        broken.forEach { captures.deleteCapture(it) }
        val settingsRestored = runCatching { applySettings(snapshot.settings) }.isSuccess

        return ImportResult.Restored(
            captures = placed.size,
            skippedCaptures = snapshot.captures.size - placed.size,
            settingsRestored = settingsRestored,
        )
    }

    /** A v5-or-older document: everything but the captures, which had no photos to carry. */
    private suspend fun importLegacyJson(input: InputStream): ImportResult {
        val json = runCatching { input.readBytes().decodeToString() }.getOrNull()
            ?: return ImportResult.Unreadable
        val snapshot = when (val outcome = readManifest(json)) {
            is ManifestOutcome.Ok -> outcome.snapshot
            is ManifestOutcome.Rejected -> return outcome.result
        }

        return replaceWith(snapshot, photos = emptyMap())
    }

    private sealed interface ManifestOutcome {
        data class Ok(val snapshot: ExportSnapshot) : ManifestOutcome
        data class Rejected(val result: ImportResult) : ManifestOutcome
    }

    private fun readManifest(json: String): ManifestOutcome {
        parseExportJson(json)?.let { return ManifestOutcome.Ok(it) }
        val declared = exportFormatVersionOf(json)
        return ManifestOutcome.Rejected(
            if (declared != null && declared > EXPORT_FORMAT_VERSION) {
                ImportResult.TooNew(declared)
            } else {
                ImportResult.Unreadable
            },
        )
    }

    private suspend fun restoreRows(snapshot: ExportSnapshot, restorable: List<Capture>) {
        snapshot.monitoredApps.forEach { app ->
            // setLimit seeds the category only when there's no row, which after the wipe is always.
            usage.setLimit(
                packageName = app.packageName,
                appLabel = app.appLabel,
                dailyLimitMinutes = app.dailyLimitMinutes,
                enabled = app.enabled,
                defaultCategory = appCategoryFrom(app.category),
            )
            usage.setWeekendLimit(app.packageName, app.weekendLimitMinutes)
        }

        usage.restoreUsage(snapshot.usage.map { it.toDomain() })
        unlocks.restoreUnlocks(snapshot.unlocks.map { DailyUnlocks(it.date, it.unlockCount) })
        // Upsert with the exported id preserves it, and clearing the table doesn't reset the
        // autoincrement high-water mark, so nothing can collide.
        snapshot.schedules.forEach { schedules.save(it.toDomain()) }
        captures.restoreCaptures(restorable)
    }

    private companion object {
        /** A ZIP local-file header's magic is four bytes; that's all the sniff needs. */
        const val ZIP_HEADER_BYTES = 4

        const val STAGED_BUNDLE = "bundle.zip"
    }
}

private fun ExportUsageDay.toDomain() = DailyUsage(
    packageName = packageName,
    date = date,
    usedSeconds = usedSeconds,
    earnedSeconds = earnedSeconds,
    blockedCount = blockedCount,
)

private fun ExportScheduleWindow.toDomain() = ScheduleWindow(
    id = id,
    label = label,
    packageName = packageName,
    startMinuteOfDay = startMinuteOfDay,
    endMinuteOfDay = endMinuteOfDay,
    daysMask = daysMask,
    enabled = enabled,
)

/** [filePath] is the path reserved for the photo, never anything from the document. */
private fun ExportCapture.toDomain(filePath: String) = Capture(
    id = id,
    filePath = filePath,
    capturedAt = capturedAtMillis,
    confidence = confidence,
    isBonus = isBonus,
    isFavorite = isFavorite,
    earnedMinutes = earnedMinutes,
    widthPx = widthPx,
    heightPx = heightPx,
)
