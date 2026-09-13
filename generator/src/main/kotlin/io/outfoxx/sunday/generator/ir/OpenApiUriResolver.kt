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

import java.net.URI

/** RFC 3986 resolution on raw components, without decoding or re-encoding document identifiers. */
internal object OpenApiUriResolver {
  fun resolve(
    base: URI,
    reference: String,
  ): URI {
    val relative = URI(reference)
    if (reference.isEmpty() || reference.startsWith('#')) {
      return URI(base.toString().substringBefore('#') + reference)
    }
    if (relative.isOpaque) return relative
    if (base.isOpaque && !relative.isAbsolute) return base.resolve(relative)

    val scheme = relative.scheme ?: base.scheme
    val authority: String?
    val path: String
    val query: String?
    when {
      relative.scheme != null -> {
        authority = relative.rawAuthority
        path = removeDotSegments(relative.rawPath.orEmpty())
        query = relative.rawQuery
      }
      relative.rawAuthority != null -> {
        authority = relative.rawAuthority
        path = removeDotSegments(relative.rawPath.orEmpty())
        query = relative.rawQuery
      }
      relative.rawPath.isNullOrEmpty() -> {
        authority = base.rawAuthority
        path = base.rawPath.orEmpty()
        query = relative.rawQuery ?: base.rawQuery
      }
      else -> {
        authority = base.rawAuthority
        val relativePath = relative.rawPath
        val merged =
          if (relativePath.startsWith('/')) {
            relativePath
          } else if (authority != null && base.rawPath.isNullOrEmpty()) {
            "/$relativePath"
          } else {
            base.rawPath
              .orEmpty()
              .substringBeforeLast('/', "")
              .let { prefix -> if (base.rawPath.orEmpty().contains('/')) "$prefix/" else "" } + relativePath
          }
        path = removeDotSegments(merged)
        query = relative.rawQuery
      }
    }
    return URI(
      buildString {
        scheme?.let { append(it).append(':') }
        authority?.let { append("//").append(it) }
        append(path)
        query?.let { append('?').append(it) }
        relative.rawFragment?.let { append('#').append(it) }
      },
    )
  }

  private fun removeDotSegments(path: String): String {
    var remaining = path
    val output = StringBuilder()
    while (remaining.isNotEmpty()) {
      when {
        remaining.startsWith("../") -> remaining = remaining.drop(3)
        remaining.startsWith("./") -> remaining = remaining.drop(2)
        remaining.startsWith("/./") -> remaining = remaining.drop(2)
        remaining == "/." -> remaining = "/"
        remaining.startsWith("/../") || remaining == "/.." -> {
          remaining = if (remaining == "/..") "/" else remaining.drop(3)
          val slash = output.lastIndexOf("/")
          output.setLength(if (slash < 0) 0 else slash)
        }
        remaining == "." || remaining == ".." -> remaining = ""
        else -> {
          val end =
            remaining
              .indexOf('/', if (remaining.startsWith('/')) 1 else 0)
              .takeIf { it >= 0 } ?: remaining.length
          output.append(remaining.take(end))
          remaining = remaining.drop(end)
        }
      }
    }
    return output.toString()
  }
}
