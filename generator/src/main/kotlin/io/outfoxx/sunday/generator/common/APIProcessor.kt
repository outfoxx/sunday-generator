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

import amf.apicontract.client.platform.RAMLConfiguration
import amf.core.client.common.transform.PipelineId
import amf.core.client.common.validation.SeverityLevels
import amf.core.client.platform.AMFGraphConfiguration
import amf.core.client.platform.errorhandling.ClientErrorHandler
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.domain.DomainElement
import amf.core.client.platform.model.domain.RecursiveShape
import amf.core.client.platform.model.domain.Shape
import amf.core.client.platform.transform.TransformationPipelineBuilder
import amf.core.client.platform.transform.TransformationStep
import amf.core.client.platform.validation.AMFValidationResult
import amf.core.client.scala.errorhandling.DefaultErrorHandler
import amf.core.client.scala.model.document.FieldsFilter
import amf.core.client.scala.traversal.iterator.`DomainElementStrategy$`
import amf.core.client.scala.traversal.iterator.IdCollector
import amf.core.internal.annotations.DeclaredElement
import amf.core.internal.annotations.InheritanceProvenance
import amf.core.internal.annotations.InheritedShapes
import amf.core.internal.annotations.ResolvedLinkTargetAnnotation
import amf.core.internal.metamodel.domain.`DomainElementModel$`
import amf.core.internal.metamodel.domain.`ShapeModel$`
import amf.shapes.client.platform.model.domain.NodeShape
import io.outfoxx.sunday.generator.utils.*
import scala.Option
import scala.collection.JavaConverters.*
import scala.collection.Seq
import java.net.URI
import java.util.concurrent.ExecutionException
import kotlin.collections.set
import amf.core.client.scala.model.document.BaseUnit as InternalBaseUnit
import amf.core.client.scala.model.domain.Annotation as InternalAnnotation
import amf.core.client.scala.model.domain.DomainElement as InternalDomainElement
import amf.core.client.scala.model.domain.NamedDomainElement as InternalNamedDomainElement
import amf.core.client.scala.model.domain.extensions.PropertyShape as InternalPropertyShape
import amf.shapes.client.scala.model.domain.NodeShape as InternalNodeShape
import scala.collection.mutable.`Set$` as MutableScalaSet

open class APIProcessor {

  data class Result(
    val document: Document,
    val shapeIndex: ShapeIndex,
    private val validationResults: List<AMFValidationResult>,
  ) {

    enum class Level {
      Error,
      Warning,
      Info,
    }

    data class Entry(
      val level: Level,
      val file: String,
      val line: Int,
      val colum: Int,
      val message: String,
    )

    val isValid: Boolean
      get() = validationResults.none { it.severityLevel() == SeverityLevels.VIOLATION() }

    val validationLog: List<Entry>
      get() = validationResults.map {
        val level =
          when (it.severityLevel()) {
            SeverityLevels.VIOLATION() -> Level.Error
            SeverityLevels.WARNING() -> Level.Warning
            SeverityLevels.INFO() -> Level.Info
            else -> Level.Error
          }
        val file = it.location()?.orElse("unknown")!!
        val line = it.position().start().line()
        val column = it.position().start().column()
        val message = it.message()
        Entry(level, file, line, column, message)
      }
  }

  open fun process(uri: URI): Result {

    val baseConfig = RAMLConfiguration.RAML10()

    val elementIds = mutableMapOf<String, InternalDomainElement>()

    val pipelineId = PipelineId.Cache()

    val pipeline =
      TransformationPipelineBuilder
        .fromPipeline(pipelineId, baseConfig)
        .get()
        .prepend(IndexElementsStep(elementIds))
        .append(InheritanceTransformationStep(elementIds))
        .build()

    val ramlClient =
      baseConfig
        .withResourceLoader(LocalSundayDefinitionResourceLoader)
        .withTransformationPipeline(pipeline)
        .baseUnitClient()

    val (unresolvedDocument, validationResults) =
      try {
        val result = ramlClient.parseDocument(uri.toString()).get()
        result.document() to result.results()
      } catch (x: ExecutionException) {
        throw x.cause ?: x
      }

    val resolvedDocument = ramlClient.transform(unresolvedDocument, pipelineId).baseUnit() as Document

    val validationReport = ramlClient.validate(resolvedDocument).get()

    val shapeIndex = ShapeIndex.builder().index(resolvedDocument).build()

    return Result(resolvedDocument, shapeIndex, validationResults + validationReport.results())
  }
}

