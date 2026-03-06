package com.languageapp.audiocourselearner.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.languageapp.audiocourselearner.model.Course
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CourseExporter {

    // --- OPTION 1: TEXT ONLY (Safe to use Cache + Share) ---
    fun createCourseJsonExport(context: Context, course: Course): File? {
        return try {
            val root = JSONObject()
            root.put("courseName", course.name)
            root.put("language", course.languageCode)

            val lessonsArray = JSONArray()
            course.lessons.forEach { lesson ->
                val lessonObj = JSONObject()
                lessonObj.put("title", lesson.title)

                val txtFile = File(lesson.transcriptionPath)
                if(txtFile.exists()) {
                    lessonObj.put("content", txtFile.readText())
                }
                lessonsArray.put(lessonObj)
            }
            root.put("lessons", lessonsArray)

            // Save to Cache
            val exportFile = File(context.cacheDir, "${course.name}_transcripts.json")
            exportFile.writeText(root.toString(4))
            exportFile
        } catch (e: Exception) {
            Log.e("CourseExporter", "JSON Export failed", e)
            null
        }
    }

    // --- OPTION 2: FULL ZIP (Direct Stream to URI for 900MB+ files) ---
    fun exportCourseToZip(context: Context, course: Course, targetUri: Uri): Boolean {
        return try {
            // Write directly to the user-selected location (No intermediate temp file)
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { out ->

                    // 1. Config
                    val configJson = JSONObject()
                    configJson.put("language", course.languageCode)

                    val configEntry = ZipEntry("config.json")
                    out.putNextEntry(configEntry)
                    out.write(configJson.toString(4).toByteArray())
                    out.closeEntry()

                    // 2. Files (Audio + Text)
                    val buffer = ByteArray(4096) // Slightly larger buffer for speed

                    course.lessons.forEach { lesson ->
                        addFileToZip(out, File(lesson.audioPath), buffer)
                        addFileToZip(out, File(lesson.transcriptionPath), buffer)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("CourseExporter", "Export failed", e)
            false
        }
    }

    private fun addFileToZip(out: ZipOutputStream, file: File, buffer: ByteArray) {
        if (!file.exists()) return
        val fis = FileInputStream(file)
        val bis = BufferedInputStream(fis)

        val entry = ZipEntry(file.name)
        out.putNextEntry(entry)

        var count: Int
        while (bis.read(buffer).also { count = it } != -1) {
            out.write(buffer, 0, count)
        }

        bis.close()
        out.closeEntry()
    }
}