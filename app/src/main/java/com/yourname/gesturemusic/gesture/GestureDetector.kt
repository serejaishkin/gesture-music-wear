package com.yourname.gesturemusic.gesture

interface GestureDetector {
    fun process(
        timestamp: Long,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        linAccX: Float, linAccY: Float, linAccZ: Float
    ): GestureType?

    fun reset()
}
