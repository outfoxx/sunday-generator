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
  testImplementation("org.glassfish.jersey.media:jersey-media-sse:3.1.11")
  testImplementation("org.glassfish.jersey.test-framework.providers:jersey-test-framework-provider-grizzly2:3.1.11")
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

val securedSources = layout.buildDirectory.dir("generated/security")
val securedContract =
  rootProject.layout.projectDirectory.file(
    "generator/src/test/resources/openapi/ir/security-enforcement.yaml",
  )

val generateSecuredApi by tasks.registering(JavaExec::class) {
  inputs.file(securedContract)
  outputs.dir(securedSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-enforce-security-schemes",
    "-use-jakarta-packages",
    "-pkg",
    "io.test.jaxrs.secure",
    "-out",
    securedSources.get().asFile.absolutePath,
    securedContract.asFile.absolutePath,
  )
}

val asyncSources = layout.buildDirectory.dir("generated/async-security")
val asyncContracts =
  listOf("security-enforcement-3.yaml", "security-api-keys-2.yaml").map {
    rootProject.layout.projectDirectory.file("generator/src/test/resources/asyncapi/ir/$it")
  }
val generateAsyncApi by tasks.registering(JavaExec::class) {
  inputs.files(asyncContracts)
  outputs.dir(asyncSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-enforce-security-schemes",
    "-use-jakarta-packages",
    "-pkg",
    "io.test.jaxrs.asyncapi",
    "-out",
    asyncSources.get().asFile.absolutePath,
  )
  args(asyncContracts.map { it.asFile.absolutePath })
}

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
}

kotlin.sourceSets.main {
  kotlin.srcDir(generateApi)
  kotlin.srcDir(generateSecuredApi)
  kotlin.srcDir(generateAsyncApi)
}

tasks.compileKotlin {
  dependsOn(generateAsyncApi)
  dependsOn(generateApi, generateSecuredApi)
}

tasks.test {
  systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

// Generated sources are compiled before the runtime tests, and retain the generator's formatting.
ktlint {
  filter {
    exclude { it.file.path.contains("/generated/") }
  }
}
