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
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.UriInfo
import kotlin.Unit
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(): RestResponse<Test>

  @PUT
  @Path(value = "/tests")
  public fun putTest(body: Test): RestResponse<Test>

  @POST
  @Path(value = "/tests")
  public fun postTest(body: Test, @Context uriInfo: UriInfo): RestResponse<Test>

  @PATCH
  @Path(value = "/tests")
  public fun patchTest(body: Test): RestResponse<Test>

  @DELETE
  @Path(value = "/tests")
  public fun deleteTest(): RestResponse<Unit>

  @HEAD
  @Path(value = "/tests")
  public fun headTest(): RestResponse<Unit>

  @OPTIONS
  @Path(value = "/tests")
  public fun optionsTest(): RestResponse<Unit>

  @PATCH
  @Path(value = "/tests2")
  public fun patchableTest(body: PatchableTest): RestResponse<Test>

  @GET
  @Path(value = "/request")
  public fun requestTest(): RestResponse<Test>

  @GET
  @Path(value = "/response")
  public fun responseTest(): RestResponse<Test>
}
