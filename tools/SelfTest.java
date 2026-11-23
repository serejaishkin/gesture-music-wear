import com.example.gesturemusicwear.GestureEngine;
import com.example.gesturemusicwear.TrainingCalibrator;

/** Synthetic regression test for GestureEngine. Exit code 1 on failure. */
public class SelfTest {
    static long t = 0;
    static int first = 0, count = 0;
    static GestureEngine e;

    static void feed(float gy, float az) {
        int r = e.process(t, 0, gy, 0, 0, 0, az);
        if (r == GestureEngine.NEXT || r == GestureEngine.PREVIOUS
                || r == GestureEngine.PLAY_PAUSE || r == GestureEngine.FIST_ARM) {
            count++;
            if (first == 0) first = r;
        }
        t += 20;
    }

    static void idle(int n) { for (int i = 0; i < n; i++) feed(0, 9.81f); }

    static void check(boolean ok, String msg) {
        System.out.println((ok ? "PASS " : "FAIL ") + msg);
        if (!ok) System.exit(1);
    }

    public static void main(String[] a) {
        e = new GestureEngine(35f, 2.2f, 2.2f, true, false);
        idle(200);
        check(count == 0, "idle produces no gestures");

        for (int i = 0; i < 15; i++) feed((float) (-4.0 * Math.sin(Math.PI * i / 14)), 9.81f);
        idle(100);
        check(first == GestureEngine.NEXT && count == 1, "left-hand outward flick -> exactly one NEXT");

        first = 0; count = 0;
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < 3; i++) feed(0, 13.81f);
            idle(6);
        }
        idle(100);
        check(first == GestureEngine.PLAY_PAUSE && count == 1, "double pinch -> exactly one PLAY_PAUSE");

        first = 0; count = 0;
        for (int i = 0; i < 3; i++) feed(0, 13.81f);
        idle(100);
        check(count == 0, "single pinch -> nothing");

        first = 0; count = 0;
        e = new GestureEngine(35f, 2.2f, 2.2f, true, true);
        for (int i = 0; i < 15; i++) feed((float) (-4.0 * Math.sin(Math.PI * i / 14)), 9.81f);
        idle(100);
        check(count == 0, "fist guard on: wrist ignored until armed");
    
        check(TrainingCalibrator.pinchThreshold(1.0f) == 1.2f, "calibrator: no deviation -> min pinch threshold");
        check(Math.abs(TrainingCalibrator.pinchThreshold(1.5f) - 2.0f) < 1e-4, "calibrator: 1.5 g peak -> pinch 2.0");
        check(TrainingCalibrator.angleThreshold(1000f) == 70f && TrainingCalibrator.angleThreshold(1f) == 20f, "calibrator: angle clamped 20..70");
    }
}
