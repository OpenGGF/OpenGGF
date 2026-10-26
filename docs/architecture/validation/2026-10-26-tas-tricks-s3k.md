# S3&K TAS trick regression obligations — 2026-10-26

Historical route-level assessment. The later
[executed S3K verdict matrix](2026-10-26-tas-verdicts-s3k.md) supersedes the
partial/unverified dispositions below using the user’s representative-mechanism
standard. It preserves all 35 original family IDs, distinguishes supported
children from exercised current limitations, and keeps publication-route limits
separate. The original notes and native derivations here remain evidence.

Implementation base: `c50876b184612428ccaf22763412f88ffe505432`.
Worktree: `/mnt/LinuxData/T3/worktrees/OpenGGF/t3-ca0b90be`, local branch
`feature/ai-tas-s3k-tests-20261026`. Scope is tests and this matrix only;
the parent owns integration, shared documentation and combined validation.

The completed audit is the input, not a new website census:
`$HOME/scratch/agent-tmp/codex/tasvideos-trick-audit-2026-10-26/audit.json`,
`games[key=s3k].tricks[support_category=partial]`, plus its saved original
`s3k-<submission>S-notes.txt` files. All **21** partial families appear once below.
Links identify the original publication/submission; this task read the saved notes.

## Disposition and claim boundary

- **Exact regression:** reproduces the identified published trick's entry,
  input and native exit, including a discriminating control. **0 families**.
- **Component-only evidence:** executable assertions cover a named constituent
  mechanism, with explicit limits. **14 families**. A synthetic component
  boundary can have an exact disassembly-derived oracle without reproducing
  the TAS's way of reaching it.
- **Native fixture pending:** the exact setup/input/oracle is missing and
  adjacent implementation/tests do not close the obligation. **7 families**.
- **Observed mismatch:** a reached, independently specified native expectation
  fails. **0 established families**; validation outcomes are recorded below.

All 21 remain **partial/unverified at publication-route scope**. No full movie,
campaign, ring total, or hardware-load synchronization is certified. Shared
physics assertions, flag setters and test-file presence are not exploit oracles.
Separate audit blockers (Reset, pointer aliasing, horizontal underflow and other
memory corruption) are not reclassified by these tests.

## New executable boundaries

The three new classes are ROM-free. They call the production conversion,
special-stage manager and sidekick controller. Their expected values were derived
before execution from locked-on disassembly pin
`8b2cf3c4178f2382f844da67798de4befc50c5e0`, read with immutable `git show` from
`$HOME/code/projects/OpenGGF/docs/skdisasm`; the dirty reference worktree
was not changed. These are retail `FixBugs=0` contracts.

