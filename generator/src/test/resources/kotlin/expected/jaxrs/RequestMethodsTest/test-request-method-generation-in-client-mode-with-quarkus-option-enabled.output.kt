package io.test

import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HEAD
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(): Test

  @PUT
  @Path(value = "/tests")
  public fun putTest(body: Test): Test

  @POST
  @Path(value = "/tests")
  public fun postTest(body: Test): Test

  @PATCH
  @Path(value = "/tests")
  public fun patchTest(body: Test): Test

  @DELETE
  @Path(value = "/tests")
  public fun deleteTest()

  @HEAD
  @Path(value = "/tests")
  public fun headTest()

  @OPTIONS
  @Path(value = "/tests")
  public fun optionsTest()

  @PATCH
  @Path(value = "/tests2")
  public fun patchableTest(body: PatchableTest): Test

  @GET
  @Path(value = "/request")
  public fun requestTest(): Test

  @GET
  @Path(value = "/response")
  public fun responseTest(): Test
}
