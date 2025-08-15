package com.example.nubianeogt3lightstrip.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.nubianeogt3lightstrip.audio.AudioAnalyzer
import com.example.nubianeogt3lightstrip.controller.NubiaLedController
import com.example.nubianeogt3lightstrip.service.AudioAnalysisService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel principal para la aplicación de control de LEDs
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val context = getApplication<Application>()
    private val ledController = NubiaLedController(context)
    
    // Estados de la UI
    private val _isAudioAnalysisActive = MutableStateFlow(false)
    val isAudioAnalysisActive: StateFlow<Boolean> = _isAudioAnalysisActive.asStateFlow()
    
    private val _currentMode = MutableStateFlow(AudioAnalysisService.MODE_SPECTRUM)
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()
    
    private val _sensitivity = MutableStateFlow(1.0f)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()
    
    private val _brightness = MutableStateFlow(80)
    val brightness: StateFlow<Int> = _brightness.asStateFlow()
    
    private val _selectedColor = MutableStateFlow(Triple(255, 255, 255))
    val selectedColor: StateFlow<Triple<Int, Int, Int>> = _selectedColor.asStateFlow()
    
    private val _ledControllerStatus = MutableStateFlow(ledController.getStatusInfo())
    val ledControllerStatus: StateFlow<String> = _ledControllerStatus.asStateFlow()
    
    init {
        // Actualizar el estado del controlador LED
        updateLedControllerStatus()
    }
    
    /**
     * Inicia o detiene el análisis de audio
     */
    fun toggleAudioAnalysis() {
        viewModelScope.launch {
            if (_isAudioAnalysisActive.value) {
                stopAudioAnalysis()
            } else {
                startAudioAnalysis()
            }
        }
    }
    
    /**
     * Inicia el análisis de audio
     */
    private fun startAudioAnalysis() {
        val intent = Intent(context, AudioAnalysisService::class.java).apply {
            action = AudioAnalysisService.ACTION_START_ANALYSIS
        }
        context.startForegroundService(intent)
        _isAudioAnalysisActive.value = true
    }
    
    /**
     * Detiene el análisis de audio
     */
    private fun stopAudioAnalysis() {
        val intent = Intent(context, AudioAnalysisService::class.java).apply {
            action = AudioAnalysisService.ACTION_STOP_ANALYSIS
        }
        context.startService(intent)
        _isAudioAnalysisActive.value = false
    }
    
    /**
     * Cambia el modo de visualización
     */
    fun setMode(mode: String) {
        _currentMode.value = mode
        
        if (_isAudioAnalysisActive.value) {
            val intent = Intent(context, AudioAnalysisService::class.java).apply {
                action = AudioAnalysisService.ACTION_SET_MODE
                putExtra(AudioAnalysisService.EXTRA_MODE, mode)
            }
            context.startService(intent)
        }
    }
    
    /**
     * Establece la sensibilidad del análisis de audio
     */
    fun setSensitivity(newSensitivity: Float) {
        _sensitivity.value = newSensitivity.coerceIn(0.1f, 3.0f)
        
        if (_isAudioAnalysisActive.value) {
            val intent = Intent(context, AudioAnalysisService::class.java).apply {
                action = AudioAnalysisService.ACTION_SET_SENSITIVITY
                putExtra(AudioAnalysisService.EXTRA_SENSITIVITY, _sensitivity.value)
            }
            context.startService(intent)
        }
    }
    
    /**
     * Establece el brillo de los LEDs
     */
    fun setBrightness(newBrightness: Int) {
        _brightness.value = newBrightness.coerceIn(0, 100)
        ledController.setBrightness(_brightness.value)
    }
    
    /**
     * Establece un color estático
     */
    fun setStaticColor(red: Int, green: Int, blue: Int) {
        _selectedColor.value = Triple(red, green, blue)
        
        // Si el análisis de audio está activo, detenerlo primero
        if (_isAudioAnalysisActive.value) {
            stopAudioAnalysis()
        }
        
        ledController.setColor(red, green, blue)
    }
    
    /**
     * Activa el efecto de respiración
     */
    fun startBreathingEffect() {
        if (_isAudioAnalysisActive.value) {
            stopAudioAnalysis()
        }
        
        val (red, green, blue) = _selectedColor.value
        ledController.breathingEffect(red, green, blue)
    }
    
    /**
     * Activa el efecto arcoíris
     */
    fun startRainbowEffect() {
        if (_isAudioAnalysisActive.value) {
            stopAudioAnalysis()
        }
        
        ledController.rainbowEffect()
    }
    
    /**
     * Apaga todos los LEDs
     */
    fun turnOffLeds() {
        if (_isAudioAnalysisActive.value) {
            stopAudioAnalysis()
        }
        
        ledController.turnOff()
    }
    
    /**
     * Colores predefinidos
     */
    fun getPresetColors(): List<Triple<Int, Int, Int>> {
        return listOf(
            Triple(255, 0, 0),     // Rojo
            Triple(0, 255, 0),     // Verde
            Triple(0, 0, 255),     // Azul
            Triple(255, 255, 0),   // Amarillo
            Triple(255, 0, 255),   // Magenta
            Triple(0, 255, 255),   // Cian
            Triple(255, 255, 255), // Blanco
            Triple(255, 165, 0),   // Naranja
            Triple(128, 0, 128),   // Púrpura
            Triple(255, 192, 203), // Rosa
            Triple(0, 128, 0),     // Verde oscuro
            Triple(75, 0, 130)     // Índigo
        )
    }
    
    /**
     * Modos disponibles
     */
    fun getAvailableModes(): List<Pair<String, String>> {
        return listOf(
            AudioAnalysisService.MODE_SPECTRUM to "Espectrograma",
            AudioAnalysisService.MODE_BASS_ONLY to "Solo Bajos",
            AudioAnalysisService.MODE_RAINBOW to "Arcoíris Musical",
            AudioAnalysisService.MODE_BREATHING to "Respiración Musical"
        )
    }
    
    /**
     * Actualiza el estado del controlador LED
     */
    private fun updateLedControllerStatus() {
        _ledControllerStatus.value = ledController.getStatusInfo()
    }
    
    /**
     * Verifica si el controlador LED está funcionando
     */
    fun isLedControllerWorking(): Boolean {
        return ledController.isWorking()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Detener análisis si está activo
        if (_isAudioAnalysisActive.value) {
            stopAudioAnalysis()
        }
    }
}
