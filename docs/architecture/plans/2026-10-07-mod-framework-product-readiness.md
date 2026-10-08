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
| 01 | Visible stock signpost reskin, complete frames, art-key diagnostics/catalogue | Creator | Focused reskin cases and both exported packaging paths pass; zero validation findings |
| 02 | Every maintained starter supports repeated builds after edits | Creator | All seven exported starters packaged twice without cleaning after edits, with changed outputs and zero findings |
| 03 | Java 21, matching candidate artifacts, pinned tooling, purpose-specific scaffolds | Creator | Clean matched artifacts and all seven purpose starters verified externally at 7318; final source refresh is recorded separately |
| 04 | Reproducible creator download with SDK, API docs, launchers, starters and artifact-only CI proof | Creator | Twelve Python checks and ten external acceptance stages pass; duplicate ZIPs match at 7318 and d49 |
| 05 | Recipient install/update/retrust/dependency/order/rollback/uninstall journey | Creator | Integrated from d4b7d1641; 31 focused tests pass, zero skips |
| 06 | Consistent beginner paths and delivered/limited/planned capability matrix | Creator, finalized after capabilities | Capability/navigation reconciliation composed; direct kit commands and explicit CI warning policy integrated from a242f28b8; handbook links/syntax pass |
| 07 | Packaging reports sorted warnings and machine-readable findings with explicit CI policy | Creator | Integrated from 6691b0277; included in 33 focused passes, zero skips |
| 08 | Portable exported examples and one shared build launcher with explicit ROM inputs | Creator, example migrations later | All eight exports pass portable and normal Maven packaging at 7318; Sitar's final changed export passes both again at d49 |
| 09 | Distributed test support uses production validation, loading, ownership and Jupiter lifecycle | Coordinator, after runtime fixes | Testkit 13, launcher two and packager two cases pass in 38496; nested Jupiter Slay 126 / Tower 17 pass; external Hello Jupiter and ordinary-package isolation pass |
| 10 | Authored numeric zone/level metadata is owner-local; namespaced saves survive composition | Runtime safety | Owner-local metadata/remapping and tagged-save cases pass in 38496: zone loader 29, runtime save context 16 and existing save consumers |
| 11 | Owned rewind adapters cannot overwrite host or another owner | Runtime safety | Owner patch 21, registry 16 and native publication nine cases pass in 38496, including cold, host and delegated-root lifetimes |
| 12 | Returned functional callbacks retain owner fault attribution | Runtime safety | Returned factories/providers and required-dependent disable cases pass in 38496 across owner patch 21, standalone 22, context 19 and save context 16 |
| 13 | Registration forwards all GamePatch methods, guarded against future omissions | Runtime safety | Complete-method forwarding guard and metadata consumers pass in the 19 context/fault cases in 38496 |
| 14 | Selective graphics/save/query/reconstruction boundaries preserve broad expert capability | Coordinator, after helpers | Expert closure retained; nine API and 14 creator UI cases pass in 59678, including supplied projection and defining-loader checks |
| 15 | Multi-act hosted campaign and typed owner-scoped runtime contributions | Runtime safety, second wave | All 35 Tide Circuit campaign cases pass in 38496, including real load/handoff/save/rewind/replay and supported route breadth |
| 16 | Contributed zone events participate in reset, capture/restore and reconciliation | Runtime safety | Seven event-lifecycle and two S3K mod-zone lifecycle cases pass in 38496; campaign consumers also pass |
| 17 | Inspectable deterministic contribution arbitration and explicit conflicts/exclusive claims | Runtime safety, second wave | Effective catalog 21 and module resolution 21 cases pass in 38496, with ordered chains and conflict/exclusive transaction coverage |
| 18 | Bounded catalog/asset performance acceptance, with measured limits | Creator, after combined changes | Three fresh 512 MiB JVMs pass production shape/rejection/repeat-order checks at 7318; measured limits and coverage qualifications are recorded below |
| 19 | Simulation-owned character landing hook and one authoritative character specification | Gameplay helpers | Initial 66 focused passes and integration consumers pass; both actual packaged character integration cases pass in 49042, including baked pixels and hidden/null-renderer behavior |
| 20 | Declarative standalone setup and safe level/registry delegation; migrate both fixtures and Survivors | Gameplay helpers | Standalone spec three, owner-aware module 22 and both phase-three fixture cases pass in 38496; corrected standalone starter and maintained Platformer repeat builds pass externally |
| 21 | Typed immutable physics/profile edits with scoped transforms; migrate Infinite | Gameplay helpers | Updated Infinite fixture passes 206 of 238 cases in 38496, with 32 expected inapplicable-route skips; withMax preserves the other profile fields |
| 22 | Typed placement operations preserve identity, ownership and stock loading; migrate Golf | Gameplay helpers | Five level-patch cases pass in 38496; all 119 Golf cases pass in 59678 after test-local fault assertion/cleanup correction |
| 23 | Owner-scoped service/rewind bundle; migrate Golf and Survivors | Gameplay helpers, after safety | Context 19 and owner patch 21 cases pass in 38496; Golf 119 pass in 59678; all 113 enabled Survivors cases pass in 49042, with only the expected opt-in diagnostic skipped |
| 24 | Shared compact font/atlas/UI and screen-space overlay canvas; migrate multiple examples | Presentation helpers, second wave | UI 14 and 25 selected graphics cases pass in 59678; UI 14 and all 113 enabled Survivors cases pass in 49042 after correcting the test viewport override, including complete font pixels and title-gradient behavior |
| 25 | ROM-qualified art recipes, palette assembly, lifetime cache, anchors and explicit animation policies | Presentation helpers, second wave | Six helper, one actual-ROM recipe and two S2 explicit-mapping cases pass in 38496; all six Tower scene cases pass in 49042, including the verified Flicky recipe and loaded-scene pixel comparison |
| 26 | Timestamped action maps, capture/labels/settings and transition edge consumption; migrate Sitar | Presentation helpers, second wave | Ten input and 35 earlier follow-up cases pass; complete upstream Sitar union is integrated at 886ce707d; all twelve Sitar classes and 131 cases pass in 49042 with no skips |
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
  behavior. Both packaged character cases now pass in the normal coherent focused run 49042.

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


