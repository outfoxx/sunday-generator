# Patchable models

Request bodies with content type `application/merge-patch+json` automatically generate and select a
companion `<Schema>Patch` model for their object schema, regardless of HTTP method. No annotation is
required. Other content types and responses retain the ordinary model, including ordinary JSON on a
PATCH operation. Each content alternative is handled separately; media type matching ignores case and
parameters. Inherited fields and their constraints are
included. Object aliases and external references retain the same selection through IR serialization
and source composition. A numeric suffix avoids collisions with existing schema names.

Use `x-sunday-patchable: true` to explicitly request a companion even when no merge-patch request body
references the schema. The annotation does not change body selection for other media types.

Automatic promotion is enabled by default. Disable only the inference step with CLI
`-no-auto-patchable` (including the `ir` command), Gradle `autoPatchable.set(false)`, or programmatic
`GeneratedApiIrOptions(autoPatchable = false)`. Explicit `x-sunday-patchable: true` schemas still
generate companions, and merge-patch bodies referring to them still select those companions. Explicit
RAML annotations also request companions. CLI `-auto-patchable` enables inference again.

```kotlin
sundayGenerations {
  create("client") {
    autoPatchable.set(false)
  }
}
```

```yaml
paths:
  /items/{id}:
    patch:
      parameters:
        - {name: id, in: path, required: true, schema: {type: string}}
      requestBody:
        required: true
        content:
          application/merge-patch+json:
            schema: {$ref: '#/components/schemas/UpdateItem'}
      responses: {'204': {description: Updated}}
components:
  schemas:
    UpdateItem:
      type: object
      required: [name]
      properties:
        name: {type: string, minLength: 2, default: Untitled}
        description: {type: [string, 'null']}
```

