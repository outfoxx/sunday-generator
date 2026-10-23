package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.PlacementFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class PythonPlacementTest : PythonTest() {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `service declaration selector places owned models without kind filter`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val service = api.services.first()
    val scope =
      io.outfoxx.sunday.generator.ir.GeneratedModelScope(
        service.name,
        service.operations.first().id,
        usage = io.outfoxx.sunday.generator.ir.GeneratedModelScope.Usage.RESPONSE_BODY,
      )
    val tag = api.source.location + "#/tags/0"
    val configured =
      api.copy(
        models = api.models.map { if (it.name == "Item") it.copy(scope = scope) else it },
        services =
          api.services.map { candidate ->
            candidate.copy(
              declarationUris = setOf(tag),
              operations =
                candidate.operations.map { operation ->
                  operation.copy(
                    responses =
                      operation.responses.map { response ->
                        response.copy(
                          payloads =
                            response.payloads.map { payload ->
                              payload.copy(
                                type =
                                  payload.type.let {
                                    if (it.name ==
                                      "Item"
                                    ) {
                                      it.copy(scope = scope)
                                    } else {
                                      it
                                    }
                                  },
                              )
                            },
                          type =
                            response.type?.let {
                              if (it.name ==
                                "Item"
                              ) {
                                it.copy(scope = scope)
                              } else {
                                it
                              }
                            },
                        )
                      },
                  )
                },
            )
          },
        placementConfiguration =
          io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration(
            relocations =
              listOf(
                io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration.Relocation(
                  tag,
                  placement =
                    io.outfoxx.sunday.generator.ir.GeneratedPlacement(
                      python =
                        io.outfoxx.sunday.generator.ir.GeneratedPlacement.Scope(
                          models =
                            io.outfoxx.sunday.generator.ir.GeneratedPlacement
                              .Scope(packageName = "local_models", module = "models"),
                        ),
                    ),
                ),
              ),
          ),
      )
    val modules = PythonSundayIrGenerator(configured).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        modules.filterNot { it.path.endsWith("__init__.py") }.map { it.path.removeSuffix(".py").replace('/', '.') },
        """
        from local_models.models import Item
        assert Item.model_validate({"shared": "one"}).model_dump(mode="json") == {"shared": "one"}
        """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `configuration variables use relocated model modules`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val shared = api.models.single { it.name == "Shared" }
    val server =
      io.outfoxx.sunday.generator.ir.GeneratedServer(
        url = "https://{region}.example.com",
        variables =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedParameter(
              "region",
              io.outfoxx.sunday.generator.ir.GeneratedParameter.Location.PATH,
              io.outfoxx.sunday.generator.ir.GeneratedTypeRef
                .named("Shared", source = shared.source),
              required = true,
            ),
          ),
      )
    val configured = api.copy(name = "Example", services = api.services.map { it.copy(servers = listOf(server)) })
    val modules = PythonSundayIrGenerator(configured).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from example_services.config import ExampleConfig
          from example_shared.models import Shared
          config = ExampleConfig(region="one")
          assert isinstance(config.region, Shared)
          assert config.region.value == "one"
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @io.outfoxx.sunday.test.extensions.RequiresPythonRuntime(
    io.outfoxx.sunday.test.extensions.PythonRuntimeProfile.LITESTAR,
  )
  fun `server annotations retain independently placed same named types`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val shared = api.models.single { it.name == "Shared" }
    val source = api.source.copy(location = directory.resolve("second.yaml").toUri().toString())
    val other =
      shared.copy(
        source = source,
        declarationUri = source.location + "#/types/Shared",
        placementScopes =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedPlacementScope(
              source.location,
              io.outfoxx.sunday.generator.ir.GeneratedPlacement(
                python =
                  io.outfoxx.sunday.generator.ir.GeneratedPlacement
                    .Scope(packageName = "other_shared", module = "models"),
              ),
            ),
          ),
      )
    val service =
      api.services.first().copy(
        name = "Example",
        group = null,
        operations =
          listOf(shared, other).mapIndexed { index, model ->
            io.outfoxx.sunday.generator.ir.GeneratedOperation(
              "read$index",
              "GET",
              "/read$index",
              responses =
                listOf(
                  io.outfoxx.sunday.generator.ir.GeneratedResponse(
                    200,
                    io.outfoxx.sunday.generator.ir.GeneratedTypeRef
                      .named("Shared", source = model.source),
                  ),
                ),
            )
          },
      )
    val modules =
      PythonLitestarIrGenerator(
        api.copy(models = api.models + other, services = listOf(service)),
      ).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from typing import get_type_hints, get_args
          from example_services.${service.pythonServiceServerModuleName} import ExampleService
          from example_shared.models import Shared
          from other_shared.models import Shared as OtherShared
          assert get_args(get_type_hints(ExampleService.read0)["return"])[0] is Shared
          assert get_args(get_type_hints(ExampleService.read1)["return"])[0] is OtherShared
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `models problems and services share a relocated module`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val document = directory.resolve(if (kind == "raml") "api.raml" else "api.yaml").toUri().toString()
    val config =
      io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration(
        relocations =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration.Relocation(
              document,
              placement =
                io.outfoxx.sunday.generator.ir.GeneratedPlacement(
                  python =
                    io.outfoxx.sunday.generator.ir.GeneratedPlacement
                      .Scope(packageName = "example_consumer", module = "combined"),
                ),
            ),
          ),
      )
    val problem =
      io.outfoxx.sunday.generator.ir.GeneratedProblem(
        "MissingOrder",
        source = api.source,
        typeUri = "https://example.com/problems/missing-order",
        status = 404,
      )
    val modules =
      PythonSundayIrGenerator(
        api.copy(problems = listOf(problem), placementConfiguration = config),
      ).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        modules.filterNot { it.path.endsWith("__init__.py") }.map { it.path.removeSuffix(".py").replace('/', '.') },
        """
        from example_consumer.combined import Item, MissingOrder
        assert Item.model_validate({"shared": "one"}).model_dump(mode="json")["shared"] == "one"
        assert MissingOrder().payload.status == 404
        """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `identical names in separate modules retain independent references`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val shared = api.models.single { it.name == "Shared" }
    val otherSource = api.source.copy(location = directory.resolve("other.yaml").toUri().toString())
    val other =
      shared.copy(
        source = otherSource,
        declarationUri = otherSource.location + "#/types/Shared",
        placementScopes =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedPlacementScope(
              otherSource.location,
              io.outfoxx.sunday.generator.ir.GeneratedPlacement(
                python =
                  io.outfoxx.sunday.generator.ir.GeneratedPlacement
                    .Scope(packageName = "other_shared", module = "models"),
              ),
            ),
          ),
      )
    val models =
      api.models.map { model ->
        if (model.name != "Item") {
          model
        } else {
          model.copy(
            properties =
              model.properties +
                model.properties.single().copy(
                  name = "other",
                  type =
                    model.properties
                      .single()
                      .type
                      .copy(source = otherSource),
                ),
          )
        }
      } + other
    val modules = PythonSundayIrGenerator(api.copy(models = models)).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        modules.filterNot { it.path.endsWith("__init__.py") }.map { it.path.removeSuffix(".py").replace('/', '.') },
        """
        from example_consumer.models import Item
        from example_shared.models import Shared
        from other_shared.models import Shared as OtherShared
        value = Item.model_validate({"shared": "one", "other": "one"})
        assert isinstance(value.shared, Shared)
        assert isinstance(value.other, OtherShared)
        assert Shared is not OtherShared
        assert value.model_dump(mode="json") == {"shared": "one", "other": "one"}
        """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `problems share model module and remain catchable`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val problem =
      io.outfoxx.sunday.generator.ir.GeneratedProblem(
        name = "MissingOrder",
        typeUri = "https://example.com/problems/missing-order",
        status = 404,
        source = api.source,
        placementScopes =
          listOf(
            io.outfoxx.sunday.generator.ir.GeneratedPlacementScope(
              api.source.location + "#/problems/MissingOrder",
              io.outfoxx.sunday.generator.ir.GeneratedPlacement(
                python =
                  io.outfoxx.sunday.generator.ir.GeneratedPlacement
                    .Scope(packageName = "example_consumer", module = "models"),
              ),
            ),
          ),
      )
    val modules = PythonSundayIrGenerator(api.copy(problems = listOf(problem))).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        modules.filterNot { it.path.endsWith("__init__.py") }.map { it.path.removeSuffix(".py").replace('/', '.') },
        """
        from example_consumer.models import Item, MissingOrder
        from sunday import Problem
        value = Item.model_validate({"shared": "one"})
        assert value.model_dump(mode="json")["shared"] == "one"
        error = MissingOrder()
        assert isinstance(error, Problem)
        assert error.payload.status == 404
        """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `cross package references compile and preserve wire values`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val modules = PythonSundayIrGenerator(api).generateModules()
    assertTrue(
      compileModules(
        compiler,
        modules,
        modules.filterNot { it.path.endsWith("__init__.py") }.map { it.path.removeSuffix(".py").replace('/', '.') },
        """
        from example_shared.models import Shared
        from example_consumer.models import Item
        value = Item.model_validate({"shared": "one"})
        assert isinstance(value.shared, Shared)
        assert value.model_dump(mode="json")["shared"] == "one"
        """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `consumer verifies against installed wheel without owner sources`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val api = PlacementFixture(directory, kind).api
    val ownerUri = directory.resolve(if (kind == "raml") "common.raml" else "common.yaml").toUri()
    val owner =
      PythonSundayIrGenerator(
        api,
      ).generateModules(
        io.outfoxx.sunday.generator
          .GeneratedOutputSelection(ownerUris = setOf(ownerUri)),
      )
    assertTrue(compileModules(compiler, owner, listOf("example_shared.models")))
    val project = directory.resolve("owner-wheel")
    owner.filter { it.path.startsWith("example_shared/") }.forEach { module ->
      val file = project.resolve("src").resolve(module.path)
      java.nio.file.Files
        .createDirectories(file.parent)
      java.nio.file.Files
        .writeString(file, module.source)
    }
    java.nio.file.Files
      .writeString(project.resolve("src/example_shared/py.typed"), "")
    java.nio.file.Files.writeString(
      project.resolve("pyproject.toml"),
      """
      [build-system]
      requires = ["hatchling"]
      build-backend = "hatchling.build"
      [project]
      name = "example-shared"
      version = "1.0.0"
      [tool.hatch.build.targets.wheel]
      packages = ["src/example_shared"]
      """.trimIndent(),
    )
    val (status, output) = compiler.installWheel(project)
    org.junit.jupiter.api.Assertions
      .assertEquals(0, status, output)
    project.toFile().deleteRecursively()
    val consumerOwner = directory.resolve(if (kind == "raml") "api.raml" else "api.yaml").toUri()
    val consumer =
      PythonSundayIrGenerator(
        api,
      ).generateModules(
        io.outfoxx.sunday.generator
          .GeneratedOutputSelection(ownerUris = setOf(consumerOwner)),
      )
    assertTrue(
      compileModules(
        compiler,
        consumer,
        listOf("example_consumer.models"),
        """
        from example_shared.models import Shared
        from example_consumer.models import Item
        value = Item.model_validate({"shared": "one"})
        assert isinstance(value.shared, Shared)
        assert value.model_dump(mode="json")["shared"] == "one"
        """.trimIndent(),
      ),
    )
  }
}
