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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Source-bound problem lookup shared by registration, validation and nullification. */
internal class GeneratedProblemIndex(
  problems: List<GeneratedProblem>,
) {
  private val candidates =
    problems
      .flatMap { problem ->
        listOfNotNull(problem.name, problem.sourceName).map { it to problem }
      }.groupBy({ it.first }, { it.second })

  fun problemOrNull(reference: GeneratedTypeRef): GeneratedProblem? =
    candidates[reference.name].orEmpty().let { problems ->
      problems.firstOrNull { it.source.documentIdentity() == reference.source?.documentIdentity() }
        ?: problems.firstOrNull().takeIf { reference.source == null }
    }
}
