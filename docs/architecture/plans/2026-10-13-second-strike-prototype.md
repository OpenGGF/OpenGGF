# Second Strike — normal-campaign difficulty prototype

Future content is scoped in the [complete AIZ prototype and S3&K campaign
roadmap](../designs/2026-10-15-second-strike-campaign-scope.md). That document is
a design proposal; the outcomes below remain the record of implemented work.

Integration base: `develop` `13add5b0b918a1f7ff7e343c8ccc858ad4527d85`.
The user authorized implementation of all brainstormed difficulty concepts.
Post Two Ambush remains a separate useful encounter concept; its trial flow is
not the template for this delivery.

## Product boundary

Build a maintained, packaged S3&K patch named **Second Strike**. The first
coherent slice is ordinary Angel Island Act 1, including its native fire bank
reload, first boss, results and subsequent progression. Keep stock physics,
native lives/death/game-over, physical checkpoints, save-slot UI, emerald
rewards, stage returns and act transitions. The launcher uses a separate player
configuration/save root while retaining normal save behavior. No custom course
controller, instant retry menu, forced fresh position or scripted victory.

| Concept | Working demonstration | Acceptance |
| --- | --- | --- |
| Badnik roles | Native-art spotter, guard and artillery replacements | Cooperation changes a real encounter; destroying the caller ends new assistance |
| Provoke/dodge/punish | Committed dash/volleys, visible brake/reload openings | Native attacking, shield deflection, hurt, destruction, score and life behavior |
| Meaningful rings | Five original ring placements across forest and fire together | Severe scarcity with native hurt/recollection; stage-entry thresholds remain native |
| Dangerous shortcuts | Hardened enemies on existing fast/upper paths | Readable fast solution and viable cautious alternative |
| Environmental combinations | Native enemies beside existing vines, logs, slopes and landings | Native terrain/mechanics and transition objects stay intact |
| Boss second trick | A bounded delayed extra breath wave late in Fire Breath | Native HP, arena, defeat, results and transition; cancellation on killing hit |
| Character opportunities | Existing momentum, flight, rock and glide paths | Native Sonic, Tails and Knuckles abilities; support advertised only with observed cells |
| Worthwhile stage detours | First Chaos board turn gates and three-spring Gumball variant | Genuine native play, unchanged rewards/return; other layouts/families remain stock |

Prototype scope is the authored opening and representative stage variants, not
a completed whole-game hard-mode campaign. It must continue normally into
unmodified later content. Native disabled behavior is the reference; new rules
exist only when the tagged patch/service graph is active.

## Ownership and implementation

- Root: normal module/title delegation, launcher/packaging, native class-preserving
  decoded placement admission, owner-scoped boss/stage seams, candidate API pin,
  documentation, integration and combined validation.
- Enemy worker: `secondstrike/enemies/**`, `TestSecondStrikeEnemies*`; bounded
  recreated object state, native art/SFX and truthful attack/vulnerability cues.
- Route worker: `secondstrike/routes/**`, `TestSecondStrikeRoutes*`; immutable
  original placement/ring selection, native anchors and character opportunities.
- Boss/stage worker: `secondstrike/boss/**`, `secondstrike/stages/**`, corresponding
  tests; authored policy values and real native behavior/restore evidence.

Each worker has its own worktree/build directory. Shared engine contracts are
sequenced by root before consumer builds. No target copying, foreign process or
queue-policy intervention, shared-desktop focus/input, or ambient audio.

The existing `LevelPatch.apply()` returns a `MutableLevel` snapshot. That loses
the concrete `Sonic3kLevel` required by native AIZ fire transitions. This approach
is rejected for the prototype. An explicit placement-only admission path must
preserve the actual native instance, publish validated object/ring edits once at
load, retain identities and leave geometry/art/transition ownership untouched.

New narrow S3K service policies select a bounded late-breath follow-up, validated
ROM-layout hazard edits, and bounded Gumball spring offsets. Absence retains
literal native behavior. Creator arrays are isolated, copied and validated inside
their trusted callback boundary; invalid returns must identify the true owner.
Pending boss follow-ups are captured with native object state and cancelled on
fatal hit/defeat/load. Stage changes are admitted at native initialization, not
reapplied each frame or after a rewind restore.

## Verification and delivery

Use Java 25, Lua 5.4 and original absolute main ROM paths. Focused tests establish
enemy phases/children/rewind, placement provenance/native class preservation,
stage validity/reward/return and boss scheduling/cancellation. Keep the four
mandatory S3K startup/loading checks green. Inspect the pinned-base category plan;
shared/public-contract changes require the normal change-based **smoke** selection
and separate fresh guards. Full-suite is release-only and is not requested.

Complete a cold native-input opening route plus bounded character/width/lifecycle
cells and actual stage/boss demonstrations. Offscreen production captures use
surfaceless EGL, unset desktop display variables, a private runtime directory and
null audio. Inspect state before images; record real PCM contributions and use
declared positioned setups only as supplementary local evidence. No trace rows
hydrate gameplay. Preserve captures outside Git and consume temporary diagnostics.

Before final delivery, reconcile the actual updated destination, compare any
disputed failures by identity/full assertion against bounded matched baseline
tests, integrate into the main checkout's current branch without switching it,
push only that branch, and account for all owned child changes before cleanup.
Execution outcomes and observed support cells will be appended here.

## Observed prototype and ownership outcomes

The lead compiles 3,850 production sources using Java 25. The ROM-free creator
jar passes the real packager with zero errors and eight deliberate internal API
warnings: native render priority, points/animal destruction helpers and title
graphics lookup. These are compatibility limits, not a published API promise.
The compiled normalized candidate surface has 20,123 lines: 22 additions and zero
removals against the pinned base. Candidate status/version remains `0.7.0`.

Native placement admission passed 35 focused legacy/transaction cases and three
real owner/ROM consumer cases. The actual `Sonic3kLevel` keeps its geometry and
native AIZ fire-terrain mutation consumer. Native plans admit bindings only;
retained rings must be original record instances in their original order. The
two inventory-gated plans retain 68/104 forest rings and 45/69 early fire rings,
with 10/3 owned enemy placements. Later physical Act 2 objects/rings are untouched.

Enemy phases, cooperative queries, bounded projectiles and recreation passed
seven focused cases. Native boss/stage cases passed 14 checks, then 23 stage and
existing owner checks. The completed wrapper/eligibility follow-up passed 60
checks, including canonical-owner faults, malicious layout mutation, retained
array isolation, proxy aliases, unrelated controls and VM-fatal identity. Two
new test sources initially used a package-private helper across package boundaries;
moving the fixture to its existing owner package repaired compilation without
widening engine access. Creator static Lists/Sets and an array were rejected by
the real packager; invocation-local immutable authoring values replaced them.

| Observed cell | Native outcome | Limit |
| --- | --- | --- |
| Sonic + CPU Tails cold phase-2 recipe | 8,746 ordinary inputs; fire 4598, physical post 4814 with 30 rings/four bonus stars, cue 6993, extra wave 7013, results 7788, playable Act 2 at 8745 | Fixed exported BK2 replay matches all non-input state rows; no death or post-intro hurt |
| Solo Sonic cold phase-2 recipe | 9,193 ordinary inputs; same cue/wave, results 7689, playable Act 2 at 9192 | Fresh exported BK2 matches all non-input rows; no follower, death or post-intro hurt |
| Blue Sphere first board | Twelve admitted red gates, 102 blues, native turn/jump, failure/results and normal level return | Direct entry; no emerald completion claim; matched stock offscreen draw also lacks Sonic's silhouette |
| Gumball | Three springs, native depletion, real 1UP (lives 3→4), natural exit and level return retaining life | Direct entry; REP generation observed separately, restoration covered by owner tests |
| Tails crown | Native flight and one original crown ring collected, alive through 380 frames | Positioned local cell, not a whole Tails route |
| Knuckles forest rock | Unrolled walking breaks native rock #69; native glide follows | Positioned local cell, later native enemy death; climbing remains unproved |
| Knuckles fire-bank glide | Native glide, alive 255 frames | Direct Act 2 native bounds prevent the requested earlier rock access; no camera override |

