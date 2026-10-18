@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.core.UserIntent

@Composable
private fun TraitSlider(left: String, right: String, value: Float?, onChange: (Float?) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = value != null, onCheckedChange = { onChange(if (it) 0f else null) })
            Text(left + "  ↔  " + right, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
        }
        if (value != null) {
            Slider(value = value, onValueChange = { onChange(it) }, valueRange = -1f..1f)
        }
    }
}

/** Sliders are the user's PREFERENCES, not facts about titles; titles are ranked by an estimate from genres. */
@Composable
fun SlidersScreen(container: AppContainer, onBack: () -> Unit, onDoors: () -> Unit) {
    val saved = container.prefs.intent.value
    var tension by remember { mutableStateOf(saved.tension?.toFloat()) }
    var fantasy by remember { mutableStateOf(saved.fantasy?.toFloat()) }
    var depth by remember { mutableStateOf(saved.depth?.toFloat()) }
    var selection by remember { mutableStateOf(saved.selection) }

    fun save() = container.prefs.setIntent(UserIntent(tension?.toDouble(), fantasy?.toDouble(), depth?.toDouble(), selection))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        BackHeader(tr("🎚️ اضبط تفضيلاتي", "🎚️ Tune my preferences"), onBack)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                tr(
                    "فعّل المحور الذي يهمك واسحب المنزلق. هذه تفضيلاتك أنت، لا حقائق عن الأعمال؛ يرتّب التطبيق الأعمال بتقدير يحسبه من أنواعها (موثَّق في «حول»).",
                    "Turn on an axis and drag. These are YOUR preferences, not facts about titles; the app ranks titles with an estimate computed from their genres.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TraitSlider(tr("هدوء", "Calm"), tr("توتر", "Tension"), tension) { tension = it }
            TraitSlider(tr("واقعية", "Realism"), tr("خيال", "Fantasy"), fantasy) { fantasy = it }
            TraitSlider(tr("خفيف وبسيط", "Light & simple"), tr("معقد وعميق", "Complex & deep"), depth) { depth = it }

            Text(tr("شروط إلزامية: لا أريد...", "Mandatory: I don't want..."), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.avoid.forEach { a ->
                    FilterChip(selected = a.isOn(selection), onClick = { selection = a.toggle(selection) }, label = { Text(tr(a.ar, a.en)) })
                }
            }
            Text(
                tr("الأعمال التي تخالف هذه الشروط لا تظهر في الأبواب ولا الصندوق ولا القرعة.", "Titles violating these never appear in the doors, the box or the roulette."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save(); onDoors() }) { Text(tr("حفظ وافتح ثلاثة أبواب", "Save & open three doors")) }
                OutlinedButton(onClick = { save() }) { Text(tr("حفظ", "Save")) }
            }
        }
    }
}
