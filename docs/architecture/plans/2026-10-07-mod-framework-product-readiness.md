# Mod framework product readiness

The October 7 framework and example audit is authorized for implementation in
full. The goal is a flexible, well documented creator framework whose ordinary
paths compose safely and work from distributed artifacts. This is work on the
mutable, unpublished API candidate, not publication of a stable baseline.

Integration base: `945b74e999c3584ad45b92a711b7a6dace7dc294` (`develop`).
The coordinator owns integration, release prose, signature reconciliation,
combined verification and cleanup. Workers use isolated local branches. Preserve
the main checkout's dirty disassemblies, downloads and unrelated worktrees.

In recorded commands, `${OPENGGF_ROM_ROOT}` denotes the absolute original main-checkout
directory containing the three verified ROM files. Set it to your own checkout root;
this portable prefix preserves the measured property names, filenames and options
without committing a machine-local home path.

## Delivery inventory

Each row needs implemented behavior, an example or consumer proving usefulness,
and relevant verification. Status changes record evidence rather than intent.

| ID | Outcome | Initial owner / sequence | Status |
|---|---|---|---|
| 01 | Visible stock signpost reskin, complete frames, art-key diagnostics/catalogue | Creator | Integrated from 6691b0277; included in 33 focused passes; artifact proof pending |
| 02 | Every maintained starter supports repeated builds after edits | Creator | Integrated from 6691b0277; included in 33 focused passes; seven exported repeat-build proof pending |
| 03 | Java 21, matching candidate artifacts, pinned tooling, purpose-specific scaffolds | Creator | Integrated from 6691b0277; focused checks pass; distributed artifact proof pending |
| 04 | Reproducible creator download with SDK, API docs, launchers, starters and artifact-only CI proof | Creator | Integrated through a242f28b8; twelve Python checks pass; real packaging/ZIP/CI-equivalent acceptance pending |
| 05 | Recipient install/update/retrust/dependency/order/rollback/uninstall journey | Creator | Integrated from d4b7d1641; 31 focused tests pass, zero skips |
| 06 | Consistent beginner paths and delivered/limited/planned capability matrix | Creator, finalized after capabilities | Capability/navigation reconciliation composed; direct kit commands and explicit CI warning policy integrated from a242f28b8; handbook links/syntax pass |
| 07 | Packaging reports sorted warnings and machine-readable findings with explicit CI policy | Creator | Integrated from 6691b0277; included in 33 focused passes, zero skips |
| 08 | Portable exported examples and one shared build launcher with explicit ROM inputs | Creator, example migrations later | Launcher/export integrated through f47c577dd; eight portable projects including Tide Circuit; actual exported builds pending |
| 09 | Distributed test support uses production validation, loading, ownership and Jupiter lifecycle | Coordinator, after runtime fixes | Testkit 13, launcher two and packager two cases pass in 38496; nested Jupiter Slay 126 / Tower 17 pass; distributed artifact proof pending |
| 10 | Authored numeric zone/level metadata is owner-local; namespaced saves survive composition | Runtime safety | Owner-local metadata/remapping and tagged-save cases pass in 38496: zone loader 29, runtime save context 16 and existing save consumers |
| 11 | Owned rewind adapters cannot overwrite host or another owner | Runtime safety | Owner patch 21, registry 16 and native publication nine cases pass in 38496, including cold, host and delegated-root lifetimes |
| 12 | Returned functional callbacks retain owner fault attribution | Runtime safety | Returned factories/providers and required-dependent disable cases pass in 38496 across owner patch 21, standalone 22, context 19 and save context 16 |
| 13 | Registration forwards all GamePatch methods, guarded against future omissions | Runtime safety | Complete-method forwarding guard and metadata consumers pass in the 19 context/fault cases in 38496 |
| 14 | Selective graphics/save/query/reconstruction boundaries preserve broad expert capability | Coordinator, after helpers | Expert closure retained; nine API and 14 creator UI cases pass in 59678, including supplied projection and defining-loader checks |
| 15 | Multi-act hosted campaign and typed owner-scoped runtime contributions | Runtime safety, second wave | All 35 Tide Circuit campaign cases pass in 38496, including real load/handoff/save/rewind/replay and supported route breadth |
| 16 | Contributed zone events participate in reset, capture/restore and reconciliation | Runtime safety | Seven event-lifecycle and two S3K mod-zone lifecycle cases pass in 38496; campaign consumers also pass |
| 17 | Inspectable deterministic contribution arbitration and explicit conflicts/exclusive claims | Runtime safety, second wave | Effective catalog 21 and module resolution 21 cases pass in 38496, with ordered chains and conflict/exclusive transaction coverage |
| 18 | Bounded catalog/asset performance acceptance, with measured limits | Creator, after combined changes | Production shape probe passed; 1024 discovered/128 effective cap documented; combined-boundary repeat pending |
| 19 | Simulation-owned character landing hook and one authoritative character specification | Gameplay helpers | Initial 66 focused passes and integration consumers pass; fresh walkthrough found empty starter drawing, now corrected with an isolated red/green renderer probe; normal regression pending |
| 20 | Declarative standalone setup and safe level/registry delegation; migrate both fixtures and Survivors | Gameplay helpers | Standalone spec three, owner-aware module 22 and both phase-three fixture cases pass in 38496; exported starter proof pending |
| 21 | Typed immutable physics/profile edits with scoped transforms; migrate Infinite | Gameplay helpers | Updated Infinite fixture passes 206 of 238 cases in 38496, with 32 expected inapplicable-route skips; withMax preserves the other profile fields |
| 22 | Typed placement operations preserve identity, ownership and stock loading; migrate Golf | Gameplay helpers | Five level-patch cases pass in 38496; all 119 Golf cases pass in 59678 after test-local fault assertion/cleanup correction |
| 23 | Owner-scoped service/rewind bundle; migrate Golf and Survivors | Gameplay helpers, after safety | Context 19 and owner patch 21 cases pass in 38496; Golf 119 pass in 59678; Survivors service/state cases pass, with one separate font-pixel case under diagnosis |
| 24 | Shared compact font/atlas/UI and screen-space overlay canvas; migrate multiple examples | Presentation helpers, second wave | UI 14 and 25 selected graphics cases pass in 59678; Survivors title-gradient case passes; complete-font pixel check remains red at the viewport edge |
| 25 | ROM-qualified art recipes, palette assembly, lifetime cache, anchors and explicit animation policies | Presentation helpers, second wave | Six helper, one actual-ROM recipe and two S2 explicit-mapping cases pass in 38496; Tower's verified Flicky recipe adoption and pixel regression await normal focus |
| 26 | Timestamped action maps, capture/labels/settings and transition edge consumption; migrate Sitar | Presentation helpers, second wave | Ten input and 35 earlier API/model/chart/arcade follow-up cases pass; complete upstream Sitar union prepared at d5251269; postmerge verification pending |
| 27 | Owner storage across modules/scenes, versioned settings and compatible deterministic-state helpers | Gameplay helpers, second wave | Seven storage, 19 context and 13 testkit cases pass in 38496; Survivors owns its compatible profile/settings migration; Infinite retains its legacy leaderboard path |

