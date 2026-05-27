package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.sse.SseEventSource

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/test1")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsSimple(): SseEventSource

  @GET
  @Path(value = "/test2")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsDiscriminated(): SseEventSource

  @GET
  @Path(value = "/test3")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsSimpleSse(): SseEventSource
}
