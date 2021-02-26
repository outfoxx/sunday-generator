package io.outfoxx.sunday.generator.typescript

import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.ANY
import io.outfoxx.typescriptpoet.TypeName.Companion.BIGINT
import io.outfoxx.typescriptpoet.TypeName.Companion.BIGINT_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.NULL
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.SYMBOL
import io.outfoxx.typescriptpoet.TypeName.Companion.SYMBOL_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.UNDEFINED

fun TypeName.box() = when (this) {
  BOOLEAN -> BOOLEAN_CLASS
  NUMBER -> NUMBER_CLASS
  BIGINT -> BIGINT_CLASS
  STRING -> STRING_CLASS
  OBJECT -> OBJECT_CLASS
  SYMBOL -> SYMBOL_CLASS
  ANY -> OBJECT_CLASS
  else -> this
}

fun TypeName.typeInitializer(): CodeBlock = CodeBlock.of("[%L]", internalTypeInitializer())

fun TypeName.internalTypeInitializer(): CodeBlock =
  when (this) {
    is TypeName.Parameterized ->
      CodeBlock.of(
        "%T, [%L]",
        this.rawType,
        this.typeArgs.map { it.internalTypeInitializer() }.joinToCode()
      )

    is TypeName.Union ->
      CodeBlock.of("%T", if (isOptionalUnion) nonOptional.box() else OBJECT) // proper unions are not currently supported

    else -> CodeBlock.of("%T", this.box())
  }



val TypeName.isOptional: Boolean
  get() = when (this) {
    is TypeName.Union -> typeChoices.any { it == NULL || it == UNDEFINED }
    else -> false
  }

val TypeName.Union.isOptionalUnion: Boolean
  get() = when (typeChoices.size) {
    3 -> typeChoices.contains(NULL) && typeChoices.contains(UNDEFINED)
    2 -> typeChoices.any { it == NULL || it == UNDEFINED }
    else -> false
  }

val TypeName.nonOptional: TypeName
  get() = when {
    this is TypeName.Union && isOptionalUnion -> typeChoices.first { it != NULL && it != UNDEFINED }
    this is TypeName.Union -> TypeName.unionType(*typeChoices.filter { it != NULL && it != UNDEFINED }.toTypedArray())
    else -> this
  }




val TypeName.undefinable: TypeName
  get() = when (this) {
    is TypeName.Union -> TypeName.unionType(*typeChoices.plus(UNDEFINED).toSet().toTypedArray())
    else -> TypeName.unionType(this, UNDEFINED)
  }

val TypeName.isUndefinable: Boolean
  get() = when (this) {
    is TypeName.Union -> typeChoices.contains(UNDEFINED)
    else -> false
  }

val TypeName.Union.isUndefinableUnion: Boolean
  get() = typeChoices.contains(UNDEFINED) && typeChoices.size == 2

val TypeName.nonUndefinable: TypeName
  get() = when {
    this is TypeName.Union && isUndefinableUnion -> typeChoices.first { it != UNDEFINED }
    this is TypeName.Union -> TypeName.unionType(*typeChoices.filter { it != UNDEFINED }.toTypedArray())
    else -> this
  }




val TypeName.nullable: TypeName
  get() = when (this) {
    is TypeName.Union -> TypeName.unionType(*typeChoices.plus(NULL).toSet().toTypedArray())
    else -> TypeName.unionType(this, NULL)
  }

val TypeName.isNullable: Boolean
  get() = when (this) {
    is TypeName.Union -> typeChoices.contains(NULL)
    else -> false
  }

val TypeName.Union.isNullableUnion: Boolean
  get() = typeChoices.contains(NULL) && typeChoices.size == 2

val TypeName.nonNullable: TypeName
  get() = when {
    this is TypeName.Union && isNullableUnion -> typeChoices.first { it != NULL }
    this is TypeName.Union -> TypeName.unionType(*typeChoices.filter { it != NULL }.toTypedArray())
    else -> this
  }
