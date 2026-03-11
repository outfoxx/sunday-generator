package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.Any
import kotlin.Int
import kotlin.String
import kotlin.collections.Map

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests/{obj}/{str}/{int}/{def}")
  public fun fetchTest(
    @PathParam(value = "obj") obj: Map<String, Any>,
    @PathParam(value = "str") str: String,
    @PathParam(value = "def") def: String,
    @PathParam(value = "int") int: Int,
  ): Response
}
