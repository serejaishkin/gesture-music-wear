package com.yourname.gesturemusic.gesture

/**
 * Базовый интерфейс для всех детекторов жестов.
 */
interface GestureDetector {
    /** Обработка нового события сенсора. Возвращает распознанный жест или null. */
    fun process(timestamp: Long, gyroX: Float, gyroY: Float, gyroZ: Float,
                linAccX: Float, linAccY: Float, linAccZ: Float): GestureType?

    /** Сброс состояния детектора. */
    fun reset()
}

enum class GestureType {
    NEXT_TRACK,
    PREVIOUS_TRACK,
    PLAY_PAUSE
}
