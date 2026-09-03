package com.yourname.gesturemusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.yourname.gesturemusic.media.MediaSessionBridge
import com.yourname.gesturemusic.viewmodel.GestureViewModel

/**
 * Media controller screen — acts as a remote control for any music player.
 *
 * Architecture:
 * Watch PlayerScreen → MediaSessionBridge → Active player (Yandex Music, Spotify, etc.)
 *
 * For Wear OS 7+ devices (Pixel Watch 3+):
 * - PrimaryActionGesture (double pinch) → play/pause
 * - DismissActionGesture (wrist turn) → back
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: GestureViewModel = viewModel()
) {
    val context = LocalContext.current
    val bridge = remember { MediaSessionBridge(context) }

    var isPlaying by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("—") }
    var artist by remember { mutableStateOf("—") }

    LaunchedEffect(Unit) {
        bridge.setCallback(object : MediaSessionBridge.MediaControllerCallback {
            override fun onPlaybackStateChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
        bridge.start()
    }

    DisposableEffect(Unit) {
        onDispose { bridge.stop() }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
            // Header
            item {
                Text("🎵 Плеер", style = MaterialTheme.typography.title3)
            }

            // Track info
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.body1,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        artist,
                        style = MaterialTheme.typography.caption2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Play/Pause button — this is where Wear OS 7 Gesture API would map
            // PrimaryActionGesture (double pinch) to this button
            item {
                Button(
                    onClick = { bridge.playPause() },
                    modifier = Modifier.size(64.dp),
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Text(
                        if (isPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.title2
                    )
                }
            }

            // Transport controls
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { bridge.previousTrack() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text("⏮", style = MaterialTheme.typography.title3)
                    }
                    Button(
                        onClick = { bridge.playPause() },
                        modifier = Modifier.size(56.dp),
                        colors = ButtonDefaults.primaryButtonColors()
                    ) {
                        Text(if (isPlaying) "⏸" else "▶")
                    }
                    Button(
                        onClick = { bridge.nextTrack() },
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text("⏭", style = MaterialTheme.typography.title3)
                    }
                }
            }

            // Gesture controls section
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Управление жестами:",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant
                )
            }
            item {
                Text(
                    "👌 Щипок → Play/Pause\n🔄 Поворот → Next/Prev",
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Gesture service toggle
            item {
                Spacer(Modifier.height(4.dp))
                val isRunning by viewModel.isRunning
                Button(
                    onClick = { if (isRunning) viewModel.stopService() else viewModel.startService() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isRunning)
                        ButtonDefaults.secondaryButtonColors()
                    else
                        ButtonDefaults.primaryButtonColors()
                ) {
                    Text(if (isRunning) "⏹ Стоп" else "▶️ Жесты ВКЛ")
                }
            }

            // Back button — Wear OS 7 DismissActionGesture (wrist turn) would
            // naturally map to this back navigation
            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Text("← Назад")
                }
            }
        }
}
