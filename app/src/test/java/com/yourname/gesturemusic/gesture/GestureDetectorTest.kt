package com.yourname.gesturemusic.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GestureDetectorTest {

    private lateinit var wristDetector: WristRotationDetector
    private lateinit var pinchDetector: DoublePinchDetector

    @Before
    fun setup() {
        wristDetector = WristRotationDetector()
        pinchDetector = DoublePinchDetector()
    }

    @Test
    fun `wrist rotation right detects next track`() {
        val baseTime = 1000L
        // Симулируем поворот вправо: положительный gyroX
        val samples = listOf(
            Triple(baseTime + 0, 0f, 0f),
            Triple(baseTime + 50, 2.0f, 0f),
            Triple(baseTime + 100, 3.0f, 0f),
            Triple(baseTime + 150, 2.5f, 0f),
            Triple(baseTime + 200, 1.0f, 0f),
            Triple(baseTime + 250, 0.5f, 0f),
            Triple(baseTime + 300, 0.2f, 0f),
            Triple(baseTime + 350, 0.1f, 0f),
            Triple(baseTime + 400, 0f, 0f)
        )

        var result: GestureType? = null
        for ((t, gx, ly) in samples) {
            result = wristDetector.process(t, gx, 0f, 0f, 0f, ly, 0f)
        }

        assertEquals(GestureType.NEXT_TRACK, result)
    }

    @Test
    fun `wrist rotation left detects previous track`() {
        val baseTime = 1000L
        val samples = listOf(
            Triple(baseTime + 0, 0f, 0f),
            Triple(baseTime + 50, -2.0f, 0f),
            Triple(baseTime + 100, -3.0f, 0f),
            Triple(baseTime + 150, -2.5f, 0f),
            Triple(baseTime + 200, -1.0f, 0f),
            Triple(baseTime + 250, -0.5f, 0f),
            Triple(baseTime + 300, -0.2f, 0f),
            Triple(baseTime + 350, -0.1f, 0f),
            Triple(baseTime + 400, 0f, 0f)
        )

        var result: GestureType? = null
        for ((t, gx, ly) in samples) {
            result = wristDetector.process(t, gx, 0f, 0f, 0f, ly, 0f)
        }

        assertEquals(GestureType.PREVIOUS_TRACK, result)
    }

    @Test
    fun `double pinch detects play pause`() {
        val baseTime = 1000L
        // Два щипка: z > 3.0, затем z < -2.0
        val events = listOf(
            // Первый щипок
            Pair(baseTime + 0, 0f),
            Pair(baseTime + 20, 4.0f),
            Pair(baseTime + 40, -3.0f),
            Pair(baseTime + 60, 0f),
            // Второй щипок
            Pair(baseTime + 200, 4.0f),
            Pair(baseTime + 220, -3.0f),
            Pair(baseTime + 240, 0f)
        )

        var result: GestureType? = null
        for ((t, lz) in events) {
            result = pinchDetector.process(t, 0f, 0f, 0f, 0f, 0f, lz)
        }

        assertEquals(GestureType.PLAY_PAUSE, result)
    }

    @Test
    fun `no gesture on idle data`() {
        val baseTime = 1000L
        for (i in 0 until 20) {
            val result = wristDetector.process(
                baseTime + i * 50,
                0.1f, 0f, 0f,
                0f, 0f, 0f
            )
            assertNull(result)
        }
    }

    @Test
    fun `anti noise blocks running motion`() {
        val baseTime = 1000L
        val samples = listOf(
            Triple(baseTime + 0, 2.0f, 20f),   // high accY
            Triple(baseTime + 50, 3.0f, 18f),
            Triple(baseTime + 100, 2.5f, 22f)
        )

        var result: GestureType? = null
        for ((t, gx, ly) in samples) {
            result = wristDetector.process(t, gx, 0f, 0f, 0f, ly, 0f)
        }

        assertNull(result)
    }
}
