@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.Eras
import com.tvseries.Movies.core.JourneyClock
import com.tvseries.Movies.core.layoutSlots
import com.tvseries.Movies.core.wrapDegrees
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Journey state lives in the view model, so opening a title and coming back resumes at the same place. */
class TimeGateState(val clock: JourneyClock) {
    var manual by mutableStateOf(false)
    var advancing by mutableStateOf(true)
    var yaw by mutableFloatStateOf(0f)
    var pitch by mutableFloatStateOf(0f)
    var index by mutableIntStateOf(clock.index)
    var warp by mutableFloatStateOf(0f)
    var timeMs by mutableLongStateOf(0L)
    val stations = mutableStateMapOf<Int, Load<List<WorkCard>>>()

    /** 1x = each front screen stays about 5 seconds (one short trailer clip). */
    var speed by mutableFloatStateOf(1f)
    var sound by mutableStateOf(false)
    var activeKey by mutableStateOf<String?>(null)

    /** card key -> YouTube video key; "" = no trailer; "?" = still looking. */
    val trailers = mutableStateMapOf<String, String>()

    /** card key -> why its trailer could not play (YouTube error code, or -1 = never started). */
    val trailerCodes = mutableStateMapOf<String, Int>()
}

class TimeGateViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val state = TimeGateState(JourneyClock(Eras.starts.size))

    /** Loads the station and its neighbours so the next jump already has screens to show. */
    fun ensure(i: Int) {
        val n = Eras.starts.size
        for (k in listOf(i, (i + 1) % n, (i - 1 + n) % n)) load(k, false)
    }

    fun retry(i: Int) = load(i, true)

    private fun load(i: Int, force: Boolean) {
        val cur = state.stations[i]
        if (!force && (cur is Load.Ok || cur is Load.Loading)) return
        state.stations[i] = Load.Loading
        viewModelScope.launch {
            state.stations[i] = try {
                val start = Eras.starts[i]
                val q = c.repo.baseQuery().copy(
                    yearFrom = start, yearTo = start + 9, minVotes = Eras.minVotes(start), minRating = 6, sort = "vote_count.desc",
                )
                val movies = c.repo.discover("movie", 1, q).results.mapNotNull { it.toCard("movie") }.filter { it.posterPath != null }.take(12)
                val shows = c.repo.discover("tv", 1, q).results.mapNotNull { it.toCard("tv") }.filter { it.posterPath != null }.take(6)
                val mixed = ArrayList<WorkCard>()
                for (k in 0 until maxOf(movies.size, shows.size)) {
                    movies.getOrNull(k)?.let { mixed += it }
                    shows.getOrNull(k)?.let { mixed += it }
                }
                Load.Ok(mixed.distinctBy { it.key }.take(18))
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    fun ensureTrailer(card: WorkCard) {
        if (state.trailers.containsKey(card.key)) return
        state.trailers[card.key] = "?"
        viewModelScope.launch {
            val k = try {
                com.tvseries.Movies.data.Trailers.pick(c.repo.videos(card.mediaType, card.id))?.key
            } catch (e: TmdbError) {
                null
            }
            state.trailers[card.key] = k.orEmpty()
        }
    }

    fun trailerFailed(cardKey: String, code: Int) {
        state.trailers[cardKey] = ""
        state.trailerCodes[cardKey] = code
    }

    fun setSpeed(v: Float) {
        state.speed = v
        state.clock.speed = v
    }

    fun setManual(on: Boolean) {
        state.manual = on
        setAdvancing(!on) // manual control freezes automatic travel; auto mode resumes it
    }

    fun setAdvancing(on: Boolean) {
        state.advancing = on
        state.clock.advancing = on
    }

    fun jump(i: Int) = state.clock.jumpTo(i)
    fun step(d: Int) = state.clock.step(d)
}

@Composable
fun TimeGateScreen(onExit: () -> Unit, onOpen: (WorkCard) -> Unit, vm: TimeGateViewModel = viewModel()) {
    val st = vm.state
    val arabic = isArabic()
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Frame loop: the journey runs by itself; nothing needs to be pressed.
    LaunchedEffect(Unit) {
        vm.ensure(st.clock.index)
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000L).coerceIn(0L, 100L)
            last = now
            val changed = st.clock.tick(dt)
            st.timeMs += dt
            st.warp = st.clock.warpFraction
            if (changed) {
                st.index = st.clock.index
                vm.ensure(st.index)
            }
            if (!st.manual) {
                st.yaw = (st.yaw + 9f * st.speed * dt / 1000f) % 360f
                st.pitch = 0.35f * sin(st.timeMs / 4200f)
            }
        }
    }

    val start = Eras.starts[st.index]
    val colors = Eras.palette[start] ?: (0xFF111827L to 0xFF7C3AEDL)
    val c1 by animateColorAsState(Color(colors.first), tween(1200), label = "c1")
    val c2 by animateColorAsState(Color(colors.second), tween(1200), label = "c2")
    val data = st.stations[st.index]
    val list = (data as? Load.Ok)?.value.orEmpty()
    val slots = remember(list.size) { layoutSlots(list.size) }
    val bucket by remember { derivedStateOf { (st.yaw / 6f).toInt() } }
    // The middle-ring screen facing the viewer is the one that plays its short trailer.
    val frontKey by remember(slots, list) {
        derivedStateOf {
            slots.filter { it.row == 0 }.minByOrNull { abs(wrapDegrees(it.angle - st.yaw)) }?.let { list.getOrNull(it.index)?.key }
        }
    }
    LaunchedEffect(frontKey) {
        val k = frontKey
        if (k != null) {
            delay(if (st.activeKey == null) 400L else 1200L)
            st.activeKey = k
            list.firstOrNull { it.key == k }?.let { vm.ensureTrailer(it) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val screenW = maxWidth * 0.34f
        val screenH = screenW * 1.5f
        val radiusPx = with(density) { (maxWidth * 0.40f).toPx() }
        val rowPx = with(density) { (screenH * 0.95f).toPx() }
        val shape = RoundedCornerShape(10.dp)

        // Environment: era gradient + time tunnel streaks that intensify during a jump.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(c1, c2)))
            val hump = sin(PI.toFloat() * st.warp)
            val t = st.timeMs / 1000f
            val cx = size.width / 2f
            val cy = size.height / 2f
            for (k in 0 until 48) {
                val ang = k * (2f * PI.toFloat() / 48f)
                val phase = (t * (0.25f + 1.6f * hump) + k * 0.137f) % 1f
                val r0 = size.minDimension * (0.12f + 0.7f * phase)
                val r1 = r0 + size.minDimension * (0.03f + 0.3f * hump)
                drawLine(
                    Color.White.copy(alpha = (0.08f + 0.45f * hump) * (1f - phase)),
                    Offset(cx + cos(ang) * r0, cy + sin(ang) * r0),
                    Offset(cx + cos(ang) * r1, cy + sin(ang) * r1),
                    strokeWidth = 2f + 3f * hump,
                )
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)), Offset(cx, cy), size.maxDimension * 0.75f))
        }
        Text(
            Eras.label(start), color = Color.White.copy(alpha = 0.10f), style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
            modifier = Modifier.align(Alignment.Center),
        )

        // The 360° ring of screens: left, right, front, behind, above and below.
        Box(
            Modifier.fillMaxSize().pointerInput(st.manual) {
                if (st.manual) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        st.yaw = (((st.yaw - drag.x * 0.4f) % 360f) + 360f) % 360f
                        st.pitch = (st.pitch - drag.y * 0.004f).coerceIn(-1f, 1f)
                    }
                }
            },
        ) {
            // Draw far screens first so near ones overlap them (re-sorted every ~6 degrees).
            val ordered = slots.sortedBy { cos(Math.toRadians((it.angle - bucket * 6f).toDouble())) }
            ordered.forEach { slot ->
                val card = list.getOrNull(slot.index)
                if (card != null) key(card.key) {
                    Box(
                        Modifier.align(Alignment.Center).size(screenW, screenH)
                            .graphicsLayer {
                                val d = wrapDegrees(slot.angle - st.yaw)
                                val rad = Math.toRadians(d.toDouble())
                                val z = cos(rad).toFloat()
                                val near = (z + 1f) / 2f
                                val fade = abs(2f * st.warp - 1f)
                                val shift = (slot.row - st.pitch) * rowPx
                                translationX = sin(rad).toFloat() * radiusPx
                                translationY = shift
                                scaleX = 0.5f + 0.5f * near
                                scaleY = 0.5f + 0.5f * near
                                alpha = (0.25f + 0.75f * near) * fade
                                rotationY = (-d).coerceIn(-70f, 70f)
                                rotationX = shift / rowPx * -12f
                                cameraDistance = 14f * this.density
                            }
                            .clip(shape)
                            .border(2.dp, Color.White.copy(alpha = 0.35f), shape)
                            .clickable { onOpen(card) },
                    ) {
                        val vk = st.trailers[card.key]
                        ScreenFace(
                            card = card, active = st.activeKey == card.key,
                            videoKey = vk, sound = st.sound, seed = slot.index,
                            onFail = { code -> vm.trailerFailed(card.key, code) },
                        )
                    }
                }
            }
        }

        // Flash + title during the jump.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.White.copy(alpha = 0.55f * sin(PI.toFloat() * st.warp)))
        }
        Text(
            tr("عبور زمني...", "Time jump..."), color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = sin(PI.toFloat() * st.warp) },
        )

        TextButton(onClick = onExit, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("✕ " + tr("خروج", "Exit"), color = Color.White)
        }

        // HUD
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Eras.label(start) + "  ·  " + Eras.name(start, arabic), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    val s = data
                    Text(
                        when {
                            s == null || s is Load.Loading -> tr("جاري تحميل أعمال هذه المحطة...", "Loading this station's titles...")
                            s is Load.Fail -> errorText(s.error)
                            list.isEmpty() -> tr("لا أعمال لهذه المحطة في TMDB.", "No titles for this station on TMDB.")
                            st.activeKey != null && st.trailerCodes.containsKey(st.activeKey!!) ->
                                trailerMessage(st.trailerCodes[st.activeKey!!] ?: 0)
                            st.activeKey != null && st.trailers[st.activeKey!!] == "" ->
                                tr("لا يوجد مقطع إعلاني لهذا العمل؛ تبقى الشاشة بحركة الملصق.", "No trailer for this title; the screen keeps the poster motion.")
                            else -> if (st.manual) tr("اسحب لتدوير الرؤية 360° والنظر للأعلى والأسفل. اضغط أي شاشة لفتح العمل.", "Drag to look around 360° and up/down. Tap a screen to open it.")
                            else tr("الرحلة تلقائية. اضغط أي شاشة لفتح العمل.", "Automatic journey. Tap any screen to open it.")
                        },
                        color = Color.White.copy(alpha = 0.8f), style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
                Text(tr("التحكم اليدوي", "Manual control"), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Switch(checked = st.manual, onCheckedChange = { vm.setManual(it) }, modifier = Modifier.padding(start = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("السرعة", "Speed"), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { sp ->
                    AssistChip(
                        onClick = { vm.setSpeed(sp) },
                        label = { Text("×" + (if (sp % 1f == 0f) sp.toInt().toString() else sp.toString()), color = if (st.speed == sp) Color.Black else Color.White) },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = if (st.speed == sp) Color.White else Color.White.copy(alpha = 0.12f),
                        ),
                    )
                }
                TextButton(onClick = { st.sound = !st.sound }) { Text(if (st.sound) "🔊" else "🔇", color = Color.White) }
            }
            if (data is Load.Fail) OutlinedButton(onClick = { vm.retry(st.index) }) { Text(tr("إعادة المحاولة", "Retry"), color = Color.White) }

            if (st.manual) {
                // Time map: pick any station.
                LazyRow(contentPadding = PaddingValues(0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(Eras.starts) { i, y ->
                        AssistChip(
                            onClick = { vm.jump(i) },
                            label = { Text(Eras.label(y), color = if (i == st.index) Color.Black else Color.White) },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = if (i == st.index) Color.White else Color.White.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.step(-1) }) { Text("⏪ " + tr("الماضي", "Past"), color = Color.White) }
                    OutlinedButton(onClick = { vm.step(1) }) { Text("⏩ " + tr("المستقبل", "Future"), color = Color.White) }
                    OutlinedButton(onClick = { vm.setAdvancing(!st.advancing) }) {
                        Text(if (st.advancing) "⏸ " + tr("إيقاف الانتقال", "Pause travel") else "▶ " + tr("استئناف الانتقال", "Resume travel"), color = Color.White)
                    }
                }
                Button(onClick = { vm.setManual(false) }) { Text(tr("العودة إلى التلقائي", "Back to automatic")) }
            }
        }
    }
}

