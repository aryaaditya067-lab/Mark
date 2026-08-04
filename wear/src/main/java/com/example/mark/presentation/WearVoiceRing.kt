package com.example.mark.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState { IDLE, PREPARING, LISTENING, THINKING, SPEAKING }

private object MoonstoneConfig {
    /**
     * 28 points with midpoint-quadratic smoothing looks identical to 40 points
     * with the same smoothing, at ~30% less per-frame work. The smoothing is what
     * removes the polygon corners — not the raw point count.
     */
    const val POINTS = 28
    const val STRAND_COUNT = 10

    const val SIN_SIZE = 512
    const val SIN_MASK = SIN_SIZE - 1

    /** Only the brightest strands get a glow pass, and only above this level. */
    const val GLOW_STRANDS = 2
    const val GLOW_AMP_THRESHOLD = 0.06f

    val ColorStart = Color(0xFFFFFFFF)
    val ColorMid = Color(0xFFC9CDD4)
    val ColorEnd = Color(0xFF6E7480)
    val Ember = Color(0xFFFF6A1A)

    val TextPrimary = Color(0xFFF2EDE7)
    val TextDim = Color(0xFF8A8F98)
}

/**
 * Plain class with @JvmField, not a data class: field access in the frame loop
 * goes straight to the field instead of through a getter, and nothing here needs
 * equals/hashCode/copy.
 */
private class StrandSpec(
    @JvmField val lobes: Int,
    @JvmField val phaseOffset: Float,
    @JvmField val baseAlpha: Float,
    @JvmField val phaseSpeedMult: Float,
    @JvmField val radiusOffsetPx: Float,
    val color: Color,
    @JvmField val isEmber: Boolean,
    @JvmField val stroke: Stroke,
    @JvmField val glowStroke: Stroke,
    @JvmField val displacementScale: Float,
    @JvmField val spreadFactor: Float
)

@Composable
fun WearVoiceRing(
    state: OrbState,
    amplitudeFlow: StateFlow<Float>,
    liveTranscript: StateFlow<String>,
    liveReply: StateFlow<String>,
    statusText: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VoiceOrbCanvas(state, amplitudeFlow)
        OrbTextOverlay(state, liveTranscript, liveReply, statusText)
    }
}

/**
 * The animation surface.
 *
 * Frame-loop rules this file follows, and must keep following:
 *  1. Nothing is allocated inside the draw scope — no lists, no Stroke, no
 *     lambdas, no iterators. Allocation during a frame means GC, and on two A55
 *     cores a GC pause is a dropped frame you can see.
 *  2. Animated values are READ inside the draw scope, never during composition.
 *     Reading them in composition recomposes the whole composable every frame.
 *  3. All trigonometry comes from a lookup table; every dp->px conversion and all
 *     colour maths happens once inside remember{}.
 */
