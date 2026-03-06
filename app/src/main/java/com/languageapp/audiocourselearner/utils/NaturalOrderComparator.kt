package com.languageapp.audiocourselearner.utils

import java.io.File

object NaturalOrderComparator : Comparator<File> {
    private val numberRegex = Regex("(\\d+)")

    override fun compare(f1: File, f2: File): Int {
        val s1 = f1.nameWithoutExtension
        val s2 = f2.nameWithoutExtension

        // Extract all number sequences from the strings
        val n1 = extractNumbers(s1)
        val n2 = extractNumbers(s2)

        // Compare the first differing number
        val len = minOf(n1.size, n2.size)
        for (i in 0 until len) {
            if (n1[i] != n2[i]) {
                return n1[i].compareTo(n2[i])
            }
        }

        // If numbers are identical (or none), fall back to standard string comparison
        return s1.compareTo(s2, ignoreCase = true)
    }

    private fun extractNumbers(s: String): List<Int> {
        return numberRegex.findAll(s).map { it.value.toInt() }.toList()
    }
}