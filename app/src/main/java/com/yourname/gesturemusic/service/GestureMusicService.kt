package com.yourname.gesturemusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.gesturemusic.MainActivity
import com.yourname.gesturemusic.R
import com.yourname.gesturemusic.gesture.DoublePinchDetector
import com.yourname.gesturemusic.gesture.GestureArmingManager
import com.yourname.gesturemusic.gesture.GestureTrainer
import com.yourname.gesturemusic.gesture.GestureType
import com.yourname.gesturemusic.gesture.WristRotationDetector
import com.yourname.gesturemusic.media.MediaControllerManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GestureMusicService : Service(), SensorEventListener {
    companion object {
        private const val TAG="GestureMusicService"; private const val CHANNEL_ID="gesture_music_channel"; private const val NOTIFICATION_ID=1
        private const val IDLE_TIMEOUT_MS=30000L; private const val GESTURE_COOLDOWN_MS=1000L; private const val SCREEN_OFF_BLOCK_MS=800L
        const val ACTION_START="com.yourname.gesturemusic.ACTION_START"; const val ACTION_STOP="com.yourname.gesturemusic.ACTION_STOP"
        const val ACTION_UPDATE_SENSITIVITY="com.yourname.gesturemusic.ACTION_UPDATE_SENSITIVITY"; const val ACTION_START_TRAINING="com.yourname.gesturemusic.ACTION_START_TRAINING"
        const val ACTION_STOP_TRAINING="com.yourname.gesturemusic.ACTION_STOP_TRAINING"; const val ACTION_CLEAR_TRAINING="com.yourname.gesturemusic.ACTION_CLEAR_TRAINING"
        const val EXTRA_ANGLE_THRESHOLD="angle_threshold"; const val EXTRA_PINCH_THRESHOLD="pinch_threshold"; const val EXTRA_COOLDOWN="gesture_cooldown"; const val EXTRA_LEFT_HAND="left_hand"; const val EXTRA_TRAINING_GESTURE="training_gesture"
<<<<<<< HEAD
        const val EXTRA_MIN_DURATION="min_duration"; const val EXTRA_MAX_DURATION="max_duration"
=======
>>>>>>> a28610485209baa8884a82ddbaef253d63caeba3
        const val ACTION_TRAINING_PROGRESS="com.yourname.gesturemusic.TRAINING_PROGRESS"; const val EXTRA_TRAINING_PROGRESS="training_progress"; const val EXTRA_TRAINING_REPETITIONS="training_repetitions"; const val EXTRA_TRAINING_DONE="training_done"; const val EXTRA_TRAINING_SUCCESS="training_success"
    }
    private lateinit var sensorManager:SensorManager; private var gyroscope:Sensor?=null; private var linearAccel:Sensor?=null; private lateinit var mediaControllerManager:MediaControllerManager
    private lateinit var wristDetector:WristRotationDetector; private lateinit var pinchDetector:DoublePinchDetector; private lateinit var gestureTrainer:GestureTrainer; private lateinit var armingManager:GestureArmingManager
    private lateinit var wakeLock:PowerManager.WakeLock; private lateinit var vibrator:Vibrator; private lateinit var notificationManager:NotificationManager
    private var isRunning=false; private var isMusicPlaying=false; private var lastGestureTime=0L; private var screenOffTime=-1L; private var isTrainingMode=false; private var trainingGestureType:GestureType?=null; private var lastTrainingProgress=-1
    private var idleExecutor:ScheduledExecutorService?=null; private var idleTask:java.util.concurrent.ScheduledFuture<*>?=null
    private var lastGyroX=0f; private var lastGyroY=0f; private var lastGyroZ=0f; private var lastLinAccX=0f; private var lastLinAccY=0f; private var lastLinAccZ=0f
    private val screenStateReceiver=object:BroadcastReceiver(){override fun onReceive(context:Context?,intent:Intent?){when(intent?.action){Intent.ACTION_SCREEN_OFF->screenOffTime=System.currentTimeMillis();Intent.ACTION_SCREEN_ON->screenOffTime=-1L}}}

    override fun onCreate(){super.onCreate();sensorManager=getSystemService(Context.SENSOR_SERVICE) as SensorManager;gyroscope=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);linearAccel=sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);mediaControllerManager=MediaControllerManager(this);wristDetector=WristRotationDetector();pinchDetector=DoublePinchDetector();gestureTrainer=GestureTrainer(this);armingManager=GestureArmingManager();val pm=getSystemService(Context.POWER_SERVICE) as PowerManager;wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GestureMusic::WakeLock");wakeLock.setReferenceCounted(false);vibrator=getSystemService(Context.VIBRATOR_SERVICE) as Vibrator;notificationManager=getSystemService(NotificationManager::class.java);mediaControllerManager.setStateListener{playing->isMusicPlaying=playing;if(playing)cancelIdleTimer()else startIdleTimer()};val f=IntentFilter().apply{addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON)};if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)registerReceiver(screenStateReceiver,f,Context.RECEIVER_EXPORTED)else registerReceiver(screenStateReceiver,f);createNotificationChannel()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{when(intent?.action){ACTION_START->startGestureDetection();ACTION_STOP->stopGestureDetection();ACTION_UPDATE_SENSITIVITY->updateSensitivity(intent);ACTION_START_TRAINING->startTraining(intent);ACTION_STOP_TRAINING->cancelTraining();ACTION_CLEAR_TRAINING->clearTraining()};return START_STICKY}
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onDestroy(){stopGestureDetection();try{unregisterReceiver(screenStateReceiver)}catch(_:Exception){};try{mediaControllerManager.disconnect()}catch(_:Exception){};idleExecutor?.shutdown();super.onDestroy()}
    override fun onTaskRemoved(rootIntent:Intent?){startService(Intent(applicationContext,GestureMusicService::class.java).apply{action=ACTION_START});super.onTaskRemoved(rootIntent)}

    override fun onSensorChanged(event:SensorEvent?){event?:return;val timestamp=System.currentTimeMillis();when(event.sensor.type){Sensor.TYPE_GYROSCOPE->{lastGyroX=event.values[0];lastGyroY=event.values[1];lastGyroZ=event.values[2]};Sensor.TYPE_LINEAR_ACCELERATION->{lastLinAccX=event.values[0];lastLinAccY=event.values[1];lastLinAccZ=event.values[2];if(isTrainingMode){val e=gestureTrainer.addSample(lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ);val p=gestureTrainer.getRecordingProgress();if(p!=lastTrainingProgress){lastTrainingProgress=p;sendTrainingProgress(p,gestureTrainer.getTrainingRepetitionCount(),false,false)};if(e==GestureTrainer.TrainingEvent.REPETITION_ACCEPTED)onTrainingRepetitionAccepted()}else processGestures(timestamp)}}}
    override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int){}

