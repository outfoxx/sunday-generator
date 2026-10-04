# TypeScript/Sunday Runtime Notes

TypeScript/Sunday output uses Zod for generated schema codecs and runtime validation.

Generated clients require Zod 4.x. The generator emits Zod 4 APIs such as `z.codec`, `z.looseObject`, and the current `z.discriminatedUnion` behavior used by named union and external-discriminator schemas. Consumers should depend on `zod` `^4.0.0` or newer alongside `@outfoxx/sunday`.

## Parameter defaults

Collection defaults are native values (`[]`, arrays, and objects), with typed
enum and formatted elements. Each omitted argument receives a fresh value, including
nested collections. Explicit `null` remains absent, and explicit values are retained.

## Closed model decoding

Models with `additionalProperties: false` use `z.strictObject` to reject undeclared
wire properties. Open models retain `z.looseObject`. Discriminator refinements retain
the original object's strictness, and recursive interfaces use the same wire names
as their schemas.
