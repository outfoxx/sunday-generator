import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory

plugins {
  id("common.conventions")
  id("publishing.conventions")
  `java-test-fixtures`
}

// Fixtures are shared only inside this build; published consumers receive the production library.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
// The publishing plugin creates this documentation variant after project evaluation.
afterEvaluate {
  javaComponent.withVariantsFromConfiguration(configurations["testFixturesSourcesElements"]) { skip() }
}

// Provider versions use the same JVM packages, so exercise them in separate class loaders.
val javaxValidationRuntime by configurations.creating
val jakartaValidationRuntime by configurations.creating
val generatedCodeClasspath by configurations.creating {
  // Preserve the existing lightweight annotation fixtures without duplicate API definitions.
  exclude(group = "org.eclipse.microprofile.rest.client", module = "microprofile-rest-client-api")
  exclude(group = "org.hibernate.validator", module = "hibernate-validator")
}
configurations.testImplementation { extendsFrom(generatedCodeClasspath) }

dependencies {

  javaxValidationRuntime("org.hibernate.validator:hibernate-validator:6.2.5.Final")
  jakartaValidationRuntime("org.hibernate.validator:hibernate-validator:8.0.5.Final")

  api(libs.amfClient)

  api(libs.kotlinPoet)
  api(libs.typeScriptPoet)
  api(libs.swiftPoet)

  api(libs.jacksonYaml)
  api(libs.jacksonKotlin)

  //
  // TESTING
  //

  // START: generated code dependencies
  constraints {
    // AMF binaries require Scala 2.12; a native framework's test BOM must not replace that ABI.
    listOf("scala-library", "scala-reflect", "scala-compiler").forEach { artifact ->
      testImplementation("org.scala-lang:$artifact") {
        version { strictly("2.12.15") }
      }
    }
  }
  generatedCodeClasspath(libs.jackson)
  generatedCodeClasspath(libs.jacksonJavaTime)
  generatedCodeClasspath(libs.sundayKt)
  generatedCodeClasspath("io.outfoxx.sunday:sunday-jdk:${libs.versions.sundayKt.get()}")
  generatedCodeClasspath(libs.sundayBroker)
  generatedCodeClasspath(libs.sundayProblem)
  generatedCodeClasspath("io.outfoxx.sunday:sunday-client-quarkus:${libs.versions.sundayKt.get()}")
  generatedCodeClasspath("io.outfoxx.sunday:sunday-validation-javax:${libs.versions.sundayKt.get()}")
  generatedCodeClasspath("io.outfoxx.sunday:sunday-validation-jakarta:${libs.versions.sundayKt.get()}")
  generatedCodeClasspath(libs.javaxJaxrs)
  generatedCodeClasspath(libs.jakartaJaxrs)
  generatedCodeClasspath(libs.validation)
  generatedCodeClasspath(libs.jakartaValidation)
  generatedCodeClasspath(libs.javaxAnnotations)
  generatedCodeClasspath(libs.zalandoProblem)
  generatedCodeClasspath(libs.quarkiverseProblem)
  generatedCodeClasspath(libs.mutiny)
  generatedCodeClasspath(libs.mutinyVertxCore)
  generatedCodeClasspath(libs.microprofileFaultTolerance)
  generatedCodeClasspath(libs.microprofileJwt)
  generatedCodeClasspath(libs.smallryeFaultTolerance)
  generatedCodeClasspath(libs.rxJava3)
  generatedCodeClasspath(libs.rxJava2)
  generatedCodeClasspath(libs.quarkusRest)
  generatedCodeClasspath(libs.quarkusSecurity)
  generatedCodeClasspath("io.outfoxx.sunday:sunday-jaxrs-quarkus:${libs.versions.sundayKt.get()}")
  generatedCodeClasspath("io.quarkus:quarkus-oidc:${libs.versions.quarkus.rest.get()}")
  generatedCodeClasspath("io.quarkus:quarkus-rest-client-oidc-filter:${libs.versions.quarkus.rest.get()}")
  generatedCodeClasspath("io.quarkus:quarkus-rest-client-oidc-token-propagation:${libs.versions.quarkus.rest.get()}")
  generatedCodeClasspath("io.quarkus:quarkus-vertx-http:${libs.versions.quarkus.rest.get()}")
  generatedCodeClasspath(libs.quarkiverseZanzibar)
  // END: generated code dependencies

  testImplementation(libs.slf4j)

  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)
  testImplementation(libs.junitPlatform)

  testImplementation(libs.hamcrest)
  testImplementation("io.strikt:strikt-core:0.35.1")
  testImplementation(libs.diffutils)
  testImplementation(libs.cliktMarkdown)

  testImplementation(libs.dockerJava)
  testImplementation(libs.dockerJavaTransport)
  testImplementation(libs.kotlinCompileTesting)

  testImplementation(libs.jcolor)
  testImplementation(libs.jimfs)
}

