#!/bin/bash
# Usage: tools/replay.sh imu_123.csv [angle pinch clench leftHand fistGuard]
set -e
S=app/src/main/java/com/example/gesturemusicwear
OUT=$(mktemp -d)
javac -d "$OUT" $S/GestureEngine.java $S/WristRotationDetector.java $S/DoublePinchDetector.java $S/FistClenchDetector.java tools/Replay.java
java -cp "$OUT" Replay "$@"
