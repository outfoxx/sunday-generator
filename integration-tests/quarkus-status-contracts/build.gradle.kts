plugins {
  id("common.conventions")
}

val generator by configurations.creating

dependencies {
  generator(project(":cli"))
  implementation(platform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.rest.get()}"))
  implementation("io.quarkus:quarkus-rest-client-jackson")
  implementation("io.quarkus:quarkus-smallrye-fault-tolerance")
  implementation(libs.quarkiverseProblem)
  implementation(libs.jacksonKotlin)
  implementation(libs.jacksonCbor)
  implementation("io.outfoxx.sunday:sunday-jaxrs-quarkus:${libs.versions.sundayKt.get()}")
}

val generatedSources = layout.buildDirectory.dir("generated/status-policies")
val generateStatusPolicies by tasks.registering(JavaExec::class) {
  val contract = layout.projectDirectory.file("src/main/openapi/status-policies.yaml")
  inputs.file(contract)
  outputs.dir(generatedSources)
  val outputDirectory = generatedSources.get().asFile
  doFirst {
    outputDirectory.deleteRecursively()
    outputDirectory.mkdirs()
  }
  classpath = generator
  mainClass.set("io.outfoxx.sunday.generator.MainKt")
  args(
    "kotlin/jaxrs",
    "-mode",
    "client",
    "-quarkus",
    "-coroutines",
    "-problem-library",
    "quarkus",
    "-out",
    generatedSources.get().asFile.absolutePath,
    contract.asFile.absolutePath,
  )
}

kotlin.sourceSets.main { kotlin.srcDir(generateStatusPolicies) }
sourceSets.main { resources.srcDir(generateStatusPolicies).include("META-INF/**") }

ktlint {
  filter { exclude { it.file.path.contains("/generated/") } }
}
