# HCZ1 miniboss local ROM parity audit

Base: `b1f693fd0104e245f23b2560717da7809b41c56a`, direct `develop` working tree.
Reference: locked-on `docs/skdisasm/sonic3k.asm`, `Obj_HCZMiniboss` and reachable
routines, shipped `FixBugs = 0`. This is a local correction, not act certification.

## Findings and decisions

| Owner | Previous behavior | Correction |
| --- | --- | --- |
| `loc_69EDA` / `loc_69EFE` | Each X-lock wait overwrote minimum Y with `$300`, even after Y locked at `$638` | Preserve the installed Y lock on subsequent waits |
| `loc_6A436` → `loc_6A3C4` → `loc_6A3DA` | Only one `$1F` wait at rocket speed 2 before speed 1 | Run both 32-update waits, preserving each callback boundary |
| `sub_6AB1A` | Initial audit misread priority direction | Superseded by the visual follow-up below: `$200` is in front of `$280`; the initial change and its predicate test were wrong |
| `loc_6A4C0` → `loc_6A47C` | Lower engine drawn and registered for touch on odd V-int counts | Share the even-V-int gate between rendering and touch, including parent closed/defeated gates |

The slowdown is not a different speed constant: the ROM explicitly installs a
second callback at the same speed. Collapsing those callbacks loses 64 phase units.
The lower engine's flicker is not cosmetic: the gate precedes
`Draw_And_Touch_Sprite`, so it also suppresses collision-list registration.
No fixture values or timing offsets select these behaviors.

## Reachable encounter and inherited graph limits

Camera trigger → independent Y/X locks → music fade → boss loop → descent/wait/rise →
attack-pattern dive/floor strafe → repeat dive or pre-vortex drift → rocket slowdown →
vortex windup/pull/cooldown → reopen/slow rise → next pattern.
The main body is player-damageable (`collision_property = 6`); the lower engine and
armed rockets are harmful. The water effect owns player pull/control. A killing
hit reaches `sub_6AC48` → `loc_6ACA6` → `BossDefeated_StopTimer` and
`Wait_FadeToLevelMusic` → `loc_6A22A` → `Obj_EndSignControl`, followed by results
and the underwater transition controllers. These publication owners are unchanged.

ROM root creation allocates one water-effect child. Combat creation allocates four
rockets and one lower engine, each rocket allocating one exhaust: 11 steady-state
slots including the root. Splash creation is a three-child table, vortex creation
is 30 simple children, and defeat separately creates explosions, four root debris,
and five water-effect debris. Peak live counts depend on overlapping transient
lifetimes and were not certified here. The engine folds several of these roles into
the parent and has four rocket touch proxies. This patch does not change allocation,
recreation topology, or the results chain. Exact ROM slot-pressure/prefix-allocation,
complete graph recreation and full publication-chain tests remain inherited gaps;
the existing rocket proxy recreation test is not proof of those broader contracts.

## HCZ1 affected route matrix

| Contract | Sonic | Sonic + Tails | Tails | Knuckles | Extra sidekicks / donor / viewport breadth |
| --- | --- | --- | --- | --- | --- |
| Arena Y lock while waiting for X | Shared local regression | Same owner | Same owner | Same owner | Fixed ROM world thresholds; full viewport routes pending |
| All four rocket slowdown subtypes | Shared local regression with midpoint restore/replay | Same owner | Same owner | Same owner | No character branch; donor routes pending |
| Rocket phase-depth and lower-engine even/odd touch | Shared local regression | Same owner | Same owner | Same owner | Draw-order predicate tested; native pixel verification pending |
| Rocket proxy remove/recreate/relink | Existing ROM integration test | Team breadth pending | Pending | Pending | Full world replay and pressure matrix pending |
| Entry → fight → defeat → HCZ2 | Inherited gap | Inherited gap | Inherited gap | Inherited gap | Full act matrix remains pending |

These are obligation distinctions, not separate execution passes. Applicable
before/active/after world snapshots, wide viewport pixels, donor and complete
character/team routes remain required for full level-standard conformance.

## Validation

The final change-based plan selected 2,172 ordinary candidate classes plus guards
(2,060 before adding the regression and documentation). Local regressions directly exercise both sides of the changed gates and all four
rocket subtypes, including capture/advance/restore/replay. The initial intention
was proportionate validation, but the rocket timing change falls under the policy's
normal change-based requirement. That broader validation remains incomplete:
`run_categories.py --base b1f693fd0104e245f23b2560717da7809b41c56a --preflight`
failed before tests because Lua 5.4 and PowerShell are absent. No broad attempt
was launched. Focused passes below are not a substitute for that outstanding gate. Commands use Java 21 and the existing absolute root ROM paths; SHA-1/CRC32 matched
all three documented identities.

