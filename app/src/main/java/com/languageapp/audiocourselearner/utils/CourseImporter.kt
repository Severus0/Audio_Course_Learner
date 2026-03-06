package com.languageapp.audiocourselearner.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.model.Lesson
import com.languageapp.audiocourselearner.utils.NaturalOrderComparator
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

object CourseImporter {

    fun importCourseFromZip(context: Context, zipUri: Uri, courseName: String): Course? {
        val courseId = UUID.randomUUID().toString()
        val courseDir = File(context.filesDir, "courses/$courseId")

        if (!courseDir.exists()) courseDir.mkdirs()

        try {
            // 1. Unzip content
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(courseDir, entry.name)
                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            // Ensure parent exists
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            // 2. Parse config.json
            val configFile = File(courseDir, "config.json")
            if (!configFile.exists()) {
                Log.e("CourseImporter", "No config.json found in zip")
                return null
            }

            val configContent = configFile.readText()
            val jsonObject = JSONObject(configContent)
            val languageCode = jsonObject.optString("language", "en") // Default to en

            // 3. Scan for Audio/Text pairs
            val lessons = mutableListOf<Lesson>()

            // Get all files
            val files = courseDir.listFiles() ?: emptyArray()

            // Filter audio files AND Sort using our Smart Comparator
            val audioFiles = files.filter {
                it.extension.lowercase() in listOf("mp3", "wav", "m4a", "ogg")
            }.sortedWith(NaturalOrderComparator) // <--- CHANGED THIS

            audioFiles.forEach { audioFile ->
                val baseName = audioFile.nameWithoutExtension
                val txtFile = File(courseDir, "$baseName.txt")

                if (txtFile.exists()) {
                    lessons.add(
                        Lesson(
                            id = UUID.randomUUID().toString(),
                            title = baseName.replace("_", " ").capitalize(), // "german_01" -> "German 01"
                            audioPath = audioFile.absolutePath,
                            transcriptionPath = txtFile.absolutePath
                        )
                    )
                }
            }

            if (lessons.isEmpty()) {
                Log.e("CourseImporter", "No valid lesson pairs found")
                return null
            }

            return Course(
                id = courseId,
                name = courseName,
                description = "${lessons.size} lessons • ${languageCode.uppercase()}",
                languageCode = languageCode,
                lessons = lessons
            )

        } catch (e: Exception) {
            Log.e("CourseImporter", "Error importing course", e)
            // Cleanup on failure
            courseDir.deleteRecursively()
            return null
        }
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}