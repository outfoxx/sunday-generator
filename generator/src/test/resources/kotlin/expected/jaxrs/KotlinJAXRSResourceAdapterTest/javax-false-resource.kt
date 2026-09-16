package io.test

import io.reactivex.rxjava3.core.Single
import javax.ws.rs.GET
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.core.SecurityContext
import javax.ws.rs.core.UriInfo
import javax.ws.rs.sse.Sse
import javax.ws.rs.sse.SseEventSink
import kotlin.String

/**
 * Generated endpoint implementation delegating operation behavior to [API].
 */
@Path(value = "/")
public class APIResource(
  private val `delegate`: API,
) {
  /**
   * Invokes the application delegate for sync.
   */
  @GET
  @Path(value = "/sync")
  public fun sync(@Context securityContext: SecurityContext): Response {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    return this.delegate.sync()
  }

  /**
   * Invokes the application delegate for created.
   */
  @POST
  @Path(value = "/created")
  public fun created(@Context uriInfo: UriInfo, @Context securityContext: SecurityContext):
      Response {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    return this.delegate.created(uriInfo)
  }

  /**
   * Invokes the application delegate for asynchronous.
   */
  @GET
  @Path(value = "/async")
  public fun asynchronous(@Suspended asyncResponse: AsyncResponse, @Context
      securityContext: SecurityContext) {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    this.delegate.asynchronous(asyncResponse)
  }

  /**
   * Invokes the application delegate for reactive.
   */
  @GET
  @Path(value = "/reactive")
  public fun reactive(@Context securityContext: SecurityContext): Single<Response> {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    return this.delegate.reactive()
  }

  /**
   * Invokes the application delegate for sse.
   */
  @GET
  @Path(value = "/sse")
  @Produces(value = ["text/event-stream"])
  public fun sse(
    @Context sse: Sse,
    @Context sseEvents: SseEventSink,
    @Context securityContext: SecurityContext,
  ) {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    this.delegate.sse(sse, sseEvents)
  }

  /**
   * Invokes the application delegate for events.
   */
  @GET
  @Path(value = "/events")
  @Produces(value = ["text/event-stream"])
  public fun events(
    @Context sse: Sse,
    @Context sseEvents: SseEventSink,
    @Context securityContext: SecurityContext,
  ) {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    this.delegate.events(sse, sseEvents)
  }

  /**
   * Invokes the application delegate for when.
   */
  @GET
  @Path(value = "/keywords")
  public fun `when`(
    @QueryParam(value = "delegate") delegate_: String?,
    @QueryParam(value = "securityContext") securityContext: String?,
    @Context securityContext_: SecurityContext,
  ): Response {
    if (securityContext_.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    return this.delegate.`when`(delegate_, securityContext)
  }

  /**
   * Invokes the application delegate for policy.
   */
  @GET
  @Path(value = "/policy")
  public fun policy(@Context securityContext: SecurityContext): Response {
    if (securityContext.userPrincipal == null) {
      throw NotAuthorizedException(Response.status(401).build())
    }
    return this.delegate.policy()
  }
}
