# Implement Sonic CD with complete regional parity

Date: 2026-10-23. Status: implementation plan, not implemented game support.
Design: [fourth-game architecture](../designs/2026-10-23-sonic-cd-fourth-game.md).
Engine baseline: `b979916808b7f9379ede2b5dcdde1bcdc8bf8357`.
Reference pin: `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`.

Deliver Sonic CD as a stock game alongside S1, S2, and S3K. Complete the USA
release first, then Japan and Europe. USA MVP means the complete native USA
game and its required features. A playable Palmtree Panic demonstration is
an intermediate proof, not the MVP or a full-parity claim.

This task delivers the reference setup, architecture, and execution plan.
It does not start the multi-phase engine implementation or a recurring agent
program. Execute later work through the repository's current development,
verification, integration, push, and cleanup flow. No new release number,
publication date, or public API commitment is assigned here.

## Track work and evidence

- Keep the design's release identity and ownership constraints while filling
  in implementation. Revisit them when a probe disproves an assumption.
- Treat each phase as a bounded outcome, split into independently verifiable
  changes where needed. The phase IDs below are work packages, not created PRs.
- Keep per-zone matrices in the existing architecture/status structure and
  link them from the [coverage backlog](../../status/level-test-coverage.md)
  as implementation starts. Do not create a second completion ledger.
- Record source labels, program domains, user-media fingerprints, command,
  source SHA, input movie, sampling boundary, skips, and first divergence.
  Missing, implemented, executed, passed, failed, skipped, and not applicable
  are separate states. Zero tests or an unavailable disc never means passed.
- Preserve rejected approaches and their kill evidence in the dated design
  or this plan. Keep raw diagnostics temporary under the testing policy.
- Assign each worker an owned file set or worktree, focused checks, and a
  stopping condition. One integration lead owns combined validation.

## Sequence the program

`CD-01` establishes source/media facts and `CD-02` proves the native oracle.
They can progress together. `CD-03` binds release identity before stock
bootstrap work. `CD-04` and `CD-05` establish media and deterministic CD
services. Audio integration can continue alongside later gameplay work. Complete the
Palmtree Panic and special-stage proofs before distributing later zones.

The main dependency chain is:

```text
CD-01 + CD-02 -> CD-03 -> CD-04 -> CD-05
CD-05 -> CD-06 audio/video -> CD-07 bootstrap/save -> CD-08 player
CD-07 + CD-08 -> CD-09 time travel
CD-05 + CD-06 + CD-07 + CD-08 -> CD-11 special stages
CD-09 + CD-11 -> CD-10 Palmtree Panic closure -> CD-12 remaining zones
CD-06 + CD-07 -> CD-13 non-level work; CD-12 -> CD-13 demo/mode closure
CD-10 + CD-11 + CD-12 + CD-13 -> CD-14 USA MVP
CD-14 -> CD-15 Japan, CD-16 Europe -> CD-17 all-region closure
```

Early device/media phases use real-media component and request probes.
Their exits do not require the later complete gameplay routes. Native route,
menu, and whole-game integration checks join at the stated later phases.

Regional inventory and PAL research start in CD-01/CD-02. Region-specific
delivery follows USA. Infrastructure must represent Europe before USA work
hardens assumptions, even while Europe remains uncertified. CD-10 may start
zone work after CD-09, but closes only after the CD-11 round trip. CD-13 can
build menus early, while later-zone attract demos wait for CD-12.

## CD-00. Pin the requested reference

- [x] Register `docs/scddisasm` as an optional submodule from
  `https://github.com/OpenGGF/scddisasm.git`, with upstream `ai-development`.
- [x] Pin `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff` and document initialization.
- [x] Run the reference structural audit. It passed with zero missing
  active includes and zero case mismatches.

Counsel synthesis is recorded in the design. Integration, push, checkout,
and cleanup outcomes are reported with this task's delivery.

The pre-existing local reference has modifications and a nested checkout.
Preserve that whole directory before replacing its main-workspace path.
Never reset it or copy its uncommitted content into the new pin.

## CD-01. Inventory the supported products and source contracts

Owner: media/reference workstream. Dependencies: CD-00 reference pin.

- [ ] Discover user-owned USA, Japan, and Europe images without copying,
  renaming, or downloading commercial media. Establish one exact USA target.
- [ ] Record TOC, track modes, indexes, pregaps, sector layouts, full data-track
  identity, audio-track identity, and disc header. Preserve both container and
  normalized content identity. Never identify a whole disc by ISO data alone.
- [ ] Create release entries with region, revision/pressing, product identifiers,
  execution profiles, and supported/unknown status. Use observed hashes only.
- [ ] Inventory the 70 stage payloads, all modes, demos, special-stage variants,
  object tables, event paths, native saves, and regional conditional branches.
  Resolve aliases, inactive sources, and runtime-selected overlays.
- [ ] Join the 127 file-ID and 215 command declarations with built outputs,
  actual media, and dispatch. Classify executable programs, data/media files,
  aliases, invalid commands, and inactive leftovers. Generate or guard provider
  and test inventories from one reviewed catalogue. Counts alone are not coverage.
- [ ] For each regional difference, identify its owning code, table, file,
  media track, or execution environment. Audit consumers before trusting names.
- [ ] Extend source citation and offset-research tooling for program-local CD
  domains when implementation needs it. Existing cartridge address tools do not
  establish a global CD address space.
