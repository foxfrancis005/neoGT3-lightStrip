package com.example.nubianeogt3lightstrip.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * Analizador de audio en tiempo real para efectos de LED reactivos al sonido
 */
class AudioAnalyzer {
    
    companion object {
        private const val TAG = "AudioAnalyzer"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
        
        // Rangos de frecuencia para análisis de espectro
        private const val FREQ_BASS_MAX = 250f
        private const val FREQ_MID_MIN = 250f
        private const val FREQ_MID_MAX = 4000f
        private const val FREQ_TREBLE_MIN = 4000f
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var analysisJob: Job? = null
    
    // Estados del análisis de audio
    private val _audioLevels = MutableStateFlow(AudioLevels())
    val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()
    
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    /**
     * Data class para almacenar los niveles de audio analizados
     */
    data class AudioLevels(
        val bass: Float = 0f,          // Nivel de bajos (0-1)
        val mid: Float = 0f,           // Nivel de medios (0-1)
        val treble: Float = 0f,        // Nivel de agudos (0-1)
        val overall: Float = 0f,       // Nivel general (0-1)
        val frequency: FloatArray = FloatArray(0), // Espectro de frecuencia
        val timestamp: Long = 0L
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as AudioLevels
            
            if (bass != other.bass) return false
            if (mid != other.mid) return false
            if (treble != other.treble) return false
            if (overall != other.overall) return false
            if (!frequency.contentEquals(other.frequency)) return false
            if (timestamp != other.timestamp) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = bass.hashCode()
            result = 31 * result + mid.hashCode()
            result = 31 * result + treble.hashCode()
            result = 31 * result + overall.hashCode()
            result = 31 * result + frequency.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
    
    /**
     * Inicia el análisis de audio
     */
    fun startAnalysis(): Boolean {
        if (isRecording) {
            Log.w(TAG, "El análisis ya está en curso")
            return true
        }
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Error al obtener el tamaño del buffer")
                return false
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Error al inicializar AudioRecord")
                return false
            }
            
            audioRecord?.startRecording()
            isRecording = true
            _isAnalyzing.value = true
            
            // Iniciar el análisis en un hilo separado
            analysisJob = CoroutineScope(Dispatchers.Default).launch {
                analyzeAudioLoop(bufferSize)
            }
            
            Log.i(TAG, "Análisis de audio iniciado correctamente")
            return true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Permisos de audio no concedidos: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error al iniciar análisis de audio: ${e.message}")
            return false
        }
    }
    
    /**
     * Detiene el análisis de audio
     */
    fun stopAnalysis() {
        isRecording = false
        _isAnalyzing.value = false
        
        analysisJob?.cancel()
        analysisJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.i(TAG, "Análisis de audio detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error al detener análisis de audio: ${e.message}")
        }
    }
    
    /**
     * Bucle principal de análisis de audio
     */
    private suspend fun analyzeAudioLoop(bufferSize: Int) {
        val audioBuffer = ShortArray(bufferSize)
        
        while (isRecording && !currentCoroutineContext().isActive.not()) {
            try {
                val readResult = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                
                if (readResult > 0) {
                    val levels = processAudioData(audioBuffer, readResult)
                    _audioLevels.value = levels
                }
                
                // Pequeña pausa para no saturar el CPU
                delay(16) // ~60 FPS
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en el bucle de análisis: ${e.message}")
                break
            }
        }
    }
    
    /**
     * Procesa los datos de audio y extrae niveles por frecuencia
     */
    private fun processAudioData(audioBuffer: ShortArray, length: Int): AudioLevels {
        // Convertir a valores flotantes normalizados
        val floatBuffer = FloatArray(length) { i ->
            audioBuffer[i].toFloat() / Short.MAX_VALUE
        }
        
        // Calcular nivel general (RMS)
        val overall = sqrt(floatBuffer.map { it * it }.average()).toFloat()
        
        // Realizar FFT simple para análisis de frecuencia
        val fftResult = performSimpleFFT(floatBuffer)
        
        // Extraer niveles por banda de frecuencia
        val bass = extractFrequencyBand(fftResult, 0f, FREQ_BASS_MAX)
        val mid = extractFrequencyBand(fftResult, FREQ_MID_MIN, FREQ_MID_MAX)
        val treble = extractFrequencyBand(fftResult, FREQ_TREBLE_MIN, SAMPLE_RATE / 2f)
        
        return AudioLevels(
            bass = bass.coerceIn(0f, 1f),
            mid = mid.coerceIn(0f, 1f),
            treble = treble.coerceIn(0f, 1f),
            overall = overall.coerceIn(0f, 1f),
            frequency = fftResult,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Realiza una FFT simple para análisis de frecuencia
     */
    private fun performSimpleFFT(audioData: FloatArray): FloatArray {
        val fftSize = nearestPowerOfTwo(audioData.size)
        val paddedData = audioData.copyOf(fftSize)
        
        // FFT simplificada - en un caso real usarías una librería como JTransforms
        val result = FloatArray(fftSize / 2)
        
        for (k in result.indices) {
            var realPart = 0.0
            var imagPart = 0.0
            
            for (n in paddedData.indices) {
                val angle = -2.0 * PI * k * n / fftSize
                realPart += paddedData[n] * cos(angle)
                imagPart += paddedData[n] * sin(angle)
            }
            
            result[k] = sqrt(realPart * realPart + imagPart * imagPart).toFloat()
        }
        
        return result
    }
    
    /**
     * Extrae el nivel de una banda de frecuencia específica
     */
    private fun extractFrequencyBand(fftData: FloatArray, minFreq: Float, maxFreq: Float): Float {
        val binSize = SAMPLE_RATE.toFloat() / (fftData.size * 2)
        val startBin = (minFreq / binSize).toInt().coerceIn(0, fftData.size - 1)
        val endBin = (maxFreq / binSize).toInt().coerceIn(0, fftData.size - 1)
        
        if (startBin >= endBin) return 0f
        
        var sum = 0f
        for (i in startBin..endBin) {
            sum += fftData[i]
        }
        
        return sum / (endBin - startBin + 1)
    }
    
    /**
     * Encuentra la potencia de 2 más cercana
     */
    private fun nearestPowerOfTwo(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }
    
    /**
     * Limpieza de recursos
     */
    fun cleanup() {
        stopAnalysis()
    }
}
