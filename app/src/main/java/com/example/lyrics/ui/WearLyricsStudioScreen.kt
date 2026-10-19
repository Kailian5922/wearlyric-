package com.example.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lyrics.viewmodel.WearLyricsViewModel

/**
 * Pure Wear OS Lyrics Screen hosting [WearLyricsPage] directly without any media control
 * components or song metadata overlays.
 */
@Composable
fun WearLyricsStudioScreen(
    viewModel: WearLyricsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pageState = rememberWearLyricsPageState(initialFirstVisibleItemIndex = uiState.activeLineIndex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF05060A))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        WearLyricsPage(
            track = uiState.currentTrack,
            currentPositionMs = uiState.currentPositionMs,
            activeLineIndex = uiState.activeLineIndex,
            isPlaying = uiState.isPlaying,
            isUserBrowsingLyrics = uiState.isUserBrowsingLyrics,
            config = uiState.config,
            onLyricLineClick = viewModel::seekToLyricLine,
            onUserManualScroll = viewModel::onUserManualScroll,
            onResumeSyncClick = viewModel::resumeAutoSyncImmediately,
            pageState = pageState,
            modifier = Modifier.fillMaxSize()
        )
    }
}
