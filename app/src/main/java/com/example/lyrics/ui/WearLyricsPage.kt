package com.example.lyrics.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.lyrics.model.LyricLine
import com.example.lyrics.model.SongTrack
import com.example.lyrics.model.WatchShapeMode
import com.example.lyrics.model.WearLyricsConfig
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlin.math.abs

/**
 * Dedicated controller state class for the Wear OS Lyrics Page.
 * Precisely centers the active singing lyric line vertically in the middle of the screen.
 */
@Stable
class WearLyricsPageState(
    val listState: LazyListState
) {
    var activeItemHeightPx by mutableIntStateOf(140)
        internal set

    /**
     * Smoothly scrolls [activeIndex] so that the vertical center of the active lyric line
     * sits directly at the optical vertical center of the display.
     */
    suspend fun smoothCenterOnLine(activeIndex: Int, totalCount: Int) {
        if (totalCount <= 0 || activeIndex !in 0 until totalCount) return

        // Wait one frame to ensure LazyColumn layoutInfo is up to date
        withFrameNanos { }

        val layoutInfo = listState.layoutInfo
        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }

        if (visibleItem != null) {
            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
            val viewportHeight = viewportEnd - viewportStart
            // Optical center (45% from top of viewport) so the active lyric line sits dead-center
            val targetCenter = viewportStart + (viewportHeight * 0.45f)
            val itemCenter = visibleItem.offset + (visibleItem.size / 2f)
            val scrollDelta = itemCenter - targetCenter

            if (abs(scrollDelta) > 1f) {
                listState.animateScrollBy(
                    value = scrollDelta,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        } else {
            // Item is off-screen: scroll it to center first, then fine-tune
            val positiveCenterOffset = (activeItemHeightPx * 0.65f).toInt()
            listState.scrollToItem(
                index = activeIndex,
                scrollOffset = positiveCenterOffset
            )
            withFrameNanos { }
            val updatedLayout = listState.layoutInfo
            val nowVisible = updatedLayout.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (nowVisible != null) {
                val viewportStart = updatedLayout.viewportStartOffset.toFloat()
                val viewportEnd = updatedLayout.viewportEndOffset.toFloat()
                val viewportHeight = viewportEnd - viewportStart
                val targetCenter = viewportStart + (viewportHeight * 0.45f)
                val itemCenter = nowVisible.offset + (nowVisible.size / 2f)
                val scrollDelta = itemCenter - targetCenter
                if (abs(scrollDelta) > 1f) {
                    listState.animateScrollBy(
                        value = scrollDelta,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberWearLyricsPageState(
    initialFirstVisibleItemIndex: Int = 0
): WearLyricsPageState {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex)
    return remember(listState) {
        WearLyricsPageState(listState)
    }
}

/**
 * Pure Wear OS Real-Time Dynamic Lyrics Page (`WearLyricsPage`)
 *
 * Displays ONLY the real-time synchronized lyrics without any media control buttons or song info:
 * - Active singing line is strictly centered in the middle of the screen
 * - Apple Music style per-character left-to-right karaoke fill sweep & spring scale
 * - Animated 3-dot instrumental interlude indicator (`•••`)
 * - Deep dark background optimization with OLED luminance control & symmetrical top/bottom fade masks
 */
@Composable
fun WearLyricsPage(
    track: SongTrack,
    currentPositionMs: Long,
    activeLineIndex: Int,
    isPlaying: Boolean,
    isUserBrowsingLyrics: Boolean,
    config: WearLyricsConfig,
    onLyricLineClick: (LyricLine) -> Unit,
    onUserManualScroll: () -> Unit,
    onResumeSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageState: WearLyricsPageState = rememberWearLyricsPageState()
) {
    val isDragged by pageState.listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) {
        if (isDragged) {
            onUserManualScroll()
        }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .testTag("wear_lyrics_page_root")
    ) {
        val viewportHeightDp = maxHeight
        // Symmetrical half-screen top & bottom padding ensures even the first and last lyric lines
        // can be scrolled to the exact vertical center of the screen.
        val halfScreenVerticalPadding = (viewportHeightDp * 0.45f).coerceAtLeast(100.dp)

        // Smoothly center the active lyric line whenever activeLineIndex, track, or viewport changes
        LaunchedEffect(
            activeLineIndex,
            track.id,
            isUserBrowsingLyrics,
            viewportHeightDp,
            config.fontScale,
            config.showTranslation
        ) {
            if (!isUserBrowsingLyrics && track.lyrics.isNotEmpty()) {
                pageState.smoothCenterOnLine(
                    activeIndex = activeLineIndex,
                    totalCount = track.lyrics.size
                )
            }
        }

        val isRoundWatch = config.watchShapeMode == WatchShapeMode.ROUND
        val horizontalSafePadding = if (isRoundWatch) 24.dp else 18.dp

        // 1. Deep Dark Optimized Apple Music Mesh Background
        AppleMusicDarkMeshBackground(
            palette = track.palette,
            darkMode = config.darkBackgroundMode,
            isPlaying = isPlaying
        )

        // 2. Pure Synchronized Lyrics Stream Centered Vertically
        LazyColumn(
            state = pageState.listState,
            flingBehavior = ScrollableDefaults.flingBehavior(),
            contentPadding = PaddingValues(
                start = horizontalSafePadding,
                end = horizontalSafePadding,
                top = halfScreenVerticalPadding,
                bottom = halfScreenVerticalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Symmetrical top & bottom smooth alpha fade mask for small watch screen readability
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.22f to Color.Black,
                            0.78f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .testTag("wear_lyrics_lazy_list")
        ) {
            itemsIndexed(
                items = track.lyrics,
                key = { _, line -> "${track.id}_${line.id}" }
            ) { index, line ->
                val distanceFromActive = abs(index - activeLineIndex)
                val isCurrentActive = index == activeLineIndex
                val isPastLine = index < activeLineIndex

                AppleMusicLyricLineItem(
                    line = line,
                    isActive = isCurrentActive,
                    isPast = isPastLine,
                    distanceFromActive = distanceFromActive,
                    currentPositionMs = currentPositionMs,
                    accentColor = track.palette.accentColor,
                    fontScaleFactor = config.fontScale.scaleFactor,
                    showTranslation = config.showTranslation,
                    enableWordByWordGlow = config.enableWordByWordGlow,
                    enablePeripheralFade = config.enablePeripheralBlurFade,
                    isRoundWatch = isRoundWatch,
                    onActiveItemMeasured = { heightPx ->
                        if (heightPx > 0) {
                            pageState.activeItemHeightPx = heightPx
                        }
                    },
                    onClick = { onLyricLineClick(line) }
                )
            }
        }

        // 3. Minimal "恢复同步" pill shown ONLY temporarily when user manually scrolls away
        AnimatedVisibility(
            visible = isUserBrowsingLyrics,
            enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        ) {
            Surface(
                onClick = onResumeSyncClick,
                shape = CircleShape,
                color = track.palette.accentColor.copy(alpha = 0.92f),
                contentColor = Color.Black,
                shadowElevation = 6.dp,
                modifier = Modifier.testTag("resume_sync_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.resume_sync),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.resume_sync),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    )
                }
            }
        }
    }
}

/**
 * Individual lyric line item styled after Apple Music Live Lyrics and optimized for Wear OS:
 * - Spring physics scale expansion (`1.0f` vs `0.86f`)
 * - Intra-character left-to-right continuous karaoke color fill sweep
 * - Distance-based opacity & subtle horizontal curve inset on round watch faces
 */
@Composable
private fun AppleMusicLyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    distanceFromActive: Int,
    currentPositionMs: Long,
    accentColor: Color,
    fontScaleFactor: Float,
    showTranslation: Boolean,
    enableWordByWordGlow: Boolean,
    enablePeripheralFade: Boolean,
    isRoundWatch: Boolean,
    onActiveItemMeasured: (Int) -> Unit,
    onClick: () -> Unit
) {
    val targetScale = if (isActive) 1.0f else 0.86f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "lyricScale"
    )

    val targetAlpha = when {
        isActive -> 1.0f
        !enablePeripheralFade -> 0.45f
        distanceFromActive == 1 -> if (isPast) 0.36f else 0.48f
        distanceFromActive == 2 -> 0.22f
        else -> 0.12f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "lyricAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (isActive) {
                    onActiveItemMeasured(size.height)
                }
            }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = animatedAlpha
                transformOrigin = TransformOrigin.Center
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = if (isActive) 10.dp else 6.dp)
            .testTag("lyric_line_${line.id}")
    ) {
        if (line.isInterlude) {
            AppleMusicInterludeDots(
                isActive = isActive,
                progress = line.lineProgress(currentPositionMs),
                accentColor = accentColor
            )
        } else {
            val baseFontSize = (19.0f * fontScaleFactor).sp
            val lineHeight = (25.5f * fontScaleFactor).sp

            if (isActive && enableWordByWordGlow) {
                AppleMusicKaraokeSweepText(
                    line = line,
                    currentPositionMs = currentPositionMs,
                    baseFontSize = baseFontSize,
                    lineHeight = lineHeight,
                    accentColor = accentColor
                )
            } else {
                // Whole-line highlight or dimmed inactive line
                Text(
                    text = line.text,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = TextStyle(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = baseFontSize,
                        lineHeight = lineHeight,
                        letterSpacing = (-0.2).sp,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        shadow = if (isActive) {
                            Shadow(
                                color = accentColor.copy(alpha = 0.55f),
                                offset = Offset(0f, 0f),
                                blurRadius = 14f
                            )
                        } else null
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Optional bilingual translation line optimized for small watch screens
            if (showTranslation && !line.translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.translation,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = TextStyle(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = (13.0f * fontScaleFactor).sp,
                        lineHeight = (17.5f * fontScaleFactor).sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (isActive) {
                            Color.White.copy(alpha = 0.82f)
                        } else {
                            Color.White.copy(alpha = 0.65f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Renders Apple Music Sing / Live Lyrics style karaoke text where each individual character
 * smoothly fills with glowing color from left to right (`charLeft -> charRight`) as it is sung.
 */
@Composable
private fun AppleMusicKaraokeSweepText(
    line: LyricLine,
    currentPositionMs: Long,
    baseFontSize: TextUnit,
    lineHeight: TextUnit,
    accentColor: Color
) {
    val charTimings = remember(line.id, line.text) { line.computeCharTimings() }
    var textLayoutResult by remember(line.text, baseFontSize) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val sharedTextStyle = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = baseFontSize,
        lineHeight = lineHeight,
        letterSpacing = (-0.2).sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. Base Layer: Un-sung portion of the active line in translucent dim white
        Text(
            text = line.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = sharedTextStyle.copy(
                color = Color.White.copy(alpha = 0.30f)
            ),
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Foreground Karaoke Sweep Layer: Crisp glowing white masked continuously left-to-right
        // across each character's exact pixel bounding box
        Text(
            text = line.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = sharedTextStyle.copy(
                color = Color.White,
                shadow = Shadow(
                    color = accentColor.copy(alpha = 0.72f),
                    offset = Offset(0f, 0f),
                    blurRadius = 18f
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val layout = textLayoutResult
                    if (layout == null || charTimings.isEmpty() || size.width <= 0f) {
                        return@drawWithContent
                    }

                    // Draw the full glowing white text into the offscreen layer first
                    drawContent()

                    val totalLines = layout.lineCount
                    val textLen = line.text.length

                    for (lineIdx in 0 until totalLines) {
                        val stripTop = if (lineIdx == 0) {
                            -16f
                        } else {
                            (layout.getLineBottom(lineIdx - 1) + layout.getLineTop(lineIdx)) * 0.5f
                        }
                        val stripBottom = if (lineIdx == totalLines - 1) {
                            size.height + 16f
                        } else {
                            (layout.getLineBottom(lineIdx) + layout.getLineTop(lineIdx + 1)) * 0.5f
                        }
                        val stripHeight = (stripBottom - stripTop).coerceAtLeast(1f)

                        val rawStartChar = layout.getLineStart(lineIdx)
                            .coerceIn(0, (textLen - 1).coerceAtLeast(0))
                        val rawEndExclusive = layout.getLineEnd(lineIdx, visibleEnd = true)
                            .coerceIn(rawStartChar + 1, textLen)
                        val lastCharOnLine = (rawEndExclusive - 1).coerceAtLeast(rawStartChar)

                        val lineStartMs = charTimings[rawStartChar].first
                        val lineEndMs = charTimings[lastCharOnLine].second

                        when {
                            currentPositionMs <= lineStartMs -> {
                                // This visual line has not started yet -> mask to 0% alpha
                                drawRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(0f, stripTop),
                                    size = Size(size.width, stripHeight),
                                    blendMode = BlendMode.DstIn
                                )
                            }

                            currentPositionMs >= lineEndMs -> {
                                // This visual line is completely sung -> keep 100% alpha
                            }

                            else -> {
                                // Locate the exact character currently being sung on this visual line
                                var activeChar = rawStartChar
                                for (c in rawStartChar..lastCharOnLine) {
                                    val (_, cEnd) = charTimings[c]
                                    if (currentPositionMs < cEnd) {
                                        activeChar = c
                                        break
                                    }
                                    activeChar = c
                                }

                                val (cStart, cEnd) = charTimings[activeChar]
                                val charProgress = ((currentPositionMs - cStart).toFloat() /
                                    (cEnd - cStart).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

                                val charBox = layout.getBoundingBox(activeChar)
                                val charLeft = charBox.left
                                val charWidth = (charBox.right - charBox.left).coerceAtLeast(2f)

                                // Sub-pixel horizontal fill position sweeping from left to right inside activeChar
                                val sweepX = charLeft + (charWidth * charProgress)
                                val featherPx = (charWidth * 0.24f).coerceIn(3f, 10f)

                                val solidStop = ((sweepX - featherPx * 0.5f) / size.width).coerceIn(0f, 1f)
                                val transparentStop = ((sweepX + featherPx) / size.width).coerceIn(
                                    (solidStop + 0.002f).coerceAtMost(1f),
                                    1f
                                )

                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0.0f to Color.Black,
                                        solidStop to Color.Black,
                                        transparentStop to Color.Transparent,
                                        1.0f to Color.Transparent,
                                        startX = 0f,
                                        endX = size.width
                                    ),
                                    topLeft = Offset(0f, stripTop),
                                    size = Size(size.width, stripHeight),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                    }
                }
        )
    }
}

/**
 * Apple Music style pulsing 3-dot instrumental interlude indicator (`• • •`).
 */
@Composable
private fun AppleMusicInterludeDots(
    isActive: Boolean,
    progress: Float,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "interludePulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        repeat(3) { dotIndex ->
            val dotThreshold = dotIndex / 3f
            val dotFill = if (!isActive) {
                0.35f
            } else {
                ((progress - dotThreshold) * 3f).coerceIn(0.25f, 1f)
            }
            val scale = if (isActive) pulse else 1f
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        if (isActive && dotFill > 0.6f) accentColor else Color.White.copy(alpha = dotFill)
                    )
            )
        }
    }
}
