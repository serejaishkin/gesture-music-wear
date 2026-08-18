package com.yourname.gesturemusic.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.yourname.gesturemusic.gesture.GestureType
import com.yourname.gesturemusic.service.GestureMusicService

private const val REQUIRED_REPETITIONS = 5

@Composable
fun TrainingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordingGesture by remember { mutableStateOf<GestureType?>(null) }
    var progress by remember { mutableStateOf(0) }
    var repetitions by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("Выберите жест. Нужно 5 одинаковых повторений.") }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != GestureMusicService.ACTION_TRAINING_PROGRESS) return
                progress = intent.getIntExtra(GestureMusicService.EXTRA_TRAINING_PROGRESS, 0)
                repetitions = intent.getIntExtra(GestureMusicService.EXTRA_TRAINING_REPETITIONS, 0)
                val done = intent.getBooleanExtra(GestureMusicService.EXTRA_TRAINING_DONE, false)
                val success = intent.getBooleanExtra(GestureMusicService.EXTRA_TRAINING_SUCCESS, false)
                if (done) {
                    isRecording = false
                    message = if (success) "✓ Жест сохранён" else "✕ Обучение отклонено"
                } else if (repetitions > 0) {
                    message = "Повтор $repetitions/$REQUIRED_REPETITIONS принят. Сделайте следующий."
                    isRecording = false
                    progress = 0
                }
            }
        }
        val filter = IntentFilter(GestureMusicService.ACTION_TRAINING_PROGRESS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("🎓 Обучение", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
        Text("5 повторений одного жеста", style = MaterialTheme.typography.body2, textAlign = TextAlign.Center)
        Text("${repetitions.coerceAtMost(REQUIRED_REPETITIONS)}/$REQUIRED_REPETITIONS", style = MaterialTheme.typography.title2, color = MaterialTheme.colors.primary)

        if (recordingGesture != null) {
            Text("${gestureName(recordingGesture!!)}\n$progress%", style = MaterialTheme.typography.body1, color = MaterialTheme.colors.primary, textAlign = TextAlign.Center)
        }
        Text(message, style = MaterialTheme.typography.caption3, textAlign = TextAlign.Center)

        TrainingButton(GestureType.ACTIVATE, recordingGesture == GestureType.ACTIVATE && isRecording,
            { begin(context, GestureType.ACTIVATE); recordingGesture=GestureType.ACTIVATE; isRecording=true; progress=0; message="Сделайте жест и нажмите Стоп" },
            { end(context); isRecording=false })
        TrainingButton(GestureType.NEXT_TRACK, recordingGesture == GestureType.NEXT_TRACK && isRecording,
            { begin(context, GestureType.NEXT_TRACK); recordingGesture=GestureType.NEXT_TRACK; isRecording=true; progress=0; message="Сделайте жест и нажмите Стоп" },
            { end(context); isRecording=false })
        TrainingButton(GestureType.PREVIOUS_TRACK, recordingGesture == GestureType.PREVIOUS_TRACK && isRecording,
            { begin(context, GestureType.PREVIOUS_TRACK); recordingGesture=GestureType.PREVIOUS_TRACK; isRecording=true; progress=0; message="Сделайте жест и нажмите Стоп" },
            { end(context); isRecording=false })
        TrainingButton(GestureType.PLAY_PAUSE, recordingGesture == GestureType.PLAY_PAUSE && isRecording,
            { begin(context, GestureType.PLAY_PAUSE); recordingGesture=GestureType.PLAY_PAUSE; isRecording=true; progress=0; message="Сделайте жест и нажмите Стоп" },
            { end(context); isRecording=false })

        Spacer(Modifier.height(4.dp))
        Button(onClick = { context.startService(Intent(context,GestureMusicService::class.java).apply{action=GestureMusicService.ACTION_CLEAR_TRAINING}); repetitions=0; progress=0; message="Шаблоны очищены" }, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.secondaryButtonColors()) { Text("🗑 Очистить всё") }
        Button(onClick=onBack, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.secondaryButtonColors()) { Text("← Назад") }
    }
}

@Composable
private fun TrainingButton(gesture: GestureType, isRecording: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Button(onClick={if(isRecording) onStop() else onStart()}, modifier=Modifier.fillMaxWidth(), colors=if(isRecording) ButtonDefaults.primaryButtonColors() else ButtonDefaults.secondaryButtonColors()) {
        Text(if(isRecording) "⏹ ${gestureName(gesture)}" else "⏺ ${gestureName(gesture)}")
    }
}

private fun gestureName(gesture: GestureType): String = when(gesture) {
    GestureType.ACTIVATE -> "Активация"
    GestureType.NEXT_TRACK -> "След. трек"
    GestureType.PREVIOUS_TRACK -> "Пред. трек"
    GestureType.PLAY_PAUSE -> "Play/Pause"
}

private fun begin(context: Context, gesture: GestureType) {
    context.startService(Intent(context,GestureMusicService::class.java).apply { action=GestureMusicService.ACTION_START_TRAINING; putExtra(GestureMusicService.EXTRA_TRAINING_GESTURE,gesture.name) })
}

private fun end(context: Context) {
    context.startService(Intent(context,GestureMusicService::class.java).apply { action=GestureMusicService.ACTION_STOP_TRAINING })
}
