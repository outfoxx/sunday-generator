# Declaration placement

Placement chooses generated package and module identities independently of [output selection](output-selection.md). References to excluded declarations still use their resolved identities.

RAML uses `(sunday.placement)` and imports the bundled `sunday.raml` library. OpenAPI and AsyncAPI use `x-sunday-placement`. The contents are the same:

```yaml
x-sunday-placement:
  kotlin:
    package: example.orders
    models:
      package: example.orders.models
    services:
      package: example.orders.api
    client:
      services:
        package: example.orders.client
    server:
      services:
        package: example.orders.server
  swift:
    module: OrdersSDK
  typescript:
    module: orders
    models:
      module: orders/models
  python:
    package: orders_sdk
    models:
      module: models
```

Kotlin packages are qualified names. Swift modules are compilation modules; consumers configure the corresponding build targets. TypeScript modules are output-relative directories containing generated filenames. Python packages and modules are dotted names: `package: shared_models` with `module: money` writes `shared_models/money.py`.

If TypeScript declarations in different directories share an exported name, the root index exports those files through deterministic `module_<path>` namespaces. Direct imports from the generated files keep their original names.

## Defaults and overrides

Document and container scopes support direct fields, `models`, `services`, and `client`/`server` variants. A role cannot contain another role. Types use direct fields and optional role variants. Service scopes may also supply `models` defaults for their owned local declarations. Operation placement applies to operation-local declarations.

Each field inherits independently. Within a scope, client model placement uses:

```text
client.models.field → client.field → models.field → field
```

Replace `client` with `server` or `models` with `services` as appropriate. Narrower ownership wins before considering role specificity:

```text
declaration → operation → service → lexical container → declaring document
→ invocation defaults → language fallback
```

References never introduce an ownership scope. Problems use model defaults while retaining the `problems` output category. Companions inherit their declaration's placement. Excluding any enclosing type also excludes all of its nested descendants and their companions; filtering never hoists nested types or forces a container to be emitted.

The invocation-wide Zanzibar JWT extractor uses the document or invocation service package, or the single resolved service package when no default exists. If its services span multiple packages, provide a shared service package default. Only one CDI extractor is generated.

Kotlin requires a package. Swift permits an unnamed output module. Named Swift modules cannot depend on unnamed declarations; assign a module to those dependencies. TypeScript defaults to the output root. Python retains an API-derived package and the `models` module. Omitted fields inherit; null, blank, invalid, unknown, and unsupported values are errors.

## Equivalent source examples

RAML:

```yaml
#%RAML 1.0
title: Orders
uses:
  sunday: sunday.raml
(sunday.placement):
  kotlin:
    models: {package: example.orders.models}
types:
  Order:
    type: object
    properties: {id: string}
  Money:
    type: object
    (sunday.placement):
      kotlin: {package: example.shared}
    properties: {amount: number}
  Context:
    type: object
    (sunday.placement):
      kotlin:
        client: {package: example.orders.client}
        server: {package: example.orders.server}
    properties: {correlationId: string}
```

OpenAPI:

```yaml
openapi: 3.1.0
info: {title: Orders, version: '1'}
paths: {}
x-sunday-placement:
  kotlin:
    models: {package: example.orders.models}
components:
  schemas:
    Order:
      type: object
      properties: {id: {type: string}}
    Money:
      type: object
      x-sunday-placement:
        kotlin: {package: example.shared}
      properties: {amount: {type: number}}
    Context:
      type: object
      x-sunday-placement:
        kotlin:
          client: {package: example.orders.client}
          server: {package: example.orders.server}
      properties: {correlationId: {type: string}}
```

AsyncAPI uses the same `components.schemas` entries and placement extensions:

```yaml
asyncapi: 3.0.0
info: {title: Orders, version: '1'}
channels: {}
x-sunday-placement:
  kotlin:
    models: {package: example.orders.models}
components:
  schemas:
    Order:
      type: object
      properties: {id: {type: string}}
    Money:
      type: object
      x-sunday-placement:
        kotlin: {package: example.shared}
      properties: {amount: {type: number}}
    Context:
      type: object
      x-sunday-placement:
        kotlin:
          client: {package: example.orders.client}
          server: {package: example.orders.server}
      properties: {correlationId: {type: string}}
  messages:
    OrderCreated:
      payload: {$ref: '#/components/schemas/Order'}
```

## Invocation configuration and relocation

```sh
sunday kotlin/sunday --placement-config placement.yaml -out generated orders.yaml
```

```yaml
# placement.yaml
defaults:
  kotlin: {package: example.sdk}
relocations:
  - select: {target: './orders.yaml#'}
    placement:
      kotlin: {package: example.sdk}
  - select: {target: './orders.yaml#/components/schemas'}
    placement:
      kotlin: {package: example.sdk.models}
  - select: {target: './common.yaml#/components/schemas/Money'}
    placement:
      kotlin: {package: example.sdk.money}
  - select:
      target: './orders.yaml#/tags/0' # the tag definition named internal
      kind: service
      tags: [internal]
    placement:
      kotlin:
        package: example.internal.api
        models: {package: example.internal.api.models}
```

Gradle generations accept `placementConfig.set(file("placement.yaml"))`; it is a tracked task input. Library callers can pass `GeneratedPlacementConfiguration` through `GeneratedApiIrOptions.placement`. One invocation has one configuration. Defaults fill missing source fields. Relocations replace only the supplied fields and languages.

Relocation placement uses direct language fields and optional `client`/`server` variants. Category blocks belong to source defaults and invocation defaults. The one relocation exception is `models` on a service selection, which places that service's owned local declarations. A service selection uses `kind: service` or an exact service declaration target, such as an OpenAPI tag definition. A document/container selector without `kind` uses direct fields; use separate `kind: model` and `kind: service` rules for different placements. `services` is never valid inside relocation placement, and there is no `problems` block. Use `kind: problem` with direct fields to relocate problems separately from model defaults. Invalid blocks are rejected in every language and role, including unselected ones.

A tag-filtered document selector has document specificity. It cannot override a conflicting document relocation merely by adding `kind` or `tags`; select the tag declaration, as above, for an overlapping service override.

A target is a document reference plus an optional JSON Pointer, with standard URI encoding and pointer escaping (`~0` and `~1`). Relative references resolve against the configuration file. `#` refers to that configuration document, not the API. Selectors inspect the loaded source graph and never load additional inputs. JSONPath, wildcards, and fragment-query syntax are not supported.

Optional kinds are `model`, `service`, and `problem`. Tags require `kind: service`, match any listed tag exactly, and filter existing service groups. They never regroup operations. OpenAPI tag definitions use their actual array pointers, such as `#/tags/0`.

A document selector covers declarations owned by that document; a container selector covers its descendants. An exact declaration selector also covers owned anonymous declarations. A service container must cover every contributing operation to place the whole service. Selecting only part of a service may place local declarations but cannot move the service.

Relocation precedence is exact declaration, owning operation, explicit service, nearest container, then document. Filters do not increase specificity. Order is irrelevant; equal-specificity conflicts and selectors matching no applicable declaration are errors.

External type substitution remains separate. Use `x-sunday-typeScriptImport` (or RAML `(sunday.typeScriptImport)`) for an external TypeScript import, alongside `typeScriptType`. Relocation does not change that import identity.
