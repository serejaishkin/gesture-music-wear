package com.yourname.gesturemusic.gesture

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

/**
 * Обучаемый детектор жестов.
 *
 * Важное отличие от старой версии:
 * - образец нормализуется перед сравнением;
 * - используется настоящий Dynamic Time Warping (DTW), поэтому жест
 *   можно выполнить немного быстрее или медленнее;
 * - сравнение выполняется по относительной форме движения, а не по
 *   абсолютным значениям датчиков;
 * - добавлена минимальная энергия движения, чтобы лёгкое покачивание
 *   руки не считалось обученным жестом.
 *
 * Для каждого GestureType хранится один обученный шаблон. Это сохраняет
 * совместимость с существующим UI и SharedPreferences проекта.
 */
class GestureTrainer(context: Context) {

    companion object {
        private const val TAG = "GestureTrainer"
        private const val PREFS_NAME = "gesture_trainer"
        private const val KEY_GESTURES = "trained_gestures"

        // Окно примерно 0.6–0.9 секунды при SENSOR_DELAY_GAME.
        private const val SAMPLE_SIZE = 45
        private const val MIN_SAMPLES = 18

        // Чем меньше значение, тем строже совпадение.
        // Значение намеренно небольшое после нормализации.
        private const val DTW_THRESHOLD = 1.15f

        // Минимальная средняя энергия движения. Защищает от
        // распознавания при почти неподвижной руке.
        private const val MIN_MOTION_ENERGY = 0.18f
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private var isRecording = false
    private var currentRecording = mutableListOf<Sample>()
    private val trainedGestures = mutableMapOf<GestureType, TrainedGesture>()

    @Serializable
    data class Sample(
        val gx: Float, val gy: Float, val gz: Float,
        val ax: Float, val ay: Float, val az: Float
    )

    @Serializable
    data class TrainedGesture(
        val gestureType: String,
        val samples: List<Sample>
    )

    init {
        loadGestures()
    }

    /** Начать запись одного обучающего образца. */
    fun startRecording() {
        isRecording = true
        currentRecording.clear()
        Log.d(TAG, "Recording started")
    }

    /** Добавить очередной пакет gyro + linear acceleration. */
    fun addSample(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float
    ) {
        if (!isRecording) return
        if (currentRecording.size >= SAMPLE_SIZE) {
            isRecording = false
            Log.d(TAG, "Recording auto-stopped: max samples reached")
            return
        }
        currentRecording += Sample(gx, gy, gz, ax, ay, az)
    }

    /**
     * Завершить обучение.
     *
     * Перед сохранением убираем постоянную составляющую каждого канала
     * и масштабируем его. Благодаря этому положение руки и сила конкретного
     * движения меньше влияют на результат.
     */
    fun stopRecording(gestureType: GestureType): Boolean {
        isRecording = false
        if (currentRecording.size < MIN_SAMPLES) {
            Log.w(TAG, "Recording too short: ${currentRecording.size} samples")
            currentRecording.clear()
            return false
        }

        val normalized = normalize(currentRecording)
        val gesture = TrainedGesture(gestureType.name, normalized)
        trainedGestures[gestureType] = gesture
        saveGestures()
        currentRecording.clear()

        Log.d(TAG, "Saved DTW template: $gestureType, ${normalized.size} samples")
        return true
    }

    /**
     * Проверить текущее скользящее окно на обученный жест.
     *
     * Возвращается только действительно хорошее совпадение. Если движение
     * слабое или расстояние DTW выше порога — возвращаем null.
     */
    fun recognize(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float
    ): GestureType? {
        if (isRecording || trainedGestures.isEmpty()) return null

        currentRecording += Sample(gx, gy, gz, ax, ay, az)
        if (currentRecording.size > SAMPLE_SIZE) {
            currentRecording.removeAt(0)
        }
        if (currentRecording.size < MIN_SAMPLES) return null

        // Сначала проверяем, есть ли вообще заметное движение.
        val energy = motionEnergy(currentRecording)
        if (energy < MIN_MOTION_ENERGY) return null

        val candidate = normalize(currentRecording)

        var bestMatch: GestureType? = null
        var bestScore = Float.MAX_VALUE

        for ((type, trained) in trainedGestures) {
            val score = dtwDistance(candidate, trained.samples)
            if (score < bestScore) {
                bestScore = score
                bestMatch = type
            }
        }

        if (bestMatch != null && bestScore <= DTW_THRESHOLD) {
            Log.d(TAG, "Recognized learned gesture: $bestMatch, DTW=$bestScore")
            // После совпадения начинаем новое окно, чтобы один жест
            // не срабатывал несколько раз подряд.
            currentRecording.clear()
            return bestMatch
        }

        return null
    }

    fun clearAll() {
        trainedGestures.clear()
        currentRecording.clear()
        prefs.edit().remove(KEY_GESTURES).apply()
        Log.d(TAG, "All gestures cleared")
    }

    fun hasTrainedGesture(type: GestureType): Boolean = trainedGestures.containsKey(type)

    fun isCurrentlyRecording(): Boolean = isRecording

    fun getRecordingProgress(): Int =
        (currentRecording.size * 100 / SAMPLE_SIZE).coerceIn(0, 100)

    /** Центрирование + RMS-нормализация шести сенсорных каналов. */
    private fun normalize(input: List<Sample>): List<Sample> {
        if (input.isEmpty()) return emptyList()

        fun mean(selector: (Sample) -> Float): Float =
            input.sumOf { selector(it).toDouble() }.toFloat() / input.size

        val means = floatArrayOf(
            mean { it.gx }, mean { it.gy }, mean { it.gz },
            mean { it.ax }, mean { it.ay }, mean { it.az }
        )

        fun rms(index: Int, selector: (Sample) -> Float): Float {
            var sum = 0.0
            for (s in input) {
                val d = selector(s) - means[index]
                sum += d * d
            }
            return sqrt(sum / input.size).toFloat().coerceAtLeast(0.001f)
        }

        val scales = floatArrayOf(
            rms(0) { it.gx }, rms(1) { it.gy }, rms(2) { it.gz },
            rms(3) { it.ax }, rms(4) { it.ay }, rms(5) { it.az }
        )

        return input.map {
            Sample(
                (it.gx - means[0]) / scales[0],
                (it.gy - means[1]) / scales[1],
                (it.gz - means[2]) / scales[2],
                (it.ax - means[3]) / scales[3],
                (it.ay - means[4]) / scales[4],
                (it.az - means[5]) / scales[5]
            )
        }
    }

    /** Средняя величина движения по шести каналам. */
    private fun motionEnergy(samples: List<Sample>): Float {
        if (samples.size < 2) return 0f
        var sum = 0f
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            sum += absMagnitude(
                b.gx - a.gx, b.gy - a.gy, b.gz - a.gz,
                b.ax - a.ax, b.ay - a.ay, b.az - a.az
            )
        }
        return sum / (samples.size - 1)
    }

