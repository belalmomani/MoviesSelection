@file:OptIn(ExperimentalMaterial3Api::class)

package com.tvseries.Movies.ui

import android.app.Application
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvseries.Movies.MoviesApp
import com.tvseries.Movies.core.LinkKind
import com.tvseries.Movies.core.WebCand
import com.tvseries.Movies.core.WebGraph
import com.tvseries.Movies.core.WorkScene
import com.tvseries.Movies.data.DetailsDto
import com.tvseries.Movies.data.DiscoverQuery
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.PersonDto
import com.tvseries.Movies.data.TmdbError
import com.tvseries.Movies.data.WorkCard
import com.tvseries.Movies.data.toCard
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class WebNeighbor(val card: WorkCard, val kind: LinkKind, val label: String)

class WebNode(val card: WorkCard, val details: DetailsDto?, val neighbors: List<WebNeighbor>, val genreIds: Set<Int>)

/**
 * A web of linked titles. Each title's neighbours come from real TMDB relations: its lead actors' other work,
 * its director/creator, titles sharing a story keyword, the same genre, and TMDB's own suggestions.
 */
class WebViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MoviesApp).container
    val trail = mutableStateListOf<WorkCard>()
    var current by mutableStateOf<WebNode?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<TmdbError?>(null)
    var startGenres by mutableStateOf<Set<Int>>(emptySet())
    private val cache = HashMap<String, WebNode>()
    private val personCache = HashMap<Int, PersonDto>()
    private var started = false

    fun start(type: String, id: Int) {
        if (started) return
        started = true
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val card = if (type == "random") {
                    c.repo.list("trending/all/week").results.mapNotNull { it.toCard("movie") }.filter { it.posterPath != null }.randomOrNull()
                        ?: throw TmdbError(TmdbError.Kind.NOT_FOUND, "Nothing to start from")
                } else {
                    c.repo.details(type, id).toCard(type)
                }
                trail.add(card)
                show(card)
            } catch (e: TmdbError) {
                error = e
                loading = false
            }
        }
    }

    fun retry(type: String, id: Int) {
        if (trail.isEmpty()) {
            started = false
            start(type, id)
        } else {
            viewModelScope.launch { show(trail[trail.size - 1]) }
        }
    }

    /** Crossing a portal: the neighbour becomes the new centre. */
    fun open(card: WorkCard) {
        trail.add(card)
        viewModelScope.launch { show(card) }
    }

    fun back() {
        if (trail.size > 1) {
            trail.removeAt(trail.size - 1)
            viewModelScope.launch { show(trail[trail.size - 1]) }
        }
    }

    fun jumpTo(index: Int) {
        if (index < 0 || index >= trail.size - 1) return
        while (trail.size > index + 1) trail.removeAt(trail.size - 1)
        viewModelScope.launch { show(trail[trail.size - 1]) }
    }

    fun watchLater(card: WorkCard) {
        if (c.prefs.watchlist.value.none { it.key == card.key }) c.prefs.toggleWatchlist(card)
    }

    private suspend fun show(card: WorkCard) {
        loading = true
        error = null
        try {
            val node = cache[card.key] ?: build(card).also { cache[card.key] = it }
            if (trail.size == 1) startGenres = node.genreIds
            current = node
        } catch (e: TmdbError) {
            error = e
        } finally {
            loading = false
        }
    }

    private suspend fun personLinks(id: Int, name: String, kind: LinkKind, crew: Boolean): List<Pair<WebCand, WorkCard>> {
        val p = personCache[id] ?: c.repo.person(id).also { personCache[id] = it }
        val all = if (crew) p.combinedCredits?.crew.orEmpty() else p.combinedCredits?.cast.orEmpty()
        val src = if (crew) all.filter { it.job == "Director" || it.job == "Creator" }.ifEmpty { all } else all
        return src.sortedByDescending { it.popularity ?: 0.0 }
            .mapNotNull { it.toCard("movie") }.filter { it.posterPath != null }.distinctBy { it.key }.take(8)
            .map { WebCand(it.key, kind, name) to it }
    }

    private suspend fun discoverLinks(media: String, q: DiscoverQuery, kind: LinkKind, label: String): List<Pair<WebCand, WorkCard>> =
        c.repo.discover(media, 1, q).results.mapNotNull { it.toCard(media) }.filter { it.posterPath != null }.take(8)
            .map { WebCand(it.key, kind, label) to it }

    private suspend fun build(card: WorkCard): WebNode = coroutineScope {
        val d = c.repo.details(card.mediaType, card.id)
        val media = card.mediaType
        val base = c.repo.baseQuery().copy(sort = "popularity.desc", minVotes = 100)
        val jobs = ArrayList<Deferred<List<Pair<WebCand, WorkCard>>>>()

        d.credits?.cast.orEmpty().sortedBy { it.order ?: Int.MAX_VALUE }.take(2).forEach { p ->
            jobs += async { runCatching { personLinks(p.id, p.name, LinkKind.ACTOR, false) }.getOrDefault(emptyList()) }
        }
        val director = if (media == "movie") d.credits?.crew.orEmpty().firstOrNull { it.job == "Director" }
        else d.createdBy.orEmpty().firstOrNull()
        if (director != null) jobs += async { runCatching { personLinks(director.id, director.name, LinkKind.DIRECTOR, true) }.getOrDefault(emptyList()) }
        d.keywords?.all().orEmpty().take(2).forEach { k ->
            jobs += async { runCatching { discoverLinks(media, base.copy(keywords = setOf(k.id)), LinkKind.WORLD, k.name) }.getOrDefault(emptyList()) }
        }
        d.genres.orEmpty().firstOrNull()?.let { g ->
            jobs += async { runCatching { discoverLinks(media, base.copy(genres = setOf(g.id)), LinkKind.GENRE, g.name) }.getOrDefault(emptyList()) }
        }

        val pairs = ArrayList<Pair<WebCand, WorkCard>>()
        jobs.awaitAll().forEach { pairs += it }
        (d.recommendations?.results.orEmpty() + d.similar?.results.orEmpty()).mapNotNull { it.toCard(media) }
            .filter { it.posterPath != null }.take(8).forEach { pairs += WebCand(it.key, LinkKind.SIMILAR, "") to it }

        val cardByKey = HashMap<String, WorkCard>()
        pairs.forEach { cardByKey.putIfAbsent(it.second.key, it.second) }
        val exclude = trail.map { it.key }.toSet() + card.key
        val picked = WebGraph.pick(pairs.map { it.first }, exclude, limit = 8)
        val neighbors = picked.mapNotNull { cand -> cardByKey[cand.key]?.let { WebNeighbor(it, cand.kind, cand.label) } }
        WebNode(card, d, neighbors, d.genres.orEmpty().map { it.id }.toSet())
    }
}

