package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import io.outfoxx.sunday.generator.tools.PlacementScopeFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

/** Scope legality is shared by source declarations and relocation across every backend. */
class GeneratedPlacementScopeTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `declaration relocation retains priority for owned descendants`(kind: String) {
    val api = PlacementScopeFixture.inheritedRelocation(directory, kind)
    val config = api.placementConfiguration
    val child = api.models.single { model -> model.properties.any { it.name == "value" } }
    val override =
      GeneratedPlacementConfiguration.Relocation(
        child.declarationUri!!,
        placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.child")),
      )
    for (rules in listOf(config.relocations, config.relocations.reversed())) {
      val resolved =
        GeneratedPlacementResolver(
          config.copy(relocations = rules),
          generationLanguage = "kotlin",
        ).resolve(api)
      assertTrue(
        resolved.models.filter { it.properties.isNotEmpty() }.all {
          it.targets["kotlinClient"]!!.modelPackageName == "example.selected"
        },
      )
      val overridden =
        GeneratedPlacementResolver(
          config.copy(relocations = rules + override),
          generationLanguage = "kotlin",
        ).resolve(api)
      assertEquals(
        "example.child",
        overridden.models
          .single {
            it.declarationUri == child.declarationUri
          }.targets["kotlinClient"]!!
          .modelPackageName,
      )
    }
  }

  private fun invalidPlacements(block: String): List<GeneratedPlacement> =
    listOf("kotlin", "swift", "typescript", "python").flatMap { language ->
      val fields = mapOf((if (language in setOf("kotlin", "python")) "package" else "module") to "invalid")
      val scope = mapOf(block to fields)
      listOf(scope, mapOf("client" to scope), mapOf("server" to scope)).map {
        GeneratedPlacement.read(mapOf(language to it), "relocation")
      }
    }

  private fun rejected(
    api: GeneratedApi,
    target: String,
    kind: String?,
    block: String,
  ) {
    for (placement in invalidPlacements(block)) {
      val config =
        GeneratedPlacementConfiguration(
          relocations = listOf(GeneratedPlacementConfiguration.Relocation(target, kind, placement = placement)),
        )
      // Unselected language and role blocks must be checked too.
      val error =
        assertThrows(IllegalArgumentException::class.java) {
          GeneratedPlacementResolver(config, generationLanguage = "kotlin").resolve(api)
        }
      assertTrue(error.message!!.contains("Placement selector '$target'"), error.message)
      assertTrue(error.message!!.contains("cannot contain"), error.message)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native model and unfiltered relocations reject category defaults`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).inline()
    val model = api.models.single { model -> model.properties.any { it.name == "value" } }
    for (block in listOf("models", "services")) {
      rejected(api, model.declarationUri!!, null, block)
      rejected(api, model.declarationUri, "model", block)
      rejected(api, api.source.location, "model", block)
      rejected(api, api.source.location, null, block)
      val container = model.declarationUri.substringBeforeLast('/')
      rejected(api, container, null, block)
    }
    rejected(api, api.source.location, "service", "services")
  }

  @Test
  fun `native explicit service declaration permits model defaults without a kind filter`() {
    PlacementRegressionFixture(directory, "openapi").inline()
    val source = directory.resolve("api.yaml")
    Files.writeString(
      source,
      Files.readString(source).replace("operationId: getItems", "operationId: getItems\n      tags: [internal]") +
        "\ntags: [{name: internal}]\n",
    )
    val api = GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = true)).export(source.toUri())
    val target =
      api.services
        .single()
        .declarationUris
        .single()
    val placement =
      GeneratedPlacement(
        kotlin = GeneratedPlacement.Scope(models = GeneratedPlacement.Scope(packageName = "example.owned")),
      )
    val config =
      GeneratedPlacementConfiguration(
        relocations = listOf(GeneratedPlacementConfiguration.Relocation(target, placement = placement)),
      )
    val resolved = GeneratedPlacementResolver(config, generationLanguage = "kotlin").resolve(api)
    assertEquals(
      "example.document",
      resolved.services
        .single()
        .targets["kotlinClient"]!!
        .packageName,
    )
    assertTrue(
      resolved.models.filter { it.properties.isNotEmpty() }.all {
        it.targets["kotlinClient"]!!.modelPackageName ==
          "example.owned"
      },
    )
    rejected(api, target, null, "services")
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `native problem selectors accept direct placement and reject category defaults`(kind: String) {
    val api = PlacementScopeFixture.problems(directory, kind)
    val problem = api.problems.single()
    for (block in listOf("models", "services")) {
      rejected(api, problem.declarationUri!!, null, block)
      rejected(api, problem.declarationUri, "problem", block)
    }
    val resolved = GeneratedPlacementResolver(api.placementConfiguration, generationLanguage = "kotlin").resolve(api)
    assertEquals(
      "example.problems",
      resolved.problems
        .single()
        .targets["kotlinClient"]!!
        .modelPackageName,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native relocations retain role variants and service owned model defaults`(kind: String) {
    val api = PlacementScopeFixture(directory, kind).api
    for (role in listOf("client", "server")) {
      val resolved = GeneratedPlacementResolver(api.placementConfiguration, role, "kotlin").resolve(api)
      val target = "kotlin" + role.replaceFirstChar(Char::uppercase)
      assertEquals(
        "example.services",
        resolved.services
          .single()
          .targets[target]!!
          .packageName,
      )
      val child = resolved.models.single { model -> model.properties.any { it.name == "value" } }
      assertEquals(
        if (role ==
          "client"
        ) {
          "example.exact"
        } else {
          "example.serverexact"
        },
        child.targets[target]!!.modelPackageName,
      )
      val parent = resolved.models.single { model -> model.properties.any { it.name == "child" } }
      // AsyncAPI 3 channel messages are referenced by operations, not owned by them.
      assertEquals(
        if (kind ==
          "asyncapi"
        ) {
          "example.operation"
        } else if (role == "client") {
          "example.owned"
        } else {
          "example.serverowned"
        },
        parent.targets[target]!!.modelPackageName,
      )
    }
    for (language in listOf("swift", "typescript", "python")) {
      GeneratedPlacementResolver(api.placementConfiguration, generationLanguage = language).resolve(api)
    }
  }

  @Test
  fun `problem relocations reject category defaults but inherit model defaults`() {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml")
    val problem =
      GeneratedProblem(
        "Failure",
        source = source,
        typeUri = "https://example.test/failure",
        declarationUri = "${source.location}#/problems/Failure",
      )
    val api = GeneratedApi(name = "Problems", source = source, problems = listOf(problem))
    for (block in listOf("models", "services")) {
      rejected(api, problem.declarationUri!!, null, block)
      rejected(api, source.location, "problem", block)
    }
    val defaults =
      GeneratedPlacement(
        kotlin = GeneratedPlacement.Scope(models = GeneratedPlacement.Scope(packageName = "example.defaults")),
      )
    val config = GeneratedPlacementConfiguration(defaults = defaults)
    assertEquals(
      "example.defaults",
      GeneratedPlacementResolver(config)
        .resolve(api)
        .problems
        .single()
        .targets["kotlinClient"]!!
        .modelPackageName,
    )
    val relocated =
      config.copy(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              source.location,
              "problem",
              placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.problems")),
            ),
          ),
      )
    assertEquals(
      "example.problems",
      GeneratedPlacementResolver(relocated)
        .resolve(api)
        .problems
        .single()
        .targets["kotlinClient"]!!
        .modelPackageName,
    )
  }

  @Test
  fun `problems blocks remain unsupported in every language and role`() {
    for (language in listOf("kotlin", "swift", "typescript", "python")) {
      for (role in listOf(null, "client", "server")) {
        val scope = mapOf("problems" to mapOf("package" to "example.problems"))
        assertThrows(IllegalArgumentException::class.java) {
          GeneratedPlacement.read(mapOf(language to (role?.let { mapOf(it to scope) } ?: scope)), "placement")
        }
      }
    }
  }
}
