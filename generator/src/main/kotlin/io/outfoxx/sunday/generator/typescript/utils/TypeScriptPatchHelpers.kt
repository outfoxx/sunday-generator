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

package io.outfoxx.sunday.generator.typescript.utils

import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.TypeName

/** Emits type-associated helpers without changing TypeScript's ordinary structural model types. */
internal object TypeScriptPatchHelpers {
  private fun TypeName.Standard.sibling(name: String): TypeName.Standard =
    when (val symbol = base) {
      is io.outfoxx.typescriptpoet.SymbolSpec.Imported ->
        TypeName.standard(
          io.outfoxx.typescriptpoet.SymbolSpec.importsName(
            symbol.value + name,
            symbol.source,
          ),
        )
      is io.outfoxx.typescriptpoet.SymbolSpec.Implicit ->
        TypeName.standard(
          io.outfoxx.typescriptpoet.SymbolSpec.implicit(
            symbol.value + name,
          ),
        )
    }

  fun adapters(
    type: TypeName.Standard,
    patch: TypeName.Standard,
    support: TypeName.Standard,
  ): CodeBlock =
    CodeBlock.of(
      """
      /** Merge-patch conversions for the associated structural model. */
      export const %L = {
        /** Creates a snapshot, or only the changes from an earlier model. */
        patch(value: %T, from?: %T, runtime = %T.runtime): %T {
          const schema = runtime.resolveSchema(%T);
          const patchSchema = runtime.resolveSchema(%T);
          const tree = %T.difference(%T.encode(schema, value), from === undefined ? undefined : %T.encode(schema, from));
          const result = patchSchema.parse(tree);
          if (!%T.equal(%T.encode(patchSchema, result), tree)) throw new TypeError('Patch conversion changed the wire value');
          return result;
        },
        /** Recursively merges into a new validated model; the inputs are unchanged. */
        merge(value: %T, patch: %T, runtime = %T.runtime): %T {
          const schema = runtime.resolveSchema(%T);
          const tree = %T.merge(%T.encode(schema, value), %T.encode(runtime.resolveSchema(%T), patch));
          return %T.withoutDefaults(schema.parse(tree), tree);
        },
      };
      """.trimIndent() + "\n",
      type.simpleName(),
      type,
      type,
      support,
      patch,
      type.sibling("Schema"),
      patch.sibling("Schema"),
      support,
      Z,
      Z,
      support,
      Z,
      type,
      patch,
      support,
      type,
      type.sibling("Schema"),
      support,
      Z,
      Z,
      patch.sibling("Schema"),
      support,
    )

  fun factory(
    type: TypeName.Standard,
    original: TypeName.Standard,
    support: TypeName.Standard,
  ): CodeBlock =
    CodeBlock.of(
      """
      /** Conversion factory for this merge-patch companion. */
      export const %L = {
        /** Delegates snapshot conversion to the ordinary model. */
        fromModel(value: %T, runtime = %T.runtime): %T { return %T.patch(value, undefined, runtime); },
      };
      """.trimIndent() + "\n",
      type.simpleName(),
      original,
      support,
      type,
      original,
    )

  fun support(name: TypeName.Standard): Pair<ClassSpec.Builder, CodeBlock> =
    ClassSpec
      .builder(name.simpleName())
      .addModifiers(Modifier.EXPORT)
      .addTSDoc("Shared codec configuration and RFC 7396 operations for model conversions.")
      .constructor(FunctionSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build()) to
      CodeBlock.of(
        """
        export namespace %L {
          /** Default response-mode codec configuration for local model conversions. */
          export const runtime = %Q({format: 'json', dateEncoding: %T.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: %T.BASE64});
          const object = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && [Object.prototype, null].includes(Object.getPrototypeOf(value));
          /** Compares wire values independently of object member ordering. */
          export function equal(a: unknown, b: unknown): boolean {
            if (Object.is(a, b)) return true;
            if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((v, i) => equal(v, b[i]));
            if (!object(a) || !object(b)) return false;
            const keys = Object.keys(a).filter(k => a[k] !== undefined);
            return keys.length === Object.keys(b).filter(k => b[k] !== undefined).length && keys.every(k => Object.hasOwn(b, k) && equal(a[k], b[k]));
          }
          /** Builds a merge-patch document; arrays and scalars replace whole values. */
          export function difference(value: unknown, original: unknown): unknown {
            if (!object(value)) return value;
            const before = object(original) ? original : {};
            const result: Record<string, unknown> = Object.create(null);
            for (const [key, item] of Object.entries(value)) {
              if (item !== undefined && (!Object.hasOwn(before, key) || !equal(item, before[key]))) result[key] = difference(item, before[key]);
            }
            for (const key of Object.keys(before)) if (before[key] !== undefined && value[key] === undefined) result[key] = null;
            return result;
          }
          /** Implements the object recursion and deletion rules of RFC 7396. */
          export function merge(value: unknown, patch: unknown): unknown {
            if (!object(patch)) return patch;
            const result: Record<string, unknown> = Object.assign(Object.create(null), object(value) ? value : {});
            for (const [key, item] of Object.entries(patch)) {
              if (item === null) delete result[key];
              else if (item !== undefined) result[key] = merge(result[key], item);
            }
            return result;
          }
          /** Retains absent optional members after schema parsing, rather than restoring their defaults. */
          export function withoutDefaults<T>(value: T, wire: unknown): T {
            if (Array.isArray(value) && Array.isArray(wire)) return value.map((v, i) => withoutDefaults(v, wire[i])) as T;
            if (!object(value) || !object(wire)) return value;
            const result: Record<string, unknown> = Object.create(Object.getPrototypeOf(value));
            for (const [key, item] of Object.entries(value)) if (Object.hasOwn(wire, key)) {
              Object.defineProperty(result, key, {value: withoutDefaults(item, wire[key]), enumerable: true, writable: true, configurable: true});
            }
            return result as T;
          }
        }
        """.trimIndent() + "\n",
        name.simpleName(),
        io.outfoxx.typescriptpoet.SymbolSpec
          .importsName("createSchemaRuntime", "@outfoxx/sunday"),
        TypeName.namedImport("DateEncoding", "@outfoxx/sunday"),
        TypeName.namedImport("ArrayBufferEncoding", "@outfoxx/sunday"),
      )
}
