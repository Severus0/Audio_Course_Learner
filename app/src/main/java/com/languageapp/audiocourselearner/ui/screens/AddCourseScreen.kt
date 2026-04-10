package com.languageapp.audiocourselearner.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.utils.CourseImporter
import com.languageapp.audiocourselearner.utils.LanguageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseScreen(
    existingCourses: List<Course>,
    onBackClick: () -> Unit,
    onCourseAdded: (Course) -> Unit,
    onCourseUpdated: (Course) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    var selectedAudioUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isSelectingDestination by remember { mutableStateOf(false) }

    var newCourseName by remember { mutableStateOf("") }

    // LANGUAGE DROPDOWN STATE
    var isLangExpanded by remember { mutableStateOf(false) }
    var selectedLangName by remember { mutableStateOf("English (US)") }
    var selectedLangCode by remember { mutableStateOf("en-US") }

    // --- Components ---

    @Composable
    fun LanguageSelector() {
        ExposedDropdownMenuBox(
            expanded = isLangExpanded,
            onExpandedChange = { isLangExpanded = !isLangExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedLangName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target Language") },
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
    }

    // --- Launchers ---

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (newCourseName.isBlank()) {
                Toast.makeText(context, "Please enter a Name first", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isLoading = true
            loadingMessage = "Importing ZIP..."
            scope.launch {
                val newCourse = withContext(Dispatchers.IO) {
                    CourseImporter.importCourseFromZip(
                        context = context,
                        zipUri = uri,
                        courseName = newCourseName,
                        fallbackLanguage = selectedLangCode // Pass the valid code
                    )
                }
                isLoading = false
                if (newCourse != null) onCourseAdded(newCourse)
                else Toast.makeText(context, "Import failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedAudioUris = uris // Still saving to the same state variable
            isSelectingDestination = true
        }
    }

    // --- Logic ---

    fun createNewCourseFromAudio() {
        if (newCourseName.isBlank()) {
            Toast.makeText(context, "Enter a Name", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        loadingMessage = "Processing Audio..."
        scope.launch {
            val newCourse = withContext(Dispatchers.IO) {
                CourseImporter.createCourseFromAudioFiles(
                    context, selectedAudioUris, newCourseName, selectedLangCode
                )
            }
            isLoading = false
            if (newCourse != null) onCourseAdded(newCourse)
            else Toast.makeText(context, "Failed to create", Toast.LENGTH_SHORT).show()
        }
    }

    fun addToExistingCourse(course: Course) {
        isLoading = true
        loadingMessage = "Adding files..."
        scope.launch {
            val updatedCourse = withContext(Dispatchers.IO) {
                CourseImporter.addAudioFilesToCourse(context, course, selectedAudioUris)
            }
            isLoading = false
            onCourseUpdated(updatedCourse)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectingDestination) "Select Destination" else "Import Content") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectingDestination) {
                            isSelectingDestination = false; selectedAudioUris = emptyList()
                        } else onBackClick()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isLoading) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(loadingMessage)
                }
            } else if (!isSelectingDestination) {
                // SCREEN 1: Choose Import Type
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("How would you like to add content?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 24.dp))

                    // UPDATE: Launch with both Audio and Video MIME types
                    ElevatedCard(onClick = { mediaPickerLauncher.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.fillMaxWidth().height(110.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Select Media Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Import MP3, WAV, MP4, MKV files directly.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp)); HorizontalDivider(); Spacer(Modifier.height(24.dp))

                    Text("Import ZIP Archive", style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold)
                    Text("Contains audio files (and optional transcripts)", style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(value = newCourseName, onValueChange = {newCourseName=it}, label={Text("Course Name")}, modifier=Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    LanguageSelector() // <--- NEW DROPDOWN

                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { if(newCourseName.isNotBlank()) zipLauncher.launch("application/zip") }, modifier=Modifier.fillMaxWidth(), enabled=newCourseName.isNotBlank()) {
                        Icon(Icons.Default.FolderZip, null); Spacer(Modifier.width(8.dp)); Text("Select ZIP File")
                    }
                }
            } else {
                // SCREEN 2: Destination
                Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("${selectedAudioUris.size} files selected.", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))

                    Text("Option A: Create New Course", style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth().padding(top=8.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            OutlinedTextField(value=newCourseName, onValueChange={newCourseName=it}, label={Text("Course Name")}, modifier=Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))

                            LanguageSelector() // <--- NEW DROPDOWN

                            Spacer(Modifier.height(16.dp))
                            Button(onClick={createNewCourseFromAudio()}, modifier=Modifier.fillMaxWidth(), enabled=newCourseName.isNotBlank()) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Create Course")
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))

                    if (existingCourses.isNotEmpty()) {
                        Text("Option B: Add to Existing Course", style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.height(300.dp).fillMaxWidth()) {
                            items(existingCourses) { course ->
                                ListItem(
                                    headlineContent = { Text(course.name) },
                                    supportingContent = { Text(LanguageUtils.getName(course.languageCode)) },
                                    modifier = Modifier.clickable { addToExistingCourse(course) },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}