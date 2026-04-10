package com.languageapp.audiocourselearner.logic

import android.util.Log
import com.languageapp.audiocourselearner.model.Annotation
import java.io.File

class AnnotationManager {
    private var annotations = mutableListOf<Annotation>()

    fun loadFromFile(path: String): List<Annotation> {
        annotations.clear()
        val file = File(path)
        if (!file.exists()) return emptyList()

        file.forEachLine { line ->
            if (line.isNotBlank()) {
                try {
                    val parts = line.trim().split(" ", limit = 2)
                    val timeParts = parts[0].split(":")
                    val ms = (timeParts[0].toLong() * 60 + timeParts[1].toLong()) * 1000
                    annotations.add(Annotation(ms, parts[1]))
                } catch (e: Exception) {
                    Log.e("AnnotationManager", "Skipping malformed line: $line")
                }
            }
        }
        return annotations.sortedBy { it.timestampMs }
    }

    fun saveToFile(path: String, list: List<Annotation>) {
        // Look how clean this is now!
        val content = list.sortedBy { it.timestampMs }.joinToString("\n") { it.fileString }
        File(path).writeText(content)
    }
}