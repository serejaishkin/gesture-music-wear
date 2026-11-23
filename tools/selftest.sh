#!/bin/bash
set -e
S=app/src/main/java/com/example/gesturemusicwear
OUT=$(mktemp -d)
javac -d "$OUT" $S/TrainingCalibrator.java $S/GestureEngine.java $S/WristRotationDetector.java $S/DoublePinchDetector.java $S/FistClenchDetector.java tools/SelfTest.java
java -cp "$OUT" SelfTest
