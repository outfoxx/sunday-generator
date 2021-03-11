package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.subcommands
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerateCommand
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerateCommand
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerateCommand

fun main(args: Array<String>) =
  GenerateCommand()
    .subcommands(KotlinJAXRSGenerateCommand(), KotlinSundayGenerateCommand())
    .subcommands(SwiftSundayGenerateCommand())
    .subcommands(TypeScriptSundayGenerateCommand())
    .main(args)
