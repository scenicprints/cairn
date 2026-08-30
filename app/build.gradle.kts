plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// An unset repo secret arrives as an empty string rather than as null, so blank means absent.
val keystorePath: String? = System.getenv("CAIRN_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.cairn.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cairn.launcher"
        minSdk = 31
        targetSdk = 35
        val build = System.getenv("CAIRN_BUILD")?.takeIf { it.isNotBlank() } ?: "1"
        versionCode = build.toInt()
        versionName = "0.1.$build"
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CAIRN_STORE_PASSWORD")
                keyAlias = System.getenv("CAIRN_KEY_ALIAS")
                keyPassword = System.getenv("CAIRN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
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
