package io.test.service

import io.test.AnotherNotFoundProblem
import io.test.Test
import io.test.TestNotFoundProblem
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Int
import org.jboss.resteasy.reactive.RestQuery
import org.zalando.problem.ThrowableProblem

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  public suspend fun fetchTest1OrNull(limit: Int): Test? = try {
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

  @GET
  @Path(value = "/test1")
  public suspend fun fetchTest1(@RestQuery limit: Int): Test

  public suspend fun fetchTest2OrNull(limit: Int): Test? = try {
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

  @GET
  @Path(value = "/test2")
  public suspend fun fetchTest2(@RestQuery limit: Int): Test

  public suspend fun fetchTest3OrNull(limit: Int): Test? = try {
    fetchTest3(limit)
  } catch(_: TestNotFoundProblem) {
    null
  } catch(_: AnotherNotFoundProblem) {
    null
  }

  @GET
  @Path(value = "/test3")
  public suspend fun fetchTest3(@RestQuery limit: Int): Test

  public suspend fun fetchTest4OrNull(limit: Int): Test? = try {
    fetchTest4(limit)
  } catch(x: ThrowableProblem) {
    when (x.status?.statusCode) {
      404, 405 -> null
      else -> throw x
    }
  }

  @GET
  @Path(value = "/test4")
  public suspend fun fetchTest4(@RestQuery limit: Int): Test

  public suspend fun fetchTest5OrNull(limit: Int): Test? = try {
    fetchTest5(limit)
  } catch(x: ThrowableProblem) {
    if (x.status?.statusCode == 404) {
      null
    } else {
      throw x
    }
  }

  @GET
  @Path(value = "/test5")
  public suspend fun fetchTest5(@RestQuery limit: Int): Test
}
