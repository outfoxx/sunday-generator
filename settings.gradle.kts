@file:Suppress("UnstableApiUsage")

import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    maven {
      setUrl("https://repository-master.mulesoft.org/nexus/content/repositories/releases")
    }
    maven {
      setUrl("https://jitpack.io")
    }
  }
  versionCatalogs {
    create("libs")
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-logic")

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli",
  "gradle-plugin",
  "code-coverage",
)
