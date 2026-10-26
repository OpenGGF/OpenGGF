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

`NativeSpriteSubmissionSource` opts an object into authored calls. Its internal
`nativeSpriteSubmissionsAuthoritative()` default is true, suppressing all inferred
fallback entries, including a fresh routine that calls nothing. A conditional
port may return a captured false flag until its native state is transcribed;
false retains the legacy path and never manufactures an admission.
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
changes. State captures immutable raw admission values and whether an actual
object pass occurred, never an ObjectInstance, RingDTO, resolver or Runnable.
Missing older snapshots clear the queue and the pass-present flag. Graph
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

## Bounded physical SST word at offset $2C

The source-only follow-up inspected Mode's native SST contracts and the frozen
PointPokey draft at `25afa09cdece7ee9e901223cfcbbcdaad0a8f0dc` plus its owned
uncommitted producer changes. The existing minimum dependency is original
`ae632fe2a6eec9bece7cafb4f2643c74365565c3`, already present in this lane. No
Mode branch aliases or unrelated mode ancestry are required for the backend.

At pinned S2 reference `34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`,
`ObjDC_Main` (s2.asm:25489-25494) and `ObjD3` (s2.asm:58849-58852) decrement the
WORD at the LONG address stored in child `$2A` before awarding/debiting rings.
`loc_2BC86` (s2.asm:59258-59262) and `loc_2BD4E` (s2.asm:59302-59306) construct
that address with `lea objoff_2C(a0),a2`; it is a physical address, not an
object identity. `DeleteObject2` (s2.asm:30334-30345) clears the whole slot,
whereas `AllocateObject` (s2.asm:33681-33698) only tests the ID. `ObjD6_Init`
(s2.asm:59142-59149) does not write `$2C`. An orphan settlement after deletion
therefore changes known zero to `$FFFF`, and another changes it to `$FFFE`;
ordinary allocation cannot erase this residue.

The accepted bounded prerequisite is one unsigned word plus knownness per
physical SST, independent of Java occupancy, with a typed writable projection
for proven live receivers and a separate copied-value rewind adapter. The
existing ten-field `NativeSstState` is a partial live projection, not arbitrary
object RAM. Unknown empty residue must remain unknown; unsupported live
receivers must remain explicitly unsupported. Actual native clear operations
must establish zero, rather than allocator release or generic Java teardown.
Factory failure must precede any preservation mutation; a successful partial
replacement samples the old owner before detachment and publishes the new
typed projection afterwards. Probe and rewind reconstruction do not execute
these gameplay writes.

Mode owns PointPokey/prize projection and producer calls, real birth importing
retained `$2C`, and the actual load-clear semantic caller. This backend does not
certify foreign layouts. In particular, another prize's `$2C` is the low WORD
of its own LONG counter pointer at `$2A`; changing it can alter the target and
alignment. The current fixed-offset `counterSlot` representation cannot express
that alias. It remains open pending a local receiver/address transcription.

The implementation adds `ObjectNativeSstWord2C`, the exact internal projection
`NativeSstWord2CSource.nativeWord2C()` / `writeNativeWord2C(int)`, and
`NativeSstSlotAccess.readWord2C`, `writeWord2C`, `addWord2C`, `clearWord2C`,
`preserveWord2C` and `invalidateWord2C`. The separate adapter key is
`object-native-sst-word2c`; `GameplayModeContext` registers it after the object
graph and retires/rebinds it with that manager. Its immutable snapshot contains
only copied `(slot, unsignedWord)` values; absent cells are unknown. Existing
`ObjectManagerSnapshot` and public Mod API signatures are unchanged.

New typed publication must match already-known retained memory; unexplained
constructor zero cannot overwrite orphan `$FFFF`. An existing registered
identity instead samples its current proven field during capture. Partial
replacement preserves the old field only after a non-null factory succeeds,
before detachment; its existing physical identity remains unchanged and no
`onUnload` runs. Gameplay access rejects
`ObjectConstructionContext.isProbeConstruction()`, `isRewindActiveRestore()`
and the existing `ObjectManager.rewindReconstructionChildCapture` phase. These
are existing reconstruction predicates, not a new gameplay clock or scope flag.

Shared hunks and exact API were agreed before editing. Native MarkObjGone and
full-load clear integration remain separately coordinated. Generic reset,
resync, removal and slot release do not acquire a native memory-clear meaning.
External/folded SST producers without an `ObjectInstance` must explicitly
invalidate knowledge on an actual unprojected `$2C` write/creation, or later
provide a proven projection. Allocator bits cannot distinguish such owners from
real birth's legitimate reserved-but-unpublished import. This backend therefore
does not infer occupancy from those bits or certify arbitrary SST bytes.

Bounded ROM-free checks on `c89fd18` plus this leaf:

- Queued selection `TestNativeSstWord2C,TestNativeSpriteSubmissionRegistration,TestObjectManagerNativeSpriteSubmissions,TestModApiSignatureSurface`:
  69 tests, zero failures/errors/skips; queue748s, Maven1m33s. The four XML
  summaries were inspected. This includes16 new word cases and3 new session
  registration cases, alongside unchanged queue-consumer and API checks.
