# Sonic 1 executed TAS trick verdicts — 2026-10-26

This pass supersedes the route-fixture requirements in
[the earlier S1 matrix](2026-10-26-tas-tricks-s1.md). A representative execution
of a named technique, with a meaningful control, is sufficient. Publication
routes and historical variants remain scope notes; they do not turn an executed
mechanism back into “partial”. The source inventory remains the 23 original IDs
in the audit's `s1.json` (audit commit `99b16240`).

Implementation base: `c69447d77baddd8d3f7e4a177d73c8d327dd223d`.
Owned branch: `feature/ai-tas-verdicts-s1-20261026`.
Owned worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-88f9a952`.
This pass changes tests and S1 evidence prose only. Parent owns integration.

`MAIN_WORKSPACE` denotes the original main checkout containing `s1.gen`;
`S1_VERDICT_TASKDIR` denotes the external `tas-trick-verdicts-2026-10-26` task
directory. Their exact absolute paths are supplied in the handoff/JSON. Command
paths below are normalized to these variables solely for repository portability.

## Evidence contract

Native reference is shipped `FixBugs=0` at immutable S1 disassembly
`28872a46e2d4c94c5c4818cbd3c04e6eb17edb67`. The working disassembly was dirty;
its immutable Git object was archived externally and used without changing it.
ROM: original `$MAIN_WORKSPACE/s1.gen`, CRC32 `AFE05EEE`,
SHA-1 `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` (World REV01).
REV00 publication caveats never redefine the result for this REV01 mechanism.

Native probes use an independently archived TraceChaser
`703bd92d32634ce5ebac60625b48c60ef5500427`, its own build, and the read-only
BizHawk 2.11 runtime. `S1NativeProbe.cs`, its launcher and diagnostic outputs are
in `$S1_VERDICT_TASKDIR/`.
They boot the ROM normally, initialize the chosen act, and declare only the local
entry position, fraction, velocity and camera. For a remote platform entry the
native object loader is reinitialized before settling the scene. This avoids
mistaking an unpopulated teleport destination for an engine mismatch. No native
observation is imported into engine gameplay state. These are local mechanism
probes, not synchronized movie/trace replay claims.

A limitation regression is intentionally named as a limitation. A green result
means the engine still contradicts the separately stated native result; it does
not mean native parity passed. The corresponding native assertion was executed
red before retaining the limitation assertion. Fixture construction errors are
recorded separately and are not unsupported verdicts.

## Executed inventory

The 23 original records resolve to **19 supported families, 2 unsupported
families, 1 mixed family with two binary subtricks, and 1 source-note record**.
There are 22 tested families and 28 binary scenario entries (27 distinct named
scenarios; the cog launch is referenced by both momentum and cog families). No named
mechanism is left partial because a publication route or movie was not replayed.
`S1_UNSPECIFIED_ROUTE` has no support verdict and is excluded from that count.

The original final exact selection passed **54 cases, zero failures/errors/skips**.
The bounded grounded-momentum follow-up passed **6 additional cases**, also with
zero failures/errors/skips, without repeating unchanged cog coverage.
Reused tests were freshly executed: the first four-class selection passed 39
cases (including 11 original movement cases now also in the final selection),
and 30 additional existing cases passed within the documented exploratory run.
Including that follow-up, **118 distinct retained cases** passed in this work,
including **49 new cases**. This is focused validation, not a smoke/full-suite or movie pass.

A family verdict aggregates its named local scenarios. Original family names,
mechanisms and publication sets remain in the companion `s1-verdicts.json`.
Narrowing is explicit: for example, giant-ring admission and independent pickups
in all six mazes do not certify a complete 100% campaign. Source-note provenance,
REV00/REV01 ambiguity and historical route edges are not engine limitations.

### Native cog and platform boundaries

`S1_COG` means the SBZ2 **running-disc terrain**, Obj67, not the Obj66 junction.
Entry is grounded `(x,y)=($2B0,$710)`, speed `$F00`, zero fractions. Neutral
movement reaches `($2D9,$718)`, angle `$20`, attached to the disc. Jump on local
update 3 yields `vx=4531`, lands against the next cog at `($320,$722)` on update
7, and moves to `($33C,$715)` on update 8: a 28-pixel forward correction. A jump
one update later hits the wall at `$315` and clears X velocity. Native samples
come from `s1-native-disc-jump3.txt.gz` and `s1-native-disc.txt.gz` (jump 4). This proves
a local high-speed cog launch and forward clip, not every SBZ2 trajectory.
Sources: [2410M/3994S](https://tasvideos.org/3994S),
[3570M/5702S](https://tasvideos.org/5702S); native `RunningDisc`, `Sonic_Jump`,
`Sonic_FindFloor` and `Sonic_Move`.

The SBZ1 platform scenario begins above native Obj52 at `($1240,$308)`, player
`($1240,$2E4)`, zero velocity. It lands at Y `$2EC`; the real platform carries it
left to `$1208`. On update 16 wall correction leaves X `$1203`. Left input gives
positive `vx=756`, then `1780` by update 20, and escapes the overlap to `$121F`
by update 28 while still holding Left. Right input gives `vx=12`, then `60`, and
reaches only `$120D` with `vx=180`. Native left/right captures agree at these
boundaries. This is an actual input-dependent reverse launch after platform
embedding. It is a bounded lower-speed variant, not a claim that the capture
reproduces every historical 16–32 px/frame zip. The non-boosting control also
executes positional ejection: movement left despite a positive low X velocity.

### Continuous wall traversal, separate from the bounded launch

`TestTasS1ContinuousZip` executes the complete local continuous-wall mechanism
from [447M/907S](https://tasvideos.org/907S): a platform supplies the entry and
Left produces rightward traversal. The authored geometry is a full-tile wall
`$200 <= x < $400`, `$140 <= y < $240`. Sonic starts outside at `($430,$1D0)`.
A real S1 Obj52 shuttle at `($430,$200)`, subtype `$39`, X-flipped, acquires Sonic
through a real landing. After 100 neutral updates it has carried him to `$3B0`.
Left then gives grounded X corrections `$3CA`, `$3EA`, `$40A`: **26, 32, 32
pixels**. His left extent `$401` clears the wall's far face `$400`. The same
shuttle with no wall moves Sonic left to `$3AC`, with no >=16-pixel corrections.
Both cases pass. Neither embedding nor zip velocity is injected after landing.

This uses S1 rules, S1 object dispatch after movement, and real GroundSensor
scans over declared solid tiles. The level lookup is authored, not a replay or
native level-layout capture. S3K supplied the geometry idea only; neither its
object nor its outcome is used as S1 proof. Independent native basis is
`MBlock_NextWhenStoodOn`, `MBlock_SlideFast`, `MvSonicOnPtfm2` and
`Sonic_WallSpeedAdjust` (signed distance shifted by eight and subtracted on the
left wall branch). The initial copied exit threshold `x > $410` was an invalid
fixture assertion: S1 already fully clears at `$40A` with radius 9. Replacing it
with `centreX - xRadius >= $400` expresses the independently specified geometric
exit; it is not a weakened native-parity assertion.

### Three independently reached incompatibilities

- **Unsigned horizontal wrap:** GHZ1 at X 16 with airborne `vx=-$2000` predicts
  unsigned X `$FFF0`. `Sonic_LevelBound` in the native REV01 capture clamps to
  **9767**, while the engine clamps to **16**. The parity assertion ran red at
  that reached boundary. The retained limitation observes the left clamp and
  cleared velocity. A nonwrapping X 40 entry predicts 8 and correctly clamps to
  16 in both. Native output: `s1-native-wrap.txt.gz`.
- **SBZ3 slope-lock inlet:** the offscreen platform test below reaches actual
  standing, offscreen collision gating and Right-driven edge departure. Native
  expects retained standing; engine releases it. The on-screen control releases
  normally. The original parity assertion ran red; the named limitation passes.
- **Start/capsule clock phase:** actual Start pause reaches three paused
  iterations with frozen level time. Native `VBlank_Exit` still advances VInt
  three times; the bound engine counter remains unchanged. Fresh Start resumes
  the same counter owner, leaving the wrong modulo-eight phase for `Pri_Animals`.
  Native capture records VInt 631, 632, 633 at fixed level frame 181. Native
  parity `before + 3` ran red against `before`; the retained limitation and resume
  control pass. Output: `s1-native-pause.txt.gz`. This variant belongs to
  **1401M/2457S and 1948M/3402S**, not every capsule publication.

### Capsule timing and prepared art service

The supported capsule subtrick executes a real SLZ3 button landing through
animal release, departure and the actual results trigger. Declared entry omits
the fight/travel: native SLZ is zone 3 (engine logical 4), act 2, postboss event
routine 4, right camera limit `$2160`, local starting VInt 2, RNG `$12345678`.
Sonic descends at `($2200,$270)` with `vy=$100`; no button state is injected.

Independent native and engine runs agree for approach delays **0, 3, 8**:
button contact always occurs on local update 6. Animal counts at updates 70/210
are **9/22, 8/24, 9/22**. RNG values also agree: `$70DDF538/$0368ECB8` for the
first/third variants and `$66EB1278/$7545E978` for delay 3. Results occur at
**366, 345, 365**. Delay 3 costs three ticks but saves 21 afterward: **18 net
frames saved**. Delay 8 totals 373 and loses seven, rejecting the idea that any
delay helps. All three tests pass. Native observations are
`s1-native-capsule-{0,3,8}.txt.gz`.

Thus `S1_CAPSULE_RNG` is mixed: approach-delay manipulation is supported; the
actual Start-pause clock shift is unsupported. No manually supplied later VInt
is used to claim pause support. Separately, actual Start pause successfully
finishes an already prepared FZ art descriptor at three patterns per VBlank
while level time stays fixed; a fresh Start resumes gameplay. That is
`S1_PAUSE_PLC`, not evidence that the capsule clock advances.

### Interpreting the slope and unnamed notes

[3570M/5702S](https://tasvideos.org/5702S) names Tee-N-Tee's SBZ3 slope glitch;
[5121M/7988S](https://tasvideos.org/7988S) credits that strategy. The earlier
runner's [SDA explanation](https://speeddemosarchive.com/Sonic1.html) identifies
looking up while riding a moving platform until it becomes invisible, then
retaining a slope angle after leaving. The independently pinned native
`FBlock_Action` (`_incObj/56 SYZ, SLZ Floating Blocks and LZ Doors.asm:131-150`)
skips **all** `SolidObject` work when `obRender` is nonnegative. This includes
the stood-on release branch. The critical negative capability check acquires a
real standing contact before testing offscreen edge departure; its on-screen
control releases normally. This is distinct from the older Carretero slope-entry
zip described in 1847S.

`S1_UNSPECIFIED_ROUTE` is retained as `kind: source_note`, not as a tested family
or a third support verdict. Original publication provenance is retained:

- **795M/1437S, MZ1:** a bat desynchronization “freak accident” discovered a
  four-second shortcut. Earlier 1383S describes enemy-assisted pushing and
  reaching the moving platform earlier; it does not identify the later accident's
  collision/input sequence. Later 1847S describes a cave zip but does not establish
  that it is the same 1437S accident.
- **1937M/3378S, SLZ3:** “Major shortcut and route change together with a new boss
  strategy.” Neither the note nor its preceding route descriptions identifies a
  distinct local action that can be tested as this unnamed shortcut.
- **5121M/7988S, SBZ1:** “Used Daniel Hall's strat.” No input, entry or native exit
  is specified. The adjacent SBZ3 Tee-N-Tee credit is separately resolved above.
- **3570M/5702S:** the inventory's general route provenance is preserved; its
  concrete cog, slope, subpixel and zip claims have their own original family IDs.

No missing description is classified as engine unsupported.

## Rejected fixture interpretations

- The first native SBZ1 teleport had no loaded platform. Resetting the native
  object loader at the declared destination populated the native Obj52 and gave
  the same landing/carry/reverse-launch boundaries as the engine. That initial
  empty scene is not a negative capability result.
- S1 drowning cadence belongs to the fixed Obj0A sidecar. Seeding only the shared
  `DrowningController` timer did not reach the final-air tick. The corrected
  fixture seeds the sidecar's countdown after real underwater initialization.
- A paused VBlank services the prepared PLC descriptor; it cannot prepare the
  next queued descriptor. The regression therefore observes completion of the
  prepared FZ entry while active gameplay remains stopped, and separately checks
  that a fresh Start resumes gameplay. “Drain the whole queue during pause” was
  an invalid native expectation.
- The rising platform has native half-height 12, not 16; the initial too-high
  player never landed. Reached-state checks caught this before any verdict.
- SBZ3 is logical `(5,2)`. The earlier `(3,3)` setup was invalid, not unsupported.

- A native capsule probe initially selected native zone 4 (SYZ), not SLZ 3;
  this was corrected to native zone 3 / engine logical zone 4. The engine also
  needs the valid postboss right boundary `$2160`; omitting it clamps the player
  before the capsule. Reusing one mutated level for multiple button controls
  left the capsule already triggered; each retained case loads a fresh level.
- A 49-ring giant-ring control initially touched an unrelated ordinary GHZ ring.
  Moving the declared entry from Y 880 to Y 700 isolates the actual 49/50 gate;
  retained tests assert that ring count does not change.
- `updateCameraBounds` takes left/top/right/bottom, not width/height. Correcting
  this made the offscreen signpost setup meaningful. The retained test checks
  the four native horizontal-window boundaries with the object offscreen.

## Family matrix and exact scenarios

Every supported row has an executed boundary/control. Existing tests are reused
where their actual mechanism is sufficient. The original publication sets are
preserved in JSON; the links below identify the specific scenario evidence.

| Original ID | Verdict | Named executed scenario(s) and author source |
| --- | --- | --- |
| `S1_MOMENTUM` | **supported** | Release forward to retain above-cap airborne momentum — [783M](https://tasvideos.org/1383S), [1066M](https://tasvideos.org/1847S), [1937M](https://tasvideos.org/3378S), [2410M](https://tasvideos.org/3994S), [3570M](https://tasvideos.org/5702S)<br>Retain grounded running speed above the rolling horizontal cap — [783M](https://tasvideos.org/1383S)<br>Native cog slope launch (same completed scenario as `S1_COG`) — [2410M](https://tasvideos.org/3994S), [3570M](https://tasvideos.org/5702S) |
| `S1_AIR_DRAG` | **supported** | Hit a ceiling to leave the upward apex-drag interval earlier — [783M](https://tasvideos.org/1383S), [1066M](https://tasvideos.org/1847S), [1937M](https://tasvideos.org/3378S), [2410M](https://tasvideos.org/3994S), [2629M](https://tasvideos.org/4348S) |
| `S1_REVERSE_START` | **supported** | Use braking for a faster opposite-direction start — [795M](https://tasvideos.org/1437S), [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S) |
| `S1_SUBPIXEL` | **supported** | Change the next integer crossing with fractional alignment — [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S), [2410M](https://tasvideos.org/3994S), [2629M](https://tasvideos.org/4348S), [3570M](https://tasvideos.org/5702S) |
| `S1_ROLL_JUMP` | **supported** | Terrain roll jump restores standing radii and locks air steering — [1948M](https://tasvideos.org/3402S), [3570M](https://tasvideos.org/5702S) |
| `S1_ROLL_REJUMP` | **supported** | Rejump during the elevated frame after a roll-jump landing — [1948M](https://tasvideos.org/3402S) |
| `S1_DAMAGE` | **supported** | Survive intentional damage with native directional recoil and protection boundaries — [447M](https://tasvideos.org/907S), [783M](https://tasvideos.org/1383S), [795M](https://tasvideos.org/1437S), [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S), [1937M](https://tasvideos.org/3378S), [1948M](https://tasvideos.org/3402S), [2410M](https://tasvideos.org/3994S), [2629M](https://tasvideos.org/4348S), [3570M](https://tasvideos.org/5702S), [5121M](https://tasvideos.org/7988S) |
| `S1_PUSH_BLOCK` | **supported** | Use elevated side contact to gain forward displacement while pushing — [447M](https://tasvideos.org/907S), [783M](https://tasvideos.org/1383S) |
| `S1_OBJECT_PHASE` | **supported** | Jump off an activated rising block to remove its sink offset before ascent — [1401M](https://tasvideos.org/2457S) |
| `S1_EJECTION` | **supported** | A moving platform carries Sonic into terrain and collision ejects him against his low input velocity — [447M](https://tasvideos.org/907S), [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S), [1937M](https://tasvideos.org/3378S), [1948M](https://tasvideos.org/3402S), [2410M](https://tasvideos.org/3994S) |
| `S1_ZIP` | **supported** | Use an S1 shuttle to enter a wall, then hold Left for repeated rightward zip corrections and exit — [447M](https://tasvideos.org/907S), [795M](https://tasvideos.org/1437S), [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S), [1948M](https://tasvideos.org/3402S), [2410M](https://tasvideos.org/3994S), [2629M](https://tasvideos.org/4348S), [3570M](https://tasvideos.org/5702S), [5121M](https://tasvideos.org/7988S) |
| `S1_LEVEL_WRAP` | **unsupported** | Horizontal unsigned underflow transports to the right boundary — [2410M](https://tasvideos.org/3994S), [2629M](https://tasvideos.org/4348S), [3570M](https://tasvideos.org/5702S) |
| `S1_SLOPE_GLITCH` | **unsupported** | Offscreen moving-platform departure retains standing for the Tee-N-Tee slope-lock trigger — [3570M](https://tasvideos.org/5702S), [5121M](https://tasvideos.org/7988S) |
| `S1_COG` | **supported** | Launch from the SBZ2 running disc and clip forward on the next cog — [2410M](https://tasvideos.org/3994S), [3570M](https://tasvideos.org/5702S) |
| `S1_BOSS` | **supported** | Position the SLZ spikeball at the killing-contact edge to end the fight one update earlier — [1937M](https://tasvideos.org/3378S), [3570M](https://tasvideos.org/5702S) |
| `S1_CAPSULE_RNG` | **mixed** | Delay an actual capsule-button landing to change animals and reduce total completion time — [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S), [1948M](https://tasvideos.org/3402S), [2629M](https://tasvideos.org/4348S)<br>Start pause shifts the VInt phase used by capsule animals — [1401M](https://tasvideos.org/2457S), [1948M](https://tasvideos.org/3402S) |
| `S1_PAUSE_PLC` | **supported** | Start pause completes a prepared FZ art descriptor without advancing gameplay time — [1937M](https://tasvideos.org/3378S), [1948M](https://tasvideos.org/3402S) |
| `S1_WATER` | **supported** | Cross the surface on the final-air tick to survive and double upward exit speed — [447M](https://tasvideos.org/907S), [783M](https://tasvideos.org/1383S), [1066M](https://tasvideos.org/1847S) |
| `S1_POWERUPS` | **supported** | Break and collect speed-shoes or invincibility monitors to change movement/hazard behavior — [1937M](https://tasvideos.org/3378S) |
| `S1_EMERALD_ENTRY` | **supported** | Enter a giant ring at fifty rings, with the six-emerald gate — [5121M](https://tasvideos.org/7988S)<br>Collect the emerald and trigger the exit sparkle in each of the six native mazes — [5121M](https://tasvideos.org/7988S) |
| `S1_SPECIAL_COLLISION` | **supported** | Use four-cell contact ordering to select a special-stage bumper response — [5121M](https://tasvideos.org/7988S)<br>Jump immediately after side-only special-stage contact establishes ground — [5121M](https://tasvideos.org/7988S) |
| `S1_CAMERA_END` | **supported** | Trigger the signpost while vertically offscreen using the native horizontal window — [1401M](https://tasvideos.org/2457S), [2410M](https://tasvideos.org/3994S)<br>Final escape takes control at its native threshold and preserves the late logical input across dispatch — [447M](https://tasvideos.org/907S), [795M](https://tasvideos.org/1437S), [1066M](https://tasvideos.org/1847S), [1401M](https://tasvideos.org/2457S), [2410M](https://tasvideos.org/3994S), [3570M](https://tasvideos.org/5702S) |
| `S1_UNSPECIFIED_ROUTE` | **source note (excluded)** | Three unspecified notes plus original 5702S cross-reference; see provenance above. |

Selectors and the native observable result are explicit below. Test classes live
under `src/test/java`; scenario methods are unique. The original matrix retains
its detailed movement, damage, push and special-stage disassembly citations.


### `S1_MOMENTUM`

- **supported — Release forward to retain above-cap airborne momentum.** `TestTasS1Movement#releasingForwardPreservesAboveCapAirMomentum`. Two cases pass: release vx 0x900; Right vx 0x600; both continue real movement with vy=-0x4c8.

