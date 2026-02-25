package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import kotlin.Any
import kotlin.ByteArray
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(body: ByteArray): Map<String, Any> = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      body = body,
      contentTypes = listOf(MediaType.OctetStream),
      acceptTypes = this.defaultAcceptTypes
    )
}
