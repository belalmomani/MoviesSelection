@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
                st.yaw = (st.yaw + 7f * dt / 1000f) % 360f
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
                    PosterImage(
                        Img.poster(card.posterPath), card.title,
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
                    )
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
                            else -> if (st.manual) tr("اسحب لتدوير الرؤية 360° والنظر للأعلى والأسفل. اضغط أي شاشة لفتح العمل.", "Drag to look around 360° and up/down. Tap a screen to open it.")
                            else tr("الرحلة تلقائية. اضغط أي شاشة لفتح العمل.", "Automatic journey. Tap any screen to open it.")
                        },
                        color = Color.White.copy(alpha = 0.8f), style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
                Text(tr("التحكم اليدوي", "Manual control"), color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                Switch(checked = st.manual, onCheckedChange = { vm.setManual(it) }, modifier = Modifier.padding(start = 6.dp))
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
