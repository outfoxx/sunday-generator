package io.test.service

import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.sse.Sse
import javax.ws.rs.sse.SseEventSink

@Produces(value = ["application/json"])
@Consumes(value = ["application/json"])
public interface API {
  @GET
  @Path(value = "/tests")
  @Produces(value = ["text/event-stream"])
  public fun fetchEvents(@Context sse: Sse, @Context sseEvents: SseEventSink)

  @GET
  @Path(value = "/tests/server")
  @Produces(value = ["text/event-stream"])
  public fun fetchEventsServer(@Context sse: Sse, @Context sseEvents: SseEventSink)

  @GET
  @Path(value = "/tests/client")
  public fun fetchEventsClient(): Response
}
