plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cairn.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cairn.launcher"
        minSdk = 31
        targetSdk = 35
        versionCode = (System.getenv("CAIRN_BUILD") ?: "1").toInt()
        versionName = "0.1.${System.getenv("CAIRN_BUILD") ?: "0"}"
    }

    signingConfigs {
        create("release") {
            val store = System.getenv("CAIRN_KEYSTORE")
            if (store != null) {
                storeFile = file(store)
                storePassword = System.getenv("CAIRN_STORE_PASSWORD")
                keyAlias = System.getenv("CAIRN_KEY_ALIAS")
                keyPassword = System.getenv("CAIRN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("CAIRN_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
