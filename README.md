# Nubia Neo GT3 5G LED Controller

Una aplicación Android diseñada específicamente para controlar las luces RGB del Nubia Neo GT3 5G, incluyendo efectos reactivos al audio como un espectrograma.

## 🌟 Características

### Control de LEDs
- **Control básico de color**: Selector de colores RGB completo
- **Colores predefinidos**: Paleta de colores listos para usar
- **Control de brillo**: Ajuste de intensidad de 0% a 100%
- **Efectos especiales**: Respiración, arcoíris, y patrones personalizados

### Análisis de Audio en Tiempo Real
- **Modo Espectrograma**: Los LEDs cambian según las frecuencias de audio
  - Rojos para agudos
  - Verdes para medios  
  - Azules para bajos
- **Modo Solo Bajos**: Responde únicamente a las frecuencias bajas
- **Modo Arcoíris Musical**: Ciclo de colores con intensidad basada en audio
- **Modo Respiración Musical**: Intensidad variable según el volumen

### Configuración Avanzada
- **Control de sensibilidad**: Ajustar la reactividad al audio (0.1x - 3.0x)
- **Servicio en segundo plano**: Funciona incluso con la app minimizada
- **Múltiples métodos de acceso**: APIs nativas, comandos shell, y configuraciones del sistema

## 🔧 Instalación y Configuración

### Prerrequisitos
- Nubia Neo GT3 5G
- Android 13+ (API 33+)
- Permisos de micrófono para análisis de audio
- Acceso root (recomendado para máximo control)

### Pasos de Instalación

1. **Clonar y Compilar**
   ```bash
   git clone [este-repositorio]
   cd NubiaNeoGT3LightStrip
   ./gradlew assembleDebug
   ```

2. **Instalar APK**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Conceder Permisos**
   - Abrir la aplicación
   - Conceder permisos de micrófono cuando se solicite
   - Para máximo control, habilitar permisos de sistema:
     ```bash
     adb shell pm grant com.example.nubianeogt3lightstrip android.permission.WRITE_SETTINGS
     adb shell pm grant com.example.nubianeogt3lightstrip android.permission.WRITE_SECURE_SETTINGS
     ```

4. **Habilitar Control Avanzado (Opcional - Requiere Root)**
   ```bash
   adb shell
   su
   chmod 666 /sys/class/leds/*/brightness
   echo 1 > /sys/kernel/debug/led/enable
   ```

## 📱 Uso de la Aplicación

### Pantalla Principal

1. **Estado del Controlador**
   - Muestra si el controlador LED está funcionando
   - Indica qué método de acceso está siendo utilizado

2. **Control de Análisis de Audio**
   - Activar/desactivar análisis en tiempo real
   - Seleccionar modo de visualización
   - Ajustar sensibilidad

3. **Control Manual**
   - Selector de colores predefinidos
   - Control de brillo
   - Efectos especiales (respiración, arcoíris)

### Modos de Visualización de Audio

#### Espectrograma (Recomendado)
- **Agudos → Rojo**: Instrumentos de alta frecuencia, voces
- **Medios → Verde**: Guitarra, piano, la mayoría de instrumentos
- **Bajos → Azul**: Bombo, bajo, frecuencias profundas

#### Solo Bajos
- Perfecto para música electrónica, hip-hop, rock
- Los LEDs parpadean en azul con cada golpe del bajo

#### Arcoíris Musical
- Ciclo continuo de colores
- Intensidad varía con el volumen general

#### Respiración Musical
- Efecto suave de respiración
- Intensidad basada en el audio ambiente

### Configuración de Sensibilidad

- **0.1x - 0.5x**: Para ambientes muy ruidosos
- **1.0x**: Sensibilidad normal (predeterminada)
- **1.5x - 3.0x**: Para audio de bajo volumen o máxima reactividad

## 🔧 Solución de Problemas

### Las luces no responden

1. **Verificar permisos**
   ```bash
   adb shell dumpsys package com.example.nubianeogt3lightstrip | grep permission
   ```

2. **Comprobar interfaces disponibles**
   ```bash
   adb shell ls /sys/class/leds/
   adb shell find /sys -name "*led*" -o -name "*rgb*"
   ```

