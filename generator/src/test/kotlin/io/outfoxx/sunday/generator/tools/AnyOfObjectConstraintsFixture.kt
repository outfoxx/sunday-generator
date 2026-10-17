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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import java.nio.file.Path
import kotlin.io.path.writeText

/** Inclusive object alternatives from issue #277. */
internal fun anyOfObjectConstraintsApi(
  shape: String,
  directory: Path,
): GeneratedApi {
  val properties = "properties: {branchId: {type: string}, targetCommitId: {type: string}}"
  val schema =
    when (shape.removeSuffix("-composed")) {
      "nested" ->
        """
        CheckoutRequest:
          type: object
          anyOf:
            - {${'$'}ref: '#/components/schemas/Targets'}
            - {type: object, required: [targetCommitId], $properties}
        Targets:
          anyOf:
            - {type: object, required: [branchId], $properties}
            - {type: object, required: [targetCommitId], $properties}
        """.trimIndent()
      "discriminator", "implicit-discriminator" ->
        """
        CheckoutRequest:
          required: [kind]
          discriminator:
            propertyName: kind
            mapping:
              cat: '#/components/schemas/Cat'
              dog: '#/components/schemas/Dog'
          anyOf:
            - {${'$'}ref: '#/components/schemas/Cat'}
            - {${'$'}ref: '#/components/schemas/Dog'}
        Cat: {type: object, properties: {kind: {type: string}}}
        Dog: {type: object, properties: {kind: {type: string}}}
        """.trimIndent()
      "inline" ->
        """
        CheckoutRequest:
          anyOf:
            - {type: object, required: [branchId], $properties}
            - {type: object, required: [targetCommitId], $properties}
        """.trimIndent()
      "named" ->
        """
        CheckoutRequest:
          anyOf:
            - {${'$'}ref: '#/components/schemas/BranchCheckoutRequest'}
            - {${'$'}ref: '#/components/schemas/CommitCheckoutRequest'}
        BranchCheckoutRequest: {type: object, required: [branchId], $properties}
        CommitCheckoutRequest: {type: object, required: [targetCommitId], $properties}
        """.trimIndent()
      else ->
        """
        CheckoutRequest:
          type: object
          $properties
          anyOf:
            - required: [branchId]
            - required: [targetCommitId]
        """.trimIndent()
    }
  val source = directory.resolve("checkout.yaml")
  source.writeText(
    """
    openapi: 3.1.0
    info: {title: Checkout, version: 1.0.0}
    paths:
      /checkout:
        put:
          operationId: checkout
          requestBody:
            required: true
            content:
              application/json:
                schema: {${'$'}ref: '#/components/schemas/CheckoutRequest'}
          responses:
            '204': {description: Updated}
    components:
      schemas:
    """.trimIndent() + "\n" +
      (
        if (shape == "implicit-discriminator") {
          schema.replace(
            "    mapping:\n      cat: '#/components/schemas/Cat'\n      dog: '#/components/schemas/Dog'\n",
            "",
          )
        } else {
          schema
        }
      ).prependIndent("    "),
  )
  val sources = mutableListOf(source.toUri())
  if (shape.endsWith("-composed")) {
    val events = directory.resolve("events.yaml")
    events.writeText("asyncapi: 3.0.0\ninfo: {title: Checkout, version: 1.0.0}\nchannels: {}\noperations: {}\n")
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}

/** Either field or both fields must survive round trips. */
internal val anyOfObjectValidWires =
  listOf(
    """{"branchId":"branch-1"}""",
    """{"targetCommitId":"commit-1"}""",
    """{"branchId":"branch-1","targetCommitId":"commit-1"}""",
  ).joinToString(prefix = "[", postfix = "]")

/** Inputs matching neither object alternative. */
internal val anyOfObjectInvalidWires = """[{}, {"future":"not-a-target"}, {"branchId":null}, "not-an-object"]"""
