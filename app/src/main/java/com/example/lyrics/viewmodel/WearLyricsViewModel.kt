package com.example.lyrics.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lyrics.model.DarkBackgroundMode
import com.example.lyrics.model.LrcParser
import com.example.lyrics.model.LyricFontScale
import com.example.lyrics.model.LyricLine
import com.example.lyrics.model.SampleSongsRepository
import com.example.lyrics.model.SongTrack
import com.example.lyrics.model.WatchShapeMode
import com.example.lyrics.model.WearLyricsConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WearLyricsUiState(
    val tracks: List<SongTrack> = SampleSongsRepository.tracks,
    val currentTrackIndex: Int = 0,
    val isPlaying: Boolean = true,
    val currentPositionMs: Long = 4_200L, // Start right as first lyric line begins for immediate visual impact
    val activeLineIndex: Int = 1,
    val isUserBrowsingLyrics: Boolean = false,
    val config: WearLyricsConfig = WearLyricsConfig(),
    val playbackSpeed: Float = 1.0f
) {
    val currentTrack: SongTrack
        get() = tracks.getOrElse(currentTrackIndex) { tracks.first() }

    val currentLyrics: List<LyricLine>
        get() = currentTrack.lyrics

    val progressFraction: Float
        get() = if (currentTrack.durationMs > 0) {
            (currentPositionMs.toFloat() / currentTrack.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

class WearLyricsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WearLyricsUiState())
    val uiState: StateFlow<WearLyricsUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var autoResumeSyncJob: Job? = null

    init {
        startPlaybackClock()
    }

    private fun startPlaybackClock() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val tickMs = 16L // ~60fps smooth per-character left-to-right karaoke fill
            var lastTime = System.currentTimeMillis()
            while (isActive) {
                delay(tickMs)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTime).coerceIn(8L, 64L)
                lastTime = now
                val state = _uiState.value
                if (state.isPlaying) {
                    val delta = (elapsed * state.playbackSpeed).toLong()
                    val nextPos = state.currentPositionMs + delta
                    if (nextPos >= state.currentTrack.durationMs) {
                        val nextTrackIdx = (state.currentTrackIndex + 1) % state.tracks.size
                        val nextTrack = state.tracks[nextTrackIdx]
                        _uiState.update {
                            it.copy(
                                currentTrackIndex = nextTrackIdx,
                                currentPositionMs = 0L,
                                activeLineIndex = computeActiveLineIndex(nextTrack.lyrics, 0L),
                                isUserBrowsingLyrics = false
                            )
                        }
                    } else {
                        val newActiveIdx = computeActiveLineIndex(state.currentLyrics, nextPos)
                        _uiState.update {
                            it.copy(
                                currentPositionMs = nextPos,
                                activeLineIndex = newActiveIdx
                            )
                        }
                    }
                }
            }
        }
    }

    fun computeActiveLineIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return 0
        val idx = lyrics.indexOfLast { positionMs >= it.startMs }
        return if (idx >= 0) idx else 0
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun seekToPosition(positionMs: Long) {
        val track = _uiState.value.currentTrack
        val clamped = positionMs.coerceIn(0L, track.durationMs)
        val activeIdx = computeActiveLineIndex(track.lyrics, clamped)
        autoResumeSyncJob?.cancel()
        _uiState.update {
            it.copy(
                currentPositionMs = clamped,
                activeLineIndex = activeIdx,
                isUserBrowsingLyrics = false
            )
        }
    }

    fun seekToLyricLine(line: LyricLine) {
        seekToPosition(line.startMs + 20L)
        if (!_uiState.value.isPlaying) {
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    fun nextTrack() {
        autoResumeSyncJob?.cancel()
        _uiState.update { state ->
            val nextIdx = (state.currentTrackIndex + 1) % state.tracks.size
            val nextTrack = state.tracks[nextIdx]
            val startPos = nextTrack.lyrics.getOrNull(1)?.startMs ?: 0L
            state.copy(
                currentTrackIndex = nextIdx,
                currentPositionMs = startPos,
                activeLineIndex = computeActiveLineIndex(nextTrack.lyrics, startPos),
                isUserBrowsingLyrics = false,
                isPlaying = true
            )
        }
    }

    fun previousTrack() {
        autoResumeSyncJob?.cancel()
        _uiState.update { state ->
            if (state.currentPositionMs > 8_000L) {
                val resetPos = state.currentLyrics.getOrNull(1)?.startMs ?: 0L
                state.copy(
                    currentPositionMs = resetPos,
                    activeLineIndex = computeActiveLineIndex(state.currentLyrics, resetPos),
                    isUserBrowsingLyrics = false
                )
            } else {
                val prevIdx = if (state.currentTrackIndex - 1 < 0) state.tracks.lastIndex else state.currentTrackIndex - 1
                val prevTrack = state.tracks[prevIdx]
                val startPos = prevTrack.lyrics.getOrNull(1)?.startMs ?: 0L
                state.copy(
                    currentTrackIndex = prevIdx,
                    currentPositionMs = startPos,
                    activeLineIndex = computeActiveLineIndex(prevTrack.lyrics, startPos),
                    isUserBrowsingLyrics = false,
                    isPlaying = true
                )
            }
        }
    }

    fun selectTrack(index: Int) {
        val tracks = _uiState.value.tracks
        if (index !in tracks.indices) return
        autoResumeSyncJob?.cancel()
        val chosen = tracks[index]
        val startPos = chosen.lyrics.getOrNull(1)?.startMs ?: 0L
        _uiState.update {
            it.copy(
                currentTrackIndex = index,
                currentPositionMs = startPos,
                activeLineIndex = computeActiveLineIndex(chosen.lyrics, startPos),
                isUserBrowsingLyrics = false,
                isPlaying = true
            )
        }
    }

    /**
     * Triggered when the user drags the lyrics list manually on the watch face.
     * Temporarily suspends auto-centering and schedules a 3.5s auto-resume timer.
     */
    fun onUserManualScroll() {
        _uiState.update { it.copy(isUserBrowsingLyrics = true) }
        autoResumeSyncJob?.cancel()
        autoResumeSyncJob = viewModelScope.launch {
            delay(3500L)
            _uiState.update { it.copy(isUserBrowsingLyrics = false) }
        }
    }

    fun resumeAutoSyncImmediately() {
        autoResumeSyncJob?.cancel()
        _uiState.update { it.copy(isUserBrowsingLyrics = false) }
    }

    /**
     * Simulates or handles Wear OS Rotary Crown input to step forward/backward by lyric line.
     */
    fun stepLyricLineByRotary(delta: Int) {
        val state = _uiState.value
        val lyrics = state.currentLyrics
        if (lyrics.isEmpty()) return
        val targetIndex = (state.activeLineIndex + delta).coerceIn(0, lyrics.lastIndex)
        val targetLine = lyrics[targetIndex]
        seekToLyricLine(targetLine)
    }

    fun setWatchShapeMode(mode: WatchShapeMode) {
        _uiState.update { it.copy(config = it.config.copy(watchShapeMode = mode)) }
    }

    fun setDarkBackgroundMode(mode: DarkBackgroundMode) {
        _uiState.update { it.copy(config = it.config.copy(darkBackgroundMode = mode)) }
    }

    fun setFontScale(scale: LyricFontScale) {
        _uiState.update { it.copy(config = it.config.copy(fontScale = scale)) }
    }

    fun toggleTranslation() {
        _uiState.update {
            it.copy(config = it.config.copy(showTranslation = !it.config.showTranslation))
        }
    }

    fun toggleWordByWordGlow() {
        _uiState.update {
            it.copy(config = it.config.copy(enableWordByWordGlow = !it.config.enableWordByWordGlow))
        }
    }

    fun togglePeripheralBlurFade() {
        _uiState.update {
            it.copy(config = it.config.copy(enablePeripheralBlurFade = !it.config.enablePeripheralBlurFade))
        }
    }

    fun applyCustomLrc(title: String, artist: String, rawLrc: String) {
        val parsedLines = LrcParser.parseLrc(rawLrc, totalDurationMs = 90_000L)
        if (parsedLines.isEmpty()) return
        val maxEnd = parsedLines.maxOf { it.endMs }.coerceAtLeast(30_000L)
        val currentTrack = _uiState.value.currentTrack
        val customTrack = SongTrack(
            id = "custom_${System.currentTimeMillis()}",
            title = title.ifBlank { "自定义腕上歌曲" },
            artist = artist.ifBlank { "Custom Artist" },
            album = "LRC Live Import",
            durationMs = maxEnd,
            palette = currentTrack.palette,
            artworkStyle = currentTrack.artworkStyle,
            lyrics = parsedLines
        )
        _uiState.update { state ->
            val updatedTracks = listOf(customTrack) + state.tracks.filterNot { it.id.startsWith("custom_") }
            val initialPos = parsedLines.first().startMs
            state.copy(
                tracks = updatedTracks,
                currentTrackIndex = 0,
                currentPositionMs = initialPos,
                activeLineIndex = 0,
                isUserBrowsingLyrics = false,
                isPlaying = true
            )
        }
    }
}
