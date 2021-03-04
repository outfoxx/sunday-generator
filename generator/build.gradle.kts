import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  jacoco
}

val slf4jVersion: String by project

val amfClientVersion: String by project

val kotlinPoetVersion: String by project
val typeScriptPoetVersion: String by project
val swiftPoetVersion: String by project
val jacksonVersion: String by project

val guavaVersion: String by project
val sundayKtVersion: String by project
val kotlinCoroutinesVersion: String by project
val jaxrsVersion: String by project
val validationVersion: String by project
val zalandoProblemVersion: String by project

val junitVersion: String by project
val hamcrestVersion: String by project
val kotlinCompileTestingVersion: String by project
val dockerJavaVersion: String by project

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

  implementation("com.google.guava:guava:$guavaVersion")
  implementation("io.outfoxx:sunday:$sundayKtVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinCoroutinesVersion")
  implementation("org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.1_spec:$jaxrsVersion")
  implementation("javax.validation:validation-api:$validationVersion")
  implementation("org.zalando:problem:$zalandoProblemVersion")
  implementation("org.zalando:jackson-datatype-problem:$zalandoProblemVersion")
  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

  implementation("org.slf4j:slf4j-jdk14:$slf4jVersion")

  //
  // TESTING
  //

  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

  testImplementation("org.hamcrest:hamcrest-library:$hamcrestVersion")

  testImplementation("com.github.docker-java:docker-java-core:$dockerJavaVersion")
  testImplementation("com.github.docker-java:docker-java-transport-httpclient5:$dockerJavaVersion")
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
