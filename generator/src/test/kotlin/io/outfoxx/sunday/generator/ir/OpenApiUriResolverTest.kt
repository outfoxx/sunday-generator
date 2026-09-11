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

package io.outfoxx.sunday.generator.ir

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.URI

class OpenApiUriResolverTest {
  @Test
  fun `document canonicalization removes fragments and dot segments without collapsing empty segments`() {
    mapOf(
      "https://example.test/schemas//item.yaml#node" to "https://example.test/schemas//item.yaml",
      "https://example.test/schemas//./item.yaml?v=%2F#node" to "https://example.test/schemas//item.yaml?v=%2F",
      "https://example.test/a//b/../c%2Fd?x=//" to "https://example.test/a//c%2Fd?x=//",
      "https://example.test/a//../b" to "https://example.test/a/b",
      "file:///tmp/a/../b%20c.yaml#node" to "file:/tmp/b%20c.yaml",
      "urn:example:a/../b#node" to "urn:example:a/../b",
    ).forEach { (source, expected) ->
      assertEquals(URI(expected), URI(source).openApiDocumentUri(), source)
    }
  }

  @Test
  fun `resolves RFC 3986 examples without changing raw components`() {
    val base = URI("http://a/b/c/d;p?q")
    mapOf(
      "?y" to "http://a/b/c/d;p?y",
      "?" to "http://a/b/c/d;p?",
      "?y#s" to "http://a/b/c/d;p?y#s",
      "" to "http://a/b/c/d;p?q",
      "#s" to "http://a/b/c/d;p?q#s",
      "g:h" to "g:h",
      "g" to "http://a/b/c/g",
      "./g" to "http://a/b/c/g",
      "g/" to "http://a/b/c/g/",
      "/g" to "http://a/g",
      "//g" to "http://g",
      "g?y" to "http://a/b/c/g?y",
      "g#s" to "http://a/b/c/g#s",
      "." to "http://a/b/c/",
      ".." to "http://a/b/",
      "../../../g" to "http://a/g",
      "/../g" to "http://a/g",
      "g/../h" to "http://a/b/c/h",
      "g?y/../x" to "http://a/b/c/g?y/../x",
      "g#s/../x" to "http://a/b/c/g#s/../x",
      "./g//h" to "http://a/b/c/g//h",
      "x%20y?z=%2F#%24defs" to "http://a/b/c/x%20y?z=%2F#%24defs",
    ).forEach { (reference, expected) ->
      assertEquals(URI(expected), OpenApiUriResolver.resolve(base, reference), reference)
    }
    assertEquals(
      URI("https://example.test/a%20b?new=%2F#s"),
      OpenApiUriResolver.resolve(URI("https://example.test/a%20b?old#anchor"), "?new=%2F#s"),
    )
    assertEquals(URI("urn:example:node#child"), OpenApiUriResolver.resolve(URI("urn:example:node#parent"), "#child"))
    assertEquals(URI("urn:example:node"), OpenApiUriResolver.resolve(URI("urn:example:node#parent"), ""))
  }
}
