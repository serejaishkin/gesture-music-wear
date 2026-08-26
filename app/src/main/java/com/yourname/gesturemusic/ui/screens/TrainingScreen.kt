package com.yourname.gesturemusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.yourname.gesturemusic.gesture.GestureType
import com.yourname.gesturemusic.viewmodel.GestureViewModel
import kotlinx.coroutines.delay

@Composable
fun TrainingScreen(
    onBack: () -> Unit,
    viewModel: GestureViewModel = viewModel()
) {
    val progress by viewModel.trainingProgress
    val repetitions by viewModel.trainingRepetitions
    val done by viewModel.trainingDone
    val success by viewModel.trainingSuccess

    var activeGesture by remember { mutableStateOf<GestureType?>(null) }

    LaunchedEffect(done) { if (done && !success) activeGesture = null }
    LaunchedEffect(done, success) {
        if (done && success) {
            delay(1200)
            onBack()
        }
    }

    val sessionActive = activeGesture != null && !done
    val gestureLabel = when (activeGesture) {
        GestureType.NEXT_TRACK -> "След. трек"
        GestureType.PREVIOUS_TRACK -> "Пред. трек"
        GestureType.PLAY_PAUSE -> "Play/Pause"
        GestureType.ACTIVATE -> "Активация"
        null -> ""
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item { Text("🎓 Обучение", style = MaterialTheme.typography.title3) }

        if (done && success) {
            item { Text("✅ Сохранено!", color = MaterialTheme.colors.primary, textAlign = TextAlign.Center) }
        } else if (done && !success) {
            item { Text("❌ Отменено / ошибка", color = MaterialTheme.colors.error, textAlign = TextAlign.Center) }
        }

        if (sessionActive) {
            item { Text("Обучается: $gestureLabel", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center) }
        }
        item { Text("Повторов: $repetitions/5", style = MaterialTheme.typography.body2) }
        item {
            CircularProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
        item { Text("$progress%", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center) }

        item {
            Text(
                when {
                    sessionActive && repetitions == 0 -> "Сделайте жест..."
                    sessionActive -> "Повторите ещё ${5 - repetitions}..."
                    else -> "Выберите жест и сделайте его 5 раз"
                },
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center
            )
        }

        item {
            Button(
                onClick = { activeGesture = GestureType.NEXT_TRACK; viewModel.startTraining(GestureType.NEXT_TRACK) },
                enabled = !sessionActive,
                modifier = Modifier.fillMaxWidth()
            ) { Text("➡️ След. трек") }
        }
        item {
            Button(
                onClick = { activeGesture = GestureType.PREVIOUS_TRACK; viewModel.startTraining(GestureType.PREVIOUS_TRACK) },
                enabled = !sessionActive,
                modifier = Modifier.fillMaxWidth()
            ) { Text("⬅️ Пред. трек") }
        }
        item {
            Button(
                onClick = { activeGesture = GestureType.PLAY_PAUSE; viewModel.startTraining(GestureType.PLAY_PAUSE) },
                enabled = !sessionActive,
                modifier = Modifier.fillMaxWidth()
            ) { Text("⏯️ Play/Pause") }
        }
        item {
            Button(
                onClick = { activeGesture = GestureType.ACTIVATE; viewModel.startTraining(GestureType.ACTIVATE) },
                enabled = !sessionActive,
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔓 Активация") }
        }
        item {
            Button(
                onClick = { activeGesture = null; viewModel.stopTraining() },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("❌ Отмена") }
        }
        item {
            Button(
                onClick = { activeGesture = null; viewModel.clearTraining() },
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("🗑 Очистить всё") }
        }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("← Назад") }
        }
    }
}
