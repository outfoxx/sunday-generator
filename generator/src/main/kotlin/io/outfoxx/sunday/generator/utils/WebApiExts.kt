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

@file:Suppress("unused")

package io.outfoxx.sunday.generator.utils

import amf.client.model.Annotable
import amf.client.model.Annotations
import amf.client.model.BoolField
import amf.client.model.DoubleField
import amf.client.model.IntField
import amf.client.model.StrField
import amf.client.model.document.BaseUnit
import amf.client.model.document.DeclaresModel
import amf.client.model.document.Document
import amf.client.model.document.EncodesModel
import amf.client.model.domain.AnyShape
import amf.client.model.domain.ArrayNode
import amf.client.model.domain.ArrayShape
import amf.client.model.domain.Callback
import amf.client.model.domain.ChannelBindings
import amf.client.model.domain.CorrelationId
import amf.client.model.domain.CreativeWork
import amf.client.model.domain.CustomDomainProperty
import amf.client.model.domain.CustomizableElement
import amf.client.model.domain.DataArrangeShape
import amf.client.model.domain.DataNode
import amf.client.model.domain.DomainElement
import amf.client.model.domain.DomainExtension
import amf.client.model.domain.Encoding
import amf.client.model.domain.EndPoint
import amf.client.model.domain.Example
import amf.client.model.domain.IriTemplateMapping
import amf.client.model.domain.License
import amf.client.model.domain.Linkable
import amf.client.model.domain.Message
import amf.client.model.domain.MessageBindings
import amf.client.model.domain.NamedDomainElement
import amf.client.model.domain.NilShape
import amf.client.model.domain.NodeShape
import amf.client.model.domain.ObjectNode
import amf.client.model.domain.Operation
import amf.client.model.domain.OperationBindings
import amf.client.model.domain.Organization
import amf.client.model.domain.Parameter
import amf.client.model.domain.Payload
import amf.client.model.domain.PropertyDependencies
import amf.client.model.domain.PropertyShape
import amf.client.model.domain.Request
import amf.client.model.domain.Response
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.ScalarShape
import amf.client.model.domain.SecurityRequirement
import amf.client.model.domain.Server
import amf.client.model.domain.ServerBindings
import amf.client.model.domain.Shape
import amf.client.model.domain.ShapeExtension
import amf.client.model.domain.Tag
import amf.client.model.domain.TemplatedLink
import amf.client.model.domain.UnionShape
import amf.client.model.domain.WebApi
import amf.client.model.domain.XMLSerializer
import amf.core.annotations.Aliases
import amf.core.model.DataType
import amf.core.remote.Vendor
import amf.plugins.document.webapi.annotations.ExternalJsonSchemaShape
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.GenerationMode
import org.apache.http.client.utils.URIBuilder
import scala.collection.JavaConverters
import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Base64

val BoolField.value: Boolean? get() = option().orElse(null) as Boolean?
val IntField.value: Int? get() = option().orElse(null) as Int?
val DoubleField.value: Double? get() = option().orElse(null) as Double?
val StrField.value: String? get() = this.value()

//
val Annotations.location: String get() = this.location().get()

//
val BaseUnit.id: String get() = this.id()
val BaseUnit.references: List<BaseUnit> get() = this.references()
val BaseUnit.location: String get() = this.location()
val BaseUnit.usage: String? get() = this.usage().value
val BaseUnit.modelVersion: String? get() = this.modelVersion().value
val BaseUnit.sourceVendor: Vendor? get() = this.sourceVendor().orElse(null)

fun BaseUnit.findDeclaringUnit(element: DomainElement) = allUnits.first { it.location == element.annotations.location }

fun BaseUnit.findInheritingTypes(type: Shape): List<Shape> {
  val resolvedType = type.resolve
  return allUnits.filterIsInstance<DeclaresModel>()
    .flatMap { unit ->
      unit.declares.filterIsInstance<NodeShape>()
        .filter { declared ->
          declared.inherits.any { inherit ->
            inherit.id == resolvedType.id || (inherit.linkTarget?.id == resolvedType.id)
          }
        }
        .plus(
          unit.declares.filterIsInstance<Shape>()
            .filter { declared ->
              declared.inheritsViaAggregation && declared.aggregateInheritanceSuper.resolve.id == resolvedType.id
            }
        )
    }
}

