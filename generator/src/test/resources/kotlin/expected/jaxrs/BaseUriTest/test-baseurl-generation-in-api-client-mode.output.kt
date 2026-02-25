package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import kotlin.String

@Path(value = "http://master.sbx.example.com/api/1")
@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(): String
}
