# Native assertion, capsule allocation and sprite submission repairs — 2026-10-22

T3 isolated worktree `t3-e5feec78`, branch
`bugfix/ai-gap-coverage_assertions-20261022`, integration base
`94c40570a15a11b70caf7456e9b0d0a8eec8a9b4`. This bounded lane owns no main
integration or push. Combined smoke is the parent coordinator's obligation.

## Decisions and independent evidence

The original HTZ test's missing arrival assertion was reproduced by adding it:
2,000 Right-only frames ended at X=172, not the named X=6635. The replacement is
an explicitly positioned short natural-earthquake crossing, with alive, active
event, arrival and continued-progress assertions. It claims no cold traversal.
CNZ's same positioned input already entered rolling on frame 28 and passed the
exit on frame 67; initial clear roll/pinball state and the actual forced outcome
are now required. See the [HTZ1](levels/s2-htz1-sonic.md) and
[CNZ1](levels/s2-cnz1-sonic.md) partial matrices.

The old DEZ cold comparison matched zero frames yet passed. First compared native
row 18670 was x=$01E8, y=$072C, camera=$00C0/$068C, rings=123; direct engine
entry was x=$0140, y=$03AC, camera=$00A0/$034C, rings=0. These are different
inlets. The new independent cold-entry test checks the direct ROM spawn and
controller-driven 64px progress, without writes from comparison data. ROM
`Sonic_Start_Locations` DEZ2 bytes at $1E3C74 are `01 40 03 AC`. No positive
native parity frontier is invented. The separately labelled historical seeded
diagnostic is not cold/bootstrap proof and has not been promoted into one.
Modern authored DEZ campaign evidence is retained in the
[existing act matrix](levels/s3k-dez-act2.md).

Capsule `Obj3E` uses the first-free dynamic allocator for each component, not
after-current allocation. Retail S2 REV01 ROM bytes at $3F220 are `61 00 03 2C`,
calling trampoline $3F54E (`4E F9 00 01 7F DA`) and then `AllocateObject` at
$17FDA (`43 F8 B4 00` starts at dynamic RAM $B400). The scan covers 112 slots.
Pinned S2 disassembly `34fc7f1` agrees:
`AllocateObject (s2.asm:33681-33695)`,
`loc_3F212 / loc_3F220 / loc_3F228 (s2.asm:84959-84990)`.
`Obj3E_ObjLoadData` at ROM $3F1FE gives body/button/lock/broken routines 2/4/6/8.

The former source comment declined the allocator change because the two cited
movie capsules had all lower slots occupied. That is a useful control, but
cannot establish equivalence when a lower hole exists. The inherited draft
regression also placed the body at the first dynamic slot and passed a null
player, returning before component initialization. It was replaced by the
independent real-manager cases rather than retained as allocation evidence.

The existing `spawnFreeChild` API supplies the correct allocator and injected
services; no shared manager change is necessary. Allocation alone is insufficient:
the actual lock and broken-child dispatch must own their behavior. Native
`RunObject / RunNextObject (s2.asm:29833-29846)` walks upward. A lower lock has
already run when the body arms it and cannot move until the next pass; a higher
lock moves immediately. A lower broken piece keeps $B4 on its arming pass;
a higher piece decrements to $B3. The eight initial real-manager cases reproduced
both lower-child failures before the dispatch repair (X=392 instead of384;
timer179 instead of180). Relevant routines are
`loc_3F2B4 (s2.asm:85014-85033)`,
`loc_3F38E (s2.asm:85081-85088)` and
`loc_3F3A8 / loc_3F3F4 (s2.asm:85096-85122)`.
The body initializer now returns before its later routine, as native does.
No FixBugs=1 branch, trace fitting or shared allocator change is introduced.

## Capsule affected-act obligations

The shared unit fixture executes the real `ObjectManager` and S2 slot layout,
with body slot40 and deliberate free holes. It does not prove production
placement, player contact, art, boss exit or completion in any act below.

