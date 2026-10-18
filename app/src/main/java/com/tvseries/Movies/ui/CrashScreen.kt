package com.tvseries.Movies.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Shown on the next launch after a crash, so the error can be copied/sent without a computer. */
@Composable
fun CrashScreen(trace: String, onContinue: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("حدث خطأ في التشغيل السابق", "The app crashed last time"), style = MaterialTheme.typography.titleLarge)
            Text(tr("انسخ النص أو شاركه وأرسله للمطوّر.", "Copy or share this text and send it to the developer."), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(trace)) }) { Text(tr("نسخ", "Copy")) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, trace)
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }) { Text(tr("مشاركة", "Share")) }
                OutlinedButton(onClick = onContinue) { Text(tr("متابعة", "Continue")) }
            }
            SelectionContainer { Text(trace.take(4000), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
