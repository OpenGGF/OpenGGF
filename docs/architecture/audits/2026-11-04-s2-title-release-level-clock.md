# S2/KiS2 title-release level clock — 2026-11-04

## Scope and native owner

Base: wave7 candidate `2c27b9cb7`; stopped implementation `72e387b58`.
The lane merges both histories and preserves TraceChaser `10f1e09` and its guards.
No fixture, timing-port, title pass-count, solo recorder bootstrap, host freeze,
camera-fraction or carried-entry implementation changes belong to this fix.

`Level` clears `Level_frame_counter` before the title/load loops
(s2.asm:4772-4773). `Level_MainLoop` (s2.asm:5088-5095) increments it after the V-int wait and
before the object pass. The locked title path uses the shared engine step,
which advanced LevelManager's mirror, while release already reset SpriteManager's
mirror. Reset both at release for the S2 locked-step capability. KiS2 delegates to
the same title provider. Other providers retain their existing LevelManager clock.
The V-int and object-dispatch clocks are independent and are not reset.
The store lives in the focused `TitleCardLevelClock` collaborator: the original
helper placement grew the lifecycle to 260 effective lines against its 253-line
budget. Extraction preserves that budget without changing the store or its order.

The four-case lifecycle regression covers fresh-boundary/tail release and native/
other providers, with arbitrary nonzero title debt. Before the implementation,
both native cases fail `expected 0, actual 23`; both other-provider cases pass.
With the implementation all cases pass and the next level increment reads one.

## Source-clock audit

| Consumer | Native read and engine source |
| --- | --- |
| ObjC8 `loc_3D3A4` | loc_3D3A4 (s2.asm:82596-82616) reads the HIGH byte of the level word, masks 3 and biases the angle; Crawl reads LevelManager. |
| `Obj44_BumpCharacter` | Same high-byte angle bias (s2.asm:45115-45127); bumper reads LevelManager. |
| `SwScrl_ARZ` | SwScrl_ARZ (s2.asm:17676-17726) masks the level word by `$3F` to index the shake bytes and modify the camera copies; ParallaxManager supplies LevelManager's word. |
| Obj68 spike | Initial word shifted for phase/direction (s2.asm:53749-53757); action reads the low byte and masks `$3F` (s2.asm:53830-53833); both engine owners use LevelManager. |
| Obj70 cog / Point Pokey | Low-byte frame gates, not the object update argument; their engine owners read LevelManager. |
| S2 zone events | SpriteManager's gameplay word, held at zero while the title is locked; already reset before this change. |
| Tails CPU | Preferred ordinary cadence is SpriteManager's ProcessSpritesEpoch; LevelManager is a fallback. Native follow/jump gates read the word/low byte (`TailsCPU_Normal_FilterAction`, s2.asm:39356-39374). |
| Obj89 pillar | Its update argument is V-int (`Vint_runcount+3`); rumble and idle shake consume that clock. No direct level-word read. |
| Oscillators | The frame argument deduplicates updates; title suppression holds the stateful table. No oscillator algorithm/suppression change here. |

Competition monitor contents also read the level word. CNZSlotMachineManager's
ordinary owner instead prefers ObjectManager V-int; its level-counter fallback
is only for a missing object manager. This release-time correction does not
certify reads made *during* locked title passes.

## ARZ2 attribution: rendering controls a solid gate

The matched instrumented runs reproduce the full baseline and candidate counts.
They record actual FrameComparison rows and pillar state before/after each
checkpoint; no values are written into gameplay. RNG, V-int, object dispatch and
both actors' checkpoint input positions/velocities agree at the origin.

The level-word offset changes `SwScrl_ARZ`'s camera-copy shake. The first shake
difference is row 4419. The first compared state difference remains row 4433.
At the end of the **previous** iteration, row 4432:

| Observation | Baseline | Corrected |
| --- | --- | --- |
| Level word | `$115F` (4447) | `$1145` (4421), matching SpriteManager/ROM |
| Ripple index | `$1F` | `$05` |
| Shake Y | 3 | 2 |
| Pillar Y after update | `$0502` | `$0502` |
| Camera Y copy | `$0403` | `$0402` |
| Relative pillar Y | 255 | 256 |
| BuildSprites visibility | Inside | Outside |

`BuildSprites_ApproxYCheck` rejects relative Y >= 224+32
(s2.asm:30598-30610). The native render bit is written at that iteration's tail;
the next `SolidObject_OnScreenTest` reads it and skips new contact when clear
(s2.asm:35338-35346). It is the **pillar's** bit, not Tails' bit.

At row 4433 / V-int `$B0FC`, both runs enter the right pillar update at Y `$0502`,
with Tails `(x,y)=($2B5E,$04C0)` and x/g speed `$000C`. The old checkpoint moves
Tails to `$2B4D` and clears both speeds; the corrected checkpoint leaves them
unchanged. Both then raise the pillar to `$0501`. This follows
`Obj89_Pillar_Sub0` (s2.asm:65451-65466), whose solid call precedes the rise, and
`Obj89_Pillar_SolidObject` (s2.asm:65652-65663), which temporarily adds four to Y
only for collision, then restores it before drawing. No pillar movement delay or
collision tolerance is added. No RNG differences occur across all 6409 ARZ2 rows.

