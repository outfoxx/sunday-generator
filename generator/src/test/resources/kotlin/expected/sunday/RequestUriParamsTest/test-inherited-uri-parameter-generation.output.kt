package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(
    obj: Map<String, Any>,
    str: String,
    def: String,
    int: Int,
  ): Map<String, Any> = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests/{obj}/{str}/{int}/{def}",
      pathParameters = mapOf(
        "obj" to obj,
        "str" to str,
        "def" to def,
        "int" to int
      ),
      acceptTypes = this.defaultAcceptTypes
    )
}
