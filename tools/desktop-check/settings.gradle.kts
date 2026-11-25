// Lokales Prüf-Werkzeug (nicht Teil des App-Builds): kompiliert :core und den reinen
// Compose-Code der App gegen Compose Desktop und rendert Screens/Animationen als PNG.
// Braucht nur Maven Central – kein Android SDK.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "desktop-check"
include(":core")
project(":core").projectDir = file("../../core")
include(":preview")
