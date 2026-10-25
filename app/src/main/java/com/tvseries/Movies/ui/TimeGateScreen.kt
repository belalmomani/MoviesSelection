@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.JourneyClock
import com.tvseries.Movies.core.Slot
import com.tvseries.Movies.core.TimeWorld
import com.tvseries.Movies.core.TimeWorlds
import com.tvseries.Movies.core.layoutSlots
import com.tvseries.Movies.core.wrapDegrees
import com.tvseries.Movies.data.DetailsDto
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
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

    // Opening sequence (played once per visit of the app session).
    var introMs by mutableLongStateOf(0L)
    var introDone by mutableStateOf(false)

    // Extras.
    var radar by mutableStateOf(false)
    var pendingYawJump by mutableStateOf(false)
    var yawTarget by mutableStateOf<Float?>(null)
    var pitchTarget by mutableStateOf<Float?>(null)

    // Attention screen: one screen is highlighted for a few seconds; the user may approach or ignore it.
    var spotSlot by mutableIntStateOf(-1)
    var spotEnv by mutableFloatStateOf(0f)
    var spotStart by mutableLongStateOf(0L)
    var nextSpotAt by mutableLongStateOf(20000L)

    // Which pool title each screen currently shows (titles rotate during the journey).
    val shown = mutableStateListOf<Int>()
    var slotCount by mutableIntStateOf(0)
    var frontSlot by mutableIntStateOf(0)
    var lastSwapAt by mutableLongStateOf(0L)

    // Black hole ("time hole"): orbiting screens, time dilation, compressed worlds.
    var hole by mutableStateOf(false)
    var holeEnter by mutableStateOf(false)
    var holeExit by mutableStateOf(false)
    var holeSince by mutableLongStateOf(0L)
    var holeTau by mutableFloatStateOf(0f)
    var holeDilation by mutableFloatStateOf(1f)
    var holePool by mutableStateOf<List<WorkCard>>(emptyList())
    var lastHoleEnd by mutableLongStateOf(0L)
    var lastPoolRefresh by mutableLongStateOf(0L)

    // "Swallowing screen": a tapped title opens a short portal transition before its info.
    var swallowCard by mutableStateOf<WorkCard?>(null)
    var swallowMs by mutableLongStateOf(0L)
    var swallowDetails by mutableStateOf<DetailsDto?>(null)
}

class TimeGateViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val state = TimeGateState(JourneyClock(TimeWorlds.all.size))
    private val keywordCache = HashMap<String, List<Int>>()
    private var loadedType: String = c.prefs.contentType.value

    /** Loads the station and its neighbours so the next jump already has screens to show. */
    fun ensure(i: Int) {
        val type = c.prefs.contentType.value
        if (type != loadedType) {
            // the all / movies / series choice changed: stations must be fetched again for the new type
            loadedType = type
            state.stations.clear()
            state.trailers.clear()
            state.trailerCodes.clear()
            state.activeKey = null
        }
        val n = TimeWorlds.all.size
        for (k in listOf(i, (i + 1) % n, (i - 1 + n) % n)) load(k, false)
    }

    fun retry(i: Int) = load(i, true)

    /** Fills the radar: fetches every station once (slowly, to stay polite to TMDB). */
    fun scanAll() {
        viewModelScope.launch {
            for (i in TimeWorlds.all.indices) {
                load(i, false)
                delay(500)
            }
        }
    }

    private fun load(i: Int, force: Boolean) {
        val cur = state.stations[i]
        if (!force && (cur is Load.Ok || cur is Load.Loading)) return
        state.stations[i] = Load.Loading
        viewModelScope.launch {
            state.stations[i] = try {
                Load.Ok(fetchWorld(TimeWorlds.all[i]))
            } catch (e: TmdbError) {
                Load.Fail(e)
            }
        }
    }

    /** Resolves keyword phrases with TMDB's own keyword search, then discovers titles. Nothing is invented. */
    private suspend fun keywordIds(term: String): List<Int> {
        keywordCache[term]?.let { return it }
        val found = c.repo.searchKeywords(term)
        val exact = found.filter { it.name.equals(term, ignoreCase = true) }
        val ids = (if (exact.isNotEmpty()) exact.take(2) else found.take(1)).map { it.id }
        keywordCache[term] = ids
        return ids
    }

    private suspend fun fetchWorld(w: TimeWorld): List<WorkCard> {
        val ids = LinkedHashSet<Int>()
        for (term in w.keywords) ids += keywordIds(term)
        val hasOtherSource = w.genres.isNotEmpty() || w.yearFrom != null
        if (w.keywords.isNotEmpty() && ids.isEmpty() && !hasOtherSource) return emptyList()
        val q = c.repo.baseQuery().copy(
            minVotes = w.minVotes, sort = "vote_count.desc", keywords = ids,
            genres = w.genres, yearFrom = w.yearFrom, yearTo = w.yearTo,
        )
        val type = c.prefs.contentType.value
        val movies = if (type == "tv") emptyList() else (c.repo.discover("movie", 1, q).results + c.repo.discover("movie", 2, q).results)
            .mapNotNull { it.toCard("movie") }.filter { it.posterPath != null }.take(if (type == "movie") 30 else 22)
        val shows = if (type == "movie") emptyList() else (
            if (type == "tv") c.repo.discover("tv", 1, q).results + c.repo.discover("tv", 2, q).results else c.repo.discover("tv", 1, q).results
            ).mapNotNull { it.toCard("tv") }.filter { it.posterPath != null }.take(if (type == "tv") 30 else 10)
        val mixed = ArrayList<WorkCard>()
        for (k in 0 until maxOf(movies.size, shows.size)) {
            movies.getOrNull(k)?.let { mixed += it }
            shows.getOrNull(k)?.let { mixed += it }
        }
        return mixed.distinctBy { it.key }.take(32)
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

    /** Sudden jump to a random station, preferring ones known to have enough titles. */
    fun randomJump() {
        val n = TimeWorlds.all.size
        val others = (0 until n).filter { it != state.index }
        val good = others.filter { ((state.stations[it] as? Load.Ok)?.value?.size ?: 0) >= TimeWorlds.ENOUGH }
        val pick = (if (good.isNotEmpty()) good else others).random()
        state.pendingYawJump = true
        state.clock.jumpTo(pick)
    }

    /** Enter from the HUD: a short warp, and the hole opens at its midpoint. */
    fun enterHole() {
        if (state.hole || state.holeEnter) return
        state.holeEnter = true
        state.clock.pulse()
    }

    fun beginHole() {
        state.hole = true
        state.holeEnter = false
        state.holeSince = state.timeMs
        state.holeTau = 0f
        state.activeKey = null
        refreshHolePool()
        // make sure several eras are loaded so the orbiting screens come from different periods
        (0 until TimeWorlds.all.size).shuffled().take(5).forEach { load(it, false) }
    }

    /** Leaving the hole is a rare long jump to a far-away station. */
    fun exitHole() {
        if (!state.hole || state.holeExit) return
        val n = TimeWorlds.all.size
        val far = (state.index + n / 2 + Random.nextInt(-1, 2) + n) % n
        state.holeExit = true
        state.pendingYawJump = true
        state.clock.jumpTo(far)
    }

    /** Titles from different stations, only from stations whose data is really loaded. */
    fun refreshHolePool() {
        state.lastPoolRefresh = state.timeMs
        val picks = ArrayList<WorkCard>()
        for ((_, s) in state.stations) if (s is Load.Ok) picks += s.value.shuffled().take(2)
        state.holePool = picks.distinctBy { it.key }.shuffled().take(14)
    }

    fun swallow(card: WorkCard) {
        state.swallowCard = card
        state.swallowMs = 0L
        state.swallowDetails = null
        viewModelScope.launch {
            state.swallowDetails = try {
                c.repo.details(card.mediaType, card.id)
            } catch (e: TmdbError) {
                null
            }
        }
    }

    fun closeSwallow() {
        state.swallowCard = null
    }

    fun watchLater(card: WorkCard) {
        if (c.prefs.watchlist.value.none { it.key == card.key }) c.prefs.toggleWatchlist(card)
    }

    /** Swaps one non-front screen to the next unseen title of the station. */
    fun rotateOneTitle() {
        val pool = (state.stations[state.index] as? Load.Ok)?.value ?: return
        val n = state.slotCount
        if (pool.size <= n || state.shown.size < n || n < 2) return
        val slot = (0 until n).filter { it != state.frontSlot }.random()
        val used = state.shown.toSet()
        val next = (0 until pool.size).firstOrNull { it !in used && it > (state.shown.maxOrNull() ?: 0) } ?: (0 until pool.size).firstOrNull { it !in used } ?: return
        state.shown[slot] = next
    }
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
            st.timeMs += dt
            if (!st.introDone) {
                st.introMs += dt
                if (st.introMs >= 7000L) st.introDone = true
                continue
            }
            if (st.swallowCard != null) {
                st.swallowMs = min(st.swallowMs + dt, 4000L)
                continue
            }
            st.clock.frozen = st.hole
            val changed = st.clock.tick(dt)
            st.warp = st.clock.warpFraction
            if (changed) {
                st.index = st.clock.index
                vm.ensure(st.index)
                if (st.holeExit) {
                    st.hole = false
                    st.holeExit = false
                    st.lastHoleEnd = st.timeMs
                } else if (st.holeEnter || (!st.hole && st.advancing && !st.manual && st.timeMs - st.lastHoleEnd > 120000L && Random.nextFloat() < 0.25f)) {
                    vm.beginHole() // entering at the middle of the warp, while the screens are faded out
                }
                if (st.pendingYawJump) {
                    st.yaw = (st.yaw + 90f + Random.nextFloat() * 180f) % 360f
                    st.pendingYawJump = false
                }
            }

            if (st.hole) {
                st.holeDilation = 1f + 0.8f * sin(st.timeMs / 2000f)
                st.holeTau += dt / 1000f * st.holeDilation * st.speed
                if (st.holePool.size < 10 && st.timeMs - st.lastPoolRefresh > 2000L) vm.refreshHolePool()
                if (!st.manual && !st.holeExit && st.timeMs - st.holeSince > 16000L) vm.exitHole()
            }

            // Camera: glide to a requested target, otherwise drift automatically (unless in manual mode).
            val yt = st.yawTarget
            val pt = st.pitchTarget
            if (yt != null || pt != null) {
                if (yt != null) {
                    val d = wrapDegrees(yt - st.yaw)
                    if (abs(d) < 1f) {
                        st.yaw = yt
                        st.yawTarget = null
                    } else {
                        st.yaw = (st.yaw + d * min(1f, dt / 260f) + 360f) % 360f
                    }
                }
                if (pt != null) {
                    val d = pt - st.pitch
                    if (abs(d) < 0.02f) {
                        st.pitch = pt
                        st.pitchTarget = null
                    } else {
                        st.pitch += d * min(1f, dt / 260f)
                    }
                }
            } else if (!st.manual && !st.hole) {
                st.yaw = (st.yaw + 9f * st.speed * dt / 1000f) % 360f
                st.pitch = 0.35f * sin(st.timeMs / 4200f)
            }

            // Attention screen: highlights one screen for ~4.5s every ~22s. Never opens anything by itself.
            if (st.hole) {
                st.spotSlot = -1
                st.spotEnv = 0f
            } else if (st.spotSlot < 0) {
                if (st.timeMs >= st.nextSpotAt && st.slotCount > 3) {
                    val choices = (0 until st.slotCount).filter { it != st.frontSlot }
                    if (choices.isNotEmpty()) {
                        st.spotSlot = choices.random()
                        st.spotStart = st.timeMs
                    }
                }
            } else {
                val p = (st.timeMs - st.spotStart) / 4500f
                if (p >= 1f) {
                    st.spotSlot = -1
                    st.spotEnv = 0f
                    st.nextSpotAt = st.timeMs + 22000L
                } else {
                    st.spotEnv = sin(PI.toFloat() * p)
                }
            }

            // Titles rotate while the journey runs.
            if (!st.hole && st.advancing && st.timeMs - st.lastSwapAt > 4200L) {
                st.lastSwapAt = st.timeMs
                vm.rotateOneTitle()
            }
        }
    }

    val world = TimeWorlds.all[st.index]
    val c1 by animateColorAsState(Color(world.colors.first), tween(1200), label = "c1")
    val c2 by animateColorAsState(Color(world.colors.second), tween(1200), label = "c2")
    val data = st.stations[st.index]
    val list = (data as? Load.Ok)?.value.orEmpty()
    val displayList = if (st.hole) st.holePool else list
    val slotN = min(if (st.hole) 14 else 18, displayList.size)
    val slots = remember(slotN, st.hole) {
        if (st.hole) (0 until slotN).map { Slot(it, 0, 0f) } else layoutSlots(slotN)
    }
    val bucket by remember { derivedStateOf { (st.yaw / 6f).toInt() } }
    val tauBucket by remember { derivedStateOf { (st.holeTau * 2f).toInt() } }

    SideEffect { st.slotCount = slotN }
    LaunchedEffect(st.index, slotN, st.hole) {
        st.shown.clear()
        st.shown.addAll(0 until slotN)
    }

    // The middle-ring screen facing the viewer is the one that plays its short trailer.
    val frontSlot by remember(slots) {
        derivedStateOf { slots.filter { it.row == 0 }.minByOrNull { abs(wrapDegrees(it.angle - st.yaw)) }?.index ?: 0 }
    }
    SideEffect { st.frontSlot = frontSlot }
    val frontKey by remember(slots, list) {
        derivedStateOf { if (st.hole) null else list.getOrNull(st.shown.getOrNull(frontSlot) ?: frontSlot)?.key }
    }
    LaunchedEffect(frontKey) {
        val k = frontKey
        if (k != null && st.introDone) {
            delay(if (st.activeKey == null) 400L else 1200L)
            st.activeKey = k
            list.firstOrNull { it.key == k }?.let { vm.ensureTrailer(it) }
        }
    }
    // Stations with no titles at all are skipped while travelling automatically.
    LaunchedEffect(st.index, data) {
        if (st.introDone && !st.hole && data is Load.Ok && data.value.isEmpty() && st.advancing) {
            delay(2200)
            vm.step(1)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val screenW = maxWidth * 0.30f
        val screenH = screenW * 1.5f
        val radiusPx = with(density) { (maxWidth * 0.40f).toPx() }
        val rowPx = with(density) { (screenH * 0.95f).toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val screenWpx = with(density) { screenW.toPx() }
        val shape = RoundedCornerShape(10.dp)

        // Environment: the world itself changes (gradient + scenery), with time-tunnel streaks during jumps.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(c1, c2)))
            val t = st.timeMs / 1000f
            if (st.hole) drawBlackHole(t, st.holeDilation) else drawEnvironment(world.env, t)
            val hump = sin(PI.toFloat() * st.warp)
            val cx = size.width / 2f
            val cy = size.height / 2f
            if (hump > 0.02f) {
                for (k in 0 until 48) {
                    val ang = k * (2f * PI.toFloat() / 48f)
                    val phase = (t * (0.25f + 1.6f * hump) + k * 0.137f) % 1f
                    val r0 = size.minDimension * (0.12f + 0.7f * phase)
                    val r1 = r0 + size.minDimension * (0.03f + 0.3f * hump)
                    drawLine(
                        Color.White.copy(alpha = (0.08f + 0.45f * hump) * (1f - phase)),
                        Offset(cx + cos(ang) * r0, cy + sin(ang) * r0), Offset(cx + cos(ang) * r1, cy + sin(ang) * r1),
                        strokeWidth = 2f + 3f * hump,
                    )
                }
            }
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), Offset(cx, cy), size.maxDimension * 0.75f))
        }

        // The 360 degree ring of screens, with different sizes and distances: around, above and below you.
        Box(
            Modifier.fillMaxSize().pointerInput(st.manual) {
                if (st.manual) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        st.yawTarget = null
                        st.pitchTarget = null
                        st.yaw = (((st.yaw - drag.x * 0.4f) % 360f) + 360f) % 360f
                        st.pitch = (st.pitch - drag.y * 0.004f).coerceIn(-1f, 1f)
                    }
                }
            },
        ) {
            // Draw far screens first so near ones overlap them (re-sorted every ~6 degrees).
            val ordered = if (st.hole) {
                slots.sortedBy { sin(orbitAngle(it.index, tauBucket / 2f)) }
            } else {
                slots.sortedBy { cos(Math.toRadians((it.angle - bucket * 6f).toDouble())) }
            }
            ordered.forEach { slot ->
                key(slot.index) {
                    val ci = if (st.hole) slot.index else (st.shown.getOrNull(slot.index) ?: slot.index)
                    val card = displayList.getOrNull(ci)
                    if (card != null) {
                        Box(
                            Modifier.align(Alignment.Center).size(screenW * slot.scale, screenH * slot.scale)
                                .graphicsLayer {
                                  if (st.hole) {
                                    val th = orbitAngle(slot.index, st.holeTau)
                                    val orb = orbitR(slot.index)
                                    val z = sin(th)
                                    val nearH = (z + 1f) / 2f
                                    val ox = cos(th) * orb * wPx * 0.62f
                                    translationX = ox
                                    translationY = z * orb * hPx * 0.30f + (0.44f - 0.5f) * hPx
                                    val sH = (0.35f + 0.65f * nearH) * (0.55f + 0.45f * orb / 0.62f)
                                    scaleX = sH * (0.7f + 0.3f * orb / 0.62f)
                                    scaleY = sH
                                    val behind = if (z < 0f && abs(ox) < screenWpx * 0.5f) 0.15f else 1f
                                    alpha = min(1f, (0.3f + 0.7f * nearH) * behind * abs(2f * st.warp - 1f))
                                    rotationZ = th * 57.29578f * 0.12f
                                    rotationY = cos(th) * 25f
                                    cameraDistance = 14f * this.density
                                  } else {
                                    val d = wrapDegrees(slot.angle - st.yaw)
                                    val rad = Math.toRadians(d.toDouble())
                                    val z = cos(rad).toFloat()
                                    val near = (z + 1f) / 2f
                                    val fade = abs(2f * st.warp - 1f)
                                    val spot = if (st.spotSlot == slot.index) st.spotEnv else 0f
                                    val shift = (slot.row - st.pitch) * rowPx
                                    translationX = sin(rad).toFloat() * radiusPx * slot.distance
                                    translationY = shift
                                    val s = (0.5f + 0.5f * near) * (1f + 0.3f * spot)
                                    scaleX = s
                                    scaleY = s
                                    alpha = min(1f, (0.25f + 0.75f * near + 0.4f * spot) * fade)
                                    rotationY = (-d).coerceIn(-70f, 70f)
                                    rotationX = shift / rowPx * -12f
                                    cameraDistance = 14f * this.density
                                  }
                                }
                                .clip(shape)
                                .border(2.dp, Color.White.copy(alpha = 0.35f), shape)
                                .clickable { vm.swallow(card) },
                        ) {
                            Crossfade(targetState = card, label = "screen") { c ->
                                ScreenFace(
                                    card = c, active = st.activeKey == c.key, videoKey = st.trailers[c.key],
                                    sound = st.sound, seed = slot.index, slotIndex = slot.index, st = st,
                                    onFail = { code -> vm.trailerFailed(c.key, code) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Flash + title during a jump.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.White.copy(alpha = 0.55f * sin(PI.toFloat() * st.warp)))
        }
        Text(
            world.name(arabic), color = Color.White, style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = sin(PI.toFloat() * st.warp) },
        )

        if (st.radar) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeRadar(
                    st, arabic,
                    counts = { i ->
                        when (val s = st.stations[i]) {
                            null -> -1
                            is Load.Loading -> -2
                            is Load.Fail -> -3
                            is Load.Ok -> s.value.size
                        }
                    },
                    onPick = { i -> vm.jump(i) },
                )
                Text(
                    tr("أخضر: أعمال كافية • أصفر: قليلة • أحمر: لا بيانات • رمادي: لم تُفحص. اضغط نقطة للانتقال.", "Green: enough titles • Yellow: few • Red: none • Grey: not scanned. Tap a dot to travel."),
                    color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(6.dp),
                )
                OutlinedButton(onClick = { vm.scanAll() }) { Text(tr("افحص كل المحطات", "Scan all stations"), color = Color.White) }
            }
        }

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
                    val dilationTenths by remember { derivedStateOf { (st.holeDilation * 10f).toInt() } }
                    Text(
                        if (st.hole) tr("ثقب الزمن · تمدد الزمن ×", "Time hole · dilation ×") + (dilationTenths / 10f).toString()
                        else world.name(arabic) + "  ·  " + world.sub(arabic),
                        color = Color.White, style = MaterialTheme.typography.titleSmall,
                    )
                    val s = data
                    Text(
                        when {
                            st.hole && st.holePool.isEmpty() -> tr("نجمع أعمالًا من حقب مختلفة...", "Gathering titles from different eras...")
                            st.hole -> tr("ثقب الزمن: أعمال من حقب مختلفة تدور حول الثقب وتتسارع وتتباطأ. اضغط أي شاشة.", "Time hole: titles from different eras orbit the hole, speeding up and slowing down. Tap a screen.")
                            s == null || s is Load.Loading -> tr("جاري تحميل أعمال هذه المحطة...", "Loading this station's titles...")
                            s is Load.Fail -> errorText(s.error)
                            list.isEmpty() -> tr("لا أعمال لهذه المحطة في TMDB؛ لن نعرض شيئًا مختلقًا، وسننتقل للتالية.", "TMDB has no titles for this station; nothing is made up, moving on.")
                            list.size < TimeWorlds.ENOUGH -> tr("بيانات هذه المحطة قليلة (${list.size} فقط).", "Thin data for this station (only ${list.size}).")
                            st.activeKey != null && st.trailerCodes.containsKey(st.activeKey!!) -> trailerMessage(st.trailerCodes[st.activeKey!!] ?: 0)
                            st.activeKey != null && st.trailers[st.activeKey!!] == "" ->
                                tr("لا يوجد مقطع إعلاني لهذا العمل؛ تبقى حركة الملصق.", "No trailer for this title; the screen keeps the poster motion.")
                            else -> if (st.manual) tr("اسحب لتدوير الرؤية 360° والنظر للأعلى والأسفل. اضغط أي شاشة لفتح العمل.", "Drag to look around 360° and up/down. Tap a screen to open it.")
                            else tr("الرحلة تلقائية. اضغط أي شاشة لفتح العمل.", "Automatic journey. Tap any screen to open it.")
                        },
                        color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(tr("يدوي", "Manual"), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Switch(checked = st.manual, onCheckedChange = { vm.setManual(it) }, modifier = Modifier.padding(start = 6.dp))
            }
            if (data is Load.Fail) OutlinedButton(onClick = { vm.retry(st.index) }) { Text(tr("إعادة المحاولة", "Retry"), color = Color.White) }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                item {
                    AssistChip(onClick = { st.radar = !st.radar }, label = { Text("📡 " + tr("الرادار", "Radar"), color = Color.White) })
                }
                item {
                    AssistChip(
                        onClick = { if (st.hole) vm.exitHole() else vm.enterHole() },
                        label = { Text("🕳️ " + (if (st.hole) tr("اخرج من الثقب", "Leave the hole") else tr("ثقب الزمن", "Time hole")), color = Color.White) },
                    )
                }
                item {
                    AssistChip(onClick = { vm.randomJump() }, label = { Text("⚡ " + tr("قفزة زمنية", "Time leap"), color = Color.White) })
                }
                if (st.spotSlot >= 0) item {
                    AssistChip(
                        onClick = {
                            val sl = layoutSlots(st.slotCount).firstOrNull { it.index == st.spotSlot }
                            if (sl != null) {
                                st.yawTarget = sl.angle
                                st.pitchTarget = sl.row.toFloat()
                            }
                        },
                        label = { Text("✨ " + tr("اقترب من الشاشة المميزة", "Approach the highlighted screen"), color = Color(0xFFFFD54F)) },
                    )
                }
                listOf(0.5f, 1f, 1.5f, 2f, 3f).forEach { sp ->
                    item {
                        AssistChip(
                            onClick = { vm.setSpeed(sp) },
                            label = { Text("×" + (if (sp % 1f == 0f) sp.toInt().toString() else sp.toString()), color = if (st.speed == sp) Color.Black else Color.White) },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = if (st.speed == sp) Color.White else Color.White.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
                item { TextButton(onClick = { st.sound = !st.sound }) { Text(if (st.sound) "🔊" else "🔇", color = Color.White) } }
            }

            if (st.manual) {
                // Time map: pick any station.
                LazyRow(contentPadding = PaddingValues(0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(TimeWorlds.all) { i, w ->
                        AssistChip(
                            onClick = { vm.jump(i) },
                            label = { Text(w.name(arabic), color = if (i == st.index) Color.Black else Color.White) },
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                containerColor = if (i == st.index) Color.White else Color.White.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.step(-1) }) { Text("⏪ " + tr("السابق", "Previous"), color = Color.White) }
                    OutlinedButton(onClick = { vm.step(1) }) { Text("⏩ " + tr("التالي", "Next"), color = Color.White) }
                    OutlinedButton(onClick = { vm.setAdvancing(!st.advancing) }) {
                        Text(if (st.advancing) "⏸ " + tr("إيقاف الانتقال", "Pause travel") else "▶ " + tr("استئناف الانتقال", "Resume travel"), color = Color.White)
                    }
                }
                Button(onClick = { vm.setManual(false) }) { Text(tr("العودة إلى التلقائي", "Back to automatic")) }
            }
        }

        val sc = st.swallowCard
        if (sc != null) {
            SwallowOverlay(
                st = st, card = sc, details = st.swallowDetails, arabic = arabic,
                onOpenPage = { vm.closeSwallow(); onOpen(sc) },
                onWatchLater = { vm.watchLater(sc) },
                onClose = { vm.closeSwallow() },
            )
        }

        if (!st.introDone) {
            val posters = st.stations.values.flatMap { s -> if (s is Load.Ok) s.value else emptyList() }.take(8)
            TimeGateIntro(st, arabic, posters, onSkip = { st.introMs = 7000L })
        }
    }
}

private const val CLIP_START = 6
private const val CLIP_SECONDS = 5

/** One cinema screen: moving poster everywhere; the front screen plays a ~5 second trailer clip. */
@Composable
private fun ScreenFace(
    card: WorkCard, active: Boolean, videoKey: String?, sound: Boolean, seed: Int,
    slotIndex: Int, st: TimeGateState, onFail: (Int) -> Unit,
) {
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
            if (st.spotSlot == slotIndex) {
                drawRect(Color(0xFFFFD54F).copy(alpha = 0.9f * st.spotEnv), style = Stroke(10f))
                drawRect(Color(0xFFFFD54F).copy(alpha = 0.18f * st.spotEnv))
            }
        }
        if (active) Text("🎬", Modifier.align(Alignment.TopEnd).padding(4.dp))
        if (st.spotSlot == slotIndex) Text("✨", Modifier.align(Alignment.TopStart).padding(4.dp))
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
