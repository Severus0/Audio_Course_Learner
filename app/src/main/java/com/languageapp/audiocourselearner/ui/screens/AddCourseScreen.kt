package com.languageapp.audiocourselearner.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.utils.CourseImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseScreen(onBackClick: () -> Unit, onCourseAdded: (Course) -> Unit) {
    var courseName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // File Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            if (courseName.isBlank()) {
                Toast.makeText(context, "Please enter a course name first", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            isImporting = true
            scope.launch {
                // Run heavy import on IO thread
                val newCourse = withContext(Dispatchers.IO) {
                    CourseImporter.importCourseFromZip(context, uri, courseName)
                }

                isImporting = false

                if (newCourse != null) {
                    onCourseAdded(newCourse)
                } else {
                    Toast.makeText(context, "Import failed. Check ZIP format.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Course") },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "1. Give your course a name\n2. Select a ZIP file containing .mp3, .txt, and config.json",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = courseName,
                onValueChange = { courseName = it },
                label = { Text("Course Name (e.g. Spanish)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isImporting) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Unzipping and processing lessons...")
                }
            } else {
                Button(
                    onClick = { launcher.launch("application/zip") }, // Filter for ZIPs
                    modifier = Modifier.fillMaxWidth(),
                    enabled = courseName.isNotBlank()
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select ZIP & Import")
                }
            }
        }
    }
}