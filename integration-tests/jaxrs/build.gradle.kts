plugins {
  id("common.conventions")
}

val generator by configurations.creating
val javaxModelRuntime by configurations.creating

dependencies {
  generator(project(":cli"))
  javaxModelRuntime("org.hibernate.validator:hibernate-validator:6.2.5.Final")
  javaxModelRuntime("org.glassfish:jakarta.el:3.0.3")
  implementation(libs.jakartaJaxrs31)
  implementation(libs.jakartaAnnotations)
  implementation(libs.jakartaValidation)
  implementation(libs.validation)
  implementation(libs.jackson)
  implementation("io.outfoxx.sunday:sunday-validation-jakarta:${libs.versions.sundayKt.get()}")
  implementation("io.outfoxx.sunday:sunday-validation-javax:${libs.versions.sundayKt.get()}")
  testImplementation(libs.jacksonKotlin)
  testImplementation(libs.jerseyValidation)
  testImplementation(libs.jerseyJackson)
  testImplementation(libs.jerseyInMemory)
  testImplementation(libs.jerseyHk2)
  testImplementation(libs.jerseySse)
  testImplementation(libs.jerseyGrizzly)
  testImplementation(libs.junit)
  testRuntimeOnly(libs.junitEngine)
  testRuntimeOnly(libs.junitPlatform)
}

val generatedSources = layout.buildDirectory.dir("generated/sunday")
val defaultModels = layout.buildDirectory.dir("generated/default-models")
val defaultModelsContract = layout.projectDirectory.file("src/main/openapi/default-models.yaml")

val generateDefaultModels by tasks.registering(JavaExec::class) {
  inputs.file(defaultModelsContract)
  outputs.dir(defaultModels)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/sunday",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.defaults.yaml")
      .asFile.absolutePath,
    "-out",
    defaultModels.get().asFile.absolutePath,
    defaultModelsContract.asFile.absolutePath,
  )
}

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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.api.yaml")
      .asFile.absolutePath,
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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.secure.yaml")
      .asFile.absolutePath,
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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.asyncapi.yaml")
      .asFile.absolutePath,
    "-out",
    asyncSources.get().asFile.absolutePath,
  )
  args(asyncContracts.map { it.asFile.absolutePath })
}

val uploadSources = layout.buildDirectory.dir("generated/uploads")
val uploadContract = rootProject.layout.projectDirectory.file("generator/src/test/resources/raml/ir/content-type.raml")
val generateUploadApi by tasks.registering(JavaExec::class) {
  inputs.file(uploadContract)
  outputs.dir(uploadSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-use-jakarta-packages",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.uploads.yaml")
      .asFile.absolutePath,
    "-out",
    uploadSources.get().asFile.absolutePath,
    uploadContract.asFile.absolutePath,
  )
}

val nominalSources = layout.buildDirectory.dir("generated/nominal")
val nominalContract = layout.projectDirectory.file("src/main/openapi/nominal-scalars.yaml")
val generateNominalApi by tasks.registering(JavaExec::class) {
  inputs.file(nominalContract)
  outputs.dir(nominalSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-use-jakarta-packages",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.nominal.yaml")
      .asFile.absolutePath,
    "-out",
    nominalSources.get().asFile.absolutePath,
    nominalContract.asFile.absolutePath,
  )
}

val nativePayloadContract =
  rootProject.layout.projectDirectory.file(
    "integration-tests/quarkus/src/main/openapi/native-payloads.yaml",
  )
val generateNativePayloads by tasks.registering(JavaExec::class) {
  val output = layout.buildDirectory.dir("generated/native-payloads")
  inputs.file(nativePayloadContract)
  outputs.dir(output)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-use-jakarta-packages",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.payloads.yaml")
      .asFile.absolutePath,
    "-out",
    output.get().asFile.absolutePath,
    nativePayloadContract.asFile.absolutePath,
  )
}

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
  freeCompilerArgs.addAll("-Xannotation-default-target=param-property", "-Xemit-jvm-type-annotations")
}

val anyOfSources = layout.buildDirectory.dir("generated/anyof-objects")
val anyOfContract = layout.projectDirectory.file("src/main/openapi/anyof-objects.yaml")

val generateAnyOfApi by tasks.registering(JavaExec::class) {
  inputs.file(anyOfContract)
  outputs.dir(anyOfSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-use-jakarta-packages",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.checkout.yaml")
      .asFile.absolutePath,
    "-out",
    anyOfSources.get().asFile.absolutePath,
    anyOfContract.asFile.absolutePath,
  )
}

val customTypeSources = layout.buildDirectory.dir("generated/custom-types")
val customTypeContract = layout.projectDirectory.file("src/main/openapi/custom-types.yaml")
val generateCustomTypeApi by tasks.registering(JavaExec::class) {
  inputs.file(customTypeContract)
  outputs.dir(customTypeSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-use-jakarta-packages",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.jaxrs.custom.yaml")
      .asFile.absolutePath,
    "-out",
    customTypeSources.get().asFile.absolutePath,
    customTypeContract.asFile.absolutePath,
  )
}

kotlin.sourceSets.main {
  kotlin.srcDir(generateCustomTypeApi)
  kotlin.srcDir(generateAnyOfApi)
  kotlin.srcDir(generateNativePayloads)
  kotlin.srcDir(generateNominalApi)
  kotlin.srcDir(generateDefaultModels)
  kotlin.srcDir(generateUploadApi)
  kotlin.srcDir(generateApi)
  kotlin.srcDir(generateSecuredApi)
  kotlin.srcDir(generateAsyncApi)
}

tasks.compileKotlin {
  dependsOn(generateUploadApi)
  dependsOn(generateAsyncApi)
  dependsOn(generateApi, generateSecuredApi)
}

tasks.test {
  exclude("**/ModelDefaultsTest*")
  systemProperty("junit.jupiter.execution.parallel.enabled", "false")
}

// Hibernate's javax and Jakarta providers share implementation class names and require separate JVMs.
val defaultModelTest by tasks.registering(Test::class) {
  useJUnitPlatform()
  testClassesDirs =
    sourceSets.test
      .get()
      .output.classesDirs
  classpath =
    javaxModelRuntime +
    sourceSets.test
      .get()
      .runtimeClasspath
      .filter { !it.name.startsWith("hibernate-validator-") }
  include("**/ModelDefaultsTest*")
  shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(defaultModelTest) }

// Generated sources are compiled before the runtime tests, and retain the generator's formatting.
ktlint {
  filter {
    exclude { it.file.path.contains("/generated/") }
  }
}

// Placement files participate in generation cache invalidation.
tasks.withType<JavaExec>().configureEach {
  inputs.files(fileTree("src/main/placement"))
}