- **supported — Retain grounded running speed above the rolling horizontal cap.** `TestTasS1Movement#groundedRunningCanOutrunTheRollingHorizontalCap`. Six cases pass: actual Down enters roll from a declared valid high-speed running state, and two real grounded movement ticks distinguish neutral running, rolling, and forward-held running. The rolling below/exact/above-cap controls verify inertia versus horizontal velocity and actual displacement. See the follow-up table below.
- **supported — Native cog slope launch.** `TestTasS1TerrainVerdicts#cogJumpTimingSelectsForwardClipOrWallStop`, also listed under `S1_COG`. The original native-correlated pass at `ee4f11025` is reused; it was not rerun. Publication IDs remain 2410 and 3570.

The three children separately resolve airborne input-cap preservation, grounded
running/rolling speed, and a representative slope-oriented launch. The new
speed fixture declares the fast running entry; it does not claim every route or
object that first forces Sonic out of rolling. 783M's REV00 provenance remains
separate from the independently specified shipped REV01 expectation. Full loop
routes and movie synchronization are not claimed.

### `S1_AIR_DRAG`

- **supported — Hit a ceiling to leave the upward apex-drag interval earlier.** `TestTasS1ObjectVerdicts#ceilingContactSkipsTheRemainingApexDragFrames`. Ceiling and absent-ceiling controls pass: tick2 both vx1862, ceiling vy0 versus -344; tick4 ceiling vx1862,x421 versus vx1748,x420.

