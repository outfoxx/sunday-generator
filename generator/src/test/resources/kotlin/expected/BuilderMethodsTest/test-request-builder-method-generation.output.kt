package io.test

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(): Request = this.requestFactory
    .request(
      method = Method.Get,
      pathTemplate = "/test/request",
      acceptTypes = this.defaultAcceptTypes
    )
}
