package com.languageapp.audiocourselearner.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.languageapp.audiocourselearner.R
import com.languageapp.audiocourselearner.model.Course
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import android.text.method.LinkMovementMethod
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.filled.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    courses: List<Course>,
    isEditorMode: Boolean,
    onToggleEditorMode: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onAddCourseClick: () -> Unit,
    onCourseClick: (String) -> Unit,
    onSettingsClick: (String) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // --- DIALOG STATES ---
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showSocialsDialog by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showPipelineDialog by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "App Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 16.dp)
                )
                HorizontalDivider()

                NavigationDrawerItem(
                    label = { Text("Data Pipeline") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showPipelineDialog = true
                    },
                    icon = { Icon(Icons.Default.Build, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Editor Mode
                NavigationDrawerItem(
                    label = { Text("Editor Mode") },
                    selected = false,
                    onClick = { onToggleEditorMode(!isEditorMode) },
                    icon = { Icon(Icons.Default.Edit, null) },
                    badge = { Switch(checked = isEditorMode, onCheckedChange = { onToggleEditorMode(it) }) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Dark Mode
                NavigationDrawerItem(
                    label = { Text("Dark Mode") },
                    selected = false,
                    onClick = { onToggleTheme(!isDarkTheme) },
                    icon = { Icon(Icons.Default.DarkMode, null) },
                    badge = { Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme(it) }) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Licenses
                NavigationDrawerItem(
                    label = { Text("Licenses") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showLicensesDialog = true
                    },
                    icon = { Icon(Icons.Default.Info, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Socials
                NavigationDrawerItem(
                    label = { Text("Socials") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSocialsDialog = true
                    },
                    icon = { Icon(Icons.Default.Share, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                // Tutorial
                NavigationDrawerItem(
                    label = { Text("Tutorial") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showTutorialDialog = true
                    },
                    icon = { Icon(Icons.Default.Help, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Language Courses") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (courses.isEmpty()) {
                    EmptyStateView(onAddCourseClick)
                } else {
                    CourseGridView(
                        courses = courses,
                        isEditorMode = isEditorMode,
                        onCourseClick = onCourseClick,
                        onSettingsClick = onSettingsClick,
                        onAddClick = onAddCourseClick
                    )
                }
            }
        }

        // --- DIALOG IMPLEMENTATIONS ---

        if (showPipelineDialog) {
            LinkDialog(
                title = "Create Your Own Courses",
                message = "Download the Data Pipeline to automatically generate course files from audio. Use it to create compatible .zip files for this app.",
                url = "https://github.com/Severus0/Audio_Course_Learner_Data_Pipeline",
                onDismiss = { showPipelineDialog = false }
            )
        }

        if (showLicensesDialog) {
            HtmlDialog(
                title = "Open Source Licenses",
                rawResourceId = R.raw.licenses,
                onDismiss = { showLicensesDialog = false }
            )
        }

        if (showSocialsDialog) {
            LinkDialog(
                title = "Join the Community",
                message = "Join our Discord server to discuss courses and features!",
                url = "https://discord.gg/t2zyfCq6KH", // Replace with real link
                onDismiss = { showSocialsDialog = false }
            )
        }

        if (showTutorialDialog) {
            LinkDialog(
                title = "How to use",
                message = "Watch the full tutorial on YouTube:",
                url = "https://youtube.com/yourvideolink", // Replace with real link
                onDismiss = { showTutorialDialog = false }
            )
        }
    }
}

// --- HELPER COMPOSABLES ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkDialog(
    title: String,
    message: String,
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(modifier = Modifier.height(12.dp))

                // The Link Component with Click + Long Click logic
                Text(
                    text = url,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Link", url)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("(Tap to open, Long-press to copy)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun HtmlDialog(
    title: String,
    rawResourceId: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    // Read raw file content
    val htmlContent = remember {
        try {
            val inputStream = context.resources.openRawResource(rawResourceId)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readText()
        } catch (e: Exception) {
            "<p>License file not found.</p>"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                AndroidView(
                    factory = { ctx ->
                        TextView(ctx).apply {
                            // KEY FIX 1: Enable clicking links
                            movementMethod = LinkMovementMethod.getInstance()

                            // KEY FIX 2: Set colors manually to match Theme (Dark/Light mode support)
                            setTextColor(textColor)
                            setLinkTextColor(linkColor)

                            textSize = 14f
                        }
                    },
                    update = { textView ->
                        // Re-apply text/colors if they change
                        textView.text = Html.fromHtml(htmlContent, Html.FROM_HTML_MODE_COMPACT)
                        textView.setTextColor(textColor)
                        textView.setLinkTextColor(linkColor)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

// ... EmptyStateView, CourseGridView, CourseCard remain unchanged ...
// (Omitting to save space, copy from previous if needed, they are untouched)
@Composable
fun EmptyStateView(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onAddClick,
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Course",
                modifier = Modifier.padding(24.dp).fillMaxSize(),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Start a new journey", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tap + to add a course", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CourseGridView(courses: List<Course>, isEditorMode: Boolean, onCourseClick: (String) -> Unit, onSettingsClick: (String) -> Unit, onAddClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(courses) { course ->
                CourseCard(
                    course = course,
                    isEditorMode = isEditorMode,
                    onClick = onCourseClick,
                    onSettingsClick = onSettingsClick
                )
            }
        }
        Box(modifier = Modifier.padding(16.dp).align(Alignment.End)) {
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, "Add") }
        }
    }
}

@Composable
fun CourseCard(course: Course, isEditorMode: Boolean, onClick: (String) -> Unit, onSettingsClick: (String) -> Unit) {
    ElevatedCard(
        onClick = { onClick(course.id) },
        modifier = Modifier.fillMaxWidth().height(150.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(course.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isEditorMode) {
                IconButton(
                    onClick = { onSettingsClick(course.id) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}