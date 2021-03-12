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

package io.outfoxx.sunday.generator.swift.tools

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
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.MINUTES

class SwiftCompiler(private val workDir: Path) : Closeable, ExtensionContext.Store.CloseableResource {

  companion object {

    val imageRepo = "swift"
    val imageTag = "5.3"
  }

  val srcDir: Path = workDir.resolve("src")

  init {
    val pkgDir = Paths.get(SwiftCompiler::class.java.getResource("/swift/compile").toURI())
    Files.walk(pkgDir).forEach { source ->
      val target = workDir.resolve(pkgDir.relativize(source))
      if (Files.isRegularFile(source)) {
        Files.copy(source, target)
      } else if (!Files.exists(target)) {
        Files.createDirectory(target)
      }
    }
  }

  private val dockerConfig: DockerClientConfig =
    DefaultDockerClientConfig.createDefaultConfigBuilder()
      .withDockerHost("unix:///var/run/docker.sock")
      .withDockerTlsVerify(false)
      .build()

  private val dockerHttpClient: DockerHttpClient =
    ApacheDockerHttpClient.Builder()
      .dockerHost(dockerConfig.dockerHost)
      .sslConfig(dockerConfig.sslConfig)
      .build()

  private val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerConfig, dockerHttpClient)

  private val containerId: String

  init {
    println("### Starting Swift Compiler")

    val imageExists =
      try {
        dockerClient.inspectImageCmd("$imageRepo:$imageTag")
          .exec()
        true
      } catch (x: NotFoundException) {
        false
      }

    if (!imageExists) {
      dockerClient.pullImageCmd(imageRepo)
        .withTag(imageTag)
        .exec(PullImageResultCallback())
        .awaitCompletion(10, MINUTES)
    }

    containerId =
      dockerClient.createContainerCmd("$imageRepo:$imageTag")
        .withCmd("sleep", "3600")
        .withHostConfig(
          HostConfig.newHostConfig()
            .withBinds(Bind.parse("${workDir.toAbsolutePath()}:/work/"))
        )
        .exec().id

    dockerClient.startContainerCmd(containerId).exec()
  }

  fun compile(): Int {

    val execId =
      dockerClient.execCreateCmd(containerId)
        .withCmd("swift", "build")
        .withWorkingDir("/work")
        .withAttachStdout(true)
        .withAttachStderr(true)
        .exec()
        .id

    dockerClient.execStartCmd(execId)
      .exec(ExecStartResultCallback(System.out, System.err))
      .awaitCompletion(3, MINUTES)

    return dockerClient.inspectExecCmd(execId)
      .exec()
      .exitCodeLong.toInt()
  }

  override fun close() {
    println("### Stopping Swift Compiler")

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
