# RFC 9457 Problem standard members

This change applies only to recognized HTTP Problems and the five standard members: `type`, `title`, `status`, `detail`, and `instance`. Ordinary models, extension requirements, and nested extension contents retain their authored semantics. The optional-member encoder fix from PR #331 remains in place.

## Decoding and encoding

Null and wrong JSON categories behave like omitted members before defaults, validation, and Problem union selection. String members accept strings; status accepts numbers and must fit the runtime's exact integer representation. Strings and booleans are not coerced to numbers, and fractional numbers are not truncated. Correctly typed values still undergo authored constraints.

Missing or discarded type means `about:blank`. It never silently becomes a custom Problem's fixed type, so decoding that value into a fixed custom subtype still fails. Standalone generic `{}` does not invent title or status. HTTP transport retains valid body status, supplies response status when absent, and derives default titles only for `about:blank`.

Making standard members optional can expose existing overlap between a generic Problem and another `oneOf` branch. Exactly-one matching remains enforced; use a fixed type or required extension to distinguish such branches.

Encoding omits absent standard members while preserving valid empty strings and nullable extensions. Standard members discarded during decoding do not become extensions.

## Runtime requirements and migration

Regenerated Problems require these runtime versions or later:

| Target | Minimum version | Implementation / API change |
| --- | --- | --- |
| Kotlin Sunday | `2.0.0-beta.19` | `ProblemString`, `ProblemReference`, and `ProblemStatus` Jackson annotations; scoped Problem union normalization. No additional Jackson module registration. |
| TypeScript | `2.0.0-beta.18` | Bidirectional Zod property codecs; optional title/status; URI members are `URL \| string`. |
| Swift | `2.0.0-beta.19` | Optional title/status; `type: String`, `instance: String?`, and optional `typeURI` / `instanceURI` convenience accessors. |
| Python | Git tag `2.0.0-beta.13` | Inherited Pydantic Problem pre-validation with fixed standard-member names. |

Swift stores the original URI reference, including empty and relative references. Use the optional URL accessors when Foundation URL behavior is needed. A URL-taking GenericProblem initializer remains available. Its dictionary decoding initializers now throw for correctly typed semantic errors.

TypeScript property codecs support `.parse()`, `.decode()`, `.safeParse()`, and `.encode()`. Specialized generated schemas compose constraints and defaults after category normalization. Use `.toString()` for either URI representation, and narrow with `instanceof URL` before accessing URL-specific fields. Encoding validates application-side types.

Kotlin uses property-level inclusion rules, preserving nullable custom properties. Annotated Problem factory creators keep convenient programmatic defaults separate from the RFC wire type default. Jackson constructs registered types through its normal creator handling; generated schema-aware extension handling remains in place.

Python keeps Pydantic field-presence information and inherited validators. Normalization copies inputs without changing ordinary SundayModel behavior. The five standard members use their fixed RFC wire names for decoding, HTTP defaults, and encoding. Sunday rejects aliases that rename these members or let extensions claim their names; this is a Sunday API restriction, not an RFC requirement. Other extension aliases and paths retain native Pydantic behavior. Authored requiredness, constraints, defaults, and serializers remain supported.

## Quarkus and Zalando limitations

Their independent classes/decoders are unchanged. Sunday's shared transport applies standard-member normalization before invoking a registered adapter, but this does not make standalone upstream decoding compliant.

| Dependency audited | Standalone behavior |
| --- | --- |
| Quarkus HTTP Problem `3.21.0` | Wrong-typed string members cause cast failures; null status causes a null-pointer failure; omitted status becomes 500. Its primitive status also cannot represent an absent status, separately from those decoding bugs. |
| Zalando Problem `1.0.0` with `problem-jackson2:1.0.0` | Numeric/boolean string members are coerced; other wrong categories are rejected. |

[RFC 9457 §3.1](https://www.rfc-editor.org/rfc/rfc9457.html#section-3.1) introduced the explicit wrong-type rule; [RFC 7807 §3.1](https://www.rfc-editor.org/rfc/rfc7807.html#section-3.1) did not contain it. Quarkus's [migration PR #62](https://github.com/quarkiverse/quarkus-http-problem/pull/62) cited headers, builder flexibility, integration, and performance. Its [RFC discussion #315](https://github.com/quarkiverse/quarkus-http-problem/issues/315) raises compatibility concerns. Neither establishes that decoding defects were an intentional RFC tradeoff. The historical maintenance concern was prolonged inactivity at the time of the Quarkus migration, not a claim that Zalando core remains abandoned. Zalando's later [core 1.0.0 release](https://github.com/zalando/problem/releases/tag/1.0.0) is distinct from the Spring integration's maintenance status.

The requested Zalando upgrade replaces 0.27.1 with core and problem-jackson2 1.0.0 without a Jackson 3 migration. The standalone behavior above was reproduced again on 1.0.0. No upstream patches, compatibility modules, or wrappers are included. Runtime PRs: [Kotlin #70](https://github.com/outfoxx/sunday-kt/pull/70), [TypeScript #87](https://github.com/outfoxx/sunday-js/pull/87), [Swift #81](https://github.com/outfoxx/sunday-swift/pull/81), and [Python #14](https://github.com/outfoxx/sunday-python/pull/14).