## Boundaries and dependencies

Runtime safety owns `mods/code` registration and aggregation, `mods/runtime`
callback wrapping and rewind ownership, and zone contribution contracts. Creator
owns SDK tools/templates, release creator-kit packaging, recipient manager flow,
reskin fixtures and creator documentation. Gameplay owns character simulation
hooks, standalone specs, profile transforms, placement utilities and relevant
fixture/example adapters. Presentation owns scene helper packages and explicit
example migrations in a later wave. Shared files are assigned before editing;
the coordinator reconciles candidate signatures and centralized release prose.

Preserve programmable GamePatch/DelegatingGameModule/provider overrides. Avoid a
separate minimal API rewrite or a target type count. Internal classification must
come with coherent supported operations and retain the deliberately supported
trace diagnostic records. No runtime asset falls back to a disassembly. Keep
game-specific golf, rhythm, card, tower and survivor rules in their mods.

Existing placement/mutator work in unrelated worktrees may advance concurrently.
Inspect integrated changes before implementing or merging duplicate contracts;
never take ownership of or discard another task's unmerged work.

## Acceptance and verification

Baseline and combined delivery select the full ordinary suite plus structural
guards because runtime ownership, public API and build/distribution contracts
change. Use the queued category runner, actual Java 21/Lua 5.4/PowerShell
preflight and verified absolute ROM paths. Historical broad cost was about
24 minutes ordinary plus 10 minutes guards. The October 7 completed runs measured
about 72–77 minutes ordinary and 3.5 minutes guards; this task's completed
`4cfb7456` baseline measured 85.96 minutes ordinary and 3.69 minutes guards.
The pre-Sitar combined plan against `37a57ebd` selected all 3,035 ordinary source
classes plus separate fresh guards. The newer completed `be3c31418` base measured
108.72 minutes ordinary and 3.58 minutes guards. Refresh the final combined plan
after composing that upstream source. Broad delivery invocations therefore use
a 150-minute limit, excluding queue wait; the ten-minute no-output timeout
remains. Record exact failing identities and skips, compare matched baseline/current
failures, and consume diagnostics after inspection.

Workers run focused regressions while contracts are evolving. Final acceptance
includes two independent identical-local-ID mods, hostile rewind keys, returned
factory failures/dependent disable, event and service rewind round trips, repeated
starter builds, exported artifact-only projects with Jupiter tests, visible
reskin frames, recipient code-update trust, and a multi-act hosted campaign with
save/load, act handoff, rewind and missing-owner recovery. Presentation migrations
preserve metrics, inputs, animation frames and palettes. RNG/storage migrations
preserve file formats and existing sequences or explicitly version changes.

After combined verification, integrate into the main checkout's current branch
without switching it, verify integration against the recorded baseline, push
only that branch, and remove only fully accounted-for owned trees and branches.
Do not call this delivered while any required work or cleanup is unresolved.

## Evidence and decisions

The audit's focused creator/tooling check ran 37 tests without failures, errors or
skips on the integration base. Bounded probes confirmed dropped patch metadata,
the signpost key mismatch, no-clobber conversion on a second invocation, and
packaging warnings hidden for Infinite, Golf and Survivors. These establish the
initial defects; they are not baseline or delivery-suite evidence.

Rejected directions: freezing the current candidate, declaring all implementation
classes public to suppress warnings, relaxing allocation caps without measurement,
and absorbing example game rules into the framework.

## Work checkpoint: 2026-10-07

The updated integration base is `5bc5f4fa60728c6c074f9bbf8cf89d6dc6b05f6f`.
Concurrent Maven throughput/diagnostic changes fast-forwarded the initial
`945b74e999c3584ad45b92a711b7a6dace7dc294` base without mod-source conflicts.
All owned worker trees use the updated base. Main remains on `develop`; unrelated
submodule changes, archives, scratch prose and other tasks' branches are preserved.

Initial ownership/callback/zone-state, creator-tooling and gameplay-helper source
changes are present in their assigned trees. Root presentation work adds compact
and atlas fonts, layout/focus/overlay primitives, timestamped actions and
ROM-qualified art helpers with maintained-example adoptions. Root test support
uses actual production loading/registration/resolution rather than direct raw
entrypoint registration or patch application.

A small isolated Java 21 + Console 1.10.3 probe compiled only the new test launcher,
stager and their regression tests: four tests passed, zero skipped. This covers
Jupiter lifecycle/parameterized/dynamic/extension dispatch, failed/empty discovery,
artifact separation and safe staging. It is not an engine suite result.

Focused queued Maven requests had not begun at this checkpoint. The older full
baseline request was cancelled while still waiting (exit 130, no compile or
tests); the runner cleaned its temporary output. Submit broad baseline after the
first focused fixes so it cannot hold younger focused work behind its aged queue
reservation. All delivery/baseline/combined verification remains required.

Ordinary mutable candidate signature regeneration preserves the release-policy
descriptor's version/status/topology. The policy hook explicitly rejects a
descriptor edit accompanying ordinary pin-only regeneration. No published API
baseline or release is created by this work.

## Baseline reconciliation

Main advanced through documentation-only commits to
`5b3a63641033506fc0d89ad5188a0c97fae29089`. The complete changed-path list from
`5bc5f4fa` contains only release prose, audit/status records and measurement
hazards; production Java, tests, POM, hooks and test selection are identical.
The detached baseline was fast-forwarded to this commit and tool preflight
passed with Java 21, Lua 5.4 and PowerShell; no tests ran during preflight.

