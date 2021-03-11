package io.outfoxx.sunday.test.extensions

import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files

class TempDir : ExtensionContext.Store.CloseableResource {

  val path = Files.createTempDirectory(null)

  override fun close() {
    path.toFile().deleteRecursively()
  }

}
