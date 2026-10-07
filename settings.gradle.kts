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
      providers.gradleProperty("quarkiverseProblemVersion").orNull?.let { version("quarkiverseProblem", it) }
    }
  }
  
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("build-logic") {
  name = "sunday-generator-build-logic"
}

val localSundayKt = providers.environmentVariable("SUNDAY_KOTLIN_PATH")
  .map { file(it) }
  .getOrElse(rootDir.parentFile.resolve("sunday-kt"))
val useLocalSundayKt =
  providers.gradleProperty("useLocalSundayKt").map { it.toBooleanStrict() }
    .getOrElse(providers.environmentVariable("SUNDAY_KOTLIN_PATH").isPresent)
if (useLocalSundayKt && localSundayKt.isDirectory) {
  includeBuild(localSundayKt) {
    dependencySubstitution {
      substitute(module("io.outfoxx.sunday:sunday-core")).using(project(":sunday-core"))
      substitute(module("io.outfoxx.sunday:sunday-jdk")).using(project(":sunday-jdk"))
      substitute(module("io.outfoxx.sunday:sunday-broker")).using(project(":sunday-broker"))
      substitute(module("io.outfoxx.sunday:sunday-problem")).using(project(":sunday-problem"))
      substitute(module("io.outfoxx.sunday:sunday-jaxrs-quarkus")).using(project(":sunday-jaxrs-quarkus"))
      if (localSundayKt.resolve("client-quarkus").isDirectory) {
        substitute(module("io.outfoxx.sunday:sunday-client-quarkus")).using(project(":sunday-client-quarkus"))
      }
      if (localSundayKt.resolve("validation-javax").isDirectory) {
        substitute(module("io.outfoxx.sunday:sunday-validation-javax")).using(project(":sunday-validation-javax"))
        substitute(module("io.outfoxx.sunday:sunday-validation-jakarta")).using(project(":sunday-validation-jakarta"))
      }
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
  "integration-tests:quarkus-security-contracts",
  "integration-tests:quarkus-security-provider",
  "integration-tests:quarkus-security",
)
