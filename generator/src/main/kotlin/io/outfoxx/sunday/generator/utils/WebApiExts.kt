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

import amf.apicontract.client.platform.model.domain.Callback
import amf.apicontract.client.platform.model.domain.CorrelationId
import amf.apicontract.client.platform.model.domain.Encoding
import amf.apicontract.client.platform.model.domain.EndPoint
import amf.apicontract.client.platform.model.domain.License
import amf.apicontract.client.platform.model.domain.Message
import amf.apicontract.client.platform.model.domain.Operation
import amf.apicontract.client.platform.model.domain.Organization
import amf.apicontract.client.platform.model.domain.Parameter
import amf.apicontract.client.platform.model.domain.Payload
import amf.apicontract.client.platform.model.domain.Request
import amf.apicontract.client.platform.model.domain.Response
import amf.apicontract.client.platform.model.domain.Server
import amf.apicontract.client.platform.model.domain.Tag
import amf.apicontract.client.platform.model.domain.TemplatedLink
import amf.apicontract.client.platform.model.domain.api.WebApi
import amf.apicontract.client.platform.model.domain.bindings.ChannelBindings
import amf.apicontract.client.platform.model.domain.bindings.MessageBindings
import amf.apicontract.client.platform.model.domain.bindings.OperationBindings
import amf.apicontract.client.platform.model.domain.bindings.ServerBindings
import amf.apicontract.client.platform.model.domain.security.ParametrizedSecurityScheme
import amf.apicontract.client.platform.model.domain.security.SecurityRequirement
import amf.apicontract.client.platform.model.domain.security.SecurityScheme
import amf.apicontract.client.platform.model.domain.security.Settings
import amf.core.client.platform.model.Annotable
import amf.core.client.platform.model.Annotations
import amf.core.client.platform.model.BoolField
import amf.core.client.platform.model.DataTypes
import amf.core.client.platform.model.DoubleField
import amf.core.client.platform.model.IntField
import amf.core.client.platform.model.StrField
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.DeclaresModel
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.document.EncodesModel
import amf.core.client.platform.model.domain.ArrayNode
import amf.core.client.platform.model.domain.CustomDomainProperty
import amf.core.client.platform.model.domain.CustomizableElement
import amf.core.client.platform.model.domain.DataNode
import amf.core.client.platform.model.domain.DomainElement
import amf.core.client.platform.model.domain.DomainExtension
import amf.core.client.platform.model.domain.Linkable
import amf.core.client.platform.model.domain.NamedDomainElement
import amf.core.client.platform.model.domain.ObjectNode
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.ScalarNode
import amf.core.client.platform.model.domain.Shape
import amf.core.client.platform.model.domain.ShapeExtension
import amf.shapes.client.platform.model.domain.AnyShape
import amf.shapes.client.platform.model.domain.ArrayShape
import amf.shapes.client.platform.model.domain.CreativeWork
import amf.shapes.client.platform.model.domain.DataArrangeShape
import amf.shapes.client.platform.model.domain.Example
import amf.shapes.client.platform.model.domain.IriTemplateMapping
import amf.shapes.client.platform.model.domain.NilShape
import amf.shapes.client.platform.model.domain.NodeShape
import amf.shapes.client.platform.model.domain.PropertyDependencies
import amf.shapes.client.platform.model.domain.ScalarShape
import amf.shapes.client.platform.model.domain.UnionShape
import amf.shapes.client.platform.model.domain.XMLSerializer
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.GenerationMode
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

fun CustomizableElement.findArrayAnnotation(name: APIAnnotationName, generationMode: GenerationMode?): List<DataNode>? =
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

val Operation.operationName: String get() = this.operationId ?: this.name ?: ""

val Operation.successes: List<Response>
  get() =
    this.responses.filter { (200 until 300).contains(it.statusCode.toInt()) }

val Operation.failures: List<Response>
  get() =
    this.responses.filter { (400 until 600).contains(it.statusCode.toInt()) }

val Operation.has404Response: Boolean
  get() =
    this.responses.any { it.statusCode == "404" }

//
val SecurityRequirement.name: String get() = this.name().value()
val SecurityRequirement.schemes: List<ParametrizedSecurityScheme> get() = this.schemes()

//
val ParametrizedSecurityScheme.name: String get() = this.name().value()
val ParametrizedSecurityScheme.description: String? get() = this.description().value()
val ParametrizedSecurityScheme.scheme: SecurityScheme get() = this.scheme()
val ParametrizedSecurityScheme.settings: Settings get() = this.settings()

//
val SecurityScheme.name: String get() = this.name().value()
val SecurityScheme.type: String get() = this.type().value()
val SecurityScheme.displayName: String? get() = this.displayName().value()
val SecurityScheme.description: String? get() = this.description().value()
val SecurityScheme.headers: List<Parameter>? get() = this.headers()
val SecurityScheme.queryParameters: List<Parameter>? get() = this.queryParameters()
val SecurityScheme.responses: List<Response>? get() = this.responses()
val SecurityScheme.settings: Settings? get() = this.settings()
val SecurityScheme.queryString: Shape? get() = this.queryString()

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

