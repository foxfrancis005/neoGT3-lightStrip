package com.example.nubianeogt3lightstrip.controller

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.util.Log
import com.example.nubianeogt3lightstrip.utils.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Enumeración para patrones de LEDs
 */
enum class LedPattern {
    STATIC,
    BREATHING,
    STROBE,
    CHARGING,
    NOTIFICATION
}

/**
 * Controlador principal para las luces LED del Nubia Neo GT3 5G
 * Implementa múltiples métodos basados en análisis del logcat de cn.nubia.colorfullight
 */
class NubiaLedController(private val context: Context) {
    
    // Variables para API específica de Nubia
    private var ledManager: Any? = null
    private var setColorMethod: Method? = null
    private var setBrightnessMethod: Method? = null
    private var setPatternMethod: Method? = null
    
    // Estados del controlador
    private var isInitialized = false
    private var useShellCommands = true
    private var availableInterfaces: List<String> = emptyList()
    
    companion object {
        private const val TAG = "NubiaLedController"
        
        // Clase específica de Nubia (si existe)
        private const val NUBIA_LED_CLASS = "nubia.hardware.ledlight.LedLightManager"
        
        // Archivos específicos del aw_led para Nubia Neo GT3 (identificados en logcat)
        private const val RGB_COLOR_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/rgb_color"
        private const val BRIGHTNESS_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/brightness"
        private const val BREATH_COLOR_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/breathcolor"
        private const val DANCE_COLOR_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/dancecolor"
        private const val EFFECT_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/effect"
        private const val LED_TEST_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/led_test"
        private const val LED_PATTERN_FILE = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led/pattern"
        
        // Settings de Android para fallback
        private const val LED_COLOR_SETTING = "led_color"
        private const val LED_BRIGHTNESS_SETTING = "led_brightness"
        private const val LED_PATTERN_SETTING = "led_pattern"
        
        // Constantes identificadas en logcat de cn.nubia.colorfullight
        private const val MSG_WHAT_CHARGING = 4      // msg.what: 4 para carga
        private const val MSG_WHAT_NOTIFICATION = 5  // msg.what: 5 para notificaciones
        private const val SPRD_LIGHTS_SERVICE = "com.sprd.lights.service"
    }
    
    /**
     * Inicializa el controlador LED
     */
    suspend fun initialize(): Boolean {
        return try {
            Log.d(TAG, "🚀 Inicializando NubiaLedController...")
            
            // 1. Intentar cargar API específica de Nubia
            loadNubiaLedApi()
            
            // 2. Obtener interfaces LED disponibles
            availableInterfaces = ShellUtils.listLedInterfaces()
            Log.d(TAG, "📋 Interfaces LED encontradas: ${availableInterfaces.size}")
            
            // 3. Habilitar debugging de LEDs
            val debugSuccess = ShellUtils.enableLedDebugging()
            Log.d(TAG, "🔧 Debug LED habilitado: $debugSuccess")
            
            // 4. Obtener información del hardware
            val hardwareInfo = ShellUtils.getLedHardwareInfo()
            Log.d(TAG, "🔍 Info del hardware LED: $hardwareInfo")
            
            isInitialized = true
            Log.d(TAG, "✅ NubiaLedController inicializado correctamente")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando NubiaLedController", e)
            false
        }
    }
    
    /**
     * Intenta cargar la API específica de Nubia mediante reflexión
     */
    private fun loadNubiaLedApi() {
        try {
            val nubiaClass = Class.forName(NUBIA_LED_CLASS)
            ledManager = nubiaClass.getDeclaredConstructor(Context::class.java).newInstance(context)
            
            setColorMethod = nubiaClass.getDeclaredMethod("setColor", Int::class.java)
            setBrightnessMethod = nubiaClass.getDeclaredMethod("setBrightness", Int::class.java)
            setPatternMethod = nubiaClass.getDeclaredMethod("setPattern", Int::class.java)
            
            Log.d(TAG, "✅ API específica de Nubia cargada correctamente")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ API específica de Nubia no disponible, usando métodos alternativos")
        }
    }
    
