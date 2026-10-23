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

### Next local units

The verified foundation is committed locally as `9152f2ef2`. The commit passed
the installed policy hooks; machine-local paths in this plan were replaced by
environment variables after the first resource-policy rejection. No hook was
bypassed. Source changes were not repeated or retested for that prose-only fix.
The branch remains unpublished and the main checkout remains on `develop`.

Program-local MMD asset access and whole-disc observed identity are the next
consumer of the reader and catalogue. Task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-program-assets-20261023-r1`
owns only the new `game.soniccd.media` package and a developer inspection tool.
It does not add stock admission, a CPU interpreter, or creator asset authority.

Task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-disc-regressions-20261023-r1`
owns the three existing synthetic reader test/helper files to add buffer-refill,
reverse mixed-sector and depth-threshold cases. Production reader changes
require evidence from a failing case. These units and the device observer use
separate files; the lead remains the sole Maven owner. The 143-test pass belongs
to the foundation commit, not to later uncommitted source or tests.

The regression-fixture worker returned three test-only cases: a patterned
131,389-byte sequential stream spanning both 64 KiB refill boundaries; a shared
2352-to-2048 transition with stored gap and distinct boundary data; and depth
acceptance at eight levels including root versus rejection at nine. Production
is unchanged. Their focused JUnit validation remains pending while the program
asset worker owns source edits. No duplicate Maven job is queued.

Whole-disc provenance research runs separately under
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-media-provenance-20261023-r1`.
It may match public checksum metadata to the supplied full track set, but cannot
turn a header, mirror or repeatable emulator boot into pressing certification.
Its output belongs in Downloads research; no commercial media is acquired.

The provenance result matches all 35 tracks by size, CRC32 and SHA-1 for each
supplied region against the current primary Redump DAT: USA record
[29188](https://redump.info/disc/29188), Europe
[8302](https://redump.info/disc/8302), and Japan
[1632](https://redump.info/disc/1632). The lead repeated the inventory-to-DAT
comparison. US/European CUE bytes match the primary exports; Japan differs
only in filename capitalization. The DAT includes each CUE as well as its
35 BIN entries. No numeric revision is supplied by these records. The four
other catalogued USA retail variants share the data track but differ at audio
track 13. A data-track-only identity would therefore lose a real content variant.

The primary export's uncompressed SHA-256 is
`6910bd275ab99049147bb43367f9dfa149e164bf1dd0486acb96523e740f4fea`.
The Downloads report preserves metadata pins, track/index comparisons and
physical acquisition limits. Content matching does not establish a physical
pressing, firmware contract or game parity.

Task
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-retail-content-20261023-r1`
owns a new `game.soniccd.release` matcher. It binds source-verified whole-disc
layout/content identities to known content records, separately from the source
region enum. Its expected fingerprints are derived from the matched metadata,
not a trusted header or a fitted gameplay observation. Validation is pending.

### Asset and device observation checkpoint

The program-asset and retail-content workers returned stable source. The
retail reproducer matched all 105 public-metadata track triples and independently
derived the normalized fingerprints used by the Java asset tests and matcher.
Six reproducer Python tests passed. The reader's three new boundary fixtures
remain in the same combined validation unit.

The lead queued this focused Java command from the local worktree at `9152f2ef2`
plus those new files. Its result is pending; the preceding 143-test pass does
not cover them.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCueDiscImage,TestIso9660FileSystem,TestCdMmdImage,TestCdObservedMediaIdentity,TestCdProgramAssets,TestSonicCdProgramMedia,TestSonicCdMediaInspectTool,TestCdRetailReleases,TestSonicCdRetailMedia' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

Asset review task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-asset-review-20261023-r1`.
Observer review task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-observer-review-20261023-r1`.
Both are read-only, on stable scoped files.

The observer worker proved read-only device access through pinned ELF symbols
and the existing Waterbox monitor, with source-derived structure offsets and
exported-RAM anchors. It captured PCM, CDDA, CDD/CDC, ownership, graphics and
timer fields without invoking device read handlers. Two repeated USA boots,
observer-free and debugger-callback controls, and four restored continuations
preserved native AV and state. Six Python tests and two real identity/layout
rejection experiments passed. All producer hashes and limits are in Downloads
`Sonic-CD/research/device-observer.md` and its final manifest. Frame-boundary
sampling still misses transient service edges; CD-02 remains open.