For `UpdateItemPatch`, omission leaves a member unchanged and a supplied value sets or merges it.
An explicit null deletes the member, as defined by [RFC 7396](https://www.rfc-editor.org/rfc/rfc7396).
Deletion is permitted only for members that are optional in the original model. A required member
cannot be deleted even when its value is nullable; merge patch cannot assign a literal null to an
object member. Arrays retain their element nullability because an array replaces the entire value.
Ordinary patch fields may be omitted, and schema defaults never select updates implicitly. Constraints
still apply to supplied values. Nested object members use their own patch companions, while array
elements retain ordinary model types because arrays replace whole values.
Recursive object models are supported; self-recursive map aliases receive a generation diagnostic.

| Original member | Patch operations |
| --- | --- |
| Required, non-nullable | unchanged, set |
| Required, nullable | unchanged, set a non-null value |
| Optional, non-nullable | unchanged, set, delete |
| Optional, nullable | unchanged, set a non-null value, delete |

| Target | Unchanged | Set | Delete an optional member |
| --- | --- | --- | --- |
| Kotlin | `PatchOp.none()` | `PatchOp.set(value)` | `PatchOp.delete()` |
| Swift | `.unchanged` | `.set(value)` | `.delete` |
| TypeScript | omitted / `undefined` | `value` | `null` |
| Python | omitted / `UNSET` | `value` | `None` |

Kotlin and Swift use `UpdateOp<T>` for required members and `PatchOp<T>` for optional members, with
non-nullable value arguments and non-optional operation properties. Swift defaults those properties
to `.unchanged` and flattens inherited patch fields into `Sendable` models, using structs for value
models and classes for recursive graphs. Assign `.unchanged` to
cancel a pending update; its Codable helpers omit that member. A standalone unchanged operation has
no JSON representation and fails encoding, as does a manually constructed `.set(nil)`.
TypeScript uses optional properties with native Zod validation. Python emits `SundayPatchModel`
subclasses whose fields include `UnsetType`, use `UNSET` as their omission default, and retain native
Pydantic validation. Explicit `UNSET` follows Pydantic's alias/name selection and is omitted from
serialization; assigning it cancels a previously selected update.

```python
from sunday import UNSET
from my_api.models import UpdateItemPatch

patch = UpdateItemPatch(name="Updated", description=None)
patch.name = UNSET
assert patch.model_dump(mode="json") == {"description": None}
```

Existing request-mode validation applies to patch payloads on every execution. Omitted fields do not
participate. Unknown enum/union values in supplied fields follow the configured directional tolerance;
mutation after a successful check is validated again at the request boundary.

RAML `(sunday.patchable): true` generates both the ordinary model and its companion, including inherited
patchability, with the same rules as native `x-sunday-patchable`. Ordinary JSON bodies and responses keep
the ordinary model. Native AsyncAPI object schemas
can also declare `x-sunday-patchable`; event payloads retain their ordinary type, and the companion is
available to consumers. A patchable declaration must resolve to an object. Discriminated object
hierarchies generate a companion hierarchy: for example, `AnimalPatch` selects `CatPatch` or `DogPatch`
using the same discriminator wire values. Both automatic discovery and explicit annotations support
these companions. Externally discriminated declarations and non-object unions are not supported as
patch roots.

The discriminator is a required, non-null dispatch tag, never an update operation. Missing, null, and
unknown tags are rejected, including when the ordinary hierarchy tolerates unknown response variants.
The tag stays fixed to the selected subtype. A discriminator-only patch is a valid no-op. All other
fields, including inherited fields, retain the omission, set, and deletion rules above. A required
nullable property may be omitted but cannot be deleted.

A subtype patch derives its field rules only from its own schema and explicit inheritance. Fields
declared only by another variant are ordinary unknown fields, with the same storage and decoding
policy as any other undeclared name. Ordinary JSON models retain their original contracts.

Merge helpers reject changes to the discriminator; Swift exposes concrete subtype helpers so a
`DogPatch` cannot be passed to `Cat.merge`. Difference helpers retain the discriminator even when it
has not changed. Services must compare the patch tag with the persisted resource before mutating it
and may return HTTP 400 for a mismatch; the generator cannot perform that persistence check.

Merge-patch generation requires Sunday Swift `2.0.0-beta.9`, Sunday Kotlin `2.0.0-beta.9`, and Sunday
Python `2.0.0-beta.6` or later. Swift patch companions with inferred `Identifiable` conformance require
`2.0.0-beta.15` or later for conditional `Hashable` support on `PatchOp` and `UpdateOp`. The compiler-backed
tests use Swift `2.0.0-beta.17`, Kotlin `2.0.0-beta.16`, and Python `2.0.0-beta.10`, including
Swift's explicit-state API and decoder fixes and Python's `UNSET` API.
Python uses its released Git tag until PyPI publishing is enabled. The Kotlin runtime rejects
`UpdateOp` deletion directly and supports decoding operations at root and collection positions,
without relying on generated Jackson field annotations.

### Swift polymorphic patch fields

Polymorphic patch fields store protocol values, including inside arrays and dictionaries. For
example, an array field uses `PatchOp<[any Animal]>`, so callers can supply `.set([cat, dog])` directly.
Decoding uses `AnimalRef` to dispatch by discriminator, then unwraps each reference's `.value` before
storing it. Encoding creates temporary references. Omission and deletion retain their usual semantics.

This requires Sunday Swift `2.0.0-beta.17` or later for conditional coding conformances and operation
`map` support. Generic runtime clients that previously relied on
`AnyPatchOp` inheriting `Codable` must now request the coding conformance explicitly.

## Model conversion and merge

Every paired object model exposes a snapshot conversion, a difference against a previous model, and
an immutable merge. The patch type also provides a delegating conversion constructor or factory.
TypeScript models remain structural values, so their helpers live on the same-name exported value.

| Target | Snapshot | Difference | Merge | Patch factory |
| --- | --- | --- | --- | --- |
| Kotlin | `model.patch()` | `updated.patch(from = original)` | `base.merge(patch)` | `ItemPatch.fromModel(model)` |
| Swift | `try model.patch()` | `try updated.patch(from: original)` | `try base.merge(patch)` | `try ItemPatch(model)` |
| TypeScript | `Item.patch(model)` | `Item.patch(updated, original)` | `Item.merge(base, patch)` | `ItemPatch.fromModel(model)` |
| Python | `model.patch()` | `updated.patch(from_model=original)` | `base.merge(patch)` | `ItemPatch.from_model(model)` |

Swift also exposes `try ItemPatch.fromModel(model)`, including for recursive reference models.
For discriminated hierarchies, Swift and Python expose conversion factories on concrete patch
variants; their root dispatch types select those variants during decoding.

Member names are allocated to avoid schema-property collisions. As with companion type names, a
collision may add a suffix. Helpers reuse the generated codecs and existing response-mode validation;
request-mode validation still runs at the transport boundary. Kotlin accepts an optional application
`ObjectMapper`, and TypeScript accepts an optional schema runtime for application codec configuration.

`patch()` captures the fields represented by the ordinary model. It fails when a required nullable
member contains null, because JSON Merge Patch cannot represent that assignment. `patch(from:)` omits
unchanged fields, including unchanged required nulls; changing such a member from a non-null value to
null fails with the codec's wire-path diagnostic. Changing it to a non-null value succeeds. Optional
nulls select deletion, consistent with the model's existing optional-field representation. Nulls inside
replaced arrays remain ordinary values.

`merge` recursively merges objects, removes members whose patch value is null, and replaces arrays and
scalars. It returns a new ordinary model through native validation. Schema defaults do not resurrect
deleted optional fields. Neither input is modified. Snapshot and difference conversion never silently
discard unrepresentable updates; conversion fails if decoding the companion changes its wire value.
There is no ordinary-model constructor from a patch alone or a redundant `(base, patch)` constructor.
