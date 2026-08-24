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
<<<<<<< HEAD
fun TrainingScreen(
    onBack: () -> Unit,
    viewModel: GestureViewModel = viewModel()
) {
    val progress by viewModel.trainingProgress
    val repetitions by viewModel.trainingRepetitions
    val done by viewModel.trainingDone
    val success by viewModel.trainingSuccess
=======
fun TrainingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isTraining by remember { mutableStateOf(false) }
    var selectedGesture by remember { mutableStateOf<GestureType?>(null) }
    var progress by remember { mutableStateOf(0) }
    var repetitions by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Выберите жест") }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != GestureMusicService.ACTION_TRAINING_PROGRESS) return
                progress = intent.getIntExtra(GestureMusicService.EXTRA_TRAINING_PROGRESS, 0)
                repetitions = intent.getIntExtra(GestureMusicService.EXTRA_TRAINING_REPETITIONS, 0)
                if (intent.getBooleanExtra(GestureMusicService.EXTRA_TRAINING_DONE, false)) {
                    isTraining = false
                    message = if (intent.getBooleanExtra(GestureMusicService.EXTRA_TRAINING_SUCCESS, false)) "✓ Жест сохранён" else "✕ Не удалось сохранить — повторите"
                } else if (repetitions > 0) {
                    message = "✓ Повтор $repetitions/$REQUIRED_REPETITIONS"
                    progress = 0
                }
            }
        }
        val filter = IntentFilter(GestureMusicService.ACTION_TRAINING_PROGRESS)
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
>>>>>>> a28610485209baa8884a82ddbaef253d63caeba3

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
<<<<<<< HEAD
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
=======
        Text("🎓 Обучение", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        Text("Повторите один жест 5 раз", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
        Text("${repetitions.coerceAtMost(5)}/5", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)

        if (selectedGesture != null) {
            Text(gestureName(selectedGesture!!), style = MaterialTheme.typography.body1, textAlign = TextAlign.Center)
        }

        if (isTraining) {
            Text("Запись: $progress%", style = MaterialTheme.typography.title3, color = MaterialTheme.colors.primary, textAlign = TextAlign.Center)
            Text("Сделайте жест сейчас", style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
        } else {
            Text(message, style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)
        }

        GestureButton(GestureType.ACTIVATE, selectedGesture, isTraining) { selectedGesture = GestureType.ACTIVATE; startOrStop(context, GestureType.ACTIVATE, isTraining); isTraining = !isTraining }
        GestureButton(GestureType.NEXT_TRACK, selectedGesture, isTraining) { selectedGesture = GestureType.NEXT_TRACK; startOrStop(context, GestureType.NEXT_TRACK, isTraining); isTraining = !isTraining }
        GestureButton(GestureType.PREVIOUS_TRACK, selectedGesture, isTraining) { selectedGesture = GestureType.PREVIOUS_TRACK; startOrStop(context, GestureType.PREVIOUS_TRACK, isTraining); isTraining = !isTraining }
        GestureButton(GestureType.PLAY_PAUSE, selectedGesture, isTraining) { selectedGesture = GestureType.PLAY_PAUSE; startOrStop(context, GestureType.PLAY_PAUSE, isTraining); isTraining = !isTraining }

        if (isTraining) {
            Button(onClick = { context.startService(Intent(context, GestureMusicService::class.java).apply { action = GestureMusicService.ACTION_STOP_TRAINING }); isTraining = false }, modifier = Modifier.fillMaxWidth()) {
                Text("⏹ Завершить повтор")
            }
        }

        Spacer(Modifier.height(3.dp))
        Button(onClick = {
            context.startService(Intent(context, GestureMusicService::class.java).apply { action = GestureMusicService.ACTION_CLEAR_TRAINING })
            isTraining = false; repetitions = 0; progress = 0; message = "Шаблоны очищены"
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("🗑 Очистить") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.secondaryButtonColors()) { Text("← Назад") }
    }
}

@Composable
private fun GestureButton(gesture: GestureType, selected: GestureType?, active: Boolean, onClick: () -> Unit) {
    val isSelected = selected == gesture
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = if (isSelected) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors()) {
        Text(if (isSelected && active) "⏺ ${gestureName(gesture)}" else "▶ ${gestureName(gesture)}")
    }
}

private fun startOrStop(context: Context, gesture: GestureType, active: Boolean) {
    context.startService(Intent(context, GestureMusicService::class.java).apply {
        action = if (active) GestureMusicService.ACTION_STOP_TRAINING else GestureMusicService.ACTION_START_TRAINING
        putExtra(GestureMusicService.EXTRA_TRAINING_GESTURE, gesture.name)
    })
}

private fun gestureName(gesture: GestureType): String = when (gesture) {
    GestureType.ACTIVATE -> "Активация"
    GestureType.NEXT_TRACK -> "След. трек"
    GestureType.PREVIOUS_TRACK -> "Пред. трек"
    GestureType.PLAY_PAUSE -> "Play/Pause"
}
>>>>>>> a28610485209baa8884a82ddbaef253d63caeba3
