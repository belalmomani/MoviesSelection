@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.tvseries.Movies.reminder.ReminderScheduler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tvseries.Movies.AppContainer
import com.tvseries.Movies.BuildConfig

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
fun SettingsScreen(container: AppContainer, onAbout: () -> Unit) {
    val p = container.prefs
    val lang by p.language.collectAsState()
    val theme by p.theme.collectAsState()
    val adult by p.showAdult.collectAsState()
    val minVotes by p.minVotes.collectAsState()
    val historyOn by p.historyEnabled.collectAsState()
    val key by p.apiKey.collectAsState()
    val hideWatched by p.hideWatched.collectAsState()
    val reminder by p.weeklyReminder.collectAsState()
    val ctx = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            p.setWeeklyReminder(true)
            ReminderScheduler.schedule(ctx)
        }
    }
    var keyInput by remember { mutableStateOf(key) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("الإعدادات", "Settings"), style = MaterialTheme.typography.titleLarge)

        Text(tr("اللغة", "Language"), style = MaterialTheme.typography.titleSmall)
        RadioRow("العربية", lang == "ar") { p.setLanguage("ar") }
        RadioRow("English", lang == "en") { p.setLanguage("en") }
        HorizontalDivider()

        Text(tr("المظهر", "Theme"), style = MaterialTheme.typography.titleSmall)
        RadioRow(tr("حسب النظام", "System"), theme == "system") { p.setTheme("system") }
        RadioRow(tr("فاتح", "Light"), theme == "light") { p.setTheme("light") }
        RadioRow(tr("داكن", "Dark"), theme == "dark") { p.setTheme("dark") }
        HorizontalDivider()

        Text(tr("المحتوى والتصفية", "Content & filtering"), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = adult, onCheckedChange = { p.setShowAdult(it) })
            Text(tr("إظهار المحتوى المخصص للبالغين (مغلق افتراضيًا)", "Show adult content (off by default)"), Modifier.padding(start = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = hideWatched, onCheckedChange = { p.setHideWatched(it) })
            Text(tr("إخفاء الأعمال التي شاهدتها من نتائج التصفية", "Hide titles I have watched from filter results"), Modifier.padding(start = 8.dp))
        }
        Text(tr("الحد الأدنى لعدد المصوّتين عند تحميل الأعمال:", "Minimum vote count when loading titles:"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 50, 100, 500).forEach { v ->
                if (v == minVotes) Button(onClick = { p.setMinVotes(v) }) { Text("$v") }
                else OutlinedButton(onClick = { p.setMinVotes(v) }) { Text("$v") }
            }
        }
        Text(
            tr("يُطبَّق على التحميل الجديد من TMDB. رفعه يقلل الأعمال المجهولة.", "Applies to new loads from TMDB. Raising it reduces obscure titles."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()

        Text(tr("التذكير الأسبوعي", "Weekly reminder"), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = reminder,
                onCheckedChange = { on ->
                    if (!on) {
                        p.setWeeklyReminder(false)
                        ReminderScheduler.cancel(ctx)
                    } else if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        p.setWeeklyReminder(true)
                        ReminderScheduler.schedule(ctx)
                    }
                },
            )
            Text(tr("تذكير كل جمعة 6 مساءً بقائمة «شاهد لاحقًا» (معطّل افتراضيًا)", "Friday 6 pm reminder about your watch list (off by default)"), Modifier.padding(start = 8.dp))
        }
        HorizontalDivider()

        Text(tr("سجل البحث", "Search history"), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = historyOn, onCheckedChange = { p.setHistoryEnabled(it) })
            Text(tr("حفظ سجل البحث على الجهاز (معطّل افتراضيًا)", "Keep search history on this device (off by default)"), Modifier.padding(start = 8.dp))
        }
        HorizontalDivider()

        Text(tr("مفتاح TMDB", "TMDB key"), style = MaterialTheme.typography.titleSmall)
        Text(
            if (container.hasBuiltInKey) tr("هذا الإصدار يحوي مفتاحًا مدمجًا وقت البناء. يمكنك تجاوزه هنا.", "This build has a key embedded at build time. You can override it here.")
            else tr("لا يوجد مفتاح مدمج. الصق مفتاح TMDB (v3 API Key أو v4 Read Access Token) هنا.", "No embedded key. Paste your TMDB key (v3 API key or v4 read access token) here."),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = keyInput, onValueChange = { keyInput = it }, singleLine = true,
            label = { Text("TMDB key") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { p.setApiKey(keyInput) }) { Text(tr("حفظ المفتاح", "Save key")) }
            OutlinedButton(onClick = { keyInput = ""; p.setApiKey("") }) { Text(tr("حذف", "Remove")) }
        }
        Text(
            tr("المفتاح يُحفظ على جهازك فقط.", "The key is stored on your device only."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        var confirmClear by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { confirmClear = true }) { Text(tr("مسح بياناتي المحلية", "Erase my local data")) }
        Text(
            tr("يحذف المفضلة وشاهد لاحقًا وعلامات المشاهدة والتصفيات المحفوظة والذوق المتعلَّم وسجل البحث من هذا الجهاز.", "Deletes favorites, watch list, watched marks, saved filters, learned taste and search history from this device."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (confirmClear) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(tr("مسح كل بياناتك المحلية؟", "Erase all your local data?")) },
                text = { Text(tr("لا يمكن التراجع عن هذا.", "This cannot be undone.")) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { p.clearUserData(); confirmClear = false }) { Text(tr("مسح", "Erase")) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text(tr("إلغاء", "Cancel")) } },
            )
        }
        OutlinedButton(onClick = onAbout) { Text(tr("حول التطبيق والمصادر", "About & sources")) }
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع", "Back")) }
        Text("Movies Selection", style = MaterialTheme.typography.headlineSmall)
        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium)
        Text(
            tr(
                "تطبيق لاكتشاف الأفلام والمسلسلات عبر تصفية هرمية ذكية. يعتمد المحرك على بيانات TMDB فقط، والسمات غير المتوفرة تبقى «غير معروفة» ولا تُخمَّن.",
                "An app to discover movies and series through smart hierarchical filtering. The engine uses TMDB data only; missing attributes stay \"unknown\" and are never guessed.",
            ),
        )
        Text(
            tr(
                "هذا المنتج يستخدم واجهة TMDB لكنه غير معتمد أو مصدّق من TMDB. جميع بيانات الأفلام والمسلسلات والصور مصدرها TMDB (themoviedb.org).",
                "This product uses the TMDB API but is not endorsed or certified by TMDB. All movie/series data and images come from TMDB (themoviedb.org).",
            ),
        )
        Text(
            tr(
                "التصنيفات المشتقة (مثل «أنيمي ياباني» و«فيلم قصير» وفئات التقييم والمدة) محسوبة من حقول TMDB وهي تقديرية وليست تصنيفات رسمية.",
                "Derived categories (e.g. Japanese anime, short film, rating/runtime bands) are computed from TMDB fields and are estimates, not official labels.",
            ),
        )
        Text(
            tr(
                "الاستخدام التجاري يتطلب مراجعة شروط TMDB وترخيصها قبل النشر.",
                "Commercial use requires reviewing TMDB's terms and licensing before publishing.",
            ),
        )
        Text(
            tr("الإعلانات: بانر في الرئيسية وصفحة العمل فقط، ولا يظهر أثناء التصفية أو الاختيار.", "Ads: a banner on Home and Details only; never while filtering or choosing."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BuildConfig.PRIVACY_URL.isNotBlank()) {
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            OutlinedButton(onClick = { uri.openUri(BuildConfig.PRIVACY_URL) }) { Text(tr("سياسة الخصوصية", "Privacy policy")) }
        }
    }
}
