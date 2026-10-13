# Sonic3K Chaos Mode implementation

Implement the [55-effect design](../designs/2026-10-13-sonic3k-chaos-mode.md)
and [50 additional candidates](../research/2026-10-13-chaos-effect-shortlist.md)
as one maintained S3K mod. The implementation starts from `develop` at
`10db9f00523d2eab79e065bdfc764cb2253781c5`. Additional IDs are 56–105 in
shortlist rank order. Stream voting remains a future extension.

The mod owns its catalogue, pool preferences, scheduling, UI and sidegames.
The host owns typed policy admission, native consumers, resources, lifetime,
fault containment and rewind. Ordinary S3K gameplay retains its stock owners
when Chaos is disabled. The native title and save-selection flow remain available.

## Ownership and dependencies

All workers use separate worktrees based on the pinned destination. No worker
switches the main branch or pushes its task branch. The coordinator integrates
and pushes only `develop`; unknown files and dirty reference submodules remain.

| Worker | Worktree | Owned deliverable |
| --- | --- | --- |
| Coordinator | `.worktrees/sonic3k-chaos-mode` | Pack assembly, shared contract reconciliation, documentation, combined verification and delivery |
| Runtime | `.worktrees/sonic3k-chaos-runtime` | Stock observer, transient lease facade, policy composition, fault/lifecycle/rewind plumbing, shared API signatures |
| Catalogue | `.worktrees/sonic3k-chaos-catalogue` | `examples/sonic3k-chaos-mode`, all 105 choices, preferences, scheduler, title/pause UI, HUD and five optional sidegames |
| Native | `.worktrees/sonic3k-chaos-native` | Native encounters, actors/projectiles/fields, rewards/challenges/power-ups, coherent relocation and transport |
| Motion | `.worktrees/sonic3k-chaos-motion` | Native movement/input, signed gravity composition, spring responses and isolated fluid context |
| Presentation | `.worktrees/sonic3k-chaos-presentation` | World/sprite/HUD transforms, trails, ROM scenery and visual qualification |
| Audio | `.worktrees/sonic3k-chaos-audio` | ROM music arbitration, native landing/spindash cues and captured audio ownership |
| Capture | `.worktrees/sonic3k-chaos-capture` | Shared native save-selection launch owner and fresh headless patch resolution |

Motion, Presentation and Audio are app-owned delegated children. The other workers
are native children of this conversation. No external CLI sessions or top-level
conversations were created.

Contracts precede consumers. Workers first build independent algorithms and
captured models, then consume the shared foundation. Central capability/permit
changes belong to Runtime; individual typed policy classes belong to their pack.
`AbstractPlayableSprite` power-up overloads belong to Native, while its isolated
fluid-context changes belong to Motion; those patches are reconciled by intent.
Each worker records actual commands, failures and remaining qualifications.

## Shared runtime contract

- `GameplayObserver` receives immutable `GameplayObservation` at admitted
  presentations, executed native ticks and lifecycle boundaries. It does not
  replace the stock frame controller or receive a mutable world.
- `TemporaryMutators` is an engine-minted, callback-scoped owner facade.
  `activate(localDefinitionId, options, durationPresentations)` admits only this
  owner's registered LIVE family; `replace(id, options)` retains its expiry,
  and `cancel(id)` removes only its contribution. Duration zero is a native
  one-shot opportunity, not a zero-tick-frame timeout.
  An instantaneous activation contains exactly one native action; independent
  cross-consumer instant bundles cannot share one lease acknowledgement.
- Internal `TransientMutatorPolicySource.Entry` contains owner, activation ID,
  typed policy, age, remaining presentations and one-shot state. Native owners
  acknowledge accepted one-shots and report bounded instructions/results.
- `CompositeWorldSessionPolicyState` gives engine pack runtimes stable per-world
  service, lifecycle and capture/restore ownership. No process-static map keeps
  gameplay state alive after world retirement.
