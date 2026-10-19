package com.example.lyrics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lyrics.viewmodel.WearLyricsViewModel

/**
 * Host container previewing the standalone [WearLyricsPage] component.
 */
@Composable
fun WearLyricsStudioScreen(
    viewModel: WearLyricsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        WearLyricsPage(
            lyrics = uiState.currentLyrics,
            currentPositionMs = uiState.currentPositionMs,
            activeLineIndex = uiState.activeLineIndex,
            isUserBrowsingLyrics = uiState.isUserBrowsingLyrics,
            onLyricLineClick = viewModel::seekToLyricLine,
            onUserManualScroll = viewModel::onUserManualScroll,
            modifier = Modifier.fillMaxSize()
        )
    }
}
