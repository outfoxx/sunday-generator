/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.ir

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.OpenApiReferenceLocations.Kind
import io.outfoxx.sunday.generator.ir.OpenApiReferenceLocations.componentKinds
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.Shape
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.resourceKeywords
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.net.URI

/** Bundles native OpenAPI references while retaining named, potentially recursive schemas. */
class OpenApiReferenceResolver(
  private val documentLoader: OpenApiDocumentLoader? = null,
) {
  /** Resolves a document with fresh schema identity and cycle state for this conversion. */
  fun resolve(sourceUri: URI): OpenApiReferenceResolution =
    Session(documentLoader ?: OpenApiDocumentLoader.create()).resolve(sourceUri)

  private class Session(
    private val documentLoader: OpenApiDocumentLoader,
  ) {

    private val mapper = ObjectMapper(YAMLFactory()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    private val documents = mutableMapOf<URI, Document>()
    private val capturedDocuments = linkedMapOf<URI, OpenApiLoadedDocument>()
    private val resources = mutableMapOf<URI, Node>()
    private val anchors = mutableMapOf<URI, Node>()
    private val scopes = mutableMapOf<Target, Scope>()
    private val indexed = mutableSetOf<Pair<Target, Kind>>()
    private val discovered = mutableSetOf<Pair<Target, Kind>>()
    private val discoveredSchemas = linkedMapOf<Target, Node>()
    private val componentNames = mutableMapOf<Target, String>()
    private val schemaReferences = mutableMapOf<Target, Node>()
    private val discriminatorMappings = mutableMapOf<Target, Map<String, String>>()
    private val knownKinds = mutableMapOf<Target, Kind>()
    private val schemaNames = mutableMapOf<Target, String>()
    private val schemas = linkedMapOf<String, Map<String, Any?>>()
    private val pendingSchemas = ArrayDeque<Pair<String, Node>>()
    private val nameAllocator = OpenApiNameAllocator()
    private val resolving = mutableSetOf<Pair<Target, Kind>>()
    private val normalizedSchemas = mutableListOf<OpenApiSchema>()

    /** Reads a root document and imports only the external objects it references. */
    fun resolve(sourceUri: URI): OpenApiReferenceResolution {
      val root = read(sourceUri.openApiDocumentUri()).root()
      val components = root.child("components")
      val rootSchemas = components.child("schemas").children()
      nameAllocator.reserveAll(rootSchemas.keys)
      rootSchemas.forEach { (name, schema) -> schemaNames[schema.target] = name }

      // A root component can give an external schema its public name. Direct uses of
      // that same target must share the declaration, including recursive references.
      rootSchemas.forEach { (name, schema) ->
        if (schema.isIdentityAlias()) {
          schemaNames.putIfAbsent(terminal(schema, Kind.SCHEMA).target, name)
        }
      }
      rootSchemas.values.forEach { discover(it, Kind.SCHEMA) }
      root
        .child("paths")
        .children()
        .values
        .forEach { discover(it, Kind.PATH_ITEM) }
      componentKinds.filterValues { it != Kind.SCHEMA }.forEach { (section, kind) ->
        components
          .child(section)
          .children()
          .values
          .forEach { discover(it, kind) }
      }
      prepareDiscriminatorMappings()
      rootSchemas.forEach { (name, schema) -> schemas[name] = normalize(schema, Kind.SCHEMA) }

      val result = root.objectValue().toMutableMap()
      result["paths"] = root.child("paths").children().mapValues { (_, path) -> normalize(path, Kind.PATH_ITEM) }
      result["components"] =
        components.objectValue().toMutableMap().apply {
          componentKinds.forEach { (section, kind) ->
            if (kind != Kind.SCHEMA && components.value.has(section)) {
              this[section] = components.child(section).children().mapValues { (_, node) -> normalize(node, kind) }
            }
          }
          this["schemas"] = schemas
        }
      while (pendingSchemas.isNotEmpty()) {
        val (name, schema) = pendingSchemas.removeFirst()
        schemas[name] = normalize(schema, Kind.SCHEMA)
      }
      return OpenApiReferenceResolution(result, capturedDocuments.toMap()).also { resolution ->
        normalizedSchemas.forEach { resolution.analysis.validate(it) }
      }
    }

    private fun normalize(
      node: Node,
      kind: Kind,
    ): Map<String, Any?> {
      val key = node.target to kind
      if (!resolving.add(key)) {
        node.child("\$ref").error("Cyclic OpenAPI ${kind.label} reference at ${node.target}")
      }
      try {
        if (kind == Kind.SCHEMA && node.value.has("\$ref") && !node.isSchemaAlias()) {
          val fields = normalizeFields(node, kind).toMutableMap()
          val target = reference(node, kind)
          fields["allOf"] = listOf(mapOf("\$ref" to "#/components/schemas/${schemaName(target)}")) +
            (fields["allOf"] as? List<*>).orEmpty()
          return node.schema(fields)
        }
        val referenced =
          if (node.value.has("\$ref")) {
            normalize(reference(node, kind), kind)
          } else {
            emptyMap()
          }
        val result = referenced.toMutableMap()
        result.putAll(normalizeFields(node, kind))
        return if (kind == Kind.SCHEMA) {
          node.schema(result, (referenced as? OpenApiSchema)?.usesBooleanExclusiveBounds)
        } else {
          result
        }
      } finally {
        resolving.remove(key)
      }
    }

    private fun normalizeFields(
      node: Node,
      kind: Kind,
    ): Map<String, Any?> =
      node
        .children()
        .filterKeys {
          !node.ignoresSchemaReferenceSiblings(kind) &&
            it != "\$ref" &&
            !(kind == Kind.SCHEMA && node.scope.dialect != OAS_30_DIALECT && it in resourceKeywords) &&
            (
              !node.value.has("\$ref") ||
                kind in setOf(Kind.SCHEMA, Kind.PATH_ITEM) ||
                it == "summary" ||
                it == "description" ||
                it.startsWith("x-sunday-")
            )
        }.mapValues { (name, child) ->
          val location = OpenApiReferenceLocations.child(kind, name)
          when {
            kind == Kind.SCHEMA && name == "discriminator" -> discriminator(child)
            // An unpromoted boolean retains the converter's unrestricted-object additionalProperties form.
            kind == Kind.SCHEMA &&
              name == "additionalProperties" &&
              child.isTrueSchema() &&
              child.target !in schemaNames -> true
            location == null -> child.rawValue()
            else ->
              when (location.shape) {
                Shape.MAP -> child.children().mapValues { (_, value) -> normalizeChild(value, location.kind) }
                Shape.LIST -> child.elements().map { normalizeChild(it, location.kind) }
                Shape.VALUE -> normalizeChild(child, location.kind)
              }
          }
        }

    private fun normalizeChild(
      node: Node,
      kind: Kind,
    ): Any? = if (kind == Kind.SCHEMA) schemaUse(node) else normalize(node, kind)

    private fun schemaUse(node: Node): Any? {
      if (!node.value.isObject && !node.isTrueSchema()) return node.rawValue()
      schemaNames[node.target]?.let { return node.schema(mapOf("\$ref" to "#/components/schemas/$it")) }
      if (!node.isSchemaAlias()) return normalize(node, Kind.SCHEMA)
      val target = reference(node, Kind.SCHEMA)
      return node.schema(normalizeFields(node, Kind.SCHEMA) + ("\$ref" to "#/components/schemas/${schemaName(target)}"))
    }

    private fun Node.isSchemaAlias(): Boolean =
      value.has("\$ref") &&
        (scope.dialect == OAS_30_DIALECT || children().keys.none(OpenApiSchemaKeywords::isAssertion))

    // An annotated alias is a distinct use of its target, even when its assertions are identical.
    private fun Node.isIdentityAlias(): Boolean =
      isSchemaAlias() &&
        (
          scope.dialect == OAS_30_DIALECT ||
            children().keys.all { it in resourceKeywords || it == "\$ref" || it == "\$schema" }
        )

    private fun Node.ignoresSchemaReferenceSiblings(kind: Kind): Boolean =
      kind == Kind.SCHEMA && scope.dialect == OAS_30_DIALECT && value.has("\$ref")

    private fun Node.isTrueSchema(): Boolean =
      value.isBoolean && value.booleanValue() && scope.dialect != OAS_30_DIALECT

    // Discovery assigns identities before the first inline use is emitted, including forward references.
    private fun discover(
      node: Node,
      kind: Kind,
    ) {
      if ((!node.value.isObject && !(kind == Kind.SCHEMA && node.value.isBoolean)) ||
        !discovered.add(node.target to kind)
      ) {
        return
      }
      if (kind == Kind.SCHEMA) discoveredSchemas[node.target] = node
      if (node.value.has("\$ref")) {
        val target = reference(node, kind)
        if (kind == Kind.SCHEMA) {
          schemaReferences[node.target] = target
          schemaName(target)
        }
        discover(target, kind)
      }
      if (node.ignoresSchemaReferenceSiblings(kind)) return
      node.referenceChildren(kind).forEach { (child, childKind) -> discover(child, childKind) }
      if (kind == Kind.SCHEMA) {
        node.child("discriminator").child("mapping").children().values.forEach { mapping ->
          val target = discriminatorTarget(mapping)
          schemaName(target)
          discover(target, Kind.SCHEMA)
        }
      }
    }

    private fun schemaName(node: Node): String {
      schemaNames[node.target]?.let { return it }
      val target = terminal(node, Kind.SCHEMA)
      schemaNames[target.target]?.let { return it }
      val hint =
        target.pointer
          .substringAfterLast('/')
          .unescapePointer()
          .ifEmpty {
            target.document.uri.path
              .orEmpty()
              .substringAfterLast('/')
              .substringBeforeLast('.')
          }.replace(Regex("[^A-Za-z0-9_]"), "-")
          .toUpperCamelCase()
          .ifEmpty { "Schema" }
      val base = if (hint.first().isDigit()) "Schema$hint" else hint
      val name = nameAllocator.allocate(base)
      // Queue definitions after reserving identity. A reference may point back to
      // an inline schema currently being visited, including a recursive $defs entry.
      schemaNames[node.target] = name
      schemaNames[target.target] = name
      pendingSchemas.addLast(name to node)
      return name
    }

    private fun discriminator(node: Node): Map<String, Any?> =
      node.objectValue().toMutableMap().apply {
        val inferred = discriminatorMappings[node.target]
        if (inferred != null || node.value.has("mapping")) {
          this["mapping"] = inferred ?: explicitDiscriminatorMappings(node)
        }
      }

    private fun explicitDiscriminatorMappings(node: Node): Map<String, String> =
      node.child("mapping").children().mapValues { (_, mapping) ->
        "#/components/schemas/${schemaName(discriminatorTarget(mapping))}"
      }

    private fun prepareDiscriminatorMappings() {
      val parents =
        discoveredSchemas.mapValues { (_, schema) ->
          val references =
            if (schema.isSchemaAlias()) listOfNotNull(schemaReferences[schema.target]) else allOfParents(schema)
          references.map { terminal(it, Kind.SCHEMA).target }.toSet()
        }
      val descendants =
        discoveredSchemas.values.filter { componentNames.containsKey(it.target) && !it.isSchemaAlias() }
      discoveredSchemas.values.forEach { schema ->
        val discriminator = schema.child("discriminator")
        if (!discriminator.value.isObject || schema.ignoresSchemaReferenceSiblings(Kind.SCHEMA)) return@forEach
        val variants = unionVariants(schema) + descendants.filter { inherits(it.target, schema.target, parents) }
        val explicit = explicitDiscriminatorMappings(discriminator)
        val implicit =
          variants
            .mapNotNull { variant ->
              val original = componentNames[variant.target] ?: componentNames[terminal(variant, Kind.SCHEMA).target]
              original?.let { it to "#/components/schemas/${schemaName(variant)}" }
            }.distinct()
            .filter { (value, reference) -> value !in explicit && reference !in explicit.values }
        // A complete mapping keeps unchanged alternatives visible to emitters that treat it as exhaustive.
        if (implicit.none { (value, reference) -> reference != "#/components/schemas/$value" }) return@forEach
        val mappings = explicit.toMutableMap()
        implicit.forEach { (value, reference) ->
          val previous = mappings.putIfAbsent(value, reference)
          if (previous != null && previous != reference) {
            discriminator.error(
              "Conflicting implicit OpenAPI discriminator value '$value' for '$previous' and '$reference'; " +
                "add explicit discriminator mappings",
            )
          }
        }
        discriminatorMappings[discriminator.target] = mappings
      }
    }

    private fun inherits(
      target: Target,
      ancestor: Target,
      parents: Map<Target, Set<Target>>,
      visited: MutableSet<Target> = mutableSetOf(),
    ): Boolean =
      visited.add(target) &&
        parents[target].orEmpty().any { it == ancestor || inherits(it, ancestor, parents, visited) }

    private fun allOfParents(schema: Node): List<Node> =
      buildList {
        if (!schema.isSchemaAlias()) schemaReferences[schema.target]?.let(::add)
        if (!schema.ignoresSchemaReferenceSiblings(Kind.SCHEMA)) {
          schema.child("allOf").elements().forEach { part ->
            schemaReferences[part.target]?.let(::add)
            addAll(allOfParents(part))
          }
        }
      }

    private fun unionVariants(
      schema: Node,
      visited: MutableSet<Target> = mutableSetOf(),
    ): List<Node> =
      buildList {
        if (!visited.add(schema.target)) return@buildList
        schemaReferences[schema.target]?.let { addAll(unionVariants(it, visited)) }
        if (!schema.ignoresSchemaReferenceSiblings(Kind.SCHEMA)) {
          listOf("oneOf", "anyOf").forEach { keyword ->
            schema.child(keyword).elements().forEach { branch ->
              if (branch.isSchemaAlias()) schemaReferences[branch.target]?.let(::add)
            }
          }
          schema.child("allOf").elements().forEach { addAll(unionVariants(it, visited)) }
        }
      }

    private fun discriminatorTarget(mapping: Node): Node {
      if (!mapping.value.isTextual) {
        mapping.error("OpenAPI discriminator mapping target must be a string")
      }
      val value = mapping.value.asText()
      val implicit =
        mapping.document
          .root()
          .child("components")
          .child("schemas")
          .child(value)
      return if (!implicit.value.isMissingNode) implicit else referenceTarget(mapping, Kind.SCHEMA)
    }

    private fun terminal(
      node: Node,
      kind: Kind,
    ): Node {
      val visited = mutableSetOf<Target>()
      var current = node
      while (current.value.has("\$ref") && (kind != Kind.SCHEMA || current.isIdentityAlias())) {
        if (!visited.add(current.target)) {
          current.child("\$ref").error("Cyclic OpenAPI ${kind.label} reference at ${current.target}")
        }
        current = reference(current, kind)
      }
      return current
    }

    private fun reference(
      node: Node,
      kind: Kind,
    ): Node = referenceTarget(node.child("\$ref"), kind)

    private fun referenceTarget(
      reference: Node,
      kind: Kind,
    ): Node {
      if (!reference.value.isTextual) {
        reference.error("OpenAPI \$ref must be a URI string")
      }
      val ref = reference.value.asText()
      val base = if (kind == Kind.SCHEMA) reference.scope.base else reference.document.uri
      val uri = resolveUri(reference, base, ref)
      val fragment = uri.fragment.orEmpty()
      if (fragment.startsWith('/') && Regex("~(?![01])").containsMatchIn(fragment)) {
        reference.error("Invalid JSON Pointer in OpenAPI reference '$ref'")
      }
      if (fragment.isNotEmpty() && !fragment.startsWith('/') && kind != Kind.SCHEMA) {
        reference.error("Invalid JSON Pointer in OpenAPI reference '$ref'")
      }
      val resourceUri = uri.openApiDocumentUri()
      var resource = if (kind == Kind.SCHEMA) resources[resourceUri] else null
      if (resource == null) {
        val document =
          try {
            read(resourceUri, kind, reference.scope.dialect)
          } catch (exception: Exception) {
            reference.error("Cannot read OpenAPI reference '$ref' ($uri): ${exception.message}")
          }
        resource = if (kind == Kind.SCHEMA) resources[resourceUri] ?: document.root() else document.root()
      }
      if (resource.pointer.isEmpty() && fragment.startsWith("/components/")) {
        indexComponents(resource)
      }
      val target =
        when {
          fragment.isEmpty() -> resource
          fragment.startsWith('/') -> {
            val pointer = resource.pointer + fragment
            Node(resource.document, pointer, resource.document.value.at(pointer))
          }
          else -> {
            if (!anchorName.matches(fragment)) {
              reference.error("Invalid JSON Schema anchor in OpenAPI reference '$ref'")
            }
            val anchorUri = URI(resource.scope.base.toString() + "#" + fragment)
            anchors[uri] ?: anchors[anchorUri]
              ?: reference.error("Unresolved OpenAPI reference '$ref' ($uri): schema anchor does not exist")
          }
        }
      if (target.value.isMissingNode) {
        reference.error("Unresolved OpenAPI reference '$ref' ($uri): JSON Pointer target does not exist")
      }
      validateTarget(target, kind, reference)
      index(target, kind, target.scope)
      return target
    }

    private fun validateTarget(
      target: Node,
      kind: Kind,
      reference: Node,
    ) {
      val value = target.value
      val section =
        target.pointer
          .split('/')
          .takeIf { it.size >= 4 && it[1] == "components" }
          ?.get(2)
      val sectionKind = componentKinds[section]
      val wrongSection = target.pointer.count { it == '/' } == 3 && sectionKind != null && sectionKind != kind
      val wrongKind = knownKinds[target.target]?.let { it != kind } == true
      if (kind == Kind.SCHEMA &&
        target.scope.dialect != OAS_30_DIALECT &&
        value.isBoolean &&
        !wrongSection &&
        !wrongKind
      ) {
        if (value.booleanValue()) return
        reference.error(
          "Unsupported OpenAPI reference '${reference.value.asText()}': false schemas cannot be represented",
        )
      }
      val compatible =
        value.isObject &&
          !wrongSection &&
          !wrongKind &&
          (
            value.has("\$ref") ||
              when (kind) {
                Kind.PARAMETER ->
                  value["name"]?.isTextual == true &&
                    value["in"]?.asText() in parameterLocations &&
                    (
                      value["schema"]?.isObject == true ||
                        target.child("schema").isTrueSchema() ||
                        value["content"]?.isObject == true
                    )
                Kind.REQUEST_BODY ->
                  value["content"]?.isObject == true && listOf("name", "in", "type", "headers").none(value::has)
                Kind.RESPONSE ->
                  value["description"]?.isTextual == true && listOf("name", "in", "type", "schema").none(value::has)
                Kind.HEADER -> !value.has("name") && !value.has("in") && (value.has("schema") || value.has("content"))
                Kind.SECURITY_SCHEME -> value["type"]?.asText() in securityTypes
                Kind.SCHEMA ->
                  (
                    target.scope.dialect != OAS_30_DIALECT ||
                      listOf("openapi", "paths", "content", "in", "schema").none(value::has)
                  ) &&
                    (
                      !value.has("type") ||
                        value["type"].let { type ->
                          if (type.isArray) type.all { it.asText() in schemaTypes } else type.asText() in schemaTypes
                        }
                    ) &&
                    OpenApiSchemaKeywords.maps.all { !value.has(it) || value[it].isObject } &&
                    OpenApiSchemaKeywords.lists.all { !value.has(it) || value[it].isArray } &&
                    (!value.has("required") || (value["required"].isArray && value["required"].all { it.isTextual }))
                else -> true
              }
          )
      if (!compatible) {
        reference.error(
          "OpenAPI reference '${reference.value.asText()}' targets ${target.target}, expected a ${kind.label} object",
        )
      }
    }

    private fun read(
      uri: URI,
      kind: Kind = Kind.SCHEMA,
      dialect: String = OAS_DIALECT,
    ): Document {
      documents[uri]?.let { return it }
      val loaded = documentLoader.load(uri)
      capturedDocuments[uri] = loaded
      capturedDocuments[loaded.uri] = loaded
      documents[loaded.uri]?.let {
        documents[uri] = it
        resources[uri] = it.root()
        return it
      }
      val bytes = loaded.bytes
      val value =
        try {
          mapper.readTree(bytes) ?: throw IllegalArgumentException("Empty OpenAPI document")
        } catch (exception: JsonProcessingException) {
          throw GenerationException(
            "Invalid OpenAPI document: ${exception.originalMessage}",
            loaded.uri.toString(),
            exception.location?.lineNr ?: 1,
            exception.location?.columnNr ?: 1,
          )
        }
      val document = Document(loaded.uri, bytes, value, dialect)
      documents[uri] = document
      documents[loaded.uri] = document
      resources.putIfAbsent(uri, document.root())
      resources.putIfAbsent(loaded.uri, document.root())
      if (document.isOpenApi) {
        val root = document.root()
        knownKinds[root.target] = Kind.DOCUMENT
        indexComponents(root)
        listOf("paths", "webhooks").forEach { section ->
          root
            .child(section)
            .children()
            .values
            .forEach { index(it, Kind.PATH_ITEM, root.scope) }
        }
      } else {
        index(document.root(), kind, document.root().scope)
      }
      return document
    }

    private fun indexComponents(root: Node) {
      root.child("components").children().forEach { (section, components) ->
        componentKinds[section]?.let { componentKind ->
          components.children().forEach { (name, schema) ->
            if (componentKind == Kind.SCHEMA) componentNames[schema.target] = name
            index(schema, componentKind, root.scope)
          }
        }
      }
    }

    private fun index(
      node: Node,
      kind: Kind,
      inherited: Scope,
    ) {
      if ((!node.value.isObject && !(kind == Kind.SCHEMA && node.value.isBoolean)) ||
        !indexed.add(node.target to kind)
      ) {
        return
      }
      knownKinds.putIfAbsent(node.target, kind)
      var scope = inherited
      if (kind == Kind.SCHEMA && scope.dialect != OAS_30_DIALECT) {
        node.value["\$schema"]?.let {
          if (!it.isTextual) node.child("\$schema").error("JSON Schema dialect must be a URI string")
          scope = scope.copy(dialect = it.asText())
        }
        if (scope.dialect !in supportedDialects) {
          node.error("Unsupported OpenAPI schema dialect '${scope.dialect}'")
        }
        node.value["\$id"]?.let {
          val identifier = node.child("\$id")
          if (!it.isTextual) identifier.error("JSON Schema \$id must be a URI string")
          val uri = resolveUri(identifier, scope.base, it.asText())
          if (!uri.isAbsolute || !uri.fragment.isNullOrEmpty()) {
            identifier.error("JSON Schema \$id must resolve to an absolute URI without a non-empty fragment")
          }
          val base = uri.openApiDocumentUri()
          register(resources, base, node, identifier, "schema resource identifier")
          scope = Scope(base, scope.dialect)
        }
        scopes[node.target] = scope
        listOf("\$anchor", "\$dynamicAnchor").forEach { keyword ->
          node.value[keyword]?.let {
            val anchor = node.child(keyword)
            if (!it.isTextual || !anchorName.matches(it.asText())) anchor.error("Invalid JSON Schema $keyword")
            register(anchors, URI(scope.base.toString() + "#" + it.asText()), node, anchor, "schema anchor")
          }
        }
        if (node.value.has("\$dynamicRef")) {
          node.child("\$dynamicRef").error("Dynamic JSON Schema \$dynamicRef evaluation is not supported")
        }
      } else if (kind == Kind.SCHEMA) {
        scopes[node.target] = scope
      }
      node.referenceChildren(kind).forEach { (child, childKind) -> index(child, childKind, scope) }
    }

    private fun Node.referenceChildren(kind: Kind): Sequence<Pair<Node, Kind>> =
      sequence {
        if (ignoresSchemaReferenceSiblings(kind)) return@sequence
        if (value.has("\$ref") && kind !in setOf(Kind.SCHEMA, Kind.PATH_ITEM)) return@sequence
        children().forEach { (name, child) ->
          val location = OpenApiReferenceLocations.child(kind, name) ?: return@forEach
          val nodes =
            when (location.shape) {
              Shape.MAP -> child.children().values.asSequence()
              Shape.LIST -> child.elements().asSequence()
              Shape.VALUE -> sequenceOf(child)
            }
          nodes.forEach { yield(it to location.kind) }
        }
      }

    private fun register(
      registry: MutableMap<URI, Node>,
      uri: URI,
      node: Node,
      origin: Node,
      label: String,
    ) {
      val previous = registry.putIfAbsent(uri, node)
      if (previous != null && previous.target != node.target) {
        origin.error("Conflicting $label '$uri', already declared at ${previous.target}")
      }
    }

    private fun resolveUri(
      origin: Node,
      base: URI,
      value: String,
    ): URI =
      try {
        OpenApiUriResolver.resolve(base, value)
      } catch (exception: IllegalArgumentException) {
        origin.error("Invalid OpenAPI reference '$value': ${exception.message}")
      } catch (exception: java.net.URISyntaxException) {
        origin.error("Invalid OpenAPI reference '$value': ${exception.message}")
      }

    private data class Scope(
      val base: URI,
      val dialect: String,
    )

    private inner class Node(
      val document: Document,
      val pointer: String,
      val value: JsonNode,
    ) {
      val target: Target get() = Target(document.uri, pointer)

      val scope: Scope
        get() {
          var location = pointer
          while (true) {
            scopes[Target(document.uri, location)]?.let { return it }
            if (location.isEmpty()) return Scope(document.uri, document.dialect)
            location = location.substringBeforeLast('/', "")
          }
        }

      fun child(name: String): Node = Node(document, "$pointer/${name.escapePointer()}", value.path(name))

      fun children(): Map<String, Node> = value.properties().associate { (name, _) -> name to child(name) }

      fun elements(): List<Node> = value.mapIndexed { index, element -> Node(document, "$pointer/$index", element) }

      fun schema(
        fields: Map<String, Any?>,
        usesBooleanExclusiveBounds: Boolean? = null,
      ): OpenApiSchema =
        OpenApiSchema(
          fields,
          document.schemaSource,
          pointer,
          usesBooleanExclusiveBounds ?: (scope.dialect == OAS_30_DIALECT),
        ).also { normalizedSchemas.add(it) }

      fun rawValue(): Any? = mapper.convertValue(value, Any::class.java)

      fun objectValue(): Map<String, Any?> =
        if (value.isObject) mapper.convertValue(value, object : TypeReference<Map<String, Any?>>() {}) else emptyMap()

      fun error(message: String): Nothing {
        val location = document.schemaSource.location(pointer)
        throw GenerationException(message, document.uri.toString(), location.lineNr, location.columnNr)
      }
    }

    private inner class Document(
      val uri: URI,
      val bytes: ByteArray,
      val value: JsonNode,
      fallbackDialect: String,
    ) {
      val schemaSource = OpenApiSchema.Source(uri.toString(), bytes)
      val isOpenApi =
        value.path("openapi").asText().matches(Regex("3\\.[01]\\.[0-9]+.*")) &&
          value.path("info").isObject &&
          value.properties().none { (name, _) -> OpenApiSchemaKeywords.isAssertion(name) }
      val dialect =
        if (isOpenApi && value.path("openapi").asText().startsWith("3.0")) {
          OAS_30_DIALECT
        } else {
          if (isOpenApi) value.path("jsonSchemaDialect").asText(OAS_DIALECT) else fallbackDialect
        }

      fun root(): Node = Node(this, "", value)
    }

    private data class Target(
      val uri: URI,
      val pointer: String,
    ) {
      override fun toString(): String = "$uri#$pointer"
    }
  }

  private companion object {
    const val OAS_DIALECT = "https://spec.openapis.org/oas/3.1/dialect/base"
    const val OAS_30_DIALECT = "openapi-3.0"
    val supportedDialects = setOf(OAS_DIALECT, "https://json-schema.org/draft/2020-12/schema")
    val anchorName = Regex("[A-Za-z_][A-Za-z0-9_.-]*")
    val parameterLocations = setOf("query", "path", "header", "cookie")
    val securityTypes = setOf("apiKey", "http", "oauth2", "openIdConnect", "mutualTLS")
    val schemaTypes = setOf("object", "array", "string", "integer", "number", "boolean", "null")

    fun String.escapePointer(): String = replace("~", "~0").replace("/", "~1")

    fun String.unescapePointer(): String = replace("~1", "/").replace("~0", "~")
  }
}
