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
          url = uri("https://repository.mulesoft.org/nexus/content/repositories/public/")
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

includeBuild("build-logic") {
  name = "sunday-generator-build-logic"
}

val localSundayKt = rootDir.parentFile.resolve("sunday-kt")
if (localSundayKt.isDirectory) {
  includeBuild(localSundayKt) {
    dependencySubstitution {
      substitute(module("io.outfoxx.sunday:sunday-core")).using(project(":sunday-core"))
      substitute(module("io.outfoxx.sunday:sunday-broker")).using(project(":sunday-broker"))
      substitute(module("io.outfoxx.sunday:sunday-problem")).using(project(":sunday-problem"))
    }
  }
}

rootProject.name = "sunday-generator"

include(
  "generator",
  "cli",
  "gradle-plugin",
  "code-coverage",
)