- `mvn -Dmse=off -Dtest=TestHczMinibossRomParity test`: first iteration ran 7,
  with one test-harness service-injection error; corrected the test setup.
- `mvn -Dmse=off '-Dtest=TestHczMiniboss*,TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils'`
  plus the three absolute ROM properties, `test`: **75 tests, zero failures/errors/skips**,
  36.840 seconds, base plus this working-tree patch.
- `mvn -Dmse=off -Ptrace-segments -Dtest=TestS3kHczZoneSliceTraceReplay`
  plus the absolute S3K ROM property, `test`: completed 29,302 compared frames;
  2 tests, 1 failure, no errors/skips, 54.388 seconds. 4,571 comparison errors,
  first at frame 9,482 (`air`, expected 1, actual 0). Matched baseline attribution
  follows below. This is not a passing trace or full-suite result.


The matched pre-task boss baseline ran the identical HCZ replay command with only
`HczMinibossInstance.java` temporarily restored from the pinned base, then restored
the fix automatically. It also completed 29,302 frames, 2 tests / 1 failure /
0 errors / 0 skips, in 1 minute 44 seconds. Both arms produced 4,571 comparison
errors starting at frame 9,482 (`air`); their complete normalized `errors` arrays
had SHA-256 `72617e18d6b481e068122f7978a5a38303270bbf9b8ef33a6a78b8659deb825d`.
This attributes the replay failure to the pre-existing baseline, without claiming
its root cause or that the fixture measures every corrected boss field. Missing
advertised auxiliary schemas are inherited and no bootstrap errors were reported.


`mvn -Dmse=off -Pguards -Dtest=TestRewindCoverageGuard test` passed in a fresh
JVM: 1 test, no failures/errors/skips, 1 minute 23 seconds. The full guard lane
remains unrun. Total accounted testing: 5.929 minutes (including the initial test
setup failure and matched baseline); no broad attempt. Raw task logs and consumed
reports were removed after inspection. Changes remain uncommitted on `develop`.


## Follow-up: sprite composition and whirlpool cleanup

User-requested follow-up on the same direct `develop` working tree; the previous
uncommitted fixes were preserved. The main sprite renderer made the earlier
priority claim untenable: `Render_Sprites` (`loc_1AD54`, `Render_Sprites_NextLevel`)
walks buckets and slots ascending into the SAT, where the earliest sprite wins.
`ObjectManager.ensureBucketsPopulated` therefore paints descending buckets and
slots. The initial `front >= $80` change and its test encoded the same mistaken
assumption and were corrected, rather than being treated as evidence of parity.

| ROM owner | Finding and correction |
| --- | --- |
| `sub_6AB1A`, `sub_6ABA8`, `word_6ABF8` | Rockets use `$200/$280`, but their exhausts independently use `$180/$200/$280`. The compositor now reverses bucket and child-slot order independently, including ties, and tags each part's SAT bucket. The bucket tag is restored afterward. |
| `loc_6A384`, `loc_6A3A0`, `loc_6A436`, `loc_6A47C` | Collision arms 32 updates before a rocket clears its exhaust-suppression bit. Each exhaust now uses its own captured active flag plus even `V_int_run_count`, instead of one any-rocket/collision gate. |
| `Child_DrawTouch_Sprite_FlickerMove`, `Set_IndexedVelocity`, `Obj_FlickerMove` | Defeated rockets continued orbiting. They now finish the dispatch, install indexed and H-flipped velocities, then fall with `$38` gravity and the ROM bit-toggle flicker, retaining subpixels and culling individually. |
| `byte_6AE46..52`, `sub_6AA30` | Vortex bubbles were static sprites at a default priority. They now alternate their selected frame with blank `$16` during formation/dissipation and use `$100/$300` from signed X velocity while swirling; the release-handler install does not move them. |
| `loc_6A5FA`, `loc_6A618`, `byte_6AE16` | Water slowdown was replaced with an immediate idle frame. Restore the decelerating multi-delay script, retained cursor/timer, and first step through the old `$30` script pointer. The single-delay pull cursor also follows `Animate_RawNoSST`'s frame indexing. |
| `loc_6A57C` | Windup uses `sfx_FanBig`; `sfx_BossRotate` belongs to the subsequent pull loop. |
| `sub_6A960`, `loc_6A636`, `Obj_EndSignControl`, `Child_Draw_Sprite2` | Killing the boss freezes the water sprite; 64 defeat-wait updates later the sign controller publishes parent bit 4, which propagates through the water effect to delete its bubbles. The engine never published this deletion. Preserve the frozen art until that boundary, then delete all tracked live bubbles and stop drawing the main body/water. Unload also cleans up retained children. |

### Rewind findings

