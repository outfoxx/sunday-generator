
plugins {
  id("common.conventions")
  id("publishing.conventions")
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
  testImplementation(libs.sundayKt)
  testImplementation(libs.sundayProblem)
  testImplementation(libs.javaxJaxrs)
  testImplementation(libs.jakartaJaxrs)
  testImplementation(libs.validation)
  testImplementation(libs.zalandoProblem)
  testImplementation(libs.quarkiverseProblem)
  testImplementation(libs.mutiny)
  testImplementation(libs.microprofileFaultTolerance)
  testImplementation(libs.smallryeFaultTolerance)
  testImplementation(libs.rxJava3)
  testImplementation(libs.rxJava2)
  testImplementation(libs.quarkusRest)
  testImplementation(libs.quarkiverseZanzibar)
  // END: generated code dependencies

  testImplementation(libs.slf4j)

  testImplementation(libs.junit)
  testImplementation(libs.junitParams)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)

  testImplementation(libs.hamcrest)
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
