package io.test

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.outfoxx.sunday.http.ResultResponse
import kotlin.Int
import kotlin.collections.List
import org.zalando.problem.ThrowableProblem

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
) {
  init {
    requestFactory.registerProblem("http://example.com/test_not_found", TestNotFoundProblem::class)
    requestFactory.registerProblem("http://example.com/another_not_found",
        AnotherNotFoundProblem::class)
  }
  public suspend fun fetchTest1OrNull(limit: Int): ResultResponse<Test>? = try {
    fetchTest1(limit)
  } catch(_: TestNotFoundProblem) {
    null
  } catch(_: AnotherNotFoundProblem) {
    null
  } catch(x: ThrowableProblem) {
    when (x.status?.statusCode) {
      404, 405 -> null
      else -> throw x
    }
  }

  public suspend fun fetchTest1(limit: Int): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Get,
      pathTemplate = "/test1",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun fetchTest2OrNull(limit: Int): ResultResponse<Test>? = try {
    fetchTest2(limit)
  } catch(_: TestNotFoundProblem) {
    null
  } catch(_: AnotherNotFoundProblem) {
    null
  } catch(x: ThrowableProblem) {
    if (x.status?.statusCode == 404) {
      null
    } else {
      throw x
    }
  }

  public suspend fun fetchTest2(limit: Int): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Get,
      pathTemplate = "/test2",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun fetchTest3OrNull(limit: Int): ResultResponse<Test>? = try {
    fetchTest3(limit)
  } catch(_: TestNotFoundProblem) {
    null
  } catch(_: AnotherNotFoundProblem) {
    null
  }

  public suspend fun fetchTest3(limit: Int): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Get,
      pathTemplate = "/test3",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun fetchTest4OrNull(limit: Int): ResultResponse<Test>? = try {
    fetchTest4(limit)
  } catch(x: ThrowableProblem) {
    when (x.status?.statusCode) {
      404, 405 -> null
      else -> throw x
    }
  }

  public suspend fun fetchTest4(limit: Int): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Get,
      pathTemplate = "/test4",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    )

  public suspend fun fetchTest5OrNull(limit: Int): ResultResponse<Test>? = try {
    fetchTest5(limit)
  } catch(x: ThrowableProblem) {
    if (x.status?.statusCode == 404) {
      null
    } else {
      throw x
    }
  }

  public suspend fun fetchTest5(limit: Int): ResultResponse<Test> = this.requestFactory
    .resultResponse(
      method = Method.Get,
      pathTemplate = "/test5",
      queryParameters = mapOf(
        "limit" to limit
      ),
      acceptTypes = this.defaultAcceptTypes
    )
}
