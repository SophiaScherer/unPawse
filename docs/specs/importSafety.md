# Import safety (audit UX-21, CQ-11, §1.10)

## What was built
- A damaged or truncated import bundle can no longer wipe the user's data. Before, a bundle cut off after `export.json` erased every store and restored nothing.
- The whole bundle is staged and verified before the first delete:
  - It is copied into an injected staging dir (`noBackupFilesDir/import-staging`), which is emptied before and after every import.
  - `BundleReader` opens the copy with `ZipFile`.
  - Every referenced photo is extracted and CRC/size-checked.
- The wipe and the restore run in one Room transaction (`Transactor`, `withTransaction` in production). A failed commit leaves the old data exactly as it was.
- The steps that can't be rolled back run only after the commit, in this order:
  1. The old JPEGs are swept.
  2. The staged photos are moved to the paths reserved for them.
  3. The sessions stop.
  4. The preferences are written last, in a single clear-and-set DataStore edit.
- "Delete all data" gets the same transaction. A failure there now shows a message instead of crashing.
- Export and import share one re-entry guard, and the running row shows a spinner. The other data rows are disabled meanwhile.

## Key decisions
- **`ZipFile`, not `ZipInputStream`.** A ZIP's central directory sits at its very end, so any truncation fails on open. A streaming reader takes a cut that lands exactly between two entries for the real end of a smaller archive, and that is precisely the after-the-manifest case.
- **A complete bundle that lacks a photo still imports and skips it.** Export deliberately leaves out a JPEG it can't read. Completeness is proven by the directory, not by the photo list.
- **New `ImportResult.Damaged`** ("incomplete or damaged — nothing was changed"). This keeps damage separate from `Unreadable` ("isn't an unPawse export").
- **`Failed` now honestly says "nothing was changed"**, because it is only produced before the commit. A preferences write that fails after the commit is `Restored(settingsRestored = false)` and gets its own message.
- **Staging is not in `cacheDir`.** That directory is setgid, so a file renamed out of it keeps the `_cache` group, and the platform then counts the user's photos as cache. `noBackupFilesDir` is on the same volume, so placing a photo is still a rename. Its contents also stay out of Auto Backup.
- **No export format bump.** PR #29 (v9, `avatarId`) will need its new key added inside the rewritten single-edit `SettingsRepository.applyImported`.
- **The JVM tests fake the transaction with rollback-capable DAOs.** `FakeTransactor` plus a `checkpoint()` on each shared fake DAO make "a failed commit leaves everything" testable without Room.

## Known gaps
- If the process dies between the commit and the photo moves, rows are left without files. `reconcileMissingFiles()` sweeps them on the next start.
- Any I/O error while staging, including a full disk, is reported as Damaged.
- Import and export cannot be cancelled, and the spinner is indeterminate.

## How to verify
- Gate (from the worktree):
  `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon -Pkotlin.compiler.execution.strategy=in-process`
- Key tests:
  - `ImportRepositoryTest`: a bundle cut right after the manifest, one cut mid-photo, a failed commit, a post-commit settings failure, and exactly-imported files on disk.
  - `ExportArchiveTest`: truncation refused on open, and an altered photo failing its CRC.
  - `ResetRepositoryTest`: a failed commit leaves everything in place.
- On a device:
  1. Settings → Export data → save through the picker.
  2. Pull the file with adb. Write one copy cut at the second local-header offset and one cut mid-photo, then push both back.
  3. Change the display name so the current state differs from the bundle.
  4. Import each truncated copy. Expect the toast "That export is incomplete or damaged — nothing was changed", and the name, photo count and `run-as … ls files/captures` all unchanged.
  5. Import the intact export. It restores, and `files/captures` holds exactly the imported photos under new UUIDs, with `no_backup/import-staging` gone.
  6. To see the busy state, export a library of a few dozen photos. The Export row shows "Exporting your data…" with a spinner, and Import and Delete are dimmed.
