import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  jacoco
}

val amfClientVersion: String by project

val kotlinPoetVersion: String by project
val typeScriptPoetVersion: String by project
val swiftPoetVersion: String by project
val jacksonVersion: String by project

val retrofitVersion: String by project
val jaxrsVersion: String by project
val validationVersion: String by project
val zalandoProblemVersion: String by project

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project

configurations.compileClasspath {
  resolutionStrategy {
    force("org.scala-lang:scala-library:2.12.10")
  }
}

repositories {
  jcenter()
}

dependencies {

  api("com.github.amlorg:amf-client_2.12:$amfClientVersion")

  api("com.squareup:kotlinpoet:$kotlinPoetVersion")
  api("io.outfoxx:typescriptpoet:$typeScriptPoetVersion")
  api("io.outfoxx:swiftpoet:$swiftPoetVersion")

  implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
  implementation("org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.1_spec:$jaxrsVersion")
  implementation("javax.validation:validation-api:$validationVersion")
  implementation("org.zalando:problem:$zalandoProblemVersion")
  implementation("org.zalando:jackson-datatype-problem:$zalandoProblemVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.tschuchortdev:kotlin-compile-testing:$kotlinCompileTestingVersion")

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
