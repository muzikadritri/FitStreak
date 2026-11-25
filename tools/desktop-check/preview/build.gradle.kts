import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Letzte Compose-Desktop-Version, die komplett auf Maven Central liegt (kein Google-Maven nötig).
val cmp = "1.5.12"

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets.main {
        kotlin.srcDir("../../../app/src/main/java")
        // Android-spezifischer Code wird hier nicht kompiliert (das prüft der CI-Build).
        kotlin.exclude(
            "**/MainActivity.kt",
            "**/FitStreakApp.kt",
            "**/AppContainer.kt",
            "**/data/db/AppDatabase.kt",
            "**/data/settings/DataStoreSettingsStore.kt",
            "**/platform/**",
            "**/notify/**",
            "**/widget/**",
        )
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:$cmp")
    implementation("org.jetbrains.compose.material:material-icons-core-desktop:$cmp")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.coroutines.get()}")
}

application {
    mainClass.set("preview.MainKt")
}
