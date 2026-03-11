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
    obj: Test? = null,
    str: String? = null,
    int: Int? = null,
    def1: String? = "test",
    def2: Int? = 10,
  ): Test = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      queryParameters = mapOf(
        "obj" to obj,
        "str" to str,
        "int" to int,
        "def1" to def1,
        "def2" to def2
      ).filterValues { it != null },
      acceptTypes = this.defaultAcceptTypes
    )
}
