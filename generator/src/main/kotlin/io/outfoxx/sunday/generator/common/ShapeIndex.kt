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
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.RecursiveShape
import amf.core.client.platform.model.domain.Shape
import amf.core.client.scala.errorhandling.DefaultErrorHandler
import amf.core.internal.annotations.DeclaredElement
import amf.shapes.client.platform.model.domain.NodeShape
import io.outfoxx.sunday.generator.utils.annotations
import io.outfoxx.sunday.generator.utils.inherits
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.uniqueId
import scala.Option
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.filter
import kotlin.collections.firstOrNull
import kotlin.collections.isNotEmpty
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.jvm.optionals.getOrNull
import amf.core.client.scala.model.domain.Shape as InternalShape

class ShapeIndex(
  private val referenceMap: Map<String, Shape>,
) {

  companion object {

    fun builder() = Builder()
  }

  class Builder {

    private val referenceMap = mutableMapOf<String, Shape>()

    fun index(baseUnit: BaseUnit): Builder {
      baseUnit._internal().transform(
        { it is InternalShape },
        { internalShape, _ ->
          val shape = baseUnit.findById(internalShape.id()).get() as Shape
          if (shape is RecursiveShape) {
            val internalTarget = shape._internal().fixpointTarget().get()
            val clientTarget = baseUnit.findById(internalTarget.id()).get() as Shape
            referenceMap[shape.uniqueId] = clientTarget
          } else if (shape.hasExplicitName() || shape._internal().annotations().contains(DeclaredElement::class.java)) {
            referenceMap[shape.uniqueId] = shape
          }
          Option.apply(internalShape)
        },
        DefaultErrorHandler()
      )
      return this
    }

    fun build() = ShapeIndex(referenceMap)
  }

  fun hasInherited(shape: Shape): Boolean =
    findInherited(shape).isNotEmpty()

  fun hasNoInherited(shape: Shape): Boolean =
    findInherited(shape).isEmpty()

  fun findSuperShapeOrNull(shape: Shape): Shape? =
    findInherited(shape).firstOrNull()

  fun findInherited(shape: Shape): List<Shape> =
    resolve(shape).inherits.map { resolve(it) }

  fun hasInheriting(shape: Shape): Boolean =
    findInheriting(shape).isNotEmpty()

  fun hasNoInheriting(shape: Shape): Boolean =
    findInheriting(shape).isEmpty()

  fun findInheriting(shape: Shape): List<Shape> {
    val opt = resolve(shape).annotations._internal().find(Inheriting::class.java)
    return if (opt.isDefined) {
      opt.get().shapes.map { resolve(it) }
    } else {
      listOf()
    }
  }

  fun findOrderedProperties(shape: NodeShape): List<PropertyShape> =
    resolveAs(shape).nonPatternProperties
      .filter { it.annotations.inheritanceProvenance().getOrNull() == shape.uniqueId }

  private inline fun <reified T : Shape> resolveAs(shape: T): T =
    resolve(shape) as T

  fun resolve(shape: Shape): Shape =
    if (shape.hasExplicitName() || shape._internal().annotations().contains(DeclaredElement::class.java)) {
      referenceMap[shape.uniqueId]
        ?: shape
    } else {
      shape.inherits.firstOrNull()
        ?.let { referenceMap[it.uniqueId] }
        ?: referenceMap[shape.uniqueId]
        ?: shape
    }
}
