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
    create("libs") {
      providers.gradleProperty("quarkusVersion").orNull?.let { version("quarkus-rest", it) }
    }
  }
  
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-logic") {
  name = "sunday-generator-build-logic"
}

val localSundayKt = rootDir.parentFile.resolve("sunday-kt")
val useLocalSundayKt = providers.gradleProperty("useLocalSundayKt").map { it.toBooleanStrict() }.getOrElse(true)
if (useLocalSundayKt && localSundayKt.isDirectory) {
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
  "integration-tests:quarkus",
  "integration-tests:jaxrs",
)
