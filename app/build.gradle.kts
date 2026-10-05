plugins {
    id("com.android.application")
}

android {
    namespace = "com.ronish.tinytvlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ronish.tinytvlauncher"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    // Intentionally empty.
    // No AndroidX, Compose, Material, Room, or other runtime libraries.
}