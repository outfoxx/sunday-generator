plugins {
  id("common.conventions")
  alias(libs.plugins.plugin.publish)
  alias(libs.plugins.shadow)
}

val companionRuntime by configurations.creating
val companionQuarkusRuntime by configurations.creating

dependencies {
  companionRuntime(libs.sundayKt)
  companionQuarkusRuntime(platform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.rest.get()}"))
  companionQuarkusRuntime("io.outfoxx.sunday:sunday-jaxrs-quarkus:${libs.versions.sundayKt.get()}")
  if (providers.environmentVariable("SUNDAY_KOTLIN_PATH").isPresent) {
    companionRuntime("io.outfoxx.sunday:sunday-validation-javax:${libs.versions.sundayKt.get()}")
  }

  shadow(gradleApi())

  // GenerationMode and other generator types are part of the public DSL.
  // Source-composite/precompiled convention consumers need them at compile time.
  api(project(path = ":generator"))

  //
  // TESTING
  //

  testImplementation(testFixtures(project(":generator")))

  testImplementation(libs.kotlin.gradle.plugin)
  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)

  testImplementation(libs.hamcrest)
  testImplementation("io.strikt:strikt-core:0.35.1")

  testImplementation(libs.kotlinCompileTesting)
}

tasks {
  test {
    systemProperty("sunday.generator.source-root", rootProject.projectDir.absolutePath)
    systemProperty("sunday.kotlin.version", libs.versions.sundayKt.get())
    systemProperty(
      "quarkus.version",
      libs.versions.quarkus.rest
        .get(),
    )
    if (providers.environmentVariable("SUNDAY_KOTLIN_PATH").isPresent) {
      inputs.files(companionRuntime, companionQuarkusRuntime)
      dependsOn(companionRuntime, companionQuarkusRuntime)
      jvmArgumentProviders.add(
        objects.newInstance<CompanionRuntimeArguments>().apply {
          propertyName.set("sunday.kotlin.classpath")
          classpath.from(companionRuntime)
        },
      )
      jvmArgumentProviders.add(
        objects.newInstance<CompanionRuntimeArguments>().apply {
          propertyName.set("sunday.kotlin.quarkus.classpath")
          classpath.from(companionQuarkusRuntime)
        },
      )
    }
  }
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

/** Defers included-build artifact resolution until the test JVM is launched. */
abstract class CompanionRuntimeArguments : org.gradle.process.CommandLineArgumentProvider {
  @get:Input
  abstract val propertyName: Property<String>

  @get:Classpath
  abstract val classpath: ConfigurableFileCollection

  override fun asArguments(): Iterable<String> = listOf("-D${propertyName.get()}=${classpath.asPath}")
}