Enemy and monitor contact variants, and route-specific placements, remain separate edges.

### `S1_REVERSE_START`

- **supported — Use braking for a faster opposite-direction start.** `TestTasS1Movement#oneLeftFrameThenRightUsesBrakingInsteadOfOrdinaryAcceleration+simultaneousDirectionsHaveLeftThenRightOrder+brakingZeroBoundaryRetainsNativeBorrowCarryAsymmetry`. Fresh execution of the existing seven parameterized/single cases passed; native +128 reversal replaces ordinary +24 after two frames.

### `S1_SUBPIXEL`

- **supported — Change the next integer crossing with fractional alignment.** `TestTasS1Movement#fractionalAlignmentChangesTheNextIntegerCrossing`. Fractions 0 and 0xff00 produce 1/2 pixels and remainders62464/62208 through actual movement.

Specific publication landing/collision alignments are not replayed.

### `S1_ROLL_JUMP`

- **supported — Terrain roll jump restores standing radii and locks air steering.** `TestTasS1Movement#jumpUsesNativeRadiiAndOnlyRollingLaunchLocksAirSteering`. Both real terrain launch variants pass; rolling versus ordinary jump differ at the native radii/height/air-steering boundaries.

Sprite-supported roll launches use a different height path; the named tested subtrick is terrain launch.

