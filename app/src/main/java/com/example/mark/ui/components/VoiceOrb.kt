package com.example.mark.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

private object PhoneOrb {
    // The phone GPU is ~10x the watch's, so this is the full-fat version:
    // more strands, more points, glow always on.
    const val POINTS = 36
    const val STRANDS = 16
    const val SIN_SIZE = 512
    const val SIN_MASK = SIN_SIZE - 1
    const val GLOW_STRANDS = 4

    val ColorStart = Color(0xFFFFFFFF)
    val ColorMid = Color(0xFFC9CDD4)
    val ColorEnd = Color(0xFF6E7480)
    val Ember = Color(0xFFFF6A1A)
}

private class Strand(
    val lobes: Int,
    val phaseOffset: Float,
    val baseAlpha: Float,
    val speedMult: Float,
    val radiusOffsetPx: Float,
    val color: Color,
    val isEmber: Boolean,
    val stroke: Stroke,
    val glowStroke: Stroke,
    val dispScale: Float,
    val spreadFactor: Float
)

/**
 * The Moonstone voice orb, phone edition. Signature kept compatible with the
 * previous component: state + raw amplitude + click + size.
 *
 * Frame-loop rules are the same as the watch orb: nothing allocated per frame,
 * animated values read only inside the draw scope, sin from a lookup table.
 */
