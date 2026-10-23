package io.outfoxx.sunday.generator.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GeneratedPlacementTest {
  @TempDir lateinit var directory: Path

  private fun placement(vararg values: Pair<String, Any>) =
    GeneratedPlacement.read(mapOf("kotlin" to mapOf(*values)), "test")

  private fun api(scopes: List<GeneratedPlacementScope> = emptyList()) =
    GeneratedApi(
      name = "Test",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
      models =
        listOf(
          GeneratedModel(
            "Money",
            GeneratedModel.Kind.OBJECT,
            declarationUri = "https://example.test/common.yaml#/components/schemas/Money",
            placementScopes = scopes,
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
          ),
        ),
    )

  @Test
  fun `external import identities remain separate while other languages ignore them`() {
    val first =
      api().models.single().copy(
        targets = mapOf("typescript" to GeneratedTarget(typeName = "Identifier", moduleName = "@first/domain")),
      )
    val second =
      first.copy(
        source = first.source.copy(location = "https://example.test/other.yaml"),
        declarationUri = "https://example.test/other.yaml#/components/schemas/Money",
        targets = mapOf("typescript" to GeneratedTarget(typeName = "Identifier", moduleName = "@second/domain")),
      )
    val input = api().copy(models = listOf(first.copy(placementDeclarations = listOf(first, second))))
    val resolved = GeneratedPlacementResolver(generationLanguage = "typescript").resolve(input)
    assertEquals(
      setOf("@first/domain", "@second/domain"),
      resolved.models
        .map {
          it.targets["typescript"]!!.moduleName
        }.toSet(),
    )
    assertEquals(1, GeneratedPlacementResolver(generationLanguage = "kotlin").resolve(input).models.size)
  }

  @Test
  fun `RAML resource contributions cannot silently choose a service placement`() {
    javaClass.getResourceAsStream("/sunday.raml")!!.use { Files.copy(it, directory.resolve("sunday.raml")) }
    val source = directory.resolve("resources.raml")
    Files.writeString(
      source,
      """
      #%RAML 1.0
      title: Resource Placement
      uses:
        sunday: sunday.raml
      (sunday.placement):
        kotlin: {package: example.api}
      /first:
        (sunday.placement):
          kotlin: {package: example.first}
        get:
          responses: {204: {}}
      /second:
        get:
          responses: {204: {}}
      """.trimIndent(),
    )
    val converted =
      RamlToGeneratedApi().convert(
        io.outfoxx.sunday.generator.utils.TestAPIProcessing
          .process(source.toUri()),
      )
    assertEquals(1, converted.services.size)
    assertThrows(IllegalArgumentException::class.java) { GeneratedPlacementResolver().resolve(converted) }
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              source.toUri().toString(),
              kind = "service",
              placement = placement("package" to "example.unified"),
            ),
          ),
      )
    val resolved = GeneratedPlacementResolver(config).resolve(converted)
    assertEquals(
      "example.unified",
      resolved.services
        .single()
        .targets["kotlinClient"]
        ?.packageName,
    )
  }

  @Test
  fun `Swift module cycles fail while same module recursion is supported`() {
    fun model(
      name: String,
      dependency: String,
      module: String,
    ) = GeneratedModel(
      name,
      GeneratedModel.Kind.OBJECT,
      declarationUri = "https://example.test/api.yaml#/types/$name",
      properties = listOf(GeneratedModelProperty("next", GeneratedTypeRef.named(dependency))),
      placementScopes =
        listOf(
          GeneratedPlacementScope(
            "https://example.test/api.yaml#/types/$name",
            GeneratedPlacement(swift = GeneratedPlacement.Scope(module = module)),
          ),
        ),
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
    )
    val input = api().copy(models = listOf(model("A", "B", "First"), model("B", "A", "Second")))
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementResolver(generationLanguage = "swift").resolve(input)
    }
    GeneratedPlacementResolver(generationLanguage = "swift").resolve(
      input.copy(models = listOf(model("A", "B", "Together"), model("B", "A", "Together"))),
    )
  }

  @Test
  fun `encoded pointers preserve slash tilde percent and space identities`() {
    val pointer = "/components/schemas/A~1B~0C %"
    val location = sourceDeclarationUri("https://example.test/common.yaml", pointer)
    val input =
      api().copy(
        models =
          listOf(
            GeneratedModel(
              "Money",
              GeneratedModel.Kind.OBJECT,
              declarationUri = location,
              source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
            ),
          ),
      )
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(location, placement = placement("package" to "escaped")),
          ),
      )
    assertEquals(pointer, java.net.URI(location).fragment)
    assertEquals(
      "escaped",
      GeneratedPlacementResolver(config)
        .resolve(input)
        .models
        .single()
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementConfiguration.Relocation(
        "https://example.test/common.yaml#/bad~2escape",
        placement = placement(),
      )
    }
  }

  @Test
  fun `nested types inherit enclosure and reject separate relocation`() {
    val source = "https://example.test/common.yaml"
    val parent =
      GeneratedModel(
        "Parent",
        GeneratedModel.Kind.OBJECT,
        declarationUri = "$source#/types/Parent",
        placementScopes = listOf(GeneratedPlacementScope("$source#/types/Parent", placement("package" to "enclosing"))),
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
      )
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        declarationUri = "$source#/types/Child",
        nested = GeneratedNestedType(enclosedIn = GeneratedTypeRef.named("Parent"), name = "Child"),
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
      )
    val input = api().copy(models = listOf(parent, child))
    val resolved = GeneratedPlacementResolver().resolve(input)
    assertEquals(
      "enclosing",
      resolved.models
        .last()
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
    val relocation =
      GeneratedPlacementConfiguration.Relocation(
        "$source#/types/Child",
        placement =
          placement(
            "package" to "elsewhere",
          ),
      )
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = listOf(relocation))).resolve(input)
    }
  }

  @Test
  fun `kind filters do not break ties or override a more specific selector`() {
    val document = "https://example.test/common.yaml"
    val rules =
      listOf(
        GeneratedPlacementConfiguration.Relocation(
          document,
          kind = "model",
          placement = placement("package" to "document"),
        ),
        GeneratedPlacementConfiguration.Relocation(
          "$document#/components/schemas",
          placement =
            placement(
              "package" to "container",
            ),
        ),
      )
    for (ordered in listOf(rules, rules.reversed())) {
      val result = GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = ordered)).resolve(api())
      assertEquals(
        "container",
        result.models
          .single()
          .targets["kotlinClient"]
          ?.modelPackageName,
      )
    }
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementResolver(
        GeneratedPlacementConfiguration(
          relocations =
            listOf(
              rules.first(),
              rules.first().copy(kind = null, placement = placement("package" to "conflict")),
            ),
        ),
      ).resolve(api())
    }
  }

  @Test
  fun `RAML problems retain declaration placement`() {
    javaClass.getResourceAsStream("/sunday.raml")!!.use { Files.copy(it, directory.resolve("sunday.raml")) }
    val source = directory.resolve("problems.raml")
    Files.writeString(
      source,
      """
      #%RAML 1.0
      title: Problems
      uses:
        sunday: sunday.raml
      (sunday.problemTypes):
        missing:
          status: 404
          title: Missing
          detail: Not found
          (sunday.placement):
            kotlin: {package: example.errors}
      """.trimIndent(),
    )
    val converted =
      RamlToGeneratedApi().convert(
        io.outfoxx.sunday.generator.utils.TestAPIProcessing
          .process(source.toUri()),
      )
    val resolved = GeneratedPlacementResolver().resolve(converted)
    assertEquals(
      "example.errors",
      resolved.problems
        .single()
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
  }

  @Test
  fun `role precedes category within one scope but type precedes document`() {
    val document =
      placement(
        "package" to "base",
        "models" to mapOf("package" to "models"),
        "client" to mapOf("package" to "client"),
      )
    assertEquals("client", document.kotlin!!.resolve("client", "model").packageName)
    assertEquals("models", document.kotlin.resolve("server", "model").packageName)
    val resolved =
      GeneratedPlacementResolver().resolve(
        api(
          listOf(
            GeneratedPlacementScope("https://example.test/common.yaml", document),
            GeneratedPlacementScope(
              "https://example.test/common.yaml#/components/schemas/Money",
              placement(
                "package" to "special",
              ),
            ),
          ),
        ),
      )
    assertEquals(
      "special",
      resolved.models
        .single()
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
    assertEquals(
      "special",
      resolved.models
        .single()
        .targets["kotlinServer"]
        ?.modelPackageName,
    )
  }

  @Test
  fun `relocation specificity is independent of rule order`() {
    val rules =
      listOf(
        GeneratedPlacementConfiguration.Relocation(
          "https://example.test/common.yaml",
          placement =
            placement(
              "package" to "shared",
            ),
        ),
        GeneratedPlacementConfiguration.Relocation(
          "https://example.test/common.yaml#/components/schemas/Money",
          placement =
            placement(
              "package" to "money",
            ),
        ),
      )
    for (ordered in listOf(rules, rules.reversed())) {
      val result = GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = ordered)).resolve(api())
      assertEquals(
        "money",
        result.models
          .single()
          .targets["kotlinClient"]
          ?.modelPackageName,
      )
    }
  }

  @Test
  fun `equally specific conflicting relocations fail`() {
    val target = "https://example.test/common.yaml#"
    val rules =
      listOf("first", "second").map {
        GeneratedPlacementConfiguration.Relocation(target, placement = placement("package" to it))
      }
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = rules)).resolve(api())
    }
  }

  @Test
  fun `pointer selectors do not match sibling prefixes`() {
    val rule =
      GeneratedPlacementConfiguration.Relocation(
        "https://example.test/common.yaml#/components/schemas/Mon",
        placement =
          placement(
            "package" to "wrong",
          ),
      )
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = listOf(rule))).resolve(api())
    }
  }

  @Test
  fun `configuration separates encoded reference fragments from local filenames`() {
    val config = directory.resolve("placement.yaml")
    Files.writeString(
      config,
      """
      defaults:
        kotlin: {package: fallback}
      relocations:
        - select: {target: './common.yaml#/components/schemas/Money'}
          placement:
            kotlin: {package: shared}
      """.trimIndent(),
    )
    val parsed = GeneratedPlacementConfiguration.read(config)
    assertEquals(
      io.outfoxx.sunday.generator.GeneratedDocumentUri
        .canonical(directory.resolve("common.yaml").toUri())
        .toString() +
        "#/components/schemas/Money",
      parsed.relocations.single().target,
    )
    assertEquals("fallback", parsed.defaults.kotlin?.packageName)
  }

  @Test
  fun `invalid source metadata fails instead of falling back`() {
    for (value in listOf(null, "", "bad-name", "a..b")) {
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedPlacement.read(mapOf("kotlin" to mapOf("package" to value)), "test")
      }
    }
    assertThrows(IllegalArgumentException::class.java) { placement("unknown" to "value") }
    assertThrows(IllegalArgumentException::class.java) {
      placement(
        "client" to mapOf("server" to emptyMap<String, Any>()),
      )
    }
  }

  @Test
  fun `placement survives IR serialization`() {
    val original =
      api(
        listOf(
          GeneratedPlacementScope(
            "https://example.test/common.yaml",
            placement(
              "package" to "shared",
            ),
          ),
        ),
      )
    assertEquals(original, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(original)))
  }

  @Test
  fun `external schema defaults are retained without adopting consumer placement`() {
    Files.writeString(
      directory.resolve("common.yaml"),
      """
      openapi: 3.1.0
      info: {title: Common, version: '1'}
      paths: {}
      x-sunday-placement:
        kotlin: {package: shared}
      components:
        schemas:
          Money:
            type: object
            properties: {amount: {type: number}}
      """.trimIndent(),
    )
    val consumer = directory.resolve("consumer.yaml")
    Files.writeString(
      consumer,
      """
      openapi: 3.1.0
      info: {title: Consumer, version: '1'}
      paths: {}
      x-sunday-placement:
        kotlin: {package: consumer}
      components:
        schemas:
          Item:
            type: object
            properties:
              money: {${'$'}ref: './common.yaml#/components/schemas/Money'}
      """.trimIndent(),
    )
    val result = GeneratedPlacementResolver().resolve(GeneratedApiIrExporter().export(consumer.toUri()))
    assertEquals(
      "shared",
      result.models
        .single { it.name == "Money" }
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
    assertEquals(
      "consumer",
      result.models
        .single { it.name == "Item" }
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = [false, true])
  fun `composition retains independently placed declarations and reference bindings`(differentStructures: Boolean) {
    val fragments =
      listOf("first", "second").map { owner ->
        val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/$owner.yaml")
        GeneratedApiFragment(
          api =
            GeneratedApi(
              name = "Test",
              source = source,
              models =
                listOf(
                  GeneratedModel(
                    "Shared",
                    GeneratedModel.Kind.OBJECT,
                    source = source,
                    declarationUri = source.location + "#/components/schemas/Shared",
                    properties =
                      if (differentStructures) {
                        listOf(
                          GeneratedModelProperty(owner, GeneratedTypeRef.scalar("string")),
                        )
                      } else {
                        emptyList()
                      },
                    placementScopes = listOf(GeneratedPlacementScope(source.location, placement("package" to owner))),
                  ),
                  GeneratedModel(
                    owner,
                    GeneratedModel.Kind.OBJECT,
                    source = source,
                    properties = listOf(GeneratedModelProperty("value", GeneratedTypeRef.named("Shared"))),
                  ),
                ),
            ),
          apiId = GeneratedIdentity.native("test"),
        )
      }
    val composed = GeneratedApiComposer().compose(fragments)
    val restored = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composed))
    val resolved = GeneratedPlacementResolver().resolve(restored)
    assertEquals(2, resolved.models.count { it.name == "Shared" })
    val index =
      io.outfoxx.sunday.generator.ir.emit
        .GeneratedApiIndex(resolved)
    for (owner in listOf("first", "second")) {
      val reference =
        resolved.models
          .single { it.name == owner }
          .properties
          .single()
          .type
      assertEquals(
        owner,
        index
          .modelOrNull(reference)
          ?.targets
          ?.get("kotlinClient")
          ?.modelPackageName,
      )
    }
  }

  @Test
  fun `composed service defaults conflict unless a whole service relocation resolves them`() {
    val contributions =
      listOf("first", "second").map { owner ->
        val location = "https://example.test/$owner.yaml"
        GeneratedService(
          "Internal",
          operations =
            listOf(
              GeneratedOperation(
                id = owner,
                method = "GET",
                path = "/$owner",
                declarationUri = "$location#/paths/~1$owner/get",
              ),
            ),
          placementScopes = listOf(GeneratedPlacementScope(location, placement("package" to owner))),
          placementTags = setOf("internal"),
          declarationUris = setOf("$location#/tags/0"),
        )
      }
    val service =
      contributions.first().copy(
        operations =
          contributions.flatMap {
            it.operations
          },
        placementDeclarations = contributions.map { it.placementContribution() },
      )
    val document = api().copy(models = emptyList(), services = listOf(service))
    assertThrows(IllegalArgumentException::class.java) { GeneratedPlacementResolver().resolve(document) }
    val explicit =
      GeneratedPlacementConfiguration.Relocation(
        "https://example.test/first.yaml#/tags/0",
        "service",
        placement = placement("package" to "relocated"),
      )
    val relocated =
      GeneratedPlacementResolver(
        GeneratedPlacementConfiguration(relocations = listOf(explicit)),
      ).resolve(document)
    assertEquals(
      "relocated",
      relocated.services
        .single()
        .targets["kotlinClient"]
        ?.packageName,
    )
    // Neither owner alone covers the composed service.
    val rule =
      GeneratedPlacementConfiguration.Relocation(
        "https://example.test/first.yaml#",
        "service",
        setOf("internal"),
        placement("package" to "relocated"),
      )
    assertThrows(IllegalArgumentException::class.java) {
      GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = listOf(rule))).resolve(document)
    }
  }

  @Test
  fun `explicit tag selection places service and its local models without following references`() {
    val location = "https://example.test/api.yaml"
    val operation =
      GeneratedOperation(
        id = "read",
        method = "GET",
        path = "/read",
        declarationUri = "$location#/paths/~1read/get",
      )
    val service =
      GeneratedService(
        "Internal",
        operations = listOf(operation),
        placementTags = setOf("internal"),
        declarationUris = setOf("$location#/tags/0"),
      )
    val local =
      GeneratedModel(
        "Local",
        GeneratedModel.Kind.OBJECT,
        declarationUri = "$location#/paths/~1read/get/responses/200/content/application~1json/schema",
        scope =
          GeneratedModelScope(
            service = "Internal",
            operation = "read",
            usage = GeneratedModelScope.Usage.RESPONSE_BODY,
          ),
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
      )
    val rule =
      GeneratedPlacementConfiguration.Relocation(
        "$location#/tags/0",
        "service",
        setOf("internal"),
        placement("package" to "internal", "models" to mapOf("package" to "internal.models")),
      )
    val resolved =
      GeneratedPlacementResolver(GeneratedPlacementConfiguration(relocations = listOf(rule)))
        .resolve(api().copy(services = listOf(service), models = listOf(local) + api().models))
    assertEquals(
      "internal",
      resolved.services
        .single()
        .targets["kotlinClient"]
        ?.packageName,
    )
    assertEquals(
      "internal.models",
      resolved.models
        .first()
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
    assertEquals(
      null,
      resolved.models
        .last()
        .targets["kotlinClient"]
        ?.modelPackageName,
    )
  }

  @Test
  fun `operation placement beats service placement but declaration placement beats both`() {
    val location = "https://example.test/api.yaml"
    val operationUri = "$location#/paths/~1read/get"
    val localUri = "$operationUri/responses/200/content/application~1json/schema"
    val root = GeneratedPlacementScope(location, placement("package" to "root"))
    val operationScope = GeneratedPlacementScope(operationUri, placement("package" to "operation"))
    val service =
      GeneratedService(
        "Internal",
        operations =
          listOf(
            GeneratedOperation(
              id = "read",
              method = "GET",
              path = "/read",
              declarationUri = operationUri,
              placementScopes = listOf(root, operationScope),
            ),
          ),
        placementScopes = listOf(root, GeneratedPlacementScope("$location#/tags/0", placement("package" to "service"))),
      )
    val local =
      GeneratedModel(
        "Local",
        GeneratedModel.Kind.OBJECT,
        declarationUri = localUri,
        placementScopes = listOf(root, operationScope),
        scope =
          GeneratedModelScope(
            service = "Internal",
            operation = "read",
            usage = GeneratedModelScope.Usage.RESPONSE_BODY,
          ),
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "https://example.test/api.yaml"),
      )

    fun resolve(model: GeneratedModel) =
      GeneratedPlacementResolver()
        .resolve(api().copy(services = listOf(service), models = listOf(model)))
        .models
        .single()
        .targets["kotlinClient"]
        ?.modelPackageName
    assertEquals("operation", resolve(local))
    assertEquals(
      "declaration",
      resolve(
        local.copy(
          placementScopes =
            local.placementScopes +
              GeneratedPlacementScope(localUri, placement("package" to "declaration")),
        ),
      ),
    )
  }
}
