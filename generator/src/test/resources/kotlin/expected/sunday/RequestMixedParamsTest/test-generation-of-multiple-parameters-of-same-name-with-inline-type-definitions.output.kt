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
    type: FetchTestTypeUriParam,
    type_: FetchTestTypeQueryParam,
    type__: FetchTestTypeHeaderParam,
  ): Operation<Unit, Map<String, Any>, Req> = this.transport.operation<Unit, Map<String, Any>, Req>(
    OperationSpec(
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
