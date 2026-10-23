/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.pythonPlacement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class PythonCustomTypeMappingTest : PythonTest() {
  @Test
  fun `custom numeric bounds retain their exclusive flags`(compiler: PythonCompiler) {
    val constraints =
      listOf(
        "minimum" to "0",
        "maximum" to "10",
      )
    val models =
      constraints.flatMap { (bound, value) ->
        val exclusive = if (bound == "minimum") "exclusiveMinimum" else "exclusiveMaximum"
        listOf(false, true).map { alreadyExclusive ->
          GeneratedModel(
            "${bound}$alreadyExclusive",
            GeneratedModel.Kind.SCALAR_ALIAS,
            aliases = listOf(GeneratedTypeRef.scalar("number")),
            validation = mapOf(bound to value) + if (alreadyExclusive) mapOf(exclusive to "true") else emptyMap(),
            targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Number")),
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          )
        }
      }
    val record =
      GeneratedModel(
        "Record",
        GeneratedModel.Kind.OBJECT,
        properties =
          models.map { model ->
            val bound = if (model.name.startsWith("minimum")) "minimum" else "maximum"
            val exclusive = if (bound == "minimum") "exclusiveMinimum" else "exclusiveMaximum"
            val value =
              if (model.name.endsWith("true")) {
                if (bound == "minimum") "1" else "9"
              } else {
                model.validation.getValue(bound)
              }
            GeneratedModelProperty(
              model.name,
              GeneratedTypeRef.named(model.name),
              required = true,
              validation = mapOf(bound to value, exclusive to "true"),
            )
          },
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Number:
                def __init__(self, value: float):
                    self.value = value

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.value),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models + record),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Number
          boundaries = {"minimumfalse": 0, "maximumfalse": 10, "minimumtrue": 1, "maximumtrue": 9}
          values = {name: Number(5) for name in boundaries}
          record = Record.model_validate(values)
          assert all(getattr(record, name) is value for name, value in values.items())
          assert record.model_dump() == dict.fromkeys(boundaries, 5)
          for name, boundary in boundaries.items():
              try:
                  Record.model_validate({**values, name: Number(boundary)})
              except ValueError:
                  pass
              else:
                  raise AssertionError(f"exclusive bound was ignored: {name}")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `recursive wire containers retain checks at every depth`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Nodes",
          GeneratedModel.Kind.ARRAY,
          aliases =
            listOf(
              GeneratedTypeRef(
                GeneratedTypeRef.Kind.UNION,
                "union",
                arguments = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.named("Nodes")),
              ),
            ),
          validation = mapOf("minItems" to "1"),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Groups",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Nodes")),
          validation = mapOf("minItems" to "1"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Groups")),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("groups", GeneratedTypeRef.named("Groups"), required = true)),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Groups:
                def __init__(self, values: list[object]):
                    self.values = values

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.values),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Groups
          for values in ([["leaf"]], [[["leaf"]]], [["leaf", [["deep"]]]]):
              groups = Groups(values)
              record = Record(groups=groups)
              assert record.groups is groups
              assert record.model_dump() == {"groups": values}
          for values in ([], [[]], [[42]], [[[42]]], [["leaf", [[42]]]], [["leaf", [[]]]]):
              try:
                  Record(groups=Groups(values))
              except ValueError:
                  pass
              else:
                  raise AssertionError(f"recursive wire constraints were ignored: {values}")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `property limits do not constrain recursive declaration references`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Nodes",
          GeneratedModel.Kind.ARRAY,
          aliases =
            listOf(
              GeneratedTypeRef(
                GeneratedTypeRef.Kind.UNION,
                "union",
                arguments = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.named("Nodes")),
              ),
            ),
          validation = mapOf("minItems" to "1"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Groups")),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "groups",
                GeneratedTypeRef.named("Nodes"),
                required = true,
                validation =
                  mapOf(
                    "maxItems" to "1",
                  ),
              ),
            ),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Groups:
                def __init__(self, values: list[object]):
                    self.values = values

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.values),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Groups
          for values in ([["a", "b"]], ["leaf"], [[["a", "b", "c"]]]):
              groups = Groups(values)
              record = Record(groups=groups)
              assert record.groups is groups
              assert record.model_dump() == {"groups": values}
          for values in ([], ["a", "b"], [[]], [[42]], [[[42]]], [["leaf", [[]]]]):
              try:
                  Record(groups=Groups(values))
              except ValueError:
                  pass
              else:
                  raise AssertionError(f"recursive wire constraints were ignored: {values}")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `mapped groups validate nested unmapped containers on the wire`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Code",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.scalar("string")),
          validation = mapOf("pattern" to "^id:"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Code")),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Codes",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Code")),
          validation = mapOf("minItems" to "1", "maxItems" to "2"),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Groups",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Codes")),
          validation = mapOf("minItems" to "1"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Groups")),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("groups", GeneratedTypeRef.named("Groups"), required = true)),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Code:
                def __init__(self, value: str):
                    self.value = value

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.value),
                    )


            class Groups:
                def __init__(self, values: list[list[Code]]):
                    self.values = values

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(
                            lambda value: [[code.value for code in codes] for codes in value.values],
                        ),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Code, Groups
          groups = Groups([[Code("id:abc")]])
          record = Record(groups=groups)
          assert record.groups is groups
          assert record.model_dump() == {"groups": [["id:abc"]]}
          for values in ([], [[]], [[Code("id:a"), Code("id:b"), Code("id:c")]], [[Code("invalid")]]):
              try:
                  Record(groups=Groups(values))
              except ValueError:
                  pass
              else:
                  raise AssertionError("nested wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `mapped arrays preserve unique collections and nested enum checks`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Status",
          GeneratedModel.Kind.ENUM,
          values = listOf("active", "inactive"),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Statuses",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Status")),
          collection = GeneratedCollectionKind.SET,
          validation = mapOf("minItems" to "1", "uniqueItems" to "true"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Statuses")),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("statuses", GeneratedTypeRef.named("Statuses"), required = true)),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Statuses:
                def __init__(self, values: list[str]):
                    self.values = values

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.values),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Statuses
          statuses = Statuses(["active"])
          record = Record(statuses=statuses)
          assert record.statuses is statuses
          assert record.model_dump() == {"statuses": ["active"]}
          for values in ([], ["unknown"]):
              try:
                  Record(statuses=Statuses(values))
              except ValueError:
                  pass
              else:
                  raise AssertionError("array wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `mapped enum wire constraints do not rerun custom validators`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Code",
          GeneratedModel.Kind.ENUM,
          values = listOf("id:abc", "id:def"),
          targets =
            mapOf(
              "python" to GeneratedTarget(typeName = "test_api.domain.Code"),
            ),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Alias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.named("Code")),
          validation =
            mapOf(
              "pattern" to "^id:a",
            ),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Code")),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "code",
                GeneratedTypeRef.named("Code"),
                required = true,
                validation =
                  mapOf(
                    "pattern" to "^id:a",
                  ),
              ),
              GeneratedModelProperty("alias", GeneratedTypeRef.named("Alias"), required = true),
            ),
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Code:
                def __init__(self, value: str):
                    self.value = value

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.value),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Code
          code = Code("id:abc")
          value = Record(code=code, alias=code)
          assert value.code is code and value.alias is code
          assert value.model_dump() == {"code": "id:abc", "alias": "id:abc"}
          for fields in ({"code": Code("id:def"), "alias": code}, {"code": code, "alias": Code("id:def")}):
              try:
                  Record.model_validate(fields)
              except ValueError:
                  pass
              else:
                  raise AssertionError("wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["openapi", "2", "3"])
  fun `custom Python scalars retain their Pydantic representation`(
    source: String,
    compiler: PythonCompiler,
  ) {
    val api =
      if (source == "openapi") {
        OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/mapped-api.yaml").toUri())
      } else {
        AsyncApiToGeneratedApi()
          .convertFragment(
            Path.of("src/test/resources/asyncapi/ir/custom-types/api-$source.yaml").toUri(),
          ).api
          .copy(services = emptyList())
      }
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Identifier(str):
                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.no_info_after_validator_function(cls, handler(str))
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(api, PythonGeneratorOptions(placement = pythonPlacement("test_api")))
          .generateModules(
            GeneratedOutputSelection(
              GeneratedTypeCategory.entries.toSet(),
            ),
          ) +
          domain,
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Identifier
          value = Record.model_validate_json('{"id":"id:abc"}')
          assert isinstance(value.id, Identifier)
          assert Record.model_validate_json(value.model_dump_json()).id == value.id
          try:
              Record.model_validate_json('{"id":"invalid"}')
          except ValueError:
              pass
          else:
              raise AssertionError("wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `Kotlin mapping metadata leaves Python output unchanged`(compiler: PythonCompiler) {
    val api = OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/api.yaml").toUri())
    val sources =
      listOf(api.models, api.models.map { it.copy(targets = emptyMap()) }).map { models ->
        val modules =
          listOf(
            PythonModelRenderer("test_api").renderModels(models),
            PythonModuleBuilder("test_api/__init__.py").build(),
          )
        assertTrue(compileModules(compiler, modules, importModules = listOf("test_api.models")))
        CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "test_api/models.py")
      }
    assertEquals(sources[0], sources[1])
  }
}