- Final focused `TestNativeSstWord2C`:18 tests, zero failures/errors/skips;
  queue33s, Maven30.581s. Two additional independent controls require preservation
  after successful construction and receiver-owned read/write/verification
  callbacks; failed/null factories execute no word-preservation callback.
  Production source is identical to the first focused run. Total distinct
  ordinary cases across these selections is71, including21 new cases.

Both used the queued wrapper, Java25.0.5, Lua5.4 and unset DISPLAY/WAYLAND;
no ROM, native capture, route or broad smoke ran. The build-only `test-compile`
also passed (Maven1m25s). The version probe passed after328s admission wait.
The queued fresh-JVM affected guard selection
`TestObjectServicesMigrationGuard,TestObjectUpdateClockTerminologyGuard,TestRewindArchitectureGuard,TestRewindTransientGuard,TestHelperStateRewindCoverageGuard,com.openggf.tests.TestNoServicesInObjectConstructors,TestNoRendererCaptureInUnsafeSpawn`
with `-Dmse=off -Pguards` passed34 tests, zero failures/errors/skips; queue409s,
Maven1m35s. All seven XML summaries were inspected. Total distinct passing
cases across the three bounded selections is105. The local source-budget
dependency is explicit: ObjectManager was3147 effective lines at `c89fd18` and
is3152 here, against3086. The separately owned draw extraction must close this
before composed structural qualification; neither a budget increase nor source
padding is part of this leaf. Actual PointPokey adoption, native delete/load
callers and the foreign LONG alias remain outside this validation.

## Follow-up: empty-pass restore and conditional producer authority

The original backend is `d89c09e660264f242192b82f3caa7600af9e1d00`; this
correction is a new descendant, not a rewrite of that tested commit. A cleared
transient RingManager dispatch sentinel cannot distinguish a restored actual
pass with no admitted ring from a legacy frame without a pass. Inferring pass
presence from a nonempty ledger therefore exposes unadmitted external sprites
after empty, rejected or truncated passes. The queue now captures a boolean
alongside its value-only ledger, exposed internally by
`NativeSpriteSubmissionAccess.nativePassPresent(ObjectManager)` (null is false).
Actual terminal update and initial dispatch begin set it even with zero calls;
raw submissions alone do not. Full reset, spawn resync and missing historical
adapter state set false. The one-argument internal State constructor defaults
false for metadata without a pass. Restore and repeated rendering retain the
captured value; callback and DTO identity still never enter this snapshot.

A second edge concerns attracted-to-bouncing conversion when every native list
from bucket2 through7 is full. No admitted pointer exists for the converted
LostRing, so accepted-entry duplicate suppression alone cannot stop its inferred
legacy draw. The marker's exact additive method is
`default boolean nativeSpriteSubmissionsAuthoritative() { return true; }`.
The backend excludes inferred fallback only while it is true. Existing Bomb
and simple-source defaults remain true. Sprite owns the LostRing captured
converted-render flag, ordinary-factory false behavior and genuine native tail
calls. This field belongs to the current object, not a guessed attempt ledger.
`ObjectRenderBucketSnapshot` includes one private authority bit so state-only
changes invalidate the lazy bucket cache. Accepted FIFO, ordinals, late slot
resolution and multipart selection are unchanged. Sprite alone owns traversal
suppression of inferred duplicates when an accepted native entry exists.

The new independent manager controls restore through a registered object graph
and queue adapter after erasing the original graph: empty ordinary/initial
passes, a full bucket whose external call is rejected, and a truncated walk
whose external tail never ran. Rendering after restore must emit none of those
unadmitted sprites while the pass bit remains true. Additional controls cover
raw submissions without a pass, reset/resync, default-true compatibility,
conditional false/true with zero calls and full2..7 capacity, state-only cache
refresh, and repeated destructive restore between conditional authority states.
Queue controls distinguish empty true/false snapshots, old/missing state and
restored capacity. Consumer-specific RingManager restore/render and converted
LostRing callsite tests remain in the sprite lane.

Queued focused checks used Java25, `env -u DISPLAY -u WAYLAND_DISPLAY` and
`LUA_BIN=/usr/bin/lua5.4`; no broad selection was submitted:

- Preliminary pass-presence implementation:
  `python3 tools/testing/maven_queue.py --lean -Dmse=off
  '-Dtest=TestObjectManagerNativeSpriteSubmissions,TestNativeSpriteSubmissionRegistration,TestModApiSignatureSurface'
  test -B`: 46/46 pass, zero failures/errors/skips; Maven1m33s, no queue wait.
- Final combined source, including the conditional marker and cache-key bit:
  `python3 tools/testing/maven_queue.py --lean -Dmse=off
  '-Dtest=TestNativeSpriteSubmissionQueue,TestObjectManagerNativeSpriteSubmissions,TestNativeSpriteSubmissionRegistration,TestModApiSignatureSurface'
  test -B`: 71/71 pass (queue21, manager33, registration6, signature11),
  zero failures/errors/skips; Maven1m36s, queue wait5s. XML summaries were
  inspected. This includes16 new independent cases relative to d89.

No registration, callback or public snapshot/Mod API shape changed. The
unchanged d89 guards are not represented as a new guard run here. This does
not migrate legacy producers or close mixed ordering/multipart obligations;
Sprite's real RingManager/LostRing consumer composition remains separate.
