# Budget Pace — R8/ProGuard configuration for the release build.
#
# The release build type has always referenced this file; until now it did not exist, so
# `assembleRelease` could not run at all from this tree.

# ── Google API client (Sheets) ────────────────────────────────────────────────
# The Sheets client maps JSON onto model classes purely by reflection over @Key-annotated fields
# and their generic types. Without these, R8 renames the fields and every request serialises to
# an empty object while every response deserialises to nulls — silently, at runtime only.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,EnclosingMethod,InnerClasses

-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# Field names in the Sheets request/response models are the wire format.
-keep class com.google.api.services.sheets.v4.model.** { *; }
-keep class com.google.api.client.googleapis.json.GoogleJsonError { *; }
-keep class com.google.api.client.googleapis.json.GoogleJsonError$* { *; }
-keep class com.google.api.client.util.** { *; }

# Referenced by the client's version check; shipped in the library's own rules too.
-keep public class com.google.api.client.googleapis.GoogleUtils

# Compile-time-only or server-side dependencies of the API client that Android never provides.
-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.**
-dontwarn java.awt.**
-dontwarn javax.naming.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**

# ── Coroutines / Kotlin ───────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**

# ── Logging ───────────────────────────────────────────────────────────────────
# Spec sections 8/65: message text, amounts and references are only ever logged at Log.d/Log.v and
# only under BuildConfig.DEBUG. Stripping the calls here means a release APK cannot leak them even
# if a future edit forgets the guard. Warning/error logs (outcome, channel, bank) are kept.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
