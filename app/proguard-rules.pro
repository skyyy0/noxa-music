# NOXA R8 rules (release minify enabled in v1.16).
#
# Stack traces stay readable: the crash log is attached to support reports,
# so file/line info survives obfuscation (mapping.txt is archived by CI).

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Release hygiene --------------------------------------------------
# Verbose/debug logs never ship: they can leak stream URLs, resolver
# responses and account emails into logcat on user devices. Info/warning/
# error stay — they back the honest diagnostics (ShareCard, resolver,
# crash paths) without the chatter.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# --- Moshi -------------------------------------------------------------
# Adapters are code-generated (KSP) and looked up by the generated factory
# names; data classes keep their @Json members.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.data.**.*JsonAdapter { *; }
-dontwarn com.squareup.moshi.**
-dontwarn okio.**

# --- OkHttp / Retrofit -------------------------------------------------
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- On-device YouTube extraction (NewPipeExtractor) -------------------
# Metadata parsing uses string constants and a couple of reflective lookups;
# keeping the package intact is cheaper than chasing a runtime NoSuchMethod
# in the player.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.**.* { *; }
-dontwarn org.schabi.newpipe.**
# NewPipe pulls in a few optional/Android-internal APIs that only exist on
# some devices.
-dontwarn org.jsoup.**
-dontwarn com.google.gson.**

# NewPipeExtractor bundles Rhino (`org.mozilla:rhino` + rhino-engine) for its
# optional script engine. Rhino's factory/converters reference desktop-Java
# APIs that never exist on Android (`java.beans.*`, `javax.script.*`) and are
# never invoked there — R8 correctly warns, and these classes are genuinely
# absent by design, not accidentally missing from our dependency set.
-dontwarn java.beans.**
-dontwarn javax.script.**

# --- Firebase (AI, Auth, Firestore, App Check) -------------------------
# Official consumer rules ship with the SDKs; these only silence optional
# pieces absent from our dependency set.
-dontwarn com.google.ai.**
-dontwarn com.google.android.gms.**
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# --- Credentials / Google ID ------------------------------------------
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.**

# --- Compose / AndroidX -----------------------------------------------
-dontwarn androidx.compose.**
-dontwarn androidx.media3.**

# Kotlin metadata used by reflection-based frameworks (Moshi/Room codegen).
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep the Room entities/daos generated accessors working under R8.
-keep class com.example.data.local.** { *; }

# The support/backend JSON models are written with org.json field names —
# keep data-class shapes used by the keyless registry.
-keep class com.example.data.support.** { *; }
-keep class com.example.data.ai.** { *; }
-keep class com.example.data.model.** { *; }
-keep class com.example.data.auth.** { *; }
