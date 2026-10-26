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

package io.outfoxx.sunday.generator.python

import com.squareup.kotlinpoet.NameAllocator
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Renders catchable companions for source-schema problem payloads, retaining their inheritance. */
internal class PythonSchemaProblemRenderer(
  private val packageName: String,
  models: List<GeneratedModel>,
) {
  private val problems = PythonProblemModels(models)

  fun render(selected: Set<GeneratedModel>): List<PythonModule> =
    selected
      .filter(problems::isProblem)
      .groupBy { it.pythonProblemModule(packageName) }
      .map { (moduleName, declarations) ->
        val module = PythonModuleBuilder(moduleName.replace('.', '/') + ".py")
        val emitted = mutableSetOf<GeneratedModel>()

        fun emit(model: GeneratedModel) {
          if (!emitted.add(model)) return
          val parent = problems.parent(model)
          if (parent in declarations) emit(requireNotNull(parent))
          val payload =
            PythonSymbol(
              model.pythonModule(packageName),
              model.name.pythonTypeName,
              model.name.pythonTypeName + "Payload",
            )
          val base =
            parent?.let {
              PythonSymbol(it.pythonProblemModule(packageName), it.name.pythonTypeName)
            } ?: PythonSymbol("sunday", "Problem", "_RuntimeProblem")
          val fields =
            problems.properties.fields(model).filter {
              it.wireName !in setOf("type", "title", "status", "detail", "instance")
            }
          val accessorNames = NameAllocator(preallocateKeywords = false)
          listOf("payload", "payload_type", "type", "title", "status", "detail", "instance").forEach {
            accessorNames.newName(it)
          }
          // Allocate inherited declarations first so descendants retain their ancestor's accessor names.
          fields.forEach { accessorNames.newName(it.declaration.name.pythonIdentifierName, it.wireName) }
          val bindings = PythonDeclarationBindings(problems.models, packageName, model.pythonModule(packageName))

          fun bindType(reference: GeneratedTypeRef): GeneratedTypeRef {
            val nested = reference.copy(arguments = reference.arguments.map(::bindType))
            val target = problems.model(reference) ?: return nested
            val symbol = bindings.symbol(bindings.model(target).name) ?: return nested
            val imported = symbol.copy(alias = (symbol.alias ?: symbol.name) + "Payload")
            module.addImport(imported)
            return nested.copy(name = requireNotNull(imported.alias))
          }
          val accessors =
            fields.filterNot { it.inherited }.map { field ->
              val property = field.effective
              val propertyType = property.type
              val type = bindType(propertyType).renderPythonType(nullable = false)
              PythonCodeBlock.of(
                """
                @property
                def %L(self) -> %C:
                    ${"\"\"\"Return the declared problem extension.\"\"\""}
                    return self.payload.%L
                """.trimIndent().prependIndent("    "),
                accessorNames[field.wireName],
                if (!property.required || propertyType.nullable) PythonCodeBlock.of("%C | None", type) else type,
                property.name.pythonIdentifierName,
              )
            }
          val exceptionAccessors =
            (
              if (parent == null) {
                listOf(
                  PythonCodeBlock.of(
                    "    @property\n" +
                      "    def type(self) -> str:\n" +
                      "        \"\"\"Return the URI used by the runtime when the payload omits it.\"\"\"\n" +
                      "        return self.payload.type or \"about:blank\"",
                  ),
                )
              } else {
                emptyList()
              }
            ) + accessors
          val defaults =
            problems.properties.fields(model).map { it.effective }.filter {
              it.defaultValue != null && it.allowedValues?.size != 1
            }
          val constructorDefaults =
            if (defaults.isEmpty()) {
              PythonCodeBlock.of("")
            } else {
              PythonCodeBlock.of(
                "%C\n",
                PythonCodeBlock.join(
                  defaults.map { property ->
                    PythonCodeBlock.of(
                      "        if payload is None and %S not in values and %S not in values:\n            values[%S] = %C",
                      property.name.pythonIdentifierName,
                      property.serializationName ?: property.name,
                      property.serializationName ?: property.name,
                      requireNotNull(problems.properties.scalarDefault(property)) {
                        "Unsupported problem default for '${model.name}.${property.name}'"
                      }.let { value ->
                        when (value) {
                          is String -> PythonCodeBlock.of("%S", value)
                          is Boolean -> PythonCodeBlock.of(if (value) "True" else "False")
                          else -> PythonCodeBlock.of("%L", value)
                        }
                      },
                    )
                  },
                ),
              )
            }
          module.addExport(model.name.pythonTypeName)
          module.addCode(
            PythonCodeBlock.of(
              """
              class %L(%C):
                  ${"\"\"\"Catchable problem with a validated source-schema payload.\"\"\""}

                  payload_type: %T[type[%T]] = %T
                  payload: %T

                  def __init__(self, payload: %T | None = None, **values: %T) -> None:
              %C        super().__init__(payload if payload is not None else self.payload_type(**values))
              %C
              """.trimIndent(),
              model.name.pythonTypeName,
              if (parent in
                declarations
              ) {
                PythonCodeBlock.of("%L", requireNotNull(parent).name.pythonTypeName)
              } else {
                PythonCodeBlock.of("%T", base)
              },
              PythonSymbol("typing", "ClassVar"),
              payload,
              payload,
              payload,
              payload,
              PythonSymbol("typing", "Any"),
              constructorDefaults,
              if (exceptionAccessors.isEmpty()) {
                PythonCodeBlock.of(
                  "",
                )
              } else {
                PythonCodeBlock.of("\n%C", PythonCodeBlock.join(exceptionAccessors, "\n\n"))
              },
            ),
          )
        }
        declarations.forEach(::emit)
        // A shared URI identifies the most specific payload in a hierarchy.
        val registered =
          declarations.filter { candidate ->
            val uri = problems.typeUri(candidate)
            uri != null &&
              uri != "about:blank" &&
              problems.models.none { problems.parent(it) == candidate && problems.typeUri(it) == uri }
          }
        module.addExport("register_problems")
        module.addCode(
          PythonCodeBlock.of(
            """
            def register_problems(registrar: %T) -> None:
                ${"\"\"\"Register source-schema problems for HTTP response decoding.\"\"\""}
            %C
            """.trimIndent(),
            PythonSymbol("sunday", "ProblemRegistrar"),
            if (registered.isEmpty()) {
              PythonCodeBlock.of("    pass")
            } else {
              PythonCodeBlock.join(
                registered.map {
                  PythonCodeBlock.of(
                    "    registrar.register_problem(%S, %L)",
                    problems.typeUri(it),
                    it.name.pythonTypeName,
                  )
                },
              )
            },
          ),
        )
        module.build()
      }
}
