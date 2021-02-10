package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.BaseUnit
import amf.client.model.domain.PropertyShape
import com.squareup.kotlinpoet.ClassName

data class KotlinResolutionContext(
  val unit: BaseUnit,
  val suggestedTypeName: ClassName?,
  val property: PropertyShape? = null
) {

  fun copy(suggestedTypeName: ClassName? = null, property: PropertyShape? = null): KotlinResolutionContext {
    return KotlinResolutionContext(unit, suggestedTypeName, property)
  }

}
