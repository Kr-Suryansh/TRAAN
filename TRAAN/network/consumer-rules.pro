# Consumer proguard rules for :network module.
# Keep Moshi models
-keepclasseswithmembers class com.sih.network.** {
    @com.squareup.moshi.Json <fields>;
}
