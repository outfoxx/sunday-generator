package io.test.service

import io.test.Test
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces

@Produces(value = ["application/cbor", "application/yaml", "application/json"])
@Consumes(value = ["application/cbor"])
public interface API {
  @GET
  @Path(value = "/tests")
  @Consumes(value = ["application/yaml"])
  public fun fetchTest(body: Test): Test
}