    /**
     * Establece el color de las luces RGB
     * Implementa múltiples métodos basados en el análisis del logcat
     */
    fun setColor(red: Int, green: Int, blue: Int): Boolean {
        if (!isInitialized) return false
        
        return try {
            val color = Color.rgb(red, green, blue)
            var success = false
            
            // 1. PRIORIDAD: Intentar comunicarse con ColorfulLightService (identificado en logcat)
            val hardwareSuccess = setColorViaHardwareService(red, green, blue)
            if (hardwareSuccess) {
                success = true
                Log.d(TAG, "✅ Color establecido usando ColorfulLightService: R=$red G=$green B=$blue")
            }
            
            // 2. Usar API específica de Nubia si está disponible
            if (!success && setColorMethod != null && ledManager != null) {
                setColorMethod?.invoke(ledManager, color)
                success = true
                Log.d(TAG, "✅ Color establecido usando API de Nubia: R=$red G=$green B=$blue")
            }
            
            // 3. Usar comandos shell específicos para Nubia Neo GT3 aw_led
            if (!success) {
                CoroutineScope(Dispatchers.IO).launch {
                    // Intentar formato RGB directo para el aw_led
                    val rgbHex = String.format("%02X%02X%02X", red, green, blue)
                    var localSuccess = ShellUtils.executeCommand("echo '$rgbHex' > $RGB_COLOR_FILE").isSuccess
                    
                    if (!localSuccess) {
                        // Intentar formato alternativo separado por comas
                        localSuccess = ShellUtils.executeCommand("echo '$red,$green,$blue' > $RGB_COLOR_FILE").isSuccess
                    }
                    
                    if (!localSuccess) {
                        // Intentar usando el archivo de test
                        localSuccess = ShellUtils.executeCommand("echo '1' > $LED_TEST_FILE && echo '$rgbHex' > $RGB_COLOR_FILE").isSuccess
                    }
                    
                    if (localSuccess) {
                        Log.d(TAG, "✅ Color establecido usando aw_led directo: R=$red G=$green B=$blue")
                    } else {
                        Log.w(TAG, "❌ No se pudo establecer color usando aw_led directo")
                    }
                }
            }
            
            // 4. Usar comandos shell genéricos
            if (!success && useShellCommands) {
                CoroutineScope(Dispatchers.IO).launch {
                    val shellSuccess = ShellUtils.setRgbColor(red, green, blue)
                    if (shellSuccess) {
                        Log.d(TAG, "✅ Color establecido usando shell commands genéricos: R=$red G=$green B=$blue")
                    }
                }
            }
            
            // 5. Intentar con Settings como último recurso
            if (!success) {
                Settings.System.putInt(context.contentResolver, LED_COLOR_SETTING, color)
                Log.d(TAG, "⚠️ Color establecido usando Settings (fallback): R=$red G=$green B=$blue")
                success = true
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer color: ${e.message}")
            false
        }
    }
    
    /**
     * Método principal para controlar el hardware usando ColorfulLightService
     * Basado en análisis del logcat de cn.nubia.colorfullight
     */
    private fun setColorViaHardwareService(red: Int, green: Int, blue: Int): Boolean {
        return try {
            // 1. Intentar formato específico SPRD (identificado en logcat)
            val colorRGB = formatColorForSprdService(red, green, blue)
            var success = sendColorToSprdService(colorRGB)
            
            if (!success) {
                // 2. Intentar usando ColorfulLightService messages
                success = sendMessageToColorfulLightService(MSG_WHAT_NOTIFICATION, red, green, blue)
            }
            
            if (!success) {
                // 3. Intentar modo strobe identificado (effect mode 117)
                success = sendStrobeEffect(red, green, blue)
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en setColorViaHardwareService", e)
            false
        }
    }
    
    /**
     * Formatea el color en el formato específico SPRD identificado en logcat
     * Ejemplo del logcat: colorRGB=05007008
     */
    private fun formatColorForSprdService(red: Int, green: Int, blue: Int): String {
        // Convertir a formato hexadecimal de 2 dígitos con padding alpha
        val alpha = 0x05 // Valor observado en el logcat
        return String.format("%02x%02x%02x%02x", alpha, red, green, blue)
    }
    
    /**
     * Envía color al servicio SPRD usando el formato identificado
     */
    private fun sendColorToSprdService(colorRGB: String): Boolean {
        return try {
            val intent = Intent(SPRD_LIGHTS_SERVICE)
            intent.putExtra("colorRGB", colorRGB)
            intent.putExtra("action", "setColor")
            context.sendBroadcast(intent)
            Log.d(TAG, "🚀 Color enviado a SPRD service: $colorRGB")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando color a SPRD service", e)
            false
        }
    }
    
    /**
     * Envía mensaje al ColorfulLightService usando los códigos identificados
     */
    private fun sendMessageToColorfulLightService(messageWhat: Int, red: Int, green: Int, blue: Int): Boolean {
        return try {
            val intent = Intent("cn.nubia.colorfullight.action.COLOR_CONTROL")
            intent.putExtra("msg_what", messageWhat)
            intent.putExtra("red", red)
            intent.putExtra("green", green)
            intent.putExtra("blue", blue)
            context.sendBroadcast(intent)
            Log.d(TAG, "📨 Mensaje enviado a ColorfulLightService: what=$messageWhat RGB=($red,$green,$blue)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje a ColorfulLightService", e)
            false
        }
    }
    
    /**
     * Activa efecto strobe con modo 117 identificado en logcat
     */
    private fun sendStrobeEffect(red: Int, green: Int, blue: Int): Boolean {
        return try {
            val intent = Intent("cn.nubia.colorfullight.action.STROBE_EFFECT")
            intent.putExtra("effect_mode", 117)
            intent.putExtra("red", red)
            intent.putExtra("green", green)
            intent.putExtra("blue", blue)
            context.sendBroadcast(intent)
            Log.d(TAG, "⚡ Efecto strobe enviado: modo=117 RGB=($red,$green,$blue)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando efecto strobe", e)
            false
        }
    }
    
    /**
     * Establece el brillo de las luces (0-100)
     */
    fun setBrightness(brightness: Int): Boolean {
        if (!isInitialized) return false
        
        val clampedBrightness = brightness.coerceIn(0, 100)
        
        return try {
            var success = false
            
            if (setBrightnessMethod != null && ledManager != null) {
                setBrightnessMethod?.invoke(ledManager, clampedBrightness)
                success = true
                Log.d(TAG, "Brillo establecido usando API de Nubia: $clampedBrightness")
            }
            
            if (!success && useShellCommands) {
                CoroutineScope(Dispatchers.IO).launch {
                    // Intentar establecer brillo usando el archivo brightness específico del aw_led
                    val brightnessValue = (clampedBrightness * 255 / 100)
                    val shellSuccess = ShellUtils.executeCommand("echo '$brightnessValue' > $BRIGHTNESS_FILE").isSuccess
                    Log.d(TAG, "Brillo establecido usando aw_led: $clampedBrightness")
                }
            }
            
            if (!success && useShellCommands) {
                CoroutineScope(Dispatchers.IO).launch {
                    // Intentar establecer brillo a través de interfaces sysfs genéricas
                    for (ledInterface in availableInterfaces) {
                        val ledName = ledInterface.substringAfterLast("/")
                        val ledSuccess = ShellUtils.setLedViaSysfs(ledName, (clampedBrightness * 255 / 100))
                        if (ledSuccess) {
                            Log.d(TAG, "Brillo establecido para $ledName: $clampedBrightness")
                        }
                    }
                }
            }
            
            if (!success) {
                Settings.System.putInt(context.contentResolver, LED_BRIGHTNESS_SETTING, clampedBrightness)
                Log.d(TAG, "Brillo establecido usando Settings: $clampedBrightness")
                success = true
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer brillo: ${e.message}")
            false
        }
    }
    
    /**
     * Establece un patrón de animación
     */
    fun setPattern(pattern: LedPattern): Boolean {
        if (!isInitialized) return false
        
        return try {
            // Usar servicio específico para patrones basados en logcat
            when (pattern) {
                LedPattern.BREATHING -> activateBreathingPattern()
                LedPattern.STROBE -> activateStrobePattern()
                LedPattern.CHARGING -> activateChargingEffect()
                LedPattern.NOTIFICATION -> activateNotificationEffect()
                else -> setGenericPattern(pattern)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer patrón: ${e.message}")
            false
        }
    }
    
    /**
     * Activa efecto de carga usando msg.what = 4 identificado en logcat
     */
    private fun activateChargingEffect(): Boolean {
        return sendMessageToColorfulLightService(MSG_WHAT_CHARGING, 0, 255, 0) // Verde para carga
    }
    
    /**
     * Activa efecto de notificación usando msg.what = 5 identificado en logcat
     */
    private fun activateNotificationEffect(): Boolean {
        return sendMessageToColorfulLightService(MSG_WHAT_NOTIFICATION, 0, 100, 255) // Azul para notificaciones
    }
    
    /**
     * Activa patrón de respiración usando aw_led
     */
    private fun activateBreathingPattern(): Boolean {
        return try {
            CoroutineScope(Dispatchers.IO).launch {
                // Usar comando shell específico para modo breathing
                val success = ShellUtils.executeCommand("echo 'breathing' > $LED_PATTERN_FILE").isSuccess
                if (success) {
                    Log.d(TAG, "🫁 Patrón breathing activado")
                } else {
                    Log.w(TAG, "❌ No se pudo activar patrón breathing")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error activando patrón breathing", e)
            false
        }
    }
    
    /**
     * Activa patrón strobe usando el modo 117 identificado
     */
    private fun activateStrobePattern(): Boolean {
        return sendStrobeEffect(255, 255, 255) // Blanco por defecto
    }
    
    /**
     * Maneja patrones genéricos
     */
    private fun setGenericPattern(pattern: LedPattern): Boolean {
        return try {
            if (setPatternMethod != null && ledManager != null) {
                val patternValue = when (pattern) {
                    LedPattern.BREATHING -> 1
                    LedPattern.STROBE -> 2
                    LedPattern.CHARGING -> 3
                    LedPattern.NOTIFICATION -> 4
                    else -> 0
                }
                setPatternMethod?.invoke(ledManager, patternValue)
                Log.d(TAG, "Patrón genérico establecido usando API de Nubia: $pattern")
                true
            } else {
                // Fallback usando Settings
                val patternValue = when (pattern) {
                    LedPattern.BREATHING -> 1
                    LedPattern.STROBE -> 2
                    LedPattern.CHARGING -> 3
                    LedPattern.NOTIFICATION -> 4
                    else -> 0
                }
                Settings.System.putInt(context.contentResolver, LED_PATTERN_SETTING, patternValue)
                Log.d(TAG, "Patrón genérico establecido usando Settings: $pattern")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error estableciendo patrón genérico", e)
            false
        }
    }
    
    /**
     * Establece un patrón de animación - Sobrecarga para Int (compatibilidad)
     */
    fun setPattern(pattern: Int): Boolean {
        return try {
            val ledPattern = when (pattern) {
                1 -> LedPattern.BREATHING
                2 -> LedPattern.STROBE
                3 -> LedPattern.CHARGING
                4 -> LedPattern.NOTIFICATION
                else -> LedPattern.STATIC
            }
            setPattern(ledPattern)
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer patrón: ${e.message}")
            false
        }
    }
    
    /**
     * Apaga todas las luces
     */
    fun turnOff(): Boolean {
        return setColor(0, 0, 0)
    }
    
    /**
     * Efecto de respiración con un color específico (usando aw_led nativo)
     */
    fun breathingEffect(red: Int, green: Int, blue: Int, duration: Long = 2000) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Intentar usar el archivo breathcolor específico del aw_led
                val rgbHex = String.format("%02X%02X%02X", red, green, blue)
                ShellUtils.executeCommand("echo '$rgbHex' > $BREATH_COLOR_FILE")
                
                // Activar el efecto breathing
                ShellUtils.executeCommand("echo 'breathing' > $EFFECT_FILE")
                Log.d(TAG, "🫁 Efecto breathing activado con color: R=$red G=$green B=$blue")
            } catch (e: Exception) {
                Log.e(TAG, "Error en efecto breathing", e)
            }
        }
    }
    
    /**
     * Efecto de baile/música (usando aw_led nativo)
     */
    fun danceEffect(red: Int, green: Int, blue: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Usar el archivo dancecolor específico del aw_led
                val rgbHex = String.format("%02X%02X%02X", red, green, blue)
                ShellUtils.executeCommand("echo '$rgbHex' > $DANCE_COLOR_FILE")
                
                // Activar efecto de baile
                ShellUtils.executeCommand("echo 'dance' > $EFFECT_FILE")
                Log.d(TAG, "💃 Efecto dance activado con color: R=$red G=$green B=$blue")
            } catch (e: Exception) {
                Log.e(TAG, "Error en efecto dance", e)
            }
        }
    }
    
    /**
     * Función de test para verificar funcionalidad LED
     */
    fun testLed(): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Activar modo test del aw_led
                ShellUtils.executeCommand("echo '1' > $LED_TEST_FILE")
                Log.d(TAG, "🧪 Modo test LED activado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en test LED", e)
            }
        }
        return true
    }
    
    /**
     * Libera recursos y limpia el controlador
     */
    fun cleanup() {
        try {
            turnOff()
            isInitialized = false
            Log.d(TAG, "🧹 NubiaLedController limpieza completada")
        } catch (e: Exception) {
            Log.e(TAG, "Error durante limpieza", e)
        }
    }
    
    /**
     * Efecto arcoíris automático
     */
    fun rainbowEffect(duration: Long = 5000) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val colors = listOf(
                    Triple(255, 0, 0),   // Rojo
                    Triple(255, 127, 0), // Naranja
                    Triple(255, 255, 0), // Amarillo
                    Triple(0, 255, 0),   // Verde
                    Triple(0, 0, 255),   // Azul
                    Triple(75, 0, 130),  // Índigo
                    Triple(148, 0, 211)  // Violeta
                )
                
                for (color in colors) {
                    setColor(color.first, color.second, color.third)
                    kotlinx.coroutines.delay(duration / colors.size)
                }
                
                Log.d(TAG, "🌈 Efecto arcoíris completado")
            } catch (e: Exception) {
                Log.e(TAG, "Error en efecto arcoíris", e)
            }
        }
    }
    
    /**
     * Obtiene información del estado del controlador
     */
    fun getStatusInfo(): String {
        return buildString {
            append("Estado: ${if (isInitialized) "✅ Inicializado" else "❌ No inicializado"}\n")
            append("API Nubia: ${if (ledManager != null) "✅ Disponible" else "❌ No disponible"}\n")
            append("Shell Commands: ${if (useShellCommands) "✅ Habilitado" else "❌ Deshabilitado"}\n")
            append("Interfaces LED: ${availableInterfaces.size} encontradas\n")
            if (availableInterfaces.isNotEmpty()) {
                append("- ${availableInterfaces.joinToString("\n- ")}")
            }
        }
    }
    
    /**
     * Verifica si el controlador está funcionando
     */
    fun isWorking(): Boolean {
        return isInitialized
    }
}
