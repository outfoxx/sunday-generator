package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.referencedSchemaProblems
import io.outfoxx.sunday.generator.ir.emit.validateSchemaProblemResponses
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class ProblemUriResolutionTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun `problem enum aliases retain nullability and intersect local constraints without mutating shared types`() {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "file:///problems.yaml")
    val enum =
      GeneratedModel("ProblemType", GeneratedModel.Kind.ENUM, source, values = listOf("missing", "unavailable"))
    val alias =
      GeneratedModel(
        "ProblemTypeAlias",
        GeneratedModel.Kind.SCALAR_ALIAS,
        source,
        aliases = listOf(GeneratedTypeRef.named(enum.name, nullable = true)),
      )
    val problem =
      GeneratedModel(
        "Problem",
        GeneratedModel.Kind.OBJECT,
        source,
        properties =
          listOf(
            GeneratedModelProperty(
              "type",
              GeneratedTypeRef.named(alias.name),
              defaultValue = "missing",
              allowedValues = listOf("missing", null),
            ),
            GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
            GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer")),
            GeneratedModelProperty("code", GeneratedTypeRef.named(alias.name), defaultValue = "missing"),
          ),
      )
    val child =
      GeneratedModel(
        "ChildProblem",
        GeneratedModel.Kind.OBJECT,
        source,
        inherits = listOf(GeneratedTypeRef.named("Problem")),
      )
    val api = GeneratedApi(name = "Problems", source = source, models = listOf(enum, alias, problem, child))
    val resolved = api.resolveProblemUris("https://example.com/problems/")
    val fields = GeneratedModelProperties(GeneratedApiIndex(resolved)::modelOrNull)
    val type = fields.fields(resolved.models.last()).single { it.wireName == "type" }.effective
    assertEquals(GeneratedTypeRef.scalar("string", nullable = true, format = "uri"), type.type)
    assertEquals("https://example.com/problems/missing", type.defaultValue)
    assertEquals(listOf("https://example.com/problems/missing", null), type.allowedValues)
    assertEquals(enum, resolved.models[0])
    assertEquals(alias, resolved.models[1])
    assertEquals(problem.properties.last(), resolved.models[2].properties.last())
    assertEquals(resolved, resolved.resolveProblemUris("https://example.com/problems/"))
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "server",
      "relative",
      "root",
      "absolute",
      "template",
      "defaults",
      "fallback",
      "base-fallback",
      "file-base",
      "override",
      "root-fallback",
      "standard",
    ],
  )
  fun `schema problems match RAML and OpenAPI annotation bases and defaults`(mode: String) {
    val server =
      when (mode) {
        "fallback", "base-fallback", "root-fallback", "standard" -> null
        "defaults", "override" -> "https://api.example.com/{version}/"
        else -> "https://api.example.com/v1/"
      }
    val base =
      when (mode) {
        "relative", "base-fallback" -> "errors/"
        "root", "root-fallback" -> "/errors/"
        "file-base" -> "errors"
        "absolute" -> "https://errors.example.com/"
        "template" -> "{scheme}://errors.example.com/{version}/"
        else -> null
      }
    val expectedBase =
      when (mode) {
        "relative" -> "https://api.example.com/v1/errors/"
        "root" -> "https://api.example.com/errors/"
        "absolute" -> "https://errors.example.com/"
        "template" -> "https://errors.example.com/v2/"
        "defaults" -> "https://api.example.com/v3/"
        "override" -> "https://api.example.com/v2/"
        "fallback" -> "https://fallback.example.com/problems/"
        "root-fallback" -> "https://fallback.example.com/errors/"
        "standard" -> "http://example.com/"
        "base-fallback" -> "https://fallback.example.com/problems/errors/"
        else -> "https://api.example.com/v1/"
      }
    val hasDefaults = mode in listOf("defaults", "override")
    val hasOverrides = mode in listOf("template", "override")
    for (format in listOf("raml", "openapi")) {
      val source = directory.resolve("$mode.$format")
      val metadata =
        if (format == "raml") {
          buildString {
            if (server != null) appendLine("baseUri: $server")
            if (hasDefaults) appendLine("baseUriParameters: {version: {type: string, default: v3}}")
            if (base != null) appendLine("(sunday.problemBaseUri): '$base'")
            if (hasOverrides) appendLine("(sunday.problemUriParams): {scheme: https, version: v2}")
          }
        } else {
          buildString {
            if (server != null) {
              val variables = if (hasDefaults) ", variables: {version: {default: v3}}" else ""
              appendLine("servers: [{url: '$server'$variables}]")
            }
            if (base != null) appendLine("x-sunday-problemBaseUri: '$base'")
            if (hasOverrides) appendLine("x-sunday-problemUriParams: {scheme: https, version: v2}")
          }
        }
      source.writeText(
        if (format == "raml") {
          """
          #%RAML 1.0
          title: Problems
          uses:
            sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
          (sunday.problemTypes):
            missing: {status: 404, title: Missing}
          """.trimIndent() + "\n" + metadata
        } else {
          """
          openapi: 3.1.0
          info: {title: Problems, version: '1'}
          x-sunday-problemTypes:
            missing: {status: 404, title: Missing}
          paths:
            /missing:
              get:
                operationId: missing
                responses:
                  '404':
                    description: Missing
                    content:
                      application/problem+json:
                        schema: {${'$'}ref: '#/components/schemas/MissingSchemaProblem'}
          components:
            schemas:
              Problem:
                type: object
                required: [type, title, status]
                properties:
                  type: {type: string}
                  title: {type: string}
                  status: {type: integer}
              MissingSchemaProblem:
                allOf:
                  - {${'$'}ref: '#/components/schemas/Problem'}
                  - type: object
                    properties:
                      type: {type: string, const: missing}
              DefaultSchemaProblem:
                allOf:
                  - {${'$'}ref: '#/components/schemas/Problem'}
                  - type: object
                    properties:
                      type: {type: string, default: defaulted}
              BlankProblem:
                allOf:
                  - {${'$'}ref: '#/components/schemas/Problem'}
                  - type: object
                    properties:
                      type: {type: string, const: 'about:blank'}
              Other:
                type: object
                properties:
                  type: {type: string, const: missing}
          """.trimIndent() + "\n" + metadata
        },
      )
      val api =
        GeneratedApiIrExporter()
          .export(
            source.toUri(),
          ).resolveProblemUris(
            if (mode ==
              "standard"
            ) {
              "http://example.com/"
            } else {
              "https://fallback.example.com/problems/"
            },
          )
      val index = GeneratedApiIndex(api)
      val fields = GeneratedModelProperties(index::modelOrNull)

      fun type(name: String) =
        fields.fields(api.models.single { it.name == name }).single { it.wireName == "type" }.effective
      assertEquals(expectedBase + "missing", api.problems.single().typeUri, "$mode/$format annotation")
      if (format == "raml") continue
      assertEquals(listOf(expectedBase + "missing"), type("MissingSchemaProblem").allowedValues, "$mode/$format schema")
      assertEquals(expectedBase + "defaulted", type("DefaultSchemaProblem").defaultValue)
      assertEquals(listOf("about:blank"), type("BlankProblem").allowedValues)
      assertEquals(listOf("missing"), type("Other").allowedValues)
      assertEquals(
        setOf(expectedBase + "missing"),
        api.services
          .single()
          .referencedSchemaProblems(index)
          .keys,
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["inherited", "relative", "absolute"])
  fun `referenced schema problems use the same library base as RAML annotated problems`(mode: String) {
    val base =
      when (mode) {
        "relative" -> "../library/"
        "absolute" -> "https://library.example.com/problems/"
        else -> null
      }
    val expectedBase =
      when (mode) {
        "relative" -> "https://api.example.com/v1/library/"
        "absolute" -> "https://library.example.com/problems/"
        else -> "https://api.example.com/v1/errors/"
      }
    directory.resolve("library.raml").writeText(
      """
      #%RAML 1.0 Library
      uses:
        sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
      (sunday.problemTypes):
        missing: {status: 404, title: Missing}
      """.trimIndent() + (
        base?.let {
          "\n(sunday.problemBaseUri): '$it'\n"
        } ?: "\n"
      ),
    )
    val raml = directory.resolve("api.raml")
    raml.writeText(
      """
      #%RAML 1.0
      title: Library
      baseUri: https://api.example.com/v1/
      uses:
        sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
        library: library.raml
      (sunday.problemBaseUri): errors/
      """.trimIndent(),
    )
    directory.resolve("library.yaml").writeText(
      """
      components:
        schemas:
          Problem:
            type: object
            required: [type, title, status]
            properties:
              type: {type: string}
              title: {type: string}
              status: {type: integer}
          MissingProblem:
            allOf:
              - {${'$'}ref: '#/components/schemas/Problem'}
              - type: object
                properties:
                  type: {type: string, const: missing}
      """.trimIndent() + (
        base?.let {
          "\nx-sunday-problemBaseUri: '$it'\n"
        } ?: "\n"
      ),
    )
    val openapi = directory.resolve("api.yaml")
    openapi.writeText(
      """
      openapi: 3.1.0
      info: {title: Library, version: '1'}
      servers: [{url: 'https://api.example.com/v1/'}]
      x-sunday-problemBaseUri: errors/
      paths:
        /missing:
          get:
            operationId: missing
            responses:
              '404':
                description: Missing
                content:
                  application/problem+json:
                    schema: {${'$'}ref: '#/components/schemas/ChildProblem'}
      components:
        schemas:
          ChildProblem:
            allOf:
              - {${'$'}ref: './library.yaml#/components/schemas/MissingProblem'}
              - type: object
                properties:
                  detail: {type: string, default: Missing}
      """.trimIndent(),
    )
    val annotated = GeneratedApiIrExporter().export(raml.toUri())
    val api = GeneratedApiIrExporter().export(openapi.toUri())
    val uri = expectedBase + "missing"
    assertEquals(uri, annotated.problems.single().typeUri)
    assertEquals(
      setOf(uri),
      api.services
        .single()
        .referencedSchemaProblems(GeneratedApiIndex(api))
        .keys,
    )
  }

  @Test
  fun `relative and absolute spellings of the same custom URI share one registration`() {
    val source = directory.resolve("conflict.yaml")
    val resource = requireNotNull(javaClass.getResource("/openapi/ir/about-blank-problems.yaml")).readText()
    source.writeText(
      resource
        .replace("required: [title, status]", "required: [type, title, status]")
        .replaceFirst("const: 'about:blank'", "const: 'missing'")
        .replace("const: 'about:blank'", "const: 'https://example.com/problems/missing'") +
        "\nx-sunday-problemBaseUri: https://example.com/problems/\n",
    )
    val api = GeneratedApiIrExporter().export(source.toUri())
    val index = GeneratedApiIndex(api)
    index.validateSchemaProblemResponses()
    val registered = api.services.single().referencedSchemaProblems(index)
    assertEquals(listOf("https://example.com/problems/missing"), registered.keys.toList())
    assertEquals("BadRequestProblem", registered.values.single().name)
  }
}
