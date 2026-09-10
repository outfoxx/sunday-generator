plugins {
  id("common.conventions")
  alias(libs.plugins.quarkus)
}

val generator by configurations.creating

dependencies {
  generator(project(":cli"))
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.rest.get()}"))
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-rest")
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-hibernate-validator")
  implementation("io.quarkus:quarkus-elytron-security-properties-file")
  testImplementation("io.quarkus:quarkus-junit5")
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
    "-quarkus",
    "-aggregate-services",
    "-aggregate-service-name",
    "TestAPI",
    "-pkg",
    "io.test.quarkus.api",
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
    "-quarkus",
    "-aggregate-services",
    "-aggregate-service-name",
    "SecureAPI",
    "-pkg",
    "io.test.quarkus.secure",
    "-out",
    securedSources.get().asFile.absolutePath,
    securedContract.asFile.absolutePath,
  )
}

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
}

kotlin.sourceSets.main {
  kotlin.srcDir(generateApi)
  kotlin.srcDir(generateSecuredApi)
}

tasks.compileKotlin {
  dependsOn(generateApi, generateSecuredApi)
}

tasks.test {
  systemProperty("junit.jupiter.execution.parallel.enabled", "false")
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// Generated sources are compiled before the runtime tests, and retain the generator's formatting.
ktlint {
  filter {
    exclude { it.file.path.contains("/generated/") }
  }
}
