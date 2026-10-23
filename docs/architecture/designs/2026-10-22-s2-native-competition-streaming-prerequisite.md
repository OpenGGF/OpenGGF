# S2 native competition streaming prerequisite

This bounded prerequisite follows the [native competition design](2026-08-09-s2-native-competition-human-p2-design.md).
It starts from `a1ee57fea02fe59d7bb18cca52c82bbb6bb11ba8` in the isolated
`s2-dual-camera-streaming` worktree. It does not activate a title route.

## Source and implementation

Authority is World REV01, CRC32 `7B905383`, SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`, with `fixBugs = 0`.
The repository-pinned S2 disassembly is `34fc7f130a96`; the supplied reference
checkout `380f37a731bf` has the same `s2.asm` content. Its two changed binary
placements are FixBugs assets, which this implementation does not load.

`Sonic2CompetitionObjectStreaming` owns the S2 numeric layout and cursor rules.
Shared managers consume the internal `NativeCompetitionObjectStreaming`
contract and require the explicit session-owned `NativeCompetitionRoster`.
No shared game-name or zone-name branch selects the new behavior.

- `ObjectsManager_2P_Init` (s2.asm:33145-33196) seeds P1's columns 0/1/2,
  then P2's. Both layout-pointer/counter pairs originate in P1's entry scan.
- `ObjectsManager_2P_Main` / `ObjectsManager_2P_Run` (s2.asm:33199-33244)
  processes P1 before P2. Each camera has its own 256px coarse position,
  front/back placement pointers, byte respawn counters and three interests.
  A coarse change shifts one column, including when a camera jumps farther.
- `ObjMan2P_GoingForward` (s2.asm:33346-33434) and the backward path share
  six physical banks, each containing twelve slots. Allocation is ascending
  within the selected bank, slots 56–127. A bank survives while either player
  still owns its column. The gap between separated views stays unloaded.
- `ChkLoadObj_2P` (s2.asm:33631-33672) sets the shared respawn latch before
  trying allocation. Forward failure parks its pointer after incrementing the
  byte counter. Backward failure rolls its pointer/counter back, retaining the
  latch. Stationary cameras do not retry. Raw Y bit 12 instead selects the
  general slots 16–55.
- `ObjectsManager_2P_UnloadObjectBlock` (s2.asm:33511-33580) deletes actual
  bank occupants, including moved objects, and clears only their nonzero live
  respawn latches. A failed allocation has no occupant to clear its latch.
- `MarkObjGone` (s2.asm:30215-30271) bypasses ordinary object-side culling
  in competition. Native bank ownership supplies the standard delete path.

`Sonic2CompetitionRingStreaming` owns two start/end cursor pairs into the
single shared ring table. `RingsManager_Init` / `RingsManager_Main`
(s2.asm:31799-31923) initially copies P1's pair to P2, then moves P1 followed
by P2. Each range is `[max(1, cameraX-8), left+336)`, using native unsigned
word arithmetic. The active/render candidate list is the deduplicated union;
each actor's touch pass uses only its own pair. Shared collection state
prevents a second pickup, and credit goes to `roster.levelState(slot)`.
The ordinary manager update compatibility path collects P1 before P2.

`Sonic2ObjectPlacement.load(ZoneAct, boolean nativeCompetition)` selects
CNZ1's explicit REV01 layout at `0x1802A` (187 records) or CNZ2 at `0x18492`
(168 records). `ObjectsManager_Init` (s2.asm:32943-32952) owns that selection;
`Objects_CNZ1_2P` / `Objects_CNZ2_2P` (s2.asm:33780-33798) own the binary
placements. Other zones, and the existing one-argument load, retain the
ordinary pointer table. Runtime bytes always come from the ROM reader.

## Integration seams

Before ordinary `reset(cameraX)` materializes placements, the native S2 world
owner binds these policies:

```java
ObjectManagerInternalAccess.enableNativeCompetitionStreaming(objects,
        new Sonic2CompetitionObjectStreaming());
RingManagerInternalAccess.enableNativeCompetitionStreaming(rings,
        new Sonic2CompetitionRingStreaming());