- [ ] Inventory residual opaque source spans. Promote a semantic investigation
  only when its consumer blocks implementation. Rebuilding the reference is
  useful corroboration, not a prerequisite for every small engine change.

Exit evidence: a finite release/component inventory with every entry assigned
an owner and disposition. Missing user media remains an explicit prerequisite.

## CD-02. Prove native observation before building against it

Owner: TraceChaser/native-reference workstream. Dependencies: identified media
from CD-01. This work belongs in the external TraceChaser project where appropriate.

- [ ] Verify pinned BizHawk 2.11/core disc support with the exact media and a
  documented native execution environment. Identify any BIOS prerequisite.
  Do not assume cartridge recording infrastructure already supports CD.
- [ ] Capture cold boot to title, title to R11A, a complete warp, a special-stage
  entry/exit, PCM/CDDA playback, an opening-video segment, and save/reload.
- [ ] Establish native main/sub-CPU sampling phases, V-int and simulation clocks,
  disc command/response boundaries, Word RAM ownership, graphics completion,
  PCM cursor and bank state, CDDA position, input lag, and program identity.
- [ ] Repeat identical input captures and insert harmless idle input before
  relevant boundaries. Establish which fields are deterministic and which
  native device timing requires a named environment or bounded contract.
- [ ] Identify actual BIOS hashes and decide the game-visible firmware
  contract before freezing execution identity. Start with the design's HLE
  proposal, certify it against those native BIOSes, and reopen the architecture
  if it fails. Any future runtime BIOS bytes require their own verified source.
- [ ] Classify each timing family as source/device-derived, environment-measured
  under a firmware/drive profile, or unresolved. Specify model inputs and
  independent validation. Recorded readiness is diagnostic for unresolved
  families and cannot close full live parity. Blocking waits stay under the
  existing main-loop admission contract unless another contract is proven.
- [ ] Prove USA, Japan, and Europe capture cadence. Record physical refresh,
  gameplay update counts, native timer changes, audio samples, and movie frames
  independently. A PAL flag or a 50 Hz sleep is insufficient.
- [ ] Extend the sole live v5 trace contract and producer/consumer guards through
  an explicit design if CD metadata or timing kinds need representation.
  Keep physics/aux rows comparison-only. Never import gameplay values or make
  fixture/frame/route names select behavior.
- [ ] If GPGX/headless CD support or observation fails, report the exact missing
  capability. Prove an independent native reference path before continuing
  parity-dependent claims. A reference reimplementation is secondary evidence.

Exit evidence: attributable captures and a recorder/comparator conformance
test. No gameplay owner consumes comparison fields. Oracle limitations stay
visible in every affected acceptance row.

## CD-03. Separate game, release, region, revision, and environment

Owner: stock identity/bootstrap workstream. Dependencies: CD-01, CD-02 findings.

- [ ] Inventory all affected public API types and callers first, including
  both `@ModApi` and `@com.openggf.game.ModApi` spellings. Verify that signature
  generation and guards include them. Do not assume a hook found every type.
- [ ] Implement the design's immutable release descriptors and verified media
  identity, including firmware semantic contract, native environment, and
  drive profile. Represent multi-region cartridges without a World console.
- [ ] Keep logical cartridge composition separate from product/revision identity.
  Bind current S1 World REV01, S2 World REV01, and S3K targets to explicit
  profiles without changing behavior. Preserve unrelated accepted unknown-ROM
  development workflows while keeping them uncertified for parity.
- [ ] Select native code/data/offset tables, timing, language/presentation, and
  audio from the resolved release. Do not scatter region checks through managers.
- [ ] Pin release and environment for the lifetime of a WorldSession. A region
  change opens a new session. Key immutable caches and save/recording metadata
  by compatible release identity. Keep donor identity independent from host identity.
- [ ] Reject a forced known release when fingerprints disagree. Test incomplete,
  ambiguous, altered, mixed-region, and unknown images with useful diagnostics.
- [ ] Derive or guard stock game membership across detection, onboarding,
  `TraceCatalog`, `TraceExecutionModel`, save namespaces, tools, and launch.
  Keep `RomIdentity`/`LogicalRom` as cartridge address spaces, not disc regions.
- [ ] Audit `GameId`, `RomGame`, `LogicalRom`, stock switches, boot paths, tools,
  and serialization consumers, plus `data.Game.getRom()` and its relocation,
  export, audio, and checksum callers. Defer factory/abstract-obligation migration to CD-07's real disc consumer.
  Wrap existing cartridge resolution to attach descriptors without changing
  its factories. Public `GameId.SCD` joins the stock API in CD-07. Use the
  internal catalogue key for recognized but not yet admitted CD media.
  Change the unpublished candidate API only where
  necessary, with descriptor, `ModApiVersion`, and normalized pins together.

Exit evidence: current cartridge behavior and lock-on resolution stay covered.
Identical file names with different content cannot share a certified profile.
Region, revision, media format, and execution cadence remain distinguishable.

## CD-04. Read disc-backed assets through one bounded source

Owner: media/data workstream. Dependencies: CD-03.

- [ ] Implement a read-only disc source with complete CUE/BIN data and CDDA as
  the first canonical input path. Prove multi-file and single-file track layouts.
  Add CHD through an evaluated bounded dependency or reader with its own tests.
  Other containers are separately admitted formats, not a parity assumption.
