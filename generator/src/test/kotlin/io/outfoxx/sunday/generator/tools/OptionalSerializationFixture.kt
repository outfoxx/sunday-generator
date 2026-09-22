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
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import java.nio.file.Path
import kotlin.io.path.writeText

/** Shared presence and nullability contract for generated-runtime regression tests. */
internal fun optionalSerializationApi(directory: Path): GeneratedApi {
  val file = directory.resolve("optional.yaml")
  file.writeText(
    OpenApiReferenceDocuments.document(
      "Optional serialization",
      """
      NullableText: {type: [string, 'null']}
      AliasRequest:
        type: object
        properties:
          nullableAlias: {${'$'}ref: '#/components/schemas/NullableText'}
          anyValue: {}
      StrictRequest:
        type: object
        required: [name]
        properties:
          name: {type: string}
          text: {type: string}
      Request:
        type: object
        required: [name, requiredNullable]
        properties:
          name: {type: string}
          requiredNullable: {type: [string, 'null']}
          optionalNullable: {type: [string, 'null']}
          text: {type: string}
          number: {type: integer}
          flag: {type: boolean}
          items: {type: array, items: {type: string}}
      """.trimIndent(),
    ),
  )
  return OpenApiToGeneratedApi().convert(file.toUri())
}
