package com.example.lyrics.model

import androidx.compose.ui.graphics.Color

/**
 * Curated library of multi-genre tracks with millisecond-accurate Apple Music style
 * lyric lines, bilingual translations, instrumental breaks, and OLED dark palettes.
 */
object SampleSongsRepository {

    val tracks: List<SongTrack> by lazy {
        listOf(
            createFloatingLightTrack(),
            createNeonHorizonTrack(),
            createStarlightOdysseyTrack(),
            createAuroraPulseTrack()
        )
    }

    private fun createFloatingLightTrack(): SongTrack {
        val durationMs = 76_000L
        val rawLines = listOf(
            Triple(0L, "•••", null),
            Triple(4_000L, "夜色漫过表盘的边缘", "Night spills across the edge of the dial"),
            Triple(9_000L, "每一拍心跳都化作流光", "Every heartbeat turns into flowing light"),
            Triple(14_500L, "在方寸之间 听见星海回响", "Within inches on the wrist, hear the starry sea echo"),
            Triple(20_500L, "微光随旋律轻轻起伏", "Soft glow rises and falls with the melody"),
            Triple(26_000L, "不必仰望遥远的天际", "No need to look up at the distant sky"),
            Triple(31_500L, "银河正绕着指尖缓缓旋转", "The galaxy is slowly spinning around your fingertips"),
            Triple(37_500L, "•••", null),
            Triple(42_500L, "当高光停驻在这一行字句", "When the highlight rests upon this very line"),
            Triple(48_000L, "连呼吸也拥有了温柔的频率", "Even breathing finds a gentle frequency"),
            Triple(54_000L, "深邃黑夜里 最清晰的共鸣", "The clearest resonance inside the deepest night"),
            Triple(60_500L, "陪你走过每一段寂静路途", "Accompanying you through every quiet journey"),
            Triple(67_000L, "余音在腕间 久久不散", "Lingering notes stay warm upon your wrist")
        )
        return SongTrack(
            id = "velvet_echoes",
            title = "浮光掠影 (Velvet Echoes)",
            artist = "林深 & 曜",
            album = "腕上夜曲 Nocturne",
            durationMs = durationMs,
            palette = AlbumPalette(
                primaryGlow = Color(0xFFFF2D55),
                secondaryGlow = Color(0xFF7B2CBF),
                tertiaryGlow = Color(0xFFFF9E00),
                accentColor = Color(0xFFFF5C8A),
                deepBase = Color(0xFF07050A)
            ),
            artworkStyle = ArtworkStyle.VELVET_RIBBONS,
            lyrics = buildLyricList(rawLines, durationMs)
        )
    }

    private fun createNeonHorizonTrack(): SongTrack {
        val durationMs = 72_000L
        val rawLines = listOf(
            Triple(0L, "•••", null),
            Triple(3_500L, "City lights reflect on midnight glass", "城市霓虹映照在午夜镜面"),
            Triple(8_500L, "Chasing echoes as the shadows pass", "追逐回声 穿过掠过的暗影"),
            Triple(13_800L, "Every synth wave pulls us through the dark", "每一道合成器声浪将我们引向深空"),
            Triple(19_200L, "Electric pulse ignites a quiet spark", "电子脉搏点亮寂静的火花"),
            Triple(24_800L, "Hold the rhythm right upon your wrist", "将律动稳稳握在腕间"),
            Triple(30_200L, "Golden horizon through the neon mist", "穿过霓虹薄雾的金色地平线"),
            Triple(35_800L, "•••", null),
            Triple(40_500L, "We are running where the starlight bends", "我们在星光弯曲处奔跑"),
            Triple(46_000L, "Time slows down before the chorus ends", "在副歌落幕前 时间悄然放缓"),
            Triple(51_800L, "Feel the bassline breathing underneath", "感受低音线条在心底呼吸"),
            Triple(57_500L, "Crystal clarity in every beat", "每一拍都澄澈如水晶"),
            Triple(63_500L, "Fading softly into cyber dreams", "轻柔消融于赛博梦境")
        )
        return SongTrack(
            id = "neon_horizon",
            title = "Neon Horizon",
            artist = "Midnight Synth",
            album = "Cybernetic Tides",
            durationMs = durationMs,
            palette = AlbumPalette(
                primaryGlow = Color(0xFF00F0FF),
                secondaryGlow = Color(0xFFFF007F),
                tertiaryGlow = Color(0xFF5A189A),
                accentColor = Color(0xFF00F0FF),
                deepBase = Color(0xFF04070F)
            ),
            artworkStyle = ArtworkStyle.NEON_SYNTHWAVE,
            lyrics = buildLyricList(rawLines, durationMs)
        )
    }

