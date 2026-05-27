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
import io.outfoxx.sunday.generator.ir.GeneratedTarget

/**
 * Returns target metadata for the preferred target id or a fallback target id.
 */
fun GeneratedApi.target(
  preferred: String,
  fallback: String,
): GeneratedTarget? = targets[preferred] ?: targets[fallback]

/**
 * Returns target metadata for the preferred target id or a fallback target id.
 */
fun GeneratedModel.target(
  preferred: String,
  fallback: String,
): GeneratedTarget? = targets[preferred] ?: targets[fallback]

/**
 * Returns target metadata for the preferred target id or a fallback target id.
 */
fun GeneratedModelProperty.target(
  preferred: String,
  fallback: String,
): GeneratedTarget? = targets[preferred] ?: targets[fallback]
