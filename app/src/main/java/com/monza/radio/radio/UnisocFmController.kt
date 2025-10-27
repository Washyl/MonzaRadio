package com.monza.radio.radio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sin

private const val TAG = "UnisocFmController"
private const val DEFAULT_FREQ = 99.5f

class UnisocFmController private constructor(private val ctx: Context) {

    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Try to detect UNISOC FM service/class by reflection
    private val isUnisocPresent: Boolean by lazy {
        try {
            Class.forName("com.unisoc.fmradio.FmNative")
            true
        } catch (e: Exception) {
            false
        }
    }

    // If true, force simulation regardless of reflection detection
    @Volatile private var forceMockMode: Boolean = false

    private val isMockMode: Boolean by lazy { !isUnisocPresent }

    // State
    @Volatile private var currentFreq: Float = DEFAULT_FREQ
    @Volatile private var muted: Boolean = false
    @Volatile private var speakerOn: Boolean = true

    // Simulation audio
    private var audioTrack: AudioTrack? = null
    private var simThreadRunning = false

    companion object {
        @Volatile private var INSTANCE: UnisocFmController? = null
        fun getInstance(ctx: Context): UnisocFmController = INSTANCE ?: synchronized(this) {
            INSTANCE ?: UnisocFmController(ctx.applicationContext).also { INSTANCE = it }
        }
    }

    // Public: force mock mode (useful for emulator/testing)
    fun forceMock(force: Boolean) {
        forceMockMode = force
        Log.i(TAG, "forceMockMode = $force")
    }

    fun hasHardware(): Boolean = !(isMockMode || forceMockMode)

    fun open() {
        Log.i(TAG, "open() unisoc=$isUnisocPresent mock=${isMockMode || forceMockMode}")
        if (isMockMode || forceMockMode) return
        try {
            val cls = Class.forName("com.unisoc.fmradio.FmNative")
            val method = cls.getDeclaredMethod("initFM", Context::class.java)
            method.isAccessible = true
            method.invoke(null, ctx)
            Log.i(TAG, "Invoked Unisoc FmNative.initFM")
        } catch (e: Exception) {
            Log.w(TAG, "Unisoc init reflection failed: ${e.message}")
        }
    }

    fun close() {
        Log.i(TAG, "close()")
        stopSimulation()
        if (!(isMockMode || forceMockMode)) {
            try {
                val cls = Class.forName("com.unisoc.fmradio.FmNative")
                val method = cls.getDeclaredMethod("closeFM")
                method.isAccessible = true
                method.invoke(null)
            } catch (e: Exception) {
                Log.w(TAG, "Unisoc close reflection failed: ${e.message}")
            }
        }
    }

    fun tune(freq: Float) {
        val f = round(freq * 10) / 10f
        Log.i(TAG, "tune -> $f MHz (mock=${isMockMode || forceMockMode})")
        currentFreq = f
        if (!(isMockMode || forceMockMode)) {
            try {
                val cls = Class.forName("com.unisoc.fmradio.FmNative")
                val method = cls.getDeclaredMethod("tune", Float::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(null, f)
            } catch (e: Exception) {
                Log.w(TAG, "Unisoc tune reflection failed: ${e.message}")
            }
        }
    }

    fun setMuted(m: Boolean) {
        muted = m
        try {
            if (m) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            else audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (e: Exception) {
            Log.w(TAG, "mute error: ${e.message}")
        }
    }

    fun setSpeakerOn(on: Boolean) {
        speakerOn = on
        audioManager.isSpeakerphoneOn = on
    }

    fun getCurrentFrequency(): Float = currentFreq

    fun getRdsProgramName(): String? {
        if (isMockMode || forceMockMode) {
            return when (((currentFreq * 10).toInt() % 7)) {
                0 -> "Monza FM"
                1 -> "Road Radio"
                2 -> "Top Hits"
                3 -> "News Live"
                4 -> "Classic"
                5 -> "Sports"
                else -> "FM ${"%.1f".format(currentFreq)}"
            }
        } else {
            try {
                val cls = Class.forName("com.unisoc.fmradio.FmNative")
                val method = cls.getDeclaredMethod("getCurrentRDS")
                method.isAccessible = true
                val res = method.invoke(null) as? String
                return res
            } catch (e: Exception) {
                Log.w(TAG, "Unisoc RDS reflection failed: ${e.message}")
                return null
            }
        }
    }

    suspend fun autoScan(onFound: suspend (Float) -> Unit) {
        val start = 87.5f
        val end = 108.0f
        var f = start
        while (f <= end) {
            tune(f)
            val found = if (isMockMode || forceMockMode) {
                ((f * 10).toInt() % 5 == 0)
            } else {
                try {
                    val cls = Class.forName("com.unisoc.fmradio.FmNative")
                    val method = cls.getDeclaredMethod("isStationAvailable", Float::class.javaPrimitiveType)
                    method.isAccessible = true
                    val res = method.invoke(null, f) as? Boolean ?: false
                    res
                } catch (e: Exception) {
                    Log.w(TAG, "Unisoc scan reflection failed: ${e.message}")
                    false
                }
            }
            if (found) onFound(f)
            f = (round((f + 0.1f) * 10) / 10f)
            delay(80L)
        }
    }

    // ----- Simulation using AudioTrack -----
    fun startSimulation() {
        if (simThreadRunning) return
        simThreadRunning = true
        val sampleRate = 44100
        val buffSize = AudioTrack.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(buffSize)
            .build()
        audioTrack?.play()

        // start thread to feed sine + noise to simulate radio
        Thread {
            val twoPi = 2 * PI
            var phase = 0.0
            val buffer = ShortArray(buffSize)
            while (simThreadRunning) {
                val tone = 300 + ((currentFreq - 87.5f) * 10).toInt() // vary tone a bit by frequency
                for (i in buffer.indices) {
                    val sample = (sin(phase * twoPi * tone / sampleRate) * 0.3 * Short.MAX_VALUE).toInt().toShort()
                    buffer[i] = sample
                    phase += 1.0
                }
                try {
                    audioTrack?.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    Log.w(TAG, "AudioTrack write failed: ${e.message}")
                }
            }
            try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
            audioTrack = null
        }.start()
    }

    fun stopSimulation() {
        simThreadRunning = false
    }
}