### `S1_ROLL_REJUMP`

- **supported — Rejump during the elevated frame after a roll-jump landing.** `TestTasS1Movement#rollingJumpLandingHasOneFrameElevatedRejumpWindow`. Immediate and delayed controls pass after actual jump, descent and landing; only the immediate branch starts from the elevated landing window.

### `S1_DAMAGE`

- **supported — Survive intentional damage with native directional recoil and protection boundaries.** `TestTasS1Damage,TestS1SpikeDoubleHit`. Nine reused damage cases and one real LZ3 spike integration case passed freshly with zero skips, including lethal/unprotected, shield, equality-side and invincibility controls.

REV00 lava/ring-count bounce details and every historical damage shortcut are not separately replayed.

### `S1_PUSH_BLOCK`

- **supported — Use elevated side contact to gain forward displacement while pushing.** `TestTasS1PushBlock`. Eight reused cases passed freshly: actual ObjectManager solid checkpoint followed by Obj33 gives player +1 versus -1, block +1; facing/wall controls reject. Terrain response is a declared boundary fixture.

Enemy-assisted approach is an alternate entry to the tested vertical alignment, not separately replayed.

### `S1_OBJECT_PHASE`

- **supported — Jump off an activated rising block to remove its sink offset before ascent.** `TestTasS1PlatformVerdicts#steppingOffRisingBlockBeforeTimerExpiryRemovesItsSinkOffset`. Real landing on Obj61 at 0xa20,0x30c and 16 standing frames reaches Y 0x310. Actual jump yields Y 0x30c at rise mode 4; staying yields 0x310 at the same gate. Both cases pass.

