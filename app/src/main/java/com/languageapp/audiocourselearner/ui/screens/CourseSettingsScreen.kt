package com.languageapp.audiocourselearner.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.model.Lesson
import com.languageapp.audiocourselearner.utils.CourseExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSettingsScreen(
    course: Course,
    onBackClick: () -> Unit,
    onEditLessonText: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    // --- 1. FULL EXPORT LAUNCHER (Save As) ---
    val fullExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
                // Run the heavy 900MB export in background
                val success = withContext(Dispatchers.IO) {
                    CourseExporter.exportCourseToZip(context, course, uri)
                }
                isExporting = false
                if (success) {
                    Toast.makeText(context, "Full Course Exported!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- 2. TEXT ONLY SHARE HELPER ---
    fun shareTextExport() {
        isExporting = true
        scope.launch {
            // CALL NEW FUNCTION
            val file = withContext(Dispatchers.IO) {
                CourseExporter.createTranscriptsZipExport(context, course)
            }
            isExporting = false
            if (file != null) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        // CHANGE MIME TYPE TO ZIP
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Transcripts"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings: ${course.name}") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

            // --- EXPORT CARD ---
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isExporting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Processing...")
                        }
                    } else {
                        // BUTTON 1: Text Only (Safe)
                        OutlinedButton(
                            onClick = { shareTextExport() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.TextSnippet, null)
                            Spacer(Modifier.width(8.dp))
                            // UPDATE LABEL
                            Text("Export Transcripts Only (ZIP)")
                        }

                        Spacer(Modifier.height(8.dp))

                        // BUTTON 2: Full Zip (Heavy)
                        Button(
                            onClick = {
                                try {
                                    // Try to open the System File Picker
                                    fullExportLauncher.launch("${course.name}_full.zip")
                                } catch (e: ActivityNotFoundException) {
                                    // Handle devices that crash (Paranoid Android / Graphene without file manager)
                                    Toast.makeText(context, "Error: No File Manager found to save the file.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Full Course (ZIP)")
                        }
                    }
                }
            }

            HorizontalDivider()

            // --- LESSON LIST ---
            Text("Raw File Editor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(course.lessons) { lesson ->
                    ListItem(
                        headlineContent = { Text(lesson.title) },
                        supportingContent = { Text(File(lesson.transcriptionPath).name) },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        trailingContent = {
                            IconButton(
                                onClick = { CourseExporter.shareTranscriptFile(context, lesson) }
                            ) {
                                Icon(Icons.Default.Download, "Export Lesson")
                            }
                        },
                        modifier = Modifier.clickable { onEditLessonText(lesson.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// --- SUB-SCREEN: RAW TEXT EDITOR ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawFileEditorScreen(
    lesson: Lesson,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    // Load text
    var textContent by remember { mutableStateOf("") }

    LaunchedEffect(lesson) {
        val file = File(lesson.transcriptionPath)
        if (file.exists()) {
            textContent = file.readText()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editing ${lesson.title}") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        try {
                            File(lesson.transcriptionPath).writeText(textContent)
                            Toast.makeText(context, "File Saved", Toast.LENGTH_SHORT).show()
                            onBackClick() // Go back after save
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                modifier = Modifier.fillMaxSize(),
                label = { Text("Raw content (Timestamp Phrase)") }
            )
        }
    }
}