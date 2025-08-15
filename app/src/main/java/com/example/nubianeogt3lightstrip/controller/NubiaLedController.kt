package com.example.nubianeogt3lightstrip.controller

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import android.util.Log
import com.example.nubianeogt3lightstrip.utils.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.reflect.Method

/**
 * Controlador para manejar las luces RGB del Nubia Neo GT3 5G
 * Utiliza reflexión para acceder a APIs específicas del fabricante
 */
class NubiaLedController(private val context: Context) {
    
    companion object {
        private const val TAG = "NubiaLedController"
        
        // Posibles nombres de métodos/clases para control de LEDs en Nubia
        private const val NUBIA_LED_CLASS = "nubia.hardware.led.NubiaLedManager"
        private const val SYSTEM_LED_CLASS = "android.hardware.led.LedManager"
        
        // Rutas específicas encontradas para el Nubia Neo GT3 5G (Z2465N)
        private const val NUBIA_LED_PATH = "/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led"
        private const val RGB_COLOR_FILE = "$NUBIA_LED_PATH/rgbcolor"
        private const val BRIGHTNESS_FILE = "$NUBIA_LED_PATH/brightness"
        private const val EFFECT_FILE = "$NUBIA_LED_PATH/effect"
        private const val EFFECT_MODE_FILE = "$NUBIA_LED_PATH/effect_mode"
        private const val BREATH_COLOR_FILE = "$NUBIA_LED_PATH/breathcolor"
        private const val DANCE_COLOR_FILE = "$NUBIA_LED_PATH/dancecolor"
        private const val LED_TEST_FILE = "$NUBIA_LED_PATH/led_test"
        
        // Configuraciones del sistema que podrían controlar LEDs
        private const val LED_BRIGHTNESS_SETTING = "led_brightness"
        private const val LED_COLOR_SETTING = "led_color"
        private const val LED_PATTERN_SETTING = "led_pattern"
        
        // Patrones de LED predefinidos
        const val PATTERN_STATIC = 0
        const val PATTERN_BREATHING = 1
        const val PATTERN_RAINBOW = 2
        const val PATTERN_MUSIC = 3
        const val PATTERN_NOTIFICATION = 4
    }
    
    private var ledManager: Any? = null
    private var setColorMethod: Method? = null
    private var setBrightnessMethod: Method? = null
    private var setPatternMethod: Method? = null
    private var isInitialized = false
    private var useShellCommands = false
    private var availableInterfaces = emptyList<String>()
    
    init {
        initializeLedController()
    }
    
    /**
     * Inicializa el controlador de LEDs intentando acceder a las APIs específicas
     */
    private fun initializeLedController() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Intentar cargar el manager específico de Nubia
                val ledClass = Class.forName(NUBIA_LED_CLASS)
                val getInstanceMethod = ledClass.getMethod("getInstance")
                ledManager = getInstanceMethod.invoke(null)
                
                // Obtener métodos de control
                setColorMethod = ledClass.getMethod("setColor", Int::class.java)
                setBrightnessMethod = ledClass.getMethod("setBrightness", Int::class.java)
                setPatternMethod = ledClass.getMethod("setPattern", Int::class.java)
                
