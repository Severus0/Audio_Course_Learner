package com.languageapp.audiocourselearner.data

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_DARK_MODE = "is_dark_mode"
    private const val KEY_EDITOR_MODE = "is_editor_mode"

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false) // Default to Light Mode
    }

    fun setDarkMode(context: Context, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, isEnabled).apply()
    }

    fun isEditorMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EDITOR_MODE, false) // Default to False
    }

    fun setEditorMode(context: Context, isEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EDITOR_MODE, isEnabled).apply()
    }
}