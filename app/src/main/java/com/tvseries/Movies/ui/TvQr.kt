package com.tvseries.Movies.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** A TV cannot tap an ad: it shows a QR code that opens the sponsor page on the viewer's phone. Back / OK closes it. */
@Composable
fun TvQrDialog(url: String, onDismiss: () -> Unit) {
    val image = remember(url) { qrImage(url, 512) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("إغلاق", "Close")) } },
        title = { Text(tr("امسح الرمز بهاتفك", "Scan with your phone")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (image != null) Image(image, contentDescription = null, modifier = Modifier.size(220.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("يفتح صفحة الراعي على هاتفك.", "Opens the sponsor page on your phone."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

/** Black modules on a white square (the quiet zone is added by the encoder's margin). Null if encoding fails. */
private fun qrImage(text: String, size: Int): ImageBitmap? = try {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) AndroidColor.BLACK else AndroidColor.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
} catch (e: Exception) {
    null
}