An independent candidate chain-only run directly records the existing object-
offscreen skip branch for both pillars at V-int `$B0FC`, including Tails at
`($2B5E,$04C0)`, with `contactBounds=false`. It again leaves position and speeds
unchanged. All temporary source probes are removed from the implementation.

## CNZ2 attribution

The executed `loc_3D3A4` read at the first bounce uses `$0710` before the reset
and `$06F6` afterward. Its high-byte mask changes the angle from `$20` to `$1F`
for the same `(dx,dy)=(8,7)`: velocity changes from `(-$04F3,-$04F3)` to
`(-$050F,-$04D0)`, matching the ROM. The second bounce similarly changes `$21`
to `$20`. The V-int inputs are identical. This is the native high-byte read,
not an extra bounce rule or a fixture-selected phase.

## Exact error-set gate

| Continuous S2 segment | Wave7 baseline | Corrected |
| --- | --- | --- |
| ARZ2 | 19,573; first row 4433 `sidekick_x`, `$2B5E/$2B4D` ROM/engine | 4,442; first row 5512 `x`, `$2C71/$2C72` |
| CNZ2 | 346,602; first row 1781 `x_speed`, `-$050F/-$04F3` | 276,895; first row 2390 `sidekick_x_speed`, `-$0200/$02DF` |

The actual 6409 ARZ2 comparison rows remove 15,224 field errors and add 93.
The first removed row is 4433. The first added error is `sidekick_x` at 5596:
ROM/base/candidate `$2CAD/$2CAD/$2CAC`. This is after the remaining row-5512
position divergence; at 5596 both engines still have Sonic x/y subpixels
`$2400/$6300` versus ROM `$CC00/$B600`. Sonic integer position, velocity and
camera agree there. Baseline Tails Y is `$046D` versus ROM/candidate `$046C`.
Thus this added X error is downstream of existing subpixel/history disagreement,
not a new matching-prefix error.

The 13045 ordinary CNZ2 rows remove 160,164 field errors and add 90,460. Three
additional baseline terminal-art comparisons (all at row 13044) account exactly
for the summary's 346,602 versus ordinary-row 346,599: `dynamic_art.edges`
ROM `[]`, engine `[14928,14929]`, and both extra `.present` fields. The raw live
summary preserves all three actual entries; the candidate has no terminal errors.
Total removals including that tail are therefore 160,167, net improvement 69,707.
The first removed row is 1781. The first newly reported fields are at 2396:
edge ordinal `5026/5024`, transfer ID `2513/2512`, and an extra edge. By then
Tails already diverged at 2390: at 2396 ROM/candidate position is
`($0743,$045B)/($0767,$0470)` and velocity `(-$0200,-$02E0)/($0327,0)`.
Candidate Sonic `($084C,$0444)` and camera `($07B9,$03F6)` still match.
No added field error precedes either corrected segment's remaining divergence.

This uses actual row fields, not expanded grouped report spans or truncated chain
messages. ARZ2 remains red at row 5512; CNZ2 remains red at Tails' hurt
response and the chain does not observe its later level-advance boundary.
Neither remaining owner is changed by this lane.

## Verification and limits

All Maven checks use `tools/testing/maven_queue.py`, Java 25, `-Dmse=off -B`,
`-Dsurefire.forkCount=1`, alphabetical order, unset DISPLAY/WAYLAND_DISPLAY,
and `JAVA_TOOL_OPTIONS=-Dopenggf.headless.gl=surfaceless-egl`. All six lane ROM
properties are explicit absolute paths. S1/S2/S3K SHA-1s match AGENTS pins;
KiS2 SHA-1 is `6cd0537a3aee0e012bb86d5837ddff9342595004`.

Each profile is a separate queue session; raw Surefire/trace reports are archived
before the next profile. Source/inputs/environment are held fixed for each run.

Observed baseline profiles on `2c27b9cb7`:

| Profile | Tests | Failures | Errors | Skips | Queue / execution |
| --- | ---: | ---: | ---: | ---: | --- |
| `trace-replay` | 889 | 9 | 0 | 7 | 980 s / 12:00 |
| `trace-replay-r7` | 119 | 81 | 2 | 0 | 732 s / 6:43 |
| `trace-segments` | 70 | 46 | 2 | 0 | 421 s / 4:37 |

The focused investigation uses `-Ptrace-replay` with
`-Dtest=TestGameLoopTitleCardLifecycle,TestS2CompleteEmeraldRunChain`:
7 tests / 3 failures before (two expected negative unit cases plus the inherited
chain failure), 7 / 1 afterward (all six lifecycle cases pass). A separate
`-Dtest=TestS2CompleteEmeraldRunChain` run records the offscreen gate directly
and reproduces the same corrected frontiers. Clean-source focused guards and
the final-commit full-profile comparison are reported separately in the lane
handoff; regenerated raw reports are not committed.

The baseline architectural guard failure is outside this lane:
`GameLoopBonusStageSession#doExitBonusStage`, 126 lines against budget 111.
The seven r6 skips are explained opt-in diagnostics/benchmarks and two absent
optional bonus-run fixtures, but all seven lack release-skip-policy rules; formal
skip classification is incomplete. No policy is weakened to clear them.
No ROM-dependent test is silently skipped. The lead owns integration and the
frontier log; this lane makes no push or frontier/SURVEY edit.
