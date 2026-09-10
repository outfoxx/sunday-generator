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

package io.test.quarkus

import io.test.quarkus.api.RamlAccessAPI
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.atomic.AtomicInteger

/** Application-owned RAML handler that records whether endpoint security allowed delegation. */
@Singleton
class RamlAccess : RamlAccessAPI {
  /** Number of operations that passed endpoint authentication. */
  val calls = AtomicInteger()

  override fun publicAccess(): RestResponse<Unit> = accept()

  override fun mixedAccess(): RestResponse<Unit> = accept()

  override fun inheritedAccess(): RestResponse<Unit> = accept()

  override fun resourcePublic(): RestResponse<Unit> = accept()

  override fun methodProtected(): RestResponse<Unit> = accept()

  override fun nestedInherited(): RestResponse<Unit> = accept()

  override fun replacementAccess(): RestResponse<Unit> = accept()

  override fun traitPublic(): RestResponse<Unit> = accept()

  private fun accept(): RestResponse<Unit> {
    calls.incrementAndGet()
    return RestResponse.noContent()
  }
}
