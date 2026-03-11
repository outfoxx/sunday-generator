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
    def2: Int? = 10,
    obj: Test? = null,
    str: String? = null,
    def1: String? = "test",
    int: Int? = null,
    def: String,
  ): Test = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}",
      pathParameters = mapOf(
        "def2" to def2,
        "obj" to obj,
        "str" to str,
        "def1" to def1,
        "int" to int,
        "def" to def
      ).filterValues { it != null },
      acceptTypes = this.defaultAcceptTypes
    )
}