### Coherent post-Sitar focused verification: 2026-10-08

The clean candidate `4f3d2c27dac0f46449a19ec420806afa7ea905f9` completed direct
queued Maven session `49042` with exit zero. Maven reported completion at
`2026-10-08T02:43:47Z`, after 5,067 seconds waiting and 10 minutes 44 seconds
execution. This direct invocation has no category-run ID. HEAD and clean tracked
source were independently checked after completion, and every selected report
was fresh relative to the pre-launch source-state cutoff; no unselected or stale
XML was included.

```bash
LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py --lean -Dmse=off -B \
  "-Dtest=TestModApiSignatureSurface,TestModApiSdkPackager,TestModApiJavadocTool,TestModSceneHost,TestSceneNetworkLifetime,TestManagedSceneNetwork,TestSceneMusicRom,TestModStorage,TestModTestKit,TestModTestKitPackager,TestSampleModsPackage,TestSitarHeroArcade,TestSitarHeroControls,TestSitarHeroCareer,TestSitarHeroWorldTour,TestSitarHeroOnlineMatch,TestSitarHeroStory,TestSitarHeroPerformers,TestSitarHeroModel,TestSitarHeroCharts,TestSitarHeroS1SongCatalogue,TestSitarHeroS2SongCatalogue,TestSitarHeroS3kSongCatalogue,TestActionReducer,TestCreatorUi,TestPhase3SampleCharacterIntegration,TestPurposeStarters,TestRobotnikTowerDefenseScene,TestSonicSurvivors" \
  "-Dsonic1.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=${OPENGGF_ROM_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=${OPENGGF_ROM_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

Result: **29 fresh XML suites / 369 tests / zero failures / zero errors / one
skip**. All 368 enabled cases passed. The sole skip remains
`com.openggf.mods.code.TestSonicSurvivors#balanceProbe`, with full causal first
line `org.opentest4j.TestAbortedException: Assumption failed: opt-in diagnostic`.
There were no missing-ROM skips. The nine actual compiled signature tests passed
against the final 880-type / 17,272-line normalized candidate. Sitar retained
all 131 cases across twelve classes, including actual charts for all 79 songs,
career, local/P2/network behavior, story, performers and all three ROM catalogues.
The character rendering, Tower Flicky pixels, complete Survivors font pixels and
UI projection/trust regressions passed in the integrated normal test harness.
These are focused results; full candidate and post-integration validation remain
required.

