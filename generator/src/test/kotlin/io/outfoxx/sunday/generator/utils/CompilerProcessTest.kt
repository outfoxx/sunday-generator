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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/** Exercises real pipes, subprocess failures and cancellation rather than mocked process state. */
class CompilerProcessTest {

  @Test
  fun `large output on both streams cannot block a failing command`() {
    val (exit, output) =
      CompilerProcess.execute(
        listOf(
          "/bin/sh",
          "-c",
          "i=0; while [ \"\$i\" -lt 10000 ]; do echo stdout; echo stderr >&2; i=\$((i+1)); done; exit 7",
        ),
      )
    assertEquals(7, exit)
    assertTrue(output.contains("stdout"))
    assertTrue(output.contains("stderr"))
  }

  @Test
  fun `timeout terminates the process tree and retains diagnostics`(
    @TempDir directory: Path,
  ) {
    val pidFile = directory.resolve("child.pid")
    val failure =
      assertThrows(IllegalStateException::class.java) {
        CompilerProcess.execute(
          listOf("/bin/sh", "-c", "sleep 60 & echo \$! > child.pid; echo waiting; wait"),
          directory,
          timeout = Duration.ofSeconds(1),
        )
      }
    assertTrue(failure.message.orEmpty().contains("waiting"))
    awaitDead(Files.readString(pidFile).trim().toLong())
  }

  @Test
  fun `interruption cancels children and preserves the interrupt status`(
    @TempDir directory: Path,
  ) {
    val failure = AtomicReference<Throwable>()
    val interrupted = AtomicReference(false)
    val worker =
      Thread.ofPlatform().start {
        try {
          CompilerProcess.execute(listOf("/bin/sh", "-c", "sleep 60 & echo \$! > child.pid; wait"), directory)
        } catch (error: Throwable) {
          failure.set(error)
          interrupted.set(Thread.currentThread().isInterrupted)
        }
      }
    val pidFile = directory.resolve("child.pid")
    val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
    while (!Files.exists(pidFile) && System.nanoTime() < deadline) Thread.sleep(10)
    try {
      assertTrue(Files.exists(pidFile))
    } finally {
      worker.interrupt()
      worker.join(5000)
    }
    assertFalse(worker.isAlive)
    assertTrue(failure.get() is InterruptedException, failure.get()?.toString())
    assertTrue(interrupted.get())
    awaitDead(Files.readString(pidFile).trim().toLong())
  }

  @Test
  fun `shell tool discovery preserves argument boundaries`() {
    val (success, output) = ShellProcess.execute("printf", "%s", "literal spaces and dollar \$value")
    assertTrue(success)
    assertEquals("literal spaces and dollar \$value", output)
    assertTrue(ShellProcess.execute("command", "-v", "sh").first)
  }

  private fun awaitDead(pid: Long) {
    val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
    while (ProcessHandle.of(pid).map { it.isAlive }.orElse(false) && System.nanoTime() < deadline) Thread.sleep(10)
    assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "Child $pid survived cleanup")
  }
}
