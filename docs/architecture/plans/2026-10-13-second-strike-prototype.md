# Second Strike — normal-campaign difficulty prototype

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
| Meaningful rings | Curated original ring groups and recovery footholds | Real scarcity, usable safe/exposed routes, attainable native bonus entry |
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

Main integration, source-equivalence/build checks, push and accounted worktree
cleanup remain pending. No whole-suite green result or completed delivery is
claimed while those steps remain open.

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

Final destination composition, integration and cleanup facts will follow;
no additional unchanged smoke sweep is required for these bounded follow-ups.