| Class | Tests / failures / errors / skips |
|---|---|
| `com.openggf.control.TestActionReducer` | 5 / 0 / 0 / 0 |
| `com.openggf.mods.TestModApiSignatureSurface` | 9 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestRobotnikTowerDefenseScene` | 6 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroArcade` | 29 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroCareer` | 9 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroCharts` | 13 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroControls` | 5 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroModel` | 10 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroOnlineMatch` | 20 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroPerformers` | 1 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroS1SongCatalogue` | 18 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroS2SongCatalogue` | 5 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroS3kSongCatalogue` | 6 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroStory` | 6 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSitarHeroWorldTour` | 9 / 0 / 0 / 0 |
| `com.openggf.mods.code.TestSonicSurvivors` | 114 / 0 / 0 / 1 |
| `com.openggf.mods.integration.TestPhase3SampleCharacterIntegration` | 2 / 0 / 0 / 0 |
| `com.openggf.mods.scene.host.TestModSceneHost` | 16 / 0 / 0 / 0 |
| `com.openggf.mods.scene.host.TestModStorage` | 7 / 0 / 0 / 0 |
| `com.openggf.mods.scene.host.TestSceneNetworkLifetime` | 3 / 0 / 0 / 0 |
| `com.openggf.mods.scene.host.music.TestSceneMusicRom` | 10 / 0 / 0 / 0 |
| `com.openggf.mods.scene.host.network.TestManagedSceneNetwork` | 16 / 0 / 0 / 0 |
| `com.openggf.mods.testing.TestModTestKit` | 13 / 0 / 0 / 0 |
| `com.openggf.mods.ui.TestCreatorUi` | 14 / 0 / 0 / 0 |
| `com.openggf.tools.modsdk.TestModApiJavadocTool` | 7 / 0 / 0 / 0 |
| `com.openggf.tools.modsdk.TestModApiSdkPackager` | 10 / 0 / 0 / 0 |
| `com.openggf.tools.modsdk.TestPurposeStarters` | 2 / 0 / 0 / 0 |
| `com.openggf.tools.modsdk.TestSampleModsPackage` | 2 / 0 / 0 / 0 |
| `com.openggf.tools.modtestkit.TestModTestKitPackager` | 2 / 0 / 0 / 0 |

### Reviewed upstream capture and fixture integration

Main advanced to `33d3976c53304dbbea1c695914ecdd7bfc64cf9d`. Its 15-path change
was independently reviewed from API/baseline and example/runtime angles, and
merged without conflict into the framework candidate as
`722559f92a1d7c1d2bbaf31ca92b31072b83cfed`. The only production change factors
`GameplayCaptureSession` drawing into a shared body and adds `renderFrame()`
without framebuffer readback. Existing `render()` retains drawing, `glFinish()`
and readback; boot, session, reload, rewind ownership and creator contracts are
unchanged. None of the eight existing Java paths overlapped this task's edits.

The measured upstream `d5f9767a609223a7a4a284026ad324cc7785a2c5` integrated
control passed 364 cases in 19 fresh reports, with zero failures, errors or skips,
and compiled all 3,700 production and 3,587 test sources under the existing lean
lane. It includes native pixel/state comparisons at 320/800 widths, active title
and special-stage results, readback omission, ownership cleanup and all nine SOZ
routes with their registry/replay checks. See the exact command and coverage limits
in [integrated natural-collection control](../research/2026-10-07-ordinary-suite-memory-cause.md#integrated-natural-collection-control).

The later `33d3976c5` changes evidence prose only. Independent blob comparisons
confirmed all fourteen failing-test classes and all thirty skipped-test classes
are identical to measured `be3c31418`; all eleven failing capture classes retain
the unchanged `render()` path. POM, hooks, selection policy and queue implementation
are unchanged. This qualifies reuse of the **be3 full baseline through d5f focused
controls**; it is not a measured full 33d baseline or a full-suite pass. Compare
delivery results with all 28 complete assertions and all 62 skip identities/reasons
recorded above. Any changed result requires bounded matched attribution. The first
external creator attempt used clean `4f3d2c27`; corrected acceptance and the later
Sitar refresh are recorded below.

### Compact-font count-in integration correction: 2026-10-08

The upstream Sitar presentation merge in `d85d2b63f55660283690e4e533d318fa842a8034`
retained the shared `CompactFont` adoption and added an authored count-in digit at
scale 5. `SitarScreens.countIn()` calls `SitarUi.big()`, whose shared metrics and
drawing rejected scales above 4. An isolated Java 21 probe against the unchanged
helper reproduced `IllegalArgumentException: Font scale must be 1..4` for both
scales 5 and 8. After admitting scales 1–8, the same probe returned widths 25 and
40 for digit `3`. No glyph, stage coordinate, audible clock, public declaration,
candidate version or signature pin changed.

The five-file correction was handed off locally as
`d49a741a249bf63508115930a2c5f306c878577d` while its original normal-lane request
39735 was still waiting. SHA-256 checks confirmed identical Java and documentation
bytes before and after that commit, at admission, and after completion. The
focused run admitted after 5,122 seconds, compiled 3,760 production and 3,617 test
sources with Java 21.0.12.1, and completed successfully at
`2026-10-08T05:18:15Z` after 1 minute 12 seconds of Maven execution. The original
absolute ROM arguments are shown through their main-workspace root below:

```bash
ROM_ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
JAVA_HOME=/usr/lib/jvm/java-21-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py -Dmse=off \
  "-Dtest=TestCreatorUi,TestSitarHeroArcade,TestSitarHeroPerformers" \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
  test -B