private val ELEMENT_REF_REGEX = """(?:([^.]+)\.)?([\w-_.]+)""".toRegex()

fun BaseUnit.resolveRef(ref: String): Pair<DomainElement, BaseUnit>? =
  if (ref.contains('#')) {
    resolveJsonRef(ref)
  } else {
    resolveUsesRef(ref)
  }

fun BaseUnit.resolveJsonRef(ref: String): Pair<DomainElement, BaseUnit>? {
  val refUri = URI(location).resolve(ref)
  val refLocation = URIBuilder(refUri).setFragment(null).build()
  val refName = refUri.fragment.split('/').last()

  val refDeclaringUnit = allUnits.first { URI(it.location()) == refLocation }
  val refElement =
    (refDeclaringUnit as DeclaresModel).declares
      .filterIsInstance<NamedDomainElement>()
      .find { it.name == refName } as? DomainElement ?: return null

  return refElement to refDeclaringUnit
}

fun BaseUnit.resolveUsesRef(name: String): Pair<DomainElement, BaseUnit>? {

  val (libraryName, declarationName) =
    ELEMENT_REF_REGEX.matchEntire(name)?.destructured
      ?: error("Invalid reference '$name'")

  val declarationUnit =
    if (libraryName.isBlank()) {

      this as? DeclaresModel
    } else {

      val aliases: Aliases =
        annotations._internal().find(Aliases::class.java).getOrElse(null)
          ?: error("Unable to find unit aliases")

      val unitUrl = JavaConverters.setAsJavaSet(aliases.aliases()).first { it._1 == libraryName }?._2?._1

      allUnits.find { it.location == unitUrl } as? DeclaresModel
    } ?: return null

  val decl = declarationUnit.declares
    .filterIsInstance<NamedDomainElement>()
    .firstOrNull { it.name == declarationName } as? DomainElement
    ?: return null

  return decl to (declarationUnit as BaseUnit)
}

//
val DeclaresModel.declares: List<DomainElement> get() = this.declares()

//
val EncodesModel.encodes: DomainElement get() = this.encodes()

//
val Annotable.annotations: Annotations get() = this.annotations()

//
val BaseUnit.allUnits: List<BaseUnit> get() = references.flatMap { it.allUnits }.plus(this)

//
val Document.api: WebApi get() = this.encodes as WebApi

//
val DomainElement.id: String get() = this.id()
val DomainElement.extendsNode: List<DomainElement> get() = this.extendsNode()

//
val CustomizableElement.customDomainProperties: List<DomainExtension> get() = this.customDomainProperties()

fun CustomizableElement.hasAnnotation(name: APIAnnotationName, generationMode: GenerationMode?) =
  customDomainProperties.any { name.matches(it.definedBy.name, generationMode) } ||
    customDomainProperties.any { name.matches(it.definedBy.name, null) }

fun CustomizableElement.findAnnotation(name: APIAnnotationName, generationMode: GenerationMode?) =
  customDomainProperties.find { name.matches(it.definedBy.name, generationMode) }?.extension
    ?: customDomainProperties.find { name.matches(it.definedBy.name, null) }?.extension

fun CustomizableElement.findStringAnnotation(name: APIAnnotationName, generationMode: GenerationMode?) =
  findAnnotation(name, generationMode)?.stringValue

fun CustomizableElement.findBoolAnnotation(name: APIAnnotationName, generationMode: GenerationMode?) =
  findAnnotation(name, generationMode)?.rawScalarValue?.toBoolean()

fun CustomizableElement.findIntAnnotation(name: APIAnnotationName, generationMode: GenerationMode?) =
  findAnnotation(name, generationMode)?.rawScalarValue?.toInt()

fun CustomizableElement.findArrayAnnotation(name: APIAnnotationName, generationMode: GenerationMode?) =
  findAnnotation(name, generationMode)?.let { it as ArrayNode }?.members()

//
val DomainExtension.name: String get() = this.name().value()
val DomainExtension.definedBy: CustomDomainProperty get() = this.definedBy()
val DomainExtension.extension: DataNode? get() = this.extension()

