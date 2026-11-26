import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// versionCode kommt in CI aus der Run-Nummer (-PversionCode=123), lokal ist es 1.
val appVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "de.leon.fitstreak"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.leon.fitstreak"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = "1.0.$appVersionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Native Bibliotheken der 3D-Engine: Handys (arm) plus x86_64 für den Emulator-Test
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        // Fester Keystore im Repo (privates Hobby-Projekt): Updates lassen sich so immer
        // über die installierte Version installieren und die Daten bleiben erhalten.
        create("release") {
            storeFile = rootProject.file("keystore/release.jks")
            storePassword = "fitstreak"
            keyAlias = "fitstreak"
            keyPassword = "fitstreak"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 3D-Figur: Filament (Rendering) + gltfio (lädt das Modell)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
