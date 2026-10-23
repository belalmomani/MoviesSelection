package com.tvseries.Movies.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.withTransform
import com.tvseries.Movies.core.WorkScene
import com.tvseries.Movies.data.DetailsDto
import kotlin.math.sqrt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tvseries.Movies.core.Env
import com.tvseries.Movies.core.TimeWorlds
import com.tvseries.Movies.core.wrapDegrees
import com.tvseries.Movies.data.Img
import com.tvseries.Movies.data.WorkCard
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Cheap deterministic pseudo-random number between 0 and 1. */
internal fun hash(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) / 2147483647f
}

private fun DrawScope.tri(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
    val p = Path()
    p.moveTo(x1, y1)
    p.lineTo(x2, y2)
    p.lineTo(x3, y3)
    p.close()
    drawPath(p, color)
}

/** Draws the station's environment (on top of its gradient). [t] = seconds. */
fun DrawScope.drawEnvironment(env: Env, t: Float) {
    val w = size.width
    val h = size.height
    val hy = h * 0.62f
    when (env) {
        Env.FUTURE -> {
            val cx = w / 2f
            for (k in -10..10) drawLine(Color(0xFF00E5FF).copy(alpha = 0.35f), Offset(cx, hy), Offset(cx + k * w * 0.16f, h), 2f)
            for (j in 0 until 9) {
                val z = (j + (t * 0.6f) % 1f) / 9f
                val y = hy + (h - hy) * z * z
                drawLine(Color(0xFFFF2EC8).copy(alpha = 0.4f), Offset(0f, y), Offset(w, y), 2f)
            }
            for (k in 0 until 7) {
                val th = h * (0.15f + 0.25f * hash(k))
                drawRect(Color(0xFF00E5FF).copy(alpha = 0.18f), Offset(w * (0.08f + k * 0.14f), hy - th), Size(w * 0.035f, th))
            }
        }
        Env.ROME -> {
            drawCircle(Color(0xFFFFD27F).copy(alpha = 0.5f), h * 0.09f, Offset(w * 0.75f, h * 0.22f))
            drawRect(Color(0xFF3B2A1A).copy(alpha = 0.55f), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 6) {
                val x = w * (0.06f + k * 0.17f)
                drawRect(Color(0xFFE8D9B5).copy(alpha = 0.35f), Offset(x, hy - h * 0.34f), Size(w * 0.045f, h * 0.34f))
                drawRect(Color(0xFFE8D9B5).copy(alpha = 0.45f), Offset(x - w * 0.01f, hy - h * 0.36f), Size(w * 0.065f, h * 0.025f))
            }
        }
        Env.CASTLE -> {
            drawCircle(Color(0xFFE6ECFF).copy(alpha = 0.55f), h * 0.07f, Offset(w * 0.2f, h * 0.18f))
            val dark = Color(0xFF05070D).copy(alpha = 0.8f)
            drawRect(dark, Offset(w * 0.25f, hy - h * 0.3f), Size(w * 0.5f, h * 0.3f))
            drawRect(dark, Offset(w * 0.2f, hy - h * 0.42f), Size(w * 0.09f, h * 0.42f))
            drawRect(dark, Offset(w * 0.71f, hy - h * 0.42f), Size(w * 0.09f, h * 0.42f))
            tri(dark, w * 0.19f, hy - h * 0.42f, w * 0.245f, hy - h * 0.52f, w * 0.30f, hy - h * 0.42f)
            tri(dark, w * 0.70f, hy - h * 0.42f, w * 0.755f, hy - h * 0.52f, w * 0.81f, hy - h * 0.42f)
            for (k in 0 until 8) drawRect(dark, Offset(w * 0.25f + k * (w * 0.5f / 8f), hy - h * 0.325f), Size(w * 0.03f, h * 0.025f))
            drawRect(Color(0xFF05070D), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 3) {
                val a = 0.5f + 0.4f * sin(t * 9f + k * 2f)
                drawCircle(Color(0xFFFFA64D).copy(alpha = a), 10f + 4f * sin(t * 7f + k), Offset(w * (0.33f + k * 0.17f), hy - h * 0.12f))
            }
        }
        Env.JUNGLE -> {
            for (k in 0 until 6) drawRect(Color(0xFF0B3D1E).copy(alpha = 0.7f), Offset(w * (0.05f + k * 0.19f), 0f), Size(w * 0.05f, hy))
            for (k in 0 until 14) {
                val lx = w * hash(k + 40)
                val ly = h * (0.1f + 0.3f * hash(k + 80))
                tri(Color(0xFF1F7A3A).copy(alpha = 0.55f), lx, ly, lx + w * 0.07f, ly + h * 0.02f, lx, ly + h * 0.1f)
            }
            drawRect(Color(0xFF062411).copy(alpha = 0.85f), Offset(0f, hy), Size(w, h - hy))
            tri(Color(0xFF1A0E0A), w * 0.62f, hy, w * 0.82f, hy - h * 0.28f, w, hy)
            drawCircle(Color(0xFFFF7A1A).copy(alpha = 0.35f), h * 0.07f, Offset(w * 0.82f, hy - h * 0.28f))
            for (k in 0 until 10) {
                val p = (t * 0.25f + hash(k)) % 1f
                drawCircle(Color(0xFFFFB347).copy(alpha = 1f - p), 3f, Offset(w * 0.82f + (hash(k + 9) - 0.5f) * w * 0.1f, hy - h * 0.28f - p * h * 0.3f))
            }
            // a long-necked dinosaur silhouette walking by
            val x = ((t * 0.02f) % 1.3f - 0.15f) * w
            val c = Color(0xFF020B05).copy(alpha = 0.9f)
            drawOval(c, Offset(x, hy - h * 0.1f), Size(w * 0.16f, h * 0.07f))
            drawLine(c, Offset(x + w * 0.14f, hy - h * 0.07f), Offset(x + w * 0.2f, hy - h * 0.2f), w * 0.02f)
            drawCircle(c, w * 0.015f, Offset(x + w * 0.21f, hy - h * 0.205f))
            drawLine(c, Offset(x, hy - h * 0.07f), Offset(x - w * 0.1f, hy - h * 0.03f), w * 0.015f)
            for (k in 0 until 4) drawLine(c, Offset(x + w * (0.02f + k * 0.04f), hy - h * 0.04f), Offset(x + w * (0.02f + k * 0.04f), hy), w * 0.012f)
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF9BE7C4).copy(alpha = 0.25f)), startY = hy - h * 0.15f, endY = h),
                Offset(0f, hy - h * 0.15f), Size(w, h - (hy - h * 0.15f)),
            )
        }
        Env.DESERT -> {
            drawCircle(Color(0xFFFFE08A).copy(alpha = 0.6f), h * 0.12f, Offset(w * 0.7f, h * 0.26f))
            tri(Color(0xFF8A5A1E).copy(alpha = 0.85f), w * 0.08f, hy, w * 0.26f, hy - h * 0.3f, w * 0.44f, hy)
            tri(Color(0xFF7A4E18).copy(alpha = 0.85f), w * 0.36f, hy, w * 0.5f, hy - h * 0.22f, w * 0.64f, hy)
            tri(Color(0xFF6B4313).copy(alpha = 0.85f), w * 0.6f, hy, w * 0.7f, hy - h * 0.14f, w * 0.8f, hy)
            drawOval(Color(0xFFC98F3A).copy(alpha = 0.7f), Offset(-w * 0.2f, hy - h * 0.02f), Size(w * 0.9f, h * 0.3f))
            drawOval(Color(0xFFB57E2F).copy(alpha = 0.7f), Offset(w * 0.3f, hy + h * 0.02f), Size(w * 0.9f, h * 0.3f))
        }
        Env.NORTH -> {
            for (i in 0 until 40) {
                val x = w * i / 40f
                val len = h * 0.28f * (0.5f + 0.5f * sin(t * 0.8f + i * 0.35f))
                drawLine((if (i % 2 == 0) Color(0xFF6BFFB0) else Color(0xFFB36BFF)).copy(alpha = 0.22f), Offset(x, 0f), Offset(x, len), w / 40f)
            }
            tri(Color(0xFFDCEBFF).copy(alpha = 0.6f), w * 0.05f, hy, w * 0.22f, hy - h * 0.3f, w * 0.4f, hy)
            tri(Color(0xFFCFE0F5).copy(alpha = 0.6f), w * 0.55f, hy, w * 0.75f, hy - h * 0.36f, w * 0.95f, hy)
            drawRect(Color(0xFF06101F).copy(alpha = 0.85f), Offset(0f, hy), Size(w, h - hy))
            val bob = sin(t * 1.3f) * h * 0.01f
            val boat = Color(0xFF020509)
            tri(boat, w * 0.3f, hy + h * 0.06f + bob, w * 0.7f, hy + h * 0.06f + bob, w * 0.62f, hy + h * 0.1f + bob)
            tri(boat, w * 0.3f, hy + h * 0.06f + bob, w * 0.26f, hy + h * 0.03f + bob, w * 0.34f, hy + h * 0.06f + bob)
            drawRect(boat, Offset(w * 0.49f, hy - h * 0.06f + bob), Size(w * 0.012f, h * 0.12f))
            tri(Color(0xFFB71C1C).copy(alpha = 0.85f), w * 0.5f, hy - h * 0.06f + bob, w * 0.6f, hy + h * 0.02f + bob, w * 0.5f, hy + h * 0.04f + bob)
        }
        Env.WEST -> {
            drawCircle(Color(0xFFFFB36B).copy(alpha = 0.7f), h * 0.12f, Offset(w * 0.5f, hy))
            val rock = Color(0xFF5A1F10).copy(alpha = 0.9f)
            drawRect(rock, Offset(w * 0.05f, hy - h * 0.2f), Size(w * 0.18f, h * 0.2f))
            drawRect(rock, Offset(w * 0.08f, hy - h * 0.26f), Size(w * 0.1f, h * 0.06f))
            drawRect(rock, Offset(w * 0.72f, hy - h * 0.16f), Size(w * 0.2f, h * 0.16f))
            drawRect(Color(0xFF4A1A0C), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 3) {
                val x = w * (0.3f + k * 0.18f)
                val g = Color(0xFF0E3D1A)
                drawRect(g, Offset(x, hy - h * 0.15f), Size(w * 0.02f, h * 0.15f))
                drawRect(g, Offset(x - w * 0.02f, hy - h * 0.11f), Size(w * 0.02f, h * 0.02f))
                drawRect(g, Offset(x + w * 0.02f, hy - h * 0.09f), Size(w * 0.02f, h * 0.02f))
            }
            drawCircle(Color(0xFFCFA66B).copy(alpha = 0.8f), h * 0.025f, Offset(((t * 0.06f) % 1.2f - 0.1f) * w, hy + h * 0.05f), style = Stroke(3f))
        }
        Env.SEA -> {
            drawCircle(Color(0xFFEAF1FF).copy(alpha = 0.6f), h * 0.06f, Offset(w * 0.8f, h * 0.18f))
            val bob = sin(t * 1.4f) * h * 0.012f
            val ship = Color(0xFF020914)
            tri(ship, w * 0.3f, hy + bob, w * 0.7f, hy + bob, w * 0.64f, hy + h * 0.05f + bob)
            tri(ship, w * 0.3f, hy + bob, w * 0.36f, hy + h * 0.05f + bob, w * 0.64f, hy + h * 0.05f + bob)
            for (k in 0 until 3) {
                val mx = w * (0.4f + k * 0.1f)
                drawRect(ship, Offset(mx, hy - h * 0.2f + bob), Size(w * 0.008f, h * 0.2f))
                tri(Color(0xFFD9DEE8).copy(alpha = 0.7f), mx, hy - h * 0.19f + bob, mx + w * 0.08f, hy - h * 0.08f + bob, mx, hy - h * 0.05f + bob)
            }
            for (j in 0 until 6) {
                val y = hy + h * 0.04f * (j + 1)
                for (i in 0 until 30) {
                    val x0 = w * i / 30f
                    val x1 = w * (i + 1) / 30f
                    drawLine(Color(0xFF5FA8E0).copy(alpha = 0.25f), Offset(x0, y + sin(t * 1.5f + i * 0.6f + j) * 5f), Offset(x1, y + sin(t * 1.5f + (i + 1) * 0.6f + j) * 5f), 2f)
                }
            }
        }
        Env.WAR -> {
            for (k in 0 until 3) {
                val x = w * (0.2f + 0.3f * k)
                val ang = sin(t * 0.7f + k) * 0.5f
                drawLine(Color(0xFFFFF3C4).copy(alpha = 0.18f), Offset(x, h), Offset(x + sin(ang) * w * 0.5f, 0f), w * 0.05f)
            }
            for (k in 0 until 9) {
                val bh = h * (0.12f + 0.25f * hash(k + 5))
                drawRect(Color(0xFF0C0C0A).copy(alpha = 0.9f), Offset(w * k / 9f, hy - bh), Size(w / 9f * 0.9f, bh))
            }
            drawRect(Color(0xFF0C0C0A), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 8) drawCircle(Color(0xFF55524A).copy(alpha = 0.25f), h * (0.04f + 0.04f * hash(k)), Offset(w * hash(k + 20) + sin(t * 0.3f + k) * 12f, hy - ((t * 0.04f + hash(k)) % 1f) * h * 0.4f))
        }
        Env.COLD -> {
            val c = Offset(w * 0.5f, hy * 0.85f)
            for (r in 1..4) drawCircle(Color(0xFFFF4B5C).copy(alpha = 0.3f), h * 0.1f * r, c, style = Stroke(2f))
            val a = t * 1.2f
            drawLine(Color(0xFFFF6B7A).copy(alpha = 0.8f), c, Offset(c.x + cos(a) * h * 0.4f, c.y + sin(a) * h * 0.4f), 4f)
            for (k in -6..6) drawLine(Color(0xFF4B8BFF).copy(alpha = 0.18f), Offset(c.x + k * w * 0.08f, 0f), Offset(c.x + k * w * 0.08f, h), 1.5f)
            drawRect(Color(0xFF05060C).copy(alpha = 0.85f), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 4) drawRect(Color(0xFF05060C), Offset(w * (0.1f + k * 0.25f), hy - h * 0.2f), Size(w * 0.01f, h * 0.2f))
        }
        Env.CYBER -> {
            for (k in 0 until 12) {
                val bw = w / 12f
                val bh = h * (0.2f + 0.4f * hash(k))
                drawRect(Color(0xFF0A0214).copy(alpha = 0.95f), Offset(k * bw, hy - bh), Size(bw * 0.9f, bh))
                for (j in 0 until 6) if (hash(k * 50 + j + (t * 1.5f).toInt()) > 0.55f) {
                    drawRect(Color(0xFFFF2E93).copy(alpha = 0.6f), Offset(k * bw + bw * 0.15f, hy - bh + bh * (0.1f + j * 0.14f)), Size(bw * 0.2f, bh * 0.05f))
                }
            }
            drawRect(Color(0xFF05010A), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 40) {
                val p = (t * 1.2f + hash(k)) % 1f
                val x = w * hash(k + 70)
                drawLine(Color(0xFF7FE9FF).copy(alpha = 0.4f), Offset(x, p * h), Offset(x - 6f, p * h + h * 0.05f), 2f)
            }
        }
        Env.ASH -> {
            drawCircle(Color(0xFFFF5A2A).copy(alpha = 0.45f), h * 0.1f, Offset(w * 0.3f, h * 0.25f))
            for (k in 0 until 10) {
                val bh = h * (0.1f + 0.3f * hash(k + 11))
                val x = w * k / 10f
                tri(Color(0xFF1A0A05).copy(alpha = 0.92f), x, hy, x, hy - bh, x + w * 0.09f, hy - bh * 0.6f)
                drawRect(Color(0xFF1A0A05).copy(alpha = 0.92f), Offset(x, hy - bh * 0.6f), Size(w * 0.09f, bh * 0.6f))
            }
            drawRect(Color(0xFF120804), Offset(0f, hy), Size(w, h - hy))
            for (k in 0 until 30) {
                val p = (t * 0.1f + hash(k)) % 1f
                drawCircle(Color(0xFFE8D2C0).copy(alpha = 0.5f), 2.5f, Offset(w * hash(k + 3) + sin(t + k) * 14f, p * h))
            }
        }
        Env.FILM -> {
            val strip = Color(0xFF050505).copy(alpha = 0.9f)
            drawRect(strip, Offset(0f, 0f), Size(w * 0.06f, h))
            drawRect(strip, Offset(w * 0.94f, 0f), Size(w * 0.06f, h))
            for (k in 0 until 14) {
                val y = ((k / 14f + t * 0.05f) % 1f) * h
                drawRect(Color(0xFFE0E0E0).copy(alpha = 0.7f), Offset(w * 0.015f, y), Size(w * 0.03f, h * 0.035f))
                drawRect(Color(0xFFE0E0E0).copy(alpha = 0.7f), Offset(w * 0.955f, y), Size(w * 0.03f, h * 0.035f))
            }
            for (k in 0 until 6) drawLine(Color.White.copy(alpha = 0.14f), Offset(w * hash(k + (t * 8f).toInt()), 0f), Offset(w * hash(k + (t * 8f).toInt()), h), 1.5f)
            drawRect(Color.White.copy(alpha = 0.02f + 0.03f * hash((t * 14f).toInt())), Offset(0f, 0f), Size(w, h))
        }
    }
}

