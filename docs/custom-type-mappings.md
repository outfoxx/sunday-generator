# OpenAPI and AsyncAPI custom language type mappings

A named OpenAPI or AsyncAPI schema can refer to an existing language type instead of generating
a replacement model or using the schema's default scalar type. Mappings are
independent: one contract can carry Kotlin, Swift, TypeScript, and Python types.

AsyncAPI 2.x and 3.x use the same extensions on named `components.schemas`
declarations. Mappings survive external schema references and direct aliases;
`allOf` parents do not transfer their mapping to a child declaration. Kotlin
broker payloads use the selected custom type, including client-specific mappings.
Other languages use the same mapped model types and serialization contracts below.

## Kotlin

Use the common Kotlin mapping and optional mode-specific overrides:

```yaml
components:
  schemas:
    Identifier:
      type: string
      pattern: '^id:[a-z]+$'
      x-sunday-kotlinType: com.example.Identifier
      x-sunday-kotlinType:client: com.example.ClientIdentifier
      x-sunday-kotlinType:server: com.example.ServerIdentifier
```

Each extension accepts a non-empty Kotlin type name. Use a fully qualified name
for an application-owned type. A simple name resolves in the configured model
package. The client or server mapping takes precedence over the common mapping
for that mode. Without a mode-specific mapping, the common mapping applies.
Kotlin Sunday uses the client mapping; Kotlin JAX-RS uses its selected generation
mode. These extensions lower to `GeneratedModel.targets` under `kotlin`,
`kotlinClient`, and `kotlinServer`, respectively, matching RAML's
`(sunday.kotlinType)`, `(sunday.kotlinType:client)`, and
`(sunday.kotlinType:server)` annotations.

Put the extension on the schema declaration, including a schema in an external
file referenced with `$ref`. References from parameters, model properties, and
request/response bodies use the supplied type. Kotlin generation does not emit a
replacement declaration for that type. The mapping does not change the wire
schema, its validation metadata, or output for targets without a mapping.

The application must put the mapped class on its compilation/runtime classpath
and supply its wire conversion. For a string identifier:

- Provide the JAX-RS string constructor, static `fromString`/`valueOf`, or a
  registered `ParamConverterProvider` for path/query conversion.
- Supply Jackson serialization/deserialization, such as `@JsonValue` and a
  delegating `@JsonCreator`. Configure the Kotlin Jackson module when binding
  generated Kotlin models.
- Return the wire string from `toString()` so generated string-schema checks
  validate the same representation. String-only Bean Validation annotations
  (`@Pattern`, `@Size`, `@Email`) are replaced with Sunday’s native `@Schema`
  constraint on mapped parameters, so wire restrictions still apply without
  asking Bean Validation to treat the custom JVM class as a string.

Generated model-property constraints continue to enforce the source restrictions
using the wire representation, with either constructor checks or Sunday's native
Bean Validation support. Mapping an identifier does not automatically supply a
serializer, converter, or application-specific contract projection. For mapped
object unions with common property rules, implement
`io.outfoxx.sunday.validation.ModelGraph.validationFields()` to expose the wire
property names and values to native validation. The generator keeps the common
validation helper local rather than requiring it inside the mapped class.

## Swift

```yaml
Identifier:
  type: string
  pattern: '^id:[a-z]+$'
  x-sunday-swiftType: DomainModels.Identifier
```

Use a module-qualified Swift name, or a simple name for a type supplied alongside
the generated sources. The type must conform to `Codable` and satisfy the
`Sendable` requirements of generated containers. No replacement type is emitted.
Generated validators encode the application-owned value with `AnyValueEncoder`
to check its wire representation against the original schema. Failed encoding
is invalid. Schema validation helpers remain in the generated module.

## TypeScript

```yaml
Identifier:
  type: string
  pattern: '^id:[a-z]+$'
  x-sunday-typeScriptType: Identifier
  x-sunday-typeScriptModule: '@example/domain'
```

The module is imported exactly as supplied (an npm package or a relative module
specifier). It must export the named type and a matching `IdentifierSchema`
Sunday `SchemaLike`, such as a Zod codec. The codec decodes the wire value into
the custom type and encodes it back. Generated model schemas retain wire
restrictions around that codec; request parameters encode typed values before
passing them to the transport. No replacement class or interface is emitted.

Omit `x-sunday-typeScriptModule` for a global type. Built-in types already known
to Sunday's runtime, such as `Date` and `URL`, retain their built-in schemas.
Other global types require a matching global `<Type>Schema`.

## Python

```yaml
Identifier:
  type: string
  x-sunday-pythonType: example.domain.Identifier
```

Use the fully qualified module and exported type name. The generated models
module exports a type alias to that application-owned type, so clients and
Litestar server contracts use the same mapping. The type must support Pydantic
validation and serialization (for example through `__get_pydantic_core_schema__`).
Generated validators check its serialized wire value while retaining the custom
instance. Python mappings are common to client and server generation.

All extension values must be non-empty strings. Swift and TypeScript spellings
match their existing RAML annotations, `(sunday.swiftType)` and
`(sunday.typeScriptType)`; Python mapping is provided by the native OpenAPI
frontend. Model references, including external `$ref` declarations, retain the
mapping. Extensions for one language do not alter another language's output.
