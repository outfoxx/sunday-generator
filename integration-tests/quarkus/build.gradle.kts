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
  implementation("io.quarkus:quarkus-smallrye-jwt")
  implementation(libs.quarkiverseZanzibar)
  implementation("io.quarkus:quarkus-hibernate-validator")
  implementation("io.quarkus:quarkus-elytron-security-properties-file")
  testImplementation("io.smallrye:smallrye-jwt-build")
  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.quarkus:quarkus-junit5-internal")
  testImplementation("io.quarkus:quarkus-arc-deployment")
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

val zanzibarSources = layout.buildDirectory.dir("generated/zanzibar")
val zanzibarContract = layout.projectDirectory.file("src/main/openapi/security-zanzibar.yaml")
val generateZanzibarApi by tasks.registering(JavaExec::class) {
  inputs.file(zanzibarContract)
  outputs.dir(zanzibarSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-enforce-security-schemes",
    "-quarkus",
    "-pkg",
    "io.test.quarkus.zanzibar",
    "-out",
    zanzibarSources.get().asFile.absolutePath,
    zanzibarContract.asFile.absolutePath,
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
    "-quarkus",
    "-pkg",
    "io.test.quarkus.asyncapi",
    "-out",
    asyncSources.get().asFile.absolutePath,
  )
  args(asyncContracts.map { it.asFile.absolutePath })
}

val selectedSources = layout.buildDirectory.dir("generated/selected-security")
val selectedContract = layout.projectDirectory.file("src/main/openapi/selected-security.yaml")
val generateSelectedApi by tasks.registering(JavaExec::class) {
  inputs.file(selectedContract)
  outputs.dir(selectedSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-enforce-security-schemes",
    "-quarkus",
    "-pkg",
    "io.test.quarkus.selected",
    "-out",
    selectedSources.get().asFile.absolutePath,
    selectedContract.asFile.absolutePath,
  )
}

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
}

kotlin.sourceSets.main {
  kotlin.srcDir(generateApi)
  kotlin.srcDir(generateSecuredApi)
  kotlin.srcDir(generateAsyncApi)
  kotlin.srcDir(generateSelectedApi)
  kotlin.srcDir(generateZanzibarApi)
}

tasks.compileKotlin {
  dependsOn(generateAsyncApi, generateSelectedApi)
  dependsOn(generateApi, generateSecuredApi, generateZanzibarApi)
}

tasks.test {
  exclude("**/ProactiveAuthenticationConfigurationTest*")
  systemProperty("junit.jupiter.execution.parallel.enabled", "false")
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

// Quarkus requires its expected-boot-failure extension to run separately from @QuarkusTest.
val configurationTest by tasks.registering(Test::class) {
  testClassesDirs =
    sourceSets.test
      .get()
      .output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  include("**/ProactiveAuthenticationConfigurationTest*")
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
  shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(configurationTest) }

// Generated sources are compiled before the runtime tests, and retain the generator's formatting.
ktlint {
  filter {
    exclude { it.file.path.contains("/generated/") }
  }
}
