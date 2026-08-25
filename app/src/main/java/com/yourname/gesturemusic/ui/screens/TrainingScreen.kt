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

    // Reset local state when the session ends or is cleared.
    LaunchedEffect(done) { if (done && !success) activeGesture = null }
    if (!done && repetitions == 0 && progress == 0) {
        // keep activeGesture — session may have just started
    }

    // Auto-return after a successful save.
    LaunchedEffect(done, success) {
        if (done && success) {
            delay(1200)
            onBack()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("🎓 Обучение", style = MaterialTheme.typography.title3)

        if (done && success) {
            Text("✅ Сохранено!", color = MaterialTheme.colors.primary, textAlign = TextAlign.Center)
            return
        }

        if (done && !success) {
            Text("❌ Отменено / ошибка", color = MaterialTheme.colors.error, textAlign = TextAlign.Center)
        }

        activeGesture?.let { g ->
            val label = when (g) {
                GestureType.NEXT_TRACK -> "След. трек"
                GestureType.PREVIOUS_TRACK -> "Пред. трек"
                GestureType.PLAY_PAUSE -> "Play/Pause"
                GestureType.ACTIVATE -> "Активация"
            }
            Text("Обучается: $label", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
        }

        Text("Повторов: $repetitions/5", style = MaterialTheme.typography.body2)

        CircularProgressIndicator(
            progress = progress / 100f,
            modifier = Modifier.fillMaxWidth(0.6f).height(4.dp)
        )
        Text("$progress%", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)

        val sessionActive = activeGesture != null && !done

        if (!sessionActive) {
            Text("Выберите жест и сделайте его 5 раз", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
        } else {
            Text(if (repetitions == 0) "Сделайте жест..." else "Повторите ещё ${5 - repetitions}...", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
        }

        Button(
            onClick = { activeGesture = GestureType.NEXT_TRACK; viewModel.startTraining(GestureType.NEXT_TRACK) },
            enabled = !sessionActive,
            modifier = Modifier.fillMaxWidth()
        ) { Text("➡️ След. трек") }

        Button(
            onClick = { activeGesture = GestureType.PREVIOUS_TRACK; viewModel.startTraining(GestureType.PREVIOUS_TRACK) },
            enabled = !sessionActive,
            modifier = Modifier.fillMaxWidth()
        ) { Text("⬅️ Пред. трек") }

        Button(
            onClick = { activeGesture = GestureType.PLAY_PAUSE; viewModel.startTraining(GestureType.PLAY_PAUSE) },
            enabled = !sessionActive,
            modifier = Modifier.fillMaxWidth()
        ) { Text("⏯️ Play/Pause") }

        Button(
            onClick = { activeGesture = GestureType.ACTIVATE; viewModel.startTraining(GestureType.ACTIVATE) },
            enabled = !sessionActive,
            modifier = Modifier.fillMaxWidth()
        ) { Text("🔓 Активация") }

        Button(
            onClick = {
                activeGesture = null
                viewModel.stopTraining()
            },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("❌ Отмена") }

        Button(
            onClick = {
                activeGesture = null
                viewModel.clearTraining()
            },
            colors = ButtonDefaults.secondaryButtonColors(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("🗑 Очистить всё") }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("← Назад") }
    }
}
