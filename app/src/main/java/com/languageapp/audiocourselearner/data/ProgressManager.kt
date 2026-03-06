package com.languageapp.audiocourselearner.data

import android.content.Context

object ProgressManager {
    private const val PREFS_NAME = "audio_course_progress"

    // Save the last played lesson ID for a Course
    fun saveLastLessonId(context: Context, courseId: String, lessonId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("last_lesson_$courseId", lessonId).apply()
    }

    // Get the last played lesson ID (returns null if never played)
    fun getLastLessonId(context: Context, courseId: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("last_lesson_$courseId", null)
    }

    // Save timestamp for a specific lesson
    fun saveLessonProgress(context: Context, lessonId: String, positionMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("progress_$lessonId", positionMs).apply()
    }

    // Get timestamp for a specific lesson (default 0)
    fun getLessonProgress(context: Context, lessonId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("progress_$lessonId", 0L)
    }
}