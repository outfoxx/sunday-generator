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

package io.test.jaxrs

import io.test.jaxrs.uploads.API
import io.test.jaxrs.uploads.AvatarContentType
import io.test.jaxrs.uploads.TolerantContentType
import jakarta.ws.rs.core.Response
import java.util.Base64

/** Echoes the values delivered by the generated upload resource for HTTP assertions. */
class Uploads : API {
  override fun putRaw(
    contentType: String,
    body: ByteArray,
  ): Response = received(contentType, body)

  override fun putImage(
    contentType: String,
    body: ByteArray,
  ): Response = received(contentType, body)

  override fun putFixed(
    contentType: String,
    body: ByteArray,
  ): Response = received(contentType, body)

  override fun putEnum(
    contentType: AvatarContentType,
    body: ByteArray,
  ): Response = received(contentType.toString(), body)

  override fun putOptional(
    cOnTeNtTyPe: String?,
    body: ByteArray,
  ): Response = received(cOnTeNtTyPe, body)

  override fun putDefault(
    contentType: String,
    body: ByteArray,
  ): Response = received(contentType, body)

  override fun putConstant(
    contentType: String,
    body: ByteArray,
  ): Response = received(contentType, body)

  override fun putMultiple(
    contentType: String,
    body: ByteArray,
  ): Response = received(contentType, body)

  override fun putTolerant(
    contentType: TolerantContentType,
    body: ByteArray,
  ): Response = received(contentType.wireValue, body)

  override fun getHeader(contentType: String?): Response = received(contentType, byteArrayOf())

  private fun received(
    contentType: String?,
    body: ByteArray,
  ): Response =
    Response
      .noContent()
      .header("X-Observed-Type", contentType ?: "missing")
      .header("X-Observed-Body", Base64.getEncoder().encodeToString(body))
      .build()
}
