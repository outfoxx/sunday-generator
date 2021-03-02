package io.outfoxx.sunday.generator.typescript

import amf.client.model.document.BaseUnit
import io.outfoxx.typescriptpoet.TypeName

data class TypeScriptResolutionContext(
  val unit: BaseUnit,
  val suggestedTypeName: TypeName.Standard?,
) {

  fun copy(suggestedTypeName: TypeName.Standard? = null): TypeScriptResolutionContext {
    return TypeScriptResolutionContext(unit, suggestedTypeName)
  }

}
