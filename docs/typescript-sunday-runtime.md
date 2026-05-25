# TypeScript/Sunday Runtime Notes

TypeScript/Sunday output uses Zod for generated schema codecs and runtime validation.

Generated clients require Zod 4.x. The generator emits Zod 4 APIs such as `z.codec`, `z.looseObject`, and the current `z.discriminatedUnion` behavior used by named union and external-discriminator schemas. Consumers should depend on `zod` `^4.0.0` or newer alongside `@outfoxx/sunday`.
