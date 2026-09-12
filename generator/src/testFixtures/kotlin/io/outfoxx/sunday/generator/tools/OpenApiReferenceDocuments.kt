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

/** Schema fragments shared by library, CLI, and Gradle remote-reference regression scenarios. */
object OpenApiReferenceDocuments {
  /** Wraps component declarations without changing their order or schema contents. */
  fun document(
    title: String,
    vararg schemas: String,
  ): String =
    """
    openapi: 3.1.0
    info: {title: $title, version: 1.0.0}
    paths: {}
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.joinToString("\n").prependIndent("    ")

  /** Nullable array and ignored conditional metadata. */
  val nullableValues: String =
    """
    NullableValues:
      ${'$'}anchor: values
      type: [array, 'null']
      items: {type: string}
      then: {type: string}
    """.trimIndent()

  /** Equivalent boolean and empty schema targets. */
  val booleanSchemas: String =
    """
    Anything: true
    Empty: {}
    """.trimIndent()

  /** Implicit discriminator hierarchy base. */
  val pet: String =
    """
    Pet:
      type: object
      required: [kind]
      properties: {kind: {type: string}}
      discriminator: {propertyName: kind}
    """.trimIndent()

  /** Named subtype with a documentary override of its inherited field. */
  val cat: String =
    """
    Cat:
      allOf:
        - ${'$'}ref: '#/components/schemas/Pet'
        - type: object
          properties:
            kind: {type: string, description: The animal kind}
            lives: {type: integer}
    """.trimIndent()

  /** Unchanged variant retained when discriminator mappings are inferred. */
  val dog: String =
    """
    Dog:
      allOf:
        - ${'$'}ref: '#/components/schemas/Pet'
        - type: object
          properties: {barks: {type: boolean}}
    """.trimIndent()

  /** Documentary intersections through nullable unions, recursive properties, and inherited fields. */
  val records: String =
    """
    RecordNode:
      allOf:
        - type: object
          required: [id]
          properties:
            id: {type: string}
            direct: {${'$'}ref: '#/components/schemas/RecordNode', description: Parent direct}
            wrapped: {allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}], description: Parent wrapped}
            next:
              anyOf:
                - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], title: Shared, description: Parent next}
                - {type: 'null'}
        - properties:
            direct: {${'$'}ref: '#/components/schemas/RecordNode', description: Updated direct}
            wrapped: {allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}], description: Updated wrapped}
            next:
              anyOf:
                - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], description: Updated next}
                - {type: 'null'}
    BaseRecord:
      type: object
      required: [id]
      properties:
        id: {type: string, description: Parent identifier}
        direct: {${'$'}ref: '#/components/schemas/RecordNode', description: Parent direct}
        wrapped: {allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}], description: Parent wrapped}
        payload:
          anyOf: [{type: string, description: Parent payload}, {type: 'null'}]
        next:
          anyOf:
            - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], title: Shared, description: Parent next}
            - {type: 'null'}
    DocumentedRecord:
      allOf:
        - ${'$'}ref: '#/components/schemas/BaseRecord'
        - type: object
          required: [id]
          properties:
            id: {type: string, description: Child identifier}
            direct: {${'$'}ref: '#/components/schemas/RecordNode', description: Child direct}
            wrapped: {allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}], description: Child wrapped}
            payload:
              anyOf: [{type: string, description: Child payload}, {type: 'null'}]
            next:
              anyOf:
                - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], description: Child next}
                - {type: 'null'}
            detail: {type: string}
    """.trimIndent()

  /** A hierarchy whose variants are reachable only through relative discriminator mapping targets. */
  fun mappedPet(includeDog: Boolean = false): String =
    """
    MappedPet:
      ${'$'}id: ./mapped-cat?base=pet
      type: object
      required: [kind]
      properties: {kind: {type: string}}
      discriminator:
        propertyName: kind
        mapping: {kitty: '?schema=cat'${if (includeDog) ", hound: mapped-dog" else ""}}
    """.trimIndent()

  /** Mapping-only subtype, optionally introducing a new transitive dependency during revalidation. */
  fun mappedCat(includeName: Boolean = false): String =
    """
    allOf:
      - ${'$'}ref: 'user.yaml#/components/schemas/MappedPet'
      - type: object
        required: [lives]
        properties:
          lives: {type: integer}
    """.trimIndent() + if (includeName) "\n      nickname: {\$ref: 'mapped-name'}" else ""

  /** An unchanged second mapping-only subtype. */
  val mappedDog: String =
    """
    allOf:
      - ${'$'}ref: 'user.yaml#/components/schemas/MappedPet'
      - type: object
        required: [barks]
        properties: {barks: {type: boolean}}
    """.trimIndent()
}
