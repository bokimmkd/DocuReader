# AndroidX Test / JUnit use reflection. These rules only process the test APK.
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keepclasseswithmembers @org.junit.runner.RunWith class * { *; }
-keepattributes *Annotation*