- [ ] Parse sectors, mode/user-data boundaries, TOC/index/pregap conventions,
  ISO directory/file extents, cross-sector reads, and the game's file table.
  Reject malformed ranges and missing required tracks before launching.
- [ ] Add a bounded native-media alternative to `level.resources.LoadSource`
  without borrowing creator asset authority or fabricated cartridge offsets.
- [ ] Resolve program-aware asset slices. Load graphics, palettes, maps, objects,
  animations, collision, samples, video, and sound sequences from the supplied
  media. Never read the disassembly checkout at runtime.
- [ ] Authenticate required audio and video as well as executable data. A bare
  ISO can be an explicitly limited research input. It cannot pass full USA parity.
- [ ] Keep `Rom` cartridge semantics intact. Migrate stock source call sites
  deliberately. CD `rom()` remains absent, with explicit CD module paths rather
  than a fake cartridge image or a standalone-content exception.
- [ ] Bound decoder output, cached bytes, open files, and worker lifetime. Test
  close/reopen, truncation, cancellation, invalid extents, equivalent containers,
  and source changes after launch preparation.

Exit evidence: known asset bytes match native disc extractions and all runtime
asset provenance is attributable to the frozen user-media manifest.

## CD-05. Model deterministic CD service boundaries

Owner: timing/session workstream. Dependencies: CD-02, CD-04.

- [ ] Implement and verify the pure stamp-device core now, using the title's
  native image/trace requests as its first consumer. Special-stage and D.A.
  Garden game programs consume that core later. Keep their game logic separate.
- [ ] Implement the observed subset of disc command, overlay, transfer,
  Word RAM, graphics, audio-command, and BRAM protocol state machines.
  Decode raw commands inside the CD boundary. Gameplay calls semantic operations.
- [ ] Separate host-side preparation from native service/readiness. Host cache
  speed and background threads cannot advance gameplay or choose completion ticks.
- [ ] Classify each native command's acknowledgement and completion contract.
  A synchronous command wait alone does not justify a recorded readiness kind.
  Require the dedicated contract's production work and polling/service conditions.
- [ ] Extend typed hardware work descriptors and fingerprints for disc-backed
  operations. Include media, program/file/slice, operation, destination, and
  all relevant parameters. Never encode a file as a fabricated ROM address.
- [ ] Preserve the readiness-only replay contract. Add a recorded kind only
  after matching production submissions and service boundaries are proven.
  Trace data cannot create jobs or supply their payloads.
- [ ] Keep SP/IPX/SPX algorithms in `game.soniccd`. Pure device cores do not
  run the game's command dispatch. Reuse existing modes, with a bounded hosted
  native-program capability only where needed, instead of per-file Engine cases.
- [ ] Implement the explicit sub-CPU timer and per-MMD interrupt regime.
  `RunPCMDriver` is timer-interrupt work, not one tick per video frame.
- [ ] Implement only timing models classified in CD-02, with cited input/cost
  rules or independently validated firmware/drive manifests. Keep unresolved
  families visible and block live parity closure. Never tune per-operation
  constants to a single movie. Reopen the design before CD-08 if unresolved
  families dominate the intended semantic model.
- [ ] Implement explicit rational native clocks and per-release cadence.
  Preserve present cartridge timing. Cover pause, lag, skipped main loops,
  transitions, resets, and drift over long recordings.
- [ ] Register rewind state for pending jobs, command/ack state, ownership,
  clocks, ordinals, buffers, and consumption. Recreate transient workers and
  invalidate stale generations on restore, reset, and session replacement.

Exit evidence: isolated boot/file-transfer, graphics handoff, and streaming probes match
native observations. Record the live timing evidence class and validation
for each family separately from permitted recorded-readiness diagnostics.
Required unresolved live families block this exit. Warm/cold host caches and
delayed workers yield the same simulation. Full warp gameplay joins in CD-09/CD-10.

If two independent feature families require escaping these services to write
shared hardware state, stop and redesign this boundary before adding another
generic callback or a CPU interpreter. Preserve the failing evidence.

## CD-06. Add PCM, CD audio, and native video

Owner: audio/presentation workstream. Dependencies: CD-04, CD-05.

- [ ] Decode the actual regional FM driver and PCM banks. Match channel update
  order, sample addressing, envelope/loop/stop rules, bank switches, priorities,
  mixing, panning, clipping, and resampling against the native implementation.
- [ ] Implement CDDA track/index/sector transport, play/stop/pause/resume,
  seek, looping, fades, mode transitions, and regional track mappings.
  Soundtrack preference overrides are an extension, not a region implementation.
- [ ] Preserve native PCM/video synchronization and streaming behavior.
  Decode the user's original streams, including opening, good/bad endings,
  and pencil test. Match frame palettes, timing, skip controls, and audio phase.
- [ ] Validate command and chip/PCM state before comparing final audio.
  Use exact equality where the native contract permits it. Document any
  independently justified analog/resampling tolerance and its limits.
- [ ] Capture/restore transport positions, fractional sample phases, stream
  cursors, queued commands, decoder state, and native buffers. Keep the live
  sink outside simulation authority and retain no-device headless boot behavior.
- [ ] Measure bounded buffering and sustained playback without underruns.
  Set performance budgets from measured probes, not invented targets.

