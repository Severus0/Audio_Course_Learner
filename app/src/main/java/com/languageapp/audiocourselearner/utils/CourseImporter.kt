package com.languageapp.audiocourselearner.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.model.Lesson
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

object CourseImporter {

    val SUPPORTED_EXTENSIONS = listOf(
        "mp3", "wav", "m4a", "flac", "ogg",
        "mp4", "mkv", "avi", "mov", "webm"
    )

    fun importCourseFromZip(
        context: Context,
        zipUri: Uri,
        courseName: String,
        fallbackLanguage: String
    ): Course? {
        val courseId = UUID.randomUUID().toString()
        val courseDir = File(context.filesDir, "courses/$courseId")

        if (!courseDir.exists()) courseDir.mkdirs()

        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val newFile = File(courseDir, entry.name)

                        if (!newFile.canonicalPath.startsWith(courseDir.canonicalPath)) {
                            entry = zis.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            val configFile = File(courseDir, "config.json")
            val languageCode: String

            if (!configFile.exists()) {
                languageCode = fallbackLanguage
                val configJson = JSONObject()
                configJson.put("language", languageCode)
                configFile.writeText(configJson.toString(4))
            } else {
                val configContent = configFile.readText()
                val jsonObject = JSONObject(configContent)
                languageCode = jsonObject.optString("language", fallbackLanguage)
            }

            val lessons = mutableListOf<Lesson>()

            // UPDATE: Support both audio and video extensions
            val mediaFiles = courseDir.walk().filter {
                it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS
            }.sortedWith(NaturalOrderComparator).toList()

            mediaFiles.forEach { mediaFile ->
                val baseName = mediaFile.nameWithoutExtension
                val txtFile = File(mediaFile.parentFile, "$baseName.txt")

                if (!txtFile.exists()) {
                    txtFile.writeText("")
                }

                lessons.add(
                    Lesson(
                        id = UUID.randomUUID().toString(),
                        title = baseName.replace("_", " ").capitalize(),
                        audioPath = mediaFile.absolutePath, // Keeping variable name as audioPath for JSON backwards compatibility
                        transcriptionPath = txtFile.absolutePath
                    )
                )
            }

            if (lessons.isEmpty()) {
                Log.e("CourseImporter", "No media files found in zip")
                courseDir.deleteRecursively()
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
            courseDir.deleteRecursively()
            return null
        }
    }

    fun createCourseFromAudioFiles(
        context: Context,
        mediaUris: List<Uri>,
        courseName: String,
        languageCode: String
    ): Course? {
        val courseId = UUID.randomUUID().toString()
        val courseDir = File(context.filesDir, "courses/$courseId")
        if (!courseDir.exists()) courseDir.mkdirs()

        try {
            val configJson = JSONObject()
            configJson.put("language", languageCode)
            File(courseDir, "config.json").writeText(configJson.toString(4))

            val lessons = processMediaUris(context, mediaUris, courseDir)

            if (lessons.isEmpty()) {
                courseDir.deleteRecursively()
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
            Log.e("CourseImporter", "Error creating course from media", e)
            courseDir.deleteRecursively()
            return null
        }
    }

    fun addAudioFilesToCourse(
        context: Context,
        course: Course,
        mediaUris: List<Uri>
    ): Course {
        val courseDir = File(context.filesDir, "courses/${course.id}")
        if (!courseDir.exists()) courseDir.mkdirs()

        val newLessons = processMediaUris(context, mediaUris, courseDir)
        val allLessons = (course.lessons + newLessons).sortedBy { it.title }

        return course.copy(
            lessons = allLessons,
            description = "${allLessons.size} lessons • ${course.languageCode.uppercase()}"
        )
    }

    private fun processMediaUris(
        context: Context,
        uris: List<Uri>,
        destDir: File
    ): List<Lesson> {
        val lessons = mutableListOf<Lesson>()

        uris.forEach { uri ->
            // UPDATE: Generic fallback name if filename resolution fails
            val fileName = getFileName(context, uri) ?: "media_${System.currentTimeMillis()}.mp4"
            val baseName = File(fileName).nameWithoutExtension
            val title = baseName.replace("_", " ").capitalize()

            val destMedia = File(destDir, fileName)
            val destTxt = File(destDir, "$baseName.txt")

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destMedia).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!destTxt.exists()) {
                    destTxt.writeText("")
                }

                lessons.add(
                    Lesson(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        audioPath = destMedia.absolutePath,
                        transcriptionPath = destTxt.absolutePath
                    )
                )

            } catch (e: Exception) {
                Log.e("CourseImporter", "Failed to copy $fileName", e)
            }
        }
        return lessons
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}