The specific source is 2457S LZ1: jumping off the moving platform makes it rise sooner. Other original references describe approach/spawn timing and remain route variants, not claimed reproductions of their obstacle schedules.

### `S1_EJECTION`

- **supported — A moving platform carries Sonic into terrain and collision ejects him against his low input velocity.** `TestTasS1TerrainVerdicts#movingPlatformWallEntryProducesInputDependentReverseLaunch`. Both captured native controls match: carry to 0x1208, correction to 0x1203; Right vx 12 then 60, Left 756 then 1780; exit Right 0x120d versus Left 0x121f.

Monitor, door, crusher and slope-assisted entries are different ways to reach overlap. This is one complete moving-platform entry/ejection/exit.

### `S1_ZIP`

- **supported — Use an S1 shuttle to enter a wall, then hold Left for repeated rightward zip corrections and exit.** `TestTasS1ContinuousZip#s1ShuttleWallEntryProducesRepeatedReverseCorrectionsAndExit`. Two cases pass. Authored wall x[0x200,0x400),y[0x140,0x240); real S1 shuttle starts 0x430,0x200 subtype0x39 xflip. Real landing/100 neutral ticks carries Sonic 0x430->0x3b0. Left produces 0x3ca,0x3ea,0x40a:26/32/32px grounded corrections; left extent 0x401 clears wall. Empty wall ends 0x3ac with 0 corrections. No player embedding/velocity seeded after landing.

Authored solid-tile geometry declares local mechanism scope; not a native level route or synchronized movie. S1 rules, S1 carrier and real GroundSensor scans are exercised; the S3K worker supplied only the geometry idea.

