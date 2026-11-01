# Instrumentation dependencies share the target classloader but are not app R8 roots.
# Keep their Kotlin API on the QA target; no blanket app/AndroidX keep is added.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# APIs called directly by the instrumentation APK, rather than by production UI.
-keep class com.blw.documentreader.*TestActivity { *; }
-keep class com.blw.documentreader.UpdateTestEnvironment { *; }
-keep class com.blw.documentreader.MainViewModel { *; }
-keep class com.blw.documentreader.data.** { *; }
-keep class com.blw.documentreader.printing.** { *; }
-keep class com.blw.documentreader.formats.** { *; }
-keep class com.blw.documentreader.pdf.** { *; }
-keep class com.blw.documentreader.updates.** { *; }
-keep class com.blw.documentreader.ui.OfficeWebView { *; }
-keep class com.google.android.play.core.appupdate.testing.** { *; }
