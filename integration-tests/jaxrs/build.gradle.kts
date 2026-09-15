plugins {
  id("common.conventions")
}

val generator by configurations.creating

dependencies {
  generator(project(":cli"))
  implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
  implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
  implementation("jakarta.validation:jakarta.validation-api:3.1.1")
  testImplementation("org.glassfish.jersey.test-framework.providers:jersey-test-framework-provider-inmemory:3.1.11")
  testImplementation("org.glassfish.jersey.inject:jersey-hk2:3.1.11")
  testImplementation(libs.junit)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)
}

val generatedSources = layout.buildDirectory.dir("generated/sunday")
val contract = rootProject.layout.projectDirectory.file("generator/src/test/resources/openapi/ir/server-adapters.yaml")
val ramlContract =
  rootProject.layout.projectDirectory.file(
    "generator/src/test/resources/raml/ir/security-overrides.raml",
  )

val generateApi by tasks.registering(JavaExec::class) {
  inputs.files(contract, ramlContract)
  outputs.dir(generatedSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-use-jakarta-packages",
    "-pkg",
    "io.test.jaxrs.api",
    "-out",
    generatedSources.get().asFile.absolutePath,
    contract.asFile.absolutePath,
    ramlContract.asFile.absolutePath,
  )
}

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
}

kotlin.sourceSets.main {
  kotlin.srcDir(generateApi)
}

tasks.compileKotlin {
  dependsOn(generateApi)
}

tasks.test {
  systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

// Generated sources are compiled before the runtime tests, and retain the generator's formatting.
ktlint {
  filter {
    exclude { it.file.path.contains("/generated/sunday/") }
  }
}
