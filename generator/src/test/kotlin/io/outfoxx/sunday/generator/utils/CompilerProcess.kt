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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Runs compiler tools with bounded waits and diagnostics that cannot fill a pipe. */
internal object CompilerProcess {

  val compilerTimeout: Duration
    get() = timeout("compilerTimeoutSeconds", 300)

  val dependencyTimeout: Duration
    get() = timeout("dependencyTimeoutSeconds", 600)

  private fun timeout(
    property: String,
    default: Long,
  ): Duration =
    Duration
      .ofSeconds(System.getProperty("sunday.validation.$property")?.toLong() ?: default)
      .also { require(!it.isNegative && !it.isZero) { "$property must be positive" } }

  /** Redirecting both streams to a file also handles children that inherit stdout after their parent exits. */
  fun execute(
    command: List<String>,
    directory: Path? = null,
    environment: Map<String, String> = emptyMap(),
    timeout: Duration = compilerTimeout,
  ): Pair<Int, String> {
    require(command.isNotEmpty())
    require(!timeout.isNegative && !timeout.isZero)
    val outputFile = Files.createTempFile("sunday-compiler-", ".log")
    val started = System.nanoTime()
    val descendants = linkedSetOf<ProcessHandle>()
    var process: Process? = null
    var exitCode = -1
    try {
      process =
        ProcessBuilder(command)
          .directory(directory?.toFile())
          .apply { environment().putAll(environment) }
          .redirectErrorStream(true)
          .redirectOutput(outputFile.toFile())
          .start()
      process.outputStream.close()
      val deadline = started + timeout.toNanos()
      while (true) {
        process.descendants().use { children -> children.forEach(descendants::add) }
        if (process.waitFor(20, TimeUnit.MILLISECONDS)) break
        check(System.nanoTime() < deadline) {
          "Compiler command timed out after $timeout: ${command.joinToString(" ")}\n${diagnostics(outputFile)}"
        }
      }
      exitCode = process.exitValue()
      return exitCode to diagnostics(outputFile)
    } catch (interrupted: InterruptedException) {
      Thread.currentThread().interrupt()
      throw interrupted
    } finally {
      // Clear interruption only during cleanup, restoring it before returning to the caller.
      val interrupted = Thread.interrupted()
      try {
        process?.descendants()?.use { children -> children.forEach(descendants::add) }
        descendants.toList().asReversed().forEach { child -> if (child.isAlive) child.destroyForcibly() }
        // Let a waiting shell reap its children before terminating it, avoiding orphaned zombies.
        if (process?.isAlive == true && !process.waitFor(200, TimeUnit.MILLISECONDS)) {
          process.destroyForcibly()
          process.waitFor(1, TimeUnit.SECONDS)
        }
        Files.deleteIfExists(outputFile)
        record(command.first(), started, exitCode)
      } finally {
        if (interrupted) Thread.currentThread().interrupt()
      }
    }
  }

  private fun diagnostics(path: Path): String {
    // Keep the tail of unusually verbose failures without retaining unbounded output in the test JVM.
    val limit = 2 * 1024 * 1024
    Files.newByteChannel(path).use { channel ->
      val omitted = maxOf(0L, channel.size() - limit)
      channel.position(omitted)
      val buffer = java.nio.ByteBuffer.allocate(minOf(channel.size(), limit.toLong()).toInt())
      while (buffer.hasRemaining() && channel.read(buffer) >= 0) { /* Read the retained diagnostics. */ }
      val prefix = if (omitted > 0) "[omitted $omitted diagnostic bytes]\n" else ""
      return prefix + buffer.array().decodeToString(0, buffer.position())
    }
  }

  /** Records in-process Kotlin compilation alongside external compiler timings. */
  fun record(
    command: String,
    started: Long,
    exitCode: Int,
  ) {
    val directory = System.getProperty("sunday.validation.metrics-dir")?.let(Path::of) ?: return
    Files.createDirectories(directory)
    val worker = System.getProperty("org.gradle.test.worker", "standalone")
    val elapsedMillis = (System.nanoTime() - started) / 1_000_000
    val tool =
      Path
        .of(command)
        .fileName
        .toString()
        .replace('\t', ' ')
        .replace('\n', ' ')
    Files.writeString(
      directory.resolve("worker-$worker.tsv"),
      "$tool\t$elapsedMillis\t$exitCode\n",
      StandardOpenOption.CREATE,
      StandardOpenOption.APPEND,
    )
  }
}
