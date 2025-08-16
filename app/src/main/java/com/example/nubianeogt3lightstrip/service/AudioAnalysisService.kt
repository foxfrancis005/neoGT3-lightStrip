package com.example.nubianeogt3lightstrip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.nubianeogt3lightstrip.MainActivity
import com.example.nubianeogt3lightstrip.R
import com.example.nubianeogt3lightstrip.audio.AudioAnalyzer
import com.example.nubianeogt3lightstrip.controller.NubiaLedController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/**
 * Servicio en segundo plano para análisis de audio y control de LEDs
 */
class AudioAnalysisService : Service() {
    
    companion object {
        private const val TAG = "AudioAnalysisService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_analysis_channel"
        
        const val ACTION_START_ANALYSIS = "START_ANALYSIS"
        const val ACTION_STOP_ANALYSIS = "STOP_ANALYSIS"
        const val ACTION_SET_SENSITIVITY = "SET_SENSITIVITY"
        const val ACTION_SET_MODE = "SET_MODE"
        
        const val EXTRA_SENSITIVITY = "sensitivity"
        const val EXTRA_MODE = "mode"
        
        const val MODE_SPECTRUM = "spectrum"
        const val MODE_BASS_ONLY = "bass_only"
        const val MODE_RAINBOW = "rainbow"
        const val MODE_BREATHING = "breathing"
    }
    
    private lateinit var audioAnalyzer: AudioAnalyzer
    private lateinit var ledController: NubiaLedController
    private var serviceJob: Job? = null
    private var analysisJob: Job? = null
    
    private var isAnalyzing = false
    private var currentMode = MODE_SPECTRUM
    private var sensitivity = 1.0f
    
