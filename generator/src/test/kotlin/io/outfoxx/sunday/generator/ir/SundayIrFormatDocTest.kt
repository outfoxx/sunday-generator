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

package io.outfoxx.sunday.generator.ir

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SundayIrFormatDocTest {

  @Test
  fun `documents and links Sunday IR format`() {

    val readme = Files.readString(Path.of("..", "README.md"))
    val doc = Files.readString(Path.of("..", "docs", "sunday-ir-format.md"))
    val audit = Files.readString(Path.of("..", "docs", "ir-emitter-readiness-audit.md"))
    val openApiAudit = Files.readString(Path.of("..", "docs", "openapi-ir-exit-audit.md"))
    val asyncApiAudit = Files.readString(Path.of("..", "docs", "asyncapi-ir-exit-audit.md"))
    val kotlinSundayPath = Files.readString(Path.of("..", "docs", "kotlin-sunday-ir-path.md"))
    val helperPlan = Files.readString(Path.of("..", "docs", "ir-emitter-helper-extraction.md"))

    assertThat(readme, containsString("docs/sunday-ir-format.md"))
    assertThat(readme, containsString("docs/ir-emitter-readiness-audit.md"))
    assertThat(readme, containsString("docs/openapi-ir-exit-audit.md"))
    assertThat(readme, containsString("docs/asyncapi-ir-exit-audit.md"))
    assertThat(readme, containsString("docs/kotlin-sunday-ir-path.md"))
    assertThat(readme, containsString("docs/ir-emitter-helper-extraction.md"))
    assertThat(doc, containsString("# Sunday IR Format"))
    assertThat(doc, containsString("ir-emitter-readiness-audit.md"))
    assertThat(doc, containsString("openapi-ir-exit-audit.md"))
    assertThat(doc, containsString("asyncapi-ir-exit-audit.md"))
    assertThat(doc, containsString("Source Fragment Composition"))
    assertThat(doc, containsString("GeneratedApiFragment"))
    assertThat(doc, containsString("GeneratedApiComposer"))
    assertThat(doc, containsString("x-sunday-operationId"))
    assertThat(doc, containsString("irVersion"))
    assertThat(doc, containsString("documentation"))
    assertThat(doc, containsString("tags"))
    assertThat(doc, containsString("GeneratedOperation.tags"))
    assertThat(doc, containsString("deprecated"))
    assertThat(doc, containsString("readOnly"))
    assertThat(doc, containsString("writeOnly"))
    assertThat(doc, containsString("serializationName"))
    assertThat(doc, containsString("validation"))
    assertThat(doc, containsString("constantValue"))
    assertThat(doc, containsString("mediaTypes"))
    assertThat(doc, containsString("headers"))
    assertThat(doc, containsString("baseUriParameters"))
    assertThat(doc, containsString("encoding"))
    assertThat(doc, containsString("allowEmptyValue"))
    assertThat(doc, containsString("payloads"))
    assertThat(doc, containsString("Protocol Metadata"))
    assertThat(doc, containsString("protocol.bindings"))
    assertThat(doc, containsString("securitySchemes"))
    assertThat(doc, containsString("bearerFormat"))
    assertThat(doc, containsString("cookie"))
    assertThat(doc, containsString("requirements"))
    assertThat(doc, containsString("jaxrs"))
    assertThat(doc, containsString("jsonBody"))
    assertThat(doc, containsString("examples"))
    assertThat(doc, containsString("strict"))
    assertThat(doc, containsString("sourceName"))
    assertThat(doc, containsString("defining source location"))
    assertThat(doc, containsString("statusBindings"))
    assertThat(doc, containsString("problemUriParams"))
    assertThat(doc, containsString("application/problem+json"))
    assertThat(doc, containsString("payload"))
    assertThat(doc, containsString("inherits"))
    assertThat(doc, containsString("discriminator"))
    assertThat(doc, containsString("Phase 1"))
    assertThat(doc, containsString("externallyDiscriminated"))
    assertThat(doc, containsString("discriminatorMappings"))
    assertThat(doc, containsString("externalDiscriminator"))
    assertThat(doc, containsString("targets"))
    assertThat(doc, containsString("implementation"))
    assertThat(doc, containsString("nested"))
    assertThat(doc, containsString("patchable"))
    assertThat(doc, containsString("collection"))
    assertThat(doc, containsString("format"))
    assertThat(doc, containsString("patternProperties"))
    assertThat(doc, containsString("additionalProperties"))
    assertThat(doc, containsString("closed"))
    assertThat(audit, containsString("# Sunday IR Emitter Readiness Audit"))
    assertThat(audit, containsString("Parity Blockers"))
    assertThat(audit, containsString("Structured default values"))
    assertThat(audit, containsString("Inline/generated local shapes"))
    assertThat(audit, containsString("File payload shapes"))
    assertThat(audit, containsString("operation transport metadata"))
    assertThat(audit, containsString("constant/literal parameter metadata"))
    assertThat(audit, containsString("typed auth/security metadata"))
    assertThat(audit, containsString("imported declaration identity"))
    assertThat(audit, containsString("Target-Only Configuration"))
    assertThat(audit, containsString("Recommended Implementation Slices"))
    assertThat(openApiAudit, containsString("# OpenAPI 3.1 IR Exit Audit"))
    assertThat(openApiAudit, containsString("native OpenAPI reader"))
    assertThat(openApiAudit, containsString("AMF is not part of the OpenAPI source frontend"))
    assertThat(openApiAudit, containsString("x-sunday-policy"))
    assertThat(openApiAudit, containsString("no additional OpenAPI IR mapping is required"))
    assertThat(asyncApiAudit, containsString("# AsyncAPI IR Exit Audit"))
    assertThat(asyncApiAudit, containsString("native AsyncAPI reader"))
    assertThat(asyncApiAudit, containsString("AMF is not part of the AsyncAPI source frontend"))
    assertThat(asyncApiAudit, containsString("OpenAPI + AsyncAPI composition"))
    assertThat(asyncApiAudit, containsString("no blocking AsyncAPI IR mapping is required"))
    assertThat(kotlinSundayPath, containsString("# Kotlin/Sunday IR Path"))
    assertThat(kotlinSundayPath, containsString("GeneratedApiIrExporter"))
    assertThat(kotlinSundayPath, containsString("GeneratedApi"))
    assertThat(kotlinSundayPath, containsString("KotlinSundayIrGenerator"))
    assertThat(kotlinSundayPath, containsString("KotlinTypeRegistry"))
    assertThat(helperPlan, containsString("# IR Emitter Helper Extraction Plan"))
    assertThat(helperPlan, containsString("Phase 2E"))
    assertThat(helperPlan, containsString("KotlinSundayIrGenerator"))
    assertThat(helperPlan, containsString("type ref resolution"))
    assertThat(helperPlan, containsString("operation response selection"))
    assertThat(helperPlan, containsString("media negotiation grouping"))
    assertThat(helperPlan, containsString("problem URI resolution"))
    assertThat(helperPlan, containsString("parameter naming"))
    assertThat(helperPlan, containsString("local model ownership"))
    assertThat(helperPlan, containsString("target override lookup"))
    assertThat(helperPlan, containsString("source/library identity lookup"))
  }
}
