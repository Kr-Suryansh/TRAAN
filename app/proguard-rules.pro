# ProGuard rules for :app module.
# Compose and Hilt handle their own tree-sharking.
-keepattributes *Annotation*
-keep class com.sih.app.** { *; }