class Inheriting(
  val shapes: List<Shape>,
) : InternalAnnotation

class IndexElementsStep(
  private val internalElementIds: MutableMap<String, InternalDomainElement>,
) : TransformationStep {

  override fun transform(
    model: BaseUnit,
    errorHandler: ClientErrorHandler,
    configuration: AMFGraphConfiguration,
  ): BaseUnit {
    model.allUnits
      .flatMap { it.findByType(`DomainElementModel$`.`MODULE$`.typeIris().head()) }
      .forEach { shape ->
        val internal = shape._internal()
        internalElementIds[internal.id()] = internal
        internalElementIds[internal.sourceId()] = internal
        internalElementIds[internal.uniqueId()] = internal
      }
    return model
  }
}

class InheritanceTransformationStep(
  private val originalElements: Map<String, InternalDomainElement>,
) : TransformationStep {

  private val clientElements = mutableMapOf<String, DomainElement>()
  private val internalElements = mutableMapOf<String, InternalDomainElement>()
  private val internalDeclared = mutableMapOf<String, InternalDomainElement>()
  private val clientDeclared = mutableMapOf<String, DomainElement>()
  private val resolvedInheriting = mutableMapOf<String, MutableSet<String>>()

  private fun index(element: DomainElement) {
    val internal = element._internal()
    val ids = listOf(element.id(), internal.sourceId(), internal.uniqueId())
    for (id in ids) {
      internalElements[id] = internal
      clientElements[id] = element
      if (internal.annotations().contains(DeclaredElement::class.java)) {
        internalDeclared[id] = internal
        clientDeclared[id] = element
      }
      internal.resolvedLinkTargets().forEach {
        internalElements[it] = internal
        clientElements[it] = element
      }
    }
  }

  private fun findOriginal(element: InternalDomainElement): InternalDomainElement {
    return originalElements[element.id()]
      ?: originalElements[element.sourceId()]
      ?: originalElements[element.uniqueId()]
      ?: error("Original element not found: ${element.id()}")
  }

  override fun transform(
    model: BaseUnit,
    errorHandler: ClientErrorHandler,
    configuration: AMFGraphConfiguration,
  ): BaseUnit {
    model.allUnits
      .flatMap { it.findByType(`DomainElementModel$`.`MODULE$`.typeIris().asList.first()) }
      .forEach { element ->
        if (element !is RecursiveShape) {
          index(element)
          if (element is NodeShape) {
            element.properties().forEach { index(it.range()) }
          }
        }
      }

    model._internal().transform(
      { it is InternalNodeShape },
      { shape, _ ->
        var updated = shape as InternalNodeShape
        updated = fixInheritance(updated)
        for (property in updated.properties().asList) {
          val rangeNode = property.range() as? InternalNodeShape ?: continue
          fixInheritance(rangeNode)
        }
        Option.apply(updated)
      },
      DefaultErrorHandler(),
    )

    model._internal().transform(
      { it is InternalNodeShape },
      { shape, _ ->
        var updated = shape as InternalNodeShape
        updated = fixInheriting(updated)
        Option.apply(updated)
      },
      DefaultErrorHandler(),
    )
    return model
  }

  private fun fixInheritance(node: InternalNodeShape): InternalNodeShape {
    val inheritsViaAnnotation =
      node.annotations().find(InheritedShapes::class.java).value?.uris()?.asList
        ?.mapNotNull { internalElements[it] as? InternalNodeShape }
        ?: listOf()
    node.withInherits(inheritsViaAnnotation.asScalaSeq)

    val inheritsViaProperties = fixPropertyInheritance(node)

    val finalInheriting =
      (inheritsViaProperties + inheritsViaAnnotation)
        .distinctBy { it.sourceId() }
        .filter { it.sourceId() != node.sourceId() }
    for (inherited in finalInheriting) {
      resolvedInheriting.getOrPut(inherited.sourceId()) { mutableSetOf() } += node.sourceId()
    }

    return node
  }

  private fun fixPropertyInheritance(node: InternalNodeShape): List<InternalNodeShape> {
    val properties = node.properties().asList
    val originalPropertyNames = resolveOriginalPropertyNames(node)

    val inherits = mutableListOf<InternalNodeShape>()
    val orderedProperties = mutableListOf<InternalPropertyShape>()
    // Add inherited properties first
    for (property in properties.filter { it.name().value() !in originalPropertyNames }) {
      orderedProperties.add(property)
      property.annotations().inheritanceProvenance().value
        ?.let { provenance ->
          val inherited =
            internalElements[provenance] as? InternalNodeShape
              ?: error("Provenance element not found: $provenance")
          inherits.add(inherited)
          if (inherited.uniqueId() != provenance) {
            property.annotations()
              .reject { it is InheritanceProvenance }
              .`$plus$eq`(InheritanceProvenance(node.uniqueId()))
          }
        }
        ?: error("Inherited property missing provenance: ${property.id()}")
    }
    for (propertyName in originalPropertyNames) {
      val found = properties.first { it.name().value() == propertyName }
      found.annotations()
        .reject { it is InheritanceProvenance }
        .`$plus$eq`(InheritanceProvenance(node.uniqueId()))
      orderedProperties.add(found)
    }
    node.withProperties(orderedProperties.asScalaSeq)
    return inherits.toList()
  }

  private fun resolveOriginalPropertyNames(node: amf.shapes.client.scala.model.domain.NodeShape): List<String> {
    val originalElement = findOriginal(node)
    val originalShape =
      originalElement as? InternalNodeShape
        ?: error("Original element not a NodeShape: ${originalElement.id()}")
    val originalPropertyNames =
      originalShape.properties().asList
        .map { it.name().value() }
    return originalPropertyNames
  }

  private fun fixInheriting(shape: InternalNodeShape): InternalNodeShape {
    val resolved = resolvedInheriting[shape.sourceId()] ?: return shape
    if (resolved.isNotEmpty()) {
      val inheriting =
        resolved
          .mapNotNull { inherit ->
            val node =
            when (val found = clientDeclared[inherit]) {
              null -> null
              is NodeShape -> found
              is RecursiveShape -> clientElements[found.fixpoint().value] as NodeShape?
              else -> error("Inheriting shape not a NodeShape: $inherit")
            }
            if (node != null && node.inherits().any { it.name == shape.name().value() })
              node
            else
              null
          }
      shape.annotations().`$plus$eq`(Inheriting(inheriting))
    }
    return shape
  }
}