- Existing gravity, head, stealth, pacing, ring-spill and rebound policies
  compose with transient contributions without changing persistent preferences.
  Factories and observer callbacks retain the verified creator fault boundary.
- Active-presentation clocks exclude menu/focus holds. The optional sidegame
  clock advances while its stock-world hold is active, and pauses for real
  menu/focus holds. Its maximum is 20 seconds. Neither clock uses GPU draws.
- Accepted decisions and presentation timing participate in live rewind,
  including zero/multiple native ticks, without consuming the user settings
  journal or obtaining gameplay authority from trace comparison rows.
- `nativeArt()` reuses the bounded, read-only ROM scene-art port. Sidegames
  cache native imagery and do not use generated artwork as a fallback.
- `complete(id, won)` closes an owned modal activation. A declared
  `RingEconomyPolicy(COMPLETION_REWARD, amount)` permits a captured, bounded
  native completion grant; duplicate completion cannot grant rings twice.
  No Rings retains authority, and fault/death/load removes pending grants.
- `outcomes()` exposes at most 32 captured owner-scoped terminal reports, even
  after acknowledgement or final-tick expiry. Receipts retain no policy or family
  slot. The controller observes each result once, so an old success cannot renew
  its respite every presentation or regrant a native reward.

## Complete effect allocation

The catalogue maps all 105 stable IDs exactly once into 27 parameterized
families; the 32-definition cap remains unchanged. These families define fixed
capabilities, not freely extensible creator callbacks.

| Family | IDs |
| --- | --- |
| Head envelope | 1, 2 |
| Player stealth | 3 |
| Sprite presentation | 4, 10, 37–40 |
| Scene presentation | 5–9, 97–99 |
| HUD presentation | 31–36 |
| Dry gravity | 11, 12 |
| Signed gravity | 23, 24 |
| Player motion | 13–15, 22, 72, 73, 83, 91 |
| Input control | 18, 19, 71, 90 |
| Pacing | 16, 17, 56 |
| Hurt scatter | 20 |
| Defeat rebound | 21 |
| Object fields | 41–47 |
| Spring response | 48, 49 |
| Ring economy | 50, 62, 75, 100 |
| Native encounter | 51, 52, 54, 55, 60, 61, 64, 70, 80, 87, 88 |
| Enemy rules | 53, 57, 58, 65, 77, 86, 89 |
| Native power-ups | 63, 67–69 |
| Transport | 78, 79, 81 |
| Relocation | 25–30 |
| Challenge | 59, 66, 76, 92, 93 |
| Audio | 94, 95 |
| Simulated chat | 96 |
| Pong with native completion reward | 101 |
| Sidegames | 102–105 |
| Fluid context | 82 |
| Projectile rules | 74, 84, 85 |

## Affected-context matrix

This is a mechanic and presentation matrix, not certification of complete stock
acts under Chaos. Existing act/character/team/respawn and whole-route rewind gaps
remain inherited. Cross-game donors are deliberately unavailable. Native target
whitelists, safe placement, ordinary control ownership and route minima qualify
individual effects; the displayed pool does not override those gates.

