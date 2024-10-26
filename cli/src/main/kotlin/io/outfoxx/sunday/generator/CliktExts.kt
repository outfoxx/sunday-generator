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

package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.BaseCliktCommand
import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

typealias EnumOption = OptionWithValues<Boolean, Boolean, Boolean>
typealias EnumOptions<E> = Map<E, EnumOption>

class EnumFlagBuilder<E: Enum<E>> {
  val flags = mutableMapOf<E, EnumFlag>()

  infix fun E.to(flag: EnumFlag) = flags.put(this, flag)

  fun String.default(default: Boolean = false): EnumFlag = EnumFlag(help = this, default = default)
}

inline fun <reified E : Enum<E>> ParameterHolder.flags(block: EnumFlagBuilder<E>.() -> Unit): EnumOptions<E> {
  val builder = EnumFlagBuilder<E>().apply { block(this) }
  return flags(builder.flags)
}

inline fun <reified E : Enum<E>> ParameterHolder.flags(
  vararg entries: Pair<E, String>,
): EnumOptions<E> = flags(entries.associate { (value, help) -> value to EnumFlag(help = help) })

@JvmName("flagsToHelp")
inline fun <reified E : Enum<E>> ParameterHolder.flags(
  entries: Map<E, String>,
): EnumOptions<E> {
  val enumFlags = entries.mapValues { (_, help) -> EnumFlag(help = help) }
  return flags(enumFlags)
}

data class EnumFlag(
  var name: String? = null,
  var help: String? = null,
  var default: Boolean = false,
) {
  constructor(help: String) : this(help = help, default = false)

  fun name(name: String) = apply { this.name = name }
  fun help(help: String) = apply { this.help = help }
  fun default(default: Boolean) = apply { this.default = default }
}

inline fun <reified E : Enum<E>> ParameterHolder.flags(
  entries: Map<E, EnumFlag>,
): EnumOptions<E> =
  entries.mapValues { (value, flag) ->
    val enableName = flag.name ?: value.name.camelCaseToKebabCase()
    val disableName = "no-$enableName"
    val helpDefault = if (flag.default) "enabled" else "disabled"
    option("-$enableName", help = flag.help ?: "")
      .flag("-$disableName", default = true, defaultForHelp = helpDefault)
  }

class EnumFlagsOptionGroup<E: Enum<E>>(
  name: String,
  help: String? = null,
  val options: EnumOptions<E>
) : OptionGroup(name, help) {
  init {
    options.forEach { (_, option) -> registerOption(option) }
  }
}

fun <E: Enum<E>> EnumOptions<E>.grouped(name: String, help: String? = null): EnumFlagsOptionGroup<E> =
  EnumFlagsOptionGroup(name, help, this)

operator fun <E : Enum<E>> EnumOptions<E>.provideDelegate(
  thisRef: ParameterHolder,
  property: KProperty<*>,
): ReadOnlyProperty<ParameterHolder, Set<E>> {
  values.forEach { thisRef.registerOption(it) }
  return ReadOnlyProperty { _, _ ->
    mapNotNull { (value, option) -> if (option.value) value else null }.toSet()
  }
}

operator fun <T : BaseCliktCommand<T>, E : Enum<E>> EnumFlagsOptionGroup<E>.provideDelegate(
  thisRef: T,
  property: KProperty<*>,
): ReadOnlyProperty<T, Set<E>> {
  thisRef.registerOptionGroup(this)
  options.forEach { (_, option) -> thisRef.registerOption(option) }
  return ReadOnlyProperty { _, _ ->
    options.mapNotNull { (value, option) -> if (option.value) value else null }.toSet()
  }
}
