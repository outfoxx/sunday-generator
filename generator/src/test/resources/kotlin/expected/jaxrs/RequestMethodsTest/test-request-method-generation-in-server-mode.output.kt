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
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(): Response

  @PUT
  @Path(value = "/tests")
  public fun putTest(body: Test): Response

  @POST
  @Path(value = "/tests")
  public fun postTest(body: Test, @Context uriInfo: UriInfo): Response

  @PATCH
  @Path(value = "/tests")
  public fun patchTest(body: Test): Response

  @DELETE
  @Path(value = "/tests")
  public fun deleteTest(): Response

  @HEAD
  @Path(value = "/tests")
  public fun headTest(): Response

  @OPTIONS
  @Path(value = "/tests")
  public fun optionsTest(): Response

  @PATCH
  @Path(value = "/tests2")
  public fun patchableTest(body: PatchableTest): Response

  @GET
  @Path(value = "/request")
  public fun requestTest(): Response

  @GET
  @Path(value = "/response")
  public fun responseTest(): Response
}
