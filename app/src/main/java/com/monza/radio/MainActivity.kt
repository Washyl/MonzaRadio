package com.monza.radio

import android.Manifest
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fmController: UnisocFmController
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var favAdapter: FavoritesAdapter
    private lateinit var audioManager: AudioManager

    private val rdsPollIntervalMs = 2500L

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms -> /* ignore result for now */ }

    // knob state
    private var isTouchingKnob = false
    private var lastAngle = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        fmController = UnisocFmController.getInstance(applicationContext)
        fmController.forceMock(true) // always simulate for emulator/testing

        favoritesManager = FavoritesManager(this)
        favAdapter = FavoritesAdapter { freq -> setFrequency(freq) }
        binding.rvFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFavorites.adapter = favAdapter
        favAdapter.submitList(favoritesManager.getFavorites())

        requestPermissions.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
        ))

        // UI wiring
        binding.btnPrev.setOnClickListener { stepFrequency(-0.1f) }
        binding.btnNext.setOnClickListener { stepFrequency(+0.1f) }
        binding.btnScan.isEnabled = true
        binding.btnScan.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch {
                fmController.autoScan { foundFreq ->
                    withContext(Dispatchers.Main) {
                        setFrequency(foundFreq)
                        Toast.makeText(this@MainActivity, "Found ${"%.1f".format(foundFreq)} MHz", Toast.LENGTH_SHORT).show()
                    }
                    delay(700)
                }
                it.isEnabled = true
            }
        }

        binding.btnFav.setOnClickListener {
            val freq = fmController.getCurrentFrequency()
            if (favoritesManager.isFavorite(freq)) {
                favoritesManager.removeFavorite(freq)
                binding.btnFav.setImageResource(R.drawable.ic_star_off)
            } else {
                favoritesManager.addFavorite(freq)
                binding.btnFav.setImageResource(R.drawable.ic_star_on)
            }
            favAdapter.submitList(favoritesManager.getFavorites())
        }

        binding.btnVolUp.setOnClickListener { changeVolume(+1) }
        binding.btnVolDown.setOnClickListener { changeVolume(-1) }
        binding.toggleMute.setOnCheckedChangeListener { _, isChecked -> fmController.setMuted(isChecked) }

        binding.toggleSpeaker.setOnCheckedChangeListener { _, isChecked ->
            audioManager.isSpeakerphoneOn = isChecked
            fmController.setSpeakerOn(isChecked)
        }

        binding.btnPlayPause.setOnClickListener {
            togglePlayPause(it as ImageButton)
        }

        // knob touch listener — rotate to tune
        setupKnobTouch(binding.imgKnob)

        // init frequency
        setFrequency(99.5f)
    }

    private fun togglePlayPause(btn: ImageButton) {
        val isPlaying = (btn.tag as? Boolean) ?: false
        if (!isPlaying) {
            // start playing
            fmController.startSimulation()
            fmController.tune(fmController.getCurrentFrequency())
            fmController.setMuted(false)
            startRdsPolling()
            btn.setImageResource(R.drawable.ic_pause)
            btn.tag = true
            // start foreground service
            val i = Intent(this, RadioService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, i) else startService(i)
        } else {
            // stop
            fmController.setMuted(true)
            fmController.stopSimulation()
            btn.setImageResource(R.drawable.ic_play)
            btn.tag = false
            stopService(Intent(this, RadioService::class.java))
        }
        // update favorite icon state
        binding.btnFav.setImageResource(if (favoritesManager.isFavorite(fmController.getCurrentFrequency())) R.drawable.ic_star_on else R.drawable.ic_star_off)
    }

    private fun setupKnobTouch(knob: ImageView) {
        knob.setOnTouchListener { view, event ->
            val cx = view.width / 2f
            val cy = view.height / 2f
            val x = event.x - cx
            val y = event.y - cy
            val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble()))
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isTouchingKnob = true
                    lastAngle = angle
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTouchingKnob) return@setOnTouchListener true
                    val delta = angle - lastAngle
                    // normalize delta to -180..180
                    var d = delta
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    // convert rotation to freq steps; experiment: 10 degrees -> 0.1 MHz
                    val freqDelta = (d / 10.0) * 0.1
                    val cur = fmController.getCurrentFrequency()
                    var next = (round((cur + freqDelta) * 10) / 10f).toDouble()
                    if (next < 87.5) next = 87.5
                    if (next > 108.0) next = 108.0
                    setFrequency(next.toFloat())
                    lastAngle = angle
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouchingKnob = false
                    // if playing, ensure fm is tuned
                    if ((binding.btnPlayPause.tag as? Boolean) == true) fmController.tune(fmController.getCurrentFrequency())
                    true
                }
                else -> false
            }
        }
    }

    private fun stepFrequency(delta: Float) {
        val cur = fmController.getCurrentFrequency()
        var next = kotlin.math.round((cur + delta) * 10) / 10f
        if (next < 87.5f) next = 87.5f
        if (next > 108f) next = 108f
        setFrequency(next)
    }

    private fun setFrequency(freq: Float) {
        val f = (kotlin.math.round(freq * 10) / 10f)
        binding.tvFrequency.text = String.format("%.1f", f)
        fmController.tune(f)
        // update favorite star
        binding.btnFav.setImageResource(if (favoritesManager.isFavorite(f)) R.drawable.ic_star_on else R.drawable.ic_star_off)
        // update RDS desc right away
        binding.tvStationDesc.text = "Freq ${"%.1f".format(f)} MHz"
    }

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
            while ((binding.btnPlayPause.tag as? Boolean) == true) {
                val rds = fmController.getRdsProgramName() ?: "--"
                withContext(Dispatchers.Main) {
                    binding.tvRds.text = if (rds.startsWith("Station:")) rds else "Station: $rds"
                    binding.tvStationDesc.text = "Freq ${"%.1f".format(fmController.getCurrentFrequency())} MHz"
                }
                delay(rdsPollIntervalMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fmController.stopSimulation()
    }
}