Exit evidence: independent effect/music tests plus synchronized native
gameplay and FMV captures, including pause, seek, loop, transition, and rewind.

## CD-07. Establish stock launch, native modes, and saves

Owner: bootstrap/mode/save workstream. Dependencies: CD-03 through CD-06.

- [ ] Add public `GameId.SCD` and the necessary stock/trace/save mappings.
  Make source-based stock launch canonical at this real consumer. Migrate any
  cartridge-only abstract obligations and callers together, with candidate
  version/descriptor/signature updates and focused cartridge checks.
- [ ] Add a genuine Sonic CD stock module and stock selection entry, initially
  labeled incomplete. Bind fresh source and services through WorldSession.
- [ ] Implement the design's host-mode map. `GameLoop` alone commits active
  `GameMode`; the CD coordinator requests transitions and owns only native
  program/load identity and carry state. Review proposed `NATIVE_PROGRAM`
  admission with the candidate API pins. Do not host stock CD via `MOD_SCENE`.
- [ ] Reproduce native startup, opening, title, new game, continue,
  return-to-title, reset, game over, and initial scene transitions.
- [ ] Model native backup RAM operations and records, including internal and
  external storage paths actually reachable in the shipped code. Cover
  unformatted/full/invalid/corrupt data, errors, slot operations, and cancellation.
- [ ] Keep device bytes/format/status in the CD device/HLE service, pure
  game-payload codec and requests in the CD save provider, and durable commits
  in host persistence. Campaign state remains the source for saved progress.
- [ ] Separate emulated BRAM state from host persistence. Rewind/replay cannot
  reissue host writes. Define deliberate commit points and atomic replacement.
  Preserve existing user saves before migration and test crash recovery.
- [ ] Namespace storage by device format and firmware semantic compatibility,
  plus game-payload/release compatibility. Expose cross-release compatibility
  only after verifying all three. Store Time Attack and unlock state correctly.
- [ ] Test repeated boot, region replacement, and cartridge/CD switching in
  one JVM. Assert source, audio, saves, and runtime-art ownership after every edge.

Exit evidence: cold boot through opening/title to the R11A load boundary and
BRAM save/reload through production owners, including failure and restore
paths. Player traversal is a CD-08/CD-10 obligation, not a CD-07 prerequisite.
Attract demos remain CD-13 obligations because their later-zone programs arrive in CD-12.

## CD-08. Implement the native player and shared stage loop

Owner: gameplay workstream. Dependencies: CD-03, CD-05, CD-07.

- [ ] Audit active player copies and native arithmetic/order across all zone
  families before deciding which code is shared. Preserve fixed-point widths,
  signedness, overflow, input latches, and collision order.
- [ ] Implement native movement, rolling, jumping, charge/release spin dash,
  Super Peel-Out, camera focus/look, hurt/death, inactivity behavior, water,
  drowning, power-ups, debug behavior, and animation/DPLC upload rules.
- [ ] Implement object slots, fixed objects, update order, spawn/despawn,
  saved flags by period, touch/solid collision, rings, monitors, checkpoints,
  score, timer, HUD, title cards, pause, results, and life/restart flow.
- [ ] Place broad movement/collision gates in narrow shared rules. Keep
  CD-only mechanics in their actual player/object/event owner. Do not copy S1
  physics and tune it until a movie passes.
- [ ] Integrate native art, tiles, palettes, layers, scroll, camera boundaries,
  sprite limits, and graphics queues. Match native output before wider views.
- [ ] Extend focused cross-game regressions for each changed shared owner.

Exit evidence: independently recorded movement/collision scenarios, slot and
animation checks, production source bindings, and deterministic rewind.

## CD-09. Implement time travel and world history

Owner: CD progression/time-travel workstream. Dependencies: CD-07, CD-08.

- [ ] Model stage location, current period, warp direction/charge/lifecycle,
  checkpoint state, generator/projector state, and global future/Time Stone
  progress as separate concepts. Do not overload zone or act integers.
- [ ] Port signs, speed/continuity gates, cancellation/rearming, trail and
  sound, fade/cutscene/load, destination selection, native coordinate/velocity
  transfer, camera/water/timer restoration, and object reset/persistence.
- [ ] Derive Good Future selection from the native conditions and their scope.
  Verify generator destruction, hologram/projector consequences, act/range
  flags, boss-act selection, and all-Time-Stones behavior independently.
- [ ] Cover warp during edge cases, death/restart after a warp, checkpoint
  interactions, repeated round trips, blocked destinations, and special-stage
  return. Establish each case from reachable source paths and native behavior.
- [ ] Exercise capture/restore immediately before, during, and after each
  handoff. Assert forward replay or explicit timeline isolation according to
  the owning load contract. A time warp is a game transition, not rewind UI.

Exit evidence: a complete R11A-to-Past-to-future production route with all
state owners verified, plus independent short rejection and restore cases.

## CD-10. Complete Palmtree Panic as the reference implementation

Owner: first-zone integration workstream. Start after CD-06 through CD-09.
Closure additionally requires CD-11 for native special-stage entry and return.

- [ ] Implement all R11/R12 A-D and R13 C-D bindings, objects, events,
  scenery, camera/scroll, palette/tile animation, boss children/defeat/escape,
  all entries, all exits, and outgoing progression.
