package com.yourname.gesturemusic.gesture

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/** Five-repetition gesture training with automatic motion start/end detection. */
class GestureTrainer(context: Context) {
    companion object {
        private const val TAG = "GestureTrainer"
        private const val PREFS_NAME = "gesture_trainer"
        private const val KEY_GESTURES = "trained_gestures"
        private const val SAMPLE_SIZE = 90
        private const val MIN_SAMPLES = 18
        private const val TRAINING_REPETITIONS = 5
        private const val DTW_THRESHOLD = 1.15f
        private const val TRAINING_VARIANCE_THRESHOLD = 1.40f
        private const val MIN_MOTION_ENERGY = 0.18f
        private const val START_MOTION_THRESHOLD = 0.45f
        private const val END_MOTION_THRESHOLD = 0.18f
        private const val QUIET_SAMPLES_TO_END = 8
        private const val RECOGNITION_COOLDOWN_MS = 1200L
        // DTW is O(n^2) per trained gesture; running it on every sensor
        // sample (~50 Hz) overloads the main thread once several gestures
        // are trained. ~10 evaluations/sec is plenty responsive.
        private const val EVALUATION_INTERVAL_MS = 100L
    }

    enum class TrainingEvent { NONE, STARTED, REPETITION_ACCEPTED }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private var trainingSession = false
    private var isRecording = false
    private var currentRecording = mutableListOf<Sample>()
    private val repetitions = mutableListOf<List<Sample>>()
    private var quietSamples = 0
    private var previousSample: Sample? = null
    private val trainedGestures = mutableMapOf<GestureType, TrainedGesture>()
    private var lastRecognitionTime = 0L
    private var lastEvaluationTime = 0L

    @Serializable data class Sample(val gx: Float, val gy: Float, val gz: Float, val ax: Float, val ay: Float, val az: Float)
    @Serializable data class TrainedGesture(val gestureType: String, val samples: List<Sample>)

    init { loadGestures() }

    fun startTraining() {
        trainingSession = true
        isRecording = false
        currentRecording.clear()
        repetitions.clear()
        quietSamples = 0
        previousSample = null
        Log.d(TAG, "Automatic 5-repetition training started")
    }

    /** Compatibility entry point: starts a new session if necessary. */
    fun startRecording() {
        if (!trainingSession) startTraining()
    }

    /**
     * Feeds one sensor sample. The trainer automatically detects motion start,
     * records the gesture, and closes it after a short quiet period.
     */
    fun addSample(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float): TrainingEvent {
        if (!trainingSession || repetitions.size >= TRAINING_REPETITIONS) return TrainingEvent.NONE
        val sample = Sample(gx, gy, gz, ax, ay, az)
        val delta = previousSample?.let { distance(it, sample) } ?: 0f
        previousSample = sample

        if (!isRecording) {
            if (delta >= START_MOTION_THRESHOLD) {
                isRecording = true
                currentRecording.clear()
                currentRecording += sample
                quietSamples = 0
                Log.d(TAG, "Gesture motion started: repetition ${repetitions.size + 1}/$TRAINING_REPETITIONS")
                return TrainingEvent.STARTED
            }
            return TrainingEvent.NONE
        }

        if (currentRecording.size < SAMPLE_SIZE) currentRecording += sample
        if (delta < END_MOTION_THRESHOLD) quietSamples++ else quietSamples = 0

        if (quietSamples >= QUIET_SAMPLES_TO_END || currentRecording.size >= SAMPLE_SIZE) {
            return finishAutoRepetition()
        }
        return TrainingEvent.NONE
    }

    private fun finishAutoRepetition(): TrainingEvent {
        val sample = currentRecording.toList()
        currentRecording.clear()
        isRecording = false
        quietSamples = 0
        if (sample.size < MIN_SAMPLES || motionEnergy(sample) < MIN_MOTION_ENERGY) {
            Log.w(TAG, "Rejected automatic repetition: samples=${sample.size}, energy=${motionEnergy(sample)}")
            return TrainingEvent.NONE
        }
        repetitions += normalize(sample)
        Log.d(TAG, "Accepted automatic repetition ${repetitions.size}/$TRAINING_REPETITIONS")
        return TrainingEvent.REPETITION_ACCEPTED
    }

    /** Retained for service compatibility; automatic training does not require it. */
    fun finishRepetition(): Boolean = finishAutoRepetition() == TrainingEvent.REPETITION_ACCEPTED

