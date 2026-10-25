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


### Static stock admission in progress

The verified units were saved locally as `08bc9aa91` (R11A assets),
`78f4fc226` (bounded graphics service), and `56beba899` (exact PCM comparisons).
Their three source patches and refreshed index/checkpoint are in the external
Downloads bundle. The worktree branch still has no upstream. No main checkout,
merge, push or PR action was taken.

Read-only task `sonic-cd-stock-admission-audit-20261023-r1` completed its external
engineering map. Current implementation ownership at base `56beba899` is:

- `sonic-cd-engine-level-adapter-20261023-r1`: new native-to-engine level geometry
  and tests, preserving original CD collision words separately from rendering.
- `sonic-cd-source-factory-migration-20261023-r1`: frozen 61-file canonical source
  factory migration, maintained consumers and tests. Its source-only handoff hash
  is `4d31740f6ab637e4a41d62dcb5855ca9be8f65c2de642aaf04d4720f647efb56`.
  Independent task `sonic-cd-source-factory-review-20261023-r1` is reviewing it.
- `sonic-cd-static-module-20261023-r1`: new normal CD module, static profile,
  controlled terrain presentation and tests. No native actors or title execution.
- Lead: detached `CdGameDataSource`/`CdStockLaunch`, public stock identity with
  cartridge capability separated, rejection at unsupported ROM/audio/editor/trace
  consumers, existing headless/capture boot seam, tests and eventual API pin.

These pending changes have not been compiled or executed. The earlier 87-test
result applies to their unchanged dependencies, not this admission unit. There
is no live or queued Maven job. Candidate signature pins remain deliberately
stale until all source handoffs are stable and compilation succeeds.

The change-based category plan at this checkpoint selects all smoke categories,
3,654 candidate classes and guards because public/shared factories changed.
This is routine smoke selection, not the public-release full suite. Combined
broad execution waits until focused fixes and API generation are complete.
Display-free preflight initially failed because the default Lua was not 5.4;
explicit `LUA_BIN=/usr/bin/lua5.4` fixed it. The repaired invocation passed Java,
Lua and PowerShell prerequisites with no tests executed:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 56beba899 --preflight
```

`TestSonicCdDirectStageBoot` is authored for actual supplied media and explicit
surfaceless EGL. It uses `GameplayCaptureSession`'s ordinary rendering passes,
checks visible terrain and same-world camera/level restore, and can save images
only when an external capture root is supplied. It has not run. Existing rewind
snapshots do not bind cross-registry source identity; this remains an open
acceptance issue, separate from module source-rebinding rejection. No CD-07 or
playable-game/parity completion is claimed.


The source-factory review returned bounded PASS with no P1/P2 finding. The lead
recomputed the frozen 61-file handoff fingerprint and it still matches exactly.
Expected migration-only API delta is eight obsolete method entries removed and
two canonical interface factories changed from default to abstract; the later
SCD identity/capability addition is a separate intended pin delta.

The seven-file detached preparation/engine geometry review also returned bounded
PASS. Its ordered path/NUL/bytes/NUL SHA256 is
`1d46b97b4aa5ae28a0e128e515e1b4881c52544ca2808a830032201718b9ab61`, independently
recomputed by the lead unchanged. Both reviews are source inspection only.
Unloaded geometry stays unavailable, whole-block serialization of those cells
is unsupported, and native collision/actor execution remains outside this slice.
The adapter task is complete; the static module task is the only active child.
No task-owned Maven job has started for these pending changes.


### Combined admission validation queued, 24 October 2026

The static module source handoff is frozen: ten files, aggregate sorted
path/NUL/bytes/NUL SHA256
`232d03415237dbac9591307c46e55a758e485f0cc121eca4ca546cdf4e04cf02`, verified
unchanged by the lead. The existing transform hook publishes decoded graphics;
no additional shared graphics hook was needed. Independent task
`sonic-cd-static-admission-review-20261023-r1` is reviewing the module and lead's
shared stock/boot changes without running Java or changing source.

One focused Maven job is alive in command session `12065`. It is waiting for
shared admission, not failing or executing tests yet. Keep this request alive;
do not submit a duplicate. The command is:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdStockPreparation,TestSonicCdLevelGeometry,TestSonicCdLevelRetail,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad,TestStockGameCapabilityMembership,TestTraceToolRomLocations,TestRomManagerGameResolution,TestRomManagerLocationResolution,TestGameDataSourceSharedFetches,TestGameDataSourceSessionOwnership,TestDelegatingGameModuleCoversInterface,TestGameModuleProfiles,TestLazyMappingHolder,TestLoadedLevelSceneSourceCapability,TestKis2GamePatchResolution,TestHeadlessAudioOwnership,TestNoProvisionalModApiShims' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

All Java is frozen for this run. Compilation/runtime results and candidate pin
remain pending. Next: inspect this job's exact outcome, fix concrete failures,
then generate/inspect the compiled candidate pin and execute the explicit EGL
render check. Required broad smoke remains after focused fixes. The lead also
read-only verified existing root `s1.gen`, `s2.gen`, `s3k.gen` against the
repository's CRC32/SHA-1 pairs for later affected cartridge/S3K validation; no
ROM was copied, renamed or linked.


### Verified normal static entry and API migration, 24 October 2026

The 25-file module/shared-admission review returned bounded PASS with no new
P1/P2 findings, aggregate sorted path/NUL/bytes/NUL SHA256
`cf3f1f1c042d39616be963a57881fbbcbb331a3405f319c914779be348c49782`.
Its proof was source inspection, separate from the execution below.

All 4,244 production sources compiled. The first two focused invocations then
stopped in test compilation: `RewindRoundTripHarness.registryFor` and
`TestScalarOnlyCodecDeletion.invokeGenericRecreateWithState` had exhaustive
cartridge object-registry switches. Each now explicitly rejects SCD; neither
harness supplies native CD object execution. These were new enum-admission
compile failures, not baseline test failures. No JUnit ran in those attempts.
Session `12065` waited 223 seconds and ran Maven 77 seconds; repaired session
`79802` waited zero and stopped after 22.600 seconds.

The repaired focused selection in session `87412` compiled all 4,349 test sources
and passed 98 tests with zero failures, errors or skips. Maven took 34.613 seconds,
queue wait zero; completion was 00:05:12 BST. The eighteen-class selector is the
same command recorded above. Expected rejection of unsupported native title
entry is exercised in all three load tests; its logged exceptions are asserted
negative cases, not unreported load failures. Actual supplied regional media ran.

The lead generated the mutable candidate pin from the just-compiled classpath,
using `ModApiSignatureSurface --snapshot` under the shared build-activity lease.
Its exact delta was ten removed entries (eight obsolete methods and two previous
default factory signatures) and four additions (two abstract canonical factories,
SCD identity and optional cartridge mapping). The descriptor and `CURRENT=0.7.0`
remain unchanged; standalone/compatibility guidance and the runtime description
now describe source factories. Historical design assumptions remain labelled.

Session `41463` then passed 34 tests with zero failures, errors or skips: 31 API
signature/pin/release/runtime policy checks and three regional real GL checks.
It waited 35 seconds; Maven took 34.685 seconds, completion 00:07:21 BST:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdDirectStageBoot,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} -Dsoniccd.static.capture.root=${SCD_STATIC_CAPTURES} -Dopenggf.headless.gl=surfaceless-egl test -B
```

The lead inspected all three actual 320x224 PNGs. Each shows Palmtree Panic
terrain/palette through the ordinary world, loop, load and capture renderer;
all camera-change/restore images and subsequent held presentations match their
assertions. Captures are external under `native/engine-static-r1` in the Downloads
bundle. No native Sonic/object execution, parallax/pixel parity, audio service,
firmware, complete entry route or USA MVP result is claimed. The
[partial act matrix](../validation/levels/scd-palmtree-panic-act1-present.md)
records every outstanding coverage dimension and cross-registry snapshot gap.

All newly introduced documentation link targets resolve. Two targets already
absent at base `56beba899` remain absent in the workflow index:
`examples/starpost-valley/generate_resources.py` and optional
`tools/tracechaser/bizhawk-headless/README.md#diagnostic-rendered-frames`.
They were attributed against base, not repaired as unrelated work.

The next required check is the change-based 3,654-class smoke selection plus
guards at base `56beba899`, with verified original root cartridge images and
surfaceless EGL. Expected cost is roughly an hour of execution, with queue delay
unknown. It will use a 90-minute execution budget excluding waits, the runner's
10-minute no-output stop, and fail visibly on launcher/compilation/source changes.
Ordinary batches retain their full selection and release admission between at
most fifty classes; this is not the public-release full suite. No smoke result
has been claimed yet. Do not mutate the tested tree while that runner is alive.

Task `sonic-cd-r11a-player-bringup-20261024-r1` is read-only on concrete native
player/collision prerequisites and writes external research only, independently
of parent validation. No further design council or second game runner is planned.

### Broad run limits and focused corrections, 24 October 2026

Run `20261023T231338Z-666ba6f3` tested clean commit `5998f6290`, tree
fingerprint `29413264d95825f801f675c0cd780be3d639e64852da1b0869423a3bbb8908c4`,
against base `56beba899`. The unchanged 3,654-class smoke selection hit its
90-minute execution budget during ordinary batch 67/74; queue waits were
excluded. Preparation passed in 26.45s and recorded ordinary execution was
5,373.74s. The retained result is **incomplete**, with 36,152 tests, five
failures, zero errors and 110 skips. Guards were not reached. No full-suite or
completed-smoke pass is claimed. The tail was executing
`TestMhzKnucklesColdRouteCapture` when the invocation budget stopped the process.

Four failures are the new optional-media tests in
`TestSonicCdStockPreparation`: three `requestedVariantRejectsAnotherRegion`
invocations and `forcedContentRecordRejectsAnotherDiscEvenWhenStageProgramIsIdentical`.
Their media assumption ran inside `assertThrows(IOException.class, ...)`,
which incorrectly caught an absent-fixture abort as a failed assertion. The
fixture lookup now occurs before the exception assertion. Focused validation
must exercise this class both without the property (explicit media skips)
and with actual supplied media (all cases execute).

The fifth failure is
`TestHczMinibossNativeRendering#allOrbitPhasesMatchRomSpriteCompositionInNativePixels`,
the line-53 `GLFW must initialize for pixel verification` assertion in the
display-free environment. The exact method on detached baseline `56beba899`
also fails at that assertion, one test with zero skips/errors. Current session
`67038` reproduced the exact test identity, exception, message and source line;
Java `25.0.5.0.2`, the absolute S3K ROM property and surfaceless-EGL property
matched baseline. It waited zero; Maven took 27.293s. This is an existing
display-free GLFW limitation. No graphics helper or desktop environment was
changed. The clean detached baseline was removed after inspecting its generated
resource links; the original ROM/config/reference targets were untouched.

All 110 skip reasons were inspected: optional diagnostics/benchmarks/captures,
GLFW/OpenAL availability, opt-in CD media/native fixtures and source-dependent
route omissions. None reports a missing required cartridge ROM. Regional CD
and native-device checks have their separately recorded focused runs; their
omission from this broad command is explicit. Consumed runner diagnostics are
acknowledged after inspection. Remaining ordinary/guard coverage and affected
checks for subsequent source changes remain open, not a cached passing result.

The corrected optional-media class passed in session `89754` without the
property: nine tests, zero failures/errors, seven explicit media skips (the
two ROM-free cases passed), 12s waiting and 29.309s Maven execution. Session
`81396` then passed all nine with supplied regional media and zero skips,
165s waiting and 34.310s Maven execution:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdStockPreparation' test -B
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 ${OPENGGF_MAIN}/tools/testing/maven_queue.py --lean -Dmse=off '-Dtest=TestSonicCdStockPreparation' -Dsoniccd.media.root=${SCD_MEDIA_ROOT} test -B
```

All individual skip identities were inspected. These checks resolve the four
new fixture-test failures; they do not fill the timed-out ordinary/guard coverage.

## R11A player components and world-owned snapshots — 24 October 2026

The private tree imports source-reviewed finite components from the external
`world-snapshot-guard-r1`, `collision-placement-r2`, `player-terrain-r1`,
`player-assets-r1`, `player-art-runtime-r1` and `player-animation-r1` candidates.
Runtime data continues through authenticated supplied discs. Native placement
keeps original material/solidity words independent of drawing and delays shape
ID reads until the native gates. Floor/wall probes retain signed distances and
optional angle writes. Bounded animation and V-int art transfer own their state
without executing a player or issuing draw-time cues.

Command examples below use `OPENGGF_MAIN` for the original checkout and
`SCD_MEDIA_ROOT` for the supplied regional media directory. Exact machine-specific
invocations remain in the external task plan.

On parent `dbe9b40be` plus these 30 imported files, the first combined focused
check passed 130 tests, zero failures/errors/skips, with Java 25 and all three
supplied discs. Command from the private cwd, with DISPLAY/WAYLAND_DISPLAY unset
and LUA_BIN=/usr/bin/lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdTerrainCollision,TestCdPlayerTerrain,TestCdR11aPlayerAssets,TestCdR11aRetailAssets,TestCdSonicVisualAssets,TestCdShieldArt,TestCdR11aLoadPlan,TestCdR11aTerrain,TestCdBoundedAssets,TestCdObjectPlacements,TestCdPlayerArtRuntime,TestCdPlayerAnimation,TestSonicCdStockPreparation,TestSonicCdLevelGeometry,TestSonicCdLevelRetail,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

Queue wait was 21 seconds; Maven execution was 1 minute 35 seconds. All 17
completed XML reports and their media/Java properties were inspected. This
includes real-media foreign-world snapshot rejection; shared rewind/API/native
publication and real-GL regressions remain pending at this checkpoint. Scope is
an opaque WorldSession identity carried by engine-created layouts. Full/course
restore checks it before adapters, callbacks, RNG or character selection.
Unscoped public private registries retain Map roundtrips; reconstructing a Map
cannot confer engine-world authority. No public signature change is intended.

Corrected native reference tools are promoted under `tools/soniccd`: R2 output
admission discovers the bound producer repository's registered worktrees; R2
charge comparison checks release-row latches and displacement rather than stored
positive velocity. The recipes are `player_reference_retail_route.json`,
`player_charge_peelout.json` and `player_charge_spindash.json`, supplied explicitly
to the producer's scenario argument. Original external sources/manifests remain
unchanged. Promoted-path Python checks passed all 33 player-reference and 20 charge-checker
cases, with zero skips; no new native boot was needed for this path integration. Earlier movement captures retain ten unexplained
private-state replay bytes; the later charge captures match complete states.
Exact instruction phase and immediate threshold-release coverage remain unknown.

Cold entry has a source owner: RunMMD (CD Initial Program/IPX.asm:1307-1341)
clears [FF2000,FFFB00), including the complete Sonic CPU art window and comparison
bytes. Both R11A GameInit branches call InitVDP, whose full VRAM DMA clears the
resident window. The unique clear/call/setup bytes were checked against all
three supplied program images. These are cold transition facts; the finite art
owner still requires explicit complete ingress. Intervening PLC/DMA writers,
restart/carry boundaries and actual engine initialization remain separate proof
and implementation obligations. No initial cue, zero tail or timing is fitted
from a native capture. Collision/animation/art component passes do not enable
Sonic execution, actor touch, camera scrolling, time travel or complete act parity.

### Shared, charge and rendered verification at the player-component checkpoint

The unchanged shared/world component state passed 296 tests across 31 actual
classes (30 selectors), zero failures/errors/skips. API surface/pin policy passed
without changing any signature pin. Cartridge SHA-1/CRC32 identities and all
selected XML properties were checked. Command from the private tree, with
DISPLAY/WAYLAND_DISPLAY unset and LUA_BIN=/usr/bin/lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestRewindRegistry,TestCompositeSnapshot,TestCompositeSnapshotOwnership,TestRewindBenchmarkSizeEstimator,TestGameplayModeContextRewindRegistry,TestGameDataSourceSessionOwnership,TestEditorModeContextLifecycle,TestOwnerBoundGamePatch,TestS1S2PlcSessionOwnership,TestModApiSignatureSurface,TestModApiPinPolicy,TestModApiReleasePolicy,TestModApiRuntimePolicy,TestRewindController,TestRewindProfilerAttribution,TestSegmentCache,TestInMemoryKeyframeStore,TestPlaybackController,TestRewindDeterminismAuditor,TestGameplayInputBranchTool,TestModZoneEventLifecycle,TestSonic1SwitchStateRewindAdapter,TestFbzBadnikGraphRewind,TestS3kIczIceObjectRewind,TestS3kCnzLocalMechanicsRewind,TestNativeRewindAdapterPublication,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
```

Session 77545 waited 244 seconds; Maven execution was 37.138 seconds. The
separate real-GL/audio/charge invocation (29219) executed 86 cases, with two
charge assertion failures and zero errors/skips. All three regional real-EGL
terrain/restore cases and both headless audio ownership cases passed. Command:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerCharge,TestSonicCdDirectStageBoot,TestHeadlessAudioOwnership' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
```

That invocation waited zero seconds and executed Maven for 1m25s. Source
attribution found two wrong authored expectations: starting at +/−4500 adds
native +/−100 to +/−4600, below the shoes cap +/−4608. Main.asm:984-1014 and
production agree; no production edit was needed. Corrected the expected
literals and added exact-cap +/−4508 and just-crossing +/−4509 inputs. The
entire charge class then passed 85 tests, zero failures/errors/skips (51184,
zero queue wait, Maven 26.716s):

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerCharge' test -B
```

These are separate bounded selections. The failed 86-case invocation is not
relabeled green or added to the successful charge total. Its five unchanged
render/audio positives remain evidence for those exact cases. The 130-component
and 296-shared checks retain their original tested inputs; later changes add
only the isolated charge owner, its corrected tests, standalone reference tools
and prose. No repeated unchanged broad coverage was launched.

The change-based planner against integration base `56beba899` selects all
3,660 smoke candidates in 74 ordinary batches plus guards for this working
state. That broad combined coverage remains pending during continuing player
implementation. Its prior 90-minute invocation was incomplete, and these
focused checks do not fill its unexecuted tail. A fresh complete smoke run needs
an execution budget informed by that measured cost; do not launch the public
release-only full suite. This is a private implementation checkpoint, not a
completed delivery or complete game/act/parity claim.

A follow-up cold-art source audit checked the intervening setup/PLC writers,
load/fade/lag interrupt selectors and conditional first V-int sequence. At the
completed cold/default-start cut before Level_MainLoop, both complete $300-byte
windows remain zero, last-frame is zero and dirty is clean; PLC work is empty.
The first two normal services see frame zero and issue no player DMA. An actual
later animation publication selects the first changed cue. Full details and
writer extents remain in the external `research/player-art-cold-ingress.md`.
That resolves the bounded source ingress question, not engine initialization:
no cold-art factory or lifecycle hook has been added. In-program restart,
checkpoint, carry and restore must retain their own state and never reuse that
cold clear. Native timing and live player/render integration remain open.

## Native player math and paired ground collision — 24 October 2026

At parent `d075b8633` plus four new files, the source-reviewed finite math and
ground-collision owners compiled and passed the complete selected checks:
110 tests across six classes, zero failures/errors/skips. Session3193 waited
157 seconds; Maven execution was1m37s. Its completed command returned exit0;
a later attempt to poll the already-closed session found no process. All six
current XML reports, counts and Java25 properties were inspected. This run
contains68 new math/ground cases and42 affected dependency regressions; do not
add repeated dependency counts to earlier coverage as new unique tests.

From the private cwd, with DISPLAY/WAYLAND_DISPLAY unset and
LUA_BIN=/usr/bin/lua5.4 (`OPENGGF_MAIN` denotes the original checkout):

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerMath,TestCdPlayerGroundCollision,TestCdPlayerTerrain,TestCdTerrainCollision,TestCdR11aTerrain,TestCdR11aPlayerAssets' test -B
```

`CdPlayerMath` consumes the actual supplied player's sine/arctangent views.
It retains low-byte/word input widths, unsigned magnitudes after NEG.W,
quotient256 and the returned angle word, including0100; byte projection is
explicit. Shared trig tables and byte-return behavior were rejected as different
asset/return contracts. No shared math implementation changed.

`CdPlayerGroundCollision` models one Player_GroundCol call over the actual native
probes: object bypass, signed quadrant rounding, four paired sensor orders,
secondary-on-tie selection, optional angle writes, signed radius bytes,
14-pixel alignment/stick/detach branches, and integer-word position writes that
retain all fraction bits. Unknown backing throws without a partially published
actor. The interleaved unused movement routines were excluded. Both owners are
immutable/pure and neither advances a player frame, clock or resource service.

Source labels are CalcSine/CalcAngle (Level/Functions (Misc).asm:69-219) and
Player_GroundCol/Player_PickSensor/walking routines (Level/Collision Floor.asm:
30-146,227-454), at `1ecc5f3a`. External handoffs and independent bounded reviews
retain exact source hashes in `research/player-math*.md` and
`research/player-ground-collision*.md` in the private task bundle. The tests use
constructed admitted asset/terrain bytes, with no runtime disassembly, media,
emulator or reference-state input. No exhaustive4billion-input or native timing
claim is made. Normal locomotion, actual actor/render binding and complete-game
parity remain open; these components leave the stock entry held and static.

## Disc-backed Sonic visual conversion and render acceptance — 24 October 2026

The immutable CD mapping/art views now convert to existing renderer data.
Five-byte piece order, signed X/Y bytes, dimensions and tile attributes remain
native. All 2171 source patterns include the actual alignment byte; all 24
resident tiles come from explicit resident state. Mutable engine patterns are
fresh per call/world. The data bundle uses a caller-provided virtual atlas base,
not hardware VRAM F000, and deliberately supplies no Draw-time DPLC metadata.
The existing CD art owner remains the sole cue/dirty/fixed-DMA producer.

At parent `15bea2cf8` plus four new files, session 15197 compiled and passed
37 tests, zero failures/errors/skips, with Java 25, actual media for all three
releases and display-free EGL. The completed XML reports and media/GL properties
were inspected. Queue wait was 11 seconds; Maven execution was 1m27s. From the
private cwd, with DISPLAY/WAYLAND_DISPLAY unset and LUA_BIN=/usr/bin/lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerVisualAdapter,TestCdPlayerArtRuntime,TestCdSonicVisualAssets,TestCdPlayerRetailComponents,TestCdPlayerVisualRendering' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.playerVisual.capture=true test -B
```

The 15 adapter tests cover format and mutable-storage ownership; 16 existing
art/asset regressions cover its dependencies. Three retail cases join the
actual data to contact, math and art consumers: start (64,1899) resolves to
(64,1898), angle FC and buffers FC/0; real trig preserves its signed asymmetry
and angle word 0100. These are explicit component calls, not a native frame.
Three GL cases boot the normal static world, then draw selected poses through
PlayerSpriteRenderer. Claim/restore all 24 tiles before drawing. A nonempty
frame on zero residency stays transparent; only explicit service makes pixels
appear. Changes stay inside the CPU opaque mapping footprint. Repeated draws
preserve art state, and restoring both logical state and the GPU bank restores
the exact image without issuing a cue. Renderer cache -1 stays distinct from
native last-frame 1.

Six 320x224 PNGs were exported byte-for-byte to the private external task bundle,
`native/engine-player-visuals-r1`, then their consumed target folder was removed.
Poses 1/2 have identical pixels/PNG bytes across the three supplied releases.
They remain queued for morning visual QA; no image was emitted overnight.

This is fixed-pose visual-component evidence over a static stage. No actor is
registered, camera focus stays null, and the stock profile still holds. There
is no claim of live player movement, source-native SAT/occlusion/culling,
physical DMA timing, complete opening-route or whole-game parity. The ongoing
mode/body/frame work must supply those consumers through the existing loop.

## Native player MODE and body calls — 24 October 2026

The immutable MODE owner now composes actual CD charge, trig, terrain probes
and paired ground collision for the four normal ground/air/roll/jump dispatches.
It preserves complete centre fractions, separate prior actor angle copies and
source global buffers. Jump return and unfinished KillPlayer continuations are
typed boundaries; the owner performs neither predispatch charge advancement nor
Main-tail animation/history/angle copying. Unsupported native predicates reject
rather than publishing an incomplete result. A caller-visible local register
effect is explicit: ObjGetFloorDist clobbers d5 to 13 on the path that reaches
it; later ground settling uses 13 without changing global acceleration 12.

Astra's source review found one production error and two authored expectation
errors before import. Downward landing read the low velocity byte instead of
the first byte of the native big-endian word; the correction changes only that
extraction. A side-air expectation had dropped C000 fractions, and a roll death
boundary expectation omitted the preceding five-pixel unroll. Their production
paths already behaved as the source requires. Frozen R1 and R2 external sources
and reports remain separate, preserving the rejected interpretation.

