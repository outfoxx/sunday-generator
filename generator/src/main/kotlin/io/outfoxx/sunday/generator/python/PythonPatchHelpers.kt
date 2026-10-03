/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.python

import com.squareup.kotlinpoet.NameAllocator
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Pydantic conversion adapters sharing one recursive wire-value implementation. */
internal class PythonPatchHelpers(
  private val models: List<GeneratedModel>,
) {
  val enabled = models.any { it.patchOf != null }
  private val mergedModels =
    buildSet {
      val index = models.associateBy { it.name }

      fun visit(type: GeneratedTypeRef) {
        type.arguments.forEach(::visit)
        val model = index[type.name] ?: return
        if (!add(model.name)) return
        (model.inherits + model.aliases + model.properties.map { it.type }).forEach(::visit)
        model.additionalProperties?.type?.let(::visit)
        model.patternProperties.forEach { visit(it.type) }
      }
      models.mapNotNull { it.patchOf }.forEach(::visit)
    }

  /** Optional defaults can be deleted anywhere in a merged graph, including inside replaced arrays. */
  fun canMerge(model: GeneratedModel): Boolean = model.name in mergedModels

  private val names =
    NameAllocator(preallocateKeywords = false).apply {
      models
        .flatMap { it.properties }
        .map { it.name.pythonIdentifierName }
        .distinct()
        .forEach { newName(it) }
    }
  private val patchName = names.newName("patch")
  private val mergeName = names.newName("merge")
  private val factoryName = names.newName("from_model")

  fun adapters(model: GeneratedModel): PythonCodeBlock? {
    val patch = models.singleOrNull { it.patchOf?.name == model.name }
    if (patch != null) {
      val root =
        generateSequence(model) { current ->
          current.inherits.firstOrNull()?.let { parent -> models.singleOrNull { it.name == parent.name } }
        }.last()
      val acceptedPatch = models.singleOrNull { it.patchOf?.name == root.name } ?: patch
      return PythonCodeBlock.of(
        """
        def %L(self, from_model: %T | None = None) -> %L:
            '''Creates a snapshot or differences; required null assignments cannot be represented.'''
            type(self).model_validate(self, context={"mode": "response"})
            original = None if from_model is None else from_model.model_dump(mode="python", by_alias=True)
            tree = _patch_difference(self.model_dump(mode="python", by_alias=True), original)
            result = %L.model_validate(tree, context={"mode": "response"})
            if not _patch_equal(result.model_dump(mode="python", by_alias=True), tree):
                raise ValueError("Patch conversion changed the wire value")
            return result

        def %L(self, patch: %L) -> %T:
            '''Recursively merges into a new validated model without changing either input.'''
            type(self).model_validate(self, context={"mode": "response"})
            type(patch).model_validate(patch, context={"mode": "response"})
            tree = _patch_merge(
                self.model_dump(mode="python", by_alias=True), patch.model_dump(mode="python", by_alias=True)
            )
            result = type(self).model_validate(tree, context={"mode": "response"})
            _patch_remove_defaults(result, tree)
            return result
        """.trimIndent().replace("'''", "\"\"\"").prependIndent("    ").lines().joinToString("\n") {
          it.trimEnd()
        },
        patchName,
        PythonSymbol("typing", "Self"),
        patch.name.pythonTypeName,
        patch.name.pythonTypeName,
        mergeName,
        acceptedPatch.name.pythonTypeName,
        PythonSymbol("typing", "Self"),
      )
    }
    val original = models.singleOrNull { it.name == model.patchOf?.name } ?: return null
    return PythonCodeBlock.of(
      """
      @classmethod
      def %L(cls, model: %T) -> %T:
          '''Creates a snapshot by delegating to the standard model's conversion.'''
          if not isinstance(model, %L):
              raise TypeError(%S)
          return cls.model_validate(model.%L(), context={"mode": "response"})
      """.trimIndent().replace("'''", "\"\"\"").prependIndent("    ").lines().joinToString("\n") {
        it.trimEnd()
      },
      factoryName,
      PythonSymbol("sunday", "SundayModel"),
      PythonSymbol("typing", "Self"),
      original.name.pythonTypeName,
      "Expected ${original.name.pythonTypeName}",
      patchName,
    )
  }

  fun support(): PythonCodeBlock =
    PythonCodeBlock.of(
      """
      def _patch_equal(a: object, b: object) -> bool:
          if isinstance(a, bool) != isinstance(b, bool):
              return False
          if isinstance(a, dict) and isinstance(b, dict):
              return a.keys() == b.keys() and all(_patch_equal(a[k], b[k]) for k in a)
          if isinstance(a, (list, tuple)) and isinstance(b, (list, tuple)):
              return len(a) == len(b) and all(_patch_equal(x, y) for x, y in zip(a, b, strict=True))
          return bool(a == b)


      def _patch_difference(value: object, original: object) -> object:
          if not isinstance(value, dict):
              return %T(value)
          before = original if isinstance(original, dict) else {}
          result = {
              key: _patch_difference(item, before.get(key))
              for key, item in value.items()
              if key not in before or not _patch_equal(item, before[key])
          }
          result.update({key: None for key in before if key not in value})
          return result


      def _patch_merge(value: object, patch: object) -> object:
          if not isinstance(patch, dict):
              return %T(patch)
          result = %T(value) if isinstance(value, dict) else {}
          for key, item in patch.items():
              if item is None:
                  result.pop(key, None)
              else:
                  result[key] = _patch_merge(result.get(key), item)
          return result


      def _patch_remove_defaults(value: object, wire: object) -> None:
          # Only fresh decoded output is changed. Presence must survive deletion of a defaulted field.
          if isinstance(value, %T) and isinstance(wire, dict):
              for name, field in type(value).model_fields.items():
                  key = field.serialization_alias or field.alias or name
                  if key not in wire and not field.is_required():
                      value.__dict__[name] = None
                      value.__pydantic_fields_set__.discard(name)
                  elif key in wire:
                      _patch_remove_defaults(getattr(value, name), wire[key])
          elif isinstance(value, dict) and isinstance(wire, dict):
              for key, item in value.items():
                  if key in wire:
                      _patch_remove_defaults(item, wire[key])
          elif isinstance(value, (list, tuple)) and isinstance(wire, (list, tuple)):
              for item, source in zip(value, wire, strict=True):
                  _patch_remove_defaults(item, source)
      """.trimIndent(),
      PythonSymbol("copy", "deepcopy"),
      PythonSymbol("copy", "deepcopy"),
      PythonSymbol("copy", "deepcopy"),
      PythonSymbol("pydantic", "BaseModel"),
    )
}
