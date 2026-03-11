package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.String
import kotlin.Suppress

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
@Suppress("RedundantVisibilityModifier", "RedundantUnitReturnType")
public interface API {
  @GET
  @Path(value = "/tests/{id}")
  public fun fetchTest(@PathParam(value = "id") id: String): Response
}
