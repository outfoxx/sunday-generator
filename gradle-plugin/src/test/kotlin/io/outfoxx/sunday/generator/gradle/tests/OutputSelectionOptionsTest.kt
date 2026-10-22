package io.outfoxx.sunday.generator.gradle.tests

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.gradle.SundayGenerate
import io.outfoxx.sunday.generator.gradle.SundayGenerations
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

class OutputSelectionOptionsTest {
  @Suppress("UNCHECKED_CAST")
  @Test
  fun `configuration propagates independent filters and resolves owner URIs`(
    @TempDir directory: File,
  ) {
    val project = ProjectBuilder.builder().withProjectDir(directory).build()
    project.pluginManager.apply("java")
    project.pluginManager.apply("io.outfoxx.sunday-generator")
    val generation = (project.extensions.getByName("sundayGenerations") as SundayGenerations).create("client")
    val task = project.tasks.named("sundayGenerate_client", SundayGenerate::class.java).get()
    assertTrue(task.outputCategories.get().isEmpty())
    assertTrue(task.outputOwners.get().isEmpty())
    generation.outputCategories.set(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem))
    generation.outputOwners.set(setOf("./common.yaml", "https://example.test/common.yaml"))
    assertEquals(generation.outputCategories.get(), task.outputCategories.get())
    assertEquals(
      setOf(
        directory
          .resolve("common.yaml")
          .canonicalFile
          .toPath()
          .toUri()
          .toString(),
        "https://example.test/common.yaml",
      ),
      task.resolvedOutputOwners,
    )
  }

  @Test
  fun `owner selection compiles and referenced owner changes invalidate generation`(
    @TempDir directory: File,
  ) {
    directory.resolve("settings.gradle").writeText("rootProject.name = 'owner-selection'")
    directory.resolve("build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.20'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      sundayGenerations {
        shared {
          source.set(files('api.yaml'))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
          placementConfig.set(file("placement-${'$'}{name}.yaml").with {
            text = groovy.json.JsonOutput.toJson([defaults: [kotlin: ['package': 'example']]])
            it
          })
          outputCategories.set([io.outfoxx.sunday.generator.GeneratedTypeCategory.Model])
          outputOwners.set(['common.yaml'])
          disableJacksonAnnotations.set(true)
          disableValidationConstraints.set(true)
        }
      }
      """.trimIndent(),
    )
    directory.resolve("api.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Example, version: '1'}
      paths: {}
      components:
        schemas:
          Item:
            type: object
            properties:
              shared: {${'$'}ref: './common.yaml#/components/schemas/Shared'}
      """.trimIndent(),
    )
    val common = directory.resolve("common.yaml")
    common.writeText(
      """
      openapi: 3.1.0
      info: {title: Shared, version: '1'}
      paths: {}
      components:
        schemas:
          Shared: {type: string, enum: [one, two]}
      """.trimIndent(),
    )

    fun run() =
      GradleRunner
        .create()
        .withProjectDir(directory)
        .withPluginClasspath()
        .withArguments("compileKotlin", "--stacktrace")
        .build()
    run()
    val sources =
      directory
        .resolve("build")
        .walkTopDown()
        .filter { it.extension == "kt" }
        .toList()
    assertTrue(sources.any { it.name == "Shared.kt" })
    assertFalse(sources.any { it.name == "Item.kt" })
    assertEquals(TaskOutcome.UP_TO_DATE, run().task(":sundayGenerate_shared")!!.outcome)
    common.writeText(common.readText().replace("[one, two]", "[one, two, three]"))
    assertEquals(TaskOutcome.SUCCESS, run().task(":sundayGenerate_shared")!!.outcome)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `configuration resolution base invalidates generation and cache entries`(
    kind: String,
    @TempDir directory: File,
  ) {
    val extension = if (kind == "raml") "raml" else "yaml"
    val header =
      when (kind) {
        "asyncapi2" -> "asyncapi: 2.6.0\nchannels: {}"
        "asyncapi" -> "asyncapi: 3.0.0\nchannels: {}"
        else -> "openapi: 3.1.0\npaths: {}"
      }
    directory.resolve("settings.gradle").writeText(
      "rootProject.name = 'placement-base'\nbuildCache { local { directory = file('cache') } }",
    )
    directory.resolve("build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.20'
        id 'io.outfoxx.sunday-generator'
      }
      repositories { mavenCentral() }
      sundayGenerations {
        models {
          source.set(files('api.$extension'${if (kind == "composed") ", 'events.yaml'" else ""}))
          framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.JAXRS)
          mode.set(io.outfoxx.sunday.generator.GenerationMode.Client)
          placementConfig.set(file("${'$'}{providers.gradleProperty('placementDir').get()}/placement.yaml"))
          outputCategories.set([io.outfoxx.sunday.generator.GeneratedTypeCategory.Model])
          disableJacksonAnnotations.set(true)
          disableValidationConstraints.set(true)
        }
      }
      """.trimIndent(),
    )
    for (name in listOf("a", "b")) {
      directory.resolve(name).mkdirs()
      directory.resolve("$name/placement.yaml").writeText(
        """
        defaults: {kotlin: {package: example.defaults}}
        relocations:
          - select: {target: './common.$extension#'}
            placement: {kotlin: {package: example.moved}}
        """.trimIndent(),
      )
      directory.resolve("$name/common.$extension").writeText(
        if (kind == "raml") {
          "#%RAML 1.0 Library\ntypes:\n  Shared${name.uppercase()}: {type: string, enum: [one, two]}"
        } else {
          header + "\n" +
            """
            info: {title: Shared, version: '1'}
            components:
              schemas:
                Shared${name.uppercase()}: {type: string, enum: [one, two]}
            """.trimIndent()
        },
      )
    }
    directory.resolve("api.$extension").writeText(
      if (kind == "raml") {
        """
        #%RAML 1.0
        title: Example
        uses:
          a: a/common.raml
          b: b/common.raml
        types:
          Item:
            type: object
            properties:
              a: a.SharedA
              b: b.SharedB
        """.trimIndent()
      } else {
        header + "\n" +
          """
          x-sunday-apiId: placement
          info: {title: Example, version: '1'}
          components:
            schemas:
              Item:
                type: object
                properties:
                  a: {${'$'}ref: './a/common.yaml#/components/schemas/SharedA'}
                  b: {${'$'}ref: './b/common.yaml#/components/schemas/SharedB'}
          """.trimIndent()
      },
    )
    if (kind == "composed") {
      directory.resolve("events.yaml").writeText(
        "asyncapi: 3.0.0\ninfo: {title: Events, version: '1'}\nx-sunday-apiId: placement\nchannels: {}",
      )
    }

    fun run(base: String) =
      GradleRunner
        .create()
        .withProjectDir(directory)
        .withPluginClasspath()
        .withArguments("compileKotlin", "-PplacementDir=$base", "--build-cache", "--stacktrace")
        .build()

    fun moved(): Set<String> =
      directory
        .resolve("build")
        .walkTopDown()
        .filter { it.extension == "kt" && it.parentFile.path.endsWith("example/moved") }
        .map { it.name }
        .toSet()

    assertEquals(TaskOutcome.SUCCESS, run("a").task(":sundayGenerate_models")!!.outcome)
    assertEquals(setOf("SharedA.kt"), moved())
    assertEquals(TaskOutcome.UP_TO_DATE, run("a").task(":sundayGenerate_models")!!.outcome)
    assertEquals(TaskOutcome.SUCCESS, run("b").task(":sundayGenerate_models")!!.outcome)
    assertEquals(setOf("SharedB.kt"), moved())
    assertEquals(TaskOutcome.UP_TO_DATE, run("b").task(":sundayGenerate_models")!!.outcome)
    assertEquals(TaskOutcome.FROM_CACHE, run("a").task(":sundayGenerate_models")!!.outcome)
    assertEquals(setOf("SharedA.kt"), moved())
  }
}