- [ ] Supply act/period/route matrices under the existing
  [level test standard](../../guide/contributing/level-test-standard.md).
  Each applicable ENTRY, OBJECT, EVENT, CAMERA, BOSS, LIFE, LOAD, REWIND,
  PRESENT, ROUTE, and ORACLE obligation needs evidence.
- [ ] Validate native bad-future and good-future progression, special-stage
  entry eligibility and return, saves, title/reset, and host-media failures.
- [ ] Prove offscreen rendering and production PCM capture at native size.
  Add every actually supported width/donor/character/team configuration using
  the standard's required short cross-products and representative routes.
- [ ] Compare a second independent input movie. Keep short failure/rewind
  checks independently runnable instead of behind the full route.

Exit evidence: a complete bounded zone delivery with explicit inherited gaps.
Do not call the first playable act complete USA support.

## CD-11. Complete special stages and Time Stones

Owner: special-stage workstream. Start after CD-05 through CD-07.
Native level entry/return integration also needs CD-08 player support.

- [ ] Bind the stamp-device core proven in CD-05 to the special-stage program.
  Compare its requests and output independently of the title consumer.
- [ ] Implement native stamp graphics, fixed-point projection, clipping,
  object geometry, and Word RAM handoff with independent pixel probes.
  Do not use a visually similar S2 or S3K special-stage renderer.
- [ ] Keep Time Stones distinct from Chaos Emeralds in reward and save contracts.
- [ ] Port player controls, UFO behavior/collision, terrain/stamp effects,
  water/time penalties, timers, rings, rewards, results, pause, failure,
  stage sequence, all seven Time Stones, and return to the correct act state.
- [ ] Implement and verify the eighth/secret-stage path and special credits
  according to native entry/exit behavior established in CD-01/CD-02.
- [ ] Validate both direct development entry and real level entry. Check
  retry, restart, reset, all-stones progression, saves, and Time Attack access.
- [ ] Snapshot all simulation, renderer/graphics-work, audio, and transition
  state. Validate forward replay while a graphics operation is pending.

Exit evidence: every native stage and reachable secret path has an independent
matrix, native render/audio evidence, and correct progression/return behavior.

## CD-12. Complete the remaining six zones

Owner: one bounded zone workstream per zone after CD-10/CD-11 prove shared contracts.

- [ ] Collision Chaos, R3. Cover bumpers/flippers, moving platforms,
  vertical camera behavior, native object interactions, and boss mechanics.
- [ ] Tidal Tempest, R4. Cover water levels/palettes, currents, drowning,
  underwater movement and audio, transitions, and the underwater boss.
- [ ] Quartz Quadrant, R5. Cover conveyors, movement-sensitive mechanisms,
  period-dependent collision/geometry, and treadmill boss behavior.
- [ ] Wacky Workbench, R6. Cover charged floor behavior, electrical hazards,
  freeze/release state, transport paths, special player copies, and boss.
- [ ] Stardust Speedway, R7. Cover route/camera speed, moving transports,
  Metal Sonic race, Amy interactions, failure/retry, and race completion.
- [ ] Metallic Madness, R8. Cover mini-Sonic transitions, layer/scale changes,
  transport machinery, final encounters, escape, and ending selection.

These mechanic lists are initial research targets, not exhaustive zone specs.
For each zone, derive the complete object/event/asset list from all ten payloads.
Every phase includes all normal periods, both boss futures, title/results,
music, palette/tile/scroll changes, progression, save/respawn, and native bugs.

Require the same evidence as CD-10 for every act/period/route. Record
source sharing only after comparing actual consumers and regional variants.
No zone is complete because its data loader or registry entry exists.

## CD-13. Close every non-level program and unlock path

Owner: menus/secrets/presentation workstream. Start after CD-06 and CD-07.
Full attract-demo/mode closure also depends on CD-12's later-zone programs.

- [ ] Complete Time Attack for regular and special stages, record display,
  sorting/ties, persistence, resets, all unlock thresholds, and return paths.
- [ ] Complete D.A. Garden, Visual Mode, opening replay, both ending replays,
  pencil test, sound test, stage select, staff times, all regional secret
  screens, debug/native cheat codes, and special-stage credits.
- [ ] Complete attract demos, title inactivity, native controller behavior,
  credits, Thank You, and reachable Comin' Soon behavior. Explain any retained
  inactive program using actual dispatch and native evidence.
- [ ] Complete R11A, R43C, and R82A attract demos after their production
  player/zone bindings exist. Verify their native inputs, exits, and timing.
  Bind D.A. Garden to the stamp core from CD-05 and verify its own behavior.
- [ ] Exercise every unlock from a fresh save and from persisted data.
  Development shortcuts cannot satisfy native unlock gates.
- [ ] Include error/cancel/back/reset, pause and music transitions, repeated
  mode entry, native rendering, and supported rewind behavior per mode.

Exit evidence: every program/entry in CD-01 has an implemented/tested or
evidence-backed not-applicable disposition. Required unimplemented content
blocks complete parity even when the main route can reach the ending.

## CD-14. Certify the complete USA MVP

Owner: integration/parity lead. Dependencies: CD-10 through CD-13.

- [ ] Complete the USA manifest for every required stage variant, object/event
  family, normal/special mode, audio/media path, persistent record, secret,
  and incoming/outgoing transition. Reconcile expected versus executed cases.
