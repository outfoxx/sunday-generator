plugins {
  `java-gradle-plugin`
  id("com.gradle.plugin-publish")
}

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project

dependencies {

  shadow(gradleApi())

  implementation(project(path = ":generator", configuration = "shadow"))

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")
}

gradlePlugin {
  plugins {
    register("sunday") {
      id = "io.outfoxx.sunday-generator"
      implementationClass = "io.outfoxx.sunday.generator.gradle.SundayGeneratorPlugin"
    }
  }
}

pluginBundle {
  website = "https://outfoxx.github.io/sunday"
  vcsUrl = "https://github.com/outfoxx/sunday-generator"
  tags = setOf("sunday", "raml", "kotlin", "swift", "typescript")

  plugins {
    named("sunday") {
      displayName = "Sunday Generator - Gradle Plugin"
      description = "Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages."
    }
  }
}

tasks {
  shadowJar.configure {
    archiveClassifier.set("")
    dependencies {
      exclude(dependency(project.dependencies.gradleApi()))
      exclude(dependency("org.jetbrains.kotlin:kotlin-.*:.*"))
    }
  }
}
