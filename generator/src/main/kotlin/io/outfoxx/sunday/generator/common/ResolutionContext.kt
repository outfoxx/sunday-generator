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

package io.outfoxx.sunday.generator.common

import amf.client.model.document.BaseUnit
import amf.client.model.domain.DomainElement
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.findDeclaringUnit
import io.outfoxx.sunday.generator.utils.findImportingUnit
import io.outfoxx.sunday.generator.utils.resolveRef

interface ResolutionContext {

  val unit: BaseUnit

  fun resolveRef(name: String, source: DomainElement): Pair<DomainElement, BaseUnit>? {
    val sourceUnit = unit.findDeclaringUnit(source)
    return sourceUnit.resolveRef(name)
      ?: sourceUnit.findImportingUnit(source, unit.allUnits)?.resolveRef(name)
  }
}
