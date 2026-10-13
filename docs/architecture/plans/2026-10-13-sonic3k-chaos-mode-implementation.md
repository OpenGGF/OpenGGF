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
| Native Sonic input/motion, signed gravity, springs and local fluid | `b3962897c`: nine classes/372 tests and eleven native/ROM classes/114 tests, no failures/errors/skips; includes actual DELAY/WorldPolicyHistory replay and authoritative audio producers | Root flat-floor jump-gate correction and final union checks; complete affected character/team routes remain separate |
| Native actors, projectiles, fields, challenges, transport and relocation | `d8626bd90`: 141 cases outside the runtime fixture pass without skips, including inactive Bloominator, ROM ghost/assets and native rewind | The 19 runtime cases await fixture-only `bfe1fa983` rerun; root last-ring/load-boundary fixes; all-act placement/transport breadth remains separate |
| Sprite/HUD/scene pictures and private trails | `1b244ab4d`: 17 HUD, five geometry and six actual production surfaceless-EGL cases pass without skips, at native/wider viewports | Ten runtime/private-bank cases on numeric-owner repair `2538ca2cc`; root decode/preview preservation; composed captures |
| Prepared wrong-zone scenery | Real supplied-ROM backgrounds AIZ1/HCZ1/LBZ1/DEZ1 at 320/400 pixels and five camera offsets passed without skips | Other donor indices remain unsupported and are not sampled; complete gameplay routes are separate |
| Native audio arbitration and cues | `463cda8e7` executable plus test-only `f1c729946`: 45 distinct runtime/access/real-PCM/rewind cases pass without skips | Actual outer-E4/source-history keyframe method and root decode/preview preservation |
| Catalogue, scheduler and ROM sidegames | Early model and real-ROM art checks; packaged registration/Pong reward and final receipt/scenery/duration methods passed; validator zero findings | Updated roulette metadata/options guard and assembled controller/menu captures |
| Host ownership and pack assembly | Foundation `6ce6cd2a4`: 56 final observer/lease/service/API cases pass without skips. Root `fd777df75`: three actual assembly cases plus nine API cases pass without skips | New assembled decode/preview regression and updated-develop union checks |
| Native title/save flow and Chaos menu | Root `3fa5ca44e`: fourteen capture args/native title/data-select/hosted pixel cases pass without skips | Explicit presentation-pacing capture callback and authored menu/effect captures; canonical captures cannot qualify observer scheduling |
| Stock S3K loading/bootstrap/decoding/AIZ1 | Required native identities passed with verified absolute ROM on `b3962897c` | Final union smoke/guards and exact skips |

## Delivery checks

- [ ] Observer and lease tests cover owner isolation, faults, expiry, replacement,
  one-shot acknowledgement, empty support, load retirement and capture/restore.
- [ ] Scheduler tests cover empty/small pools, 90/9/1 rarity, downward-only
  fallback, repeat reduction, sampled bounds, revision admission and rewind.
- [ ] The actual packaged mod registers all 105 choices within 32 definitions,
  preserves native title/save flow and renders the available/selected lists,
  interval, duration multiplier, countdown and effect instructions.
- [ ] Each effect reaches a real host or mod consumer in an eligible context.
  Availability and registration counts alone are not behavior evidence.
- [ ] Motion checks cover input history/queues, native gravity pads plus expiry,
  required-route spring minima, fluid entry/exit and captured forward replay.
- [ ] Native checks cover art readiness, graph/slot budgets, actor opt-in,
  shield/defeat/targeting precedence, rewards, occupied-solid expiry, relocation
  sensor/camera/team reconciliation and the native gust dispatch clock.
- [ ] Presentation checks cover native SAT/DPLC identity, collision unchanged,
  supported viewports, paired HUD values, world-only transforms, trails and
  resource/lifetime cleanup. Headless/offscreen captures inspect real output.
- [ ] Sidegames prove their independent deadline, input ownership, optional
  selection, rewind, bounded rewards and immediate safe cancellation.
- [ ] Native S3K loading/bootstrap/decoding and AIZ1 checks remain green, with
  original ROM identity and absolute ROM paths so required checks do not skip.
- [ ] Inspect change-based smoke selection against the pinned destination and
  run actual tool preflight before its bounded run. State class count, expected
  cost and stopping rule. Attribute disputed failures with matched tests on the
  pinned base; do not repeat unchanged suites as a ceremony or use full-suite.
- [ ] Update API descriptor/version/signatures together, creator documentation,
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

The updated combined plan selects 3,194 smoke classes and separate guards against
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
