package com.yourname.gesturemusic.gesture

/**
 * Prevents accidental media commands by requiring a deliberate activation gesture.
 * Activation is intentionally separate from normal playback gestures.
 */
class GestureArmingManager(
    private val activeTimeoutMs: Long = 15_000L
) {
    var isArmed: Boolean = false
        private set

    private var armedAt: Long = 0L

    fun activate(now: Long): Boolean {
        isArmed = true
        armedAt = now
        return true
    }

    fun deactivate() {
        isArmed = false
        armedAt = 0L
    }

    fun update(now: Long): Boolean {
        if (isArmed && activeTimeoutMs > 0 && now - armedAt >= activeTimeoutMs) {
            deactivate()
        }
        return isArmed
    }

    fun touch(now: Long) {
        if (isArmed) armedAt = now
    }
}
