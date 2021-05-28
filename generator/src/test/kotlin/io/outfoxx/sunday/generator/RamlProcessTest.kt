/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator

import amf.client.model.domain.AnyShape
import amf.client.model.domain.NamedDomainElement
import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.customDomainProperties
import io.outfoxx.sunday.generator.utils.declares
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.operations
import io.outfoxx.sunday.generator.utils.path
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.blankOrNullString
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class RamlProcessTest {

  @Test
  fun `process produces validation result entries`(
    @ResourceUri("raml/invalid.raml") testUri: URI
  ) {

    val validationLog = APIProcessor().process(testUri).validationLog

    assertThat(validationLog, hasSize(1))
    assertThat(validationLog[0].level, equalTo(APIProcessor.Result.Level.Error))
    assertThat(validationLog[0].file, not(blankOrNullString()))
    assertThat(validationLog[0].line, not(equalTo(0)))
    assertThat(validationLog[0].message, not(blankOrNullString()))
  }

  @Test
  fun `process produces valid overlays documents`(
    @ResourceUri("raml/test-overlay.raml") testUri: URI
  ) {

    val result = APIProcessor().process(testUri)

    assertThat(result.isValid, equalTo(true))
    assertThat(result.validationLog, empty())

    // Has merged endpoints?
    val api = result.document.api
    assertThat(api.endPoints.map { it.path }, hasItems("/test", "/test/{id}", "/test2", "/test2/sub/{id}"))

    // Did overlay onto endpoint work?
    val overlaidEndpoint = api.endPoints.firstOrNull { it.path == "/test/{id}" }
    assertThat(overlaidEndpoint, not(nullValue()))
    assertThat(overlaidEndpoint?.customDomainProperties?.map { it.name }, hasItem("test-overlay-ann"))

    // Did overlay operation work?
    val overlaidOp = overlaidEndpoint?.operations?.firstOrNull()
    assertThat(overlaidOp?.customDomainProperties?.map { it.name }, hasItem("test-overlay-ann"))
    assertThat(overlaidOp?.name, equalTo("fetch"))

    // Has merged types/decls
    val decls = result.document.declares.filterIsInstance<NamedDomainElement>()
    assertThat(decls.map { it.name }, hasItems("Test", "Tester", "Tester2"))

    // Did overlay onto type work?
    val overlaidType = decls.firstOrNull { it.name == "Tester" } as? AnyShape
    assertThat(overlaidType?.customDomainProperties?.map { it.name }, hasItem("test-overlay-ann"))
  }
}