//
val CustomDomainProperty.name: String get() = this.name().value()
val CustomDomainProperty.displayName: String? get() = this.displayName().value()
val CustomDomainProperty.description: String? get() = this.description().value()
val CustomDomainProperty.domain: List<String> get() = this.domain().map { it.value() }
val CustomDomainProperty.schema: Shape get() = this.schema()

//
val NamedDomainElement.name: String? get() = this.name().value()

//
val Linkable.linkTarget: DomainElement? get() = this.linkTarget().orElse(null)
val Linkable.linkLabel: String? get() = this.linkLabel().value

//
val WebApi.description: String? get() = this.description().value
val WebApi.identifier: String? get() = this.identifier().value
val WebApi.schemes: List<String?> get() = this.schemes().map { it.value }
val WebApi.endPoints: List<EndPoint> get() = this.endPoints()
val WebApi.accepts: List<String?> get() = this.accepts().map { it.value }
val WebApi.contentType: List<String?> get() = this.contentType().map { it.value }
val WebApi.version: String? get() = this.version().value
val WebApi.termsOfService: String? get() = this.termsOfService().value
val WebApi.provider: Organization? get() = this.provider()
val WebApi.license: License? get() = this.license()
val WebApi.documentations: List<CreativeWork> get() = this.documentations()
val WebApi.servers: List<Server> get() = this.servers()
val WebApi.security: List<SecurityRequirement> get() = this.security()

//
val EndPoint.description: String? get() = this.description().value
val EndPoint.summary: String? get() = this.summary().value
val EndPoint.path: String get() = this.path().value()
val EndPoint.operations: List<Operation> get() = this.operations()
val EndPoint.parameters: List<Parameter> get() = this.parameters()
val EndPoint.payloads: List<Payload> get() = this.payloads()
val EndPoint.servers: List<Server> get() = this.servers()
val EndPoint.security: List<SecurityRequirement> get() = this.security()
val EndPoint.bindings: ChannelBindings get() = this.bindings()
val EndPoint.parent: EndPoint? get() = this.parent().orElse(null)
val EndPoint.relativePath: String get() = this.relativePath()

val EndPoint.root: EndPoint get() = this.parent?.root ?: this

//
val Operation.method: String get() = this.method().value()
val Operation.description: String? get() = this.description().value
val Operation.deprecated: Boolean? get() = this.deprecated().value
val Operation.summary: String? get() = this.summary().value
val Operation.documentation: CreativeWork get() = this.documentation()
val Operation.schemes: List<String> get() = this.schemes().map { it.value() }
val Operation.accepts: List<String> get() = this.accepts().map { it.value() }
val Operation.contentType: List<String> get() = this.contentType().map { it.value() }
val Operation.request: Request? get() = this.request()
val Operation.requests: List<Request> get() = this.requests()
val Operation.responses: List<Response> get() = this.responses()
val Operation.security: List<SecurityRequirement> get() = this.security()
val Operation.tags: List<Tag> get() = this.tags()
val Operation.callbacks: List<Callback> get() = this.callbacks()
val Operation.servers: List<Server> get() = this.servers()
val Operation.abstract: Boolean? get() = this.isAbstract.value
val Operation.bindings: OperationBindings get() = this.bindings()
val Operation.operationId: String? get() = this.operationId().value()

val Operation.operationName: String get() = this.operationId ?: this.name!!

val Operation.successes: List<Response>
  get() =
    this.responses.filter { (200 until 300).contains(it.statusCode.toInt()) }

val Operation.failures: List<Response>
  get() =
    this.responses.filter { (400 until 600).contains(it.statusCode.toInt()) }

//
val Message.description: String? get() = this.description().value
val Message.abstract: Boolean? get() = this.isAbstract.value
val Message.documentation: CreativeWork get() = this.documentation()
val Message.tags: List<Tag> get() = this.tags()
val Message.examples: List<Example> get() = this.examples()
val Message.payloads: List<Payload> get() = this.payloads()
val Message.correlationId: CorrelationId get() = this.correlationId()
val Message.displayName: String? get() = this.displayName().value
val Message.title: String? get() = this.title().value
val Message.summary: String? get() = this.summary().value
val Message.bindings: MessageBindings get() = this.bindings()