```

The object reset seeds from P1 and its initial materialization performs native
Init followed by Main for both roster cameras. The ring reset performs native
Init only; its next normal update performs Main. Normal object streaming stays
after object execution; the ordinary post-camera gap loader is disabled for
the native policy. Normal player touch calls continue to use
`collectStageRings(player, frameCounter)`.

Both internal bridges expose `nativeCompetitionStreamingRewindAdapter(manager)`
and `swapNativeCompetitionStreamingSlots(manager)`. Register the adapters in
the real native world in addition to the existing manager snapshots. They
capture only private streaming state, under keys
`native_competition_object_streaming` and `native_competition_ring_streaming`.
The existing snapshots retain shared respawn bytes, live object graph and ring
statuses. Restore the ordinary ring snapshot before its cursor adapter, so
the latter reconstructs the union from both restored pairs.

Teleport exchanges the independent object pointer/counter/coarse/interest
states and ring cursor pairs; it preserves shared banks, objects and ring
statuses. This matches `swap_players` / `teleport_swap_table`
(s2.asm:26003-26050,26168-26189). The actor/camera/state swap and dependent
object flags belong to the native session owner.

The lead owns the semantic general-allocation cap at slot 56, independent of
this placement-specific bounded reserve scan.
`ObjectManagerInternalAccess.nativeCompetitionGeneralAllocationSlotLimitExclusive(manager)`
returns the active policy's exclusive bound (56 for this S2 policy), or -1 when
inactive. The same package-private manager method is available to the plain
allocator's implementation. The value originates in the S2 provider, not in
shared manager code. Apply it only to plain `AllocateObject`;
`AllocateObjectAfterCurrent` retains its distinct native range. This child changes no shared
allocator or reservation method. The lead also owns real data-pipeline binding,
rewind registration and the final activation gates. S3K's upfront `$800`
placement loader is a distinct policy and must not bind this S2 streamer.

## Validation and limits

Focused tests exercise shared overlap, separated and crossing cameras, exact
column/ring edges, P1 allocation order, bank/general-RAM exhaustion, native
latch/retry behavior, slot-specific collection and independent capture/restore
followed by forward replay and teleport. Existing S1/object/ring regressions
cover the unaffected ordinary path. ROM-backed parser tests verify both CNZ
layouts and ordinary selection. Exact completed commands and counts follow.

These checks establish a streaming prerequisite. They do not establish a
production title-to-results route, presentation parity, or native BK2 route
parity. No trace comparison data supplies gameplay or collection progress.


Both completed invocations used Java 25 and Lua 5.4. `S2_ROM_PATH` below
stands for the absolute supplied REV01 path verified above; the machine-local
prefix is parameterized to satisfy the repository resource policy:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py -Dmse=off \
'-Dtest=TestNativeCompetitionObjectStreaming,TestNativeCompetitionRingStreaming,Sonic2ObjectPlacementTest,TestObjectPlacementControllerS1Counter,TestObjectManagerLifecycle,TestObjectManagerVerticalPlacement,TestObjectManagerRewindSnapshot,TestRingManagerActiveIndices,TestRingManagerRewindSnapshot,TestRingManager' \
"-Dsonic2.rom.path=$S2_ROM_PATH" test -B

JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py -Dmse=off \
'-Dtest=TestNativeCompetitionObjectStreaming,TestNativeCompetitionRingStreaming' test -B
```

The first completed at 2026-10-22 09:58:26 UTC: **117 tests, zero failures,
errors or skips**, Maven elapsed 1:18, separate admission wait 54 seconds.
The second completed at 10:13:27 UTC after the internal bridge move:
**22 tests, zero failures, errors or skips** (14 object, 8 ring), Maven elapsed
1:16, separate admission wait 78 seconds. It revalidated the final native-only refinements;
the ordinary/parser paths were unchanged after the first run. These selections
overlap and their counts must not be added as distinct coverage. No broad
smoke, release-only full suite, or native trace was run in this child.
After adding the lead-requested semantic pool-bound accessor, the final
executable sources also passed the exact focused command below at
2026-10-22 10:19:22 UTC: **25 tests, zero failures, errors or skips** (14 native
object tests, 11 API signature tests), Maven elapsed 1:20, separate admission
wait 73 seconds. The ring implementation was unchanged from the 22-case pass.
The actual API surface/pin checks passed without changing any signature pin.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py -Dmse=off \
'-Dtest=TestNativeCompetitionObjectStreaming,TestModApiSignatureSurface' test -B
```

The source base remained `a1ee57fea`; all task changes were uncommitted during
validation and are included with this note in the delivery commit.

`git diff --check` passed. The final citation checker over the new S2 provider files,
parser and this note reported no stale/missing/out-of-range findings (eleven
matched, five near-label and four unverifiable citation tokens). A wider check of the existing manager files
reported inherited stale/missing citations; those unrelated references were
left unchanged. Maven removed consumed reports after successful execution;
no diagnostic archive or pass cache was created.

The commit hook also checks declaration text inside annotated manager files.
Anonymous adapter/loader overrides there were conservatively treated as API
changes. Their implementations now live in the unannotated internal bridge
files, retaining package-private manager seams and the unchanged public API.
The exact prepared message passed `validate-policy.sh commit-msg`; no API pin
was edited to satisfy the detector.

## Approaches corrected during implementation

- A shared bounding rectangle, or advancing the ordinary cursor twice, cannot
  represent six physical banks plus independent interests/counters. The native
  bank/cursor model above preserves the allocation and eviction sequence.
- Treating Java placement identity as a duplicate latch is wrong for an
  untracked general-pool entry: its previous owner can survive eviction while
  the native loader creates another. A focused reentry test retains both SST
  owners and verifies restore/replay. The first test attempt correctly exposed
  that its private test actor had not opted into the existing dynamic
  `RewindRecreatable` contract; the fixture was corrected without changing the
  shared dynamic restore gate. The supported competition ROM layouts contain
  no untracked bit-12 entry; the crafted case protects the actual loader rule.
- Reusing the ordinary 128px subcolumn ordering would reorder native records
  inside one 256px column. The policy recovers ROM order from the parser's
  layout ordinals; a deliberately opposed full-X record pair proves slot and
  respawn-index order. Indexed ROM placements are the authoritative input.
- Capturing mutable cursor classes in an opaque snapshot would compare Java
  identity in the real `RewindSnapshotDiff`. Private primitive record payloads
  now compare by content, reject changed state, and compare equal after replay.
- A blanket bypass of every custom lifetime check was rejected: some checks
  express parent destruction rather than camera culling. S2 object-specific
  native mode gates remain with the S2 domain owner. For example,
  `loc_2C5C4` (s2.asm:60077-60094) bypasses moving bumper endpoint culling in
  competition; its current Java custom-check consumer needs that domain gate.

The remaining lead-owned hooks are the explicit data/profile binding, general
allocation cap at 56, real-world rewind registration, complete teleport actor
swap, and native object-specific mode consumers. The neighboring lost-ring
spill clear still selects the ordinary level state (and its deferred owner
selects the main player); the native hurt owner must route that clear by slot.
These are integration dependencies, not claims that the streaming prerequisite
activates a complete native mode.
