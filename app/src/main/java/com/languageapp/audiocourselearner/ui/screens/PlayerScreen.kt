package com.languageapp.audiocourselearner.ui.screens

import android.Manifest
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.ui.components.AnnotationDialog
import com.languageapp.audiocourselearner.ui.components.PlayerControls
import com.languageapp.audiocourselearner.ui.components.VisualContent
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val window = (context as? android.app.Activity)?.window

    DisposableEffect(isFullscreen) {
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { insetsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val isVideo = remember(viewModel.currentLesson) {
        val path = viewModel.currentLesson?.audioPath ?: ""
        path.substringAfterLast('.', "").lowercase() in listOf("mp4", "mkv", "avi", "mov", "webm")
    }

    // --- Dialog State ---
    var showEditorDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var dialogInitialTimestamp by remember { mutableStateOf(0L) }
    var dialogInitialPhrase by remember { mutableStateOf("") }

    if (showEditorDialog) {
        AnnotationDialog(
            audioFileName = File(viewModel.currentLesson?.audioPath ?: "").name,
            initialTimestampMs = dialogInitialTimestamp,
            initialPhrase = dialogInitialPhrase,
            isEditMode = isEditMode,
            onDismiss = { showEditorDialog = false },
            onSave = { newMs, phrase ->
                if (isEditMode) {
                    viewModel.editAnnotation(dialogInitialTimestamp, newMs, phrase)
                } else {
                    viewModel.addAnnotation(newMs, phrase)
                }
                showEditorDialog = false
            },
            onDelete = if (isEditMode) {
                {
                    viewModel.deleteAnnotation(dialogInitialTimestamp)
                    showEditorDialog = false
                }
            } else null
        )
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Column {
                            Text(course.name, style = MaterialTheme.typography.titleMedium)
                            Text(viewModel.currentLessonTitle + (if (isEditorMode) " [EDITOR]" else ""), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = {
                        var showSpeedMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSpeedMenu = true }) {
                                Icon(Icons.Default.Speed, "Playback Speed", tint = if (viewModel.playbackSpeed == 1.0f) LocalContentColor.current else MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text(if (speed == 1.0f) "Normal" else "${speed}x") },
                                        onClick = { viewModel.setSpeed(speed); showSpeedMenu = false }
                                    )
                                }
                            }
                        }
                        if (isEditorMode) {
                            IconButton(onClick = {
                                viewModel.cancelInteraction()
                                dialogInitialTimestamp = viewModel.getCurrentPosition()
                                dialogInitialPhrase = ""
                                isEditMode = false
                                showEditorDialog = true
                            }) {
                                Icon(Icons.Default.Add, "Add Timestamp")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
        ) {

            // Interaction Feedback Overlay
            if (viewModel.feedbackMessage != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (isFullscreen) 32.dp else 16.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .zIndex(10f)
                ) {
                    Text(text = viewModel.feedbackMessage!!, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                }
            }

            // Layout
            if (isFullscreen) {
                VisualContent(Modifier.fillMaxSize(), isVideo, true, isEditorMode, { isFullscreen = false }, viewModel, isLandscape)
            } else if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(0.45f).fillMaxHeight()) {
                        VisualContent(Modifier.fillMaxSize(), isVideo, false, isEditorMode, { isFullscreen = true }, viewModel, isLandscape)
                    }
                    Column(modifier = Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp), verticalArrangement = Arrangement.Center) {
                        PlayerControls(viewModel, isEditorMode) { ms, phrase ->
                            dialogInitialTimestamp = ms; dialogInitialPhrase = phrase; isEditMode = true; showEditorDialog = true
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        VisualContent(Modifier.fillMaxSize(), isVideo, false, isEditorMode, { isFullscreen = true }, viewModel, isLandscape)
                    }
                    PlayerControls(viewModel, isEditorMode) { ms, phrase ->
                        dialogInitialTimestamp = ms; dialogInitialPhrase = phrase; isEditMode = true; showEditorDialog = true
                    }
                }
            }
        }
    }
}