//
val Request.required: Boolean get() = this.required().value()
val Request.queryParameters: List<Parameter> get() = this.queryParameters()
val Request.headers: List<Parameter> get() = this.headers()
val Request.queryString: Shape? get() = this.queryString()
val Request.uriParameters: List<Parameter> get() = this.uriParameters()
val Request.cookieParameters: List<Parameter> get() = this.cookieParameters()

//
val Response.statusCode: String get() = this.statusCode().value()
val Response.headers: List<Parameter> get() = this.headers()
val Response.links: List<TemplatedLink> get() = this.links()

//
val Payload.mediaType: String? get() = this.mediaType().value
val Payload.schemaMediaType: String? get() = this.schemaMediaType().value
val Payload.schema: Shape? get() = this.schema()
val Payload.examples: List<Example> get() = this.examples()
val Payload.encodings: List<Encoding> get() = this.encodings()

//
val Parameter.parameterName: String? get() = this.parameterName().value
val Parameter.description: String? get() = this.description().value
val Parameter.required: Boolean? get() = this.required().value
val Parameter.deprecated: Boolean? get() = this.deprecated().value
val Parameter.allowEmptyValue: Boolean? get() = this.allowEmptyValue().value
val Parameter.style: String? get() = this.style().value
val Parameter.explode: Boolean? get() = this.explode().value
val Parameter.allowReserved: Boolean? get() = this.allowReserved().value
val Parameter.binding: String? get() = this.binding().value
val Parameter.schema: Shape? get() = this.schema()
val Parameter.payloads: List<Payload> get() = this.payloads()
val Parameter.examples: List<Example> get() = this.examples()

//
val Server.url: String get() = this.url().value()
val Server.description: String? get() = this.description().value
val Server.variables: List<Parameter> get() = this.variables()
val Server.protocol: String? get() = this.protocol().value
val Server.protocolVersion: String? get() = this.protocolVersion().value
val Server.security: List<SecurityRequirement> get() = this.security()
val Server.bindings: ServerBindings get() = this.bindings()

//
val Shape.displayName: String? get() = this.displayName().value
val Shape.description: String? get() = this.description().value
val Shape.defaultValue: DataNode? get() = this.defaultValue()
val Shape.defaultValueStr: String? get() = this.defaultValueStr().value
val Shape.values: List<DataNode> get() = this.values()
val Shape.inherits: List<Shape> get() = this.inherits()
val Shape.customShapeProperties: List<ShapeExtension> get() = this.customShapeProperties()
val Shape.customShapePropertyDefinitions: List<PropertyShape> get() = this.customShapePropertyDefinitions()
val Shape.or: List<Shape> get() = this.or()
val Shape.and: List<Shape> get() = this.and()
val Shape.xone: List<Shape> get() = this.xone()
val Shape.not: Shape? get() = this.not()
val Shape.readOnly: Boolean? get() = this.readOnly().value
val Shape.writeOnly: Boolean? get() = this.writeOnly().value
val Shape.deprecated: Boolean? get() = this.deprecated().value
val Shape.ifShape: Shape? get() = this.ifShape()
val Shape.thenShape: Shape? get() = this.thenShape()
val Shape.elseShape: Shape? get() = this.elseShape()
val Shape.inlined: Boolean get() = this.annotations.inlinedElement()

val Shape.wasLink: Boolean get() = this.annotations._internal().contains(ExternalJsonSchemaShape::class.java)
val Shape.isOrWasLink: Boolean get() = isLink || wasLink

val Shape.resolve: Shape
  get() =
    if (this.isLink)
      (this.linkTarget as Shape).resolve
    else if (this.inherits.size == 1 && (this !is NodeShape || this.isReferenceNode))
      this.inherits.first().resolve
    else
      this

val Shape.inheritsViaAggregation: Boolean get() = and.size == 2 && and.count { it is NodeShape && !it.isOrWasLink } == 1 && and.count { it.isOrWasLink } == 1
val Shape.aggregateInheritanceSuper: Shape get() = and.first { it.isOrWasLink }.resolve
val Shape.aggregateInheritanceNode: NodeShape get() = and.filterIsInstance<NodeShape>().first { !it.isOrWasLink }

