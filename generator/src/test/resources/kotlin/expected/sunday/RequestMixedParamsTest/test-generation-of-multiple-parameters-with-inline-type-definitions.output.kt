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
  public suspend fun fetchTest(
    select: FetchTestSelectUriParam,
    page: FetchTestPageQueryParam,
    xType: FetchTestXTypeHeaderParam,
  ): Map<String, Any> = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests/{select}",
      pathParameters = mapOf(
        "select" to select
      ),
      queryParameters = mapOf(
        "page" to page
      ),
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "x-type" to xType
      )
    )

  public enum class FetchTestSelectUriParam {
    All,
    Limited,
  }

  public enum class FetchTestPageQueryParam {
    All,
    Limited,
  }

  public enum class FetchTestXTypeHeaderParam {
    All,
    Limited,
  }
}