/** Position (pixels) and scale of the i-th neighbour on a floating ring around the centre node. */
private fun webPos(i: Int, n: Int, w: Float, h: Float): Triple<Float, Float, Float> {
    val a = -PI.toFloat() / 2f + 2f * PI.toFloat() * i / maxOf(n, 1) + (hash(i + 3) - 0.5f) * 0.25f
    return Triple(w * 0.5f + cos(a) * w * 0.38f, h * CENTER_Y + sin(a) * h * 0.25f, 0.55f + 0.30f * ((sin(a) + 1f) / 2f))
}

private const val CENTER_Y = 0.37f

private fun kindColor(k: LinkKind): Color = when (k) {
    LinkKind.ACTOR -> Color(0xFFFFC107)
    LinkKind.DIRECTOR -> Color(0xFFFF7043)
    LinkKind.WORLD -> Color(0xFF00E5FF)
    LinkKind.GENRE -> Color(0xFFB388FF)
    LinkKind.SIMILAR -> Color(0xFF90CAF9)
}

@Composable
private fun linkText(nb: WebNeighbor, isTv: Boolean): String = when (nb.kind) {
    LinkKind.ACTOR -> tr("🎭 نفس الممثل: ", "🎭 Same actor: ") + nb.label
    LinkKind.DIRECTOR -> (if (isTv) tr("🎬 نفس المنشئ: ", "🎬 Same creator: ") else tr("🎬 نفس المخرج: ", "🎬 Same director: ")) + nb.label
    LinkKind.WORLD -> tr("🌌 عالم قصصي: ", "🌌 Story world: ") + nb.label
    LinkKind.GENRE -> tr("🧬 نفس النوع: ", "🧬 Same genre: ") + nb.label
    LinkKind.SIMILAR -> tr("🔗 ترشيح TMDB", "🔗 TMDB suggestion")
}

