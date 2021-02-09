import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  application
  jacoco
  id("com.google.cloud.tools.jib")
}

val cliktVersion: String by project

val junitVersion: String by project
val hamcrestVersion: String by project

configurations.compileClasspath {
  resolutionStrategy {
    force("org.scala-lang:scala-library:2.12.10")
  }
}

repositories {
  jcenter()
}

dependencies {

  implementation(project(":generator"))

  implementation("com.github.ajalt.clikt:clikt:$cliktVersion")

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

}

//kotlin {
//  explicitApi()
//}

tasks {

  withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
  }

  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
      javaParameters = true
      freeCompilerArgs = freeCompilerArgs + "-Xuse-experimental=kotlin.ExperimentalUnsignedTypes"
    }
  }

  test {
    useJUnitPlatform()
  }

}


application {
  applicationName = "sunday-generator"
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
}


jib {
  to {
    image = "docker.pkg.github.com/outfoxx/sunday-generator/sunday-generator:${project.version}"
    auth {
      username = project.findProperty("github.user") as String? ?: System.getenv("USERNAME")
      password = project.findProperty("github.token") as String? ?: System.getenv("GITHUB_TOKEN")
    }
  }
  from {
    image = "openjdk:14-jdk-alpine"
  }
  containerizingMode = "packaged"
}
