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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.languageapp.audiocourselearner.utils.LanguageUtils
import com.languageapp.audiocourselearner.utils.SilenceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSettingsScreen(
    course: Course,
    onBackClick: () -> Unit,
    onEditLessonText: (String) -> Unit,
    onCourseUpdated: (Course) -> Unit,
    onDeleteCourse: (Course) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    var isLangExpanded by remember { mutableStateOf(false) }
    var selectedLangName by remember { mutableStateOf(LanguageUtils.getName(course.languageCode)) }
    var selectedLangCode by remember { mutableStateOf(course.languageCode) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // --- PAUSE DETECTOR SETTINGS ---
    var minSilenceSec by remember { mutableFloatStateOf(1.5f) }
    var silenceDb by remember { mutableFloatStateOf(-25f) }

    // --- PAUSE DETECTOR STATE ---
    var processingLessonId by remember { mutableStateOf<String?>(null) }
    var processingProgress by remember { mutableFloatStateOf(0f) }
    var processingJob by remember { mutableStateOf<Job?>(null) }
    var lessonToOverwrite by remember { mutableStateOf<Lesson?>(null) }
    var generateAllWarning by remember { mutableStateOf(false) }

    val fullExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            scope.launch {
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

    fun saveLanguageChange() {
        try {
            val courseDir = File(context.filesDir, "courses/${course.id}")
            val configFile = File(courseDir, "config.json")
            val json = if (configFile.exists()) JSONObject(configFile.readText()) else JSONObject()

            json.put("language", selectedLangCode)
            configFile.writeText(json.toString(4))

            val updatedCourse = course.copy(
                languageCode = selectedLangCode,
                description = "${course.lessons.size} lessons • ${selectedLangCode.uppercase()}"
            )
            onCourseUpdated(updatedCourse)
            Toast.makeText(context, "Language updated", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving language", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTextExport() {
        isExporting = true
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                CourseExporter.createTranscriptsZipExport(context, course)
            }
            isExporting = false
            if (file != null) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
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

    suspend fun runDetectorOnLesson(lesson: Lesson) {
        processingLessonId = lesson.id
        processingProgress = 0f

        val pauses = withContext(Dispatchers.IO) {
            SilenceDetector.detectPauses(
                audioPath = lesson.audioPath,
                minimumSilenceDurationSec = minSilenceSec,
                silenceThresholdDb = silenceDb,
                onProgress = { p -> processingProgress = p }
            )
        }

        if (pauses.isNotEmpty()) {
            val sb = StringBuilder()
            pauses.forEach { ms ->
                val totalSeconds = ms / 1000
                val mm = totalSeconds / 60
                val ss = totalSeconds % 60
                val timeString = "%02d:%02d".format(mm, ss)
                sb.append("$timeString [TODO]\n")
            }
            withContext(Dispatchers.IO) {
                File(lesson.transcriptionPath).writeText(sb.toString())
            }
        }

        processingLessonId = null
    }

    // Dialog: Delete Course
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Course") },
            text = { Text("This will permanently delete the course and all its lessons.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteCourse(course)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    // Dialog: Overwrite Single Lesson
    if (lessonToOverwrite != null) {
        AlertDialog(
            onDismissRequest = { lessonToOverwrite = null },
            title = { Text("Overwrite Transcript?") },
            text = { Text("This will erase all existing text/timestamps in '${lessonToOverwrite?.title}' and replace them with empty pause markers.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = lessonToOverwrite!!
                    lessonToOverwrite = null
                    processingJob = scope.launch {
                        runDetectorOnLesson(target)
                        Toast.makeText(context, "Finished ${target.title}", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = { lessonToOverwrite = null }) { Text("Cancel") } }
        )
    }

    // Dialog: Overwrite All Lessons
    if (generateAllWarning) {
        AlertDialog(
            onDismissRequest = { generateAllWarning = false },
            title = { Text("Overwrite ALL Transcripts?") },
            text = { Text("This will erase all text in EVERY lesson and run the auto-pause detector on the entire course. This may take a few minutes.") },
            confirmButton = {
                TextButton(onClick = {
                    generateAllWarning = false
                    processingJob = scope.launch {
                        for (lesson in course.lessons) {
                            runDetectorOnLesson(lesson)
                        }
                        Toast.makeText(context, "Finished Course Processing!", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Start Processing", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { generateAllWarning = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings: ${course.name}") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- Language Settings Card ---
                Card(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Course Language", style = MaterialTheme.typography.titleMedium)
                        Text("Used for speech recognition accuracy.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))

                        ExposedDropdownMenuBox(
                            expanded = isLangExpanded,
                            onExpandedChange = { isLangExpanded = !isLangExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedLangName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Selected Language") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isLangExpanded,
                                onDismissRequest = { isLangExpanded = false }
                            ) {
                                LanguageUtils.getList().forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedLangName = name
                                            selectedLangCode = LanguageUtils.getCode(name)
                                            isLangExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { saveLanguageChange() },
                            enabled = selectedLangCode != course.languageCode,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Language")
                        }
                    }
                }

                // --- Auto Pause Generator Settings Card ---
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Auto-Generate Pauses", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(16.dp))

                        Text("Minimum Pause: ${"%.1f".format(minSilenceSec)}s", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = minSilenceSec,
                            onValueChange = { minSilenceSec = it },
                            valueRange = 0.5f..5.0f,
                            enabled = processingLessonId == null
                        )

                        Text("Volume Catch Threshold: ${silenceDb.toInt()} dB", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = silenceDb,
                            onValueChange = { silenceDb = it },
                            valueRange = -50f..-1f,
                            enabled = processingLessonId == null
                        )

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { generateAllWarning = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = processingLessonId == null
                        ) {
                            Text("Run on Entire Course")
                        }
                    }
                }

                // --- Export Card ---
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
                            OutlinedButton(
                                onClick = { shareTextExport() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.TextSnippet, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Export Transcripts Only (ZIP)")
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        fullExportLauncher.launch("${course.name}_full.zip")
                                    } catch (e: ActivityNotFoundException) {
                                        Toast.makeText(context, "Error: No File Manager found", Toast.LENGTH_LONG).show()
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
                Text("Raw File Editor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))

                // --- Lessons List with Progress Bars ---
                course.lessons.forEach { lesson ->
                    val isProcessingThis = processingLessonId == lesson.id

                    ListItem(
                        headlineContent = { Text(lesson.title) },
                        supportingContent = {
                            if (isProcessingThis) {
                                Column {
                                    Text("Scanning audio...", color = MaterialTheme.colorScheme.primary)
                                    LinearProgressIndicator(
                                        progress = { processingProgress },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    )
                                }
                            } else {
                                Text(File(lesson.transcriptionPath).name)
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        trailingContent = {
                            Row {
                                if (isProcessingThis) {
                                    IconButton(
                                        onClick = {
                                            processingJob?.cancel()
                                            processingLessonId = null
                                        }
                                    ) { Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.error) }
                                } else {
                                    IconButton(
                                        enabled = processingLessonId == null,
                                        onClick = { lessonToOverwrite = lesson }
                                    ) { Icon(Icons.Default.AutoFixHigh, "Auto Generate Pauses") }

                                    IconButton(
                                        enabled = processingLessonId == null,
                                        onClick = { CourseExporter.shareTranscriptFile(context, lesson) }
                                    ) { Icon(Icons.Default.Download, "Export") }
                                }
                            }
                        },
                        modifier = Modifier.clickable(enabled = !isProcessingThis) { onEditLessonText(lesson.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawFileEditorScreen(
    lesson: Lesson,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // We use TextFieldValue instead of String to know where the cursor is
    var textContent by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var validationErrors by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Real-time Format Validator
    LaunchedEffect(textContent.text) {
        val errors = mutableListOf<Int>()
        val lines = textContent.text.split("\n")
        // Regex looks for strictly "MM:SS text..." or "M:SS text..."
        val regex = Regex("^\\d{1,2}:\\d{2}\\s+.+$")

        lines.forEachIndexed { index, line ->
            if (line.isNotBlank() && !line.trim().matches(regex)) {
                errors.add(index + 1)
            }
        }
        validationErrors = errors
    }

    LaunchedEffect(lesson) {
        val file = File(lesson.transcriptionPath)
        if (file.exists()) {
            textContent = androidx.compose.ui.text.input.TextFieldValue(file.readText())
        }
    }

    fun insertAtCursor(textToInsert: String) {
        val currentText = textContent.text
        val selectionStart = textContent.selection.start
        val selectionEnd = textContent.selection.end

        val newText = currentText.substring(0, selectionStart) +
                textToInsert +
                currentText.substring(selectionEnd)

        textContent = androidx.compose.ui.text.input.TextFieldValue(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(selectionStart + textToInsert.length)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editing ${lesson.title}") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        try {
                            File(lesson.transcriptionPath).writeText(textContent.text)
                            Toast.makeText(context, "File Saved", Toast.LENGTH_SHORT).show()
                            onBackClick()
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            // Format Helper UI
            if (validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Format errors on lines: ${validationErrors.joinToString(", ")}\nExpected: MM:SS Phrase",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (textContent.text.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Valid", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Format looks good!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Toolbar
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Button(onClick = { insertAtCursor("\n00:00 ") }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Insert Timestamp")
                }
            }

            // Text Editor
            OutlinedTextField(
                value = textContent,
                onValueChange = { textContent = it },
                modifier = Modifier.fillMaxSize(),
                label = { Text("Raw content") }
            )
        }
    }
}