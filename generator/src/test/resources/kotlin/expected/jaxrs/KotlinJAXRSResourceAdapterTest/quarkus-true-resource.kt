package io.test

import io.quarkiverse.zanzibar.annotations.FGAObject
import io.quarkiverse.zanzibar.annotations.FGARelation
import io.quarkus.security.Authenticated
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.container.Suspended
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.UriInfo
import java.time.temporal.ChronoUnit
import kotlin.String
import kotlin.Unit
import org.eclipse.microprofile.faulttolerance.Timeout
import org.jboss.resteasy.reactive.RestQuery
import org.jboss.resteasy.reactive.RestResponse

/**
 * Generated endpoint implementation delegating operation behavior to [API].
 */
@Singleton
@Path(value = "/")
public class APIResource @Inject constructor(
  private val `delegate`: API,
) {
  /**
   * Invokes the application delegate for sync.
   */
  @GET
  @Path(value = "/sync")
  @Authenticated
  public suspend fun sync(): RestResponse<String> = this.delegate.sync()

  /**
   * Invokes the application delegate for created.
   */
  @POST
  @Path(value = "/created")
  @Authenticated
  public suspend fun created(@Context uriInfo: UriInfo): RestResponse<Unit> =
      this.delegate.created(uriInfo)

  /**
   * Invokes the application delegate for asynchronous.
   */
  @GET
  @Path(value = "/async")
  @Authenticated
  public suspend fun asynchronous(@Suspended asyncResponse: AsyncResponse) {
    this.delegate.asynchronous(asyncResponse)
  }

  /**
   * Invokes the application delegate for reactive.
   */
  @GET
  @Path(value = "/reactive")
  @Authenticated
  public suspend fun reactive(): Uni<RestResponse<String>> = this.delegate.reactive()

  /**
   * Invokes the application delegate for sse.
   */
  @GET
  @Path(value = "/sse")
  @Produces(value = ["text/event-stream"])
  @Authenticated
  public suspend fun sse() {
    this.delegate.sse()
  }

  /**
   * Invokes the application delegate for events.
   */
  @GET
  @Path(value = "/events")
  @Produces(value = ["text/event-stream"])
  @Authenticated
  public fun events(): Multi<String> = this.delegate.events()

  /**
   * Invokes the application delegate for when.
   */
  @GET
  @Path(value = "/keywords")
  @Authenticated
  public suspend fun `when`(@RestQuery delegate_: String?, @RestQuery securityContext: String?):
      RestResponse<String> = this.delegate.`when`(delegate_, securityContext)

  /**
   * Invokes the application delegate for policy.
   */
  @GET
  @Path(value = "/policy")
  @Timeout(
    value = 1,
    unit = ChronoUnit.SECONDS,
  )
  @FGAObject(
    id = "one",
    type = "document",
  )
  @FGARelation("reader")
  @Authenticated
  public suspend fun policy(): RestResponse<String> = this.delegate.policy()
}
