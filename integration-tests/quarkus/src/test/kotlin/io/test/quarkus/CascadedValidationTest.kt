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

import io.quarkus.test.common.http.TestHTTPResource
import io.quarkus.test.junit.QuarkusTest
import io.test.quarkus.validation.Wrapper
import jakarta.inject.Inject
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@QuarkusTest
class CascadedValidationTest {
  @TestHTTPResource
  lateinit var baseUri: URI

  @Inject
  lateinit var validator: Validator

  @Test
  fun `nested object union and map properties are cascaded`() {
    // Constructor guards also reject invalid values, so assert the validator metadata independently.
    val metadata = validator.getConstraintsForClass(Wrapper::class.java)
    for (property in listOf("child", "choice")) {
      assertTrue(metadata.getConstraintsForProperty(property)?.isCascaded == true, property)
    }
    val map = requireNotNull(metadata.getConstraintsForProperty("childMap"))
    assertFalse(map.isCascaded, "The map itself must not use legacy cascading")
    assertTrue(map.constrainedContainerElementTypes.any { it.typeArgumentIndex == 1 && it.isCascaded }, "map values")
    assertFalse(map.constrainedContainerElementTypes.any { it.typeArgumentIndex == 0 && it.isCascaded }, "string keys")
  }

  @Test
  fun `nested invalid requests return 400 and valid requests reach the handler`() {
    HttpClient.newHttpClient().use { client ->
      for (template in listOf(
        """{"child":{"name":"VALUE"}}""",
        """{"choice":{"kind":"cat","name":"VALUE"}}""",
        """{"choice":{"kind":"dog","name":"VALUE"}}""",
        """{"childMap":{"first":{"name":"VALUE"}}}""",
        """{"choiceMap":{"first":{"kind":"cat","name":"VALUE"}}}""",
        """{"groups":{"first":[{"name":"VALUE"}]}}""",
        """{"children":[{"name":"VALUE"}]}""",
      )) {
        // Email is enforced by Bean Validation, independently of constructor pattern checks.
        for ((name, expected) in listOf(
          "valid" to 204,
          "BAD" to 400,
          "valid\",\"email\":\"valid@example.test" to 204,
          "valid\",\"email\":\"bad" to 400,
        )) {
          val json = template.replace("VALUE", name)
          val response =
            client.send(
              HttpRequest
                .newBuilder(baseUri.resolve("/w"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
              HttpResponse.BodyHandlers.ofString(),
            )
          assertEquals(expected, response.statusCode(), "$json: ${response.body()}")
        }
      }
    }
  }
}
