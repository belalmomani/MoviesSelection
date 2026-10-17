@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvseries.Movies.core.Selection

@Composable
fun ChoiceRow(title: String, choices: List<Presets.Choice>, selected: Presets.Choice?, onPick: (Presets.Choice?) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            FilterChip(
                selected = selected?.id == c.id,
                onClick = { onPick(if (selected?.id == c.id) null else c) },
                label = { Text(tr(c.ar, c.en)) },
            )
        }
    }
}

/** Mood + story traits + things to avoid -> a Selection handed to the guided filter. */
@Composable
fun MoodScreen(onBack: () -> Unit, onApply: (Selection) -> Unit) {
    var mood by remember { mutableStateOf<Presets.Choice?>(null) }
    var tension by remember { mutableStateOf<Presets.Choice?>(null) }
    var ending by remember { mutableStateOf<Presets.Choice?>(null) }
    var duration by remember { mutableStateOf<Presets.Choice?>(null) }
    var avoidOn by remember { mutableStateOf<List<AvoidOption>>(emptyList()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع", "Back")) }
        Text(tr("اختر حسب مزاجي", "Pick by my mood"), style = MaterialTheme.typography.headlineSmall)

        ChoiceRow(tr("أريد شيئًا...", "I want something that..."), Presets.moods, mood) { mood = it }
        ChoiceRow(tr("مستوى التوتر", "Tension level"), Presets.tensions, tension) { tension = it }
        ChoiceRow(tr("النهاية المرغوبة", "Preferred ending"), Presets.endings, ending) { ending = it }
        ChoiceRow(tr("مدة المشاهدة (للأفلام)", "Watch time (films)"), Presets.durations, duration) { duration = it }

        Text(tr("لا أريد...", "I don't want..."), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.avoid.forEach { a ->
                val on = avoidOn.any { it.id == a.id }
                FilterChip(
                    selected = on,
                    onClick = { avoidOn = if (on) avoidOn.filterNot { it.id == a.id } else avoidOn + a },
                    label = { Text(tr(a.ar, a.en)) },
                )
            }
        }
        Text(
            tr(
                "تنبيه: المزاج والتوتر تقديرات يحسبها التطبيق من أنواع TMDB. النهاية والاستبعادات (خيانة، دماء، مدرسة...) تُستنتج من الكلمات المفتاحية الإنجليزية في TMDB فقط، وقد تفوتها أعمال؛ لذلك يجلب التطبيق تفاصيل الأعمال تلقائيًا ويُظهر غير المؤكد تحت «قد تطابق».",
                "Note: mood and tension are app estimates from TMDB genres. Endings and exclusions (betrayal, gore, school...) are inferred only from English TMDB keywords and can miss titles; the app therefore fetches details automatically and shows unconfirmed ones under \"Might match\".",
            ),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onApply(Presets.build(mood, tension, ending, duration, avoidOn)) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("أرني الأعمال", "Show me titles"))
        }
    }
}
