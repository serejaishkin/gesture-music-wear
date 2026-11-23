package com.example.gesturemusicwear;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * Records raw IMU samples to CSV for offline tuning (tools/Replay.java).
 * Columns: t_ms,gx,gy,gz,ax,ay,az,label  (gyro rad/s, accel m/s^2 raw with gravity).
 * The label is the gesture the user is currently performing (set from the UI), "none" by default.
 */
public class ImuRecorder {
    private final Object lock = new Object();
    private BufferedWriter out;
    private File file;
    private volatile String label = "none";
    private long samples;

    public boolean isRecording() { synchronized (lock) { return out != null; } }
    public File getFile() { return file; }
    public long getSamples() { return samples; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = (label == null || label.isEmpty()) ? "none" : label; }

    public boolean start(File dir) {
        synchronized (lock) {
            if (out != null) return true;
            try {
                if (!dir.exists() && !dir.mkdirs()) return false;
                file = new File(dir, "imu_" + System.currentTimeMillis() + ".csv");
                out = new BufferedWriter(new FileWriter(file), 1 << 16);
                out.write("t_ms,gx,gy,gz,ax,ay,az,label\n");
                samples = 0;
                return true;
            } catch (IOException e) {
                out = null;
                return false;
            }
        }
    }

    public void write(long tMs, float gx, float gy, float gz, float ax, float ay, float az) {
        synchronized (lock) {
            if (out == null) return;
            try {
                out.write(String.format(Locale.US, "%d,%.4f,%.4f,%.4f,%.3f,%.3f,%.3f,%s\n",
                        tMs, gx, gy, gz, ax, ay, az, label));
                samples++;
            } catch (IOException e) {
                stop();
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            if (out == null) return;
            try { out.flush(); out.close(); } catch (IOException ignored) {}
            out = null;
        }
    }
}