| Receiving act / native route | Shared executable contract | Remaining production obligation |
| --- | --- | --- |
| EHZ2 / Sonic, Tails, pair | `TestEggPrisonNativeAllocation` lower holes, mixed, full RAM, later-slot control and arming order | Actual placed capsule/button/animals/results with pressure and rewind |
| CPZ2 / Sonic, Tails, pair | Same shared allocation contract | Same; water/arena/result handoff context unrun |
| ARZ2 / Sonic, Tails, pair | Same shared allocation contract; one native level-select replay matches all7,809 rows and the capsule-animal landing oracle passes | Pressure/recreation at the actual capsule, other rosters/configurations and outgoing-load composition remain open |
| CNZ2 / Sonic, Tails, pair | Same shared allocation contract | Same; arena mutation/results context unrun |
| HTZ2 / Sonic, Tails, pair | Same shared allocation contract | Same; earthquake/arena/results context unrun |
| MCZ2 / Sonic, Tails, pair | Same shared allocation contract | Same; arena/results context unrun |
| OOZ2 / Sonic, Tails, pair | Same shared allocation contract | Same; oil/arena/results context unrun |
| MTZ3 / Sonic, Tails, pair | Same shared allocation contract | Same; native Act3 arena/results context unrun |

Every row retains the standard's five viewport presets, supported donor/main
character and team breadth, before/active/after interaction and boss rewind,
checkpoint/death/reload, actual outgoing-load isolation and native picture/audio
obligations. No configuration has been declared unsupported to excuse a gap.
The slot algorithm itself has no viewport or movement arithmetic, but its real
placement/contact and same-pass execution consumers still require that breadth.

## Validation

Supplied S2 and S3K absolute ROMs were hashed, matching the required CRC32/SHA-1.
Java25/Lua5.4/PowerShell preflight passed:
`LUA_BIN=/usr/bin/lua5.4 python3 tools/testing/run_categories.py --base 94c40570a15a11b70caf7456e9b0d0a8eec8a9b4 --preflight`.
No broad smoke or full-suite run belongs to this lane.

Focused commands use the existing absolute ROM paths through shell variables
`S2_ROM` and `S3K_ROM`; no ROM copies or links are made. Each command is prefixed
with `env -u DISPLAY -u WAYLAND_DISPLAY LUA_BIN=/usr/bin/lua5.4`.

- Initial assertion diagnosis: `python3 tools/testing/maven_queue.py -Dmse=off
  '-Dtest=TestS2Htz1Headless#testWalkThroughEarthquakeZoneNoStall,TestS2Cnz1Headless#testForcedSpinTunnelEntry,TestS3kDezColdRoutes#theColdActTwoRouteReachesItsRecordedFrontier'
  "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM"
  -Dopenggf.headless.gl=surfaceless-egl test`: three cases, the strengthened HTZ
  arrival assertion fails, CNZ passes and the legacy DEZ assertion falsely
  passes at matching-prefix zero. No skips.
- Native lower-child diagnosis: `python3 tools/testing/maven_queue.py --lean
  -Dmse=off -Dtest=TestEggPrisonNativeAllocation test`: eight cases, six pass;
  the lower lock and broken timer assertions fail as recorded above. No skips.
- Repaired behavior: `python3 tools/testing/maven_queue.py -Dmse=off
  '-Dtest=TestEggPrisonNativeAllocation,TestEggPrisonAnimalRelease,TestS2EggPrisonButtonGraphRewind,TestS2Htz1Headless#testPositionedWalkThroughEarthquakeZoneNoStall,TestS2Cnz1Headless#testForcedSpinTunnelEntry,TestS3kDezColdRoutes#theColdActTwoEntryUsesItsOwnSpawnAndAcceptsControllerInput'
  "-Dsonic2.rom.path=$S2_ROM" "-Ds3k.rom.path=$S3K_ROM"
  -Dopenggf.headless.gl=surfaceless-egl test`: 17 pass, zero failures/errors/skips.
- Additional graph-pressure replay: queued lean
  `'-Dtest=TestEggPrisonNativeAllocation#lowerComponentGraphRecreatesAndReplaysTheArmingBoundaryTwice+laterComponentGraphRecreatesAndReplaysTheArmingBoundaryTwice'`:
  both pass, zero failures/errors/skips. Each removes the entire live graph,
  recreates the original slots and replays the arming boundary twice. The
  initial test-only attempt used bare external reservations with no snapshot
  owner, so restoring legitimately removed their pressure; replacing those
  reservations with real recreatable occupants resolved that fixture error.