| Context | Completed evidence and tested worker SHA | Remaining qualification |
| --- | --- | --- |
| Native Sonic input/motion, signed gravity, springs and local fluid | `b3962897c`: nine classes/372 tests and eleven native/ROM classes/114 tests, no failures/errors/skips; includes actual DELAY/WorldPolicyHistory replay and authoritative audio producers. Root flat-floor qualification passed at `da3e5a768` | Final union checks; complete affected character/team routes remain separate |
| Native actors, projectiles, fields, challenges, transport and relocation | 160 distinct cases passed without skips across executable `d8626bd90` and fixture-only repairs `bfe1fa983`/`40bc4e012`; includes ROM ghost/assets, exact descendants and native rewind. Root last-ring and wrapped native-gust regressions passed | Final union checks; all-act placement/transport breadth remains separate |
| Sprite/HUD/scene pictures and private trails | 17 HUD, five geometry and six actual production surfaceless-EGL cases passed at `1b244ab4d`; ten runtime/private-bank cases passed at `2538ca2cc`, all without skips. Root nonempty-bank decode/preview preservation passed | Final union checks and complete composed visual breadth |
| Prepared wrong-zone scenery | Real supplied-ROM backgrounds AIZ1/HCZ1/LBZ1/DEZ1 at 320/400 pixels and five camera offsets passed without skips | Other donor indices remain unsupported and are not sampled; complete gameplay routes are separate |
| Native audio arbitration and cues | `463cda8e7` executable plus test-only `f1c729946`: 45 distinct runtime/access/real-PCM/rewind cases passed without skips. Root `12b220f5e` actual outer-E4/source-history keyframe method and `da3e5a768` decode/preview preservation also passed without skips | Final union checks |
| Catalogue, scheduler and ROM sidegames | Early model and real-ROM art checks; packaged registration/Pong reward and final receipt/scenery/duration methods passed; validator zero findings. Root roulette metadata/options guard passed | Final union checks |
| Host ownership and pack assembly | Foundation `6ce6cd2a4`: 56 final observer/lease/service/API cases pass without skips. Root `fd777df75`: three actual assembly cases plus nine API cases pass without skips; assembled decode/preview preservation passed at `da3e5a768` | Updated-develop union checks |
| Native title/save flow and Chaos menu | Root capture args/title/data-select/pixels and production presentation callback passed. `3fbc29d15`: 46 distinct affected Engine/GameLoop/save/package and actual-ROM cases passed without skips. Root `5f724050b` captured title configuration, native No Save/Sonic selection and AIZ gameplay | Headless hub return remains unsupported; full interactive window route breadth is separate |
| Stock S3K loading/bootstrap/decoding/AIZ1 | Required native identities passed with verified absolute ROM on `b3962897c` | Final union smoke/guards and exact skips |

## Delivery checks

- [x] Observer and lease tests cover owner isolation, faults, expiry, replacement,
  one-shot acknowledgement, empty support, load retirement and capture/restore.
- [x] Scheduler tests cover empty/small pools, 90/9/1 rarity, downward-only
  fallback, repeat reduction, sampled bounds, revision admission and rewind.
- [x] The actual packaged mod registers all 105 choices within 32 definitions,
  preserves native title/save flow and renders the available/selected lists,
  interval, duration multiplier, countdown and effect instructions.
- [x] Each effect reaches a real host or mod consumer in an eligible context.
  Availability and registration counts alone are not behavior evidence.
- [x] Motion checks cover input history/queues, native gravity pads plus expiry,
  required-route spring minima, fluid entry/exit and captured forward replay.
- [x] Native checks cover art readiness, graph/slot budgets, actor opt-in,
  shield/defeat/targeting precedence, rewards, occupied-solid expiry, relocation
  sensor/camera/team reconciliation and the native gust dispatch clock.
- [x] Presentation checks cover native SAT/DPLC identity, collision unchanged,
  supported viewports, paired HUD values, world-only transforms, trails and
  resource/lifetime cleanup. Headless/offscreen captures inspect real output.
- [x] Sidegames prove their independent deadline, input ownership, optional
  selection, rewind, bounded rewards and immediate safe cancellation.
- [x] Native S3K loading/bootstrap/decoding and AIZ1 checks remain green, with
  original ROM identity and absolute ROM paths so required checks do not skip.
- [x] Inspect change-based smoke selection against the pinned destination and
  run actual tool preflight before its bounded run. State class count, expected
  cost and stopping rule. Attribute disputed failures with matched tests on the
  pinned base; do not repeat unchanged suites as a ceremony or use full-suite.
- [x] Update API descriptor/version/signatures together, creator documentation,
  example instructions, configuration and affected route/support matrices.
