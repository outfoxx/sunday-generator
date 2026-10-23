# Output selection

Output selection controls which generated declarations are written. All source
inputs and references are parsed and resolved normally, even when their generated
definitions are excluded. An excluded dependency must be provided by the consuming
build; selection does not install dependencies or replace schema types.

Selection applies category first, then owning document URI. Categories are
`models`, `services`, and `problems`. An empty category set means all categories;
an empty owner set means all documents. Omitting both preserves self-contained
output. A composed service matches any contributing owner and retains all its
operations. Selecting an owner does not add it to source inputs or load it.

Top-level Kotlin and Swift model declarations belong to the `models` category.
This includes operation-local types placed outside their service's package or
module, synthetic response unions, and their companions. A model artifact contains the interfaces
implemented by its concrete models. Service-only generation references those model
declarations without emitting them again. Their owning documents remain those of
the service, including every contributor to a composed service. Models nested
inside a service remain service output and are excluded with their containing type.

## CLI

Repeat `-category models`, `-category services`, or `-category problems` to select
categories. Repeat `-owner common.yaml` or `-owner https://example.org/common.yaml`
to select owners. The two options are independent.

An owner identifier without a URI scheme denotes a local filename. Relative
configuration filenames resolve against the working directory. Local files,
including `file:` URIs, are canonicalized using `Path` before conversion to an
absolute URI; symbolic links and equivalent relative spellings identify one file.

## Gradle

```kotlin
outputCategories.set(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem))
outputOwners.set(setOf("common.yaml", "https://example.org/events.yaml"))
```

Relative configuration filenames resolve against the configured source base
(default: project directory). Canonical owner identities participate in the task
cache key. Referenced dependencies remain tracked build inputs without being
added to output selection.

The former `generateModel` and `generateService` booleans have been removed.
Replace model-only configuration with `outputCategories` containing `Model`, and
include `Problem` if the old combined behavior is wanted. For service-only output,
select `Service`. Empty means all, not none.

## Generator API

Output entry points accept `GeneratedOutputSelection(categories, ownerUris)`.
`ownerUris` is a set of absolute `URI` values. The API rejects relative URIs and
never resolves them against a process working directory. Configuration adapters
can use `GeneratedDocumentUri.configuration(identifier, baseDirectory)`.

Document fragments are not part of owner identity; declaration pointers remain
separate. Queries remain part of remote document identity. Source references
resolve against their referring document and applicable dialect base rules
(including OpenAPI schema `$id`), never the CLI/Gradle configuration base.
Remote cache filenames do not replace original document identities.

IR version 3 preserves ownership and placement metadata. Re-export older serialized IR.
Flattened inputs must retain original provenance or be replaced with the original
referenced documents; the generator cannot reconstruct erased ownership.

## Shared artifacts and naming

Generate a shared artifact using its document owner selection, then generate
consumers with their own owner selections. Compile consumers against the built
shared artifact, not copies of its generated sources. Keep package/module naming
consistent between invocations. Issue #293 separately supplies declaring-document
package/module defaults for the complete cross-language shared-artifact workflow;
output selection does not implement those naming rules.

Composition can deduplicate structurally identical models from different source
documents. Their candidate owners are preserved in IR. Selecting such a model by
owner reports an ambiguous-ownership diagnostic; reference one shared declaration
or disambiguate the declarations. Unfiltered generation retains its existing
structural deduplication behavior.