The separate real SBZ1 native capture proves a bounded lower-speed reverse launch. Continuous zip is established by this longer authored wall, not inferred from that capture.

Ceiling/cardinal-angle quirks, every historic inlet and camera-limited route remain edge cases.

### `S1_LEVEL_WRAP`

- **unsupported — Horizontal unsigned underflow transports to the right boundary.** `TestTasS1TerrainVerdicts#currentLimitationHorizontalUnderflowClampsLeftInsteadOfNativeRight+nonUnderflowLeftBoundaryUsesTheSameClampInNativeAndEngine`. Native parity assertion executed red: expected 9767, actual 16. Retained limitation reaches prediction 0xfff0 then engine left clamp 16,vx 0. Nonunderflow x40->prediction8 correctly clamps16. Both retained cases pass.

The fixture declares a valid high-speed boundary entry; acquiring that speed is separately proven by zip. No REV00 verdict is inferred.

### `S1_SLOPE_GLITCH`

- **unsupported — Offscreen moving-platform departure retains standing for the Tee-N-Tee slope-lock trigger.** `TestTasS1SlopeVerdict#currentLimitationOffscreenPlatformReleasesStandingInsteadOfKeepingSlopeLockTrigger`. Real SBZ3 floating block at0x1a0,0x210 is acquired through landing; camera gate is independently checked and controlled; actual Right movement leaves its width. Native parity red: expected standing true, actual false. Retained limitation and onscreen control both pass; object remains alive, so this is collision dispatch, not deletion.

The camera pose is a declared local entry; looking up to obtain that pose and downstream slope travel are not replayed. The required retained-standing trigger itself is unavailable.

5702S/7988S credit was resolved through Tee-N-Tee SDA notes, https://speeddemosarchive.com/Sonic1.html. This differs from 1847S Carretero slope-entry zip.

### `S1_COG`

- **supported — Launch from the SBZ2 running disc and clip forward on the next cog.** `TestTasS1TerrainVerdicts#cogJumpTimingSelectsForwardClipOrWallStop`. Jump 3 reaches 0x2d9,0x718 before launch, vx 4531 on launch, lands 0x320,0x722 then corrects 28 px to 0x33c,0x715 on tick 8. Jump 4 stops at 0x315,vx 0. Both agree with independent REV01 native captures and pass.

Obj67 running-disc terrain, not Obj66 junction. Different cog routes/velocities are not required for this representative verdict.

### `S1_BOSS`

- **supported — Position the SLZ spikeball at the killing-contact edge to end the fight one update earlier.** `TestS1SlzSpikeballNativeContact#killingEdgeContactHappensBeforeMovementAndPreservesBothFractions`. Fresh existing test reaches actual boss defeated and ball EXPLODING at edge Y 0x280; Y 0x281 control is alive on the first update and defeated on the second. Velocities clear and fractions survive as native; ROM byte checks in the same five-case class pin comparison/order.

This names local final-hit placement/timing, not a certificate for complete GHZ/MZ/SYZ/SLZ/FZ fight schedules or the historical optimal cadence.

The separately executed FZ escape-cue tests only prove the attack callback and cue persistence after its timer, not geometric contact or a whole fight; they are supplemental component coverage.

### `S1_CAPSULE_RNG`

- **supported — Delay an actual capsule-button landing to change animals and reduce total completion time.** `TestTasS1CapsuleVerdicts#approachDelayChangesAnimalsAndCanSaveMoreTimeThanItCosts`. Native and engine delays 0/3/8 all land on button at local 6. Animal counts at 70/210:9/22,8/24,9/22; matching RNG 70ddf538/0368ecb8 versus 66eb1278/7545e978. Results 366/345/365; delay 3 totals 348 and saves 18 over 366; delay 8 totals 373 and loses 7. Three cases pass through actual results.
- **unsupported — Start pause shifts the VInt phase used by capsule animals.** `TestTasS1PausePlcVerdicts#currentLimitationNativeStartPauseFreezesCapsulePhaseClock`. Actual Start pause reached. Native assertion before + 3 executed red against unchanged before. Retained limitation observes3 paused iterations with frozen level time AND frozen VInt; fresh Start resumes both and exposes wrong modulo8 phase. Native capture advances631,632,633 at fixed level 181.

Mixed aggregates two executed binary subtricks. Unsupported Start-clock variant is attributed only to 1401M/2457S and 1948M/3402S, not to the delay-only publications.

Declared native phase2 and RNG0x12345678 are local fixture seeds. No observation row hydrates the engine. Original exact animal schedules/pausing duration are not claimed.

### `S1_PAUSE_PLC`

- **supported — Start pause completes a prepared FZ art descriptor without advancing gameplay time.** `TestTasS1PausePlcVerdicts#startPauseServicesPreparedFinalZoneArtWithoutRunningGameplay`. One test passes: prepared descriptor31 completes in ceil(patterns/3) paused ticks, active callback 0 and unchanged level time; queued descriptors stay unprepared; fresh Start runs active callback.

This proves already-prepared art service. It does not drain unprepared queued descriptors or claim a captured whole FZ transition. Capsule VInt-clock limitation is separate.

### `S1_WATER`

