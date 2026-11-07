package com.example.vazsubvisualizer

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.Visualizer
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.sin

class AudioCaptureService : Service() {

    private var visualizer: Visualizer? = null
    private var bassBoost: BassBoost? = null
    private val binder = LocalBinder()
    
    val bassIntensity = MutableStateFlow(0f)
    
    private var isDrebezgEnabled = true
    private var drebezgTrack: AudioTrack? = null
    private var isPlayingDrebezg = false

    inner class LocalBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initAudioEffects()
        initVisualizer()
        initDrebezgGenerator()
    }

    private fun startForegroundService() {
        val channelId = "vaz_audio_channel"
        val channel = NotificationChannel(channelId, "VAZ Subwoofer Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Сабвуфер ВАЗ 2110 активен")
            .setContentText("Визуализация и обработка звука работают в фоне")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1, notification)
    }

    private fun initAudioEffects() {
        try {
            bassBoost = BassBoost(0, 0).apply {
                enabled = true
                setStrength(1000.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initVisualizer() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform == null) return
                        
                        var energy = 0f
                        for (i in 0 until waveform.size / 4) {
                            val sample = (waveform[i].toInt() and 0xFF) - 128
                            energy += abs(sample).toFloat()
                        }
                        val normalizedBass = (energy / (waveform.size / 4)) / 128f
                        val clampedBass = normalizedBass.coerceIn(0f, 1f)
                        
                        bassIntensity.value = clampedBass
                        
                        if (clampedBass > 0.4f && isDrebezgEnabled) {
                            triggerDrebezgSound(clampedBass)
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initDrebezgGenerator() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            22050, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        drebezgTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(22050)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        drebezgTrack?.play()
    }

    private fun triggerDrebezgSound(intensity: Float) {
        if (isPlayingDrebezg) return
        isPlayingDrebezg = true
        
        CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 22050
            val duration = 0.08
            val numSamples = (duration * sampleRate).toInt()
            val samples = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val rawSine = sin(2 * Math.PI * 45.0 * t)
                val distortion = if (rawSine > 0.3) 0.8 else if (rawSine < -0.3) -0.8 else rawSine
                val noise = (Math.random() * 2.0 - 1.0) * 0.2
                
                samples[i] = ((distortion + noise) * Short.MAX_VALUE * (intensity * 0.4f)).toInt().toShort()
            }
            
            try {
                drebezgTrack?.write(samples, 0, samples.size)
            } catch (e: Exception) { e.printStackTrace() }
            delay(60)
            isPlayingDrebezg = false
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        bassBoost?.enabled = enabled
    }

    fun setDrebezgEnabled(enabled: Boolean) {
        isDrebezgEnabled = enabled
    }

    override fun onDestroy() {
        visualizer?.enabled = false
        visualizer?.release()
        bassBoost?.release()
        drebezgTrack?.stop()
        drebezgTrack?.release()
        super.onDestroy()
    }
}