Both successful cold recipes use the ordinary intro (including its native punch),
real hurt/collision owners, fire reload, boss and results. Controller authoring
repaired the stock movie's approaches to the new guard, vines and rock steps;
runtime physics and fixtures were never tuned to make the route pass. A rejected
lower-bank wall heuristic was removed after the native Sonic upper-route approach
completed. Positioned cells are supplementary and never stand in for cold reachability.

Graphics were actually surfaceless EGL on AMD's integrated Raphael/Mendocino
renderer, GL 4.6 Compatibility/Mesa 26.2.4, not an asserted discrete GPU. Display
variables were unset; runtime/config/save directories were private and host audio
used the null device. Native SMPS PCM is offline output, not speaker certification.
Gumball and Blue Sphere moving witnesses retain 593/1,143 frames with matching
48 kHz stereo audio. Useful state, input, images, movies and exact launch hashes
live outside Git under the task's explicit scratch directory.

The continuous pair witness retains all 8,746 frames and 6,996,800 stereo PCM
frames. Every non-input state field matches the lightweight cold replay. Its
late-boss tell is a brief native white/blue barrel puff, followed by four native
parent-attached, staggered flames; it is not an independent travelling projectile
or a large warning banner. Lossless audio has nonzero AC variance in intro, level,
boss and results windows. The 145.767-second moving derivative and canonical
pair/solo proof are retained under the external `second-strike-route-survey`
task directory, with the selected cue/wave images and audio analysis.

The initial upper-left title badge overlapped the native title's Sonic artwork in
frame 899. Its replacement occupies the free footer strip between Press Start and
copyright; final pixels were inspected. An owned offscreen menu probe observes
native title → data select, Right selecting empty slot 1, and fresh Start emitting
`NEW_SLOT_START` for Sonic+Tails at frame 1622 through the normal fade. It records
the callback action, not an Engine launch or save. The original title-first
capture stalled because `HeadlessGameBoot` omits Engine's data-select launch
callback and `GameplayCaptureSession` lacks data-select drawing. The probe uses
the native provider's draw path; shared capture/input/fade contracts are unchanged.
A new delegation fixture also incorrectly expected two default data-select
factory calls to share identity; the corrected fixture supplies one known provider
and verifies forwarding.

Final build-only SDK/Javadoc/binary artifact verification passed; its tests were
deliberately skipped. The repaired delegation and bounded native save/launch
checks subsequently passed. Remaining delivery gates are change-selected
smoke/guards, destination composition and main integration/push. No full-suite
or finished delivery is claimed here.

## Combined validation checkpoint

Implementation `b2d31bd3e` is composed with documentation-only destination
`b3464b31b6a6d855797f353e2c192d514ffaaf2b` at `b5a496175`. Executable,
test, build and API inputs of the destination are identical to the pinned
`13add5b0b918a1f7ff7e343c8ccc858ad4527d85` base. The repaired delegation
case passed once without skips, then six native confirmation, save-slot,
fade/dispatch and patch re-resolution cases passed without skips. The observed
native menu action remains separate from actual Engine launch/save coverage.

The unchanged 3,178-class, all-category **smoke** selection ran with fresh guards
requested, Java 25/Lua 5.4 and desktop display variables unset:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 tools/testing/run_categories.py \
  --base 13add5b0b918a1f7ff7e343c8ccc858ad4527d85 --max-minutes 40 --run
```

Run `20261013T134518Z-cf7752fa` admitted at 13:45:18 UTC after 1,473 seconds
of queue wait. The ordinary invocation reached its 2,402.48-second execution
limit: 2,758 reports, 23,497 cases, two assertions, zero errors and 57 skips.
All eight new prototype/ownership classes completed: 49 cases passed with zero
skips. The invocation is **incomplete**; remaining ordinary coverage and fresh
guards did not run. The diagnostic tail shows continuing native route tests,
not a stalled or crashed process. The two complete disputed assertions are:

- `TestS3kMhzAct2AuthoredRoute#incomingRoutesCompleteActTwoWithLiveRewindBoundaries(String, int)[2]`:
  `late pulley owns Tails ==> expected: <true> but was: <false>`.
- The same method `[3]`:
  `late pulley owns Sonic ==> expected: <true> but was: <false>`.

All 57 skip identities and first causal reasons were inspected; they comprise
explicit opt-in diagnostics/probes, native renderer availability, the Infinite
Sonic platform/flyer prerequisites and CPZ's unobserved tube interaction. No
missing-ROM skip occurred. A bounded matched method on the exact stock base is
required before attribution. Consumed diagnostics were acknowledged/deleted.
The same smoke selection needs a revised measured execution budget; this is
not a passing broad result or release-only full-suite validation.

The necessary retry used the same selection and environment with
`--max-minutes 75`. Run `20261013T154956Z-784d9cd3` tested clean `370d081b2`
against `13add5b0`, admitting at 15:49:56 UTC after 3,539 seconds of queue
wait. All ordinary coverage finished in 3,335.71 seconds: 3,159 reports,
26,718 cases, 26 assertions, zero errors and 63 skips. No failure/skip detail
was omitted, and no missing-ROM skip occurred. The skips were explicitly
inspected: opt-in diagnostics, native graphics prerequisites, the Infinite
Sonic platform/flyer prerequisites, CPZ's unobserved tube interaction and
optional local audio reference/capture work. This is smoke coverage, not a
release-only full-suite result.

