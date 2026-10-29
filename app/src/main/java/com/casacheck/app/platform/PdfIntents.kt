package com.casacheck.app.platform

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal fun openPdf(context: android.content.Context, path: String, share: Boolean) {
    check(path.isNotBlank()) { "Acta sin archivo PDF" }
    val file = File(path)
    check(file.exists()) { "No se encontró el PDF" }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = if (share) Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri) }
    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
    intent.clipData = android.content.ClipData.newRawUri("Acta HomeCheck", uri)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, if (share) "Compartir acta" else "Abrir acta"))
}
