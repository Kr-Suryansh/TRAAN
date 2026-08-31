# Consumer proguard rules for :data module.
# Keep Room entities and Moshi models
-keepclasseswithmembers class com.sih.data.db.entity.** {
    *;
}
-keepclasseswithmembers class com.sih.network.model.** {
    @com.squareup.moshi.Json <fields>;
}