                isInitialized = true
                Log.i(TAG, "Nubia LED Manager inicializado correctamente")
                
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo acceder al LED Manager de Nubia, intentando métodos alternativos: ${e.message}")
                tryAlternativeMethods()
            }
        }
    }
    
    /**
     * Intenta métodos alternativos para controlar LEDs
     */
    private fun tryAlternativeMethods() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Buscar interfaces disponibles del sistema
                availableInterfaces = ShellUtils.listLedInterfaces()
                
                // Intentar habilitar debugging de LEDs
                ShellUtils.enableLedDebugging()
                
                // Obtener información del hardware
                val hardwareInfo = ShellUtils.getLedHardwareInfo()
                Log.i(TAG, "Info del hardware: $hardwareInfo")
                
                useShellCommands = availableInterfaces.isNotEmpty()
                isInitialized = true
                
                Log.i(TAG, "Usando métodos alternativos para control de LEDs")
                Log.i(TAG, "Interfaces disponibles: $availableInterfaces")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error al inicializar métodos alternativos: ${e.message}")
                // Aún así marcar como inicializado para permitir intentos
                isInitialized = true
            }
        }
    }
    
    /**
     * Establece el color de las luces RGB
     */
    fun setColor(red: Int, green: Int, blue: Int): Boolean {
        if (!isInitialized) return false
        
        return try {
            val color = Color.rgb(red, green, blue)
            var success = false
            
            if (setColorMethod != null && ledManager != null) {
                // Usar API específica de Nubia
                setColorMethod?.invoke(ledManager, color)
                success = true
                Log.d(TAG, "Color establecido usando API de Nubia: R=$red G=$green B=$blue")
            }
            
            if (!success) {
                // Usar comandos shell específicos para Nubia Neo GT3
                CoroutineScope(Dispatchers.IO).launch {
                    // Intentar formato RGB directo para el aw_led
                    val rgbHex = String.format("%02X%02X%02X", red, green, blue)
                    success = ShellUtils.executeCommand("echo '$rgbHex' > $RGB_COLOR_FILE").isSuccess
                    
                    if (!success) {
                        // Intentar formato alternativo separado por comas
                        success = ShellUtils.executeCommand("echo '$red,$green,$blue' > $RGB_COLOR_FILE").isSuccess
                    }
                    
                    if (!success) {
                        // Intentar usando el archivo de test
                        success = ShellUtils.executeCommand("echo '1' > $LED_TEST_FILE && echo '$rgbHex' > $RGB_COLOR_FILE").isSuccess
                    }
                    
                    Log.d(TAG, "Color establecido usando aw_led directo: R=$red G=$green B=$blue, Success: $success")
                }
            }
            
            if (!success && useShellCommands) {
                // Usar comandos shell genéricos
                CoroutineScope(Dispatchers.IO).launch {
                    success = ShellUtils.setRgbColor(red, green, blue)
                    Log.d(TAG, "Color establecido usando shell commands genéricos: R=$red G=$green B=$blue, Success: $success")
                }
            }
            
            if (!success) {
                // Intentar con Settings como último recurso
                Settings.System.putInt(context.contentResolver, LED_COLOR_SETTING, color)
                Log.d(TAG, "Color establecido usando Settings: R=$red G=$green B=$blue")
                success = true
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error al establecer color: ${e.message}")
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
            
            if (!success) {
                // Intentar establecer brillo directamente en aw_led
                CoroutineScope(Dispatchers.IO).launch {
                    val brightnessValue = (clampedBrightness * 255 / 100)
                    val ledSuccess = ShellUtils.executeCommand("echo '$brightnessValue' > $BRIGHTNESS_FILE").isSuccess
                    Log.d(TAG, "Brillo establecido usando aw_led directo: $clampedBrightness, Success: $ledSuccess")
                }
            }
            
            if (!success && useShellCommands) {
                // Intentar establecer brillo a través de interfaces sysfs genéricas
                CoroutineScope(Dispatchers.IO).launch {
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
    fun setPattern(pattern: Int): Boolean {
        if (!isInitialized) return false
        
        return try {
            if (setPatternMethod != null && ledManager != null) {
                setPatternMethod?.invoke(ledManager, pattern)
                Log.d(TAG, "Patrón establecido usando API de Nubia: $pattern")
            } else {
                Settings.System.putInt(context.contentResolver, LED_PATTERN_SETTING, pattern)
                Log.d(TAG, "Patrón establecido usando Settings: $pattern")
            }
            true
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
                val success = ShellUtils.executeCommand("echo '$rgbHex' > $BREATH_COLOR_FILE").isSuccess
                
                if (success) {
                    // Activar el efecto de respiración
                    ShellUtils.executeCommand("echo 'breath' > $EFFECT_FILE")
                    Log.d(TAG, "Efecto de respiración activado con aw_led: $rgbHex")
                } else {
                    // Fallback al método anterior
                    setPattern(PATTERN_BREATHING)
                    setColor(red, green, blue)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en efecto de respiración: ${e.message}")
                // Fallback
                setPattern(PATTERN_BREATHING)
                setColor(red, green, blue)
            }
        }
    }
    
    /**
     * Efecto arcoíris (usando aw_led nativo)
     */
    fun rainbowEffect() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Intentar activar efecto rainbow/dance del aw_led
                val success = ShellUtils.executeCommand("echo 'rainbow' > $EFFECT_FILE").isSuccess ||
                             ShellUtils.executeCommand("echo 'dance' > $EFFECT_FILE").isSuccess
                
                if (success) {
                    Log.d(TAG, "Efecto arcoíris activado con aw_led")
                } else {
                    // Fallback al método anterior
                    setPattern(PATTERN_RAINBOW)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en efecto arcoíris: ${e.message}")
                setPattern(PATTERN_RAINBOW)
            }
        }
    }
    
    /**
     * Efecto dance/disco específico del aw_led
     */
    fun danceEffect(red: Int, green: Int, blue: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rgbHex = String.format("%02X%02X%02X", red, green, blue)
                val success = ShellUtils.executeCommand("echo '$rgbHex' > $DANCE_COLOR_FILE").isSuccess
                
                if (success) {
                    ShellUtils.executeCommand("echo 'dance' > $EFFECT_FILE")
                    Log.d(TAG, "Efecto dance activado con aw_led: $rgbHex")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en efecto dance: ${e.message}")
            }
        }
    }
    
    /**
     * Prueba directa del LED (para debugging)
     */
    fun testLed(): Boolean {
        return try {
            CoroutineScope(Dispatchers.IO).launch {
                // Activar el modo de prueba del LED
                val success = ShellUtils.executeCommand("echo '1' > $LED_TEST_FILE").isSuccess
                Log.d(TAG, "Modo de prueba LED: $success")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en prueba LED: ${e.message}")
            false
        }
    }
    
    /**
     * Establece el modo de música (reactivo al audio)
     */
    fun setMusicMode(enabled: Boolean): Boolean {
        return if (enabled) {
            setPattern(PATTERN_MUSIC)
        } else {
            setPattern(PATTERN_STATIC)
        }
    }
    
    /**
     * Verifica si el controlador está funcionando
     */
    fun isWorking(): Boolean = isInitialized
    
    /**
     * Obtiene información del estado actual
     */
    fun getStatusInfo(): String {
        return when {
            !isInitialized -> "No inicializado"
            ledManager != null -> "API Nubia disponible"
            useShellCommands -> "Usando comandos del sistema (${availableInterfaces.size} interfaces)"
            else -> "Usando métodos básicos"
        }
    }
    
    /**
     * Obtiene las interfaces LED disponibles
     */
    fun getAvailableInterfaces(): List<String> = availableInterfaces
}
