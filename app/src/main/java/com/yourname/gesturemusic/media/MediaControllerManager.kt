package com.yourname.gesturemusic.media

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/** Local media control for the watch without Bluetooth HID emulation. */
class MediaControllerManager(private val context: Context) {
    companion object {
        private const val TAG = "MediaControllerManager"
        private const val POLL_INTERVAL_MS = 2000L
        private const val SESSION_MEMORY_MS = 30000L
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var stateListener: ((Boolean) -> Unit)? = null
    private var isPolling = false
    private var hadRecentPlayback = false
    private var lastPlaybackAt = 0L

    fun setStateListener(listener: (Boolean) -> Unit) { stateListener = listener }

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
        hadRecentPlayback = false
        lastPlaybackAt = 0L
    }

    /**
     * FIX: previously these three methods were gated behind canControlPlayback(),
     * which required audioManager.isMusicActive == true OR playback within the
     * last 30s. That meant the very first PLAY_PAUSE (starting playback from a
     * paused/stopped state) was always silently dropped, because nothing had
     * "recently played" yet — the gesture was recognized (vibration + broadcast
     * fired from GestureMusicService) but no media key was ever dispatched.
     * Media key events are safe to dispatch even with no active session; Android
     * routes them to the appropriate session (or does nothing) on its own, so the
     * gate was providing no real protection — only breaking legitimate use.
     */
    fun playPause() {
        dispatchLocal(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun nextTrack() {
        dispatchLocal(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previousTrack() {
        dispatchLocal(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun isPlaying(): Boolean = audioManager.isMusicActive
    fun hasActiveSession(): Boolean = canControlPlayback()

    /** Kept for UI/state purposes (e.g. idle-timer decisions), no longer used to gate dispatch. */
    private fun canControlPlayback(): Boolean {
        val playing = audioManager.isMusicActive
        if (playing) {
            hadRecentPlayback = true
            lastPlaybackAt = SystemClock.elapsedRealtime()
            return true
        }
        return hadRecentPlayback && SystemClock.elapsedRealtime() - lastPlaybackAt <= SESSION_MEMORY_MS
    }

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
        if (playing) {
            hadRecentPlayback = true
            lastPlaybackAt = SystemClock.elapsedRealtime()
        } else if (hadRecentPlayback && SystemClock.elapsedRealtime() - lastPlaybackAt > SESSION_MEMORY_MS) {
            hadRecentPlayback = false
        }
        stateListener?.invoke(playing)
        handler.postDelayed({ pollState() }, POLL_INTERVAL_MS)
    }
}
