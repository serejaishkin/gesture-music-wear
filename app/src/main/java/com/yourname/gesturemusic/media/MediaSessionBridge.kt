package com.yourname.gesturemusic.media

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Bridges to the active audio session on the device.
 * Detects if music is playing and sends media key events
 * to control any player (Yandex Music, Spotify, YouTube Music, etc.)
 *
 * Uses AudioManager which works without system permissions.
 */
class MediaSessionBridge(private val context: Context) {

    companion object {
        private const val TAG = "MediaSessionBridge"
        private const val POLL_INTERVAL_MS = 1000L
        private const val SESSION_MEMORY_MS = 30000L
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var listener: MediaControllerCallback? = null
    private var isPolling = false

    var isPlaying = false; private set
    var hadRecentPlayback = false; private set
    var lastPlaybackAt = 0L; private set

    interface MediaControllerCallback {
        fun onPlaybackStateChanged(isPlaying: Boolean)
    }

    fun setCallback(callback: MediaControllerCallback) {
        this.listener = callback
    }

    fun start() {
        if (isPolling) return
        isPolling = true
        pollState()
        Log.d(TAG, "Started monitoring audio state")
    }

    fun stop() {
        isPolling = false
        handler.removeCallbacksAndMessages(null)
    }

    fun playPause() { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
    fun nextTrack() { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
    fun previousTrack() { dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }

    private fun dispatchMediaKey(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        try {
            val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            handler.postDelayed({ audioManager.dispatchMediaKeyEvent(upEvent) }, 50)
            Log.d(TAG, "Media key: keyCode=$keyCode")
        } catch (e: Exception) {
            Log.w(TAG, "Media control failed: ${e.message}")
        }
    }

    private fun pollState() {
        if (!isPolling) return
        val playing = audioManager.isMusicActive
        if (playing) {
            hadRecentPlayback = true
            lastPlaybackAt = SystemClock.elapsedRealtime()
        } else if (hadRecentPlayback && SystemClock.elapsedRealtime() - lastPlaybackAt > SESSION_MEMORY_MS) {
            hadRecentPlayback = false
        }
        if (playing != isPlaying) {
            isPlaying = playing
            listener?.onPlaybackStateChanged(playing)
        }
        handler.postDelayed({ pollState() }, POLL_INTERVAL_MS)
    }
}
