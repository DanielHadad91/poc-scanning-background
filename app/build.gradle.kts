plugins {
    alias(libs.plugins.android.application)
}

// The only dependency is our own :sdk module (which is itself framework-only, no AndroidX).
// This module keeps the GUI and the manifest that declares the SDK's components and permissions.
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

dependencies {
    implementation(project(":sdk"))
}
