plugins {
  id("common.conventions")
  alias(libs.plugins.plugin.publish)
  alias(libs.plugins.shadow)
}

dependencies {

  shadow(gradleApi())

  implementation(project(path = ":generator"))

  //
  // TESTING
  //

  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)

  testImplementation(libs.hamcrest)

  testImplementation(libs.kotlinCompileTesting)
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
      description =
        """
        Sunday Generator is a code generator for Sunday HTTP clients and JAX-RS server stubs in multiple languages.
        """.trimIndent()
      tags = setOf("sunday", "raml", "kotlin", "swift", "typescript")
    }
  }
}
