plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "vn.uit.healthos.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Same floor as the app. Collecting a profile without root needs the device on API 33 or newer.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    // The test APK instruments itself so it can kill, restart and drive the app under test.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // Runs on the phone connected over adb rather than on a Gradle managed emulator.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