Reuse the already completed, source-equivalent engine baseline recorded in
[stock parity verification](../audits/2026-10-07-stock-parity-gap-verification.md#post-integration-ordinary-validation-and-concurrent-tooling):
ordinary run `20261007T130421Z-74206bd5`, 3,005 XML suites / 26,166 tests,
28 failures, zero errors and 61 skips; fresh separate guards on `9583f244`,
86 suites / 672 tests, zero failures, errors or skips. Its owners inspected
fresh reports, compared all failure identities/concrete assertions and all skip
identities/reasons, and cleaned their diagnostics. Those reports are no longer
available for a new raw-XML inspection; this task relies on their recorded
verification and the independently inspected source-equivalence diff. It does
not claim to have executed those profiles again or claim a green baseline.
The linked failure table pins the inherited red identities and first concrete
mismatches. A disputed changed failure requires a bounded matched base/current
check, including its full assertion and skips.

This avoids repeating unchanged baseline engine execution. Combined development
and post-integration validation remain required for this task's source changes.

Later, `develop` advanced to `4cfb745646d9439cdb9c07d53d0670dd0fb3fe58`,
integrating Sonic Survivors progression, balance and rendering changes. This
includes a new power-up music ownership rule and a playable-sprite consumer;
the earlier source-equivalence argument therefore no longer covers the current
destination. Preserve this newer example behavior while reconciling helper
migrations. Its recorded focused checks do not constitute a completed engine
baseline. An updated-base ordinary/guard run is required before comparison with
the combined development and post-integration runs.

## Integration review checkpoint

Input follow-up on `cce4013eb` completed with 35 tests, zero failures/errors/skips
(`TestModApiSignatureSurface`, `TestSitarHeroModel`, `TestSitarHeroCharts`,
`TestSitarHeroArcade`). The initial action/physical controls run contributed a
separate ten passing tests, zero skipped. Fresh XML identities and counts were
inspected; these are focused checks, not a full suite.

Creator tooling through `0dc4abb8c` is composed in the coordinator tree. The worker
recorded 33 passing focused tests, zero skipped, including the configured S2 ROM
integration, 31 recipient-manager tests, and nine Python tooling checks. The
follow-up artifact inventory, exported README and test-profile checks still need
execution against the final matching jars. Borrowed cross-worker sources and
mixed candidate pins are excluded from patch handoffs; the coordinator will
compile and pin the coherent combined source once.

The ID09 static review found disconnected configured input and a discarded save
warning sink. The testkit now consumes the resolver's supplied live configuration
and records production save/load warnings in the same finding store. Regressions
cover remapping and missing-owner recovery. An isolated Java 21/Console 1.10.3
check of the clock/physical-input/remapping helper ran three tests with zero
failures or skips; the complete production testkit check remains pending.
Silent scene tests and module-only snapshots explicitly retain audio/session,
GPU appearance and full gameplay coverage limits.

A primitive coordinate overflow regression failed against the earlier helper
and passed after frame/gradient offsets were validated before any drawing
(one isolated test, zero skipped). Queued Maven UI validation includes this
regression. ROM review retained full nonnegative S3K mapping-frame bytes:
`Animate_Sprite`/`loc_1AC1C` in the locked-on `sonic3k.asm` writes the full byte and
derives facing from status. Masking it according to a different game's animation
routine was rejected; this player documents its S3K ordinary-object policy.

The kit exporter now includes Tide Circuit from `f47c577dd`, preserving seven
starter kinds and extending the maintained gallery to nine examples/eight
portable exports. Eleven Python checks passed; the original campaign generator
reproduced all 22 binary assets byte for byte in a disposable external export.
Actual combined campaign, portable Maven and distributed artifact checks remain
pending. Probe storage isolation from `34040494d` awaits the runtime seam before
composition.

Origin-aware sprite placement now covers vertical as well as horizontal
mirroring. The isolated centre/feet regression failed against the old helper
(expected vertical centre anchor 96, actual 104), then passed after correction
(one test, zero skipped). Proper queued art validation includes that regression.

Upstream Survivors introduced cached disjoint menu-font rectangles, with a
regression requiring at least 30% fewer commands than row spans. Replacing it
with uncached row-span emission was rejected. `BitmapFont` now prepares bounded
linear-time geometry once, coalescing equal adjacent spans without overlap.
The new 64-row stem check failed against the old helper, then three isolated
Java 21/Console 1.10.3 font checks passed with zero skips. A read-only calculation
on the complete newer Survivors face gives 466 row spans versus 276 coalesced
rectangles; this is a geometry count, not a frame-rate measurement or the
upstream greedy algorithm's exact count. The actual example performance/pixel
regression remains pending in the gameplay adoption check. Proper queued UI/API
verification on the reconciled coordinator tree completed with 20 tests, zero
failures, errors or skips (11 `TestCreatorUi`, nine `TestModApiSignatureSurface`);
fresh XML identities, counts and timestamps were inspected.

The first proper art check completed 152 tests: 151 passed and the real-ROM
recipe check errored while rasterizing `Map_Animals1`. The disassembly declares
three pointers and stores frame 2 before frame 0; inferring table length from
the first pointer incorrectly read seven frames. Removing frames from the test
or guessing a different mapping format was rejected. `RomSpriteRequest` now
retains its original constructor and supports a bounded explicit mapping count;
the named Flicky recipe specifies the three verified pointers. A rerun covering
request compatibility, all recipes and both affected example suites is pending.

The coherent 50-selector run `44690` compiled production and tests, then ran
562 tests with 23 failures, 144 errors and zero skips. These are actual failures,
not a passing adoption claim. Diagnosis groups the 107 Golf load errors, the
35 campaign errors, external example-build failures and boundary regressions
before deciding production versus fixture changes. The campaign's four authored
objects omitted the respawn bit in `rawYWord`; the production validator correctly
rejected them. The decoded-geometry fixture also matched byte arguments to int
methods; correcting it preserves the exact-once and 512-pixel expectations.
The testkit audio fixture used a namespaced value where the manifest requires a
local clip name. The combined retry will retain these stricter production paths.

Fresh compiled closure review found 71 curated roots, 874 reachable types and
874 annotated types: zero missing/orphan annotations, class-loading failures or
external signature leaks. Engine/GameLoop/GameplayModeContext are unreachable;
CNZ slot-machine and S2 Tornado diagnostic records remain explicit roots, along
with every checked expert/helper capability. These are structural checks, not
runtime verification. Candidate pin regeneration follows the final compiled
union; no published baseline or release-policy descriptor is changed.

Updated develop `2fc65c8479570f16ebd9830115485ee369c2b1e6` was reconciled
without replacing any owned migration. An independent changed-path check found
no engine Java, POM, hooks or category-selection/queue changes from `4cfb74564`;
the sole Java test difference is Infinite Sonic's bounded terrain-observation
cache, terrain-only music suppression and strengthened hazard witnesses. The
ongoing baseline remains measured at `4cfb74564`; qualify unchanged evidence and
exercise the changed fixture separately on the current tree. No second duplicate
full baseline is implied by the new measurement tooling or prose.

Creator commit `f73f9aa81` fixes local artifact dependency activation beside OS
native and explicit test profiles. Twelve Python checks and 19 actual Maven
profile-selector checks passed without launching a Maven build lifecycle. The
real external repeat-build proof remains pending. Compiled API normalization
currently records 17,214 sorted LF lines from the 874-type coherent union;
public API signature tests and matching artifact inventory still need execution.

Further direct campaign preflight caught a missing reserved GPAL header word.
The original generator and both palettes now use the specified 12-byte header;
production parsing and host-data conversion accept both complete acts. All 22
original asset payloads reproduce exactly. This proves authored-format intake,
not physical traversal, checkpoint, finish or donor-route behavior.

The migrated Slay/Tower engine harnesses each report two outer cases because
real Jupiter discovery now launches the example suite as one dynamic case.
Inspect the nested Console summaries as well as Surefire totals; do not compare
outer counts to the earlier reflection-expanded 127/18 counts. The two protocols
count different test identities. The production testkit storage-isolation and
headless repeated-close regressions passed in `44690`; its remaining two fixture
failures await the corrected campaign export and local audio-name retry.

## Composed source follow-up

Creator onboarding/CI policy from `a242f28b8` is merged through `acf49a3038`;
the coordinator retained its capability table and navigation changes. Twelve
Python checks, shell/YAML syntax and changed-page links passed in that worker.
The candidate CI explicitly permits and reports expert internal-reference
warnings; strict rejection remains a selectable packaging policy.

The native lifecycle fix preserves the raw PLC service instance within each
load and admits only the actual same-root current publication after `createGame`
recreates it. Generic key-only refresh and retaining/resetting an old PLC service
were rejected because they discard authority or alter native queue lifetimes.
Cold native identities are reserved before and after owned patch/bundle callbacks,
without creating absent services or jobs; capture/retag attempts fault the actual
creator and dependents. The campaign fixture now keeps the exact root used by
resolution, the world session and pinned data. Nine native regression cases and
the complete campaign route matrix await execution.

Example review caught a cached-font gradient regression in Survivors: filtering
rectangles by starting row painted merged stems with their first row's colour.
Gradient text now intersects each cached rectangle with the authored row band;
uniform labels still emit the complete cached geometry. The bounded model found
40 incorrectly coloured old wordmark pixels and 96 row spans versus 46 cached
uniform rectangles. These are diagnostic geometry observations, not executed
render/JUnit evidence. The new regression checks every wordmark row, fade alpha,
overlap and uniform-label command reduction through the actual presentation port.

The queued composed retry selects 53 focused selectors and all three verified
original ROM paths. It includes the updated `2fc65c8` Infinite fixture, API pin
checks, actual S2/S3 native lifecycle cases and both maintained campaign acts.
The runner plan on the composed tree selects all ordinary categories plus fresh
guards. Focused fixes precede broader candidate and post-integration validation.

## Completed baseline ordinary evidence: d9b56478

Measured commit: `4cfb745646d9439cdb9c07d53d0670dd0fb3fe58`; detached task baseline.
Run: `20261007T180233Z-d9b56478`. Plan: 3,007/3,007 source classes, all ten
ordinary categories, one worker, separate fresh guards. Working-tree fingerprint:
`55b44b901fac66a69e61722fbf4ba41793572ea16504f749f0e7b3ed8326a67b`.

```sh
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py \
  --base 4cfb745646d9439cdb9c07d53d0670dd0fb3fe58 \
  --category all --max-minutes 120 --run
```

The terminal ordinary lane reported 3,005 XML suites / 26,222 tests, 28 failures,
zero errors, 62 skips and exit 1 in 5,157.73 seconds (85.96 minutes).
The actual Maven command supplied all three original verified absolute ROM paths.
All failure/skip entries are present (zero omitted). The full assertion first
lines below were checked against retained XML; the 2,048-character JSON message
for the SSZ Tails replay is truncated, but its complete 2,952-character detail
first line is recorded. Skip reasons retain the complete first causal line,
omitting only stack frames. Fresh guards completed in a separate JVM: 86 XML
suites / 672 tests, zero failures, errors or skips, exit 0, 221.57 seconds
(3.69 minutes). Both lanes are terminal; overall runner exit 1 reflects the
28 ordinary failures. The coordinator inspected both lane summaries and every
recorded failure/skip before normal diagnostic acknowledgment.

### All ordinary failure identities and assertions

| Test identity | Complete assertion |
|---|---|
| `com.openggf.tests.TestFbzSandopolisTimelineHeadless#productionExitResetsTimelineAndFreshDestinationRestoresAndReplaysTwice` | `org.opentest4j.AssertionFailedError: SOZ restore cycle 0 sprites: [sprites.sprites[0].state.playerExtra.instaShieldRegistered: A=false B=true] ==> expected: <true> but was: <false>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]` | `org.opentest4j.AssertionFailedError: late pulley owns Tails ==> expected: <true> but was: <false>` |
| `com.openggf.tests.TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[3]` | `org.opentest4j.AssertionFailedError: late pulley owns Sonic ==> expected: <true> but was: <false>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloTailsClearsAllFinalPhasesAndLoadsEnding` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[1]` | `org.opentest4j.AssertionFailedError: death at 26706 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#incomingFinalFightRestoresAndReplaysEveryPhase(int)[2]` | `org.opentest4j.AssertionFailedError: death at 26750 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldWideOrdinarySoloSonicClearsAllFinalPhasesAndLoadsEnding` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldEmeraldTeamClearsBothActsFinalFightAndDoomsday` | `org.opentest4j.AssertionFailedError: death at 53897 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestDezIncomingFinalRouteCapture#coldOrdinaryTeamClearsHandsCoreAndEscapeShipAndLoadsEnding` | `org.opentest4j.AssertionFailedError: expected: <96> but was: <0>` |
| `com.openggf.tools.TestLrzActTwoColdRouteCapture#coldTeamCompletesActTwoAndReachesBossActWithRepeatableWorldState` | `org.opentest4j.AssertionFailedError: death at input 36526 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzBossColdRouteCapture#coldTeamCompletesBossActWithEarnedShieldAndRepeatableWorldState` | `org.opentest4j.AssertionFailedError: death at input 36526 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzKnucklesColdRouteCapture#coldKnucklesCompletesActTwoAndReachesPlayableHiddenPalace` | `org.opentest4j.AssertionFailedError: expected: <1069> but was: <899>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsClearsActOneAndRestoresTraversalFightAndHandoff` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsRestoresActTwoTraversalToTheMiddleCorridor` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesBossActAndReachesPlayableHiddenPalace` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzTailsColdRouteCapture#coldTailsCompletesActTwoAndRestoresTheBoulderHandoff` | `org.opentest4j.AssertionFailedError: death at input 19460 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestLrzWideBossColdRouteCapture#coldWideTeamClearsBossAndReleasesHiddenPalaceWithRepeatableWorld` | `org.opentest4j.AssertionFailedError: expected: <2796> but was: <524>` |
| `com.openggf.tools.TestMhzPairColdRouteCapture#pairedColdCompletionIsolatesTheLiveTimelineAtTheActualFbzLoad` | `org.opentest4j.AssertionFailedError: the route must observe the actual history-reset boundary ==> expected: <true> but was: <false>` |
| `com.openggf.tools.TestMhzWideColdRouteCapture#wideSonicCompletesBothActsThroughProductionLoopWithWholeWorldReplay` | `org.opentest4j.AssertionFailedError: [wide-route-19500] restore 0 zone-runtime: [zone-runtime.stateBytes[2]: A=46 B=26, zone-runtime.stateBytes[3]: A=-104 B=64, zone-runtime.stateBytes[6]: A=38 B=21, zone-runtime.stateBytes[7]: A=-44 B=-32, zone-runtime.stateBytes[10]: A=31 B=17, zone-runtime.stateBytes[11]: A=16 B=-128] ==> expected: <true> but was: <false>` |
| `com.openggf.tools.TestSszColdRouteCapture#coldCompleteRouteDefeatsMechaAndLoadsDeathEggWithRewindAtLateEvents` | `org.opentest4j.AssertionFailedError: death at input 7311 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszColdRouteCapture#coldRouteDefeatsBothReplicasAndReplaysTraversalAndTransport` | `org.opentest4j.AssertionFailedError: death at input 7311 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsBothReplicasAndReplaysTheirApproaches` | `org.opentest4j.AssertionFailedError: death at 7671 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszSoloColdRouteCapture#coldSoloSonicDefeatsMechaAndLoadsDezWithIsolatedHistory` | `org.opentest4j.AssertionFailedError: death at 7671 ==> expected: <false> but was: <true>` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[1]` | `org.opentest4j.AssertionFailedError: expected: <48> but was: <0>` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsMechaAndLoadsDezWithIsolatedHistory(int)[2]` | `org.opentest4j.AssertionFailedError: expected: <48> but was: <0>` |
| `com.openggf.tools.TestSszTailsColdRouteCapture#coldSoloTailsDefeatsBothReplicasAndRidesTheirTeleporters(int)[2]` | `org.opentest4j.AssertionFailedError: replay at 4018 object-manager: [object-manager.usedSlotsBits differs, object-manager.usedSlotsBits.onlyA: 24, 29, object-manager.dynamic[6][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC]] missing in B (A=DynamicObjectEntry[className=com.openggf.game.sonic3k.objects.badniks.EggRoboJetFlameChildInstance, spawn=ObjectSpawn[x=1291, y=2332, objectId=0, subtype=0, renderFlags=1, respawnTracked=false, rawYWord=0, layoutIndex=-1, ownerModId=null, objectKey=null], slotIndex=6, state=PerObjectRewindSnapshot[destroyed=false, destroyedRespawnable=false, hasDynamicSpawn=true, dynamicSpawnX=1291, dynamicSpawnY=2332, preUpdateX=1291, preUpdateY=2332, preUpdateValid=true, preUpdateCollisionFlags=-1, skipTouchThisFrame=false, solidContactFirstFrame=false, slotIndex=6, respawnStateIndex=-1, badnikExtra=null, badnikSubclassExtra=null, objectSubclassExtra=RewindExtra[parentId=null, x=1291, y=2332, mappingFrame=5, hFlip=true], playerExtra=null, genericState=null, compactGenericState=com.openggf.game.rewind.schema.RewindObjectStateBlob@86abcabb], playerOwner=null, objectId=ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=125, kind=DYNAMIC], ownerModId=null, rewindableAuxiliary=false]), object-manager.dynamic[28][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=126, kind=DYNAMIC]] missing in B (A=DynamicObjectEntry[className=com.openggf.game.sonic3k.objects.badniks.EggRoboGunArmChildInstance, spawn=ObjectSpawn[x=1307, y=2300, objectId=0, subtype=0, renderFlags=1, respawnTracked=false, rawYWord=0, layoutIndex=-1, ownerModId=null, objectKey=null], slotIndex=28, state=PerObjectRewindSnapshot[destroyed=false, destroyedRespawnable=false, hasDynamicSpawn=true, dynamicSpawnX=1307, dynamicSpawnY=2300, preUpdateX=1307, preUpdateY=2300, preUpdateValid=true, preUpdateCollisionFlags=-1, skipTouchThisFrame=false, solidContactFirstFrame=false, slotIndex=28, respawnStateIndex=-1, badnikExtra=null, badnikSubclassExtra=null, objectSubclassExtra=RewindExtra[parentId=null, x=1307, y=2300, cooldown=-1, hFlip=true], playerExtra=null, genericState=null, compactGenericState=com.openggf.game.rewind.schema.RewindObjectStateBlob@203aa981], playerOwner=null, objectId=ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=126, kind=DYNAMIC], ownerModId=null, rewindableAuxiliary=false]), object-manager.dynamic[27][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=128, kind=DYNAMIC]].slotIndex: A=27 B=6, object-manager.dynamic[27][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=128, kind=DYNAMIC]].state.slotIndex: A=27 B=6, object-manager.dynamic[33][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=129, kind=DYNAMIC]].slotIndex: A=33 B=27, object-manager.dynamic[33][ObjectRefId[slotIndex=-1, generation=0, spawnId=-1, dynamicId=129, kind=DYNAMIC]].state.slotIndex: A=33 B=27] ==> expected: <true> but was: <false>` |
| `com.openggf.tools.audio.timeline.TestS1GameplayAudioTimelineCli#shellUsesAbsoluteBootstrapToolsAndRejectsInjectedEnvironmentBeforePathLookup` | `org.opentest4j.AssertionFailedError: expected: <0> but was: <4>` |

### All ordinary skip identities and reasons

| Test identity | Recorded reason |
|---|---|
| `com.openggf.audio.TestSmpsRepeatedPlaybackBenchmark#repeatedPublicMusicAndSfxPlaybackEmitsStableRawSamples` | `System property [openggf.audio.repeatedPlaybackBenchmark] does not exist` |
| `com.openggf.game.rewind.TestLiveRewindCheckpointCost#compareCheckpointCadencesOnTheSameRecordedRoute` | `System property [openggf.checkpoint.measure] does not exist` |
| `com.openggf.game.rewind.TestRewindTorture#tortureProgressiveLongRewinds` | `org.opentest4j.TestAbortedException: Assumption failed: Long-running soak profile; excluded from normal runs — run manually with -Drewind.soak=true` |
| `com.openggf.game.rewind.TestS3kRewindAllocationProbe#measure` | `System property [openggf.rewind.alloc.measure] does not exist` |
| `com.openggf.game.sonic3k.objects.TestS3kAiz1CompatibilityRoutes#axisRouteCompletes(int, String)` | `System property [openggf.aiz1.routes] does not exist` |
| `com.openggf.game.sonic3k.objects.TestS3kAiz1EntryMatrix#introReleasesInputAndEntryReplaysTwice(int, String)` | `System property [openggf.aiz1.entry] does not exist` |
| `com.openggf.game.sonic3k.objects.TestS3kAiz1SpringRecovery#liveSpringJumpCrossesAndReplaysWhileWalkingIsRejected(int, String)` | `System property [openggf.aiz1.recovery] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozAct1VictoryCapture#captureVictoryAndHandoff` | `System property [soz.act1.victory.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozColdAct1Capture#fixedControllerRouteReachesVisiblePlayableAct2FromColdAct1` | `System property [soz.cold.act1.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozColdAct2Capture#fixedControllerRouteReachesVisibleLavaReefFromColdAct2` | `System property [soz.cold.act2.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozEndBossVictoryCapture#captureBattleAndLrz` | `System property [soz.endboss.victory.capture] does not exist` |
| `com.openggf.game.sonic3k.objects.TestSozMinibossCapture#positionedAwakening` | `System property [soz.miniboss.capture] does not exist` |
| `com.openggf.graphics.TestArenaMaskRenderer#nativeWidthCentrePixelsFrameCadenceAndGlStateSurviveCaptureFbo` | `org.opentest4j.TestAbortedException: Assumption failed: assumption is not true` |
| `com.openggf.graphics.TestBackgroundScrollWrapPixels#integerScrollNeverSamplesOutsideTheRenderedPeriod` | `org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable` |
| `com.openggf.graphics.TestForegroundWindowRendering#windowReplacesScrolledForegroundAndItsPriorityMask` | `org.opentest4j.TestAbortedException: Assumption failed: Surfaceless EGL unavailable (try EGL_PLATFORM=surfaceless)` |
| `com.openggf.graphics.TestScrollBufferUploadNative#arraysAndViewsUploadExactValuesAcrossResourceAndContextRecreation` | `System property [openggf.scrollNative] does not exist` |
| `com.openggf.graphics.TestShaderPixelCentreSampling#pixelCentresSurviveNativeIntegerAndFractionalScalingWithViewportOffsets` | `org.opentest4j.TestAbortedException: Assumption failed: OpenGL 4.1 unavailable` |
| `com.openggf.graphics.TestSlotWindowGpuPassNative#pixelsAndDrawStateSurviveResizeCleanupAndContextRecreation(boolean)` | `System property [openggf.slotNative] does not exist` |
| `com.openggf.graphics.shaderlib.TestDisplayShaderPackDiagnostics#writeCompatibilityReportForLocalShaderPack` | `org.opentest4j.TestAbortedException: Assumption failed: Set -Dshaderlib.diagnostic.enabled=true to scan a local shader pack` |
| `com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#captureLiveBackgroundSamplingScenes` | `org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.capture.backgroundSampling=true` |
| `com.openggf.level.TestLevelRendererBackgroundSamplingPerformance#postWarmupRenderSamplingAllocationProbe` | `org.opentest4j.TestAbortedException: Assumption failed: enable with -Dopenggf.measure.backgroundSampling=true` |
| `com.openggf.level.objects.TestObjectRewindTypeSafetyDispatchPerformance#measureMixedRouteDispatchAllocationAndTime` | `System property [openggf.performance.rewindDispatch.measure] does not exist` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[11]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[12]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[19]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[20]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[21]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[22]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[23]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[24]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[25]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[26]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[27]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[28]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[29]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[30]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[31]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[32]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[33]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[34]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[35]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesABounceStretchOffAHoveringFlyer(int, int, WidescreenAspect)[36]` | `org.opentest4j.TestAbortedException: Assumption failed: act has no platform stretches or no flyer to bounce off` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[11]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[12]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[19]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[20]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[21]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[22]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[23]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[24]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[33]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[34]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[35]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestInfiniteSonic#sonicCrossesAPlatformStretchOnSpawnedStockPlatforms(int, int, WidescreenAspect)[36]` | `org.opentest4j.TestAbortedException: Assumption failed: act places no platforms` |
| `com.openggf.mods.code.TestSonicSurvivors#balanceProbe` | `org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic` |
| `com.openggf.tests.TestCPZObjectBugs#testSpinTubeForcesRolling` | `org.opentest4j.TestAbortedException: Assumption failed: Spin tube at (1920,896) did not capture/release Sonic` |
| `com.openggf.tests.TestSozAct1ArenaCapture#naturalArenaAdmission` | `System property [soz.act1.capture] does not exist` |
| `com.openggf.tests.TestSozConnectedMechanismCapture#capture(Scene)` | `System property [soz.connected.capture] does not exist` |
| `com.openggf.tests.TestSozPostBossRedrawCapture#capture(int)` | `System property [soz.postboss.redraw.capture] does not exist` |
| `com.openggf.tools.TestS3kSlotsGlassNative#glassOccludesPlayerAfterRealBonusFrame(String, int)` | `System property [openggf.test.gl.native] does not exist` |
| `com.openggf.tools.audio.parity.TestS1OpenGgfAudioCapture#capturesTheCompleteReferenceControlledInterval` | `org.opentest4j.TestAbortedException: Assumption failed: local deterministic BizHawk reference required` |
| `com.openggf.tools.audio.timeline.TestS1Ghz1OpenGgfAudioTimelineCapture#captureRequestedOutput` | `org.opentest4j.TestAbortedException: Assumption failed: no local OpenGGF timeline capture was requested` |

Main later advanced to `37a57ebdbe62864737f39e7b14c72932fbaf3d74`. Independent
diffs confirm no engine Java, POM, hooks or category-selection changes from
`4cfb74564`; the sole Java test change remains `TestInfiniteSonic` from
`2fc65c8`. The cleanup-locking change affects runner diagnostics, not engine
behavior. Qualify this measured baseline accordingly and exercise the changed
Infinite fixture separately; do not present the baseline as green.


The actual command arrays for ordinary and guards were inspected. Both supplied:

```text
-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen
-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen
-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen
```

The ordinary command was `mvn -Dmse=off test -B` with this run's exclusive
report/tmp directories; the fresh guard command additionally used `-Pguards`.
No concurrent process or queue limit was changed for this run.

## Coherent focused run: 2026-10-07 21:32:19 UTC

Queued Maven session `38496` ran on `acf49a3038f17cff7681f8bb8a5dad6d391da151`
plus the composed working tree. Its measured source fingerprint was
`9dfd209c755bfc4375a80269b3816b49dfde2537621ceb66e479364caf79ca52`; the same fingerprint and all 22 separately
hashed authored campaign binary assets were verified after execution. The queue
admitted this request normally after 8,488 seconds. Maven completed in 4:22 with
exit 1: **53 fresh XML suites / 956 tests / 2 failures / 8 errors / 33 skips**.
This is focused validation, not a full-suite result. Compilation and all nine
candidate signature checks passed. No baseline, queue limit or other run was changed.

The submitted command, with its original ROM directory represented portably, was:

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -B \
  "-Dtest=TestRenderProjection,HeadlessGameBootTest,Sonic1SpecialStageBackgroundCleanupRetryTest,Sonic2SpecialStageBackgroundCleanupRetryTest,TestRuntimeSaveContext,TestSaveManager,TestDonatedSaveSession,TestS3kSaveSnapshotProvider,TestEngineDataSelectPatchResolution,TestS3kModZoneLifecycle,TestStandaloneGameSpec,TestObjectQuery,TestTwoPhaseRestoreOrdering,TestObjectManagerDynamicChainRewindRestore,TestShieldRewindRestore,TestSampleFlappyIntegration,TestLevelPatch,TestModStorage,TestInfiniteSonic,TestPuttPuttParadise,TestSonicSurvivors,TestSamplePlatformerIntegration,TestPhase3SampleCharacterIntegration,TestPhase3StandaloneSampleIntegration,TestOwnerAwareStandaloneModule,TestOwnedCharacterRegistry,TestModCharacterConstructionIdentity,TestGameLoop#testDoEnterEndingDoesNotWriteSaveForActiveSlot,TestModContextAndFaultBoundary,TestModRegistrationRuntime,TestModZoneLoader,TestOwnerBoundGamePatch,TestModZoneEventLifecycle,TestGameplayModeContextRewindRegistry,TestRewindRegistry,TestTwoActModCampaign,TestEffectiveCatalogBuilder,TestModManifestParser,TestModuleResolutionService,TestCreatorTestLauncher,TestDeterministicInput,TestModTestKit,TestModTestKitPackager,TestSceneArtHelpers,TestStockSceneArtRom,TestSceneApiValues,TestSlayTheRobotnikExample,TestRobotnikTowerDefenseExample,TestCreatorUi,TestS2ExplicitMappingFrames,TestModValidator,TestModApiSignatureSurface,TestNativeRewindAdapterPublication" \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

`--lean` supplies one reusable Surefire fork and a 1 GiB Java heap; its queue
reservation is 4 GiB / 4 cores. Source stayed frozen during this invocation.

| Fresh suite | Tests / failures / errors / skips |
|---|---|
| `com.openggf.TestEngineDataSelectPatchResolution` | 7 / 0 / 0 / 0 |
| `com.openggf.TestGameLoop` | 1 / 0 / 0 / 0 |
| `com.openggf.game.TestStandaloneGameSpec` | 3 / 0 / 0 / 0 |
| `com.openggf.game.patch.TestModuleResolutionService` | 21 / 0 / 0 / 0 |
| `com.openggf.game.rewind.TestRewindRegistry` | 16 / 0 / 0 / 0 |
| `com.openggf.game.save.TestDonatedSaveSession` | 10 / 0 / 0 / 0 |
| `com.openggf.game.save.TestSaveManager` | 20 / 0 / 0 / 0 |
| `com.openggf.game.session.TestGameplayModeContextRewindRegistry` | 28 / 0 / 0 / 0 |
| `com.openggf.game.sonic1.specialstage.Sonic1SpecialStageBackgroundCleanupRetryTest` | 3 / 0 / 0 / 0 |
| `com.openggf.game.sonic2.TestS2ExplicitMappingFrames` | 2 / 0 / 0 / 0 |
| `com.openggf.game.sonic2.specialstage.Sonic2SpecialStageBackgroundCleanupRetryTest` | 2 / 0 / 0 / 0 |
| `com.openggf.game.sonic3k.dataselect.TestS3kSaveSnapshotProvider` | 10 / 0 / 0 / 0 |
| `com.openggf.graphics.TestRenderProjection` | 4 / 0 / 0 / 0 |
| `com.openggf.level.TestLevelPatch` | 5 / 0 / 0 / 0 |
| `com.openggf.level.objects.TestObjectManagerDynamicChainRewindRestore` | 1 / 0 / 0 / 0 |
| `com.openggf.level.objects.TestObjectQuery` | 2 / 0 / 0 / 0 |
| `com.openggf.level.objects.TestTwoPhaseRestoreOrdering` | 1 / 0 / 0 / 0 |
| `com.openggf.mods.TestEffectiveCatalogBuilder` | 21 / 0 / 0 / 0 |
| `com.openggf.mods.TestModApiSignatureSurface` | 9 / 0 / 0 / 0 |
| `com.openggf.mods.TestModManifestParser` | 12 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestInfiniteSonic` | 238 / 0 / 0 / 32 |
| `com.openggf.mods.code.TestModCharacterConstructionIdentity` | 11 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestModContextAndFaultBoundary` | 19 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestModRegistrationRuntime` | 11 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestModZoneEventLifecycle` | 7 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestModZoneLoader` | 29 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestNativeRewindAdapterPublication` | 9 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestOwnedCharacterRegistry` | 22 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestOwnerAwareStandaloneModule` | 22 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestOwnerBoundGamePatch` | 21 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestPuttPuttParadise` | 119 / 1 / 8 / 0 |
| `com.openggf.mods.code.TestRobotnikTowerDefenseExample` | 2 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestRuntimeSaveContext` | 16 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestS3kModZoneLifecycle` | 2 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSampleFlappyIntegration` | 11 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSlayTheRobotnikExample` | 2 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSonicSurvivors` | 114 / 1 / 0 / 1 |
| `com.openggf.mods.code.TestTwoActModCampaign` | 35 / 0 / 0 / 0 |
| `com.openggf.mods.integration.TestPhase3SampleCharacterIntegration` | 1 / 0 / 0 / 0 |
| `com.openggf.mods.integration.TestPhase3StandaloneSampleIntegration` | 1 / 0 / 0 / 0 |
| `com.openggf.mods.integration.TestSamplePlatformerIntegration` | 2 / 0 / 0 / 0 |
| `com.openggf.mods.scene.TestSceneApiValues` | 3 / 0 / 0 / 0 |
| `com.openggf.mods.scene.art.TestSceneArtHelpers` | 6 / 0 / 0 / 0 |
| `com.openggf.mods.scene.art.TestStockSceneArtRom` | 1 / 0 / 0 / 0 |
| `com.openggf.mods.scene.host.TestModStorage` | 7 / 0 / 0 / 0 |
| `com.openggf.mods.testing.TestCreatorTestLauncher` | 2 / 0 / 0 / 0 |
| `com.openggf.mods.testing.TestDeterministicInput` | 3 / 0 / 0 / 0 |
| `com.openggf.mods.testing.TestModTestKit` | 13 / 0 / 0 / 0 |
| `com.openggf.mods.ui.TestCreatorUi` | 11 / 0 / 0 / 0 |
| `com.openggf.mods.validation.TestModValidator` | 25 / 0 / 0 / 0 |
| `com.openggf.sprites.playable.TestShieldRewindRestore` | 7 / 0 / 0 / 0 |
| `com.openggf.tools.HeadlessGameBootTest` | 4 / 0 / 0 / 0 |
| `com.openggf.tools.modtestkit.TestModTestKitPackager` | 2 / 0 / 0 / 0 |

The two example Jupiter launches additionally reported Slay **126 successful /
0 failed / 0 skipped** and Tower **17 successful / 0 failed / 0 skipped**.
These nested counts are separate from the two outer Surefire tests per example.

All 35 Tide Circuit campaign cases and all nine native publication cases passed,
including real S2/S3 ROM-backed publication, cold reservation and host/delegated
root lifetimes. The updated Infinite fixture from `2fc65c8` ran 238 cases:
206 passed and 32 skipped for inapplicable platform/flyer routes. The only other
skip was the opt-in Survivors balance diagnostic. All 33 skip identities and
reasons match those already listed in the baseline table above.

All ten remaining failure identities are listed below. The Golf failures share
a test-local injected audio sink cause; fixing the exception assertion and
ensuring cleanup is under investigation. Survivors exposes a production helper
contract violation during CPU sprite preparation. Neither result warrants an
unrelated engine audio, recording, fade or parity change.

| Failing identity | First assertion / deepest cause |
|---|---|
| `com.openggf.mods.code.TestPuttPuttParadise#closingOnlineModeReleasesRoomEvenWhenAudioSinkFlushFails` | `org.opentest4j.AssertionFailedError: Unexpected exception type thrown, expected: <java.lang.IllegalStateException> but was: <com.openggf.mods.code.ModFaultBoundary.CallbackAborted>; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#zoneMusicStartsAtCourseEntryAndNeverRestartsDuringShotsOrRestores` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#shotRewindRestoresEveryCourseOwnerAndRefundsStrokeOnce(String, int)[1]` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#shotRewindRestoresEveryCourseOwnerAndRefundsStrokeOnce(String, int)[2]` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#shotRewindRestoresEveryCourseOwnerAndRefundsStrokeOnce(String, int)[3]` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#shotRewindRestoresEveryCourseOwnerAndRefundsStrokeOnce(String, int)[4]` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#pauseMenuCanRewindAnInFlightShotWithGenesisInputs` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#concedingDuringReverseClosesSoundAndViewBeforeResults` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestPuttPuttParadise#rewindRetryKeepsTheConfirmedTurnWithoutAnotherHandoff` | `com.openggf.mods.code.ModFaultBoundary$CallbackAborted: Mod callback aborted gameplay for putt-putt-paradise; Caused by: java.lang.IllegalStateException: failed replay sink flush` |
| `com.openggf.mods.code.TestSonicSurvivors#menuFontPreservesEveryPixelWithFewerPresentationAllocations` | `java.lang.AssertionError: java.lang.reflect.InvocationTargetException; Caused by: java.lang.IllegalStateException: GPU command submitted during CPU sprite preparation: com.openggf.mods.ui.LevelOverlayCanvas$ScreenRect` |

Artifact-only acceptance and both broad delivery runs remain pending.

## Rendering retry and creator walkthrough: 2026-10-08

Queued focused session `59678` ran on `a6b5b48cb3afbfdb0574bceb3d8ba6be57cc315b`
plus the frozen working tree, fingerprint
`14c423db8d8c3aac1e61798209b93f777f4b4e26735ea33ca1dee7fcaa31ee28`.
The identical fingerprint and all 22 authored binary assets were verified after
execution. Normal admission took 9,458 seconds; Maven ran for 2:27 and exited 1.
The last fresh XML report was written at `2026-10-08T00:48:31.375307Z`.

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -B \
  "-Dtest=TestCreatorUi,TestPuttPuttParadise,TestSonicSurvivors,TestSpritePresentation,TestLevelSpritePresentation,TestLevelSpritePresentationLifecycle,TestGLCommandGroup,TestModApiSignatureSurface" \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

Eight fresh XML suites reported **281 tests / one failure / zero errors / one
skip**. This is focused validation, not a full-suite pass.

| Fresh suite | Tests / failures / errors / skips |
|---|---|
| `com.openggf.graphics.TestGLCommandGroup` | 1 / 0 / 0 / 0 |
| `com.openggf.graphics.TestSpritePresentation` | 13 / 0 / 0 / 0 |
| `com.openggf.level.TestLevelSpritePresentation` | 5 / 0 / 0 / 0 |
| `com.openggf.level.TestLevelSpritePresentationLifecycle` | 6 / 0 / 0 / 0 |
| `com.openggf.mods.TestModApiSignatureSurface` | 9 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestPuttPuttParadise` | 119 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSonicSurvivors` | 114 / 1 / 0 / 1 |
| `com.openggf.mods.ui.TestCreatorUi` | 14 / 0 / 0 / 0 |

The sole failure is
`com.openggf.mods.code.TestSonicSurvivors#menuFontPreservesEveryPixelWithFewerPresentationAllocations`:
`org.opentest4j.AssertionFailedError: pixel at 312,2 ==> expected: <true> but was: <false>`.
The sole skip is `com.openggf.mods.code.TestSonicSurvivors#balanceProbe`:
`org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic`.
The test's persisted width request resolved to the native 320-pixel aspect overlay,
so local pixel 312 at origin nine lay at absolute X321 and was correctly clipped.
A bounded Java 21 probe reproduced that precise failure, then preserved all pixels,
colors and alpha at scales one through three by changing only the configuration's
session width override. The camera became 974 pixels while the graphics viewport
remained 320; geometry stayed at 276 primitives per scale. The integrated test now
uses `setSessionOverride` and asserts its resolved camera width. Its pixel,
translucency, scale and performance assertions and production clipping are unchanged;
normal Maven verification of this test correction is pending.

The Golf test-local audio fault assertion and cleanup now pass all 119 cases.
Typed screen geometry, current supplied-manager replay, defining-loader rejection
and all existing selected world-geometry/lifecycle cases pass. The complete
compiled candidate signature checks pass with the 17,215-line pin. No audio,
recording, fade or stock parity behavior was changed for this retry.

During the wait, three independent read-only reviews followed the creator journey,
checked public snippets and mapped helper adoption across seven example games and
all nine maintained gallery samples. After inspecting the terminal retry and
verifying its source, the coordinator applied three separately hashed patches:

- Eleven documentation/template files: use normal repeated Maven builds, retain
  generated starter projects, supply exact artifact/cwd paths, explain adopting
  the exported Jupiter profile, handle checked I/O, describe transaction freeze
  after registration returns, and accurately name Infinite's actual physics and
  legacy leaderboard path.
- Five Tower/Golf/Survivors/Infinite files: use the verified three-pointer Flicky
  recipe with an actual loaded-scene pixel regression; explain the real helper
  lifetimes; correct required-ROM appendix flags to S1 or S2 as appropriate.
- Two character fixture/test files: draw the installed renderer. `init --kind
  character` copies this maintained fixture, so its former empty draw made the
  generated character invisible. An isolated Java 21/Console 1.10.3 probe failed
  with zero tiles before correction and passed one case after correction, including
  actual packaged loading, owned construction, baked pixels and hidden/null-renderer
  behavior. Normal focused verification of the exact integrated test is pending.

Each patch's before/after file hashes and whitespace checks passed. Changed-only
documentation checks covered fences, syntax, new paths and actual CLI/POM contracts;
the earlier unchanged complete link/Python checks were not repeated. No API or core
engine change was needed for these walkthrough corrections. Preserve the original
full Sitar career, P2, network, performer and music behavior during reconciliation.

The helper map also identified inherited failed-save handling in Hello, Tower and
Slay. Those call sites already ignored `false` at pre-task `945b74e`; they are not
storage-migration regressions. Tower retry work must separate one-time win
aggregation from dirty persistence to avoid duplicate wins. Optional existing-helper
adoption includes Infinite's manual provider/registry forwarding and Hello's feet
arithmetic. Preserve authored markup, multipart art, gradient rounding and Slay's
SplitMix64 sequence; the xorshift helper is not a compatible replacement for it.

### Updated integration baseline

Actual integrated `develop` at `be3c3141808c98d4c56a3dfb0fee19e241720154` completed
run `20261007T213945Z-2b9d2b4b` with
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 37a57ebdbe62864737f39e7b14c72932fbaf3d74 --max-minutes 150 --run`.
Its full plan selected 3,020 ordinary classes: 3,018 XML suites / 26,373 tests /
28 failures / zero errors / 62 skips; separate fresh guards reported 86 suites /
672 tests / zero failures, errors or skips. The upstream record states that all
28 complete assertions and all 62 skip identities/reasons match this task's
inspected `4cfb7456` baseline, with only exception-prefix and JVM object-hash
normalization for the full SSZ line. See
[integrated Sitar verification](../designs/2026-10-07-sitar-hero-full-version.md#integrated-develop-verification).

Main then advanced to `02796b4c497c3aa887f77a8d8e9697b7009d2b4c` through verification
prose and a root launcher only; its runtime/test/build source is unchanged from
the measured `be3c31418`. Reuse this qualified updated-base evidence rather than
launching another duplicate full baseline. It does not validate the framework
candidate: its combined development and post-integration runs remain required.