At parent `1fe96ac59` plus the imported MODE and body sources, parent62385 ran
corrected distinguishing tests against R1 production: three tests, exactly two
expected assertion failures, zero errors/skips, Java25. The 0138 landing row
passed; 01F8 wrongly remained airborne, and the two-distance -10 case wrongly
landed. After replacing only movement production with R2, parent6081 compiled
and passed all78 MODE cases plus58 Init/Main body cases:136 tests, zero
failures/errors/skips. Queue wait0s for both; Maven execution1m22s and1m26s.
Both completed XML identities and Java25 properties were inspected. Commands
from the private worktree, with display variables unset and Lua5.4 selected:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=com.openggf.game.soniccd.player.TestCdPlayerMovement#postGravity0138And01F8ShareHighMemoryByteLandingThreshold+bothFloorSensorsBelowNegativeNineRejectLanding' test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=com.openggf.game.soniccd.player.TestCdPlayerMovement,com.openggf.game.soniccd.level.TestCdPlayerBody' test -B
```

The body composes predispatch, routine0 Init-only or admitted routine2 Main,
source camera extension, control-lock retention, display eligibility, complete
history bytes, angle copies, animation and the explicitly empty development
touch scan. Its58 passing cases are constructed-data tests, not a retail route;
independent Body source review and normal actor/frame integration remain pending
at this checkpoint. All effects are staged until complete return; sounds stay
typed observations with no driver playback. No player is yet registered in the
normal stock world. Broad smoke remains incomplete, and these finite results
establish neither a playable opening route nor USA/full-regional parity.

## Complete player-slot and serviced-HOLD boundaries — 24 October 2026

Internal NativePlayableSlot dispatch runs once before all generic player setup,
input writes, physics and tails. Scripted, warm-up and movement-only helper
entries reject native participants; nonparticipants keep their existing path.
ControlledVBlankOnlyOwner allows NORMAL_PAUSE, LAG and SPECIAL_STAGE_PAUSE held
rows through the existing PLC/game-VBlank/hardware timing boundary, without a
player, timer or level tick. Non-opt-in and null-held owners retain unserviced
HOLD. Runtime-owned audio starts only after the opt-in decision/phase validates.

Source review exposed two inherited PLC token leaks made reachable by held
service: failed fade preclaim before the wrapper's try/finally, and failed
preparation/final publication before the token becomes finished. Acquisition
and owned wrappers now retire only their failed token, drop success callbacks,
consume its one-shot declarations and preserve primary/suppressed failures.
They never retry service/preparation/publication, close a retained fade, reset
queues/clocks, or detach a later replay token. Raw manually held tokens retain
their existing missing-preparation -> prepare -> finish correction contract.
This is coordinator cleanup, not rollback of partially serviced native work;
device owners still need complete rollback or closed admission/rebuild.

Parent63507 executed three regressions against the original coordinator:
2 assertion failures and1 stranded-token error, zero skips, Java25. Parent60554
initially repeated the combined selection with stale compiled coordinator bytes:
177 tests,9 failures/11 errors,0 skips; source/class identity exposed preserved
import mtime as the cause. That run does not validate the candidate. Updating
only the changed source timestamp triggered normal recompilation. Parent39232
then passed177 tests across16 actual classes, zero failures/errors/skips,
Java25 with actual S1/S2/S3K paths and surfaceless EGL: queue13s/Maven1m29s.
All16 XML identities/properties and the new compiled cleanup helper were checked.
The selection covers held/slot/PLC behavior, existing input/dispatch ordering,
API signatures and all four required S3K selectors (both level-loading classes).
Astra independently found no remaining P1/P2 in the bounded cleanup source.

Parent25620 added two missing runtime-owned audio ordering/invalid-phase cases
and the typed Body Display cut. It passed19 held cases and60 Body cases:79 tests,
zero failures/errors/skips, Java25, queue14s/Maven1m27s. DrawObject clears sprite
bit7 inside Display; DrawObjects sets it later after the camera pass. Both writes
belong to the queue owner. DisplayEligibility now retains the exact pre-tail
centre words, Y radius and sprite flags, while DisplayPublication retains the
completed pose. A constructed tunnel test distinguishes radius19/Y76 from
radius14/Y81ABCD after SpecialChunks. Fable's independent Body review found no
P1/code P2; its prose correction and suggested metadata cut are incorporated.

Commands use the current main queue wrapper from the private worktree with
DISPLAY/WAYLAND_DISPLAY unset and LUA_BIN selecting Lua5.4:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off '-Dtest=TestPlcOwnedIterationFailureCleanup#failedFadePreclaimDiscardsCompletionAndRetainsFadeWithoutReset+failedBeforeFadeVblankKeepsPrimaryAndMissingPreparationDiagnostic+finalPublicationFailureIsNotRetriedAndDoesNotRequireReset' test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestNativePlayableSlotDispatch,TestControlledVBlankOnlyOwner,TestPlcOwnedIterationFailureCleanup,TestPlcFrameLifecycleCoordinator,TestPlcLifecycleDriverParity,TestControlledFrameRuntime,TestSpriteManagerGameplayInputFilter,TestSpriteManagerUpdateOrder,TestSpriteManagerCollisionOrder,TestSpriteManagerMainTailsTailsDispatch,TestModApiSignatureSurface,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerBody,TestControlledVBlankOnlyOwner' test -B
```

These are bounded local checks, not a broad-smoke or native gameplay certificate.
The normal stock CD entry remains static while frame/actor/camera integration
continues. Remaining exceptional held-tail/comparison-window combinations are
source-reviewed but lack direct injected-failure fixtures. No public signature
pin, version, shared GameLoop switch or game-name carve-out was added.

## Explicit terrain-player world, 24 October 2026

The named `terrainOnlyDevelopmentPlayer()` module and
`bootSonicCdTerrainPlayer(source)` host now join the authenticated R11A source
to the normal WorldSession/load/GameLoop, one registered native Sonic slot,
source V-int input/art service, MODE/Main and exact foreground arithmetic.
Default construction remains STATIC/HOLD with no actors. No title entry,
objects, rings, HUD, native music driver or full-act completion is implied.
The supported route and remaining products are in the
[act matrix](../validation/levels/scd-palmtree-panic-act1-present.md).

Frame state has disjoint capture owners: actor Body/history/display/packet,
module Environment/art/pause/clock and the existing parallax handler's source
camera. Engine snapshots retain their world scope; a restored source owner
reprojects camera/actor and all 24 resident tiles without servicing a cue.
Only the existing controlled-frame/PLC/native-slot boundaries execute gameplay;
drawing never samples input, advances animation or services DPLCs. The internal
suppression bridge supplies actual neutral held input when the host suppresses
input; native taps still derive from the last controller read.

Native input after the omitted fade is not proved zero by LevelStart's earlier
clear: fade V-ints read it again. Raw-zero ingress is explicitly DEV policy
(no buttons held at the last represented fade read); logical input is cleared
by the source. Controls are unlocked because title is omitted. PauseMusic and
UnpauseMusic requests/latch are omitted DEV audio; retained Body sounds are
not a complete service/frame audio ledger. Absolute native V-int seed remains
unknown; the runtime exposes a delta from this admitted cut separately.

### Reviews, distinguishing failures and repairs

Astra's foreground review found no P1/P2. Opus's frame review found no P1 and
identified an invalid zero arctangent fixture plus the input/audio declarations
above. The corrected real-decoder fixture passed 74 frame cases; production
arithmetic was unchanged. Fable's actor review and Astra's admission review
each found one P2. Their frozen source mirrors and reports remain externally
preserved, with no repeated unchanged review round.

- A serviced Body failure left the engine owner open even though V-int/input/
  art/timers had advanced. The original actor failed all three regional death-
  boundary regressions (parent 31241: three expected assertion failures, no
  errors/skips). A package-private entitlement preflight now distinguishes an
  unserviced protocol call from a failed serviced slot. The latter closes
  admission and clears pending sprite publication; constructor/rebind bank
  failures also close rather than retry a potentially partial upload. No GPU
  rollback or complete fault-injection coverage is claimed.
- Retained-level reset unbound the presentation before background dimensions
  were read. The real reset consumer failed all three regional regressions
  with `CD background has no bound assets`. Presentation binding now precedes
  the terrain reset, while new actor construction stays after it.
- `CourseControl.restore()` sent undefined negative song IDs into audio;
  `-1` collided with the silent profile's speed-shoes command. Original code
  failed the `-1` and `-2` cases while five positive/publication controls passed.
  The existing restore owner now requests only nonnegative music IDs. This is
  a generic absence guard, without a game-name condition or audio fallback.

The reset/music negative selection completed ten cases: five expected failures,
zero errors/skips, Java 25. Its command-session handle was lost from the root's
printed tool result; completion was established from the ended owned wrapper
process and both complete XML reports. No command exit code is asserted for
that invocation, and no duplicate run was submitted to recover the handle.

Additional actor corrections preserve the native priority mask, reproject the
native mirror on a rejected foreign actor capture and use precise source
citations. Normal module admission supplies the CD rules provider; the older
direct actor fixture's static-module provider is not the production player
entry. Generic movement remains bypassed by the native slot.

### Observed normal-world and render checks

Parent 55641 passed 51 cases with no failures/errors/skips, all three actual
regional discs, actual Sonic 2 ROM, Java 25.0.5.0.2 and surfaceless EGL, with
DISPLAY/WAYLAND_DISPLAY absent. Queue wait was zero, Maven execution 1m46s.
It covers player admission, registry restore, default STATIC, API and audio
replacement, including actual visible native-player residency.

Parent 43898 passed the USA route with no skips: 132 ordinary inputs for
walk/reverse/B-jump/land/Down/pause/resume, every frame rendered, followed by
132 restored inputs rendered again. Complete actor/history, Environment,
CPU/resident art, pause/V-int clocks, sounds and camera match at every step;
final pixels match. Queue wait was 34s, Maven execution 26.317s. Positions in
the retained CSV span centre X64–169 and Y1843–1902; these are observations,
never runtime tuning targets. Six route screenshots and three regional
idle-player screenshots are queued externally, unviewed before 07:00.

After all three repairs, parent 4226 passed **192 cases, zero failures, errors
or skips**, on `a8cb4675a` plus the exact pending integration, completed
05:48:37 BST. Queue wait was zero; Maven execution 2m01s. All 13 XML class
identities and Java/media/EGL properties were inspected. Exact selection:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 \
  python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  '-Dtest=TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestCourseControlMusicRestore,TestModApiSignatureSurface,TestHeadlessAudioOwnership,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad,TestSonicCdPlayerPresentation,TestNativePlayableSlotDispatch,TestControlledFrameRuntime,TestControlledVBlankOnlyOwner' \
  "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" test -B
```

Counts are 74 frame, 12 actor, 24 regional load/reset, 8 boot/render/route,
7 music restore, 11 API, 2 audio owner, 4 module, 6 static, 10 held-byte,
9 native-slot/suppression, 6 controlled-frame and 19 serviced-HOLD. Earlier
overlapping selections are not additional unique coverage. Comment-only
cleanup afterward removes obsolete UNRUN labels; no executable changes follow
this focused result.

Shared changes still require the change-based smoke selection and guards.
The earlier timed-out broad invocation remains incomplete. These finite checks
do not establish whole-act, complete USA game, native audiovisual, instruction-
phase, physical timing or complete hardware-device parity. All source remains
on the local private branch; no develop merge, push or public announcement.

## Optional opening-object data and animator, 24 October 2026

This component unit is prepared on a separate local object worktree based on
`9598c663c`. The primary terrain-player checkout remains frozen for its running
smoke selection. No target directory was copied or shared. New components are
not yet admitted to the game loop and do not replace the default STATIC entry
or the explicit three-region terrain-only player entry.

`CdR11aAssets.openingObjects()` is optional and USA-only. It re-verifies the
actual retained immutable MMD before reading nine new native tables or decoding
Ring/Mosqui art. Ordinary regional loading and its constructor remain unchanged.
Raw five-byte mappings, signed pointers/aliases, script tables, touch sizes,
tile selectors and descriptive source/PLC art associations remain bounded and
immutable. Decoded art is not a live PLC transfer or residency certificate.
All runtime bytes still come from the authenticated user-supplied disc.

`CdObjectAnimation` ports the separate object animator, with byte timers,
selected signed-relative reads, map/flip publication and the exact shared
FD/FC/FB/FA comparison value. FF/FE selected bytes go directly to frame
publication without recursive command dispatch. Ring sparkle's routine change
does not delete or dispatch the object; those remain caller responsibilities.
Slot bytes must be carried into the animator, since Ring Init does not clear
its animation fields and native Null clears only the ID.

Astra's six-file asset review and Opus's two-file animator review found no
P1/P2 issue. Both frozen source mirrors remain unchanged externally. Their
source/data checks are distinct from the following parent Java executions:

| Parent command | Observed execution | Scope |
| --- | --- | --- |
| 82893 | 59 tests, zero failures/errors/skips; Java 25.0.5.0.2; queue 0s, Maven 1m36s | New visual/assets/retail consumers 18, animator 27, existing regional asset consumers 3, API 11; actual configured USA/Japan/Europe media |
| 45862 | 2 tests, zero failures/errors/skips; queue 18s, Maven 28.037s | Strengthened all-256 distinct directory targets and exact `id == scriptCount` rejection; production unchanged |

The second run adds one case and strengthens an existing case; these are not
61 distinct cases or a single suite. Other animator expectations passed in the
first run and were unchanged. The header/citation cleanup adds no executable
production change. All six initial XML identities and the two-case result were
inspected, including Java/media properties and zero skips.

Commands ran from the separate object worktree, with display variables unset,
Lua 5.4 and the current main queue wrapper. Each worktree owns its own target:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectAnimation,TestCdObjectVisualAssets,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdR11aRetailAssets,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectAnimation#all256AnimationIdsCanSelectTheirOwnWordIncludingTheLastOne+scriptDirectoryEndIsNotAnotherEntryEvenWhenItsBytesWouldSelectAValidScript' test -B
```

Focused validation is proportionate here: the only existing runtime edit is an
explicit optional data-consumer method. No normal load, shared algorithm,
render/timing loop, mutable world owner or API signature pin is changed. New
slot/placement candidates are a separate uncommitted component under review,
not part of this data/animation checkpoint. Their successful compilation cannot
be used as evidence of object execution, touch, live Ring/Mosqui presentation,
shared sprite queues, PLC lifetime, whole-act or complete-game parity.


## Native placement and player touch prerequisite, 24 October 2026

This local component builds on `8c8a975fb` in the separate object worktree;
the primary `9598c663c` remains frozen under its own running smoke selection.
It does not admit objects to either stock/static or terrain-player gameplay.

`CdObjectPool` owns only physical dynamic slots 32..127. Immutable 64-byte
images preserve aliased fields, dirty ID-zero bytes and native write widths.
Null clears only ID; Delete clears the complete slot. Allocation does not
clear or reserve the result. The final FindNext boundary accepts whole-index
31..127: callers 0..30 could read reserved slots this owner cannot observe and
are rejected. The pinned native decoder confirms this restriction; the last
reserved slot31 still begins at32 and every dynamic caller remains exact.
Fable's A1 advisory motivated this explicit boundary rather than pretending
to observe unowned reserved state. No admitted R11A caller changes behavior.

`CdObjectPlacementRuntime` ports native cursor/header/saved-bank state,
initialization, forward/backward streaming, period checks, loaded-bit order,
allocation-failure rollback and unchanged-chunk behavior. Capture owns its
pool exactly once with its own saved flags/cursors. USA admission binds actual
loaded assets; pure constructed-format inputs retain their separate contract.
The actual USA camera0 Present pass produces parent slots32/33/34 with Ring,
Ring and Mosqui, matching the source list and dispatch pointers. Ring children,
body execution and complete RunObjects order are still separate work.

`CdPlayerObjectTouch` consumes all96 physical images, testing source sprite
sign/type gates before dimension lookup. It preserves word carry/borrow,
radius-byte underflow, exact duck shape, physical first-return order and the
unsigned hurt threshold. A generic item yields a detached routine-byte patch;
no pool mutation occurs during the scan. Enemy/special/hazard/monitor handlers
remain explicit unsupported call boundaries. The root must stage an item patch
at Body's post-animation/pre-SpecialChunks cut and commit only with the completed
body. This prerequisite adds no Body, frame, sprite-queue or reward writer.

Astra's touch review and Fable's slot/placement review found no P1/P2 defect.
Their frozen candidates remain unchanged externally. Root's only production
adjustment is the FindNext lower-bound restriction above; its caller-visible
regression was edited first and all six pool methods were then executed.
The actual-retail USA test now also constructs the touch consumer from the
loaded table and checks a detached item patch with the real owned slot pool.

| Parent command | Observed execution | Coverage and limits |
| --- | --- | --- |
| 83004 | 26 tests, zero failures/errors/skips; queue31s, Maven1m25s | Six pool,18 constructed streaming and two actual-media methods; original finite allocator contract |
| 13459 | 22 tests, zero failures/errors/skips; queue28s, Maven1m25s |19 touch methods and three regional object-asset cases, including the actual-USA factory/slot assertion |
| 11699 | Six tests, zero failures/errors/skips; queue41s, Maven1m19s | Final pool contract, including all31 earlier reserved-caller rejections,31/32/126/127 bounds, dirty reuse and capture ownership |

Java25.0.5.0.2 and each selected XML identity were inspected. The two media
selections used all three configured discs and had zero skips. Fresh source
compilation occurred for the final pool edit. Counts overlap; they are not one
combined suite or distinct aggregate. Source reviews did not execute Java.

Commands from the object worktree used display variables unset, Lua5.4 and the
current main queue wrapper; all targets remained worktree-local:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectPool,TestCdObjectPlacementRuntime,TestCdObjectPlacementRetailAssets' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdPlayerObjectTouch,TestCdR11aObjectRetailAssets' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectPool' test -B
```

Focused validation is proportionate to new, unadmitted CD data/state owners.
No shared loop, timing/physics, renderer, public API pin or current world
execution is changed. The sole existing test edit consumes the new real-data
factory. The primary's broad smoke remains a separate unfinished result.
No live object, Ring pickup, Mosqui route, source sprite queue, native audio,
whole act, USA MVP or regional/full-game parity is certified by this unit.


## Ring execution and shared CPU sprite queue, 24 October 2026

These are finite, unadmitted components on the separate local object branch,
based on `7309600e6`. No native object world or launch entry is installed by
this checkpoint. The primary `9598c663c` smoke source remains untouched.

`CdRing` uses the existing physical pool and placement-owned saved bank for
Init group expansion, Main, Collect, Sparkle and next-dispatch Destroy. It
preserves dirty slot/animation bytes, loaded-bit and allocation-failure order,
the shipped warp D2/X clobber, source range/period arithmetic, reward thresholds
and selected sound ordering. DrawObject is a synchronous source-cut port; no
slot write follows it. The global Ring timer is a separate immutable value
advanced once by the future entitled loop tail, never per object or render.
The production factory binds the actual optional USA asset owner. Decoded art
still establishes no preload, residency or complete native PLC lifecycle.

`CdObjectDrawQueue` binds one live-slot port and one mapping port. It queues
physical addresses only in eight buckets of 63 pointers, shared by player0
and dynamic32..127 under this finite admission. It clears bit7 before culling,
uses the old foreground camera at enqueue, then rereads every current occupant
after camera movement, retaining append order and delete/reuse semantics.
CPU SAT construction preserves native full-word attributes, flips, per-piece
80-entry truncation, links, terminator and raw unused tail. Screen Y at0A is a
word. Closed-cut snapshots own only this CPU partition; the parent still owns
next-VInt displayed pixels, art, composite source/phase validation and closure.
There is no ID-selected mapping, second player raw image, private Ring queue,
generic RingManager, renderer or hardware scanline emulation in this unit.

Opus's four-file Ring review and Astra's CPU queue review found no P1/P2
source/expectation defect. Their mirrors remain frozen. The first parent queue
build revealed a missing private putWord helper: compilation failed before
any test ran. Root added only the big-endian two-byte writer; the subsequent
26-case run passed. Final queue source SHA-256 is `3d9b4fd470ed6a40e3a42133b2b89323407329793d42f2ad80e129e5363f9074`.
The reviews' UNRUN wording describes reviewer execution; it does not replace
these separately observed parent runs:

| Parent command | Observed execution | Scope |
| --- | --- | --- |
| 68837 |137 tests, zero failures/errors/skips; queue46s, Maven1m29s |28 new Ring cases, including two actual-USA retail methods;14 separate shared-phase checks and95 maintained frame/pause/S2/S3K/API cases |
| 25374 | Compilation failure, no tests executed; queue383s, Maven55.794s | Missing putWord helper in the first queue source; no behavior result |
| 84647 |26 tests, zero failures/errors/skips; queue120s, Maven1m23s |16 CPU arithmetic/order/SAT methods and10 phase/ownership/failure methods after the helper correction |

All16 selected reports from68837 and both queue reports from84647 were
inspected. Java25.0.5.0.2 was used;68837 had actual three-region media and all
S1/S2/S3K ROM properties, with zero skips. The137 count includes unrelated
maintained coverage and is not137 new component cases. These are separate
bounded selections, not a complete smoke or whole-game pass.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestNativeDynamicObjectFrameOrder,TestNativeDynamicObjectPass,TestCdRing,TestCdRingRetailAssets,TestNativeFixedLevelObjectPass,TestFbzFramePhaseOrdering,TestLevelFrameHardwareTimingBoundaries,TestPlcVBlankOrdering,TestInGamePause,TestS2FrameOrderingExecThenLoad,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectDrawQueue,TestCdObjectDrawQueueLifecycle' test -B
```

The shared-phase source used by68837 remains a separate uncommitted change.
Its later source review identified an earlier controlled V-int increment that
can precede rejection of an invalid native binding. That P2 remains open until
its caller-level guard regression/fix is executed; it is not hidden by137 green
focused checks. No shared phase source is part of this component commit.
Ephemeral player-touch integration and actual Mosqui execution also remain
separate work. Next-VInt publication, art admission, real Ring pickup and
rewind through the complete world loop are still unverified. Full USA MVP
continues to mean the entire game, followed by regional certification.


### Native dynamic phases and pre-clock admission, 24 October 2026

Added the internal `NativeDynamicObjectPass` service without changing GameModule
or the pinned public Mod API. One resolved owner spans placement before reserved
slots and live dynamic execution after reserved slots, before the actual camera.
A bound native owner rejects legacy ordering, nonordinary phases and competing
generic object/ring managers. Unbound loops retain their existing execution.

The initial source review found a caller-level P2: ControlledFrameRuntime could
advance the represented V-int before LevelFrameStep rejected an invalid binding.
The actual two-case regression compiled and failed with V-int99 becoming100 and
the ordinary timer9 becoming8. The fix admits before beforeGameplay/clock work,
retains resolved absence, and revalidates callback-driven module/manager/load
replacement without a second lookup in an unchanged world. A valid earlier
service is not rolled back after a later fade replacement. Setup/hold/pause/lag
do not acquire an ordinary dynamic owner. No static or thread-local admission
cache, source-name branch or new timing-data authority was added.

Focused parent27844 ran159 tests: zero assertion failures, three fixture errors,
zero skips. All three positive controlled paths reached the real Camera without
a focused actor. Root attached the fixture's actual NativeProbe to that camera;
no production Camera change was made. Affected rerun20020 then passed all12
TestControlledNativeDynamicAdmission cases with zero failures/errors/skips.
The unchanged other147 checks from27844 passed, including direct phase order,
PLC, represented clocks, hardware boundaries, pause, Mod API and maintained
S3K loading/bootstrap/decoding consumers. XML verified Java25.0.5.0.2, actual
CD media and the surfaceless-EGL property; the actual unbound CD controller
movement check executed. These overlapping selections are not a broad pass.

Commands, worktree HEAD a5b01f073 plus the six uncommitted phase paths:

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestControlledNativeDynamicAdmission#legacyOrderRejectsBeforeRepresentedVIntAndGameplayEffects+competingRingManagerRejectsBeforeRepresentedVIntAndGameplayEffects' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestControlledNativeDynamicAdmission,TestNativeDynamicObjectFrameOrder,TestNativeDynamicObjectPass,TestControlledFrameRuntime,TestControlledVBlankOnlyOwner,TestVIntRunCounter,TestGameLoopVIntRunCounter,TestPlcVBlankOrdering,TestLevelFrameHardwareTimingBoundaries,TestModApiSignatureSurface,TestNativeFixedLevelObjectPass,TestInGamePause,TestFbzFramePhaseOrdering,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" "-Dsonic1.rom.path=$OPENGGF_MAIN/s1.gen" "-Dsonic2.rom.path=$OPENGGF_MAIN/s2.gen" "-Ds3k.rom.path=$OPENGGF_MAIN/s3k.gen" -Dopenggf.headless.gl=surfaceless-egl test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestControlledNativeDynamicAdmission' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl test -B
```

Separate primary9598 smoke45011 completed red (all74ordinary batches and guards):
ordinary38921 tests,2 failures,2 errors,176 skips; guards722 tests,8 failures.
Attribution and required corrections remain open; no clean delivery is claimed.
Actual Ring pickup, Mosqui execution, shared displayed SAT and composite rewind
remain the next private-world integration, separate from this internal phase.


### Ephemeral player touch through the frame owner, 24 October 2026

Body now accepts one optional immutable touch context at the actual post-Animate,
pre-SpecialChunks call. It stages the first physical selected item image without
writing the pool, retains no snapshot in player/environment/rewind state, and
rejects unimplemented enemy/monitor/hazard/special handlers with a typed boundary.
A later Body failure publishes no item patch, sound or player result. The caller
still owns committing an item only after the complete Body returns. Raw Display
width/priority travel with the call cut; the old terrain-only cull remains equal.

The named external-touch-required Frame factory does not claim empty dynamic
RAM and retains the two-stage art callback binding. Existing terrain factories
keep their prior behavior. Root added four Frame checks for binding, missing or
non-retained context, detached item/replay behavior, failed handlers and unserviced
/paused entitlement. Opus's four-file source review found no P1/P2 or wrong
expectation. Root's additional four Frame invocations executed separately from
that historical source review in the combined parent run below.

Parent83771 passed244 tests, zero failures/errors, one skip at componentac49f80cb
plus the reviewed touch/Mosqui files and parent Frame regressions. Per class:
Body78, Frame78, actual-regional actor12, regional lifecycle24, boot8 (one
skipped), Mosqui26, floor helper7 and Mod API11. Java25.0.5.0.2 and actual CD
media/surfacelessEGL properties were verified in all eight XML reports. The
skipped boot case is cartridge-to-CD switching: the command omitted sonic2.rom.path.
It is not counted as observed switching coverage and will be included with the
next affected world integration selection. The other boot cases included the
existing USA132-input rendered/rewind route and regional EGL loading. No new
Ring pickup or shared-object world route ran.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestCdMosqui,TestCdObjectCollisionCheck,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl test -B
```