- [ ] Review high-risk owner/rewind/native boundaries, fix attributable defects,
  integrate into the updated main branch, verify policy, push and account for
  every worker change before removing fully merged task trees/branches.

## Evidence and decisions

The initial inventories confirmed that LIVE was menu-resume admission and
that creator HUD profiles were limited to owned zones. The implementation
therefore extends the neutral observer, transient admission and native HUD
presentation boundaries. It does not take over `GameplayFrameController`,
restore checkpoints to simulate teleportation, or restore old gravity/shield
snapshots at expiry. Source inspection identified native Cluckoid pressure,
Hyudoro context, power-up duration and Tails carry ownership as specific reuse
sites; the worker checks must establish their qualified runtime behavior.

The common contract landed at `7ec4bfa0d`. Motion consumers landed on the
integration tree at `b8a35ab91` from worker checkpoint `eeb3b5220f`; bounded
power-up grants followed at `e50e8220c` from `0f1b59f2e`. These are implementation
checkpoints, not passing validation claims. Motion and power-up production and
test sources compiled in their respective worker trees; focused checks were still waiting
for shared test admission when these checkpoints were integrated.

Private trail atlases replace reuse of live DPLC addresses: queued GPU draws
otherwise see the last uploaded pose at every historical address. Spring
weakening retains native minima at unreviewed upward/horizontal launches;
weak downward launches and explicitly qualified route minima are supported.
The local fluid context remains a captured circle shared by movement and
target queries; converting it to a global water plane was rejected because
it would alter native water history and unrelated actors.

The callback/factory review identified reentrant admission, bonus-stage authority
and abandoned pending cleanup history as concrete risks. The runtime worker
reproduces these before changing the contract. Sidecar history is armed by the
live rewind owner; disabled and canonical paths must not accumulate cleanup
journals. Public boundary documentation and actual revocation order must agree.

Native one-shots receive a captured opportunity deadline: 180 qualified native
ticks or 360 active presentations; Budding Badniks has 600/1200. Accepted object
tails keep their independent bounded lifetime after acknowledgement. Motion
momentum/ground-stop actions instead allow 120 admitted native ticks or 180
active presentations. An unqualified motion deadline reports
`Failed: no qualified motion opportunity before deadline` and acknowledges once.
Pause, held gameplay and rewind do not consume native attempts.

Actor fields capture exact native identities. Each activation selects at most
four badniks or six projectiles, with eight/twelve total acquisitions including
replacement. Stable slot ordering resolves equal distance, and target-loss
hysteresis retains identities within 256×160 pixels. Per-object displacement
stays bounded and proposed badnik movement retains native body clearance.

Shield, ring and bounded shoes/invincibility grants are consumed native actions.
Removing their source lease prevents further work but does not undo a completed
grant or shorten a longer stock timer. Native grant timers follow native ticks;
temporary lease/scheduler deadlines follow active presentations. This avoids
restoring stale inventory or music state after another native owner has acted.

Observed focused evidence so far: runtime `c4fd7a088` passed 10
`TestTemporaryMutatorState` tests and a separate 74-test history/services/pacing/
fault/API batch, without failures, errors or skips. Motion `bbe132405` passed 31
input/consumer/fluid tests without skips. Catalogue models passed 11 tests before
the two final host-admission/Pong assertions were added; its actual supplied-ROM
art check passed once without skips. Follow-up fixes and assembled behavior still
require their own completed verification. Presentation `ccb70c8c` compiled, but
its first focused run was red and is being repaired. Native's first 50-test run
had a new inactive Bloominator helper error; that regression blocks delivery
until its corrected stock path is verified. Audio `463cda8e7` completed 45 cases without skips: 37 unaffected cases passed;
eight access cases require a rerun after a new test expected fade for native S3K
stop command `0xE0`. Existing native command-semantic tests corroborated correcting
the assertion without changing executable audio. Presentation repair `1b244ab4d`
and Motion history/assertion repair `b3962897c` compile and remain unverified.
The combined jar builds and passes package validation with zero findings on
`bc5cdecfd` plus the coordinator assembly, before those final repair picks.