private const val CLIP_START = 6
private const val CLIP_SECONDS = 5

/** One cinema screen: moving poster everywhere; the front screen plays a ~5 second trailer clip. */
@Composable
private fun ScreenFace(card: WorkCard, active: Boolean, videoKey: String?, sound: Boolean, seed: Int, onFail: (Int) -> Unit) {
    val inf = rememberInfiniteTransition(label = "face")
    val zoom = inf.animateFloat(
        1.02f, 1.14f, infiniteRepeatable(tween(6500 + (seed % 5) * 700, easing = LinearEasing), RepeatMode.Reverse), label = "zoom",
    )
    val sweep = inf.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(3800 + (seed % 4) * 500, easing = LinearEasing), RepeatMode.Restart), label = "sweep",
    )
    Box(Modifier.fillMaxSize()) {
        PosterImage(
            Img.poster(card.posterPath), card.title,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom.value
                scaleY = zoom.value
                translationX = (zoom.value - 1.08f) * 90f
            },
        )
        if (active && !videoKey.isNullOrEmpty() && videoKey != "?") {
            TrailerPlayer(videoKey, sound, onFail, Modifier.fillMaxSize())
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * sweep.value
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    Offset(x - size.width * 0.3f, 0f), Offset(x + size.width * 0.3f, size.height),
                ),
            )
        }
        if (active) Text("🎬", Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

private class TrailerBridge(private val onFail: (Int) -> Unit, private val onStarted: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun error(code: Int) {
        main.post { onFail(code) }
    }

    @JavascriptInterface
    fun playing() {
        main.post { onStarted() }
    }
}

@Composable
private fun trailerMessage(code: Int): String = when (code) {
    101, 150, 153 -> tr("صاحب هذا الإعلان يمنع تشغيله داخل التطبيقات؛ تبقى حركة الملصق.", "The owner blocks playing this trailer inside apps; showing the animated poster.")
    100 -> tr("الإعلان لم يعد موجودًا على YouTube.", "The trailer no longer exists on YouTube.")
    -1 -> tr("لم يبدأ تشغيل المقطع (تحقق من الإنترنت، أو أن WebView محدَّث).", "The clip never started (check internet, or update Android System WebView).")
    else -> tr("تعذّر تشغيل المقطع (رمز $code).", "The clip could not play (code $code).")
}

private fun trailerHtml(key: String): String = """
    <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{pointer-events:none}</style></head>
    <body><div id="p"></div>
    <script src="https://www.youtube.com/iframe_api"></script>
    <script>
    var player;
    function onYouTubeIframeAPIReady(){
      player = new YT.Player('p',{width:'100%',height:'100%',videoId:'__KEY__',
        playerVars:{autoplay:1,mute:1,controls:0,playsinline:1,rel:0,modestbranding:1,disablekb:1,fs:0,iv_load_policy:3,start:__START__},
        events:{
          onReady:function(e){e.target.mute();e.target.playVideo();},
          onStateChange:function(e){
            if(e.data===1){Android.playing();clearTimeout(window.clipTimer);
              window.clipTimer=setTimeout(function(){player.seekTo(__START__,true);},__CLIP__*1000);}
          },
          onError:function(e){Android.error(e.data);}
        }});
    }
    </script></body></html>
""".trimIndent().replace("__KEY__", key).replace("__START__", CLIP_START.toString()).replace("__CLIP__", CLIP_SECONDS.toString())

/** Official YouTube IFrame player (muted autoplay, no controls); loops a ~5 second excerpt. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TrailerPlayer(videoKey: String, sound: Boolean, onFail: (Int) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    var started by remember(videoKey) { mutableStateOf(false) }
    val web = remember(videoKey) {
        WebView(ctx).apply {
            setBackgroundColor(AndroidColor.BLACK)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            isClickable = false
            isFocusable = false
            webViewClient = WebViewClient()
            addJavascriptInterface(TrailerBridge(onFail) { started = true }, "Android")
            loadDataWithBaseURL("https://www.youtube.com", trailerHtml(videoKey), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            (web.parent as? android.view.ViewGroup)?.removeView(web)
            web.destroy()
        }
    }
    // If nothing plays within 10 seconds, say so instead of leaving a mysterious black screen.
    LaunchedEffect(web) {
        delay(10000)
        if (!started) onFail(-1)
    }
    LaunchedEffect(sound, web) {
        delay(1500)
        web.evaluateJavascript(if (sound) "try{player.unMute();player.setVolume(100);}catch(e){}" else "try{player.mute();}catch(e){}", null)
    }
    AndroidView(factory = { web }, modifier = modifier)
}
