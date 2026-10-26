# S2 native CNZ map bumper port

This bounded domain prerequisite follows the
[native competition design](2026-08-09-s2-native-competition-human-p2-design.md).
It starts from `86d40181522dcaaa38770c8b9347233b665f7fa3` in the isolated
`s2-dual-camera-streaming` worktree. The native host owns activation and the
complete teleport transaction. No shared manager, zone-feature shell, object
allocator or ordinary bumper behavior changes here.

## Authority and decisions

Authority is Sonic 2 World REV01 with `fixBugs=0`, CRC32 `7B905383`, SHA-1
`8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`. Runtime data comes exclusively
from the injected ROM. `CNZBumperDataLoader.loadNativeCompetition(rom, act)`
reads the two 324-byte tables at `$1781A` and `$1795E`, plus the preceding six
bytes. The immutable `CNZNativeBumperTable` retains every address, raw ID,
coordinate, leading zero record and terminal `{0000,FFFF,0000}` record.

- `SpecialCNZBumpers_Init` (s2.asm:32380-32411) seeds both start/end pairs
  from P1's camera. Camera X is an unsigned word; values 0–8 produce left
  edge 1, otherwise camera minus 8. The right edge adds `$150` as a word.
- `SpecialCNZBumpers_Main` (s2.asm:32414-32496) moves P1 then P2. Each
  persistent pointer scans forward while edge is greater than current X,
  then backward while edge is less than or equal to previous X. There is
  no sorting, binary search, bounding union or stationary-camera shortcut.
- `SpecialCNZBumpers_Act1` (s2.asm:32881-32900) documents the shipped missing
  initial boundary. The preceding `jmp PlaySound` bytes are `4EF9 0000 1370`;
  their previous-X word zero stops backward scanning. The bug-fixed branch
  inserts a real zero record; this port preserves the shipped bytes instead.
- `Check_CNZ_bumpers` (s2.asm:32499-32594) selects the caller's own pair,
  performs inclusive word/carry overlap checks, and uses mapping frame `$4D`
  for the shipped duck box, including on Tails. Dimensions mask ID with `$E`;
  handler dispatch uses the entire ID word.
- `loc_17586` / `loc_17638` (s2.asm:32606-32718) return immediately after
  the first triangle's box overlap, including a rejected slope. Rejection
  does not continue the scan or change flags/audio. `loc_175EA`
  (s2.asm:32651-32675) compares the unsigned low byte of a word absolute
  angle difference, then uses signed multiplication/arithmetic shifts.
- `CalcAngle` (s2.asm:4037-4074) can return word `$100` for a small negative
  Y velocity with positive X, such as `($100,-1)`. The common trig helper
  returns a byte angle, so the port restores this native word case locally
  before reflection. At a type-8 bumper's center the result is surface `$F0`,
  not `$F8`; one pixel above center forces `$08` rather than reflecting `$10`.
  Source review found this independently of the ordinary bumper helper.
- `loc_1769E` through `loc_177FA` (s2.asm:32720-32884) retain equality-side
  choices and the shipped positive-X writes at vertical bumper tips. A
  response sets airborne, clears rolljumping/pushing/jumping, preserves
  inertia and invulnerability, then invokes the injected slot audio callback.
- `teleport_swap_table` (s2.asm:26169-26187) exchanges the two eight-byte
  bumper address blocks. The port swaps only those pairs in one immutable
  state assignment; actor, camera and other domain exchange remains host-owned.

The ordinary placement manager was rejected as a native implementation:
it sorts/rebuilds its window and has different triangle/zero-axis responses.
The decisive ROM counterexample is CNZ2 raw records 18/19 at X `$18C0`
followed by record 20 at X `$1890`. Starting camera `$1768` yields end index
18; advancing to `$1779` yields 21; retreating to `$1768` leaves 21 because
the previous raw `$1890` stops the backward walk. A fresh sorted window
cannot preserve that history. Ordinary loading and behavior remain intact.

