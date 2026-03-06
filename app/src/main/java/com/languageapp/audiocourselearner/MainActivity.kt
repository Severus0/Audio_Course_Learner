package com.languageapp.audiocourselearner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.languageapp.audiocourselearner.data.AppSettings
import com.languageapp.audiocourselearner.data.CourseRepository
import com.languageapp.audiocourselearner.data.ProgressManager
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.ui.screens.*
import com.languageapp.audiocourselearner.ui.theme.AudioCourseLearnerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // LOAD SAVED DARK MODE STATE
            // We use mutableStateOf with the saved value as the initial value
            val context = LocalContext.current
            var isDarkTheme by remember { mutableStateOf(AppSettings.isDarkMode(context)) }

            AudioCourseLearnerTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = it
                        AppSettings.setDarkMode(context, it) // SAVE TO DISK
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(isDarkTheme: Boolean, onToggleTheme: (Boolean) -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val courses = remember { mutableStateListOf<Course>() }

    var isEditorMode by remember { mutableStateOf(AppSettings.isEditorMode(context)) }

    LaunchedEffect(Unit) {
        val savedCourses = CourseRepository.loadCourses(context)
        courses.addAll(savedCourses)
    }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                courses = courses,
                isEditorMode = isEditorMode,
                onToggleEditorMode = {
                    isEditorMode = it
                    AppSettings.setEditorMode(context, it) // SAVE TO DISK
                },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onAddCourseClick = { navController.navigate("add_course") },
                onCourseClick = { courseId -> navController.navigate("player/$courseId") },
                onSettingsClick = { courseId -> navController.navigate("course_settings/$courseId") }
            )
        }

        composable("add_course") {
            AddCourseScreen(
                onBackClick = { navController.popBackStack() },
                onCourseAdded = { newCourse ->
                    courses.add(newCourse)
                    CourseRepository.saveCourses(context, courses)
                    navController.popBackStack()
                }
            )
        }

        composable("player/{courseId}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId")
            val course = courses.find { it.id == courseId }

            if (course != null && course.lessons.isNotEmpty()) {
                val lastLessonId = ProgressManager.getLastLessonId(context, course.id)
                var startLessonId = lastLessonId
                if (startLessonId == null || course.lessons.none { it.id == startLessonId }) {
                    startLessonId = course.lessons[0].id
                }

                PlayerScreen(
                    course = course,
                    startLessonId = startLessonId!!,
                    isEditorMode = isEditorMode,
                    onBackClick = { navController.popBackStack() }
                )
            } else {
                androidx.compose.material3.Text("Course Empty")
            }
        }

        // ... course_settings and raw_editor routes remain same as previous ...
        composable("course_settings/{courseId}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId")
            val course = courses.find { it.id == courseId }
            if (course != null) {
                CourseSettingsScreen(
                    course = course,
                    onBackClick = { navController.popBackStack() },
                    onEditLessonText = { lessonId -> navController.navigate("raw_editor/$courseId/$lessonId") }
                )
            }
        }

        composable("raw_editor/{courseId}/{lessonId}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId")
            val lessonId = backStackEntry.arguments?.getString("lessonId")
            val course = courses.find { it.id == courseId }
            val lesson = course?.lessons?.find { it.id == lessonId }
            if (lesson != null) {
                RawFileEditorScreen(
                    lesson = lesson,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}