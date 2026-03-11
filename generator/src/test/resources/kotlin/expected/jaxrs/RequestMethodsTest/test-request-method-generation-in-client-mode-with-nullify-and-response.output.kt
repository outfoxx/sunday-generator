package io.test

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response
import kotlin.Int

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/test1")
  public fun fetchTest1(@QueryParam(value = "limit") limit: Int): Response

  @GET
  @Path(value = "/test2")
  public fun fetchTest2(@QueryParam(value = "limit") limit: Int): Response

  @GET
  @Path(value = "/test3")
  public fun fetchTest3(@QueryParam(value = "limit") limit: Int): Response

  @GET
  @Path(value = "/test4")
  public fun fetchTest4(@QueryParam(value = "limit") limit: Int): Response

  @GET
  @Path(value = "/test5")
  public fun fetchTest5(@QueryParam(value = "limit") limit: Int): Response
}