## Host seam and timing

Construct only for the active explicit human roster:

```java
var table = new CNZBumperDataLoader().loadNativeCompetition(rom, act);
var bumpers = new Sonic2NativeCNZBumpers(table, roster, slot -> playLargeBumper(slot));
registry.register(bumpers.rewindAdapter());
bumpers.reset();
bumpers.updateCursors(); // Init, before initial RunObjects
```

`Level_FromCheckpoint` (s2.asm:5003-5007) initializes managers before
`RunObjects`. Thereafter `Level_MainLoop` (s2.asm:5095-5103) executes objects,
deformation, rings and then bumper cursor Main. The host calls
`checkPlayerCollision(slot)` during each human's eligible TouchResponse,
after rings and before ordinary object touch, using the existing cursors.
`TouchResponse` (s2.asm:85174-85180) does not inherit the ring check's
invulnerability-timer rejection. Control's object-control bit-7 gate remains
effective; bit 0 alone does not suppress touch. The host owns dispatcher
eligibility; the port also rejects hurt/dead actors and P1 debug placement.

`swapNativeCompetitionSlots()` swaps only both bumper cursor pairs. It does
not read or move cameras/actors and does not play audio. The host must include
it in the complete native teleport transaction. `reset()` and a missing
adapter snapshot restore the uninitialized state, so the next cursor call
seeds both pairs from P1 again.

`rewindAdapter()` has private key `sonic2_native_cnz_bumpers`. Its typed codec
captures immutable private init and pointer-index values, with no public
snapshot fields. The table and roster are fixed session dependencies. Ordinary
player/camera adapters own actor and view state; register this adapter in the
same world graph. No comparison/trace data supplies progress or collision state.

## Validation and limits

Focused tests cover separated/overlapping/crossing views, exact cursor and
collision edges, repeated raw records, P1-first initialization/update order,
unsigned camera words, native first-overlap rejection, all narrow handlers,
per-human callbacks and shared non-consuming bumpers. The graph regression
captures both real players, both cameras and the private adapter, then restores
and forwards through collision, swap and separated windows. ROM checks verify
all raw words/addresses, both boundary forms, actual two-human responses and
CNZ2's path-dependent cursor restore. The ordinary manager regression remains
in the exact focused selection.

The final focused command used Java 25, Lua 5.4 and the verified supplied ROM
as an absolute `sonic2.rom.path`. `S2_ROM_PATH` below denotes that external
machine-local path:

```bash
env -u DISPLAY -u WAYLAND_DISPLAY \
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --lean -Dmse=off \
'-Dtest=TestSonic2NativeCNZBumpers,TestCNZNativeBumperRom,TestCNZBumperManager' \
"-Dsonic2.rom.path=$S2_ROM_PATH" test
```

It completed at **2026-10-22 12:42:20 UTC: 38 tests, zero failures, errors or
skips** (30 native behavior, 5 ROM/parser, 3 ordinary controls). Maven elapsed
was 1:22; admission waiting was separately 186 seconds. The tested source was
base `86d401815` plus this commit's domain/test changes. The private graph
restore/forward test passes without adding any public snapshot fields.

The first 29-test run found two incorrect test expectations (zero-velocity
reflection and duck-box geometry); source inspection corrected those inputs.
The subsequent 30-test run passed. The independent word-angle regression was
then reproduced with this exact additional selector before the local fix:

```text
-Dtest=TestSonic2NativeCNZBumpers#fullWordAngleRetains100BeforeSurfaceReflection
```

Both cases failed before word recovery (2 tests, 2 failures, zero errors/skips)
and pass in the final selection. These overlapping runs are not additive
coverage. The strict changed-file disassembly citation check and whitespace
checks also pass against pinned S2 disassembly `34fc7f130a96`.

This is a domain prerequisite: host service binding, phase wiring, complete teleport,
production title route and native trace/presentation parity remain integration
work. No broad smoke, release-only full suite, native trace or presentation
capture was run for this bounded slice.
