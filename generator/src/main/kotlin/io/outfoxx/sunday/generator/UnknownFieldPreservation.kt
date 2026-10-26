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

package io.outfoxx.sunday.generator

/**
 * Controls ordinary model catch-all storage independently of schema validation and declared pattern fields.
 * Kotlin problem extension handling is independent of this setting.
 */
enum class UnknownFieldPreservation {
  /** Preserve fields on open models unless the schema forbids additional properties. */
  On,

  /** Do not introduce catch-all storage, even when the schema permits additional properties. */
  Off,

  /** Preserve fields only when additional properties are explicitly declared by the schema. */
  Schema,
}
