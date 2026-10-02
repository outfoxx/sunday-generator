
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
  testImplementation(libs.jackson)
  testImplementation(libs.jacksonJavaTime)
  testImplementation(libs.sundayKt)
  testImplementation("io.outfoxx.sunday:sunday-jdk:${libs.versions.sundayKt.get()}")
  testImplementation(libs.sundayBroker)
  testImplementation(libs.sundayProblem)
  testImplementation("io.outfoxx.sunday:sunday-client-quarkus:${libs.versions.sundayKt.get()}")
  testImplementation("io.outfoxx.sunday:sunday-validation-javax:${libs.versions.sundayKt.get()}")
  testImplementation("io.outfoxx.sunday:sunday-validation-jakarta:${libs.versions.sundayKt.get()}")
  testImplementation(libs.javaxJaxrs)
  testImplementation(libs.jakartaJaxrs)
  testImplementation(libs.validation)
  testImplementation(libs.jakartaValidation)
  testImplementation(libs.javaxAnnotations)
  testImplementation(libs.zalandoProblem)
  testImplementation(libs.quarkiverseProblem)
  testImplementation(libs.mutiny)
  testImplementation(libs.mutinyVertxCore)
  testImplementation(libs.microprofileFaultTolerance)
  testImplementation(libs.microprofileJwt)
  testImplementation(libs.smallryeFaultTolerance)
  testImplementation(libs.rxJava3)
  testImplementation(libs.rxJava2)
  testImplementation(libs.quarkusRest)
  testImplementation(libs.quarkusSecurity)
  testImplementation("io.quarkus:quarkus-vertx-http:${libs.versions.quarkus.rest.get()}")
  testImplementation(libs.quarkiverseZanzibar)
  // END: generated code dependencies

  testImplementation(libs.slf4j)

  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)

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

tasks.withType<Test>().configureEach {
  // Compiler-backed fixtures retain compiler state; bound concurrency independently of host CPU count.
  maxHeapSize = "2g"
  systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
  systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
  systemProperty("sunday.validation.javax.classpath", javaxValidationRuntime.asPath)
  systemProperty("sunday.validation.jakarta.classpath", jakartaValidationRuntime.asPath)
}

tasks.javadoc {
  include("io/outfoxx/**")
}
