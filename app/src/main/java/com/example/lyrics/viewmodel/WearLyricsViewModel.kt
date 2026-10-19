package com.example.lyrics.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lyrics.model.LyricLine
import com.example.lyrics.model.SampleSongsRepository
import com.example.lyrics.model.SongTrack
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
    val currentPositionMs: Long = 4_200L,
    val activeLineIndex: Int = 1,
    val isUserBrowsingLyrics: Boolean = false
) {
    val currentTrack: SongTrack
        get() = tracks.getOrElse(currentTrackIndex) { tracks.first() }

    val currentLyrics: List<LyricLine>
        get() = currentTrack.lyrics
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
                    val nextPos = state.currentPositionMs + elapsed
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

    fun seekToLyricLine(line: LyricLine) {
        val track = _uiState.value.currentTrack
        val clamped = (line.startMs + 20L).coerceIn(0L, track.durationMs)
        val activeIdx = computeActiveLineIndex(track.lyrics, clamped)
        autoResumeSyncJob?.cancel()
        _uiState.update {
            it.copy(
                currentPositionMs = clamped,
                activeLineIndex = activeIdx,
                isUserBrowsingLyrics = false,
                isPlaying = true
            )
        }
    }

    fun onUserManualScroll() {
        _uiState.update { it.copy(isUserBrowsingLyrics = true) }
        autoResumeSyncJob?.cancel()
        autoResumeSyncJob = viewModelScope.launch {
            delay(3000L)
            _uiState.update { it.copy(isUserBrowsingLyrics = false) }
        }
    }
}
