package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.test.InvalidIdProblem
import io.test.Test
import io.test.TestNotFoundProblem
import kotlin.collections.List

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  init {
    requestFactory.registerProblem("http://example.com/invalid_id", InvalidIdProblem::class)
    requestFactory.registerProblem("http://example.com/test_not_found", TestNotFoundProblem::class)
  }
  public suspend fun fetchTest(): Test = this.requestFactory
    .result(
      method = Method.Get,
      pathTemplate = "/tests",
      acceptTypes = this.defaultAcceptTypes
    )
}
