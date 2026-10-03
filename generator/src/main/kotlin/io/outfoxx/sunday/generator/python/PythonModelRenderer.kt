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

import com.fasterxml.jackson.databind.ObjectMapper
import io.outfoxx.sunday.generator.Tolerance
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedPatchModels
import io.outfoxx.sunday.generator.ir.GeneratedTolerance
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedDiscriminatorFallback
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNominalTypes
import io.outfoxx.sunday.generator.ir.emit.discriminatorFallbackOrNull
import io.outfoxx.sunday.generator.ir.emit.externalDiscriminatorFallbackOrNull
import java.math.BigDecimal
import java.math.BigInteger

private val pythonEnumMemberIdentifierRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")
private const val FALLBACK_TAG = "__unknown__"

/** Renders IR models into a Python Pydantic models module. */
class PythonModelRenderer(
  private val packageName: String,
  private val preserveUnknownFields: Boolean = true,
  private val defaultTolerance: Tolerance = Tolerance.Response,
) {

  private var modelIndex: Map<String, GeneratedModel> = mapOf()
  private val nominalTypes = GeneratedNominalTypes { modelIndex[it.name] }
  private var modelProperties = GeneratedModelProperties { modelIndex[it.name] }
  private var discriminatorFallbacks: Map<String, GeneratedDiscriminatorFallback> = mapOf()
  private val pythonEnumEntriesByModel = mutableMapOf<GeneratedModel, List<PythonEnumEntry>>()
  private val enumStringAdapters = linkedMapOf<Map<String, String>, String>()

  /** Renders the given models into the package `models.py` module. */
  fun renderModels(models: List<GeneratedModel>): PythonModule =
    renderNormalizedModels(GeneratedPatchModels.normalizeFields(models))

  private fun renderNormalizedModels(models: List<GeneratedModel>): PythonModule {
    val module = PythonModuleBuilder("$packageName/models.py")
    modelIndex = models.associateBy { model -> model.name }
    modelProperties = GeneratedModelProperties { modelIndex[it.name] }
    discriminatorFallbacks =
      buildList {
        models.mapNotNullTo(this) { model -> model.discriminatorFallbackOrNull(modelIndex) }
        models.forEach { owner ->
          owner.properties.mapNotNullTo(this) { property ->
            property.externalDiscriminatorFallbackOrNull(owner, modelIndex)
          }
        }
      }.associateBy { fallback -> fallback.hierarchy.name }
    pythonEnumEntriesByModel.clear()
    enumStringAdapters.clear()
    models.flatMap { it.effectiveModelProperties() }.forEach { property ->
      val constraints = property.enumStringConstraints()
      if (constraints.isNotEmpty()) {
        enumStringAdapters.getOrPut(constraints) { "_enum_string_constraints_${enumStringAdapters.size + 1}" }
      }
    }
    enumStringAdapters.forEach { (constraints, name) ->
      module.addCode(
        PythonCodeBlock.of(
          "%L: %T[str] = %T(\n    %T[\n        str,\n        %T(\n%C\n        ),\n    ],\n)",
          name,
          PythonSymbol("pydantic", "TypeAdapter"),
          PythonSymbol("pydantic", "TypeAdapter"),
          PythonSymbol("typing", "Annotated"),
          PythonSymbol("pydantic", "StringConstraints"),
          PythonCodeBlock.join(
            constraints.renderFieldConstraints("enum string", GeneratedTypeRef.scalar("string")).map {
              PythonCodeBlock.of("            %C,", it)
            },
            "\n",
          ),
        ),
      )
    }

    if (models.any { model -> model.properties.any { it.allowedValues != null } }) {
      module.addCode(
        PythonCodeBlock.of(
          """
          def _is_allowed_wire_value(value: object, allowed: tuple[object, ...], format: str = "") -> bool:
              if isinstance(value, %T):
                  value = value.value
              if (
                  (format == "uuid" and isinstance(value, %T))
                  or (
                      format in ("date", "date-only", "full-date") and isinstance(value, %T) and not isinstance(value, %T)
                  )
                  or (format in ("date-time", "datetime", "date-time-only", "datetime-only") and isinstance(value, %T))
                  or (format in ("time", "time-only", "partial-time") and isinstance(value, %T))
                  or (format in ("uri", "url", "iri") and isinstance(value, (%T, %T)))
              ):
                  value = %T(value)
              elif format in ("byte", "binary") and isinstance(value, bytes):
                  try:
                      value = value.decode("utf-8")
                  except UnicodeDecodeError:
                      return False
              return any(value == candidate and isinstance(value, bool) == isinstance(candidate, bool) for candidate in allowed)
          """.trimIndent(),
          PythonSymbol("enum", "Enum"),
          PythonSymbol("uuid", "UUID"),
          PythonSymbol("datetime", "date"),
          PythonSymbol("datetime", "datetime"),
          PythonSymbol("datetime", "datetime"),
          PythonSymbol("datetime", "time"),
          PythonSymbol("pydantic", "AnyUrl"),
          PythonSymbol("pydantic_core", "Url"),
          PythonSymbol("pydantic_core", "to_jsonable_python"),
        ),
      )
    }

    if (models.any { model -> model.effectiveModelProperties().any { it.requiresUniqueListValidation() } }) {
      module.addCode(
        PythonCodeBlock.of(
          """
          def _wire_values_equal(left: object, right: object) -> bool:
              if isinstance(left, %T):
                  left = left.model_dump(mode="json", by_alias=True)
              if isinstance(right, %T):
                  right = right.model_dump(mode="json", by_alias=True)
              if isinstance(left, %T):
                  left = left.value
              if isinstance(right, %T):
                  right = right.value
              if isinstance(left, bool) != isinstance(right, bool):
                  return False
              if isinstance(left, (list, tuple)) and isinstance(right, (list, tuple)):
                  if len(left) != len(right):
                      return False
                  return all(_wire_values_equal(a, b) for a, b in zip(left, right, strict=True))
              if isinstance(left, dict) and isinstance(right, dict):
                  return left.keys() == right.keys() and all(_wire_values_equal(left[key], right[key]) for key in left)
              return bool(left == right)
          """.trimIndent(),
          PythonSymbol("pydantic", "BaseModel"),
          PythonSymbol("pydantic", "BaseModel"),
          PythonSymbol("enum", "Enum"),
          PythonSymbol("enum", "Enum"),
        ),
      )
    }

    models
      .filter { model -> model.isSupportedModel() }
      .orderedForInheritance()
      .forEach { model ->
        discriminatorFallbacks[model.name]?.let { fallback ->
          module.addExport(fallback.modelName.pythonTypeName)
          module.addCode(fallback.renderFallbackModel())
        }
        module.addExport(model.name.pythonTypeName)
        module.addCode(model.renderModel())
      }

    val rebuilds =
      models
        .filter {
          it.hasUnionCommonRules()
        }.map { PythonCodeBlock.of("_%LFields.model_rebuild()", it.name.pythonTypeName) } +
        models
          .filter { model -> model.isObjectClass() }
          .map { model -> PythonCodeBlock.of("%L.model_rebuild()", model.name.pythonTypeName) } +
        discriminatorFallbacks.values.map { fallback ->
          PythonCodeBlock.of("%L.model_rebuild()", fallback.fieldsClassName())
        }
    if (rebuilds.isNotEmpty()) {
      module.addCode(PythonCodeBlock.join(rebuilds, separator = "\n"))
    }

    return module.build()
  }

  private fun List<GeneratedModel>.orderedForInheritance(): List<GeneratedModel> {
    val remaining = toMutableList()
    val emitted = mutableSetOf<String>()
    val ordered = mutableListOf<GeneratedModel>()
    while (remaining.isNotEmpty()) {
      val ready =
        remaining.filter { model ->
          model.inherits.none { inherited ->
            inherited.kind == GeneratedTypeRef.Kind.NAMED &&
              modelIndex[inherited.name]?.isObjectClass() == true &&
              inherited.name !in emitted
          }
        }
      if (ready.isEmpty()) {
        genError(
          "Python object inheritance contains a cycle involving " +
            remaining.joinToString { model -> model.name },
        )
      }
      ordered += ready
      emitted += ready.map { model -> model.name }
      remaining.removeAll(ready.toSet())
    }
    return ordered
  }

  private fun GeneratedModel.isSupportedModel(): Boolean =
    kind == GeneratedModel.Kind.ENUM ||
      kind == GeneratedModel.Kind.OBJECT ||
      kind == GeneratedModel.Kind.SCALAR_ALIAS ||
      kind == GeneratedModel.Kind.UNION ||
      kind == GeneratedModel.Kind.ARRAY ||
      kind == GeneratedModel.Kind.MAP

  private fun GeneratedModel.renderModel(): PythonCodeBlock =
    if (nominal) {
      renderNominalScalar()
    } else if (nominalTypes.branches(this).isNotEmpty()) {
      renderNominalUnion()
    } else {
      when (kind) {
        GeneratedModel.Kind.OBJECT -> renderObjectModel()
        GeneratedModel.Kind.ENUM -> renderEnumModel()
        GeneratedModel.Kind.SCALAR_ALIAS -> renderScalarAliasModel()
        GeneratedModel.Kind.UNION -> renderUnionModel()
        GeneratedModel.Kind.ARRAY -> renderArrayAliasModel()
        GeneratedModel.Kind.MAP -> renderMapAliasModel()
      }
    }

  private fun GeneratedModel.isObjectClass(): Boolean =
    kind == GeneratedModel.Kind.OBJECT &&
      !isDiscriminatedAlias()

  private fun GeneratedModel.isDiscriminatedAlias(): Boolean =
    (discriminatorMappings.isNotEmpty() && (properties.isEmpty() || discriminator != null)) ||
      (discriminator != null && discriminatorFallbacks.containsKey(name))

  private fun GeneratedModel.renderObjectModel(className: String = name.pythonTypeName): PythonCodeBlock {
    if (isDiscriminatedAlias()) {
      return renderUnionAliasModel()
    }

    val effectiveProperties = renderedModelProperties()
    val renderedProperties =
      syntheticDiscriminatorProperty()
        ?.let { discriminatorProperty ->
          val discriminatorWireName = discriminatorProperty.serializationName ?: discriminatorProperty.name
          listOf(discriminatorProperty) +
            effectiveProperties.filterNot { property ->
              (property.serializationName ?: property.name) == discriminatorWireName
            }
        } ?: effectiveProperties
    val bodyBlocks =
      listOf(
        PythonCodeBlock.of("    %L", "\"\"\"Generated ${name.pythonTypeName} model.\"\"\""),
      ) +
        listOfNotNull(
          renderObjectConfiguration(),
          renderedProperties.takeIf { it.isNotEmpty() }?.let { modelProperties ->
            PythonCodeBlock.join(modelProperties.map { property -> property.renderProperty(this) })
          },
          renderNonNullableOptionalValidator(),
          renderExternalDiscriminatorValidator(),
          renderWireValueValidator(),
          renderAllowedValuesValidator(),
          renderEnumStringValidator(),
          renderUniqueListValidator(),
        )
    val body =
      bodyBlocks.takeIf { it.isNotEmpty() }?.let { PythonCodeBlock.join(it, separator = "\n\n") }
        ?: PythonCodeBlock.of("    pass")
    val bases =
      inherits
        .filter { inherited ->
          inherited.kind == GeneratedTypeRef.Kind.NAMED && modelIndex[inherited.name]?.isObjectClass() == true
        }.map { inherited -> inherited.renderPythonType(nullable = false) }
        .ifEmpty {
          listOf(PythonCodeBlock.of("%T", PythonSymbol("sunday", if (patchable) "SundayPatchModel" else "SundayModel")))
        }

    return PythonCodeBlock.of(
      """
      class %L(%C):
      %C
      """.trimIndent(),
      className,
      PythonCodeBlock.join(bases, separator = ", "),
      body,
    )
  }

  private fun GeneratedModel.renderObjectConfiguration(): PythonCodeBlock? {
    val extra =
      when {
        modelProperties.isClosed(this) && modelProperties.patternProperties(this).isEmpty() -> "forbid"
        preserveUnknownFields -> "allow"
        else -> "ignore"
      }
    val schemaExtra = mutableListOf<PythonCodeBlock>()
    if (examples.isNotEmpty()) {
      schemaExtra +=
        PythonCodeBlock.of(
          "%S: [%C]",
          "examples",
          PythonCodeBlock.join(examples.mapNotNull { example -> example.value?.renderPythonValue() }, separator = ", "),
        )
    }
    if (deprecated) {
      schemaExtra += PythonCodeBlock.of("%S: True", "deprecated")
    }
    val arguments = mutableListOf<PythonCodeBlock>()
    arguments += PythonCodeBlock.of("extra=%S", extra)
    if (schemaExtra.isNotEmpty()) {
      arguments +=
        PythonCodeBlock.of(
          "json_schema_extra={%C}",
          PythonCodeBlock.join(schemaExtra, separator = ", "),
        )
    }
    return PythonCodeBlock.of(
      "    model_config = %T(%C)",
      PythonSymbol("pydantic", "ConfigDict"),
      PythonCodeBlock.join(arguments, separator = ", "),
    )
  }

  private fun GeneratedModel.renderNominalScalar(): PythonCodeBlock {
    val scalar = nominalTypes.scalar(this)
    val base = scalar.type.renderPythonType(nullable = false)
    val validated = renderValidatedType(base, scalar.property.validation, "model '$name'", scalar.type)
    val boolean = scalar.type.name == "boolean"
    val checks =
      scalar.patterns.filterNot { it == scalar.property.validation["pattern"] }.map {
        PythonCodeBlock.of(
          "        if %T(%S, value) is None:\n            raise ValueError(%S)",
          PythonSymbol("re", "search"),
          it,
          "Invalid value for '$name'",
        )
      }
    val constructor =
      if (boolean) {
        PythonCodeBlock.of(
          """
          value: bool

          def __post_init__(self) -> None:
              self._adapter.validate_python(self.value, strict=True)

          def __bool__(self) -> bool:
              return self.value

          def __str__(self) -> str:
              return "true" if self.value else "false"
          """.trimIndent().lines().joinToString("\n") {
            if (it.isEmpty()) it else "    $it"
          },
        )
      } else {
        PythonCodeBlock.of(
          """
              def __new__(cls, value: %C) -> %T:
                  value = cls._adapter.validate_python(value, strict=True)
          %C
                  return %C.__new__(cls, value)
          """.trimIndent(),
          base,
          PythonSymbol("typing", "Self"),
          PythonCodeBlock.join(checks, "\n"),
          base,
        )
      }
    return PythonCodeBlock.of(
      """
      %Cclass %L%C:
          "Validated scalar wire value."

          _adapter: %T[%T[%C]] = %T(%C)

      %C

          @classmethod
          def __get_pydantic_core_schema__(cls, source_type: object, handler: %T) -> %T:
              return %T(
                  cls,
                  %C,
                  serialization=%T(
                      lambda value: %C, return_schema=handler.generate_schema(%C)
                  ),
              )
      """.trimIndent(),
      if (boolean) {
        PythonCodeBlock.of(
          "@%T(frozen=True)\n",
          PythonSymbol("dataclasses", "dataclass"),
        )
      } else {
        PythonCodeBlock.of("")
      },
      name.pythonTypeName,
      if (boolean) PythonCodeBlock.of("") else PythonCodeBlock.of("(%C)", base),
      PythonSymbol("typing", "ClassVar"),
      PythonSymbol("pydantic", "TypeAdapter"),
      base,
      PythonSymbol("pydantic", "TypeAdapter"),
      validated,
      constructor,
      PythonSymbol("pydantic", "GetCoreSchemaHandler"),
      PythonSymbol("pydantic_core.core_schema", "CoreSchema"),
      PythonSymbol("pydantic_core.core_schema", "no_info_after_validator_function"),
      if (boolean) {
        PythonCodeBlock.of(
          "%T(\n                lambda value: value.value if isinstance(value, cls) else value,\n" +
            "                cls._adapter.core_schema,\n            )",
          PythonSymbol("pydantic_core.core_schema", "no_info_before_validator_function"),
        )
      } else {
        PythonCodeBlock.of("cls._adapter.core_schema")
      },
      PythonSymbol("pydantic_core.core_schema", "plain_serializer_function_ser_schema"),
      if (boolean) PythonCodeBlock.of("value.value") else PythonCodeBlock.of("%C(value)", base),
      base,
    )
  }

  private fun GeneratedModel.renderNominalUnion(): PythonCodeBlock {
    val branches = nominalTypes.branches(this)
    val raw = nominalTypes.unionType(this)
    val function = "_validate_${name.pythonIdentifierName}"
    val union = aliases.renderUnionType()
    val branchesCode = PythonCodeBlock.join(branches.map { PythonCodeBlock.of("%L", it.name.pythonTypeName) }, ", ")
    return PythonCodeBlock.of(
      """
      def %L(value: object) -> %C:
          matches: list[%C] = []
      %C
          if not isinstance(value, %C):
              raise ValueError(%S)
          for branch in (%C):
              with %T(TypeError, ValueError):
                  matches.append(branch(value))
          if not matches:
              raise ValueError(%S)
      %C
          return matches[0]


      type %L = %T[
          %C,
          %T(%L, json_schema_input_type=%C),
          %T(lambda value: %C, return_type=%C),
      ]
      """.trimIndent(),
      function,
      union,
      union,
      if (raw.name ==
        "boolean"
      ) {
        PythonCodeBlock.of("    if isinstance(value, (%C,)):\n        value = value.value", branchesCode)
      } else {
        PythonCodeBlock.of("")
      },
      if (raw.name == "number") PythonCodeBlock.of("(int, float)") else raw.renderPythonType(),
      "No branch matched $name",
      if (branches.size == 1) PythonCodeBlock.of("%C,", branchesCode) else branchesCode,
      PythonSymbol("contextlib", "suppress"),
      "No branch matched $name",
      if (unionMode ==
        GeneratedModel.UnionMode.ONE_OF
      ) {
        PythonCodeBlock.of(
          "    if len(matches) != 1:\n        raise ValueError(%S)",
          "Ambiguous value for $name: multiple branches matched",
        )
      } else {
        PythonCodeBlock.of("")
      },
      name.pythonTypeName,
      PythonSymbol("typing", "Annotated"),
      union,
      PythonSymbol("pydantic", "PlainValidator"),
      function,
      union,
      PythonSymbol("pydantic", "PlainSerializer"),
      if (raw.name ==
        "boolean"
      ) {
        PythonCodeBlock.of("value.value")
      } else {
        PythonCodeBlock.of("%C(value)", raw.renderPythonType())
      },
      raw.renderPythonType(),
    )
  }

  private fun GeneratedModel.renderScalarAliasModel(): PythonCodeBlock =
    PythonCodeBlock.of(
      "type %L = %C",
      name.pythonTypeName,
      renderValidatedType(
        aliases.firstOrNull()?.renderPythonType(nullable = false)
          ?: GeneratedTypeRef.scalar("any").renderPythonType(),
        validation,
        "model '$name'",
        aliases.firstOrNull() ?: GeneratedTypeRef.scalar("any"),
      ),
    )

  private fun GeneratedModel.renderArrayAliasModel(): PythonCodeBlock {
    val elementType = aliases.firstOrNull()?.renderPythonType() ?: PythonCodeBlock.of("object")
    val collectionType =
      PythonCodeBlock.of(
        "%L[%C]",
        if (collection?.name == "SET") "set" else "list",
        elementType,
      )
    return PythonCodeBlock.of(
      "type %L = %C",
      name.pythonTypeName,
      renderValidatedType(
        collectionType,
        validation,
        "model '$name'",
        GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", collection = collection),
      ),
    )
  }

  private fun GeneratedModel.renderMapAliasModel(): PythonCodeBlock {
    val mapType =
      PythonCodeBlock.of(
        "dict[str, %C]",
        aliases.firstOrNull()?.renderPythonType() ?: PythonCodeBlock.of("object"),
      )
    return PythonCodeBlock.of(
      "type %L = %C",
      name.pythonTypeName,
      renderValidatedType(mapType, validation, "model '$name'", GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map")),
    )
  }

  private fun renderValidatedType(
    baseType: PythonCodeBlock,
    validation: Map<String, String>,
    context: String,
    type: GeneratedTypeRef,
  ): PythonCodeBlock {
    val constraints = validation.renderFieldConstraints(context, type)
    return if (constraints.isEmpty()) {
      baseType
    } else {
      PythonCodeBlock.of(
        "%T[%C, %T(%C)]",
        PythonSymbol("typing", "Annotated"),
        baseType,
        PythonSymbol("pydantic", "Field"),
        PythonCodeBlock.join(constraints, separator = ", "),
      )
    }
  }

  private fun GeneratedModel.renderNonNullableOptionalValidator(): PythonCodeBlock? {
    val fields =
      effectiveModelProperties().filter {
        !it.required &&
          (it.patchDeletionAllowed == false || !it.type.acceptsNull())
      }
    if (fields.isEmpty()) return null
    // Field validation lets Pydantic select the effective input spelling before checking nullability.
    return PythonCodeBlock.of(
      "%C\n" +
        "    @classmethod\n" +
        "    def _validate_non_nullable_optional(cls, value: object, info: %T) -> object:\n" +
        "        if value is not None:\n" +
        "            return value\n" +
        "%C\n" +
        "        return value",
      fields.renderFieldValidator("after"),
      PythonSymbol("pydantic", "ValidationInfo"),
      PythonCodeBlock.join(
        fields.map { property ->
          val wireName = property.serializationName ?: property.name
          PythonCodeBlock.of(
            "        if info.field_name == %S:\n" +
              "            raise ValueError(%S)",
            property.name.pythonIdentifierName,
            "Property '$wireName' is not nullable",
          )
        },
        separator = "\n",
      ),
    )
  }

  // Strict primitive leaves reject JSON coercions while enums and formatted scalars retain their wire decoders.
  private fun GeneratedTypeRef.renderPatternValueType(): PythonCodeBlock {
    val type = modelProperties.declarationType(this)
    val base =
      when (type.kind) {
        GeneratedTypeRef.Kind.ARRAY ->
          PythonCodeBlock.of(
            "%L[%C]",
            if (type.collection == GeneratedCollectionKind.SET) "set" else "list",
            type.arguments.single().renderPatternValueType(),
          )
        GeneratedTypeRef.Kind.MAP -> PythonCodeBlock.of("dict[str, %C]", type.arguments.last().renderPatternValueType())
        GeneratedTypeRef.Kind.SCALAR ->
          if (type.format == null &&
            type.name in setOf("string", "integer", "number", "boolean")
          ) {
            PythonCodeBlock.of(
              "%T[%C, %T()]",
              PythonSymbol("typing", "Annotated"),
              renderPythonType(nullable = false),
              PythonSymbol("pydantic", "Strict"),
            )
          } else {
            renderPythonType(nullable = false)
          }
        else -> renderPythonType(nullable = false)
      }
    return if (type.nullable) PythonCodeBlock.of("%C | None", base) else base
  }

  private fun GeneratedModel.renderWireValueValidator(): PythonCodeBlock? {
    val patterns = modelProperties.patternProperties(this)
    val additional = modelProperties.additionalProperties(this)
    val validatesAdditionalProperties = patterns.isNotEmpty() || additional.isNotEmpty()
    if (!validatesAdditionalProperties) return null

    val statements = mutableListOf<PythonCodeBlock>()
    statements +=
      PythonCodeBlock.of(
        "        adapter: %T[%T]",
        PythonSymbol("pydantic", "TypeAdapter"),
        PythonSymbol("typing", "Any"),
      )
    statements +=
      PythonCodeBlock.of(
        "        declared_names = set(cls.model_fields)\n" +
          "        declared_names.update(" +
          "field.alias for field in cls.model_fields.values() if field.alias is not None)\n" +
          "        wire_names = {name: field.alias or name for name, field in cls.model_fields.items()}\n" +
          "        for key in list(data):\n" +
          "            wire_key = wire_names.get(key, key)\n" +
          "            matched = wire_key in declared_names",
      )
    statements += PythonCodeBlock.of("            original_value = data[key]")
    patterns.forEach { patternProperty ->
      val validatedType =
        renderValidatedType(
          patternProperty.type.renderPatternValueType(),
          patternProperty.validation,
          "pattern property '${patternProperty.pattern}' on model '$name'",
          patternProperty.type,
        )
      patternProperty.allowedValues?.let { values ->
        statements +=
          PythonCodeBlock.of(
            "            if %T(%S, wire_key) is not None and original_value not in [%C]:\n" +
              "                raise ValueError(f\"Invalid pattern property value: {key}\")",
            PythonSymbol("re", "search"),
            patternProperty.pattern,
            PythonCodeBlock.join(values.map { it.renderPythonValue() ?: PythonCodeBlock.of("None") }, separator = ", "),
          )
      }
      statements +=
        PythonCodeBlock.of(
          "            if %T(%S, wire_key) is not None:\n" +
            "                adapter = %T(%C)\n" +
            "                validated = adapter.validate_python(original_value, context=info.context)\n" +
            "                if key not in declared_names:\n" +
            "                    data[key] = validated\n" +
            "                matched = True",
          PythonSymbol("re", "search"),
          patternProperty.pattern,
          PythonSymbol("pydantic", "TypeAdapter"),
          validatedType,
        )
    }
    statements += PythonCodeBlock.of("            if matched:\n                continue")
    if (modelProperties.isClosed(this)) {
      statements +=
        PythonCodeBlock.of(
          "            raise ValueError(f\"Extra property '{key}' is not allowed\")",
        )
    } else {
      additional.forEach { declaration ->
        val additionalType = requireNotNull(declaration.type)
        val validatedType =
          renderValidatedType(
            declaration.allowedValues?.let { values ->
              PythonCodeBlock.of(
                "%T[%C]",
                PythonSymbol("typing", "Literal"),
                PythonCodeBlock.join(values.map { it.renderPythonValue() ?: PythonCodeBlock.of("None") }, ", "),
              )
            } ?: additionalType.renderPatternValueType(),
            declaration.validation,
            "additional properties on model '$name'",
            additionalType,
          )
        statements +=
          PythonCodeBlock.of(
            "            adapter = %T(%C)\n" +
              "            data[key] = adapter.validate_python(original_value, context=info.context)",
            PythonSymbol("pydantic", "TypeAdapter"),
            validatedType,
          )
      }
    }

    return PythonCodeBlock.of(
      "    @%T(mode=%S)\n" +
        "    @classmethod\n" +
        "    def _validate_wire_values(cls, data: object, info: %T) -> object:\n" +
        "        if not isinstance(data, dict):\n" +
        "            return data\n" +
        "        data = data.copy()\n" +
        "%C\n" +
        "        return data",
      PythonSymbol("pydantic", "model_validator"),
      "before",
      PythonSymbol("pydantic", "ValidationInfo"),
      PythonCodeBlock.join(statements, separator = "\n"),
    )
  }

  private fun GeneratedModel.renderEnumModel(): PythonCodeBlock {
    val entries = pythonEnumEntries()
    val members =
      entries
        .joinToString("\n") { entry -> "    ${entry.name} = ${entry.value.pythonStringLiteral()}" }
        .ifBlank { "    pass" }
    val body =
      unknownValue?.let { fallbackValue ->
        val fallbackEntry =
          entries.singleOrNull { entry -> entry.value == fallbackValue }
            ?: genError("Python tolerant enum '$name' unknown value '$fallbackValue' does not match any enum value")
        PythonCodeBlock.of(
          """
          %L

              __unknown_member_name__ = %S
              __request_tolerant__ = %L
          """.trimIndent(),
          members,
          fallbackEntry.name,
          if (tolerance == GeneratedTolerance.ALL ||
            (tolerance == null && defaultTolerance == Tolerance.All)
          ) {
            "True"
          } else {
            "False"
          },
        )
      } ?: PythonCodeBlock.of("%L", members)

    return PythonCodeBlock.of(
      """
      class %L(%T):
      %C
      """.trimIndent(),
      name.pythonTypeName,
      if (unknownValue != null) PythonSymbol("sunday", "TolerantStrEnum") else PythonSymbol("enum", "StrEnum"),
      body,
    )
  }

  private fun GeneratedModel.pythonEnumEntries(): List<PythonEnumEntry> =
    pythonEnumEntriesByModel.getOrPut(this) {
      createPythonEnumEntries()
    }

  private fun GeneratedModel.createPythonEnumEntries(): List<PythonEnumEntry> {
    if (enumValueNames.isNotEmpty() && enumValueNames.size != values.size) {
      genError(
        "Python enum '$name' has ${enumValueNames.size} enum value names for ${values.size} enum values. " +
          "Fix x-enum-varnames so it has one entry per enum value.",
      )
    }

    val entries =
      values.mapIndexed { index, value ->
        val memberName =
          if (enumValueNames.isNotEmpty()) {
            enumValueNames[index].pythonEnumMemberName
          } else {
            value.pythonEnumMemberName
          }
        if (memberName.isBlank()) {
          if (enumValueNames.isNotEmpty()) {
            genError(
              "Python enum '$name' x-enum-varnames entry '${enumValueNames[index]}' for value '$value' " +
                "contains no valid identifier characters. Fix x-enum-varnames with a valid Python enum member name.",
            )
          }
          genError(
            "Python enum '$name' value '$value' contains no valid identifier characters. " +
              "Add x-enum-varnames with a valid Python enum member name.",
          )
        }
        validatePythonEnumMemberName(
          memberName,
          value,
          enumValueNames.getOrNull(index),
        )
        PythonEnumEntry(memberName, value)
      }

    entries
      .groupBy { entry -> entry.name }
      .filterValues { duplicates -> duplicates.size > 1 }
      .forEach { (memberName, duplicates) ->
        genError(
          "Python enum '$name' member name '$memberName' is used for multiple values " +
            duplicates.joinToString(", ") { entry -> "'${entry.value}'" } +
            ". Add x-enum-varnames to disambiguate them.",
        )
      }

    return entries
  }

  private fun GeneratedModel.validatePythonEnumMemberName(
    memberName: String,
    value: String,
    explicitName: String?,
  ) {
    if (!pythonEnumMemberIdentifierRegex.matches(memberName)) {
      if (explicitName != null) {
        genError(
          "Python enum '$name' x-enum-varnames entry '$explicitName' for value '$value' " +
            "maps to invalid member name '$memberName'. Fix x-enum-varnames with a valid " +
            "Python enum member name.",
        )
      }
      genError(
        "Python enum '$name' value '$value' maps to invalid member name '$memberName'. " +
          "Add x-enum-varnames with a valid Python enum member name.",
      )
    }
  }

  private data class PythonEnumEntry(
    val name: String,
    val value: String,
  )

  private fun GeneratedModel.renderUnionModel(): PythonCodeBlock = renderUnionAliasModel()

  private fun GeneratedModel.renderUnionAliasModel(): PythonCodeBlock {
    val fallback = discriminatorFallbacks[name]
    val aliases = unionAliases().ifEmpty { listOf(GeneratedTypeRef.scalar("any")) }
    val unionType = aliases.renderUnionType()
    val hasCommon = hasUnionCommonRules()
    val unionName = if (hasCommon) "_${name.pythonTypeName}Payload" else name.pythonTypeName

    val payload =
      if (fallback == null) {
        renderStandardUnionAlias(aliases, unionType, unionName)
      } else if (fallback.externallyDiscriminated || isExternallyDiscriminatedUnion()) {
        val fallbackType = PythonCodeBlock.of("%L", fallback.modelName.pythonTypeName)
        PythonCodeBlock.of("type %L = %C | %C", unionName, unionType, fallbackType)
      } else {
        renderTolerantDiscriminatedUnionAlias(aliases, fallback, unionName)
      }
    if (!hasCommon) return payload
    val common =
      copy(
        kind = GeneratedModel.Kind.OBJECT,
        aliases = emptyList(),
        discriminator = null,
        discriminatorMappings = emptyMap(),
        externallyDiscriminated = false,
      )
    return PythonCodeBlock.join(
      listOf(
        common.renderObjectModel("_${name.pythonTypeName}Fields"),
        payload,
        PythonCodeBlock.of(
          "type %L = %T[%L, %T(_%LFields%L)]",
          name.pythonTypeName,
          PythonSymbol("typing", "Annotated"),
          unionName,
          PythonSymbol("sunday", "ModelIntersection"),
          name.pythonTypeName,
          fallback?.let { ", skip=(${it.modelName.pythonTypeName},)" } ?: "",
        ),
      ),
      separator = "\n\n\n",
    )
  }

  private fun GeneratedModel.hasUnionCommonRules(): Boolean = modelProperties.hasUnionCommonRules(this)

  private fun GeneratedModel.renderStandardUnionAlias(
    aliases: List<GeneratedTypeRef>,
    unionType: PythonCodeBlock,
    unionName: String,
  ): PythonCodeBlock =
    if (discriminator == null || kind == GeneratedModel.Kind.OBJECT || isExternallyDiscriminatedUnion()) {
      if (aliases.size > 3) {
        PythonCodeBlock.of(
          """
          type %L = (
          %C
          )
          """.trimIndent(),
          unionName,
          aliases.renderMultilineUnionType(),
        )
      } else {
        PythonCodeBlock.of("type %L = %C", unionName, unionType)
      }
    } else {
      if (aliases.size > 1) {
        PythonCodeBlock.of(
          """
          type %L = %T[
              %C,
              %T(discriminator=%S),
          ]
          """.trimIndent(),
          unionName,
          PythonSymbol("typing", "Annotated"),
          unionType,
          PythonSymbol("pydantic", "Field"),
          discriminator.pythonIdentifierName,
        )
      } else {
        PythonCodeBlock.of(
          "type %L = %T[%C, %T(discriminator=%S)]",
          unionName,
          PythonSymbol("typing", "Annotated"),
          unionType,
          PythonSymbol("pydantic", "Field"),
          discriminator.pythonIdentifierName,
        )
      }
    }

  private fun GeneratedModel.renderTolerantDiscriminatedUnionAlias(
    aliases: List<GeneratedTypeRef>,
    fallback: GeneratedDiscriminatorFallback,
    unionName: String,
  ): PythonCodeBlock {
    val discriminatorFunctionName = "_${name.pythonIdentifierName}_discriminator"
    val taggedTypes =
      aliases.mapNotNull { alias ->
        val value =
          discriminatorMappings.entries.firstOrNull { (_, mappedType) -> mappedType == alias }?.key
            ?: modelIndex[alias.name]?.discriminatorValue
            ?: return@mapNotNull null
        PythonCodeBlock.of(
          "%T[%C, %T(%S)]",
          PythonSymbol("typing", "Annotated"),
          alias.renderPythonType(nullable = false),
          PythonSymbol("pydantic", "Tag"),
          value,
        )
      } +
        PythonCodeBlock.of(
          "%T[%L, %T(%S)]",
          PythonSymbol("typing", "Annotated"),
          fallback.modelName.pythonTypeName,
          PythonSymbol("pydantic", "Tag"),
          FALLBACK_TAG,
        )
    val mappedValues = fallback.mappedValues.sorted().joinToString(", ") { value -> value.pythonStringLiteral() }

    return PythonCodeBlock.of(
      """
      def %L(value: object) -> str | None:
          if isinstance(value, %L):
              return %S
          discriminator = value.get(%S) if isinstance(value, dict) else getattr(value, %S, None)
          if not isinstance(discriminator, str):
              return None
          if discriminator in {%L}:
              return discriminator
          return %S


      type %L = %T[
          %C,
          %T(%L),
      ]
      """.trimIndent(),
      discriminatorFunctionName,
      fallback.modelName.pythonTypeName,
      FALLBACK_TAG,
      fallback.discriminatorWireName,
      fallback.discriminatorProperty.name.pythonIdentifierName,
      mappedValues,
      FALLBACK_TAG,
      unionName,
      PythonSymbol("typing", "Annotated"),
      PythonCodeBlock.join(taggedTypes, separator = if (taggedTypes.size > 2) "\n    | " else " | "),
      PythonSymbol("pydantic", "Discriminator"),
      discriminatorFunctionName,
    )
  }

  private fun GeneratedDiscriminatorFallback.renderFallbackModel(): PythonCodeBlock {
    val exposedProperties =
      buildList {
        if (!externallyDiscriminated) {
          add(discriminatorProperty)
        }
        addAll(baseProperties)
      }
    val fieldsModel = GeneratedModel(modelName, GeneratedModel.Kind.OBJECT, properties = exposedProperties)
    val propertyBlocks =
      exposedProperties.map { property ->
        val basePropertyType = property.type.renderPythonType(nullable = false)
        val propertyType =
          if (property.required && !property.type.nullable) {
            basePropertyType
          } else {
            PythonCodeBlock.of("%C | None", basePropertyType)
          }
        PythonCodeBlock.of(
          "    @property\n" +
            "    def %L(self) -> %C:\n" +
            "        return %L.model_validate(self.root).%L",
          property.name.pythonIdentifierName,
          propertyType,
          fieldsClassName(),
          property.name.pythonIdentifierName,
        )
      }
    val propertySection =
      propertyBlocks
        .takeIf { blocks -> blocks.isNotEmpty() }
        ?.let { blocks -> PythonCodeBlock.of("%C\n\n", PythonCodeBlock.join(blocks, separator = "\n\n")) }
        ?: PythonCodeBlock.of("")

    return PythonCodeBlock.of(
      """
      %C


      class %L(%T):
          __request_tolerant__ = %L

      %C    @%T(mode="after")
          def _validate_declared_properties(self, info: %T) -> %T:
              %L.model_validate(self.root, context=info.context)
              return self

          @property
          def raw_body(self) -> dict[str, %T]:
              return self.root
      """.trimIndent(),
      fieldsModel.renderObjectModel(fieldsClassName()),
      modelName.pythonTypeName,
      PythonSymbol("sunday", "UnknownModel"),
      if ((hierarchy.tolerance ?: enumModel?.tolerance) == GeneratedTolerance.ALL ||
        (hierarchy.tolerance == null && enumModel?.tolerance == null && defaultTolerance == Tolerance.All)
      ) {
        "True"
      } else {
        "False"
      },
      propertySection,
      PythonSymbol("pydantic", "model_validator"),
      PythonSymbol("pydantic", "ValidationInfo"),
      PythonSymbol("typing", "Self"),
      fieldsClassName(),
      PythonSymbol("typing", "Any"),
    )
  }

  private fun GeneratedDiscriminatorFallback.fieldsClassName(): String = "_${modelName.pythonTypeName}Fields"

  private fun List<GeneratedTypeRef>.renderUnionType(): PythonCodeBlock =
    PythonCodeBlock.join(
      map { type -> type.renderPythonType(nullable = false) },
      separator = " | ",
    )

  private fun List<GeneratedTypeRef>.renderMultilineUnionType(): PythonCodeBlock =
    PythonCodeBlock.join(
      mapIndexed { index, type ->
        if (index == 0) {
          PythonCodeBlock.of("    %C", type.renderPythonType(nullable = false))
        } else {
          PythonCodeBlock.of("    | %C", type.renderPythonType(nullable = false))
        }
      },
    )

  private fun GeneratedModel.renderExternalDiscriminatorValidator(): PythonCodeBlock? {
    val externalProperties =
      properties.filter { property ->
        property.externalDiscriminator != null &&
          property.type.kind == GeneratedTypeRef.Kind.NAMED &&
          modelIndex[property.type.name]?.discriminatorMappings?.isNotEmpty() == true
      }
    if (externalProperties.isEmpty()) return null
    val capture =
      externalProperties.map { property ->
        val discriminator =
          properties.singleOrNull {
            (it.serializationName ?: it.name) == property.externalDiscriminator ||
              it.name == property.externalDiscriminator
          }
        PythonCodeBlock.of(
          "        key = %S if %S in data else %S\n" +
            "        if key in data and data[key] is not None:\n" +
            "            current = data[key]\n" +
            "            if isinstance(current, %T):\n" +
            "                current = current.value\n" +
            "            data[key] = %T(data.get(%S, data.get(%S)), current)",
          (property.serializationName ?: property.name),
          (property.serializationName ?: property.name),
          property.name.pythonIdentifierName,
          PythonSymbol("sunday.external_discriminator", "ExternalDiscriminatorValue"),
          PythonSymbol("sunday.external_discriminator", "ExternalDiscriminatorValue"),
          discriminator?.let { it.serializationName ?: it.name } ?: property.externalDiscriminator!!,
          discriminator?.name?.pythonIdentifierName ?: property.externalDiscriminator!!,
        )
      }
    val before =
      PythonCodeBlock.of(
        """
            @%T(mode="before")
            @classmethod
            def _capture_external_discriminators(cls, data: object) -> object:
                if not isinstance(data, dict):
                    return data
                data = data.copy()
        %C
                return data
        """.trimIndent(),
        PythonSymbol("pydantic", "model_validator"),
        PythonCodeBlock.join(capture, separator = "\n"),
      )
    return PythonCodeBlock.join(
      listOf(before) + externalProperties.map { it.renderExternalDiscriminatorMapping() },
      "\n\n",
    )
  }

  private fun GeneratedModelProperty.renderExternalDiscriminatorMapping(): PythonCodeBlock {
    val mappedValues = modelIndex[type.name]?.discriminatorMappings.orEmpty()
    val branches =
      mappedValues.map { (tag, mappedType) ->
        PythonCodeBlock.of(
          "        if value.discriminator == %S:\n" +
            "            return %T(%C).validate_python(value.value, context=info.context)",
          tag,
          PythonSymbol("pydantic", "TypeAdapter"),
          mappedType.renderPythonType(nullable = false),
        )
      }
    val fallback =
      discriminatorFallbacks[type.name]?.let {
        PythonCodeBlock.of(
          "        if isinstance(value.discriminator, str):\n" +
            "            return %T(%L).validate_python(value.value, context=info.context)",
          PythonSymbol("pydantic", "TypeAdapter"),
          it.modelName.pythonTypeName,
        )
      }
    return PythonCodeBlock.of(
      """
          @%T(%S, mode="wrap")
          @classmethod
          def _validate_%L_discriminator(
              cls,
              value: object,
              handler: %T,
              info: %T,
          ) -> %T:
              if not isinstance(value, %T):
                  return handler(value)
              if value.value is None:
                  return handler(None)
      %C
              raise ValueError("Missing or unsupported external discriminator")
      """.trimIndent(),
      PythonSymbol("pydantic", "field_validator"),
      name.pythonIdentifierName,
      name.pythonIdentifierName,
      PythonSymbol("pydantic", "ValidatorFunctionWrapHandler"),
      PythonSymbol("pydantic", "ValidationInfo"),
      PythonSymbol("typing", "Any"),
      PythonSymbol("sunday.external_discriminator", "ExternalDiscriminatorValue"),
      PythonCodeBlock.join(branches + listOfNotNull(fallback), "\n"),
    )
  }

  private fun GeneratedModelProperty.requiresUniqueListValidation(): Boolean =
    validation["uniqueItems"] == "true" &&
      modelProperties.declarationType(type).let {
        it.kind == GeneratedTypeRef.Kind.ARRAY &&
          it.collection != io.outfoxx.sunday.generator.ir.GeneratedCollectionKind.SET
      }

  private fun GeneratedModelProperty.enumStringConstraints(): Map<String, String> =
    if (modelProperties.declarationModel(type)?.let { it.kind == GeneratedModel.Kind.ENUM || it.nominal } == true) {
      validation.filterKeys { it in setOf("minLength", "maxLength", "pattern") }
    } else {
      emptyMap()
    }

  private fun GeneratedModel.renderEnumStringValidator(): PythonCodeBlock? {
    val fields = effectiveModelProperties().filter { it.enumStringConstraints().isNotEmpty() }
    if (fields.isEmpty()) return null
    return PythonCodeBlock.of(
      "%C\n" +
        "    @classmethod\n" +
        "    def _validate_enum_strings(cls, value: object, info: %T) -> object:\n" +
        "        if value is None:\n" +
        "            return value\n" +
        "        wire_value = value.value if isinstance(value, %T) else value\n" +
        "%C\n" +
        "        return value",
      fields.renderFieldValidator("after"),
      PythonSymbol("pydantic", "ValidationInfo"),
      PythonSymbol("enum", "Enum"),
      PythonCodeBlock.join(
        fields.map { property ->
          PythonCodeBlock.of(
            "        if info.field_name == %S:\n            %L.validate_python(wire_value, strict=True)",
            property.name.pythonIdentifierName,
            enumStringAdapters.getValue(property.enumStringConstraints()),
          )
        },
        "\n",
      ),
    )
  }

  private fun GeneratedModel.renderAllowedValuesValidator(): PythonCodeBlock? {
    val fields = effectiveModelProperties().filter { it.allowedValues != null }
    if (fields.isEmpty()) return null
    // Pydantic must inspect discriminator literals before any validator can alter their values.
    val (discriminatorFields, valueFields) = fields.partition { it.discriminatorLiteralValue(this) != null }
    return PythonCodeBlock.join(
      listOf("before" to valueFields, "after" to discriminatorFields)
        .filter { (_, properties) -> properties.isNotEmpty() }
        .mapIndexed { index, (mode, properties) ->
          // Replace the inherited validator even when every restriction is now on a discriminator.
          properties.renderAllowedValuesValidator(
            mode,
            if (index == 0) "_validate_allowed_values" else "_validate_discriminator_allowed_values",
          )
        },
      separator = "\n\n",
    )
  }

  private fun List<GeneratedModelProperty>.renderAllowedValuesValidator(
    mode: String,
    name: String,
  ): PythonCodeBlock {
    val checks =
      map { property ->
        val values = property.allowedValues.orEmpty().map { it?.renderPythonValue() ?: PythonCodeBlock.of("None") }
        val tuple =
          if (values.size == 1) {
            PythonCodeBlock.of("(%C,)", values.single())
          } else {
            PythonCodeBlock.of("(%C)", PythonCodeBlock.join(values, ", "))
          }
        val format =
          modelProperties
            .declarationType(
              property.type,
            ).let { it.format?.lowercase() ?: it.name.lowercase() }
        val arguments = PythonCodeBlock.of("value, %C, %S", tuple, format)
        val fieldName = property.name.pythonIdentifierName.pythonStringLiteral()
        val prefix = "        if info.field_name == $fieldName and not "
        val renderedArguments = arguments.render(PythonRenderContext(PythonImportSet()))
        val condition =
          if (prefix.length + "_is_allowed_wire_value():".length + renderedArguments.length <= 120) {
            PythonCodeBlock.of("%L_is_allowed_wire_value(%C):", prefix, arguments)
          } else {
            PythonCodeBlock.of("%L_is_allowed_wire_value(\n            %C\n        ):", prefix, arguments)
          }
        PythonCodeBlock.of(
          "%C\n            raise ValueError(%S)",
          condition,
          "Invalid value for '${property.serializationName ?: property.name}'",
        )
      }
    return PythonCodeBlock.of(
      "%C\n" +
        "    @classmethod\n" +
        "    def %L(cls, value: object, info: %T) -> object:\n" +
        "%C\n" +
        "        return value",
      renderFieldValidator(mode),
      name,
      PythonSymbol("pydantic", "ValidationInfo"),
      PythonCodeBlock.join(checks, separator = "\n"),
    )
  }

  private fun GeneratedModel.renderUniqueListValidator(): PythonCodeBlock? {
    val fields = effectiveModelProperties().filter { it.requiresUniqueListValidation() }
    if (fields.isEmpty()) return null
    return PythonCodeBlock.of(
      "%C\n" +
        """
        @classmethod
        def _validate_unique_lists(cls, value: object) -> object:
            if isinstance(value, %T) and not isinstance(value, (str, bytes, dict)):
                items = value if isinstance(value, list) else list(value)
                for index, item in enumerate(items):
                    if any(_wire_values_equal(item, previous) for previous in items[:index]):
                        raise ValueError("Array items must be unique")
                return items
            return value
        """.trimIndent().lines().joinToString("\n") {
          if (it.isEmpty()) it else "    $it"
        },
      fields.renderFieldValidator("before"),
      PythonSymbol("collections.abc", "Iterable"),
    )
  }

  private fun List<GeneratedModelProperty>.renderFieldValidator(mode: String): PythonCodeBlock {
    val fieldNames = map { it.name.pythonIdentifierName }
    val arguments = fieldNames.map { it.pythonStringLiteral() } + "mode=${mode.pythonStringLiteral()}"
    val inline = "    @field_validator(${arguments.joinToString(", ")})"
    return if (inline.length <= 120) {
      PythonCodeBlock.of(
        "    @%T(%C, mode=%S)",
        PythonSymbol("pydantic", "field_validator"),
        PythonCodeBlock.join(fieldNames.map { PythonCodeBlock.of("%S", it) }, separator = ", "),
        mode,
      )
    } else {
      PythonCodeBlock.of(
        "    @%T(\n%C,\n        mode=%S,\n    )",
        PythonSymbol("pydantic", "field_validator"),
        PythonCodeBlock.join(fieldNames.map { PythonCodeBlock.of("        %S", it) }, separator = ",\n"),
        mode,
      )
    }
  }

  private fun GeneratedModelProperty.renderProperty(model: GeneratedModel): PythonCodeBlock {
    val propertyName = name.pythonIdentifierName
    val literalValue = discriminatorLiteralValue(model)
    val externalDiscriminatorType = renderExternalDiscriminatorPropertyType()
    val basePropertyType =
      literalValue?.renderPythonLiteralType()
        ?: externalDiscriminatorType
        ?: type.renderPythonType(nullable = false)
    val valueType =
      if (!type.nullable && (model.patchable || required || defaultValue != null)) {
        basePropertyType
      } else {
        PythonCodeBlock.of("%C | None", basePropertyType)
      }
    val propertyType =
      if (model.patchable) {
        PythonCodeBlock.of("%C | %T", valueType, PythonSymbol("sunday", "UnsetType"))
      } else {
        valueType
      }
    val alias = serializationName ?: name
    val fieldArguments = mutableListOf<PythonCodeBlock>()
    if (model.patchable) {
      fieldArguments += PythonCodeBlock.of("default_factory=lambda: %T", PythonSymbol("sunday", "UNSET"))
      fieldArguments += PythonCodeBlock.of("exclude_if=%T", PythonSymbol("sunday", "is_unset"))
    } else if (!required) {
      fieldArguments +=
        PythonCodeBlock.of(
          "default=%C",
          defaultValue?.let { value -> renderDefaultValue(value, model.name) } ?: PythonCodeBlock.of("None"),
        )
    }
    if (!model.patchable && !required && !type.acceptsNull()) {
      fieldArguments += PythonCodeBlock.of("exclude_if=lambda value: value is None")
    }
    if (!model.patchable && defaultValue != null) {
      fieldArguments += PythonCodeBlock.of("validate_default=True")
    }
    if (alias != propertyName) {
      fieldArguments += PythonCodeBlock.of("alias=%S", alias)
    }
    val enumConstraints = enumStringConstraints()
    fieldArguments +=
      (validation - enumConstraints.keys).renderFieldConstraints(
        "property '${model.name}.$name'",
        type,
        requiresUniqueListValidation(),
      )
    documentation?.description?.let { description ->
      fieldArguments += PythonCodeBlock.of("description=%S", description)
    }
    if (deprecated) {
      fieldArguments += PythonCodeBlock.of("deprecated=True")
    }
    val schemaExtra = mutableListOf<PythonCodeBlock>()
    enumConstraints.forEach { (constraint, value) ->
      schemaExtra +=
        if (constraint == "pattern") {
          PythonCodeBlock.of("%S: %S", constraint, value)
        } else {
          PythonCodeBlock.of(
            "%S: %L",
            constraint,
            value.pythonNonNegativeIntegerLiteral("constraint '$constraint' on property '${model.name}.$name'"),
          )
        }
    }
    if (readOnly) {
      schemaExtra += PythonCodeBlock.of("%S: True", "readOnly")
    }
    if (writeOnly) {
      schemaExtra += PythonCodeBlock.of("%S: True", "writeOnly")
    }
    if (schemaExtra.isNotEmpty()) {
      fieldArguments +=
        PythonCodeBlock.of(
          "json_schema_extra={%C}",
          PythonCodeBlock.join(schemaExtra, separator = ", "),
        )
    }
    val exampleValues = examples.mapNotNull { example -> example.value?.renderPythonValue() }
    if (exampleValues.isNotEmpty()) {
      fieldArguments +=
        PythonCodeBlock.of(
          "examples=[%C]",
          PythonCodeBlock.join(exampleValues, separator = ", "),
        )
    }

    val overrideSuffix =
      if (model.inheritedPropertyNames().contains(name) &&
        modelProperties
          .fields(model)
          .single { it.wireName == (serializationName ?: name) }
          .declaration.type
          .let { declaration ->
            declaration.copy(nullable = false) != type.copy(nullable = false) ||
              (
                literalValue != null &&
                  modelProperties.declarationModel(declaration)?.kind == GeneratedModel.Kind.ENUM
              )
          }
      ) {
        "  # type: ignore[assignment]"
      } else {
        ""
      }

    return if (fieldArguments.isEmpty()) {
      PythonCodeBlock.of("    %L: %C%L", propertyName, propertyType, overrideSuffix)
    } else {
      val arguments = PythonCodeBlock.join(fieldArguments, separator = ", ")
      val inline =
        PythonCodeBlock.of(
          "    %L: %C = %T(%C)%L",
          propertyName,
          propertyType,
          PythonSymbol("pydantic", "Field"),
          arguments,
          overrideSuffix,
        )
      val context = PythonRenderContext(PythonImportSet())
      if ((model.patchable || enumConstraints.isNotEmpty() || !required && !type.acceptsNull()) &&
        inline.render(context).length > 120
      ) {
        val multilineArguments =
          if (arguments.render(context).length + 8 <= 120) {
            PythonCodeBlock.of("        %C", arguments)
          } else {
            PythonCodeBlock.join(fieldArguments.map { PythonCodeBlock.of("        %C,", it) }, "\n")
          }
        PythonCodeBlock.of(
          "    %L: %C = %T(\n%C\n    )%L",
          propertyName,
          propertyType,
          PythonSymbol("pydantic", "Field"),
          multilineArguments,
          overrideSuffix,
        )
      } else if (defaultValue == null ||
        required ||
        inline.render(context).length <= 120 ||
        arguments.render(context).length + 8 > 120
      ) {
        inline
      } else {
        PythonCodeBlock.of(
          "    %L: %C = %T(\n        %C\n    )%L",
          propertyName,
          propertyType,
          PythonSymbol("pydantic", "Field"),
          arguments,
          overrideSuffix,
        )
      }
    }
  }

  private fun GeneratedModel.inheritedPropertyNames(): Set<String> =
    inherits
      .mapNotNull { inherited -> modelIndex[inherited.name]?.takeIf { model -> model.isObjectClass() } }
      .flatMapTo(mutableSetOf()) { inherited ->
        inherited.properties.map { property -> property.name } + inherited.inheritedPropertyNames()
      }

  private fun GeneratedModelProperty.renderDefaultValue(
    value: String,
    modelName: String,
  ): PythonCodeBlock {
    val declarationType = modelProperties.declarationType(type)
    return when {
      requiresUniqueListValidation() -> {
        val parsed = runCatching { ObjectMapper().readValue(value, List::class.java) }.getOrNull()
        parsed?.renderPythonValue() ?: genError("Invalid array default for property '$name': expected a JSON array")
      }
      declarationType.kind == GeneratedTypeRef.Kind.SCALAR && declarationType.name == "boolean" ->
        PythonCodeBlock.of(
          when (value) {
            "true" -> "True"
            "false" -> "False"
            else -> genError("Invalid boolean default '$value' for property '$name'")
          },
        )
      declarationType.kind == GeneratedTypeRef.Kind.SCALAR && declarationType.name == "integer" ->
        PythonCodeBlock.of(
          "%L",
          value.pythonIntegerDefault("default for property '$modelName.${serializationName ?: name}'"),
        )
      declarationType.kind == GeneratedTypeRef.Kind.SCALAR && declarationType.name == "number" ->
        PythonCodeBlock.of("%L", value.pythonNumberLiteral("default for property '$name'"))
      else -> PythonCodeBlock.of("%S", value)
    }
  }

  private fun Map<String, String>.renderFieldConstraints(
    context: String,
    type: GeneratedTypeRef,
    validatesUniqueList: Boolean = false,
  ): List<PythonCodeBlock> =
    entries.sortedBy { entry -> entry.key }.mapNotNull { (name, value) ->
      when (name) {
        "minimum" ->
          PythonCodeBlock.of(
            if (this["exclusiveMinimum"] == "true") "gt=%L" else "ge=%L",
            value.pythonNumberLiteral("constraint '$name' on $context"),
          )
        "maximum" ->
          PythonCodeBlock.of(
            if (this["exclusiveMaximum"] == "true") "lt=%L" else "le=%L",
            value.pythonNumberLiteral("constraint '$name' on $context"),
          )
        "exclusiveMinimum" ->
          value.renderExclusiveConstraint("gt", "minimum", name, context, this)
        "exclusiveMaximum" ->
          value.renderExclusiveConstraint("lt", "maximum", name, context, this)
        "multipleOf" ->
          PythonCodeBlock.of(
            "multiple_of=%L",
            value.pythonPositiveNumberLiteral("constraint '$name' on $context"),
          )
        "minLength", "minItems", "minProperties" ->
          PythonCodeBlock.of(
            "min_length=%L",
            value.pythonNonNegativeIntegerLiteral("constraint '$name' on $context"),
          )
        "maxLength", "maxItems", "maxProperties" ->
          PythonCodeBlock.of(
            "max_length=%L",
            value.pythonNonNegativeIntegerLiteral("constraint '$name' on $context"),
          )
        "pattern" -> PythonCodeBlock.of("pattern=%S", value)
        "uniqueItems" -> {
          when (value) {
            "true" ->
              if (!validatesUniqueList &&
                modelProperties.declarationType(type).let {
                  it.kind == GeneratedTypeRef.Kind.ARRAY &&
                    it.collection?.name != "SET"
                }
              ) {
                genError("Python $context requires uniqueItems but is not represented as a set")
              }
            "false" -> Unit
            else -> genError("Invalid boolean constraint 'uniqueItems' value '$value' on $context")
          }
          null
        }
        else -> genError("Unsupported Python validation '$name' on $context")
      }
    }

  private fun String.renderExclusiveConstraint(
    pythonName: String,
    boundName: String,
    constraintName: String,
    context: String,
    constraints: Map<String, String>,
  ): PythonCodeBlock? =
    when (this) {
      "true" -> {
        if (boundName !in constraints) {
          genError("Python $context requires '$boundName' when '$constraintName' is true")
        }
        null
      }
      "false" -> null
      else ->
        PythonCodeBlock.of(
          "$pythonName=%L",
          pythonNumberLiteral("constraint '$constraintName' on $context"),
        )
    }

  private fun String.pythonIntegerDefault(context: String): String =
    try {
      BigDecimal(this).toBigIntegerExact().toString()
    } catch (_: NumberFormatException) {
      genError("Invalid integer $context: '$this'")
    } catch (_: ArithmeticException) {
      genError("Invalid integer $context: '$this'")
    }

  private fun String.pythonNonNegativeIntegerLiteral(context: String): String {
    val parsed = parsePythonInteger(context)
    if (parsed.signum() < 0) {
      genError("Invalid negative integer $context: '$this'")
    }
    return parsed.toString()
  }

  private fun String.pythonNumberLiteral(context: String): String = parsePythonNumber(context).toString()

  private fun String.pythonPositiveNumberLiteral(context: String): String {
    val parsed = parsePythonNumber(context)
    if (parsed.signum() <= 0) {
      genError("Invalid non-positive number $context: '$this'")
    }
    return parsed.toString()
  }

  private fun String.parsePythonInteger(context: String): BigInteger =
    try {
      BigInteger(this)
    } catch (_: NumberFormatException) {
      genError("Invalid integer $context: '$this'")
    }

  private fun String.parsePythonNumber(context: String): BigDecimal =
    try {
      BigDecimal(this)
    } catch (_: NumberFormatException) {
      genError("Invalid number $context: '$this'")
    }

  private fun Any?.renderPythonValue(): PythonCodeBlock? =
    when (this) {
      null -> PythonCodeBlock.of("None")
      is Boolean -> PythonCodeBlock.of(if (this) "True" else "False")
      is Number -> PythonCodeBlock.of("%L", this)
      is String -> PythonCodeBlock.of("%S", this)
      is List<*> ->
        PythonCodeBlock.of(
          "[%C]",
          PythonCodeBlock.join(mapNotNull { value -> value.renderPythonValue() }, separator = ", "),
        )
      is Map<*, *> -> {
        val entries =
          entries.mapNotNull { (key, value) ->
            if (key !is String) {
              null
            } else {
              value.renderPythonValue()?.let { rendered -> PythonCodeBlock.of("%S: %C", key, rendered) }
            }
          }
        PythonCodeBlock.of("{%C}", PythonCodeBlock.join(entries, separator = ", "))
      }
      else -> null
    }

  private fun GeneratedModelProperty.renderExternalDiscriminatorPropertyType(): PythonCodeBlock? {
    if (externalDiscriminator == null) {
      return null
    }
    val fallback = discriminatorFallbacks[type.name] ?: return null
    val hierarchy = modelIndex[type.name] ?: return null
    val memberTypes =
      hierarchy.discriminatorMappings.values
        .distinct()
        .map { mappedType -> mappedType.renderPythonType(nullable = false) } +
        PythonCodeBlock.of("%L", fallback.modelName.pythonTypeName)
    return memberTypes.takeIf { types -> types.isNotEmpty() }?.let { types ->
      PythonCodeBlock.join(types, separator = " | ")
    }
  }

  private fun GeneratedModel.unionAliases(): List<GeneratedTypeRef> =
    if (discriminatorMappings.isNotEmpty()) {
      discriminatorMappings.values.toList()
    } else if (kind == GeneratedModel.Kind.OBJECT && isDiscriminatedAlias()) {
      modelIndex.values
        .filter { child -> child.inherits.any { it.name == name } }
        .map { child -> GeneratedTypeRef.named(child.name, source = child.source) }
    } else {
      aliases
    }

  private fun GeneratedModel.effectiveModelProperties(): List<GeneratedModelProperty> =
    modelProperties.fields(this).map { it.effective }

  private fun GeneratedModel.renderedModelProperties(): List<GeneratedModelProperty> {
    val inheritedAliasProperties =
      inherits
        .mapNotNull { inherited ->
          modelIndex[inherited.name]?.takeUnless { model -> model.isObjectClass() }
        }.flatMap { model -> model.effectiveModelProperties() }
    val overrideNames =
      properties
        .map { property -> property.serializationName ?: property.name }
        .toSet()
    val inheritedClassFields =
      inherits
        .mapNotNull { modelIndex[it.name]?.takeIf { model -> model.isObjectClass() } }
        .flatMap { modelProperties.fields(it) }
        .groupBy { it.wireName }
    // Pydantic selects one parent's Field metadata; explicitly retain the combined contract where it differs.
    val combinedProperties =
      modelProperties
        .fields(this)
        .filter { field ->
          field.wireName !in overrideNames &&
            inheritedClassFields[field.wireName]?.firstOrNull()?.let { it.effective != field.effective } == true
        }.map { it.effective }
    val combinedNames = combinedProperties.map { it.serializationName ?: it.name }.toSet()
    return inheritedAliasProperties
      .filterNot { property ->
        (property.serializationName ?: property.name) in overrideNames + combinedNames
      } +
      properties.map { property ->
        if (modelProperties.declarationModel(property.type)?.nominal == true) {
          modelProperties.fields(this).first { it.wireName == (property.serializationName ?: property.name) }.effective
        } else {
          property
        }
      } + combinedProperties
  }

  private fun GeneratedModelProperty.discriminatorLiteralValue(model: GeneratedModel): String? {
    val discriminator = model.discriminatorPropertyName() ?: return null
    val value = model.discriminatorValue ?: model.mappedDiscriminatorValue() ?: return null
    return value.takeIf { name == discriminator }
  }

  private fun GeneratedModel.syntheticDiscriminatorProperty(): GeneratedModelProperty? {
    val discriminator = discriminatorPropertyName() ?: return null
    if ((discriminatorValue ?: mappedDiscriminatorValue()) == null ||
      properties.any { property -> property.name == discriminator }
    ) {
      return null
    }
    return effectiveModelProperties()
      .firstOrNull { property -> property.name == discriminator }
      ?.copy(required = true)
      ?: GeneratedModelProperty(
        name = discriminator,
        type = GeneratedTypeRef.scalar("string"),
        required = true,
      )
  }

  private fun GeneratedModel.discriminatorPropertyName(): String? =
    discriminator
      ?: inheritedDiscriminatorPropertyName()
      ?: mappedDiscriminatorPropertyName()

  private fun GeneratedModel.inheritedDiscriminatorPropertyName(): String? =
    inherits.firstNotNullOfOrNull { inherited ->
      inherited
        .takeIf { type -> type.kind == GeneratedTypeRef.Kind.NAMED }
        ?.let { type -> modelIndex[type.name]?.discriminatorPropertyName() }
    }

  private fun GeneratedModel.mappedDiscriminatorPropertyName(): String? =
    modelIndex.values.firstNotNullOfOrNull { candidate ->
      candidate.discriminator
        ?.takeIf {
          !candidate.isExternallyDiscriminatedUnion() &&
            candidate.discriminatorMappings.values.any { mappedType ->
              mappedType.kind == GeneratedTypeRef.Kind.NAMED && mappedType.name == name
            }
        }
    }

  private fun GeneratedModel.mappedDiscriminatorValue(): String? =
    modelIndex.values.firstNotNullOfOrNull { candidate ->
      if (candidate.isExternallyDiscriminatedUnion()) {
        return@firstNotNullOfOrNull null
      }
      candidate.discriminatorMappings.entries
        .firstOrNull { (_, mappedType) ->
          mappedType.kind == GeneratedTypeRef.Kind.NAMED && mappedType.name == name
        }?.key
    }

  private fun GeneratedModel.isExternallyDiscriminatedUnion(): Boolean =
    kind == GeneratedModel.Kind.UNION &&
      modelIndex.values.any { candidate ->
        candidate.properties.any { property ->
          property.externalDiscriminator != null &&
            property.type.kind == GeneratedTypeRef.Kind.NAMED &&
            property.type.name == name
        }
      }

  private fun GeneratedTypeRef.acceptsNull(): Boolean = modelProperties.acceptsNull(this)
}
