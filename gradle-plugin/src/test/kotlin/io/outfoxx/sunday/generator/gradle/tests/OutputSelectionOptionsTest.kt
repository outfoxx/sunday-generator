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
          pkgName.set('example')
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
}
