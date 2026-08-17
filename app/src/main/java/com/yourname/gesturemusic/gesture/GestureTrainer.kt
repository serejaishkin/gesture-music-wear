package com.yourname.gesturemusic.gesture

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Обучаемый детектор жестов.
 *
 * Позволяет записать образец жеста (набор сенсорных данных) и потом
 * распознавать похожие жесты через DTW (Dynamic Time Warping) или
 * простое евклидово расстояние.
 */
class GestureTrainer(context: Context) {

    companion object {
        private const val TAG = "GestureTrainer"
        private const val PREFS_NAME = "gesture_trainer"
        private const val KEY_GESTURES = "trained_gestures"
        private const val SAMPLE_SIZE = 30        // количество сэмплов в жесте (~600мс при 50Гц)
        private const val SIMILARITY_THRESHOLD = 2.5f  // порог похожести (чем меньше, тем строже)
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private var isRecording = false
    private var currentRecording = mutableListOf<Sample>()
    private var trainedGestures = mutableMapOf<GestureType, TrainedGesture>()

    @Serializable
    data class Sample(val gx: Float, val gy: Float, val gz: Float,
                      val ax: Float, val ay: Float, val az: Float)

    @Serializable
    data class TrainedGesture(val gestureType: String, val samples: List<Sample>)

    init {
        loadGestures()
    }

    /** Начать запись образца жеста */
    fun startRecording() {
        isRecording = true
        currentRecording.clear()
        Log.d(TAG, "Recording started")
    }

    /** Добавить сэмпл в текущую запись */
    fun addSample(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float) {
        if (!isRecording) return
        if (currentRecording.size >= SAMPLE_SIZE) {
            isRecording = false
            Log.d(TAG, "Recording auto-stopped: max samples reached")
            return
        }
        currentRecording.add(Sample(gx, gy, gz, ax, ay, az))
    }

    /** Остановить запись и сохранить жест */
    fun stopRecording(gestureType: GestureType): Boolean {
        isRecording = false
        if (currentRecording.size < 10) {
            Log.w(TAG, "Recording too short: ${currentRecording.size} samples")
            return false
        }

        val gesture = TrainedGesture(gestureType.name, currentRecording.toList())
        trainedGestures[gestureType] = gesture
        saveGestures()
        Log.d(TAG, "Saved gesture: $gestureType with ${currentRecording.size} samples")
        return true
    }

    /** Распознать жест по текущим сэмплам */
    fun recognize(gx: Float, gy: Float, gz: Float, ax: Float, ay: Float, az: Float): GestureType? {
        if (trainedGestures.isEmpty()) return null

        // Добавляем сэмпл в скользящее окно
        currentRecording.add(Sample(gx, gy, gz, ax, ay, az))
        if (currentRecording.size > SAMPLE_SIZE) {
            currentRecording.removeAt(0)
        }
        if (currentRecording.size < 10) return null

        // Ищем наиболее похожий обученный жест
        var bestMatch: GestureType? = null
        var bestScore = Float.MAX_VALUE

        for ((type, trained) in trainedGestures) {
            val score = calculateDistance(currentRecording, trained.samples)
            Log.d(TAG, "Score for $type: $score")
            if (score < bestScore) {
                bestScore = score
                bestMatch = type
            }
        }

        return if (bestScore < SIMILARITY_THRESHOLD * currentRecording.size) {
            Log.d(TAG, "Recognized: $bestMatch (score=$bestScore)")
            bestMatch
        } else {
            Log.d(TAG, "No match (best score=$bestScore)")
            null
        }
    }

    /** Очистить все обученные жесты */
    fun clearAll() {
        trainedGestures.clear()
        prefs.edit().remove(KEY_GESTURES).apply()
        Log.d(TAG, "All gestures cleared")
    }

    fun hasTrainedGesture(type: GestureType): Boolean = trainedGestures.containsKey(type)

    fun isCurrentlyRecording(): Boolean = isRecording

    fun getRecordingProgress(): Int = (currentRecording.size * 100) / SAMPLE_SIZE

    private fun calculateDistance(a: List<Sample>, b: List<Sample>): Float {
        val len = minOf(a.size, b.size)
        if (len == 0) return Float.MAX_VALUE

        var sum = 0f
        for (i in 0 until len) {
            val dx = a[i].gx - b[i].gx
            val dy = a[i].gy - b[i].gy
            val dz = a[i].gz - b[i].gz
            val dax = a[i].ax - b[i].ax
            val day = a[i].ay - b[i].ay
            val daz = a[i].az - b[i].az
            sum += sqrt(dx*dx + dy*dy + dz*dz + dax*dax + day*day + daz*daz)
        }
        return sum / len
    }

    private fun saveGestures() {
        val data = json.encodeToString(trainedGestures.values.toList())
        prefs.edit().putString(KEY_GESTURES, data).apply()
    }

    private fun loadGestures() {
        val data = prefs.getString(KEY_GESTURES, null) ?: return
        try {
            val list = json.decodeFromString<List<TrainedGesture>>(data)
            trainedGestures.clear()
            for (g in list) {
                try {
                    trainedGestures[GestureType.valueOf(g.gestureType)] = g
                } catch (_: IllegalArgumentException) {}
            }
            Log.d(TAG, "Loaded ${trainedGestures.size} gestures")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load gestures: ${e.message}")
        }
    }
}
