package com.example.nubianeogt3lightstrip.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Utilidades para ejecutar comandos shell que puedan controlar hardware específico
 */
object ShellUtils {
    
    private const val TAG = "ShellUtils"
    
    /**
     * Ejecuta un comando shell y devuelve el resultado
     */
    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Ejecutando comando: $command")
            
            val process = ProcessBuilder()
                .command("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            val exitCode = process.waitFor()
            val result = output.toString().trim()
            
            Log.d(TAG, "Comando completado con código: $exitCode")
            Log.d(TAG, "Salida: $result")
            
            if (exitCode == 0) {
                Result.success(result)
            } else {
                Result.failure(Exception("Comando falló con código $exitCode: $result"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ejecutando comando: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Intenta encontrar rutas de archivos del sistema relacionados con LEDs
     */
    suspend fun findLedControlFiles(): List<String> {
        val possiblePaths = listOf(
            "/sys/class/leds/",
            "/sys/devices/platform/soc/led/",
            "/sys/kernel/debug/led/",
            "/proc/led/",
            "/vendor/etc/led/",
            "/system/etc/led/"
        )
        
        val foundPaths = mutableListOf<String>()
        
        for (path in possiblePaths) {
            executeCommand("find $path -type f 2>/dev/null || true").getOrNull()?.let { result ->
                if (result.isNotEmpty()) {
                    foundPaths.addAll(result.lines().filter { it.isNotEmpty() })
                }
            }
        }
        
        Log.i(TAG, "Archivos de control LED encontrados: $foundPaths")
        return foundPaths
    }
    
    /**
     * Intenta listar todas las interfaces de LED disponibles
     */
    suspend fun listLedInterfaces(): List<String> {
        val interfaces = mutableListOf<String>()
        
        // Buscar en /sys/class/leds/
        executeCommand("ls /sys/class/leds/ 2>/dev/null || true").getOrNull()?.let { result ->
            interfaces.addAll(result.lines().filter { it.isNotEmpty() })
        }
        
        // Buscar interfaces específicas de Nubia
        val nubiaCommands = listOf(
            "find /sys -name '*nubia*led*' 2>/dev/null || true",
            "find /sys -name '*rgb*' 2>/dev/null || true",
            "find /proc -name '*led*' 2>/dev/null || true"
        )
        
        for (command in nubiaCommands) {
            executeCommand(command).getOrNull()?.let { result ->
                interfaces.addAll(result.lines().filter { it.isNotEmpty() })
            }
        }
        
        Log.i(TAG, "Interfaces LED encontradas: $interfaces")
        return interfaces.distinct()
    }
    
    /**
     * Intenta controlar LED a través de sysfs
     */
    suspend fun setLedViaSysfs(ledName: String, brightness: Int): Boolean {
        val brightnessFile = "/sys/class/leds/$ledName/brightness"
        val command = "echo $brightness > $brightnessFile"
        
        return executeCommand(command).isSuccess
    }
    
    /**
     * Intenta establecer un color RGB a través de interfaces del sistema
     */
    suspend fun setRgbColor(red: Int, green: Int, blue: Int): Boolean {
        val commands = listOf(
            // Comandos para diferentes fabricantes de Android
            "echo '${red},${green},${blue}' > /sys/class/leds/rgb/rgb_color 2>/dev/null",
            "echo '$red' > /sys/class/leds/red/brightness 2>/dev/null",
            "echo '$green' > /sys/class/leds/green/brightness 2>/dev/null",
            "echo '$blue' > /sys/class/leds/blue/brightness 2>/dev/null",
            
            // Comandos específicos para Nubia (hipotéticos)
            "echo '${red}' > /sys/class/leds/nubia_led_r/brightness 2>/dev/null",
            "echo '${green}' > /sys/class/leds/nubia_led_g/brightness 2>/dev/null",
            "echo '${blue}' > /sys/class/leds/nubia_led_b/brightness 2>/dev/null",
            
            // Intentar con setprop (propiedades del sistema)
            "setprop sys.led.color '${red},${green},${blue}' 2>/dev/null",
            "setprop vendor.led.rgb '${red},${green},${blue}' 2>/dev/null"
        )
        
        var success = false
        for (command in commands) {
            if (executeCommand(command).isSuccess) {
                success = true
                Log.d(TAG, "Comando exitoso: $command")
            }
        }
        
        return success
    }
    
    /**
     * Obtiene información del hardware LED disponible
     */
    suspend fun getLedHardwareInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()
        
        val commands = mapOf(
            "led_interfaces" to "ls /sys/class/leds/ 2>/dev/null || echo 'No disponible'",
            "device_model" to "getprop ro.product.model 2>/dev/null || echo 'Desconocido'",
            "device_brand" to "getprop ro.product.brand 2>/dev/null || echo 'Desconocido'",
            "android_version" to "getprop ro.build.version.release 2>/dev/null || echo 'Desconocido'",
            "kernel_version" to "uname -r 2>/dev/null || echo 'Desconocido'"
        )
        
        for ((key, command) in commands) {
            executeCommand(command).getOrNull()?.let { result ->
                info[key] = result.take(100) // Limitar longitud
            }
        }
        
        return info
    }
    
    /**
     * Intenta habilitar el modo developer/debugging para LEDs
     */
    suspend fun enableLedDebugging(): Boolean {
        val commands = listOf(
            "echo '1' > /sys/kernel/debug/led/enable 2>/dev/null",
            "setprop sys.led.debug 1 2>/dev/null",
            "chmod 666 /sys/class/leds/*/brightness 2>/dev/null"
        )
        
        var anySuccess = false
        for (command in commands) {
            if (executeCommand(command).isSuccess) {
                anySuccess = true
            }
        }
        
        return anySuccess
    }
}
