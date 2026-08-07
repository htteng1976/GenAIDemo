import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing secrets are read from local.properties (never committed to git).
// Expected keys:
//   keystore.storeFile, keystore.keyAlias, keystore.storePassword, keystore.keyPassword
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) load(FileInputStream(localPropertiesFile))
}
val hasSigning = localProperties.getProperty("keystore.storePassword") != null

android {
    namespace = "com.genai.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.htteng.voicetotext"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (hasSigning) {
                storeFile = rootProject.file(
                    localProperties.getProperty("keystore.storeFile", "upload-keystore.jks")
                )
                storePassword = localProperties.getProperty("keystore.storePassword")
                keyAlias = localProperties.getProperty("keystore.keyAlias", "upload")
                keyPassword = localProperties.getProperty("keystore.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Embed native debug symbols in the AAB (resolves Play's
            // "no debug symbols" warning for the bundled .so libraries).
            ndk { debugSymbolLevel = "FULL" }
            // Only sign if signing keys are present in local.properties,
            // so unsigned builds still work without them.
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // On-device speech recognition via ML Kit GenAI (no API key, runs on-device).
    // Basic mode supports API 31+ incl. Traditional Chinese (cmn-Hant-TW).
    // Availability is gated at runtime; unsupported devices show a message.
    implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
