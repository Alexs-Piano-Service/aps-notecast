import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val noteCastBugReportUrl = providers.gradleProperty("APS_NOTECAST_REPORT_URL")
    .orElse(providers.environmentVariable("APS_NOTECAST_REPORT_URL"))
    .orElse("https://www.alexanderpeppe.com/notecast-data/bug-report.php")

val noteCastBugReportSecret = providers.gradleProperty("APS_NOTECAST_REPORT_SECRET")
    .orElse(providers.environmentVariable("APS_NOTECAST_REPORT_SECRET"))
    .orElse("6ea29fb50dec1b43041c9131a8001575c5e80db4e148c82723d742270c2bbee1")

android {
    namespace = "com.alexanderpeppe.pianobeam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alexanderpeppe.notecast"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.7"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BUG_REPORT_URL", noteCastBugReportUrl.get().asBuildConfigString())
        buildConfigField("String", "BUG_REPORT_SECRET", noteCastBugReportSecret.get().asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
