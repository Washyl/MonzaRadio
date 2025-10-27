
package com.monza.radio.streaming

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

private const val TAG = "StreamPlayer"

class StreamPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                setDataSource(url)
                isLooping = false
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error $what / $extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream play failed: ${e.message}")
        }
    }

    fun stop() {
        mediaPlayer?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true
}
