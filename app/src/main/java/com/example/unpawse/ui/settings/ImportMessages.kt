package com.example.unpawse.ui.settings

import com.example.unpawse.data.export.ImportResult
import com.example.unpawse.ui.format.countLabel

/**
 * What to tell the user after an import. Each refusal says something different about *why*, and the
 * two that changed nothing say so — otherwise a failed import is indistinguishable from one that
 * quietly wiped the device. Pure, so the branches are unit-tested.
 */
internal fun importMessage(result: ImportResult): String = when (result) {
    is ImportResult.Restored -> when {
        result.captures == 0 && result.skippedCaptures == 0 -> "Data restored"
        // An older export carried no photos, so saying "restored" alone would overstate it.
        result.skippedCaptures > 0 ->
            "Data restored — ${countLabel(result.skippedCaptures, "photo")} " +
                "couldn't be recovered"
        else -> "Data restored with ${countLabel(result.captures, "photo")}"
    }
    ImportResult.Unreadable -> "That isn't an unPawse export — nothing was changed"
    is ImportResult.TooNew ->
        "That export is from a newer version of unPawse — nothing was changed"
    ImportResult.Failed -> "Couldn't finish the import"
}
