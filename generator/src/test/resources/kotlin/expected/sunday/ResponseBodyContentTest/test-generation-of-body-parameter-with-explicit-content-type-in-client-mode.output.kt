package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import kotlin.ByteArray
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
) {
  public suspend fun fetchTest(): ByteArray = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = listOf(MediaType.OctetStream)
    )
}
