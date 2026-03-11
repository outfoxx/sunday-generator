package io.test

import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.OPTIONS
import javax.ws.rs.PATCH
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.Produces

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