- S3K required stability and retained diagnostic:
  `python3 tools/testing/maven_queue.py -Dmse=off
  '-Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils,TestS3kDezColdRoutes#theActTwoLoadRowIsStillWhereItWasMeasured+theDiagnosticSeededActTwoRouteReachesItsRecordedFrontier'
  "-Ds3k.rom.path=$S3K_ROM" -Dopenggf.headless.gl=surfaceless-egl test`:
  62 pass, zero failures/errors/skips. This includes both identically named
  level-loading classes. The historical seeded diagnostic remains exactly1256
  matching frames; it proves no cold inlet. Queue wait172s; Maven execution1m31s.

- Affected guards in a fresh JVM:
  `python3 tools/testing/maven_queue.py -Dmse=off -Pguards
  '-Dtest=TestRewindArchitectureGuard,TestObjectUpdateClockTerminologyGuard,TestObjectPhysicsStandardizationGuard,TestTraceReplayInvariantGuard'
  test`: 48 pass, zero failures/errors/skips. This is the four named guards,
  not the full guard suite.

Local Markdown targets and skill mirrors pass. The six citations in this
record pass strict verification against the pinned disassemblies. A whole-file
strict check of the existing capsule/pitfall files also reports inherited
stale/unresolvable citations; checking the added/changed lines finds no new bad
citation. Foreign dirty disassembly checkouts are preserved and reference bytes
are read from pinned Git objects.

- Real native capsule continuation:
  `python3 tools/testing/maven_queue.py -Dmse=off
  '-Dtest=TestS2ObjectOccupancyOracle#arz2EggPrisonAnimalDoesNotWalkOnLandingTransitionFrame,TestS2Arz2LevelSelectTraceReplay'
  "-Dsonic2.rom.path=$S2_ROM" -Dopenggf.headless.gl=surfaceless-egl test`:
  two pass, zero failures/errors/skips. The actual ARZ2 replay report covers7809
  frames with zero errors, warnings or bootstrap mismatches; the separate
  capsule-animal landing oracle checks native frame7064. Queue wait317s,
  Maven execution34.388s. No trace frontier moved and no fixture changed.

Total distinct final passing cases across these focused selections:131. These
are bounded behavior, stability, guard and two native-route checks, not broad
smoke or a full-suite result. No S3K runtime, campaign recipe, shared movement,
audio, renderer or general allocator changed. Remaining per-act/configuration,
lifecycle, pressure-at-real-placement and native picture/audio gaps stay explicit
above; the parent owns combined integration validation.

## Follow-up: native sprite submission backend

The first milestone above is `55c764612a26afbec059a1c57e5228a2ed17436c`.
This follow-up imports the approved S1 object-retirement dependency
`52a3469027a0b445903da5bd81969c6320e59fd6`, locally reconciled as
`767bb2877` without changing its runtime edits. The historical water-clock
section of its audit remains evidence from that separate S1 branch, not a claim
that those earlier changes were imported here. The foreign dirty S1 disassembly
pin is preserved; all references below use the repository's pinned commits.

### Native evidence and rejected shortcuts

Pinned S1 `28872a46`, `DisplaySprite (_incObj/sub DisplaySprite.asm:6-19)`
admits word-sized SST addresses until the count reaches `$7E`. Each `$80`-byte
list reserves its first word for the count: **63 admitted calls per bucket**,
eight independent buckets, not 64 and not the hardware SAT's 80 pieces.
Duplicate calls, references to subsequently deleted SSTs and ID-zero references
consume capacity. `DisplaySprite2 (_incObj/sub DisplaySprite.asm:28-41)` has
the same rule. The supplied S1 REV01 ROM's `$DC92` and `$DCB0` routines contain
the exact `0C 51 00 7E 64 06` / `0C 52 00 7E 64 06` comparisons and branches.
Its identity was freshly checked: CRC32 `AFE05EEE`, SHA-1
`69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B`.

`BuildSprites (_inc/BuildSprites.asm:23-38,118-125)` walks each list FIFO,
loads the current SST contents and skips ID zero only after admission. ROM
`$DD00` begins `30 74 60 00 4A 10 67 00 00 AC`: load queued address, test current
ID, branch past drawing. Thus retaining a Java object, deduplicating slots,
sorting calls by slot, refunding capacity on deletion or freezing attributes
when the object calls DisplaySprite would each change shipped behavior.

