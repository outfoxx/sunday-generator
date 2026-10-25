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

import io.test.inherited.Metadata
import io.test.inherited.QueuedProblem
import io.test.inherited.TeamNotFoundProblem
import io.test.inherited.UserNotFoundProblem
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam

/** Exercises generated throwable inheritance through the runtime's exception mapper and postprocessors. */
@Path("/")
class InheritedProblemEndpoint {
  /** Throws a generated problem with fields declared at three inheritance levels. */
  @GET
  @Path("inherited-problems")
  fun readResource(): String =
    throw QueuedProblem(
      nullableChild = null,
      metadata = Metadata(region = "west"),
    )

  /** Throws either concrete problem declared for the shared 404 response. */
  @GET
  @Path("missing/{kind}")
  fun readMissing(
    @PathParam("kind") kind: String,
  ): String = throw if (kind == "team") TeamNotFoundProblem() else UserNotFoundProblem()

  /** Exercises the named union response through the same exception mapper. */
  @GET
  @Path("missing-named/{kind}")
  fun readMissingNamed(
    @PathParam("kind") kind: String,
  ): String = readMissing(kind)
}
