package com.yourname.gesturemusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.items
import com.yourname.gesturemusic.ui.components.SensitivitySlider
import com.yourname.gesturemusic.viewmodel.GestureViewModel

@Composable
fun ControlScreen(viewModel: GestureViewModel = viewModel()) {
    var showTraining by remember { mutableStateOf(false) }
    if (showTraining) {
        TrainingScreen(onBack = { showTraining = false })
        return
    }

    val isRunning by viewModel.isRunning
    val lastGesture by viewModel.lastGesture
    val strategyName by viewModel.strategyName
    val angleThreshold by viewModel.angleThreshold
    val pinchThreshold by viewModel.pinchThreshold
    val minDuration by viewModel.minDuration
    val maxDuration by viewModel.maxDuration
    val gestureCooldown by viewModel.gestureCooldown
    val leftHand by viewModel.leftHand
    val saveMessage by viewModel.saveMessage

    ScalingLazyColumn(
        autoCentering = null,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("🎵 Gesture Music", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        }
        item {
            Text(
                if (isRunning) "● Слушаю жесты" else "○ Остановлено",
                color = if (isRunning) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
        }
        if (isRunning && strategyName.isNotEmpty()) {
            item {
                Text(
                    "Движок: $strategyName",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Button(onClick = { if (isRunning) viewModel.stopService() else viewModel.startService() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.primaryButtonColors()) {
                Text(if (isRunning) "⏹ Стоп" else "▶️ Старт")
            }
        }
        item {
            Button(onClick = { viewModel.updateLeftHand(!leftHand) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) {
                Text(if (leftHand) "⌚ Левая рука" else "⌚ Правая рука")
            }
        }
        item { SensitivitySlider("Поворот", angleThreshold, 15f..35f, 19, viewModel::updateAngleThreshold) { "%.0f°".format(it) } }
        item { SensitivitySlider("Щипок", pinchThreshold, 2.0f..5.0f, 29, viewModel::updatePinchThreshold) { "%.1f".format(it) } }
        item { SensitivitySlider("Мин. время", minDuration.toFloat(), 50f..300f, 24, { viewModel.updateMinDuration(it.toLong()) }) { "${it.toInt()}мс" } }
        item { SensitivitySlider("Макс. время", maxDuration.toFloat(), 300f..1000f, 69, { viewModel.updateMaxDuration(it.toLong()) }) { "${it.toInt()}мс" } }
        item { SensitivitySlider("Пауза между жестами", gestureCooldown.toFloat(), 600f..2500f, 19, { viewModel.updateGestureCooldown(it.toLong()) }) { "${it.toInt()}мс" } }
        item {
            Button(onClick = viewModel::saveSettings, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("💾 Сохранить") }
        }
        item {
            Button(onClick = viewModel::restoreDefaults, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("↺ Сбросить") }
        }
        item {
            Button(onClick = { showTraining = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("🎓 Обучение") }
        }
        if (saveMessage.isNotEmpty()) {
            item { Text(saveMessage, style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center) }
        }
        if (lastGesture.isNotEmpty()) {
            item { Text(lastGesture, style = MaterialTheme.typography.caption3, color = MaterialTheme.colors.secondary, textAlign = TextAlign.Center) }
        }
        item {
            Text(
                "Поворот кисти вправо ➡️ next\nПоворот влево ⬅️ prev\n(для левой руки — наоборот)\n👌 двойной щипок: play/pause",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
