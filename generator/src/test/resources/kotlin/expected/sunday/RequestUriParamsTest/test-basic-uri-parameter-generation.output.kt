package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.test.Test
import kotlin.Int
import kotlin.String
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(
    def: String,
    obj: Test,
    strReq: String,
    int: Int = 5,
  ): Test = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests/{obj}/{str-req}/{int}/{def}",
      pathParameters = mapOf(
        "def" to def,
        "obj" to obj,
        "str-req" to strReq,
        "int" to int
      ),
      acceptTypes = this.defaultAcceptTypes
    )
}
