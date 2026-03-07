package com.languageapp.audiocourselearner.ui.screens

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.viewmodel.PlayerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    course: Course,
    startLessonId: String,
    isEditorMode: Boolean,
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {

    LaunchedEffect(course.id) {
        viewModel.initialize(course, startLessonId)
        viewModel.skipDurationMs = if (isEditorMode) 5000 else 10000
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var editModeTimestamp by remember { mutableStateOf<Long?>(null) }

    // --- DIALOG STATE ---
    var showEditorDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var dialogTimestampText by remember { mutableStateOf("") }
    var dialogPhraseText by remember { mutableStateOf("") }

    // Helper to open dialog
    fun openDialog(timestampMs: Long, phrase: String = "", isEdit: Boolean) {
        viewModel.cancelInteraction()
        isEditMode = isEdit
        val totalSeconds = timestampMs / 1000
        val mm = totalSeconds / 60
        val ss = totalSeconds % 60
        dialogTimestampText = "%02d:%02d".format(mm, ss)
        dialogPhraseText = phrase
        editModeTimestamp = if (isEdit) timestampMs else null
        showEditorDialog = true
    }

    if (showEditorDialog) {
        AlertDialog(
            onDismissRequest = { showEditorDialog = false },
            title = { Text(if (!isEditMode) "Add Annotation" else "Edit Annotation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Audio: ${File(viewModel.currentLesson?.audioPath ?: "").name}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    OutlinedTextField(
                        value = dialogTimestampText,
                        onValueChange = { dialogTimestampText = it },
                        label = { Text("Timestamp (MM:SS)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogPhraseText,
                        onValueChange = { dialogPhraseText = it },
                        label = { Text("Phrase / Sentence") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val parts = dialogTimestampText.split(":")
                        if (parts.size == 2) {
                            val mm = parts[0].toLong()
                            val ss = parts[1].toLong()
                            val newMs = (mm * 60 + ss) * 1000

                            if (isEditMode && editModeTimestamp != null) {
                                viewModel.editAnnotation(editModeTimestamp!!, newMs, dialogPhraseText)
                            } else {
                                viewModel.addAnnotation(newMs, dialogPhraseText)
                            }
                            showEditorDialog = false
                        }
                    } catch (e: Exception) { }
                }) { Text(if (!isEditMode) "Add" else "Save") }
            },
            dismissButton = {
                Row {
                    if (isEditMode && editModeTimestamp != null) {
                        TextButton(
                            onClick = {
                                viewModel.deleteAnnotation(editModeTimestamp!!)
                                showEditorDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, "Delete")
                            Text("Delete")
                        }
                    }
                    TextButton(onClick = { showEditorDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Hide TopBar in landscape on small screens to save space, or keep it if preferred.
            // Keeping it here for consistency.
            TopAppBar(
                title = {
                    Column {
                        Text(course.name, style = MaterialTheme.typography.titleMedium)
                        Text(viewModel.currentLessonTitle + (if(isEditorMode) " [EDITOR]" else ""), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (isEditorMode) {
                        IconButton(onClick = {
                            openDialog(timestampMs = viewModel.getCurrentPosition(), isEdit = false)
                        }) {
                            Icon(Icons.Default.Add, "Add Timestamp")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {

            // --- 1. FEEDBACK OVERLAY (Always on top) ---
            if (viewModel.feedbackMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .zIndex(10f)
                ) {
                    Text(
                        text = viewModel.feedbackMessage!!,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // --- DEFINING UI SECTIONS FOR RE-USE ---

            // A. The Big Listening Button Area
            val listeningVisualContent: @Composable BoxScope.() -> Unit = {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Reduce size slightly in landscape to fit height
                    val circleSize = if (isLandscape) 200.dp else 260.dp
                    val buttonSize = if (isLandscape) 140.dp else 180.dp

                    Box(modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    )

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

            // B. The Controls Area (Info, Editor Buttons, Slider, Playback)
            val controlsContent: @Composable ColumnScope.() -> Unit = {

                // 1. Info Card (Upcoming Phrase)
                Box(
                    modifier = Modifier.height(40.dp).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isEditorMode && viewModel.nearbyAnnotation != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        ) {
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

                // 2. Editor "Edit Entry" Button
                if (isEditorMode) {
                    val targetAnnotation = viewModel.nearbyAnnotation
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            enabled = targetAnnotation != null,
                            onClick = {
                                if (targetAnnotation != null) {
                                    viewModel.cancelInteraction()
                                    editModeTimestamp = targetAnnotation.timestampMs
                                    openDialog(targetAnnotation.timestampMs, targetAnnotation.expectedPhrase, isEdit = true)
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

                // 3. Playback Controls Wrapper
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

                    // Editor Seek Buttons
                    if (isEditorMode) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            IconButton(
                                enabled = viewModel.hasPreviousAnnotation(),
                                onClick = { viewModel.jumpToPreviousAnnotation() }
                            ) { Icon(Icons.Default.FirstPage, "Previous") }

                            Spacer(Modifier.width(40.dp))

                            IconButton(
                                enabled = viewModel.hasNextAnnotation(),
                                onClick = { viewModel.jumpToNextAnnotation() }
                            ) { Icon(Icons.Default.LastPage, "Next") }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Times and Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(viewModel.currentTimeLabel, style = MaterialTheme.typography.bodySmall)
                        Text(viewModel.totalTimeLabel, style = MaterialTheme.typography.bodySmall)
                    }

                    if (isEditorMode) {
                        AnnotatedSlider(
                            value = viewModel.currentProgress,
                            onValueChange = { viewModel.seekTo(it) },
                            durationMs = viewModel.durationMs,
                            annotations = viewModel.visibleAnnotations,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Slider(
                            value = viewModel.currentProgress,
                            onValueChange = { viewModel.seekTo(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Media Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.playPreviousLesson() }, enabled = viewModel.hasPrevious()) {
                            Icon(Icons.Default.SkipPrevious, "Prev", modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { viewModel.skipBack() }) {
                            Icon(if (isEditorMode) Icons.Default.Replay5 else Icons.Default.Replay10, "Rewind")
                        }
                        Surface(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(64.dp), // Slightly smaller for better fit
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
                        IconButton(onClick = { viewModel.skipForward() }) {
                            Icon(if (isEditorMode) Icons.Default.Forward5 else Icons.Default.Forward10, "Forward")
                        }
                        IconButton(onClick = { viewModel.playNextLesson() }, enabled = viewModel.hasNext()) {
                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            // --- MAIN LAYOUT SWITCHING ---
            if (isLandscape) {
                // LANDSCAPE: Side-by-Side Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Visuals
                    Box(
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        listeningVisualContent()
                    }

                    // Right Side: Controls (Scrollable just in case)
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        controlsContent()
                    }
                }
            } else {
                // PORTRAIT: Vertical Stack (Original Logic)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        listeningVisualContent()
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        controlsContent()
                    }
                }
            }
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
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .zIndex(1f)
                    .padding(horizontal = 10.dp)
            ) {
                val width = size.width
                val zoneColor = androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.6f)
                annotations.forEach { timestamp ->
                    val centerPct = timestamp.toFloat() / durationMs.toFloat()
                    val widthPct = 4000f / durationMs.toFloat()
                    val centerX = centerPct * width
                    val zoneWidth = widthPct * width
                    val startX = (centerX - zoneWidth / 2).coerceAtLeast(0f)
                    val endX = (centerX + zoneWidth / 2).coerceAtMost(width)
                    drawRect(
                        color = zoneColor,
                        topLeft = androidx.compose.ui.geometry.Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(endX - startX, size.height)
                    )
                }
            }
        }
    }
}