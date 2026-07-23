package com.example.unpawse.ui.gallery

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Authority of the manifest-declared [FileProvider]; must match `${applicationId}.fileprovider`. */
private fun authority(context: Context): String = "${context.packageName}.fileprovider"

/**
 * Shares a single capture JPEG out to other apps via the system chooser. The file is app-internal,
 * so we hand out a temporary read-granted [FileProvider] content URI rather than a raw path.
 * No-ops silently if the file is missing (e.g. purged between tap and share).
 */
fun shareCapture(context: Context, imagePath: String) {
    val file = File(imagePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, authority(context), file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share cat photo").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}
