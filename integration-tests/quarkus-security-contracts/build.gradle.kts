plugins {
  id("common.conventions")
}

val generator by configurations.creating

dependencies {
  generator(project(":cli"))
  implementation(platform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.rest.get()}"))
  implementation("io.quarkus:quarkus-rest")
  implementation("io.quarkus:quarkus-oidc")
  implementation("io.quarkus:quarkus-rest-client-oidc-filter")
  implementation("io.quarkus:quarkus-rest-client-oidc-token-propagation")
  implementation("io.outfoxx.sunday:sunday-jaxrs-quarkus:${libs.versions.sundayKt.get()}")
}

listOf("first", "second", "native", "client", "web").forEach { name ->
  val output = layout.buildDirectory.dir("generated/$name")
  val generate =
    tasks.register<JavaExec>("generate${name.replaceFirstChar { it.uppercase() }}") {
      val contract = layout.projectDirectory.file("src/main/openapi/$name.yaml")
      inputs.file(contract)
      outputs.dir(output)
      doFirst {
        output.get().asFile.deleteRecursively()
        output.get().asFile.mkdirs()
      }
      classpath = generator
      mainClass.set("io.outfoxx.sunday.generator.MainKt")
      args(
        "kotlin/jaxrs",
        "-mode",
        if (name == "client") "client" else "server",
        "-quarkus",
        "-pkg",
        "io.test.packaged.$name",
        "-out",
        output.get().asFile.absolutePath,
        contract.asFile.absolutePath,
      )
      if (name != "client") args("-resource-adapters", "-enforce-security-schemes")
    }
  kotlin.sourceSets.main { kotlin.srcDir(generate) }
  val artifact =
    tasks.register<Jar>("${name}Jar") {
      dependsOn(tasks.classes)
      archiveClassifier.set(name)
      from(
        sourceSets.main
          .get()
          .output.classesDirs,
      ) { include("io/test/packaged/$name/**") }
      from(generate) { include("META-INF/**") }
    }
  configurations.create("${name}Elements") {
    isCanBeResolved = false
    isCanBeConsumed = true
    extendsFrom(configurations.implementation.get())
    outgoing.artifact(artifact)
  }
}

ktlint {
  filter { exclude { it.file.path.contains("/generated/") } }
}
