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

import io.test.quarkus.uploads.API
import io.test.quarkus.uploads.AvatarContentType
import io.test.quarkus.uploads.TolerantContentType
import jakarta.inject.Singleton
import org.jboss.resteasy.reactive.RestResponse
import java.util.Base64

/** Echoes the values delivered by the generated upload resource for HTTP assertions. */
@Singleton
class Uploads : API {
  override fun putRaw(
    contentType: String,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType, body)

  override fun putImage(
    contentType: String,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType, body)

  override fun putFixed(
    contentType: String,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType, body)

  override fun putEnum(
    contentType: AvatarContentType,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType.toString(), body)

  override fun putOptional(
    cOnTeNtTyPe: String?,
    body: ByteArray,
  ): RestResponse<Unit> = received(cOnTeNtTyPe, body)

  override fun putDefault(
    contentType: String,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType, body)

  override fun putConstant(
    contentType: String,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType, body)

  override fun putMultiple(
    contentType: String,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType, body)

  override fun putTolerant(
    contentType: TolerantContentType,
    body: ByteArray,
  ): RestResponse<Unit> = received(contentType.wireValue, body)

  override fun getHeader(contentType: String?): RestResponse<Unit> = received(contentType, byteArrayOf())

  private fun received(
    contentType: String?,
    body: ByteArray,
  ): RestResponse<Unit> =
    RestResponse.ResponseBuilder
      .create<Unit>(204)
      .header("X-Observed-Type", contentType ?: "missing")
      .header("X-Observed-Body", Base64.getEncoder().encodeToString(body))
      .build()
}
