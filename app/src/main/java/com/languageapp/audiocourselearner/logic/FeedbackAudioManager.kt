package com.languageapp.audiocourselearner.logic

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.languageapp.audiocourselearner.R

class FeedbackAudioManager(context: Context) {
    private var soundPool: SoundPool
    private var successId: Int = 0
    private var errorId: Int = 0
    private var loaded = false

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
        soundPool.setOnLoadCompleteListener { _, _, _ -> loaded = true }

        successId = soundPool.load(context, R.raw.success, 1)
        errorId = soundPool.load(context, R.raw.error, 1)
    }

    fun playSuccess() { if (loaded) soundPool.play(successId, 1f, 1f, 1, 0, 1f) }
    fun playError() { if (loaded) soundPool.play(errorId, 1f, 1f, 1, 0, 1f) }
    fun release() { soundPool.release() }
}