#!/bin/bash

# Script de prueba para las luces RGB del Nubia Neo GT3 5G
# Ejecutar con: adb shell "sh /sdcard/test_leds.sh"

LED_PATH="/sys/devices/platform/soc/soc:ap-ahb/222d0000.i2c/i2c-9/9-0030/leds/aw_led"

echo "=== Prueba de LEDs Nubia Neo GT3 5G ==="
echo "Ruta del LED: $LED_PATH"
echo ""

# Verificar si el directorio existe
if [ -d "$LED_PATH" ]; then
    echo "✓ Directorio LED encontrado"
    
    # Listar archivos disponibles
    echo "Archivos disponibles:"
    ls -la "$LED_PATH/" | grep -v "^d"
    echo ""
    
    # Intentar leer algunos archivos
    echo "=== Información del LED ==="
    
    if [ -r "$LED_PATH/max_brightness" ]; then
        echo "Brillo máximo: $(cat $LED_PATH/max_brightness)"
    else
        echo "max_brightness: No accesible"
    fi
    
    if [ -r "$LED_PATH/brightness" ]; then
        echo "Brillo actual: $(cat $LED_PATH/brightness)"
    else
        echo "brightness: No accesible"
    fi
    
    if [ -r "$LED_PATH/trigger" ]; then
        echo "Triggers disponibles: $(cat $LED_PATH/trigger)"
    else
        echo "trigger: No accesible"
    fi
    
    echo ""
    echo "=== Intentando pruebas de escritura ==="
    
    # Intentar encender con rojo
    echo "Probando color rojo (FF0000)..."
    if echo "FF0000" > "$LED_PATH/rgbcolor" 2>/dev/null; then
        echo "✓ rgbcolor - Comando exitoso"
        sleep 2
    else
        echo "✗ rgbcolor - Acceso denegado"
    fi
    
    # Intentar formato alternativo
    echo "Probando formato alternativo (255,0,0)..."
    if echo "255,0,0" > "$LED_PATH/rgbcolor" 2>/dev/null; then
        echo "✓ rgbcolor formato comas - Comando exitoso"
        sleep 2
    else
        echo "✗ rgbcolor formato comas - Acceso denegado"
    fi
    
    # Probar efecto de respiración
    echo "Probando efecto breath..."
    if echo "breath" > "$LED_PATH/effect" 2>/dev/null; then
        echo "✓ effect breath - Comando exitoso"
        sleep 2
    else
        echo "✗ effect breath - Acceso denegado"
    fi
    
    # Apagar
    echo "Apagando LED..."
    if echo "000000" > "$LED_PATH/rgbcolor" 2>/dev/null; then
        echo "✓ LED apagado"
    else
        echo "✗ No se pudo apagar"
    fi
    
else
    echo "✗ Directorio LED no encontrado"
    echo "Buscando rutas alternativas..."
    find /sys -name "*led*" -type d 2>/dev/null | head -10
fi

echo ""
echo "=== Información del dispositivo ==="
echo "Modelo: $(getprop ro.product.model)"
echo "Marca: $(getprop ro.product.brand)"
echo "Android: $(getprop ro.build.version.release)"

echo ""
echo "=== Prueba completada ==="
