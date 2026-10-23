/*
 * Copyright 2020 Outfox, Inc.
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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Path

/**
 * YAML serialization for the Sunday generated API IR.
 */
object GeneratedApiYaml {

  private val mapper =
    YAMLMapper
      .builder()
      .addModule(KotlinModule.Builder().build())
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
      .defaultPropertyInclusion(
        JsonInclude.Value.construct(JsonInclude.Include.NON_DEFAULT, JsonInclude.Include.NON_DEFAULT),
      ).build()

  /** Serializes a generated API IR value as YAML. */
  fun writeString(api: GeneratedApi): String = mapper.writeValueAsString(api)

  /** Deserializes a generated API IR value from YAML. */
  fun readString(yaml: String): GeneratedApi {
    val document = mapper.readTree(yaml)

    // Compact IR may omit a declaration source only within its original containing document.
    // Materialize it before composition; serialized declarations always retain their concrete source.
    fun inheritSource(
      node: JsonNode,
      owner: JsonNode,
    ) {
      if (node is ObjectNode && !node.has("source")) node.set<JsonNode>("source", owner)
      node.path("placementDeclarations").forEach { inheritSource(it, node.path("source")) }
    }
    (document.path("models") + document.path("problems")).forEach { inheritSource(it, document.path("source")) }
    val api = mapper.treeToValue(document, GeneratedApi::class.java)
    require(api.irVersion == GeneratedApi.CURRENT_IR_VERSION) {
      "Unsupported Sunday IR version '${api.irVersion}'; expected '${GeneratedApi.CURRENT_IR_VERSION}'"
    }
    return api
  }

  /** Deserializes a generated API IR value from a YAML file. */
  fun readPath(path: Path): GeneratedApi = readString(Files.readString(path))
}
