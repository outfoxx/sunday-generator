package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Response

@Produces(value = ["application/json", "application/yaml"])
@Consumes(value = ["application/json", "application/yaml"])
public interface API {
  @GET
  @Path(value = "/tests/1")
  @Produces(value = ["application/yaml"])
  public fun fetchTest1(): Response

  @GET
  @Path(value = "/tests/2s")
  public fun fetchTest2Same(): Response

  @GET
  @Path(value = "/tests/2d")
  @Produces(value = ["application/yaml", "application/cbor"])
  public fun fetchTest2Different(): Response

  @GET
  @Path(value = "/tests/3")
  @Produces(value = ["application/yaml", "application/json", "application/cbor"])
  public fun fetchTest3(): Response
}