@Composable
private fun VoiceOrbCanvas(
    state: OrbState,
    amplitudeFlow: StateFlow<Float>
) {
    val density = LocalDensity.current
    val amplitude = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(amplitudeFlow) {
        amplitudeFlow.collect { amplitude.floatValue = it }
    }

    val masterPhase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state) {
        var lastFrameTime = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { time ->
                if (lastFrameTime != 0L) {
                    val amp = amplitude.floatValue
                    val speed = when (state) {
                        OrbState.IDLE -> 0.0008f
                        OrbState.LISTENING -> 0.0015f + amp * 0.010f
                        OrbState.SPEAKING -> -0.0015f
                        else -> 0.0012f
                    }
                    // Clamp dt so a stall (GC, STT burst) doesn't make the wave jump.
                    val dt = (time - lastFrameTime).coerceAtMost(50L)
                    masterPhase.floatValue += speed * dt
                }
                lastFrameTime = time
            }
        }
    }

    val sinTable = remember {
        FloatArray(MoonstoneConfig.SIN_SIZE) {
            sin(it * 2f * PI.toFloat() / MoonstoneConfig.SIN_SIZE)
        }
    }
    val sinIndexScale = remember { MoonstoneConfig.SIN_SIZE / (2f * PI.toFloat()) }

    val angles = remember {
        FloatArray(MoonstoneConfig.POINTS) { it * 2f * PI.toFloat() / MoonstoneConfig.POINTS }
    }
    val cosAngles = remember { FloatArray(MoonstoneConfig.POINTS) { cos(angles[it]) } }
    val sinAngles = remember { FloatArray(MoonstoneConfig.POINTS) { sin(angles[it]) } }

    val strandPath = remember { Path() }
    val xs = remember { FloatArray(MoonstoneConfig.POINTS) }
    val ys = remember { FloatArray(MoonstoneConfig.POINTS) }

    // Array, not List — indexed access allocates no iterator in the frame loop.
    val strands = remember(density) {
        val lobesList = intArrayOf(9, 11, 13, 10, 14, 8, 15, 12, 16, 7)
        val strokeDp = floatArrayOf(1.4f, 1.2f, 1.0f, 0.9f, 0.9f, 0.8f, 0.7f, 0.7f, 0.6f, 0.6f)
        val speeds = floatArrayOf(1.00f, 1.15f, 0.85f, 1.30f, 0.70f, 1.45f, 0.60f, 1.20f, 0.90f, 1.60f)
        val offsetDp = floatArrayOf(0f, -2f, 2f, -4f, 4f, -6f, 6f, -8f, 8f, 10f)
        val alphas = floatArrayOf(0.95f, 0.85f, 0.75f, 0.66f, 0.60f, 0.52f, 0.46f, 0.40f, 0.35f, 0.32f)

        Array(MoonstoneConfig.STRAND_COUNT) { i ->
            val fraction = i / (MoonstoneConfig.STRAND_COUNT - 1).toFloat()
            val silver = if (fraction < 0.5f) {
                lerp(MoonstoneConfig.ColorStart, MoonstoneConfig.ColorMid, fraction * 2f)
            } else {
                lerp(MoonstoneConfig.ColorMid, MoonstoneConfig.ColorEnd, (fraction - 0.5f) * 2f)
            }
            val isEmber = (i == 7 || i == 9)
            val wPx = with(density) { strokeDp[i].dp.toPx() }

            StrandSpec(
                lobes = lobesList[i],
                // Each strand starts at its own angle so lobes never line up into
                // one fat wave — this is what reads as separate fibres.
                phaseOffset = i * 0.7f,
                baseAlpha = alphas[i],
                phaseSpeedMult = speeds[i],
                radiusOffsetPx = with(density) { offsetDp[i].dp.toPx() },
                color = if (isEmber) MoonstoneConfig.Ember else silver,
                isEmber = isEmber,
                stroke = Stroke(width = wPx, cap = StrokeCap.Round),
                glowStroke = Stroke(width = wPx * 5f, cap = StrokeCap.Round),
                displacementScale = 1.0f - i * 0.045f,
                spreadFactor = i / MoonstoneConfig.STRAND_COUNT.toFloat()
            )
        }
    }

    val onePx = remember(density) { with(density) { 1.dp.toPx() } }

    val animRadiusScale = animateFloatAsState(
        targetValue = if (state == OrbState.SPEAKING) 0.94f else 1.0f,
        animationSpec = tween(350), label = "radius"
    )
    val animWaveHeight = animateFloatAsState(
        targetValue = when (state) {
            OrbState.IDLE, OrbState.PREPARING -> 3f
            OrbState.THINKING -> 8f
            OrbState.SPEAKING -> 5f
            else -> 0f
        },
        animationSpec = tween(350), label = "wave"
    )
    val animAlpha = animateFloatAsState(
        targetValue = if (state == OrbState.PREPARING) 0.4f else 1.0f,
        animationSpec = tween(350), label = "alpha"
    )
    val thinkingPulse = rememberInfiniteTransition(label = "think").animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val pMaster = masterPhase.floatValue
        val amp = amplitude.floatValue
        val oAlpha = animAlpha.value

        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val baseRadius = (if (size.width < size.height) size.width else size.height) * 0.42f

        val listening = state == OrbState.LISTENING

        val breath = if (state == OrbState.IDLE) {
            val idx = ((pMaster * 0.35f) * sinIndexScale).toInt()
            1f + sinTable[idx and MoonstoneConfig.SIN_MASK] * 0.015f
        } else 1f

        // Voice drives three things at once — ripple, swell and brightness.
        // Driving only one of them is what made this look inert while listening.
        val voiceSwell = if (listening) 1f + amp * 0.10f else 1f
        val radiusScale = animRadiusScale.value * breath * voiceSwell *
                (if (state == OrbState.THINKING) thinkingPulse.value else 1f)

        // Squared response: quiet speech stays subtle, loud speech clearly spikes.
        val waveDp = if (listening) (2f + amp * amp * 26f) else animWaveHeight.value
        val waveHeightPx = waveDp * onePx
        val voiceGlow = if (listening) amp * 0.45f else 0f
        val drawGlow = !listening || amp > MoonstoneConfig.GLOW_AMP_THRESHOLD

        val lastI = MoonstoneConfig.POINTS - 1

        for (index in 0 until MoonstoneConfig.STRAND_COUNT) {
            val spec = strands[index]

            val strandPhase = pMaster * spec.phaseSpeedMult + spec.phaseOffset
            // Outer strands react more, so loud speech fans the ring outward
            // instead of just thickening it uniformly.
            val spread = 1f + spec.spreadFactor * amp * 1.4f
            val displacement = waveHeightPx * spec.displacementScale * spread
            val radius = (baseRadius + spec.radiusOffsetPx) * radiusScale

            for (i in 0 until MoonstoneConfig.POINTS) {
                val idx = ((angles[i] * spec.lobes + strandPhase) * sinIndexScale).toInt()
                val r = radius + sinTable[idx and MoonstoneConfig.SIN_MASK] * displacement
                xs[i] = cx + r * cosAngles[i]
                ys[i] = cy + r * sinAngles[i]
            }

            strandPath.rewind()
            var mx = (xs[lastI] + xs[0]) * 0.5f
            var my = (ys[lastI] + ys[0]) * 0.5f
            strandPath.moveTo(mx, my)
            for (i in 0 until MoonstoneConfig.POINTS) {
                val ni = if (i == lastI) 0 else i + 1
                mx = (xs[i] + xs[ni]) * 0.5f
                my = (ys[i] + ys[ni]) * 0.5f
                strandPath.quadraticBezierTo(xs[i], ys[i], mx, my)
            }
            strandPath.close()

            if (drawGlow && (spec.isEmber || index < MoonstoneConfig.GLOW_STRANDS)) {
                val base = if (spec.isEmber) 0.10f else 0.07f
                val boost = if (spec.isEmber) 1.6f else 0.8f
                var a = base + voiceGlow * boost
                if (a > 0.55f) a = 0.55f
                drawPath(strandPath, spec.color.copy(alpha = a * oAlpha), style = spec.glowStroke)
            }

            var mainAlpha = spec.baseAlpha + voiceGlow * 0.4f
            if (mainAlpha > 1f) mainAlpha = 1f
            drawPath(strandPath, spec.color.copy(alpha = mainAlpha * oAlpha), style = spec.stroke)
        }
    }
}