- [ ] Run fresh native full-game routes to bad and good endings. Cover both
  future-restoration and all-Time-Stones progression, death/continue/restart,
  fresh saves, reloads, and separate Time Attack/unlock campaigns.
- [ ] Compare independent input recordings and perturb boundary inputs.
  Confirm the implementation generalizes beyond its development movies.
- [ ] Verify native pixels, palettes, sprite order/limits, scroll, text, HUD,
  video, sound commands, PCM/CDDA output, and their timing at named boundaries.
- [ ] Exercise supported OpenGGF extensions separately. Preserve the native
  acceptance lane when enabling wider views, donors, or creator features.
- [ ] Run the required focused/domain checks and change-based smoke coverage.
  Protect S1/S2/S3K stock launch, gameplay, rewind, saves, audio, and rendering.
  Keep the four named S3K regression obligations in AGENTS.md satisfied.
- [ ] Verify Windows, Linux, and macOS packaging/launch paths for the supported
  media formats and newly required decoders. Admit dependencies deliberately.
- [ ] Update stock selection, player/setup/configuration docs, known gaps,
  coverage backlog, active-line changelog, and affected mirrored agent skills.
  Remove incomplete labeling only when its acceptance conditions pass.

Exit evidence: zero unresolved required USA parity gaps, including live timing
families and firmware/HLE behavior. Recorded-readiness-only agreement cannot
satisfy this playable-game gate. Require zero missing or skipped required
USA acceptance cases, and no new/worsened shared regressions.
Known unrelated baseline failures require exact identity/attribution. They do
not erase missing CD evidence. Public release preparation uses the repository's
explicit full-suite, guards, domain checks, and release-publishing workflow.

## CD-15. Certify Japan

Owner: regional workstream. Dependencies: CD-14, early regional research.

- [ ] Bind the verified Japanese media/revision and native execution profile.
  Implement every code/data/presentation/media difference from CD-01.
- [ ] Compare shared components before reusing evidence. Execute every changed
  binding, every native mode, all period families, complete good/bad routes,
  special-stage and unlock paths, audio/video, and backup RAM behavior.
- [ ] Reject mixed USA/Japan data/audio sources and invalid forced profiles.
  Test new-session switching and any deliberately supported save compatibility.
- [ ] Complete the Japanese acceptance matrix and documentation with the same
  parity definition as USA. A soundtrack toggle cannot satisfy this phase.

## CD-16. Certify Europe and PAL execution

Owner: regional/timing workstream. Dependencies: CD-14, early PAL research.

- [ ] Bind verified European media/revision and native PAL execution profile.
  Implement the audited code, layout, localization, and media differences.
- [ ] Validate native simulation/timer cadence, input, V-int/main-loop phase,
  display geometry, FM/PCM clocks, CDDA sample cadence, video sync, and loading.
  Do not apply a global 50/60 speed multiplier to conceal subsystem differences.
- [ ] Repeat every required European mode/period and good/bad route, special
  stage, unlock, save, audio/video, transition, and rewind obligation.
- [ ] Validate long-duration clock drift and deterministic movies. Separate
  host display refresh from the selected native simulation environment.
- [ ] Keep Japan/USA behavior unchanged when selecting Europe, then restore
  a cartridge session and verify its existing profile.

## CD-17. Close the all-region contract

Owner: integration/parity lead. Dependencies: CD-15, CD-16.

- [ ] Reconcile all required release × native-environment × mode/stage/period
  rows. Share evidence only for proven identical behavior and real bindings.
- [ ] Require an explicit supported revision list. Unknown pressings remain
  uncertified until their content and behavior are verified. Three regions
  do not imply every pressing, prototype, rerelease, or remaster is supported.
- [ ] Retain complete component and transition coverage, negative media tests,
  independent native oracles, rewind/load isolation, and platform launch proof.
- [ ] Record the finite corpus used to support the parity claim and any limits
  of its native timing environment. A test corpus cannot prove every possible
  input sequence. Newly discovered shipped behavior creates a tracked obligation.
- [ ] Prepare a public release only under the actual release policy. Confirm
  its downloadable artifacts before saying the fourth game has shipped.

## Choose proportionate verification for each delivery

Follow [AGENTS.md](../../../AGENTS.md) and the
[testing guide](../../../tools/testing/README.md), not a second testing policy.
Focused checks prove bounded changes. Shared algorithms, physics/timing,
public contracts, or uncertain changes normally need change-based smoke.

Before broad validation, inspect
`python3 tools/testing/run_categories.py --base <pre-task-integration-sha>`.
State its candidate count, expected cost, and stopping rule. Run its preflight
in the launch environment, then the queued runner when appropriate. Reuse an
existing matching active job. Do not run duplicate baseline/development/merge
suites by ceremony. Attribute disputed failures with bounded matched tests.
Full suites are reserved for public release preparation.

Required native/domain checks remain separate from smoke. Every domain gate
names its actual command and selector when its implementation exists. The
plan does not invent runnable Sonic CD tests or recorder flags in advance.
Do not narrow a category selection by hand, relax an assertion, increase a
tolerance, or suppress a missing input just to close a phase.

Use headless gameplay and owned offscreen rendering. Never use the shared
desktop for automation. Measure performance after correctness probes exist,
interleave comparable baseline/current scenarios, and set budgets from evidence.
No fixed duration or staffing estimate is supported by this planning task.

