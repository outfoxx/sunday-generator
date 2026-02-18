import java.time.OffsetDateTime
import java.time.ZoneOffset

plugins {
  id("common.conventions")
  id("publishing.conventions")
  application
  alias(libs.plugins.jib)
  alias(libs.plugins.shadow)
  alias(libs.plugins.graalNative)
}

dependencies {

  implementation(project(":generator"))

  implementation(libs.bundles.clikt)
  implementation(libs.slf4j)

  //
  // TESTING
  //

  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)

  testImplementation(libs.hamcrest)
}

application {
  applicationName = "sunday-generator"
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  applicationDefaultJvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

val releaseVersion: String by project

val enableMultiPlatformJib = !gradle.startParameter.taskNames.any { taskName ->
  taskName == "jibDockerBuild" || taskName.endsWith(":jibDockerBuild")
}

fun Manifest.updateAttributes() {
  val title = "Sunday Generator"
  val version = releaseVersion
  val build = System.getenv("GIT_COMMIT_SHA") ?: OffsetDateTime.now(ZoneOffset.UTC).toString()
  attributes["Implementation-Title"] = title
  attributes["Implementation-Version"] = version
  attributes["Implementation-Build"] = build
}

tasks {
  shadowJar.configure {
    manifest { updateAttributes() }
    dependsOn(assembleDist)
    archiveClassifier.set("all")
    minimize {
      exclude(dependency("com.github.ajalt.clikt:.*:.*"))
    }
  }
}

graalvmNative {
  binaries.all {
    resources.autodetect()
  }
  binaries {
    named("main") {
      imageName = "sunday"
      sharedLibrary = false
    }
  }
}

tasks.generateResourcesConfigFile {
  dependsOn(tasks.shadowJar)
}

tasks.nativeCompile {
  classpathJar = tasks.shadowJar.flatMap { it.archiveFile }
}

jib {
  to {
    image = "outfoxx/sunday-generator:${project.version}"
    auth {
      username = project.findProperty("docker.user") as String? ?: System.getenv("DOCKER_PUBLISH_USER")
      password = project.findProperty("docker.token") as String? ?: System.getenv("DOCKER_PUBLISH_TOKEN")
    }
  }
  from {
    image = "eclipse-temurin:21-jre-noble"
    platforms {
      platform {
        os = "linux"
        architecture = "arm64"
      }
      if (enableMultiPlatformJib) {
        logger.warn("JIB: Enabling Multi-Platform Images")
        platform {
          os = "linux"
          architecture = "amd64"
        }
      }
    }
  }
  containerizingMode = "packaged"
}
