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
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GeneratedProblemsTest {

  @Test
  fun `resolves problem references by generated name source name and source identity`() {
    val root = source("api.raml")
    val library = source("problems.raml")
    val rootProblem = problem("ProjectNotFoundProblem", "project_not_found", "project_not_found", root)
    val libraryProblem = problem("InvalidIdProblem", "invalid_id", "invalid_id", library)
    val index = apiIndex(rootProblem, libraryProblem)

    assertSame(rootProblem, GeneratedTypeRef.named("ProjectNotFoundProblem").problemOrNull(index))
    assertSame(libraryProblem, GeneratedTypeRef.named("invalid_id").problemOrNull(index))
    assertSame(libraryProblem, GeneratedTypeRef.named("InvalidIdProblem", source = library).problemOrNull(index))
    assertNull(GeneratedTypeRef.named("InvalidIdProblem", source = source("missing.raml")).problemOrNull(index))
  }

  @Test
  fun `collects service referenced problems in declaration order by type uri`() {
    val invalidId = problem("InvalidIdProblem", "invalid_id", "invalid_id")
    val invalidIdAlias = problem("InvalidIdAliasProblem", "invalid_id_alias", "invalid_id")
    val notFound = problem("ProjectNotFoundProblem", "project_not_found", "project_not_found")
    val service =
      GeneratedService(
        name = "Projects",
        operations =
          listOf(
            operation("fetch", "InvalidIdProblem", "ProjectNotFoundProblem"),
            operation("delete", "InvalidIdAliasProblem", "ProjectNotFoundProblem"),
          ),
      )
    val index = apiIndex(invalidId, invalidIdAlias, notFound)

    assertEquals(listOf(invalidId, notFound), service.referencedProblems(index))
  }

  @Test
  fun `resolves problem source code and type uri`() {
    val relative = problem("InvalidIdProblem", "invalid_id", "invalid_id")
    val absolute =
      problem(
        name = "ProjectNotFoundProblem",
        sourceName = "project_not_found",
        typeUri = "https://errors.example.com/v2/project_not_found",
      )

    assertEquals("invalid_id", relative.sourceCode)
    assertEquals("https://errors.example.com/v2/invalid_id", relative.resolvedTypeUri("https://errors.example.com/v2/"))
    assertEquals("project_not_found", absolute.sourceCode)
    assertEquals(
      "https://errors.example.com/v2/project_not_found",
      absolute.resolvedTypeUri("https://fallback.example.com/"),
    )
  }

  private fun apiIndex(vararg problems: GeneratedProblem) =
    GeneratedApiIndex(
      GeneratedApi(
        name = "Example",
        source = source("api.raml"),
        problems = problems.toList(),
      ),
    )

  private fun operation(
    id: String,
    vararg problems: String,
  ) = GeneratedOperation(
    id = id,
    method = "GET",
    path = "/projects",
    problems = problems.map { problem -> GeneratedTypeRef.named(problem) },
  )

  private fun problem(
    name: String,
    sourceName: String?,
    typeUri: String,
    source: GeneratedSourceSpec? = null,
  ) = GeneratedProblem(
    name = name,
    sourceName = sourceName,
    source = source,
    typeUri = typeUri,
  )

  private fun source(location: String) = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, location)
}
