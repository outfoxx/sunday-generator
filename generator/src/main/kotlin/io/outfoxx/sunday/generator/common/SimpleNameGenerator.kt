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

import amf.apicontract.client.platform.model.domain.EndPoint
import amf.apicontract.client.platform.model.domain.Operation
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.operationId

class SimpleNameGenerator : NameGenerator {

  private var generatedCount = 0

  override fun generate(
    endPoint: EndPoint,
    operation: Operation,
  ): String {

    val specifiedName = operation.name ?: operation.operationId
    if (specifiedName != null) {
      return specifiedName
    }

    generatedCount++

    return "method$generatedCount"
  }
}
