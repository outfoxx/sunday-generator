/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator.common

import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.DeclaresModel
import amf.core.client.platform.model.document.ExternalFragment
import amf.core.client.platform.model.domain.DomainElement
import amf.core.client.platform.model.domain.NamedDomainElement
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.Shape
import amf.core.internal.annotations.Aliases
import amf.shapes.client.platform.model.domain.NodeShape
import io.outfoxx.sunday.generator.utils.*
import scala.collection.JavaConverters

interface ResolutionContext {

  val unit: BaseUnit
  val shapeIndex: ShapeIndex

  fun hasInherited(shape: Shape): Boolean = shapeIndex.hasInherited(shape.nonNullableType)

  fun hasNoInherited(shape: Shape): Boolean = shapeIndex.hasNoInherited(shape.nonNullableType)

  fun hasNoInheriting(shape: Shape): Boolean = shapeIndex.hasNoInheriting(shape.nonNullableType)

  fun findRootShape(shape: Shape): Shape = findSuperShapeOrNull(shape.nonNullableType)?.let(this::findRootShape) ?: shape

  fun findSuperShapeOrNull(shape: Shape): Shape? {
    return shapeIndex.findSuperShapeOrNull(shape)
  }

  fun findInheritingShapes(shape: Shape): List<Shape> {
    return shapeIndex.findInheriting(shape.nonNullableType)
  }

  fun findProperties(shape: NodeShape): List<PropertyShape> =
    shapeIndex.findOrderedProperties(shape)

  fun findAllProperties(shape: NodeShape): List<PropertyShape> {
    val superShape = findSuperShapeOrNull(shape) as NodeShape?
    val superProperties = superShape?.let(this::findAllProperties) ?: emptyList()
    return superProperties + findProperties(shape)
  }

  fun findDeclaringUnit(element: DomainElement) =
    unit.allUnits.first { it.location == element.annotations.location }

  fun findImportingUnit(element: DomainElement, allUnits: Set<BaseUnit>): BaseUnit? {
    if (this !is ExternalFragment) return null

    val importingUnitLocation = element.id.split("#", limit = 2).first()
    return allUnits.find { it.location == importingUnitLocation }
  }

  fun resolveRef(name: String, source: DomainElement): Pair<DomainElement, BaseUnit>? {
    val sourceUnit = findDeclaringUnit(source)
    return sourceUnit.resolveRef(name)
      ?: findImportingUnit(source, unit.allUnits)?.resolveRef(name)
  }

  fun dereference(shape: Shape): Shape =
    shapeIndex.resolve(shape)
}

private val ELEMENT_REF_REGEX = """(?:([^.]+)\.)?([\w-_.]+)""".toRegex()

private fun BaseUnit.resolveRef(ref: String): Pair<DomainElement, BaseUnit>? {

  val (libraryName, declarationName) =
    ELEMENT_REF_REGEX.matchEntire(ref)?.destructured
      ?: error("Invalid reference '$ref'")

  val declarationUnit =
    if (libraryName.isBlank()) {

      this as? DeclaresModel
    } else {

      val aliases: Aliases =
        annotations._internal().find(Aliases::class.java).getOrElse(null)
          ?: error("Unable to find unit aliases")

      val unitUrl = JavaConverters.setAsJavaSet(aliases.aliases()).first { it._1 == libraryName }?._2!!.fullUrl()

      allUnits.find { it.location == unitUrl } as? DeclaresModel
    } ?: return null

  val decl = declarationUnit.declares
    .filterIsInstance<NamedDomainElement>()
    .firstOrNull { it.name == declarationName } as? DomainElement
    ?: return null

  return decl to (declarationUnit as BaseUnit)
}
