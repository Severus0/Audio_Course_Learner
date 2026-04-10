package com.languageapp.audiocourselearner.model

data class Annotation(
    val timestampMs: Long,
    val expectedPhrase: String
) {
    /**
     * Converts the timestamp to a clean MM:SS string.
     * Use this instead of calculating it in ViewModels/UI.
     */
    val formattedTime: String
        get() {
            val totalSeconds = timestampMs / 1000
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            return "%02d:%02d".format(m, s)
        }

    /**
     * Returns exactly how this annotation should look when written to a .txt file
     */
    val fileString: String
        get() = "$formattedTime $expectedPhrase"
}