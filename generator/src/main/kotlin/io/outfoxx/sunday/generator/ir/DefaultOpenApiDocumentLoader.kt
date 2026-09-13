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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One loading session; persistent cache locking also coordinates other sessions and processes. */
internal class DefaultOpenApiDocumentLoader(
  private val options: OpenApiReferenceOptions,
  private val networkPolicy: OpenApiNetworkPolicy = OpenApiNetworkPolicy(options),
  private val clientFactory: (ProxySelector) -> HttpClient = { proxySelector ->
    HttpClient
      .newBuilder()
      .connectTimeout(options.connectionTimeout)
      .followRedirects(HttpClient.Redirect.NEVER)
      .proxy(proxySelector)
      .build()
  },
) : OpenApiDocumentLoader {
  private val documents = mutableMapOf<URI, OpenApiLoadedDocument>()
  private val remoteDocuments = mutableMapOf<URI, CachedDocument>()
  private val redirectsByUri = mutableMapOf<URI, URI>()
  private val mapper = ObjectMapper(YAMLFactory()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

  @Synchronized
  override fun load(uri: URI): OpenApiLoadedDocument {
    val source = uri.openApiDocumentUri()
    documents[source]?.let { return it }
    val document =
      when (source.scheme?.lowercase()) {
        "file" -> {
          val path = Path.of(source).normalize()
          val bytes = Files.newInputStream(path).use { it.readNBytes(options.maximumDocumentBytes + 1) }
          requireSize(bytes.size)
          OpenApiLoadedDocument(path.toUri(), bytes)
        }
        "http", "https" -> loadRemote(source)
        else -> throw IOException("Unsupported OpenAPI retrieval scheme in '$source'; expected file, http, or https")
      }
    documents[source] = document
    documents.putIfAbsent(document.uri, document)
    return document
  }

  private fun loadRemote(uri: URI): OpenApiLoadedDocument {
    if (uri.userInfo != null) throw IOException("Authenticated OpenAPI URLs are not supported: $uri")
    val aliases = linkedSetOf<URI>()
    val fetched =
      withCacheLock(uri) { directory ->
        val cached = readCache(directory)
        if (options.offline) {
          cached ?: throw IOException("OpenAPI offline cache miss for '$uri'")
        } else {
          fetch(uri, cached, aliases).also { result ->
            // Parse before replacing any alias's usable entry. Online failures never use stale content.
            if (mapper.readTree(result.document.bytes) == null) throw IOException("Empty OpenAPI document at '$uri'")
            writeCache(directory, result)
          }
        }
      }
    aliases.add(uri)
    aliases.add(fetched.document.uri)
    if (!options.offline) {
      // Release the requested URI's lock before writing aliases: crossed redirect chains must not deadlock.
      aliases.filter { it != uri }.forEach { alias ->
        withCacheLock(alias) { directory -> writeCache(directory, fetched) }
      }
    }
    aliases.forEach { alias ->
      documents.putIfAbsent(alias, fetched.document)
      remoteDocuments.putIfAbsent(alias, fetched)
    }
    return fetched.document
  }

  private fun <T> withCacheLock(
    uri: URI,
    action: (Path) -> T,
  ): T {
    val directory = options.cacheDirectory.resolve("v1").resolve(uri.toString().toByteArray().sha256())
    Files.createDirectories(directory)
    FileChannel.open(directory.resolve("lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
      // JVM file locks reject overlapping locks instead of waiting; retry also coordinates separate loader instances.
      var lock = runCatching { channel.tryLock() }.getOrNull()
      while (lock == null) {
        Thread.sleep(10)
        lock =
          try {
            channel.tryLock()
          } catch (_: OverlappingFileLockException) {
            null
          }
      }
      lock.use {
        return action(directory)
      }
    }
  }

  private fun fetch(
    uri: URI,
    cached: CachedDocument?,
    aliases: MutableSet<URI>,
  ): CachedDocument =
    clientFactory(networkPolicy).use { client ->
      var current = uri
      val visited = mutableSetOf<URI>()
      repeat(options.maximumRedirects + 1) { redirects ->
        if (!visited.add(current)) throw IOException("OpenAPI redirect cycle at '$current'")
        aliases.add(current)
        documents[current]?.let { return remoteDocuments[current] ?: CachedDocument(it, null, null) }
        redirectsByUri[current]?.let { next ->
          if (redirects == options.maximumRedirects) throw IOException("Too many OpenAPI redirects for '$uri'")
          current = next
          return@repeat
        }
        networkPolicy.validate(current)
        val request = HttpRequest.newBuilder(current).timeout(options.requestTimeout).GET()
        if (cached?.document?.uri == current) {
          cached.etag?.let { request.header("If-None-Match", it) }
          cached.lastModified?.let { request.header("If-Modified-Since", it) }
        }
        val future = client.sendAsync(request.build()) { LimitedBodySubscriber(options.maximumDocumentBytes) }
        val response =
          try {
            future.get(options.requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
          } catch (exception: TimeoutException) {
            future.cancel(true)
            throw IOException("OpenAPI request timed out for '$current'", exception)
          } catch (exception: ExecutionException) {
            throw IOException(
              "Cannot retrieve OpenAPI document '$current': ${exception.cause?.message}",
              exception.cause,
            )
          } catch (exception: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw IOException("Interrupted retrieving OpenAPI document '$current'", exception)
          }
        when (response.statusCode()) {
          200 -> return CachedDocument(
            OpenApiLoadedDocument(current, response.body()),
            response.headers().firstValue("ETag").orElse(null),
            response.headers().firstValue("Last-Modified").orElse(null),
          )
          304 -> return cached?.takeIf { it.document.uri == current }
            ?: throw IOException("Unexpected HTTP 304 for uncached OpenAPI document '$current'")
          301, 302, 303, 307, 308 -> {
            if (redirects == options.maximumRedirects) throw IOException("Too many OpenAPI redirects for '$uri'")
            val location =
              response.headers().firstValue("Location").orElseThrow {
                IOException("Missing redirect Location at '$current'")
              }
            val next = OpenApiUriResolver.resolve(current, location).openApiDocumentUri()
            if (next.scheme?.lowercase() !in setOf("http", "https") || next.userInfo != null) {
              throw IOException("Unsupported OpenAPI redirect from '$current' to '$next'")
            }
            if (current.scheme.equals("https", ignoreCase = true) && !next.scheme.equals("https", ignoreCase = true)) {
              throw IOException("OpenAPI HTTPS-to-HTTP redirect is not allowed: '$current' to '$next'")
            }
            redirectsByUri[current] = next
            current = next
          }
          else -> throw IOException("HTTP ${response.statusCode()} retrieving OpenAPI document '$current'")
        }
      }
      error("Redirect limit must have terminated retrieval")
    }

  private fun readCache(directory: Path): CachedDocument? =
    runCatching {
      val metadata = mapper.readTree(directory.resolve("metadata.yaml").toFile())
      val digest = metadata.path("sha256").asText()
      if (!Regex("[0-9a-f]{64}").matches(digest)) return null
      val body = directory.resolve("$digest.body")
      if (Files.size(body) > options.maximumDocumentBytes) return null
      val bytes = Files.readAllBytes(body)
      if (bytes.sha256() != digest) return null
      CachedDocument(
        OpenApiLoadedDocument(URI(metadata.path("uri").asText()), bytes),
        metadata["etag"]?.takeUnless { it.isNull }?.asText(),
        metadata["lastModified"]?.takeUnless { it.isNull }?.asText(),
      )
    }.getOrNull()

  private fun writeCache(
    directory: Path,
    cached: CachedDocument,
  ) {
    val bytes = cached.document.bytes
    val digest = bytes.sha256()
    directory.resolve("$digest.body").writeOpenApiContent(bytes)
    val metadata =
      mapOf(
        "uri" to cached.document.uri.toString(),
        "sha256" to digest,
        "etag" to cached.etag,
        "lastModified" to cached.lastModified,
      )
    directory.resolve("metadata.yaml").writeOpenApiContent(mapper.writeValueAsBytes(metadata))
  }

  private fun requireSize(size: Int) {
    if (size >
      options.maximumDocumentBytes
    ) {
      throw IOException("OpenAPI document exceeds ${options.maximumDocumentBytes} bytes")
    }
  }

  private data class CachedDocument(
    val document: OpenApiLoadedDocument,
    val etag: String?,
    val lastModified: String?,
  )

  /** Bounds memory while keeping the HTTP future incomplete until the entire body has arrived. */
  private class LimitedBodySubscriber(
    private val maximumBytes: Int,
  ) : HttpResponse.BodySubscriber<ByteArray> {
    private val result = CompletableFuture<ByteArray>()
    private val content = ByteArrayOutputStream()
    private lateinit var subscription: Flow.Subscription

    override fun getBody(): CompletionStage<ByteArray> = result

    override fun onSubscribe(subscription: Flow.Subscription) {
      this.subscription = subscription
      subscription.request(1)
    }

    override fun onNext(items: List<ByteBuffer>) {
      for (item in items) {
        if (item.remaining() > maximumBytes - content.size()) {
          subscription.cancel()
          result.completeExceptionally(IOException("OpenAPI document exceeds $maximumBytes bytes"))
          return
        }
        val bytes = ByteArray(item.remaining())
        item.get(bytes)
        content.write(bytes)
      }
      subscription.request(1)
    }

    override fun onError(throwable: Throwable) {
      result.completeExceptionally(throwable)
    }

    override fun onComplete() {
      result.complete(content.toByteArray())
    }
  }
}

internal fun ByteArray.sha256(): String =
  MessageDigest.getInstance("SHA-256").digest(this).joinToString("") {
    "%02x".format(it)
  }

internal fun Path.writeOpenApiContent(bytes: ByteArray) {
  if (Files.exists(this) && Files.readAllBytes(this).contentEquals(bytes)) return
  Files.createDirectories(parent)
  val temporary = Files.createTempFile(parent, ".openapi-", ".tmp")
  try {
    Files.write(temporary, bytes)
    try {
      Files.move(temporary, this, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
      Files.move(temporary, this, StandardCopyOption.REPLACE_EXISTING)
    }
  } finally {
    Files.deleteIfExists(temporary)
  }
}
