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

package io.outfoxx.sunday.generator.tools

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import java.nio.file.Files
import java.nio.file.Path

/** Records each fork's effective JUnit selection so CI can verify the combined partition inventory. */
class TestInventoryListener : TestExecutionListener {

  override fun testPlanExecutionFinished(testPlan: TestPlan) {
    val methods =
      testPlan.roots
        .flatMap(testPlan::getDescendants)
        .filter { it.source.orElse(null) is MethodSource }
        .map { it.uniqueId.substringBefore("/[test-template-invocation:").substringBefore("/[dynamic-test:") }
        .distinct()
        .sorted()
    val directory = Path.of("build/diagnostics/inventory")
    Files.createDirectories(directory)
    val worker = System.getProperty("org.gradle.test.worker", "standalone")
    jacksonObjectMapper().writeValue(directory.resolve("worker-$worker.json").toFile(), methods)
  }
}
