package io.test.service

import io.test.Test
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.Int
import kotlin.String

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(
    @HeaderParam(value = "obj") obj: Test,
    @HeaderParam(value = "str-req") strReq: String,
    @HeaderParam(value = "int") @DefaultValue(value = "5") int: Int,
  ): Response

  @DELETE
  @Path(value = "/tests")
  public fun deleteTest(): Response
}