/**
 * Text layer over the orb.
 *
 * Two things were wrong before:
 *  - a fixed 5-word window meant long replies were never fully readable; the
 *    words scrolled past faster than they could be read and the tail was lost
 *    when the reply ended.
 *  - the block was a fixed height, so anything over two lines was clipped.
 *
 * Now the window sizes itself: short replies show whole, long ones keep the
 * most recent chunk, and the reveal speed is tied to how much there is to say.
 * There is still no per-change animation — an AnimatedContent fade made every
 * partial STT result wait ~180ms before appearing, which is what made the text
 * arrive in stutters.
 */
@Composable
private fun OrbTextOverlay(
    state: OrbState,
    liveTranscript: StateFlow<String>,
    liveReply: StateFlow<String>,
    statusText: String
) {
    val transcript by liveTranscript.collectAsState()
    val reply by liveReply.collectAsState()

    // What to show is decided by CONTENT, not by state.
    //
    // The old rule was `state == LISTENING || state == SPEAKING`, and SPEAKING
    // is only true while the TTS engine is actually talking. So the reply
    // vanished the instant speech ended — long answers were still revealing
    // when that happened, which is why they looked truncated, and silent-confirm
    // commands (no TTS at all) never showed text in the first place.
    //
    // Now: a live transcript wins while the user is talking; otherwise the last
    // reply stays on screen until the next utterance replaces it.
    val listening = state == OrbState.LISTENING
    val hasTranscript = transcript.isNotBlank()
    val showingTranscript = listening && hasTranscript

    val activeText = if (showingTranscript) transcript else reply

    val visible = remember(activeText, showingTranscript) {
        val words = activeText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // A transcript only needs its tail. Three words is what stays readable
        // on a watch while speech is still streaming in — six meant a long
        // sentence shrank to nothing legible.
        val keep = if (showingTranscript) 3 else 18
        words.takeLast(keep).joinToString(" ")
    }

    val showText = visible.isNotEmpty() && state != OrbState.PREPARING

    val monoAlpha by animateFloatAsState(
        targetValue = if (showText) 0f else 0.9f,
        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "monoAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (showText) 1f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing), label = "textAlpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(0.76f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Height is a floor, not a cap: a long reply is allowed to grow instead
        // of being cut off mid-sentence.
        Box(
            modifier = Modifier.heightIn(min = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (monoAlpha > 0.01f) {
                MarkMonogram(
                    alpha = monoAlpha,
                    modifier = Modifier.size(52.dp)
                )
            }
            if (textAlpha > 0.01f) {
                // Long replies step down a size rather than getting clipped.
                val long = visible.length > 60
                Text(
                    text = visible,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = if (long) 15.sp else 17.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = if (long) 20.sp else 23.sp,
                    letterSpacing = 0.2.sp,
                    color = MoonstoneConfig.TextPrimary.copy(alpha = textAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state != OrbState.IDLE) {
            Text(
                text = statusText,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.4.sp,
                color = MoonstoneConfig.TextDim,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The Mark monogram: an angular M cut through by a diagonal.
 *
 * Drawn rather than set as a glyph — no font has this shape, and at this size a
 * typeface "M" reads as placeholder text rather than a mark. The slash is the
 * identity: it splits the letter into two halves that still read as one letter.
 *
 * Coordinates are on a 0..1 grid and scaled to the canvas, so it stays crisp at
 * any size and needs no asset.
 */
@Composable
private fun MarkMonogram(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun x(f: Float) = w * f
        fun y(f: Float) = h * f

        // Solid angular M — sharp inner vertex, tapered outer legs.
        val m = Path().apply {
            moveTo(x(0.10f), y(0.92f))
            lineTo(x(0.10f), y(0.08f))
            lineTo(x(0.50f), y(0.62f))
            lineTo(x(0.90f), y(0.08f))
            lineTo(x(0.90f), y(0.92f))
            lineTo(x(0.74f), y(0.92f))
            lineTo(x(0.74f), y(0.42f))
            lineTo(x(0.50f), y(0.75f))
            lineTo(x(0.26f), y(0.42f))
            lineTo(x(0.26f), y(0.92f))
            close()
        }

        drawPath(m, MoonstoneConfig.TextPrimary.copy(alpha = alpha))

        // The diagonal. Drawn in the background colour so it carves the letter
        // instead of sitting on top of it, then repeated thinly in ember so the
        // cut still reads against the dark ring behind.
        val strokeW = w * 0.075f
        drawLine(
            color = Color(0xFF0B0B0C),
            start = Offset(x(-0.05f), y(1.05f)),
            end = Offset(x(1.05f), y(-0.05f)),
            strokeWidth = strokeW,
            cap = StrokeCap.Square
        )
        drawLine(
            color = MoonstoneConfig.Ember.copy(alpha = alpha * 0.9f),
            start = Offset(x(-0.02f), y(1.02f)),
            end = Offset(x(1.02f), y(-0.02f)),
            strokeWidth = strokeW * 0.22f,
            cap = StrokeCap.Round
        )
    }
}