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

package io.outfoxx.sunday.generator.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** Identical direct-decoding cases for every target and problem library (issue #319). */
internal val problemOneOfCases: String =
  run {
    val mapper = jacksonObjectMapper()
    val cases = mapper.createArrayNode()

    fun add(
      union: String,
      wire: JsonNode,
      expected: String? = null,
      error: String? = null,
    ) {
      cases.addObject().apply {
        put("union", union)
        set<JsonNode>("wire", wire)
        put("expected", expected)
        put("error", error)
      }
    }
    for (union in listOf("Problems", "ReversedProblems", "TaggedProblems")) {
      for ((kind, title) in listOf("item" to "Item", "collection" to "Collection")) {
        val raw =
          mapper
            .createObjectNode()
            .put("type", "https://example.org/problems/$kind-missing")
            .put("title", "$title missing")
            .put("status", 404)
            .put("detail", "No matching resource")
        add(union, raw, "${title}MissingProblem")
        add(union, raw.deepCopy().put("status", 404.0), "${title}MissingProblem")
        for ((field, value) in listOf(
          "type" to "https://example.org/problems/unknown",
          "title" to "Wrong",
          "detail" to "x",
        )) {
          add(union, raw.deepCopy().put(field, value))
        }
        for (invalid in listOf(
          raw.deepCopy().put("status", 400),
          raw.deepCopy().put("status", "404"),
          raw.deepCopy().putNull("type"),
          raw.deepCopy().put("status", true),
          raw.deepCopy().put("status", 404.5),
        )) {
          add(union, invalid)
        }
        for (required in listOf("type", "title", "status", "detail")) {
          add(union, raw.deepCopy().apply { remove(required) })
        }
        if (union == "Problems" && kind == "collection") {
          add("AmbiguousProblems", raw, error = "ambiguous")
          add("ConstrainedProblems", raw.deepCopy().put("detail", "short"), "ShortDetailProblem")
          add("ConstrainedProblems", raw.deepCopy().put("detail", "Long detail"), "LongDetailProblem")
          add("ConstrainedProblems", raw.deepCopy().put("detail", "x"), error = "constraints")
        }
      }
      add(union, mapper.createArrayNode())
      add(union, mapper.createObjectNode())
    }
    cases.toString()
  }
