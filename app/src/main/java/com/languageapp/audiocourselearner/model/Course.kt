package com.languageapp.audiocourselearner.model

data class Course(
    val id: String,
    val name: String,
    val description: String,
    val languageCode: String, // e.g., "de", "es"
    val lessons: List<Lesson>
)

data class Lesson(
    val id: String,
    val title: String,
    val audioPath: String, // Absolute path to the .mp3/.wav file
    val transcriptionPath: String // Absolute path to the .txt file
)