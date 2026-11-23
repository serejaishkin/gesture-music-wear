import com.example.gesturemusicwear.GestureEngine;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replay a recorded IMU CSV through GestureEngine on a PC.
 * Usage: tools/replay.sh imu_123.csv [angle pinch clench leftHand fistGuard]
 * Prints every detected gesture with its time and the label active at that moment,
 * then a per-label summary (false triggers = detections while label is "none").
 */
public class Replay {
    public static void main(String[] a) throws Exception {
        float angle = a.length > 1 ? Float.parseFloat(a[1]) : 35f;
        float pinch = a.length > 2 ? Float.parseFloat(a[2]) : 2.2f;
        float clench = a.length > 3 ? Float.parseFloat(a[3]) : 2.2f;
        boolean left = a.length > 4 ? Boolean.parseBoolean(a[4]) : true;
        boolean guard = a.length > 5 && Boolean.parseBoolean(a[5]);
        GestureEngine e = new GestureEngine(angle, pinch, clench, left, guard);
        String[] names = {"-", "NEXT", "PREVIOUS", "PLAY_PAUSE", "FIST_ARM", "ACT_NEEDED"};
        Map<String, int[]> summary = new LinkedHashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(a[0]))) {
            r.readLine();
            String line;
            long t0 = -1;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                long t = Long.parseLong(p[0]);
                if (t0 < 0) t0 = t;
                String label = p.length > 7 ? p[7] : "none";
                int res = e.process(t, f(p[1]), f(p[2]), f(p[3]), f(p[4]), f(p[5]), f(p[6]));
                summary.computeIfAbsent(label, k -> new int[6]);
                if (res != GestureEngine.NONE && res != GestureEngine.ACTIVATION_NEEDED) {
                    summary.get(label)[res]++;
                    System.out.printf("%7.2fs  %-10s  label=%s%n", (t - t0) / 1000.0, names[res], label);
                }
            }
        }
        System.out.println("\nlabel            NEXT PREV PLAY FIST");
        for (Map.Entry<String, int[]> en : summary.entrySet()) {
            int[] c = en.getValue();
            System.out.printf("%-15s %5d %4d %4d %4d%n", en.getKey(), c[1], c[2], c[3], c[4]);
        }
    }

    private static float f(String s) { return Float.parseFloat(s); }
}
