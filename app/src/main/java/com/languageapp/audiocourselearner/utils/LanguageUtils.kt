package com.languageapp.audiocourselearner.utils

object LanguageUtils {
    // Map of Display Name -> Locale Code
    val SUPPORTED_LANGUAGES = mapOf(
        "Afrikaans" to "af-ZA",
        "Arabic" to "ar-SA",
        "Bengali (Bangladesh)" to "bn-BD",
        "Bengali (India)" to "bn-IN",
        "Catalan" to "ca-ES",
        "Chinese (Simplified)" to "zh-CN",
        "Chinese (Traditional)" to "zh-TW",
        "Czech" to "cs-CZ",
        "Danish" to "da-DK",
        "Dutch" to "nl-NL",
        "English (Australia)" to "en-AU",
        "English (Canada)" to "en-CA",
        "English (India)" to "en-IN",
        "English (UK)" to "en-GB",
        "English (US)" to "en-US",
        "Finnish" to "fi-FI",
        "French" to "fr-FR",
        "German" to "de-DE",
        "Greek" to "el-GR",
        "Hebrew" to "he-IL",
        "Hindi" to "hi-IN",
        "Hungarian" to "hu-HU",
        "Indonesian" to "id-ID",
        "Italian" to "it-IT",
        "Japanese" to "ja-JP",
        "Kannada" to "kn-IN",
        "Korean" to "ko-KR",
        "Malay" to "ms-MY",
        "Malayalam" to "ml-IN",
        "Norwegian" to "nb-NO",
        "Persian (Farsi)" to "fa-IR",
        "Polish" to "pl-PL",
        "Portuguese" to "pt-PT",
        "Portuguese (Brazil)" to "pt-BR",
        "Romanian" to "ro-RO",
        "Russian" to "ru-RU",
        "Serbian (Cyrillic)" to "sr-Cyrl-RS",
        "Serbian (Latin)" to "sr-Latn-RS",
        "Slovak" to "sk-SK",
        "Spanish" to "es-ES",
        "Spanish (Latin America)" to "es-419",
        "Spanish (Mexico)" to "es-MX",
        "Swedish" to "sv-SE",
        "Tamil" to "ta-IN",
        "Telugu" to "te-IN",
        "Thai" to "th-TH",
        "Turkish" to "tr-TR",
        "Ukrainian" to "uk-UA",
        "Vietnamese" to "vi-VN"
    ).toSortedMap()

    fun getCode(name: String): String = SUPPORTED_LANGUAGES[name] ?: "en-US"

    fun getName(code: String): String {
        return SUPPORTED_LANGUAGES.entries.find { it.value.equals(code, ignoreCase = true) }?.key
            ?: "Unknown ($code)"
    }

    fun getList(): List<String> = SUPPORTED_LANGUAGES.keys.toList()
}