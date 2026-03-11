package io.test.service

import io.smallrye.mutiny.Uni
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
  public fun fetchTest1(@RestQuery limit: Int): Uni<Test>

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
  public fun fetchTest2(@RestQuery limit: Int): Uni<Test>

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
  public fun fetchTest3(@RestQuery limit: Int): Uni<Test>

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
  public fun fetchTest4(@RestQuery limit: Int): Uni<Test>

  public fun fetchTest5OrNull(limit: Int): Uni<Test?> = fetchTest5(limit)
    .onFailure().recoverWithItem { x ->
      when {
        x is ThrowableProblem && x.status?.statusCode == 404 -> null
        else -> throw x
      }
    }

  @GET
  @Path(value = "/test5")
  public fun fetchTest5(@RestQuery limit: Int): Uni<Test>
}
