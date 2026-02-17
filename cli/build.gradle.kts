import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.gradle.api.publish.maven.MavenPublication

plugins {
  application
  alias(libs.plugins.jib)
  alias(libs.plugins.shadow)
  alias(libs.plugins.graalNative)
}

dependencies {

  implementation(project(path = ":generator"))

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
val isSnapshot = releaseVersion.endsWith("SNAPSHOT")

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
    archiveClassifier.set("")
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

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  if (!isSnapshot) {
    signAllPublications()
  }

  coordinates(
    groupId = "io.outfoxx.sunday",
    artifactId = "cli",
    version = releaseVersion,
  )

  pom {

    name.set("Sunday Generator - CLI")
    description.set(
      "Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages.",
    )
    url.set("https://outfoxx.github.io/sunday-generator")

    organization {
      name.set("Outfox, Inc.")
      url.set("https://outfoxx.io")
    }

    issueManagement {
      system.set("GitHub")
      url.set("https://github.com/outfoxx/sunday-generator/issues")
    }

    licenses {
      license {
        name.set("Apache License 2.0")
        url.set("https://raw.githubusercontent.com/outfoxx/sunday-generator/main/LICENSE.txt")
        distribution.set("repo")
      }
    }

    scm {
      url.set("https://github.com/outfoxx/sunday-generator")
      connection.set("scm:https://github.com/outfoxx/sunday-generator.git")
      developerConnection.set("scm:git@github.com:outfoxx/sunday-generator.git")
    }

    developers {
      developer {
        id.set("kdubb")
        name.set("Kevin Wooten")
        email.set("kevin@outfoxx.io")
      }
    }
  }

  configureBasedOnAppliedPlugins()
}

publishing {
  publications.withType<MavenPublication>().configureEach {
    if (name != "maven") {
      return@configureEach
    }
    val plainJarArtifacts = artifacts.filter { it.classifier == null && it.extension == "jar" }
    plainJarArtifacts.forEach { artifacts.remove(it) }
    artifact(tasks.named("shadowJar")) {
      classifier = null
    }
  }
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
  }
  containerizingMode = "packaged"
}

gradle.taskGraph.addTaskExecutionGraphListener {
  jib.from.platforms {
    platform {
      os = "linux"
      architecture = "arm64"
    }
    if (!it.hasTask(":cli:jibDockerBuild")) {
      logger.warn("JIB: Enabling Multi-Platform Images")
      platform {
        os = "linux"
        architecture = "amd64"
      }
    }
  }
}
