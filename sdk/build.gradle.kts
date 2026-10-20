plugins {
    alias(libs.plugins.android.library)
}

// The SDK: all background scanning + scheduling logic, framework-only (no AndroidX), no GUI,
// no Activity, and no component/permission declarations in its manifest. The integrating app
// declares the components and permissions in its own manifest and calls ScanningSdk to start.
android {
    namespace = "com.biocatch.poc.scanningbackground.sdk"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 19
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
