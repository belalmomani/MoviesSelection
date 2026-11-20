package com.tvseries.Movies.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp

/** True on a real TV or leanback device (the same test MainActivity uses to pick the TV edition automatically). */
fun isTelevisionDevice(context: Context): Boolean {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/**
 * Two small translucent "up / down" buttons for a TV. They take no remote focus (so they never get in the way of
 * normal D-pad navigation) and are used by touch / air-mouse; the remote's page and channel keys do the same job.
 */
@Composable
fun TvPageButtons(onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageButton("\u25B2", onUp)
        PageButton("\u25BC", onDown)
    }
}

@Composable
private fun PageButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
    }
}
