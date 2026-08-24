package com.yourname.gesturemusic.gesture

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/**
 * Five-repetition gesture training with DTW validation.
 *
 * A training session consists of five independently recorded repetitions.
 * startTraining() starts a new session; startRecording() starts only the next
 * repetition and deliberately does NOT erase repetitions already accepted.
 */
class GestureTrainer(context: Context) {
    companion object {
        private const val TAG = "GestureTrainer"
        private const val PREFS_NAME = "gesture_trainer"
        private const val KEY_GESTURES = "trained_gestures"
        private const val SAMPLE_SIZE = 45
        private const val MIN_SAMPLES = 18
        private const val TRAINING_REPETITIONS = 5
        private const val DTW_THRESHOLD = 1.15f
        private const val TRAINING_VARIANCE_THRESHOLD = 1.40f
        private const val MIN_MOTION_ENERGY = 0.18f
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var isRecording = false
    private var currentRecording = mutableListOf<Sample>()
    private val repetitions = mutableListOf<List<Sample>>()
    private val trainedGestures = mutableMapOf<GestureType, TrainedGesture>()

    @Serializable data class Sample(val gx: Float, val gy: Float, val gz: Float, val ax: Float, val ay: Float, val az: Float)
    @Serializable data class TrainedGesture(val gestureType: String, val samples: List<Sample>)

    init { loadGestures() }

    fun startTraining() {
        repetitions.clear()
        currentRecording.clear()
        isRecording = true
        Log.d(TAG, "New 5-repetition training session")
    }

    /** Starts recording one repetition without clearing previous accepted repetitions. */
    fun startRecording() {
        isRecording = true
        currentRecording.clear()
        Log.d(TAG, "Recording repetition ${repetitions.size + 1}/$TRAINING_REPETITIONS")
    }

    fun addSample(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float) {
        if (!isRecording || currentRecording.size >= SAMPLE_SIZE) return
        currentRecording += Sample(gx, gy, gz, ax, ay, az)
    }

    /** Finishes the current repetition and returns whether it contains enough motion. */
    fun finishRepetition(): Boolean {
        if (!isRecording) return false
        isRecording = false
        val sample = currentRecording.toList()
        currentRecording.clear()
        if (sample.size < MIN_SAMPLES || motionEnergy(sample) < MIN_MOTION_ENERGY) {
            Log.w(TAG, "Rejected repetition: samples=${sample.size}, energy=${motionEnergy(sample)}")
            return false
        }
        repetitions += normalize(sample)
        Log.d(TAG, "Accepted repetition ${repetitions.size}/$TRAINING_REPETITIONS")
        return true
    }

    /** Saves the five accepted repetitions after DTW consistency validation. */
    fun saveTraining(gestureType: GestureType): Boolean {
        if (repetitions.size < TRAINING_REPETITIONS) return false
        val template = chooseTemplate() ?: run {
            Log.w(TAG, "Training rejected: repetitions are too different")
            return false
        }
        trainedGestures[gestureType] = TrainedGesture(gestureType.name, template)
        saveGestures()
        repetitions.clear()
        currentRecording.clear()
        isRecording = false
        Log.d(TAG, "Saved validated 5-repetition template: $gestureType")
        return true
    }

    fun cancelTraining() {
        isRecording = false
        currentRecording.clear()
        repetitions.clear()
    }

    fun getTrainingRepetitionCount(): Int = repetitions.size
    fun getRequiredRepetitions(): Int = TRAINING_REPETITIONS
    fun getRecordingProgress(): Int = (currentRecording.size * 100 / SAMPLE_SIZE).coerceIn(0, 100)

    fun recognize(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float): GestureType? {
        if (isRecording || trainedGestures.isEmpty()) return null
        currentRecording += Sample(gx, gy, gz, ax, ay, az)
        if (currentRecording.size > SAMPLE_SIZE) currentRecording.removeAt(0)
        if (currentRecording.size < MIN_SAMPLES || motionEnergy(currentRecording) < MIN_MOTION_ENERGY) return null
        val candidate = normalize(currentRecording)
        var bestMatch: GestureType? = null
        var bestScore = Float.MAX_VALUE
        for ((type, trained) in trainedGestures) {
            val score = dtwDistance(candidate, trained.samples)
            if (score < bestScore) { bestScore = score; bestMatch = type }
        }
        if (bestMatch != null && bestScore <= DTW_THRESHOLD) {
            currentRecording.clear()
            Log.d(TAG, "Recognized learned gesture: $bestMatch DTW=$bestScore")
            return bestMatch
        }
        return null
    }

    fun clearAll() {
        trainedGestures.clear(); currentRecording.clear(); repetitions.clear(); isRecording = false
        prefs.edit().remove(KEY_GESTURES).apply()
    }

    fun hasTrainedGesture(type: GestureType): Boolean = trainedGestures.containsKey(type)
    fun isCurrentlyRecording(): Boolean = isRecording

    private fun chooseTemplate(): List<Sample>? {
        if (repetitions.size < TRAINING_REPETITIONS) return null
        var best = repetitions.first()
        var bestScore = Float.MAX_VALUE
        for (candidate in repetitions) {
            val score = repetitions.sumOf { dtwDistance(candidate, it).toDouble() }.toFloat() / repetitions.size
            if (score < bestScore) { bestScore = score; best = candidate }
        }
        return if (bestScore <= TRAINING_VARIANCE_THRESHOLD) best else null
    }

    private fun normalize(input: List<Sample>): List<Sample> {
        if (input.isEmpty()) return emptyList()
        fun mean(s: (Sample) -> Float) = input.sumOf { s(it).toDouble() }.toFloat() / input.size
        val m = floatArrayOf(mean { it.gx }, mean { it.gy }, mean { it.gz }, mean { it.ax }, mean { it.ay }, mean { it.az })
        fun rms(i: Int, s: (Sample) -> Float): Float {
            var sum = 0.0
            input.forEach { val d = s(it) - m[i]; sum += d * d }
            return sqrt(sum / input.size).toFloat().coerceAtLeast(0.001f)
        }
        val scale = floatArrayOf(rms(0){it.gx}, rms(1){it.gy}, rms(2){it.gz}, rms(3){it.ax}, rms(4){it.ay}, rms(5){it.az})
        return input.map { Sample((it.gx-m[0])/scale[0], (it.gy-m[1])/scale[1], (it.gz-m[2])/scale[2], (it.ax-m[3])/scale[3], (it.ay-m[4])/scale[4], (it.az-m[5])/scale[5]) }
    }

    private fun motionEnergy(s: List<Sample>): Float {
        if (s.size < 2) return 0f
        var total = 0f
        for (i in 1 until s.size) {
            val a = s[i-1]; val b = s[i]
            total += sqrt((b.gx-a.gx)*(b.gx-a.gx)+(b.gy-a.gy)*(b.gy-a.gy)+(b.gz-a.gz)*(b.gz-a.gz)+(b.ax-a.ax)*(b.ax-a.ax)+(b.ay-a.ay)*(b.ay-a.ay)+(b.az-a.az)*(b.az-a.az))
        }
        return total / (s.size - 1)
    }

    private fun dtwDistance(a: List<Sample>, b: List<Sample>): Float {
        val dp = Array(a.size + 1) { FloatArray(b.size + 1) { Float.POSITIVE_INFINITY } }
        dp[0][0] = 0f
        for (i in 1..a.size) for (j in 1..b.size) {
            val x=a[i-1]; val y=b[j-1]
            val cost=sqrt((x.gx-y.gx)*(x.gx-y.gx)+(x.gy-y.gy)*(x.gy-y.gy)+(x.gz-y.gz)*(x.gz-y.gz)+(x.ax-y.ax)*(x.ax-y.ax)+(x.ay-y.ay)*(x.ay-y.ay)+(x.az-y.az)*(x.az-y.az))
            dp[i][j]=cost+minOf(dp[i-1][j],dp[i][j-1],dp[i-1][j-1])
        }
        return dp[a.size][b.size]/(a.size+b.size).toFloat()
    }

    private fun saveGestures() = prefs.edit().putString(KEY_GESTURES, json.encodeToString(trainedGestures.values.toList())).apply()

    private fun loadGestures() {
        val data=prefs.getString(KEY_GESTURES,null) ?: return
        try { json.decodeFromString<List<TrainedGesture>>(data).forEach { g -> try { trainedGestures[GestureType.valueOf(g.gestureType)] = g } catch (_: IllegalArgumentException) {} } }
        catch (e: Exception) { Log.e(TAG,"Failed to load gestures",e) }
    }
}
