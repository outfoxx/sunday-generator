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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.TypeName
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.DETAIL
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.INSTANCE
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.STATUS
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.TITLE
import io.outfoxx.sunday.generator.kotlin.utils.ProblemField.TYPE

enum class KotlinProblemLibrary(
  val id: String,
) {
  QUARKUS("quarkus"),
  SUNDAY("sunday"),
  ZALANDO("zalando"),
  ;

  fun support(rfc: KotlinProblemRfc): KotlinProblemLibrarySupport =
    when (this) {
      QUARKUS -> QuarkusProblemLibrarySupport(rfc)
      SUNDAY -> SundayProblemLibrarySupport(rfc)
      ZALANDO -> ZalandoProblemLibrarySupport(rfc)
    }
}

data class ProblemCustomProperty(
  val jsonName: String,
  val paramName: String,
  val typeName: TypeName,
)

interface KotlinProblemLibrarySupport {
  val rfc: KotlinProblemRfc
  val requiredFields: Set<ProblemField> get() = rfc.requiredFields
  val allowedFields: Set<ProblemField> get() = rfc.allowedFields
  val defaultFieldSet: Set<ProblemField> get() = rfc.defaultFieldSet
  val builderFieldMapping: Map<ProblemField, String>
  val throwableType: TypeName

  fun statusCodeAccess(varName: String): String

  fun validateRfcCompliance() {
    ProblemRfcCompliance.validate(
      rfc = rfc,
      builderFieldMapping = builderFieldMapping,
    )
  }
}

object ProblemRfcCompliance {
  fun validate(
    rfc: KotlinProblemRfc,
    builderFieldMapping: Map<ProblemField, String>,
  ) {
    require(rfc.requiredFields.all { it in rfc.allowedFields }) {
      "RFC ${rfc.id} required fields must be a subset of allowed fields"
    }
    require(rfc.defaultFieldSet.all { it in rfc.allowedFields }) {
      "RFC ${rfc.id} default field set must be a subset of allowed fields"
    }
    require(rfc.requiredFields.all { it in rfc.defaultFieldSet }) {
      "RFC ${rfc.id} required fields must be a subset of the default field set"
    }
    require(builderFieldMapping.keys.all { it in rfc.allowedFields }) {
      "Problem library defines field mappings that are not allowed by RFC ${rfc.id}"
    }
    require(rfc.defaultFieldSet.all { it in builderFieldMapping }) {
      "Problem library is missing field mappings required for RFC ${rfc.id}"
    }
  }
}

private class QuarkusProblemLibrarySupport(
  override val rfc: KotlinProblemRfc,
) : KotlinProblemLibrarySupport {

  override val builderFieldMapping: Map<ProblemField, String> =
    mapOf(
      TYPE to "withType",
      TITLE to "withTitle",
      STATUS to "withStatus",
      DETAIL to "withDetail",
      INSTANCE to "withInstance",
    )

  override val throwableType: TypeName = QUARKUS_HTTP_PROBLEM

  override fun statusCodeAccess(varName: String): String = "$varName.statusCode"
}

private class SundayProblemLibrarySupport(
  override val rfc: KotlinProblemRfc,
) : KotlinProblemLibrarySupport {

  override val builderFieldMapping: Map<ProblemField, String> =
    mapOf(
      TYPE to "super(TYPE_URI)",
      TITLE to "super(title)",
      STATUS to "super(status)",
      DETAIL to "super(detail)",
      INSTANCE to "super(instance)",
    )

  override val throwableType: TypeName = SUNDAY_HTTP_PROBLEM

  override fun statusCodeAccess(varName: String): String = "$varName.status"
}

private class ZalandoProblemLibrarySupport(
  override val rfc: KotlinProblemRfc,
) : KotlinProblemLibrarySupport {

  override val builderFieldMapping: Map<ProblemField, String> =
    mapOf(
      TYPE to "super(TYPE_URI)",
      TITLE to "super(title)",
      STATUS to "super(status)",
      DETAIL to "super(detail)",
      INSTANCE to "super(instance)",
    )

  override val throwableType: TypeName = ZALANDO_THROWABLE_PROBLEM

  override fun statusCodeAccess(varName: String): String = "$varName.status?.statusCode"
}
