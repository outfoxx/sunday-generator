
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

dependencies {

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
  testImplementation(libs.jackson)
  testImplementation(libs.jacksonJavaTime)
  testImplementation(libs.sundayKt)
  testImplementation(libs.sundayBroker)
  testImplementation(libs.sundayProblem)
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

tasks.javadoc {
  include("io/outfoxx/**")
}
