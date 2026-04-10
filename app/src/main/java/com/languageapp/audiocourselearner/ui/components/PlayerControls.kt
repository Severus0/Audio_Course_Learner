package com.languageapp.audiocourselearner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.languageapp.audiocourselearner.viewmodel.PlayerViewModel

@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    isEditorMode: Boolean,
    onEditCurrentAnnotation: (Long, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        // Editor Info Box
        Box(modifier = Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (isEditorMode && viewModel.nearbyAnnotation != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))) {
                    Text(
                        text = "Upcoming: ${viewModel.nearbyAnnotation?.expectedPhrase}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (isEditorMode) {
            val targetAnnotation = viewModel.nearbyAnnotation
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    enabled = targetAnnotation != null,
                    onClick = {
                        if (targetAnnotation != null) {
                            viewModel.cancelInteraction()
                            onEditCurrentAnnotation(targetAnnotation.timestampMs, targetAnnotation.expectedPhrase)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        disabledContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                    )
                ) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Entry")
                }
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }

        // Editor Jump Controls
        if (isEditorMode) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(enabled = viewModel.hasPreviousAnnotation(), onClick = { viewModel.jumpToPreviousAnnotation() }) { Icon(Icons.Default.FirstPage, "Previous") }
                Spacer(Modifier.width(40.dp))
                IconButton(enabled = viewModel.hasNextAnnotation(), onClick = { viewModel.jumpToNextAnnotation() }) { Icon(Icons.Default.LastPage, "Next") }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Timer
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(viewModel.currentTimeLabel, style = MaterialTheme.typography.bodySmall)
            Text(viewModel.totalTimeLabel, style = MaterialTheme.typography.bodySmall)
        }

        // Slider
        if (isEditorMode) {
            AnnotatedSlider(
                value = viewModel.currentProgress,
                onValueChange = { viewModel.seekTo(it) },
                durationMs = viewModel.durationMs,
                annotations = viewModel.visibleAnnotations,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Slider(value = viewModel.currentProgress, onValueChange = { viewModel.seekTo(it) }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playback Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.playPreviousLesson() }, enabled = viewModel.hasPrevious()) { Icon(Icons.Default.SkipPrevious, "Prev", modifier = Modifier.size(32.dp)) }
            IconButton(onClick = { viewModel.skipBack() }) { Icon(if (isEditorMode) Icons.Default.Replay5 else Icons.Default.Replay10, "Rewind") }
            Surface(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Icon(
                    if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Play",
                    modifier = Modifier.padding(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(onClick = { viewModel.skipForward() }) { Icon(if (isEditorMode) Icons.Default.Forward5 else Icons.Default.Forward10, "Forward") }
            IconButton(onClick = { viewModel.playNextLesson() }, enabled = viewModel.hasNext()) { Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
fun AnnotatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    durationMs: Long,
    annotations: List<Long>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        if (durationMs > 0) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .zIndex(1f)
                    .padding(horizontal = 10.dp)
            ) {
                val width = size.width
                val zoneColor = Color.Yellow.copy(alpha = 0.6f)
                annotations.forEach { timestamp ->
                    val centerPct = timestamp.toFloat() / durationMs.toFloat()
                    val widthPct = 4000f / durationMs.toFloat()
                    val centerX = centerPct * width
                    val zoneWidth = widthPct * width
                    val startX = (centerX - zoneWidth / 2).coerceAtLeast(0f)
                    val endX = (centerX + zoneWidth / 2).coerceAtMost(width)
                    drawRect(
                        color = zoneColor,
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, size.height)
                    )
                }
            }
        }
    }
}