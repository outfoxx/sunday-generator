package io.test.service

import io.test.Test
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.sse.SseEventSource

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @Produces(value = ["text/event-stream"])
  public fun fetchEvents(): SseEventSource

  @GET
  @Path(value = "/tests/server")
  public fun fetchEventsServer(): Test

  @GET
  @Path(value = "/tests/client")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsClient(): SseEventSource
}