The offscreen renderer previously skipped hosted overlays and rendered data select
through the level branch. Source comparison with Engine found that queued hosted
canvas rectangles also lacked a same-frame flush after fade. The coordinator adds
the native data-select draw branch and the same post-fade hosted pass; the pass
flushes its rectangles and is dormant without policies or a hosted run. A real-ROM
title/data-select pixel regression checks the current frame, including drawing
without framebuffer readback. No desktop input or display is used.

Checkpoint qualification rejected layout-number inference. Native high-water marks
can include unseen alternate-route or duplicate-number posts. `312c7a01d` instead
captures up to 32 exact native saved coordinate banks observed before/after native
ticks; assembly clears uncertain history and the current native bank seeds it
conservatively. Source/tests compile; its unchanged focused request still awaits
execution.

The descendant audit found that monitor free children and transferred-slot broken
wall pieces bypassed the first child hook. `a2e83ea77` extends the existing internal
notification to structurally owned free/anchored/legacy children and wall piece
zero. Removed producers remain graph anchors for their original bounded lease;
expiry and owner retirement reach exact descendants. Explicit independent siblings
retain their own lifetime. Source/tests compile; behavioral verification is pending.

The updated combined plan selects 3,198 smoke classes and separate guards against
`10db9f005`. The launch uses verified ROM-directory discovery, Lua 5.4 and production
surfaceless EGL with desktop display variables unset. Historical large-category
holds are about 64–91 minutes, so the invocation allows 120 minutes excluding
queue wait, with a ten-minute no-output stopping rule. Exhaustive slow-suite and
release-only full-suite tests remain excluded. The earlier broad request was
canceled while waiting and never executed. Updated develop `4a9bec1ea` merged
cleanly at `ad98d0dcd`; its local runner retains the complete smoke selection in
batches of at most fifty candidates and releases admission between them and before
guards. Actual Java 25/Lua 5.4/PowerShell preflight passed again. The next combined
run follows the bounded final-review fixes and corrected captures.
Delivered upstream runtime-rule binding and AIZ ownership repairs at `1ead8d9b4`
also merged cleanly at `9d1fb0456`, preserving the Chaos fluid/input consumers.


The final bounded source audit traced all 105 entries to executable mode paths
across 27 families; this is consumer coverage, not certification of every route.
It found four concrete catalogue/consumer edges: retain the final existing ring
under Leak without manufacturing inventory from zero; describe Roulette's actual
bounded +/-10 change and remove its unused sampled amount; limit Grounded to free,
non-airborne movement at angle zero in floor mode; preserve native position-word
subtraction in Cluckoid pressure across coordinate wrap. `Find_OtherObject` uses
`sub.w` before signed direction and absolute range gates, so clamping the source
coordinate was rejected in favor of the ROM arithmetic. Focused regressions cover
these boundaries; their results remain pending at this checkpoint.

The assembly review also found that decode-only/preview admission preserves the
host lease graph but native/presentation/audio children cleared it. Those consumers
now preserve captured state and exact render/audio ownership on both successful and
failed non-admitting loads. Full assembly continues to revoke the graph. Tests use
an actual assembled source/pack, a nonempty private trail bank and a live ROM music
tail rather than inferring safety from an empty registration list.

Canonical `GameplayCaptureTool` rows call one native step and do not advance
presentation observers. The first title/DVD/gust captures therefore froze the Chaos
countdown and were rejected as effect/menu evidence. Explicit `--interactive-pacing`
reuses the existing production physical-input/presentation owner, preserving the
canonical default and admitting zero/multiple native ticks per presentation. Fresh
owned captures use that flag. No desktop keyboard or ambient display is involved.

