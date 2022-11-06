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
import amf.core.client.platform.model.domain.Shape
import amf.shapes.client.platform.model.domain.NodeShape
import io.outfoxx.sunday.generator.utils.ShapeVisitor
import io.outfoxx.sunday.generator.utils.id
import io.outfoxx.sunday.generator.utils.inherits
import io.outfoxx.sunday.generator.utils.linkTarget
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.properties
import io.outfoxx.sunday.generator.utils.range

class ShapeIndex(
  private val inheritedMap: Map<String, Set<String>>,
  private val inheritingMap: Map<String, Set<String>>,
  private val orderOfProperties: Map<String, List<String>>,
  private val referenceMap: Map<String, String>,
) {

  companion object {

    fun builder() = Builder()
  }

  class Builder : ShapeVisitor() {

    private val inheritedMap = mutableMapOf<String, MutableSet<String>>()
    private val inheritingMap = mutableMapOf<String, MutableSet<String>>()
    private val orderOfProperties = mutableMapOf<String, List<String>>()
    private val referenceMap = mutableMapOf<String, String>()

    fun index(baseUnit: BaseUnit): Builder {
      visit(baseUnit)
      return this
    }

    fun build() = ShapeIndex(inheritedMap, inheritingMap, orderOfProperties, referenceMap)

    override fun visit(shape: Shape?) {
      add(shape)
    }

    private fun add(shape: Shape?) {
      shape ?: return

      // Mark and ignore soft links...
      if (
        !shape.hasExplicitName() &&
        shape.inherits.size == 1 &&
        (shape !is NodeShape || shape.properties.isEmpty())
      ) {
        storeReference(shape, dereference(shape.inherits.single()))
        return
      }

      val shapeId = shape.id

      if (shape is NodeShape) {
        orderOfProperties[shapeId] = shape.nonPatternProperties.mapNotNull { it.name }

        shape.properties.forEach { add(dereference(it.range)) }
      }

      inheritedMap.getOrPut(shapeId) { mutableSetOf() }
      inheritingMap.getOrPut(shapeId) { mutableSetOf() }

      shape.inherits().forEach { inheritedNode ->
        val inheritedNodeId = dereference(inheritedNode).id
        inheritedMap.getOrPut(shapeId) { mutableSetOf() }.add(inheritedNodeId)
        inheritingMap.getOrPut(inheritedNodeId) { mutableSetOf() }.add(shapeId)
      }
    }

    private fun dereference(shape: Shape): Shape =
      when {
        shape.linkTarget != null -> dereference(storeReference(shape, shape.linkTarget as Shape))

        else -> shape
      }

    private fun storeReference(source: Shape, target: Shape): Shape {
      referenceMap[source.id] = target.id
      return target
    }
  }

  fun hasInherited(shape: Shape): Boolean {
    return inheritedMap[resolveIfReference(shape)]?.isNotEmpty() ?: false
  }

  fun hasNoInherited(shape: Shape): Boolean {
    return !hasInherited(shape)
  }

  fun findInheritedIds(shape: Shape): Set<String> {
    return inheritedMap[resolveIfReference(shape)] ?: setOf()
  }

  fun findSuperShapeIdOrNull(shape: Shape): String? {
    return findInheritedIds(shape).firstOrNull()
  }

  fun hasInheriting(shape: Shape): Boolean {
    return inheritingMap[resolveIfReference(shape)]?.isNotEmpty() ?: false
  }

  fun hasNoInheriting(shape: Shape): Boolean {
    return !hasInheriting(shape)
  }

  fun findInheritingIds(shape: Shape): Set<String> {
    return inheritingMap[resolveIfReference(shape)] ?: setOf()
  }

  fun findOrderOrProperties(shape: NodeShape): List<String> {
    return orderOfProperties[resolveIfReference(shape)] ?: emptyList()
  }

  fun findReferenceTargetId(shape: Shape): String? {
    return referenceMap[shape.id]
  }

  fun resolveIfReference(shape: Shape): String {
    return findReferenceTargetId(shape) ?: shape.id
  }
}
