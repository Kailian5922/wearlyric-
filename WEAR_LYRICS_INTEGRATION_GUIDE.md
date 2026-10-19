# Wear OS Apple Music 风格实时动态歌词组件 (`WearLyricsPage`) 接入与实现技术文档（供 AI 集成参考）

> **致目标项目 AI 助手 (Instructions for Target AI Agent)**：
> 本文档提供了已验证通过的 **Wear OS 实时动态歌词组件 (`WearLyricsPage`)** 的完整架构说明、数据模型、LRC 解析器、核心渲染源码以及宿主播放器接入规范。请直接按照本文档将歌词组件集成到当前 Wear OS 项目中。

---

## 1. 组件核心特性与视觉规范

1. **纯透明背景、零冗余 UI**：
   - 组件自身**不绘制任何背景底色**，不包含任何媒体控制按钮、歌曲标题栏或底部进度条，可直接叠加在宿主项目的任意深色/表盘背景之上。
2. **水平与垂直双向正中心对齐**：
   - **垂直正居中**：通过 `BoxWithConstraints` 动态测量视口高度 `maxHeight`，设置上下对称的 `contentPadding(top = maxHeight * 0.45f, bottom = maxHeight * 0.45f)`，保证第一行与最后一行歌词同样可以滚动至屏幕正中心；结合 `LazyListLayoutInfo` 计算当前激活行中心点与视口光学中心的像素差 `scrollDelta`，通过 `animateScrollBy` 平滑滚动居中。
   - **水平正居中**：所有歌词行、双语译文、间奏呼吸圆点均采用 `TextAlign.Center` 与 `Alignment.CenterHorizontally`，且弹簧缩放原点固定为 `TransformOrigin.Center`（`0.5f, 0.5f`），缩放过程中严格沿屏幕垂直中轴线对齐。
3. **Apple Music 风格单字从左到右连续填充（卡拉 OK 逐字扫光）**：
   - 使用双层 `Text` 叠加（底层 `0.30f` 透明度未唱底字 + 顶层 `1.0f` 纯白高亮字）。
   - 通过 `onTextLayout` 获取 `TextLayoutResult`，调用 `layout.getBoundingBox(activeChar)` 获取当前正在演唱的那**单个字符**的精确像素边界 `[charBox.left, charBox.right]`。
   - 在单个字符的演唱时间窗口 `[cStart, cEnd]` 内，根据实时毫秒进度 `currentPositionMs` 计算字符内部的扫光横坐标 `sweepX = charLeft + charWidth * charProgress`，并使用 `BlendMode.DstIn` + `Brush.horizontalGradient` 对每个视觉折行（支持多行自动折行）进行从左到右的羽化裁剪，实现**单个字内部从左到右平滑充满颜色**的效果。
4. **小屏幕阅读体验优化**：
   - **上下边缘渐隐遮罩**：列表容器使用 `CompositingStrategy.Offscreen` + `BlendMode.DstIn` 绘制上下对称的垂直渐变透明遮罩（`0.0f -> 0.22f` 与 `0.78f -> 1.0f`），防止文字在圆形表盘上下边缘生硬截断。
   - **非激活行距离衰减**：当前行缩放为 `1.0f`（`alpha = 1.0f`），相邻行与远端行缩放为 `0.86f`，透明度按距离阶梯衰减（`0.48f` / `0.36f` -> `0.22f` -> `0.12f`），突出视觉焦点。

---

## 2. 完整源码文件（仅需 2 个 Kotlin 文件即可完整移植）

将以下两个文件放入目标 Wear OS 项目的包名下（修改顶部 `package` 声明即可，仅依赖标准 Jetpack Compose Foundation / Material3 / UI）：

### 文件一：`LyricsModels.kt`（数据模型与 LRC 解析器）