- **supported — Cross the surface on the final-air tick to survive and double upward exit speed.** `TestTasS1WaterVerdicts#lastAirSurfaceCrossingEscapesWhileLaterCrossingDrowns`. Real LZ underwater initialization and final fixed-sidecar timer reached. At surface+2 with vy=-0x380: dry,air30,vy=-0x6e0,alive; surface+6 remains wet and drowns. Both cases pass.

Tests native REV01 mechanism despite 447M REV00 provenance. Specific moving water-level phases and full air-budget routes are separate edges.

### `S1_POWERUPS`

- **supported — Break and collect speed-shoes or invincibility monitors to change movement/hazard behavior.** `TestTasS1ObjectVerdicts#fallingOntoMonitorGrantsItsPowerOnlyAfterBreakingAndIconRise`. Four cases pass: real falling contact breaks monitor; grant occurs after >=32 updates; shoes max 0xc00/accel 0x18, invincibility blocks real damage. Missed controls remain normal 0x600 and unpowered.

Uses real monitor objects in GHZ1 geometry; the original SYZ/LZ approaches are not replayed.

### `S1_EMERALD_ENTRY`

- **supported — Enter a giant ring at fifty rings, with the six-emerald gate.** `TestTasS1ObjectVerdicts#giantRingRequiresFiftyRingsAndDisappearsAfterSixEmeralds`. Four cases pass with unchanged ring counts:49 rejects,50/51 complete actual touch/flash,all 6 destroys ring. The entry is at Y 700 to exclude an unrelated GHZ ring.
- **supported — Collect the emerald and trigger the exit sparkle in each of the six native mazes.** `TestTasS1EmeraldVerdicts#eachNativeMazeEmeraldIsCollectedAndItsSparkleStartsExit,TestSpecialStageModuleConfig#sonic1ModuleConfiguresSixStagesAndEmeralds`. Six native-maze local pickup/update scenarios and the existing six-stage config check pass; every stage collects before the deferred exit trigger.

These are explicitly named giant-ring admission and six independent maze pickups. They do not prove the whole 100% campaign, six-act routing, accumulated inventory across transitions, or complete movie synchronization. The original campaign scope is retained here rather than silently replaced by a one-jump claim.

### `S1_SPECIAL_COLLISION`

- **supported — Use four-cell contact ordering to select a special-stage bumper response.** `TestTasS1SpecialStageContacts#lastSolidOfFourCellsOwnsBumperResponse`. Fresh full-update cases pass: native bumper vector -1267/-1267 when last solid is bumper; swapped wall suppresses bounce; nonsolid 0x28/0x3a retain bumper.
- **supported — Jump immediately after side-only special-stage contact establishes ground.** `TestTasS1SpecialStageContacts#sideContactWithoutFloorAllowsFreshJumpOnFollowingUpdate+airborneContactFramePressIsNotBufferedAndHeldJumpDoesNotRetrigger+emptyAndNonSolidCellsCannotCreateTheSideContactJumpWindow`. Fresh reused full-update scenarios pass with native vy=-0x656 for A/B/C; airborne/held/nonsolid controls reject. Eleven total cases in the reused class, zero skips.

Authored cells declare the collision mechanism; no input trace or precomputed contact result is injected. The notes name hitbox/immediate-jump behavior but do not specify every shortcut cell.

### `S1_CAMERA_END`

- **supported — Trigger the signpost while vertically offscreen using the native horizontal window.** `TestTasS1FinishVerdicts#offscreenSignpostTriggersOnlyInsideItsUnsignedHorizontalWindow`. Four cases pass withactual object offscreen:0/31 trigger, -1/32 do not. Bounds supplied as left, top, right, bottom.
- **supported — Final escape takes control at its native threshold and preserves the late logical input across dispatch.** `TestS1FzEscapeNativeInput`. Twelve freshly executed existing cases pass: before threshold retains player word, lock clears jump edge, rising/falling controls select Up/zero, fractions survive clamp; real next-player dispatch and restore-forward preserve forced look-up.

Offscreen signpost trigger and final input ownership are the named local outcomes. Movie length, every camera catch-up wait and full end sequence are not asserted.

## Completed focused validation

All commands ran here at base `c69447d77` with only the owned tests in progress,
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk`, `LUA_BIN=/usr/bin/lua5.4`, `-Dmse=off`,
and original absolute ROM path `$MAIN_WORKSPACE/s1.gen`.
The measurement-hazard table was read. ROM-backed cases actually executed;
all reported completed selections had **zero skips and zero errors**.
Production remained unchanged. No broad run, native trace sweep, desktop focus
or key injection was used. Parent owns integration validation and publication.

The exact selections below were recovered from each completed run's Surefire
`test` property. The first command passed 39/39. The second exploratory command
ran 40 cases: 37 passed, and three failed (native slope parity, native pause-clock
parity, and the invalid capsule postboss camera bound). Its 30 unchanged reused
cases all passed; it is not described as a green run. The third finalized the original
owned-source run at `ee4f11025`: **54/54 passed**, zero failures/errors/skips, **42.625 s** Maven
execution and zero queue wait. It includes the curated named limitations and
corrected capsule/continuous-zip fixtures. Counts are parameterized invocations.

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py -Dmse=off \
  '-Dtest=TestTasS1Movement,TestTasS1Damage,TestTasS1PushBlock,TestTasS1SpecialStageContacts' \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test
```

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestTasS1SlopeVerdict,TestTasS1ObjectVerdicts#ceilingContactSkipsTheRemainingApexDragFrames+giantRingRequiresFiftyRingsAndDisappearsAfterSixEmeralds,TestTasS1CapsuleVerdicts,TestTasS1PausePlcVerdicts#pausedVBlankShouldAdvanceCapsuleSpawnPhaseWhileGameplayWaits,TestS1SpikeDoubleHit,TestSonic1StaircaseWallCollision,TestS1SlzSpikeballNativeContact,TestSonic1FzBossCombatContact,TestS1FzEscapeNativeInput,TestSonic1FzBossEscapeHitCue,TestSpecialStageModuleConfig#sonic1ModuleConfiguresSixStagesAndEmeralds' \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test
```

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestTasS1ContinuousZip,TestTasS1Movement,TestTasS1TerrainVerdicts,TestTasS1WaterVerdicts,TestTasS1PausePlcVerdicts,TestTasS1ObjectVerdicts,TestTasS1PlatformVerdicts,TestTasS1SlopeVerdict,TestTasS1FinishVerdicts,TestTasS1EmeraldVerdicts,TestTasS1CapsuleVerdicts' \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test
```

