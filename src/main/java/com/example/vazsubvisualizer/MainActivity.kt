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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private var audioService: AudioCaptureService? = null
    private var isBound = false
    private val bassValue = mutableStateOf(0f)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioCaptureService.LocalBinder
            audioService = binder.getService()
            isBound = true
            
            lifecycleScope.launch {
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
            topLeft = Offset(centerX - 100f, centerY + 130f),
            size = Size(200f, 50f)
        )

        val trunkWidth = 460f
        val trunkHeight = 210f
        val trunkTop = centerY - 30f
        
        drawRect(
            color = Color(0xFF0F0F14),
            topLeft = Offset(centerX - trunkWidth / 2, trunkTop),
            size = Size(trunkWidth, trunkHeight)
        )

        val subCenterX = centerX
        val subCenterY = trunkTop + trunkHeight / 2f
        
        val maxSubRadius = 90f
        val subScale = 1.0f + (bass * 0.25f)
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
