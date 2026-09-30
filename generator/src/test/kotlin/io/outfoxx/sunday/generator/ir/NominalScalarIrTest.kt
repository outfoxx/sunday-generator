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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.ir.emit.GeneratedNominalTypes
import io.outfoxx.sunday.generator.tools.nominalScalarApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class NominalScalarIrTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `frontends retain scalar identity and union mode`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = nominalScalarApi(frontend, directory)
    val models = api.models.associateBy { it.name }
    assertTrue(models.getValue("BaseFactSid").nominal)
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, models.getValue("BaseFactSid").kind)
    assertEquals(
      "string",
      models
        .getValue("BaseFactSid")
        .aliases
        .single()
        .name,
    )
    assertTrue(models.getValue("BaseFactSid").validation.containsKey("pattern"))
    assertEquals(listOf("BaseFactSid", "BaseLossSid"), models.getValue("AnySid").aliases.map { it.name })
    assertEquals(if (frontend == "raml") null else GeneratedModel.UnionMode.ONE_OF, models.getValue("AnySid").unionMode)
    assertEquals(api.models, GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)).models)
  }

  @Test
  fun `transparent aliases preserve nominal union branches`(
    @TempDir directory: Path,
  ) {
    val api = nominalScalarApi("openapi", directory)
    val alias =
      GeneratedModel(
        "FactAlias",
        GeneratedModel.Kind.SCALAR_ALIAS,
        aliases = listOf(GeneratedTypeRef.named("BaseFactSid")),
      )
    val models = (api.models + alias).associateBy { it.name }
    val nominal = GeneratedNominalTypes { models[it.name] }
    val union =
      models
        .getValue(
          "AnySid",
        ).copy(aliases = listOf(GeneratedTypeRef.named("FactAlias"), GeneratedTypeRef.named("BaseLossSid")))
    assertEquals(listOf("BaseFactSid", "BaseLossSid"), nominal.branches(union).map { it.name })
    assertEquals(GeneratedTypeRef.scalar("string"), nominal.unionType(union))
  }

  @Test
  fun `old IR aliases keep legacy defaults`() {
    val api =
      GeneratedApi(
        name = "Nominal",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "test.yaml"),
        models =
          listOf(
            GeneratedModel(
              "Id",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
          ),
      )
    val yaml = GeneratedApiYaml.writeString(api)
    assertFalse(yaml.contains("nominal"))
    assertFalse(yaml.contains("unionMode"))
    assertFalse(
      GeneratedApiYaml
        .readString(yaml)
        .models
        .single()
        .nominal,
    )
  }
}
