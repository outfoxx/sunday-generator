package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GeneratedDocumentUri
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.outputOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GeneratedOutputProvenanceTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `external schemas retain document ownership through bundling and IR serialization`() {
    val shared =
      Files.writeString(
        directory.resolve("shared.yaml"),
        """
        openapi: 3.1.0
        info: {title: Shared, version: '1'}
        paths: {}
        components:
          schemas:
            Shared:
              type: object
              properties: {value: {type: string}}
        """.trimIndent(),
      )
    Files.createSymbolicLink(directory.resolve("alias.yaml"), shared)
    val service =
      Files.writeString(
        directory.resolve("service.yaml"),
        """
        openapi: 3.1.0
        info: {title: Service, version: '1'}
        paths: {}
        components:
          schemas:
            Item:
              type: object
              properties:
                first: {${'$'}ref: './shared.yaml#/components/schemas/Shared'}
                second: {${'$'}ref: '././shared.yaml#/components/schemas/Shared'}
                third: {${'$'}ref: './alias.yaml#/components/schemas/Shared'}
        """.trimIndent(),
      )
    val api = GeneratedApiIrExporter().export(service.toUri())
    val models = api.models.associateBy { it.name }
    assertEquals(GeneratedDocumentUri.canonical(shared.toUri()).toString(), models.getValue("Shared").source.location)
    assertEquals(GeneratedDocumentUri.canonical(service.toUri()).toString(), models.getValue("Item").source.location)
    assertEquals(api, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)))
    assertEquals(1, api.models.count { it.name == "Shared" })
    assertTrue(models.getValue("Shared").declarationUri!!.endsWith("#/components/schemas/Shared"))

    val captured = OpenApiReferenceResolver().resolve(service.toUri())
    val snapshot = directory.resolve("snapshot")
    OpenApiDocumentSnapshot.write(snapshot, directory, captured.documents)
    val relocated = directory.resolve("relocated")
    val restored =
      GeneratedApiIrExporter(openApiDocumentLoader = OpenApiDocumentSnapshot.loader(snapshot, relocated))
        .export(relocated.resolve("service.yaml").toUri())
    assertEquals(1, restored.models.count { it.name == "Shared" })
    assertEquals(
      GeneratedDocumentUri.canonical(relocated.resolve("shared.yaml").toUri()).toString(),
      restored.models
        .single { it.name == "Shared" }
        .source
        .location,
    )
  }

  @Test
  fun `deduplicated declarations retain ambiguous owners in either input order`() {
    val fragments =
      listOf("first", "second").map { name ->
        val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/$name.yaml")
        GeneratedApiFragment(
          api =
            GeneratedApi(
              name = "Example",
              source = source,
              models = listOf(GeneratedModel("Shared", GeneratedModel.Kind.OBJECT, source = source)),
            ),
          apiId = GeneratedIdentity.native("example"),
        )
      }
    val selection = GeneratedOutputSelection(ownerUris = setOf(java.net.URI("https://example.test/first.yaml")))
    val messages =
      listOf(fragments, fragments.reversed()).map { ordered ->
        val api = GeneratedApiComposer().compose(ordered)
        val restored = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
        val origin = restored.models.single().outputOrigin(restored)
        assertTrue(GeneratedOutputSelection().includes(origin.category, origin.ownerUris))
        assertThrows(IllegalArgumentException::class.java) {
          selection.includes(origin.category, origin.ownerUris)
        }.message
      }
    assertEquals(messages.first(), messages.last())
    assertTrue(messages.first()!!.contains("Ambiguous declaration ownership"))
  }
}