```

All three fresh XML reports identify those absolute ROM properties and contain
54 tests, zero failures, errors or skips: `TestCreatorUi` 16,
`TestSitarHeroArcade` 37 and `TestSitarHeroPerformers` 1. The new helper regressions
check every authored digit pixel exactly once at scales 5 and 8, preserve alpha,
match width and fitting metrics, and reject scales 0 and 9 before emitting any
geometry. The new arcade regression draws the actual child-loaded Sitar scene
with a controlled audible position of minus three seconds, checks every scale-5
digit pixel in its authored location, and proves drawing does not change that
clock. Its music player and ROM boundary are controlled fixture doubles; passing
the three ROM properties does not turn it into a live venue or speaker capture.
This is focused verification. Combined development and postintegration checks
remain the coordinator's delivery obligations.

### Corrected external creator acceptance: 2026-10-08

The first `4f3d2c27` attempt exposed two distinct problems. Its temporary reactor
driver wrote absolute module paths that Maven prefixed with the reactor directory;
all seven modules were missing before any child compilation. Changing the driver
to relative module paths resolved that harness error. Six starters then packaged
with zero findings, but the standalone starter reported two `AUDIO_ASSET_INVALID`
findings and one `AUDIO_ASSET_MISSING`: `audio/sample-tone.wav` was absent.

The maintained standalone fixture stores encoded audio under `src/main/mod` and
its shell build relocates the decoded file to `src/main/resources/audio`. SDK
initialization had omitted that relocation. Source correction
`64da7de5452c6d59f7a42137af3609d1fa9c440f`, merged as
`7318d6db4d1f3865f5cfbaa8ee84ea012c45b3e7`, makes the same narrow relocation
when copying the trusted maintained fixture. A classifier-only loader regression
checks exact audio bytes and strict packaged validation. Its JavaCompiler uses
the full session engine classpath; SDK-only compilation is established by the
external generated-project builds, not that regression alone.

The normal focused command
`python3 tools/testing/maven_queue.py --lean -Dmse=off -B -q -Dtest=TestPurposeStarters,TestModApiSdkPackager test`
completed with 13 tests, zero failures, errors or skips (3 purpose-starter cases
and 10 SDK-packager cases). No API declaration, candidate version or pin changed.

All ten external acceptance stages then passed on clean `7318d6db4d1f3865f5cfbaa8ee84ea012c45b3e7`.
The temporary external driver received the source tree, extracted kit, work
directory and expected SHA explicitly, and ran these stages in order:
`artifacts scaffold first edit second examples jupiter ordinary platformer catalog`.
The artifact stage used normal queued Maven, without `--lean`:

```bash
python3 tools/testing/maven_queue.py -Dmse=off -B -q -DskipTests -Puniversal-jar verify
python3 tools/modding/build_creator_kit.py --out "$CREATOR_ACCEPTANCE_ROOT/kit-first.zip"
python3 tools/modding/build_creator_kit.py --out "$CREATOR_ACCEPTANCE_ROOT/kit-repeat.zip"
```

Both ZIPs had SHA-256
`688be20f443b2d1c23c814d3d7822e96fdee8ed089aaaa98f90b4cbd83a1f93d`.
All four artifacts reported candidate API `0.7.0`, engine `0.7.prerelease`, the
same expected source commit and a clean build. The complete hash inventory and
exported launcher were checked.

| External acceptance | Observed result |
|---|---|
| Seven generated purpose starters | Normal Maven package twice without cleaning after manifest, Java, art and audio edits; changed outputs reached every jar; zero validation findings |
| Eight exported examples | Both portable launcher and normal Maven package passed; Infinite 26, Golf 10 and Survivors 28 documented internal-API warnings accepted under explicit `allow`; the other five had zero warnings; no errors |
| Tide authored outputs | All 22 regenerated binaries matched byte-for-byte |
| Exported Hello Jupiter profile | One fresh `hello.HelloSceneIntegrationTest`, zero failures/errors/skips |
| Hello ordinary package after profile | Existing test-report timestamps unchanged; production jar excluded tests and testkit classes |
| Maintained Platformer | Two normal Maven packages after source/art edits, no clean or forced local profile; all five converters reran, edited outputs verified, zero findings/warnings; waits 5,190 and 565 seconds |
| Catalog | Three fresh Java 21.0.12.1 JVMs with 512 MiB heaps; repeat ordering/diagnostics and both oversized-asset and 1,025-jar rejection passed |

DPLC bank-cost notices from conversion were separate from validation findings.
The catalog used the production scan, validate, load/register and close pipeline:

| Shape | Discovery / effective / registration | Total pipeline time |
|---|---|---|
| 1 / 32 / 128 owners × 1 MiB | All admitted and registered | 130 / 121 / 323 ms |
| 1 / 8 owners × 32 MiB | All admitted and registered | 182 / 353 ms |
| 1,024 owners × 64 KiB | 1,024 / 128 / 128; 896 `PATTERN_WINDOW_BUDGET_EXCEEDED` | 647 ms |

Oversized assets produced `MOD_JAR_INVALID`; 1,025 jars produced
`REPOSITORY_JAR_LIMIT_EXCEEDED` before activation. Process peak RSS was
545,910,784 / 648,396,800 / 582,569,984 bytes. Summed historical heap-pool peaks
were 360,232,176 / 501,870,696 / 329,315,512 bytes: these are neither simultaneous
heap usage nor allocation measurements, and timings are not latency guarantees.
Probe directories were removed. The acceptance driver created no ROM copies or
links. These are creator-build/catalog results, not ROM gameplay certification.

### Final merged Sitar and artifact refresh

Main's Sitar presentation polish at `09cfcc0f882305d6a0c6a4fc05b600d3ca27a888`
was reconciled as `d85d2b63f55660283690e4e533d318fa842a8034`. Java merged cleanly;
two documentation conflicts retained the new venues/audio/reading order, the
nine-sample catalogue and portable artifact instructions. All 66 local link
targets and seven shell fences in the resolved documents passed. Independent
review confirmed shared control/font/layout adoption survived; it also identified
the count-in incompatibility corrected and verified above.

At the Sitar-polish checkpoint, the full baseline was **be3**, qualified through the d5f capture/fixture
controls and matched ae2 Sitar verification. The integrated polish record reports
141 passing baseline cases at `33d3976c5`, then all 141 plus seven new passing cases
at `ae2dfd3dd`: 14 fresh suites / 148 tests / zero failures/errors/skips. Separate
fresh guards passed 86 suites / 672 tests with zero failures/errors/skips.
Independent blob comparison found all fourteen failing-test classes and thirty
skipped-test classes unchanged from be3. No engine Java, POM, hook, selection
policy, API descriptor or pin changed in that upstream polish. See the exact
commands and coverage limits in the
[integrated polish record](../designs/2026-10-08-sitar-hero-presentation-polish.md).
This qualifies the existing full baseline; it is not a full ae2/09cf pass.

Normal artifact refresh on clean `d49a741a249bf63508115930a2c5f306c878577d`
completed direct session 37357 after 1,150 seconds waiting. Two more ZIPs were
byte-identical, with SHA-256
`912a2f0d5b7434c6c732737c3ed0cc6da93e8bea29de38513d618014d8d15848`.
All four clean-source metadata records, complete inventory and launcher matched.

| Final artifact | SHA-256 |
|---|---|
| `engine.jar` | `c29c0aed46017a327debe21c158afec757945c57dac45d44c92e1587073f3bd2` |
| `sdk.jar` | `6fa030644fbe980e648923fe530962c76f34bf78628c785bcdf99b3f3540024b` |
| `mod-testkit.jar` | `31aa828d5d481b058b05aa9ac3fb0f2cc7748404d9b2c1dfb8b5fddbaf6b4115` |
| `api-docs.jar` | `3497d0907a7d57a557b529c78d6facb0ec04d2ef8d26fa277bda88b84efe14d3` |

Independent readback found 13,368 engine classes with no SDK tools or testkit
classes, 61 SDK tool classes, three testkit support classes, and the API-docs
`index.html`. Only Sitar's export changed after the ten-stage acceptance; the seven
other exports and starter inputs are unchanged. CompactFont's compatible scale
extension changes no signatures and preserves scales 1–4.

Sitar alone was rebuilt with the final matched artifacts, through its portable
launcher and normal queued Maven package (session 22908, 140 seconds waiting,
no `--lean`). Both strict `validate --warnings error` runs returned zero findings
and warnings. Both production jars have 78 entries / 77 classes, including 15
stage classes and all eight required new scene/audio/stage top-level classes;
tests, testkit and JUnit classes are absent. The export has 39 production Java
files. Portable jar SHA-256 is
`e77b8fe513b267584a845bc8b1686a532f447bbe2b5f1a4623b1789ca413a916`;
normal Maven jar SHA-256 is
`8ff3e99fa90ff61f6a3c1e96ee571a419feaa48553b75f55fe0e755d27107777`.
Jar byte equality is not required across those two packaging tools. Runtime
verification is the separate 54-case focused run above. Full development and
post-integration validation, push and owned cleanup remain required.

With `KIT` set to that extracted matching bundle, the targeted export commands
were:

```bash
python3 "$KIT/tools/build_project.py" "$KIT/examples/sitar-hero"
python3 tools/testing/maven_queue.py -Dmse=off -B -q \
  -f "$KIT/examples/sitar-hero/pom.xml" package \
  "-Dopenggf.engine.jar=$KIT/engine.jar" \
  "-Dopenggf.sdk.jar=$KIT/sdk.jar"
