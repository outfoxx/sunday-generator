package io.test

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import kotlin.Int
import org.zalando.problem.ThrowableProblem

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  public fun fetchTest1OrNull(limit: Int): Test? = try {
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
  public fun fetchTest1(@QueryParam(value = "limit") limit: Int): Test

  public fun fetchTest2OrNull(limit: Int): Test? = try {
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
  public fun fetchTest2(@QueryParam(value = "limit") limit: Int): Test

  public fun fetchTest3OrNull(limit: Int): Test? = try {
    fetchTest3(limit)
  } catch(_: TestNotFoundProblem) {
    null
  } catch(_: AnotherNotFoundProblem) {
    null
  }

  @GET
  @Path(value = "/test3")
  public fun fetchTest3(@QueryParam(value = "limit") limit: Int): Test

  public fun fetchTest4OrNull(limit: Int): Test? = try {
    fetchTest4(limit)
  } catch(x: ThrowableProblem) {
    when (x.status?.statusCode) {
      404, 405 -> null
      else -> throw x
    }
  }

  @GET
  @Path(value = "/test4")
  public fun fetchTest4(@QueryParam(value = "limit") limit: Int): Test

  public fun fetchTest5OrNull(limit: Int): Test? = try {
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
  public fun fetchTest5(@QueryParam(value = "limit") limit: Int): Test
}
