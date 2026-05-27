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

import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import java.net.URI

/**
 * Returns the problem matching a generated type reference from an API index.
 */
fun GeneratedTypeRef.problemOrNull(index: GeneratedApiIndex): GeneratedProblem? = index.problemOrNull(this)

/**
 * Returns problems referenced by an operation in declaration order.
 */
fun GeneratedOperation.referencedProblems(index: GeneratedApiIndex): List<GeneratedProblem> =
  problems
    .mapNotNull { problem -> problem.problemOrNull(index) }
    .distinctBy { problem -> problem.typeUri }

/**
 * Returns problems referenced by all operations in a service in declaration order.
 */
fun GeneratedService.referencedProblems(index: GeneratedApiIndex): List<GeneratedProblem> =
  operations
    .flatMap { operation -> operation.referencedProblems(index) }
    .distinctBy { problem -> problem.typeUri }

/**
 * Source problem code before target fallback URI resolution.
 */
val GeneratedProblem.sourceCode: String
  get() = sourceName ?: name

/**
 * Resolves relative problem type URIs against a target default problem base URI.
 */
fun GeneratedProblem.resolvedTypeUri(defaultProblemBaseUri: String): String {
  val uri = URI(typeUri)
  return if (uri.isAbsolute) {
    typeUri
  } else {
    URI(defaultProblemBaseUri).resolve("./$typeUri").toString()
  }
}
