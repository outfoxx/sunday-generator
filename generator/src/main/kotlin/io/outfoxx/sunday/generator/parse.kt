package io.outfoxx.sunday.generator

import amf.MessageStyles
import amf.ProfileNames
import amf.client.AMF
import amf.client.environment.Environment
import amf.client.model.document.Document
import amf.client.parse.Raml10Parser
import io.outfoxx.sunday.generator.utils.LocalResourceLoader
import java.net.URI
import kotlin.system.exitProcess

fun parseAndValidate(uri: URI): Document {

  val parentUri = uri.resolve(".")

  AMF.init().get()

  val environment = Environment().addClientLoader(LocalResourceLoader)
  val document = Raml10Parser(environment).parseFileAsync(uri.toString()).get() as Document
  val validation = AMF.validate(document.cloneUnit(), ProfileNames.RAML10(), MessageStyles.RAML()).get()

  if (!validation.conforms()) {

    validation.results().forEach { result ->

      val locationURI = result.location().orElse(null)?.let { URI(it) }
      val location = locationURI?.let { parentUri.relativize(it) }?.toASCIIString() ?: "unknown"
      val line = result.position().start().line()

      System.err.println("$location:${line}: ${result.message()}")

    }

    exitProcess(1)

  }

  return document
}
