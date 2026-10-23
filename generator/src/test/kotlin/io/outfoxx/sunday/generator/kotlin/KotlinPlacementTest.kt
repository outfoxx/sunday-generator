@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
class KotlinPlacementTest {
  @TempDir lateinit var directory: Path

  @org.junit.jupiter.api.Test
  fun `composition compares the resolved identities of referenced declarations`() {
    fun placement(pkg: String) =
      io.outfoxx.sunday.generator.ir.GeneratedPlacement(
        kotlin =
          io.outfoxx.sunday.generator.ir.GeneratedPlacement
            .Scope(packageName = pkg),
      )

    fun fragment(owner: String): io.outfoxx.sunday.generator.ir.GeneratedApiFragment {
      val source =
        io.outfoxx.sunday.generator.ir.GeneratedSourceSpec(
          io.outfoxx.sunday.generator.ir.GeneratedSourceSpec.Kind.OPENAPI,
          "https://example.test/$owner.yaml",
        )
      return io.outfoxx.sunday.generator.ir.GeneratedApiFragment(
        io.outfoxx.sunday.generator.ir.GeneratedApi(
          name = "Test",
          source = source,
          models =
            listOf(
              io.outfoxx.sunday.generator.ir.GeneratedModel(
                "Money",
                io.outfoxx.sunday.generator.ir.GeneratedModel.Kind.OBJECT,
                source = source,
                declarationUri = source.location + "#/components/schemas/Money",
                properties =
                  listOf(
                    io.outfoxx.sunday.generator.ir.GeneratedModelProperty(
                      "amount",
                      io.outfoxx.sunday.generator.ir.GeneratedTypeRef
                        .scalar("string"),
                    ),
                  ),
                placementScopes =
                  listOf(
                    io.outfoxx.sunday.generator.ir
                      .GeneratedPlacementScope(source.location, placement(owner)),
                  ),
              ),
              io.outfoxx.sunday.generator.ir.GeneratedModel(
                "Order",
                io.outfoxx.sunday.generator.ir.GeneratedModel.Kind.OBJECT,
                source = source,
                declarationUri = source.location + "#/components/schemas/Order",
                properties =
                  listOf(
                    io.outfoxx.sunday.generator.ir.GeneratedModelProperty(
                      "money",
                      io.outfoxx.sunday.generator.ir.GeneratedTypeRef
                        .named("Money"),
                    ),
                  ),
                placementScopes =
                  listOf(
                    io.outfoxx.sunday.generator.ir
                      .GeneratedPlacementScope(source.location, placement("common")),
                  ),
              ),
            ),
        ),
        io.outfoxx.sunday.generator.ir.GeneratedIdentity
          .native("test"),
      )
    }
    val api =
      io.outfoxx.sunday.generator.ir.GeneratedApiComposer().compose(
        listOf(fragment("first"), fragment("second")),
      )
    org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
      io.outfoxx.sunday.generator.ir
        .GeneratedPlacementResolver(generationLanguage = "kotlin")
        .resolve(api)
    }
    val relocated =
      api.copy(
        placementConfiguration =
          io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration(
            relocations =
              listOf("first", "second").map { owner ->
                io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration.Relocation(
                  "https://example.test/$owner.yaml#/components/schemas/Order",
                  placement = placement(owner),
                )
              },
          ),
      )
    val registry = KotlinTypeRegistry(null, GenerationMode.Client, setOf(KotlinTypeRegistry.Option.ImplementModel))
    KotlinJAXRSIrGenerator(
      relocated,
      registry,
      KotlinJAXRSOptions(
        false,
        false,
        null,
        false,
        null,
        false,
        defaultProblemBaseUri = "http://example.com/",
        defaultMediaTypes = listOf("application/json"),
        serviceSuffix = "API",
        quarkus = false,
      ),
    ).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    for (owner in listOf("first", "second")) {
      val order = result.classLoader.loadClass("$owner.Order")
      assertEquals("$owner.Money", order.getDeclaredField("money").type.name)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `declaring document placement compiles across source formats`(kind: String) {
    val api =
      io.outfoxx.sunday.generator.tools
        .PlacementFixture(directory, kind)
        .api
    val resolved =
      io.outfoxx.sunday.generator.ir
        .GeneratedPlacementResolver()
        .resolve(api)
    assertEquals(
      "example.shared",
      resolved.models
        .single {
          it.name == "Shared"
        }.targets["kotlinClient"]
        ?.modelPackageName,
      api.toString(),
    )
    assertEquals(
      "example.consumer",
      resolved.models
        .single {
          it.name == "Item"
        }.targets["kotlinClient"]
        ?.modelPackageName,
      api.toString(),
    )
    val registry =
      KotlinTypeRegistry(null, GenerationMode.Client, setOf(KotlinTypeRegistry.Option.ImplementModel))
    KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    val shared = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "example/shared/Shared.kt")
    val consumer = CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "example/consumer/Item.kt")
    assertTrue(shared.contains("enum class Shared"))
    assertTrue(consumer.contains("example.shared.Shared"))
  }
}