3. **Habilitar debugging**
   ```bash
   adb shell
   su
   echo 1 > /sys/kernel/debug/led/enable
   ```

### El análisis de audio no funciona

1. **Verificar permisos de micrófono**
   - Configuración > Aplicaciones > Nubia LED Controller > Permisos

2. **Comprobar servicio en segundo plano**
   ```bash
   adb shell dumpsys activity services | grep AudioAnalysisService
   ```

3. **Reiniciar servicio**
   - Desactivar y reactivar el análisis de audio en la app

### Rendimiento

- El análisis de audio consume ~5-10% de CPU
- Para mayor duración de batería, usar modos menos intensivos
- El servicio se detiene automáticamente al cerrar la app

## 🛠️ Desarrollo

### Estructura del Proyecto

```
app/src/main/java/com/example/nubianeogt3lightstrip/
├── controller/
│   └── NubiaLedController.kt       # Control principal de LEDs
├── audio/
│   └── AudioAnalyzer.kt            # Análisis de frecuencias
├── service/
│   └── AudioAnalysisService.kt     # Servicio en segundo plano
├── viewmodel/
│   └── MainViewModel.kt            # Lógica de la UI
├── utils/
│   └── ShellUtils.kt               # Utilidades para comandos shell
└── MainActivity.kt                 # Interfaz principal
```

### APIs Utilizadas

1. **Reflexión Java**: Acceso a APIs específicas de Nubia
2. **AudioRecord**: Captura de audio en tiempo real
3. **FFT**: Análisis de frecuencias de audio
4. **Shell Commands**: Control directo del hardware
5. **Settings Provider**: Configuraciones del sistema Android

### Agregar Soporte para Otros Dispositivos

Para adaptar la app a otros dispositivos con LEDs RGB:

1. **Identificar APIs del fabricante**
   ```kotlin
   // En NubiaLedController.kt, agregar:
   private const val XIAOMI_LED_CLASS = "com.xiaomi.hardware.LedManager"
   private const val SAMSUNG_LED_CLASS = "com.samsung.android.hardware.LedController"
   ```

2. **Descubrir interfaces del sistema**
   ```bash
   adb shell find /sys -name "*led*" -o -name "*rgb*" -o -name "*light*"
   ```

3. **Probar comandos específicos**
   ```bash
   adb shell ls /sys/class/leds/
   adb shell cat /sys/class/leds/*/trigger
   ```

## ⚡ Comandos ADB Útiles

### Debugging
```bash
# Ver logs de la aplicación
adb logcat | grep "NubiaLedController\|AudioAnalyzer"

# Información del dispositivo
adb shell getprop | grep -E "(brand|model|version)"

# Estado de permisos
adb shell dumpsys package com.example.nubianeogt3lightstrip | grep permission

# Procesos en ejecución
adb shell ps | grep nubianeogt3lightstrip
```

### Control Manual de LEDs
```bash
# Listar interfaces LED disponibles
adb shell ls /sys/class/leds/

# Establecer brillo manualmente
adb shell "echo 255 > /sys/class/leds/red/brightness"
adb shell "echo 255 > /sys/class/leds/green/brightness"
adb shell "echo 255 > /sys/class/leds/blue/brightness"

# Ver capacidades de LED
adb shell cat /sys/class/leds/*/max_brightness
adb shell cat /sys/class/leds/*/trigger
```

## 📋 Lista de Verificación Pre-Instalación

- [ ] Nubia Neo GT3 5G con Android 13+
- [ ] Depuración USB habilitada
- [ ] ADB instalado y funcionando
- [ ] Conexión estable al dispositivo
- [ ] Permisos de administrador (para control total)

## 🎯 Próximas Características

- [ ] Sincronización con Spotify/música externa
- [ ] Patrones personalizables por el usuario
- [ ] Control por gestos
- [ ] Integración con notificaciones del sistema
- [ ] Tema personalizable de la aplicación
- [ ] Export/import de configuraciones

## 📞 Soporte

Si encuentras problemas o tienes sugerencias:

1. Revisa la sección de solución de problemas
2. Ejecuta los comandos de debugging
3. Incluye logs de ADB en cualquier reporte de bug

---

**¡Disfruta personalizando las luces RGB de tu Nubia Neo GT3 5G! 🌈✨**
