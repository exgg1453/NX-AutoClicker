plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val nxKeystore: String? = System.getenv("NX_KEYSTORE")

android {
    namespace = "com.nxteam.nxautoclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nxteam.nxautoclicker"
        minSdk = 24
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("nx") {
            if (nxKeystore != null) {
                storeFile = file(nxKeystore)
                storePassword = System.getenv("NX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NX_KEY_ALIAS")
                keyPassword = System.getenv("NX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (nxKeystore != null) {
                signingConfig = signingConfigs.getByName("nx")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (nxKeystore != null) {
                signingConfigs.getByName("nx")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
