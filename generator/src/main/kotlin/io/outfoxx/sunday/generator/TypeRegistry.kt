package io.outfoxx.sunday.generator

import amf.client.model.document.BaseUnit
import amf.client.model.document.DeclaresModel
import amf.client.model.domain.DomainElement
import amf.client.model.domain.NamedDomainElement
import amf.client.model.domain.NodeShape
import amf.client.model.domain.PropertyShape
import amf.core.annotations.Aliases
import scala.collection.JavaConverters

abstract class TypeRegistry {

  data class ResolutionContext<TYPENAME>(
    val unit: BaseUnit,
    val suggestedTypeName: TYPENAME?,
    val property: PropertyShape? = null
  ) {

    fun copy(suggestedTypeName: TYPENAME? = null, property: PropertyShape? = null): ResolutionContext<TYPENAME> {
      return ResolutionContext(unit, suggestedTypeName, property)
    }

  }

  companion object {

    private val DECL_REGEX = """(?:([^.]+)\.)?([\w-_.]+)""".toRegex()
  }

}
