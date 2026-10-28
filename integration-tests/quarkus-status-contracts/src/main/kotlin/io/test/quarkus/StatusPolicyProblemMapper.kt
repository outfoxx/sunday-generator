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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import io.quarkiverse.resteasy.problem.HttpProblem
import io.test.quarkus.status.StatusAPI
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.ws.rs.core.Response
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper

/** Fixture decoding configured JSON/CBOR Problems using generated subtype registration. */
@Singleton
class StatusPolicyProblemMapper
  @Inject
  constructor(
    mapper: ObjectMapper,
  ) : ResponseExceptionMapper<RuntimeException> {
    private val json =
      mapper.copy().apply {
        addMixIn(HttpProblem::class.java, ProblemTypes::class.java)
        StatusAPI.registerProblems(this)
      }
    private val cbor = json.copyWith(CBORFactory())

    override fun toThrowable(response: Response): RuntimeException? {
      val format = response.mediaType?.subtype ?: return null
      if (!format.startsWith("problem+")) return null
      val mapper = if (format.endsWith("cbor")) cbor else json
      val problem = mapper.readValue(response.readEntity(ByteArray::class.java), HttpProblem::class.java)
      return if (response.getHeaderString("X-Wrap-Problem") ==
        "true"
      ) {
        IllegalStateException("wrapped", problem)
      } else {
        problem
      }
    }

    @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.PROPERTY,
      property = "type",
      visible = true,
      defaultImpl = HttpProblem::class,
    )
    private interface ProblemTypes
  }