The first combined Java run compiled successfully and executed 60 tests with
zero assertion failures, one error and zero skips. The error was
`TestSonicCdMediaInspectTool.realMainWritesExplicitExternalJsonAndRefusesToReplaceIt`:
the tool treated an unrelated Git marker on a user-home ancestor as an OpenGGF
source checkout. The test already used an external Downloads directory. Root
inspection confirmed that ancestor had neither the project POM nor source tree.
The fix recognizes an actual OpenGGF source root, retaining media, symlink and
new-file protections; its new regressions cover all three regional CLI options.
Fix task: `node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-cli-fix-20261023-r1`.

Asset review also found an identity-provenance mismatch not covered by that run:
the helper could combine one open disc's track hashes with another supplied
CUE's file records, or re-resolve a retargeted BIN symlink. The fix keeps frozen
CUE bytes and bounded source views on the open owner, hashes its retained file
handles, and rejects the wrong-CUE compatibility call. Normalized identity
semantics and the three retail fingerprints are unchanged. Wrong-owner,
symlink-retarget and source lifecycle tests are added. Fix task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-provenance-fix-20261023-r1`.
The same affected nine-class selector is running again after these source fixes;
the final result remains pending. This is an affected rerun, not a duplicate pass.

Observer review passed its five files and source/runtime/artifact bindings at
fingerprint `fe4ac9ad0777ce63a917fc6f667022a5199f65a676bcda38b82f67f87523a3ce`.
It confirmed read-only memory access and the recorded repetition/control/restore
evidence, retaining the one-disc x86-64, frame-boundary and wrap limits.

Two separate native investigations continue on new tool prefixes. Stamp input
and output fixtures are owned by
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-stamp-fixtures-20261023-r1`;
exact service-event observation is owned by
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-service-events-20261023-r1`.
They do not write Java source or the shared Maven target while validation runs.

The affected rerun executed 68 tests with zero assertion failures, three errors
and zero skips. Both provenance regressions passed. Only the three new
`cliAllowsExternalJsonUnderUnrelatedAncestorGit` parameterizations errored: their
synthetic fixtures lived inside the real worktree's generated target directory,
so the source-checkout guard correctly found that ancestor. The real external
CLI invocation passed. This is a test/output-contract mismatch, not another
home-Git misclassification.

The lead chose an explicit output contract for the undelivered diagnostic tool:
protect media, existing files and project source; permit temporary diagnostic
JSON under the generated `target/` tree, as required for Maven-owned test output.
Canonical symlink resolution must prevent a target alias from granting writes
into source. Nested real source roots stay protected. This avoids external
test scratch, test skips and extra output-policy flags. Fix task:
`node:delegated-task:command%3Amcp%3A21b3a84c-8f4f-4e36-af09-5662834858be%3Adelegate-task%3Asonic-cd-cli-target-policy-20261023-r1`.
Only the affected CLI class needs rerunning after that fix; the 65 other passing
invocations are retained on unchanged source.

The generated-output fix returned stable CLI source and boundary tests. The
lead is running only `TestSonicCdMediaInspectTool` through the shared lean queue
with the explicit regional media root. Its live command session is `73622`;
there is no other task-owned Maven job. The two native investigations remain
active and own only their tool prefixes and external build/capture directories.

Before committing the observer tooling, its task-specific output-root literal
must become a portable default or explicit parameter. The currently verified
observer producer stays frozen while both native consumers use it. No installed
binary, probe payload or layout is to be changed for that portability cleanup.
The observer files remain accounted-for local work, not an accepted Git unit.

### Verified media assets and retail content checkpoint

The CLI-only run above finished with 13 tests, six errors and zero skips.
All six errors were generated-output positives. A linked worktree sits below
the main source checkout, so checking every source ancestor assigned two owners
to the same output. The final contract uses the nearest canonical source root.
Its target directory is allowed; its source remains protected even when that
checkout itself sits in an outer target directory. This replaces the earlier
all-ancestor interpretation. Source/media symlinks and existing-file replacement
remain rejected.

The final affected command at `9152f2ef2` plus the completed asset/release unit
passed 14 tests, zero failures, errors or skips. Maven took 1 minute 18 seconds;
queue admission reported zero seconds of waiting. Session `37225` is finished.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off -Dtest=TestSonicCdMediaInspectTool -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

The preceding combined run's other eight classes passed all 60 invocations,
including real media for each region and both owner-bound provenance regressions.
Their source has not changed since that run. The completed unit therefore has
74 distinct passing invocations across these two runs, not a single 74-test
run or a broad smoke pass. Six retail-profile Python tests also passed.
The earlier 143-test foundation result remains separate. There is no live
task-owned Maven job. New device Java is excluded from this verified unit.

Known retail content keys are `redump-29188` (USA), `redump-8302` (Europe)
and `redump-1632` (Japan). All 105 metadata triples match the pinned primary
DAT, and actual-reader tests reproduce the complete layout/content fingerprints.
The API keeps region, content record and source provenance separate. A forced
known key rejects a different disc. This is not stock game admission, physical
pressing provenance, firmware certification or gameplay parity.

### Native continuation and service observation handoff

Both native investigations returned stable new tool prefixes and stopped all
owned processes. Service callbacks produced repeated/control/restore evidence
for the title and a normal-button route visibly reaching Palmtree Panic.
The source audit corrected the earlier scope assumption: S68K shares the
callbacks advertised as `M68K BUS`. CPU identity and access width are unknown
in that adapter. Sector forwarding is paired and preserves original results.
Graphics launch is bracketed before any lines complete, but busy clear, final
output completion and ownership return remain distinct boundaries. Eleven
Python checks passed. Exact owner events and clock epochs keep CD-02 open.

Six native title fixtures retain full 256 KiB ingress and egress Word RAM,
with partial progress explicit. Exact continuation comparisons match every RAM
byte and the complete 12,288-byte image. The observed subset is mode 7, normal
priority, 256 by 96 pixels; rotation, mirroring, wrapping and other modes are
not proven by these captures. Twelve Python checks, repeated boots, an
observer-free AV control and restored continuations passed. Egress is a
pre-consumption ownership poll, not an exact completion timestamp. Commercial
payloads stay outside Git in the Downloads task directory. These diagnostic
schemas are not V5 or engine-input authority.

Fresh scoped tasks now own the next units:

- `sonic-cd-stamp-device-20261023-r1` implements pure device semantics and
  synthetic plus opt-in exact continuation JUnit tests in the new device package.
- `sonic-cd-native-tools-review-20261023-r1` reviews only the new service and
  fixture tools and their evidence, read-only.
- `sonic-cd-native-owner-ledger-20261023-r1` prepares a separate pinned native
  core build and bounded owner instrumentation. Installed cores and frozen
  helpers remain unchanged.

Each task ID uses the existing delegated-task command prefix recorded above.
The parent retains Git, Java compilation, Maven target and delivery ownership.
All implementation remains on the local feature branch with no upstream,
integration or push. No playable Sonic CD module is implemented yet.

The media-loading unit is committed locally as `ae72cb487`; its patch and the
updated plan are exported to Downloads. The native-tool review verified both
fixture runs' full RAM comparisons and independently reproduced all six images.
It found one P2 acceptance gap: 1M ingress and changes to egress stamp mode or
priority could pass validation. Existing captures have the expected settings.
Fix task `sonic-cd-native-guards-portability-20261023-r1` owns the producer and
validator guards, regressions, a fresh guarded capture, and portable Python
output-root defaults. The review's nine-file fingerprint was
`0ed1cf71393cfeafc94f4857d437257926a9c5ebf4bbf8ebf8999bb12442d045`.
Captured original producer sources are preserved in the Downloads source bundle
before these changes, so old manifests remain attributable without rewriting
their pins. The pure Java and native-owner workers have the finding and changed
helper ownership. Their owned packages/prefixes remain separate. No Maven job
is active, and no probe unit has been committed yet.

### Guarded probe delivery checkpoint

The accepted fixture finding is fixed in both producer and validator. Ingress
requires 2M sub ownership and matching raw request/register state. Stamp mode
and priority stay bound while the ownership bits legitimately return to main.
The saved review mutations now reject. Eighteen stamp Python tests, including
27 compiled C# guard cases, six device tests and eleven service tests passed
without skips. Twenty-eight CLI scenarios checked portable roots and path guards.
Machine-specific Python root literals now derive from the user's home directory.

Two fresh 1,500-frame observed boots, an observer-free control and every
120-frame restored continuation passed. All six guarded ingress/egress RAM
payloads, images, inventories, AV streams and checkpoints equal the originals.
The guarded manifest SHA256 is
`10552d10b019ea1a689f62d85b32d4ae8c9f85468b66cb6230f19b095f09781a`.
The parent independently checked its current producer hashes and all bound run
streams/full-byte continuations with `stamp_fixture_validate.py`. Old manifests
remain unchanged and refer to their captured source versions; the historical
service checker correctly rejects a revised wrapper rather than silently
certifying it. The Downloads source archive retains the original versions.

The separate pure Java device unit returned seven stable files. Its pending
focused command is below. Session `14205` owns the only Maven job. Fresh review
task `sonic-cd-stamp-java-review-20261023-r1` is read-only on those seven files.
The native-owner build continues separately and does not write Java or target.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdStampGraphics,TestStampFixtureAdmission,TestCdStampNativeContinuations' -Dsoniccd.stamp.fixtures=${SCD_STAMP_FIXTURES} test -B
```

