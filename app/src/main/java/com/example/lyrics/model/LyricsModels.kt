package com.example.lyrics.model

/**
 * Single word/character timing inside a lyric line for Apple Music style real-time karaoke fill.
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

data class SongTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val lyrics: List<LyricLine>
)

/**
 * Utility to parse standard [mm:ss.xx] LRC text into synchronized [LyricLine] items
 * with automatically distributed word/character timings for Apple Music-style fill.
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
