package com.yourname.gesturemusic.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.*
import com.yourname.gesturemusic.gesture.GestureType
import com.yourname.gesturemusic.viewmodel.GestureViewModel

@Composable
fun TrainingScreen(
    onBack: () -> Unit,
    viewModel: GestureViewModel = viewModel()
) {
    val progress by viewModel.trainingProgress
    val repetitions by viewModel.trainingRepetitions
    val done by viewModel.trainingDone
    val success by viewModel.trainingSuccess

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🎓 Обучение", style = MaterialTheme.typography.title3)

        if (done && success) {
            Text("✅ Сохранено!", color = MaterialTheme.colors.primary, textAlign = TextAlign.Center)
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
            return
        }

        if (done && !success) {
            Text("❌ Отменено / ошибка", color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
        }

        Text("Повторов: \$repetitions/5", style = MaterialTheme.typography.body2)

        LinearProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )

        if (!done) {
            Text("Сделайте жест 5 раз", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
        }

        Button(
            onClick = { viewModel.startTraining(GestureType.NEXT_TRACK) },
            enabled = !done,
            modifier = Modifier.fillMaxWidth()
        ) { Text("➡️ След. трек") }

        Button(
            onClick = { viewModel.startTraining(GestureType.PREVIOUS_TRACK) },
            enabled = !done,
            modifier = Modifier.fillMaxWidth()
        ) { Text("⬅️ Пред. трек") }

        Button(
            onClick = { viewModel.startTraining(GestureType.PLAY_PAUSE) },
            enabled = !done,
            modifier = Modifier.fillMaxWidth()
        ) { Text("⏯️ Play/Pause") }

        Button(
            onClick = { viewModel.stopTraining() },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("❌ Отмена") }

        Button(
            onClick = { viewModel.clearTraining() },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("🗑 Очистить всё") }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("← Назад") }
    }
}