The device has no runtime registration, timing authority, file reads or hidden
mutable worker state. It transforms caller-owned 2M Word RAM in bounded line
steps. Exact native evidence remains partial mode-7, normal-priority title work;
other modes, rotations and wrapping have constructed tests only. Compilation
and JUnit are still pending at this checkpoint. CD-02 and CD-05 remain open.

The first device command finished in 1 minute 19 seconds with 112 tests,
one failure, zero errors and zero skips. All six native full-RAM continuations
and all 52 fixture-admission invocations passed. The sole failure was the
constructed overlap expectation in
`TestCdStampGraphics.takesEachSubsequentVectorFromTheCurrentCallerMemory`.
Its second output replaces only a byte's high nibble; the expected array
incorrectly discarded the original low nibble of the overlapping delta word.
Pinned `gfx_render` preserves that paired pixel. The lead removed the erroneous
expected-byte assignment; production source is unchanged. Only
`TestCdStampGraphics` is rerunning, in session `82450`. The other 58 passing
invocations retain unchanged source and need no ceremonial repeat.

### Verified pure stamp transform

The focused rerun passed all 54 `TestCdStampGraphics` invocations with zero
failures, errors or skips. Maven took 24.041 seconds, with zero queue wait.
Together with the unchanged six native continuations and 52 admission checks,
this establishes 112 distinct passing checks across the two affected runs.
It is not a single green combined run or a broad smoke result. Session `82450`
is finished; no task-owned Maven job remains.

