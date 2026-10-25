plugins {
  id("common.conventions")
  alias(libs.plugins.quarkus)
}

val generator by configurations.creating

dependencies {
  generator(project(":cli"))
  implementation(libs.jackson)
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.rest.get()}"))
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-rest")
  implementation("io.quarkus:quarkus-rest-jackson")
  implementation("io.quarkus:quarkus-rest-client-jackson")
  implementation("io.quarkus:quarkus-rest-client-oidc-filter")
  implementation("io.quarkus:quarkus-smallrye-fault-tolerance")
  implementation(libs.quarkiverseProblem)
  implementation("io.outfoxx.sunday:sunday-jaxrs-quarkus:${libs.versions.sundayKt.get()}")
  implementation("io.outfoxx.sunday:sunday-client-quarkus:${libs.versions.sundayKt.get()}")
  implementation(libs.jacksonKotlin)
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
  if (providers.systemProperty("os.name").get().startsWith("Mac")) {
    val architecture = if (providers.systemProperty("os.arch").get() == "aarch64") "aarch_64" else "x86_64"
    testRuntimeOnly("io.netty:netty-resolver-dns-native-macos::osx-$architecture")
  }
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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.api.yaml")
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
    "-quarkus",
    "-aggregate-services",
    "-aggregate-service-name",
    "SecureAPI",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.secure.yaml")
      .asFile.absolutePath,
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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.zanzibar.yaml")
      .asFile.absolutePath,
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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.asyncapi.yaml")
      .asFile.absolutePath,
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
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.selected.yaml")
      .asFile.absolutePath,
    "-out",
    selectedSources.get().asFile.absolutePath,
    selectedContract.asFile.absolutePath,
  )
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
    "-quarkus",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.uploads.yaml")
      .asFile.absolutePath,
    "-out",
    uploadSources.get().asFile.absolutePath,
    uploadContract.asFile.absolutePath,
  )
}

val validationSources = layout.buildDirectory.dir("generated/validation")
val validationContract =
  rootProject.layout.projectDirectory.file("generator/src/test/resources/openapi/ir/cascaded-validation.yaml")
val generateValidationApi by tasks.registering(JavaExec::class) {
  inputs.file(validationContract)
  outputs.dir(validationSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-quarkus",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.validation.yaml")
      .asFile.absolutePath,
    "-out",
    validationSources.get().asFile.absolutePath,
    validationContract.asFile.absolutePath,
  )
}

val closedModelSources = layout.buildDirectory.dir("generated/closed-models")
val closedModelContract =
  rootProject.layout.projectDirectory.file("generator/src/test/resources/openapi/ir/closed-models.yaml")
val generateClosedModelApi by tasks.registering(JavaExec::class) {
  inputs.file(closedModelContract)
  outputs.dir(closedModelSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-quarkus",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.closed.yaml")
      .asFile.absolutePath,
    "-out",
    closedModelSources.get().asFile.absolutePath,
    closedModelContract.asFile.absolutePath,
  )
}

val freeformModelSources = layout.buildDirectory.dir("generated/freeform-objects")
val freeformModelContract =
  rootProject.layout.projectDirectory.file("generator/src/test/resources/openapi/ir/freeform-objects.yaml")
val generateFreeformModelApi by tasks.registering(JavaExec::class) {
  inputs.file(freeformModelContract)
  outputs.dir(freeformModelSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-resource-adapters",
    "-quarkus",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.freeform.yaml")
      .asFile.absolutePath,
    "-out",
    freeformModelSources.get().asFile.absolutePath,
    freeformModelContract.asFile.absolutePath,
  )
}

val policyContract = layout.projectDirectory.file("src/main/openapi/scoped-policies.yaml")
val policyGenerators =
  listOf("server", "client").map { role ->
    tasks.register<JavaExec>("generate${role.replaceFirstChar { it.uppercase() }}Policies") {
      val output = layout.buildDirectory.dir("generated/policies-$role")
      inputs.file(policyContract)
      outputs.dir(output)
      classpath = generator
      mainClass.set("io.outfoxx.sunday.generator.MainKt")
      args(
        "kotlin/jaxrs",
        "-mode",
        role,
        "-quarkus",
        "-profile",
        "internal",
        "--placement-config",
        layout.projectDirectory
          .file("src/main/placement/io.test.quarkus.policies.$role.yaml")
          .asFile.absolutePath,
        "-out",
        output.get().asFile.absolutePath,
      )
      if (role == "server") args("-resource-adapters")
      args(policyContract.asFile.absolutePath)
    }
  }

val nativePayloadContract = layout.projectDirectory.file("src/main/openapi/native-payloads.yaml")
val nativePayloadGenerators =
  listOf("server", "client").map { role ->
    tasks.register<JavaExec>("generate${role.replaceFirstChar { it.uppercase() }}NativePayloads") {
      val output = layout.buildDirectory.dir("generated/native-payloads-$role")
      inputs.file(nativePayloadContract)
      outputs.dir(output)
      classpath = generator
      mainClass.set("io.outfoxx.sunday.generator.MainKt")
      args(
        "kotlin/jaxrs",
        "-mode",
        role,
        "-quarkus",
        "--placement-config",
        layout.projectDirectory
          .file("src/main/placement/io.test.quarkus.payloads.$role.yaml")
          .asFile.absolutePath,
        "-out",
        output.get().asFile.absolutePath,
      )
      if (role == "server") args("-resource-adapters")
      args(nativePayloadContract.asFile.absolutePath)
    }
  }

