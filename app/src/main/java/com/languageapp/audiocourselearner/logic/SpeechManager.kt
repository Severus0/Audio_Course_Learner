package com.languageapp.audiocourselearner.logic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.languageapp.audiocourselearner.R

/**
 * Encapsulates the Speech-to-Text engine.
 * Swap the implementation inside [startListening] to change the STT provider.
 */
class SpeechManager(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onResults: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    init {
        setup()
    }

    private fun setup() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onReady()
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.error_silence)
                        SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.error_no_match)
                        SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.error_no_internet)
                        else -> context.getString(R.string.error_generic, error)
                    }
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    onResults(matches?.firstOrNull() ?: "")
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(targetLanguageCode: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, mapLocale(targetLanguageCode))
        }
        speechRecognizer?.startListening(intent)
    }

    fun stop() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
    }

    fun destroy() {
        speechRecognizer?.destroy()
    }

    private fun mapLocale(code: String): String = when (code.lowercase()) {
        "de", "german" -> "de-DE"
        "es", "spanish" -> "es-ES"
        "fr", "french" -> "fr-FR"
        "it", "italian" -> "it-IT"
        "pl", "polish" -> "pl-PL"
        else -> code // Use default or exact code
    }
}