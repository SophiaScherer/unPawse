package com.example.unpawse.data.export

import android.net.Uri
import com.example.unpawse.data.ResetRepository
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
import java.io.InputStream

/**
 * What an import did. Four cases rather than a boolean for the same reason `RewardOutcome` has four:
 * a refusal the user can't explain reads as the app being broken.
 */
sealed interface ImportResult {

    /** Everything in the document was restored. [skippedCaptures] carried no photo to restore. */
    data class Restored(val captures: Int, val skippedCaptures: Int) : ImportResult

    /** Not an unPawse export, or too damaged to read. Nothing was touched. */
    data object Unreadable : ImportResult

    /** A document from a newer build, whose meaning we'd only be guessing at. Nothing was touched. */
    data class TooNew(val formatVersion: Int) : ImportResult

    /** The document read fine but the restore itself failed part-way. */
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
    /**
     * Writes the imported preferences. Injected as a function for the same reason as
     * [ResetRepository]'s `clearSettings`: `SettingsRepository` needs a `Context`.
     */
    private val applySettings: suspend (ExportSettings) -> Unit,
    /**
     * Opens the picked document. A lambda rather than a `ContentResolver` for the same reason
     * `applySettings` is one: it keeps the whole class constructible in a JVM unit test.
     */
    private val openDocument: (Uri) -> InputStream?,
) {

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val stream = runCatching { openDocument(uri) }.getOrNull()
            ?: return@withContext ImportResult.Unreadable
        stream.use { importFrom(it) }
    }

    /**
     * Reads [input], which may be a v6 bundle or a bare legacy document, and restores it.
     *
     * The manifest is parsed and accepted *before* anything is erased. A wipe followed by a failed
     * parse would destroy the user's data on behalf of a corrupt file, so that ordering is the one
     * thing in here that must not be rearranged.
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
        var manifest: ExportSnapshot? = null
        var rejected: ImportResult? = null
        // fileName as the archive knew it -> the path PhotoStorage just wrote it to.
        val restoredPaths = mutableMapOf<String, String>()

        val read = runCatching {
            readBundle(
                input = input,
                onManifest = { json ->
                    when (val outcome = readManifest(json)) {
                        is ManifestOutcome.Ok -> {
                            manifest = outcome.snapshot
                            reset.eraseEverything()
                        }
                        is ManifestOutcome.Rejected -> rejected = outcome.result
                    }
                },
                onPhoto = { fileName, bytes ->
                    // Only once a manifest has been accepted, so a bundle with no manifest (or an
                    // unreadable one) can't leave orphan JPEGs behind.
                    if (manifest != null) restoredPaths[fileName] = captures.storePhoto(bytes)
                },
            )
        }.isSuccess

        rejected?.let { return it }
        val snapshot = manifest ?: return ImportResult.Unreadable
        if (!read) return ImportResult.Failed

        return runCatching { restore(snapshot, restoredPaths) }.getOrElse { ImportResult.Failed }
    }

    /** A v5-or-older document: everything but the captures, which had no photos to carry. */
    private suspend fun importLegacyJson(input: InputStream): ImportResult {
        val json = runCatching { input.readBytes().decodeToString() }.getOrNull()
            ?: return ImportResult.Unreadable
        val snapshot = when (val outcome = readManifest(json)) {
            is ManifestOutcome.Ok -> outcome.snapshot
            is ManifestOutcome.Rejected -> return outcome.result
        }

        reset.eraseEverything()
        return runCatching { restore(snapshot, restoredPaths = emptyMap()) }
            .getOrElse { ImportResult.Failed }
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

    private suspend fun restore(
        snapshot: ExportSnapshot,
        restoredPaths: Map<String, String>,
    ): ImportResult {
        applySettings(snapshot.settings)

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

        val restorable = snapshot.captures.mapNotNull { capture ->
            restoredPaths[capture.fileName]?.let { path -> capture.toDomain(path) }
        }
        captures.restoreCaptures(restorable)

        return ImportResult.Restored(
            captures = restorable.size,
            skippedCaptures = snapshot.captures.size - restorable.size,
        )
    }

    private companion object {
        /** A ZIP local-file header's magic is four bytes; that's all the sniff needs. */
        const val ZIP_HEADER_BYTES = 4
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

/** [filePath] comes from where the JPEG was just written, never from the document. */
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