val optionalBodyContract = layout.projectDirectory.file("src/main/openapi/optional-bodies.yaml")
val optionalBodyGenerators =
  listOf("server", "client").map { role ->
    tasks.register<JavaExec>("generate${role.replaceFirstChar { it.uppercase() }}OptionalBodies") {
      group = "code generation"
      description = "Generates the optional request body $role fixture."
      val output = layout.buildDirectory.dir("generated/optional-bodies-$role")
      inputs.file(optionalBodyContract)
      outputs.dir(output)
      classpath = generator
      mainClass.set("io.outfoxx.sunday.generator.MainKt")
      args(
        "kotlin/jaxrs",
        "-mode",
        role,
        "-quarkus",
        "--placement-config",
        layout.projectDirectory
          .file("src/main/placement/io.test.quarkus.optional.$role.yaml")
          .asFile.absolutePath,
        "-out",
        output.get().asFile.absolutePath,
      )
      if (role == "server") args("-resource-adapters", "-coroutines")
      args(optionalBodyContract.asFile.absolutePath)
    }
  }

val profiledClientSources = layout.buildDirectory.dir("generated/profiled-client")
val profiledClientContract = layout.projectDirectory.file("src/main/openapi/profiled-client.yaml")
val generateProfiledClient by tasks.registering(JavaExec::class) {
  inputs.file(profiledClientContract)
  outputs.dir(profiledClientSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "client",
    "-quarkus",
    "-profile",
    "internal",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.profiled.yaml")
      .asFile.absolutePath,
    "-out",
    profiledClientSources.get().asFile.absolutePath,
    profiledClientContract.asFile.absolutePath,
  )
}
tasks.processResources {
  dependsOn(generateProfiledClient)
  from(profiledClientSources) { include("META-INF/services/**") }
}

val sseSources = layout.buildDirectory.dir("generated/sse")
val sseContract = rootProject.layout.projectDirectory.file("generator/src/test/resources/sse-response-modes/api.yaml")
val generateSseApi by tasks.registering(JavaExec::class) {
  inputs.file(sseContract)
  outputs.dir(sseSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-quarkus",
    "-resource-adapters",
    "--placement-config",
    layout.projectDirectory
      .file("src/main/placement/io.test.quarkus.sse.yaml")
      .asFile.absolutePath,
    "-out",
    sseSources.get().asFile.absolutePath,
    sseContract.asFile.absolutePath,
  )
}

val inheritedProblemSources = layout.buildDirectory.dir("generated/inherited-problems")
val inheritedProblemContract =
  rootProject.layout.projectDirectory.file("generator/src/test/resources/openapi/ir/inherited-problem.yaml")
val generateInheritedProblems by tasks.registering(JavaExec::class) {
  inputs.file(inheritedProblemContract)
  outputs.dir(inheritedProblemSources)
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "server",
    "-quarkus",
    "-use-jakarta-packages",
    "-problem-library",
    "quarkus",
    "-category",
    "models",
    "-category",
    "problems",
    "-out",
    inheritedProblemSources.get().asFile.absolutePath,
    inheritedProblemContract.asFile.absolutePath,
  )
}

kotlin.compilerOptions {
  allWarningsAsErrors.set(true)
  freeCompilerArgs.addAll("-Xannotation-default-target=param-property", "-Xemit-jvm-type-annotations")
}

kotlin.sourceSets.main {
  kotlin.srcDir(generateInheritedProblems)
  kotlin.srcDir(generateSseApi)
  optionalBodyGenerators.forEach { kotlin.srcDir(it) }
  nativePayloadGenerators.forEach { kotlin.srcDir(it) }
  kotlin.srcDir(generateProfiledClient)
  policyGenerators.forEach { kotlin.srcDir(it) }
  kotlin.srcDir(generateClosedModelApi)
  kotlin.srcDir(generateFreeformModelApi)
  kotlin.srcDir(generateValidationApi)
  kotlin.srcDir(generateUploadApi)
  kotlin.srcDir(generateApi)
  kotlin.srcDir(generateSecuredApi)
  kotlin.srcDir(generateAsyncApi)
  kotlin.srcDir(generateSelectedApi)
  kotlin.srcDir(generateZanzibarApi)
}

tasks.compileKotlin {
  dependsOn(generateInheritedProblems)
  dependsOn(generateSseApi)
  dependsOn(optionalBodyGenerators)
  dependsOn(nativePayloadGenerators)
  dependsOn(generateProfiledClient)
  dependsOn(policyGenerators)
  dependsOn(generateClosedModelApi)
  dependsOn(generateFreeformModelApi)
  dependsOn(generateValidationApi)
  dependsOn(generateUploadApi)
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

// Placement files participate in generation cache invalidation.
tasks.withType<JavaExec>().configureEach {
  inputs.files(fileTree("src/main/placement"))
}