The intermediate investigation also executed the unsigned-wrap native assertion
red (expected 9767, actual 16), then its explicitly named limitation. No failing
parity assertion remains in ordinary CI, and no test is disabled/skipped.
Other failed experiments were setup errors described above, not engine verdicts.
That run confirms the original retained owned source after those changes; the
additional momentum method is covered by the separate follow-up below.

The 30 additional reused cases comprise `TestSonic1StaircaseWallCollision` (1),
`TestS1FzEscapeNativeInput` (12), `TestS1SlzSpikeballNativeContact` (5),
`TestSonic1FzBossCombatContact` (8), `TestSonic1FzBossEscapeHitCue` (2),
`TestS1SpikeDoubleHit` (1), and the six-stage S1 module configuration method (1).
The FZ cue callback tests are supplemental; they do not claim geometric contact.
The SLZ edge-hit test itself supplies real contact, defeat and a one-pixel control.

Native probe sources, compressed observations and final verdict JSON remain in
`$S1_VERDICT_TASKDIR/`.
They are comparison evidence, not engine fixtures or canonical V5 replay traces.
Consumed Maven diagnostics are removed after inspection. The local tested commit
is supplied in the handoff and in every JSON record's `evidence_commit`.


## Bounded grounded-momentum follow-up

The remaining source claim is [783M/1383S](https://tasvideos.org/1383S), cached
`s1-1383S.txt:172`: grounded rolling caps horizontal
speed at 4096, whereas a running state can retain higher speed if forward is
released. This is a distinct claim from the existing airborne-input-cap test.

Independent oracle is the same immutable S1 pin and shipped `FixBugs=0`:
`Sonic_MdNormal` calls `Sonic_Move` before `Sonic_Roll`, so the tick that enters
roll through Down still moves with the running velocity.
`Sonic_CheckDpadLetGo` (`_incObj/01 Sonic.asm:499-513`) subtracts `$C` while
running with no direction;
`Sonic_RollSpeed` (`_incObj/01 Sonic.asm:767-812`) subtracts `$6` on the following
rolling tick. `Sonic_AngledRollSpeed` (`_incObj/01 Sonic.asm:829-875`) clamps X
velocity to `$1000` but retains larger inertia.
`Sonic_MoveRight` (`_incObj/01 Sonic.asm:705-727`) caps forward-held running at `$600`.

The fixture first reaches GHZ1's real flat floor at `(384,940)` and uses an
ordinary Right frame to acquire running. It then declares only a valid fast
running speed and zero fractions. Two measured ticks use actual input: neutral,
Down, or Right. No state or speed is injected between them. Every tick asserts
actual position/fraction, speed, grounded state and roll transition; the final
state also checks angle zero and absence of object support. These are short
movement executions, not assertions about an existing branch.

| Declared entry inertia | Input on both measured ticks | Second-tick inertia | Second-tick X speed | Final centre X / fraction |
| --- | --- | --- | --- | --- |
| `$1800` | Neutral (running) | `$17E8` | `$17E8` | `431 / $DC00` |
| `$1800` | Down (actual roll entry) | `$17EE` | `$1000` | `423 / $F400` |
| `$1800` | Right (running cap control) | `$600` | `$600` | `396 / $0000` |
| `$1011` | Down (below roll cap) | `$FFF` | `$FFF` | `416 / $0400` |
| `$1012` | Down (at roll cap) | `$1000` | `$1000` | `416 / $0600` |
| `$1013` | Down (above roll cap) | `$1001` | `$1000` | `416 / $0700` |

All six independent native expectations passed: **6 tests, 0 failures, 0 errors,
0 skipped**. Maven execution took **40.770 s**, with zero queue wait. The exact
focused command was:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
python3 tools/testing/maven_queue.py --lean -Dmse=off \
  '-Dtest=TestTasS1Movement#groundedRunningCanOutrunTheRollingHorizontalCap' \
  "-Dsonic1.rom.path=$MAIN_WORKSPACE/s1.gen" test
```

This closes the grounded rolling-speed child as **supported**. The existing cog
launch provides the slope child under `S1_MOMENTUM` using its completed original
test and native captures. No unchanged cog test or broad selection was repeated.
The JSON assigns the new child's evidence to the follow-up commit and preserves
`ee4f1102549675cda3e082a90b23c74a98da1c42` for every unchanged child's evidence.
The source-note record describes the original undefined route provenance and
remains separate from the tested-family inventory.
