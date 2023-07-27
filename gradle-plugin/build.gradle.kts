
plugins {
  id("com.gradle.plugin-publish")
  id("com.github.johnrengelman.shadow")
}

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {

  shadow(gradleApi())

  implementation(project(path = ":generator"))

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
}

tasks {
  shadowJar.configure {
    dependsOn(jar)
    isZip64 = true
    archiveClassifier.set("")
    dependencies {
      exclude(dependency("org.jetbrains.kotlin:.*"))
    }
    minimize()
  }
}

gradlePlugin {
  website = "https://outfoxx.github.io/sunday"
  vcsUrl = "https://github.com/outfoxx/sunday-generator"
  plugins {
    create("sunday") {
      id = "io.outfoxx.sunday-generator"
      implementationClass = "io.outfoxx.sunday.generator.gradle.SundayGeneratorPlugin"
      displayName = "Sunday Generator - Gradle Plugin"
      description = "Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages."
      tags = setOf("sunday", "raml", "kotlin", "swift", "typescript")
    }
  }
}
