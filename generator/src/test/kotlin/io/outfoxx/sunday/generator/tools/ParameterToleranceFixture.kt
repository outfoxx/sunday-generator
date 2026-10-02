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

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import java.nio.file.Path

/** Gives validation helpers adversarial parameter names while retaining frontend-produced schemas. */
internal fun parameterNameCollisionApi(
  frontend: String,
  directory: Path,
  names: List<String>,
): GeneratedApi {
  val api = parameterToleranceApi(frontend, directory)
  val state = GeneratedTypeRef.named("State")
  return api.copy(
    services =
      api.services.map { service ->
        service.copy(
          operations =
            service.operations.map { operation ->
              operation.copy(
                path = "/parameters",
                parameters =
                  names.map { name ->
                    val type =
                      when (name) {
                        "valid", "values" ->
                          GeneratedTypeRef(
                            GeneratedTypeRef.Kind.ARRAY,
                            "array",
                            arguments = listOf(state),
                          )
                        "key" -> GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = listOf(state))
                        else -> state
                      }
                    GeneratedParameter(name, GeneratedParameter.Location.QUERY, type, required = false)
                  },
              )
            },
        )
      },
  )
}

/** Exercises each parameter boundary with the same frontend-produced tolerant schemas. */
internal fun parameterToleranceApi(
  frontend: String,
  directory: Path,
  cookies: Boolean = false,
): GeneratedApi {
  val api = directionalToleranceApi(frontend, directory)
  val state = GeneratedTypeRef.named("State")
  val parameters =
    buildList {
      add(GeneratedParameter("pathState", GeneratedParameter.Location.PATH, state, required = true))
      add(
        GeneratedParameter(
          "queryStates",
          GeneratedParameter.Location.QUERY,
          GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(state)),
          required = false,
        ),
      )
      add(GeneratedParameter("headerState", GeneratedParameter.Location.HEADER, state, required = false))
      if (cookies) add(GeneratedParameter("cookieState", GeneratedParameter.Location.COOKIE, state, required = false))
      add(
        GeneratedParameter(
          "openState",
          GeneratedParameter.Location.QUERY,
          GeneratedTypeRef.named("OpenState"),
          required = false,
        ),
      )
      add(
        GeneratedParameter(
          "defaultState",
          GeneratedParameter.Location.QUERY,
          GeneratedTypeRef.named("DefaultState"),
          required = false,
        ),
      )
    }
  return api.copy(
    models = api.models + api.models.single { it.name == "State" }.copy(name = "DefaultState", tolerance = null),
    services =
      api.services.take(1).map { service ->
        service.copy(
          name = "Parameters",
          group = null,
          protocol = null,
          operations =
            service.operations.take(1).map {
              it.copy(
                id = "parameters",
                method = "GET",
                path = "/parameters/{pathState}",
                parameters = parameters,
                requestBody = null,
                streaming = null,
                exchange = null,
                protocol = null,
              )
            },
        )
      },
  )
}
