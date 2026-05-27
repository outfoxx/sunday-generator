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

import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedProblem

/** Renders IR problem declarations into catchable Python exceptions backed by Pydantic payloads. */
class PythonProblemRenderer(
  private val packageName: String,
) {

  /** Renders the given problems into the package `problems.py` module. */
  fun renderProblems(problems: List<GeneratedProblem>): PythonModule {
    val module = PythonModuleBuilder("$packageName/problems.py")

    module.addExport("ProblemPayload")
    module.addExport("Problem")
    module.addCode(baseProblemCode())

    problems.forEach { problem ->
      module.addExport(problem.payloadTypeName)
      module.addExport(problem.name.pythonTypeName)
      module.addCode(problem.renderPayloadType())
      module.addCode(problem.renderProblemType())
    }

    return module.build()
  }

  private fun baseProblemCode(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      class ProblemPayload(%T):
          model_config = %T(populate_by_name=True)

          type: str = "about:blank"
          title: str | None = None
          status: int | None = None
          detail: str | None = None
          instance: str | None = None


      class Problem(Exception):
          payload_type: %T[type[ProblemPayload]] = ProblemPayload
          payload: ProblemPayload

          def __init__(self, payload: ProblemPayload) -> None:
              super().__init__(payload.title or payload.type)
              self.payload = payload

          @classmethod
          def model_validate(cls, value: object) -> %T:
              return cls(cls.payload_type.model_validate(value))

          @property
          def type(self) -> str:
              return self.payload.type

          @property
          def title(self) -> str | None:
              return self.payload.title

          @property
          def status(self) -> int | None:
              return self.payload.status

          @property
          def detail(self) -> str | None:
              return self.payload.detail

          @property
          def instance(self) -> str | None:
              return self.payload.instance

          def model_dump(self, **kwargs: %T) -> dict[str, %T]:
              return self.payload.model_dump(**kwargs)
      """.trimIndent(),
      PythonSymbol("pydantic", "BaseModel"),
      PythonSymbol("pydantic", "ConfigDict"),
      PythonSymbol("typing", "ClassVar"),
      PythonSymbol("typing", "Self"),
      PythonSymbol("typing", "Any"),
      PythonSymbol("typing", "Any"),
    )

  private fun GeneratedProblem.renderPayloadType(): PythonCodeBlock {
    val baseProperties =
      listOfNotNull(
        PythonCodeBlock.of("    type: str = %C", typeUri.renderPythonValue()),
        title?.let { PythonCodeBlock.of("    title: str | None = %S", it) },
        status?.let { PythonCodeBlock.of("    status: int | None = %L", it) },
        detail?.let { PythonCodeBlock.of("    detail: str | None = %S", it) },
      )
    val allProperties = baseProperties + fields.map { field -> field.renderPayloadProperty() }
    val body =
      if (allProperties.isEmpty()) {
        PythonCodeBlock.of("    pass")
      } else {
        PythonCodeBlock.join(allProperties)
      }

    return PythonCodeBlock.of(
      """
      class %L(ProblemPayload):
      %C
      """.trimIndent(),
      payloadTypeName,
      body,
    )
  }

  private fun GeneratedProblem.renderProblemType(): PythonCodeBlock {
    val fieldAccessors =
      if (fields.isEmpty()) {
        null
      } else {
        PythonCodeBlock.join(fields.map { field -> field.renderProblemProperty() }, separator = "\n\n")
      }

    return PythonCodeBlock.of(
      """
      class %L(Problem):
          payload_type: %T[type[%L]] = %L
          payload: %L

          def __init__(
              self,
              payload: %L | None = None,
              **values: object,
          ) -> None:
              super().__init__(payload or self.payload_type.model_validate(values))
      %C
      """.trimIndent(),
      name.pythonTypeName,
      PythonSymbol("typing", "ClassVar"),
      payloadTypeName,
      payloadTypeName,
      payloadTypeName,
      payloadTypeName,
      fieldAccessors?.let { PythonCodeBlock.of("\n%C", it) } ?: PythonCodeBlock.of(""),
    )
  }

  private fun GeneratedModelProperty.renderPayloadProperty(): PythonCodeBlock {
    val propertyName = name.pythonIdentifierName
    val propertyType =
      if (required) {
        type.renderPythonType()
      } else {
        PythonCodeBlock.of("%C | None", type.renderPythonType(nullable = false))
      }
    val defaultValue = if (required) "" else " = None"
    val alias = serializationName ?: name

    return if (alias != propertyName) {
      if (required) {
        PythonCodeBlock.of(
          "    %L: %C = %T(alias=%S)",
          propertyName,
          propertyType,
          PythonSymbol("pydantic", "Field"),
          alias,
        )
      } else {
        PythonCodeBlock.of(
          "    %L: %C = %T(default=None, alias=%S)",
          propertyName,
          propertyType,
          PythonSymbol("pydantic", "Field"),
          alias,
        )
      }
    } else {
      PythonCodeBlock.of("    %L: %C%L", propertyName, propertyType, defaultValue)
    }
  }

  private fun GeneratedModelProperty.renderProblemProperty(): PythonCodeBlock =
    PythonCodeBlock.of(
      "    @property\n" +
        "    def %L(self) -> %C:\n" +
        "        return self.payload.%L",
      name.pythonIdentifierName,
      if (required) {
        type.renderPythonType()
      } else {
        PythonCodeBlock.of("%C | None", type.renderPythonType(nullable = false))
      },
      name.pythonIdentifierName,
    )

  private val GeneratedProblem.payloadTypeName: String
    get() = "${name.pythonTypeName}Payload"

  private fun String.renderPythonValue(): PythonCodeBlock =
    if (length <= 80) {
      PythonCodeBlock.of("%S", this)
    } else {
      PythonCodeBlock.of("(\n        %S\n    )", this)
    }
}
