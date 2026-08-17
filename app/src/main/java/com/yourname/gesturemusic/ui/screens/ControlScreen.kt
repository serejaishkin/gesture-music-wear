package com.yourname.gesturemusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.yourname.gesturemusic.ui.components.SensitivitySlider
import com.yourname.gesturemusic.viewmodel.GestureViewModel

@Composable
fun ControlScreen(
    viewModel: GestureViewModel = viewModel()
) {
    val isRunning by viewModel.isRunning
    val lastGesture by viewModel.lastGesture
    val angleThreshold by viewModel.angleThreshold
    val pinchThreshold by viewModel.pinchThreshold
    val minDuration by viewModel.minDuration
    val maxDuration by viewModel.maxDuration
    val saveMessage by viewModel.saveMessage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "🎵 Gesture Music",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center
        )

        val statusColor = if (isRunning) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.onSurfaceVariant
        }
        Text(
            text = if (isRunning) "● Слушаю жесты" else "○ Остановлено",
            color = statusColor,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = {
                if (isRunning) viewModel.stopService() else viewModel.startService()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text(if (isRunning) "⏹ Стоп" else "▶️ Старт")
        }

        Spacer(modifier = Modifier.height(2.dp))

        SensitivitySlider(
            label = "Поворот",
            value = angleThreshold,
            valueRange = 15f..35f,
            steps = 19,
            onValueChange = viewModel::updateAngleThreshold,
            valueFormatter = { "%.0f°".format(it) }
        )

        SensitivitySlider(
            label = "Щипок",
            value = pinchThreshold,
            valueRange = 2.0f..5.0f,
            steps = 29,
            onValueChange = viewModel::updatePinchThreshold,
            valueFormatter = { "%.1f".format(it) }
        )

        // !!! Новые слайдеры для длительности жеста
        SensitivitySlider(
            label = "Мин. время",
            value = minDuration.toFloat(),
            valueRange = 50f..300f,
            steps = 24,
            onValueChange = { viewModel.updateMinDuration(it.toLong()) },
            valueFormatter = { "${it.toInt()}мс" }
        )

        SensitivitySlider(
            label = "Макс. время",
            value = maxDuration.toFloat(),
            valueRange = 300f..1000f,
            steps = 69,
            onValueChange = { viewModel.updateMaxDuration(it.toLong()) },
            valueFormatter = { "${it.toInt()}мс" }
        )

        Spacer(modifier = Modifier.height(2.dp))

        Button(
            onClick = viewModel::saveSettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("💾 Сохранить")
        }

        Button(
            onClick = viewModel::restoreDefaults,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("↺ Сбросить")
        }

        if (saveMessage.isNotEmpty()) {
            Text(
                text = saveMessage,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.secondary,
                textAlign = TextAlign.Center
            )
        }

        if (lastGesture.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lastGesture,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.secondary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "➡️ вправо: prev\n⬅️ влево: next\n👌 щипок: play/pause",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
