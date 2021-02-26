package io.outfoxx.sunday.generator.typescript

import amf.client.model.document.BaseUnit
import amf.client.model.domain.PropertyShape
import io.outfoxx.typescriptpoet.TypeName

data class TypeScriptResolutionContext(
  val unit: BaseUnit,
  val suggestedTypeName: TypeName.Standard?,
  val property: PropertyShape? = null
) {

  fun copy(
    suggestedTypeName: TypeName.Standard? = null,
    property: PropertyShape? = null
  ): TypeScriptResolutionContext {
    return TypeScriptResolutionContext(unit, suggestedTypeName, property)
  }

}
