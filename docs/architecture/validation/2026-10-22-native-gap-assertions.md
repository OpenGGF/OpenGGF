# Native assertion and capsule allocation repairs — 2026-10-22

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
