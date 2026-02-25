package io.test.service

import io.test.Test
import javax.ws.rs.Consumes
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.Int
import kotlin.String

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}")
  public fun fetchTest(
    @PathParam(value = "def2") @DefaultValue(value = "10") def2: Int,
    @PathParam(value = "obj") obj: Test?,
    @PathParam(value = "str") str: String?,
    @PathParam(value = "def1") @DefaultValue(value = "test") def1: String,
    @PathParam(value = "int") int: Int?,
    @PathParam(value = "def") def: String,
  ): Response
}
