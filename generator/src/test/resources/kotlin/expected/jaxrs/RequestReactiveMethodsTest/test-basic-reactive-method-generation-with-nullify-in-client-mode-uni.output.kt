package io.test.service

import io.smallrye.mutiny.Uni
import io.test.AnotherNotFoundProblem
import io.test.Test
import io.test.TestNotFoundProblem
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
  public fun fetchTest1OrNull(limit: Int): Uni<Test?> = fetchTest1(limit)
    .onFailure().recoverWithItem { x ->
      when {
        x is TestNotFoundProblem -> null
        x is AnotherNotFoundProblem -> null
        x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
            null
        else -> throw x
      }
    }

  @GET
  @Path(value = "/test1")
  public fun fetchTest1(@QueryParam(value = "limit") limit: Int): Uni<Test>

  public fun fetchTest2OrNull(limit: Int): Uni<Test?> = fetchTest2(limit)
    .onFailure().recoverWithItem { x ->
      when {
        x is TestNotFoundProblem -> null
        x is AnotherNotFoundProblem -> null
        x is ThrowableProblem && x.status?.statusCode == 404 -> null
        else -> throw x
      }
    }

  @GET
  @Path(value = "/test2")
  public fun fetchTest2(@QueryParam(value = "limit") limit: Int): Uni<Test>

  public fun fetchTest3OrNull(limit: Int): Uni<Test?> = fetchTest3(limit)
    .onFailure().recoverWithItem { x ->
      when {
        x is TestNotFoundProblem -> null
        x is AnotherNotFoundProblem -> null
        else -> throw x
      }
    }

  @GET
  @Path(value = "/test3")
  public fun fetchTest3(@QueryParam(value = "limit") limit: Int): Uni<Test>

  public fun fetchTest4OrNull(limit: Int): Uni<Test?> = fetchTest4(limit)
    .onFailure().recoverWithItem { x ->
      when {
        x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
            null
        else -> throw x
      }
    }

  @GET
  @Path(value = "/test4")
  public fun fetchTest4(@QueryParam(value = "limit") limit: Int): Uni<Test>

  public fun fetchTest5OrNull(limit: Int): Uni<Test?> = fetchTest5(limit)
    .onFailure().recoverWithItem { x ->
      when {
        x is ThrowableProblem && x.status?.statusCode == 404 -> null
        else -> throw x
      }
    }

  @GET
  @Path(value = "/test5")
  public fun fetchTest5(@QueryParam(value = "limit") limit: Int): Uni<Test>
}