Pinned S2 `34fc7f1`, `DisplaySprite (s2.asm:30358-30373)`, also rejects a full
selected bucket. Pinned S3K `8b2cf3c`, however, retries later lists:
`Draw_Sprite / loc_1ABCE / loc_1ABDC (sonic3k.asm:36171-36191)`.
Locked-on ROM `$1ABC6` begins `43 F8 AC 00 D2 E8 00 08`; `$1ABCE` compares
`0C 51 00 7E`, and `$1ABDC` is
`B2 FC AF 80 67 F8 D2 FC 00 80 60 E6` (stop at list7, otherwise advance `$80`
and retry). The supplied locked-on ROM was freshly verified as CRC32 `63522553`,
SHA-1 `CFBF98C36C776677290A872547AC47C53D2761D6`. A universal reject-on-full
implementation was rejected for S3K after this direct check. The default
`submit` keeps S1/S2 semantics; the additive caller-selected `submitSpilling`
models Draw_Sprite and records the bucket actually admitted. No game-name gate
or new shared GameRules/Mod API field selects this behavior.

### Internal API, frame ownership and rewind

`NativeSpriteSubmissionSource` opts an object into authored calls and suppresses
all inferred fallback entries, including a fresh routine that calls nothing.
`NativeSpriteSubmissionAccess.submit(manager, object, bucket)` requires that
marker and uses the object's owned SST slot. The raw-slot overload and
`submitSpilling(manager, slot, bucket)` support trusted reserved-slot owners.
`admissions(manager)` is an immutable accepted-call ledger in global call order;
each value contains only the raw slot and actual admitted bucket. Its index is
the global admission ordinal. Rejected calls never enter this ledger.

The manager clears admission at the beginning of an actual object simulation
pass, including initial Process_Sprites. Rendering reuses the completed frame
without consuming, clearing or re-admitting it, supporting pause and repeated
presentation. This is an engine frame-admission cache; the native BuildSprites
routine itself drains its list counters. Full reset and spawn-list resync clear
the ledger and unbind external collaborators; per-frame clearing retains them.

The separate rewind adapter key is `object-native-sprite-submissions`.
`GameplayModeContext.registerLevelAdapters` and
`rebindActTransitionManagerAdapters` register it immediately after
`object-manager`; null owners and teardown deregister it. No
`ObjectManagerSnapshot`, public Mod API signature or ObjectServices method
changes. State captures immutable raw admission values, never an ObjectInstance,
RingDTO, resolver or Runnable. Missing older snapshots clear the queue. Graph
restore precedes queue restore, so capture between update and render, orphan
pointers and same-pass slot reuse resolve against the restored current graph.

`bindExternalResolver(manager, IntFunction<SpriteSlotEmission>)` binds a live
lookup, independent of snapshots. Bucket assembly first chooses a real current
object and its live ID; a real ID-zero owner skips drawing and blocks stale
external lookup. Otherwise it asks for the current external reserved-slot owner.
The returned emission freezes draw data only then. Its bucket cannot relocate
an already admitted call. A render-only facade carries the frozen mask/draw
through the existing loop without entering object registration, allocation,
gameplay update or graph snapshots. Rebind/invalidate when presentation inputs
change after a bucket cache has already been assembled.

`bindExternalSlotDispatch(manager, ExternalSlotDispatch)` supplies `beginPass()`
and `dispatch(slot, player, vIntRunCount, cameraX)`. Both native slot walkers
invoke dispatch only at an empty execution position with no real owned SST
occupant, after recording the player's centre and native retirement for that
position. The walk limit is checked first. Conversion into the current slot
does not execute the replacement twice; a later slot can execute in the same
pass and a past slot waits. `beginPass` runs even with no rings or a truncated
walk. The callback receives V-int explicitly, not the ring animation clock.
RingManager owns its unchanged `LevelManager.frameCounter + 1` clock, transient
late-fallback suppression and restore/rebind behavior in the sprite lane.
Neither callback identity nor that collaborator's DTO state belongs to this
adapter. The sprite lane owns actual ring phase/domain and second-movie checks.

### Ordering bridge and remaining migration obligations

Cache lists remain painter order; the existing SAT consumer reverses them.
The backend appends admitted calls in reverse FIFO after the legacy block, so
the consumer sees an explicit FIFO prefix followed by the existing legacy SST
suffix. Slots `70,40,70` stay `70,40,70`. `RenderEntry.slot()` remains the raw
SST, with `nativeSubmission()` and `admissionOrdinal()` metadata; legacy entries
use false/-1. The complete ledger retains raw empty/deleted entries even when
they produce no RenderEntry or SAT pieces.

