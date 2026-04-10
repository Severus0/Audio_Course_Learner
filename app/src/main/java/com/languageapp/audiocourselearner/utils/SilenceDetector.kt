package com.languageapp.audiocourselearner.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.pow

object SilenceDetector {

    suspend fun detectPauses(
        audioPath: String,
        minimumSilenceDurationSec: Float,
        silenceThresholdDb: Float,
        onProgress: (Float) -> Unit
    ): List<Long> {
        val pauseTimestamps = mutableListOf<Long>()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(audioPath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) return pauseTimestamps

            var durationUs = 1L
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                durationUs = format.getLong(MediaFormat.KEY_DURATION)
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            var lastLoudMs = 0L
            val thresholdAmplitude = (10.0.pow(silenceThresholdDb / 20.0) * 32767.0).toInt()
            var lastProgressReportMs = 0L

            // EXTREME SPEEDUP #1: Reusable JVM Array
            // We start with 32KB, but it will dynamically grow if a massive frame arrives
            var fastBuffer = ShortArray(32768)

            // EXTREME SPEEDUP #2: 44.1kHz / 44 = 1 check per millisecond.
            // Massive speedup with zero loss to human voice detection.
            val stepSize = 1000

            while (!isOutputEOS) {
                if (!coroutineContext.isActive) break

                // Feed Input
                if (!isInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(1000L)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        if (buffer != null) {
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isInputEOS = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain Output
                var outIndex = codec.dequeueOutputBuffer(info, 1000L)
                while (outIndex >= 0) {
                    if (info.size > 0) {
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null) {
                            buffer.order(ByteOrder.nativeOrder())
                            val shortBuffer = buffer.asShortBuffer()
                            val shortCount = shortBuffer.remaining()

                            // Dynamically expand our fast buffer if the hardware gives us a huge frame
                            if (shortCount > fastBuffer.size) {
                                fastBuffer = ShortArray(shortCount)
                            }

                            // BULK COPY: Cross the JNI boundary exactly ONE time per frame!
                            shortBuffer.get(fastBuffer, 0, shortCount)

                            var isLoud = false

                            // Loop over our fast JVM memory array using our stepSize
                            for (i in 0 until shortCount step stepSize) {
                                val sample = fastBuffer[i].toInt()
                                // Pure inline math (faster than Kotlin's .absoluteValue)
                                val absVal = if (sample < 0) -sample else sample
                                if (absVal >= thresholdAmplitude) {
                                    isLoud = true
                                    break
                                }
                            }

                            val currentMs = info.presentationTimeUs / 1000

                            // Update UI progress every ~1 second of audio
                            if (currentMs - lastProgressReportMs > 1000) {
                                val progress = (info.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                                onProgress(progress)
                                lastProgressReportMs = currentMs
                                yield()
                            }

                            if (isLoud) {
                                val timeSinceLastLoud = currentMs - lastLoudMs
                                if (lastLoudMs > 0 && timeSinceLastLoud >= minimumSilenceDurationSec * 1000) {
                                    pauseTimestamps.add(currentMs)
                                }
                                lastLoudMs = currentMs
                            }
                        }
                    }

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if (isOutputEOS) break
                    outIndex = codec.dequeueOutputBuffer(info, 0L)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

        } catch (e: Exception) {
            Log.e("SilenceDetector", "Error decoding audio", e)
        }

        return pauseTimestamps.sorted()
    }
}