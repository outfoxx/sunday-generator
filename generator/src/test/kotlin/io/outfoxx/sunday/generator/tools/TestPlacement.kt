package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration

/** Shared invocation defaults for compile-backed Kotlin fixtures. */
fun kotlinPlacement(
  models: String?,
  services: String? = models,
): GeneratedPlacementConfiguration =
  GeneratedPlacementConfiguration(
    defaults =
      GeneratedPlacement(
        kotlin =
          GeneratedPlacement.Scope(
            models = models?.let { GeneratedPlacement.Scope(packageName = it) },
            services = services?.let { GeneratedPlacement.Scope(packageName = it) },
          ),
      ),
  )

/** Shared invocation defaults for compile-backed Python fixtures. */
fun pythonPlacement(packageName: String): GeneratedPlacementConfiguration =
  GeneratedPlacementConfiguration(
    defaults = GeneratedPlacement(python = GeneratedPlacement.Scope(packageName = packageName)),
  )
