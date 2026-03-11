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
    type: FetchTestTypeUriParam,
    type_: FetchTestTypeQueryParam,
    type__: FetchTestTypeHeaderParam,
  ): Map<String, Any> = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests/{type}",
      pathParameters = mapOf(
        "type" to type
      ),
      queryParameters = mapOf(
        "type" to type_
      ),
      acceptTypes = this.defaultAcceptTypes,
      headers = mapOf(
        "type" to type__
      )
    )

  public enum class FetchTestTypeUriParam {
    All,
    Limited,
  }

  public enum class FetchTestTypeQueryParam {
    All,
    Limited,
  }

  public enum class FetchTestTypeHeaderParam {
    All,
    Limited,
  }
}
