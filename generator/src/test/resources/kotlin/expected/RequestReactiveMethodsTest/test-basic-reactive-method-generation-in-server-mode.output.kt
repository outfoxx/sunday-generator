package io.test.service

import java.util.concurrent.CompletionStage
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  public fun fetchTest(): CompletionStage<Response>

  @GET
  @Path(value = "/tests/derived")
  public fun fetchDerivedTest(): CompletionStage<Response>
}
