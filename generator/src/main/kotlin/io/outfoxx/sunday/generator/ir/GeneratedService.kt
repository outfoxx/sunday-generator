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

/**
 * Generated service declaration.
 */
data class GeneratedService(
  val name: String,
  /** Canonical document URIs contributing to this complete service declaration. */
  val ownerUris: Set<String> = emptySet(),
  val baseUri: String? = null,
  val baseUriParameters: List<GeneratedParameter> = listOf(),
  val group: String? = null,
  val operations: List<GeneratedOperation> = listOf(),
  val auth: GeneratedAuth? = null,
  val jaxrs: GeneratedJaxrs? = null,
  val protocol: GeneratedProtocol? = null,
  val media: GeneratedMedia? = null,
  val documentation: GeneratedDocumentation? = null,
  /** Effective server alternatives shared by every operation in this service. */
  val servers: List<GeneratedServer> = listOf(),
  /** Resolved target metadata for this declaration. */
  val targets: Map<String, GeneratedTarget> = emptyMap(),
  /** Original lexical placement scopes; references do not add scopes. */
  val placementScopes: List<GeneratedPlacementScope> = emptyList(),
  /** Tags that actually established this service grouping. */
  val placementTags: Set<String> = emptySet(),
  /** Source objects explicitly declaring this service, such as tag definitions. */
  val declarationUris: Set<String> = emptySet(),
  /** Individual source contributions retained when composition combines a service. */
  val placementDeclarations: List<PlacementContribution> = emptyList(),
) {
  /** Placement provenance retained without copying operation behavior or security policy. */
  data class PlacementContribution(
    val operationUris: List<String> = emptyList(),
    val placementScopes: List<GeneratedPlacementScope> = emptyList(),
    val targets: Map<String, GeneratedTarget> = emptyMap(),
  )

  internal fun placementContribution() =
    PlacementContribution(
      operations.flatMap { it.declarationUris() },
      placementScopes,
      targets,
    )
}