/** Opening sequence: the gate opens, eras flash inside, a corridor of cinema walls, arrival in the film world. */
@Composable
fun TimeGateIntro(st: TimeGateState, arabic: Boolean, posters: List<WorkCard>, onSkip: () -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } },
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val worlds = TimeWorlds.all

        Canvas(Modifier.fillMaxSize()) {
            val t = st.introMs.toFloat()
            val cx = size.width / 2f
            val cy = size.height / 2f
            if (t < 2600f) {
                val p = 1f - (1f - t / 2600f) * (1f - t / 2600f)
                val r = size.minDimension * 0.48f * p
                val idx = (t / 420f).toInt() % worlds.size
                val path = Path()
                path.addOval(Rect(Offset(cx, cy), r))
                clipPath(path) {
                    drawRect(Brush.verticalGradient(listOf(Color(worlds[idx].colors.first), Color(worlds[idx].colors.second))))
                    drawEnvironment(worlds[idx].env, t / 1000f)
                }
                drawCircle(Color(0xFFFFC107), r, Offset(cx, cy), style = Stroke(8f + 6f * sin(t / 160f)))
                for (k in 0 until 24) {
                    val a = k * (2f * PI.toFloat() / 24f) + t / 900f
                    drawCircle(Color(0xFFFFE082).copy(alpha = 0.6f), 4f, Offset(cx + cos(a) * (r + 18f), cy + sin(a) * (r + 18f)))
                }
            } else if (t < 5400f) {
                val u = (t - 2600f) / 2800f
                drawRect(Brush.radialGradient(listOf(Color(0xFF2A1B5E), Color.Black), Offset(cx, cy), size.maxDimension * 0.7f))
                for (k in 0 until 16) {
                    val z = (u * 1.6f + k / 16f) % 1f
                    val s = z * z * 1.6f
                    val fw = size.width * s
                    val fh = size.height * s
                    drawRect(
                        Color(worlds[k % worlds.size].colors.second).copy(alpha = 0.2f + 0.8f * z),
                        Offset(cx - fw / 2f, cy - fh / 2f), Size(fw, fh), style = Stroke(3f + 10f * z),
                    )
                }
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - u)), size.minDimension * 0.06f, Offset(cx, cy))
            } else {
                val a = ((t - 5400f) / 1600f).coerceIn(0f, 1f)
                drawRect(Brush.radialGradient(listOf(Color(0xFF2A1B5E), Color.Black), Offset(cx, cy), size.maxDimension * 0.7f))
                drawRect(Color.White.copy(alpha = 0.9f * (1f - a)))
            }
        }

        // Cinema walls: real posters fly past during the corridor.
        posters.take(8).forEachIndexed { i, card ->
            Box(
                Modifier.align(Alignment.Center).size(110.dp, 165.dp).graphicsLayer {
                    val tt = st.introMs.toFloat()
                    if (tt < 2600f || tt > 5400f) {
                        alpha = 0f
                    } else {
                        val p = (((tt - 2600f) / 1400f) + i * 0.125f) % 1f
                        val ang = i * (2f * PI.toFloat() / 8f)
                        alpha = sin(PI.toFloat() * p)
                        val s = 0.15f + 2.6f * p * p
                        scaleX = s
                        scaleY = s
                        translationX = cos(ang) * p * wPx * 0.6f
                        translationY = sin(ang) * p * hPx * 0.5f
                        rotationZ = (i % 3 - 1) * 8f * p
                    }
                }.clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            ) {
                PosterImage(Img.poster(card.posterPath), card.title, Modifier.fillMaxSize())
            }
        }

        Text(
            text = when {
                st.introMs < 2600L -> tr("بوابة الزمن", "The Time Gate")
                st.introMs < 5400L -> tr("اندفاعٌ عبر الزمن...", "Rushing through time...")
                else -> tr("عالم الأفلام", "The world of cinema")
            },
            color = Color.White, style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center).padding(top = 260.dp).graphicsLayer {
                val tt = st.introMs.toFloat()
                val local = when {
                    tt < 2600f -> tt / 2600f
                    tt < 5400f -> (tt - 2600f) / 2800f
                    else -> (tt - 5400f) / 1600f
                }
                alpha = sin(PI.toFloat() * local.coerceIn(0f, 1f))
            },
        )
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Text(tr("تخطي المقدمة ⏭", "Skip intro ⏭"), color = Color.White)
        }
    }
}

