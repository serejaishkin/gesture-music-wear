package com.yourname.gesturemusic.media

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Media control for the watch itself.
 *
 * We intentionally do not emulate a Bluetooth HID device here. When no real
 * playback is active on the watch, a gesture must never start a local player.
 * Phone control can later be provided by a real Wear OS companion/data-layer
 * transport instead of hidden Bluetooth profile reflection.
 */
class MediaControllerManager(private val context: Context) {
    companion object {
        private const val TAG = "MediaControllerManager"
        private const val POLL_INTERVAL_MS = 2000L
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var stateListener: ((Boolean) -> Unit)? = null
    private var isPolling = false

    fun setStateListener(listener: (isPlaying: Boolean) -> Unit) {
        stateListener = listener
    }

    fun connect() {
        if (isPolling) return
        isPolling = true
        pollState()
        Log.d(TAG, "Connected in local media mode")
    }

    fun refreshConnection() {}

    fun disconnect() {
        isPolling = false
        handler.removeCallbacksAndMessages(null)
    }

    fun playPause() {
        if (!audioManager.isMusicActive) {
            Log.d(TAG, "Ignoring PLAY_PAUSE: no active playback on watch")
            return
        }
        dispatchLocal(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun nextTrack() {
        if (!audioManager.isMusicActive) {
            Log.d(TAG, "Ignoring NEXT: no active playback on watch")
            return
        }
        dispatchLocal(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previousTrack() {
        if (!audioManager.isMusicActive) {
            Log.d(TAG, "Ignoring PREVIOUS: no active playback on watch")
            return
        }
        dispatchLocal(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun isPlaying(): Boolean = audioManager.isMusicActive
    fun hasActiveSession(): Boolean = audioManager.isMusicActive

    private fun dispatchLocal(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()
        try {
            val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            handler.postDelayed({ audioManager.dispatchMediaKeyEvent(upEvent) }, 50)
            Log.d(TAG, "Local media key sent: keyCode=$keyCode")
        } catch (e: Exception) {
            Log.w(TAG, "Local media control failed: ${e.message}")
        }
    }

    private fun pollState() {
        if (!isPolling) return
        val playing = audioManager.isMusicActive
        stateListener?.invoke(playing)
        handler.postDelayed({ pollState() }, POLL_INTERVAL_MS)
    }
}
