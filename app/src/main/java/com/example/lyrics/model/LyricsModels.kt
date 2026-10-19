package com.example.lyrics.model

import androidx.compose.ui.graphics.Color

/**
 * Single word/syllable timing inside a lyric line for Apple Music style real-time karaoke fill.
 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * A single synchronized lyric line (or instrumental interlude `•••`).
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
     * Returns 0f..1f progress of this line at [positionMs].
     */
    fun lineProgress(positionMs: Long): Float {
        if (endMs <= startMs) return if (positionMs >= startMs) 1f else 0f
        return ((positionMs - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Returns exact (startMs, endMs) timing for each character index in [text]
     * so every single character sweeps smoothly from left to right like Apple Music Karaoke.
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
        // Fallback: distribute evenly across text characters
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
 * Palette used by the Apple Music-style deep dark ambient mesh background.
 */
data class AlbumPalette(
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryGlow: Color,
    val accentColor: Color,
    val deepBase: Color = Color(0xFF06070B)
)

enum class ArtworkStyle {
    NEON_SYNTHWAVE,
    VELVET_RIBBONS,
    AURORA_BOREALIS,
    STARLIGHT_COSMOS
}

data class SongTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val palette: AlbumPalette,
    val artworkStyle: ArtworkStyle,
    val lyrics: List<LyricLine>
)

/**
 * Watch screen form factor mode for testing and adaptive Wear OS rendering.
 */
enum class WatchShapeMode(val label: String) {
    ROUND("圆形表盘"),
    SQUARE("方形表盘"),
    FULLSCREEN("全屏直显")
}

/**
 * Deep-dark background optimization presets tailored for small OLED watch screens.
 */
enum class DarkBackgroundMode(
    val label: String,
    val description: String,
    val meshMaxAlpha: Float,
    val vignetteStrength: Float
) {
    DYNAMIC_MESH(
        label = "流光深色",
        description = "Apple Music 动态专辑流体背景 + 暗部压制",
        meshMaxAlpha = 0.42f,
        vignetteStrength = 0.78f
    ),
    DIMMED_OLED(
        label = "极暗护眼",
        description = "大幅降低背景明度，夜间小屏阅读不刺眼",
        meshMaxAlpha = 0.20f,
        vignetteStrength = 0.88f
    ),
    PURE_BLACK_OLED(
        label = "纯黑OLED",
        description = "纯黑底色 (#000000) 极致对比度与腕表省电",
        meshMaxAlpha = 0.0f,
        vignetteStrength = 0.96f
    )
}

/**
 * Small-screen font scale options for wrist legibility at a glance.
 */
enum class LyricFontScale(
    val label: String,
    val scaleFactor: Float
) {
    STANDARD("标准", 1.0f),
    LARGE("大字", 1.15f),
    EXTRA_LARGE("特大", 1.30f)
}

/**
 * Configuration state for the Wear OS Lyrics Page component.
 */
data class WearLyricsConfig(
    val watchShapeMode: WatchShapeMode = WatchShapeMode.ROUND,
    val darkBackgroundMode: DarkBackgroundMode = DarkBackgroundMode.DYNAMIC_MESH,
    val fontScale: LyricFontScale = LyricFontScale.LARGE,
    val showTranslation: Boolean = true,
    val enableWordByWordGlow: Boolean = true,
    val enablePeripheralBlurFade: Boolean = true,
    val alwaysShowMiniHeader: Boolean = true
)

/**
 * Utility to parse standard [mm:ss.xx] LRC text into synchronized [LyricLine] items
 * with automatically distributed word/syllable timings for Apple Music-style fill.
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

    /**
     * Splits either English words (preserving spaces) or CJK characters/clusters into timed tokens.
     */
    fun buildSyllableWords(text: String, startMs: Long, endMs: Long): List<LyricWord> {
        if (text.isBlank()) return emptyList()
        val containsSpaces = text.contains(' ')
        val tokens: List<String> = if (containsSpaces) {
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            words.mapIndexed { idx, w -> if (idx < words.lastIndex) "$w " else w }
        } else {
            // CJK character tokenization for smooth character-by-character karaoke highlight
            text.map { it.toString() }
        }

        if (tokens.isEmpty()) return emptyList()
        val totalSpan = (endMs - startMs).coerceAtLeast(300L)
        val totalWeight = tokens.sumOf { it.trim().length.coerceAtLeast(1) }.toFloat()

        var cursorMs = startMs
        return tokens.mapIndexed { idx, token ->
            val weight = token.trim().length.coerceAtLeast(1).toFloat()
            val duration = (totalSpan * (weight / totalWeight)).toLong().coerceAtLeast(60L)
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
