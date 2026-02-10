plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.marlonpra.cameraviewertv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.marlonpra.cameraviewertv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Debug puede compilar multi-ABI (emulador), pero Release lo limitamos abajo.
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.recyclerview)
    implementation(libs.glide)

    implementation(libs.libvlc.all)

    constraints {
        implementation(libs.androidx.vectordrawable)
        implementation(libs.androidx.vectordrawable.animated)
    }
}