The final focused boundary run on `da3e5a768` completed 44 cases: 43 passed,
and the last-ring fixture failed because it had no native inventory owner.
`7a9450ab8` binds the real `LevelGamestate` rather than weakening the assertion;
its exact rerun passed on `61c7aa7be`. The actual audio-history fixture also needed
the live owner's explicit history-recording admission. After that admission,
the first real E4 check disproved an immediate physical music-slot restoration
assumption: native intent changes immediately, while the producer drains the
restore command on its next outer packet. `12b220f5e` checks that native ordering,
admits a new jukebox and accepted landing in the gap, and proves the new cue wins
after the queued restore. The exact history method passed with no skips, including
source/runtime and native audio keyframes, retained zero-native-tick clocks and
unchanged diagnostic/command counts under rewind suppression. These results give
45 distinct passing root boundary cases with no skips; they are focused evidence,
not a combined smoke result.

The root commands used the verified S3K ROM, unset desktop display variables and
`LUA_BIN=/usr/bin/lua5.4`. The boundary selector included capture argument and
production presentation-callback checks, last-ring preservation, free flat-floor
jump admission, assembled decode/preview preservation, nonempty trail ownership,
native audio preservation, wrapped Cluckoid gusts and every catalogue entry's
bounded semantic preparation. The two repaired exact selectors were
`TestNativeMutatorRuntime#ringLeakRetainsTheLastRingAndDoesNotRestoreSpentInventoryOnExpiry`
and
`TestAudioMutatorHistory#naturalOneUpBetweenTicksAndAcceptedAudioReplayAcrossKeyframes`,
through `tools/testing/maven_queue.py -Dmse=off -Dtest=... test -B`.

Fresh 1,000-presentation supplied-ROM captures completed after the interactive
pacing fix. DVD HUD pictures at presentations 650 and 800 show the native plaque
at different positions while its lease and next-effect clocks advance. Windy Day
moves Sonic from native centre X=5024 to X=5184 between presentations 600 and 650
with neutral input, without death. The title configuration capture selects DVD
HUD alone, a five-second interval and the default duration multiplier. Its next
capture reached native data select but could not enter gameplay: the headless
host omitted Engine's data-select action callback. That capture is menu evidence,
not a successful launch claim. The dedicated capture worker is sharing the native
launch owner and preparing fresh mod registrations for the selected team, rather
than forcing a mode flag or reusing retired callbacks.

The later shared launch owner preserves Engine's native slot payload, selected
team, progression restore and save reason. A capture-owned jar loader creates a
fresh registration transaction and fault boundary per exact launch request;
retired observers are not reused. `3fbc29d15` passed 46 distinct affected checks
without skips, including actual-ROM No Save/new-slot confirmations and a packaged
Knuckles-specific activation despite Sonic title configuration. Two generated
fixture prerequisites were repaired before its final pass: the protected
delegating constructor needs an anonymous subclass, and the observer needs a
declared support profile to receive an actual world-policy close.

Root `5f724050b` completed a fresh 2,822-presentation title capture using native
No Save and Sonic selection. Its CSV changes from TITLE_SCREEN to DATA_SELECT at
838 and LEVEL at 1242; later native AIZ pictures and the advancing Chaos countdown
confirm the fresh gameplay observer. This fresh-settings capture used the default
30-second interval and edited 95-entry pool, unlike the earlier deliberately
seeded five-second/DVD-only configuration capture. No effect activation is claimed
from its shorter gameplay portion. The separate controlled DVD/gust captures
remain the effect evidence. Capture inputs, settings and pictures live in the
task's explicit directory outside the repository. The complete smoke/guard run
remains pending.