This commit contains only the five touch/player test paths and this existing
record. Mosqui is a separately reviewed component. Pool commit, live dynamic
execution, shared CPU SAT and displayed-next-VInt pixels remain root-owned
world integration, with no touch suppression or generic Ring fallback.


### Native Mosqui body and floor call, 24 October 2026

The selected $3D body now uses the sole placement pool/saved bank and actual
Mosqui tables, including Init/Main fall-through, dirty-slot bytes, the second
movement on patrol reversal, pointer-selected animation, full-word debug gate,
dive/floor/landing/Wait and source draw/despawn order. Synchronous DrawObject
observes committed fields; the body reloads the live slot afterward, retaining
callback writes and zeroed-slot behavior. Unknown routine/script/good-future
conversion stops at its explicit ownership boundary. No enemy-touch response,
Explosion, audio driver, native art preload or complete-act support is implied.

Astra found no P1/P2 in the four frozen files. Root narrowed only the imported
javadocs: the shared DrawObject port must not reenter body dispatch, and the
returned PRIMARY_ANGLE byte is for the caller's existing shared scratch owner;
unlike pool/bank writes, that global has not already been committed. It must
not be ignored based on a universal overwrite claim. The immutable player
previous-angle bytes remain a separate field. The external mirror/review bytes
are preserved; no executable Mosqui expression changed after review/testing.

Parent83771 executed all33 Mosqui/floor invocations (26 body including eight
range rows and actual-USA assets, seven floor helper), zero failures/errors/skips,
within the244-case touch/player integration selection documented above. Actual
USA media bound both mapping/script pairs at their source addresses. Constructed
terrain verifies the dive continuation; this does not prove its actual opening
column, native capture parity, multibody World ordering or displayed pixels.
Root's next composition owns those globals, shared queue, displayed-next-VInt
latch, live Ring/Mosqui pass, unknown-ID rejection and capture/restore.


### Authenticated mappings and immutable shared picture, 24 October 2026

Added exact raw-pointer mapping resolution from the actual Sonic/Ring/normal-
and damaged-Mosqui source views, plus pure CPU SAT conversion to the existing
immutable SpritePresentation.Frame. Native full-word attributes, tile wrap,
column-major pieces/flips and reverse-SAT painter order are retained. Unknown
pointers/art and unsupported finite coordinate modes fail explicitly. Static
one-piece mappings remain a distinct declared format; no unowned production
static source is invented. Conversion neither clocks nor publishes a picture.

Opus's independent four-file review found no P1/P2. Parent83759 then passed all18
new methods, including actual-USA factory/bank checks, within58 selected cases
(geometry10, retail3, module4, actor12, API11, mappings7, picture11), zero failures/
errors/skips. Java25.0.5.0.2 and actual CD media were verified in all7XML reports.
Other root geometry/rule corrections in this tree remain a separate guard repair.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdLevelGeometry,TestSonicCdLevelRetail,TestSonicCdModuleContracts,TestCdPlayableSonic,TestModApiSignatureSurface,TestCdObjectMappings,TestCdObjectPresentation' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

World composition must register one exclusive governed atlas range, subdivide
its three logical pixel spans, recreate the player pixel source at every completed
selector8 service and latch previous CPU SAT with now-serviced art. A one-time
preload would freeze Sonic and is not permitted. The existing renderer's mixed
sprite/plane-priority overlap approximation remains an explicit parity gap: native
sprite winner precedes plane comparison; do not reorder SAT to compensate. This
pure unit does not certify foreground compositing, native residency, actual
opening-object rendering, shared World restore or gameplay parity.


### Guard attribution and world composition repair, 24 October 2026

The completed smoke at clean9598c663c executed all74 ordinary batches plus
guards:38,921 ordinary cases, two failures, two errors and176 skips;722 guard
cases, eight failures. Matched19-case baseline/current runs reproduced the
exact same four ordinary failure identities, types and messages: bare GLFW
without a display, a same-JVM cleared EGL property, and an inherited Java launcher
notice preceding the audio CLI's stderr prefix. No unrelated donor or graphics
code was changed to hide those failures. Diagnostics were consumed and removed.
This remains a bounded red smoke with attributed environmental failures, not a
complete green delivery or a release/full-suite result.

Repair the introduced ownership violations at their owners. WorldSession keeps
only a private opaque scope; GameplayModeContext composes its registry through
the exact-caller EngineRewindRegistryFactory. Its single composition-root
allowlist entry does not widen creator authority. Public unscoped constructors
assign their own fields directly; frozen architecture stores and API pins stay
unchanged. Reconciled tests use actual gameplay contexts and empty registry
fixtures, retaining the original size-estimator constants. Independent Opus
review found no P1/P2; parent67225 passed141 rewind/core/API cases with zero
failures/errors/skips, Java25 and required cartridge properties inspected.

Initial CD geometry now uses the existing Sega-format decoders while retaining
independent blank descriptors. A root mistake passed a grid side instead of its
256 entries; the resulting failure and Mockito cascades were inspected, the
count corrected, then parent83759 passed58 geometry/retail/module/actor/API/SAT
cases with zero failures/errors/skips and actual regional media. Presentation
rules use an engine-owned semantic factory; unimplemented shared gameplay gates
remain absent. No cartridge profile or game-name exception was introduced.

The optional canonical scddisasm pin remains1ecc5f3a. Remove its floating branch
hint and include it with the other three canonical reference paths in structural
checks. Parent15071 then passed721 of722 guards; the remaining assertion rejected
the documented ordinary clone's optional --branch develop. Port the current
main-workspace assertion accepting only that optional branch, leaving developer
instructions unchanged. Parent27346 passed the exact corrected case, zero skips.
Following opening-world composition, parent55552 freshly passed111 affected
architecture/rewind/module/presentation guards, zero failures/errors/skips. All
selected XML Java25 identities were inspected. Unchanged guard coverage is reused
proportionately rather than repeated as a ceremony.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off -Pguards '-Dtest=TestArchUnitRules,TestArchitecturalSourceGuard,TestRewindArchitectureGuard,TestGameModuleRegistryUsageGuard,TestProductionAwtBlacklistGuard,TestNoWorldPresentation' test -B
```

All work stays on private local branches. Public changelog/guide publication is
intentionally deferred under the user's explicit no-disclosure instruction;
this existing dated engineering record preserves the corrections and limits.


### Opening native frame owner and shared player bridge, 24 October 2026

CdOpeningObjectRuntime now composes the authenticated USA placement pool/saved
bank, Ring and Mosqui bodies, ephemeral post-player touch, shared live-pointer
DrawObject queue, CPU SAT and immutable displayed-next-VInt picture. It executes
physical slots in live ascending order, including same-pass children; unknown
IDs and unsupported enemy/hazard/monitor/special contact fail at their real
dispatch or handler. No visibility/coordinate/route fence suppresses execution.
The separately declared development ingress has zero CPU SAT, empty displayed
picture, explicit Ring globals, absent reserved slots1..31 and preloaded actual
object art. It does not prove a retail title/new-game load or native residency.

Ordinary service latches the previous CPU SAT with now-serviced player art;
pause publishes without a body and lag retains the prior picture. A single
root-supplied primary-angle setter commits Mosqui's returned scratch byte before
later dispatch; actor previous-angle copies remain separate. Completed Body
returns stage the detached selected-item patch before pool commit. The last-loop
typed sound ledger resets on ordinary service only, remains visible across
holds and must not be replayed by draw/restore. FM_CHARGESTOP remains a stop
request. Runtime and queue failure close sibling admission without rollback.

CdPlayableSonic's optional ObjectPass delegates Body and the exact DrawObject
cut, then projects the shared on-screen bit after camera/SAT construction. It
does not build or draw a private one-player SAT in this mode. Pre-service misuse
still rejects without closing an unadvanced frame; body/enqueue/GPU failures
after admission close both owners. The existing terrain-only constructor and
draw path are retained. Snapshots partition actor Body/history/display from
Frame globals/art and runtime pool/CPU/display/sounds; each retained owner
preflights its capture before mutation.

Fable's independent runtime review found no production P1/P2. Its one test-only
finding confirmed native unconditional charge-stop precedes a Ring reward;
root corrected only that expectation. Parent18666 passed114 cases, no failures,
errors or skips:Frame80, regional actor12, shared bridge4 and runtime18. Actual
media and Java25 were inspected in all four reports. The earlier omitted S2
property was supplied in parent31860, whose cartridge-to-CD switching case
passed; it is not a retained skip. These component checks do not themselves
establish a composed World pickup or native gameplay parity.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestCdOpeningObjectRuntime' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```


### Actual opening World and first Ring, 24 October 2026

The separately named openingObjectsDevelopmentPlayer / bootSonicCdOpeningObjects
entry authenticates USA R11A before retiring an existing World. It assembles one
native Sonic and the shared Ring/Mosqui pass through normal input, PLC, level
frame, live dynamic slots, camera and ordinary tail owners. Default STATIC and
regional terrain-only entries retain their capabilities. Advanced camera,
afterTick and restore failures now close terrain-only admission more strictly
as well. The one exclusive OBJECTS atlas range supplies108 picture patterns;
the separate player resident bank retains24. No generic object/ring manager,
private player drawing or alternate sound playback substitutes for the pass.

Parent11800 compiled and ran69 cases, zero errors/skips:68 passed, including all13
new actual-World lifecycle invocations and maintained module4/static6/terrain24/
presentation10/API11. The sole failure was the original88-row input proposal,
which stopped at centreX214.75 before RingX240. Its observations were inspected;
only the ordinary input tail changed from2neutral to16Right, making102 available
rows. No movement, touch, object, camera or production expression changed to
reach the Ring.

Parent63645 then passed the single real-EGL route case, zero failures/errors/
skips. Actual USA media and surfaceless EGL/no ambient displays were required;
Java25 and selected properties were verified in XML. Ring pickup occurred at
call92 with FM_RING; call93 published the completed pickup SAT and now-serviced
player art. All91 rows after the two-call checkpoint reproduced complete actor,
shared state/art, pool/saved bank/cursors, Ring globals/sounds, CPU SAT/provenance,
displayed pixels, camera/scroll and resident bank through both full and course
restore. Restored and final framebuffer pixels matched exactly. Rejected boot
preserved the old World; fresh boot retired it and cleared CPU/display/Rings.
Mosqui dispatched throughout, including when outside the viewport. Audio stayed
NullAudioBackend. Actual PNGs, input log and observations live outside the repo.

Opus's independent World/bridge review found no P1 and one P2 in the stated
presentation limits, now recorded here and in the external R3 handoff:
**terrain is drawn at the current loop's camera, sprites come from the previous
loop; there is no synchronized native plane/scroll or pixel-parity claim.**
During camera motion this offsets sprites relative to terrain by one camera
delta. Native VInt_Level latches scroll/SAT/level drawing together
(Level/Main.asm:690,826-827,840-844); the development terrain path does not yet.
Engine-to-engine replay equality cannot establish native pixel parity. Mixed
plane/sprite priority remains a second explicit gap. Neither input nor actor
positions are adjusted to conceal these gaps.

Other reviewed limits remain explicit: BG/BG3 cameras are fixed0, external engine
stage failures close at the next admission gate, generic-manager preconditions
are checked before the first represented frame, and mid-cycle restore is not an
admitted cut. HUD, enemy/hazard response, native title ingress, driver audio,
live PLC residency, time travel, other stages and complete-game parity remain
open. The first Ring is an intermediate milestone toward the full USA game.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true "-Dsoniccd.opening.output=$SCD_NEW_EXTERNAL_CAPTURE" test -B
```

The optional capture hook requires a new directory under Downloads/Sonic-CD,
preserves earlier captures and records only post-step observations. It never
supplies native state to gameplay. A dedicated R3 external revision preserves
the tested six-file bytes, failed and successful input history, review response,
commands and remaining publication limits. No merge, push or public launch is
performed by this checkpoint.


### Optional frontier assets and counted formats, 24 October 2026

Added nine individually lazy USA views for Animal/3DPlant/ramp-booster data and
a separate lazy FrontierArtBank for their three actual art sources. Existing
opening load still eagerly reads exactly its original nine views and decodes
only Ring/Mosqui; initialArtBank remains two bindings. The owner authenticates
the retained immutable MMD, synchronizes private lazy caches and publishes only
complete immutable derivatives. Native act/time indices preserve actual zero
Animal base words and promise no art residency for other acts/periods.

Unscripted counted mapping tables now have an explicit decodeStatic entry;
the old animated entry still requires a script. Unscripted selection uses the
native full unsigned oMapFrame byte, independently of the script's five-bit
frame field (DrawObjects.DrawSprite, Object Functions.asm:333-335). This is not
the bit-5 header-free StaticMap format. A separate exact-tail overload retains
the ramp's11/21 unused source bytes after mapping frames8/9 without drawing or
compacting them; unlisted frames retain strict body/alignment validation.
The metadata is about native mapping indices, never gameplay frames/routes.

Finite Nemesis bounds distinguish authored blob from actual reader bytes:
Animals446/445, Plant392/393, Ramp678/677. Plant's required extra lookahead is
the actual adjacent MMD byte80, not invented padding. Exact decoded capacities
are640/896/2016 bytes. Bank association checks source/PLC row/VRAM/base word;
surrounding gaps remain absent. No object dispatch, live PLC or resident-VRAM
certificate is established by these bindings.

Independent Opus decoder and Sol asset reviews found no P1/P2 in their frozen
scopes. Root verified their advisories against code/native source, strengthened
tail-key/unlisted-frame regressions, documented StaticMap distinction and added
the full-byte unscripted frame boundary. A fresh asset owner first decodes its
frontier bank after the media source has closed, checking real Plant bytes and
repeat identity. Root44175 passed22 decoder/old-asset cases; then23823 passed13
new-asset cases; final71286 passed16 affected decoder/retail cases (13+3), all
zero failures/errors/skips. Java25.0.5.0.2 and actual media were inspected in all
selected XML. These selections overlap and are not one aggregate suite.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectVisualAssets,TestCdR11aObjectRetailAssets' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

The full ordinary byte-pointer range does not invent any additional source
table or runtime admission. Roaming/hologram animals, solid FloatBlock,
object-to-player effects and shared-world dispatch remain separate owners.
Actual first Ring captures and their documented scroll/publication gap are
unchanged by this asset/format work. Original frozen reports/source mirrors
retain their reviewed hashes; root's small advisory followups are recorded here.


### Native frontier body owners, 24 October 2026

Added bounded Animal, 3DPlant and ramp-booster bodies using the existing injected
live pool and saved-placement bank. Animal's intact-projector Present path only
checks despawn; roaming/hologram variants retain explicit owner boundaries.
Plant commits parent and first-free children in native order, preserves dirty
unwritten slot fields and stops at the actual out-of-pool write without rollback.
Ramp Init replaces its sprite flag byte, preserves word arithmetic and returns
its player Y-velocity write as a staged typed effect, not a private player edit.
Draw callbacks are synchronous; completed child/despawn effects are never replayed.
Four raw slot aliases add names only, without changing state storage behavior.

The independent frozen-source review found no P1/P2. Its test-fixture advisory
was verified: placing a ramp overwrote the dirty sprite-byte seed. Root moved the
seed after placement so the assertion distinguishes byte replacement from OR.
No production change was needed. Original frozen source/report bytes remain intact.

Root session2865 passed113 focused cases: Animal25, Plant29, Ramp35, pool6 and
placement18, with zero failures/errors/skips and both actual-media methods run.
After the fixture correction, session48345 passed that exact Ramp case again;
its unrelated scroll-fixture errors and expected CD presentation regression
remain separate, not a green combined selection. Java25.0.5.0.2 and media inputs
were inspected in selected reports. Overlapping executions are not summed.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdAnimal,TestCd3dPlant,TestCd3dRamp,TestCdObjectPool,TestCdObjectPlacementRuntime' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" test -B
```

These pure owners are not yet dispatched by the opening world at this commit.
Solid FloatBlock, alternate ramp fall, animal roaming, enemy/hazard response,
live art residency, native ramp player mode and complete route parity remain
open. Existing first-Ring evidence and the terrain/SAT publication gap are unchanged.


### Secondary native slot aliases, 24 October 2026

Corrected oSolidType and oLayer aliases to physical bytes25/29, matching
Level/_Definitions.inc:59-66 at the pinned reference. Each zero-size declaration
follows the primary byte, so it shares the following secondary byte. The prior
names incorrectly shared primary routine/subtype24/28. The item-touch test had
encoded the same incorrect alias; it now checks that the primary routine wraps
while secondary solidity/layer retain their actual dirty values. A raw64-byte
regression proves alias writes change only25/29. Existing screen-Y byte view is
compatibility evidence, not a new word-wide screen-coordinate owner.

Both corrected regressions failed against the old aliases in session14216
(2tests/2failures/0errors/skips). After the minimal enum-value correction,
session89744 passed complete pool/touch classes:26tests/0failures/errors/skips,
Java25.0.5.0.2 verified in both reports. This enables later explosion/solid owners;
no existing production consumer used these two aliases, and enemy response is
still unadmitted. No player values or collision outcomes were adjusted.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestCdObjectPool,TestCdPlayerObjectTouch' test -B
```


### Paired completed-loop scroll publication, 24 October 2026

The opening development World now publishes terrain scroll and its immutable SAT
picture from the same completed native loop. The previous checkpoint's current
terrain/prior-sprite displacement is fixed for this admitted path. A real EGL
regression first reproduced it at pixel37,0 outside every displayed sprite tile.
No gameplay position, input, source clock or native movement was adjusted.

LevelSpritePresentation now exposes opaque immutable ScrollFrame capture, pure
owner/lifetime validation and a terrain draw scope. It retains the actual full
scroll buffers/registers and resolved render mode, with manager/World/source,
level/load generation, renderer, camera/parallax, graphics/atlas and viewport
ownership. Capture does not drain/draw/advance; scope never moves gameplay camera.
The scope owns normal pattern commands, not arbitrary SAT/effect/art mutations.
Same-instance graphics cleanup/reinit without a World/renderer/load reset remains
outside the available lifetime detection. No generic CD sprite publication was enabled.

The CD Presenter owns prepared/published render cuts separately from its native
foreground accumulator. Cold pairs remain null until first input after completed
production load. Its per-visit PLC composition delegates exactly one Runtime
service, then publishes the prior prepared cut on ordinary/pause; PP lag retains
both. After a completed ordinary tail it captures the next cut. Rendering and
restore never manufacture or advance it. Shared restore preflights all three
Frame/Runtime/scroll partitions before writing, and stale service handles reject
before they can close or change the current visit. Advanced capture/service/draw
failures close the sibling source owners without pretending to roll back GPU work.

Independent source review found two shared P2s, both repaired and re-reviewed:
queued terrain wrappers now own render-mode copies rather than controller reuse
buffers; cinematic admission covers contributor setup through unwind. The direct
graphics capture dependency now restores its prior target even when unwind,
discard or frame cleanup fails, retains the primary error and visits each release
once. Nested captures retain their documented immediate-inner order. Failed lists
are cleared and not pooled. No ordinary flush owner was refactored.

Observed checks, each attributed to its source selection rather than summed:

- 48345: original rendered pixel failure plus17 new-fixture cascade errors; the
 constructor-safe partial service fixture corrected the latter separately.
- 3453: tests-only R2 on old production,31tests/9 intended failures/0errors/skips.
- 38364: repaired shared scope/cleanup plus maintained capture regressions,
 57tests/0failures/errors/skips.
- 90130: actual CD pickup/full/course/pause/cold replay, regional entries and API,
 71tests/0failures/errors/skips.
- 79813: required S3K consumers, publication lifecycle and two real cinematic
 terrain/priority-mask probes,68tests/0failures/errors/skips.
- 78762: complete guards profile,722tests/0failures/errors/skips across89fresh XML.

All selected XML Java25.0.5.0.2 and required media/ROM/EGL properties were checked.
The actual capture contains93 observations; all22 earlier gameplay columns match
exactly. The91 consecutively sampled camera pairs publish prior prepared values;
the initial0-to2 sample gap deliberately omits call1. At call93 current cameraX72,
published cameraX69; the SAT and terrain use the latter. Actual call16/65/93 PNGs
were visually inspected. Repeated draw and full/course replay preserve the image.
The new local comparison uses only actual OpenGGF captures, not emulator references.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --lean -Dmse=off '-Dtest=TestLevelScrollCapture,TestLevelScrollDrawScope,TestCapturedCommandCleanup,TestLevelSpritePresentation,TestPooledRenderCommandDiscard,TestRenderContext' test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestSonicCdModuleContracts,TestSonicCdStaticStageLoad,TestSonicCdPlayerPresentation,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" -Dmse=off -Pguards test -B
```

These checks are not a full ordinary smoke or release full-suite pass. Native
plane VRAM/palette publication, background deformation, mixed sprite/plane
priority, live PLC, driver audio, contact consequences and complete-game parity
remain open. Generic mode-copy allocation impact is unmeasured. Existing earlier
smoke attribution remains historical; no unrelated donor/platform code was fixed.


### Lazy enemy-contact asset bindings, 24 October 2026

Added six individually lazy USA tables for EnemyPoints and the explosion,
points and flower visuals. Points is an unscripted counted mapping. A separate
ContactArtBank binds the three cold PLC1 sources:70 explosion tiles,5 points
and39 flower tiles. The checkpoint tile gap remains unbound. Opening load still
reads its original nine views/two sources; neither existing bank is expanded.
No contact response, object lifetime or runtime residency is inferred from art.

Finite source/reader extents are explosions1256/1256, points100/101 and
flower646/645. Points uses the actual adjacent80 byte at the FlowerCapsule
header, not padding. Init base words8680/06C6/A6D7 record their actual consumers;
flower's later06D7/26D7 writes remain object execution decisions. All lazy
views/visuals/art first decode after media closure in the fresh-owner regression.
Authentication and separate JP/EU terrain loading are unchanged.

Independent source/data review found no P1/P2. Root combined session1757 passed
166 cases with zero failures/errors/skips, including all13 constructed asset
cases and three actual regional invocations. It also exercised the existing
frontier bank through the new runtime factory, World lifecycle, actual EGL
pickup/full-course replay and API. All nine XML confirm Java25.0.5.0.2 and the
actual media/EGL properties. This is one bounded selection, not a full suite.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdOpeningObjectRuntime,TestCdPlayerFrameRuntime,TestCdObjectMappings,TestCdObjectPresentation,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

These bindings do not implement hurt, defeat, death, score/global writes or
the missing aftermath bodies. The full USA and regional parity goal stays open.


### Live frontier object composition, 24 October 2026

The opening Runtime now dispatches the bounded Animal24, 3DPlant2C and ramp2A
owners through the actual live physical-slot walk. Later-slot plant children
run in the same pass; earlier slots wait. Each dispatch reads the completed
player fields, and ramp's staged Y-velocity word is applied exactly once before
the next slot, including after same-call despawn. No actor position, control or
collision flag is fabricated. The narrow Frame setter preserves every other
actor/shared/display/art/input field and enforces its open closed-frame cut.

Explicit development globals goodFuture/projDestroyed are zero and captured once
with the Runtime. Five logical picture spans now cover24/14/70/28/63 patterns;
the old three-argument bank constructor derives the two appended bases. The
Presenter reserves and retires199 patterns in its existing governed range.
Mapping joins use the actual authenticated plant/booster tables, retaining
unused counted tails. The old three-span pixel policy stays strict; the new
named policy admits only its five disjoint physical/logical spans. Animal art
is decoded by the frontier bank but deliberately absent from drawing admission.

Independent Opus review found no production P1/P2 and one test-only P2: the
plant fixture paused with Start, then released Start without unpausing before
its next spawn. Root session25051 compiled and reproduced the exact phase
error (1test/0failures/1error/0skips). The minimal fixture correction uses an
ordinary service to publish before starting the next pass; production stayed
unchanged. The restored ramp assertion now compares its actual pre-body capture.
The World/EGL restore checks include the new single-owned globals, and the
rendered expected-pixel check uses the five-span policy.

