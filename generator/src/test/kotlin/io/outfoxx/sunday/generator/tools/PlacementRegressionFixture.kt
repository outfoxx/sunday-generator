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
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import java.nio.file.Files
import java.nio.file.Path

/** Native source fixtures for placement bugs, round-tripped through serialized IR before generation. */
internal class PlacementRegressionFixture(
  private val directory: Path,
  private val kind: String,
) {
  private fun write(
    name: String,
    format: String,
    raml: String,
    schemas: String,
    placement: String,
  ): Path {
    val path = directory.resolve("$name.${if (format == "raml") "raml" else "yaml"}")
    val text =
      if (format == "raml") {
        javaClass.getResourceAsStream("/sunday.raml")!!.use {
          Files.copy(it, directory.resolve("sunday.raml"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        "#%RAML 1.0\ntitle: Placement\n(sunday.apiId): placement\nuses:\n  sunday: sunday.raml\n(sunday.placement):\n" +
          placement.prependIndent("  ") + "\ntypes:\n" + raml.prependIndent("  ")
      } else {
        val header =
          when (format) {
            "asyncapi2" -> "asyncapi: 2.6.0\ninfo: {title: Placement, version: '1'}\nchannels: {}"
            "asyncapi" -> "asyncapi: 3.0.0\ninfo: {title: Placement, version: '1'}\nchannels: {}"
            else -> "openapi: 3.1.0\ninfo: {title: Placement, version: '1'}\npaths: {}"
          }
        header + "\nx-sunday-apiId: placement\nx-sunday-placement:\n" + placement.prependIndent("  ") +
          "\ncomponents:\n  schemas:\n" + schemas.prependIndent("    ")
      }
    Files.writeString(path, "$text\n")
    return path
  }

  private fun export(paths: List<Path>): GeneratedApi =
    GeneratedApiIrExporter().export(paths.map { it.toUri() }).let {
      GeneratedApiYaml.readString(GeneratedApiYaml.writeString(it))
    }

  fun duplicates(problems: Boolean = false): GeneratedApi {
    val raml =
      if (problems) {
        """
        URI: {type: string, (sunday.typeScriptType): URL}
        BaseProblem:
          type: object
          discriminator: kind
          properties:
            kind: string
            type: URI
            title: string
            status: integer
            detail: {type: string, required: false}
            instance: {type: URI, required: false}
        ChildProblem:
          type: BaseProblem
          discriminatorValue: child
          properties:
            reason: string
        """.trimIndent()
      } else {
        """
        Record:
          type: object
          (sunday.patchable): true
          properties:
            value: {type: string, minLength: 2}
            detail: Detail
        Detail:
          type: object
          properties:
            label: string
        """.trimIndent()
      }
    val schemas =
      if (problems) {
        """
        BaseProblem:
          type: object
          discriminator: {propertyName: kind, mapping: {child: '#/components/schemas/ChildProblem'}}
          required: [kind, type, title, status]
          properties:
            kind: {type: string}
            type: {type: string, format: uri}
            title: {type: string}
            status: {type: integer}
            detail: {type: string}
            instance: {type: string, format: uri}
        ChildProblem:
          allOf:
            - {${'$'}ref: '#/components/schemas/BaseProblem'}
            - type: object
              required: [reason]
              properties:
                reason: {type: string}
        """.trimIndent()
      } else {
        """
        Record:
          type: object
          x-sunday-patchable: true
          required: [value, detail]
          properties:
            value: {type: string, minLength: 2}
            detail: {${'$'}ref: '#/components/schemas/Detail'}
        Detail:
          type: object
          required: [label]
          properties:
            label: {type: string}
        """.trimIndent()
      }
    return export(
      listOf("first", "second").mapIndexed { index, owner ->
        val format =
          if (kind == "composed") {
            if (index == 0) {
              "openapi"
            } else {
              "asyncapi"
            }
          } else {
            kind
          }
        write(
          owner,
          format,
          raml,
          schemas,
          """
          kotlin: {package: example.$owner}
          swift: {module: ${if (index == 0) "SharedModels" else "SundayGenTest"}}
          typescript: {module: $owner}
          python: {package: $owner, module: models}
          """.trimIndent(),
        )
      },
    )
  }

  fun inheritance(): GeneratedApi {
    val raml =
      """
      Base:
        type: object
        properties:
          next: {type: Base, required: false}
      Child:
        type: Base
        (sunday.placement): {swift: {module: SundayGenTest}, python: {module: child}}
        properties:
          child: {type: Child, required: false}
      """.trimIndent()
    val schemas =
      """
      Base:
        type: object
        properties:
          next: {${'$'}ref: '#/components/schemas/Base'}
      Child:
        x-sunday-placement: {swift: {module: SundayGenTest}, python: {module: child}}
        allOf:
          - {${'$'}ref: '#/components/schemas/Base'}
          - type: object
            properties:
              child: {${'$'}ref: '#/components/schemas/Child'}
      """.trimIndent()
    val format = if (kind == "composed") "openapi" else kind
    val paths =
      mutableListOf(
        write("api", format, raml, schemas, "swift: {module: SharedModels}\npython: {package: example, module: base}"),
      )
    if (kind ==
      "composed"
    ) {
      paths.add(
        write("events", "asyncapi", "", "Event: {type: string, enum: [event]}", "swift: {module: SundayGenTest}"),
      )
    }
    return export(paths)
  }

  fun inline(declarationOverride: Boolean = false): GeneratedApi {
    val format = if (kind == "composed") "openapi" else kind
    val source = write("api", format, "Unused: string", "Unused: {type: string}", "kotlin: {package: example.document}")
    val inlineSchema =
      """
      type: object
      required: [child]
      properties:
        child:
          type: object
          required: [value]
          properties:
            value: {type: string}
      """.trimIndent()
    val text = Files.readString(source)
    Files.writeString(
      source,
      when (format) {
        "raml" ->
          text +
            """
            /items:
              get:
                displayName: getItems
                (sunday.placement): {kotlin: {models: {package: example.operation}}}
                responses:
                  200:
                    body:
                      application/json:
                        type: object
                        properties:
                          child:
                            type: object
                            properties:
                              value: string
            """.trimIndent() + "\n"
        "asyncapi2" ->
          text.replace(
            "channels: {}",
            """
            channels:
              /items:
                subscribe:
                  operationId: getItems
                  x-sunday-placement: {kotlin: {models: {package: example.operation}}}
                  message:
                    payload:
            SCHEMA
            """.trimIndent().replace("SCHEMA", inlineSchema.prependIndent("          ")),
          )
        "asyncapi" ->
          text.replace(
            "channels: {}",
            """
            channels:
              items:
                address: /items
                x-sunday-placement: {kotlin: {models: {package: example.operation}}}
                messages:
                  item:
                    payload:
            SCHEMA
            operations:
              getItems:
                action: receive
                x-sunday-placement: {kotlin: {models: {package: example.reference}}}
                channel: {${'$'}ref: '#/channels/items'}
                messages: [{${'$'}ref: '#/channels/items/messages/item'}]
            """.trimIndent().replace("SCHEMA", inlineSchema.prependIndent("          ")),
          )
        else ->
          text.replace(
            "paths: {}",
            """
            paths:
              /items:
                get:
                  operationId: getItems
                  x-sunday-placement: {kotlin: {models: {package: example.operation}}}
                  responses:
                    '200':
                      description: item
                      content:
                        application/json:
                          schema:
            SCHEMA
            """.trimIndent().replace("SCHEMA", inlineSchema.prependIndent("                ")),
          )
      },
    )
    if (declarationOverride) {
      val annotation = if (format == "raml") "(sunday.placement)" else "x-sunday-placement"
      val content =
        Files.readString(source).replace(Regex("(?m)^( +)child:$")) { match ->
          match.value + "\n" + match.groupValues[1] + "  $annotation: {kotlin: {package: example.declaration}}"
        }
      Files.writeString(source, content)
    }
    val paths = mutableListOf(source)
    if (kind ==
      "composed"
    ) {
      paths.add(
        write("events", "asyncapi", "", "Event: {type: string, enum: [event]}", "kotlin: {package: example.document}"),
      )
    }
    return export(paths)
  }

  fun service(
    containerPlacement: String? = null,
    documentPlacement: Boolean = true,
    collectionPayload: Boolean = false,
    zanzibar: Boolean = false,
  ): GeneratedApi {
    val format = if (kind == "composed") "openapi" else kind
    val source = write("api", format, "Unused: string", "Unused: {type: string}", "kotlin: {package: example.document}")
    val text =
      Files
        .readString(source)
        .replace("types:\n  Unused: string", "types: {}")
        .replace("components:\n  schemas:\n    Unused: {type: string}", "components: {}")
    Files.writeString(
      source,
      when (format) {
        "raml" ->
          text +
            """
            baseUri: https://example.com
            /check:
              get:
                displayName: check
                responses:
                  204:
            """.trimIndent() + "\n"
        "asyncapi2" ->
          text.replace(
            "channels: {}",
            """
            servers:
              production: {url: 'https://example.com', protocol: https}
            channels:
              /check:
                subscribe:
                  operationId: check
                  message:
                    payload: {type: string}
            """.trimIndent(),
          )
        "asyncapi" ->
          text.replace(
            "channels: {}",
            """
            servers:
              production: {host: example.com, protocol: https}
            channels:
              check:
                address: /check
                messages:
                  result:
                    payload: {type: string}
            operations:
              check:
                action: receive
                channel: {${'$'}ref: '#/channels/check'}
            """.trimIndent(),
          )
        else ->
          text.replace(
            "paths: {}",
            """
            servers: [{url: 'https://example.com'}]
            paths:
              /check:
                get:
                  operationId: check
                  responses:
                    '204': {description: empty}
            """.trimIndent(),
          )
      },
    )
    var content = Files.readString(source)
    if (collectionPayload) {
      content =
        when (format) {
          "raml" ->
            content.replace(
              "  204:",
              "  200:\n        body:\n          application/json:\n            type: array\n            items: string",
            )
          "openapi" ->
            content.replace(
              "'204': {description: empty}",
              "'200': {description: values, content: {application/json: {schema: {type: array, items: {type: string}}}}}",
            )
          else -> content.replace("payload: {type: string}", "payload: {type: array, items: {type: string}}")
        }
    }
    if (!documentPlacement) content = content.replace("kotlin: {package: example.document}", "{}")
    if (containerPlacement != null) {
      val annotation = if (format == "raml") "(sunday.placement)" else "x-sunday-placement"
      val key =
        if (format == "raml") {
          "/check:"
        } else if (format == "asyncapi") {
          "  check:"
        } else {
          "  /check:"
        }
      content =
        content.replaceFirst(
          "$key\n",
          "$key\n${if (format == "raml") "  " else "    "}$annotation: $containerPlacement\n",
        )
    }
    if (zanzibar) {
      content +=
        "\n${if (format == "raml") "(sunday.zanzibar)" else "x-sunday-zanzibar"}: {user-source: {jwt: {claims: [sub]}}}\n"
    }
    Files.writeString(source, content)
    val paths = mutableListOf(source)
    if (kind ==
      "composed"
    ) {
      paths.add(write("events", "asyncapi", "", "Unused: {type: string}", "kotlin: {package: example.document}"))
    }
    return export(paths)
  }

  /** Writes equivalent native model declarations and preserves them through composition and IR. */
  fun models(
    raml: String,
    schemas: String,
    placement: String,
  ): GeneratedApi {
    val format = if (kind == "composed") "openapi" else kind
    val paths = mutableListOf(write("api", format, raml, schemas, placement))
    if (kind == "composed") paths.add(write("events", "asyncapi", "", "Unused: {type: string}", placement))
    return export(paths)
  }

  /** A local declaration and a referenced library declaration share a name within one parsed fragment. */
  fun localAndImported(): GeneratedApi {
    val format = if (kind == "composed") "openapi" else kind
    val raml = "Record: {type: object, properties: {value: string}}"
    val schema = "Record: {type: object, required: [value], properties: {value: {type: string}}}"
    val library =
      write(
        "library",
        format,
        raml,
        schema,
        "kotlin: {package: example.first}\nswift: {module: SharedModels}\ntypescript: {module: first}\npython: {package: first, module: models}",
      )
    if (format == "raml") {
      Files.writeString(
        library,
        Files
          .readString(
            library,
          ).replace("#%RAML 1.0\ntitle: Placement", "#%RAML 1.0 Library")
          .replace("(sunday.apiId): placement\n", ""),
      )
    }
    val source =
      write(
        "api",
        format,
        raml + "\nHolder: {type: object, properties: {local: Record, imported: library.Record}}",
        schema + "\n" +
          """
          Holder:
            type: object
            required: [local, imported]
            properties:
              local: {${'$'}ref: '#/components/schemas/Record'}
              imported: {${'$'}ref: './library.yaml#/components/schemas/Record'}
          """.trimIndent(),
        "kotlin: {package: example.second}\nswift: {module: SundayGenTest}\ntypescript: {module: second}\npython: {package: second, module: models}",
      )
    if (format == "raml") {
      Files.writeString(source, Files.readString(source).replace("uses:\n", "uses:\n  library: library.raml\n"))
    }
    val paths = mutableListOf(source)
    if (kind ==
      "composed"
    ) {
      paths.add(write("events", "asyncapi", "", "Unused: {type: string}", "kotlin: {package: example.second}"))
    }
    return export(paths)
  }

  /** Creates disjoint container contributions, optionally assigning them to independent services. */
  fun serviceContainers(
    split: Boolean,
    collectionPayload: Boolean = false,
    documentPlacement: Boolean = true,
  ): GeneratedApi {
    service("{kotlin: {services: {package: example.first}}}", documentPlacement, collectionPayload)
    val format = if (kind == "composed") "openapi" else kind
    val source = directory.resolve(if (format == "raml") "api.raml" else "api.yaml")
    var content = Files.readString(source)
    val extension = if (format == "raml") "(sunday.group)" else "x-sunday-service"
    val key =
      if (format == "raml") {
        "/check:"
      } else if (format == "asyncapi") {
        "  check:"
      } else {
        "  /check:"
      }
    val indent = if (format == "raml") "  " else "    "
    content = content.replaceFirst("$key\n", "$key\n$indent$extension: ${if (split) "First" else "Combined"}\n")
    val block = Regex("(?m)^" + Regex.escape(key) + "\\n(?: +.*\\n|\\n)*").find(content)!!.value
    val other = block.replace("check", "other").replace("example.first", "example.second").replace("First", "Second")
    content = content.replaceFirst(block, block + other)
    if (format == "asyncapi") {
      val operation = Regex("(?m)^  check:\\n(?: +.*\\n|\\n)*").find(content, content.indexOf("operations:"))!!.value
      content = content.replaceFirst(operation, operation + operation.replace("check", "other"))
    }
    Files.writeString(source, content)
    val paths = mutableListOf(source)
    if (kind == "composed") paths.add(directory.resolve("events.yaml"))
    return export(paths)
  }

  /** Distinct declarations must inherit only their own document, regardless of input ordering. */
  fun distinctDefaults(reverse: Boolean = false): GeneratedApi {
    val paths =
      listOf("First", "Second").mapIndexed { index, name ->
        write(
          name.lowercase(),
          if (kind == "composed") {
            if (index == 0) "raml" else "asyncapi"
          } else {
            kind
          },
          "$name: {type: object, properties: {value: string}}",
          "$name: {type: object, required: [value], properties: {value: {type: string}}}",
          if (index ==
            0
          ) {
            "kotlin: {package: example.first}\nswift: {module: SharedModels}\ntypescript: {module: first}\npython: {package: first}"
          } else {
            "{}"
          },
        )
      }
    return export(if (reverse) paths.reversed() else paths).copy(
      placementConfiguration =
        io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration(
          defaults =
            io.outfoxx.sunday.generator.ir.GeneratedPlacement(
              kotlin =
                io.outfoxx.sunday.generator.ir.GeneratedPlacement
                  .Scope(packageName = "example.defaults"),
              swift =
                io.outfoxx.sunday.generator.ir.GeneratedPlacement
                  .Scope(module = "SundayGenTest"),
              typescript =
                io.outfoxx.sunday.generator.ir.GeneratedPlacement
                  .Scope(module = "defaults"),
              python =
                io.outfoxx.sunday.generator.ir.GeneratedPlacement
                  .Scope(packageName = "defaults"),
            ),
        ),
    )
  }

  /** Duplicate aliases have different constraints and independently bound service payloads. */
  fun aliasServices(): GeneratedApi {
    val paths =
      listOf("First", "Second").mapIndexed { index, name ->
        val format =
          if (kind == "composed") {
            if (index == 0) "openapi" else "raml"
          } else {
            kind
          }
        val path =
          write(
            name.lowercase(),
            format,
            "Identifier: {type: string, minLength: ${if (index == 0) 2 else 10}}",
            "Identifier: {type: string, minLength: ${if (index == 0) 2 else 10}}",
            "kotlin: {package: example.${name.lowercase()}}",
          )
        val ref = "{${'$'}ref: '#/components/schemas/Identifier'}"
        val content = Files.readString(path)
        Files.writeString(
          path,
          when (format) {
            "raml" ->
              content +
                "\n/$name:\n  (sunday.group): $name\n  get:\n    displayName: get$name\n    responses:\n      200:\n        body:\n          application/json: Identifier\n"
            "asyncapi2" ->
              content.replace(
                "channels: {}",
                "channels:\n  /$name:\n    x-sunday-service: $name\n    subscribe:\n      operationId: get$name\n      message:\n        payload: $ref",
              )
            "asyncapi" ->
              content.replace(
                "channels: {}",
                "channels:\n  $name:\n    address: /$name\n    x-sunday-service: $name\n    messages:\n      result:\n        payload: $ref\noperations:\n  get$name:\n    action: receive\n    channel: {${'$'}ref: '#/channels/$name'}",
              )
            else ->
              content.replace(
                "paths: {}",
                "paths:\n  /$name:\n    x-sunday-service: $name\n    get:\n      operationId: get$name\n      responses:\n        '200': {description: value, content: {application/json: {schema: $ref}}}",
              )
          },
        )
        path
      }
    return export(paths)
  }

  /** A nested RAML resource containing every operation supplies the service defaults. */
  fun nestedService(): GeneratedApi {
    service("{kotlin: {services: {package: example.nested}}}")
    val path = directory.resolve("api.raml")
    val content = Files.readString(path)
    val offset = content.indexOf("/check:")
    Files.writeString(
      path,
      content.substring(0, offset) + "/parent:\n" + content.substring(offset).prependIndent("  ") + "\n",
    )
    return export(listOf(path))
  }

  /** Typed RAML server variables bind to each declaring document's enum. */
  fun typedServers(): GeneratedApi {
    val paths =
      listOf("First", "Second").mapIndexed { index, name ->
        val path =
          write(
            name.lowercase(),
            "raml",
            "Environment: {type: string, enum: [${if (index == 0) "a, b" else "c, d"}]}",
            "",
            "kotlin: {package: example.${name.lowercase()}}\npython: {package: ${name.lowercase()}}",
          )
        Files.writeString(
          path,
          Files.readString(path) +
            "\nbaseUri: https://{environment}.example.com\nbaseUriParameters:\n  environment: Environment\n/$name:\n  (sunday.group): $name\n  get:\n    displayName: get$name\n    responses:\n      204:\n",
        )
        path
      }
    return export(paths)
  }

  /** Optional shorthand keys must remain addressable by their actual source pointers. */
  fun optionalProperty(): GeneratedApi {
    val path =
      write(
        "optional",
        if (kind == "composed") "raml" else kind,
        """
        Holder:
          type: object
          properties:
            detail?: {type: object, properties: {value: string}}
            explicit: {type: object, required: false, properties: {value: string}}
        """.trimIndent(),
        """
        Holder:
          type: object
          properties:
            detail: {type: object, required: [value], properties: {value: {type: string}}}
            explicit: {type: object, required: [value], properties: {value: {type: string}}}
        """.trimIndent(),
        "kotlin: {package: example.models}",
      )
    val paths = mutableListOf(path)
    if (kind ==
      "composed"
    ) {
      paths.add(write("events", "asyncapi", "", "Unused: {type: string}", "kotlin: {package: example.events}"))
    }
    val api = export(paths)
    val holder = api.models.single { it.name == "Holder" }
    return api.copy(
      placementConfiguration =
        GeneratedPlacementConfiguration(
          relocations =
            listOf("detail", "explicit").map { name ->
              GeneratedPlacementConfiguration.Relocation(
                holder.declarationUri + "/properties/" + name +
                  if (kind in setOf("raml", "composed") && name == "detail") "%3F" else "",
                placement = GeneratedPlacement(kotlin = GeneratedPlacement.Scope(packageName = "example.$name")),
              )
            },
        ),
    )
  }

  /** Nest across documents so unrelated filtered relocations cannot appear to detach the child. */
  fun filteredNested(overridden: Boolean = false): GeneratedApi {
    val api = distinctDefaults()
    val parent = api.models.single { it.name == "First" }
    val child = api.models.single { it.name == "Second" }
    val placement =
      GeneratedPlacement(
        kotlin = GeneratedPlacement.Scope(packageName = "example.problems"),
        swift = GeneratedPlacement.Scope(module = "Problems"),
        typescript = GeneratedPlacement.Scope(module = "problems"),
      )
    val rules =
      if (overridden) {
        listOf(
          GeneratedPlacementConfiguration.Relocation(child.source.location, placement = placement),
          GeneratedPlacementConfiguration.Relocation(
            child.declarationUri!!,
            placement =
              GeneratedPlacement(
                kotlin = GeneratedPlacement.Scope(packageName = "example.first"),
                swift = GeneratedPlacement.Scope(module = "SharedModels"),
                typescript = GeneratedPlacement.Scope(module = "first"),
              ),
          ),
        )
      } else {
        listOf(
          GeneratedPlacementConfiguration.Relocation(child.source.location, kind = "problem", placement = placement),
        )
      }
    return GeneratedApiYaml.readString(
      GeneratedApiYaml.writeString(
        api.copy(
          models =
            api.models.map { model ->
              if (model == child) {
                model.copy(
                  nested =
                    GeneratedNestedType(
                      enclosedIn = GeneratedTypeRef.named(parent.name).copy(source = parent.source),
                      name = "Child",
                    ),
                )
              } else {
                model
              }
            },
          problems =
            if (overridden) {
              emptyList()
            } else {
              listOf(
                GeneratedProblem(
                  name = "Failure",
                  source = child.source,
                  typeUri = "https://example.com/failure",
                  status = 400,
                  title = "Failure",
                  detail = "A placement test failure",
                  declarationUri = child.source.location + "#/problems/Failure",
                ),
              )
            },
          placementConfiguration = api.placementConfiguration.copy(relocations = rules),
        ),
      ),
    )
  }

  /** Three independently owned source declarations nested through the shared IR contract. */
  fun nestedSelection(): GeneratedApi {
    val paths =
      listOf("Root", "Middle", "Leaf").mapIndexed { index, name ->
        val format = if (kind == "composed") listOf("raml", "openapi", "asyncapi")[index] else kind
        val names = if (name == "Leaf") listOf(name, "Survivor") else listOf(name)
        write(
          name.lowercase(),
          format,
          names.joinToString("\n") { "$it: {type: object, properties: {value: {type: string, minLength: 2}}}" },
          names.joinToString("\n") {
            "$it: {type: object, required: [value], properties: {value: {type: string, minLength: 2}}}"
          },
          "kotlin: {package: example.models}\nswift: {module: SundayGenTest}\ntypescript: {module: models}\npython: {package: selection}",
        )
      }
    val api = export(paths)
    val models =
      api.models.map { model ->
        val parentName =
          when (model.name) {
            "Middle" -> "Root"
            "Leaf" -> "Middle"
            else -> null
          }
        if (parentName == null) {
          model
        } else {
          model.copy(
            nested =
              GeneratedNestedType(
                enclosedIn =
                  GeneratedTypeRef
                    .named(
                      parentName,
                    ).copy(source = api.models.single { it.name == parentName }.source),
                name = model.name,
              ),
          )
        }
      }
    return GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api.copy(models = models)))
  }
}
