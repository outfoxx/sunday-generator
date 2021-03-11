package io.outfoxx.sunday.generator.swift

import amf.client.model.document.BaseUnit
import io.outfoxx.swiftpoet.DeclaredTypeName

data class SwiftResolutionContext(
  val unit: BaseUnit,
  val suggestedTypeName: DeclaredTypeName?
) {

  fun copy(suggestedTypeName: DeclaredTypeName? = null): SwiftResolutionContext {
    return SwiftResolutionContext(unit, suggestedTypeName)
  }

}
