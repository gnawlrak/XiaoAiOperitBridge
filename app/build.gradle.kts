import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

configurations.all {
    resolutionStrategy {
        // 离线环境：强制对齐到本地缓存中已有的版本
        force(
            "androidx.savedstate:savedstate:1.4.0",
            "androidx.savedstate:savedstate-android:1.4.0",
            "androidx.savedstate:savedstate-ktx:1.4.0",
            "androidx.savedstate:savedstate-compose:1.4.0",
            "androidx.compose.material3:material3:1.3.1",
            "androidx.compose.material3:material3-android:1.3.1",
            "androidx.compose.material:material-ripple:1.7.6",
            "androidx.compose.material:material-icons-core:1.7.6",
        )
    }
}

android {
    namespace = "com.example.xiaoaioperit"
    compileSdk = 37

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localProps.load(localPropsFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.example.xiaoaioperit"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("RELEASE_STORE_FILE", "D:/key/release.jks"))
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")

    implementation("androidx.compose.ui:ui:1.11.2")
    implementation("androidx.compose.ui:ui-graphics:1.11.2")
    implementation("androidx.compose.foundation:foundation:1.11.2")

    // 顶/底栏高斯模糊（背景模糊）
    implementation("dev.chrisbanes.haze:haze:1.7.2")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}