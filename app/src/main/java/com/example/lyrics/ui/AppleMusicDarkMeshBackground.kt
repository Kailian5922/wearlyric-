package com.example.lyrics.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lyrics.model.AlbumPalette
import com.example.lyrics.model.ArtworkStyle
import com.example.lyrics.model.DarkBackgroundMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Music-inspired fluid ambient mesh background specifically engineered for
 * Wear OS small OLED screens:
 * - Animated multi-orb color gradients derived from the current song's [AlbumPalette]
 * - Strict luminance clamping via [DarkBackgroundMode] to prevent eye strain and preserve
 *   AAA text contrast on compact displays
 * - Radial edge darkening to blend seamlessly into round or square watch bezels
 */
@Composable
fun AppleMusicDarkMeshBackground(
    palette: AlbumPalette,
    darkMode: DarkBackgroundMode,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor by animateColorAsState(
        targetValue = palette.primaryGlow,
        animationSpec = tween(durationMillis = 900),
        label = "primaryGlow"
    )
    val secondaryColor by animateColorAsState(
        targetValue = palette.secondaryGlow,
        animationSpec = tween(durationMillis = 900),
        label = "secondaryGlow"
    )
    val tertiaryColor by animateColorAsState(
        targetValue = palette.tertiaryGlow,
        animationSpec = tween(durationMillis = 900),
        label = "tertiaryGlow"
    )
    val baseColor by animateColorAsState(
        targetValue = if (darkMode == DarkBackgroundMode.PURE_BLACK_OLED) Color.Black else palette.deepBase,
        animationSpec = tween(durationMillis = 700),
        label = "baseColor"
    )

    val meshAlpha by animateFloatAsState(
        targetValue = darkMode.meshMaxAlpha * (if (isPlaying) 1.0f else 0.65f),
        animationSpec = tween(durationMillis = 600),
        label = "meshAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "meshOrbit")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbPhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)

        // 1. Solid OLED deep base
        drawRect(color = baseColor)

        if (meshAlpha > 0.01f && minDim > 0f) {
            // 2. Three slow-drifting Apple Music style color orbs with low-luminance clamping
            val orb1Center = Offset(
                x = w * (0.30f + 0.18f * cos(phase)),
                y = h * (0.28f + 0.14f * sin(phase))
            )
            val orb2Center = Offset(
                x = w * (0.72f + 0.16f * sin(phase * 0.8f + 1.2f)),
                y = h * (0.62f + 0.16f * cos(phase * 0.8f + 1.2f))
            )
            val orb3Center = Offset(
                x = w * (0.45f + 0.20f * sin(phase * 0.6f + 2.5f)),
                y = h * (0.78f + 0.12f * cos(phase * 0.6f + 2.5f))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = meshAlpha),
                        primaryColor.copy(alpha = meshAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = orb1Center,
                    radius = minDim * 0.72f
                ),
                center = orb1Center,
                radius = minDim * 0.72f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = meshAlpha * 0.88f),
                        secondaryColor.copy(alpha = meshAlpha * 0.28f),
                        Color.Transparent
                    ),
                    center = orb2Center,
                    radius = minDim * 0.68f
                ),
                center = orb2Center,
                radius = minDim * 0.68f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tertiaryColor.copy(alpha = meshAlpha * 0.65f),
                        Color.Transparent
                    ),
                    center = orb3Center,
                    radius = minDim * 0.58f
                ),
                center = orb3Center,
                radius = minDim * 0.58f
            )

            // 3. Radial bezel anti-glare vignette to optimize small-screen contrast and blend into watch frame
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.62f to Color.Black.copy(alpha = darkMode.vignetteStrength * 0.35f),
                        1.0f to Color.Black.copy(alpha = darkMode.vignetteStrength)
                    ),
                    center = Offset(w * 0.5f, h * 0.46f),
                    radius = minDim * 0.58f
                )
            )
        }
    }
}

/**
 * High-detail procedural album cover art renderer for each track.
 */
@Composable
fun ProceduralAlbumArtwork(
    style: ArtworkStyle,
    palette: AlbumPalette,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.deepBase)
            .border(0.75.dp, Color.White.copy(alpha = 0.18f), shape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Rich diagonal base gradient
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        palette.primaryGlow.copy(alpha = 0.85f),
                        palette.secondaryGlow.copy(alpha = 0.85f),
                        palette.deepBase
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            when (style) {
                ArtworkStyle.VELVET_RIBBONS -> {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(palette.tertiaryGlow.copy(alpha = 0.75f), Color.Transparent),
                            center = Offset(w * 0.75f, h * 0.25f),
                            radius = w * 0.65f
                        )
                    )
                    val path = Path().apply {
                        moveTo(0f, h * 0.65f)
                        cubicTo(w * 0.35f, h * 0.3f, w * 0.65f, h * 0.9f, w, h * 0.45f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.55f),
                        style = Stroke(width = w * 0.08f)
                    )
                }

                ArtworkStyle.NEON_SYNTHWAVE -> {
                    // Retro-futuristic glowing sun + horizon
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFD166), palette.secondaryGlow)
                        ),
                        radius = w * 0.28f,
                        center = Offset(w * 0.5f, h * 0.45f)
                    )
                    drawLine(
                        color = palette.primaryGlow,
                        start = Offset(0f, h * 0.66f),
                        end = Offset(w, h * 0.66f),
                        strokeWidth = w * 0.06f
                    )
                }

                ArtworkStyle.STARLIGHT_COSMOS -> {
                    // Orbital rings & bright star core
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = w * 0.34f,
                        center = Offset(w * 0.5f, h * 0.5f),
                        style = Stroke(width = w * 0.04f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = w * 0.12f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )
                }

                ArtworkStyle.AURORA_BOREALIS -> {
                    val wave = Path().apply {
                        moveTo(0f, h * 0.4f)
                        quadraticTo(w * 0.5f, h * 0.1f, w, h * 0.5f)
                    }
                    drawPath(
                        path = wave,
                        color = palette.accentColor.copy(alpha = 0.8f),
                        style = Stroke(width = w * 0.12f)
                    )
                }
            }
        }
    }
}
