@file:Suppress("UnstableApiUsage")

pluginManagement {
  repositories {
    // Resolve Kotlin dependencies directly instead of relying on the plugin portal's proxy.
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "build-logic"

dependencyResolutionManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
