package com.example.nubianeogt3lightstrip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nubianeogt3lightstrip.service.AudioAnalysisService
import com.example.nubianeogt3lightstrip.ui.theme.NubiaNeoGT3LightStripTheme
import com.example.nubianeogt3lightstrip.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NubiaNeoGT3LightStripTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    
    // Estados del ViewModel
    val isAudioAnalysisActive by viewModel.isAudioAnalysisActive.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val sensitivity by viewModel.sensitivity.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val selectedColor by viewModel.selectedColor.collectAsState()
    val ledControllerStatus by viewModel.ledControllerStatus.collectAsState()
    
    // Estados locales
    var showColorPicker by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Launcher para permisos
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Nubia LED Controller",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Estado del controlador LED
            StatusCard(
                status = ledControllerStatus,
                isWorking = viewModel.isLedControllerWorking()
            )
            
            // Control de análisis de audio
            AudioAnalysisCard(
                isActive = isAudioAnalysisActive,
                hasPermission = hasAudioPermission,
                onToggle = { 
                    if (hasAudioPermission) {
                        viewModel.toggleAudioAnalysis()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )
            
            // Modos de análisis de audio
            if (isAudioAnalysisActive) {
                AudioModesCard(
                    currentMode = currentMode,
                    availableModes = viewModel.getAvailableModes(),
                    onModeSelected = viewModel::setMode
                )
                
                // Control de sensibilidad
                SensitivityCard(
                    sensitivity = sensitivity,
                    onSensitivityChanged = viewModel::setSensitivity
                )
            }
            
            // Control de brillo
            BrightnessCard(
                brightness = brightness,
                onBrightnessChanged = viewModel::setBrightness
            )
            
            // Colores predefinidos
            PresetColorsCard(
                presetColors = viewModel.getPresetColors(),
                selectedColor = selectedColor,
                onColorSelected = { red, green, blue ->
                    viewModel.setStaticColor(red, green, blue)
                }
            )
            
            // Efectos especiales
            EffectsCard(
                onBreathingEffect = viewModel::startBreathingEffect,
                onRainbowEffect = viewModel::startRainbowEffect,
                onTurnOff = viewModel::turnOffLeds
            )
        }
    }
}

@Composable
fun StatusCard(
    status: String,
    isWorking: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWorking) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isWorking) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (isWorking) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Estado del Controlador",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun AudioAnalysisCard(
    isActive: Boolean,
    hasPermission: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Análisis de Audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isActive) "Activo" else "Inactivo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isActive) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle() },
                    enabled = hasPermission
                )
            }
            
            if (!hasPermission) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Se requiere permiso de micrófono",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AudioModesCard(
    currentMode: String,
    availableModes: List<Pair<String, String>>,
    onModeSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Modo de Visualización",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(availableModes) { (modeKey, modeName) ->
                    FilterChip(
                        onClick = { onModeSelected(modeKey) },
                        label = { 
                            Text(
                                text = modeName,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        selected = currentMode == modeKey,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SensitivityCard(
    sensitivity: Float,
    onSensitivityChanged: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sensibilidad: ${String.format("%.1f", sensitivity)}x",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Slider(
                value = sensitivity,
                onValueChange = onSensitivityChanged,
                valueRange = 0.1f..3.0f,
                steps = 28
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Baja",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Alta",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun BrightnessCard(
    brightness: Int,
    onBrightnessChanged: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Brillo: $brightness%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Slider(
                value = brightness.toFloat(),
                onValueChange = { onBrightnessChanged(it.toInt()) },
                valueRange = 0f..100f
            )
        }
    }
}

@Composable
fun PresetColorsCard(
    presetColors: List<Triple<Int, Int, Int>>,
    selectedColor: Triple<Int, Int, Int>,
    onColorSelected: (Int, Int, Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Colores Predefinidos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presetColors) { (red, green, blue) ->
                    val isSelected = selectedColor == Triple(red, green, blue)
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(red, green, blue))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .clickable {
                                onColorSelected(red, green, blue)
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun EffectsCard(
    onBreathingEffect: () -> Unit,
    onRainbowEffect: () -> Unit,
    onTurnOff: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Efectos Especiales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBreathingEffect,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Respiración")
                }
                
                Button(
                    onClick = onRainbowEffect,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Arcoíris")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onTurnOff,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apagar LEDs")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NubiaNeoGT3LightStripTheme {
        MainScreen()
    }
}