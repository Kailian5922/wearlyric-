package com.example

import com.example.lyrics.model.LrcParser
import com.example.lyrics.model.SampleSongsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun lrcParser_parsesBilingualAndWordTimings() {
        val raw = """
            [00:00.00] •••
            [00:04.00] 夜色漫过表盘的边缘 / Night spills across the dial
            [00:09.50] 每一拍心跳都化作流光 / Every heartbeat turns into light
        """.trimIndent()

        val parsed = LrcParser.parseLrc(raw, totalDurationMs = 20_000L)
        assertEquals(3, parsed.size)
        assertTrue(parsed[0].isInterlude)
        assertEquals("夜色漫过表盘的边缘", parsed[1].text)
        assertEquals("Night spills across the dial", parsed[1].translation)
        assertTrue(parsed[1].words.isNotEmpty())
        assertEquals(4_000L, parsed[1].startMs)
        assertEquals(9_500L, parsed[1].endMs)
    }

    @Test
    fun sampleRepository_hasValidSynchronizedLyrics() {
        val tracks = SampleSongsRepository.tracks
        assertEquals(4, tracks.size)
        tracks.forEach { track ->
            assertTrue(track.lyrics.isNotEmpty())
            assertTrue(track.durationMs > 0L)
        }
    }
}
