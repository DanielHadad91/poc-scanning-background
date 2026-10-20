plugins {
    alias(libs.plugins.android.application)
}

// No dependencies: scheduling, storage, networking and UI use only Android framework APIs,
// so the app needs no AndroidX library and runs on API 19 through 37.
android {
    namespace = "com.biocatch.poc.scanningbackground"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.biocatch.poc.scanningbackground"
        minSdk = 19
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
