package io.test.service

import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.http.Method
import io.test.Test1
import io.test.Test2
import io.test.Test3
import kotlin.Any
import kotlin.collections.List
import kotlin.reflect.typeOf
import kotlinx.coroutines.flow.Flow

public class API(
  public val requestFactory: RequestFactory,
  public val defaultContentTypes: List<MediaType> = listOf(),
  public val defaultAcceptTypes: List<MediaType> = listOf(),
) {
  public fun fetchEventsSimple(): Flow<Test1> = this.requestFactory
    .eventStream(
      method = Method.Get,
      pathTemplate = "/test1",
      acceptTypes = listOf(MediaType.EventStream),
      decoder = { decoder, _, _, data, _ -> decoder.decode<Test1>(data, typeOf<Test1>()) }
    )

  public fun fetchEventsDiscriminated(): Flow<Any> = this.requestFactory
    .eventStream(
      method = Method.Get,
      pathTemplate = "/test2",
      acceptTypes = listOf(MediaType.EventStream),
      decoder = { decoder, event, _, data, logger ->
        when (event) {
          "Test1" -> decoder.decode<Test1>(data, typeOf<Test1>())
          "test2" -> decoder.decode<Test2>(data, typeOf<Test2>())
          "t3" -> decoder.decode<Test3>(data, typeOf<Test3>())
          else -> {
            logger.error("Unknown event type, ignoring event: event=$event")
            null
          }
        }
      }
    )
}
