plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Release AdMob identifiers are supplied only at build time; never commit real IDs.
val sampleAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val sampleBannerId = "ca-app-pub-3940256099942544/9214589741"
val liveAdMobAppId = providers.gradleProperty("WREADER_ADMOB_APP_ID").orNull.orEmpty().trim()
val liveAdMobBannerId = providers.gradleProperty("WREADER_ADMOB_BANNER_ID").orNull.orEmpty().trim()
val enableReleaseAds = providers.gradleProperty("WREADER_ENABLE_RELEASE_ADS").orNull == "true"
if (enableReleaseAds) {
    require(Regex("""ca-app-pub-\d{16}~\d{10}""").matches(liveAdMobAppId)) {
        "Release ads require a valid WREADER_ADMOB_APP_ID."
    }
    require(Regex("""ca-app-pub-\d{16}/\d{10}""").matches(liveAdMobBannerId)) {
        "Release ads require a valid WREADER_ADMOB_BANNER_ID."
    }
    require(liveAdMobAppId != sampleAdMobAppId && liveAdMobBannerId != sampleBannerId) {
        "Real release advertising cannot use AdMob sample IDs."
    }
}
android {
    namespace = "com.blw.documentreader"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.blw.wreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5-admob-prep"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("debug") {
            manifestPlaceholders["admobAppId"] = sampleAdMobAppId
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "PURCHASES_ENABLED", "false")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"" + sampleBannerId + "\"")
        }
        getByName("release") {
            // Remains ad-free unless explicitly enabled with verified real IDs.
            manifestPlaceholders["admobAppId"] = if (enableReleaseAds) liveAdMobAppId else sampleAdMobAppId
            buildConfigField("boolean", "ADS_ENABLED", enableReleaseAds.toString())
            buildConfigField("boolean", "PURCHASES_ENABLED", "false")
            buildConfigField("String", "ADMOB_BANNER_UNIT_ID",
                "\"" + (if (enableReleaseAds) liveAdMobBannerId else sampleBannerId) + "\"")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES,LICENSE.md,NOTICE.md}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.webkit:webkit:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // No commercial Word/Excel renderer added without a validated redistribution license.
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
