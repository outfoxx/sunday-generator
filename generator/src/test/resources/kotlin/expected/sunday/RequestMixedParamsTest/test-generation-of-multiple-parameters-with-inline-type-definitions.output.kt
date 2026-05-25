package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.Operation
import io.outfoxx.sunday.OperationSpec
import io.outfoxx.sunday.Transport
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.operation
import kotlin.Any
import kotlin.String
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.Map

public class API<Req : Request>(
  public val transport: Transport<Req>,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public fun fetchTest(
    select: FetchTestSelectUriParam,
    page: FetchTestPageQueryParam,
    xType: FetchTestXTypeHeaderParam,
  ): Operation<Unit, Map<String, Any>, Req> = this.transport.operation<Unit, Map<String, Any>, Req>(
    OperationSpec(
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
