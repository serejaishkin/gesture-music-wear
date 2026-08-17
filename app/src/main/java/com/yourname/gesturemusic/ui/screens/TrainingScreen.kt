package com.yourname.gesturemusic.ui.screens

import android.content.Intent
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

@Composable
fun TrainingScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recordingGesture by remember { mutableStateOf<GestureType?>(null) }
    var progress by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "🎓 Обучение",
            style = MaterialTheme.typography.title3,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Запишите свой жест",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )

        if (isRecording && recordingGesture != null) {
            Text(
                text = "Запись: ${gestureName(recordingGesture!!)}\n$progress%",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Кнопки записи
        TrainingButton(
            gesture = GestureType.NEXT_TRACK,
            isRecording = isRecording && recordingGesture == GestureType.NEXT_TRACK,
            onStart = {
                isRecording = true
                recordingGesture = GestureType.NEXT_TRACK
                startTraining(context, GestureType.NEXT_TRACK)
            },
            onStop = {
                isRecording = false
                recordingGesture = null
                stopTraining(context)
            }
        )

        TrainingButton(
            gesture = GestureType.PREVIOUS_TRACK,
            isRecording = isRecording && recordingGesture == GestureType.PREVIOUS_TRACK,
            onStart = {
                isRecording = true
                recordingGesture = GestureType.PREVIOUS_TRACK
                startTraining(context, GestureType.PREVIOUS_TRACK)
            },
            onStop = {
                isRecording = false
                recordingGesture = null
                stopTraining(context)
            }
        )

        TrainingButton(
            gesture = GestureType.PLAY_PAUSE,
            isRecording = isRecording && recordingGesture == GestureType.PLAY_PAUSE,
            onStart = {
                isRecording = true
                recordingGesture = GestureType.PLAY_PAUSE
                startTraining(context, GestureType.PLAY_PAUSE)
            },
            onStop = {
                isRecording = false
                recordingGesture = null
                stopTraining(context)
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = {
                val intent = Intent(context, GestureMusicService::class.java).apply {
                    action = GestureMusicService.ACTION_CLEAR_TRAINING
                }
                context.startService(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("🗑 Очистить всё")
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("← Назад")
        }
    }
}

@Composable
private fun TrainingButton(
    gesture: GestureType,
    isRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Button(
        onClick = { if (isRecording) onStop() else onStart() },
        modifier = Modifier.fillMaxWidth(),
        colors = if (isRecording) {
            ButtonDefaults.primaryButtonColors()
        } else {
            ButtonDefaults.secondaryButtonColors()
        }
    ) {
        Text(
            if (isRecording) "⏹ ${gestureName(gesture)}" else "⏺ ${gestureName(gesture)}"
        )
    }
}

private fun gestureName(gesture: GestureType): String = when (gesture) {
    GestureType.NEXT_TRACK -> "След. трек"
    GestureType.PREVIOUS_TRACK -> "Пред. трек"
    GestureType.PLAY_PAUSE -> "Play/Pause"
}

private fun startTraining(context: android.content.Context, gesture: GestureType) {
    val intent = Intent(context, GestureMusicService::class.java).apply {
        action = GestureMusicService.ACTION_START_TRAINING
        putExtra(GestureMusicService.EXTRA_TRAINING_GESTURE, gesture.name)
    }
    context.startService(intent)
}

private fun stopTraining(context: android.content.Context) {
    val intent = Intent(context, GestureMusicService::class.java).apply {
        action = GestureMusicService.ACTION_STOP_TRAINING
    }
    context.startService(intent)
}
