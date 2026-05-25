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
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/**
 * Target-neutral parameter view used by IR emitters.
 */
data class GeneratedOperationParameter(
  val source: GeneratedParameter,
  val name: String,
  val wireName: String,
  val location: GeneratedParameter.Location,
  val type: GeneratedTypeRef,
  val required: Boolean,
  val defaultValue: Any?,
  val constantValue: Any?,
  val isNullable: Boolean,
) {

  /**
   * True when this parameter is represented by a generated constant value instead of a call-site argument.
   */
  val isConstant: Boolean = constantValue != null

  /**
   * True when emitted request maps need to remove null values for this parameter.
   */
  val shouldFilterNullValue: Boolean = !isConstant && isNullable
}

/**
 * Builds target-neutral parameter views for an operation.
 */
fun GeneratedOperation.operationParameterViews(
  identifierName: (GeneratedParameter) -> String = { parameter -> parameter.name },
  allocateName: (GeneratedParameter, String) -> String = { _, proposedName -> proposedName },
): List<GeneratedOperationParameter> =
  parameters.map { parameter ->
    GeneratedOperationParameter(
      source = parameter,
      name = allocateName(parameter, identifierName(parameter)),
      wireName = parameter.serializationName ?: parameter.name,
      location = parameter.location,
      type = parameter.type,
      required = parameter.required,
      defaultValue = parameter.defaultValue,
      constantValue = parameter.constantValue,
      isNullable = parameter.type.nullable || !parameter.required,
    )
  }

/**
 * Returns only parameters with the requested wire location.
 */
fun Iterable<GeneratedOperationParameter>.withLocation(location: GeneratedParameter.Location) =
  filter { parameter -> parameter.location == location }
