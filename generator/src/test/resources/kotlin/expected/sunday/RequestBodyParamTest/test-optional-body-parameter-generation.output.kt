package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.test.Test
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(body: Test?): Test = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )
}