Combined root1757 passed166 tests with zero failures/errors/skips:23Runtime,
82Frame,7mappings,11picture,13constructed assets,3regional assets,15World,
1actualEGL pickup/replay and11API. All nine XML confirm Java25.0.5.0.2, actual
media and explicit surfaceless EGL. Queue wait115s, execution1m31. These checks
are affected coverage of the combined working tree, not an exhaustive suite.
The previous shared/S3K/guards checks are not repeated for unchanged owners.

The camera200/380 tests are constructed service-contract inputs, not natural
World-route proof. Camera200 runs the inert Animal without drawing; camera380
commits the plant family then stops at the actual unowned FloatBlock12. The
booster tests are explicit constructed ingress, because the natural route still
has the earlier player ramp/contact owners. No object IDs are skipped, no
contacts suppressed and no full-pool allocation silently omitted. Current World
render verification still covers the normal-input first Ring, not full act play.
Enemy hurt/defeat/death consequences, solid objects, native player ramp mode,
section PLC before camera680 and complete USA/regional parity remain open.


### Explicit player rotation byte before contact response, 24 October 2026

Body.State now requires native oPlayerRotDist ($39) explicitly, independent of
motion.stick ($38). There is no implicit five-argument zero default. Cold Frame
ingress supplies zero from ClearObjects, while every Body/Frame reconstruction,
projection and owned actor capture preserves dirty values. Native KillPlayer's
word write to oPlayerStick also writes this adjacent byte; the future response
must adopt it rather than dropping half the native store. Ordinary Main/MODE
do not write it. PP SpinningDisc and WWZ writers remain future owners.

Six new Frame invocations exercise 0/1/128/255 through Init, Main, V-int,
projection, narrow object writes and actor/shared restore replay, plus invalid
byte bounds. All existing constructors now supply an explicit ingress value.
No contact response or KillPlayer gameplay admission is added by this change.

The combined byte39/U3 command completed at13:07:28 BST with298 tests, zero
failures/errors/skips:78Body,88Frame,23Runtime,24Explosion,18Points,14Flower,
7pool,19touch-scan,15World,1realEGL pickup/replay and11API. All11selected XML
confirm Java25.0.5.0.2, actual supplied media and explicit surfaceless EGL.
Queue wait371s and Maven execution1m33 are separate. Source/test compilation
covered4289/4406 classes. The U3 results are for its exact concurrent source
inputs, not an implication of runtime dispatch or a full-suite certificate.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestCdExplosion,TestCdPoints,TestCdFlower,TestCdObjectPool,TestCdPlayerObjectTouch,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```


### Pure contact aftermath bodies, 24 October 2026

Explosion18, Points1C and Flower1F execute over the existing live pool with
actual authenticated mapping/animation tables. Explosion preserves first-free
allocation and dirty bytes; a later Points child can execute this pass. The
low-byte points-frame shift, call21 animation finish, call22 Flower conversion,
Points23 rises/call24 delete-then-DrawObject, and Flower floor/growth/no-despawn
semantics match the pinned source. Deleted Points is still queued, then its zero
ID is skipped at DrawObjects. Points priority remains its allocated slot byte.
Three native aliases1C/3E/2A are added without changing secondary25/29.

The exact seven-file Opus unit passed all56actual cases (24/18/14), including
three opt-in retail methods, in the298-case byte39/U3 check above. Independent
Opus review found no P1/P2 and reconstructed the actual floor fixture through
the production helpers: distance31 above both blocks,15..1 while falling, then
-1 and angle22, landing on call9 at77F. The earlier fitted Python model is
secondary evidence, not the test oracle. All reviewed source hashes stayed exact.

Unsupported HUD/saved-flags routines remain boundaries. Saved-flags landing
commits the pool adjustment but loses its staged scratch-angle effect on throw;
that path is unreachable from admitted subtype1 badnik flowers. Dependency
exceptions can leave local per-dispatch writes unapplied. This unit therefore
does not promise complete native-prefix state on arbitrary failure. The runtime
must close instead of retrying it. World dispatch, art banking and the real
KillEnemy producer are separate integration units, not certified by these tests.
Fable's earlier handoff supplied reads/derivations only; Opus authored the code.


### Pure native enemy/hazard touch response, 24 October 2026

The pure response stages the selected enemy/hazard entry over one call-cut.
It returns final actor/player/world values, distinct changed slot images in
first-write order, and typed ordered calls, retaining neither snapshot nor pool.
It preserves native NEG/ASR, signed/unsigned compares, old-chain indexing and
odd-address boundary, unsigned score cap/life wrap, shield/combined-ring order,
hurt/kill/debug exits, bounce and the adjacent38/39 store. Result d0 is explicit
only at native MOVEQ -1 exits; Main does not consume it. No pool, audio or world
write occurs here. Body must pass the SAME snapshot object to scan and response;
selected-image equality alone cannot prove the other slots came from that cut.

Movement shares one floor-reset implementation, including the miniature branch,
while steep attachment remains separate. Ordinary MODE still rejects miniature
Sonic before work. This extraction preserves physics and the high-memory-byte
landing threshold; the shared helper's existence is not miniature admission.

Root54609 passed484cases with zero failures/errors/skips at13:31:39BST:
90response,89Movement,78Body,88Frame,12actor,4object-pass actor,85charge,
19scanner,3regionalcomponents,15World and1realEGL pickup/replay. The actual-USA
EnemyPoints method executed. All11XML confirmed Java25.0.5.0.2, actual media
and surfaceless EGL. Queue1180s is separate from1m40 Maven execution. This
bounded CD-internal extraction uses its complete maintained MODE/Body/Frame/
actor/World consumers; it does not repeat unchanged general-engine smoke.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerTouchResponse,TestCdPlayerMovement,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestCdPlayerCharge,TestCdPlayerObjectTouch,TestCdPlayerRetailComponents,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

Independent Opus review found no P1/P2 or wrong assertions in all179new-unit
invocations. Correction to authoring prose: SPECIAL selector0B can reach
Player_TouchHazard2; MechaBlu/bosses and spikes/time-over have related entries.
They remain unowned here, not unreachable. Expose existing hurt/kill routines
narrowly when those callers land rather than duplicate them. Native SubCPUCmd
blocks on hardware; this pure ordered request has no measured service cost.
Body/Frame staging, world score/contact globals, live aftermath dispatch,
routine4 recovery, lost rings11 and same-frame death-camera freeze remain
separate required units. Nothing in this commit admits contact into gameplay.


### Live contact-aftermath dispatch and picture banks, 24 October 2026

The existing physical object pass now dispatches Explosion18, Points1C and
Flower1F through the same pool/queue as the earlier objects. Points allocation
retains same-pass later-slot and next-pass earlier-slot behavior. The deleted
Points draw is queued without testing ID, then skipped by ID at SAT drain.
Flower's final scratch-angle request is applied once before the next slot;
no intervening animation/draw code reads that global. Its subtype2 low-priority
Init path still ends at the later06D7 overwrite. Immutable source tables join
at23F088/20AC64/235EF4, and the eight-span cold DEV picture covers313patterns.
The old three/five-span policies and the checkpoint-art gap remain strict.
Presenter reset/retirement and actual EGL expectations use the same313extent.

Root33585 passed315cases/0failures/errors/skips over12classes at13:39:06BST,
including28Runtime,7mappings,11picture,56pureU3,88Frame,15World,1actualEGL,
3regionalassets and11API (plus95Movement in the concurrent helper unit).
All12XML verify Java25.0.5.0.2, supplied media and explicit EGL. Queue247s,
execution1m34. Root43304 subsequently passed264affected Body/Frame/Runtime/
actor/World/EGL/API cases,0skips. These overlapping selections are not summed.
Independent Opus source review found noP1/P2 or wrong assertion in this unit.
The Flower terrain fixture was independently derived from the pinned layout,
not fitted: the probe finds two nonsolid blocks, distance31/angle0, and moves
720to722 inside the actual70B camera.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerMovement,TestCdOpeningObjectRuntime,TestCdObjectMappings,TestCdObjectPresentation,TestCdExplosion,TestCdPoints,TestCdFlower,TestCdPlayerFrameRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestCdR11aObjectRetailAssets,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

The new slots are constructed transaction/device inputs, not a normal gameplay
route to a kill. The existing real EGL route still proves the first Ring only.
If a later allocation reuses a previously deleted/queued slot before its Init,
the bounded mapping resolver can stop at pointer0 where native would read
unowned memory. Preserve this shipped dirty-slot behavior as an explicit
coverage boundary; do not hide it with an ID/visibility or active-list filter.
Contact production, world score, hurt/death and complete USA remain separate.


### Body and Frame contact staging, 24 October 2026

Body now accepts an explicit ephemeral response context alongside the same
scanner/call-cut snapshot. The legacy two-argument context has no response owner;
items/no-overlap stay unchanged and handlers still reject without one. Enemy/
hazard response uses post-Display hurt and actual byte39, cursor invincibility,
scoreChain/debug/mini values, and declared Runtime-owned contact/world values.
Final actor/tile/priority/hurt/byte39/invincible/chain are adopted once before
SpecialChunks. One immutable response effect carries the final slot/world/contact
values; there is no pool write or duplicated global owner in Body/Frame captures.
Frame extracts typed FM/subCPU requests in call order before committing the whole
Body result. Display's queue cut remains before touch; final pose uses later state.

Root43304 passed264cases/0failures/errors/skips at13:51:10BST:100Body,93Frame,
28Runtime,12actor,4object-pass,15World,1actualEGL and11API. All8XML confirm
Java25.0.5.0.2, supplied media and explicit EGL. Queue467s/execution1m40.
All27newinvocations ran. Independent Opus review found noP1/P2 or wrong
assertion, and confirmed unchanged captured partitions and same-snapshot input.
It records weak assertions rather than treating them as proof of retention.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test -B
```

Runtime still must consume ObjectTouchResponse, prevalidate every changed slot,
commit world/contact globals once and capture them. Until U5, routine>=6 must
close before the death-camera/scroll branch; then the actor's final priority
projection cannot mislabel that death frame's earlier queue bucket. The native
three-slot FM drop policy and blocking subCPU timing remain unimplemented.
TerrainPlayerLoad/Boot are additional maintained consumers for the next combined
integration check. This commit alone admits no complete contact gameplay.


### Shared CD collision call cuts for Hurt, 24 October 2026

CdPlayerMovement exposes the existing native air-collision and LevelBound calls
without MODE admission, input, charge, motion integration or a new terrain/math
algorithm. The air call preserves routine4 and returns the actual scratch angle
and typed effects. LevelBound retains its signed word comparison, distinct from
HurtChkLand's earlier unsigned comparison, and stops before KillPlayer. Its
separate bored2B bottom branch throws only at that native predicate
(Sonic/Main.asm:1703-1707), because native sets routine6 directly there.