java -cp "$KIT/engine.jar:$KIT/sdk.jar" \
  com.openggf.tools.modsdk.GgfModCli validate \
  "$KIT/examples/sitar-hero/target/sitar-hero-mod.jar" \
  --format json --warnings error
```

The strict validation command ran after each packaging path.

### Parity destination reconciliation and merged runtime verification

Main advanced to `5d1ff9b8206594ee1c979ae5174328dd9134ffd4` with the stock parity
swarm. The framework tree merged it as
`e600e7674e6167f6730a74f93424a3fc29fc3300` without conflicts. Independent source
reviews and the coordinator's resolved diff confirm both sides survive in
`GameLoop`, `GameplayModeContext`, `LevelManager` and `AbstractPlayableSprite`:
live movie-input publication and deferred fresh-boundary/shield state coexist
with native adapter identity, owner-scoped contributions, decoded-level transforms
and character callback/subclass state. No parity process or diagnostic is owned
or modified by this task.

The updated qualified ordinary expectation is **27 inherited failures, zero
errors and 62 skips**. The parity candidate
`7d87e02a1c75b80c8f5969ab6654ee1cf3976b7a`, run
`20261008T034810Z-47c3194d`, selected 3,027 source classes and completed
3,025 reports / 26,422 tests in 5,221.08 seconds. The FBZ/SOZ full-registry
restore/replay failure alone resolves; all other full first assertion lines and
all 62 skip identities/reasons match the qualified earlier baseline. The SSZ
comparison retains only its previously established exception-prefix/object-hash
normalization. The spin-tube assumption remains a recorded skip, not a pass.

That invocation's fresh guards exposed two caused failures. The bounded
`98137d8516a06e546185aa070bf0bf574473c04a` repair is qualified by 56 focused cases
with no failures/errors/skips (request 52813) and fresh 86-suite / 672-case guards
with no failures/errors/skips (request 40077). Independent comparison found no
engine Java, Java-test/resource, POM, hook, pin, release-policy or selection-tool
changes from 981 to main 5d; only Sitar README prose differs. This qualifies the
candidate plus its repair as the updated inherited expectation. No completed
full run measured at main 5d was present in the inspected record. See the
[parity candidate and repair evidence](../audits/2026-10-07-stock-parity-gap-verification.md#completed-candidate-ordinary-lane-and-guard-repairs).

The upstream range changes no starter, export, SDK/testkit, mod catalog or creator
tooling contract. Prior external acceptance retains its actual 7318/d49 source
attribution. Engine artifacts must be refreshed for the new native source;
unchanged creator builds are not relabeled as fresh engine-runtime verification.

Direct focused session 89974 measured clean merged source e600 with zero queue
wait, using the supported profile-free `--lean` lane. All tracked source,
resource, example, POM and release-policy hashes remained unchanged through its
terminal exit zero. The command was:

```bash
ROM_ROOT="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
DISPLAY=:0 LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/maven_queue.py \
  --lean -Dmse=off -B -q \
  "-Dtest=TestNativeRewindAdapterPublication,TestGameplayModeContextRewindRegistry,TestTwoActModCampaign,TestPhase3SampleCharacterIntegration,TestShieldRewindRestore,TestShieldRewindPendingRestore,TestAbstractPlayableSpriteRewindCapture,TestFreshLevelBoundaryRewindHeadless,TestFbzSandopolisTimelineHeadless,TestGameLoopFreshLevelHandoff,TestScheduledLevelPlaybackInput,TestScheduledPlaybackInputPublisher,TestModApiSignatureSurface" \
  "-Dsonic1.rom.path=$ROM_ROOT/Sonic The Hedgehog (W) (REV01) [!].gen" \
  "-Dsonic2.rom.path=$ROM_ROOT/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  "-Ds3k.rom.path=$ROM_ROOT/Sonic and Knuckles & Sonic 3 (W) [!].gen" test
