package io.test.service

import javax.`annotation`.processing.Generated
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.Response
import kotlin.String

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
@Generated(
  value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
  date = "2024-01-01T00:00:00",
)
public interface API {
  @GET
  @Path(value = "/tests/{id}")
  public fun fetchTest(@PathParam(value = "id") id: String): Response
}
