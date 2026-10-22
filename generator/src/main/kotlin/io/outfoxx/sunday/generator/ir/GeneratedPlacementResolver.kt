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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.GeneratedDocumentUri
import io.outfoxx.sunday.generator.python.pythonIdentifierName
import java.net.URI

/** Resolves lexical defaults and relocation while retaining the complete reference graph. */
class GeneratedPlacementResolver(
  private val configuration: GeneratedPlacementConfiguration = GeneratedPlacementConfiguration(),
  private val generationRole: String = "client",
  private val generationLanguage: String? = null,
) {
  init {
    require(generationRole in setOf("client", "server")) { "Unsupported placement role '$generationRole'" }
    require(generationLanguage == null || generationLanguage in setOf("kotlin", "swift", "typescript", "python")) {
      "Unsupported placement language '$generationLanguage'"
    }
  }

  private val matched = mutableSetOf<Int>()
  private val relocationWinners = mutableMapOf<Triple<String, String, String>, List<Int>>()
  private var pythonPackageFallback = ""

  /** Resolves every declaration, including declarations excluded from emitted output. */
  fun resolve(api: GeneratedApi): GeneratedApi {
    matched.clear()
    relocationWinners.clear()
    pythonPackageFallback = (api.id ?: api.name).pythonIdentifierName
    val services =
      api.services.map { service ->
        service.placementScopes
          .filter { it.declarationUri in service.declarationUris }
          .forEach { it.placement.requireDeclaration("service", it.declarationUri) }
        val uris = service.operations.flatMap { it.declarationUris() }.ifEmpty { service.ownerUris.toList() }
        val contributions = service.placementDeclarations.ifEmpty { listOf(service.placementContribution()) }
        val resolved =
          contributions.map { contribution ->
            targets(
              contribution.targets,
              contribution.placementScopes,
              uris,
              "service",
              service.placementTags,
              explicitServiceUris = service.declarationUris,
              documentTargets = api.targets,
            )
          }
        val merged =
          resolved.flatMap { it.keys }.toSet().associateWith { language ->
            val values = resolved.mapNotNull { it[language] }

            fun field(get: (GeneratedTarget) -> String?): String? {
              val distinct = resolved.map { it[language]?.let(get) }.distinct()
              require(distinct.size <= 1) {
                "Conflicting $language placement for service '${service.name}' contributed by $uris"
              }
              return distinct.singleOrNull()
            }
            (values.firstOrNull() ?: GeneratedTarget()).copy(
              packageName = field { it.packageName },
              modelPackageName = field { it.modelPackageName },
              moduleName = field { it.moduleName },
              modelModuleName = field { it.modelModuleName },
            )
          }
        service.copy(targets = merged)
      }
    val contributionsByModel =
      api.models.map { original ->
        val contributions = original.placementDeclarations.ifEmpty { listOf(original) }
        val resolved =
          contributions.map { model ->
            val lexicalOwner =
              model.declarationUri?.let { declaration ->
                services
                  .flatMap { service -> service.operations.flatMap { it.declarations() }.map { service to it } }
                  .filter { (_, operation) -> operation.declarationUri?.let { matches(it, declaration) } == true }
                  .maxByOrNull { (_, operation) -> operation.declarationUri!!.length }
              }
            val service =
              model.scope?.service?.let { name -> services.firstOrNull { it.name == name } } ?: lexicalOwner?.first
            val operation =
              lexicalOwner?.second ?: service?.operations?.flatMap { it.declarations() }?.firstOrNull {
                it.id == model.scope?.operation &&
                  it.declarationUri?.let { uri -> GeneratedDocumentUri.canonical(URI(uri)).toString() } ==
                  model.source.documentIdentity()
              }
            val ownScopes = model.placementScopes
            val serviceScopes =
              service
                ?.placementDeclarations
                ?.firstOrNull { contribution ->
                  operation?.declarationUri in contribution.operationUris
                }?.placementScopes ?: service?.placementScopes.orEmpty()
            val inherited =
              if (service != null) {
                serviceScopes + operation?.placementScopes.orEmpty()
              } else {
                api.placementScopes
                  .takeIf { model.source.documentIdentity() == api.source.documentIdentity() }
                  .orEmpty()
              }
            val scopes =
              (inherited + ownScopes).distinctBy { it.declarationUri }.sortedBy { scope ->
                when {
                  URI(scope.declarationUri).fragment.isNullOrEmpty() -> 0
                  scope.declarationUri == model.declarationUri -> 4
                  scope.declarationUri == operation?.declarationUri -> 3
                  scope in serviceScopes -> 2
                  else -> 1
                }
              }
            val uris = listOfNotNull(model.declarationUri ?: model.source.location)
            model.copy(
              targets =
                targets(
                  model.targets,
                  scopes,
                  uris,
                  "model",
                  service?.placementTags.orEmpty(),
                  service,
                  operation?.declarationUri,
                  documentTargets =
                    api.targets
                      .takeIf {
                        model.source.documentIdentity() ==
                          api.source.documentIdentity()
                      }.orEmpty(),
                ),
            )
          }
        original to resolved
      }
    val contributions = contributionsByModel.flatMap { it.second }
    val resolvedDeclarations = nestedModels(contributions)
    val resolvedByContribution = contributions.zip(resolvedDeclarations).toMap()
    val identities = GeneratedDeclarationIdentities(resolvedDeclarations, generationLanguage, generationRole)
    val models =
      contributionsByModel.flatMap { (original, resolved) ->
        val groups =
          resolved.map(resolvedByContribution::getValue).groupBy(identities::declaration)
        groups.values.forEach { declarations ->
          require(
            declarations
              .map {
                identities.structure(it) to
                  identities.references(it)
              }.distinct()
              .size <=
              1,
          ) {
            "Incompatible declarations resolve to the same identity '${original.name}': " +
              declarations.joinToString { it.declarationUri ?: it.source.location }
          }
        }
        if (groups.size == 1) {
          listOf(
            groups.values.single().first().copy(
              ownerUris = original.ownerUris,
              placementDeclarations = original.placementDeclarations,
            ),
          )
        } else {
          groups.values.map { declarations ->
            val owners =
              declarations
                .flatMap {
                  it.ownerUris.ifEmpty {
                    setOf(
                      it.source.location,
                    )
                  }
                }.toSet()
            declarations.first().copy(
              ownerUris = owners.takeIf { declarations.size > 1 }.orEmpty(),
              placementDeclarations = declarations.takeIf { it.size > 1 }.orEmpty(),
            )
          }
        }
      }
    services.forEach { service ->
      service.operations.forEach { operation ->
        require(
          operation
            .declarations()
            .map(identities::references)
            .distinct()
            .size <= 1,
        ) {
          "Operation '${operation.id}' in service '${service.name}' has conflicting resolved reference identities: " +
            operation.declarationUris().joinToString()
        }
      }
    }
    val problems =
      api.problems.map { problem ->
        problem.copy(
          targets =
            targets(
              problem.targets,
              problem.placementScopes.ifEmpty {
                api.placementScopes
                  .takeIf {
                    problem.source.documentIdentity() ==
                      api.source.documentIdentity()
                  }.orEmpty()
              },
              listOf(problem.declarationUri ?: problem.source.location),
              "problem",
              emptySet(),
              documentTargets =
                api.targets
                  .takeIf { problem.source.documentIdentity() == api.source.documentIdentity() }
                  .orEmpty(),
            ),
        )
      }
    val unused = configuration.relocations.indices.filterNot { it in matched }
    require(unused.isEmpty()) {
      "Placement selectors matched no applicable declaration: ${unused.map { configuration.relocations[it].target }}"
    }
    return api
      .copy(
        models = models,
        services = services,
        problems = problems,
        targets = sourceTargets(api.targets, api.placementScopes, "service", api.source.location),
      ).also(::validateModuleDependencies)
  }

  private fun validateModuleDependencies(api: GeneratedApi) {
    if (generationLanguage != "swift") return
    val index =
      io.outfoxx.sunday.generator.ir.emit
        .GeneratedApiIndex(api)

    fun GeneratedModel.module(): String =
      targets["swift"]?.modelModuleName
        ?: scope?.service?.let { name ->
          api.services
            .firstOrNull { it.name == name }
            ?.targets
            ?.get("swift")
            ?.moduleName
        }
        ?: ""

    val hierarchy =
      io.outfoxx.sunday.generator.ir.emit
        .GeneratedModelHierarchy(index)
    val graph = mutableMapOf<String, MutableSet<String>>()

    fun dependencies(
      module: String,
      rewrite: (GeneratedTypeReferenceRewriter) -> Unit,
      inferred: List<GeneratedModel> = emptyList(),
    ) {
      val modules = inferred.map { it.module() }.toMutableSet()
      rewrite(
        GeneratedTypeReferenceRewriter(null, { reference, _ ->
          if (reference.kind == GeneratedTypeRef.Kind.NAMED) {
            index.modelOrNull(reference)?.let { modules.add(it.module()) }
            index.problemOrNull(reference)?.let { modules.add(it.targets["swift"]?.modelModuleName.orEmpty()) }
          }
          reference
        }),
      )
      modules.remove(module)
      require(module.isEmpty() || "" !in modules) {
        "Named Swift module '$module' depends on declarations in the unnamed output module; assign those declarations a module"
      }
      graph.getOrPut(module) { mutableSetOf() }.addAll(modules)
    }
    api.models.forEach { model ->
      dependencies(
        model.module(),
        { it.model(model.copy(placementDeclarations = emptyList())) },
        if (model.discriminator != null) hierarchy.cases(model) else emptyList(),
      )
    }
    api.problems.forEach { problem ->
      dependencies(problem.targets["swift"]?.modelModuleName.orEmpty(), { it.problem(problem) })
    }
    api.services.forEach { service ->
      dependencies(service.targets["swift"]?.moduleName.orEmpty(), { it.service(service) })
    }
    val done = mutableSetOf<String>()

    fun visit(
      module: String,
      active: List<String>,
    ) {
      require(module !in active) { "Cyclic Swift module placement: ${(active + module).joinToString(" -> ")}" }
      if (!done.add(module)) return
      graph[module].orEmpty().forEach { visit(it, active + module) }
    }
    graph.keys.forEach { visit(it, emptyList()) }
  }

  private fun nestedModels(models: List<GeneratedModel>): List<GeneratedModel> {
    val resolved = mutableMapOf<GeneratedModel, GeneratedModel>()
    val index =
      io.outfoxx.sunday.generator.ir.emit
        .GeneratedModelIndex(models)

    fun resolve(
      model: GeneratedModel,
      active: Set<GeneratedModel>,
    ): GeneratedModel {
      resolved[model]?.let { return it }
      require(model !in active) { "Cyclic nesting for '${model.declarationUri ?: model.name}'" }
      val reference = model.nested?.enclosedIn ?: return model
      val enclosing =
        index.modelOrNull(reference)
          ?: error("Unknown enclosing declaration '${reference.name}' for '${model.name}'")
      val parent = resolve(enclosing, active + model)
      val targets = model.targets.toMutableMap()
      for (key in (model.targets.keys + parent.targets.keys).filter {
        it.startsWith("kotlin") ||
          it == "swift" ||
          it == "typescript"
      }) {
        val childTarget = targets[key] ?: GeneratedTarget()
        val parentTarget = parent.targets[key] ?: GeneratedTarget()
        val language = if (key.startsWith("kotlin")) "kotlin" else key
        val role =
          when {
            key.endsWith("Server") -> "server"
            key.endsWith("Client") -> "client"
            else -> generationRole
          }
        val sourcePlacement =
          model.placementScopes
            .filter { it.declarationUri == model.declarationUri }
            .mapNotNull { it.placement.language(language)?.resolve(role, "model") }

        fun conflicts(
          field: String,
          value: String?,
          enclosingValue: String?,
          get: (GeneratedPlacement.Scope) -> String?,
        ): Boolean {
          val winners = relocationWinners[Triple(model.declarationUri ?: model.source.location, key, field)].orEmpty()
          if (winners.isNotEmpty()) {
            // Only the effective, applicable relocation can detach a nested declaration. A rule
            // shared with its parent is an inherited default, not a request to detach the child.
            return winners.any { index ->
              parent.declarationUri?.let { matches(configuration.relocations[index].target, it) } != true
            } &&
              value != enclosingValue
          }
          return sourcePlacement.firstNotNullOfOrNull(get)?.let { it != enclosingValue } == true
        }
        require(
          !conflicts("package", childTarget.modelPackageName, parentTarget.modelPackageName) { it.packageName } &&
            !conflicts("module", childTarget.modelModuleName, parentTarget.modelModuleName) { it.module },
        ) {
          "Nested declaration '${model.declarationUri ?: model.name}' must remain with '${parent.declarationUri ?: parent.name}'"
        }
        targets[key] =
          childTarget.copy(
            modelPackageName = parentTarget.modelPackageName,
            modelModuleName = parentTarget.modelModuleName,
          )
      }
      return model.copy(targets = targets).also { resolved[model] = it }
    }
    return models.map { resolve(it, emptySet()) }
  }

  private fun sourceTargets(
    existing: Map<String, GeneratedTarget>,
    scopes: List<GeneratedPlacementScope>,
    category: String,
    document: String,
  ): Map<String, GeneratedTarget> {
    val services = targets(existing, scopes, listOf(document), category, emptySet())
    val models = targets(existing, scopes, listOf(document), "model", emptySet())
    return (services.keys + models.keys).associateWith { id ->
      (services[id] ?: GeneratedTarget()).copy(
        modelPackageName = models[id]?.modelPackageName,
        modelModuleName = models[id]?.modelModuleName,
      )
    }
  }

  private fun targets(
    existing: Map<String, GeneratedTarget>,
    scopes: List<GeneratedPlacementScope>,
    uris: List<String>,
    category: String,
    tags: Set<String>,
    owningService: GeneratedService? = null,
    operationUri: String? = null,
    explicitServiceUris: Set<String> = emptySet(),
    documentTargets: Map<String, GeneratedTarget> = emptyMap(),
  ): Map<String, GeneratedTarget> {
    scopes
      .filter { scope ->
        !URI(scope.declarationUri).fragment.isNullOrEmpty() && scope.declarationUri in uris && category != "service"
      }.forEach { it.placement.requireDeclaration(category, it.declarationUri) }
    val rules =
      configuration.relocations.mapIndexedNotNull { index, rule ->
        val serviceRule =
          category == "model" &&
            owningService != null &&
            (
              rule.kind == "service" ||
                rule.kind == null &&
                owningService.declarationUris.any {
                  matches(rule.target, it) && URI(rule.target).fragment == URI(it).fragment
                }
            )
        if (rule.kind != null &&
          rule.kind != category &&
          !serviceRule ||
          rule.tags.isNotEmpty() &&
          rule.tags.none { it in tags }
        ) {
          return@mapIndexedNotNull null
        }
        val locations = if (serviceRule) owningService.operations.flatMap { it.declarationUris() } else uris
        val explicitHit =
          (if (serviceRule) owningService.declarationUris else explicitServiceUris)
            .any { matches(rule.target, it) && URI(rule.target).fragment == URI(it).fragment }
        val hits = locations.map { matches(rule.target, it) }
        val operationHit =
          operationUri?.let { matches(rule.target, it) && URI(rule.target).fragment == URI(it).fragment } == true
        if (hits.none { it } && !operationHit && !explicitHit) return@mapIndexedNotNull null
        if ((category == "service" || serviceRule) && !hits.all { it } && !explicitHit) {
          require(rule.kind != "service") { "Placement selector '${rule.target}' covers only part of a service" }
          return@mapIndexedNotNull null
        }
        // A relocation selects declarations, not a new defaults container. Only a
        // service selection can additionally establish defaults for its owned models.
        val relocationScope =
          if (rule.kind == "service" || serviceRule || category == "service" && explicitHit) {
            "service"
          } else {
            rule.kind ?: "declaration"
          }
        rule.placement.requireDeclaration(relocationScope, "Placement selector '${rule.target}'")
        matched += index
        val pointer = URI(rule.target).fragment.orEmpty()
        val rank =
          when {
            explicitHit -> 20000
            pointer.isEmpty() -> 0
            uris.any { URI(it).fragment == pointer } -> 40000
            operationHit -> 30000
            else -> 10000 + pointer.count { it == '/' }
          }
        Triple(rank, index, rule.placement)
      }
    val result = existing.toMutableMap()
    for (language in listOf("kotlin", "swift", "typescript", "python").filter {
      generationLanguage == null ||
        it == generationLanguage
    }) {
      for (role in if (generationLanguage == null &&
        language == "kotlin"
      ) {
        listOf("client", "server")
      } else {
        listOf(generationRole)
      }) {
        val id = if (language == "kotlin") "kotlin" + role.replaceFirstChar(Char::uppercase) else language
        val old = existing[id] ?: existing[language] ?: GeneratedTarget()
        val retained =
          GeneratedPlacement.Scope(
            packageName = if (category == "service") old.packageName else old.modelPackageName,
            module = if (category == "service") old.moduleName else old.modelModuleName,
          )
        val documentTarget = documentTargets[id] ?: documentTargets[language]
        val documentDefaults =
          documentTarget?.let { target ->
            GeneratedPlacement.Scope(
              packageName = if (category == "service") target.packageName else target.modelPackageName,
              module = if (category == "service") target.moduleName else target.modelModuleName,
            )
          }
        val defaults = configuration.defaults.language(language)?.resolve(role, category)
        val inherited =
          scopes.asReversed().mapNotNull { it.placement.language(language)?.resolve(role, category) } +
            listOfNotNull(retained, documentDefaults, defaults) +
            if (language ==
              "python"
            ) {
              listOf(GeneratedPlacement.Scope(packageName = pythonPackageFallback))
            } else {
              emptyList()
            }

        fun field(
          name: String,
          get: (GeneratedPlacement.Scope) -> String?,
        ): String? {
          val candidates =
            rules.mapNotNull { (rank, index, placement) ->
              placement
                .language(language)
                ?.resolve(role, category)
                ?.let(get)
                ?.let { Triple(rank, index, it) }
            }
          val rank = candidates.maxOfOrNull { it.first }
          val winners = candidates.filter { it.first == rank }
          require(winners.map { it.third }.distinct().size <= 1) {
            "Conflicting $language placement for $uris: ${winners.map { configuration.relocations[it.second].target }}"
          }
          if (category == "model") {
            uris.forEach { relocationWinners[Triple(it, id, name)] = winners.map { winner -> winner.second } }
          }
          return winners.firstOrNull()?.third ?: inherited.firstNotNullOfOrNull(get)
        }
        val pkg = field("package") { it.packageName }
        val module = field("module") { it.module }
        val target =
          old.copy(
            packageName = pkg ?: old.packageName,
            modelPackageName = pkg ?: old.modelPackageName,
            moduleName = if (old.typeName == null) module ?: old.moduleName else old.moduleName,
            modelModuleName = module ?: old.modelModuleName,
          )
        if (target != GeneratedTarget()) result[id] = target
      }
    }
    return result
  }

  private fun matches(
    target: String,
    declaration: String,
  ): Boolean {
    val targetUri = URI(target)
    val declarationUri = URI(declaration)
    if (GeneratedDocumentUri.canonical(targetUri) != GeneratedDocumentUri.canonical(declarationUri)) return false
    val pointer = targetUri.fragment.orEmpty()
    val actual = declarationUri.fragment.orEmpty()
    return pointer.isEmpty() || pointer == actual || actual.startsWith("$pointer/")
  }
}
