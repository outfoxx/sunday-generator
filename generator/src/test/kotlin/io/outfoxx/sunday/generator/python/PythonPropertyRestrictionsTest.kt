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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class PythonPropertyRestrictionsTest : PythonTest() {

  @Test
  fun `integer defaults normalize exactly through aliases and inherited restrictions`(compiler: PythonCompiler) {
    val integer = GeneratedTypeRef.scalar("integer")
    val aliases =
      listOf(
        GeneratedModel("Counter", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(integer)),
        GeneratedModel(
          "CounterAlias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.named("Counter")),
        ),
      )
    val fields =
      listOf(
        GeneratedModelProperty("decimal", integer, defaultValue = "1.0"),
        GeneratedModelProperty("exponent", GeneratedTypeRef.named("CounterAlias"), defaultValue = "1e3"),
        GeneratedModelProperty(
          "negativeZero",
          integer,
          defaultValue = "-0.0",
          serializationName = "negativeZero",
        ),
        GeneratedModelProperty("huge", integer, defaultValue = "123456789012345678901234567890.0"),
        GeneratedModelProperty("fraction", GeneratedTypeRef.scalar("number"), defaultValue = "1.25"),
        GeneratedModelProperty("count", GeneratedTypeRef.named("CounterAlias")),
      )
    val base = GeneratedModel("Base", GeneratedModel.Kind.OBJECT, properties = fields)
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        inherits = listOf(GeneratedTypeRef.named("Base")),
        properties =
          listOf(
            fields.last().copy(
              defaultValue = "20.0",
              validation = mapOf("minimum" to "20"),
              allowedValues = listOf(20),
            ),
          ),
      )
    val invalidDefault =
      child.copy(
        name = "InvalidDefault",
        properties = listOf(child.properties.single().copy(defaultValue = "19.0")),
      )
    val models = aliases + listOf(base, child, invalidDefault)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("turnpost_api").renderModels(models),
          PythonModuleBuilder("turnpost_api/__init__.py").build(),
        ),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import ValidationError
          from turnpost_api.models import Base, Child, InvalidDefault
          assert Base().count is None
          for value in (Child(), Child.model_validate({}), Child.model_validate_json('{}')):
              assert value.decimal == 1 and type(value.decimal) is int
              assert value.exponent == 1000 and type(value.exponent) is int
              assert value.negative_zero == 0 and type(value.negative_zero) is int
              assert value.huge == 123456789012345678901234567890
              assert value.fraction == 1.25 and type(value.fraction) is float
              assert value.count == 20 and isinstance(value, Base)
          assert Child(negative_zero=2).negative_zero == Child(negativeZero=2).negative_zero == 2
          for factory in (InvalidDefault, lambda: Child(count=None), lambda: Child(count=19)):
              try:
                  factory()
                  raise AssertionError('invalid default or value was accepted')
              except ValidationError:
                  pass
          """.trimIndent(),
      ),
    )
    for (literal in listOf("1.2", "NaN", "Infinity", "nonsense")) {
      val invalid =
        GeneratedModel(
          "Invalid",
          GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "count",
                GeneratedTypeRef.named("CounterAlias"),
                serializationName = "countValue",
                defaultValue = literal,
              ),
            ),
        )
      val error =
        assertThrows(GenerationException::class.java) {
          PythonModelRenderer("turnpost_api").renderModels(aliases + invalid)
        }
      assertTrue(error.message.orEmpty().contains("Invalid.countValue"), error.message)
      assertTrue(error.message.orEmpty().contains(literal), error.message)
    }
  }

  @Test
  fun `enum string refinements preserve typed values for every input path`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("enum-patterns.yaml")
    source.writeText(
      OpenApiReferenceDocuments.document(
        "Enum refinements",
        """
        State: {type: string, enum: [short, longer, unknown], x-unknown-value: unknown}
        StateAlias: {${'$'}ref: '#/components/schemas/State'}
        Ordinary: {type: string, enum: [short, longer]}
        Base:
          type: object
          properties:
            stateTag: {${'$'}ref: '#/components/schemas/StateAlias'}
            ordinary: {${'$'}ref: '#/components/schemas/Ordinary'}
        Child:
          allOf: [{${'$'}ref: '#/components/schemas/Base'}]
          properties:
            stateTag: {pattern: '^s', minLength: 2, maxLength: 8, default: short}
            ordinary: {pattern: '^s', enum: [short], default: short}
        Grandchild:
          allOf: [{${'$'}ref: '#/components/schemas/Child'}]
          properties: {stateTag: {maxLength: 6}}
        InvalidDefault:
          allOf: [{${'$'}ref: '#/components/schemas/Child'}]
          properties: {stateTag: {default: longer}}
        """.trimIndent(),
      ),
    )
    val models = OpenApiToGeneratedApi().convert(source.toUri()).models
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("turnpost_api").renderModels(models),
          PythonModuleBuilder("turnpost_api/__init__.py").build(),
        ),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          import json
          from pydantic import ValidationError
          from turnpost_api.models import Base, Child, Grandchild, InvalidDefault, StateAlias, Ordinary
          for model in (Child, Grandchild):
              for factory in (lambda: model(), lambda: model(state_tag=StateAlias.SHORT, ordinary=Ordinary.SHORT),
                              lambda: model(stateTag='short'), lambda: model.model_validate({'stateTag': 'short'}),
                              lambda: model.model_validate_json('{"stateTag":"short"}')):
                  value = factory()
                  assert isinstance(value, Base)
                  assert value.state_tag is StateAlias.SHORT
                  assert value.ordinary is Ordinary.SHORT
                  assert json.loads(value.model_dump_json(by_alias=True)) == {'stateTag': 'short', 'ordinary': 'short'}
              for field, bad in (('state_tag', 'longer'), ('stateTag', 's'), ('ordinary', 'longer'), ('state_tag', None)):
                  try:
                      model(**{field: bad})
                      raise AssertionError(f'{field} accepted {bad!r}')
                  except ValidationError:
                      pass
              schema = model.model_json_schema()['properties']['stateTag']
              assert schema['pattern'] == '^s' and schema['minLength'] == 2
          unknown = Child(state_tag='surprise').state_tag
          assert isinstance(unknown, StateAlias) and unknown.value == 'surprise'
          assert Base(state_tag='longer').state_tag is StateAlias.LONGER
          for factory in (InvalidDefault, lambda: Grandchild(state_tag='surprise')):
              try:
                  factory()
                  raise AssertionError('inherited constraint was bypassed')
              except ValidationError:
                  pass
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `formatted restrictions accept typed and wire inputs without coercing unrelated values`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("formatted.yaml")
    source.writeText(
      OpenApiReferenceDocuments.document(
        "Formatted restrictions",
        """
        Identifier: {type: string, format: uuid}
        IdentifierAlias: {${'$'}ref: '#/components/schemas/Identifier'}
        Base:
          type: object
          properties:
            idValue: {${'$'}ref: '#/components/schemas/IdentifierAlias'}
            instant: {type: string, format: date-time}
            day: {type: string, format: date}
            clock: {type: string, format: time}
            link: {type: string, format: uri}
            encoded: {type: string, format: byte}
        Child:
          allOf: [{${'$'}ref: '#/components/schemas/Base'}]
          properties:
            idValue: {const: '00000000-0000-0000-0000-000000000000', default: '00000000-0000-0000-0000-000000000000'}
            instant: {const: '2026-01-01T00:00:00Z'}
            day: {const: '2026-01-01'}
            clock: {const: '12:30:00'}
            link: {const: 'https://example.test/'}
            encoded: {const: 'SGk='}
        Grandchild: {allOf: [{${'$'}ref: '#/components/schemas/Child'}]}
        """.trimIndent(),
      ),
    )
    val models = OpenApiToGeneratedApi().convert(source.toUri()).models
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("turnpost_api").renderModels(models),
          PythonModuleBuilder("turnpost_api/__init__.py").build(),
        ),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          import json
          from datetime import date, datetime, time, timezone
          from uuid import UUID
          from pydantic import AnyUrl, ValidationError
          from pydantic_core import Url
          from turnpost_api.models import Base, Child, Grandchild
          wire = dict(idValue='00000000-0000-0000-0000-000000000000', instant='2026-01-01T00:00:00Z',
                      day='2026-01-01', clock='12:30:00', link='https://example.test/', encoded='SGk=')
          typed = dict(id_value=UUID(wire['idValue']), instant=datetime(2026, 1, 1, tzinfo=timezone.utc),
                       day=date(2026, 1, 1), clock=time(12, 30), link=AnyUrl(wire['link']), encoded=b'SGk=')
          for model in (Child, Grandchild):
              for value in (model(**typed), model(**wire), model.model_validate(wire), model.model_validate_json(json.dumps(wire))):
                  assert isinstance(value, Base)
                  assert value.model_dump(mode='json', by_alias=True) == wire
                  assert isinstance(value.id_value, UUID)
                  assert isinstance(value.instant, datetime)
                  assert value.encoded == b'Hi'
              assert model(link=Url(wire['link'])).link == typed['link']
              omitted = model()
              assert omitted.id_value == typed['id_value'] and omitted.instant is None
              for field, value in (('id_value', UUID(int=1)), ('instant', datetime(2027, 1, 1, tzinfo=timezone.utc)),
                                   ('day', date(2027, 1, 1)), ('clock', time(13)), ('link', AnyUrl('https://other.test/')),
                                   ('idValue', 0), ('instant', 1767225600), ('instant', '2026-01-01T00:00:00+00:00'),
                                   ('day', None), ('encoded', b'QQ==')):
                  try:
                      model(**{field: value})
                      raise AssertionError(f'{field} accepted excluded {value!r}')
                  except ValidationError:
                      pass
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `OpenAPI scalar refinements validate constructor names and defaults`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("restrictions.yaml")
    source.writeText(
      OpenApiReferenceDocuments.document(
        "Scalar restrictions",
        """
        Base:
          type: object
          properties:
            stateTag: {type: string, enum: [good, bad]}
        Child:
          allOf: [{${'$'}ref: '#/components/schemas/Base'}]
          properties:
            stateTag: {const: good, default: bad}
        """.trimIndent(),
      ),
    )
    val models = OpenApiToGeneratedApi().convert(source.toUri()).models
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("turnpost_api").renderModels(models),
          PythonModuleBuilder("turnpost_api/__init__.py").build(),
        ),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from turnpost_api.models import Base, Child
          from pydantic import ValidationError
          assert isinstance(Child(state_tag='good'), Base)
          assert Base(state_tag='bad').state_tag == 'bad'
          for construct in (
              lambda: Child(state_tag='bad'),
              lambda: Child(stateTag='bad'),
              lambda: Child.model_validate({'state_tag': 'bad'}),
              lambda: Child.model_validate({'stateTag': 'bad'}),
              lambda: Child.model_validate_json('{"stateTag":"bad"}'),
              lambda: Child(),
          ):
              try:
                  construct()
                  raise AssertionError('excluded scalar accepted')
              except ValidationError:
                  pass
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `scalar field validation preserves inheritance enums nulls and omitted values`(compiler: PythonCompiler) {
    val state = GeneratedTypeRef.named("StateAlias")
    val fields =
      listOf(
        GeneratedModelProperty("stateTag", state, defaultValue = "good", allowedValues = listOf("good")),
        GeneratedModelProperty(
          "requiredState",
          state,
          required = true,
          defaultValue = "bad",
          allowedValues = listOf("good"),
        ),
        GeneratedModelProperty(
          "zero",
          GeneratedTypeRef.scalar("integer"),
          defaultValue = "0",
          allowedValues = listOf(0),
        ),
        GeneratedModelProperty(
          "flag",
          GeneratedTypeRef.scalar("boolean"),
          defaultValue = "false",
          allowedValues = listOf(false),
        ),
        GeneratedModelProperty(
          "aliasedZero",
          GeneratedTypeRef.named("IntegerAlias"),
          defaultValue = "0",
          allowedValues = listOf(0),
        ),
        GeneratedModelProperty(
          "aliasedFlag",
          GeneratedTypeRef.named("BooleanAlias"),
          defaultValue = "false",
          allowedValues = listOf(false),
        ),
        GeneratedModelProperty(
          "choice",
          GeneratedTypeRef.scalar("any", nullable = true),
          allowedValues = listOf(null, 0, false),
        ),
        GeneratedModelProperty("omittedValue", GeneratedTypeRef.scalar("string"), allowedValues = listOf("yes")),
        GeneratedModelProperty("blocked", GeneratedTypeRef.scalar("string"), allowedValues = emptyList()),
      )
    val models =
      listOf(
        GeneratedModel(
          "IntegerAlias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.scalar("integer")),
        ),
        GeneratedModel(
          "BooleanAlias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.scalar("boolean")),
        ),
        GeneratedModel(
          "State",
          GeneratedModel.Kind.ENUM,
          values = listOf("good", "bad", "unknown"),
          unknownValue = "unknown",
        ),
        GeneratedModel(
          "StateAlias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.named("State")),
        ),
        GeneratedModel("Base", GeneratedModel.Kind.OBJECT, properties = fields.map { it.copy(allowedValues = null) }),
        GeneratedModel(
          "Child",
          GeneratedModel.Kind.OBJECT,
          properties = fields,
          inherits = listOf(GeneratedTypeRef.named("Base")),
        ),
        GeneratedModel(
          "Grandchild",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(fields.single { it.name == "zero" }),
          inherits = listOf(GeneratedTypeRef.named("Child")),
        ),
      )
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("turnpost_api").renderModels(models),
          PythonModuleBuilder("turnpost_api/__init__.py").build(),
        ),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          import json
          from turnpost_api.models import Base, Child, Grandchild, State
          from pydantic import ValidationError
          for model in (Child, Grandchild):
              value = model(required_state=State('good'))
              assert isinstance(value, Base)
              assert value.state_tag is State('good')
              assert value.zero == 0 and value.flag is False
              assert value.aliased_zero == 0 and value.aliased_flag is False
              assert value.choice is None and value.omitted_value is None and value.blocked is None
              assert value.model_dump(mode='json', by_alias=True)['stateTag'] == 'good'
              for choice in (None, 0, 0.0, False):
                  model(required_state='good', choice=choice)
              for field, invalid in (
                  ('stateTag', 'bad'), ('stateTag', 'future'), ('stateTag', None),
                  ('requiredState', 'bad'), ('zero', False), ('zero', 1), ('flag', 0), ('flag', True),
                  ('aliasedZero', False), ('aliasedZero', 1), ('aliasedFlag', 0), ('aliasedFlag', True),
                  ('choice', True), ('choice', '0'), ('omittedValue', None), ('blocked', 'any'),
              ):
                  name = next(name for name, metadata in model.model_fields.items() if name == field or metadata.alias == field)
                  for construct in (
                      lambda: model(**({'required_state': 'good'} | {name: invalid})),
                      lambda: model.model_validate({'requiredState': 'good'} | {field: invalid}),
                      lambda: model.model_validate_json(json.dumps({'requiredState': 'good'} | {field: invalid})),
                  ):
                      try:
                          construct()
                          raise AssertionError(f'{model.__name__}.{field} accepted {invalid!r}')
                      except ValidationError:
                          pass
              try:
                  model()
                  raise AssertionError('required field received a default')
              except ValidationError:
                  pass
          assert Base(required_state='future').required_state.value == 'future'
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `restricted discriminator literals coexist with inherited scalar validators`(compiler: PythonCompiler) {
    val kind =
      GeneratedModelProperty(
        "kindTag",
        GeneratedTypeRef.scalar("string"),
        required = true,
        allowedValues = listOf("service", "user"),
      )
    val models =
      buildList {
        for (prefix in listOf("Only", "Mixed")) {
          add(
            GeneratedModel(
              "${prefix}Base",
              GeneratedModel.Kind.OBJECT,
              properties =
                listOf(kind) +
                  if (prefix == "Mixed") {
                    listOf(
                      GeneratedModelProperty(
                        "zero",
                        GeneratedTypeRef.scalar("integer"),
                        defaultValue = "0",
                        allowedValues = listOf(0),
                      ),
                    )
                  } else {
                    emptyList()
                  },
            ),
          )
          for ((name, value) in listOf("Service" to "service", "User" to "user")) {
            add(
              GeneratedModel(
                "$prefix$name",
                GeneratedModel.Kind.OBJECT,
                properties = listOf(kind.copy(allowedValues = listOf(value))),
                inherits = listOf(GeneratedTypeRef.named("${prefix}Base")),
                discriminatorValue = value,
              ),
            )
          }
          add(
            GeneratedModel(
              "${prefix}Identity",
              GeneratedModel.Kind.UNION,
              discriminator = kind.name,
              discriminatorMappings =
                mapOf(
                  "service" to GeneratedTypeRef.named("${prefix}Service"),
                  "user" to GeneratedTypeRef.named("${prefix}User"),
                ),
            ),
          )
        }
        add(
          GeneratedModel(
            "BlockedService",
            GeneratedModel.Kind.OBJECT,
            properties = listOf(kind.copy(allowedValues = emptyList())),
            inherits = listOf(GeneratedTypeRef.named("OnlyService")),
            discriminatorValue = "service",
          ),
        )
      }
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("turnpost_api").renderModels(models),
          PythonModuleBuilder("turnpost_api/__init__.py").build(),
        ),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          import json
          from pydantic import TypeAdapter, ValidationError
          from turnpost_api.models import (
              OnlyIdentity, OnlyService, OnlyUser, MixedIdentity, MixedService, MixedUser, BlockedService,
          )
          for union, service, user in (
              (OnlyIdentity, OnlyService, OnlyUser), (MixedIdentity, MixedService, MixedUser),
          ):
              adapter = TypeAdapter(union)
              for model, tag in ((service, 'service'), (user, 'user')):
                  assert model(kind_tag=tag).kind_tag == tag
                  for field_name in ('kindTag', 'kind_tag'):
                      payload = {field_name: tag}
                      for decoded in (adapter.validate_python(payload), adapter.validate_json(json.dumps(payload))):
                          assert isinstance(decoded, model)
                          assert decoded.model_dump(by_alias=True)['kindTag'] == tag
                  for invalid in ('future', False, 0, None):
                      try:
                          model(kind_tag=invalid)
                          raise AssertionError('invalid discriminator accepted')
                      except ValidationError:
                          pass
              try:
                  adapter.validate_python({'kindTag': 'future'})
                  raise AssertionError('unknown discriminator accepted')
              except ValidationError:
                  pass
          assert MixedService(kind_tag='service').zero == 0
          for construct in (
              lambda: MixedService(kind_tag='service', zero=False),
              lambda: BlockedService(kind_tag='service'),
              lambda: BlockedService.model_validate({'kindTag': 'service'}),
              lambda: BlockedService.model_validate_json('{"kindTag":"service"}'),
          ):
              try:
                  construct()
                  raise AssertionError('excluded discriminator or coerced scalar accepted')
              except ValidationError:
                  pass
          """.trimIndent(),
      ),
    )
  }
}
