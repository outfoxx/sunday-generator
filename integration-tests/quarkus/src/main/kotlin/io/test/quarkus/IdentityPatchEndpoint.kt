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

package io.test.quarkus

import io.test.quarkus.patch.IdentityUpdatePatch
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response

/** Exercises unknown-field handling on polymorphic PATCH models with asymmetric sibling fields. */
@Path("/identity")
class IdentityPatchEndpoint {
  /** Accepts a partial identity update after Jackson deserialization. */
  @PATCH
  @Consumes("application/json")
  fun update(body: IdentityUpdatePatch): Response =
    Response.noContent().header("X-Patch-Type", body.javaClass.simpleName).build()
}
