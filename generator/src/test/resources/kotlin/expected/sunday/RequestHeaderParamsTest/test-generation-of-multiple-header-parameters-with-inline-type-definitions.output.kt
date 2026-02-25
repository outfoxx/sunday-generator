package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import kotlin.Any
import kotlin.String
import kotlin.collections.List
import kotlin.collections.Map

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(category: FetchTestCategoryHeaderParam,
      type: FetchTestTypeHeaderParam): Map<String, Any> = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "category" to category,
        "type" to type
      )
    )

  public enum class FetchTestCategoryHeaderParam {
    Politics,
    Science,
  }

  public enum class FetchTestTypeHeaderParam {
    All,
    Limited,
  }
}
