@file:OptIn(ExperimentalPathApi::class)
package io.outfoxx.sunday.generator.swift.tools

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.StreamType.RAW
import com.github.dockerjava.api.model.StreamType.STDERR
import com.github.dockerjava.api.model.StreamType.STDOUT
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.transport.DockerHttpClient
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.listDirectoryEntries

class SwiftCompiler(private val workDir: Path) : Closeable {

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

  fun compile(): Int {

    val container =
      dockerClient.createContainerCmd("swift:5.3")
        .withCmd("swift", "build")
        .withWorkingDir("/work")
        .withHostConfig(
          HostConfig.newHostConfig()
            .withBinds(Bind.parse("${workDir.toAbsolutePath()}:/work/"))
        )
        .exec()

    try {

      dockerClient.startContainerCmd(container.id).exec()

      val statusCode =
        dockerClient.waitContainerCmd(container.id)
          .start()
          .awaitStatusCode()

      if (statusCode != 0) {
        dockerClient.logContainerCmd(container.id)
          .withStdOut(true)
          .withStdErr(true)
          .exec(
            object : ResultCallback.Adapter<Frame>() {
              override fun onNext(frame: Frame?) {
                val (stream, payload) =
                  when (frame?.streamType) {
                    RAW, STDOUT -> System.out to frame.payload
                    STDERR -> System.err to frame.payload
                    else -> null
                  } ?: return
                stream.write(payload)
                stream.flush()
              }
            }
          )
          .awaitCompletion()
      }

      return statusCode

    } finally {
      dockerClient.removeContainerCmd(container.id).exec()
    }
  }

  override fun close() {
    dockerClient.close()
    dockerHttpClient.close()
  }

}
