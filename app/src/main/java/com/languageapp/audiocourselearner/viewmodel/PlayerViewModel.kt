package com.languageapp.audiocourselearner.viewmodel

import android.app.Application
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.languageapp.audiocourselearner.R
import com.languageapp.audiocourselearner.data.ProgressManager
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.model.Lesson
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.io.File

data class Annotation(
    val timestampMs: Long,
    val expectedPhrase: String
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // -- UI STATE --
    var isPlaying by mutableStateOf(false)
        private set
    var currentProgress by mutableFloatStateOf(0f)
        private set
    var currentTimeLabel by mutableStateOf("00:00")
        private set
    var totalTimeLabel by mutableStateOf("00:00")
        private set
    var currentLessonTitle by mutableStateOf("Loading...")
        private set

    // -- INTERACTION STATE --
    var isListening by mutableStateOf(false)
        private set
    var feedbackMessage by mutableStateOf<String?>(null)
        private set
    var currentExpectedPhrase by mutableStateOf<String?>(null)
        private set

    var currentAnnotation: Annotation? = null
        private set

    private val handledTimestamps = HashSet<Long>()

    // -- INTERNALS --
    private var exoPlayer: ExoPlayer? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var annotations = listOf<Annotation>()

    private val handler = Handler(Looper.getMainLooper())
    private val retryToken = Any()
    private val resumeToken = Any()

    // FIX: Flag to ignore callbacks after we cancel manually
    private var isInteractionCancelled = false

    // -- SOUND --
    private var soundPool: SoundPool? = null
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0
    private var soundsLoaded = false
    private var retryCount = 0

    var nearbyAnnotation by mutableStateOf<Annotation?>(null)
        private set
    // -- DATA --
    private lateinit var currentCourse: Course
    private var currentLessonIndex = 0
    private var targetLanguageCode: String = "en"
    var currentLesson: Lesson? = null
        private set

    var visibleAnnotations by mutableStateOf<List<Long>>(emptyList())
        private set

    var skipDurationMs: Long = 10000

    // NEW: We need exact duration for calculations
    var durationMs by mutableStateOf(0L)
        private set

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateUIState()
            if (isPlaying) handler.postDelayed(this, 500)
        }
    }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) soundsLoaded = true
        }

        successSoundId = soundPool?.load(context, R.raw.success, 1) ?: 0
        errorSoundId = soundPool?.load(context, R.raw.error, 1) ?: 0
    }

    fun initialize(course: Course, startLessonId: String) {
        this.currentCourse = course
        this.targetLanguageCode = course.languageCode
        val index = course.lessons.indexOfFirst { it.id == startLessonId }
        this.currentLessonIndex = if (index != -1) index else 0
        loadCurrentLesson(autoPlay = false)
    }

    private fun loadCurrentLesson(autoPlay: Boolean) {
        handledTimestamps.clear()
        exoPlayer?.release()
        stopProgressUpdater()

        cancelInteraction()
        isPlaying = false

        val lesson = currentCourse.lessons[currentLessonIndex]
        currentLesson = lesson
        currentLessonTitle = lesson.title
        currentTimeLabel = "00:00"
        currentProgress = 0f

        ProgressManager.saveLastLessonId(context, currentCourse.id, lesson.id)
        loadAnnotationsFromFile(lesson.transcriptionPath)

        val savedPosition = ProgressManager.getLessonProgress(context, lesson.id)

        exoPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(lesson.audioPath)))
            setMediaItem(mediaItem)
            prepare()

            if (savedPosition > 0 && savedPosition < (duration - 1000)) {
                seekTo(savedPosition)
                currentTimeLabel = formatTime(savedPosition)
            }

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    this@PlayerViewModel.isPlaying = playing
                    if (playing) startProgressUpdater() else stopProgressUpdater()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        durationMs = duration
                        totalTimeLabel = formatTime(duration)
                        updateUIState()
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        playNextLesson(autoPlay = true)
                    }
                }
            })
        }

        scheduleAnnotationTriggers()
        setupSpeechRecognizer()

        if (autoPlay) play() else updateUIState()
    }

    // --- CRITICAL FIX: INTERACTION CONTROL ---

    fun cancelInteraction() {
        // 1. Set flag so callbacks are ignored
        isInteractionCancelled = true
        isListening = false

        // 2. Stop Speech Engine
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) { }

        // 3. Remove delayed tasks
        handler.removeCallbacksAndMessages(retryToken)
        handler.removeCallbacksAndMessages(resumeToken)

        // 4. Wipe all interaction data immediately
        feedbackMessage = null
        currentExpectedPhrase = null
        currentAnnotation = null // Clear this too
        retryCount = 0

        // 5. Force Pause
        pause()
    }

    // --- PLAYLIST CONTROLS ---

    fun playNextLesson(autoPlay: Boolean = false) {
        if (currentLessonIndex < currentCourse.lessons.lastIndex) {
            currentLessonIndex++
            loadCurrentLesson(autoPlay)
        } else {
            pause()
        }
    }

    fun playPreviousLesson() {
        if (currentLessonIndex > 0) {
            currentLessonIndex--
            loadCurrentLesson(autoPlay = false)
        } else {
            seekTo(0f)
        }
    }

    fun hasNext() = ::currentCourse.isInitialized && currentLessonIndex < currentCourse.lessons.lastIndex
    fun hasPrevious() = currentLessonIndex > 0

    // --- ANNOTATIONS ---

    private fun loadAnnotationsFromFile(path: String) {
        try {
            val file = File(path)
            annotations = if (file.exists()) {
                val list = mutableListOf<Annotation>()
                file.forEachLine { line ->
                    if (line.isNotBlank()) {
                        try {
                            val parts = line.trim().split(" ", limit = 2)
                            val timeParts = parts[0].split(":")
                            val minutes = timeParts[0].toLong()
                            val seconds = timeParts[1].toLong()
                            val ms = (minutes * 60 + seconds) * 1000
                            val phrase = parts[1]
                            list.add(Annotation(ms, phrase))
                        } catch (e: Exception) { Log.e("PlayerVM", "Bad line: $line") }
                    }
                }
                list
            } else {
                emptyList()
            }

            visibleAnnotations = annotations.map { it.timestampMs }

        } catch (e: Exception) {
            annotations = emptyList()
        }
    }

    @OptIn(UnstableApi::class)
    private fun scheduleAnnotationTriggers() {
        exoPlayer?.let { player ->
            annotations.forEach { annotation ->
                player.createMessage { _, _ -> pauseAndListen(annotation) }
                    .setPosition(annotation.timestampMs)
                    .setDeleteAfterDelivery(false)
                    .setLooper(Looper.getMainLooper())
                    .send()
            }
        }
    }

    private fun pauseAndListen(annotation: Annotation) {
        // Safety 1: If currently editing or listening, ignore new triggers
        if (currentAnnotation == annotation && (isListening || feedbackMessage != null)) return

        // Safety 2: If we already answered this one manually, ignore this trigger and keep playing
        if (handledTimestamps.contains(annotation.timestampMs)) return

        pause()

        // Reset state
        isInteractionCancelled = false
        retryCount = 0
        currentAnnotation = annotation
        currentExpectedPhrase = annotation.expectedPhrase
        feedbackMessage = "Speak now..."

        startListening()
    }

    // --- SPEECH RECOGNITION ---

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (isInteractionCancelled) return
                        isListening = true
                    }
                    override fun onBeginningOfSpeech() {
                        if (isInteractionCancelled) return
                        feedbackMessage = "Listening..."
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        if (isInteractionCancelled) return

                        // FIX: Handle specific error codes for better UX
                        val message = when(error) {
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Silence"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match found" // Error 7
                            SpeechRecognizer.ERROR_NETWORK -> "No internet"
                            else -> "Error $error"
                        }
                        handleWrongAnswer(message)
                    }

                    override fun onResults(results: Bundle?) {
                        // FIX: If user cancelled, DO NOT process results
                        if (isInteractionCancelled) return

                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            validateAnswer(matches[0])
                        } else {
                            handleWrongAnswer("Unclear")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun startListening() {
        if (isInteractionCancelled) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val localeCode = when(targetLanguageCode.lowercase()) {
                "de", "german" -> "de-DE"
                "es", "spanish" -> "es-ES"
                "fr", "french" -> "fr-FR"
                "it", "italian" -> "it-IT"
                "pl", "polish" -> "pl-PL"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        handler.post { try { speechRecognizer?.startListening(intent) } catch (e: Exception) { e.printStackTrace() } }
    }

    private fun validateAnswer(userSpoke: String) {
        if (isInteractionCancelled) return

        val expected = currentExpectedPhrase ?: return
        val ratio = FuzzySearch.ratio(userSpoke.lowercase(), expected.lowercase())

        val threshold = 65

        if (ratio > threshold) {
            // FIX: Pass 'expected' phrase to the handler
            handleCorrectAnswer(userSpoke, expected)
        } else {
            handleWrongAnswer(userSpoke)
        }
    }

    private fun handleCorrectAnswer(userSpoke: String, expectedPhrase: String) {
        if (isInteractionCancelled) return

        // FIX: Show both what was heard AND what was expected
        // Using HTML-like formatting or just distinct brackets
        feedbackMessage = "Correct!\nHeard: \"$userSpoke\"\nTarget: \"$expectedPhrase\""

        // Add timestamp to handled list
        currentAnnotation?.let { handledTimestamps.add(it.timestampMs) }

        if (soundsLoaded) soundPool?.play(successSoundId, 1f, 1f, 1, 0, 1f)

        handler.postAtTime({
            if (isInteractionCancelled) return@postAtTime
            clearInteractionState()
            play()
        }, resumeToken, android.os.SystemClock.uptimeMillis() + 2000) // Increased to 2s so user can read text
    }

    private fun handleWrongAnswer(userSpoke: String) {
        if (isInteractionCancelled) return

        if (soundsLoaded) soundPool?.play(errorSoundId, 1f, 1f, 1, 0, 1f)

        if (retryCount == 0) {
            retryCount++
            feedbackMessage = "Try again! (Heard: $userSpoke)"

            handler.postAtTime({
                if (!isInteractionCancelled) startListening()
            }, retryToken, android.os.SystemClock.uptimeMillis() + 1200)
        } else {
            feedbackMessage = "Moving on... (Answer: ${currentExpectedPhrase})"

            handler.postAtTime({
                if (!isInteractionCancelled) {
                    clearInteractionState()
                    play()
                }
            }, resumeToken, android.os.SystemClock.uptimeMillis() + 2000)
        }
    }

    private fun clearInteractionState() {
        feedbackMessage = null
        currentExpectedPhrase = null
        currentAnnotation = null
        retryCount = 0
    }

    // --- CONTROLS ---

    fun play() {
        cancelInteraction()
        exoPlayer?.play()
    }

    fun pause() { exoPlayer?.pause() }

    fun togglePlayPause() {
        if (isListening || currentExpectedPhrase != null) {
            cancelInteraction()
            play()
        } else {
            if (isPlaying) pause() else play()
        }
    }

    fun seekTo(pct: Float) {
        handledTimestamps.clear() // Reset history
        cancelInteraction()
        exoPlayer?.let {
            val position = (it.duration * pct).toLong()
            it.seekTo(position)
            updateUIState()
        }
    }

    fun jumpToNextAnnotation() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition

        val next = annotations
            .sortedBy { it.timestampMs }
            .firstOrNull { it.timestampMs > currentPos + 2000 }

        if (next != null) {
            cancelInteraction()

            val target = (next.timestampMs - 2000).coerceAtLeast(0)
            player.seekTo(target)
            updateUIState()
        }
    }

    fun jumpToPreviousAnnotation() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition

        val prev = annotations
            .filter { it.timestampMs < currentPos }
            .maxByOrNull { it.timestampMs }

        if (prev != null) {
            cancelInteraction()

            val target = (prev.timestampMs - 2000).coerceAtLeast(0)
            player.seekTo(target)
            updateUIState()
        }
    }

    fun hasNextAnnotation(): Boolean {
        val pos = getCurrentPosition()
        return annotations.any { it.timestampMs > pos }
    }

    fun hasPreviousAnnotation(): Boolean {
        val pos = getCurrentPosition()
        return annotations.any { it.timestampMs < pos }
    }
    fun skipForward() {
        handledTimestamps.clear()
        cancelInteraction()
        exoPlayer?.let {
            it.seekTo(it.currentPosition + skipDurationMs)
            updateUIState()
        }
    }

    fun skipBack() {
        handledTimestamps.clear()
        cancelInteraction()
        exoPlayer?.let {
            it.seekTo(it.currentPosition - skipDurationMs)
            updateUIState()
        }
    }

    fun manualListenTrigger() {
        // Case 1: We are already waiting for an answer (Retry)
        if (currentExpectedPhrase != null) {
            retryCount = 0
            isInteractionCancelled = false
            startListening()
            return
        }

        // Case 2: We are playing, but near a timestamp (Pre-emptive Answer)
        // logic: minByOrNull finds the closest one (e.g. 5:29 is closer to 5:30 than 5:32)
        val target = nearbyAnnotation
        if (target != null) {
            // Check if we already did this one
            if (handledTimestamps.contains(target.timestampMs)) return

            // Trigger the interaction manually
            pauseAndListen(target)
        }
    }


    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    private fun startProgressUpdater() { handler.post(updateProgressRunnable) }
    private fun stopProgressUpdater() { handler.removeCallbacks(updateProgressRunnable) }

    private fun updateUIState() {
        exoPlayer?.let {
            val currentPos = it.currentPosition
            val dur = it.duration

            if (dur > 0) {
                currentProgress = currentPos.toFloat() / dur.toFloat()
                currentTimeLabel = formatTime(currentPos)
            }

            // NEW: Find closest annotation within +/- 2 seconds (2000ms)
            nearbyAnnotation = annotations.minByOrNull { Math.abs(it.timestampMs - currentPos) }
                ?.takeIf { Math.abs(it.timestampMs - currentPos) <= 2000 }

            if (::currentCourse.isInitialized) {
                ProgressManager.saveLessonProgress(context, currentCourse.lessons[currentLessonIndex].id, currentPos)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val m = totalSecs / 60
        val s = totalSecs % 60
        return "%02d:%02d".format(m, s)
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        speechRecognizer?.destroy()
        soundPool?.release()
        stopProgressUpdater()
    }

    // --- EDITOR LOGIC ---

    fun addAnnotation(timestampMs: Long, phrase: String) {
        val newAnnotation = Annotation(timestampMs, phrase)
        val mutableList = annotations.toMutableList()
        mutableList.add(newAnnotation)
        saveAndReloadAnnotations(mutableList)
    }

    fun editAnnotation(oldTimestampMs: Long, newTimestampMs: Long, newPhrase: String) {
        val mutableList = annotations.toMutableList()
        // Find by timestamp
        val index = mutableList.indexOfFirst { it.timestampMs == oldTimestampMs }

        if (index != -1) {
            mutableList.removeAt(index)
            mutableList.add(Annotation(newTimestampMs, newPhrase))
            saveAndReloadAnnotations(mutableList)
        } else {
            Log.e("PlayerVM", "Could not find annotation to edit at $oldTimestampMs")
        }
    }

    // FIX: Changed parameter from Annotation? to Long
    fun deleteAnnotation(timestampMs: Long) {
        val mutableList = annotations.toMutableList()
        val index = mutableList.indexOfFirst { it.timestampMs == timestampMs }

        if (index != -1) {
            mutableList.removeAt(index)

            // Check if we are deleting the one currently active/waiting
            if (currentAnnotation?.timestampMs == timestampMs) {
                cancelInteraction()
            }

            saveAndReloadAnnotations(mutableList)
        }
    }

    private fun saveAndReloadAnnotations(list: MutableList<Annotation>) {
        val lesson = currentLesson ?: return
        list.sortBy { it.timestampMs }

        val sb = StringBuilder()
        for (item in list) {
            val totalSeconds = item.timestampMs / 1000
            val mm = totalSeconds / 60
            val ss = totalSeconds % 60
            val timeString = "%02d:%02d".format(mm, ss)
            sb.append("$timeString ${item.expectedPhrase}\n")
        }

        try {
            val file = File(lesson.transcriptionPath)
            file.writeText(sb.toString())
        } catch (e: Exception) {
            Log.e("PlayerVM", "Failed to save annotations", e)
            return
        }

        loadCurrentLesson(autoPlay = false)
    }

    // --- SPEED CONTROL ---
    var playbackSpeed by mutableStateOf(1.0f)
        private set

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }
}