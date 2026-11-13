package com.tvseries.Movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.tvseries.Movies.BuildConfig

/**
 * First-run screen: tells the user plainly what the ads do and lets them choose. Both answers are equally easy
 * (no dark patterns), and the choice can be changed later in Settings. Nothing ad-related starts before this.
 */
@Composable
fun ConsentScreen(onChoice: (personalized: Boolean) -> Unit) {
    val uri = LocalUriHandler.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("🎬 Movies Selection", style = MaterialTheme.typography.headlineSmall)
            Text(tr("قبل أن تبدأ: الإعلانات وخصوصيتك", "Before you start: ads and your privacy"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr(
                    "التطبيق مجاني ويعرض بانرًا إعلانيًا في الرئيسية وصفحة العمل فقط (لا في التصفية ولا في شاشات الاختيار).",
                    "The app is free and shows a banner ad on Home and on a title's page only (never while filtering or choosing).",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                tr(
                    "الإعلانات تقدّمها مكتبة طرف ثالث (Start.io). قد تستعمل معرّف الإعلانات في جهازك ومعلومات عن الجهاز والشبكة. اختيارك يحدد هل تكون الإعلانات مخصصة لاهتماماتك أم عامة.",
                    "Ads come from a third-party SDK (Start.io). It may use your device's advertising ID and device/network information. Your choice decides whether ads are personalised or generic.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                tr(
                    "ما تفضّله وتشاهده وقوائمك وذوقك تبقى على جهازك فقط ولا تُرسَل لأي خادم نملكه. التطبيق موجّه لمن عمره 18 سنة فأكثر.",
                    "Your favourites, history, lists and taste stay on your device and are never sent to any server of ours. The app is intended for people aged 18 and over.",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { onChoice(true) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("موافق على الإعلانات المخصصة", "I agree to personalised ads"))
            }
            OutlinedButton(onClick = { onChoice(false) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("إعلانات عامة فقط (غير مخصصة)", "Generic ads only (not personalised)"))
            }
            Text(
                tr("يمكنك تغيير اختيارك في أي وقت من «الإعدادات».", "You can change this at any time in Settings."),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (BuildConfig.PRIVACY_URL.isNotBlank()) {
                TextButton(onClick = { uri.openUri(BuildConfig.PRIVACY_URL) }) { Text(tr("اقرأ سياسة الخصوصية", "Read the privacy policy")) }
            }
        }
    }
}