The direct landing regression stores0138 so its high-memory-byte threshold-9
rejects two-10 sensors; a wrong low-byte threshold-64 would land. The inside-bound
bored case remains admitted. All96 Movement invocations passed in Root81731's
406-case selection (one unrelated missing-S2-path skip was subsequently executed
alone); exact fresh Java25/media/EGL XML was inspected. Earlier315-case helper
coverage and the independent Opus source review found noP1/P2 or wrong assertion.
These are bounded CD call ports, not routine4 or death gameplay admission.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdOpeningObjectRuntime,TestCdObjectPool,TestCdPlayerMovement,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestModApiSignatureSurface' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
```


### World contact transaction and first actual enemy route, 24 October 2026

The opening Runtime constructs the actual asset-bound response once and passes
the SAME ephemeral pool capture through scanner, pure handler and unchanged-slot
compare-and-set. All new slot images and score/contact/ring values are validated
before writes; actor/invincible/chain values already belong to the final Body
and are not replayed from its earlier touch cut. The Runtime exclusively owns
contact(timeWarp/shield/combineRing), score/next-life native longs and HUD-score,
and captures/restores them once beside the existing ring/placement/SAT partition.
Module contact0/HUD-score0 are explicit DEV policy; score0/next5000/lives3 also
match first-load native reset writers. There is no retail title-entry claim.

Zero-ring contact death terminates at DEAD_CAMERA_FREEZE before effects/render,
because native's after-RunObjects death camera branch is still unowned. Ring-
bearing hurt reaches the actual unowned11 live dispatch rather than suppressing
it. Hurt4/death6/8, absolute native entry timing and complete USA remain open.

The first fixed controller-only real-World EGL proposal succeeded unchanged:
`2 -;80 R;repeat 14 {8 R+B;7 R}`. Call152 defeats first Mosqui(slot34), score0to10;
call160 visibly runs explosion/points; victim is Flower1F by178(off-screen in the
final picture). The checkpoint at82 and96-row continuation reproduce every
captured partition and final framebuffer through both full and course restore.
Terrain outside displayed sprites agrees with the completed-scroll control.
No native rows/clock/position/slot data feed this route. Durable pictures, input
and observations live externally in Downloads/Sonic-CD/native/engine-contact-
world-r1 and-r2; four PNGs are byte-identical between captures.

Root81731:406selected/0fail/0error/1skip at14:25:22BST;405executed passes,
Java25/media/EGL confirmed in12freshXML. Queue344s/execution2m09. The missing
S2-path cartridge-to-CD handoff case was then run ALONE with existing absolute
REV01 ROM verified CRC7B905383/SHA1 8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9:
Root11040 passed1/0/0/0 at14:27:33BST,21squeue/29.463sexecution. Thus all406
selected cases executed across the two invocations; do not sum the overlapping
historical selections as unique coverage. Independent Opus source review found
noP1/P2 or wrong literal in the seven-file world and two-file helper candidates.

Root strengthened W1 by restoring pre-contact score/life/shield values in the
constructed tests, W2 by checking empty terminal-death sound publication, and
W4 by checking the exact kill+26 continuation. Both rendered replay lanes now
draw/check each row; session/banks close before graphics/context; each capture
has its own correct CSV header and outcome. Root88628 passed35cases/0failures/
errors/skips at14:51:23BST (Runtime33/EGL2), Java25/media/EGL verified, queue458s/
execution32.288s. Production was unchanged; the concurrent pure KillPlayer call
extraction was separately validated by336affected cases, and remains a separate
source unit. No complete ordinary-smoke/full-suite/native-pixel parity claim.

```bash
python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldEgl' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
```


### Independent native contact controls and retained tooling, 24 October 2026

Promoted the bounded controller-only native Mosqui kill/hurt producer, probe,
checker, recipes, secondary corroborator and lossless picture converter. All
producer/probe/checker/recipe bytes equal the independently reviewed R2 freeze;
only the installed-layout test changes to assert sys.path preservation for both
an absent and already-present dependency path. Unittest discovery legitimately
adds the same tools directory before loading tests. The first promoted-path
run exposed that test assumption; after the bounded test-only correction all40
controls pass. Root also corrected a patch-helper naming error during that test
edit. No native process or tool algorithm changed during promotion.

The original R1 duplicate artifacts were hard-linked after validation and cannot
reprove independent repeats. R2 runs six fresh sequential fixed controls with
50evidencefiles/50inodes per route,64comparisonpairs,18786totalFrameAdvancecalls
(no discovery/newrecipe). Checkpoint equality and complete end-state comparison
are enforced; the imports are hash-pinned and media/research/explicit earlier
roots protected before scenario/preparation/output work. Independent source/
evidence review confirmed all four originalP2 fixes and byte-reproduced the
117-file inventory af804c0b…f60e, both event reports, and19secondary constant
comparisons. Its report-only unsafe recomputation command was corrected to a
read-only inventory exclusion check; no evidence file was edited. Root observed
40/40external and40/40promoted-path Python checks, not new native captures.

Native kill full raw difference is0; native hurt retains31unmasked differing
bytes in every mode, still unattributed. Eight offsets overlap the historical
player-referenceR1 ten-byte specimen; this does not prove a common cause. The
five V-int mod4 bounce matches are consistency only, not phase authority.
Control player/contact/dynamic placeholder categories are NOTOBSERVED, not
passes. The corroborator transcribes static expectations; it is not an independent
source reader. The tools produce comparison data only and never hydrate engine
state or grant timing/gameplay authority. Raw media/captures remain external.

```bash
python3 -I -B -m unittest discover -s tools/soniccd -p test_contact_reference.py
```


### Shared native KillPlayer call, 24 October 2026

The selected-touch cursor delegates to the same public pure KillPlayer call
that Hurt can invoke. It needs actual Actor/invincible/debug-word/scoreChain/mini
values, no fabricated selected object, world record, table or pool. Routine6
precedes ResetOnFloor; the post-reset Y writes both stick bytes; the ordered
reset/stop/Hurt calls and all unwritten fields match the former inline owner.
The full-word debug branch returns unchanged. There is no new death admission.
Root87184 passed336affected cases (99response,100Body,93Frame,33Runtime,11API),
0failures/errors/skips, including the actualUSAEnemyPoints view. AllfreshXML
Java25/media verified. Independent Opus review found noP1/P2 and confirmed
all9newliteral invocations. The report's LevelBound citation is1705, not1704;
the bored2B branch remains a distinct typed boundary in the collision port.


### Lost-ring body and spill globals, 24 October 2026

ObjLostRing11 owns all five routine entries and preserves native live allocation,
dirty routine addition, the zero-ring96-slot quirk, signed velocity word carry,
old-coordinate integration and the conditional floor/bounce/global-angle order.
It consumes explicit raw native V-int bits plus the physical RunObjectsD7,
never an engine counter or comparison row. Collect binds the unchanged existing
CdRing.collectRing through a package-only modifier seam; no duplicate reward
algorithm or saved-bank owner is introduced. Call/child/delete metadata is not
replayed by the caller. The separate immutable spill record owns only timer,
accumulator and frame; its native tail wraps as byte/word operations.

Root39416 executed79lost-ring cases (including bothactualUSAmedia methods and
2production reward bindings),21spill and26existingRing, all0failures/errors/
skips. Its one unrelated new clock-test wrapper-equality assertion failed and
was fixed separately; the233-case selection is not described as a233pass.
AllfreshXMLJava25/media confirmed. Independent Opus source review re-derived
all48velocity rows and actual floor probes, found noP1/P2 in these bodies/tests,
and confirmed d7 survives the native callees. MMZ and the dirty standing-object
branch remain explicit finite boundaries, and native phase is uncertified.
These device cases are not yet a normal-input hurt/landing gameplay claim.


### Explicit development V-int seed, 24 October 2026

The new external-touch factory overload accepts a declared unsigned native
longword seed at the admitted cut. The original factories keep an unknown seed.
No delta, engine frame or comparison observation substitutes for unknown time.
Only the existing service increments and shared snapshot restores the clock;
the getter exposes its immutable value and creates no second clock owner.
Cold actor, controls, art and cue state remain unchanged by a seed declaration.

Root39416 found one authored test error: record equality compared identity-based
CdAssetBytes wrappers between two separate runtimes. The test now compares both
complete buffers and scalar fields, with production untouched. The exactfailed
case passedalone in Root38507,1/0/0/0 at15:14:37BST (190squeue/26.846sexecution).
All96Frame cases subsequently passed in Root98350's149-case liveRuntime/World/
actualEGL selection at15:31:15BST,0skips; allfreshXMLJava25/media/EGL verified.
Independent Opus review found only that same testP2; source behavior is sound.
The eventual module's seed0 is an explicit DEV policy, not retail title/fade or
native phase proof. Hurt's additive Frame changes are a separate next unit.


### Live lost-ring frame composition, 24 October 2026

The physical ID11 dispatch now invokes the source-owned LostRing with the
Frame's declared raw absolute V-int counter, d7=127-whole, actual zone and bottom
bound. Unknown absolute time stops at the live binding before the body, never
substituting a delta or engine clock. The opening module explicitly selects DEV
seed0; terrain-only admission remains unknown. This is not retail entry-phase
or mid-pass lag timing certification. One runtime store owns spill bytes,
capture/restore and the once-per-unpaused-loop tail after Ring animation.
Shared rewards and ordered sound/angle effects are applied once; allocation,
call and delete observations are not replayed.

Root98350 passed149affected cases with actual media/EGL, no failures/errors/
skips. Root95177 then passed38Runtime cases including two review regressions:
a seed1/slot42 probe distinguishes absolute time from delta/engine frames and
requires exactly one angle write; an FE/FF/0 spill capture is restored and its
next tail replays to FD/1FD/0. Exact reward sound order and paused-clock advance
are now checked. World captures include the spill. Independent Opus source
review found noP1/P2. CdObjectState.signedWord is a real short-returning helper;
the earlier brief's contrary premise was wrong, not a production defect.

These checks remain bounded. Root95177 selected532cases at8314b20f6 plus eleven
pending source/test files:531passed, no skips, while the new walking input
proposal missed contact and reached the existing unowned FloatBlock12. This is
not described as a532pass. Hurt admission and its final controller route are
recorded in the next checkpoint, not inferred from device cases.


### Hurt recovery and real ring-spill route, 24 October 2026

Routine4 now follows ObjSonic_Hurt source order: old-velocity ObjMove, word
$30 gravity (underwater subtracts$20), PP special collision, unsigned HurtChkLand,
signed LevelBound, history, animation and unconditional DrawObject. Both bottom
calls use the same pure KillPlayer owner and return into Hurt. Main's Display,
timer service, input copy, MODE, touch and special chunks are absent. Landing
stops all three velocities, returns routine2 and writes hurt120; next Main
resumes at119. Standing-object movement remains a named finite dependency.
Actor queueing accepts the complete Hurt draw cut and the Frame preserves
ordered collision/KillPlayer sounds. Both Root clock ports and all Hurt changes
were composed; capture/restore retains both. Independent Opus source review of
original and composite6files found noP1/P2 or wrong assertion. Its prerequisite
KillPlayer port is committed in d40cb962c. Test comments now describe authorship
without the historical UNRUN label; frozen mirrors/reports remain unchanged.

Root95177,8314b20f6 plus eleven pending files, finished15:53:14BST with532tests,
0assertion failures,1error,0skips (43squeue,2m03execution). All12freshXMLs confirm
Java25.0.5.0.2, actual CD media/EGL and the existing S2REV01 property. Body120,
Frame99, Actor15, shared actor4, TouchResponse99, Movement96, Runtime38,
WorldLoad15, TerrainLoad24, TerrainBoot8, API11 and the two existing EGL routes
passed. Only the new walking proposal missed contact and reached known ID12.
No production state or gate was altered to force an encounter.

Rejected controller proposals are preserved externally: continuous Right
passed the Mosqui; coast/rest/Right restarted too quickly to meet its dive.
One observation-only rerun recorded the full patrol toX751, dive selection at
call439/X696, groundwait473 while Sonic had already reachedX797. It justified
waiting for the returning patrol and then a short walking approach. No fitted
physics, guest writes, player/object fixtures or production frame fences were
introduced. Final fixed input is `2 -;108 R;500 -;100 R`.

Root68427 ran ONLY the revised actual EGL method, passed1/0/0/0 at16:00:46BST
(0squeue,31.780sexecution), with freshXML Java25/media/EGL confirmed. Natural
hit644 had2rings: routine4, anim1A, hurt120, Xvelocity-$200/Yvelocity-$400,
rings0 and two real LostRing objects. Landing686 resets speeds/routine/timer;
Main687 resumes hurt119. From the call82 full/course checkpoints, each replay
matches605successive captured rows and finalpixels; each replay row is rendered.
The comparator covers actor, shared environment/art/clock, live slots/savedbank,
spill/ring/contact/score globals, CPU SAT/display, camera/scroll, logical artbank
and level clock. Terrain outside sprite pixels agrees with completed scroll.
Five actual PNGs plus fixed input/CSV/outcome remain external; selected pictures
were visually inspected. This is actual OpenGGF DEV evidence, not native timing,
FM playback, pixel/full-game or regional parity. The module remains silent and
uses explicit art preload/DEVseed0. Routines6/8, MODE kill continuations and the
same-frame death camera boundary are the next owners.

```bash
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdPlayableSonic,TestCdPlayableSonicObjectPass,TestCdPlayerTouchResponse,TestCdPlayerMovement,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot,TestModApiSignatureSurface' "-Dsonic2.rom.path=$S2_REV01" "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 "$OPENGGF_MAIN/tools/testing/maven_queue.py" --normal -Dmse=off '-Dtest=TestSonicCdOpeningWorldEgl#actualEglWalkingHitSpillHurtLandingAndFullCourseReplay' "-Dsoniccd.media.root=$SCD_MEDIA_ROOT" -Dopenggf.headless.gl=surfaceless-egl -Dsoniccd.opening.egl=true test
```


### Diving contact and retained-native observations, 24 October 2026

The actual EGL hurt test now shares one route owner for two real contacts:
grounded Mosqui8/two rings and diving Mosqui6/three rings. Native's firstRight
takes effect at level198; the earlier engine walk started at3 and met another
patrol phase. A neutral controller lead-in (`197 -;108 R;252 -;153 R`) aligns only
the input origin; no native gameplay value, clock seed or source gate changes.
The diving route hits600, lands643 and resumes119on644, matching the retained
native event numbers. These are comparison observations, never frame fences.

Root2436 at3f3d13a06 plus ONLY the changed testcase passed bothaffected methods,
2/0/0/0 at16:16:14BST (37squeue,35.040sexecution), freshXMLJava25/media/EGL
confirmed. New full/course checkpoints82 each replay562rendered rows and
finalpixels. Existing grounded605row route passes after helper extraction too.
No production delta. Original Ring/defeat methods do not use the changed helper
and were not repeated. New5actualPNGs/input/CSV/outcome remain external.

Read-only in-memory join to all overlapping accepted native forward polls uses
actual globals.levelFrames, no fitted ordinal shift or skipped row. At54unique
levels591..644, all23shared fields other than absoluteVInt match exactly:
fullXY, signedXY/Gvel, routine/flags/animation, hurt/rings/lostRingcount, three
spill values, cameraXYwords, heldinput/action and firstMosqui id/routine/XY/
spriteflags. That is1242exact scalar comparisons. The24th field, absoluteVInt,
differs onall54rows by61(native higher):54mismatches out of1296comparisons,
explicitly reported and not applied to gameplay. This verifies only these short
observed fields, not all actor state, device timing, native pixels/audio, later
bounces, whole-game or regional parity. DEVseed0 remains declared policy.


### Pure dead-camera bound write, 24 October 2026

CdR11aForegroundScroll.deadCameraFreeze models only Level_MainLoop's two word
writes after all object slots (Level/Main.asm:405-418). Both bottom bounds take
the current cameraY high word. All camera fractions, cross bytes, differences,
flags, shift/lock/left state, bounds left/right/bossFight and Y-centre remain
unchanged. It runs no LevelScroll/event path and does not select the branch;
levelRestart/debug/routine admission and SAT/tail completion remain caller-owned.
The helper is not yet wired into the World. The audited current early post-Body
death stop is still present until that integration unit lands.

Root56397 atf03955e65 plus ONLY the two camera files passed61/0/0/0 at16:29:52BST,
0queue/1m29execution, freshXMLJava25 confirmed. Five new invocations cover every
unchanged field, the fractional camera high word, signed-boundary raw words,
null owners and word-centre validation. Existing56foreground cases also pass.
This pure value unit does not prove a completed death frame or restart route.


### Powerup/Game Over raw art service, 24 October 2026

The new pure CdPowerupArtRuntime models LoadShieldArt (Powerup/Main.asm:157-186):
BCLR7 gates the lookup, byte SUBQ2/word double selects one of five registered
raw copies, DBF copies exactly1024bytes into complete caller-supplied CPU RAM,
and a1152byte DMA replaces the whole resident window atA880. The trailing128CPU
bytes are preserved, including nonzero carry; no source padding or defaultRAM
is invented. Inactive bit7 skips the lookup for anylowbyte, while unboundactive
pointer offsets stop before a Result. Powerup remains external, returned once
withbit7cleared; the component holds no mutableglobal/clock/capturebank. State
compares immutable bytes bycontent. Caller still owns actualVInt admission,
logical/global commit and DMA publication; draw/restore cannot resubmit it.

Root41425 at9694cefe1 plus these2newfiles passed20/0/0/0 at16:44:06BST
(100squeue,1m30execution), fresh2XMLJava25confirmed:18source-literal service
cases and2existingraw assetdecoder cases. Fivecopies, request-bit behavior,
bounded invalidselectors, retainedtail/fullupload, immutablebytes and replay
are covered with constructed format input. No media identity, native timing,
GPUupload or GameOver world support is inferred. Runtime/atlas/snapshot wiring
and the349-pattern C2picture remain next integration work.


### Explicit death/reset state ownership, 24 October 2026

The player Timers record now requires the actual reset word3A in addition to
hurt/invincibility/shoes. There is no default three-word overload. Every
constructor is migrated: cold0 is sourceClearObjects, existing state preserves
its word, and Body cursor/capture/projection retain it. KillPlayer's adjacent
word38 does not overwrite3A. RESET_TIMER is reserved for its native writers.
The new nine-field CdLevelGlobals has one Runtime store and private-owned
capture/validation/restore. DEVmodule supplies every field explicitly; HUD-time0
is the existing omission, not the native1. The flowerCount24-bit value stores
three independent per-time-zone wrapping bytes; no writer may carry across
zones. Clear-long also spans the adjacent fade byte. No state writer or runtime
6/8 context is admitted by this shape-only unit.

Root56302 at746c3e192 plus14exactpendingB0/MODE files passed576/0/0/0 at17:11:28BST
(760squeue,2m14execution). All13freshXMLJava25.0.5.0.2/actualmedia/EGL/existingS2
ROM confirmed:11LevelGlobals,128Body,103Frame,15Actor,4sharedActor,99TouchResponse,
115Movement,39Runtime,15WorldLoad,4EGL,24TerrainLoad,8TerrainBoot,11API. The24new
B0cases include nonzero timer retention through Init/Main/Hurt/contact/Kill,
Frame ports/replay, native widths and explicit Runtime ingress. Changed-value
global restore still awaits a real writer; no setter was invented. Independent
Opus review found noP1/P2 or wrong assertion in all12frozenfiles. Its packing
clarification was added as comment only after execution; frozeninputs unchanged.


### Native MODE return after KillPlayer — 24 October 2026

The four source-owned MODE tails now share one implementation between the
ordinary path and `resumeAfterLevelBound`. The resume consumes the stopped
LevelBound result and caller-returned state, validates fields the shared
KillPlayer/ResetOnFloor routines cannot write, and returns tail-only effects.
It neither re-dispatches from routine 6 nor repeats the MODE prefix. Body
composition is still pending; the pure helper does not itself admit pit deaths.

The same completed Root 56302 selection recorded above executed all 115
Movement invocations, including 19 new continuation cases, with no failure,
error or skip. The independent source review found no P1/P2. Constructed
terrain coverage is not a World/native timing or pixel-parity claim.
The frozen handoff's air-resume prose should read Y $07EA.8000; its tests
already preserve that fraction. Some selected global angle buffers remain
unchanged, as the assertions record.


### Pure Game/Time Over owner — 24 October 2026

`CdGameOver` now ports the source-owned $3B Init/Main stores, live allocation,
child ordering and typed sub-CPU requests. The separate authenticated lazy
56-byte mapping view retains its two relative table bases. No World dispatch,
resident powerup service, text renderer, audio or restart is claimed here.

Root44257 at382ef3e6e plus the six exact candidate files and a tests-only
World regression executed29 GameOver,14 constructed asset and3 regional
retail cases successfully, zero skips. Both new World rows independently
rejected at the old DEAD_CAMERA_FREEZE, the intended negative control.
The complete request was48/0 failures/2 errors/0 skips, not a green48 run.
Java25.0.5.0.2/media and all four fresh XML were inspected. Independent
Opus source review found no P1/P2. Only the view is observed while media is
open; the new fromObjectAssets calls run after close. JP's identical blob
is elsewhere; no regional GameOver owner or offset inference is admitted.


### Native player death fall, restart delay and Main return — 24 October 2026

The pure Body now runs routines6/8 from an ephemeral caller-supplied LevelContext.
It publishes a staged lives/HUD/GameOver-slot summary, full-pool alias metadata,
the restart saved-flags request and the typed relative Z80/FADECDA requests.
No pool/global/driver operation is replayed inside the Body. Main calls the
shared KillPlayer once and resumes the original movement tail, then completes
its remaining source calls. Frame contexts are not retained; Dead draws and
Restart does not. World commit and actual level reconstruction remain separate.

Root74991 atab8804a6a plus the six exact Body files and four camera files
executed542checks:541passed and one existing Runtime assertion was accidentally
changed by Root's broad test-text edit. No production defect caused that failure.
Restoring only the old ChargeSound expectation gave Root82194:40/0/0/0 for
the entire Runtime class. The other502cases passed in74991, including Body195,
Frame106, Actor18, Movement115 and actual EGL/restore consumers. All selected
XML, Java25/media/EGL and zero skips were inspected; this is not a single
combined542green run. Independent Body source review is pending at this commit.
The routine6/8 source and tests are byte-identical to the frozen Opus handoff.


### Selector-eight level-art source cut — 24 October 2026

The Frame accepts one ephemeral level-art call after Sonic DPLC/DMA and before
VInt_Finish, clock increment and Start toggle. Ordinary and normal-pause service
call it even when no Sonic DMA was pending; PP lag never calls it. The callback
is not stored. Failure keeps completed controller/player-art writes, closes
admission, and skips Finish, clock and Body entitlement. Existing terrain DEV
retains its explicitly omitted level-art owner through the original overload.

Root17600 at276cf1b6e plus the two exact port files and four camera files:
`maven_queue.py --lean -Dmse=off -Dtest=TestCdPlayerFrameRuntime test`, with
DISPLAY/WAYLAND_DISPLAY unset and Lua5.4.112/0/0/0, Java25.0.5.0.2 fresh XML
inspected, no queue wait,87s execution. Six new cases pin selector8/pause/lag
order, dirty Sonic DMA, no-DMA service, pre-Finish failure and null preflight.
This is the call port only; the World's powerup-state/byte/service binding
and real hardware timing remain pending.


### World death-camera cut — 24 October 2026

The World now checks levelRestart after every object slot and before scroll.
With debug off and the final player routine at least6, both bottom words take
the current cameraY while the camera/scroll state is kept. Sprite construction
and the ordinary loop tail still execute. The former post-player rejection
is removed. A pending actual restart remains a typed stop before scroll/SAT.
HUD-time input comes from the single level store; terrain DEV retains0.

Two constructed actor/slot cases through the actual World distinguish Main's
pre-contact draw bucket2 from Hurt's post-kill bucket0, run a later live Mosqui,
freeze bottom/camera, execute the ring tail and restore/replay the whole cut.
The original production rejected both (Root44257 negative control). They passed
inRoot74991 with the other502non-Runtime cases, including actual EGL routes.
Root's unrelated old Ring sound assertion edit was restored exactly in R2;
all40Runtime cases then passed in82194. Independent review's sole finding
was that same resolved test literal; no unaddressed P1/P2 remains. It was a
static review, not execution. No natural-input death or native parity is claimed.


### World-owned death and restart summaries — 24 October 2026

The Runtime passes the same pool snapshot to touch and level contexts. After
the complete Body succeeds, one validated summary commits lives/HUD and the
ID-only Game Over slot image, or resets the actual saved-object bank and
accepts the restart level record. All immutable values, allocation, owner,
image and alias checks precede the first pool write. Full-pool $FFF000/$3B
remains typed Body metadata, with no invented pool slot or host RAM buffer.
Final actor values and descriptive LevelWrite/call effects are not replayed.

Tests-first Root70992 hit the intended missing-context boundaries in all three
new cases (two DEAD_ROUTINE, one RESTART_ROUTINE). After the production change,
Root92167 passed all60affected checks:43Runtime+17actualWorldLoad, zero failures,
errors or skips. Java25.0.5.0.2 and supplied-media properties were inspected in
both fresh XML reports. Queue366s, execution86s; finished18:03:49BST. Independent
source review is pending. The later live $3B dispatch and actual LevelStart
reconstruction remain separate; this does not claim a completed death route.


### GAME/TIME OVER mapping and resident pixels — 24 October 2026

The immutable resolver now registers two two-frame roots over the unchanged
authenticated56-byte include. A separate nine-span DEV pixel policy consumes
the complete now-serviced36-tile powerup resident window, including explicit
RAM tail, alongside the prior eight sources. No service or default RAM is
hidden in the converter; the Runtime's shared byte/state binding is next.

Root23905 at1dca98b36 plus the four exact picture files passed83cases:
Mappings9, Presentation14, Runtime43 and WorldLoad17, zero failures/errors/skips.
Java25.0.5.0.2/media were verified in all four fresh XML, including execution of
actualUsaGameOverRootsAndNowServicedPowerupWindowDrawOnlyTheirRetailPieces.
Queue16s, execution88s; finished18:09:59BST. The independent source review
found no P1/P2. This proves bounded mappings/pixel-source behavior, not actual
Game Over world rendering, lifecycle restart, native DMA or hardware pixels.

Subsequent independent Body and world-summary/level-art reviews also found no
P1/P2. Their supplied execution results remain Root's observations; their
static source reasoning does not count as another run. The unreachable killed
ceiling/tunnel stop remains an advisory for the later roll-tunnel owner.


### Live Game Over and powerup art ownership — 24 October 2026

The Runtime now dispatches $3B in the same physical object walk. Lives are read
from the Ring store; powerup and timeOver are read and updated through the one
level-global store. Only typed sub-CPU requests enter the ordered sound ledger;
committed pool/global/call metadata is never replayed. One complete immutable
powerup CPU/resident state is captured, validated and restored with the World.
The selector8 callback services it after Sonic art and before Finish, including
pause; lag retains it. Picture conversion then uses the now-serviced nine spans.
Cold zeros are justified only for the existing fresh DEV admission, not restart.

Root5086 at978ec39d8 plus the exact Runtime2/client3 candidates has fresh reports
for255checks with zero failures, errors or skips: Runtime50, Presentation14,
Mappings9, Frame112, Powerup18, GameOver29, WorldLoad17 and EGL6. All eight XML
confirm Java25.0.5.0.2, supplied media and the requested EGL properties. Queue
telemetry ended18:44:17BST with1.8s waiting and99.8s execution. The command handle
was unavailable after context recovery; the completed JUnit/queue evidence was
used without repeating validation or inventing a recovered console exit status.
The independent Runtime source review found no P1/P2 and re-derived all seven
new cases. It also checked actual Nemesis extents: Springs ends exactly at
$A880 and HUD starts at$AD00; no R11A PLC overlaps the powerup window. On an
unreachable invalid-selector failure, the pure owner publishes no partial
BCLR result, so the closed Runtime keeps its pre-call diagnostic byte; this is
not the native post-BCLR byte and no closed owner consumer may rely on it.
Actual World route/capture results belong to the following client checkpoint.


### Controller death and constructed GAME OVER in the World — 24 October 2026

The presentation now retires all349picture patterns. WorldLoad and EGL rewind
comparators include both complete powerup windows, and the pickup oracle uses
all nine now-serviced art spans. The actual controller-only death route uses
fixed ordinary input, never a position, slot, clock or reference write. It
first takes a walking hit at644, then a zero-ring second contact at968. The
death camera remains(377,1803); lives fall3→2 at1052, reset60 then56 at1056.
The newly allocated lives-remaining GameOver object deletes itself in that
same physical pass. Full and course restore each reproduce974 rendered rows
and final pixels from the pre-event cut. Actual outputs and inputs are retained
under Downloads/Sonic-CD/native/engine-death-world-r1, outside the repository.

A separate constructed scene uses three same-owner gone-line actor cuts, each
preceded by clearing ALL dynamic slots, then21neutral forward inputs. It is
explicitly not a controller-only route to zero lives. The actual World runs
the GameOver bodies and selector8 art service; EGL renders32retail glyph tiles.
Both rewind modes replay the21rendered rows and final picture. Retirement then
removes the glyph atlas entries beyond the prior313-pattern bank. A terrain-only
absence control differs from the rendered GAME OVER image.

Root5086's255affected checks recorded in the preceding section include both
new EGL cases, the four maintained EGL routes and lifecycle consumers, without
skips. Independent client review found no logic defect but two evidence P2s:
PASS was written before cleanup assertions and labels omitted the dynamic-slot
clears. R2 keeps all R1 logic and moves PASS after successful retirement, with
complete construction labels. The R1 JUnit run did pass all cleanup assertions;
its original outcome ordering is historical rather than proof on its own.

Root4338 deliberately restored cleanup313:1test/1failure/0errors/0skips at
19:02:16BST, specifically first added glyph131385 still present; no outcome.txt
was written. Root6117 was an invalid positive attempt: copy2 preserved an old
source timestamp, Maven reused the negative bytecode and produced the same
failure with no outcome. After advancing the restored349source timestamp,
Root46132 actually recompiled and passed1/0/0/0, exit0 at19:09:51BST. Fresh XML
confirm Java25/media/EGL, and the new PASS follows all cleanup checks. Its GAME
image is byte-identical to the original. This does not add three passes to255.
The narrow repair did not change production or other test behavior; no broad
rerun was duplicated. Both review P2s are resolved.

These are silent DEV entries with explicit art preload and V-int seed0.
Native LevelStart fades/title/HUD/audio/PLC timing, actual reconstruction,
zero-lives program exit, pixel parity and the complete game remain open.

### Shared SolidObject probe ports — 24 October 2026

The CD movement owner now exposes CalcRoomInFront, CheckFloor and GetCeilDist
as thin calls into its existing private probes. Each returns the native signed
distance and optional primary/secondary angle-buffer stores, including stores
that preserve the old value or precede a zero-height fallback. Room seeds both
buffers before probing. A private probe-only cursor has no Environment/input;
unwritten scratch values never escape it. MODE continues to use the same probes
and final-value behavior. Source is Collision Check.asm:14-65,79-125,298-314 at
the existing CD pin; no collision algorithm or route-specific correction is added.

Root26637 tested the exact two-file port at7b758b364 plus those changes:
211tests/0failures/0errors/0skips, Java25, comprising Movement123, Ground54,
Terrain20 and Math14. All eight new constructed probe cases executed. The later
affected533case selection also ran the new88case pure SolidObject consumer;
that consumer remains a separate unit. Independent source review found no
P1/P2. Its optional suggestions concern direct ProbeResult validation coverage
and a room probe whose stored angle differs from its seed; no incorrect
expectation or production defect was found. No World standing/pushing or
Floating Block dispatch is admitted by these ports. Full-USA and regional
completion remain open.

### Authenticated Floating Block data — 24 October 2026

The USA object asset owner adds two lazy views: Ani_FloatBlock at$A3DC/30 and
MapSpr_FloatBlock at$A3FA/76. The existing strict decoder accepts six frames,
three scripts and15tiles without another format seam. A separate lazy art bank
binds ART_FLOAT_BLOCK to PLC_Std row2, VRAM$6680, tile$334..$342/baseword$4334.
The source blob is230bytes; the Java bit reader consumes231realbytes, including
the first$00byte of the adjacent SpringWheel blob at$366B6. No padding is
invented. Selector5/subtype2zero is newly admitted; selector0 is unchanged and
all other consumer combinations still reject. The original nine eager tables,
two initial art decodes and existing three art banks remain unchanged.

Root73022 passed34/0/0/0 with Java25 and supplied media: Visual13, constructed
Assets18 and all three regional retail invocations. The USA first lazy decode
after source closure executes; Japan/Europe remain explicitly unadmitted.
Independent source review found noP1/P2 and re-derived the three real mapping
alignment bytes, table/PLC operands and reader adjacency. This authenticates
data only. The Floating Block body, live mapping/art presentation, solid-player
state and world dispatch are separate work, not an act-completion claim.

### Carried restart and native restore admission — 24 October 2026

The opening World now ends the source LevelStart cut after every object slot,
without scrolling, rebuilding SAT or running the Ring/spill tail. The old PLC
token drains on its own owners, and the cut remains capturable. The next
beforeFrame builds an explicit native carry and invokes the existing level
load owner before admitting the new loop. Lives, score/next-life, saved object
bank, history, Sonic art, debug scratch and the declared raw-held latch carry;
the source-cleared fields use explicit ingress. Powerup CPU RAM is cleared and
its resident window carries. The load omits audio reset/configuration, retires
the old player/Frame/PLC/presentation owners, creates a new visit and emits one
LEVEL_LOAD rewind boundary. Lives0 and unowned restart paths stop typed.

Root69093 compiled and ran328cases:4failures,0errors,1skip. Two new assertions
incorrectly compared CdObjectState wrappers from different pools. R2 compares
the physical Identity and all64bytes through the existing public copyBytes();
the independent review's claim that no such accessor exists was incorrect.
The corrected77devicecases passed in Root53753, including their previously
unreached tails. Pool owner equality was not weakened. The one skip was the
reused-boot cartridge-to-CD case, because the first request omitted the S2ROM.

The other two failures exposed a scope assumption: raw RewindRegistry captures
share their WorldSession across loadLevel, so the old camera restored before
the late CD adapter rejected its retired Frame. The original CD-only review
recommended removing these raw-restore assertions; sanctioned live/course
paths already isolate epochs. This work deliberately adds a separate shared
native preflight rather than weakening the unchanged-state checks. It does
not promise arbitrary adapter rollback or global transactional restore.

NativeRewindRestoreGuard is an engine-internal opt-in. Only an actual native
root publication admitted into this registry may validate before full/course
restore. Foreign scope and course key checks still run first. Missing guarded
payloads reject before any earlier restore/reset. Ordinary registrations gain
no authority; generic adapter order, RNG-last and callbacks stay unchanged.
The existing CD opening validator is factored into this preflight. Its normal
rejection paths preserve gameplay state; immutable-source authentication may
memoize, and already-broken lifecycle owners retain their fail-closed rules.
The terrain-only adapter remains unguarded for raw old-visit tools; sanctioned
epoch/course paths are safe, and this pre-existing residual is not claimed fixed.

The tests-first interface-only control Root53753 produced the intended five
guard failures (6guardcases), while77correctedDevicecases passed:83/5/0/0.
Root57948 then passed533/0/0/0 across24affected classes, Java25, real supplied
media, surfaceless EGL and the existing S2ROM. Native-publication authority,
registry, session, owner-bound patch, API pin/signature and reused-boot checks
all executed. Independent restart device/World/probe and shared-guard reviews
were read in full; no productionP1/P2 remains. The shared guard review's
missing-payload and validator-purity documentation advisories are clarified
without changing code behavior. Broad smoke/guards remain pending.

Actual fixed controllers reach death968, life3→2/reset60 at1052, the old cut at
1112 and the new visit at1113. Generation1→2 and frame/V-int delta1 are observed;
Sonic returns to64,$76B with two lives. Full and course restore each reproduce
54rendered new-visit rows and final pixels. Live rewind also exercises the new
epoch's frame-zero saved bank before SpawnObjects and frame-one replay. Raw
old composites now reject without mutating the new visit. Captures, complete
input/CSV and provenance are external under native/engine-restart-world-r2.

This is a DEV restart: no native fade/loading/title/HUD/audio/PLC timing,
explicit clock seed0 and raw-held/tap policy, and no timing/pixel/full-game or
regional parity claim. The complete USA game remains the goal before regions.

### Pure native SolidObject owner — 24 October 2026

CdSolidObject ports every Object Solid.asm entry at the existing pin, including
GetOff/StandOn, top/bottom/plain solidity, pushes and all four shared KillPlayer
sites. It returns final player/object/global values, native write sets, d0 and
ordered calls. It reads an ephemeral call-cut capture only for actual a2
dereferences and commits no pool, player, angle buffer or sound. Existing
movement probes and KillPlayer are reused; no physics is copied. Reserved
and out-of-pool stand/push bytes stop at actual dereference, while byte0's
player-slot alias and compare-only paths preserve native behavior.

Root57948 executed all88cases, including the supplied-USA binding, without
failure/error/skip. The independent source review found noP1/P2, re-derived
all13probe distances, all22get-off reasons and the four kill sites. Before
commit, documentation clarifies three reviewed details without changing code:
bytes192..255 wrap on the24-bit bus; stick clearing depends on GetOff guards;
and SOLID_TYPE/ROUTINE_2 are one storage byte at$25, not separate writes. The
open-air prediction test does not distinguish the killed Y velocity; stronger
row-terrain coverage remains advisory. S4 must validate pool ownership before
committing the returned object/other-slot images. World integration, the
player's support continuation and Floating Block dispatch remain separate.

### Player push/stand storage — 24 October 2026

Body State now requires ObjectLinks(pushObject,standObject), the unsigned raw
bytes at$20/$3D (_Definitions.inc:92-93). They hold whole-slot indices rather
than object IDs. All0..255 values are retained; a solid-object dereference,
not storage construction, owns reserved/out-of-pool address admission. There
is no old State overload or zero fallback. LevelStart's ClearObjects supplies
explicit cold/restart zeros; Init and all Body calls preserve supplied values.
Existing actor capture/Extra rewind owns the pair automatically. State copies,
the narrow projection/Y-velocity ports and constructed test cuts preserve them.

The first21-constructor migration missed the fully-qualified EGL call: Root87868
stopped at testCompile, exit1, with no JUnit execution. R2 adds old.objectLinks()
to that ninth file; it does not weaken the new required ingress. Root86248 then
passed486/0/0/0 in11affected classes, actual CD media/EGL/S2ROM and Java25,
exit0 at20:33:31BST (0swait,81sMaven). Body199 and Frame121 include eight new
width/null, Init/Main/Hurt, cold/restart and actor restore/replay cases. World,
render, actor and signature-pin consumers also executed. Independent Codex
review accounts for all21calls/ninefiles and finds no remainingP1/P2 after R2.
This owns the storage; solid-result application and the next supported-object
MODE continuation remain separate. No new World support or parity is claimed.

### Native Floating Block body — 24 October 2026

CdFloatBlock now owns the seven source routines, using the authenticated lazy
USA mappings/scripts and selector5 art. Each TopSolid pair publishes its working
object, captures one cut, invokes the pure SolidObject owner and synchronously
requires the commit port to publish the returned pool images. Player/globals
thread into the next call; the Future Main-to-Fall continuation makes four calls.
Visible writes animation0 before solidity, Reset makes no Solid/Draw call, and
Fall preserves the native delete-and-return wrapper behavior. Source boundaries
reject before their first write; failures after published source writes do not
roll back. There is no new player, pool or snapshot retained in its result.

Root79585 at48562c827 plus this exact two-file owner and the separate S3 Frame
port passed311/0/0/0, exit0 at20:43:50BST (0s queue,86s Maven). The selected
Frame126, Solid88, Float42 and Runtime55 reports all show Java25, actual supplied
USA media and zero skips. All42Float invocations executed, including the media
binding. The independent Codex review read every method and caller contract,
found noP1/P2 or actionableP3, and retained matching frozen fingerprints. Its
source-only verdict is separate from Root's execution. The Draw test stand-in
fuses enqueue and later culling; the real queue/live-slot path remains S4 work.
No live World dispatch, mapping/art-bank admission, supported-object MODE,
route reachability, act completion or native timing/pixel parity is claimed.

### Guarded solid player/global commit — 24 October 2026

Frame now validates and applies the final SolidObject Player/Globals against
the live value cut. Every native PlayerField and GlobalField has a projection;
integer/fraction words are checked independently, and unrepresented motion,
slot identity and undeclared changes reject. Validation is pure. Apply builds
all immutable state before assignment and adopts once into the existing actor
and shared owners. Display, history, non-hurt timers, art, clocks, input and
publication remain untouched. There is no pool, effect replay or sound work.
Value equality preserves admission; it is not a source-call identity token.

The five new component cases execute within Root79585's311/0/0/0 result above,
including pure preflight, a real StandOn/GetOff pair, both rewind partitions,
replay, all four globals, stale/undeclared writes and lifecycle rejection.
Independent source review found noP1/P2. Its nonblocking coverage advisory
is retained: direct nonzero-fraction and Solid-triggered KillPlayer port cases
are not yet present, though the implementation and separate owner paths were
checked. S4 still owns same-cut pool coherence/CAS before publication, a
synchronous apply without intervening callbacks, ordered FM forwarding and
failure closure. Next supported-object MODE is not admitted by this port.

### Live Floating Block composition and actual World pixels — 24 October 2026

The physical dynamic walk now owns ID$12. Every native TopSolid call validates
its exact same-pool cut, the complete live pool, all returned image owners and
Frame's declared player/global write sets before the first result write. Pool
CAS then Frame adoption run synchronously with no callback between. Only FM
requests enter the existing contact sound ledger; source-call metadata is not
replayed. A later slot observes earlier stand/link/angle writes and can release
the previous slot. The object's pre-call source stores remain on failure; the
queue/Frame close, with no rollback. Existing native Solid/Float owners are reused.

The byte-equal mapping join adds20A3FA's6countedframes (empty0), and a separate
preloaded Float art span adds native334..342. Older pixel policies remain intact.
LogicalBanks derives a tenth span at powerup+36 in its older constructor forms;
all ten spans validate even with emptySAT. World reservation/retirement now spans
364patterns. This remains named DEV residency, not live native sectionPLCs.

Tests-only Root46828 hit exactly the missing$12dispatch in five cases (59tests,
1failure/4errors/0skips). Root52852 then passed483/0/0/0 across16actualclasses,
exit0 at21:03:46BST (0squeue/138sMaven), actualmedia/EGL/S2ROM/Java25. A mistaken
TestApiSignaturePin selector matched nothing; the corrected separate
TestModApiSignatureSurface request88687 passed11/0/0/0, pin unchanged. Independent
Codex review found noP1/P2 and re-derived actual terrain probes, fractions,
angleC0 writes, live two-block release, exact FM order, SAT and picture ranges.

All three P3 proof/wording advisories were addressed in tests/comments, with
production unchanged: actual playerSAT uses post-objectX65/Y721 plus native
mapping offsets; GPUdifferences are required within a Float-only footprint that
excludes Sonic/other tiles; full/course replay compares all6intermediate rows'
pixels. Root68523 passed the2affectedmethods,0failure/error/skip,exit0 at21:15:17BST
(58squeue/31.225sMaven). No unchanged broad selection was repeated for that edit.

Real HeadlessGameBoot neutral controls exercise a constructed slot40Fall at
(200,740) after explicitly clearing ALL dynamic slots. Seven Fallcalls endY74E;
full/course eachreplay6rows/finalpixels and remove the nine used Float patterns
on World retirement. No player/globalsetter, normalPresent-to-FutureFall route,
standingMODE, nativeplanes/PLCs/timing/pixel/act/fullgame claim. R1/R2images/input
match byteforbyte; captures/provenance remain external under
native/engine-float-block-world-r1 and-r2. Broad smoke remains pending. The next
source dependencies are supportedplayer movement, actual rampBG and earlier
Springdispatch; neither gatesuppression nor staticBG substitutes for those owners.

### Supported player, ramp MODE and source-ordered flipper commits — 24 October 2026

Movement now borrows an invocation-local object cut for the native stand-slot
reads at jump/flipper, idle balance and ObjMove. Whole slot0 aliases the working
player; dynamic32..127 reads use real immutable pool images; reserved/outside
addresses stop at the actual read. Shared ObjMove and ObjMoveGrv preserve native
local resistance, OLD Y-velocity integration, caps and arithmetic wrapping.
Hurt and Dead reuse these moves at their existing source cuts. Ramp control
bit1 runs the native MODE branches; charge keeps bit0/routine/size/mode guards
and admits that real ramp byte without normalizing it. Other control/warp
boundaries remain. Native owners: ObjSonic modes/CheckJump/ChkFlipper
(Level/_Objects/Sonic/Main.asm:675-776,1779-1861,3112-3157) and ObjMove/ObjMoveGrv
(Level/Object Functions.asm:42-128), at reference pin1ecc5f3a.

Body, Frame and Runtime forward one borrowed context from the same post-spawn
pool snapshot as touch and level context. A flipper animation store is an
immutable before/after patch; a later touch scans and responds against the same
locally overlaid snapshot. After the whole Body succeeds, Runtime folds final
images in native effect order, validates every real owner and the full original
cut, then CAS-writes final pool images and commits external globals once.
A same-slot enemy response retains the earlier animation store. Actor/effect
metadata is never replayed; exact sounds remain Frame-owned. Captures acquire
no snapshot/context owner, and real SolidObject later detaches a jumped player.

Tests-first Movement RED40558 stopped at RAMP_ENTRY as expected. First combined
Movement287 check46048 exposed the unchanged Charge ramp guard and invalid
noncardinal atan fixture; both independently reviewed P1/P2 findings were
repaired against source. A direct Charge RED28152 reached its original gate.
Root27341 then passed376/0/0/0 (Movement202, Charge86, Ground54, Terrain20, Math14),
Java25, with strict assets unchanged. Its media-independent command selected
TestCdPlayerMovement,TestCdPlayerCharge,TestCdPlayerGroundCollision,
TestCdPlayerTerrain,TestCdPlayerMath through maven_queue.py -Dmse=off.

Combined request13247 ran1050 cases across19 actual classes on ac4e5efa1 plus
these12 Java files: one failure, no errors/skips. Selection included Body226,
Frame132, Pool12, Runtime62, Movement/Charge/terrain/math/ground, Solid88, Float42,
Actor/ObjectPass, actual WorldLoad/EGL, terrain load/boot, restart carry and
ModApi signatures; USA media/EGL/S2ROM and surfaceless flags were supplied.
The only failure was a constructed flipper/enemy missing spriteFlags bit7,
which native Player_ObjCollide .Loop correctly skips (PP Player Object
Collision.asm:28-32). R2 changes only that fixture's explicit $80 byte.
Request25192 reran the entire Runtime62:62/0/0/0, exit0 at21:50:29BST,
156squeue/29.688sMaven. All selected XML counts/skips were inspected; production
is identical between those two runs. Do not describe the original1050 run as
green or add overlapping totals as independent coverage.

Independent Codex composition review confirmed that fixture P2 and its R2
response, with no further P1/P2. Prior nonblocking Movement oracle gaps and
the absence of a same-slot Runtime replay remain recorded externally. The
Runtime standing/jump cases do replay actual actor/shared/pool/SAT state.
No natural supported platform route, live ramp World traversal or native
planes/PLC/timing/pixel/act/game parity is claimed. ACTIVE_3D_RAMP_BACKGROUND,
Spring$0A, background tile streaming and section residency remain real owners.
The fixed-ac4 broad smoke runs independently; it does not cover this later
source revision and is not promoted to a pass before its result is inspected.

### R11A background arithmetic component — 24 October 2026

The pure background owner now binds the existing authenticated cloud-size and
water-section views. Its one immutable State holds all six camera longs, full
crossing/padding/flags,512-byte cloud and deformation RAM,1024-byte HScroll RAM
and VScroll. Init takes explicit complete RAM and incoming d0/d1 bits; step
takes completed foreground words/diff, actual signed player X velocity and
control. No actor, World, callback, renderer, clock or snapshot is retained.
Native cloud ticks, signed arithmetic, flag high bytes, fractional carries,
borrow/halving and FixBugs0 water-block accounting are preserved. A $281 BGY
case intentionally writes247 HScroll longs, not a generic240; unwritten bytes
remain supplied values. Invalid adjacent reads stop at their actual boundary.
Native owners: InitLevelScroll/LevelScroll/ApplyBGHScroll (PP Scroll:135-534)
and BG flag helpers (Level/Scroll Flag Set.asm:102-224), pin1ecc5f3a.

First actual queued component run45832 passed118/0/0/0: Background54, Foreground61,
RetailAssets3, with actual supplied media, Java25 and lean4GiB/4core admission;
exit0 at21:52:40BST,5squeue/86sMaven. Command selected those three named classes
through maven_queue.py --lean -Dmse=off with soniccd.media.root. Compilation
explicitly included the new owner; all three completed XML files/skips were
inspected. Independent Codex review found noP1/P2. Its two P3 coverage limits
remain explicit: the detachment case does not mutate caller table arrays, and
some water-exit suffixes cannot distinguish wrong cursors because their bands
coincide. Source assessment found neither a production escape nor cursor bug.

This commit is the arithmetic component only. Existing World background stays
static until one retained BG State is integrated with justified load/restart
RAM/register ingress, source-order stepping, capture/restore and publication.
BGCameraSectIDs/BGCameraSects runtime joins, native tile-plane streaming and
section PLC residency remain separate work. No live ramp, new plane picture,
render/native timing/parity, act completion or full-game claim follows.

### Spring raw views/art and native background section source joins — 24 October 2026

Spring now has lazy authenticated USA Ani_Spring14-byte and complete shared
MapSpr_Spring1/2/3 200-byte views, with a separate36-tile normal bank at native
$520..$543. Existing startup nine views/two decodes, four banks and selector0/5
admission are unchanged. Source546-byte Nemesis data needs547 real reader bytes;
the next $80 byte belongs to Art_MonitorTimePosts, not padding. Exact consumption,
XOR output/hash, all tiles/gaps/cache/post-close access and regional rejection
ran against supplied media. Native owners: Spring/Main.asm:186-244,492-496 and
PLC_Std (PP Data:70-71), pin1ecc5f3a. Shared independent indices are still raw;
a separate explicit decoder prerequisite owns their later visual family.

BGCameraSectIDs130-byte and BGCameraSects16-byte views bind independently
verified USA offsets3534/35B6 and JP/EU3530/35B2. The retained offscreen row0
and finalrow129 are source data. Selectors0/2/4/6 address four longs, including
the nonnull static/BG1 alias; MOVEA leaves the static branch's CCR unchanged.
These are native camera RAM addresses, not MMD offsets. Initial live pointers
and incremental copied-camera pointers are distinct source cuts. Only metadata
is added; no plane write, rendering fallback, source-table literal or extra
eager decode is installed. Native PP Scroll:892-995,1092-1198 supplies the
lookup contracts; later bounded PlaneB ownership remains separate.

Frozen Spring3 and BGsection2 do not overlap production owners. Root reconciled
one constructed test's enum-wide TABLE count35->37; source blobs stay69. The
new enum values shift later ordinals, but no CD ordinal consumer/published pin
uses them. An initial wrong Spring mirror src/ prefix stopped the import guard
before writes; unnecessary queued62838 was cancelled before admission130. The
correct paths main/ and test/ were then checked against exact hashes and imported
fresh-mtime. Original freezes remain untouched.

Root33885 passed172/0/0/0 across seven actual classes: ObjectVisual13,
ObjectAssets21, ObjectRetail3, RetailAssets9, Background54, Foreground61,
ModApiSignatureSurface11. Java25, actual supplied media, lean4GiB/4core;
exit0 at22:05:38BST,262squeue/95sMaven. Command used those exact named selectors
through maven_queue.py --lean -Dmse=off with soniccd.media.root. All completed
XML identities/skips were inspected. Independent Spring review found noP1/P2;
Root also inspected the full two-entry BG delta/new tests. Known Spring P3
limits: post-close access alone would not catch eager decoding, and malformed
authenticated-owner accessor retry paths have no direct test seam.

No Spring animation/object/collision/runtime admission, diagonal art, real PLC
residency, BG tile streaming, new World route or native-plane/pixel/game parity
is claimed. The full USA then regional task remains active.

### Bounded shared indices and normal Spring visual families — 24 October 2026

The explicit indexed decoder retains a complete mapping include, a signed-word
index root and a disjoint declared body window. Native index aliases, negative
relative words, nonmonotonic ordering and real EVEN padding remain bounded;
old strict decode/static/tail APIs are unchanged. Root added two direct oracle
checks for the reviewed strict-padding and adjacent-window gaps. Spring now
has lazy vertical/horizontal getters over the same200-byte include at roots0/6
and body windows12..46/46..80, each3frames/one real FC script/36tiles. The
separate four-byte speed view reads signed F000/F600 by subtype bit1. Generic
mapping joins retain each actual root, registering $209BA0/$209BA6; diagonal
$209BF0 and frames>=3 remain rejected. Native Spring Main:237-244,492-496 and
Mappings:1-44 at pin1ecc5f3a own these values. No Spring object is dispatched.

Root7648 established a meaningful pointer RED: the new actual-media mapping
case reached the old resolver and failed at unregistered $00209BA0 (1test,
1error, no skip). Root89987 passed88/0/0/0 over Visual24, ObjectAssets21,
ObjectRetail3, Animation28 and Mapping12. Root then added the two decoder
oracles and the family composition. Root44673 completed193/1failure/0errors/
0skips across Visual26, ObjectAssets22, ObjectRetail3, Animation28, Mapping13,
Runtime62, WorldLoad20, EGL8 and API11. The sole failure was a missing expected
SPRING_SPEEDS digest in the retail fixture's exhaustive table map; independent
source bytes F000F600 hash to5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621.
Root's one-line fixture-only correction changed no production. Root76885
reran all3 regional retail invocations:3/0/0/0, exit0 at22:26:08BST,285squeue/
30.165sMaven. XML identities and all skips were inspected; assertions below the
former failure now execute. The original193-case run is not described as green
and these overlapping counts are not summed as independent coverage.

Commands used exact named selectors through the queued Maven wrapper, Java25
and soniccd.media.root=<supplied-media-root>. The193-case run
also enabled the existing opening EGL tests with surfaceless rendering. The
independent decoder review found noP1/P2; the family review confirmed the
fixture P2 and its exact R2 correction, with no productionP1/P2. Lazy-cache
oracle limits remain disclosed. No Spring launch/collision/runtime bank,
diagonal art, natural route, live PLC residency or native pixel/game parity
is claimed. Fixed-ac4 broad smoke is independent and still pending.

### One source-ordered background World state — 24 October 2026

The CD presentation now owns one immutable background arithmetic State per
visit alongside foreground state. Explicit complete deformation/HScroll ingress
and native cleared camera/crossing/cloud/flag domains feed InitLevelScroll,
then exactly one FG+BG load step before ObjSonic_Init. The camera begins at
$70B; incoming register upper halves are zero. Completed ordinary object passes
feed actual signed player oXVel and authoritative high-byte scrollLock to FG
then BG. Sprite culling retains pre-scroll FG; SAT construction receives the
completed FG/BG1/BG3 words. Capture/restore, draw and projection never tick clouds.
Pause/lag/death/restart cuts hold BG. Published HScroll and sprite tables retain
the same completed-loop cut; all requested packed words/fractions survive.

Root corrected its original false HScroll-carry premise. LevelStart calls
ClearScreen (Main:225; Functions General:220-226), whose FixBugs0 DBF clears257
longs:1024HScroll bytes plus one player header long. Restart carries all512
deformation bytes, clears HScroll/clouds and runs one load tick. Fresh zero
deformation remains explicit DEV residue policy. Cleared flags/tile and later
LoadPlayer's ID1 are represented by the collapsed cold builder; neither load
scroll owner reads that ID. Native ClearFlowers covers every cloud byte.
Cold load writes229HScroll longs, leaving27 source-cleared tail longs.

Independent review found no productionP1/P2 and one test-integrationP2: two
existing replay helpers compared new immutable RAM wrappers by identity. RootR3
compares owner, foreground, all background scalar fields and every RAM byte in
both helpers; the published projection assertion now requires224 entries. It
changes no R2 runtime behavior. Original R1/R2 external freezes remain preserved.
BG-Y/opposing-cull SAT oracle and overwritten-deformation transport limits remain
recorded; pure carry independently checks all512 bytes.

Queued Root33510 passed290/0/0/0 on this eight-Java-file composition at base
b550e4ac8: WorldLoad29, RestartCarry21, Foreground63, Background54, TerrainPlayerLoad24,
TerrainPlayerBoot8, PlayerPresentation10, OpeningWorldEgl8, OpeningObjectRuntime62
and ModApiSignatureSurface11. Java25, actual CD media and S2 donor, explicit EGL
opt-in/surfaceless mode; normal7GiB/8core admission. Exit0 at22:39:52BST,65squeue/
128sMaven. All ten completed XML identities and zero skips were inspected.
The full named selection used maven_queue.py --normal -Dmse=off with media,
soniccd.opening.egl=true, openggf.headless.gl=surfaceless-egl and the donor path.
No image artifact was retained by this run. Native PlaneB name-table streaming,
copied-camera/VInt service, PLC residency, natural controller ramp traversal and
pixel/act/game parity remain separate owners. Fixed-ac4 broad smoke is still
pending and has a GLFW-startup failure awaiting matched baseline attribution.

### Finite native Plane B transformation component — 24 October 2026

The new pure PlaneB owner binds the existing authenticated section ID/live
pointer views and retained terrain. Its explicit immutable4096-byte seed
represents64x32 raw tile words at VRAM E000..EFFF. Init draws16rows with native
7F0 lookup wrap; incremental row/column work keeps signed finite lookup, live
BG1 selection and separately supplied copied cameras. Static CCR gating, top
priority, right-mask flag consumption, blank A0 metadata provenance, real
layout crossing, all raw pattern attributes/flips and VDP rings are preserved.
BG2/3 draw helpers are native no-ops. Actual finite reads throw typed boundaries
without a partial Result or seed mutation. There is no implicit clear/service,
World state, renderer upload, clock, pointer-memory emulation or PLC owner.
Native owners: PP Scroll:892-995,1092-1198 and Block Draw:20-68,111-234,376-395,
pin1ecc5f3a. Result metadata describes completed writes; callers never replay it.

Independent review found noP1/P2 and two oracle gaps. Root's test-onlyR2 adds
an initial selector8 pointer-read failure at byte16 and makes the second
chunk's block137 use asymmetric raw tiles A2AB/42AC/12AD/E2AE. The ring-wrap
case asserts these exact four destination words, defeating the old periodic
fixture alias. Production is byte-identical to the reviewed freeze.

Root84398 passed58/0/0/0: Plane43 (including actual USA post-close view binding),
Terrain5, RetailAssets9 and one fresh actual-EGL Ring/pickup/full-course-replay
case. Java25, supplied regional media/S2 donor and surfaceless EGL, normal
7GiB/8core admission; exit0 at22:43:31BST,1squeue/88sMaven. All four completed
XML counts/identities and zero skips were inspected. Exact selectors used the
queued wrapper with the same media/EGL flags and a fresh external output.
The route retained94PNGs, controller log, state CSV and a successful outcome
in its explicit external task directory. It ran at0e4e6d055 plus these two
new component files; the World does not yet call this PlaneB owner. Therefore
those images show source-ordered BG scroll through development planes, not
native PlaneB integration. The opt-in component case proves source binding,
not a native plane-image/PLC oracle. Complete native video, section residency,
full act/game and regional gameplay parity remain unclaimed.

### Normal native Spring execution component — 24 October 2026

CdSpring ports normal up/side/down routines0..18 and the routine2==5 player
mirror. Exactly five dynamic-slot aliases name speed30,parent34,anchor36 and
signed byte offsets38/39. Init returns before Main; reached Solid calls publish
a0 and borrow one real pool cut, then require synchronous a0/a2 publication.
A distinct immutable launch patch names exact native stores, preserving both
fractions and all undeclared actor/slot fields. Move lock is word3E, not timer32.
Down's returning sound call falls into Animate in the same dispatch; up/side
return directly to the wrapper. Real script FC occurs on animation call11; reset
runs on the next dispatch. DrawObject precedes live parent following and unsigned
anchor-window deletion, with post-draw flags reloaded. ID-zero dynamic parents
remain actual live images. Diagonal selector15/routines, MovingSpring execution
and other reserved/unaligned parent reads remain named unsupported boundaries.
No collision, probe, KillPlayer or animator implementation is duplicated.
Native owners: Spring Main:98-409, Object Solid and Object Animate, pin1ecc5f3a.

Root22580 passed231/0/0/0: Spring65, Solid88, Animation28, Mapping13, Pool12,
ObjectAssets22, ObjectRetail3. Java25 and actual supplied media for inherited
opt-in consumers; Spring's65 constructed invocations have no media skip. Lean
4GiB/4core admission,330squeue/94sMaven, exit0 at22:53:33BST. All seven completed
XML identities and skips were inspected. Command used the named selectors through
maven_queue.py --lean -Dmse=off with soniccd.media.root. Three Java files match
the external freeze exactly; tests were authored before production.
Independent bounded source review found noP1/P2 or wrong expected assertions.
One nonblocking oracle gap remains: the dedicated parent/draw case does not
set parent ID0 or assert complete post-draw flags/returned-pool equality; source
handles those correctly. These are component passes, not a World Spring claim.
Root still owns launch/solid phase-CAS, typed sound forwarding,400patternbank
and cleanup, ID0A live dispatch, actual contacts/SAT/replay, PLC residency and
later regional admission. Fixed-ac4 broad smoke remains independent/pending.

### Guarded Spring launch, level-plane cut and normal picture policy — 24 October 2026

Frame now validates current player/globals and every Spring launch store at an
open/idle/closed cut, then adopts checked motion once. Both fractions and all
other actor/slot fields, animation cursor, display cut, timers/history/links and
shared/art/clock state survive. MoveLock word3E remains separate from timer32.
Spring actions have no global-write authority. These are value checks, not
proof a call ran; Runtime still owns live identity/phase/source-prefix binding.
The Runtime's ephemeral two-argument V-int service calls the supplied level-plane
service after powerup art and before Finish/count on ordinary and every paused
selector8; lag skips it. Null leaves prepared input intact; source failure closes
the existing owners without rollback. The old one-argument entry delegates to
a no-op. No extra clock/callback retention or stricter swallowed-reentry promise
is introduced. Native Main:829-847 and Spring Main:257-396 own these cuts.

A separate explicit Spring picture factory validates all11spans/400patterns even
for empty SAT, using actual normal36tiles520..543 and adjacent powerup544..567.
All old factories/helpers/convert are byte-identical. New tests cover all400joins,
both source families/all6frames/rawattributes, gaps and all logical span overlap
pairs. Runtime is still364 and has no Spring dispatch in this commit; its
400-pattern reservation/logicalbanks/cleanup/client work remains next.

Tests were authored before production. Root79344 test-compile exited1 at absent
launch APIs: compileRED only, no JUnit execution. Root73735 completed298/0failures/
1error/0skips: Frame165, Runtime66, picture19, WorldLoad29, EGL8, API11. The only
error was Root's new failure test reading displayedFrame after correct terminal
closure. Existing requireReadable correctly rejects it. Test-onlyR2 removes the
unused old-picture capture, checks rendering rejection and names that behavior;
all source/art/count/closure assertions stay. Production unchanged. Root53533
reran fullRuntime66:66/0/0/0, exit0 at23:11:29BST,451squeue/30.365sMaven. The
original298 run remains red, and overlapping counts are not added as distinct
coverage. All completed XML identities/skips were inspected. Both runs used
queued exact selectors and actual supplied media/Java25; the298 run enabled
actual surfaceless EGL and S2 donor, normal7GiB/8core,147squeue/105sMaven.
The corrected narrow run used lean4GiB/4core.

Independent review confirmed R1's P2 and exactR2 correction, with no remaining
P1/P2 in these six files. Nonblocking oracle limits remain: positive launch
preservation starts after Init with empty display/mostlyzero history/cursor,
and picture raw bases/flips are paired rather than Cartesian. No live Spring
contact, native PlaneB World service, PLC residency, native timing/pixels or
complete-game/region result follows from these port/component checks.

### Native Plane B CPU service, copies and publication — 24 October 2026

The presentation now retains one immutable PlaneB image and a native draw-copy
image alongside its one FG/BG arithmetic state. Explicit ClearScreen seed,
one load scroll and forced liveFG0400 precede initial442block drawing;560
unwritten bytes retain the clear seed. Each genuine selector8 copies all32
camera bytes/eight flag bytes, draws once after art/beforeFinish, and consumes
only copied BG flags. Live flags/fractions stay authoritative; repeated paused
services recopy them, lag skips service, and death/lock can repeat draw without
BG arithmetic. No load/fade V-int or extra clock is fabricated.
Selected PlaneN is published with prior prepared ScrollFrame/SAT N while CPU
advances N+1. All4096bytes/copies restore without source work; restart clears
plane/copies/publication, retaining only existing justified deformation ingress.
Terrain PLC is a composed service with its own stable identity and stale-owner
guard; all interface methods forward. Existing native opening restore preflight
covers the extended publication before unrelated writes. Rendering, native FG
streaming/flag consumption and364pattern reservation remain unchanged.
Native owners: Main:225-249,821-847; PP Scroll:807-820,892-995,1092-1198, pin1ecc5f3a.

Independent review found one P2: WorldLoad still expected raw Frame service
identity. Root's pre-import test-onlyR2 checks nonnull/different/stable wrapper,
keeping all entry/input assertions. No productionP1/P2 was found. Root5314
completed508/6failures/1error/0skips: World32, TerrainLoad27, EGL8, TerrainBoot8,
RestartCarry21, Presentation10, Plane43, BG54, FG63, Frame165, Runtime66, API11.
All seven failures/errors were three old raw-Frame lookup/cast sites in the
Tools Boot class; all other selected checks passed. All12XML were inspected.
Root's first Boot migration66860 then failed compilation because the direct
module getter is package-private; noJUnit ran. Test-onlyR4 keeps Boot's package
and uses a same-CD-package SonicCdTestAccess bridge, with wrapper identity
checks. No production accessor, game service, reflection or owner is added.
All8 Boot cases passed in Root60964,0failures/errors/skips, exit23:26:32BST,
176squeue/34.246sMaven. The original508 run is not called green and overlapping
counts are not summed. Production is unchanged after5314. The508 run used
actual regional media/S2, Java25, surfaceless EGL, normal7GiB/8core,1squeue/
131sMaven, exit23:16:32BST; correctedBoot also used actual media/EGL.

Original fivefile freeze and reviewed reports are preserved. New Plane-callback
finite-read failure lacks a direct World test (existing CPU BG failure differs);
source closure is reviewed, with generic callback failure separately exercised.
The39/442 count assertions use pure component expected Results, not independently
measured native draw calls. The renderer still uses development planes: no native
PlaneB image, native priority/PLC/timing, route/act/game or region-parity claim.
The next client must consume selected immutable words with explicit residency
authority; it may not replay observations or advance source state during draw.

### Live normal Spring composition and rendered picture — 24 October 2026

The physical dynamic walk now dispatches normal Spring ID0A with current
post-Body player/global values and one shared Solid owner. Each genuine Solid
call checks actual snapshot/image ownership and the current pool cut before
pool writes, then applies exactly its declared player/global writes. Launch
has a separate guarded motion-only port after the native object prefix and
before FM_SPRING. Both integer coordinates preserve their fractions; move-lock
WORD3E stays distinct from invincibility WORD32. Completed Solid sounds precede
Spring sound; no call/effect is replayed. The final SAT lens still reads actual
post-object actor state with the earlier Body eligibility/bucket. Cold/restart
admission and each serviced picture validate all eleven actual art spans.
The existing governed picture range now owns400 patterns and removes the same
400 on close; normal Spring art contributes36 after the old364. No extra state
or mutable snapshot partition was added. Native sources are ObjSpring/Index,
Init/Action/Reset (_Objects/Spring/Main.asm:130-409), DrawObjects
(Object Functions.asm:263-420), and PLC_Std (PP Data Act1 Present:54-83), pin1ecc5f3a.

Root60408 ran the nine-class combined selection at9e6d6c973 plus four imported
Java files:477 tests,2failures,1error,0skips, exit23:42:10BST. Runtime76 had a
reset full-image expectation omitting FLAGS bit3 clear and a replay fixture
retaining unsupported playerControl1; WorldLoad32 still expected unowned0A,
but the actual400-Right-input route reached unowned34 at physical58. Frame165,
picture19, EGL8, API11, Spring65, Solid88 and mappings13 passed. Independent
source review identified both Runtime fixture defects and a masked art-wrapper
equality defect. Test-only R2 preserves dirty-control preservation cases,
seeds control0 only for next-Body replay, asserts MAIN_RETURN and a real animation
change, checks both art buffers/scalars by content, and clears the expected
reset flag. The same World recipe now asserts the actual SpinTunnel tag34
frontier, without admitting that body, changing inputs or adding a camera fence.
Root33975 reran complete Runtime76/WorldLoad32:108/0/0/0, exit23:45:16BST,
13squeue/32.987sMaven. Production stayed byte-identical to reviewed frozenRuntime.

A new constructed actual-EGL test clears all dynamic slots after real player
Init, places up/side/down Springs40/41/42, runs real native Init, then explicitly
selects animation routines4/10/16. Sonic remains live. Six actual source-animator
rows check independent native piece/SAT/painter literals, all36 normal tile
nibbles, family-local framebuffer differences against completed-scroll terrain,
full/course state and every-row pixel replay, real atlas textures, and absence
of all400 logical entries after World close before PASS is written. It proves
constructed rendering and retirement, not a natural contact/launch route.
Only57 entries were actually materialized in this fixture, including all36
Spring IDs; checking400 policy/reservation/removal is not rendering all400.
Root34863 ran complete EGL class9/0/0/0, exit23:49:35BST,
33squeue/43.403sMaven. Actual PNGs/input/outcome are retained externally under
native/engine-spring-picture-r1. The previous eight EGL routes remain intact.
Independent client review found no P1/P2. Its nonblocking GPU oracle limit
remains: per-family presence and complete phase-image changes do not separately
prove every family's GPU animation/color change against native RGB. No stronger
per-family or native-pixel claim is made.

Commands were queued exact selectors with -Dmse=off and supplied absolute
media/S2 properties: combined -Dtest=TestCdOpeningObjectRuntime,
TestCdPlayerFrameRuntime,TestCdObjectPresentation,TestSonicCdOpeningWorldLoad,
TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface,TestCdSpring,
TestCdSolidObject,TestCdObjectMappings; corrected Runtime/WorldLoad selection;
then -Dtest=TestSonicCdOpeningWorldEgl with fresh soniccd.spring.output.
All used Java25.0.5.0.2, DISPLAY/WAYLAND_DISPLAY unset, actual surfaceless-EGL
opt-in and no-device audio. Every selected XML identity/skip/property was
inspected; stale XML from other runs was excluded. The original477 run stays
red, overlapping results are not summed, and no broad/full-suite pass follows.

The native PlaneB CPU image/service/rewind is present, but drawing still uses
development planes. Cold/terrain-only rendering needs a shared owner scope
before descriptor mutation; its feasibility audit stopped without importing
unchanged client snapshots. Native section PLC residency, diagonal Springs,
mixed plane/sprite priority, natural Spring launches, remaining USA gameplay
and Japan/Europe parity remain outside these bounded checks. Work stays private
and local with no destination integration, push, PR or complete-game claim.

### Retained background resource scope — 25 October 2026

An internal LevelSpritePresentation.withScrollAndBackground scope now accepts
one optional immutable ScrollFrame and an exact8192-byte physical64x32 RGBA
image. Null privately observes the current completed-load view without creating
or publishing a public frame. Actual attached World/source/module/Level,
renderer/load lifetime, camera/parallax, viewport, graphics/atlas and cache
identities are retained and revalidated. Renderer scopes, native preparation
and arbitrary enclosing graphics captures reject before cache mutation.
A fixed static512px/no-feature/no-full-width-or-persistent-owner contract is
enforced; it is not a native-owner override or a game-name branch.

The existing GraphicsManager owns the true ordinary FIFO barrier: close prior
normal patterns, transfer queued work to existing capture ownership, clear the
old list, and drain on the old view/image with failure-safe tail release.
Its new helpers are package-private; public forwarding stays on the existing
unmarked SpritePresentation. The marked ModApi type gains no public/protected
signature. After old work drains, cache bookkeeping settles before retained
installation/upload; captured callback patterns/commands drain while selected.
Scroll/mode/guard restore on all exits. Derived cache need not roll back after
upload failure. No field, manager, epoch or default-path behavior is added;
old capture/validate/withScroll/cinematic/S3K/generic code and capture executor
remain byte-identical. Source-only independent review found no productionP1/P2.

Tests were frozen before R2 production; Root99167 tests-only test-compile
failed on intentionally absent APIs, compilationRED only/no JUnit. Root85534
then ran96/0failures/3errors/0skips: a new persistent content42 upload stub
also rejected recovery's old-image settlement, and Root omitted the explicit
S3K property needed by two cinematic rows. Review independently confirmed the
sole testP2. Test-onlyRootR3 clears the mocked endpoint fault before image43;
all original failure/restoration/recovery assertions remain. Production is
unchanged. Root89645 reran complete96/0/0/0 with actual verified S3K property,
Java25.0.5.0.2/surfaceless and no ambient display, normal7GiB/8core,
43squeue/33.654sMaven, exit00:11:31BST. All nine selected XMLs/ROM/backend
properties and skips were inspected. The original96 run remains red.

Exact selectors: TestLevelScrollCapture7, TestLevelScrollDrawScope27,
TestCapturedCommandCleanup10, TestLevelSpritePresentation7,
TestPooledRenderCommandDiscard5, TestRenderContext21,
TestLevelSpritePresentationLifecycle6, TestStageplayCinematicCameraRendering2,
TestModApiSignatureSurface11. Command used queued -Dmse=off, explicit
-Dopenggf.headless.gl=surfaceless-egl and -Ds3k.rom.path to the supplied image.
Focused scope/lifecycle/cleanup/API and actual S3K cinematic coverage bounds
this optional internal component: no existing production caller uses it yet,
and existing paths are source-preserved. Actual CD cold/terrain native-plane
cache integration, residency,256px wrap and EGL pixels remain next; the
mocked/CPU scope tests do not prove those. No broad/full-suite pass follows.

Owning render-thread convention and current completed-load admission remain;
no closed-World or same-instance cleanup/reinitialization detector is invented.
Caller must not enter during a load or mutate source/art/palette/cache owners
inside the callback. Native pattern residency and CD terminal/per-visit gates
belong to its client. Oracle limits retained from review include early-clone
mutation coverage, resolver-emitted command coverage, and actual ordinary
LevelManager/GPU cache integration. No repeat review or unrelated graphics
change was used to turn the run green. Work is private/local, not delivered.


## Spin Tunnel tag assets, pure response and Frame field port — 25 October 2026

Private component checkpoint at b6d17e897 plus nine Java changes. The USA
POWERUP_MAPPINGS view binds exactly274 retained bytes at file3EE9C/native23EE9C;
its thirteen raw counted frames deliberately use the existing raw mapper,
including the valid empty frame inside another body. Strict visual decoding,
eager startup views, prior art banks and the400-pattern policy are unchanged.
The pure ID34 owner ports native Tag Init/Main and post-draw despawn metadata;
it retains no pool, snapshot, Frame or callbacks. Native unadjusted TST.B at
both debug word bases observes the HIGH byte. No new gameplay sound is added.
The Frame port validates the complete expected motion and preserves every
field outside the exact eight-field executed-write contract, then applies
once at the existing completed cut. Fractions, state partitions, art, input,
clock and service ownership remain unchanged. Live Runtime dispatch is next.

Tests were authored before production. Independent static review found one
malformed newly appended mapper assertion and one replay comparison using
CdAssetBytes identity through record equality. Root repaired those tests only:
restored the existing FloatBlock frame5 assertion, and compared the real next
service's art/DMA byte contents and scalars. Frozen originals remain external.
Actual-media mapper negative Root62691 failed exactly one expected missing
23EE9C pointer error on the old mapper (387s queue/1m30s Maven). Frame tests-only
Root70543 test-compile failed only on the two proposed absent Frame APIs;
that is compilationRED, not a Jupiter run.

After exact production imports, Root5579 ran queued --lean -Dmse=off test with
-Dtest=TestCdSpinTunnelTag,TestCdPlayerFrameRuntime,TestCdObjectMappings,TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectVisualAssets,TestCdObjectPool,TestModApiSignatureSurface,
supplied soniccd.media.root and explicit surfaceless-egl, DISPLAY/WAYLAND unset.
Observed481 tests/0failures/0errors/0skips; immediate admission/1m39s Maven.
Exact completed XML identities, Java25 and media/backend properties inspected.
Counts: Tag156, Frame235, mapper15, assets23, retail3, visual26, pool12, API11.
The three regional invocations executed; USA-only object admission is unchanged.
This is bounded component/media/API evidence, not World contact, native timing,
section PLC residency, rendered debug pixels, an act/game or regional parity.
Independent source reviews found no remaining productionP1/P2 in these units.


## Retained native Plane B client and cold-mask correction — 25 October 2026

The CD client projects the selected immutable4096-byte native Plane B into
physical64x32 RGBA descriptors and pairs it with the same published scroll/SAT.
Native pattern/palette/H/V/priority bits survive, including opaque pattern0;
encoding all2048 IDs is separate from admission to the authenticated800 resident
base-level patterns. CPU draws require actual Level patterns/palettes but no GPU
entries. GPU draws additionally require the actual current atlas0 entries,
matching64-pixel upload samples and matching manager lookup, rechecked after
old FIFO drain. No fake entries, allocator fallback, source advance or new BG
owner is added. Native PLC/section residency remains explicitly unimplemented.
The real static512px window,256px vertical wrap and origin0 cache are selected
through the shared scope; the old draw-time parallax update is removed.

Independent client review found noP1/P2 and noted a missing discriminating BG
pixel oracle. Root28111 ran the exact four-file R2 client on4fd78f6bc:
130/0failures/3errors/0skips after727s queue/2m13s Maven. WorldLoad46,
OpeningEGL11,TerrainLoad27,ScrollScope27 andAPI11 passed. All three actual
regional TerrainBoot cold-render cases errored at GraphicsManager.drawArenaMask
because standalone scroll captured a null transition sample before first
presentation preparation. CPU headless short-circuit and opening tilesOnly
had concealed that actual GPU/player path. No fixture assertion was weakened.

Root's captureForDrawScope snapshots actual cold mask geometry at capture,
without advancing its transition; an existing sample is retained exactly.
Only standalone captureScroll and nullable-background capture select it.
The ordinary/native-table capture body, cinematic paths and S3K publication
semantics remain byte-identical. Two tests were authored before this repair;
the three existing actual terrain cases supply observed regressionRED.
A bounded independent review found noP1/P2; the new pure cold test uses native
320px width and does not independently certify wider asymmetric wing geometry.

Test-onlyRootR3 also compares real native-BG terrain pixels against the same
foreground/art/camera/scroll with transparent BG descriptors. Pixels must differ,
then an ordinary draw must restore the native image and the source capture.
This rejects an omitted BG draw that deterministic replay alone could miss.
Root60908 passed71/0failures/0errors/0skips,577s queue/1m34s Maven,
exit01:23:09BST. Exact eight XMLs/Java25/media/EGL/verifiedS2+S3K properties and
case identities inspected: TerrainBoot8 (all three regional actual EGL cases),
ScrollCapture9,ScrollScope27,SpritePresentation7,Lifecycle6,S3KCinematic2,
NativePlaneEGL1 (cold/full/course/93-row replay and BG counterfactual),API11.
It used queued --normal -Dmse=off, display variables unset, supplied media,
soniccd.opening.egl=true and explicit surfaceless-egl/ROM properties. The original
130-case invocation remains red. Changed checks were rerun proportionately;
unchanged WorldLoad46/TerrainLoad27/otherOpeningEGL10 retain their earlier result,
not a new post-repair pass. No image/output property or desktop audio was enabled.
These are actual engine pixel/resource/replay checks, not native pixel parity,
priority composition, section PLC, all regional GPU gameplay or act completion.

The two broad-smoke boot errors are now matched on retained9598c663/currentac4e5efa
by TestSurfacelessEglContext then TraceCaptureBootDimensionsTest in one alphabetical
reused JVM. Root67418 and83348 each observed5/0failures/2errors/0skips; both
Trace errors match line41/initGl332 and follow the predecessor's unconditional
backend-property clear. Earlier isolated Trace cases passed both trees. Together
with the matched HCZ53 and CLI182 failures, all four original broad failure
identities reproduce on both retained trees. This does not make broad smoke
green, cover later changes or establish a release/full-suite pass. Its diagnostics
were consumed and deleted; the clean validation tree/merged local branch were
removed after preserving11 unknown ignored regular files outside the repository.


## Live Spin Tunnel tag dispatch and next real Door frontier — 25 October 2026

ID34 now runs from the actual physical object walk with current post-Body motion,
full debug words/raw held, authenticated Powerup pointer, exact Init preflight,
one same-owner96-slot CAS and the eight-field Frame patch. Optional real debug
DrawObject is followed by a mandatory live reload before native despawn. The
real766-byte saved bank is preflighted, loaded bit7 alone clears before full
delete, and failed tails preserve completed prefixes. No Body, sound, clock,
input or publication is replayed. Existing Spring/Solid/Float/V-int/capture and
400-pattern policy remain unchanged except mutual nonreentry guards.

Tests were authored first; independent review confirmed the authored ramp-timer
expectation must be60. Root49039 new dirty-Init test against old production
failed exactly1 expected unowned34 error atphysical40,0skips (197squeue,
28.238sMaven). After exact production import, Root82832 ran fullRuntime89,
World46 and NativePlaneEGL1:136/4failures/1error/0skips after883squeue/1m30sMaven,
exit01:46:50BST. All76 inherited Runtime cases passed. Three new assertions
omitted the prior Body ChargeSound(FM_CHARGESTOP), whose unconditional stopped
charge branch is native Sonic/Main.asm:1033-1036. The composite replay called
closed-cut restore after an ordinary service had opened the next loop. The
remaining World assertion still expected34; its actual exception was unowned
vertical Door35 atphysical32. These are distinct from a new gameplay defect.

RootR3 changes tests only: compare the preserved prior ledger for no-sound
Tag/ramp/warp paths; pin ChargeStop then Spring sound order before an unknown
later dispatch; complete the additional real ordinary loop before composite
restore; migrate the unchanged400-Right World route to the actually observed
Door35 dispatcher. Timer60 remains. No production behavior or input route is
changed. Root10457 fullRuntime89 plus that World method passed90/0/0/0,
immediate admission/30.293sMaven,exit01:50:55BST. Exact XML identities/Java25,
media/backend andzero skips inspected. The first136 run remains red; other45
World cases and theEGL method retain their prior result, not a new rerun claim.
The observed frontier establishes live Tag dispatch, not natural Tag contact,
velocity launch/native parity, section PLC service, or a completed act.

Root82832's exact NativePlane EGL method passed after its own teardown and
saved three320x224 actual ENGINE PNGs at cold,2-row checkpoint and93-row end.
Each image's dimensions/hash/pixel diversity were checked; its full/course
replay, selected PlaneB and visible-background counterfactual passed. A
method-scoped outcome explicitly records the red combined run. Visual review
and display remain queued for daylight, with no contact-event or native pixel
claim from those images. All work and exports remain private/local.


## Vertical Tunnel Door authenticated assets and body — 25 October 2026

Upcoming ID35 now has retained-source asset access and a source-ordered body;
it is not yet dispatched by Runtime or registered in the picture policy.
Table ordinal31 appends MapSpr_FlapDoorV atCE9A/28/native20CE9A; two static
frames preserve all four native pieces/flip attributes and two real pads.
SelectorC is admitted only atsubtype2=0, retaining0/5 and all other rejections.
The standard row1 binds actual ART_TUNNEL_DOOR at36AFE/154, nativebase8328,
VRAM6500,12tiles328..333. Existing startup9views/2art, all previous table
ordinals/banks/defaults and USA-only object admission remain unchanged/lazy.
Actual Java reader153 succeeds and152 fails; no adjacent/invented padding is
added. Independent author/reviewer byte joins bind actual USA ID/function,
Init mapping/selector operands, PLC and XOR pixel hashes. Source authenticity
is separate from decoding/semantic-guard coverage and from native residency.

The body ports Init0 fallthroughMain2, unsigned borrow/range gates, word3A
clear, incoming bit7 FM_A4 before frame1 before unconditional shared Solid,
and Reset4 high-byte3A add8 with byte carry/low3B retention. Reset never calls
Solid, including carry. Actual DrawObject is followed by a live reload and
source saved-bank loaded-bit clear before full deletion. One ephemeral real
Solid cut and synchronous publication thread current actor/global results;
there is no second gameplay store, animation, bank or effect replay. Only two
storage aliases are added to the existing object image. Root's prose-only
citation correction narrows the spawner owner to385-446; gameplay is unchanged.

Tests were frozen first. Static reviews confirmed a new retail helper's missing
throwsIOException, a retained31-table expectation incompatible with append32,
and one horizontal-contact expected X F3.8000 instead of native F2.8000
(width4+9+1, overlap8). Root corrected tests only and preserved frozen originals.
The raw production bank/body have no identified additional P1/P2 within review.
Root37799 tests-only test-compile failed on expected absent Door APIs/type/aliases;
that is compilationRED/no JUnit, not a witnessed behavioral negative.

After exact production imports, Root34255 queued --lean -Dmse=off test with
TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdTunnelDoorVertical,
TestCdSolidObject,TestCdObjectState,TestCdObjectPool,TestCdSpring,TestCdFloatBlock,
TestModApiSignatureSurface and suppliedmedia/explicitEGL/displayunset.
Observed329/0failures/0errors/0skips, immediateadmission/1m36sMaven,
exit01:58:15BST. Exact eight XML identities/Java25/media/backend inspected:
assets29,retail3,Door79,Solid88,pool12,Spring65,Float42,API11. The requested
TestCdObjectState selector matches no standalone class and contributes no cases;
alias correctness is directly checked in Door and these actual consumers.
All regional retail and selected media-gated Solid/Float cases executed.

Coverage is bounded: constructed Door Solid and actual CPU DrawObject/cull,
not final DrawObjects/SAT/GPU or live Door contact; fake-auth mutations reject
at the full-MMD digest before semantic guards; direct decoder/slice tests
supply separate format evidence. CAS stand-ins do not prove Runtime phase/full
pool preflight. Native320 cold geometry and pending audio/PLC remain limited.
Actual400-Right World currently stops at unowned35, not a completed act. Next:
12-span412-picture join, real Runtime callbacks/dispatch, graphics cleanup range,
actual route observation and rendered/replay checks. No full-suite/game claim.


### Vertical Door counted mapping and twelve-span picture — 25 October 2026 GMT

On component base7c5c28ea738515600091fbb60775fb53af808204, Root44297 ran
only TestCdObjectMappings#actualUsaVerticalTunnelDoorJoinsItsExactSourceAndPreservesEveryPriorMappingRoot
with the supplied media, --lean -Dmse=off and explicit surfaceless-EGL.
Observed1/0failures/1error/0skips, immediateadmission/28.454sMaven,
exit01:10:05Z: exact native20CE9A has no registered counted-table pointer.
This behavioral negative used the new mapper test against unchanged production.

Exact frozen production then adds only the existing actual-byte addObject join
at20CE9A/count2 and a separate twelve-span412-pattern policy. The old eleven
Spring spans/policy/convert and every old test/helper remain byte-identical.
The new span is actual ART_TUNNEL_DOOR, native328..333/12tiles, appended after
Spring logically. NOW-serviced immutable player/powerup resident windows retain
the established tails, and all physical/logical spans validate before empty SAT.
No service/state/default-seed/renderer callback or fallback is introduced here.

Root45971 --lean -Dmse=off test selected exact qualified
com.openggf.game.soniccd.level.TestCdObjectMappings and
com.openggf.game.soniccd.resources.TestCdObjectPresentation, suppliedmedia,
explicitEGL and noDISPLAY/WAYLAND. Observed41/0failures/0errors/0skips,
immediateadmission/1m35sMaven, exit01:20:32Z. Both XML identities,17+24,
Java25.0.5.0.2/media/backend and absence of aborted tests were inspected.
Six new media-gated cases and the prior gated cases executed. Literal native
closed/open mapping pieces, real pads, all412 pixels, every span pair/bound,
raw flips, coordinates/palette/priority, actual CPU SAT and ADD.W attribute
carry are checked. Separate source review reports noP1/P2; optional oracle
qualifications do not claim same-world certificates or GPU/native residency.
Frozen author/reviewer reports remain external, unchanged, private/local.

This is CPU mapping/picture evidence only. No live Door Runtime/client412,
World route/contact, GPU rendering, audio playback, nativePLC or regional
parity is admitted by this checkpoint. Runtime tests-only build91483 separately
failed compilation on expected absent12-bank/accessor/typedFM APIs; no JUnit
ran and no behavioral Runtime negative is claimed. Its exact external tests
remain preserved for the following Runtime import. Full-game goal stays active.


### Vertical Door live Runtime, World cleanup and complete GPU oracle — 25 October 2026 GMT

Base547318cd9 plus exact frozen Runtime440e07b0/Test4d1e021a adds real ID35
physical dispatch, a twelfth LogicalBanks field with old arity compatibility,
one cached actual Door bank and one dedicated FM_A4 ledger wrapper. Real Door
prefix/call-cut/result go through the existing shared Solid. Every96-slot value,
all returned images and Frame expected player/globals preflight before S4 writes;
S3 applies once, shared FM follows, and later slots observe the completed image.
Dedicated A4 runs after routine4/word-timer0 prefix, before frame1/Solid. Effects
are not replayed. Snapshot is unchanged; phase/mutual guards cover callbacks.
Independent Runtime source review found noP1/P2. Root24309 actualmedia lean
TestCdOpeningObjectRuntime passed97/0/0/0 (89 retained+8 new), immediateadmission,
1m28sMaven, exit01:24:44Z; Java25/media/backend and all8new XML names inspected.

Presentation changes only comment/cleanup count400->412; all prior client/native
PlaneB scope logic stays unchanged. New EGL component proof explicitly clears
all96 dynamic slots and seeds ID35 physical40 at camera+(192,72). It stages the
same live Sonic24px left before Init and each row2..35, keeping the declared
actor/header/timer/history/animation owners; it repeats that ingress on full and
course replay. This is constructed component evidence, not a normal-input Door
contact. Source Init is silent/frame0, Reset carries after32 calls without Main,
next Main emits A4/frame1, following V-int selects the open prior SAT. Actual
DrawObjects/SAT, decoded12 art tiles, real textures, complete captures/replay,
terrain controls and all412 retirement checks use the engine owners.

Root87329 actualEGL tests-only with new Runtime/picture but old400cleanup gave
1/1failure/0errors/0skips at surviving real logicalID131472 (OBJECTSbase+400)
after close. Queue7s/Maven27.188s, exit01:29:05Z; exact media/Java25/optin/S2ROM
properties inspected. After exact412client import, Root77883 normal queued
-Dmse=off test selected TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,
TestCdPlayerFrameRuntime,TestModApiSignatureSurface with actualmedia/EGLoptin,
explicit surfaceless-EGL, suppliedS2ROM and noDISPLAY/WAYLAND. Observed304/
1failure/0errors/0skips, immediateadmission/1m45sMaven, exit01:31:54Z. EGL12,
Frame235,API11 and45of46 World tests passed. Sole failure: same400Right route's
stale expected35 now observes Unowned CD object ID19 at physical59. No input or
runtime fence changed. Root changed only expectedID/methodname;51832 one exact
World frontier method passed1/0/0/0, immediate/27.867sMaven, exit01:33:25Z.
All class identities, failures, properties and zero aborts/skips were inspected.

Independent World review found P2: one changed pixel across a pose could certify
partially missing geometry despite correct CPU versions/uploads. Root preserved
the frozen original and wrote a negative first: erase one complete visible Door
tile from framebuffer copy while preserving visible RGB from the other five.
Root68639 observed1/1failure/0errors/0skips because the weak oracle accepted the
partial image (25.343sMaven, exit01:38:23Z). Root's test-only revision now checks
every native nibble at literal flipped/positioned screen pixels against the
current engine palette/display-colour policy, or exact current terrain for zero,
including alpha. It pins full rows, layer, subject, ghost/opacity, priority shader
and occlusion metadata. No sprite converter/renderer constructs this geometry
oracle, and the omitted-tile negative remains for both poses. Production did not
change for this repair. This tests engine display colours, not native palette
or mixed VDP plane/sprite priority parity.

Root51554 reran only the affected constructedDoor EGL method:1/0/0/0, immediate
normaladmission/27.506sMaven, exit01:40:03Z, actualmedia/optin/EGL/S2ROM/Java25
inspected. Every real Door pixel and both omitted-tile negatives passed, followed
by full/course replay,412 retirement and cleanup. R2 closed/open ENGINE captures
and terrain controls under external native/engine-tunnel-door-r2 are byte-identical
to earlier R1 images; R1 outcome records the superseded weak-oracle limit. R2 PASS
is written only after all assertions and session/graphics/boot cleanup. Images
are320x224 and remain queued/unviewed/unpublished during00-07 Europe/London quiet.
No broad unchanged selection was repeated after these assertion-only repairs.
Earlier all12 EGL/235Frame/11API/45otherWorld outcomes retain their exact sources;
304 is a red batch, not a claimed304-green rerun. Whole-game/region/nativePLC/
audio/nativepalette/priority parity remain open. The actual next owner is19.


### Monitor data and detached USA FM source — 25 October 2026 GMT

On576656be9, native Monitor views append atA9C6/136 andAA4E/450, preserving all
32 old table ordinals, nine startup views/two art decodes and priorbanks/defaults.
Strict existing decoding admits20frames/10scripts/82tiles. The actual8052 XOR
art source is1540bytes, but Java's finite8bitpeek needs1541, including the real
next Explosion header byte00 at3513E. The standardPLC1/row9/B500/base05A8 join,
all decoded2624 bytes and eight odd five-byte static item pointers are separately
authenticated from USA media. New monitorTime()/MonitorTimeArtBank reads stay
lazy and detached afterdisc close. This unit adds no mapper/static/picture/body/
reward/residency admission; native item85A8 attributes remain execution-owned.

Root52866 tests-only test-compile RED showed absent Monitor APIs/tables; no
JUnit ran. Exact frozen production then passed Root13888 --lean -Dmse=off
-Dtest=TestCdR11aObjectAssets,TestCdR11aObjectRetailAssets,TestCdObjectVisualAssets,TestCdObjectMappings,TestCdFmSoundAssets,TestModApiSignatureSurface
-Dsoniccd.media.root=<supplied-media-root>
-Dopenggf.headless.gl=surfaceless-egl test, noDISPLAY/WAYLAND, Lua5.4.
Observed98/0failures/0errors/0skips, immediateadmission/1m41sMaven,
exit02:03:45Z. Exact6XML identities/Java25/media inspected:34/3/26/17/7/11.
Retail3 reachedactual new source/byte/decode/lazy/after-close assertions; no
aborted media gate was counted as a pass. Frozen originals remain external.

Root's new CdFmSoundAssets uses only the existing actual CdProgramAssets/MMD
pipeline. It requires observed knownUSA whole-content plusheaderUSA and exact
MDINIT file/hash/header/copy operands/bank. Native file1DA/length1AD6 detaches
6870 origin-zero Z80 bytes; no934 unresolved relocation bytes or1C00 RAM is
padded. LE pointer rows90..DF,79 declared priorities/80 native reads and AB's
backward AA voice addressE6A stay distinct. DF's native priority read is real
byte4 at7DA. Raw header addresses do not admit a universal80-ID parser (D5 is
nonordinary). Assets remain uninstalled: no stock preparation/source stream,
clock/driver/synthesis/CDDA/PCM/rewind change or sound-playback claim.

Root FM tests136lines/hash2e2647ce froze01:52:41.643680Z before production;
56213 test-compile RED missingnewclass/noJUnit;125line production9574f076 first
write01:54:58.064377Z. Root3191 leanactualmedia selected newFM assets/MMD image/
ProgramAssets/RetailReleases/ModAPI:39/0/0/0 (7/5/4/12/11), immediateadmission/
1m33sMaven, exit01:56:32Z. ExactXML/Java25/media/backend/zeroabortsskip inspected;
newregional cases admitUSA and explicitlyrejectJP/EU withoutclosingmediaowner.
Unit bounds test all coordinate domains, RAM/overflow/ID errors, immutability,
closed-owner readability, fullbank/index/priority/header/voice byte identities.
Whole-file hash gates are authentication precedence, not semanticmutationproof.

One independent bounded review of both units found noP1/P2; it separately read
actualUSA/JP/EU data tracks and corroborated all relevant source/operand/hash
joins and Monitor's1541 reader extent. Neither that review nor the data audits
ranJava/nativecode. Parentexecuted outcomes above are bounded component evidence.
Existing silent audio and actualMonitor19 frontier remain; no route/game/parity
advance follows from these assets. Rawmovement and pureMONITOR touch candidates
are separate next units. All5 source/test files here remain exactfrozen copies.

### Shared raw-object movement and Monitor touch response — 25 October 2026 GMT

Private/local continuation from `ecfa2c2a62448c8d2e731288c28bdbf300b35e4c`.
The raw-object ports delegate to the same instruction integrator used by MODE,
Hurt and Dead. They consume primitive raw fields and an optional actual standing
slot projection, preserve the native read cuts and typed unsupported addresses,
and retain no pool or snapshot. This extracts existing arithmetic without a
second movement implementation. All 120 inherited Movement methods are retained.

The pure PP contact response now admits MONITOR through its existing selected
boundary and same call-cut snapshot. It models signed upward velocity, the
unsigned wrapped Y comparison, zero-only falling-byte write and animation-2
break bounce. It publishes final detached images without sounds, allocation,
score or reward. Body adopts its final actor once before SpecialChunks. An absent
response owner and SPECIAL still stop at their existing typed boundaries. The
existing Frame/Runtime transaction and capture partitions are unchanged.

Tests were authored externally before production. Root12876 tests-only compile
failed at the absent raw APIs; Root4122 tests-only compile failed at the absent
Monitor exits. Neither invoked JUnit. Independent reviews found no production
P1/P2. The Monitor review did find one wrong Body expectation: stationary ground
MODE selects idle animation 5 before touch, rather than retaining incoming 0.
Root preserved the frozen R1 and changed only that expected literal in R2
(`5abc461aa9e48312b2e72eaa1aab7d0db43147891f9176b7aa457a431409ac80`).
Production was not changed to satisfy it. The optional Monitor-specific flipper
no-write overlay assertion remains weaker than the existing Enemy/Item checks;
the raw negative support-packet cases do not cover every non-flipper ID.

Observed Root77298 from this six-file working candidate, immediate admission,
Maven 1m45s, terminal `2026-10-25T02:30:04Z`:

```text
python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off   -Dtest=TestCdPlayerMovement,TestCdPlayerTouchResponse,TestCdPlayerBody,TestCdPlayerFrameRuntime,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface   -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true   -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```

All eight completed XML reports were inspected: Movement265, Touch130, Body237,
Frame235, Runtime97, WorldLoad46, EGL12, API11: **1033 tests, zero failures,
errors or skips**. Java25, actual media, EGL opt-in/backend and the verified S2
SHA-1 were checked. The retail EnemyPoints test and all twelve EGL cases ran.
The imported sources received fresh timestamps before compilation. This is
bounded CD-consumer validation, not a broad-smoke, native-parity, Monitor-body,
item-reward or completed-game certificate. The unchanged 400-Right observation
still reaches unowned Monitor ID19 at physical slot59.

External frozen handoffs/reviews remain under the private task directory:
`object-motion-port-codex.md`, `object-motion-port-review.md`,
`monitor-touch-response-codex.md`, `monitor-touch-response-review.md`.
The review's P2 is reconciled above; original mirrors remain unchanged.

### Monitor picture bindings and closed shoes reward port — 25 October 2026 GMT

Private/local continuation from `37ec5b96faa195cbad3387fc712b39da95866e7a`.
The mapping factory joins all twenty authenticated Monitor/time-post frames and
eight odd header-free item roots. A separate thirteen-span policy adds the82
native Monitor patterns to the prior412, for494 total; previous policies and
logical ranges are unchanged. This is declared development preload, not native
PLC service. The Runtime and World client still select the previous412 policy;
Monitor bodies, item dispatch and natural reward remain unadmitted here.

A shared pure shoes primitive returns the native five stores and ordered typed
SubCPU commands. The Frame validate/apply port compares the actual five input
values and adopts flag, timer and three physics words once at the existing
closed cut, preserving all other state. It changes no capture schema and plays
no audio. Region selects only the1320-USA/1200-non-USA source rule.

Two source-supported corrections preserve the original external freezes. The
R1 author counted only labelled command declarations and omitted the anonymous
`rs.b 1` at System.inc:183. Root R2 changes SHOESMUS6B→6C and FADEPCM81→82 in
production and numeric assertions, with the counter lesson recorded in the
pitfall catalogue. Root R2 tests froze before its corrected production copy.
Independent source review confirms the corrected values and exact five stores.

Root89550 tests-only `test-compile` failed at missing reward/picture APIs; no
JUnit ran. Root90505 selected exactly:

```text
TestCdPlayerSpeedShoesReward,TestCdPlayerFrameRuntime,TestCdObjectMappings,TestCdObjectPresentation,TestCdOpeningObjectRuntime,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestModApiSignatureSurface
```

It ran through the main-workspace Maven queue with `--normal -Dmse=off`, actual
`soniccd.media.root`, `soniccd.opening.egl=true`, surfaceless EGL and verified S2
REV01. Immediate admission, Maven1m52s, terminal `2026-10-25T02:41:47Z`:
**512 tests, five assertion failures, zero errors/skips**. All42 pure reward,
21 mapping,30 presentation,97 Runtime,46 WorldLoad,12 EGL and11 API tests passed;
Frame had248 passes and five new-test failures. All eight XML reports, actual
media, Java25 and EGL properties were inspected. All seven new media-gated
picture methods executed. The original235 Frame cases remain unchanged.

All five failures omitted standing MODE's independent FM_CHARGESTOP call
(Sonic/Main.asm:1029-1037), before Display services the shoes timer. Root R3
changes only three exact sound-list assertions inside the same two new methods:
ChargeStop alone, or ChargeStop then the optional expiry level music. This also
corrects the later nonexpiry assertion that the first failure had hidden.
No production or fixture change was needed. Root34057 reran precisely those two
methods/five invocations with `--lean -Dmse=off`: **5/0/0/0**, immediate admission,
Maven25.962s, terminal `2026-10-25T02:45:16Z`; exact XML identities/Java25 checked.
Restore/replay, actual next Display1320→1319, DMA byte content and expiry-order
checks all completed. The512 red batch and repaired-five result are separate
observations, not a fabricated green512 run.

Independent picture and shoes reviews have no remaining P1/P2 objection to
these final sources. Optional gaps remain: explicit static rejection vectors
for every post/HUD first piece; an inherited wrapper-identity inequality; and
requiring both expiry-effect indices to be present before comparing order.
Original R1, Root R2 and R3 mirrors remain preserved externally. Full hashes and
source citations are in the four matching private handoff/review reports.

A separate read-only route probe, Root44900, observed the unchanged Right input
route stop on attempted call324: ID19, whole59, X2128/Y728, subtype4, layer0,
savedFlagsID45, routine0. Its one actual-media case passed with no skips; terminal
`02:35:56Z`, Maven25.439s. The first probe failed to compile because my diagnostic
used a nonexistent unsignedWord accessor; I corrected it to centreX/centreY.
The temporary diagnostic was then removed byte-for-byte. This identifies a live
shoes Monitor, not a natural contact, break or reward. It adds no runtime route
predicate. Broad-smoke/full-game/native-playback/regional-parity claims remain
excluded; the prior broad baseline-attributed failures remain unchanged history.

### Source-ordered Monitor and item body components — 25 October 2026 GMT

Private/local continuation from `eddee5d17d0464a2eb058b7c3b1d9d7823d6ef41`.
New ID19 Monitor and ID1A item owners use the existing saved bank, shared raw
movement, actual plain Solid and object floor/animation/DrawObject functions.
Exactly three storage aliases expose raw control2C, stand link3D and deletion
word1E. No Frame, Runtime, global store, renderer or existing owner changes.
Native prefixes stay live when a later callback fails; effect lists describe
completed work and are never replay instructions. The Runtime binding remains
future work, so normal gameplay still stops at the ID19 dispatch.

The Monitor handles routines0/2/4/6/8 and monitor subtypes0..7. It preserves
sprite visibility/read order, timeStop's animation-only gate, actual saved-byte
addressing, live item-then-explosion allocation, dirty slot bytes and the direct
Break draw. Item preserves byte-wrapped/signed-relative static-pointer math,
old-negative-velocity motion, reward on clean call33 after32 moves, deletion
word29 and the returning DrawObject after deletion. Only source kind4 reaches
a mandatory synchronous award callback; other rewards and posts/HUD remain
typed boundaries. Real Frame rewards, sound ordering, physical child scheduling
and authenticated World pictures are not certified by these component tests.

The original external tests preceded production and remain frozen. Root R2
repairs only the author's five known test groups: invalid compound var syntax;
two expected final DrawObject bit7 mutations; explicit offscreen input for the
time-attack despawn test; and next-call SAT visibility input in the layer test.
The harness models DrawObject, not the later DrawObjects publication. Root68205
tests-only compile failed at absent body/alias APIs, with no JUnit execution.
The exact original three production files were then imported freshly.

Observed Root81938, queued `--lean -Dmse=off` exact selection
`TestCdMonitor,TestCdMonitorItem,TestCdObjectPool`, no optional media dependency:
**146 tests, one failure, zero errors/skips**, immediate admission, Maven1m20s,
terminal `2026-10-25T03:00:47Z`. All59 Item and12 pool cases passed; Monitor
74/75 passed. All three XML reports and Java25 were inspected. The independent
review found the same remaining test defect: a fixture with only push bit5
changed the player but no object byte, so skipping the unchanged object image
could not trigger the expected missing-publication rejection.

Root R3 changes only that method's first subcase to matching object/player
standing bits and link40. Native GetOffObject now clears the object's bit3
(Object Solid.asm:15-55); StopObjPush still only clears the player's push link.
This creates the intended unequal object image without altering production.
Root54605 reran that exact method: **1/0/0/0**, immediate admission, Maven24.589s,
terminal `2026-10-25T03:02:30Z`; exact XML/Java25 checked. Both the object and
other-slot rejection subcases completed. Keep the146 red run and repaired-one
result separate. No broad repeated run was needed for this test-only change.

The source review found no production P1/P2; its remaining test P2 is reconciled
above. Original R1, Root R2 and R3 files remain externally preserved. Final Root
Monitor test SHA-256 is `32fe83d7bf5a73a118f219109adb769f146726402a6ec589700effe44b84a45a`.
Optional gaps remain: negative relative mapping word, final saved-bank byte
vectors, and the real adapter/physical pass/World render obligations. Exact APIs,
all134 declared component shapes, full native citations and immutable hashes are
in private `monitor-bodies-codex.md` and `monitor-bodies-review.md`.
No native/region/whole-game, natural contact/break/reward or audio claim is made.


### Live Monitor reward, 494-pattern World and detached FM retention — 25 October 2026 GMT

Private/local continuation from `6f8f47e1333643e36b6abeed05bf432031e651a3`.
The real physical ID19/1A walk now composes the existing Monitor/item bodies,
shared Solid, raw gravity/floor, saved-object bank and single Frame shoes owner.
All96 call-cut slot images and the expected actor/globals are checked before
publication; pool images commit before the one Frame patch and ordered sound
requests. Falling writes the primary angle directly once; the legacy external
angle adapter is not also called. The MMZ global-layer read remains a typed
boundary before body entry. Deleted-item DrawObject can report live ID0; the
shared SAT builder owns skipping it. Completed metadata is never replayed.

One actual Monitor/time art bank is retained in the common factory. Both cold
admission and now-serviced conversion select the existing thirteen-span policy;
the World reserves and removes all494 patterns. Runtime Snapshot ownership is
unchanged. Other item rewards, posts/HUD/warp, reserved powerups and unsupported
regions remain explicit boundaries. Source-only Runtime/World review found no
production P1/P2; the original World preflight P2 was confined to a test fixture.

Tests-only Root76438 failed to compile at absent bank/sound APIs, no JUnit ran.
After exact production import, Root54190 ran all121 Runtime cases (97 old+24 new):
**121/0/0/0**, immediate admission, Maven1m23s, terminal03:18:28Z; actual media,
Java25/backend and exact XML were checked. The original Runtime/test mirrors are
unchanged (`827c28f2...`, `87201b63...`).

The new constructed actual-EGL component proof keeps fixed source-backed assets
and the actual GameLoop. It clears all96 dynamic slots once, seeds one subtype4
Monitor40, and stages only Sonic's contact and move-away states at closed cuts.
Those operations are disclosed in the test, capture construction metadata and
outcome. The actual Body touch precedes same-pass Break; earlier item32 and
explosion33 wait.32 old-velocity rise moves precede real award call33: flag1,
USA timer1320, physicsC00/18/80 and command6C. Next actual Display gives1319.
The alternate explosion creates no Points/Flower; item Delete30 and the genuinely
earned expiry both run. Pre-break and nonzero-award full/course restores compare
complete state and framebuffer on every replay row. Expiry is forward-only.

Independent counted/static SAT, decoded tile nibble/current-palette opaque ARGB,
transparent-terrain and omitted-visible-tile negative checks cover the rendered
geometry. At teardown all494 actual tile versions are installed as witnesses,
including unused suffix tiles, then all must be absent after module retirement.
This is retirement coverage, not evidence that every tile drew. PASS is written
only after all assertions and session/graphics/boot closure.

Root47242 and diagnostic75238 first failed the original coarse solidity-mask
preflight before any contact. An actual top-solid block at x168/y1907 had floor
distance11, so its selector flag did not imply occupied space. Root R3 narrows
the precondition to the actual glyph/motion envelope and actual shape distances;
coordinates, physics and downstream pixel/reward assertions are unchanged.
Root91135 then exposed a missing expected ChargeSound: the native short-floor
CheckCharge branch requests ChargeStop even at charge0. Root R4 changes only
that new sound-helper expectation. Production was not adjusted to a fixture.

With the old412 client, Root9419 reached the intended cleanup RED after the
contact/reward/pixel/replay/expiry checks: pattern131484, the first suffix tile,
remained in the atlas. With the exact494 client Root80562 passed **1/0/0/0**,
immediate admission, Maven1m24s, terminal03:43:49Z. Exact method/Java25/media/EGL
properties and zero skips were inspected. Five labelled ENGINE PNGs, input,
state CSV, construction and final outcome stay in the private task capture
root; quiet-hours visuals were queued, not emitted. This is constructed engine
coverage, not a natural contact, native palette/priority/PLC/audio/timing,
regional or whole-game parity claim.

The unchanged400-Right route now finishes. Root5317 failed only its stale
unowned19 expectation. The replacement asserts actual call324 Monitor admission
at whole59/subtype4/saved45/X850/Y2D8 and replays every row324..400 through full
and course restores. A bounded ordinary-input probe then observed the next
unowned dispatch on attempted call518: **ID0D, physical37**, player wordY65482.
The first probe's error handler accidentally called the module's live-owner
getter after its fail-closed exception; retaining the already-owned Frame
reference exposed the original exception in Root46997, **1/0/0/0**, terminal
03:56:06Z. The temporary diagnostic was removed. A permanent regression checks
ID0D/routine0/subtype1/X96F/Y318 and both closed owners. No natural Monitor hit
or shoes reward was observed on this Right-only route.

Stock preparation separately retains `Optional<CdFmSoundAssets>` from the SAME
still-open authenticated programs used for the stage, before try-with-resources
closes them. USA requires presence; Japan/Europe explicitly require absence.
Release, stage and FM full-disc identities must agree. The existing source
identity and three exact named streams remain unchanged. The bank's readable
6870-byte slice shares its privately copied7514-byte backing; no file handle,
source fallback, duplicate payload, driver installation or playback is added.
Tests-only Root78691 failed to compile at pending getter/three-argument API,
with no JUnit. Exact production mirrors were then imported; independent review
found no P1/P2. Negative-constructor guard reachability remains explicitly
limited by private authentic owners, not a reason to invent forged fixtures.

Combined affected validation on HEAD6f plus the eight integrated Java files:

```text
env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4 python3 <main-workspace>/tools/testing/maven_queue.py --normal -Dmse=off '-Dtest=TestSonicCdStockPreparation,TestCdFmSoundAssets,TestCdProgramAssets,TestCdMmdImage,TestCdRetailReleases,TestGameDataSourceSessionOwnership,TestGameDataSourceSharedFetches,TestModApiSignatureSurface,TestSonicCdOpeningWorldLoad,TestSonicCdOpeningWorldEgl,TestCdPlayerFrameRuntime,TestHeadlessAudioOwnership,TestHeadlessGameBootAudioIdentity,TestSonicCdTerrainPlayerLoad,TestSonicCdTerrainPlayerBoot' -Dsoniccd.media.root=<supplied-media-root> -Dsoniccd.opening.egl=true -Dopenggf.headless.gl=surfaceless-egl -Dsonic2.rom.path=<verified-S2-REV01> test -B
```

Root20780: **424 tests, zero failures/errors/skips**,15 current XML identities,
immediate admission, Maven1m17s, terminal `2026-10-25T04:00:10Z`. Counts: stock17,
FM7, programs4, MMD5, releases12, source ownership5/shared fetches10, API11,
World47/EGL13, Frame253, audio ownership2/native boot3, terrain load27/boot8.
Every XML had Java25 and actual media/EGL settings; all regional retention,
all13 actual-EGL methods, both natural-route regressions and no-device cases
executed. This proportionate affected-consumer batch covers the concrete CD
source/World changes; it is not smoke/full-suite/native audio or full-game
certification. Earlier broad baseline-attributed failures remain history.
