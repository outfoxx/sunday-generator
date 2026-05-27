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

package io.outfoxx.sunday.generator.utils

import io.outfoxx.sunday.generator.common.APIProcessor
import org.junit.jupiter.api.fail
import java.net.URI

object TestAPIProcessing : APIProcessor() {

  override fun process(uri: URI): Result {

    val result = super.process(uri)

    if (!result.isValid) {
      val log =
        result.validationLog.joinToString("\n") { entry ->
          "${entry.file}:${entry.line}: ${entry.message}"
        }

      fail("Invalid file\n$log")
    }

    return result
  }
}
