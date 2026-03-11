package io.test

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.Request
import io.outfoxx.sunday.http.Response
import io.outfoxx.sunday.http.ResultResponse
import kotlin.Unit
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  public suspend fun fetchTest(): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun putTest(body: Test): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Put,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun postTest(body: Test): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Post,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun patchTest(body: Test): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Patch,
      pathTemplate = "/tests",
      body = body,
      contentTypes = this.defaultContentTypes,
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun deleteTest(): ResultResponse<Unit> = this.requestFactory
    .resultResponse(
      method = Method.Delete,
      pathTemplate = "/tests"
    )

  public suspend fun headTest(): ResultResponse<Unit> = this.requestFactory
    .resultResponse(
      method = Method.Head,
      pathTemplate = "/tests"
    )

  public suspend fun optionsTest(): ResultResponse<Unit> = this.requestFactory
    .resultResponse(
      method = Method.Options,
      pathTemplate = "/tests"
    )

  public suspend fun patchableTest(body: PatchableTest): ResultResponse<Test> = this.requestFactory
    .resultResponse(
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