// Copy only generated-code API stubs; exposing the entire test output defeats classpath isolation.
val compilerFixtures by tasks.registering(Sync::class) {
  dependsOn(tasks.testClasses)
  from(
    sourceSets.test
      .get()
      .output.classesDirs,
  )
  include("io/test/client/**", "io/quarkus/oidc/client/filter/**", "org/eclipse/microprofile/rest/client/**")
  into(layout.buildDirectory.dir("compiler-fixtures"))
}

val processors = Runtime.getRuntime().availableProcessors()
val memoryGiB =
  (ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean)
    ?.totalMemorySize
    ?.div(1024L * 1024 * 1024) ?: 0L
val automaticForks = minOf(8, maxOf(1, processors / 2), maxOf(1L, (memoryGiB - 4) / 4).toInt())
val compilerTestForks = providers.gradleProperty("compilerTestForks").map(String::toInt).getOrElse(automaticForks)
require(compilerTestForks in 1..automaticForks) {
  "compilerTestForks must be between 1 and $automaticForks for this host"
}
val swiftCompilerJobs =
  providers.gradleProperty("swiftCompilerJobs").map(String::toInt).getOrElse(
    minOf(
      2,
      maxOf(
        1,
        processors / compilerTestForks,
      ),
    ),
  )
require(swiftCompilerJobs > 0) { "swiftCompilerJobs must be positive" }

val instrumentationExclusions =
  listOf(
    "org.jetbrains.*",
    "com.tschuchort.*",
    "scala.*",
    "amf.*",
    "org.yaml.*",
    "org.hibernate.*",
    "io.github.classgraph.*",
    "nonapi.io.github.classgraph.*",
  )
kover {
  currentProject {
    instrumentation { excludedClasses.addAll(instrumentationExclusions) }
  }
}

tasks.withType<Test>().configureEach {
  dependsOn(compilerFixtures)
  inputs.file(rootProject.layout.projectDirectory.file("scripts/ci/partitions.json"))
  val diagnosticsDirectory =
    layout.buildDirectory
      .dir("diagnostics")
      .get()
      .asFile
  outputs.dir(diagnosticsDirectory)
  doFirst { diagnosticsDirectory.deleteRecursively() }
  // Compiler-backed fixtures retain compiler state; bound concurrency independently of host CPU count.
  maxHeapSize = "2g"
  maxParallelForks = compilerTestForks
  // AMF's Scala static initialization can deadlock when first used by concurrent test threads.
  systemProperty("junit.jupiter.execution.parallel.enabled", "false")
  systemProperty("sunday.validation.swift.jobs", swiftCompilerJobs)
  providers.environmentVariable("SUNDAY_SWIFT_PREPARED_WORKSPACE").orNull?.let { workspace ->
    require(compilerTestForks == 1) { "A prepared Swift workspace requires compilerTestForks=1" }
    systemProperty("sunday.validation.swift.workspace", workspace)
  }
  jvmArgumentProviders.add(
    objects.newInstance<GeneratedCodeClasspathArguments>().apply {
      compilerClasspath.from(layout.buildDirectory.dir("compiler-fixtures"), generatedCodeClasspath)
    },
  )
  systemProperty(
    "sunday.validation.metrics-dir",
    layout.buildDirectory
      .dir("diagnostics/compilers")
      .get()
      .asFile,
  )
  systemProperty("sunday.validation.instrumentation-exclusions", instrumentationExclusions.joinToString(","))
  listOf("compilerTimeoutSeconds", "dependencyTimeoutSeconds").forEach { name ->
    providers.gradleProperty(name).orNull?.let { value ->
      require(value.toLong() > 0) { "$name must be positive" }
      systemProperty("sunday.validation.$name", value)
    }
  }
  providers.gradleProperty("testTags").orNull?.let { expression ->
    require(expression.isNotBlank()) { "testTags must be a nonempty JUnit expression" }
    useJUnitPlatform { includeTags(expression) }
  }
  systemProperty("sunday.validation.javax.classpath", javaxValidationRuntime.asPath)
  systemProperty("sunday.validation.jakarta.classpath", jakartaValidationRuntime.asPath)
}

tasks.javadoc {
  include("io/outfoxx/**")
}

/** Resolves local companion builds after configuration while tracking compiler inputs for caching. */
abstract class GeneratedCodeClasspathArguments : org.gradle.process.CommandLineArgumentProvider {
  @get:Classpath
  abstract val compilerClasspath: ConfigurableFileCollection

  override fun asArguments(): Iterable<String> =
    listOf("-Dsunday.validation.kotlin.classpath=${compilerClasspath.asPath}")
}
