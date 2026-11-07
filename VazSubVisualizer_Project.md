# VAZ 2110 Subwoofer VU - Android Project

Этот проект содержит полный исходный код для создания Android-приложения с визуализацией сабвуфера в автомобиле ВАЗ 2110. Приложение захватывает системный звук, применяет эффекты глубокого баса и выводит анимированную графику.

Ниже приведены файлы, которые вам нужно создать в своем репозитории на GitHub.

---

### 1. Файл: `build.gradle.kts` (в корневом каталоге проекта)
```kotlin
plugins {
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.0"
}

android {
    namespace = "com.example.vazsubvisualizer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vazsubvisualizer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.0"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
}
```

---

### 2. Файл: `settings.gradle.kts` (в корневом каталоге проекта)
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "VazSubVisualizer"
```

---

### 3. Файл: `src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:allowBackup="true"
        android:label="VAZ 2110 Sub VU"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        
        <service 
            android:name=".AudioCaptureService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

### 4. Файл: `src/main/java/com/example/vazsubvisualizer/AudioCaptureService.kt`
```kotlin
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
            .setContentText("Визуализация работает")
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
            
            drebezgTrack?.write(samples, 0, samples.size)
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
```

---

### 5. Файл: `src/main/java/com/example/vazsubvisualizer/MainActivity.kt`
```kotlin
package com.example.vazsubvisualizer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var audioService: AudioCaptureService? = null
    private var isBound = false
    private val bassValue = mutableStateOf(0f)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioCaptureService.LocalBinder
            audioService = binder.getService()
            isBound = true
            
            MainScope().launch {
                audioService?.bassIntensity?.collectLatest { intensity ->
                    bassValue.value = intensity
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startAudioService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startAudioService()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            var isWhiteCar by remember { mutableStateOf(false) }
            var isBoostActive by remember { mutableStateOf(true) }
            var isDrebezgActive by remember { mutableStateOf(true) }

            val animatedBass = remember { Animatable(0f) }
            LaunchedEffect(bassValue.value) {
                animatedBass.animateTo(
                    targetValue = bassValue.value,
                    animationSpec = tween(durationMillis = 30)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ВАЗ 2110 SUBWOOFER VU",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    VazCarVisualizer(
                        bass = animatedBass.value,
                        isWhiteCar = isWhiteCar
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Цвет кузова: ${if (isWhiteCar) "Белый" else "Черный"}", color = Color.White)
                            Button(onClick = { isWhiteCar = !isWhiteCar }) {
                                Text("Сменить")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Глубокий Саб-Звук (DSP)", color = Color.White)
                            Switch(
                                checked = isBoostActive,
                                onCheckedChange = {
                                    isBoostActive = it
                                    audioService?.setBassBoostEnabled(it)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Дребезг кузова и обшивки", color = Color.White)
                            Switch(
                                checked = isDrebezgActive,
                                onCheckedChange = {
                                    isDrebezgActive = it
                                    audioService?.setDrebezgEnabled(it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startAudioService() {
        val intent = Intent(this, AudioCaptureService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun VazCarVisualizer(bass: Float, isWhiteCar: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        val shakeX = if (bass > 0.4f) Random.nextFloat() * (bass * 15f) - (bass * 7.5f) else 0f
        val shakeY = if (bass > 0.4f) Random.nextFloat() * (bass * 15f) - (bass * 7.5f) else 0f

        val carColor = if (isWhiteCar) Color(0xFFEBEBEB) else Color(0xFF1A1A1A)
        val centerX = width / 2f + shakeX
        val centerY = height / 2f + shakeY

        drawRect(
            color = Color(0xFF0A0A0A),
            topLeft = Offset(centerX - 220f, centerY - 280f),
            size = Size(440f, 160f)
        )
        
        drawRect(
            color = carColor,
            topLeft = Offset(centerX - 320f, centerY - 120f),
            size = Size(640f, 320f)
        )
        if (!isWhiteCar) {
            drawRect(
                color = Color.DarkGray,
                topLeft = Offset(centerX - 320f, centerY - 120f),
                size = Size(640f, 320f),
                style = Stroke(width = 3f)
            )
        }

        drawRect(
            color = Color(0xFF990000),
            topLeft = Offset(centerX - 310f, centerY - 100f),
            size = Size(620f, 50f)
        )
        drawRect(color = Color.White, topLeft = Offset(centerX - 120f, centerY - 95f), size = Size(60f, 40f))
        drawRect(color = Color.White, topLeft = Offset(centerX + 60f, centerY - 95f), size = Size(60f, 40f))

        drawRect(
            color = Color(0xFFF0F0F0),
            topLeft = Offset(centerX - 100f, centerY + 250f),
            size = Size(200f, 50f)
        )

        val trunkWidth = 460f
        val trunkHeight = 240f
        val trunkTop = centerY - 30f
        
        drawRect(
            color = Color(0xFF0F0F14),
            topLeft = Offset(centerX - trunkWidth / 2, trunkTop),
            size = Size(trunkWidth, trunkHeight)
        )

        val subCenterX = centerX
        val subCenterY = trunkTop + trunkHeight / 2f
        
        val maxSubRadius = 110f
        val subScale = 1.0f + (bass * 0.22f)
        val currentRadius = maxSubRadius * subScale

        drawCircle(
            color = Color(0xFF262626),
            radius = maxSubRadius * 1.15f,
            center = Offset(subCenterX, subCenterY)
        )

        drawCircle(
            color = Color(0xFF151515),
            radius = currentRadius,
            center = Offset(subCenterX, subCenterY),
            style = Stroke(width = 25f)
        )

        drawCircle(
            color = Color(0xFF333333),
            radius = currentRadius - 15f,
            center = Offset(subCenterX, subCenterY)
        )

        drawCircle(
            color = Color(0xFF1F1F1F),
            radius = (maxSubRadius * 0.4f) * (1.0f + (bass * 0.1f)),
            center = Offset(subCenterX, subCenterY)
        )

        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val boltX = subCenterX + (maxSubRadius * 1.1f) * Math.cos(angle).toFloat()
            val boltY = subCenterY + (maxSubRadius * 1.1f) * Math.sin(angle).toFloat()
            drawCircle(color = Color.Gray, radius = 4f, center = Offset(boltX, boltY))
        }
    }
}
```

---

### 6. Файл Автосборщика: `.github/workflows/android.yml`
```yaml
name: Android CI

on:
  push:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      continue-on-error: true

    - name: Build APK (Debug)
      run: |
        if [ -f "./gradlew" ]; then
          ./gradlew assembleDebug
        else
          gradle assembleDebug || npx android-actions-core build
        fi

    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: VAZ-2110-Subwoofer-App
        path: "**/build/outputs/apk/debug/*.apk"
```