Matched stock controls on exact, clean `13add5b0` ran the three MHZ cases
once (one pass/two identical assertions), then only the other failing methods:
eleven classes, 22 whole methods, 25 cases, 24 assertions, one pass and zero
errors/skips. All 26 candidate negative identities and exception types
reproduced. Twenty-five complete assertion messages match literally; the
complete SSZ Tails replica diagnostic matches after replacing only its two
verified `RewindObjectStateBlob@hex` identity suffixes. Frame numbers, native
slots/IDs and every mutable field remain equal. This does not claim raw blob
byte equality. The shared negative inventory is also documented in the
[runtime compatibility record](../designs/2026-10-13-mod-runtime-compatibility.md#completed-recovery-and-attribution).
Both matched runs ended with Maven exit 1; consumed XML/text reports were
removed, and the complete category run was acknowledged/deleted.

Fresh guards finished in 275.60 seconds: 88 reports, 678 cases, one owned
failure and zero errors/skips. `TestBootstrapModuleProviderCachingGuard`
identified the new Blue Sphere policy lookup's bootstrap-capable accessor.
The repair reads only the admitted `WorldSession` module; pre-session ROM
loading retains stock layout and never invokes creator callbacks. Two real
ROM initialization checks cover active-world precedence and stale bootstrap
policy isolation. Guard approvals and budgets are unchanged; verification of
this repair is pending.

Published destination `4a9bec1eaf86e7b70959113e484315c5013c1b7c` is now
privately composed by intent. It includes runtime compatibility diagnostics
as well as the Maven queue fix; the merge was conflict-free and preserves
both hazard entries. Its local runner batches the complete smoke selection
at 50 candidates and releases admission between batches/guards. The existing
monolithic request was preserved through completion; it was not restarted
to adopt new scheduling. All 161 Python safety checks and actual Java 25,
Lua 5.4 and PowerShell preflight pass on this composition.

The composed change-based plan selects 3,181 smoke classes and guards.
Completed native gameplay coverage remains applicable to unchanged inputs;
the bounded policy repair and incoming mod/runtime composition require
focused mod, real-stage, API/SDK and startup checks plus fresh guards. They
do not require repeating the unchanged ordinary campaign coverage.

The composed checks completed on frozen index tree
`7b89a2b6e298e6ab26644b6f2d5c18f43ea93810`: 23 exact selectors matched 24
actual test classes, with 276 cases passing without skips, including both new
ROM stage initializations, all affected compatibility/runtime consumers,
native placement owners, stage snapshots/results, API reflection/pins and the
four required S3K startup/loading classes. The normal `verify` invocation
compiled 3,852 production and 3,831 test sources and completed SDK/Javadoc/
binary artifact verification in 1:40. Its actual compiled candidate surface
matches all 20,123 canonical pin lines. A separate fresh `-Pguards` invocation
passed all 88 classes/678 cases without skips in 4:40. The owned bootstrap
guard failure is resolved without changing its approval list or any budget.

The example rebuild passes the packager with the same eight documented internal
API warnings and no errors. Its final ROM-free jar SHA-256 remains
`1e2e9b0094a3819439ada9d544c916d463ef4ac874ec45da298a919244188d01`.
The final composed Blue Sphere witness runs 1,143 native updates, observes all
twelve gates, turn/jump, natural failure/results and a normal level return at
1022. All 39 previously recorded state fields match the earlier witness exactly;
the maintained driver now also records mapping-frame/fade-timer diagnostics.
The complete 914,400-frame stereo 48 kHz WAV and inspected native PNGs at
294/600/1022 are byte-identical. Its isolated surfaceless EGL process closes
normally; the existing missing-Sonic-silhouette limitation remains visible.
The final witness is under external `second-strike-20261013/final-stage-blue`.

At that checkpoint, main integration, source-equivalence/build checks, push and
accounted worktree cleanup were pending. The completed steps are recorded below;
no whole-suite green result is claimed.

The destination subsequently advanced to
`1ead8d9b44b303f3a7617a2ab3c2b0541c37a8ae`, including native AIZ post-boss/
drawbridge ownership fixes and selected-world initial-rule binding. Its
composition is conflict-free. The affected physics/character/bridge/boss,
prototype, API and required startup checks passed 404 cases across 24 actual
classes without skips in 1:41; a separate fresh invocation passed all 678
guards/88 classes without skips in 4:32. SDK/binary verification completed.
Frozen tested index: `9ce204872a24fa32cd2e7b58705b8ca946d4faf7`.

The maintained cold driver then replays both original phase-2 BK2s against
that compiled composition, without video-wide rendering or authoring feedback:
8,746 pair and 9,193 solo ordinary updates, both exiting successfully at their
same playable Act 2 boundary. All thirty shared non-input state fields match
the prior proof on every row; intro hold remains zero and the late-boss cue
still begins at 6993. The earlier continuous movie remains applicable to those
unchanged observations. Final replay state and milestone images live under
external `second-strike-20261013/final-route-{pair,solo}`. Private runtime/save
directories, surfaceless EGL, unset desktop display variables and null host
audio were retained; all owned replay processes exited normally.

The final destination composition, integration and cleanup facts follow below;
no additional unchanged smoke sweep was required for these bounded follow-ups.

## Main integration verification

The main checkout remains on `develop`; its fast-forward pull found destination
`1ead8d9b44b303f3a7617a2ab3c2b0541c37a8ae` already current. Integration is
`85c73afa9152e8352f879227006e78a4ad4a7888`, with no conflicts. Its index tree
exactly equals qualified feature `7c5ea81b3b2174c54b93a079f3684a67f3dd95b3`.
The latest composed checks and native replays therefore apply to the integrated
executable/test inputs; they were not repeated as an unchanged smoke ceremony.

Post-integration `python3 tools/testing/maven_queue.py -Dmse=off -DskipTests
verify -B` completed with build success in 1:27, including current engine, SDK,
Javadoc and binary artifact verification. Tests were deliberately skipped in
that build. The CI-push policy audit passed across 13,044 tree entries. Known
dirty disassembly states, archives, launch scripts and notes are preserved.
The integration and evidence were pushed on `develop` at
`843aa4738d9905a9125acb37fc5459f80d73633f`; the remote head was independently
verified. This closing prose changes no executable, test, build or API input.

## Delivery and cleanup

All fifty accepted child paths match their original handoff hashes and the
committed `b2d31bd3ea8768c20238aae438179c9e24bd7d40` source. The integrated tree
has only the two documented root follow-ups to those paths: the admitted-world
stage-policy lookup and its native regression tests. Expanded staged/untracked
inventories contain no unknown child changes. All five task branch heads are
ancestors of the delivered `develop` history.

The three child worktrees, the stock-control baseline and the lead worktree,
with their five local branches, were removed after that accounting. No owned
build, test or capture process remained. Four config templates, the lead's
existing rewind-gap notes and its Starpost Valley save were preserved outside
the repository before removal. Linked main configs, ROMs and disassembly
directories were left untouched. Generated build trees and image caches were
discarded; useful captures, source accounting, bounded stock comparisons and
app-owned conversations remain available. The lifecycle registry is closed
with no pending cleanup.

The ROM-free deliverable is external
`second-strike-20261013/second-strike.jar`, with the SHA-256 recorded above.
The continuous native campaign movie remains
under external `second-strike-route-survey/mod-canonical-pair-phase2-film/`.
The external task directory also holds the small cleanup record and preserved
files. A final read-only check confirmed the known unrelated main files and
submodule states match their pre-integration preservation record.

Delivery comprises the observed AIZ1 cold campaign slice through native results
and playable Act 2, the local character opportunities and first-stage variants.
The smoke run's twenty-six ordinary failures are stock-attributed; the repaired
fresh guards and focused compositions pass. This is a verified prototype with
the support limits above, not full campaign coverage or a green full-suite claim.

## Harsher AIZ1 revision

The user requested a stronger difficulty layer after that delivery. Its pinned
integration base is `5d9dd81381b05db66f530f55b8627f90ad1b88cc`. The requested
economy is at most five original placed rings across the forest and its physical
fire continuation together. Ordinary non-speed-shoe monitors become visibly
native Robotnik boxes; speed shoes remain unchanged. Native stage prizes and
ring recollection are not a ring-counter cap. The twenty-ring checkpoint bonus
threshold remains native, so those five placed rings alone cannot qualify it.
The later playable Act 2 and other zones remain outside this authored slice.
The six hidden boss/results-region monitors remain native: physical table
records 162/164/167/168/170/174 at `$1130`–`$11E8` have contents
8/8/7/5/1/7, none a ring or super monitor. Fifteen ordinary boxes become
Robotnik traps, one keeps speed shoes, and those six remain hidden rewards.
This implements the requested majority of hostile monitors without changing
the native signpost's reward-spawning graph.

Enemy durability is two separated accepted hits for spotters/artillery and
three for guards, with bounded hit recovery and final-hit-only native rewards.
Extra allies are admitted from verified enemy ground anchors, with bounded
population, lifetime and replay state. Guard dash speed rises from four to ten
pixels per update, preserving its committed direction, readable wind-up,
native floor/ledge checks and recovery opening. While dashing it is invulnerable
and uses the native fire-shield dash art; no invented fallback sprite is used.

The reported strange sound near vines was investigated without changing stock
audio or vine behavior. The added spotter used S3K Whistle `$46`, a 48-tick
modulated FM tone. Stock Monkey Dude instead uses Missile Throw `$51` when
throwing a coconut (`MonkeyDudeArm_ThrowCoconut`). The native vine grab uses
Grab `$4A` at its successful grab edge (`AIZRideVineHandle_CheckGrab`), and the
guard's Blast `$48` matches `Rhinobot_StartChargePrep`. All numeric IDs and
the S3K bank path agree with the disassembly. The prior route records spotter
CALL entries at 2473, 2577 and 2752; native unload/recreation explains the short
first interval. There is no per-frame cue loop or established bank-routing bug.
The spotter cue will use `$51` at the same CALL-entry edge. Mixed prior PCM does
not establish which sound the user heard; this is creator cue polish.

The native floor audit admits three additional guard companions: both forest
artillery anchors and the first early-fire anchor. The fourth candidate has a
real gap at its proposed feet and is rejected rather than changing terrain or
relaxing the floor sensor. Guards probe every pixel of their ten-pixel stride,
stop within their bounded charge range and expose a seventy-two-update brake.
They then return toward their post at at most two pixels per update, with a
sixty-four-update cap and normal vulnerable collision. A dry first-stride check
prevents a blocked tell from flashing a stationary flame charge. The return
samples native centre-foot terrain at each pixel: opposite-front sampling
rejected a continuous native slope after the prior charge's front sensor set Y.
The acceptance range is unchanged; no terrain, position or fixture is fitted.
The native flame attack cue is `$43`; dash immunity includes powered attacks,
while the native wind-up contact behavior remains intact.

A supplementary direct-level observation positions solo Sonic at
`($1BC0,$3B0)` with neutral input. The candidate produced 300 native GPU frames
and 240,000 stereo PCM frames at 48 kHz. Inspected frames 35/40/45/60 show the
wind-up, moving flame-shield dash and exposed brake. It uses surfaceless EGL,
an owned profile and the null host-audio backend; it neither proves physical
speaker output nor a cold campaign completion. The native pixel oracle checks
all six dash frames and flips against ROM DPLCs, independently of player-shield
bank changes. The compiled candidate surface has 20,123 signatures and exactly
matches the existing unpublished `0.7` pin; these internal helpers do not expand
the supported Mod API.

The original continuous movie and full-route evidence above describe the
previous ring economy and enemy behavior. They are historical evidence, not
validation of this revision. New focused native monitor/damage/art/rewind
checks, the actual change-based smoke plan and a fresh gameplay witness are
required before integrating it. Isolated worktrees and the updated batched
category runner are used; no shared-desktop automation is permitted.

### Focused revision evidence and route limits

The final combined nineteen-class check used the normal Maven wrapper with
all three original absolute ROM properties and `verify dependency:build-classpath`.
It passed 167 cases without failures, errors or skips in 52.806 seconds,
including native monitors, contact/floor/slot rewind, ROM pixels, all required
S3K startup classes, API pins and SDK/Javadoc/binary artifact verification.
The subsequent return-to-post polish passed twenty-two affected unit/native
cases without skips; unchanged art, monitor and API checks were not repeated.

The final ROM-free jar builds with twelve explicit unsupported-internals
warnings and no package errors. Its new offscreen witness is external
`second-strike-hard-mode-20261013/guard-final-capture/flaming-rhino.mp4`:
300 decoded 960x672 frames and five seconds of native stereo audio. Inspected
frames 40/130/205 show the first flaming charge, slow return and another full
charge. Both video and audio decode completely. These are positioned visual
observations; native contact/immunity and return replay have separate tests.

A controller-only cold exploration retained the native intro, reached the
physical fire reload and checkpoint three, and showed actual separated
multi-hit losses and ten-pixel dash strides. It did not reach the boss/results:
the controller repeatedly hopped beside the native tree/wall near `$F35`.
Two ordinary-input retreat heuristics did not clear that obstacle and were
discarded. No observed death, enemy crash or terrain change explains that
controller limitation. The earlier complete campaign movie stays historical;
this revision does not claim a new full cold campaign completion.

The combined change-based smoke run and final destination integration are
pending. Existing twenty-six stock-attributed negatives are the comparison
reference; new or worsened failures remain delivery blockers.


## Hard-mode delivery qualification and updated-base composition (2026-10-14)

The initial `32df1e98c9dfd0b9813b69323731b772a9af73e0` candidate used
pinned base `5d9dd81381b05db66f530f55b8627f90ad1b88cc`. The complete
3,187-candidate smoke plan had 64 ordinary batches. Its supervising transport
exited 143 after 58 completed batches; the cause is unknown. The surviving
59th batch subsequently ended without a captured exit and was not counted.
After exact process reconciliation, only the 287 unfinished candidates were
retried through the unchanged local category runner and normal admission.
The prefix plus run `20261013T232622Z-2173fecd` covers the original selection;
it is not a narrower selection or a repeated full baseline. Original and
continuation diagnostics were inspected, acknowledged and deleted.

Combined ordinary outcome: 3,157 reports, 26,700 cases, 27 assertions, zero
errors and 64 inspected skips, in 4,185.85 seconds of completed execution.
All 26 historical negative identities/types match: 25 complete assertions
literally, and the full SSZ Tails diagnostic after only its two verified
`RewindObjectStateBlob@hex` identity tokens. The missing complete historical
SSZ text was recovered from the original runtime-compatibility tool output,
not recreated from truncated prose. No gameplay field was normalized.
The historical exact skip inventory is unavailable; this does not claim
literal whole-skip equality. Current skips are opt-in work, graphics
prerequisites, Infinite Sonic platform/flyer prerequisites, the CPZ tube
assumption and local audio reference/capture prerequisites. None is a
missing-ROM skip; one generic graphics assumption has an opaque cause.
Fresh guards passed: 88 reports / 678 cases, zero failures/errors/skips,
261.54 seconds. This remains inherited-failure-qualified smoke, not a green
whole-suite or public-release full-suite result.

The additional audio CLI assertion is launcher-environment contamination.
The exact `TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput`
case reproduced on clean pinned `5d9dd813`: one failure with
`JDK_JAVA_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`, one pass with that
variable unset; zero errors/skips in both. Java 25 prints a leading `NOTE`
before the application's expected `PRODUCER_UNAVAILABLE:` stderr prefix.
The same unchanged candidate case passed with launcher-option variables
unset. No product/test source was changed to suppress the assertion.
The merged native checks pass the graphics property as a Maven property,
with both launcher-option variables unset.

Develop subsequently advanced to
`5b2ff8e7851b99dd40998dfc41fbfd21c80031ef`. Private intent composition
preserves native encounter monitor disguise/metadata and level-VRAM ending
refreshes alongside our canonical content constructor and independent flame
sheet. Ending scene identity remains canonical and never re-enters the AIZ
placement patch. Fresh-load destination ring publication is preserved once;
our five placed records do not become an inventory reset or clamp. No new
enemy implementation adjustment was required. Optional combined mutator packs
can change dynamic rings or generic truce behavior; native field/firing rules
and whole-picture badnik transforms do not automatically target our variants.
Standalone hard mode is the supported qualification here.

The updated-base planner selects all 3,256 smoke candidates because the
example paths conservatively select all categories. Another unchanged broad
run would be disproportionate after the completed own smoke: new overlap is
bounded to the native monitor/art provider and identified load, hurt, rewind,
child-lifetime, presentation and audio consumers. The explicit 33-class/method
union below passed all 212 cases with no skips, including a real
Second Strike-resolved `WorldSession` proving no opt-in mutator graph is
installed while difficulty services remain available. Java 25 compiled 3,965
production sources; `verify` also passed binary SDK, Javadoc and artifact
checks. Fresh actual-composition guards passed 88 reports / 680 cases with
zero failures/errors/skips. This is bounded composition validation, not a
whole updated-base smoke-suite claim.

Exact focused selector union (comma-separated, supplied as `-Dtest`):

```text
com.openggf.audio.TestAudioMutatorAccess#internalLeasePreservesExternalObserverAndJournalsNativeRomRequests+noBindingLeavesNativeCommandAndOverridePathsUnchanged+replayRetainsLogicalNativeIntentWithoutDoublePhysicalSubmissions,com.openggf.game.mutators.TestSpritePictureTransform#anatomyCompositionRetainsWholePictureMetadataAndUsesSeparateAnchors+semanticTargetingAndStealthTakePrecedenceOverEveryWholePictureEffect,com.openggf.game.session.TestWorldPolicyHistory#disabledHistoryDoesNotCaptureOrAccumulateStageBoundariesAndArmingStartsFresh+zeroTickCleanupReplaysItsNativeDeletionWithoutRepeatingCreatorCallbacks,com.openggf.game.sonic3k.TestSecondStrikeEnemiesNativeArt,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kPlcArtRegistry#s3kArtRegistryMappingsStayWithinSaneSpriteSheetLimits,com.openggf.game.sonic3k.audio.TestSonic3kSpeedShoesCommandSemantics#monitorPickupAndTimerExpiryUseSemanticEightAndOneWithoutSystemCommands+shorterNativeGrantRetainsStockDurationAndRewindRestoresBehavioralExpiry,com.openggf.game.sonic3k.mutators.TestNativeMutatorRuntime#nativeAllocationHelpersCaptureOwnedTailsAfterRootRemovalAndLeaveIndependentSiblingsAlone+nativeDefeatReplacementAndDestructionChildrenBelongOnlyToTheExactOwnedGraph,com.openggf.game.sonic3k.objects.TestSonic3kMonitorContentOverride,com.openggf.game.sonic3k.objects.TestSonic3kMonitorObjectInstance,com.openggf.level.objects.TestSecondStrikeEnemiesNative,com.openggf.level.rings.TestRingManagerRewindSnapshot#restoreSparseAttractedRingsClearsPreviousActiveSlots+roundTripAttractedRingSlot+roundTripCollectedBitSet,com.openggf.mods.TestModApiPinPolicy,com.openggf.mods.TestModApiSignatureSurface,com.openggf.mods.code.TestSecondStrikeBoss,com.openggf.mods.code.TestSecondStrikeCampaign,com.openggf.mods.code.TestSecondStrikeEnemies,com.openggf.mods.code.TestSecondStrikeStages,com.openggf.sprites.managers.TestPlayableHurtObjectControlMovement,com.openggf.sprites.playable.TestPlayableHurtLandingObjectControl,com.openggf.sprites.playable.TestShieldRewindRestore#pendingRealInstaShieldRestoreDiscardsAbandonedAttackAndSlotState+registeredInstaShieldRestoreStillRebindsAndInvalidatesItsArt+restoreBeforeFirstStatusTickKeepsInstaShieldRegistrationPending,com.openggf.tests.TestFreshLevelBoundaryRewindHeadless#restoredBoundaryRetainsPublicationPhaseAndDispatchesDeferredAssembly,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kMonitorObjectInstance,com.openggf.tools.TestGameplayCaptureDataSelectLaunch#captureRelaunchRegistersFreshCreatorStateAgainstTheChosenNativeTeam,com.openggf.tools.TestGameplayCaptureFreshLoad#omittedDestinationTitleFinishesDeferredAssemblyAndReleasesTheLivePlayer,com.openggf.tools.audio.completerun.TestCompleteRunAudioCli#freshJvmBootstrapsTheS1ProfileButItsUnavailableDispatcherEmitsNoStandardOutput,com.openggf.tools.modsdk.TestGgfModCliCommands,com.openggf.tools.modsdk.TestModApiJavadocTool,com.openggf.tools.modsdk.TestModApiSdkPackager
```

Both focused and guard invocations use `python3 tools/testing/maven_queue.py`,
`-Dmse=off`, `-Dopenggf.headless.gl=surfaceless-egl`, the original three
absolute ROM properties, Java 25 and Lua 5.4. Focus ends with
`verify dependency:build-classpath -Dmdep.outputFile=target/second-strike-classpath.txt -B`;
guards use `-Pguards test -B`. Fresh compiled API export is 20,838 lines,
byte-equal to the candidate pin, SHA-256
`c0a84d671d8cba5c69423fe5616e6b4940851e12d530ce2fbccf0c6350c002a3`.
Our additions expose no new supported API surface or version.

The rebuilt creator jar retains its 12 explicit native-internal compatibility
warnings and has no errors or ROM assets. A new maintained
`GameplayCaptureTool` run uses only the actual merged classes, the rebuilt jar,
private profile, surfaceless EGL and null audio driver: AIZ1 at `(0x1BC0,0x3B0)`,
300 neutral BK2 rows, `--frames 300 --audio`. All 300 GPU PNGs, state CSV and
240,000 stereo 48 kHz PCM frames are byte-identical to the accepted final
hard-mode capture. Fresh images 40/130/205 show first flame dash, exposed
return and second flame dash. `guard-composed-capture/flaming-rhino.mp4` fully
decodes and is a continuous five-second clip, without desktop input automation.
It remains positioned supplementary evidence, not a new cold campaign clear
or a physical-speaker observation. The earlier harder cold controller still
stalls at the documented later wall/tree; no full harder-route completion is
claimed. Main integration, build, push and owned cleanup follow this checkpoint.

The final destination then advanced to
`4a0e714d5e29b65a5b1ba8e6cbb6a8223f560c75`. Its six-path EggSky announcement
recovery changes only that independent example, its two tests and prose;
engine, our test/creator sources, POM, testing tools and API inputs are unchanged.
Private intent composition retains both changelog entries. The completed
212-case focus, 680 fresh guards, compiled pin and native clip retain their
precise executable-input attribution; no unchanged engine run was repeated.

Main develop integration is `60fe182da136c5bb0ceb5af9477c9802903f138c`,
with a tracked tree exactly equal to verified private `c9122c74b`. The main
worktree build/test lease covers the merge and build. Main
`mvn -Dmse=off -DskipTests package -B` passed in 82 seconds; this is a build,
not another suite result. All known unrelated files and dirty submodules
retain their before-merge status and file hashes. No source tests were repeated
on identical integrated inputs. This prose-only successor records delivery;
publication and fully accounted task cleanup are the coordinator's final steps.


## 2026-10-14 native/mod architecture review and enemy expansion

This round is based on `develop` `485e89fbea5a2587b763052f0db115986ca681ce`.
Two independent read-only reviews confirmed that placement admission retains the
native level, and native physics, saves, checkpoints, rewards and progression
remain the correct owners. The intentional enemy tactics stay in the mod. The
stock game must not acquire Second Strike identity branches or difficulty values.

The review also found a real decorator defect: `ModDecoratedObjectRegistry`
forwarded slot layout but omitted `installFixedSstObjects`. Merely registering
creator objects therefore removed HCZ's native slot 5 owner and MHZ's native slot 4
owner beyond the authored AIZ slice. New manager tests failed on both absent
owners, and a stacked-decorator/reset test failed before any layout allocation.
The repair forwards the exact native hook to the underlying registry. Both native
owners now precede dynamic allocation, reset reinstalls each exactly once, and
ordinary rewind does not repeat initialization. The campaign badge also now uses
the backing content patch's S3K eligibility; it cannot disappear merely because a
custom character is selected. This aligns activation without certifying that
character's traversal support. These eight selected architecture cases pass with no
errors or skips after reproducing four failures before the repairs.

"150% further" is implemented as 225 pixels: the previous flat-floor charge
actually moved 90, despite its 96-pixel anchor limit. A distance budget replaces the
ten-update expiry, preserving ten-pixel strides and allowing a five-pixel final
stride. Return duration derives from the actual displacement at two pixels per
update, including an odd final pixel. Terrain can still end the charge early.
The longer range exposed a previously untested immediate-gap case: the front
sensor skipped the first four pixels after reversing. That prefix is now queried
with the same native surface/tolerance rules, rather than changing terrain or
allowing a jump. Native interaction fixtures use a viewport containing the whole
new range and a genuine grounded guard placement; an airborne synthetic placement
cannot prove that a charge was admitted. The four affected distance/gap/native-return/
powered-dispatch cases pass without skips. At this checkpoint broader composed qualification awaited
the Caterkiller and barrage integration; completed results follow below.

Powered full-screen attacks retain native eligibility: an armed HURT target or an
ENEMY with nonzero collision property is not dispatched to its powered callback.
Only the final eligible health point is cleared. The new test uses the production
powered surface; the older direct-callback test is explicitly named as such.
There is no generic touch/physics change or new mod-only exception in that owner.

The composed expansion retains literal stock Caterkiller roles/delays and admits
an explicit twelve-body preset: three firing tall, three non-firing middle, two
thin and four tails. Exact head/body links and the recreation recipe survive
rewind. Original-ROM inventory yields twenty forest bindings and eleven early-fire
bindings (six badniks including three Caterkillers, five monitors); later canonical
placements remain unchanged. Plants emit ten committed seeds per release, twice
twelve updates apart, with twenty live seeds per exact owner, gravity `$18`,
36-update tell, 108-update reload and 120-update TTL. Native Bloominator's unrelated
allocation/parity observation remains untouched.

The user's debt-cleanup steering supersedes the earlier twelve-warning acceptance.
The current strict bundled package exits zero with zero findings; no warning
suppression, budget relaxation or wholesale native AI export was used. The actual
compiled candidate pin has 20,897 lines, 59 additions and no removals against
`f36cc16c2`; descriptor and runtime version remain unpublished `0.7.0`.

| Reviewed debt | Disposition in the lead composition |
| --- | --- |
| Twelve unsupported references | Native score/popup/animal recipe, tiny powered callback, monitor factory, title canvas and attached-effect seams; mod-owned valid priority constants. Strict packaging reports zero findings. |
| Common-base enemy coupling | Guard owns companion state; Artillery overrides final-defeat release. Placement dedupe and exact captured lifetime ObjectRefId have distinct authority. Committed seeds survive combat defeat; unload retires them. |
| Target eligibility | Existing native P1/P2 query filters alive and each role's range before nearest-X selection; swapped-role, dead and off-range participant regressions retain commitment/dodge. |
| Repeated/dead control state | Supported SubpixelMotion replaces copied fixed-point arithmetic; obsolete origin coordinates, targetY, ID-zero rewrite and copied EnemyBinding oracle removed. Actual registration/decoded placements are asserted. |
| Optional capabilities | Semantic body centres, native firing/field queries, attached effects and grounded creator derivatives compose under the original content owner. Collision state remains unchanged by picture transforms. Other capability/family support is not inferred. |
| Copied stage admission | Authored twelve gates, empty-cell requirement and cloning remain; canonical host admission owns reachability/trailer/runway safety. Incomplete playback now writes its summary and fails; failure/results exits do not require an emerald. |
| Builder snapshot/publication | One inherited build lease covers Maven/classpath/javac/strict publisher. A validated temporary candidate atomically replaces the prior jar; failed validation preserves it. Existing profile options are rejected explicitly, never overwritten. Five bounded Python tests include a competing writer. |
| Creator-kit omission | Exact root two-path patch exports all sixteen authored ROM-free walkthroughs and explicit native-ROM Jupiter prerequisites. Twenty-one Python checks pass. SDK remains tools/templates paired with the same-source engine fatjar. |
| Test setup/static state | One explicit test-only compiler/loader fixture replaces copied setup, without shared authority/cache. No mutable gameplay statics or unresolved TODOs were found. |

Source accounting: exact child index trees `14a7a5a8` (eight paths) and `eea5f71f`
(seven paths) were reviewed and applied as stable Git patches; child sources and
history were retained. Root creator-kit commit `d1e8383a` contributes only
`tools/modding/build_creator_kit.py` and `test_creator_kit.py`; both imported index
blobs equal that clean peer. Rework after import is confined to the lead tree.

A real manager negative-peak test reproduced the late-boss cue gap (three expected
live flares, zero actual). The old out-of-Process_Sprites fixture hid the creation
pass: a later-slot flare consumes its first animation update immediately, while
the newly scheduled follow-up timer first decrements on the next parent update.
Only opted-in warnings now include that birth update and hold the native final
pose for longer admitted delays. Ordinary stock flare construction/sequence remains
sixteen updates. Twelve boss checks verify every pre-wave render producer call,
child-first/parent-first barrel slots, no-slot bounded failure, exact pending-wave
rewind, defeat cancellation and policy-disabled graph/timing equality. Stock napalm,
miniboss graph and AIZ2 codecs also pass (20-case prior composed warning run, no skips).

Focused evidence before the combined run: the composed 164-case invocation had
four fixture/assertion issues (array identity, two obsolete delay expectations and
missing camera); the affected twenty cases pass after correction. Sixty mandatory
S3K loading/bootstrap/decoding/AIZ checks passed without skips. Native floor, gap,
collision, shield reflection, exhaustion, colocated identities, double graphs,
registered owner/fault boundaries and restore/replay have focused coverage. The
maintained builder and strict bundled `-Dopenggf.bundled.mods=examples/second-strike
-DskipTests package` pass. Matched engine fatjar + SDK + testkit compile the actual
exported example, strict-validate it with zero findings, and execute four public
ModTestKit Jupiter placement cases without skips; SDK-only compilation is an
expected negative, not a packaging-contract defect.

Private upstream fetch pins `origin/develop` at `f076ef0063b4da24c52cb08038946e80d595b425`.
Its native tooling retains native policy implementations/erased service factories
and scene-owned startup. Intent composition is conflict-free and preserves original ancestry. Matched
upstream checks compile the member contract/native gameplay probe with the actual
engine + testkit, pass the bytecode contract controls and host-JVM bootstrap policy
probe, and pass all six Linux/Windows packaging safety cases. The initial probe
compile omitted the testkit/helper, executed no cases, and was corrected without
production edits. This is no native-image portability qualification. At that private checkpoint, strict artifacts, the compiled pin and paired SDK
source/Jupiter gates passed; supplementary GPU/PCM witnesses, change-based smoke
and main integration were still pending. Their later results are recorded below.
Historical gentler full-route movies do not certify the harder cold campaign, and
custom-character traversal and exhaustive/native-image portability remain unverified.

### Receiver callback containment and supplementary witnesses

The final source review of `31b9503d3` found raw calls to the new
`NativeObjectFieldTarget` callbacks. A foreign receiver could throw outside a
boundary during field selection, or inside a healthy caller's firing update and
quarantine the wrong owner. Field classification, grounded capability,
displacement and powered attack dispatch now use the existing
`ObjectCallbackDispatch` with the exact receiving object. Nested
`ModFaultBoundary.CallbackAborted` propagates unchanged: the receiver owns
quarantine, the current frame stops, and host recovery discards unpublished state
and returns to the title. No blanket callback-availability precheck was added;
VM-fatal exceptions still escape without quarantining an owner.

Six registered-owner regressions reproduced five failures before the repair
(four wrong-owner aborts and one uncontained field-selection exception). The
corrected six now pass without errors or skips. The first combined repair run
also passed all eight existing engine abort/recovery cases and five healthy
Second Strike composition cases, but exposed two new fixture omissions: a null
registry windowing strategy and a destroyed receiver not actually retired from
the manager. Only those fixture omissions were corrected before the final
six-case run. The fixtures use a small synthetic ROM and do not certify native
ROM behavior. The compiled 20,897-line API export remains byte-identical to its
pin (SHA-256 `d997ec1568bea5fa83087421a74510276898c34f30af9c1e8d809dc4bef7eb0c`).
The coordinator resumed the same private tree after the implementation task hit
provider capacity; no duplicate build or replacement implementation was started.

The `expansion-31b9503d3` captures are supplementary positioned witnesses, using
offscreen rendering, original ROM bytes and offline PCM with no desktop input.
The guard's flame charge and the plant's broad seed fan are visible; both short
routes later die and are not completion evidence. Blue Sphere records 1,143 rows
through twelve turn gates and a native return without an emerald. Gumball records
593 rows, three springs consumed and a native return with lives increasing from
three to four. A deliberately short Blue Sphere input fails with an incomplete
summary instead of claiming completion.

Cold and checkpoint AIZ2 positioning were rejected as Caterkiller witnesses:
native apparent-act-one events clamp the player to `$F50`. A separate 180-row
inherited-fire continuation explicitly sets apparent act zero before native
events, retaining physical act one, actual fire placements, the native factory
and ordinary GameLoop. It observes twelve linked bodies, a 116-pixel span at row
136 and 144,000 stereo PCM frames. This declared supplementary setup is neither a
cold-entry nor a whole-route qualification. These captures precede the receiver
containment repair; subsequent strict artifacts and change-based smoke validation
are recorded below rather than inferred from those captures.


### Completed smoke and narrow architecture repairs

The one frozen `60223c39ba81a32c7f272afe40233ca7d591f392` invocation,
`20261014T151115Z-e5464144`, completed all 66 ordinary smoke batches: 3,264
selected candidates, 3,234 reports, 27,294 cases, zero failures/errors and 64 skips.
Ordinary Maven execution took 4,745.08 seconds. Fresh guards completed 680 cases
with eight failures, no errors or skips, in 271.98 seconds. The source fingerprint
remained `2ae721f8a8f7aac6aa30172013dd2f3034d4227a6bfbd4fd312a38b316c57b41`;
no timeout or negative-row omission occurred. Root inspected the eight complete
assertions and all 64 exact skip identities/causes, then acknowledged and deleted
the diagnostics. The cycle assertion's full 4,686-character reported message was
recovered from the runner detail rather than its capped short message.

The eight failures are task regressions, not inherited failures:

- The native Caterkiller preset lived in the mod implementation package, adding
  a game/mod cycle and violating the explicit-contract boundary. It now lives
  with the native badnik consumer; its two enum values and recipes are unchanged.
- The S3K monitor factory violated matching-game placement. It now lives under
  `game.sonic3k.objects`. Native destruction config moves to
  `Sonic3kBadnikConfig.destruction()`, leaving shared effects dependent only on
  their existing factory contracts; this removes the concrete-construction and
  shared-game-dependency failures without changing the native reward sequence.
- The body-to-head reference already restored exactly in adjacent-graph tests,
  but lacked its explicit audit policy. The class-specific `CAPTURED` declaration
  now documents that identity codec; no gap baseline is enlarged. The synthetic
  filler update parameter now names `vIntRunCount`.
- Registered-factory derivative admission moves intact from ObjectManager into
  `RegisteredObjectDerivatives`. The manager's internal registry accessor serves
  derivative admission and rewind recreation. The 3,086-line budget is unchanged.

The 64 skipped cases remain unexecuted coverage: opt-in diagnostics/measurements,
Infinite Sonic route applicability, graphics/EGL preconditions, local reference
availability and a CPZ spin-tube assumption. No missing-ROM skip occurred. No
baseline equivalence is inferred for these skips.

The private intent merge now includes published `88fc03dacd9835437070d40109d62b97a665f944`.
Its sole textual conflict is the runtime lease expiration branch: retain both
producer/content-owner cancellation and upstream's early dead-root cleanup.
New native ring leases have no separate content owner. Host-key registry routing,
preview/browse delegation and fixed-SST forwarding all survive. A composed
attached-effect test then reproduced loss of the new deferred palette replay;
the attached subject now retains that replay while keeping its own part, native
head scale and failure-safe outer-subject restoration.

The actually compiled mutable API snapshot has 20,900 lines, SHA-256
`c1ab9c6d18efc4d8094aabd46c6ebadc4327c75485f4e714d04ad8a608703f05`.
Its private-checkpoint delta is 16 additions/13 removals due to the relocated
unpublished roots; descriptor/runtime version stays candidate `0.7.0`, and no
published pin changes. At this composition checkpoint, final focused behavior, actual artifacts and full
fresh guards were pending; their completed results follow below. Completed ordinary coverage is not
repeated merely for these package/extraction repairs; new overlap risks receive
focused registry, ownership, palette, native-recipe and startup checks.


The composed final focus then passed all 211 cases across the 29 selected classes,
with zero skips. Strict bundled packaging, paired SDK/testkit/Javadoc artifact
verification and the actual exported four-case Jupiter source gate also passed
with zero findings or skips. All original eight guard failures are resolved.
The 681-case fresh guard run exposed one extraction follow-up: the new helper
retained a raw `addDynamicObject` call that was exempt only while inside the
manager. It now admits the already-validated instance through the existing
`createDynamicObject` path, under the same content-owner boundary and native
construction context. Factory rejection still occurs before admission; no budget
or guard exemption changes. Delta lifecycle/ownership checks and another fresh
guard invocation were required to close that checkpoint, as recorded below; the
other completed focus/artifact checks were not repeated as a ceremony.


Final narrow repair qualification passed: the three-class lifecycle focus completed
31 cases, and the complete fresh guard profile completed 681 cases, both with
zero failures/errors/skips. Both Maven invocations used normal admission, Java 25,
the explicit surfaceless-EGL flag, Lua 5.4 and the original three absolute ROM
paths. Focused `verify` also rebuilt the strict bundled example and matching SDK,
testkit and Javadoc artifacts with zero findings. The API snapshot remains
byte-identical at 20,900 lines. Frozen build-input fingerprint
`96911bc13650d9e87145290feffdfb3dd207ec707a7792bf6790b1f27fbd5b56`
was unchanged across both gates; all nine observed blockers are resolved. Root
consumed the exact reports and removed their generated diagnostics.

Develop subsequently published `b649a3c9b87e8708f0428e51b3848c5c3712cdd1`,
adding the shared content importer and its illustrated editor guide. The only
executable path overlap is Engine: this task adds two title-overlay draw lines;
the incoming change adds import-screen ownership and host UI away from that draw
call. The actual main composition preserves both. Its title/import/overlay and
native startup consumers plus affected structural guards passed the bounded
post-integration checks below; the completed ordinary smoke was not repeated.

### Actual-main delivery selection

The normal no-ff merge `a8221fb7d5eb48f5bb199602227ec6ab4bbbf945` composes the exact verified
private `5d45451fa3fc3fe97321cb9fa73bf0c5ab8bc66e` with published
`b649a3c9b87e8708f0428e51b3848c5c3712cdd1`. Git's three-way tree matches
the actual main index/HEAD exactly; no conflict resolution was needed. Both
Second Strike title-overlay lines and incoming import-screen ownership survive.
The local change-based plan selected 3,287 ordinary smoke candidates against the pinned pre-task
`485e89fbea5a2587b763052f0db115986ca681ce` was inspected without executing
another category run, and actual-environment preflight passed. The completed
27,294-case ordinary smoke and final fresh 681 guards cover this task's own
algorithm/API/native inputs; incoming importer qualification is recorded in its
dated design. The bounded remaining composition risk is Engine's title/import
host and the campaign/startup consumers. Accordingly actual main ran the
authorized 14 focused selectors through strict bundled `verify`, followed by a
fresh JVM selecting the four affected architecture/physics guards. This
proportionate selection replaces a broad repeat, without narrowing a category
runner invocation. Any failure, material skip, missing selector or unexpected
input change stops publication. Normal admission and resource budgets apply;
no release-only full suite or extra creator-source qualification is claimed.

### Actual-main results and publication boundary

At main integration commit `a8221fb7d5eb48f5bb199602227ec6ab4bbbf945`, the exact
14 focus selectors ran 15 classes (both packages' `TestSonic3kLevelLoading`)
and passed **132 cases, zero failures/errors/skips**. The S3K startup/loading
subset accounts for 60 of these cases. A separate Maven invocation and fresh
Surefire JVM ran all four requested structural classes and passed **141 cases,
zero failures/errors/skips**: object-physics standardization 33, ArchUnit 31,
architectural review 5 and architectural source 72. Fresh XML class inventories,
Java 25, surfaceless-EGL and all three absolute ROM properties were checked.
There are no main-gate failures to attribute or material skips to excuse.

The focus `verify` produced the actual integrated engine/fatjar, matching SDK,
testkit and Javadoc artifacts. Second Strike strict bundling passed with **zero
findings** and one bundled `second-strike` 0.1.0 entry; the artifact verifier
passed. The actual fatjar's compiled API snapshot is byte-identical to the mutable
20,900-line candidate pin, SHA-256
`c1ab9c6d18efc4d8094aabd46c6ebadc4327c75485f4e714d04ad8a608703f05`.
The unpublished 0.7.0 descriptor/runtime version and published pins are unchanged.

The main build-input fingerprint
`117f5b18a863e8b2ffbe3357d260beb904a9b7066ef0d0ceab89bcb5ff1deca9` (11,570 tracked input files)
remained unchanged across both gates and compiled-pin inspection. Main's five
untracked regular files, three dirty submodules (HEAD/index/diffs/untracked state),
ROM/config bytes and symlink targets retain their initial exact snapshot. The
tested source is this merge; this final evidence update changes only this plan.
It does not rebuild artifacts merely to change their evidence-only commit identity.

Commands used the local official `maven_slot(root, tree_fd=main_fd)` API under
the retained `worktree_build_slot`, with inherited OS descriptors. Each gate
used normal 7 GiB / 8-core admission and released shared admission afterward.
Environment: `JDK_JAVA_OPTIONS`, `JAVA_TOOL_OPTIONS`, `DISPLAY` and
`WAYLAND_DISPLAY` unset; `LUA_BIN=/usr/bin/lua5.4`,
`ALSOFT_DRIVERS=null`. The following render the actual Maven argv with shell quoting. For portable
documentation, `OPENGGF_ROOT` denotes the actual main checkout; the outside
task summary retains the complete absolute argv and light results for the
coordinator. This path substitution represents the same files and run:

```bash
mvn -Dmse=off -B -Dtest=TestSecondStrikeCampaign,TestTitleInputOwnership,TestTitleContentImportAccess,TestContentImportScreen,TestContentImportService,TestContentImportWindowBridge,TestEngineModCallbackAbortBoundary,TestModApiSignatureSurface,TestObjectAttachedEffect,TestSecondStrikePolicyComposition,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils "-Dsonic1.rom.path=${OPENGGF_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${OPENGGF_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${OPENGGF_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" -Dopenggf.headless.gl=surfaceless-egl -Dopenggf.bundled.mods=examples/second-strike "-Dopenggf.surefire.reports=${OPENGGF_ROOT}/target/second-strike-main-focus-r1-reports" "-Dopenggf.test.tmpdir=${OPENGGF_ROOT}/target/second-strike-main-focus-r1-tmp" verify
```

```bash
mvn -Dmse=off -B -Pguards -Dtest=TestArchUnitRules,TestArchitecturalReviewGuard,TestArchitecturalSourceGuard,TestObjectPhysicsStandardizationGuard "-Dsonic1.rom.path=${OPENGGF_ROOT}/Sonic The Hedgehog (W) (REV01) [!].gen" "-Dsonic2.rom.path=${OPENGGF_ROOT}/Sonic The Hedgehog 2 (W) (REV01) [!].gen" "-Ds3k.rom.path=${OPENGGF_ROOT}/Sonic and Knuckles & Sonic 3 (W) [!].gen" -Dopenggf.headless.gl=surfaceless-egl "-Dopenggf.surefire.reports=${OPENGGF_ROOT}/target/second-strike-main-guards-r1-reports" "-Dopenggf.test.tmpdir=${OPENGGF_ROOT}/target/second-strike-main-guards-r1-tmp" test
```

Tool preflight was
`python3 tools/testing/run_categories.py --base 485e89fbea5a2587b763052f0db115986ca681ce --preflight`;
the same command without `--preflight` inspected selection only. The compiled
export command was
`java -cp ${OPENGGF_ROOT}/target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar com.openggf.mods.code.ModApiSignatureSurface --snapshot`.

Focus admission waited 745 seconds; Maven reported 1:35 execution. Guard admission
waited zero reported seconds; Maven reported 36.848 seconds execution. These
measurements are separate from source integration, inspection, documentation and
cleanup time. Earlier private ordinary smoke retains its 64 documented
unexecuted skips; no equivalence is inferred for them. The incoming importer's
ordered editor-presentation/loading assertion is recorded in its own design and
was not silently classified or rerun here. This is bounded main-composition
qualification, not another ordinary/full-suite pass, cold harder-campaign clear,
native-image portability or physical-speaker qualification.

After this evidence-only commit and normal hook/push policy checks, publication
targets only `origin refs/heads/develop`. No feature branch, tag or master
publication is requested. Accounted task-tree removal follows a verified push;
unknown Starpost save bytes are preserved outside the lead tree, and all main
save copies and bootstrap targets remain untouched.
