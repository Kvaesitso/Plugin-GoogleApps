plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "de.mm20.launcher2.plugin.google"
    compileSdkPreview = "VanillaIceCream"

    defaultConfig {
        applicationId = "de.mm20.launcher2.plugin.google"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources.excludes += "META-INF/DEPENDENCIES"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activitycompose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialicons)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.browser)
    implementation(libs.launchersdk)
    implementation(libs.coil.compose)
    implementation(libs.google.auth)
    implementation(libs.google.oauth2)
    implementation(libs.google.drive)
    implementation(libs.google.apiclient)
}