```kotlin
package com.example.lyrics.model

/**
 * 单个字/词的时间轴模型，用于 Apple Music 风格逐字从左到右填充高亮。
 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * 单行同步歌词模型（支持间奏 `•••` 与双语译文）。
 */
data class LyricLine(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val translation: String? = null,
    val isInterlude: Boolean = false,
    val words: List<LyricWord> = emptyList()
) {
    /**
     * 计算当前行在 [positionMs] 时的整体进度 (0f..1f)。
     */
    fun lineProgress(positionMs: Long): Float {
        if (endMs <= startMs) return if (positionMs >= startMs) 1f else 0f
        return ((positionMs - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 计算 [text] 中每一个字符对应的精确 (startMs, endMs) 时间区间，
     * 驱动单字内部从左到右的像素级扫光填充。
     */
    fun computeCharTimings(): List<Pair<Long, Long>> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<Pair<Long, Long>>(text.length)
        if (words.isNotEmpty() && words.sumOf { it.text.length } == text.length) {
            for (word in words) {
                val len = word.text.length
                if (len == 1) {
                    result.add(word.startMs to word.endMs)
                } else if (len > 1) {
                    val span = (word.endMs - word.startMs).coerceAtLeast(len.toLong())
                    for (i in 0 until len) {
                        val cStart = word.startMs + (span * i) / len
                        val cEnd = if (i == len - 1) word.endMs else word.startMs + (span * (i + 1)) / len
                        result.add(cStart to cEnd.coerceAtLeast(cStart + 1L))
                    }
                }
            }
            return result
        }
        val effectiveEnd = (endMs - 250L).coerceAtLeast(startMs + 400L)
        val totalSpan = (effectiveEnd - startMs).coerceAtLeast(text.length.toLong())
        for (i in text.indices) {
            val cStart = startMs + (totalSpan * i) / text.length
            val cEnd = if (i == text.lastIndex) effectiveEnd else startMs + (totalSpan * (i + 1)) / text.length
            result.add(cStart to cEnd.coerceAtLeast(cStart + 1L))
        }
        return result
    }
}

/**
 * 标准 [mm:ss.xx] LRC 歌词解析工具：
 * 支持自动切分单字时间轴，并支持通过 "原文 / 译文" 格式解析双语歌词。
 */
object LrcParser {
    private val lineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    fun parseLrc(rawLrc: String, totalDurationMs: Long = 180_000L): List<LyricLine> {
        val rawEntries = mutableListOf<Pair<Long, String>>()
        rawLrc.lines().forEach { line ->
            val trimmed = line.trim()
            val match = lineRegex.matchEntire(trimmed)
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val millis = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val timestampMs = min * 60_000L + sec * 1_000L + millis
                val content = match.groupValues[4].trim()
                rawEntries.add(timestampMs to content)
            }
        }

        if (rawEntries.isEmpty()) return emptyList()
        val sorted = rawEntries.sortedBy { it.first }

        return sorted.mapIndexed { index, (startMs, rawText) ->
            val nextStart = if (index + 1 < sorted.size) {
                sorted[index + 1].first
            } else {
                maxOf(startMs + 5000L, totalDurationMs)
            }
            val endMs = maxOf(startMs + 600L, nextStart)

            val isInterlude = rawText.isEmpty() || rawText == "•••" || rawText == "..." || rawText.equals("[间奏]", ignoreCase = true)
            val parts = rawText.split(" / ", limit = 2)
            val primaryText = if (isInterlude) "•••" else parts[0].trim()
            val translation = if (!isInterlude && parts.size > 1) parts[1].trim() else null

            val words = if (isInterlude) {
                emptyList()
            } else {
                buildSyllableWords(primaryText, startMs, (endMs - 250L).coerceAtLeast(startMs + 400L))
            }

            LyricLine(
                id = index,
                startMs = startMs,
                endMs = endMs,
                text = primaryText,
                translation = translation,
                isInterlude = isInterlude,
                words = words
            )
        }
    }

    fun buildSyllableWords(text: String, startMs: Long, endMs: Long): List<LyricWord> {
        if (text.isBlank()) return emptyList()
        val tokens: List<String> = text.map { it.toString() }
        val totalSpan = (endMs - startMs).coerceAtLeast(300L)
        val totalWeight = tokens.sumOf { it.trim().length.coerceAtLeast(1) }.toFloat()

        var cursorMs = startMs
        return tokens.mapIndexed { idx, token ->
            val weight = token.trim().length.coerceAtLeast(1).toFloat()
            val duration = (totalSpan * (weight / totalWeight)).toLong().coerceAtLeast(40L)
            val tokenEnd = if (idx == tokens.lastIndex) endMs else (cursorMs + duration).coerceAtMost(endMs)
            val word = LyricWord(
                text = token,
                startMs = cursorMs,
                endMs = tokenEnd
            )
            cursorMs = tokenEnd
            word
        }
    }
}
```

