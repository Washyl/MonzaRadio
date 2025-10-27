
package com.monza.radio

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.monza.radio.databinding.ActivityMainBinding
import com.monza.radio.favorites.FavoritesAdapter
import com.monza.radio.favorites.FavoritesManager
import com.monza.radio.radio.RadioService
import com.monza.radio.radio.UnisocFmController
import com.monza.radio.streaming.StreamPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var fmController: UnisocFmController
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var favAdapter: FavoritesAdapter
    private lateinit var streamPlayer: StreamPlayer
    private lateinit var audioManager: AudioManager

    private val rdsPollIntervalMs = 3000L

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> /* ignore for now */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        fmController = UnisocFmController.getInstance(applicationContext)
        favoritesManager = FavoritesManager(this)
        streamPlayer = StreamPlayer(this)

        // Recycler
        favAdapter = FavoritesAdapter { freq -> setFrequency(freq) }
        binding.rvFavorites.layoutManager = LinearLayoutManager(this)
        binding.rvFavorites.adapter = favAdapter
        favAdapter.submitList(favoritesManager.getFavorites())

        // Request important permissions (Bluetooth/Location/Internet)
        requestPermissions.launch(arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
        ))

        // Seekbar change
        binding.seekFreq.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val freq = progressToFreq(progress)
                binding.tvFrequency.text = String.format("%.1f MHz", freq)
                if (fromUser && binding.togglePlay.isChecked) {
                    if (fmController.hasHardware()) fmController.tune(freq)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Play toggle
        binding.togglePlay.setOnCheckedChangeListener { _, isChecked ->
            val freq = progressToFreq(binding.seekFreq.progress)

            if (isChecked) {
                if (fmController.hasHardware()) {
                    fmController.open()
                    fmController.tune(freq)
                    fmController.setMuted(false)
                    startRdsPolling()
                } else {
                    fmController.startSimulation()
                    fmController.tune(freq)
                    startRdsPolling()
                }

                // Start foreground service safely
                val serviceIntent = Intent(this, com.monza.radio.radio.RadioService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                if (fmController.hasHardware()) {
                    fmController.setMuted(true)
                    fmController.close()
                } else {
                    fmController.stopSimulation()
                }
                stopService(Intent(this, com.monza.radio.radio.RadioService::class.java))
            }
        }


        // Speaker toggle — set speakerphone and inform fmController
        binding.toggleSpeaker.setOnCheckedChangeListener { _, isChecked ->
            audioManager.isSpeakerphoneOn = isChecked
            fmController.setSpeakerOn(isChecked)
        }

        // Scan
        binding.btnScan.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch {
                fmController.autoScan { foundFreq ->
                    withContext(Dispatchers.Main) {
                        setFrequency(foundFreq)
                        Toast.makeText(this@MainActivity, "Found ${"%.1f".format(foundFreq)} MHz", Toast.LENGTH_SHORT).show()
                    }
                    delay(800)
                }
                it.isEnabled = true
            }
        }

        // Favorite
        binding.btnFav.setOnClickListener {
            val freq = progressToFreq(binding.seekFreq.progress)
            if (favoritesManager.isFavorite(freq)) {
                favoritesManager.removeFavorite(freq)
                binding.btnFav.setImageResource(android.R.drawable.btn_star_big_off)
            } else {
                favoritesManager.addFavorite(freq)
                binding.btnFav.setImageResource(android.R.drawable.btn_star_big_on)
            }
            favAdapter.submitList(favoritesManager.getFavorites())
        }

        // Volume controls
        binding.btnVolUp.setOnClickListener { changeVolume(+1) }
        binding.btnVolDown.setOnClickListener { changeVolume(-1) }
        binding.toggleMute.setOnCheckedChangeListener { _, isChecked -> fmController.setMuted(isChecked) }

        // Stream play/stop (internet fallback)
        binding.btnStreamPlay.setOnClickListener {
            val url = binding.etStreamUrl.text.toString().trim()
            if (url.isEmpty()) { Toast.makeText(this, "Enter stream URL", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (streamPlayer.isPlaying()) {
                streamPlayer.stop()
                binding.btnStreamPlay.text = "Play"
            } else {
                streamPlayer.play(url)
                binding.btnStreamPlay.text = "Stop"
            }
        }

        // Initialize to 99.5
        setFrequency(99.5f)
    }

    private fun setFrequency(freq: Float) {
        val p = freqToProgress(freq)
        binding.seekFreq.progress = p
        binding.tvFrequency.text = String.format("%.1f MHz", freq)
        if (binding.togglePlay.isChecked && fmController.hasHardware()) fmController.tune(freq)
        binding.btnFav.setImageResource(if (favoritesManager.isFavorite(freq)) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off)
    }

    private fun progressToFreq(progress: Int): Float = 87.5f + progress * 0.1f
    private fun freqToProgress(freq: Float): Int = ((freq - 87.5f) / 0.1f).toInt().coerceIn(0, 205)

    private fun changeVolume(delta: Int) {
        val stream = AudioManager.STREAM_MUSIC
        audioManager.adjustStreamVolume(stream, if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        val vol = audioManager.getStreamVolume(stream)
        val max = audioManager.getStreamMaxVolume(stream)
        val perc = (vol.toFloat() / max.toFloat() * 100).toInt()
        binding.tvVolume.text = "Vol $perc%"
    }

    private fun startRdsPolling() {
        lifecycleScope.launch {
            while (binding.togglePlay.isChecked && fmController.hasHardware()) {
                val rds = fmController.getRdsProgramName() ?: "--"
                withContext(Dispatchers.Main) { binding.tvRds.text = "Station: $rds" }
                kotlinx.coroutines.delay(rdsPollIntervalMs)
            }
            // also handle simulation mode
            while (binding.togglePlay.isChecked && !fmController.hasHardware()) {
                val rds = fmController.getRdsProgramName() ?: "--"
                withContext(Dispatchers.Main) { binding.tvRds.text = "Station: $rds" }
                kotlinx.coroutines.delay(rdsPollIntervalMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fmController.close()
        streamPlayer.stop()
    }
}
