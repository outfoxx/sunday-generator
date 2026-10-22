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
import java.nio.file.Files
import java.nio.file.Path

/** Acceptance fixtures that never insert declaration, binding or placement metadata into parsed IR. */
internal class PlacementNativeFixture(
  private val directory: Path,
) {
  val sources = mutableListOf<Path>()

  fun export(reverse: Boolean = false): GeneratedApi =
    GeneratedApiIrExporter().export((if (reverse) sources.reversed() else sources).map { it.toUri() })

  private fun annotations() {
    javaClass.getResourceAsStream("/sunday.raml")!!.use {
      Files.copy(it, directory.resolve("sunday.raml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private fun placement(owner: String) =
    """
    kotlin: {package: example.$owner}
    swift: {module: ${if (owner == "first") "SharedModels" else "SundayGenTest"}}
    typescript: {module: $owner}
    python: {package: $owner, module: models}
    """.trimIndent()

  fun importedNesting(): GeneratedApi {
    annotations()
    Files.writeString(
      directory.resolve("library.raml"),
      """
      #%RAML 1.0 Library
      uses:
        sunday: sunday.raml
      (sunday.placement):
      """.trimIndent() + "\n" + placement("first").prependIndent("  ") + "\n" +
        """
        types:
          Parent: {type: object, properties: {value: string}}
          Child:
            type: object
            (sunday.nested): {enclosedIn: Parent, name: Child}
            properties: {value: integer}
        """.trimIndent() + "\n",
    )
    val source = directory.resolve("api.raml")
    Files.writeString(
      source,
      """
      #%RAML 1.0
      title: Placement
      uses:
        sunday: sunday.raml
        library: library.raml
      (sunday.placement):
      """.trimIndent() + "\n" + placement("second").prependIndent("  ") + "\n" +
        """
        types:
          Parent: {type: object, properties: {value: string}}
          Holder:
            type: object
            properties: {local: Parent, parent: library.Parent, child: library.Child}
        """.trimIndent() + "\n",
    )
    sources.add(source)
    return export()
  }

  fun distinctEnclosures(sameParent: Boolean = false): GeneratedApi {
    annotations()
    for (name in listOf("First", "Second")) {
      val parent = if (sameParent) "Parent" else name
      val nestedName =
        if (sameParent) {
          if (name == "First") "Nested_Child" else "NestedChild"
        } else {
          "Child"
        }
      val properties = if (sameParent) "{value: string}" else "{child: Child}"
      val source = directory.resolve("$name.raml")
      Files.writeString(
        source,
        """
        #%RAML 1.0
        title: Placement
        uses: {sunday: sunday.raml}
        (sunday.apiId): placement
        (sunday.placement):
        """.trimIndent() + "\n" + placement("second").prependIndent("  ") + "\n" +
          """
          types:
            $parent: {type: object, properties: $properties}
            Child:
              type: object
              (sunday.nested): {enclosedIn: $parent, name: $nestedName}
              properties: {value: string}
          """.trimIndent() + "\n",
      )
      sources.add(source)
    }
    return export()
  }

  fun duplicateOperations(
    kind: String,
    payloads: Boolean = false,
    distinctModels: Boolean = false,
  ): GeneratedApi {
    for (owner in listOf("a", "b")) {
      val root = Files.createDirectories(directory.resolve(owner))
      PlacementRegressionFixture(root, kind).service()
      val path = root.resolve(if (kind == "raml") "api.raml" else "api.yaml")
      var content =
        Files.readString(path).replace(
          "  kotlin: {package: example.document}",
          placement("second").prependIndent("  "),
        )
      if (payloads) {
        val modelPlacement = if (distinctModels && owner == "a") "first" else "second"
        if (kind == "raml") {
          content =
            content
              .replace(
                "types: {}",
                "types:\n  Record:\n    type: object\n    (sunday.placement):\n" +
                  placement(modelPlacement).prependIndent("      ") + "\n    properties: {value: string}",
              ).replace("204:", "200:\n        body:\n          application/json: Record")
        } else {
          content =
            content
              .replace(
                "components: {}",
                "components:\n  schemas:\n    Record:\n      type: object\n      required: [value]\n" +
                  "      properties: {value: {type: string}}\n      x-sunday-placement:\n" +
                  placement(modelPlacement).prependIndent("        "),
              ).replace(
                "'204': {description: empty}",
                "'200': {description: value, content: {application/json: " +
                  "{schema: {${'$'}ref: '#/components/schemas/Record'}}}}",
              ).replace("payload: {type: string}", "payload: {${'$'}ref: '#/components/schemas/Record'}")
        }
      }
      Files.writeString(path, content)
      sources.add(path)
    }
    if (kind == "composed") sources.add(directory.resolve("a/events.yaml"))
    return export()
  }

  fun hierarchy(
    kind: String,
    nested: Boolean = false,
    cycle: Boolean = false,
  ): GeneratedApi {
    val raml =
      """
      Container: {type: object}
      Base:
        type: object
        discriminator: kind
        ${if (nested) "(sunday.nested): {enclosedIn: Container, name: Base}" else ""}
        properties: {kind: string}
      Case:
        type: Base
        discriminatorValue: case
        ${if (nested) "(sunday.nested): {enclosedIn: Container, name: Variant}" else ""}
        ${if (cycle) "(sunday.placement): {swift: {module: SundayGenTest}}" else ""}
        properties: {value: string}
      """.trimIndent()
    val schemas =
      """
      Container: {type: object}
      Base:
        type: object
        discriminator: {propertyName: kind}
        required: [kind]
        properties: {kind: {type: string}}
      Case:
        ${if (cycle) "x-sunday-placement: {swift: {module: SundayGenTest}}" else ""}
        allOf:
          - {${'$'}ref: '#/components/schemas/Base'}
          - type: object
            required: [value]
            properties: {value: {type: string}}
      """.trimIndent()
    return PlacementRegressionFixture(directory, kind).models(raml, schemas, placement("first"))
  }
}