## Preserve the finite coverage obligations

Each applicable row needs both short independent checks and meaningful route
evidence. Add cases when an observed branch makes the initial inventory incomplete.

| Coverage axis | Required contract |
| --- | --- |
| Product | USA, Japan, Europe with an exact revision/media manifest and environment |
| Stage payload | 70 declared retail payloads per region, with aliases/reachability explained |
| Progression | Every stage edge, all period warps, both boss futures, Time Stones, both endings |
| Object/event | Every dispatched family, subtype and meaningful state/interaction, including children |
| Lifecycle | Cold entry, checkpoint, death/restart, game over/continue, reset, outgoing load, reload |
| Rewind | Before/during/after each stateful boundary, pending service work, isolation on intentional resets |
| Non-level | Every native mode, secret, demo, unlock, record and save/error path |
| Presentation | Native pixels/palette/layer/scroll/sprite rules, audio and video synchronization |
| Extensions | Every supported width/donor/character/team obligation under the existing standard |
| Regression | Existing cartridge identities/profiles, public pins, sources, sessions, audio and saves |

## Checkpoint for this planning delivery

The lead owns documentation and submodule integration in
`.worktrees/ai-sonic-cd-parity-plan-20261023`. Astra, Opus, and Fable have
read-only design tasks. No engine code, POM, test selection, or queue policy
changes are part of this delivery. Its verification uses gitlink/configuration,
reference structure, documentation links/mirrors, policy, and checkout scenarios.
Engine parity and native media validation remain future phase work.

## Local implementation checkpoint, 23 October 2026

Implementation is authorized on `feature/ai-sonic-cd-local` only. The starting
commit is `63222aab395db19d3a3b12f797fb7803876898bd`; the retained worktree is
`.worktrees/ai-sonic-cd-local`. Do not integrate or push this work to `develop`.
The earlier planning commit was reverted on `develop`; its public history is
not erased. The current task does not change that delivery boundary.

The user supplied three regional CUE/BIN archives and a BIOS archive in
Downloads. Extraction preserved their contents and all four original archives.
All 121 extracted files matched archive sizes and CRC32 values. Each disc has
35 tracks, comprising one MODE1/2352 data track and 34 audio tracks. CUE file
references, indexes, sector-sized files, and U/E/J header fields passed checks.
`~/Downloads/Sonic-CD/media-inventory.json` records archive and per-file
SHA-1/SHA-256 identities and the supplied CUE layouts. These checks establish
available inputs, not pressing authenticity, firmware certification, physical
read offsets, native boot, or game parity.

The first implementation units have separate writers. The lead owns this
checkpoint, review, combined validation, and local commits.

| Unit | Owned implementation | Exit check | Current state |
| --- | --- | --- | --- |
| Disc source | `com.openggf.data.cd` and its tests | Synthetic malformed/layout/lifecycle cases and actual file reads from all three discs | In progress |
| Native catalogue | `com.openggf.game.soniccd` and its tests | All 70 valid stage payloads per region, invalid combinations rejected, source dispatch and component facts checked | In progress |
| Reference oracle | Task-owned `tools/soniccd/oracle_*` and Downloads captures | Verified BIOS selection, native USA CUE boot, observable memory domains, repeated bounded capture | In progress |

The first code shape uses one closeable CUE owner, immutable track facts, and
bounded ISO file extents. A generic machine or cartridge address adapter would
hide the disc's separate tracks and lifetimes. The catalogue uses a shared
finite source table with release-bound views. Duplicating 70 stage entries for
each region would obscure which facts differ by release. Traversal stages and
boss stages have distinct valid period combinations.

The native oracle and pure media/catalogue work proceed independently. Gameplay
parity remains behind CD-02's native evidence gate. Focused media/catalogue
tests can establish their own behavior without certifying the later game.
Workers do not run concurrent Maven jobs in this worktree. The lead uses the
current main-workspace queue wrapper with this worktree as its working directory
and owns the single command session for each validation job.

Next checkpoint records actual source state, exact commands, failures, skips,
and native capability limits before the next dependent unit starts. Decision
and evidence updates stay in this dated plan, following repository guidance.

### Recovery after the app restart

The worktree still has the partial disc reader and native catalogue. The earlier
workers and queued Java version probe did not survive; no completed Java or
native boot check is claimed. Fresh app-owned delegates continue the same owned
paths from those files. Their task IDs are recorded here so a later restart can
recover the actual tasks instead of starting duplicate work.

