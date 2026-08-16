package com.yourname.gesturemusic.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Менеджер медиа-контроллера.
 * Подключается к активной медиа-сессии и отправляет команды play/pause/next/previous.
 */
class MediaControllerManager(context: Context) {

    companion object {
        private const val TAG = "MediaControllerManager"
    }

    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var mediaController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    private var callback: MediaController.Callback? = null
    private var stateListener: ((Boolean) -> Unit)? = null

    /** Подключиться к активной медиа-сессии. */
    fun connect() {
        try {
            val sessions = mediaSessionManager.getActiveSessions(ComponentName("com.yourname.gesturemusic", "com.yourname.gesturemusic.service.GestureMusicService"))
            val activeSession = sessions.firstOrNull()

            if (activeSession != null) {
                mediaController = activeSession
                setupCallback(activeSession)
                Log.d(TAG, "Connected to: ${activeSession.packageName}")
            } else {
                Log.w(TAG, "No active media session found")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    /** Обновить подключение к текущей активной сессии. */
    fun refreshConnection() {
        disconnect()
        connect()
    }

    /** Отключиться от сессии. */
    fun disconnect() {
        mediaController?.unregisterCallback(callback ?: return)
        mediaController = null
        callback = null
    }

    /** Установить слушатель изменения состояния воспроизведения. */
    fun setStateListener(listener: (isPlaying: Boolean) -> Unit) {
        stateListener = listener
    }

    /** Воспроизведение / пауза (toggle). */
    fun playPause() {
        val controller = mediaController ?: run {
            refreshConnection()
            return
        }
        val state = controller.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    /** Следующий трек. */
    fun nextTrack() {
        val controller = mediaController ?: run {
            refreshConnection()
            return
        }
        controller.transportControls.skipToNext()
    }

    /** Предыдущий трек. */
    fun previousTrack() {
        val controller = mediaController ?: run {
            refreshConnection()
            return
        }
        controller.transportControls.skipToPrevious()
    }

    /** Воспроизводится ли сейчас музыка. */
    fun isPlaying(): Boolean {
        return mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    /** Есть ли активная сессия. */
    fun hasActiveSession(): Boolean = mediaController != null

    private fun setupCallback(controller: MediaController) {
        callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                super.onPlaybackStateChanged(state)
                val playing = state?.state == PlaybackState.STATE_PLAYING
                stateListener?.invoke(playing)
            }

            override fun onSessionDestroyed() {
                super.onSessionDestroyed()
                refreshConnection()
            }
        }
        controller.registerCallback(callback!!, handler)
        // Отправляем текущее состояние
        stateListener?.invoke(isPlaying())
    }
}