val Shape.inheritsViaInherits: Boolean get() = inherits.size == 1 && inherits.first() is NodeShape
val Shape.inheritsInheritanceSuper: Shape get() = inherits.first().resolve
val Shape.inheritsInheritanceNode: NodeShape get() = this as NodeShape

val Shape.anyInheritance: Boolean get() = inheritsViaAggregation || inheritsViaInherits
val Shape.anyInheritanceSuper: Shape?
  get() =
    when {
      inheritsViaAggregation -> aggregateInheritanceSuper
      inheritsViaInherits -> inheritsInheritanceSuper
      else -> null
    }
val Shape.anyInheritanceNode: NodeShape?
  get() =
    when {
      inheritsViaAggregation -> aggregateInheritanceNode
      inheritsViaInherits -> inheritsInheritanceNode
      else -> null
    }

val Shape.inheritanceRoot: Shape get() = anyInheritanceSuper?.inheritanceRoot ?: this

//
val ShapeExtension.definedBy: PropertyShape get() = this.definedBy()
val ShapeExtension.extension: DataNode? get() = this.extension()

//
val TemplatedLink.description: String? get() = this.description().value
val TemplatedLink.template: String? get() = this.template().value
val TemplatedLink.operationId: String? get() = this.operationId().value
val TemplatedLink.operationRef: String? get() = this.operationRef().value
val TemplatedLink.mapping: List<IriTemplateMapping> get() = this.mapping()
val TemplatedLink.requestBody: String? get() = this.requestBody().value
val TemplatedLink.server: Server? get() = this.server()

//
val AnyShape.documentation: CreativeWork? get() = this.documentation()
val AnyShape.xmlSerialization: XMLSerializer? get() = this.xmlSerialization()
val AnyShape.examples: List<Example> get() = this.examples()
val AnyShape.comment: String? get() = this.comment().value

//
val ScalarShape.dataType: String? get() = this.dataType().value
val ScalarShape.pattern: String? get() = this.pattern().value
val ScalarShape.minLength: Int? get() = this.minLength().value
val ScalarShape.maxLength: Int? get() = this.maxLength().value
val ScalarShape.minimum: Double? get() = this.minimum().value
val ScalarShape.maximum: Double? get() = this.maximum().value
val ScalarShape.exclusiveMinimum: Boolean? get() = this.exclusiveMinimum().value
val ScalarShape.exclusiveMaximum: Boolean? get() = this.exclusiveMaximum().value
val ScalarShape.format: String? get() = this.format().value
val ScalarShape.multipleOf: Double? get() = this.multipleOf().value

//
val NodeShape.minProperties: Int? get() = this.minProperties().value
val NodeShape.maxProperties: Int? get() = this.maxProperties().value
val NodeShape.closed: Boolean? get() = this.closed().value
val NodeShape.discriminator: String? get() = this.discriminator().value
val NodeShape.discriminatorValue: String? get() = this.discriminatorValue().value
val NodeShape.discriminatorMapping: List<IriTemplateMapping> get() = this.discriminatorMapping()
val NodeShape.additionalPropertiesSchema: Shape? get() = this.additionalPropertiesSchema()
val NodeShape.dependencies: List<PropertyDependencies> get() = this.dependencies()
val NodeShape.propertyNames: Shape? get() = this.propertyNames()

val NodeShape.properties: List<PropertyShape> get() =
  this.properties().filter { it.patternName == null }

val NodeShape.isReferenceNode: Boolean get() =
  inherits.size == 1 && dependencies.isEmpty() &&
    properties.isEmpty() && propertyNames == null &&
    additionalPropertiesSchema == null && minProperties == null && maxProperties == null &&
    discriminator == null && discriminatorValue == null && discriminatorMapping.isEmpty() &&
    or.isEmpty() && and.isEmpty() && xone.isEmpty() && not == null &&
    ifShape == null && elseShape == null && thenShape == null

//
val DataArrangeShape.minItems: Int? get() = this.minItems().value
val DataArrangeShape.maxItems: Int? get() = this.maxItems().value
val DataArrangeShape.uniqueItems: Boolean?
  get() =
    this.uniqueItems().value