@Composable
fun VoiceOrb(
    state: OrbState,
    amplitude: Float,
    onClick: () -> Unit,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Smooth the incoming amplitude: fast attack, slow decay — peaks register
    // instantly and fall away softly, like a VU meter.
    val amp = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(amplitude) {
        val v = amplitude.coerceIn(0f, 1f)
        val prev = amp.floatValue
        amp.floatValue = if (v > prev) prev * 0.4f + v * 0.6f else prev * 0.82f + v * 0.18f
    }

    val masterPhase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        var last = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { time ->
                if (last != 0L) {
                    val a = amp.floatValue
                    val speed = when (state) {
                        OrbState.IDLE -> 0.0007f
                        OrbState.LISTENING -> 0.0014f + a * 0.009f
                        OrbState.SPEAKING -> -0.0014f
                        OrbState.THINKING -> 0.0011f
                    }
                    masterPhase.floatValue += speed * (time - last).coerceAtMost(50L)
                }
                last = time
            }
        }
    }

    val sinTable = remember {
        FloatArray(PhoneOrb.SIN_SIZE) { sin(it * 2f * PI.toFloat() / PhoneOrb.SIN_SIZE) }
    }
    val sinScale = remember { PhoneOrb.SIN_SIZE / (2f * PI.toFloat()) }
    val angles = remember { FloatArray(PhoneOrb.POINTS) { it * 2f * PI.toFloat() / PhoneOrb.POINTS } }
    val cosA = remember { FloatArray(PhoneOrb.POINTS) { cos(angles[it]) } }
    val sinA = remember { FloatArray(PhoneOrb.POINTS) { sin(angles[it]) } }
    val path = remember { Path() }
    val xs = remember { FloatArray(PhoneOrb.POINTS) }
    val ys = remember { FloatArray(PhoneOrb.POINTS) }

    val strands = remember(density) {
        val lobes = intArrayOf(9, 11, 13, 10, 14, 8, 15, 12, 16, 7, 17, 13, 11, 9, 15, 10)
        val widths = floatArrayOf(1.8f, 1.5f, 1.3f, 1.2f, 1.1f, 1.0f, 0.9f, 0.9f, 0.8f, 0.8f, 0.7f, 0.7f, 0.6f, 0.6f, 0.5f, 0.5f)
        val speeds = floatArrayOf(1.0f, 1.15f, 0.85f, 1.3f, 0.7f, 1.45f, 0.6f, 1.2f, 0.9f, 1.6f, 0.55f, 1.35f, 1.05f, 0.75f, 1.5f, 0.65f)
        val offsets = floatArrayOf(0f, -2f, 2f, -4f, 4f, -6f, 6f, -8f, 8f, -10f, 10f, 12f, -12f, 14f, -14f, 16f)
        val alphas = floatArrayOf(0.95f, 0.86f, 0.78f, 0.70f, 0.64f, 0.58f, 0.52f, 0.47f, 0.42f, 0.38f, 0.34f, 0.31f, 0.28f, 0.26f, 0.24f, 0.22f)

        Array(PhoneOrb.STRANDS) { i ->
            val f = i / (PhoneOrb.STRANDS - 1).toFloat()
            val silver = if (f < 0.5f) lerp(PhoneOrb.ColorStart, PhoneOrb.ColorMid, f * 2f)
            else lerp(PhoneOrb.ColorMid, PhoneOrb.ColorEnd, (f - 0.5f) * 2f)
            val ember = (i == 9 || i == 12 || i == 15)
            val wPx = with(density) { widths[i].dp.toPx() }
            Strand(
                lobes = lobes[i],
                phaseOffset = i * 0.62f,
                baseAlpha = alphas[i],
                speedMult = speeds[i],
                radiusOffsetPx = with(density) { offsets[i].dp.toPx() },
                color = if (ember) PhoneOrb.Ember else silver,
                isEmber = ember,
                stroke = Stroke(wPx, cap = StrokeCap.Round),
                glowStroke = Stroke(wPx * 5f, cap = StrokeCap.Round),
                dispScale = 1.0f - i * 0.035f,
                spreadFactor = i / PhoneOrb.STRANDS.toFloat()
            )
        }
    }

    val onePx = remember(density) { with(density) { 1.dp.toPx() } }

    val radiusScaleAnim = animateFloatAsState(
        targetValue = if (state == OrbState.SPEAKING) 0.94f else 1f,
        animationSpec = tween(350), label = "r"
    )
    val waveAnim = animateFloatAsState(
        targetValue = when (state) {
            OrbState.IDLE -> 3.5f
            OrbState.THINKING -> 9f
            OrbState.SPEAKING -> 6f
            else -> 0f
        },
        animationSpec = tween(350), label = "w"
    )
    val pulse = rememberInfiniteTransition(label = "p").animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "pl"
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val p = masterPhase.floatValue
            val a = amp.floatValue

            val cx = this.size.width * 0.5f
            val cy = this.size.height * 0.5f
            val baseRadius = this.size.minDimension * 0.40f

            val listening = state == OrbState.LISTENING
            val breathIdx = ((p * 0.35f) * sinScale).toInt()
            val breath = if (state == OrbState.IDLE) 1f + sinTable[breathIdx and PhoneOrb.SIN_MASK] * 0.018f else 1f
            val swell = if (listening) 1f + a * 0.10f else 1f
            val rScale = radiusScaleAnim.value * breath * swell * (if (state == OrbState.THINKING) pulse.value else 1f)

            val waveDp = if (listening) (2.5f + a * a * 30f) else waveAnim.value
            val wavePx = waveDp * onePx
            val voiceGlow = if (listening) a * 0.45f else 0.05f

            val lastI = PhoneOrb.POINTS - 1

            for (index in 0 until PhoneOrb.STRANDS) {
                val s = strands[index]
                val sp = p * s.speedMult + s.phaseOffset
                val spread = 1f + s.spreadFactor * a * 1.5f
                val disp = wavePx * s.dispScale * spread
                val radius = (baseRadius + s.radiusOffsetPx) * rScale

                for (i in 0 until PhoneOrb.POINTS) {
                    val idx = ((angles[i] * s.lobes + sp) * sinScale).toInt()
                    val r = radius + sinTable[idx and PhoneOrb.SIN_MASK] * disp
                    xs[i] = cx + r * cosA[i]
                    ys[i] = cy + r * sinA[i]
                }

                path.rewind()
                var mx = (xs[lastI] + xs[0]) * 0.5f
                var my = (ys[lastI] + ys[0]) * 0.5f
                path.moveTo(mx, my)
                for (i in 0 until PhoneOrb.POINTS) {
                    val ni = if (i == lastI) 0 else i + 1
                    mx = (xs[i] + xs[ni]) * 0.5f
                    my = (ys[i] + ys[ni]) * 0.5f
                    path.quadraticBezierTo(xs[i], ys[i], mx, my)
                }
                path.close()

                if (s.isEmber || index < PhoneOrb.GLOW_STRANDS) {
                    val base = if (s.isEmber) 0.10f else 0.07f
                    val boost = if (s.isEmber) 1.6f else 0.8f
                    var ga = base + voiceGlow * boost
                    if (ga > 0.55f) ga = 0.55f
                    drawPath(path, s.color.copy(alpha = ga), style = s.glowStroke)
                }

                var ma = s.baseAlpha + voiceGlow * 0.4f
                if (ma > 1f) ma = 1f
                drawPath(path, s.color.copy(alpha = ma), style = s.stroke)
            }
        }
    }
}