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

package io.outfoxx.sunday.generator.kotlin.tools

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import java.lang.reflect.InvocationTargetException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Base64

/** Exercises SDK wire contracts only after the generated classes have passed the Kotlin compiler. */
fun assertSdkCompatibility(classLoader: ClassLoader) {
  fun model(name: String): Class<*> = classLoader.loadClass("io.test.$name")
  val mapper = jacksonObjectMapper()
  val mappedPayload = mapOf("kind" to "cat", "name" to "Mittens")
  val mappedValue = mapper.convertValue(mappedPayload, model("SdkMappedPet"))
  assertTrue(model("SdkWrappedCat").isInstance(mappedValue))
  assertTrue(model("SdkMappedCat").isInstance(mappedValue))
  assertEquals(mapper.valueToTree(mappedPayload), mapper.valueToTree(mappedValue))
  val mappedRoundTrip = mapper.readValue(mapper.writeValueAsBytes(mappedValue), model("SdkMappedPet"))
  assertTrue(model("SdkWrappedCat").isInstance(mappedRoundTrip))
  val aliasChild = model("SdkAliasChild")
  assertTrue(model("SdkAliasBase").isAssignableFrom(aliasChild))
  val aliasPayload = mapOf("label" to "base", "count" to 2, "extra" to "child")
  val aliasValue = mapper.convertValue(aliasPayload, aliasChild)
  assertEquals(mapper.valueToTree(aliasPayload), mapper.valueToTree(aliasValue))
  assertEquals("base", aliasChild.getMethod("getLabel").invoke(aliasValue))
  assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(aliasPayload + ("count" to 0), aliasChild) }
  for (name in listOf("SdkMultiChild", "SdkMultiReversed")) {
    val child = model(name)
    assertTrue(!model("SdkMultiFirst").isAssignableFrom(child))
    assertTrue(!model("SdkMultiSecond").isAssignableFrom(child))
    val payload = mapOf("a" to "first", "b" to "second", "count" to 2, "state" to "b")
    val decoded = mapper.convertValue(payload, child)
    assertEquals(mapper.valueToTree(payload), mapper.valueToTree(decoded))
    assertEquals(2, child.getMethod("getCount").invoke(mapper.convertValue(payload - "count", child)))
    for (invalid in listOf(payload + ("count" to 0), payload + ("count" to 3), payload + ("state" to "a"))) {
      assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(invalid, child) }
    }
  }
  val temporalMapper =
    mapper
      .copy()
      .registerModule(
        JavaTimeModule(),
      ).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  val temporalParent = model("SdkTemporalBase")
  val temporalChild = model("SdkTemporalChild")
  assertTrue(temporalParent.isAssignableFrom(temporalChild))
  val temporalPayload =
    mapOf(
      "timestamp" to "2026-09-14T00:00:00Z",
      "local" to "2026-09-14T00:00:00",
      "clockTime" to "00:00:00",
      "calendarDate" to "2026-09-14",
    )
  val constructed =
    temporalChild
      .getConstructor(
        OffsetDateTime::class.java,
        LocalDateTime::class.java,
        LocalTime::class.java,
        LocalDate::class.java,
      ).newInstance(
        OffsetDateTime.parse(temporalPayload.getValue("timestamp")),
        LocalDateTime.parse(temporalPayload.getValue("local")),
        LocalTime.parse(temporalPayload.getValue("clockTime")),
        LocalDate.parse(temporalPayload.getValue("calendarDate")),
      )
  for (value in listOf(constructed, temporalMapper.convertValue(temporalPayload, temporalChild))) {
    assertEquals(
      temporalMapper.valueToTree(temporalPayload),
      temporalMapper.readTree(temporalMapper.writeValueAsBytes(value)),
    )
    temporalMapper.readValue(temporalMapper.writeValueAsBytes(value), temporalChild)
  }
  for ((field, value) in temporalPayload) {
    val invalid = temporalPayload + (field to value.replace("00:00:00", "00:00:01").replace("09-14", "09-15"))
    assertThrows(IllegalArgumentException::class.java) { temporalMapper.convertValue(invalid, temporalChild) }
    temporalMapper.convertValue(invalid, temporalParent)
  }
  val conflicting = model("SdkConflictingChild")
  assertTrue(!model("SdkFirstParent").isAssignableFrom(conflicting))
  assertTrue(!model("SdkSecondParent").isAssignableFrom(conflicting))
  assertEquals(String::class.java, conflicting.getMethod("getStatus").returnType)
  assertEquals("b", conflicting.getMethod("getStatus").invoke(mapper.convertValue(mapOf("status" to "b"), conflicting)))
  for (value in listOf("a", "c")) {
    assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf("status" to value), conflicting) }
  }
  val byteParent = model("SdkByteBase")
  val byteChild = model("SdkByteRestrictions")
  assertTrue(byteParent.isAssignableFrom(byteChild))
  for (name in listOf("Data", "Choices", "Encoded")) {
    assertEquals(ByteArray::class.java, byteChild.getMethod("get$name").returnType)
    assertEquals(byteParent, byteChild.getMethod("get$name").declaringClass)
  }
  val byteConstructor = byteChild.getConstructor(ByteArray::class.java, ByteArray::class.java, ByteArray::class.java)
  for (wire in listOf("", "SGk=", "/wA=")) {
    val payload = mapOf("data" to "SGk=", "choices" to wire, "encoded" to "SGk=")
    val bytes = Base64.getDecoder().decode(wire)
    val values =
      listOf(
        mapper.convertValue(payload, byteChild),
        mapper.readValue(mapper.writeValueAsBytes(payload), byteChild),
        byteConstructor.newInstance("Hi".toByteArray(), bytes, "Hi".toByteArray()),
      )
    for (value in values) {
      assertArrayEquals(bytes, byteChild.getMethod("getChoices").invoke(value) as ByteArray)
      assertEquals(mapper.valueToTree(payload), mapper.readTree(mapper.writeValueAsBytes(value)))
    }
  }
  for ((field, invalid) in listOf(
    "data" to "Tm8=",
    "data" to null,
    "choices" to "Tm8=",
    "encoded" to "",
    "encoded" to "SGVsbG8=",
    "encoded" to "Tm8=",
  )) {
    val payload = mapOf("data" to "SGk=") + (field to invalid)
    assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(payload, byteChild) }
    val args = arrayOf<ByteArray?>("Hi".toByteArray(), null, null)
    args[listOf("data", "choices", "encoded").indexOf(field)] = invalid?.let(Base64.getDecoder()::decode)
    assertTrue(
      assertThrows(InvocationTargetException::class.java) {
        byteConstructor.newInstance(*args)
      }.cause is IllegalArgumentException,
    )
    mapper.convertValue(payload, byteParent)
  }
  assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(emptyMap<String, Any>(), byteChild) }
  mapper.convertValue(mapOf("data" to "SGk="), byteChild)
  assertThrows(IllegalArgumentException::class.java) {
    mapper.convertValue(mapOf("data" to "SGk=", "choices" to null), byteChild)
  }
  val inlineParent = model("SdkInlineBase")
  val inlineChild = model("SdkInlineChild")
  assertTrue(inlineParent.isAssignableFrom(inlineChild))
  assertEquals(inlineParent.getMethod("getDetail").returnType, inlineChild.getMethod("getDetail").returnType)
  assertEquals(inlineParent.getMethod("getSelection").returnType, inlineChild.getMethod("getSelection").returnType)
  val inline =
    mapper.convertValue(
      mapOf(
        "detail" to mapOf("value" to "value"),
        "selection" to "text",
        "tags" to listOf("b", "a"),
      ),
      inlineChild,
    )
  assertEquals(listOf("b", "a"), inlineChild.getMethod("getTags").invoke(inline))
  assertThrows(IllegalArgumentException::class.java) {
    mapper.convertValue(
      mapOf("detail" to emptyMap<String, Any>(), "selection" to "text", "tags" to listOf("a", "a")),
      inlineChild,
    )
  }
  val entity = model("EntityDetails")
  assertEquals(model("CurrentAsset"), entity.getMethod("getCurrentAsset").returnType)
  assertThrows(ClassNotFoundException::class.java) { model("EntityDetailsCurrentAsset") }
  for ((state, field, variant) in listOf(
    Triple("rendered", "versionId", "RenderedAsset"),
    Triple("refused", "refusalReason", "RefusedAsset"),
  )) {
    val payload = mapOf("currentAsset" to mapOf("state" to state, field to "value"))
    val decoded = mapper.convertValue(payload, entity)
    val asset = entity.getMethod("getCurrentAsset").invoke(decoded)
    assertEquals(model(variant), asset.javaClass)
    assertEquals(mapper.valueToTree(payload), mapper.readTree(mapper.writeValueAsBytes(decoded)))
  }
  val parent = model("BaseNarrativeChangeEvent")
  val child = model("CharacterChangeEvent")
  assertTrue(parent.isAssignableFrom(child))
  assertEquals(model("NarrativeChangeEventType"), child.getMethod("getType").returnType)
  assertEquals(parent, child.getMethod("getType").declaringClass)
  val event = mapper.convertValue(mapOf("type" to "character", "id" to "one"), child)
  assertEquals(20, child.getMethod("getCount").invoke(event))
  for (invalid in listOf(
    mapOf("type" to "prop", "id" to "one"),
    mapOf("type" to "future", "id" to "one"),
    mapOf("type" to "character", "id" to "one", "count" to 0),
    mapOf("type" to "character", "id" to "one", "count" to 21),
  )) {
    assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(invalid, child) }
  }
  mapper.convertValue(mapOf("type" to "prop", "id" to "one", "count" to 100), parent)
  assertTrue(model("FactEditOp").isAssignableFrom(model("AddFactOp")))
  val edit = mapper.convertValue(mapOf("op" to "addFact", "value" to "fact"), model("FactEditOp"))
  assertEquals(model("AddFactOp"), edit.javaClass)
  assertEquals("addFact", mapper.readTree(mapper.writeValueAsBytes(edit)).path("op").asText())
  assertTrue(model("HttpProblem").isAssignableFrom(model("BadRequestProblem")))
  val problem = model("BadRequestProblem")
  mapper.convertValue(mapOf("title" to "Bad request", "status" to 400), problem)
  for (invalid in listOf(401, null, false)) {
    assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(mapOf("status" to invalid), problem) }
  }
  val defaultProblem = mapper.convertValue(emptyMap<String, Any>(), problem)
  assertEquals("Invalid request", problem.getMethod("getDetail").invoke(defaultProblem))
  val restrictions = model("ScalarRestrictions")
  val defaults = mapper.convertValue(mapOf("value" to "present"), restrictions)
  assertEquals(0, restrictions.getMethod("getZero").invoke(defaults))
  assertEquals(false, restrictions.getMethod("getFlag").invoke(defaults))
  assertEquals("character", mapper.readTree(mapper.writeValueAsBytes(defaults)).path("mode").asText())
  for (choice in listOf(null, 0, false)) {
    mapper.convertValue(mapOf("value" to "present", "choice" to choice), restrictions)
  }
  for ((field, value) in listOf(
    "value" to null,
    "value" to "",
    "zero" to false,
    "zero" to 1,
    "flag" to true,
    "choice" to "0",
    "choice" to "false",
    "choice" to true,
    "mode" to "future",
  )) {
    assertThrows(IllegalArgumentException::class.java) {
      mapper.convertValue(
        mapOf("value" to "present") + (field to value),
        restrictions,
      )
    }
  }
  assertThrows(IllegalArgumentException::class.java) { mapper.convertValue(emptyMap<String, Any>(), restrictions) }
}
