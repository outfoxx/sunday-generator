# Kotlin model value equality

Kotlin/Sunday and Kotlin/JAX-RS generate structural `equals` and `hashCode`
for implemented data models by default in the CLI and Gradle plugin.
Regenerating an existing SDK enables value comparison, which can change its
collection or state-comparison behavior. Disable the option to retain the
previous equality behavior.

## Configuration

- CLI: `-model-value-equality` enables the policy; `-no-model-value-equality`
  disables it. The default is enabled.
- Kotlin Gradle plugin: `modelValueEquality` defaults to `true`. Set
  `modelValueEquality.set(false)` in a generation block or on a `SundayGenerate`
  task to disable it.
- Generator API: include `KotlinTypeRegistry.Option.ModelValueEquality` in the
  registry options.

Regenerate all participating models with the same setting. The option affects
implemented model classes, including existing data classes and unknown
polymorphic fallback models. Interfaces cannot supply these implementations.
HTTP Problem exceptions and their descendants keep their existing equality.
Custom mapped types retain the equality supplied by their implementation.

Without the option, ordinary classes inherit reference equality. Models already
emitted as data classes keep Kotlin's generated equality, which uses only their
primary-constructor properties. Data classes cannot serve as inheritance bases,
and their built-in equality does not compare byte-array contents. Enabling the
option preserves the chosen class/data-class shape and adds explicit methods.

## Value policy

Two models are equal when they have the same runtime class and equal retained
values. The runtime-class requirement prevents a base instance from considering
a subclass equal when the subclass has additional state. Declared and inherited
fields, fixed discriminators, nested generated values, and retained additional
properties participate. Unknown discriminator fallbacks also compare their
retained raw body.

Lists and arrays compare in order. Byte arrays and other primitive arrays compare
by contents, including arrays nested in lists and map values. Map insertion
order is irrelevant; keys use their normal equality and values are compared
recursively. Generated JSON maps have string keys. Other values use their own
`equals` and `hashCode` contracts, including custom runtime types and numeric
values: this is Kotlin value equality, not canonical JSON numeric equivalence.

Equality uses the state the model actually retains. If decoding maps both an
absent declared optional field and explicit JSON null to Kotlin `null`, those
values compare equal. A retained additional-property key with a null value is
distinct from a missing key. Discarded unknown fields do not participate, and
schema defaults compare as their resulting stored values. Patch models keep
no-update, delete, and set operations distinct; set payloads use the same
recursive value policy, including byte-array contents.

Equal values have equal hash codes and work as equivalent map keys or set
members. Hash codes are process-local implementation details, not persistent
content identifiers.

## Mutation and ownership

This option does not make models immutable or copy their contents. Mutable
properties, arrays, collections, nested models, raw fallback bodies, and
`setAdditionalProperty` updates can change equality and hashing. Do not mutate
any reachable value while a model is a hash-map key or a hash-set member; remove
it before mutation and reinsert it afterward, or use a separate immutable key.
Application subclasses that add state must maintain the same equality contract.
As with Kotlin data classes, cyclic object graphs are not supported by recursive
value comparison or hashing.