The first complete combined invocation on `0047d2cb1` finished all 64 ordinary
batches: 26,837 tests, 28 failures, one error and 64 skips in 4,180.19 execution
seconds. Its separate guards finished 678 tests, eight failures, no errors/skips
in 266.04 seconds. This is a completed red smoke/guard result, not a green suite.
The unchanged motion fixture lacked live history admission and the rewind inventory
needed the three new concrete encounter classes; their test-only repairs landed at
`d3d0b7db2` and `f01f42aa9`. The guard failures exposed global HUD/history lookup,
render/input layer coupling, three class-size ratchets, native-clock terminology,
and a pre-registration scanner treating an injected manager collaborator as an
object. These were repaired through exact world ownership, pure render values,
focused timer/presentation collaborators and object hierarchy analysis. Frozen
layer/global baselines and size budgets were not widened. Explicit Mod API policy
and UI values are enumerated in the cycle guard; a new unfrozen assertion rejects
concrete mod-host dependencies. Observer snapshot/configuration projections,
native launch error translation and shared ROM mapping pixels remove those
implementation dependencies while retaining host fault/identity authority.

The consolidated repair selector completed 391 tests across 35 class reports with
zero failures/errors/skips on `f01f42aa9` plus the bounded repair patch. Every selected
XML report was inspected separately from seven stale reports left by earlier
focused commands. It covers every originally failing guard, actual surfaceless EGL
pixels, native launch/team/save behavior, observer/source history, motion/timers,
and all five mandatory S3K loading/bootstrap/decoding/AIZ classes. The exact command
used `tools/testing/maven_queue.py -Dmse=off -Dopenggf.headless.gl=surfaceless-egl
-Dtest=... test -B`, all three verified absolute ROM properties, Lua 5.4 and unset
desktop display variables. Unchanged broad smoke coverage is not repeated as a
ceremony; remaining route attribution and the later updated-develop union remain
separate delivery checks.


Final attribution compared clean baseline `1ead8d9b4` with frozen Chaos `0047d2cb1`:
all remaining 26 failures and one error are pre-existing in this bounded comparison.
The MHZ boss fixture's missing gameplay owner and two late-pulley route failures
match exact identities/types/assertions; the DEZ/LRZ/MHZ selector ran 17 cases,
all failing identically, and the SSZ selector ran eight cases with seven identical
outcomes and no errors/skips on both trees. Twenty-six of the 27 failing/error
cases have exact raw assertion/detail equivalence. SSZ Tails teleporter case `[2]`
requires semantic comparison: replay 4018 misses children 125/126 and shifts slots
128/129 on both trees, while printed compact blob hashes differ between JVMs.
An identical temporary diagnostic retained the original assertion/input and read
actual blobs before failure. Jet schema 15 has scalar bytes
`01050000000b0500001c090000`; gun schema 16 has
`ffffffff011b050000fc080000`; both opaque arrays are empty and these captured
contents match exactly. Each blob hash delta equals the real Class hash delta
multiplied by 961. Jet classfile bytes match; gun bytes differ at the owned firing
hooks, whose empty-port paths preserve native firing/cooldown. Child recreation,
parent restoration and parent-null deletion are unchanged. This supports semantic
inherited-state attribution, not raw-text or classfile equality. Both fixtures were
restored byte-for-byte and both attribution trees are clean, with no live jobs.
The diagnostic source and raw logs are temporary; no trace row supplied gameplay.

The 64 ordinary skips are 23 opt-in diagnostic/soak/property cases, 32 InfiniteSonic
cases without the requested native targets, six legacy GLFW-null/EGL-window context
checks, one CPZ tube fixture admission assumption, and two external audio-reference
opt-ins. No missing/wrong-ROM skip was recorded. The supplied-ROM properties were
identical across every batch. Actual Chaos production surfaceless-EGL pixel cases
passed separately; those results do not certify the skipped legacy helpers.