    private fun absMagnitude(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float
    ): Float = sqrt(gx * gx + gy * gy + gz * gz + ax * ax + ay * ay + az * az)

    /**
     * Настоящий DTW: допускает растяжение/сжатие времени движения.
     */
    private fun dtwDistance(a: List<Sample>, b: List<Sample>): Float {
        if (a.isEmpty() || b.isEmpty()) return Float.MAX_VALUE

        val dp = Array(a.size + 1) {
            FloatArray(b.size + 1) { Float.POSITIVE_INFINITY }
        }
        dp[0][0] = 0f

        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val cost = sampleDistance(a[i - 1], b[j - 1])
                dp[i][j] = cost + minOf(
                    dp[i - 1][j],
                    dp[i][j - 1],
                    dp[i - 1][j - 1]
                )
            }
        }

        return dp[a.size][b.size] / (a.size + b.size).toFloat()
    }

    private fun sampleDistance(a: Sample, b: Sample): Float =
        absMagnitude(
            a.gx - b.gx, a.gy - b.gy, a.gz - b.gz,
            a.ax - b.ax, a.ay - b.ay, a.az - b.az
        )

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
                } catch (_: IllegalArgumentException) {
                    Log.w(TAG, "Unknown stored gesture: ${g.gestureType}")
                }
            }
            Log.d(TAG, "Loaded ${trainedGestures.size} trained gestures")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load gestures", e)
        }
    }
}