Fresh review `sonic-cd-stamp-java-review-20261023-r1` returned bounded PASS on
the final seven files. Independent calculations matched all 10,240 local
orientation coordinates and 262,144 priority-byte combinations against the
native source semantics. It also reproduced all six complete egress RAM images,
hashes and cursor results, and verified the overlap correction. The reviewed
production file SHA256 is
`83683b04ee3f218fbf20d841dbb2dd978f6824c9c1c8d6f7cee76b7446cf1253`.

The transform supports both stamp sizes, both map sizes, all eight orientations,
priorities, clipping/repeat, unsigned cursor wrapping and overlapping reads and
writes. Immutable semantic progress plus caller-owned memory is sufficient to
restore and replay a bounded operation. These source/constructed checks do not
extend the native capture coverage beyond mode-7 title continuations. There is
no game module, live scheduling or 1M RAM integration in this unit.

The native probe tools were committed locally as `39dd03c50`. Next bounded
tasks are `sonic-cd-pcm-device-20261023-r1`, owning only the new PCM subpackage
and optional integer-oracle tool prefix, and `sonic-cd-stage-data-20261023-r1`,
owning only new stage-inventory tools and external evidence. The separate
`sonic-cd-native-owner-ledger-20261023-r1` continues its source-built core work.
The lead owns future compilation, validation and local commits. No branch is
being merged or pushed.

### Verified PCM sample device and owner-event handoff

The pure stamp transform is committed locally as `372af0e6f`. The next PCM
unit adds explicit register/RAM access boundaries, eight-channel integer
sampling, source-defined loop/sign/pan/envelope/clipping behavior and deep
immutable capture/restore. Work is bounded to 4,096 samples per request. Zero
work does not service time. CPU synchronization, PCM driver execution, host
volume, blip synthesis, resampling and live audio integration remain external.