Develop advanced independently to `5d9dd81381b` with Second Strike. Integration at
`312979cc5` retains both changelog entries, the observer snapshot projection and
new decoded-placement ownership. The candidate signature pin is exactly the sorted
union of both parents: 20,835 lines, all 22 incoming signatures retained, zero
removals; the descriptor stays unpublished `0.7.0`. Current compiled signature
checks pass. The actual final union passed 197 cases/22 classes, 26 legacy patch/
owner cases/two classes and 69 native graph/reload/policy/SDK cases/ten classes,
all without failures/errors/skips. Commands use the wrapper with exact selectors;
the last invocation uses `verify` and completed SDK/Javadoc/binary artifact checks.
The three selectors cover all eight incoming ordinary test classes plus affected
Chaos history/assembly/capture and required S3K consumers. Fresh `-Pguards` then
passed 680 cases/88 classes, zero skips, in 4:23. These are bounded focused and guard
results; no unchanged broad or release-only full-suite run was repeated.
Second Strike's authored enemies/projectiles have no native Chaos field/firing
opt-in; combined-mod effect breadth against them is not certified by these checks.

`python3 examples/sonic3k-chaos-mode/build.py` completed on `312979cc5`, and the
actual ROM-free mod package has zero validation findings. The 80,806-byte jar's
SHA-256 is `954b580e8566cca2803bc8e11d59f229e41f1b4559a64797e63e1e6877165ba1`.
Useful captures and the jar are retained in the explicit external task directory;
consumed combined-run diagnostics were acknowledged and deleted. Integration push
and fully accounted worktree cleanup follow this evidence checkpoint.


The completed combined command on `0047d2cb1` was the following, with
`OPENGGF_ROM_ROOT` resolving to the main checkout's existing verified ROM files:

```sh
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  MAVEN_ARGS=-Dopenggf.headless.gl=surfaceless-egl \
  python3 tools/testing/run_categories.py \
  --base 10db9f00523d2eab79e065bdfc764cb2253781c5 \
  --rom-directory "$OPENGGF_ROM_ROOT" --max-minutes 120 --run
```

Final-union focused selectors, each submitted once at `312979cc5`, were:

```text
TestNativeDecodedPlacementPatch,TestNativeDecodedPlacementPatchRom,TestNativeDecodedPlacementPatchHost,TestOwnerBoundDifficultyPolicies,TestSecondStrikeBoss,TestSecondStrikeCampaign,TestSecondStrikeEnemies,TestSecondStrikeStages,TestModApiSignatureSurface,TestModContextAndFaultBoundary,TestMutatorWorldServices,TestWorldPolicyHistory,TestSonic3kChaosPackAssembly,TestAudioMutatorHistory,TestEngineDataSelectPatchResolution,TestGameplayCaptureDataSelectLaunch,TestSonic3kChaosModeExampleModels,TestS3kAiz1SkipHeadless,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.tests.TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils
```

The first selector uses `tools/testing/maven_queue.py -Dmse=off -Dtest=... test -B`.
The ROM-free legacy selector uses `--lean -Dmse=off
-Dtest=TestLevelPatch,TestOwnerBoundGamePatch test -B`. The final domain selector
uses ordinary admission and `verify -B`:

```text
TestModApiPinPolicy,TestModApiRuntimePolicy,TestModApiReleasePolicy,TestModApiJavadocTool#canonicalInventoryIsExactSortedAndContainsTheMandatedRoots,TestModApiSdkPackager#mergedCreatorContractTypesStayInCreatorApiClosure+gameSpecificTraceDiagnosticsStayInCreatorApiClosure,TestTemporaryMutatorState,TestNativeMutatorRuntime,TestGameLoopNativeStageMutatorPacing,TestS3kAizMinibossGraphRewind,TestS3kAiz1FireTransitionPreparedReload
```

Both ROM-backed focused commands and final `-Pguards test -B` add
`-Dopenggf.headless.gl=surfaceless-egl`, the three absolute properties
`sonic1.rom.path`, `sonic2.rom.path`, `s3k.rom.path`, unset desktop display
variables and use `LUA_BIN=/usr/bin/lua5.4`. These are the existing root files
listed in the repository ROM identity table, not renamed fixture copies.
