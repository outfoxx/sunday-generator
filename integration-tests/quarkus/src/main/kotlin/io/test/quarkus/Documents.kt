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

import io.quarkus.security.credential.TokenCredential
import io.quarkus.security.identity.SecurityIdentity
import io.test.quarkus.zanzibar.DocumentsAPI
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.eclipse.microprofile.jwt.JsonWebToken
import org.jboss.resteasy.reactive.RestResponse
import java.util.concurrent.atomic.AtomicInteger

/** Checks that endpoint policies publish a complete native identity before delegation. */
@Singleton
class Documents : DocumentsAPI {
  @Inject lateinit var identity: SecurityIdentity

  @Inject lateinit var jwt: JsonWebToken
  val calls = AtomicInteger()

  override fun readDocument(documentId: String): RestResponse<String> = respond(documentId)

  override fun writeDocument(documentId: String): RestResponse<String> = respond(documentId)

  override fun combinedDocument(documentId: String): RestResponse<String> = respond(documentId)

  override fun simpleDocument(documentId: String): RestResponse<String> = respond(documentId)

  override fun publicDocument(documentId: String): RestResponse<String> = respond(documentId)

  override fun ignoredDocument(documentId: String): RestResponse<String> {
    calls.incrementAndGet()
    return RestResponse.ok("public")
  }

  private fun respond(documentId: String): RestResponse<String> {
    calls.incrementAndGet()
    check(identity.principal is JsonWebToken)
    check(identity.principal.name == jwt.name)
    check(identity.getCredential(TokenCredential::class.java) != null)
    return RestResponse.ok("$documentId:${jwt.subject}:${identity.getAttribute<String>("binding") ?: "framework"}")
  }
}
