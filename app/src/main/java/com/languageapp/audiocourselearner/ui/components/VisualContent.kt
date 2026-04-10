package com.languageapp.audiocourselearner.ui.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import com.languageapp.audiocourselearner.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VisualContent(
    modifier: Modifier = Modifier,
    isVideo: Boolean,
    isFullscreen: Boolean,
    isEditorMode: Boolean,
    onToggleFullscreen: () -> Unit,
    viewModel: PlayerViewModel,
    isLandscape: Boolean
) {
    Box(
        modifier = modifier.padding(if (isFullscreen) 0.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isVideo && viewModel.exoPlayer != null) {

            var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
            var playPauseVisible by remember { mutableStateOf(false) }
            var playPauseTimerTrigger by remember { mutableStateOf(0) }

            LaunchedEffect(flashIcon) {
                if (flashIcon != null) { delay(400); flashIcon = null }
            }
            LaunchedEffect(playPauseTimerTrigger) {
                if (playPauseVisible) { delay(2000); playPauseVisible = false }
            }

            AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.PlayerView(ctx).apply {
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView -> playerView.player = viewModel.exoPlayer },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(if (isFullscreen) androidx.compose.ui.graphics.RectangleShape else MaterialTheme.shapes.medium)
                    .background(Color.Black)
            )

            // Gesture Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = size.width
                                if (offset.x > width * 0.33f && offset.x < width * 0.66f) {
                                    playPauseVisible = true
                                    playPauseTimerTrigger++
                                }
                            },
                            onDoubleTap = { offset ->
                                val width = size.width
                                val rewindIcon = if (isEditorMode) Icons.Default.Replay5 else Icons.Default.Replay10
                                val forwardIcon = if (isEditorMode) Icons.Default.Forward5 else Icons.Default.Forward10

                                if (offset.x < width * 0.33f) {
                                    viewModel.skipBack()
                                    flashIcon = rewindIcon
                                } else if (offset.x > width * 0.66f) {
                                    viewModel.skipForward()
                                    flashIcon = forwardIcon
                                }
                            }
                        )
                    }
            ) {
                AnimatedVisibility(visible = flashIcon != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                    flashIcon?.let {
                        Icon(it, null, modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(12.dp), tint = Color.White)
                    }
                }
            }

            // Fullscreen Controls Overlay
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(onClick = onToggleFullscreen, modifier = Modifier.align(Alignment.BottomEnd).padding(if (isFullscreen) 24.dp else 8.dp).zIndex(5f)) {
                    Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle Fullscreen", tint = Color.White)
                }

                val isWaitingForVoice = viewModel.isListening || viewModel.currentExpectedPhrase != null

                if (isFullscreen && isWaitingForVoice) {
                    IconButton(onClick = { viewModel.togglePlayPause() }, modifier = Modifier.align(Alignment.Center).size(80.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape).zIndex(5f)) {
                        Icon(Icons.Default.PlayArrow, "Skip/Continue", tint = Color.White, modifier = Modifier.size(50.dp))
                    }
                } else {
                    AnimatedVisibility(visible = playPauseVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center).zIndex(5f)) {
                        IconButton(onClick = { viewModel.togglePlayPause(); playPauseTimerTrigger++ }, modifier = Modifier.size(80.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)) {
                            Icon(if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.White, modifier = Modifier.size(50.dp))
                        }
                    }
                }
            }

        } else {
            // Audio Only UI
            val circleSize = if (isLandscape) 200.dp else 260.dp
            val buttonSize = if (isLandscape) 140.dp else 180.dp

            Box(modifier = Modifier.size(circleSize).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)))

            val iconColor = if (viewModel.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
            val canTrigger = viewModel.currentExpectedPhrase != null || viewModel.nearbyAnnotation != null

            Surface(
                onClick = { if (canTrigger) viewModel.manualListenTrigger() },
                modifier = Modifier.size(buttonSize),
                shape = CircleShape,
                color = iconColor,
                shadowElevation = 12.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (viewModel.isListening) {
                        Icon(Icons.Default.Mic, "Listening", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onError)
                    } else {
                        Icon(Icons.Default.GraphicEq, "Audio", modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}