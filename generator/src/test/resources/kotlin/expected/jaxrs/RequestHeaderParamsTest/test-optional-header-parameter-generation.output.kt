package io.test.service

import io.test.Test
import javax.ws.rs.Consumes
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
    @HeaderParam(value = "obj") obj: Test?,
    @HeaderParam(value = "str") str: String?,
    @HeaderParam(value = "int") int: Int?,
    @HeaderParam(value = "def1") @DefaultValue(value = "test") def1: String,
    @HeaderParam(value = "def2") @DefaultValue(value = "10") def2: Int,
  ): Response
}
