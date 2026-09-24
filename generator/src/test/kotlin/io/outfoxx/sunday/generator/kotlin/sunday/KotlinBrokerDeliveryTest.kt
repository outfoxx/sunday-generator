/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.kotlin.sunday

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.broker.BrokerConsumeSpec
import io.outfoxx.sunday.broker.BrokerConsumer
import io.outfoxx.sunday.broker.BrokerDecodeFailureHandler
import io.outfoxx.sunday.broker.BrokerDelivery
import io.outfoxx.sunday.broker.BrokerMessage
import io.outfoxx.sunday.broker.BrokerMessageCodec
import io.outfoxx.sunday.broker.BrokerProducer
import io.outfoxx.sunday.broker.BrokerRawDelivery
import io.outfoxx.sunday.broker.BrokerSendSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.kotlin.KotlinSundayIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.test.extensions.ResourceUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isSameInstanceAs
import java.net.URI

@KotlinTest
class KotlinBrokerDeliveryTest {

  @OptIn(ExperimentalCompilerApi::class)
  @Test
  fun `compiled consumer recovers decoding without catching downstream processing`(
    @ResourceUri("asyncapi/ir/amqp-broker.yaml") uri: URI,
  ) = runBlocking<Unit> {
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary.SUNDAY,
      )
    KotlinSundayIrGenerator(AsyncApiToGeneratedApi().convertFragment(uri).api, registry, kotlinSundayTestOptions)
      .generateServiceTypes()
    val compiled = compileTypesResult(registry.buildTypes())
    expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)

    val malformed = Delivery("not JSON")
    val valid = Delivery("""{"id":"one","type":"event.created"}""")
    val handled = mutableListOf<BrokerRawDelivery>()
    val consumer =
      object : BrokerConsumer {
        override fun consume(spec: BrokerConsumeSpec): Flow<BrokerRawDelivery> = flowOf(malformed, valid)
      }
    val producer =
      object : BrokerProducer {
        override suspend fun send(
          spec: BrokerSendSpec,
          message: BrokerMessage,
        ) = error("unexpected publication")
      }
    val handler =
      BrokerDecodeFailureHandler { spec, delivery, _ ->
        expectThat(spec.id).isEqualTo("consumePlatformEvents")
        handled += delivery
        delivery.ack()
      }
    val facade = compiled.classLoader.loadClass("io.test.service.EventsBroker")
    val instance =
      facade
        .getConstructor(
          BrokerProducer::class.java,
          BrokerConsumer::class.java,
          BrokerMessageCodec::class.java,
          BrokerDecodeFailureHandler::class.java,
        ).newInstance(producer, consumer, BrokerMessageCodec(), handler)

    @Suppress("UNCHECKED_CAST")
    val deliveries = facade.getMethod("consumePlatformEvents").invoke(instance) as Flow<BrokerDelivery<Any>>
    val values = deliveries.toList()
    expectThat(values.size).isEqualTo(1)
    expectThat(values.single().raw).isSameInstanceAs(valid)
    expectThat(handled.toList()).isEqualTo(listOf(malformed))
    expectThat(malformed.actions.toList()).isEqualTo(listOf("ack"))
    expectThat(valid.actions).isEmpty()

    handled.clear()
    val failure = IllegalStateException("projection failed")
    expectThrows<IllegalStateException> {
      deliveries.collect { throw failure }
    }.isSameInstanceAs(failure)
    expectThat(handled.toList()).isEqualTo(listOf(malformed))
    expectThat(valid.actions).isEmpty()
  }

  private class Delivery(
    body: String,
  ) : BrokerRawDelivery {
    override val message = BrokerMessage(body.encodeToByteArray(), "application/json")
    override val exchange = "platform.events"
    override val routingKey = "platform.event"
    override val redelivered = false
    val actions = mutableListOf<String>()

    override suspend fun ack() {
      actions += "ack"
    }

    override suspend fun nack(requeue: Boolean) {
      actions += "nack:$requeue"
    }
  }
}