    override fun onCreate() {
        super.onCreate()
        
        audioAnalyzer = AudioAnalyzer()
        ledController = NubiaLedController(this)
        
        createNotificationChannel()
        Log.i(TAG, "Servicio de análisis de audio creado")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        when (action) {
            ACTION_START_ANALYSIS -> startAnalysis()
            ACTION_STOP_ANALYSIS -> stopAnalysis()
            ACTION_SET_SENSITIVITY -> {
                val newSensitivity = intent.getFloatExtra(EXTRA_SENSITIVITY, 1.0f)
                setSensitivity(newSensitivity)
            }
            ACTION_SET_MODE -> {
                val newMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_SPECTRUM
                setMode(newMode)
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Inicia el análisis de audio y el servicio en primer plano
     */
    private fun startAnalysis() {
        if (isAnalyzing) return
        
        // Verificar permisos antes de iniciar el servicio foreground
        if (!checkAudioPermissions()) {
            Log.e(TAG, "No se tienen los permisos necesarios para el análisis de audio")
            stopSelf()
            return
        }
        
        val notification = createNotification("Analizando audio...")
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permisos al iniciar servicio foreground: ${e.message}")
            stopSelf()
            return
        }
        
        if (audioAnalyzer.startAnalysis()) {
            isAnalyzing = true
            startLedControl()
            Log.i(TAG, "Análisis de audio iniciado")
        } else {
            Log.e(TAG, "Error al iniciar análisis de audio")
            stopSelf()
        }
    }
    
    /**
     * Verifica si los permisos de audio están concedidos
     */
    private fun checkAudioPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, 
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Detiene el análisis de audio
     */
    private fun stopAnalysis() {
        if (!isAnalyzing) return
        
        isAnalyzing = false
        analysisJob?.cancel()
        audioAnalyzer.stopAnalysis()
        ledController.turnOff()
        
        stopForeground(true)
        Log.i(TAG, "Análisis de audio detenido")
    }
    
    /**
     * Inicia el control de LEDs basado en el análisis de audio
     */
    private fun startLedControl() {
        analysisJob = CoroutineScope(Dispatchers.Main).launch {
            audioAnalyzer.audioLevels.collect { levels ->
                if (isAnalyzing) {
                    updateLedsBasedOnAudio(levels)
                }
            }
        }
    }
    
    /**
     * Actualiza los LEDs basándose en los niveles de audio
     */
    private fun updateLedsBasedOnAudio(levels: AudioAnalyzer.AudioLevels) {
        when (currentMode) {
            MODE_SPECTRUM -> updateSpectrumMode(levels)
            MODE_BASS_ONLY -> updateBassOnlyMode(levels)
            MODE_RAINBOW -> updateRainbowMode(levels)
            MODE_BREATHING -> updateBreathingMode(levels)
        }
    }
    
    /**
     * Modo espectrograma: diferentes colores para diferentes frecuencias
     */
    private fun updateSpectrumMode(levels: AudioAnalyzer.AudioLevels) {
        val bassIntensity = (levels.bass * sensitivity * 255).roundToInt().coerceIn(0, 255)
        val midIntensity = (levels.mid * sensitivity * 255).roundToInt().coerceIn(0, 255)
        val trebleIntensity = (levels.treble * sensitivity * 255).roundToInt().coerceIn(0, 255)
        
        // Mapear frecuencias a colores RGB
        val red = trebleIntensity  // Agudos -> Rojo
        val green = midIntensity   // Medios -> Verde
        val blue = bassIntensity   // Bajos -> Azul
        
        ledController.setColor(red, green, blue)
    }
    
    /**
     * Modo solo bajos: intensidad de azul basada en bajos
     */
    private fun updateBassOnlyMode(levels: AudioAnalyzer.AudioLevels) {
        val intensity = (levels.bass * sensitivity * 255).roundToInt().coerceIn(0, 255)
        ledController.setColor(0, 0, intensity)
    }
    
    /**
     * Modo arcoíris: ciclo de colores con intensidad basada en audio
     */
    private fun updateRainbowMode(levels: AudioAnalyzer.AudioLevels) {
        val time = System.currentTimeMillis() / 1000.0
        val intensity = levels.overall * sensitivity
        
        val red = (Math.sin(time) * 127.5 + 127.5) * intensity
        val green = (Math.sin(time + 2.0 * Math.PI / 3.0) * 127.5 + 127.5) * intensity
        val blue = (Math.sin(time + 4.0 * Math.PI / 3.0) * 127.5 + 127.5) * intensity
        
        ledController.setColor(
            red.roundToInt().coerceIn(0, 255),
            green.roundToInt().coerceIn(0, 255),
            blue.roundToInt().coerceIn(0, 255)
        )
    }
    
    /**
     * Modo respiración: intensidad variable de un color fijo
     */
    private fun updateBreathingMode(levels: AudioAnalyzer.AudioLevels) {
        val baseIntensity = 0.3f
        val audioIntensity = levels.overall * sensitivity * 0.7f
        val totalIntensity = (baseIntensity + audioIntensity).coerceIn(0f, 1f)
        
        val color = (totalIntensity * 255).roundToInt()
        ledController.setColor(color, color / 2, color / 4) // Tono cálido
    }
    
    /**
     * Establece la sensibilidad del análisis
     */
    private fun setSensitivity(newSensitivity: Float) {
        sensitivity = newSensitivity.coerceIn(0.1f, 3.0f)
        Log.d(TAG, "Sensibilidad establecida: $sensitivity")
    }
    
    /**
     * Establece el modo de visualización
     */
    private fun setMode(newMode: String) {
        currentMode = newMode
        Log.d(TAG, "Modo establecido: $currentMode")
    }
    
    /**
     * Crea el canal de notificación
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Análisis de Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de análisis de audio para control de LEDs"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Crea la notificación del servicio en primer plano
     */
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Nubia LED Controller")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAnalysis()
        analysisJob?.cancel()
        serviceJob?.cancel()
        audioAnalyzer.cleanup()
        Log.i(TAG, "Servicio destruido")
    }
}