//
val ArrayShape.items: Shape? get() = this.items()
val ArrayShape.contains: Shape? get() = this.contains()

//
val UnionShape.anyOf: List<Shape> get() = this.anyOf()
val UnionShape.makesNullable: Boolean get() = anyOf.size == 2 && anyOf.any { it is NilShape }
val UnionShape.nullableType: Shape get() = anyOf.first { it !is NilShape }

//
val PropertyShape.path: String? get() = this.path().value
val PropertyShape.range: Shape get() = this.range()
val PropertyShape.minCount: Int? get() = this.minCount().value
val PropertyShape.maxCount: Int? get() = this.maxCount().value
val PropertyShape.patternName: String? get() = this.patternName().value
val PropertyShape.optional: Boolean get() = (this.minCount ?: 0) == 0
val PropertyShape.required: Boolean get() = (this.minCount ?: 0) > 0

//
val DataNode.anyValue: Any? get() =
  when (this) {
    is ScalarNode ->
      when (dataType().value()) {
        DataType.String() -> value().value
        DataType.Boolean() -> value().value?.toBoolean()
        DataType.Integer() -> value().value?.toInt()
        DataType.Long() -> value().value?.toLong()
        DataType.Float() -> value().value?.toFloat()
        DataType.Double() -> value().value?.toDouble()
        DataType.Number() -> value().value?.toBigDecimal()
        DataType.Decimal() -> value().value?.toBigDecimal()
        DataType.Duration() -> value().value?.let { Duration.parse(it) }
        DataType.Date() -> value().value?.let { LocalDate.parse(it) }
        DataType.Time() -> value().value?.let { LocalTime.parse(it) }
        DataType.DateTimeOnly() -> value().value?.let { LocalDateTime.parse(it) }
        DataType.DateTime() -> value().value?.let { OffsetDateTime.parse(it) }
        DataType.Binary() -> value().value?.let { Base64.getDecoder().decode(it) }
        else -> error("unsupported scalar node data type")
      }
    is ArrayNode -> arrayValue!!
    is ObjectNode -> objectValue!!
    else -> error("unsupported data node")
  }

val DataNode.stringValue: String? get() = anyValue as? String
val DataNode.rawScalarValue: String? get() = (this as? ScalarNode)?.value
val DataNode.scalarValue: Any? get() = (this as? ScalarNode)?.value?.let {
  when (dataType().value()) {
    DataType.String() -> value().value
    DataType.Boolean() -> value().value?.toBoolean()
    DataType.Integer() -> value().value?.toInt()
    DataType.Long() -> value().value?.toLong()
    DataType.Float() -> value().value?.toFloat()
    DataType.Double() -> value().value?.toDouble()
    DataType.Number() -> value().value?.toBigDecimal()
    DataType.Decimal() -> value().value?.toBigDecimal()
    DataType.Duration() -> value().value?.let { Duration.parse(it) }
    DataType.Date() -> value().value?.let { LocalDate.parse(it) }
    DataType.Time() -> value().value?.let { LocalTime.parse(it) }
    DataType.DateTimeOnly() -> value().value?.let { LocalDateTime.parse(it) }
    DataType.DateTime() -> value().value?.let { OffsetDateTime.parse(it) }
    DataType.Binary() -> value().value?.let { Base64.getDecoder().decode(it) }
    else -> error("unsupported scalar node data type")
  }
}
val DataNode.arrayValue: List<Any?>? get() = (this as? ArrayNode)?.members()?.map { it.anyValue }
val DataNode.objectValue: Map<String, Any?>? get() = (this as? ObjectNode)?.properties()?.mapValues { it.value.anyValue }

//
val ScalarNode.value: String? get() = this.value().value

//
inline fun <reified T : DataNode> ArrayNode.values(): List<T> = this.members().map { it as T }

//
inline fun <reified T : DataNode> ObjectNode.get(propertyName: String): T? {
  val value = getProperty(propertyName)
  return if (value.isPresent) {
    value.get() as T
  } else {
    null
  }
}

fun ObjectNode.getValue(propertyName: String): String? = get<ScalarNode>(propertyName)?.value
