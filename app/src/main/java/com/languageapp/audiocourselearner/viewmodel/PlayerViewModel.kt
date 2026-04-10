package com.languageapp.audiocourselearner.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
import com.languageapp.audiocourselearner.data.AppSettings
import com.languageapp.audiocourselearner.data.ProgressManager
import com.languageapp.audiocourselearner.model.Course
import com.languageapp.audiocourselearner.model.Lesson
import com.languageapp.audiocourselearner.logic.SpeechManager
import com.languageapp.audiocourselearner.logic.AnnotationManager
import com.languageapp.audiocourselearner.logic.FeedbackAudioManager
import com.languageapp.audiocourselearner.model.Annotation
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.io.File


class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val annotationManager = AnnotationManager()
    private val feedbackManager = FeedbackAudioManager(context)
    private val speechManager = SpeechManager(
        context,
        onReady = {
            if (!isInteractionCancelled) isListening = true
        },
        onResults = {
            if (!isInteractionCancelled) {
                if (it.isNotEmpty()) validateAnswer(it) else handleWrongAnswer("Unclear")
            }
        },
        onError = {
            if (!isInteractionCancelled) {
                isListening = false
                handleWrongAnswer(it)
            }
        }
    )

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
    var exoPlayer by mutableStateOf<ExoPlayer?>(null)
        private set
    private var annotations = listOf<Annotation>()

    private val handler = Handler(Looper.getMainLooper())
    private val retryToken = Any()
    private val resumeToken = Any()

    private var isInteractionCancelled = false
    private var retryCount = 0

    var nearbyAnnotation by mutableStateOf<Annotation?>(null)
        private set

    // -- DATA --
    private lateinit var currentCourse: Course
    private var currentLessonIndex = 0
    private var targetLanguageCode: String = "en"

    var currentLesson by mutableStateOf<Lesson?>(null)
        private set

    var visibleAnnotations by mutableStateOf<List<Long>>(emptyList())
        private set

    var skipDurationMs: Long = 10000
    var durationMs by mutableStateOf(0L)
        private set

    var playbackSpeed by mutableStateOf(AppSettings.getPlaybackSpeed(context))
        private set

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateUIState()
            if (isPlaying) handler.postDelayed(this, 500)
        }
    }

    fun initialize(course: Course, startLessonId: String) {
        if (::currentCourse.isInitialized && currentCourse.id == course.id && currentLesson?.id == startLessonId && exoPlayer != null) {
            return
        }

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
            setPlaybackSpeed(playbackSpeed)

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
        if (autoPlay) play() else updateUIState()
    }

    fun cancelInteraction() {
        isInteractionCancelled = true
        isListening = false

        speechManager.stop()

        handler.removeCallbacksAndMessages(retryToken)
        handler.removeCallbacksAndMessages(resumeToken)

        feedbackMessage = null
        currentExpectedPhrase = null
        currentAnnotation = null
        retryCount = 0

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
        annotations = annotationManager.loadFromFile(path)
        visibleAnnotations = annotations.map { it.timestampMs }
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
        if (currentAnnotation == annotation && (isListening || feedbackMessage != null)) return
        if (handledTimestamps.contains(annotation.timestampMs)) return

        pause()
        isInteractionCancelled = false
        retryCount = 0
        currentAnnotation = annotation
        currentExpectedPhrase = annotation.expectedPhrase

        startListening()
    }

    private fun startListening() {
        if (isInteractionCancelled) return
        feedbackMessage = context.getString(R.string.feedback_listening)
        speechManager.startListening(targetLanguageCode)
    }

    private fun validateAnswer(userSpoke: String) {
        if (isInteractionCancelled) return

        val expected = currentExpectedPhrase ?: return
        val ratio = FuzzySearch.ratio(userSpoke.lowercase(), expected.lowercase())

        if (ratio > 65) {
            handleCorrectAnswer(userSpoke, expected)
        } else {
            handleWrongAnswer(userSpoke)
        }
    }

    private fun handleCorrectAnswer(userSpoke: String, expectedPhrase: String) {
        if (isInteractionCancelled) return

        feedbackMessage = context.getString(R.string.feedback_correct, userSpoke, expectedPhrase)
        currentAnnotation?.let { handledTimestamps.add(it.timestampMs) }
        feedbackManager.playSuccess()

        handler.postAtTime({
            if (isInteractionCancelled) return@postAtTime
            clearInteractionState()
            play()
        }, resumeToken, android.os.SystemClock.uptimeMillis() + 2000)
    }

    private fun handleWrongAnswer(userSpoke: String) {
        if (isInteractionCancelled) return

        feedbackManager.playError()

        if (retryCount == 0) {
            retryCount++
            feedbackMessage = context.getString(R.string.feedback_try_again, userSpoke)

            handler.postAtTime({
                if (!isInteractionCancelled) startListening()
            }, retryToken, android.os.SystemClock.uptimeMillis() + 1200)
        } else {
            feedbackMessage = context.getString(R.string.feedback_moving_on, currentExpectedPhrase ?: "")

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
        val wasPlaying = isPlaying
        handledTimestamps.clear()
        cancelInteraction()

        exoPlayer?.let { player ->
            val position = (player.duration * pct).toLong()
            player.seekTo(position)
            updateUIState()

            if (wasPlaying) {
                player.play()
            }
        }
    }

    fun jumpToNextAnnotation() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition

        val next = annotations.sortedBy { it.timestampMs }.firstOrNull { it.timestampMs > currentPos + 2000 }
        if (next != null) {
            cancelInteraction()
            player.seekTo((next.timestampMs - 2000).coerceAtLeast(0))
            updateUIState()
        }
    }

    fun jumpToPreviousAnnotation() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition

        val prev = annotations.filter { it.timestampMs < currentPos }.maxByOrNull { it.timestampMs }
        if (prev != null) {
            cancelInteraction()
            player.seekTo((prev.timestampMs - 2000).coerceAtLeast(0))
            updateUIState()
        }
    }

    fun hasNextAnnotation(): Boolean = annotations.any { it.timestampMs > getCurrentPosition() }
    fun hasPreviousAnnotation(): Boolean = annotations.any { it.timestampMs < getCurrentPosition() }

    fun skipForward() {
        val wasPlaying = isPlaying
        handledTimestamps.clear()
        cancelInteraction()
        exoPlayer?.let {
            it.seekTo(it.currentPosition + skipDurationMs)
            updateUIState()
            if (wasPlaying) it.play()
        }
    }

    fun skipBack() {
        val wasPlaying = isPlaying
        handledTimestamps.clear()
        cancelInteraction()
        exoPlayer?.let {
            it.seekTo(it.currentPosition - skipDurationMs)
            updateUIState()
            if (wasPlaying) it.play()
        }
    }

    fun manualListenTrigger() {
        if (currentExpectedPhrase != null) {
            retryCount = 0
            isInteractionCancelled = false
            startListening()
            return
        }

        val target = nearbyAnnotation
        if (target != null) {
            if (handledTimestamps.contains(target.timestampMs)) return
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

            nearbyAnnotation = annotations.minByOrNull { Math.abs(it.timestampMs - currentPos) }
                ?.takeIf { Math.abs(it.timestampMs - currentPos) <= 2000 }

            if (::currentCourse.isInitialized) {
                ProgressManager.saveLessonProgress(context, currentCourse.lessons[currentLessonIndex].id, currentPos)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        return "%02d:%02d".format(totalSecs / 60, totalSecs % 60)
    }

    // --- EDITOR LOGIC ---

    fun addAnnotation(timestampMs: Long, phrase: String) {
        val mutableList = annotations.toMutableList()
        mutableList.add(Annotation(timestampMs, phrase))
        saveAndReloadAnnotations(mutableList)
    }

    fun editAnnotation(oldTimestampMs: Long, newTimestampMs: Long, newPhrase: String) {
        val mutableList = annotations.toMutableList()
        val index = mutableList.indexOfFirst { it.timestampMs == oldTimestampMs }

        if (index != -1) {
            mutableList.removeAt(index)
            mutableList.add(Annotation(newTimestampMs, newPhrase))
            saveAndReloadAnnotations(mutableList)
        } else {
            Log.e("PlayerVM", "Could not find annotation to edit at $oldTimestampMs")
        }
    }

    fun deleteAnnotation(timestampMs: Long) {
        val mutableList = annotations.toMutableList()
        val index = mutableList.indexOfFirst { it.timestampMs == timestampMs }

        if (index != -1) {
            mutableList.removeAt(index)
            if (currentAnnotation?.timestampMs == timestampMs) {
                cancelInteraction()
            }
            saveAndReloadAnnotations(mutableList)
        }
    }

    private fun saveAndReloadAnnotations(list: MutableList<Annotation>) {
        val lesson = currentLesson ?: return
        annotationManager.saveToFile(lesson.transcriptionPath, list)
        loadCurrentLesson(autoPlay = false)
    }

    // --- SPEED CONTROL ---

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer?.setPlaybackSpeed(speed)
        AppSettings.setPlaybackSpeed(context, speed)
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        speechManager.destroy()
        feedbackManager.release()
        stopProgressUpdater()
    }
}