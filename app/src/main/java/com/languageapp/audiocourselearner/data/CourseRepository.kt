package com.languageapp.audiocourselearner.data

import android.content.Context
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.languageapp.audiocourselearner.model.Course
import java.io.File

object CourseRepository {
    private const val FILE_NAME = "courses_metadata.json"
    private val gson = Gson()

    fun saveCourses(context: Context, courses: List<Course>) {
        val json = gson.toJson(courses)
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(json)
    }

    fun loadCourses(context: Context): MutableList<Course> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableListOf()

        return try {
            val json = file.readText()
            val type = object : TypeToken<List<Course>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}