package io.test.service

import io.smallrye.mutiny.Uni
import io.test.Test
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import kotlin.Int
import org.jboss.resteasy.reactive.RestQuery
import org.jboss.resteasy.reactive.RestResponse

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/test1")
  public fun fetchTest1(@RestQuery limit: Int): Uni<RestResponse<Test>>

  @GET
  @Path(value = "/test2")
  public fun fetchTest2(@RestQuery limit: Int): Uni<RestResponse<Test>>

  @GET
  @Path(value = "/test3")
  public fun fetchTest3(@RestQuery limit: Int): Uni<RestResponse<Test>>

  @GET
  @Path(value = "/test4")
  public fun fetchTest4(@RestQuery limit: Int): Uni<RestResponse<Test>>

  @GET
  @Path(value = "/test5")
  public fun fetchTest5(@RestQuery limit: Int): Uni<RestResponse<Test>>
}