    private fun createStarlightOdysseyTrack(): SongTrack {
        val durationMs = 74_000L
        val rawLines = listOf(
            Triple(0L, "•••", null),
            Triple(3_800L, "穿过零重力的数据星云", "Drifting through zero-gravity data nebulae"),
            Triple(8_800L, "表冠轻旋 拨动时间的刻度", "A gentle turn of the crown shifts the scale of time"),
            Triple(14_200L, "白昼的喧嚣在身后渐渐隐退", "The noise of daylight slowly fades behind"),
            Triple(19_800L, "只剩纯粹的音符在黑暗中发光", "Only pure notes shine inside the dark"),
            Triple(25_500L, "像北极星锁定了航向", "Like Polaris locking onto our course"),
            Triple(31_000L, "每一句词行都踏着节拍跃动", "Every lyric line leaps upon the beat"),
            Triple(36_500L, "•••", null),
            Triple(41_000L, "失重漫游在无边的夜色里", "Weightless roaming in the boundless night"),
            Triple(46_800L, "用最小的光束 照亮最深的海", "Using the smallest beam to light the deepest sea"),
            Triple(52_500L, "当尾奏缓缓收拢成一束微芒", "When the outro gently folds into a faint gleam"),
            Triple(58_500L, "我依然听见 你心底的歌唱", "I can still hear the song deep in your heart"),
            Triple(65_000L, "晚安 宇宙漫游者", "Goodnight, cosmic voyager")
        )
        return SongTrack(
            id = "starlight_odyssey",
            title = "星河漫游指南",
            artist = "银河电台 Cosmic FM",
            album = "Zero Gravity 零重力",
            durationMs = durationMs,
            palette = AlbumPalette(
                primaryGlow = Color(0xFF6C63FF),
                secondaryGlow = Color(0xFF00B4D8),
                tertiaryGlow = Color(0xFFE0AAFF),
                accentColor = Color(0xFFA594F9),
                deepBase = Color(0xFF05060D)
            ),
            artworkStyle = ArtworkStyle.STARLIGHT_COSMOS,
            lyrics = buildLyricList(rawLines, durationMs)
        )
    }

    private fun createAuroraPulseTrack(): SongTrack {
        val durationMs = 70_000L
        val rawLines = listOf(
            Triple(0L, "•••", null),
            Triple(3_200L, "Breathe in the silence of the northern sky", "深吸北方天际的静谧"),
            Triple(8_200L, "Emerald ribbons dancing way up high", "翡翠色光带在高空翩翩起舞"),
            Triple(13_500L, "Zero glare on the obsidian screen", "黑曜石屏幕上的零眩光深邃感"),
            Triple(18_800L, "Purest contrast that you've ever seen", "前所未见的纯净高对比度"),
            Triple(24_200L, "Step by step the melody unfolds", "旋律一步步徐徐展开"),
            Triple(29_500L, "Warming up the winter's quiet cold", "温暖了冬夜里的静谧寒意"),
            Triple(34_800L, "•••", null),
            Triple(39_500L, "Follow the glow as the lyrics rise", "伴随歌词缓缓升起 追随微光"),
            Triple(45_000L, "Smooth as silk before your eyes", "在眼前如丝绸般顺滑流淌"),
            Triple(50_800L, "Every syllable locked in time", "每一个音节都精准契合节拍"),
            Triple(56_500L, "Lost inside the ambient chime", "沉浸在空灵的泛音之中"),
            Triple(62_500L, "Drifting into tranquil peace", "漂向安宁的彼岸")
        )
        return SongTrack(
            id = "aurora_pulse",
            title = "Aurora Pulse",
            artist = "Nordik Drift",
            album = "Polaris Sessions",
            durationMs = durationMs,
            palette = AlbumPalette(
                primaryGlow = Color(0xFF00E676),
                secondaryGlow = Color(0xFF00B4D8),
                tertiaryGlow = Color(0xFF7209B7),
                accentColor = Color(0xFF00F5D4),
                deepBase = Color(0xFF040908)
            ),
            artworkStyle = ArtworkStyle.AURORA_BOREALIS,
            lyrics = buildLyricList(rawLines, durationMs)
        )
    }

    private fun buildLyricList(
        rawLines: List<Triple<Long, String, String?>>,
        totalDurationMs: Long
    ): List<LyricLine> {
        return rawLines.mapIndexed { idx, (startMs, text, translation) ->
            val endMs = if (idx + 1 < rawLines.size) {
                rawLines[idx + 1].first
            } else {
                totalDurationMs
            }
            val isInterlude = text == "•••"
            val words = if (isInterlude) {
                emptyList()
            } else {
                LrcParser.buildSyllableWords(
                    text = text,
                    startMs = startMs,
                    endMs = (endMs - 300L).coerceAtLeast(startMs + 500L)
                )
            }
            LyricLine(
                id = idx,
                startMs = startMs,
                endMs = endMs,
                text = text,
                translation = translation,
                isInterlude = isInterlude,
                words = words
            )
        }
    }
}
