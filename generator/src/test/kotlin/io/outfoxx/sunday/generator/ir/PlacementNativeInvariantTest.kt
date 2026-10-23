package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex
import io.outfoxx.sunday.generator.tools.OutputSelectionFixture
import io.outfoxx.sunday.generator.tools.PlacementNativeFixture
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/** Contract assertions inspect IR; generated-source assertions live behind the language compile gates. */
class PlacementNativeInvariantTest {
  @TempDir lateinit var directory: Path

  private fun variants(api: GeneratedApi): List<GeneratedApi> {
    val roundTrip = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
    return listOf(
      api,
      roundTrip,
      GeneratedApiComposer().compose(
        listOf(
          GeneratedApiFragment(
            roundTrip,
            GeneratedIdentity.native(
              roundTrip.id ?: roundTrip.name,
            ),
          ),
        ),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native duplicate behavior retains all original operation locations through recomposition`(kind: String) {
    val fixture = PlacementNativeFixture(directory)
    val api = fixture.duplicateOperations(kind)
    for (input in variants(api) + variants(fixture.export(reverse = true))) {
      val operation =
        input.services
          .single()
          .operations
          .single()
      assertEquals(2, operation.declarationUris().distinct().size)
      assertEquals(
        2,
        input.services
          .single()
          .ownerUris.size,
      )
      for (language in listOf("kotlin", "swift", "typescript", "python")) {
        GeneratedPlacementResolver(generationLanguage = language).resolve(input)
      }
      // One contributing document cannot silently relocate the complete, composed service.
      val rule =
        GeneratedPlacementConfiguration.Relocation(
          operation.declarationUris().first().substringBefore('#'),
          kind = "service",
          placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.partial")),
        )
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedPlacementResolver(
          GeneratedPlacementConfiguration(relocations = listOf(rule)),
          generationLanguage = "kotlin",
        ).resolve(input)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native duplicate operations reject distinct resolved payload identities`(kind: String) {
    val fixture = PlacementNativeFixture(directory)
    val api = fixture.duplicateOperations(kind, payloads = true, distinctModels = true)
    for (input in variants(api) + variants(fixture.export(reverse = true))) {
      for (language in listOf("kotlin", "swift", "typescript", "python")) {
        val error =
          assertThrows(IllegalArgumentException::class.java) {
            GeneratedPlacementResolver(generationLanguage = language).resolve(input)
          }
        assertTrue(error.message!!.contains("conflicting resolved reference identities"), error.message)
      }
    }
  }

  @Test
  fun `native shared document identity is independent of the importing frontend`() {
    for (input in variants(OutputSelectionFixture(directory, "composed").api)) {
      for (language in listOf("kotlin", "swift", "typescript", "python")) {
        val resolved = GeneratedPlacementResolver(generationLanguage = language).resolve(input)
        val shared = resolved.models.single { it.name == "Shared" }
        val index = GeneratedApiIndex(resolved)
        val item = resolved.models.single { it.name == "Item" }
        for (contribution in item.placementDeclarations) {
          assertEquals(shared, index.modelOrNull(contribution.properties.single().type))
        }
      }
    }
  }

  @Test
  fun `native nested names use each backend's emitted spelling`() {
    val api = PlacementNativeFixture(directory).distinctEnclosures(sameParent = true)
    for (input in variants(api)) {
      for (language in listOf("kotlin", "swift")) {
        val resolved = GeneratedPlacementResolver(generationLanguage = language).resolve(input)
        assertEquals(
          setOf("Nested_Child", "NestedChild"),
          resolved.models
            .filter { it.name == "Child" }
            .map { it.nested!!.name }
            .toSet(),
        )
      }
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedPlacementResolver(generationLanguage = "typescript").resolve(input)
      }
    }
  }

  @Test
  fun `native nested identities include their enclosure independent of input order and serialization`() {
    val fixture = PlacementNativeFixture(directory)
    val api = fixture.distinctEnclosures()
    for (input in variants(api) + variants(fixture.export(reverse = true))) {
      for (language in listOf("kotlin", "swift", "typescript")) {
        val resolved = GeneratedPlacementResolver(generationLanguage = language).resolve(input)
        val children = resolved.models.filter { it.name == "Child" }
        assertEquals(2, children.size)
        assertEquals(setOf("First", "Second"), children.map { it.nested!!.enclosedIn!!.name }.toSet())
        val index = GeneratedApiIndex(resolved)
        for (name in listOf("First", "Second")) {
          val parent = resolved.models.single { it.name == name }
          assertEquals(
            name,
            index
              .modelOrNull(parent.properties.single().type)!!
              .nested!!
              .enclosedIn!!
              .name,
          )
        }
      }
    }
  }

  @Test
  fun `native lexical binding survives relocation and serialization`() {
    val api = PlacementNativeFixture(directory).importedNesting()
    for (input in variants(api)) {
      val child = input.models.single { it.name == "Child" }
      assertEquals(child.source, child.nested!!.enclosedIn!!.source)
      val config =
        GeneratedPlacementConfiguration(
          relocations =
            listOf(
              GeneratedPlacementConfiguration.Relocation(
                child.source.location,
                placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.relocated")),
              ),
            ),
        )
      val resolved = GeneratedPlacementResolver(config, generationLanguage = "kotlin").resolve(input)
      val moved = resolved.models.single { it.name == "Child" }
      assertEquals(child.source, moved.nested!!.enclosedIn!!.source)
      assertEquals("example.relocated", moved.targets["kotlinClient"]!!.modelPackageName)
      assertEquals(
        "example.second",
        resolved.models
          .single {
            it.name == "Holder"
          }.targets["kotlinClient"]!!
          .modelPackageName,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native reference identity is invariant under relocation and rule ordering`(kind: String) {
    val api = PlacementRegressionFixture(directory, kind).localAndImported()
    for (input in variants(api)) {
      val holder = input.models.single { it.name == "Holder" }
      val declarations = GeneratedModelIndex(input.models.flatMap { it.placementDeclarations.ifEmpty { listOf(it) } })
      val bindings = holder.properties.map { requireNotNull(declarations.modelOrNull(it.type)).source }
      val rules =
        input.models
          .flatMap {
            it.placementDeclarations.ifEmpty {
              listOf(
                it,
              )
            }
          }.filter { it.name == "Record" }
          .mapIndexed {
            index,
            model,
            ->
            GeneratedPlacementConfiguration.Relocation(
              model.declarationUri!!,
              placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.moved$index")),
            )
          }
      val resolved =
        listOf(rules, rules.reversed()).map {
          GeneratedPlacementResolver(
            GeneratedPlacementConfiguration(relocations = it),
            generationLanguage = "kotlin",
          ).resolve(input)
        }
      assertEquals(resolved.first(), resolved.last())
      for (result in resolved) {
        val index = GeneratedApiIndex(result)
        assertEquals(
          bindings,
          result.models
            .single {
              it.name == "Holder"
            }.properties
            .map { index.modelOrNull(it.type)!!.source },
        )
        assertTrue(
          result.models
            .filter {
              it.name == "Record"
            }.all { it.targets["kotlinClient"]!!.modelPackageName!!.startsWith("example.moved") },
        )
      }
    }
  }
}