inline fun <reified T> InternalBaseUnit.filterIsInstance(): Sequence<T> {
  return asJavaIterator(
    this.iterator(
      `DomainElementStrategy$`.`MODULE$`,
      FieldsFilter.`All$`.`MODULE$`,
      IdCollector(MutableScalaSet.`MODULE$`.empty()),
    ),
  )
    .asSequence()
    .filterIsInstance<T>()
}

private val <T> Option<T>.value: T?
  get() = if (isDefined) get() else null

private val <T> Seq<T>.asList: List<T>
  get() = seqAsJavaList(this)

private val <T> Seq<T>.asSequence: Sequence<T>
  get() = asJavaIterable(this).asSequence()

private val <T> Iterable<T>.asScalaSeq: Seq<T>
  get() = asScalaIterator(iterator()).toSeq()

private fun InternalDomainElement.resolvedLinkTargets(): List<String> =
  annotations().serializables().asList.filterIsInstance<ResolvedLinkTargetAnnotation>()
    .map { it.linkTargetId() }

private fun InternalDomainElement.sourceId(): String =
  annotations().sourceLocation().toString()

private fun InternalDomainElement.uniqueIdLocation(): String? =
  fields()[`ShapeModel$`.`MODULE$`.Name()]
    ?.let { name ->
      name.location().value?.ifBlank { null }
    }
    ?: annotations().location().value

private fun InternalDomainElement.uniqueId(): String =
  if (this is InternalNamedDomainElement) {
    uniqueIdLocation()
      ?.let { location ->
        annotations().fragmentName().value
          ?.let { fragmentName ->
            "$location#$fragmentName/${name().value()}"
          }
          ?: "$location/${name().value()}"
      }
      ?: id()
  } else {
    id()
  }
