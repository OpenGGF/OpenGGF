# Editor authoring parity implementation

Base: OpenGGF `8aa692b04`; desktop editor `dbf9761`.
Both local branches: `feature/ai-editor-authoring-parity`.
Integration/push belong to the lead. Stageplay namespaces/panels are excluded.

## Delivery slices

1. Lossless named subtype schemas via a non-API registry capability, shared desktop
   typed inspector and HUD property list; all unmodelled bytes retain raw editing.
   Registry names remain the object library's source. Schema coverage explicitly
   lists raw fallbacks rather than inferring field meaning from object names.
2. World-cell selection and clipboard command core, active-layer fill, ghost preview
   and canvas navigation. A clipboard is tied to its source library; native S1 ring
   groups are indivisible. Local compositions still derive definitions through the
   existing controller path.
3. Exact Genesis palette authoring through existing project/export fields. Other
   persistent settings are blocked pending approval for the pinned API contract.

## Decisions and rejected approaches

At base `8aa692b04`, `ObjectRegistry` is a pinned creator API. Rejected adding schema
members to it: that would need coordinated API policy/version/signature changes.
A separate non-API capability implemented by each game registry and forwarded by
`ModDecoratedObjectRegistry` keeps the creator contract unchanged. Shared schema
code does not select a game or zone.

Rejected guessing schemas from display names. IDs can mean different things across
S3KL/SKL; the initial S3K schemas only cover common monitor/spring IDs. Unknown
subtype values remain unchanged on inspection, and writing one field replaces
only its mask. Disassembly citations accompany every authored meaning.

At base `8aa692b04`, strict baked level formats v1/v2, `ModLevelDefinition`,
`ModZoneLevelData`, `ModZoneHostMetadata`, and `ModZoneRuntimeProfile` have no
persistent stock-scroll source, water-height or allowed-team contract. The latter
DTOs are pinned `@ModApi` types. Rejected an internal side channel or editor-only
settings that disappear on export: neither meets canonical round-trip semantics.
The follow-up needs explicit approval for coordinated descriptor, `ModApiVersion`
and normalized pin changes, plus strict project v1-to-v2 and baked-format migration.
Stock parallax additionally needs a verified provider compatibility catalogue;
some handlers depend on native events, water or dynamic art. Palette edits use
existing RGB storage and exact Genesis export conversion and need no new format.

Items 4–6 are deferred: semantic gizmos need typed persistent per-placement
properties beyond the existing subtype byte; campaign linking needs a persistent
multi-level contract; art import needs bounded asset installation/undo ownership.
Shipping those before the settings contract decision would leave inconsistent
round trips. No Stageplay-side hook is introduced by these slices.

## Verification

Focused command/schema/HUD tests are queued through `tools/testing/maven_queue.py`.
Combined validation uses `run_categories.py --base 8aa692b04 --run` once after all
source edits; inspect exact results/skips and acknowledge diagnostics. Engine/render
changes require separate structural guards. Desktop builds/tests use the task
engine JAR in its own private `.m2`; no main checkout is modified.

See the implementation handoff and external task checkpoint for tested SHAs,
completed commands and remaining coverage limits.
