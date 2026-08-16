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

/**
 * Главный экран приложения.
 * Показывает статус, настройки чувствительности и кнопки управления.
 */
@Composable
fun ControlScreen(
    viewModel: GestureViewModel = viewModel()
) {
    val isRunning by viewModel.isRunning
    val lastGesture by viewModel.lastGesture
    val angleThreshold by viewModel.angleThreshold
    val pinchThreshold by viewModel.pinchThreshold
    val isCalibrating by viewModel.isCalibrating

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Заголовок
        Text(
            text = "🎵 Gesture Music",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center
        )

        // Статус
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

        // Кнопка запуска / остановки
        Button(
            onClick = {
                if (isRunning) viewModel.stopService() else viewModel.startService()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.primaryButtonColors()
        ) {
            Text(if (isRunning) "⏹ Стоп" else "▶️ Старт")
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Ползунок чувствительности поворота
        SensitivitySlider(
            label = "Поворот",
            value = angleThreshold,
            valueRange = 15f..35f,
            steps = 19,
            onValueChange = viewModel::updateAngleThreshold,
            valueFormatter = { "%.0f°".format(it) }
        )

        // Ползунок чувствительности щипка
        SensitivitySlider(
            label = "Щипок",
            value = pinchThreshold,
            valueRange = 2.0f..5.0f,
            steps = 29,
            onValueChange = viewModel::updatePinchThreshold,
            valueFormatter = { "%.1f".format(it) }
        )

        // Кнопка калибровки
        Button(
            onClick = viewModel::calibrate,
            enabled = !isCalibrating,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text(if (isCalibrating) "Калибровка..." else "🔧 Калибровка")
        }

        // Последний жест
        if (lastGesture.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lastGesture,
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.secondary,
                textAlign = TextAlign.Center
            )
        }

        // Подсказка
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "➡️ вправо: next\n⬅️ влево: prev\n👌 щипок: play/pause",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
