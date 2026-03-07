package com.languageapp.audiocourselearner.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.languageapp.audiocourselearner.model.Course
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.content.Intent
import androidx.core.content.FileProvider
import com.languageapp.audiocourselearner.model.Lesson

object CourseExporter {

    // --- OPTION 1: TRANSCRIPTS ONLY ZIP (Lightweight - Safe for Cache + Share) ---
    fun createTranscriptsZipExport(context: Context, course: Course): File? {
        return try {
            // Create a temp zip in the cache directory
            val zipFile = File(context.cacheDir, "${course.name}_transcripts.zip")
            val fos = FileOutputStream(zipFile)

            ZipOutputStream(BufferedOutputStream(fos)).use { out ->
                // 1. Add Config (Required for compatibility)
                val configJson = JSONObject()
                configJson.put("language", course.languageCode)

                val configEntry = ZipEntry("config.json")
                out.putNextEntry(configEntry)
                out.write(configJson.toString(4).toByteArray())
                out.closeEntry()

                // 2. Add Text Files Only
                val buffer = ByteArray(4096)

                course.lessons.forEach { lesson ->
                    // Only add the TXT file
                    addFileToZip(out, File(lesson.transcriptionPath), buffer)
                }
            }
            zipFile
        } catch (e: Exception) {
            Log.e("CourseExporter", "Transcripts Export failed", e)
            null
        }
    }

    // --- OPTION 2: FULL ZIP (Direct Stream to URI for 900MB+ files) ---
    // (This remains exactly the same as before)
    fun exportCourseToZip(context: Context, course: Course, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                ZipOutputStream(BufferedOutputStream(outputStream)).use { out ->

                    val configJson = JSONObject()
                    configJson.put("language", course.languageCode)

                    val configEntry = ZipEntry("config.json")
                    out.putNextEntry(configEntry)
                    out.write(configJson.toString(4).toByteArray())
                    out.closeEntry()

                    val buffer = ByteArray(4096)

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

    fun shareTranscriptFile(context: Context, lesson: Lesson) {
        try {
            val file = File(lesson.transcriptionPath)
            if (!file.exists()) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, lesson.title)
                putExtra(Intent.EXTRA_TITLE, "${lesson.title}.txt")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share Transcript"))

        } catch (e: Exception) {
            Log.e("CourseExporter", "Share transcript failed", e)
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