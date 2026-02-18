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
import amf.core.client.platform.model.domain.Linkable
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.RecursiveShape
import amf.core.client.platform.model.domain.Shape
import amf.core.client.scala.errorhandling.DefaultErrorHandler
import amf.core.internal.annotations.DeclaredElement
import amf.core.internal.annotations.InheritedShapes
import amf.core.internal.annotations.ResolvedLinkTargetAnnotation
import amf.shapes.client.platform.model.domain.NodeShape
import io.outfoxx.sunday.generator.utils.annotations
import io.outfoxx.sunday.generator.utils.id
import io.outfoxx.sunday.generator.utils.inherits
import io.outfoxx.sunday.generator.utils.linkTarget
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.uniqueId
import scala.Option
import scala.collection.JavaConverters
import scala.collection.Seq
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

  private val referenceMapById = referenceMap.values.associateBy { it.id }
  private val referenceMapByName =
    referenceMap.values
      .mapNotNull { shape -> shape.name?.let { it to shape } }
      .groupBy({ it.first }, { it.second })

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
        DefaultErrorHandler(),
      )
      return this
    }

    fun build() = ShapeIndex(referenceMap)
  }

  fun hasInherited(shape: Shape): Boolean = findInherited(shape).isNotEmpty()

  fun hasNoInherited(shape: Shape): Boolean = findInherited(shape).isEmpty()

  fun findSuperShapeOrNull(shape: Shape): Shape? = findInherited(shape).firstOrNull()

  fun findInherited(shape: Shape): List<Shape> = resolve(shape).inherits.map { resolve(it) }

  fun hasInheriting(shape: Shape): Boolean = findInheriting(shape).isNotEmpty()

  fun hasNoInheriting(shape: Shape): Boolean = findInheriting(shape).isEmpty()

  fun findInheriting(shape: Shape): List<Shape> {
    val opt = resolve(shape).annotations._internal().find(Inheriting::class.java)
    return if (opt.isDefined) {
      opt.get().shapes.map { resolve(it) }
    } else {
      listOf()
    }
  }

  fun findOrderedProperties(shape: NodeShape): List<PropertyShape> {
    val resolved = resolveAs(shape)
    val properties = resolved.nonPatternProperties
    if (hasNoInherited(shape)) {
      return properties
    }
    return properties.filter {
      val provenance = it.annotations.inheritanceProvenance().getOrNull()
      provenance == shape.uniqueId ||
        provenance == shape.id ||
        provenance == resolved.uniqueId ||
        provenance == resolved.id
    }
  }

  private inline fun <reified T : Shape> resolveAs(shape: T): T = resolve(shape) as T

  fun resolve(shape: Shape): Shape {
    val linkTargetShape = (shape as? Linkable)?.linkTarget as? Shape
    if (linkTargetShape != null) {
      return resolve(linkTargetShape)
    }

    return if (shape.hasExplicitName() || shape._internal().annotations().contains(DeclaredElement::class.java)) {
      referenceMap[shape.uniqueId]
        ?: referenceMapById[shape.id]
        ?: shape
    } else {
      val resolvedLinkTargetId = shape.resolvedLinkTargetIds().firstOrNull()
      if (resolvedLinkTargetId != null) {
        referenceMapById[resolvedLinkTargetId]
          ?: referenceMap[resolvedLinkTargetId]
          ?: shape
      } else {
        val inheritedIds = shape.inheritedShapeIds()
        val inheritedIdMatch =
          inheritedIds.firstNotNullOfOrNull { id ->
            referenceMapById[id]
              ?: referenceMap[id]
              ?: run {
                val name = id.substringAfterLast('/')
                referenceMapByName[name]?.singleOrNull()
              }
          }

        val nameMatch =
          shape.name?.let { name ->
            val candidates = referenceMapByName[name].orEmpty()
            val baseId = shape.id.substringBefore("#")
            candidates.firstOrNull { it.id.substringBefore("#") == baseId }
              ?: candidates.singleOrNull()
          }

        val inheritedResolved =
          shape.inherits
            .firstOrNull()
            ?.takeIf { it != shape }
            ?.let { resolve(it) }

        inheritedIdMatch
          ?: nameMatch
          ?: inheritedResolved
          ?: referenceMapById[shape.id]
          ?: referenceMap[shape.uniqueId]
          ?: shape
      }
    }
  }
}

private fun <T> Seq<T>.asList(): List<T> = JavaConverters.seqAsJavaList(this)

private fun Shape.resolvedLinkTargetIds(): List<String> =
  _internal()
    .annotations()
    .serializables()
    .asList()
    .filterIsInstance<ResolvedLinkTargetAnnotation>()
    .map { it.linkTargetId() }

private fun Shape.inheritedShapeIds(): List<String> =
  _internal()
    .annotations()
    .find(InheritedShapes::class.java)
    .let { opt ->
      if (opt.isDefined) {
        opt.get().baseIds().asList()
      } else {
        emptyList()
      }
    }