```

All 13 fresh XML reports carry the three absolute ROM properties. They contain
**128 tests, zero failures, errors or skips**:

| Class | Tests |
|---|---:|
| `TestGameLoopFreshLevelHandoff` | 4 |
| `TestScheduledLevelPlaybackInput` | 6 |
| `TestGameplayModeContextRewindRegistry` | 28 |
| `TestScheduledPlaybackInputPublisher` | 2 |
| `TestShieldRewindPendingRestore` | 4 |
| `TestModApiSignatureSurface` | 9 |
| `TestNativeRewindAdapterPublication` | 9 |
| `TestTwoActModCampaign` | 35 |
| `TestPhase3SampleCharacterIntegration` | 2 |
| `TestAbstractPlayableSpriteRewindCapture` | 14 |
| `TestShieldRewindRestore` | 12 |
| `TestFbzSandopolisTimelineHeadless` | 1 |
| `TestFreshLevelBoundaryRewindHeadless` | 2 |

Compiled signature verification confirms the unchanged 880-type / 17,272-line
candidate surface. This is focused merged-runtime evidence. The actual-base
plan now selects all **3,055 ordinary source classes** and separate fresh guards;
Java 21, Lua 5.4 and PowerShell preflight passes. Allow 110–125 minutes ordinary
plus about four minutes guards, excluding queue wait. The invocation limit is
150 minutes with the unchanged ten-minute no-output limit; either timeout means
incomplete verification. Full development and actual-main delivery checks,
failure/skip comparison, push and cleanup remain required.