<<<<<<< HEAD
    private fun processGestures(timestamp:Long){
        if(screenOffTime>0&&timestamp-screenOffTime<SCREEN_OFF_BLOCK_MS)return
        if(timestamp-lastGestureTime<GESTURE_COOLDOWN_MS)return
        armingManager.update(timestamp)
        val learned=gestureTrainer.recognize(lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ)
        if(learned==GestureType.ACTIVATE){
            armingManager.activate(timestamp)
            lastGestureTime=timestamp
            vibrateLong()
            sendGestureBroadcast(learned)
            return
        }
        // FIX: block only learned gestures when not armed, classic detectors always work
        val effectiveLearned=if(gestureTrainer.hasTrainedGesture(GestureType.ACTIVATE)&&!armingManager.isArmed)null else learned
        val gesture=effectiveLearned?:run{val w=wristDetector.process(timestamp,lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ);val p=pinchDetector.process(timestamp,lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ);w?:p}
        gesture?.let{armingManager.touch(timestamp);executeGesture(it,timestamp)}
    }
    private fun executeGesture(gesture:GestureType,timestamp:Long=System.currentTimeMillis()){
        if(gesture==GestureType.ACTIVATE){
            armingManager.activate(timestamp)
            lastGestureTime=timestamp
            vibrateLong()
            sendGestureBroadcast(gesture)
            return
        }
        lastGestureTime=timestamp
        vibrate()
        when(gesture){
            GestureType.NEXT_TRACK->mediaControllerManager.nextTrack()
            GestureType.PREVIOUS_TRACK->mediaControllerManager.previousTrack()
            GestureType.PLAY_PAUSE->mediaControllerManager.playPause()
            GestureType.ACTIVATE->Unit
        }
        sendGestureBroadcast(gesture)
    }

    private fun startTraining(intent:Intent){
        val name=intent.getStringExtra(EXTRA_TRAINING_GESTURE)?:return
        trainingGestureType=try{GestureType.valueOf(name)}catch(_:IllegalArgumentException){return}
        if(!isRunning)startGestureDetection()
        ensureSensorsRegistered()
        gestureTrainer.startTraining()
        isTrainingMode=true
        lastTrainingProgress=0
        sendTrainingProgress(0,0,false,false)
        vibrate()
    }
    private fun onTrainingRepetitionAccepted(){
        val count=gestureTrainer.getTrainingRepetitionCount()
        sendTrainingProgress(100,count,false,false)
        vibrate()
        if(count>=gestureTrainer.getRequiredRepetitions()){
            val type=trainingGestureType?:return
            val success=gestureTrainer.saveTraining(type)
            isTrainingMode=false
            trainingGestureType=null
            sendTrainingProgress(100,count,true,success)
            if(success)vibrateLong()
        }
    }
    private fun cancelTraining(){
        isTrainingMode=false
        trainingGestureType=null
        gestureTrainer.cancelTraining()
        sendTrainingProgress(0,gestureTrainer.getTrainingRepetitionCount(),true,false)
    }
    private fun clearTraining(){
        gestureTrainer.clearAll()
        armingManager.deactivate()
        trainingGestureType=null
        isTrainingMode=false
        sendTrainingProgress(0,0,true,true)
    }
    private fun ensureSensorsRegistered(){
        gyroscope?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        linearAccel?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)}
        if(!wakeLock.isHeld)wakeLock.acquire(10*60*1000L)
    }
    private fun sendTrainingProgress(progress:Int,repetitions:Int,done:Boolean,success:Boolean){
        sendBroadcast(Intent(ACTION_TRAINING_PROGRESS).apply{
            putExtra(EXTRA_TRAINING_PROGRESS,progress)
            putExtra(EXTRA_TRAINING_REPETITIONS,repetitions)
            putExtra(EXTRA_TRAINING_DONE,done)
            putExtra(EXTRA_TRAINING_SUCCESS,success)
        })
    }
    private fun sendGestureBroadcast(gesture:GestureType)=sendBroadcast(Intent("com.yourname.gesturemusic.GESTURE_DETECTED").apply{putExtra("gesture",gesture.name)})

    private fun startGestureDetection(){
        if(isRunning)return
        isRunning=true
        lastGestureTime=0L
        screenOffTime=-1L
        try{mediaControllerManager.connect()}catch(e:Exception){Log.e(TAG,"MediaController connect failed",e)}
        ensureSensorsRegistered()
        startForeground(NOTIFICATION_ID,buildNotification())
    }
    private fun stopGestureDetection(){
        if(!isRunning)return
        isRunning=false
        sensorManager.unregisterListener(this)
        if(wakeLock.isHeld)wakeLock.release()
        wristDetector.reset()
        pinchDetector.reset()
        armingManager.deactivate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun updateSensitivity(intent:Intent){
        val angle=intent.getFloatExtra(EXTRA_ANGLE_THRESHOLD,28f)
        val pinch=intent.getFloatExtra(EXTRA_PINCH_THRESHOLD,3.5f)
        val cooldown=intent.getLongExtra(EXTRA_COOLDOWN,1200L)
        val left=intent.getBooleanExtra(EXTRA_LEFT_HAND,false)
        val minDur=intent.getLongExtra(EXTRA_MIN_DURATION,160L)
        val maxDur=intent.getLongExtra(EXTRA_MAX_DURATION,600L)
        wristDetector=WristRotationDetector(
            angleThresholdDegrees=angle,
            cooldownMs=cooldown,
            leftHand=left,
            minDurationMs=minDur,
            maxDurationMs=maxDur
        )
        pinchDetector=DoublePinchDetector(thresholdUp=pinch,thresholdDown=-(pinch*0.8f),cooldownMs=cooldown)
        Log.d(TAG,"Sensitivity updated: angle=\$angle, pinch=\$pinch, cooldown=\$cooldown, left=\$left, min=\$minDur, max=\$maxDur")
    }
    private fun startIdleTimer(){
        cancelIdleTimer()
        idleExecutor=idleExecutor?:Executors.newSingleThreadScheduledExecutor()
        idleTask=idleExecutor?.schedule({if(!isMusicPlaying&&!isTrainingMode){sensorManager.unregisterListener(this@GestureMusicService);if(wakeLock.isHeld)wakeLock.release()}},IDLE_TIMEOUT_MS,TimeUnit.MILLISECONDS)
    }
    private fun cancelIdleTimer(){
        idleTask?.cancel(false)
        idleTask=null
        if(isRunning)ensureSensorsRegistered()
    }
=======
    private fun processGestures(timestamp:Long){if(screenOffTime>0&&timestamp-screenOffTime<SCREEN_OFF_BLOCK_MS)return;if(timestamp-lastGestureTime<GESTURE_COOLDOWN_MS)return;armingManager.update(timestamp);val learned=gestureTrainer.recognize(lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ);if(learned==GestureType.ACTIVATE){armingManager.activate(timestamp);lastGestureTime=timestamp;vibrateLong();sendGestureBroadcast(learned);return};if(gestureTrainer.hasTrainedGesture(GestureType.ACTIVATE)&&!armingManager.isArmed)return;val gesture=learned?:run{val w=wristDetector.process(timestamp,lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ);val p=pinchDetector.process(timestamp,lastGyroX,lastGyroY,lastGyroZ,lastLinAccX,lastLinAccY,lastLinAccZ);w?:p};gesture?.let{armingManager.touch(timestamp);executeGesture(it,timestamp)}}
    private fun executeGesture(gesture:GestureType,timestamp:Long=System.currentTimeMillis()){if(gesture==GestureType.ACTIVATE){armingManager.activate(timestamp);lastGestureTime=timestamp;vibrateLong();sendGestureBroadcast(gesture);return};lastGestureTime=timestamp;vibrate();when(gesture){GestureType.NEXT_TRACK->mediaControllerManager.nextTrack();GestureType.PREVIOUS_TRACK->mediaControllerManager.previousTrack();GestureType.PLAY_PAUSE->mediaControllerManager.playPause();GestureType.ACTIVATE->Unit};sendGestureBroadcast(gesture)}

    private fun startTraining(intent:Intent){val name=intent.getStringExtra(EXTRA_TRAINING_GESTURE)?:return;trainingGestureType=try{GestureType.valueOf(name)}catch(_:IllegalArgumentException){return};if(!isRunning)startGestureDetection();ensureSensorsRegistered();gestureTrainer.startTraining();isTrainingMode=true;lastTrainingProgress=0;sendTrainingProgress(0,0,false,false);vibrate()}
    private fun onTrainingRepetitionAccepted(){val count=gestureTrainer.getTrainingRepetitionCount();sendTrainingProgress(100,count,false,false);vibrate();if(count>=gestureTrainer.getRequiredRepetitions()){val type=trainingGestureType?:return;val success=gestureTrainer.saveTraining(type);isTrainingMode=false;trainingGestureType=null;sendTrainingProgress(100,count,true,success);if(success)vibrateLong()}}
    private fun cancelTraining(){isTrainingMode=false;trainingGestureType=null;gestureTrainer.cancelTraining();sendTrainingProgress(0,gestureTrainer.getTrainingRepetitionCount(),true,false)}
    private fun clearTraining(){gestureTrainer.clearAll();armingManager.deactivate();trainingGestureType=null;isTrainingMode=false;sendTrainingProgress(0,0,true,true)}
    private fun ensureSensorsRegistered(){gyroscope?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)};linearAccel?.let{sensorManager.registerListener(this,it,SensorManager.SENSOR_DELAY_GAME)};if(!wakeLock.isHeld)wakeLock.acquire(10*60*1000L)}
    private fun sendTrainingProgress(progress:Int,repetitions:Int,done:Boolean,success:Boolean){sendBroadcast(Intent(ACTION_TRAINING_PROGRESS).apply{putExtra(EXTRA_TRAINING_PROGRESS,progress);putExtra(EXTRA_TRAINING_REPETITIONS,repetitions);putExtra(EXTRA_TRAINING_DONE,done);putExtra(EXTRA_TRAINING_SUCCESS,success)})}
    private fun sendGestureBroadcast(gesture:GestureType)=sendBroadcast(Intent("com.yourname.gesturemusic.GESTURE_DETECTED").apply{putExtra("gesture",gesture.name)})

    private fun startGestureDetection(){if(isRunning)return;isRunning=true;lastGestureTime=0L;screenOffTime=-1L;try{mediaControllerManager.connect()}catch(e:Exception){Log.e(TAG,"MediaController connect failed",e)};ensureSensorsRegistered();startForeground(NOTIFICATION_ID,buildNotification())}
    private fun stopGestureDetection(){if(!isRunning)return;isRunning=false;sensorManager.unregisterListener(this);if(wakeLock.isHeld)wakeLock.release();wristDetector.reset();pinchDetector.reset();armingManager.deactivate();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    private fun updateSensitivity(intent:Intent){val angle=intent.getFloatExtra(EXTRA_ANGLE_THRESHOLD,28f);val pinch=intent.getFloatExtra(EXTRA_PINCH_THRESHOLD,3f);val cooldown=intent.getLongExtra(EXTRA_COOLDOWN,1200L);val left=intent.getBooleanExtra(EXTRA_LEFT_HAND,false);wristDetector=WristRotationDetector(angleThresholdDegrees=angle,cooldownMs=cooldown,leftHand=left);pinchDetector=DoublePinchDetector(thresholdUp=pinch)}
    private fun startIdleTimer(){cancelIdleTimer();idleExecutor=idleExecutor?:Executors.newSingleThreadScheduledExecutor();idleTask=idleExecutor?.schedule({if(!isMusicPlaying&&!isTrainingMode){sensorManager.unregisterListener(this@GestureMusicService);if(wakeLock.isHeld)wakeLock.release()}},IDLE_TIMEOUT_MS,TimeUnit.MILLISECONDS)}
    private fun cancelIdleTimer(){idleTask?.cancel(false);idleTask=null;if(isRunning)ensureSensorsRegistered()}
>>>>>>> a28610485209baa8884a82ddbaef253d63caeba3
    private fun createNotificationChannel(){notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID,"Gesture Music Control",NotificationManager.IMPORTANCE_LOW).apply{description="Фоновое управление музыкой жестами";setShowBadge(false)})}
    private fun buildNotification(text:String="Слушаю жесты запястья"):Notification{val stopIntent=PendingIntent.getService(this,0,Intent(this,GestureMusicService::class.java).apply{action=ACTION_STOP},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val contentIntent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);return NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("Gesture Music").setContentText(text).setSmallIcon(R.drawable.ic_music_note).setContentIntent(contentIntent).addAction(R.drawable.ic_stop,"Стоп",stopIntent).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build()}
    private fun vibrate(){try{vibrator.vibrate(VibrationEffect.createOneShot(45,VibrationEffect.DEFAULT_AMPLITUDE))}catch(_:Exception){}}
    private fun vibrateLong(){try{vibrator.vibrate(VibrationEffect.createOneShot(120,VibrationEffect.DEFAULT_AMPLITUDE))}catch(_:Exception){}}
}
