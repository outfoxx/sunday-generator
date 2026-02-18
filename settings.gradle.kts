@file:Suppress("UnstableApiUsage")

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
    exclusiveContent {
      forRepository {
        maven {
          url = uri("$rootDir/gradle/vendor/m2")
        }
      }
      filter {
        includeGroup("com.github.amlorg")
        includeGroup("org.mule.syaml")
        includeGroup("org.mule.common")
      }
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
