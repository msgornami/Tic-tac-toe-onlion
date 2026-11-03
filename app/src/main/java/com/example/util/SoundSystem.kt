package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundSystem {

    private var soundEnabled = true
    private var volumeLevel = 0.8f
    private val soundScope = CoroutineScope(Dispatchers.Default)

    fun setPreferences(enabled: Boolean, volume: Float) {
        soundEnabled = enabled
        volumeLevel = volume
    }

    /**
     * Synthesis of sine wave tones to bypass dependency on external resources
     */
    private fun playSineTone(frequency: Double, durationMs: Int, volume: Float) {
        if (!soundEnabled) return

        soundScope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (durationMs * sampleRate) / 1000
                val generatedSustain = ShortArray(numSamples)

                val volCoeff = (volume * volumeLevel).coerceIn(0f, 1f)

                for (i in 0 until numSamples) {
                    // Smooth envelope at start and end of tone to prevent audible popping/clicking
                    val envelope = when {
                        i < 100 -> i / 100f
                        i > numSamples - 1500 -> (numSamples - i) / 1500f
                        else -> 1.0f
                    }
                    val sineVal = sin(2.0 * Math.PI * i / (sampleRate / frequency))
                    generatedSustain[i] = (sineVal * 32767.0 * volCoeff * envelope).toInt().toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(generatedSustain.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(generatedSustain, 0, generatedSustain.size)
                audioTrack.play()

                // Release resource after finish playing
                val delayTime = durationMs.toLong() + 100L
                soundScope.launch {
                    kotlinx.coroutines.delay(delayTime)
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (e: Exception) {
                        // Suppress background errors
                    }
                }
            } catch (e: Exception) {
                // Audio not supported or device muted
            }
        }
    }

    fun playMoveX() {
        // X consists of two crossing quick pen strokes on paper
        soundScope.launch {
            if (soundEnabled) {
                playSineTone(680.0, 45, 0.18f)
                kotlinx.coroutines.delay(65)
                playSineTone(840.0, 55, 0.15f)
            }
        }
    }

    fun playMoveO() {
        // O consists of a smooth circular loop pen stroke on paper
        soundScope.launch {
            if (soundEnabled) {
                playSineTone(730.0, 120, 0.16f)
            }
        }
    }

    fun playMenuClick() {
        playSineTone(880.00, 60, 0.25f) // Tactile click (A5)
    }

    fun playVictory() {
        soundScope.launch {
            // Victory Arpeggio (C major esports flourish)
            playSineTone(523.25, 150, 0.5f) // C5
            kotlinx.coroutines.delay(120)
            playSineTone(659.25, 150, 0.5f) // E5
            kotlinx.coroutines.delay(120)
            playSineTone(783.99, 150, 0.5f) // G5
            kotlinx.coroutines.delay(120)
            playSineTone(1046.50, 450, 0.6f) // C6 high triumph chord
        }
    }

    fun playDefeat() {
        soundScope.launch {
            // Sad slides (minor fall)
            playSineTone(349.23, 180, 0.5f) // F4
            kotlinx.coroutines.delay(150)
            playSineTone(293.66, 180, 0.5f) // D4
            kotlinx.coroutines.delay(150)
            playSineTone(220.00, 450, 0.6f) // A3 somber drone
        }
    }

    fun playDraw() {
        // Fallback draw sound (never triggers, but ready)
        playSineTone(261.63, 200, 0.3f)
    }
}
