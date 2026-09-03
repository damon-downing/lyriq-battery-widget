plugins {
    id("com.android.application")
}

android {
    namespace = "com.downinglabs.lyriqwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.downinglabs.lyriqwidget"
        minSdk = 31
        targetSdk = 35
        versionCode = 11
        versionName = "1.6.1"
    }

    signingConfigs {
        // Same key build.sh has always used — keeps this a valid update instead of a signature
        // mismatch against whatever's already installed on the phone.
        create("shared") {
            storeFile = file("../keystore/debug.jks")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.smartcar.sdk:smartcar-auth:4.1.3")
}