At `372af0e6f` plus the eight PCM files, the following focused command passed
36 tests, zero failures, errors or skips. Maven took 1 minute 18 seconds and
queue admission reported zero wait. Session `8220` is finished.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdPcmDevice,TestCdPcmState' test -B
```

Four Python checks passed separately, covering twenty constructed integer cases
against fingerprint-verified pinned C with stub delta collection. That harness
does not include resampling and is not a Java-vs-C differential run, official
native capture or hardware proof. Fresh review
`sonic-cd-pcm-review-20261023-r1` returned bounded PASS on all eight files,
including the source fingerprints and meaningful literal expectations.
The reviewed production device SHA256 is
`cf5183e0e5243159592b9482c42e1fc8fcce6eced9ada7d9a81d77b81c4d2340`.
New task `sonic-cd-pcm-native-fixtures-20261023-r1` owns only a new tool prefix
and separate native builds/captures to observe real PCM blocks. Current Java
and the completed owner-ledger tools are frozen inputs to it.

The native-owner build unit returned fifteen stable tools and a separate pinned
R3 core. It captured 103,646 USA-title events with CPU identity, native access
width, owner commits, separate graphics start/busy-clear/final-output phases,
and explicit reset/rebase epochs. Repeated disabled/enabled captures and
120-frame restores matched AV and sampled public CPU/RAM views; all four
instrumented checkpoints are byte-identical without a mask. Twenty-four Python
tests and actual identity/layout/overflow controls passed. Fresh task
`sonic-cd-native-owner-review-20261023-r1` is reviewing that unit read-only.
Its final build and event artifacts remain external. No owner tool is committed
at this checkpoint. Source-model CDD seek approximation is not physical-drive
timing. CDC DMA completion and enabled level-3 IRQ remain unexercised.

One original official cold checkpoint hash differed despite matched AV/public
views/restore output. That earlier producer retained no raw state, so the
differing bytes cannot be attributed. The lead performed two additional
official-core boots using the immutable R3 compiled producer in control mode.
Both complete 2,026,393-byte states match byte-for-byte and hash to
`7bda4839945dfec7d9419d9a21692ada9603a10baa00d3c16845981279cc479a`.
Their AV/views/restored continuation streams also agree, and all eight installed
runtime pins remain unchanged. This bounded repeat did not reproduce or explain
the historical difference. It does not retrospectively turn that pair into a
raw-state pass or establish general cold-state determinism. The exact records
and raw follow-up states remain in Downloads with the owner-ledger research.

The stage-inventory task continues independently. No task-owned Maven or native
diagnostic process remains active at this checkpoint; delegated work may create
its own bounded external builds. The lead owns subsequent target writes, Git
and validation. All commits remain local, and no playable CD module exists yet.

The verified PCM unit is committed locally as `89fc2b9a0`. Native-owner review
found one P2 selector gap: main gate-array mirror `A12043` decodes to register
03 but was excluded from ownership commits; sub alias `FF8203` was accepted by
the owner hook but omitted from CPU write pairs. All 1,194 recorded R3 commits
used canonical ports, so their evidence remains valid. Fix task
`sonic-cd-native-owner-alias-fix-20261023-r1` owns only the selector/derived-patch
and affected tests, plus a separate R4 build and controls. The PCM-native worker
uses the immutable R3 source snapshot rather than concurrently changing files.
The review's fifteen-file fingerprint was
`be13540d8915813ef8ff63d10c71dd6d5a514412afd3966637b73ff9a2dfbfce`.

Stage-data analysis returned two stable new tools with 23 passing Python tests.
It inventories all 210 regional MMD headers and verifies 69 R11A blob views and
29 tables against actual media and pinned consumer encodings. Other stages
remain header-only. Native chunk IDs are one-based, pointer high bytes can
select PLCs, and Sonic's DPLC consumes one verified alignment byte beyond the
raw art blob. Gameplay, decompression output, carry suffixes and timing remain
unproven. Fresh task `sonic-cd-stage-inventory-review-20261023-r1` is reviewing
the tool and its metadata before they become production-loader inputs.

The alias fix requires one additional frozen-file edit: the analyzer duplicated
the canonical-only ownership-address check and would reject corrected mirror
events. The lead admitted the minimal `native_owner_analyze.py` validation change
with cases in the already-owned analyzer test. Original addresses and CPU access
widths stay in events; long selection unions both native word legs, including
decoder boundaries. The R4 inventory and producer pins will include that change.
Other frozen files and immutable R3 evidence remain unchanged.

Stage-inventory review found one P2 encoding error before loader use. Its
generic `.nem` filename inference mislabeled Game Over and Time Over art.
`LoadShieldArt` selects five raw pointers and copies 256 longs, or 1,024 bytes.
Some consumed views continue into the following source blob: Time Over by
64 bytes and Time Warp Stars by 32. The following DMA reads 1,152 bytes from
the animated-art RAM buffer, so its final 128 bytes are resident RAM, not extra
file bytes or invented zeroes. Task
`sonic-cd-stage-inventory-encoding-fix-20261023-r1` owns the two tool files,
source-consumer proof, regression cases and a separate R2 metadata output.
The original metadata remains immutable and superseded for encoding decisions.
No production stage loader consumes it yet. Other reviewed R11A offsets,
one-based chunk and tagged-pointer rules, and the verified Sonic alignment byte
remain valid within their stated bounds. No Java/native process is owned by
the lead; the three delegated units continue independently.

The mirror-fix worker reports 32 passing native-owner Python/C tests, zero
skips, a regenerated patch and a separate single-worker R4 build with passing
ABI inspection. Four fresh native controls are running. The lead inspected the
R3-to-R4 selector, analyzer and test changes while those controls execute.
Selection now follows actual mirrored decoder maps and unions both word legs
for native long writes while retaining the original access identity. These
pending native controls are not yet a completed validation result, and the
owner tools remain uncommitted.

### Verified owner ledger R4

The final alias fix and analyzer validation passed 32 native-owner Python/C
tests with zero skips. Its compiled controlled oracle uses the pinned native
CPU helpers, mapped handler bodies and decoder markers. This certifies alias
selection within that controlled source model, not real-route alias exercise.
The lead reviewed the selector/analyzer/test delta against the immutable R3
snapshot. No further material issue was found in that bounded review.

The separate R4 build passed ELF/ABI inspection and four fresh 1,800-call
disabled/enabled boots, each with a 120-call restored continuation. All four
complete states are 2,035,641 bytes with SHA256
`097368d1cb17c9dd313ff8ac460a2e48fa1d3d421666d0806fa2b0f12eec43c8`.
The lead checked their full hashes, lengths, saved restore flags and exact
official AV/public CPU/RAM/continuation streams. Enabled capture, per-call
counts and restored event bytes match R3 exactly, without forcing the counts:
103,646 capture events, 1,194 ownership commits and 4,936 continuation events.

Real identity/layout rejection controls passed. Overflow dropped 766 events,
was rejected as incomplete evidence, and preserved complete state equality
against its disabled control plus matching AV. Native core compressed SHA256 is
`82af02e7080426f11d5618221a7b05d62565504cbe4caa698cf687d76077b515`;
patch SHA256 is
`de60a4ea90b6b1f51b02d1914562b76646ab793c6b9fccca48ac7da9004af769`.
The final sixteen-file input fingerprint is
`7d9ef07172a299309db8b95a440af1ac03f97bed80fd19e67b2b5675fa26e5e4`.
R3 source/artifacts remain unchanged. The R4 inventory binds the actual sources,
compiled producer dependencies, toolchain, patch and native build.

This unit provides source-model owner observations only. Title captures use
canonical ports; mirrors have controlled decoder proof. CDC DMA completion,
enabled level-3 timer pending, general 1M paths, later gameplay routes, other
regions and complete firmware/drive behavior remain uncertified. The historical
official checkpoint discrepancy remains unattributed despite the two matching
lead follow-up boots. There is no physical timing, V5 or gameplay-input authority.
No installed core, shared TraceChaser or Java target was changed by this unit.
The parent owns staging/local delivery; no owner job is running.

The generated native patch retains exact upstream context, including CRLF and
whitespace-only context lines. Its single path joins the existing native-patch
whitespace attribute policy; the patch bytes and tested source fingerprint are
unchanged. Ordinary source whitespace checks still apply to all other new files.

The owner unit is committed locally as `c6607c15c`, with its patch exported to
Downloads. New task `sonic-cd-stamp-service-20261023-r1` owns only the new
`service.graphics` package and tests for bounded source-derived graphics
service arithmetic, readbacks and restore. It must keep contention/alignment
approximations explicit and preserve the existing transform and owner evidence
as frozen inputs. The stage-encoding fix and PCM-native fixture task continue
on separate tool prefixes. No Maven job is active; parent compilation waits
for stable Java handoff.

### Verified R11A source and consumer inventory

The encoding fix returned two stable tools and a separate schema-v2 R2 JSON.
The lead reviewed the source/consumer classification delta and checked its
current producer binding plus all five raw consumer records for each region.
All five ShieldArtIndex entries copy 1,024 actual media bytes, with exact
adjacent-blob attribution where needed. The complete pointer table and 80-byte
native copy/DMA routine are matched, including macro expansion. The following
1,152-byte RAM upload records its unknown 128-byte resident tail separately.
No padding, guessed encoding or extra file read is supplied.

Genuine Nemesis declarations now require checked PLC/block entry/header
consumers; unlisted uses remain unverified. Per region, the 69 source blobs
have 36 Nemesis, five raw and 28 unknown encoding summaries. Ten `.nem`-named
blobs lost filename-only certification. R2 has 30 tables including the new
ShieldArtIndex. Decoder execution/read cursors and final decompressed output
remain outside this inventory proof.

All 61 Python tests passed. All 45 actual-media mutations rejected, and two
three-region CLI runs produced identical R2 metadata. Original JSON and exact
R1 tool/test snapshots remain preserved as superseded evidence. Current R2
metadata SHA256 is
`f7eedea40b127a09b31441134ff26c361beb787c346d903834133db4e1460cbf`.
The original review's other bounds still hold: all 210 regional stage headers,
one-based chunk addressing, pointer tags, source-pinned R11A views and the
actual Sonic art alignment byte. Non-R11A layouts are still header-only.

New task `sonic-cd-r11a-assets-20261023-r1` owns only the new `level` package
and tests for actual media-backed R11A decoding, using the corrected R2 evidence.
It has no GameModule, GameId, shared engine or creator API changes. The existing
graphics-service task owns a separate Java package; the lead will compile only
after stable handoff from Java writers. Native PCM fixtures returned eight
startup/channel-stop blocks and are under fresh read-only review
`sonic-cd-pcm-native-review-20261023-r1`. Gameplay-active PCM and active-PCM
checkpoint restoration remain missing. The lead owns their test-only Java
consumer. No native fixture or comparison row becomes runtime game input.

### Native PCM startup fixture handoff

Stage inventory is committed locally as `5c0f2eae2`. The native PCM unit
returned eleven stable tool/test files and eight source-native startup/stop
blocks, with complete RAM, channel fields, selectors and integer samples/deltas.
They contain 3,160 samples, 3,107 nonzero samples and three loop events. Seven
active blocks use channel 3; the final block is channel stop with the chip
still enabled, not chip disable. No clipping limit was reached.

All nineteen Python tests passed with zero skips. Nine fresh native controls
cover two official, two unaltered-source, two disabled, two enabled and one
overflow run on the same 3,000-call authored-input route. Each has 120 forward
calls plus restoration/replay. AV, pictures and public CPU/RAM views agree;
unmasked raw states agree within each binary. Both enabled payload sets match.
Fresh review `sonic-cd-pcm-native-review-20261023-r1` returned bounded PASS on
the eleven files and checked additional corruptions, typed ABI, source/build
identity, `.invis` storage and before-volume integer hook phases. Native cycles
and unity host volume are separately validated metadata, not hidden chip fields.
The manifest SHA256 is
`0236df320dcb3322a21263b2b771f108269a779bb6712445e75ba36a309141b4`.

The post-3,000 restore interval has no active PCM; active-PCM native restoration
is not established. Nor are active multichannel mixing, clipping, resampling,
physical timing, sound-driver or in-level audio parity. Normal PP jumps did
not activate PCM in this bounded route. New task
`sonic-cd-pcm-soundtest-route-20261023-r1` owns a separate route tool prefix and
fresh captures, using native source-derived menu/controller inputs to seek
broader natural music coverage. The completed core/tools and captures are frozen.

The lead authored a test-only binary admission helper and opt-in Java
comparisons for all eight original blocks. These tests are pending compilation;
their source is separate from the verified tool unit. The `level` asset-loader
and `service.graphics` writers are still active, so no Maven command is started
against partially authored Java. The pending comparison checks every represented
chip scalar, all wave RAM, every sample/delta and Java capture/restore, without
claiming a native active-PCM restoration interval. Commercial payloads remain
outside Git and are never a runtime data source.

The reviewed PCM tools are committed locally as `144cc091d`; their core patch
joins the existing exact-context whitespace attribute policy. The parent owns
three uncommitted test-only additions in `device.pcm`: `PcmNativeBlock`,
`TestPcmNativeBlock` and `TestCdPcmNativeContinuations`. The opt-in property is
`soniccd.pcm.fixtures`, pointing to the original controls-startup-jumps-r1 root.
Admission checks the complete binary extent, channel fields, service-counter
metadata, unity host volume, all RAM, every sample/delta and unused storage.
The comparisons use the pinned manifest and both repeated payload sets, then
exercise Java state restoration separately. Compilation/JUnit remain pending.
No Maven job is queued or running; future combined focused validation waits
for stable `level` and `service.graphics` handoffs. No pass is claimed for
these new Java test files.

The graphics-service unit returned six stable Java/test files, calling the
production transform and preserving explicit clock domains, busy-clear/wake/IRQ
ordering and RAM/cursor snapshots. Offline scalar calculations matched all
1,052 R4 operations and 1,800 rebases with zero mismatches. These calculations
are not Java/JUnit execution or hardware timing proof. Fresh task
`sonic-cd-stamp-service-pcm-tests-review-20261023-r1` is read-only on these six
files plus the parent's three PCM comparison/admission tests. The R11A loader
writer was asked to announce a stable Java freeze before remaining external
checks if possible, so one combined focused compile/test run can overlap them.
No Maven command has started; the parent still owns target and validation.

### Native music-preview and pending Java checkpoint

The controller-only Sound Test route succeeded on its first discovery schedule.
Pinned source derives Down, Down, Down, Left, Right, A; native UI shows PCM NO.10.
Its source command loads the Palmtree Panic PCM bank and queues Past music.
All nine fresh controls match retail Sound Test entry/current instruction bytes
and PC `FF3C30`. The read-only state search requires the entire public 68K RAM
hash, not an assumed savestate offset or authoritative gameplay value.

Eight main music blocks contain 1,623 nonzero samples, seven channel masks,
varied envelope/pan values and six natural loops. The checkpoint is active;
all 120 following native calls contain active PCM. Exactly 21,568 integer
summaries match after restoration. Four complete forward/replay blocks contain
526 nonzero samples and match across both enabled cold boots. All nine fresh
AV/picture/public CPU/RAM controls and complete same-binary raw states match.
The existing nineteen PCM producer tests passed without skips on the new capture.
No clipping sample occurred. This is retail music-preview evidence, not an
in-level audio, physical timing, full sound-driver or resampling certificate.

Fresh review `sonic-cd-pcm-music-evidence-review-20261023-r1` returned bounded
PASS on the read-only checker and actual new evidence. The lead checked its
unchanged source identity and all repeated main/forward/replay payload bindings.
Checker SHA256 is
`08d24f1b241ede033dbba59c9e96f5aa6744c5d6170acb478c5eccbf5d5f3825`;
manifest SHA256 is
`a0b1355233f41659545f778f044ae39dfa0de55a5ff7a5a8293070036e27661b`.

The prior nine-file Java review also returned bounded PASS for the six graphics
service files and three startup PCM comparison/admission files. It independently
reproduced the 1,052 graphics-operation/1,800-rebase scalar boundaries and
validated all sixteen startup payloads plus mutation controls. This is static
review/calculation evidence; Java execution remains pending. The parent then
added `TestCdPcmNativeMusic`, using `soniccd.pcm.musicFixtures`, to compare all
eight music blocks and the four active native restore blocks through the same
test-only binary admission helper. The original nine reviewed files are unchanged.
Combined focused validation still awaits a stable R11A loader source handoff.

### Verified R11A loader, graphics service and PCM comparisons

At `92825f5b1` plus the frozen new units, the lead ran this combined focused
command. It compiled 4,232 production and 4,341 test sources, then passed all
87 tests across thirteen classes, with zero failures, errors or skips. Maven
took 1 minute 28 seconds; queue admission reported zero wait. Session `20024`
is finished and there is no live Maven job. This is bounded focused validation,
not a broad smoke/full-suite pass or stock game integration. The new packages
have no existing runtime callers; the shared Nemesis implementation is unchanged.

```bash
python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestCdBoundedAssets,TestCdObjectPlacements,TestCdR11aLoadPlan,TestCdR11aRetailAssets,TestCdR11aTerrain,TestCdShieldArt,TestCdSonicVisualAssets,TestCdStampOperationRequest,TestCdStampGraphicsService,TestCdStampOwnerLedgerSourceModel,TestPcmNativeBlock,TestCdPcmNativeContinuations,TestCdPcmNativeMusic' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} -Dsoniccd.stamp.ownerLedger=${SCD_OWNER_LEDGER} -Dsoniccd.pcm.fixtures=${SCD_PCM_STARTUP} -Dsoniccd.pcm.musicFixtures=${SCD_PCM_MUSIC} test -B
```

The R11A unit's 26 invocations include actual supplied USA/Japan/Europe media.
Authentication precedes decode; known whole-disc content, variant, stage/file
join, original Word RAM domain and exact immutable MMD bytes are checked.
Decoded blocks/tiles, layouts, collision, palettes, placements, load metadata,
Sonic visual views and bounded raw shield copies are detached immutable data.
Actual Java decoder cursors and complete output hashes match the independent
reference. Unknown resident tails and other 207 regional stage paths remain
unsupported. Eight Python reference tests and all three regional checks passed
separately. Fresh review `sonic-cd-r11a-assets-review-20261023-r1` returned
bounded PASS on twenty files. The lead verified its unchanged aggregate SHA256
`0c1de79bc575b3a2467f0dd06024171a02848da6873eba1304d976c3f67418ca`.

The graphics service's 39 invocations include the explicit source-model ledger
check over 1,052 operations and 1,800 rebases. Request/clock/ownership validation,
ceil/ahead service, finish ordering, stale handles, RAM snapshots and replay
passed. The model retains native contention/alignment approximations; this is
not physical timing, full register mutation semantics or firmware certification.

The PCM consumer's 22 invocations cover two binary admission checks, eight
startup/stop blocks, eight music blocks and four active native restore blocks.
All 5,309 distinct captured integer samples/deltas, complete represented chip
state and wave RAM match Java exactly. Java checkpoint replay also passed.
This establishes the bounded startup and retail music-preview device comparisons,
not in-level audio, clipping, resampling or full-driver scheduling parity.

Task `sonic-cd-stock-admission-audit-20261023-r1` is read-only on actual launch,
module, media and API obligations for the next real disc consumer. It writes
only external research. The next integration still needs a deliberate engine
adapter and stock bootstrap; no GameId.SCD or playable game module exists yet.
All implementation remains local with no merge, push or main-workspace mutation.
