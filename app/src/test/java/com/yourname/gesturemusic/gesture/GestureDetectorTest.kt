package com.yourname.gesturemusic.gesture

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GestureDetectorTest {
    private lateinit var wristDetector: WristRotationDetector
    private lateinit var pinchDetector: DoublePinchDetector

    @Before
    fun setup() { wristDetector = WristRotationDetector(); pinchDetector = DoublePinchDetector() }

    @Test
    fun wristRotationRightDetectsNextTrack() {
        val baseTime = 1000L
        val samples = listOf(Triple(baseTime+0,0f,0f), Triple(baseTime+50,2.0f,0f), Triple(baseTime+100,3.0f,0f), Triple(baseTime+150,2.5f,0f), Triple(baseTime+200,1.0f,0f), Triple(baseTime+250,0.5f,0f), Triple(baseTime+300,0.2f,0f), Triple(baseTime+350,0.1f,0f), Triple(baseTime+400,0f,0f))
        var result: GestureType? = null
        for ((t,gx,ly) in samples) result = wristDetector.process(t,gx,0f,0f,0f,ly,0f)
        assertEquals(GestureType.NEXT_TRACK, result)
    }

    @Test
    fun wristRotationLeftDetectsPreviousTrack() {
        val baseTime = 1000L
        val samples = listOf(Triple(baseTime+0,0f,0f), Triple(baseTime+50,-2.0f,0f), Triple(baseTime+100,-3.0f,0f), Triple(baseTime+150,-2.5f,0f), Triple(baseTime+200,-1.0f,0f), Triple(baseTime+250,-0.5f,0f), Triple(baseTime+300,-0.2f,0f), Triple(baseTime+350,-0.1f,0f), Triple(baseTime+400,0f,0f))
        var result: GestureType? = null
        for ((t,gx,ly) in samples) result = wristDetector.process(t,gx,0f,0f,0f,ly,0f)
        assertEquals(GestureType.PREVIOUS_TRACK, result)
    }

    @Test
    fun doublePinchDetectsPlayPause() {
        val baseTime = 1000L
        val events = listOf(Pair(baseTime+0,0f), Pair(baseTime+20,4.0f), Pair(baseTime+40,-3.0f), Pair(baseTime+60,0f), Pair(baseTime+200,4.0f), Pair(baseTime+220,-3.0f), Pair(baseTime+240,0f))
        var result: GestureType? = null
        for ((t,lz) in events) result = pinchDetector.process(t,0f,0f,0f,0f,0f,lz)
        assertEquals(GestureType.PLAY_PAUSE, result)
    }

    @Test
    fun noGestureOnIdleData() {
        val baseTime = 1000L
        for (i in 0 until 20) assertNull(wristDetector.process(baseTime+i*50,0.1f,0f,0f,0f,0f,0f))
    }

    @Test
    fun antiNoiseBlocksRunningMotion() {
        val baseTime = 1000L
        val samples = listOf(Triple(baseTime+0,2.0f,20f), Triple(baseTime+50,3.0f,18f), Triple(baseTime+100,2.5f,22f))
        var result: GestureType? = null
        for ((t,gx,ly) in samples) result = wristDetector.process(t,gx,0f,0f,0f,ly,0f)
        assertNull(result)
    }
}
