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

import org.junit.jupiter.api.Assertions.assertEquals
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private val updateSnapshots: Boolean =
  System
    .getenv("UPDATE_SNAPSHOTS")
    ?.lowercase()
    ?.let { it == "1" || it == "true" || it == "yes" }
    ?: false

internal fun assertSnapshotAt(
  snapshotRoot: Path,
  snapshotPath: String,
  actual: String,
) {
  val normalizedActual = actual.replace("\r\n", "\n")
  val snapshotFile = snapshotRoot.resolve(snapshotPath)

  if (updateSnapshots) {
    Files.createDirectories(snapshotFile.parent)
    Files.writeString(snapshotFile, normalizedActual, StandardCharsets.UTF_8)
  }

  if (!Files.exists(snapshotFile)) {
    throw AssertionError(
      "Snapshot file not found: $snapshotFile. " +
        "Run tests with UPDATE_SNAPSHOTS=1 to create it.",
    )
  }

  val expected = Files.readString(snapshotFile, StandardCharsets.UTF_8).replace("\r\n", "\n")
  assertEquals(expected, normalizedActual, "Snapshot mismatch: $snapshotFile")
}

private val kotlinSnapshotsRoot: Path =
  Path.of("src", "test", "resources", "kotlin", "expected")

private val swiftSnapshotsRoot: Path =
  Path.of("src", "test", "resources", "swift", "expected")

fun assertKotlinSnapshot(
  snapshotPath: String,
  actual: String,
) {
  assertSnapshotAt(kotlinSnapshotsRoot, snapshotPath, actual)
}

fun assertKotlinSundaySnapshot(
  snapshotPath: String,
  actual: String,
) {
  assertKotlinSnapshot("sunday/$snapshotPath", actual)
}

fun assertKotlinJaxrsSnapshot(
  snapshotPath: String,
  actual: String,
) {
  assertKotlinSnapshot("jaxrs/$snapshotPath", actual)
}

fun assertSwiftSnapshot(
  snapshotPath: String,
  actual: String,
) {
  assertSnapshotAt(swiftSnapshotsRoot, snapshotPath, actual)
}
