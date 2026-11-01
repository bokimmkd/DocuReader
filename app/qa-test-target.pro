# Instrumentation dependencies share the target classloader but are not app R8 roots.
# Keep their Kotlin API on the QA target; no blanket app/AndroidX keep is added.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
