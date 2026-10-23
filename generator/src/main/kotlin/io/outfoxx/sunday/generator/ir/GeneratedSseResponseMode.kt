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

/** Server-side representation of an SSE response; the wire payload schema remains unchanged. */
enum class GeneratedSseResponseMode(
  /** Source annotation and command-line spelling. */
  val value: String,
) {
  TYPED("typed"),
  OUTBOUND_EVENT("outbound-event"),
  RAW("raw"),
  ;

  companion object {
    /** Reads an optional source setting, rejecting unsupported values instead of ignoring them. */
    fun parse(value: Any?): GeneratedSseResponseMode? =
      value?.let {
        requireNotNull(entries.find { mode -> mode.value == value }) {
          "Invalid SSE response mode '$value'; expected typed, outbound-event, or raw"
        }
      }
  }
}
