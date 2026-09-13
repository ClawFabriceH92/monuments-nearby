# Règles R8 / ProGuard de Monuments à proximité.
#
# La minification reste désactivée dans build.gradle.kts tant qu'elle n'a pas
# été validée sur appareil (la CI ne fait que compiler). Ces règles sont prêtes
# pour le jour où `isMinifyEnabled = true` : elles couvrent ce que R8 ne peut
# pas déduire seul (réflexion, JNI, sérialisation).

# --- Débogage : garder les numéros de ligne dans les traces de plantage ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- osmdroid (chargement de classes par nom pour les tuiles et le cache) ---
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# --- Coil ---
-dontwarn coil.**

# --- ZXing ---
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- ML Kit (scanner de codes-barres) ---
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# --- Google Play services (géolocalisation, geofencing) ---
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- WorkManager : workers instanciés par nom ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# --- Receivers / services déclarés dans le manifeste ---
-keep class com.fabrice.monumentsnearby.** extends android.content.BroadcastReceiver
-keep class com.fabrice.monumentsnearby.** extends android.app.Service

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**
