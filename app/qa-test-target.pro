# Instrumentation dependencies share the target classloader but are not app R8 roots.
# Keep their Kotlin API on the QA target; no blanket app/AndroidX keep is added.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# APIs called directly by the instrumentation APK, rather than by production UI.
-keep class com.blw.documentreader.*TestActivity { *; }
-keep class com.blw.documentreader.MainViewModel { *; }
-keep class com.google.android.play.core.appupdate.testing.** { *; }
