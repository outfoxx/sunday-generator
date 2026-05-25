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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedModelScope
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedApiIndexTest {

  @Test
  fun `resolves models by name scope and source identity`() {
    val root = source("api.raml")
    val library = source("library.raml")
    val rootModel = model("Test", root)
    val libraryModel = model("Test", library)
    val index = apiIndex(rootModel, libraryModel)

    assertSame(rootModel, GeneratedTypeRef.named("Test").modelOrNull(index))
    assertSame(libraryModel, GeneratedTypeRef.named("Test", source = library).modelOrNull(index))
    assertNull(GeneratedTypeRef.named("Test", source = source("missing.raml")).modelOrNull(index))
  }

  @Test
  fun `returns service owned scoped models referenced by operations`() {
    val serviceScope = scope("Projects", "fetch", GeneratedModelScope.Usage.PARAMETER, name = "state")
    val responseScope = scope("Projects", "fetch", GeneratedModelScope.Usage.RESPONSE_BODY, status = 200)
    val otherServiceScope = scope("Users", "fetch", GeneratedModelScope.Usage.PARAMETER, name = "state")
    val parameterModel = model("FetchState", scope = serviceScope)
    val responseModel = model("FetchResponse", scope = responseScope)
    val otherServiceModel = model("FetchState", scope = otherServiceScope)
    val freeformModel = model("Freeform", scope = serviceScope, freeform = true)
    val service =
      GeneratedService(
        name = "Projects",
        operations =
          listOf(
            GeneratedOperation(
              id = "fetch",
              method = "GET",
              path = "/projects/{id}",
              parameters =
                listOf(
                  GeneratedParameter(
                    name = "state",
                    location = GeneratedParameter.Location.QUERY,
                    type = GeneratedTypeRef.named("FetchState", scope = serviceScope),
                  ),
                  GeneratedParameter(
                    name = "metadata",
                    location = GeneratedParameter.Location.QUERY,
                    type = GeneratedTypeRef.named("Freeform", scope = serviceScope),
                  ),
                ),
              requestBody =
                GeneratedPayload(
                  type = GeneratedTypeRef.scalar("string"),
                ),
              responses =
                listOf(
                  GeneratedResponse(
                    status = 200,
                    type = GeneratedTypeRef.named("FetchResponse", scope = responseScope),
                  ),
                ),
            ),
          ),
      )
    val index =
      GeneratedApiIndex(
        GeneratedApi(
          name = "Example",
          source = source("api.raml"),
          services = listOf(service),
          models = listOf(parameterModel, responseModel, otherServiceModel, freeformModel),
        ),
      )

    assertEquals(listOf(parameterModel, responseModel), index.referencedScopedModels(service))
  }

  @Test
  fun `flattens nested union refs in declaration order`() {
    val first = GeneratedTypeRef.named("First")
    val second = GeneratedTypeRef.named("Second")
    val third = GeneratedTypeRef.named("Third")
    val union =
      GeneratedTypeRef(
        kind = GeneratedTypeRef.Kind.UNION,
        name = "union",
        arguments =
          listOf(
            first,
            GeneratedTypeRef(kind = GeneratedTypeRef.Kind.UNION, name = "nested", arguments = listOf(second, third)),
          ),
      )

    assertEquals(listOf(first, second, third), union.flattenedUnionTypes())
  }

  @Test
  fun `resolves preferred target before fallback target`() {
    val preferred = GeneratedTarget(typeName = "ClientType")
    val fallback = GeneratedTarget(typeName = "CommonType")
    val api = GeneratedApi(name = "Example", source = source("api.raml"), targets = mapOf("kotlin" to fallback))
    val model = model("Test", targets = mapOf("kotlinClient" to preferred, "kotlin" to fallback))
    val property =
      GeneratedModelProperty(
        name = "value",
        type = GeneratedTypeRef.scalar("string"),
        targets = mapOf("kotlin" to fallback),
      )

    assertSame(fallback, api.target("kotlinClient", "kotlin"))
    assertSame(preferred, model.target("kotlinClient", "kotlin"))
    assertSame(fallback, property.target("kotlinClient", "kotlin"))
  }

  private fun apiIndex(vararg models: GeneratedModel) =
    GeneratedApiIndex(
      GeneratedApi(
        name = "Example",
        source = source("api.raml"),
        models = models.toList(),
      ),
    )

  private fun model(
    name: String,
    source: GeneratedSourceSpec? = null,
    scope: GeneratedModelScope? = null,
    targets: Map<String, GeneratedTarget> = mapOf(),
    freeform: Boolean = false,
  ) = GeneratedModel(
    name = name,
    kind = GeneratedModel.Kind.OBJECT,
    source = source,
    scope = scope,
    properties =
      if (freeform) {
        listOf()
      } else {
        listOf(GeneratedModelProperty(name = "id", type = GeneratedTypeRef.scalar("string")))
      },
    targets = targets,
  )

  private fun source(location: String) = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, location)

  private fun scope(
    service: String,
    operation: String,
    usage: GeneratedModelScope.Usage,
    name: String? = null,
    status: Int? = null,
  ) = GeneratedModelScope(
    service = service,
    operation = operation,
    usage = usage,
    name = name,
    status = status,
  )
}
