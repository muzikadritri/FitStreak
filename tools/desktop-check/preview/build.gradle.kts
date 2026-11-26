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
    // Die Fakes der App-Unit-Tests dienen auch der Screen-Vorschau; die Tests selbst laufen hier als :preview:test.
    sourceSets.main {
        kotlin.srcDir("../../../app/src/test/java/de/leon/fitstreak/testing")
        kotlin.srcDir("../../../app/src/main/java")
        // Android-spezifischer Code wird hier nicht kompiliert (das prüft der CI-Build).
        kotlin.exclude(
            "**/MainActivity.kt",
            "**/FitStreakApp.kt",
            "**/AppContainer.kt",
            "**/ui/Navigation.kt",
            "**/ui/theme/Fonts.kt",
            "**/data/db/AppDatabase.kt",
            "**/data/settings/DataStoreSettingsStore.kt",
            "**/platform/**",
            "**/notify/**",
            "**/widget/**",
        )
    }
}
kotlin.sourceSets.test {
    kotlin.srcDir("../../../app/src/test/java")
    kotlin.exclude("**/testing/**")
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:$cmp")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.coroutines.get()}")
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test { useJUnit() }

application {
    mainClass.set("preview.MainKt")
}
