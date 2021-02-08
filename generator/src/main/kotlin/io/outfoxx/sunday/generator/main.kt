package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) =
  GenerateCommand()
    .subcommands(KotlinJAXRSGenerateCommand())
    .main(args)
