package io.test

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.http.Response
import kotlin.Unit
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(): Test = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun putTest(body: Test): Test = this.requestFactory
    .result(
      method = Method.Put,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun postTest(body: Test): Test = this.requestFactory
    .result(
      method = Method.Post,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun patchTest(body: Test): Test = this.requestFactory
    .result(
      method = Method.Patch,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun deleteTest(): Unit = this.requestFactory
    .result(
      method = Method.Delete,
      pathTemplate = "/tests"
    )

  public suspend fun headTest(): Unit = this.requestFactory
    .result(
      method = Method.Head,
      pathTemplate = "/tests"
    )

  public suspend fun optionsTest(): Unit = this.requestFactory
    .result(
      method = Method.Options,
      pathTemplate = "/tests"
    )

  public suspend fun patchableTest(body: PatchableTest): Test = this.requestFactory
    .result(
      method = Method.Patch,
      pathTemplate = "/tests2",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun requestTest(): Request = this.requestFactory
    .request(
      method = Method.Get,
      pathTemplate = "/request",
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun responseTest(): Response = this.requestFactory
    .response(
      method = Method.Get,
      pathTemplate = "/response",
      acceptTypes = this.defaultAcceptTypes
    )
}