/** counts(i): >=0 titles, -1 not scanned, -2 loading, -3 failed. */
private fun dotOffset(i: Int, n: Int, yaw: Float, w: Float, h: Float): Offset {
    val a = Math.toRadians((i * 360.0 / n) - yaw)
    val rx = w * 0.40f
    val ry = rx * 0.45f
    return Offset(w / 2f + (sin(a) * rx).toFloat(), h / 2f + (-cos(a) * ry).toFloat())
}

/** Time radar: stations around you (tilted ring that turns with your heading); dot size = how many titles TMDB gave. */
@Composable
fun TimeRadar(st: TimeGateState, arabic: Boolean, counts: (Int) -> Int, onPick: (Int) -> Unit) {
    val worlds = TimeWorlds.all
    val n = worlds.size
    Box(
        Modifier.size(300.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(n) {
                detectTapGestures { tap ->
                    var best = -1
                    var bestD = Float.MAX_VALUE
                    for (i in 0 until n) {
                        val d = (dotOffset(i, n, st.yaw, size.width.toFloat(), size.height.toFloat()) - tap).getDistance()
                        if (d < bestD) {
                            bestD = d
                            best = i
                        }
                    }
                    if (best >= 0 && bestD < 70f) onPick(best)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (r in 1..3) drawOval(Color(0xFF7CFFB2).copy(alpha = 0.25f), Offset(w / 2f - w * 0.4f * r / 3f, h / 2f - w * 0.18f * r / 3f), Size(w * 0.8f * r / 3f, w * 0.36f * r / 3f), style = Stroke(2f))
            val sweep = st.timeMs / 700f
            drawLine(Color(0xFF7CFFB2).copy(alpha = 0.7f), Offset(w / 2f, h / 2f), Offset(w / 2f + sin(sweep) * w * 0.4f, h / 2f - cos(sweep) * w * 0.18f), 3f)
            // your viewing direction
            tri(Color.White.copy(alpha = 0.8f), w / 2f - 10f, h * 0.2f, w / 2f + 10f, h * 0.2f, w / 2f, h * 0.2f - 16f)
            for (i in 0 until n) {
                val c = counts(i)
                val p = dotOffset(i, n, st.yaw, w, h)
                val col = when {
                    i == st.index -> Color.White
                    c >= com.tvseries.Movies.core.TimeWorlds.ENOUGH -> Color(0xFF4CFF8F)
                    c in 1 until com.tvseries.Movies.core.TimeWorlds.ENOUGH -> Color(0xFFFFD54F)
                    c == 0 -> Color(0xFFFF5252)
                    c == -2 -> Color(0xFF64B5F6)
                    c == -3 -> Color(0xFFFF5252)
                    else -> Color(0xFF8A8A8A)
                }
                val r = 7f + (if (c > 0) minOf(c, 30) / 30f * 10f else 0f)
                drawCircle(col.copy(alpha = 0.9f), r, p)
                if (i == st.index) drawCircle(Color.White, r + 6f, p, style = Stroke(2f))
            }
        }
        Text(
            worlds[st.index].name(arabic), color = Color.White, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Orbit radius (fraction) of the i-th screen around the black hole. */
internal fun orbitR(i: Int): Float = 0.30f + 0.32f * hash(i + 1)

/** Orbit angle: closer screens go faster (Kepler-like); [tau] is the dilated "proper time". */
internal fun orbitAngle(i: Int, tau: Float): Float {
    val r = orbitR(i)
    val omega = 0.9f / (r * sqrt(r))
    return 2f * PI.toFloat() * hash(i + 7) + omega * tau
}

/** The "time hole": lensed starfield, accretion disk, and small squeezed worlds orbiting inside each other. */
fun DrawScope.drawBlackHole(t: Float, dilation: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h * 0.44f
    val base = minOf(w, h)
    val r = base * 0.12f
    drawRect(Color(0xFF020108))
    // starfield streaming toward the hole; the flow speeds up and slows down with time dilation
    for (k in 0 until 90) {
        val ang = hash(k) * 2f * PI.toFloat()
        val p = (t * 0.08f * dilation + hash(k + 200)) % 1f
        val dist = 1f - p
        val rr = r * 1.4f + dist * dist * base * 0.9f
        val x = cx + cos(ang) * rr * 1.3f
        val y = cy + sin(ang) * rr * 0.55f
        val trail = 8f + 40f * (1f - dist)
        drawLine(Color.White.copy(alpha = 0.3f + 0.5f * (1f - dist)), Offset(x, y), Offset(x - cos(ang) * trail * 1.3f, y - sin(ang) * trail * 0.55f), 2f)
    }
    // worlds squeezed into small bubbles that overlap and orbit the hole
    val worlds = TimeWorlds.all
    for (b in 0 until 3) {
        val a = t * 0.5f * dilation + b * 2.1f
        val bx = cx + cos(a) * r * 3.2f
        val by = cy + sin(a) * r * 1.3f
        val br = r * (1.3f - 0.25f * b)
        val wd = worlds[(b * 4 + (t / 6f).toInt()) % worlds.size]
        val path = Path()
        path.addOval(Rect(Offset(bx, by), br))
        clipPath(path) {
            withTransform({
                translate(bx - w * 0.11f, by - h * 0.15f)
                scale(0.22f, 0.3f, Offset.Zero)
            }) {
                drawRect(Brush.verticalGradient(listOf(Color(wd.colors.first), Color(wd.colors.second))))
                drawEnvironment(wd.env, t)
            }
        }
        drawCircle(Color(0xFFB388FF).copy(alpha = 0.7f), br, Offset(bx, by), style = Stroke(3f))
    }
    // lensing glow, accretion disk, event horizon
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFA040).copy(alpha = 0.35f), Color.Transparent), Offset(cx, cy), r * 3.5f), r * 3.5f, Offset(cx, cy))
    for (i in 0 until 14) {
        val rr = r * (1.35f + i * 0.17f)
        val col = if (i < 5) Color(0xFFFFF0B0) else if (i < 10) Color(0xFFFFA040) else Color(0xFFB04A20)
        drawOval(col.copy(alpha = 0.55f - i * 0.025f), Offset(cx - rr * 1.6f, cy - rr * 0.38f), Size(rr * 3.2f, rr * 0.76f), style = Stroke(3f + (14 - i) * 0.4f))
    }
    val sp = t * 1.5f * dilation
    drawCircle(Color.White, 9f, Offset(cx + cos(sp) * r * 2.2f * 1.6f, cy + sin(sp) * r * 2.2f * 0.38f))
    drawCircle(Color.Black, r, Offset(cx, cy))
    drawCircle(Color(0xFFFFA040).copy(alpha = 0.6f), r * 1.04f, Offset(cx, cy), style = Stroke(4f))
}

/**
 * The screen that swallows you: the poster grows, its frame becomes a portal, you cross into an ORIGINAL scenery
 * inspired by the title's genres, then its info and the option to open its page appear. Short and skippable.
 */
@Composable
fun SwallowOverlay(
    st: TimeGateState,
    card: WorkCard,
    details: DetailsDto?,
    arabic: Boolean,
    onOpenPage: () -> Unit,
    onWatchLater: () -> Unit,
    onClose: () -> Unit,
) {
    val genreIds = details?.genres.orEmpty().map { it.id }
    val env = remember(genreIds) { WorkScene.envFor(genreIds) }
    val colors = remember(env) { WorkScene.colorsFor(env) }
    val showInfo by remember { derivedStateOf { st.swallowMs >= 2900L } }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer { alpha = ((st.swallowMs - 1100f) / 900f).coerceIn(0f, 1f) },
        ) {
            drawRect(Brush.verticalGradient(listOf(Color(colors.first), Color(colors.second))))
            drawEnvironment(env, st.timeMs / 1000f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)), startY = size.height * 0.35f, endY = size.height))
        }
        // portal frame and vortex while crossing
        Canvas(Modifier.fillMaxSize()) {
            val t = st.swallowMs.toFloat()
            if (t < 2300f) {
                val p = (t / 1100f).coerceIn(0f, 1f)
                val fade = (1f - (t - 1100f) / 1200f).coerceIn(0f, 1f)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rr = size.minDimension * (0.12f + 0.6f * p * p)
                for (k in 0 until 3) drawCircle(Color(0xFF7CF9FF).copy(alpha = 0.85f * fade), rr * (1f + 0.12f * k), Offset(cx, cy), style = Stroke(4f + k * 2f))
                for (k in 0 until 40) {
                    val a = k * (2f * PI.toFloat() / 40f) + t / 500f
                    val r0 = rr * 0.45f
                    val r1 = rr * (1.0f + 0.6f * hash(k))
                    drawLine(Color(0xFFB388FF).copy(alpha = 0.6f * fade), Offset(cx + cos(a) * r0, cy + sin(a) * r0), Offset(cx + cos(a + 0.35f) * r1, cy + sin(a + 0.35f) * r1), 2.5f)
                }
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(150.dp, 225.dp).graphicsLayer {
                val t = st.swallowMs.toFloat()
                val p = (t / 1100f).coerceIn(0f, 1f)
                val s = 1f + 5.5f * p * p
                scaleX = s
                scaleY = s
                alpha = if (t < 1100f) 1f - 0.5f * p else (0.5f - (t - 1100f) / 1400f).coerceIn(0f, 0.5f)
            }.clip(RoundedCornerShape(10.dp)),
        ) {
            PosterImage(Img.poster(card.posterPath), card.title, Modifier.fillMaxSize())
        }

        if (showInfo) {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(card.title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                val meta = listOfNotNull(
                    card.year,
                    card.rating?.let { "★ " + fmt1(it) },
                    details?.genres.orEmpty().joinToString(if (arabic) "، " else ", ") { it.name }.ifBlank { null },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                val story = details?.overview?.takeIf { it.isNotBlank() }
                Text(
                    story ?: tr("لا تتوفر قصة لهذا العمل في TMDB.", "No story available for this title on TMDB."),
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 6,
                )
                Text(
                    tr("المشهد من تصميم التطبيق، مستوحى من أجواء نوع العمل، وليس من مشاهد الفيلم نفسه.", "This scenery is the app's own design, inspired by the title's genre mood, not scenes from the film."),
                    color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenPage) { Text(tr("افتح صفحة العمل", "Open its page")) }
                    OutlinedButton(onClick = onWatchLater) { Text(tr("شاهد لاحقًا", "Watch later"), color = Color.White) }
                    OutlinedButton(onClick = onClose) { Text(tr("عودة", "Back"), color = Color.White) }
                }
            }
        } else {
            TextButton(onClick = { st.swallowMs = 2900L }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Text(tr("تخطي ⏭", "Skip ⏭"), color = Color.White)
            }
        }
    }
}
