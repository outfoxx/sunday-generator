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

package io.test.packaged

import jakarta.inject.Singleton
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.resteasy.reactive.RestResponse

/** Browser login delegates token propagation to the generated native REST client. */
@Singleton
class WebDelegate(
  @RestClient private val client: io.test.packaged.client.API,
) : io.test.packaged.web.API {
  override fun protectedCall(): RestResponse<String> = RestResponse.ok(client.userCall())

  override fun publicCall(): RestResponse<String> = RestResponse.ok("public")
}
