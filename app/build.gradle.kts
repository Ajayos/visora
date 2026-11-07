plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.visora"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.visora.tv"
        minSdk = 28
        targetSdk = 28 // Dedicated Fire OS 7 deployment; reassess before Appstore distribution.
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "dev.visora.DeviceTestRunner"
        ndk { abiFilters += "armeabi-v7a" }
    }
    signingConfigs {
        create("production") {
            System.getenv("VISORA_KEYSTORE")?.let { storeFile = file(it) }
            storePassword = System.getenv("VISORA_STORE_PASSWORD")
            keyAlias = System.getenv("VISORA_KEY_ALIAS")
            keyPassword = System.getenv("VISORA_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (System.getenv("VISORA_KEYSTORE") != null)
                signingConfig = signingConfigs.getByName("production")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        abortOnError = true
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
