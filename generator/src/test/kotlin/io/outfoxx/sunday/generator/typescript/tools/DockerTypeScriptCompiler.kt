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

@file:Suppress("DEPRECATION")

package io.outfoxx.sunday.generator.typescript.tools

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.core.command.ExecStartResultCallback
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MINUTES

class DockerTypeScriptCompiler(
  workDir: Path,
) : TypeScriptCompiler(workDir),
  ExtensionContext.Store.CloseableResource {

  class Shutdown(
    value: DockerTypeScriptCompiler,
  ) : Thread() {

    private val ref = WeakReference(value)

    override fun run() {
      ref.get()?.close()
    }
  }

  companion object {

    private const val IMAGE_REPO = "node"
    private const val IMAGE_TAG = "15-alpine"
  }

  private val dockerConfig: DockerClientConfig =
    DefaultDockerClientConfig
      .createDefaultConfigBuilder()
      .build()

  private val dockerHttpClient: DockerHttpClient =
    ApacheDockerHttpClient
      .Builder()
      .dockerHost(dockerConfig.dockerHost)
      .sslConfig(dockerConfig.sslConfig)
      .build()

  private val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient)

  private val containerId: String

  init {
    println("### Starting TypeScript Compiler")

    val imageExists =
      try {
        dockerClient
          .inspectImageCmd("$IMAGE_REPO:$IMAGE_TAG")
          .exec()
        true
      } catch (x: NotFoundException) {
        false
      }

    if (!imageExists) {
      dockerClient
        .pullImageCmd(IMAGE_REPO)
        .withTag(IMAGE_TAG)
        .exec(PullImageResultCallback())
        .awaitCompletion(10, MINUTES)
    }

    containerId =
      dockerClient
        .createContainerCmd("$IMAGE_REPO:$IMAGE_TAG")
        .withCmd("sleep", "3600")
        .withHostConfig(
          HostConfig
            .newHostConfig()
            .withBinds(Bind.parse("${workDir.toAbsolutePath()}:/work/")),
        ).exec()
        .id

    dockerClient.startContainerCmd(containerId).exec()

    Runtime.getRuntime().addShutdownHook(Shutdown(this))

    val execId =
      dockerClient
        .execCreateCmd(containerId)
        .withCmd("npm", "ci")
        .withWorkingDir("/work")
        .withAttachStdout(true)
        .withAttachStderr(true)
        .exec()
        .id

    dockerClient
      .execStartCmd(execId)
      .exec(ExecStartResultCallback(System.out, System.err))
      .awaitCompletion(3, MINUTES)
  }

  override fun compile(): Pair<Int, String> {

    val execId =
      dockerClient
        .execCreateCmd(containerId)
        .withCmd("npm", "run", "build")
        .withWorkingDir("/work")
        .withAttachStdout(true)
        .withAttachStderr(true)
        .exec()
        .id

    val out = ByteArrayOutputStream()

    dockerClient
      .execStartCmd(execId)
      .exec(ExecStartResultCallback(out, out))
      .awaitCompletion(3, MINUTES)

    val resultCode =
      dockerClient
        .inspectExecCmd(execId)
        .exec()
        .exitCodeLong
        .toInt()

    return resultCode to out.toByteArray().decodeToString()
  }

  override fun close() {
    println("### Stopping TypeScript Compiler")

    try {
      dockerClient.killContainerCmd(containerId).exec()
    } catch (x: Throwable) {
    }

    try {
      dockerClient.removeContainerCmd(containerId).exec()
    } catch (x: Throwable) {
    }

    dockerClient.close()
    dockerHttpClient.close()
  }
}
