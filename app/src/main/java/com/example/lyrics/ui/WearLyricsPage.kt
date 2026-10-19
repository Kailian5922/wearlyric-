package com.example.lyrics.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyrics.model.LyricLine
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlin.math.abs

/**
 * Controller state class for the embeddable Wear OS Lyrics Page (`WearLyricsPage`).
 * Precisely centers the active singing lyric line vertically in the middle of the viewport.
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

        withFrameNanos { }

        val layoutInfo = listState.layoutInfo
        val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }

        if (visibleItem != null) {
            val viewportStart = layoutInfo.viewportStartOffset.toFloat()
            val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
            val viewportHeight = viewportEnd - viewportStart
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
 * Standalone, transparent-background Wear OS Real-Time Dynamic Lyrics Component (`WearLyricsPage`).
 *
 * Ready to be embedded directly into any Wear OS media player or page:
 * - Zero background drawing (transparent container so host project controls background)
 * - Active singing line is centered both horizontally and vertically
 * - Apple Music style single-character left-to-right continuous karaoke color fill sweep
 * - Smooth spring scale transition & top/bottom edge fade mask
 */
@Composable
fun WearLyricsPage(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    activeLineIndex: Int = lyrics.indexOfLast { currentPositionMs >= it.startMs }.coerceAtLeast(0),
    isUserBrowsingLyrics: Boolean = false,
    showTranslation: Boolean = true,
    fontScaleFactor: Float = 1.15f,
    fontFamily: FontFamily = PlusJakartaSansFontFamily,
    onLyricLineClick: (LyricLine) -> Unit = {},
    onUserManualScroll: () -> Unit = {},
    modifier: Modifier = Modifier,
    pageState: WearLyricsPageState = rememberWearLyricsPageState(initialFirstVisibleItemIndex = activeLineIndex)
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
        val halfScreenVerticalPadding = (viewportHeightDp * 0.45f).coerceAtLeast(100.dp)

        LaunchedEffect(
            activeLineIndex,
            lyrics,
            isUserBrowsingLyrics,
            viewportHeightDp,
            fontScaleFactor,
            showTranslation
        ) {
            if (!isUserBrowsingLyrics && lyrics.isNotEmpty()) {
                pageState.smoothCenterOnLine(
                    activeIndex = activeLineIndex,
                    totalCount = lyrics.size
                )
            }
        }

        LazyColumn(
            state = pageState.listState,
            flingBehavior = ScrollableDefaults.flingBehavior(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
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
                    // Symmetrical top & bottom edge alpha fade for small Wear OS screens
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
                items = lyrics,
                key = { index, line -> "${line.id}_$index" }
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
                    fontScaleFactor = fontScaleFactor,
                    fontFamily = fontFamily,
                    showTranslation = showTranslation,
                    onActiveItemMeasured = { heightPx ->
                        if (heightPx > 0) {
                            pageState.activeItemHeightPx = heightPx
                        }
                    },
                    onClick = { onLyricLineClick(line) }
                )
            }
        }
    }
}

@Composable
private fun AppleMusicLyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    distanceFromActive: Int,
    currentPositionMs: Long,
    fontScaleFactor: Float,
    fontFamily: FontFamily,
    showTranslation: Boolean,
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
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = if (isActive) 10.dp else 6.dp)
            .testTag("lyric_line_${line.id}")
    ) {
        if (line.isInterlude) {
            AppleMusicInterludeDots(
                isActive = isActive,
                progress = line.lineProgress(currentPositionMs)
            )
        } else {
            val baseFontSize = (19.0f * fontScaleFactor).sp
            val lineHeight = (25.5f * fontScaleFactor).sp

            if (isActive) {
                AppleMusicKaraokeSweepText(
                    line = line,
                    currentPositionMs = currentPositionMs,
                    baseFontSize = baseFontSize,
                    lineHeight = lineHeight,
                    fontFamily = fontFamily
                )
            } else {
                Text(
                    text = line.text,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = baseFontSize,
                        lineHeight = lineHeight,
                        letterSpacing = (-0.2).sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showTranslation && !line.translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.translation,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontFamily = fontFamily,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = (13.0f * fontScaleFactor).sp,
                        lineHeight = (17.5f * fontScaleFactor).sp,
                        textAlign = TextAlign.Center,
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
 * smoothly fills with pure white color from left to right (`charLeft -> charRight`) as it is sung.
 */
@Composable
private fun AppleMusicKaraokeSweepText(
    line: LyricLine,
    currentPositionMs: Long,
    baseFontSize: TextUnit,
    lineHeight: TextUnit,
    fontFamily: FontFamily
) {
    val charTimings = remember(line.id, line.text) { line.computeCharTimings() }
    var textLayoutResult by remember(line.text, baseFontSize) {
        mutableStateOf<TextLayoutResult?>(null)
    }

    val sharedTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = baseFontSize,
        lineHeight = lineHeight,
        letterSpacing = (-0.2).sp,
        textAlign = TextAlign.Center
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. Base Layer: Un-sung portion of the active line in translucent dim white
        Text(
            text = line.text,
            textAlign = TextAlign.Center,
            style = sharedTextStyle.copy(
                color = Color.White.copy(alpha = 0.30f)
            ),
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. Foreground Karaoke Sweep Layer: Crisp white masked continuously left-to-right
        // across each character's exact pixel bounding box
        Text(
            text = line.text,
            textAlign = TextAlign.Center,
            style = sharedTextStyle.copy(
                color = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val layout = textLayoutResult
                    if (layout == null || charTimings.isEmpty() || size.width <= 0f) {
                        return@drawWithContent
                    }

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
                                drawRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(0f, stripTop),
                                    size = Size(size.width, stripHeight),
                                    blendMode = BlendMode.DstIn
                                )
                            }

                            currentPositionMs >= lineEndMs -> {
                                // Entire visual line is already sung -> keep 100% opaque
                            }

                            else -> {
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
    progress: Float
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
                    .background(Color.White.copy(alpha = dotFill))
            )
        }
    }
}