---

### 文件二：`WearLyricsPage.kt`（核心可嵌入歌词 UI 组件）

```kotlin
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyrics.model.LyricLine
import kotlin.math.abs

@Stable
class WearLyricsPageState(
    val listState: LazyListState
) {
    var activeItemHeightPx by mutableIntStateOf(140)
        internal set

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

@Composable
fun WearLyricsPage(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    activeLineIndex: Int = lyrics.indexOfLast { currentPositionMs >= it.startMs }.coerceAtLeast(0),
    isUserBrowsingLyrics: Boolean = false,
    showTranslation: Boolean = true,
    fontScaleFactor: Float = 1.15f,
    fontFamily: FontFamily = FontFamily.Default,
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
        modifier = modifier.fillMaxSize()
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
        Text(
            text = line.text,
            textAlign = TextAlign.Center,
            style = sharedTextStyle.copy(
                color = Color.White.copy(alpha = 0.30f)
            ),
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier.fillMaxWidth()
        )

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
                                // 整行已唱完，保持 100% 不透明
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
```

---

## 3. 宿主 Wear OS 项目的集成步骤（供目标 AI 执行）

### 步骤 1：解析或构建歌词数据 (`List<LyricLine>`)
- **如果宿主项目拿到的是标准 `.lrc` 歌词文本**：
  直接调用 `val lyrics = LrcParser.parseLrc(rawLrcString, songDurationMs)`，即可自动生成包含单字时间轴 (`words: List<LyricWord>`)、间奏呼吸点 (`isInterlude`) 和双语译文的 `List<LyricLine>`。
- **如果宿主项目已有逐字歌词接口（如 YRC / KRC / QRC / TTML）**：
  将接口返回的每一行映射为 `LyricLine(id, startMs, endMs, text, translation, isInterlude, words = listOf(LyricWord(wordText, wordStartMs, wordEndMs), ...))`。

### 步骤 2：以 16ms~33ms 频率同步播放器进度 `currentPositionMs`
为了保证“单个字内部从左到右充满颜色”的卡拉 OK 动画达到 60fps 丝滑效果，宿主 `ViewModel` 在播放状态下应以 `16ms` 间隔读取 `MediaController` / `ExoPlayer` 的 `currentPosition`（或基于上次同步位置 + `SystemClock.elapsedRealtime()` 差值插值）：

```kotlin
// 在宿主页面的 Composable 中直接嵌入：
WearLyricsPage(
    lyrics = uiState.lyrics,                    // List<LyricLine>
    currentPositionMs = uiState.positionMs,     // 实时播放毫秒进度 (建议 16ms 刷新)
    isUserBrowsingLyrics = uiState.isUserScrolling,
    showTranslation = true,                     // 是否展示双语译文
    fontScaleFactor = 1.15f,                    // 小屏字号缩放倍率
    onLyricLineClick = { clickedLine ->
        // 点击某行歌词时跳转到对应毫秒进度
        player.seekTo(clickedLine.startMs)
    },
    onUserManualScroll = {
        // 用户手动滑动歌词时暂停自动居中 3 秒，3 秒后恢复 isUserScrolling = false
        viewModel.onUserManualScroll()
    },
    modifier = Modifier.fillMaxSize()
)
```
