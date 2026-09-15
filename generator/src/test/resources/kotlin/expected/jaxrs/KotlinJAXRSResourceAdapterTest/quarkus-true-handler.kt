package io.test

import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.Uni
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.core.UriInfo
import kotlin.String
import kotlin.Unit
import org.jboss.resteasy.reactive.RestResponse

/**
 * Application-owned operation behavior. Register the generated resource with the server.
 */
public interface API {
  /**
   * Handles the sync operation.
   */
  public suspend fun sync(): RestResponse<String>

  /**
   * Handles the created operation.
   */
  public suspend fun created(uriInfo: UriInfo): RestResponse<Unit>

  /**
   * Handles the asynchronous operation.
   */
  public suspend fun asynchronous(asyncResponse: AsyncResponse)

  /**
   * Handles the reactive operation.
   */
  public suspend fun reactive(): Uni<RestResponse<String>>

  /**
   * Handles the sse operation.
   */
  public suspend fun sse()

  /**
   * Handles the events operation.
   */
  public fun events(): Multi<String>

  /**
   * Handles the when operation.
   */
  public suspend fun `when`(delegate_: String?, securityContext: String?): RestResponse<String>

  /**
   * Handles the policy operation.
   */
  public suspend fun policy(): RestResponse<String>
}
