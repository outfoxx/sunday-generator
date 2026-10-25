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

package io.outfoxx.sunday.generator.tools

/** Expected wire body shared by every target's inherited HTTP Problem regression. */
internal const val INHERITED_PROBLEM_JSON =
  """{"type":"https://example.com/problems/rebuilding","title":"Resource Rebuilding","status":409,"detail":"Resource A is rebuilding","traceId":"trace-A","resourceKey":"A","retryAfterSeconds":1,"nullableParent":null,"nullableChild":null,"metadata":{"region":"west"},"queuePosition":2,"queue-name":"rebuilds"}"""

/** Wire bodies for distinct inherited problems sharing the same 404 response. */
internal const val TEAM_NOT_FOUND_JSON =
  """{"type":"https://example.com/problems/team-not-found","title":"Team Not Found","status":404,"detail":"Team not found","traceId":"trace-A","nullableParent":null,"resourceId":"unknown","error-code":"missing"}"""

internal const val USER_NOT_FOUND_JSON =
  """{"type":"https://example.com/problems/user-not-found","title":"User Not Found","status":404,"detail":"Resource not found","traceId":"trace-A","nullableParent":null,"resourceId":"unknown","error-code":"missing"}"""