The real-manager regression exposed two additional failures. Bubbles were declared
as `RewindStateful` value helpers despite being live managed objects; the owner's
list could therefore retain stale instances. They now use default object scalar
capture and identity-based collection relinking. Their vortex centre, chosen frame,
animation phase, depth and fractional movement all survive remove/recreate/restore.
The first follow-up HCZ replay stopped at frame 9,374 with a reference-closure
error: a bubble removed after the boss's update remained in its owner's list.
Pruning on the next boss update was insufficient. Each bubble now captures its
owner reference and unlinks itself on unload, before end-of-frame capture. The
real-manager test validates closure immediately after removal and verifies the
recreated owner links.

The optional `S3kBossExplosionController` pointer was explicitly deferred and came
back null after restoring a killed boss. `updateDefeated` then returned before
advancing the handoff timer. A permanent HCZ-owned snapshot adapter now reconstructs
that optional helper with its timer, intervals, RNG state, ranges and pending
explosions. The shared helper gained value snapshot methods; its ordinary explosion
algorithm and other bosses' capture policies are unchanged.

### Follow-up coverage

- `TestHczMinibossVisualParity`: production draw-call order and independent
  exhaust gates, water slowdown/freeze/handoff, indexed rocket debris trajectories,
  flicker and culling.
- `TestHczMinibossNativeRendering`: ROM PLC art and mappings rendered through
  native OpenGL, compared pixel-for-pixel with an independently assembled oracle
  reading the priority/frame/offset tables directly from the ROM. Sixteen orbit
  phases and both even/odd V-int cases; a nonblank-pixel assertion prevents an
  inert renderer from passing. This certifies local sprite composition, not full
  level foreground/water-palette or viewport rendering.
- `TestHczMinibossRocketTouchRewind`: real object-manager 30-bubble batch,
  nondefault movement → remove → recreate → replay, then two defeat-handoff
  timelines checking the entire live batch is deleted at the boundary.
- `TestHczMinibossVortexBubbleMotion`: fractional motion, frame alternation,
  signed-velocity depth, release-install no-movement and delayed deletion.

The act/character matrix above still carries the full-route, donor, viewport and
slot-pressure gaps. No new allocation model or complete transient encounter graph
certification is claimed. The normal broad validation requirement remains blocked
by absent Lua 5.4 and PowerShell; focused passes are not a broad-suite pass.

### Follow-up execution record

Direct `develop` working changes against `b1f693fd0104e245f23b2560717da7809b41c56a`:

- `mvn -Dmse=off '-Dtest=TestHczMiniboss*,TestS3kBossExplosionController'`
  with the absolute locked-on ROM property passed **32 tests, zero skips** after
  the removal-boundary fix. This includes the 32-case native pixel oracle.
- The earlier combined focused invocation also included
  `TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`,
  `TestSonic3kBootstrapResolver`, and `TestSonic3kDecodingUtils`: all 58 mandatory
  route/loading/bootstrap/decoding checks passed, with no skips. Its sole failure
  was the native fixture's unsigned-coordinate mismatch, corrected and rerun.
- Native fixture development first exposed an unsupported OpenGL context, then
  an invalid negative world coordinate in one arm. The final oracle uses native
  OpenGL 4.1, valid unsigned world coordinates and an explicit camera; generated
  phase images were visually inspected as well as compared numerically.
- The final change-based plan selects **2,514 classes plus guards** after the
  explicit bubble-owner capture policy is included. No broad
  attempt was launched because preflight lacks Lua 5.4 and PowerShell. The
  selection is the full ordinary suite; focused checks do not discharge that
  normal validation requirement.
- Final affected replay: `mvn -Dmse=off -Ptrace-segments
  -Dtest=TestS3kHczZoneSliceTraceReplay` with the absolute locked-on ROM property.
  Completed 29,302 frames; 2 tests, 1 comparison failure, no errors/skips.
  Its 4,571 errors still begin at frame 9,482 `air` (expected 1, actual 0).
  SHA-256 of `json.dumps(errors, sort_keys=True)` remains
  `72617e18d6b481e068122f7978a5a38303270bbf9b8ef33a6a78b8659deb825d`,
  exactly matching the earlier pinned baseline. No bootstrap errors; advertised
  auxiliary schema gaps remain inherited. The temporary frame-9,374 reference
  closure regression is resolved, but the trace remains red.
- Final fresh guard JVM: `mvn -Dmse=off -Pguards
  -Dtest=TestRewindCoverageGuard,TestHelperStateRewindCoverageGuard,TestRewindFieldDispositionGuard,TestHczMinibossRocketTouchRewind`
  with the absolute ROM property passed **5 tests, zero skips**. The preceding
  coverage guard required an explicit `CAPTURED` policy for the new owner link;
  that declaration is now installed, with no baseline suppression.

Follow-up test accounting totals 12.534 minutes, including failed setup and
regression checks; no broad attempt. Consumed raw logs, reports and temporary
native images were removed. The changes remain uncommitted on `develop`.
