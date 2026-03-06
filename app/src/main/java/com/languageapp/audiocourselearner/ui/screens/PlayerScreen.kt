package com.languageapp.audiocourselearner.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.model.Lesson
import com.languageapp.audiocourselearner.viewmodel.PlayerViewModel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import java.io.File
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex

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
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    var editModeTimestamp by remember { mutableStateOf<Long?>(null) }
    // --- DIALOG STATE ---
    var showEditorDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) } // true = edit, false = add
    var dialogTimestampText by remember { mutableStateOf("") }
    var dialogPhraseText by remember { mutableStateOf("") }

    // Helper to open dialog
    fun openDialog(timestampMs: Long, phrase: String = "", isEdit: Boolean) {
        // FIX 1: Explicitly Kill all audio/listening when dialog opens
        viewModel.cancelInteraction()

        isEditMode = isEdit
        val totalSeconds = timestampMs / 1000
        val mm = totalSeconds / 60
        val ss = totalSeconds % 60
        dialogTimestampText = "%02d:%02d".format(mm, ss)
        dialogPhraseText = phrase

        // Save the *original* timestamp so the VM knows which one to replace
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
                                // FIX 2: Pass the OLD timestamp (editModeTimestamp) to locate the item
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
                                // FIX 3: Pass timestamp for deletion
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
            TopAppBar(
                title = {
                    Column {
                        Text(course.name, style = MaterialTheme.typography.titleMedium)
                        Text(viewModel.currentLessonTitle + (if(isEditorMode) " [EDITOR]" else ""), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // --- SPEED CONTROL (Keep existing) ---
                    // (I'll assume you have the speed code from previous steps here)

                    // --- EDITOR ADD BUTTON ---
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
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            // FIX: Feedback Message at TOP
            if (viewModel.feedbackMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .zIndex(10f) // Ensure it's on top
                ) {
                    Text(
                        text = viewModel.feedbackMessage!!,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // --- VISUAL ---
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(260.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)))

                    val iconColor = if (viewModel.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer

                    val canTrigger = viewModel.currentExpectedPhrase != null || viewModel.nearbyAnnotation != null

                    Surface(
                        // Update onClick condition
                        onClick = {
                            if (canTrigger) viewModel.manualListenTrigger()
                        },
                        modifier = Modifier.size(180.dp),
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

                if (isEditorMode && viewModel.nearbyAnnotation != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "Upcoming: ${viewModel.nearbyAnnotation?.expectedPhrase}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    // Keep some spacing if nothing is there so layout doesn't jump too much
                    // Or remove if you prefer it collapsing
                    Spacer(Modifier.height(8.dp))
                }

                // --- EDITOR EDIT BUTTON ---
                // Show if Editor Mode AND we have a current phrase (meaning we are at a timestamp)
                if (isEditorMode) {
                    val targetAnnotation = viewModel.nearbyAnnotation

                    Button(
                        // Enabled only if we are near a timestamp (+/- 2s)
                        enabled = targetAnnotation != null,
                        onClick = {
                            if (targetAnnotation != null) {
                                // 1. KILL any current listening/playback
                                viewModel.cancelInteraction()

                                // 2. Set the ID for the dialog logic
                                editModeTimestamp = targetAnnotation.timestampMs

                                // 3. Open Dialog
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
                } else {
                    Spacer(Modifier.height(48.dp))
                }

                // --- CONTROLS ---
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(viewModel.currentTimeLabel, style = MaterialTheme.typography.bodySmall)
                        Text(viewModel.totalTimeLabel, style = MaterialTheme.typography.bodySmall)
                    }

                    Slider(value = viewModel.currentProgress, onValueChange = { viewModel.seekTo(it) }, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.playPreviousLesson() }, enabled = viewModel.hasPrevious()) {
                            Icon(Icons.Default.SkipPrevious, "Prev Lesson", modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { viewModel.skipBack() }) {
                            Icon(Icons.Default.Replay10, "Rewind", modifier = Modifier.size(32.dp))
                        }
                        Surface(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play/Pause",
                                modifier = Modifier.padding(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { viewModel.skipForward() }) {
                            Icon(Icons.Default.Forward10, "Forward", modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = { viewModel.playNextLesson() }, enabled = viewModel.hasNext()) {
                            Icon(Icons.Default.SkipNext, "Next Lesson", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}