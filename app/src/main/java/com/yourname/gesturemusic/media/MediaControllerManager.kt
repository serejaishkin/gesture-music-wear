package com.yourname.gesturemusic.media

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

class MediaControllerManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaControllerManager"
        private const val POLL_INTERVAL_MS = 2000L
    }

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var stateListener: ((Boolean) -> Unit)? = null
    private var isPolling = false

    private var hidDevice: Any? = null
    private var hidProfile: Any? = null

    fun setStateListener(listener: (isPlaying: Boolean) -> Unit) {
        stateListener = listener
    }

    fun connect() {
        if (isPolling) return
        isPolling = true
        pollState()
        initBluetoothHid()
        Log.d(TAG, "Connected (Bluetooth HID mode)")
    }

    fun refreshConnection() {}

    fun disconnect() {
        isPolling = false
        handler.removeCallbacksAndMessages(null)
    }

    fun playPause() {
        Log.d(TAG, "playPause")
        dispatch(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun nextTrack() {
        Log.d(TAG, "nextTrack")
        dispatch(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previousTrack() {
        Log.d(TAG, "previousTrack")
        dispatch(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun isPlaying(): Boolean = audioManager.isMusicActive
    fun hasActiveSession(): Boolean = audioManager.isMusicActive

    private fun dispatch(keyCode: Int) {
        val downTime = SystemClock.uptimeMillis()

        if (sendBluetoothMediaKey(keyCode)) {
            Log.d(TAG, "Bluetooth HID sent: keyCode=$keyCode")
            return
        }

        try {
            val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)

            audioManager.dispatchMediaKeyEvent(downEvent)
            handler.postDelayed({
                audioManager.dispatchMediaKeyEvent(upEvent)
            }, 50)
            Log.d(TAG, "AudioManager.dispatch sent: keyCode=$keyCode")
            return
        } catch (e: Exception) {
            Log.w(TAG, "AudioManager failed: ${e.message}")
        }

        fallbackBroadcast(keyCode, downTime)
    }

    private fun initBluetoothHid() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return
            val hidDeviceProfile = 19

            bluetoothAdapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    Log.d(TAG, "Bluetooth HID profile connected: $profile")
                    hidProfile = proxy
                    try {
                        val getDevicesMethod = proxy.javaClass.getDeclaredMethod("getConnectedDevices")
                        val devices = getDevicesMethod.invoke(proxy) as? List<*>
                        if (!devices.isNullOrEmpty()) {
                            hidDevice = devices[0]
                            Log.d(TAG, "HID device found: $hidDevice")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get HID devices: ${e.message}")
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    Log.d(TAG, "Bluetooth HID profile disconnected")
                    hidProfile = null
                    hidDevice = null
                }
            }, hidDeviceProfile)
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth HID init failed: ${e.message}")
        }
    }

    private fun sendBluetoothMediaKey(keyCode: Int): Boolean {
        return try {
            val device = hidDevice ?: return false
            val profile = hidProfile ?: return false

            val sendReportMethod = profile.javaClass.getDeclaredMethod(
                "sendReport",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType,
                ByteArray::class.java
            )

            val report = when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> byteArrayOf(0x01, 0x00, 0x00)
                KeyEvent.KEYCODE_MEDIA_NEXT -> byteArrayOf(0x00, 0x01, 0x00)
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> byteArrayOf(0x00, 0x02, 0x00)
                else -> return false
            }

            sendReportMethod.invoke(profile, device, 1, report)

            handler.postDelayed({
                try {
                    sendReportMethod.invoke(profile, device, 1, byteArrayOf(0x00, 0x00, 0x00))
                } catch (_: Exception) {}
            }, 50)

            true
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth HID send failed: ${e.message}")
            false
        }
    }

    private fun fallbackBroadcast(keyCode: Int, downTime: Long) {
        try {
            val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val up = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)

            val downIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(android.content.Intent.EXTRA_KEY_EVENT, down)
            }
            appContext.sendOrderedBroadcast(downIntent, null)

            handler.postDelayed({
                val upIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                    putExtra(android.content.Intent.EXTRA_KEY_EVENT, up)
                }
                appContext.sendOrderedBroadcast(upIntent, null)
            }, 50)

            Log.d(TAG, "Fallback broadcast sent: keyCode=$keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback broadcast failed: ${e.message}")
        }
    }

    private fun pollState() {
        if (!isPolling) return
        val playing = audioManager.isMusicActive
        stateListener?.invoke(playing)
        handler.postDelayed({ pollState() }, POLL_INTERVAL_MS)
    }
}
