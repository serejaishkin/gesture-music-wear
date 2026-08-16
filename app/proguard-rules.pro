# ProGuard rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.compose.ui.tooling.preview.Preview <methods>;
}