**B1 — `com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereRingConversion`**
has three tests for [3521M](https://tasvideos.org/3521M) /
[5639S](https://tasvideos.org/5639S), “Turning spheres into rings without
surrounding them.” A single red loop occupies X=3..11, Y=3..9 with a notch
X=5..9, Y=3..6 and touched cell (7,6). Walking either way first turns north,
so `Red_Loop_Processes_Loop` (`sonic3k.asm:13204-13242`), including
`Red_Loop_Direction_Changed`, selects (7,5), outside the polygon. Four connected
outside blues, including diagonal-only links, become rings;
`Blue_To_Ring_Next_Ring_B` (`sonic3k.asm:12952-12995`), including the subsequent
`Blue_To_Ring_Next_Ring_R` pass, leaves a disconnected blue, a geometric-interior
blue and distant red unchanged. Breaking the red loop suppresses conversion.
A convex square control converts its center but cannot flood across an empty gap.
The reached-state assertions include conversion admission, exact blue decrement
and actual changed cells. This synthetic notch is **not** the publication's stage
1/2/3/4/6/9/13/14 layout or traversal.

**B2 — `com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereMotion`**
has six invocations for the same submission's red-turn, bumper and emerald
positioning mechanisms. The fixture seeds an initialized local gameplay boundary,
installs no-device audio, omits entry fades/art/renderer, and steps the real
manager. `sub_9580` (`sonic3k.asm:11950-12115`) returns before collision for
15 rotations of four angle units; the sixteenth reaches `$40` and moves X
from `$1000` to `$0FF0` on the same update. The aligned no-turn control fails
on the red sphere. Actual bumper contact first reverses displacement without
changing stored velocity. A separately seeded different-cell unlock writes
`-$1000`, skips both movement and red collision once, then resumes collision
on the next update. This does not assert that the movie's jumping/subpixel
sequence reaches the seeded turn or unlock state.

The two final-sphere cases start at X=`$1000`, Y=`$10DF`/`$10E0`, angle 0,
velocity `$1000`, one blue at (16,17), and one sphere left. Actual collision and
queue processing reduce the count to zero and start the clear timer at 2.
`loc_84C2` (`sonic3k.asm:10773-10789`) orders movement, sphere processing and
clear processing. `sub_9B62` / `loc_9BA6` (`sonic3k.asm:12564-12625`)
advance the timer by 2, then 3, then 4: after 84 further updates it is `$101`
and no emerald has appeared; the 85th places exactly one emerald and sets
velocity `$800`. The one-subpixel input difference yields indices `$70`/`$90`
and remaining distances `$87F`/`$780`, independently checking native round-to-cell
placement. Entry buffering, non-cardinal sine lookup, art-load latency, emerald
collection and the final results clock are outside this fixture.

**B3 — `com.openggf.sprites.playable.TestTasS3kRespawnBypass`** has seven
invocations associated with [4086M](https://tasvideos.org/4086M) /
[6240S](https://tasvideos.org/6240S), whose notes identify Tails respawn as
essential to the AIZ2 ring route. `Tails_Catch_Up_Flying` / `loc_13B50` /
`loc_13B78` (`sonic3k.asm:26514-26560`) accepts manual jump or Start before
testing the 64-frame cadence and leader's object-control bit. The entry is
routine 2, normal gravity, leader at (`$1234`,`$0400`) with object control
blocking ordinary return, Tails at (`$0200`,`$0500`) with nonzero velocities.
At level frame 1 or 64, jump/Start reaches routine 4, (`$1234`,`$0340`), zero
velocities and native `$81` object control. Direction-only controls test both
the off-cadence and controlled-leader rejection. This closes a local test gap:
the older ABC test used frame 0 and could not distinguish manual bypass from
the ordinary cadence trigger. It does not establish where the movie loses or
retrieves Tails, reverse-gravity return, subsequent flight, or ring collection.

## Exhaustive per-family matrix

Every pending fixture below requires a verified original S3&K ROM, native
320×224 viewport, explicit character/team, exact 16.16 player coordinates,
velocities and status at entry, and native controller samples. Capture the
owning clock (`Level_frame_counter`, V-int, special-stage update or object scan)
and relevant object identities/camera state. A named zone/interaction is an
acquisition target, **not** a fabricated coordinate or an assertion that the
movie reaches it. Preserve a bounded lead-in and an adjacent failed-input or
boundary control. Use captures only as comparison/oracle data, never gameplay
hydration from trace rows.

| Stable audit ID | Disposition; executable evidence | Concrete remaining native fixture / exit obligation |
|---|---|---|
| `s3k-movement` | Component-only; E13: real AIZ1 rolling loop traversal from explicit seeded speed. | [156M / 261S](https://tasvideos.org/261S), Sonic AIZ1: acquire the six-rev spindash input and exact release position, then compare charge, release velocity, slope launch, grounded/air transitions and exit coordinate per level tick. Five-rev or one-tick-late release is the control. The seeded loop regression does not test charging or all damage boosts. |
| `s3k-zip` | Native fixture pending. Ordinary loop traversal is not a penetration regression. | [2468M / 4087S](https://tasvideos.org/4087S), solo Tails DEZ1 extending stairs: capture the stair phase, contact geometry and subpixel entry just before the small zip. Require native embedded-to-exit trajectory and exact X/Y correction per collision tick; shift entry one subpixel/frame to reject the zip. Separate from coordinate underflow. |
| `s3k-stair` | Native fixture pending. | 2468M / 4087S, Tails HCZ1 before its horizontal-stop object/terrain edge; acquire both bottom sensor depths, X/Y speed and fixed-point position. Compare the native landing-depth branch, first missed attachment and wall entry. A depth on the opposite side of the native threshold must land. LRZ1/2 variants remain separate cases within this family. |
| `s3k-offscreen` | Component-only; E10 releases pushing when visibility is lost; E11 unloads the real AIZ bridge at camera X=`$480` and admits it again. | 2468M / 4087S, Tails SOZ2 offscreen sprite passage: capture exact obstacle identity, render bit, camera, player entry and loading window. Require passage while offscreen, contact when brought back onscreen, and separate new-contact from existing-rider behavior. Normal unload/reload does not prove the TAS passage. |
| `s3k-camera` | Component-only; E12 preserves delay 32 under scroll lock and checks two-pixel biased scrolling; E9 checks AIZ→HCZ handoff camera Y=`$2B6`. | [2741M / 4514S](https://tasvideos.org/4514S), Sonic+Tails LRZ2: acquire the spindash-delayed camera during the leftward zip and flame dash immediately after the wrap. Require native camera maximum/lock transitions and release frame, against omission or a one-tick shift of the dash. This does not reclassify the separate coordinate-underflow blocker; low-camera boss spawns need another fixture. |
| `s3k-slope` | Native fixture pending. | 2468M / 4087S, Tails CNZ1 wheel/monitor departure into CNZ2: capture attachment, monitor contact and departure path; require retained adhesion through the documented departure, subsequent anomalous floor/wall movement and the ordinary-jump clear control. ICZ block, LBZ elevator, FBZ capsule/mesh and SOZ sources need their own departure/delete witnesses; one flag does not certify them. |
| `s3k-coop` | Component-only; E1 checks exact pickup windows, live manual-P2 flight, attachment and release values. | 4086M / 6240S, Sonic+Tails AIZ2 ring route: acquire both controller lanes before carry/pickup and offscreen assistance. Require carrier/passenger positions, attachment lifetime, shared ring ledger and camera-relative P2 admission; offset pickup outside the unsigned contact window. Boss quick-hit and cutscene-pinning variants remain additional route obligations. |
| `s3k-respawn` | Component-only; B3 now discriminates off-cadence manual bypass; E2 checks ordinary 64-frame admission and controlled-leader rejection. | 4086M / 6240S, AIZ2: capture P2's loss/return with exact leader camera, routine and raw input, and compare return frame/coordinate through resumed flight and ring contact. The same local state with no ABC/Start is the control. B3 seeds routine 2 and does not prove offscreen loss creates it. |
| `s3k-slots` | Component-only; E9 allocates results below capsule slot 30 and verifies a lower-slot cutscene child waits until the next scan. | [6890M / 10042S](https://tasvideos.org/10042S), Sonic AIZ2 at the leftward camera crossing X=`$4980`: notes describe braking dust occupying slot 6 so the cutscene button receives slot 8. Acquire the pre-allocation population and compare admitted identity/slot and first-update scan; omit braking as control. This tests ordinary occupancy/allocation only, not the resulting bridge pointer exploit. No stale-pointer or neighboring-RAM behavior is inferred. |
| `s3k-act` | Native fixture pending; E9's normal handoff is adjacent evidence only. | 2741M / 4514S, Sonic+Tails LBZ tally/transition death: capture the last-hit/tally lead-in, death tick, checkpoint, level/event/timer state and next act initialization. Assert the native restart destination and timer reset against death one tick earlier/later. MGZ both-act skip, ICZ omission and DEZ transition death each still need their own branch fixture. |
| `s3k-glide` | Component-only; E3/E4 assert floor-before-wall response, real sensor ordering/fractions, signed climb bounds and retail idle-distance clobber. | [2593M / 4289S](https://tasvideos.org/4289S), Knuckles ring attack AIZ1 at 0:09 / movie frame 1060: acquire the cliff touch that loads/charges the Rhinobot, then the superglide off it toward the high ring monitor. Compare native launch/glide velocity and monitor reach; miss the badnik contact by a frame as control. Also acquire the documented thin/deep-floor and high-wall variants separately. |
| `s3k-flight` | Component-only; E5/E6 assert activation gravity, flap phases, odd-tick timer and camera-minimum clamp. | 2468M / 4087S, Tails MGZ2: enter before the early terrain-rise trigger, preserving camera and event phase. Verify flight through the roof, native 3-pixel/eight-frame upward drag and seven offscreen hits, with an untriggered/late-rise control. Ordinary flight timing alone does not certify this route. |
| `s3k-nolr` | Native fixture pending; explicitly a campaign obligation. | [6106M / 9169S](https://tasvideos.org/9169S), Knuckles from original start through completion. Acquire the full native movie, verification/reset provenance and per-act checkpoints; assert absence of Left/Right throughout and real route progression/ending with native clock observations. Wall recoil, balancing, springs and momentum need reached-state checks along the campaign. A parser-only input check cannot close this. The audit's Reset/underflow blockers remain. |
| `s3k-shields` | Component-only; E7 tests lightning's `-$580` impulse/latch and underwater steep bubble landing's X=`$2D4`, Y/ground=`$1E6`. | [5453M / 8453S](https://tasvideos.org/8453S), Sonic 100% HCZ1: acquire the bubble shield and piranha contact used to leave the water for two emeralds. Compare native bounce impulse, water transition and giant-ring reach; no shield/one-tick shift is the control. Exact input and contact coordinates remain to be extracted. Fire activation and all published bubble/lightning collision boosts are not established by E7. |
| `s3k-sphere-ring` | Component-only; B1 has an exact local outside-fill oracle, not the movie layout. | 3521M / 5639S, Knuckles special stage 1, movie frames 1100–1600 and 3300–3600 (notes' numbering): acquire original layout, visited-red path, touched index and input lead-in. Compare full changed-cell set, sphere count and conversion update; break the final red-path edge as control. Repeat separately for stages 2/3/4/6/9/13/14 named in the notes. |
| `s3k-sphere-motion` | Component-only; B2 checks manager collision-tail ordering and bumper dynamics. | 3521M / 5639S, Knuckles stage 5, frames 29951 and 30329–30490: acquire the one-frame turn delay and three jump windows, exact coordinates/fractions/rate/jump phase. Require survival over red/bumper and the final blue touch; each jump shifted one frame must produce the native miss/failure. Stage 11's repeated same-bumper route needs a separate capture. |
| `s3k-sphere-timing` | Component-only; B2 reaches last-sphere clear, pins 85 further updates and neighboring emerald rounding cases. | 3521M / 5639S, stage 1 entry around frame 903 and reverse/emerald sequence around 3150/3666; also [5453M / 8453S](https://tasvideos.org/8453S). Capture both input-before-load and one/two-frame-delay controls, hardware lag/input sampling, last sphere and emerald collection. Compare first accepted input, turn cadence, spawn coordinate and complete exit clock. B2 omits entry/load timing and seeded-state reachability. |
| `s3k-ngplus` | Native fixture pending. Engine save support is not emulator verification-state equivalence. | [5654M / 8501S](https://tasvideos.org/8501S), Sonic NG+ title/data-select→AIZ1: acquire original verification movie/save provenance, selected slot, emerald masks, lives/rings, spawn and startup phase. Reconstruct through engine save/title owners, compare first controllable native state and powered eligibility; fresh-save control must lack those powers. Do not import an arbitrary emulator RAM state. |
| `s3k-rings` | Component-only; E8 uses genuine ROM placements/allocation, delayed owner award, native P2/shared ledger and exactly-once rewind replays. | [2593M / 4289S](https://tasvideos.org/4289S) Knuckles ring attack and 4086M / 6240S Sonic+Tails NG+: acquire per-zone ring identity ledgers, giant-ring flags and entry state, starting with AIZ2 offscreen P2 collection. Assert collection only inside native vertical/camera eligibility and no loss/despawn/double award. Totals 8,577/13,771 require the full respective routes; the latter notes describe only through HCZ1. |
| `s3k-rng` | Native fixture pending. Slot ordering does not establish RNG schedule. | 2741M / 4514S, Sonic+Tails end of SSZ: the notes identify a pause to manipulate DEZ1 Red Eye and a possible three-frame saving if Red Eye/Death Ball were replayed. Capture initial RNG seed, universal/level clocks, exact pause input and every consuming object in scan order through the boss-pattern selection. Compare the resulting pattern with one-tick/no-pause controls. No seed or timing sequence is inferred from deterministic Java behavior. |
| `s3k-boss` | Component-only; E14 asserts MHZ pre-arena health floor, flash duration, attacker response and rejection of another hit during flash. | [3422M / 5427S](https://tasvideos.org/5427S), Knuckles MHZ2 at 0:41.59 / movie frame 123464: the notes claim six hits in six frames before the scrolling fight. Capture glide entry, hit-proxy exposure and health/flash each tick. Require that native hit sequence, not just final HP; a missed glide contact is the control. SOZ open/close invulnerability, LBZ paired hits, DEZ solidity and delayed capsule variants remain distinct fixtures. |

## Focused validation selection

No broad smoke or release-only full suite was launched. Java 25 is selected
explicitly; the parent's Lua prerequisite is `/usr/bin/lua5.4`, although these
focused tests do not invoke Lua or guards. No shared-desktop rendering is used.

The original ROM used for E1/E8/E9/E13 is
`$HOME/code/projects/OpenGGF/s3k.gen`, CRC32 `63522553`, SHA-1
`CFBF98C36C776677290A872547AC47C53D2761D6`, verified by reading its bytes.
No ROM was copied or linked. ROM-free classes need no disassembly at runtime.
E3/E4 parameterized methods include KiS2 comparison controls; those do not load
a KiS2 ROM and are not extra S3&K TAS claims.

The first command selects B1/B2 in full:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off '-Dtest=com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereRingConversion,com.openggf.game.sonic3k.specialstage.TestTasS3kBlueSphereMotion' test
```

The second command selects B3 and the following exact existing method selectors.
`+` is Surefire's method union; the selection is intentionally bounded rather
than a run of every method in the broader shared test classes.

| Key | Fully qualified selector |
|---|---|
| B3 | `com.openggf.sprites.playable.TestTasS3kRespawnBypass` |
| E1 | `com.openggf.sprites.playable.TestTailsCarryController#manualGrabUsesExactUnsignedContactWindowsAndImmediateNativeAttachment+jumpReleaseUsesExactNeutralAndDirectionalNativeValues+manualP2FlightUsesLiveCarryStateForAirAndUnderwaterFlightUpdates` |
| E2 | `com.openggf.sprites.playable.TestSidekickCpuControllerCatchUpFlight#catchUp64FrameGateTriggersOnlyEvery64Frames+catchUp64FrameGateSuppressedWhenSonicIsObjectControlled` |
| E3 | `com.openggf.sprites.managers.TestKnucklesGlideCollisionContract#s3kFloorWinsAfterWallAndClearCeilingAtCorrectedCoordinates+realGroundSensorInnerCornerCollectsWallAndFloorBeforeNativeResponse` |
| E4 | `com.openggf.sprites.managers.TestKnucklesWallClimbVerticalContract#upperClampUsesSignedWordsAndWrappingBypass+idleUsesRadiusAndRetailDistanceClobberOnlyOnS3k` |
| E5 | `com.openggf.sprites.managers.TestPlayableSpriteMovementTailsFlight#nativeS3kMainTailsActivatesFlightOnAirborneJumpRepress+activationFrameKeepsNormalAirGravityBeforeFlightGravityStarts` |
| E6 | `com.openggf.sprites.managers.TestTailsFlightController#timerDecrementsOnlyOnOddRomVisibleFrames+readyJumpStartsFlapButCurrentFrameStillAddsEight+flapStateSubtractsTwentyWithoutReadyGravity+upwardVelocityClampsWithinTenHexPixelsOfCameraMinimum` |
| E7 | `com.openggf.sprites.managers.TestPlayableSpriteMovement#s3kLightningShieldClearsJumpHeightLatchLikeRomJumpingByte+s3kBubbleShieldSteepLandingCopiesPostBounceYSpeedToGroundSpeed` |
| E8 | `com.openggf.tests.TestS3kAttractedRingAwardPhase#acceptedControlOneTouchAwardsOnlyWhenRingOwnerRuns+actualNativeP2AcceptanceAwardsSharedLedgerOnlyOnceInMainOwnerPass+acceptedPendingWorldRestoresAndAwardsExactlyOnceAcrossTwoForwardReplays` |
| E9 | `com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects#aizResultsUseAllocateObjectBelowTheCapsuleOwner+controllerUsesAllocateObjectForCutsceneKnuckles+hydrocityTransitionKeepsPrePhysicsCameraTargetForHandoffFrame` |
| E10 | `com.openggf.level.objects.TestSolidObjectManager#sonic3kOffscreenSolidPushReleasePublishesNativeWalkRunAnimationWord` |
| E11 | `com.openggf.level.objects.TestObjectManagerCounterBasedDynamicUnload#s3kAizDrawBridgeUnloadsAndClearsItsRespawnLatchAtTheRomTail` |
| E12 | `com.openggf.camera.TestCamera#scrollLockPreservesPendingHorizontalHistoryDelay+testCameraSlowScrollWhenBiasNotDefault` |
| E13 | `com.openggf.tests.TestS3kAiz1SpindashLoopTraversal` |
| E14 | `com.openggf.game.sonic3k.objects.bosses.TestMhzEndBossNativeDamage#preArenaDamageClampsAtTwoButStillFlashesAndStopsTheAttacker+anotherAttackDuringTheFlashCannotConsumeTheTerminalCount` |

Reproduce the second command with the same expanded selector and ROM arguments
without retyping the long list (`$HOME` names the original local ROM owner):

```bash
TAS_S3K_SELECTORS=$(python3 - <<'PY'
import re
from pathlib import Path
doc = Path('docs/architecture/validation/2026-10-26-tas-tricks-s3k.md').read_text()
print(','.join(re.findall(r'^\| (?:B3|E\d+) \| `([^`]+)` \|$', doc, re.M)))
PY
)
JAVA_HOME=/usr/lib/jvm/java-25-openjdk python3 tools/testing/maven_queue.py -Dmse=off "-Dtest=$TAS_S3K_SELECTORS" "-Ds3k.rom.path=$HOME/code/projects/OpenGGF/s3k.gen" test
```

## Results and rejected approaches

- B1/B2 completed successfully on the base plus the new tests: **9 tests,
  0 failures, 0 errors, 0 skips**. Maven execution reported 1:34 including
  the clean worktree compilation; queue waiting preceded execution and is not
  included in that duration.
- B3/E1–E14 completed successfully on the same base plus additions: **49 tests,
  0 failures, 0 errors, 0 skips** across all 15 selected classes. Maven execution
  reported 35.015 seconds. Together the two focused commands ran **58 invocations**,
  including **16 new invocations** and 42 reused assertions/cases. These are
  bounded focused passes, not full-suite or full-movie passes.
- Matrix checks matched all 21 partial audit IDs exactly once, resolved the local
  documentation link, and confirmed every fully qualified selected method exists.
  The strict disassembly citation check passed all 13 citations at the pinned
  revision. One bounded independent review found no correctness issue.
- Rejected the existing assertion-free `testBasic3x3SquareConversion` as
  positive outside-fill evidence; B1 asserts actual conversion and controls.
- Rejected treating frame-0 manual respawn as a cadence-bypass proof; B3 uses
  frame 1 and independently blocked cadence controls.
- Rejected a trivial no-Left/Right parser test as campaign coverage, save
  serialization as native NG+ equivalence, and broad physics tests as zip proof.
- No production changes, oracle resnapshots, disabled TODO tests or fitted
  native inputs. Missing movie state remains an explicit fixture obligation.

The measurement-hazard table in
[`briefing-trace-rounds.md`](../../agent-workflow/briefing-trace-rounds.md#measurement-hazards--all-produce-plausible-output)
was read before reporting: completed command identity, class/method selection,
skips and exact assertions determine the result. Counts do not establish a
larger route, the original TAS frame numbers are not engine clocks, and entry
state seeding does not prove that a full gameplay route can reach that state.
