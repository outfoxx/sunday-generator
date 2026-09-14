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

  /** Canonical nullable references and representable inherited field restrictions used by real SDKs. */
  val sdkCompatibility: String =
    """
    CurrentAssetState:
      type: string
      enum: [rendered, refused, unrecognized]
      x-unknown-value: unrecognized
    CurrentAsset:
      type: object
      required: [state]
      properties: {state: {${'$'}ref: '#/components/schemas/CurrentAssetState'}}
      discriminator:
        propertyName: state
        mapping:
          rendered: '#/components/schemas/RenderedAsset'
          refused: '#/components/schemas/RefusedAsset'
    RenderedAsset:
      allOf:
        - ${'$'}ref: '#/components/schemas/CurrentAsset'
        - type: object
          required: [versionId]
          properties: {versionId: {type: string}}
    RefusedAsset:
      allOf:
        - ${'$'}ref: '#/components/schemas/CurrentAsset'
        - type: object
          required: [refusalReason]
          properties: {refusalReason: {type: string}}
    EntityDetails:
      type: object
      properties:
        currentAsset:
          ${'$'}ref: '#/components/schemas/CurrentAsset'
          nullable: true
          description: Most recent asset
    NarrativeChangeEventType:
      type: string
      enum: [character, prop, unrecognized]
      x-unknown-value: unrecognized
    BaseNarrativeChangeEvent:
      type: object
      required: [type, id]
      properties:
        type: {${'$'}ref: '#/components/schemas/NarrativeChangeEventType'}
        id: {type: string}
        count: {type: integer, minimum: 0, maximum: 100}
    CharacterChangeEvent:
      allOf:
        - ${'$'}ref: '#/components/schemas/BaseNarrativeChangeEvent'
        - type: object
          properties:
            type: {type: string, enum: [character]}
            count: {type: integer, minimum: 1, maximum: 20, default: 20}
    FactEditOp:
      type: object
      required: [op]
      properties: {op: {type: string, enum: [addFact, deleteFact]}}
      discriminator:
        propertyName: op
        mapping:
          addFact: '#/components/schemas/AddFactOp'
          deleteFact: '#/components/schemas/DeleteFactOp'
    AddFactOp:
      allOf:
        - ${'$'}ref: '#/components/schemas/FactEditOp'
        - type: object
          required: [value]
          properties:
            op: {type: string, enum: [addFact]}
            value: {type: string}
    DeleteFactOp:
      allOf:
        - ${'$'}ref: '#/components/schemas/FactEditOp'
        - type: object
          properties: {op: {type: string, enum: [deleteFact]}}
    HttpProblem:
      type: object
      properties:
        type: {type: string, format: uri, nullable: true}
        title: {type: string, nullable: true}
        status: {type: integer, nullable: true}
        detail: {type: string, nullable: true}
        instance: {type: string, format: uri, nullable: true}
    BadRequestProblem:
      allOf:
        - ${'$'}ref: '#/components/schemas/HttpProblem'
        - type: object
          properties:
            title: {type: string, const: Bad request}
            status: {type: integer, const: 400}
            detail: {type: string, default: Invalid request}
    EventTypeAlias: {${'$'}ref: '#/components/schemas/NarrativeChangeEventType'}
    ScalarRestrictionsBase:
      type: object
      properties:
        zero: {type: integer, nullable: true}
        flag: {type: boolean, nullable: true}
        choice: {}
        value: {type: string, nullable: true}
        mode: {${'$'}ref: '#/components/schemas/EventTypeAlias'}
    ScalarRestrictions:
      allOf:
        - ${'$'}ref: '#/components/schemas/ScalarRestrictionsBase'
        - type: object
          required: [value]
          properties:
            zero: {type: integer, const: 0, default: 0}
            flag: {type: boolean, const: false, default: false}
            choice: {enum: [null, 0, false]}
            value: {type: string, minLength: 1}
            mode: {type: string, enum: [character], default: character}
    SdkInlineNode:
      type: object
      properties:
        value: {type: string}
        next: {${'$'}ref: '#/components/schemas/SdkInlineNode'}
    SdkInlineBaseDetail: {type: string}
    SdkInlineBase:
      type: object
      properties:
        detail:
          type: object
          properties:
            value: {type: string}
            next: {${'$'}ref: '#/components/schemas/SdkInlineNode'}
        selection:
          anyOf: [{type: string}, {type: integer}]
        tags: {type: array, items: {type: string}}
    SdkInlineAlias: {${'$'}ref: '#/components/schemas/SdkInlineBase'}
    SdkInlineMiddle:
      allOf:
        - ${'$'}ref: '#/components/schemas/SdkInlineBase'
        - type: object
          properties: {note: {type: string}}
    SdkInlineChild:
      allOf:
        - ${'$'}ref: '#/components/schemas/SdkInlineMiddle'
        - type: object
          required: [detail, selection]
          properties:
            tags: {type: array, items: {type: string}, uniqueItems: true}
    SdkInlineAliasChild:
      allOf:
        - ${'$'}ref: '#/components/schemas/SdkInlineAlias'
        - type: object
          required: [detail, selection]
          properties:
            tags: {type: array, items: {type: string}, uniqueItems: true}
    ScalarWireState: {type: string, enum: [a, good]}
    ScalarWireAlias: {${'$'}ref: '#/components/schemas/ScalarWireState'}
    ScalarWireBase:
      type: object
      properties:
        state: {${'$'}ref: '#/components/schemas/ScalarWireAlias'}
        url: {type: string, format: uri}
        uuid: {type: string, format: uuid}
    ScalarWireChild:
      allOf: [{${'$'}ref: '#/components/schemas/ScalarWireBase'}]
      properties:
        state: {minLength: 2, maxLength: 4, pattern: '^go'}
        url: {const: 'https://example.test/', default: 'https://example.test/'}
        uuid: {const: '00000000-0000-0000-0000-000000000000'}
    SdkIntegerBase:
      type: object
      properties: {count: {type: integer}}
    SdkIntegerChild:
      allOf: [{${'$'}ref: '#/components/schemas/SdkIntegerBase'}]
      properties: {count: {minimum: 1, maximum: 3, default: 1}}
    SdkFirstStatus: {type: string, enum: [a, b]}
    SdkSecondStatus: {type: string, enum: [b, c]}
    SdkFirstParent:
      type: object
      properties: {status: {${'$'}ref: '#/components/schemas/SdkFirstStatus'}}
    SdkSecondParent:
      type: object
      properties: {status: {${'$'}ref: '#/components/schemas/SdkSecondStatus'}}
    SdkConflictingChild:
      allOf: [{${'$'}ref: '#/components/schemas/SdkFirstParent'}, {${'$'}ref: '#/components/schemas/SdkSecondParent'}]
    SdkMappedPet:
      type: object
      required: [kind]
      properties: {kind: {type: string}}
      discriminator:
        propertyName: kind
        mapping: {cat: '#/components/schemas/SdkWrappedCat'}
    SdkMappedCat:
      type: object
      allOf: [{${'$'}ref: '#/components/schemas/SdkMappedPet'}]
      required: [name]
      properties: {name: {type: string}}
    SdkWrappedCat:
      type: object
      description: Mapped wrapper retains its wire identity
      allOf: [{${'$'}ref: '#/components/schemas/SdkMappedCat'}]
    SdkAliasBase:
      type: object
      required: [label, count]
      properties:
        label: {type: string}
        count: {type: integer, minimum: 0}
    SdkAliasWrapper:
      type: object
      allOf: [{${'$'}ref: '#/components/schemas/SdkAliasBase'}]
    SdkAliasChain:
      type: object
      allOf: [{${'$'}ref: '#/components/schemas/SdkAliasWrapper'}]
    SdkAliasChild:
      allOf: [{${'$'}ref: '#/components/schemas/SdkAliasChain'}]
      required: [extra]
      properties:
        count: {minimum: 1}
        extra: {type: string}
    SdkMultiFirst:
      type: object
      required: [a]
      properties:
        a: {type: string}
        count: {type: integer, minimum: 0}
        state: {type: string, enum: [a, b]}
    SdkMultiSecond:
      type: object
      required: [b]
      properties:
        b: {type: string}
        count: {type: integer, maximum: 10}
        state: {type: string, enum: [b, c]}
    SdkMultiChild:
      allOf: [{${'$'}ref: '#/components/schemas/SdkMultiFirst'}, {${'$'}ref: '#/components/schemas/SdkMultiSecond'}]
      properties: {count: {minimum: 1, const: 2, default: 2}}
    SdkMultiReversed:
      allOf: [{${'$'}ref: '#/components/schemas/SdkMultiSecond'}, {${'$'}ref: '#/components/schemas/SdkMultiFirst'}]
      properties: {count: {minimum: 1, const: 2, default: 2}}
    SdkTimestamp: {type: string, format: date-time}
    SdkTimestampAlias: {${'$'}ref: '#/components/schemas/SdkTimestamp'}
    SdkTemporalBase:
      type: object
      required: [timestamp]
      properties:
        timestamp: {${'$'}ref: '#/components/schemas/SdkTimestampAlias'}
        local: {type: string, format: datetime-only}
        clockTime: {type: string, format: time}
        calendarDate: {type: string, format: date}
    SdkTemporalChild:
      allOf: [{${'$'}ref: '#/components/schemas/SdkTemporalBase'}]
      properties:
        timestamp: {const: '2026-09-14T00:00:00Z', minLength: 20, maxLength: 20, pattern: 'T00:00:00Z$'}
        local: {const: '2026-09-14T00:00:00'}
        clockTime: {const: '00:00:00'}
        calendarDate: {const: '2026-09-14'}
    SdkDefaultParent:
      type: object
      properties: {count: {type: integer, default: 1}}
    SdkDefaultChild:
      allOf: [{${'$'}ref: '#/components/schemas/SdkDefaultParent'}]
      properties: {count: {minimum: 2, default: 2}}
    SdkBytes: {type: string, format: byte}
    SdkBytesAlias: {${'$'}ref: '#/components/schemas/SdkBytes'}
    SdkByteBase:
      type: object
      properties:
        data: {${'$'}ref: '#/components/schemas/SdkBytesAlias'}
        choices: {${'$'}ref: '#/components/schemas/SdkBytesAlias'}
        encoded: {${'$'}ref: '#/components/schemas/SdkBytesAlias'}
    SdkByteRestrictions:
      allOf: [{${'$'}ref: '#/components/schemas/SdkByteBase'}]
      required: [data]
      properties:
        data: {const: 'SGk='}
        choices: {enum: ['', 'SGk=', '/wA=']}
        encoded: {minLength: 4, maxLength: 4, pattern: '^SG'}
    SdkEnvelope:
      type: object
      properties:
        integerDefault: {${'$'}ref: '#/components/schemas/SdkIntegerChild'}
        conflictingParents: {${'$'}ref: '#/components/schemas/SdkConflictingChild'}
        mappedAlias: {${'$'}ref: '#/components/schemas/SdkMappedPet'}
        collapsedAlias: {${'$'}ref: '#/components/schemas/SdkAliasChild'}
        multipleParents: {${'$'}ref: '#/components/schemas/SdkMultiChild'}
        reversedParents: {${'$'}ref: '#/components/schemas/SdkMultiReversed'}
        temporalRestrictions: {${'$'}ref: '#/components/schemas/SdkTemporalChild'}
        childDefault: {${'$'}ref: '#/components/schemas/SdkDefaultChild'}
        byteRestrictions: {${'$'}ref: '#/components/schemas/SdkByteRestrictions'}
        scalarWire: {${'$'}ref: '#/components/schemas/ScalarWireChild'}
        uuid: {type: string, format: uuid, default: '00000000-0000-0000-0000-000000000000'}
        timestamp: {type: string, format: date-time, default: '2026-01-01T01:00:00.125+01:00'}
        aliasInline: {${'$'}ref: '#/components/schemas/SdkInlineAliasChild'}
        inline: {${'$'}ref: '#/components/schemas/SdkInlineChild'}
        entity: {${'$'}ref: '#/components/schemas/EntityDetails'}
        event: {${'$'}ref: '#/components/schemas/CharacterChangeEvent'}
        edit: {${'$'}ref: '#/components/schemas/FactEditOp'}
        problem: {${'$'}ref: '#/components/schemas/BadRequestProblem'}
        restrictions: {${'$'}ref: '#/components/schemas/ScalarRestrictions'}
    """.trimIndent()

  /** Decimal integer spelling for the Swift and IR default regressions. */
  val sdkCompatibilityDecimalDefaults: String =
    sdkCompatibility.replace("maximum: 3, default: 1}", "maximum: 3, default: 1.0}")

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