- Reader: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-resume-reader-20261023-r1`.
- Catalogue: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-resume-catalogue-20261023-r1`.
- Native oracle: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-resume-oracle-20261023-r1`.

The copied category runner selects the repository containing its own script.
Use this worktree's `tools/testing/run_categories.py` when inspecting changes.
The queue wrapper explicitly uses the caller's working directory and can be
invoked from the main workspace as documented. A plan from the main checkout
does not validate this development tree.

The replacement version probe completed through the shared queue with no wait.
`python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py -v`,
from this worktree, reported Maven 3.10.0 and Java 25.0.5.0.2. This is environment
verification only. Source compilation and focused behavior tests remain pending.

### First unit and native boot evidence

The catalogue worker completed its source unit. Its rerunnable source audit
passed at reference pin `1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff`:
`python3 -B tools/soniccd/catalogue.py --check`. The focused Python command
`python3 -B -m unittest discover -s tools/soniccd -p test_catalogue.py -v`
passed 17 tests with no skips. JUnit selectors `TestCdStageKey,TestCdProgramCatalogue`
and the actual-reader integration remain pending. The source inventory has 216
nonzero dispatch slots, including unnamed invalid slot 39, alongside the 215
named SCMD declarations. These are different counts of the same source table.

The catalogue covers a shared set of 70 stage payloads per release, all 127
file IDs, and 33 nonstage load bindings. Data streams, unused prototype copies,
payload aliases and files without an SPX load binding have explicit dispositions.
Source review found R33's R3PCM driver and R83's BOSSPCM load boundary, the dummy
commands' MCSONIC payload, and Time Attack's main-only SPX loader. Independent
review task: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-catalogue-review-20261023-r1`.

The native worker's initial 2,400-frame USA run reached the title screen. Its
`~/Downloads/Sonic-CD/native/usa-initial-2400/summary.json` records BizHawk
2.11.0.0, Genesis Plus GX, Mega CD mode, and a successful 120-frame native
save/restore forward-replay snapshot comparison. The supplied US Model 1 v1.10
BIOS matches an acceptable firmware-database entry at SHA-1
`f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed`. The contact image was inspected.
The core exposes main RAM, CD program/Word/backup RAM, VRAM, and main/sub CPU
bus domains. Repeated boots, regional probes, and the exact observability
limits are still being checked. This is native oracle capability evidence;
it does not close the complete CD-02 timing/capture gate or any engine parity gate.

The catalogue review found two material omissions before Java validation.
Seventeen sound-test commands may load a PCM driver; their audio disposition
must not erase that conditional file dependency. `SPCmd_TestR3BMus` and
`LoadPCMDriver` (SPX.asm:2393-2397,2456-2464) show the path. The Python audit
also accepted a mutation changing the stage-disposition return to AUDIO,
because it substituted its expected classification. Both findings are accepted
after source inspection. Fix task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-catalogue-fixes-20261023-r1`.
The earlier source-audit pass is bounded by these findings, not a catalogue
acceptance. The fix adds native PCM dependency facts and mutation regressions;
the Java behavior checks remain authoritative for the executed catalogue API.

### Verified foundation unit

Both catalogue findings are fixed. The source audit passes and its focused
Python suite now passes 24 tests, including mutations of PCM targets and
disposition branches. The audit checks source correspondence; executed Java
behavior remains covered by JUnit. Independent reader review found no material
defect at fingerprint `a8f1453762f14c94baadc4dc5fabf0e34442c199881bbfe3cbcbd9e1d6833e0b`.
It proposed additional buffer-refill, reverse mixed-sector and depth-limit
fixtures for the next media unit.

From this worktree at `63222aab` plus the foundation source, the lead ran:

Here `OPENGGF_MAIN` identifies the main checkout and `SCD_MEDIA_ROOT` identifies
the supplied regional media directory in Downloads. The external checkpoint
retains the machine-specific invocation.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCueDiscImage,TestIso9660FileSystem,TestSonicCdDiscMedia,TestCdStageKey,TestCdProgramCatalogue,TestRomImageClassifier,TestRomImageCatalogue,TestRomLocationResolver' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

The run passed 143 tests, with zero failures, errors or skips. It compiled all
4,203 production and 4,312 test source files under Java 25. The selection includes
24 synthetic disc tests, three real regional disc invocations, 72 catalogue/key
invocations and 44 existing cartridge catalogue/classifier/location invocations.
Queue admission was immediate; Maven reported 1 minute 23 seconds. Compiler
deprecation warnings and test startup's missing FAST manifest warning did not
produce a failure. This is a focused pass, not a broad smoke or full-suite pass.

The local category plan selected 3,621 smoke candidates because the new package
and tools have no narrow change rule. Focused coverage replaces that broad run
for this unit: it adds isolated, currently unregistered libraries and diagnostic
tools without changing existing runtime callers, shared algorithms, physics,
timing, public API pins, build policy or configuration behavior. Stock admission
and later shared integration still require their affected smoke coverage.

The final native probe source and wrapper produced two matching cold boots in
each of USA, Japan and Europe, with 1,800 observed hardware frames per boot and
a 120-frame save/restore audio/video continuation check. Selected BIOS SHA-1s
are USA `f4f315adcef9b8feb0364c21ab7f0eaf5457f3ed`, Japan
`4846f448160059a7da0215a5df12ca160f26dd69`, and Europe
`f891e0ea651e2232af0c5c4cb46a0cae2ee8f356`. Five Python wrapper tests passed.
The final captures, source/runtime manifests and limits are under Downloads in
`Sonic-CD/native/{usa,japan,europe}-title-final-1800` and
`Sonic-CD/research/native-oracle.md`.

Managed bus peeks intentionally hide handled device I/O with FF; only the main
CPU bus advertises memory callbacks. Boot repeatability therefore does not prove
PCM/CDDA cursors, Word RAM ownership, graphics readiness or service timing.
A separate read-only observer investigation continues under task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-observer-20261023-r1`.
It owns only `device_observer_*` tools and external evidence. No engine gameplay
uses these observations. No Sonic CD game module or playable route is admitted yet.
