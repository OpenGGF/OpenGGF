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

| Context | Evidence available at this checkpoint | Remaining qualification |
| --- | --- | --- |
| Native Sonic input/motion, dry/signed gravity, springs and local fluid | 31 early focused input/consumer/fluid tests passed on `bbe132405`; final input publication, deadline and actual history replay cases compile | Final nine-class and ROM/native checks; complete affected character/team routes |
| Native actors, projectiles, fields, challenges, transport and relocation | Real native consumers and bounded actor/descendant/field ownership implemented; first focused inactive Bloominator defect repaired | Final 14-class ROM-backed run; all-act placement/transport routes and load/respawn breadth |
| Accepted terrain/solid landings and native spindash releases | Nine of ten exact producer/history cases passed; the sole failure was an added riding pass preceding an original first-contact assertion | Repaired assertion ordering and final consumer run; Knuckles state transitions outside accepted terrain/solid owners remain native |
| Sprite/HUD/scene pictures and private trails | CPU/pixel fixtures and native metadata implemented; first run red and narrow fixes compile | Repaired CPU and surfaceless-EGL run with exact skips; composed native-frame captures |
| Prepared wrong-zone scenery | Real supplied-ROM backgrounds AIZ1/HCZ1/LBZ1/DEZ1 at 320/400 pixels and five camera offsets passed without skips | Other donor indices remain unsupported and are not sampled; complete gameplay routes are separate |
| Native audio arbitration and cues | 37 runtime/real-PCM/rewind-suppression/observer cases passed on `463cda8e7`, without skips | Eight access cases after correcting S3K stop-command assertion; actual outer-E4/source-history keyframe ordering |
| Catalogue, scheduler and ROM sidegames | 11 early model cases; real-ROM art case; packaged registration and actual Pong reward cases passed, validator zero findings | Final receipts/scenery/duration checks and assembled controller/menu captures |
| Native title/save flow and Chaos menu | Mod preserves native providers; capture drawing now includes data select and hosted overlays above fade | Actual authored-input title/configuration/save-selection captures and framebuffer regression |
| Stock S3K loading/bootstrap/decoding/AIZ1 | Required identities are part of combined validation and use verified absolute ROM paths | Completed selected smoke/domain results, with skips inspected |

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