This deliberately preserves legacy relative behavior, **not** authentic total
mixed ordering or native capacity accounting for unported objects. In particular
an unmarked replacement can retain its inferred legacy entry as well as appear
through an old admitted pointer. S1 Bomb fuse/shrapnel producer calls belong to
S1chain; ordinary Bomb body rendering remains legacy. Attracted-ring producers,
live bindings and actual per-slot motion/display belong to the sprite lane.
Folded `MultiBucketRenderable` owners still select parts by bucket; native
opt-in or late reuse into a multipart owner needs slot-to-part projection and
independent regressions before it can claim native closure. No allocator,
retirement algorithm, ring motion/collection math, draw traversal or shader
implementation is changed in this backend follow-up.

### Follow-up focused verification

All commands ran in this worktree at local dependency `767bb2877` plus the
backend changes committed with this section; no fixture, ROM file or submodule
pin was changed. `LUA_BIN=/usr/bin/lua5.4` was set and DISPLAY/WAYLAND_DISPLAY
were unset. These are logic/consumer checks with headless GraphicsManager, not
GPU pixel or whole-ROM output validation.

- `python3 tools/testing/maven_queue.py -v`: Java 25.0.5 and Maven 3.10.0;
  queue wait318s. No unleased JVM probe was used.
- Queued lean `-Dmse=off
  '-Dtest=TestNativeSpriteSubmissionQueue,TestObjectManagerNativeSpriteSubmissions,TestNativeSpriteSubmissionRegistration,TestObjectManagerMultiBucketRenderable,TestBorrowedExecutionSlotRetirement,TestGameplayModeContextRewindRegistry,TestModApiSignatureSurface'
  test -B`: initial90 cases had two errors in existing tests whose fully mocked
  ObjectManagers lacked an initialized queue. Their original graph-order probes
  now wrap a real queue owner and expect object-manager → queue → rings. The
  rerun passed90/90, no failures/errors/skips, Maven35.611s, queue wait2s.
- Consumer review then caught missing throwaway-construction rejection. The
  common admission path now rejects probe construction, including the object
  overload before marker/slot access. A real probe constructor calls all three
  ports and proves false results, an unchanged ledger and intact63-call capacity.
  The callback ordering test also checks an actual participant and captured
  centre coordinates at the external slot.
- Final affected queued lean selection
  `-Dmse=off
  '-Dtest=TestNativeSpriteSubmissionQueue,TestObjectManagerNativeSpriteSubmissions,TestNativeSpriteSubmissionRegistration,TestGameplayModeContextRewindRegistry'
  test -B`: 73/73 pass (14 queue,24 manager,6 registration,29 existing context),
  no failures/errors/skips; Maven1m28s, queue wait49s. The unchanged earlier
  multipart5, borrowed-retirement2 and Mod API signature11 results remain bounded
  evidence for their unchanged paths; no public signature pins were edited.
- Pinned disassembly citation check over this artifact and the queue/tests:
  exit0,11 exact and1 nearby inherited anchor, no invalid references. Dirty
  optional reference checkouts were reported and preserved.

- Affected structural guards, separate queued fresh JVM:
  `-Dmse=off -Pguards
  '-Dtest=TestObjectPriorityBucketGuard,TestObjectServicesMigrationGuard,TestObjectUpdateClockTerminologyGuard,TestRewindArchitectureGuard,TestRewindTransientGuard,TestNoServicesInObjectConstructors,TestNoRendererCaptureInUnsafeSpawn'
  test -B`: 34/34 pass, no failures/errors/skips; Maven1m46s, queue wait280s.
  XML summaries were inspected for every selected class. Independent read-only
  review also found no actionable mismatch in the new dispatch seams.

Total distinct passing follow-up cases across the focused selections:125
(91 ordinary including44 new cases,34 guards). Final affected73 and guards34
ran after the probe rejection fix; the other18 ordinary cases cover unchanged
paths as described above. No independent broad smoke, exhaustive route,
second-movie or GPU-output claim is made; those consumer/domain and combined
integration checks remain coordinated with S1chain, the sprite lane and parent.
