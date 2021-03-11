package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.CliktCommand

class GenerateCommand :
  CliktCommand(name = "sunday-generate", help = "Generate types and/or services from RAML definitions") {

  override fun run() = Unit

}
