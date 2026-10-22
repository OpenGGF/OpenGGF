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
3. Exact Genesis palette authoring and, following the user ruling below, provider-backed
   background/water/team settings in the unpublished project/export contracts.
4. Typed semantic gizmos, data-only ordered campaigns and bounded in-editor art import.
   Blocking Stageplay uses the public playback API; zoom-out and non-blocking playback
   remain explicitly deferred for the reasons below.

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

At property slice `abe958d76` (desktop `cb5f42f`), two new clipboard regressions
proved that copy could be changed by reusing an editor-owned block or a cut slot:
expected first chunk ID 1 became 2 or 3. Allocator pins preserve live clipboard
references. A copy followed by undo can still restore an old definition, so the
clipboard also captures its library composition and paste imports missing block/
chunk definitions into free slots atomically. Rejected index-only clipboard
snapshots: an immutable list of IDs is not immutable terrain content. Preview
uses captured composition with the level's current global palette.

At `08cf4c7d4`, the guard run rejected placing the shared property and palette
capabilities under `editor`: game registries, the mod decorator and coverage tool
created new `game -> editor`, `mods -> editor` and `tools -> editor` edges and
package cycles. Rejected widening the architectural ratchet. The capability
contracts and schema now live under `level.metadata`; editor implementations
remain under `editor`. This changes package ownership, not serialized data or
the pinned creator API.

## Verification

Focused command/schema/HUD tests are queued through `tools/testing/maven_queue.py`.
Combined validation uses `run_categories.py --base 8aa692b04 --run --max-minutes 150` once after all
source edits; inspect exact results/skips and acknowledge diagnostics. Engine/render
changes require separate structural guards. Desktop builds/tests use the task
engine JAR in its own private `.m2`; no main checkout is modified.

See the implementation handoff and external task checkpoint for tested SHAs,
completed commands and remaining coverage limits.


## Approved continuation, 2026-10-22

After round-one heads `9fb61c7ed` / desktop `71a5cfb`, the user clarified that the
API/editor formats are unpublished and approved changing them directly. The
previous migration/version decision gate is superseded: no new migrations or
version branches, no independent ModApiVersion bump. Regenerate the candidate
signature pin while retaining the engine-pegged release descriptor. The hook
explicitly rejects unnecessary descriptor changes accompanying ordinary candidate
surface growth; changing the descriptor for this feature was rejected on that
contract evidence.

Settings use safe per-game scroll catalogues and existing native palette providers.
Rejected listing every stock handler: many assume live native events, dynamic art
or water state. Required teams are launch context/policy, never user configuration.
Gizmos are eager rewind-owned auxiliary objects, so region origins outside native
streaming windows still work without altering ROM slot pressure. Typed JSON and
copy paths preserve the properties. Data-only campaigns reuse owner transactions
and multi-act runtime topology with an explicit terminal contribution.

Rejected treating a clipboard warp link as a permanent identity: copying a full
pair into the same level creates four endpoints. Paste remaps complete pairs to
fresh links. Rejected toggling raw respawn bits in shared gizmo creation: S1 uses a
different native encoding; the per-game placement codec owns those flags.

Zoom-out remains deferred after inspecting rendering at `9fb61c7ed`: widening the
visible world needs larger foreground/background/priority render targets and
correct culling/scroll composition. Tiling the existing camera view repeats native
background phase and causes seams; magnification remains 1×–8×. Non-blocking
Stageplay is also deferred: the existing public player requires exclusive control
and holds native simulation. Registration/multiplexing consumes the public API
from mods code; no Stageplay package or workspace changes are made.


The narrow architecture allowlists admit only the new art converter/publisher and
scene resource/composition owners, with negative assertions for unrelated callers.
The fixed 16-slice cluster and frozen violations remain unchanged. This is an
explicit ownership addition for the requested public API consumption, not a broad
Stageplay or tools exemption.


Expanded 52-ID schema coverage is committed at `a3228f79a` after 11 focused
schema tests and scoped strict citation checks. Review also rejected silently
substituting imported pixels when a clipboard outlives undo/reuse of its source
pattern slots: paste now rejects stale art before mutation, including redo.
Required teams are validated against host sidekick capability as well as keys;
Sonic 1 cannot silently drop a required sidekick. Cached authored scene service
controllers retire their players on close and recreate them through the already
prepared public registration factories on reuse; stable rewind adapters follow
the current generation.


Campaign review found stock signpost retirement beyond native act limits. The
reserved authored goal now selects native signpost/results behavior in S1/S2 as
well as S3K; only that named factory bypasses retirement. Rejected rewriting
numeric stock gating or remapping logical act identity to zero: both change ROM
behavior or break sequence progression. Native results artwork beyond acts 1–3
remains a presentation limitation; the renderer bounds-checks absent frames and
logical progression is preserved.


Before combined smoke, focused verification passed 72 affected persistence/clipboard/
gizmo cases and 34 final settings, campaign, validator and authored-goal cases,
all without skips. The structural/render selection ran 132 cases: 60 S3K regressions,
HUD captures, API pins and new owner/lifecycle checks passed. Its two unchanged
HubMusicController architecture failures match the earlier bounded baseline check;
a third failure was invalid test fixture art, corrected and passed in the final
focused run. The final smoke selection is 3,591 candidate classes plus guards,
with a 150-minute execution limit. This remains smoke coverage, not release-only
full-suite validation. Offscreen desktop build/captures follow with the task JAR.

## Complete smoke and recovery

At `4aff4b8fd`, run `20261022T173137Z-8c14482f` completed all 72 ordinary
batches and guards: 38,239 tests, 23 failures, 11 errors and 93 skips. All details
were inspected and the run acknowledged. Six cases match the earlier bounded
baseline comparisons: the HCZ GLFW pixel fixture, two HubMusic architecture
violations, two MenuAudioProbe singleton violations, and the native-image
AuthoredLevelDecoder service include. These unrelated failures remain inherited.

The other 28 failing/errored cases exposed editor compatibility and inventory
gaps. Rejected serializing empty settings into otherwise unchanged exports:
omitting defaults preserves the original golden bytes. Rejected consulting new
environment providers when no background/water is selected, and inserting new
menu actions before the existing Close action. Native art remains byte-exact;
new transparent import capacity is checked separately from missing native art.

The new gizmo is now an executed isolated rewind identity. Review also found
that generic spawn-based recreation first constructs a probe: an anonymous spawn
rejects typed properties before the real recreation hook runs. Captured placement
data must reach that probe. A first recovery attempt required every marker to
have a spawn; two focused failures identified S3kSignpostSparkleChild, whose
coordinate-only constructor deliberately stores none. Keep the neutral probe
fallback for absent placements. No game/class exception or guard allowance is
needed. Immutable sealed placement properties are explicitly classified by the
in-place restore audit.

The full guard run also rejected one raw destruction call and a new provisional
ModZoneContribution constructor overload. Use ObjectLifetimeOps for consuming
triggers and retain only the canonical contribution constructor, updating the
unpublished candidate signature pin without a separate API version change.

Recovery validation ran 1,648 cases with no skips: all original task failures
were repaired; only the two null-spawn probe cases failed. After correcting that
fallback, all 1,401 affected rewind/architecture cases passed with no skips.
The preceding selection also passed the settings/export/project/mod-zone cases,
lifecycle/no-shim/API guards and all 60 required S3K cases. No unchanged broad
smoke coverage was repeated after these bounded corrections.
