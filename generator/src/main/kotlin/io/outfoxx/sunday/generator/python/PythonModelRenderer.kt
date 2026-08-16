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

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedDiscriminatorFallback
import io.outfoxx.sunday.generator.ir.emit.discriminatorFallbackOrNull
import io.outfoxx.sunday.generator.ir.emit.externalDiscriminatorFallbackOrNull

private val pythonEnumMemberIdentifierRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")
private const val FALLBACK_TAG = "__unknown__"

/** Renders IR models into a Python Pydantic models module. */
class PythonModelRenderer(
  private val packageName: String,
) {

  private var modelIndex: Map<String, GeneratedModel> = mapOf()
  private var discriminatorFallbacks: Map<String, GeneratedDiscriminatorFallback> = mapOf()
  private val pythonEnumEntriesByModel = mutableMapOf<GeneratedModel, List<PythonEnumEntry>>()

  /** Renders the given models into the package `models.py` module. */
  fun renderModels(models: List<GeneratedModel>): PythonModule {
    val module = PythonModuleBuilder("$packageName/models.py")
    modelIndex = models.associateBy { model -> model.name }
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

    models
      .filter { model -> model.isSupportedModel() }
      .forEach { model ->
        discriminatorFallbacks[model.name]?.let { fallback ->
          module.addExport(fallback.modelName.pythonTypeName)
          module.addCode(fallback.renderFallbackModel())
        }
        module.addExport(model.name.pythonTypeName)
        module.addCode(model.renderModel())
      }

    return module.build()
  }

  private fun GeneratedModel.isSupportedModel(): Boolean =
    kind == GeneratedModel.Kind.ENUM ||
      kind == GeneratedModel.Kind.OBJECT ||
      kind == GeneratedModel.Kind.SCALAR_ALIAS ||
      kind == GeneratedModel.Kind.UNION

  private fun GeneratedModel.renderModel(): PythonCodeBlock =
    when (kind) {
      GeneratedModel.Kind.OBJECT -> renderObjectModel()
      GeneratedModel.Kind.ENUM -> renderEnumModel()
      GeneratedModel.Kind.SCALAR_ALIAS -> renderScalarAliasModel()
      GeneratedModel.Kind.UNION -> renderUnionModel()
      else -> error("Unsupported Python model kind in initial model slices: $kind")
    }

  private fun GeneratedModel.renderObjectModel(): PythonCodeBlock {
    if (discriminatorMappings.isNotEmpty() && (properties.isEmpty() || discriminator != null)) {
      return renderUnionAliasModel()
    }

    val effectiveProperties = effectiveModelProperties()
    val renderedProperties =
      syntheticDiscriminatorProperty()
        ?.let { discriminatorProperty ->
          val discriminatorWireName = discriminatorProperty.serializationName ?: discriminatorProperty.name
          listOf(discriminatorProperty) +
            effectiveProperties.filterNot { property ->
              (property.serializationName ?: property.name) == discriminatorWireName
            }
        } ?: effectiveProperties
    val body =
      if (renderedProperties.isEmpty()) {
        PythonCodeBlock.of("    pass")
      } else {
        val validators = renderExternalDiscriminatorValidator()
        if (validators == null) {
          PythonCodeBlock.join(renderedProperties.map { property -> property.renderProperty(this) })
        } else {
          val propertyBlock = PythonCodeBlock.join(renderedProperties.map { property -> property.renderProperty(this) })
          val blocks = listOf(propertyBlock, validators)
          PythonCodeBlock.join(blocks, separator = "\n\n")
        }
      }

    return PythonCodeBlock.of(
      """
      class %L(%T):
          model_config = %T(populate_by_name=True, serialize_by_alias=True)

      %C
      """.trimIndent(),
      name.pythonTypeName,
      PythonSymbol("pydantic", "BaseModel"),
      PythonSymbol("pydantic", "ConfigDict"),
      body,
    )
  }

  private fun GeneratedModel.renderScalarAliasModel(): PythonCodeBlock =
    PythonCodeBlock.of(
      "type %L = %C",
      name.pythonTypeName,
      aliases.firstOrNull()?.renderPythonType(nullable = false) ?: GeneratedTypeRef.scalar("any").renderPythonType(),
    )

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

              @classmethod
              def _missing_(cls, value: object) -> %T | None:
                  if not isinstance(value, str):
                      return None
                  member = str.__new__(cls, value)
                  member._name_ = %S
                  member._value_ = value
                  return member
          """.trimIndent(),
          members,
          PythonSymbol("typing", "Self"),
          fallbackEntry.name,
        )
      } ?: PythonCodeBlock.of("%L", members)

    return PythonCodeBlock.of(
      """
      class %L(%T):
      %C
      """.trimIndent(),
      name.pythonTypeName,
      PythonSymbol("enum", "StrEnum"),
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

    return if (fallback == null) {
      renderStandardUnionAlias(aliases, unionType)
    } else if (fallback.externallyDiscriminated || isExternallyDiscriminatedUnion()) {
      val fallbackType = PythonCodeBlock.of("%L", fallback.modelName.pythonTypeName)
      PythonCodeBlock.of("type %L = %C | %C", name.pythonTypeName, unionType, fallbackType)
    } else {
      renderTolerantDiscriminatedUnionAlias(aliases, fallback)
    }
  }

  private fun GeneratedModel.renderStandardUnionAlias(
    aliases: List<GeneratedTypeRef>,
    unionType: PythonCodeBlock,
  ): PythonCodeBlock =
    if (discriminator == null || kind == GeneratedModel.Kind.OBJECT || isExternallyDiscriminatedUnion()) {
      if (aliases.size > 3) {
        PythonCodeBlock.of(
          """
          type %L = (
          %C
          )
          """.trimIndent(),
          name.pythonTypeName,
          aliases.renderMultilineUnionType(),
        )
      } else {
        PythonCodeBlock.of("type %L = %C", name.pythonTypeName, unionType)
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
          name.pythonTypeName,
          PythonSymbol("typing", "Annotated"),
          unionType,
          PythonSymbol("pydantic", "Field"),
          discriminator.pythonIdentifierName,
        )
      } else {
        PythonCodeBlock.of(
          "type %L = %T[%C, %T(discriminator=%S)]",
          name.pythonTypeName,
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
      name.pythonTypeName,
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
    val propertyBlocks =
      exposedProperties.map { property ->
        val wireName = property.serializationName ?: property.name
        val propertyType = property.type.renderPythonType(nullable = !property.required || property.type.nullable)
        val rawValue = PythonCodeBlock.of("self.root.get(%S)", wireName)
        PythonCodeBlock.of(
          "    @property\n" +
            "    def %L(self) -> %C:\n" +
            "        return %T(%C).validate_python(%C)",
          property.name.pythonIdentifierName,
          propertyType,
          PythonSymbol("pydantic", "TypeAdapter"),
          propertyType,
          rawValue,
        )
      }
    val validationReads =
      exposedProperties
        .joinToString("\n") { property ->
          "        _ = self.${property.name.pythonIdentifierName}"
        }.ifBlank { "        pass" }
    val propertySection =
      propertyBlocks
        .takeIf { blocks -> blocks.isNotEmpty() }
        ?.let { blocks -> PythonCodeBlock.of("%C\n\n", PythonCodeBlock.join(blocks, separator = "\n\n")) }
        ?: PythonCodeBlock.of("")

    return PythonCodeBlock.of(
      """
      class %L(%T[dict[str, %T]]):
      %C    @%T(mode="after")
          def _validate_declared_properties(self) -> %T:
      %L
              return self

          @property
          def raw_body(self) -> dict[str, %T]:
              return self.root
      """.trimIndent(),
      modelName.pythonTypeName,
      PythonSymbol("pydantic", "RootModel"),
      PythonSymbol("typing", "Any"),
      propertySection,
      PythonSymbol("pydantic", "model_validator"),
      PythonSymbol("typing", "Self"),
      validationReads,
      PythonSymbol("typing", "Any"),
    )
  }

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

    if (externalProperties.isEmpty()) {
      return null
    }

    return PythonCodeBlock.of(
      """
          @%T(mode="before")
          @classmethod
          def _validate_external_discriminators(cls, data: object) -> object:
              if not isinstance(data, dict):
                  return data
      %C
              return data
      """.trimIndent(),
      PythonSymbol("pydantic", "model_validator"),
      PythonCodeBlock.join(
        externalProperties.map { property -> property.renderExternalDiscriminatorMapping() },
        separator = "\n",
      ),
    )
  }

  private fun GeneratedModelProperty.renderExternalDiscriminatorMapping(): PythonCodeBlock {
    val discriminatorName = externalDiscriminator ?: error("External discriminator is required")
    val mappedValues = modelIndex[type.name]?.discriminatorMappings.orEmpty()
    val mappings =
      mappedValues
        .map { (value, mappedType) ->
          PythonCodeBlock.of(
            """
            |        if data.get(%S) == %S:
            |            data = dict(data)
            |            data[%S] = %T(%C).validate_python(data.get(%S))
            """.trimMargin(),
            discriminatorName,
            value,
            serializationName ?: name,
            PythonSymbol("pydantic", "TypeAdapter"),
            mappedType.renderPythonType(nullable = false),
            serializationName ?: name,
          )
        }

    val fallbackMapping =
      discriminatorFallbacks[type.name]?.let { fallback ->
        val values = mappedValues.keys.sorted().joinToString(", ") { value -> value.pythonStringLiteral() }
        PythonCodeBlock.of(
          """
          |        if isinstance(data.get(%S), str) and data.get(%S) not in {%L}:
          |            data = dict(data)
          |            data[%S] = %T(%L).validate_python(data.get(%S))
          """.trimMargin(),
          discriminatorName,
          discriminatorName,
          values,
          serializationName ?: name,
          PythonSymbol("pydantic", "TypeAdapter"),
          fallback.modelName.pythonTypeName,
          serializationName ?: name,
        )
      }

    return PythonCodeBlock.join(mappings + listOfNotNull(fallbackMapping), separator = "\n")
  }

  private fun GeneratedModelProperty.renderProperty(model: GeneratedModel): PythonCodeBlock {
    val propertyName = name.pythonIdentifierName
    val literalValue = discriminatorLiteralValue(model)
    val externalDiscriminatorType = renderExternalDiscriminatorPropertyType()
    val basePropertyType =
      literalValue?.renderPythonLiteralType()
        ?: externalDiscriminatorType
        ?: type.renderPythonType(nullable = false)
    val propertyType =
      if (required && !type.nullable) {
        basePropertyType
      } else {
        PythonCodeBlock.of("%C | None", basePropertyType)
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
    } else {
      aliases
    }

  private fun GeneratedModel.effectiveModelProperties(): List<GeneratedModelProperty> {
    val inheritedProperties =
      inherits
        .mapNotNull { inherited -> modelIndex[inherited.name] }
        .flatMap { model -> model.effectiveModelProperties() }
    val overrideNames =
      properties
        .map { property -> property.serializationName ?: property.name }
        .toSet()
    return inheritedProperties
      .filterNot { property ->
        (property.serializationName ?: property.name) in overrideNames
      } + properties
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
}
