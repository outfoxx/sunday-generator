package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiComposer
import io.outfoxx.sunday.generator.ir.GeneratedApiFragment
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedIdentity
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementScope
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import java.nio.file.Files
import java.nio.file.Path

/** Parsed source declarations exercised through composition and the serialized IR contract. */
internal class PlacementIdentityFixture(
  private val directory: Path,
  private val kind: String,
) {
  private fun parsed(
    owner: String,
    raml: String,
    schemas: String,
    placement: String = "{}",
  ): GeneratedApi =
    PlacementRegressionFixture(
      Files.createDirectories(directory.resolve(owner)).toRealPath(),
      if (kind == "composed") {
        if (owner == "second") {
          "asyncapi"
        } else {
          "openapi"
        }
      } else {
        kind
      },
    ).models(raml, schemas, placement)

  private fun placement(owner: String): String =
    """
    kotlin: {package: example.$owner}
    swift: {module: ${if (owner == "first") "SharedModels" else "SundayGenTest"}}
    typescript: {module: $owner}
    python: {package: $owner, module: models}
    """.trimIndent()

  private fun roundTrip(api: GeneratedApi): GeneratedApi =
    GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

  private fun compose(apis: List<GeneratedApi>): GeneratedApi =
    roundTrip(
      GeneratedApiComposer().compose(
        apis.map { GeneratedApiFragment(it, GeneratedIdentity("placement", GeneratedIdentity.Origin.EXPLICIT)) },
      ),
    )

  /** Compact contribution references must acquire bindings when a later composition adds a name. */
  fun recomposed(): GeneratedApi {
    val apis =
      listOf("first", "second").map { owner ->
        parsed(
          owner,
          "Foo: {type: object, properties: {value: string}}\nWrapper: {type: object, properties: {foo: Foo}}",
          """
          Foo: {type: object, required: [value], properties: {value: {type: string}}}
          Wrapper: {type: object, required: [foo], properties: {foo: {${'$'}ref: '#/components/schemas/Foo'}}}
          """.trimIndent(),
          placement(owner),
        )
      }
    val shared = apis.first().models.single { it.name == "Foo" }
    val fragments =
      apis.map { api ->
        api.copy(
          models =
            api.models.map { model ->
              if (model.name == "Foo") {
                shared
              } else {
                model.copy(
                  properties =
                    model.properties.map {
                      // A unique name may be unqualified in compact serialized IR.
                      it.copy(type = it.type.copy(source = null))
                    },
                )
              }
            },
        )
      }
    val extra =
      parsed(
        "third",
        "Foo: {type: object, properties: {value: integer}}",
        "Foo: {type: object, required: [value], properties: {value: {type: integer}}}",
        placement("second"),
      )
    return compose(listOf(extra, compose(fragments)))
  }

  /** Both children inherit their individually placed enclosure before structural deduplication. */
  fun nested(): GeneratedApi =
    compose(
      listOf("first", "second").map { owner ->
        val api =
          parsed(
            owner,
            "Parent:\n  type: object\n  (sunday.placement):\n" + placement(owner).prependIndent("    ") +
              "\n  properties: {child: Child}\nChild: {type: object, properties: {value: string}}",
            "Parent:\n  type: object\n  x-sunday-placement:\n" + placement(owner).prependIndent("    ") +
              "\n  required: [child]\n  properties: {child: {${'$'}ref: '#/components/schemas/Child'}}\n" +
              "Child: {type: object, required: [value], properties: {value: {type: string}}}",
          )
        api.copy(
          models =
            api.models.map { model ->
              if (model.name == "Child") {
                model.copy(
                  nested =
                    GeneratedNestedType(
                      enclosedIn = GeneratedTypeRef.named("Parent", source = model.source),
                      name = "Child",
                    ),
                )
              } else {
                model
              }
            },
        )
      },
    )

  /** One service references two independently placed models and two independently placed problems. */
  fun pythonImports(): GeneratedApi {
    val apis =
      listOf("first", "second").mapIndexed { index, owner ->
        val valueType = if (index == 0) "string" else "integer"
        val api =
          parsed(
            owner,
            "Record: {type: object, properties: {value: $valueType}}",
            "Record: {type: object, required: [value], properties: {value: {type: $valueType}}}",
            "python: {package: ${if (index == 0) "a" else "a_b"}, module: ${if (index == 0) "b_c" else "c"}}",
          )
        val problem =
          GeneratedProblem(
            "Missing",
            source = api.source,
            typeUri = "https://example.test/$owner/missing",
            status = 404,
            placementScopes =
              listOf(
                GeneratedPlacementScope(
                  api.source.location,
                  GeneratedPlacement(python = GeneratedPlacement.Scope(packageName = owner, module = "errors")),
                ),
              ),
          )
        api.copy(problems = listOf(problem))
      }
    val api = compose(apis)
    val service =
      GeneratedService(
        "Example",
        operations =
          apis.mapIndexed { index, source ->
            val problem = GeneratedTypeRef.named("Missing", source = source.source)
            GeneratedOperation(
              id =
                if (index ==
                  0
                ) {
                  "readFirst"
                } else {
                  "readSecond"
                },
              method = "GET",
              path = "/${if (index == 0) "first" else "second"}",
              responses =
                listOf(
                  GeneratedResponse(
                    200,
                    GeneratedTypeRef.named("Record", source = source.source),
                    mediaTypes = listOf("application/json"),
                  ),
                ),
              problems = listOf(problem),
              nullify = GeneratedNullify(problems = listOf(problem)),
            )
          },
        placementScopes =
          listOf(
            GeneratedPlacementScope(
              api.source.location,
              GeneratedPlacement(python = GeneratedPlacement.Scope(packageName = "client", module = "api")),
            ),
          ),
      )
    return roundTrip(api.copy(services = listOf(service)))
  }

  /** A service-to-problem-to-model chain can cross an unnamed module or form a module cycle. */
  fun swiftProblems(
    problemModule: String?,
    fieldModule: String?,
  ): GeneratedApi {
    val api =
      parsed(
        "problems",
        "Detail: {type: object, properties: {value: string}}",
        "Detail: {type: object, required: [value], properties: {value: {type: string}}}",
      )

    fun scopes(module: String?) =
      module
        ?.let {
          listOf(
            GeneratedPlacementScope(
              api.source.location,
              GeneratedPlacement(swift = GeneratedPlacement.Scope(module = it)),
            ),
          )
        }.orEmpty()
    val problem =
      GeneratedProblem(
        "Missing",
        source = api.source,
        typeUri = "https://example.test/missing",
        status = 404,
        fields =
          listOf(
            GeneratedModelProperty("info", GeneratedTypeRef.named("Detail", source = api.source), required = true),
          ),
        placementScopes = scopes(problemModule),
      )
    val reference = GeneratedTypeRef.named("Missing", source = api.source)
    val service =
      GeneratedService(
        "Example",
        operations =
          listOf(
            GeneratedOperation(
              "read",
              "GET",
              "/read",
              responses =
                listOf(
                  GeneratedResponse(200, GeneratedTypeRef.scalar("string"), mediaTypes = listOf("application/json")),
                ),
              problems = listOf(reference),
              nullify = GeneratedNullify(problems = listOf(reference)),
            ),
          ),
        placementScopes = scopes("SundayGenTest"),
      )
    return roundTrip(
      api.copy(
        placementScopes = emptyList(),
        models = api.models.map { it.copy(placementScopes = scopes(fieldModule)) },
        problems = listOf(problem),
        services = listOf(service),
      ),
    )
  }
}