@Composable
fun WebScreen(type: String, id: Int, onExit: () -> Unit, onOpen: (WorkCard) -> Unit, vm: WebViewModel = viewModel()) {
    LaunchedEffect(type, id) { vm.start(type, id) }
    val arabic = isArabic()
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            timeMs += ((now - last) / 1_000_000L).coerceIn(0L, 100L)
            last = now
        }
    }
    val cross = remember { Animatable(0f) }
    var crossing by remember { mutableStateOf<WorkCard?>(null) }
    val appear = remember { Animatable(0f) }
    val node = vm.current
    val shape = RoundedCornerShape(10.dp)

    LaunchedEffect(crossing) {
        val target = crossing
        if (target != null) {
            cross.animateTo(1f, tween(650))
            vm.open(target)
        }
    }
    LaunchedEffect(node?.card?.key) {
        cross.snapTo(0f)
        crossing = null
        appear.snapTo(0f)
        appear.animateTo(1f, tween(500))
    }
    LaunchedEffect(vm.error) {
        if (vm.error != null) {
            cross.snapTo(0f)
            crossing = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF05020F))) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val centerW = maxWidth * 0.34f
        val centerH = centerW * 1.5f
        val cardW = maxWidth * 0.24f
        val neighbors = node?.neighbors.orEmpty()
        val n = neighbors.size
        val env = remember(node?.genreIds) { WorkScene.envFor(node?.genreIds.orEmpty()) }
        val colors = remember(env) { WorkScene.colorsFor(env) }

        // The node's own world: the scenery changes from title to title (a space film sits in a space station...).
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.42f }) {
            drawRect(Brush.verticalGradient(listOf(Color(colors.first), Color(colors.second))))
            drawEnvironment(env, timeMs / 1000f)
        }
        // Cosmos, glowing cables to every neighbour, and the traveller's platform.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = timeMs / 1000f
            drawRect(Brush.radialGradient(listOf(Color(0xFF7B3FE4).copy(alpha = 0.35f), Color.Transparent), Offset(w * 0.3f, h * 0.35f), w * 0.7f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFFF8A3D).copy(alpha = 0.28f), Color.Transparent), Offset(w * 0.65f, h * 0.5f), w * 0.6f))
            for (k in 0 until 70) {
                drawCircle(Color.White.copy(alpha = 0.35f + 0.35f * sin(t * 2f + k)), 1f + 1.8f * hash(k + 7), Offset(hash(k) * w, hash(k + 99) * h))
            }
            val vis = appear.value * (1f - cross.value)
            val c0 = Offset(w * 0.5f, h * CENTER_Y)
            for (i in 0 until n) {
                val (px, py, _) = webPos(i, n, w, h)
                val p1 = Offset(px, py)
                val ctrl = Offset((c0.x + p1.x) / 2f - (p1.y - c0.y) * 0.25f, (c0.y + p1.y) / 2f + (p1.x - c0.x) * 0.25f)
                val col = kindColor(neighbors[i].kind)
                val path = Path()
                path.moveTo(c0.x, c0.y)
                path.quadraticBezierTo(ctrl.x, ctrl.y, p1.x, p1.y)
                drawPath(path, col.copy(alpha = 0.18f * vis), style = Stroke(10f))
                drawPath(path, col.copy(alpha = 0.85f * vis), style = Stroke(2.5f))
                val u = (t * 0.35f + i * 0.13f) % 1f
                val bx = (1 - u) * (1 - u) * c0.x + 2 * (1 - u) * u * ctrl.x + u * u * p1.x
                val by = (1 - u) * (1 - u) * c0.y + 2 * (1 - u) * u * ctrl.y + u * u * p1.y
                drawCircle(Color.White.copy(alpha = vis), 5f, Offset(bx, by))
            }
            // glass platform with a hooded traveller seen from behind (the app's own drawing)
            val px = w * 0.5f
            val py = h * 0.60f
            val plat = Path()
            plat.moveTo(px - w * 0.22f, py)
            plat.lineTo(px + w * 0.22f, py)
            plat.lineTo(px + w * 0.30f, py + h * 0.045f)
            plat.lineTo(px - w * 0.30f, py + h * 0.045f)
            plat.close()
            drawPath(plat, Color(0xFF7CF9FF).copy(alpha = 0.16f))
            drawPath(plat, Color(0xFF7CF9FF).copy(alpha = 0.7f), style = Stroke(3f))
            val fh = h * 0.075f
            drawCircle(Color(0xFF0B1220), fh * 0.16f, Offset(px, py - fh * 0.9f))
            tri(Color(0xFF0B1220), px - fh * 0.28f, py - fh * 0.75f, px + fh * 0.28f, py - fh * 0.75f, px, py)
            drawLine(Color(0xFF7CF9FF).copy(alpha = 0.5f), Offset(px - fh * 0.28f, py - fh * 0.75f), Offset(px, py), 2f)
        }

        if (node != null) {
            // centre screen
            Box(
                Modifier.align(Alignment.Center).size(centerW, centerH)
                    .graphicsLayer {
                        translationY = (CENTER_Y - 0.5f) * hPx + sin(timeMs / 1100f) * 5f
                        alpha = appear.value * (1f - cross.value)
                        val s = 0.9f + 0.1f * appear.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(shape).border(3.dp, Color(0xFF7CF9FF).copy(alpha = 0.8f), shape)
                    .clickable { onOpen(node.card) },
            ) { PosterImage(Img.poster(node.card.posterPath), node.card.title, Modifier.fillMaxSize()) }

            // neighbours: every screen is a portal to a linked world
            neighbors.forEachIndexed { i, nb ->
                key(nb.card.key) {
                    val (px, py, sc) = webPos(i, n, wPx, hPx)
                    Column(
                        Modifier.align(Alignment.Center).width(cardW)
                            .graphicsLayer {
                                val tx = px - wPx / 2f
                                val ty = py - hPx / 2f + sin(timeMs / 900f + i) * 6f
                                val p = cross.value
                                val a = appear.value
                                if (crossing?.key == nb.card.key) {
                                    translationX = tx * (1f - p)
                                    translationY = ty * (1f - p) + (CENTER_Y - 0.5f) * hPx * p
                                    val s = sc * (1f + 2.2f * p)
                                    scaleX = s
                                    scaleY = s
                                    alpha = 1f
                                } else {
                                    translationX = tx
                                    translationY = ty
                                    scaleX = sc * a
                                    scaleY = sc * a
                                    alpha = a * (1f - p)
                                }
                            }
                            .clickable(enabled = crossing == null) { crossing = nb.card },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(cardW * 1.5f).clip(shape)
                                .border(2.dp, kindColor(nb.kind), shape),
                        ) { PosterImage(Img.poster(nb.card.posterPath), nb.card.title, Modifier.fillMaxSize()) }
                        Text(
                            linkText(nb, node.card.mediaType == "tv"), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 3.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // flash while crossing a portal
        Canvas(Modifier.fillMaxSize()) { drawRect(Color.White.copy(alpha = 0.5f * sin(PI.toFloat() * cross.value))) }

        if (vm.loading && node == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        vm.error?.let { e ->
            Column(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                ErrorBox(e, onRetry = { vm.retry(type, id) })
            }
        }

        // top: exit + the path travelled so far
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("✕ " + tr("خروج", "Exit"), color = Color.White) }
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(vm.trail.toList()) { i, c ->
                    val last = i == vm.trail.size - 1
                    Box(
                        Modifier.size(34.dp, 51.dp).clip(RoundedCornerShape(5.dp))
                            .border(if (last) 2.dp else 1.dp, if (last) Color(0xFF7CF9FF) else Color.White.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                            .clickable { vm.jumpTo(i) },
                    ) { PosterImage(Img.poster(c.posterPath), c.title, Modifier.fillMaxSize()) }
                }
            }
        }

        // bottom: where you are + what you can do
        if (node != null) {
            val unexpected = vm.trail.size > 2 && WebGraph.unexpected(vm.startGenres, node.genreIds)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(node.card.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                val meta = listOfNotNull(
                    node.card.year, node.card.rating?.let { "★ " + fmt1(it) },
                    node.details?.genres.orEmpty().joinToString(if (arabic) "، " else ", ") { it.name }.ifBlank { null },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                if (unexpected) Text(tr("✨ اكتشاف لم تكن تبحث عنه: لا يشارك نوع بداية رحلتك.", "✨ An unexpected discovery: it shares no genre with where you started."), color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelMedium)
                Text(
                    tr("اضغط أي شاشة محيطة لتعبر إليها (${vm.trail.size} محطات في رحلتك). لا ظهر رابط إلا من بيانات TMDB.", "Tap a surrounding screen to cross into it (${vm.trail.size} stops so far). Links come only from TMDB data."),
                    color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall,
                )
                if (n == 0 && !vm.loading) Text(tr("لا روابط كافية لهذا العمل في TMDB.", "TMDB has too few links for this title."), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onOpen(node.card) }) { Text(tr("افتح صفحته", "Open page")) }
                    OutlinedButton(onClick = { vm.watchLater(node.card) }) { Text(tr("شاهد لاحقًا", "Later"), color = Color.White) }
                    OutlinedButton(onClick = { vm.back() }, enabled = vm.trail.size > 1) { Text(tr("رجوع", "Back"), color = Color.White) }
                }
            }
        }
    }
}