val Callback.name: String? get() = this.name().value
val Callback.expression: String? get() = this.expression().value
val Callback.endPoint: EndPoint? get() = this.endpoint()

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

val NodeShape.properties: List<PropertyShape> get() = this.properties()

val NodeShape.nonPatternProperties: List<PropertyShape> get() =
  this.properties().filter { it.patternName == null }

val NodeShape.patternProperties: List<PropertyShape> get() =
  this.properties().filterNot { it.patternName == null }

//
val DataArrangeShape.minItems: Int? get() = this.minItems().value
val DataArrangeShape.maxItems: Int? get() = this.maxItems().value
val DataArrangeShape.uniqueItems: Boolean? get() = this.uniqueItems().value

//
val ArrayShape.items: Shape? get() = this.items()
val ArrayShape.contains: Shape? get() = this.contains()

//
val UnionShape.anyOf: List<Shape> get() = this.anyOf()
val UnionShape.makesNullable: Boolean get() = anyOf.size == 2 && anyOf.any { it is NilShape }
val UnionShape.nullableType: Shape get() = anyOf.first { it !is NilShape }
val UnionShape.flattened: List<Shape> get() = this.anyOf.flatMap { if (it is UnionShape) it.flattened else listOf(it) }

//
val PropertyShape.path: String? get() = this.path().value
val PropertyShape.range: Shape get() = this.range()
val PropertyShape.minCount: Int? get() = this.minCount().value
val PropertyShape.maxCount: Int? get() = this.maxCount().value
val PropertyShape.patternName: String? get() = this.patternName().value
val PropertyShape.optional: Boolean get() = (this.minCount ?: 0) == 0
val PropertyShape.required: Boolean get() = (this.minCount ?: 0) > 0
val PropertyShape.nullable: Boolean get() = (range as? UnionShape)?.makesNullable ?: false
val PropertyShape.isInherited: Boolean get() = annotations.inheritanceProvenance().isPresent

//
val DataNode.anyValue: Any? get() =
  when (this) {
    is ScalarNode ->
      when (dataType().value()) {
        DataTypes.String() -> value().value
        DataTypes.Boolean() -> value().value?.toBoolean()
        DataTypes.Integer() -> value().value?.toInt()
        DataTypes.Long() -> value().value?.toLong()
        DataTypes.Float() -> value().value?.toFloat()
        DataTypes.Double() -> value().value?.toDouble()
        DataTypes.Number() -> value().value?.toBigDecimal()
        DataTypes.Decimal() -> value().value?.toBigDecimal()
//        DataTypes.Duration() -> value().value?.let { Duration.parse(it) }
        DataTypes.Date() -> value().value?.let { LocalDate.parse(it) }
        DataTypes.Time() -> value().value?.let { LocalTime.parse(it) }
        DataTypes.DateTimeOnly() -> value().value?.let { LocalDateTime.parse(it) }
        DataTypes.DateTime() -> value().value?.let { OffsetDateTime.parse(it) }
        DataTypes.Binary() -> value().value?.let { Base64.getDecoder().decode(it) }
        else -> error("unsupported scalar node data type")
      }
    is ArrayNode -> arrayValue!!
    is ObjectNode -> objectValue!!
    else -> error("unsupported data node")
  }

val DataNode.stringValue: String? get() = anyValue as? String
val DataNode.numberValue: Number? get() = anyValue as? Number
val DataNode.rawScalarValue: String? get() = (this as? ScalarNode)?.value
val DataNode.scalarValue: Any? get() = (this as? ScalarNode)?.value?.let {
  when (dataType().value()) {
    DataTypes.String() -> value().value
    DataTypes.Boolean() -> value().value?.toBoolean()
    DataTypes.Integer() -> value().value?.toInt()
    DataTypes.Long() -> value().value?.toLong()
    DataTypes.Float() -> value().value?.toFloat()
    DataTypes.Double() -> value().value?.toDouble()
    DataTypes.Number() -> value().value?.toBigDecimal()
    DataTypes.Decimal() -> value().value?.toBigDecimal()
//    DataTypes.Duration() -> value().value?.let { Duration.parse(it) }
    DataTypes.Date() -> value().value?.let { LocalDate.parse(it) }
    DataTypes.Time() -> value().value?.let { LocalTime.parse(it) }
    DataTypes.DateTimeOnly() -> value().value?.let { LocalDateTime.parse(it) }
    DataTypes.DateTime() -> value().value?.let { OffsetDateTime.parse(it) }
    DataTypes.Binary() -> value().value?.let { Base64.getDecoder().decode(it) }
    else -> error("unsupported scalar node data type")
  }
}
val DataNode.arrayValue: List<Any?>? get() = (this as? ArrayNode)?.members()?.map { it.anyValue }
val DataNode.objectValue: Map<String, Any?>? get() =
  (this as? ObjectNode)?.properties()?.mapValues { it.value.anyValue }

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
