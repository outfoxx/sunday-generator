package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.test.Base
import io.test.Test1
import io.test.Test2
import kotlin.collections.List
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.Flow

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
) {
  public fun fetchEventsSimple(): Flow<Base> = this.requestFactory
    .eventStream(
      method = Method.Get,
      pathTemplate = "/test1",
      acceptTypes = listOf(MediaType.EventStream),
      decoder = { decoder, _, _, data, _ -> decoder.decode<Base>(data, typeOf<Base>()) }
    )

  public fun fetchEventsDiscriminated(): Flow<Base> = this.requestFactory
    .eventStream(
      method = Method.Get,
      pathTemplate = "/test2",
      acceptTypes = listOf(MediaType.EventStream),
      decoder = { decoder, event, _, data, logger ->
        when (event) {
          "Test1" -> decoder.decode<Test1>(data, typeOf<Test1>())
          "Test2" -> decoder.decode<Test2>(data, typeOf<Test2>())
          else -> {
            logger.error("Unknown event type, ignoring event: event=$event")
            null
          }
        }
      }
    )
}