    fun saveTraining(gestureType: GestureType): Boolean {
        if (repetitions.size < TRAINING_REPETITIONS) return false
        val template = chooseTemplate() ?: run {
            Log.w(TAG, "Training rejected: repetitions are too different")
            return false
        }
        trainedGestures[gestureType] = TrainedGesture(gestureType.name, template)
        saveGestures()
        trainingSession = false
        isRecording = false
        currentRecording.clear()
        repetitions.clear()
        previousSample = null
        return true
    }

    fun cancelTraining() {
        trainingSession = false
        isRecording = false
        currentRecording.clear()
        repetitions.clear()
        previousSample = null
    }

    fun getTrainingRepetitionCount(): Int = repetitions.size
    fun getRequiredRepetitions(): Int = TRAINING_REPETITIONS
    fun getRecordingProgress(): Int = if (!isRecording) 0 else (currentRecording.size * 100 / SAMPLE_SIZE).coerceIn(0, 100)
    fun isCurrentlyRecording(): Boolean = isRecording

    fun recognize(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float): GestureType? {
        if (trainingSession || isRecording || trainedGestures.isEmpty()) return null
        val now = System.currentTimeMillis()
        if (now - lastRecognitionTime < RECOGNITION_COOLDOWN_MS) return null
        currentRecording += Sample(gx, gy, gz, ax, ay, az)
        if (currentRecording.size > SAMPLE_SIZE) currentRecording.removeAt(0)
        if (now - lastEvaluationTime < EVALUATION_INTERVAL_MS) return null
        lastEvaluationTime = now
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
            lastRecognitionTime = System.currentTimeMillis()
            return bestMatch
        }
        // Window full but no match: drop the older half so a stale motion
        // doesn't keep poisoning every subsequent DTW evaluation.
        if (currentRecording.size >= SAMPLE_SIZE) {
            currentRecording.subList(0, SAMPLE_SIZE / 2).clear()
        }
        return null
    }

    fun clearAll() {
        trainedGestures.clear(); currentRecording.clear(); repetitions.clear()
        trainingSession = false; isRecording = false; previousSample = null
        prefs.edit().remove(KEY_GESTURES).apply()
    }

    fun hasTrainedGesture(type: GestureType): Boolean = trainedGestures.containsKey(type)

    private fun chooseTemplate(): List<Sample>? {
        if (repetitions.size < TRAINING_REPETITIONS) return null
        var best = repetitions.first(); var bestScore = Float.MAX_VALUE
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
        fun rms(i: Int, s: (Sample) -> Float): Float { var sum=0.0; input.forEach { val d=s(it)-m[i]; sum+=d*d }; return sqrt(sum/input.size).toFloat().coerceAtLeast(0.001f) }
        val scale=floatArrayOf(rms(0){it.gx},rms(1){it.gy},rms(2){it.gz},rms(3){it.ax},rms(4){it.ay},rms(5){it.az})
        return input.map { Sample((it.gx-m[0])/scale[0],(it.gy-m[1])/scale[1],(it.gz-m[2])/scale[2],(it.ax-m[3])/scale[3],(it.ay-m[4])/scale[4],(it.az-m[5])/scale[5]) }
    }

    private fun distance(a: Sample,b: Sample): Float = sqrt((b.gx-a.gx)*(b.gx-a.gx)+(b.gy-a.gy)*(b.gy-a.gy)+(b.gz-a.gz)*(b.gz-a.gz)+(b.ax-a.ax)*(b.ax-a.ax)+(b.ay-a.ay)*(b.ay-a.ay)+(b.az-a.az)*(b.az-a.az))

    private fun motionEnergy(s: List<Sample>): Float { if(s.size<2)return 0f; var total=0f; for(i in 1 until s.size)total+=distance(s[i-1],s[i]); return total/(s.size-1) }

    private fun dtwDistance(a:List<Sample>,b:List<Sample>):Float { val dp=Array(a.size+1){FloatArray(b.size+1){Float.POSITIVE_INFINITY}};dp[0][0]=0f;for(i in 1..a.size)for(j in 1..b.size){val c=distance(a[i-1],b[j-1]);dp[i][j]=c+minOf(dp[i-1][j],dp[i][j-1],dp[i-1][j-1])};return dp[a.size][b.size]/(a.size+b.size).toFloat() }
    private fun saveGestures()=prefs.edit().putString(KEY_GESTURES,json.encodeToString(trainedGestures.values.toList())).apply()
    private fun loadGestures(){val data=prefs.getString(KEY_GESTURES,null)?:return;try{json.decodeFromString<List<TrainedGesture>>(data).forEach{g->try{trainedGestures[GestureType.valueOf(g.gestureType)]=g}catch(_:IllegalArgumentException){}}}catch(e:Exception){Log.e(TAG,"Failed to load gestures",e)}}
}
