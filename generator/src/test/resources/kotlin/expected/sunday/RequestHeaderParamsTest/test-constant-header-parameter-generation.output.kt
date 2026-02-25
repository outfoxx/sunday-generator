package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.test.Test
import kotlin.String
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun putTest(xCustom: String): Test = this.requestFactory
    .result(
      method = Method.Put,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "Expect" to "100-continue",
        "x-custom" to xCustom
      )
    )
}
