# Trace Frontier Log

Persistent ledger for trace replay frontier work. Update this file whenever a
trace fix is committed, a frontier moves, a previously passing trace regresses,
or a full `*TraceReplay` sweep is run to choose the next target.

## 2026-05-17 - BizHawk input-indexing fix impact snapshot

- Branch: `feature/ai-trace-frontier-continuation`
- Commit under test: `535895780 Fix BizHawk trace input indexing`
- Worktree state: dirty with local CNZ slot-machine investigation edits
- Command: `mvn -q -Dmse=off '-Dtest=*TraceReplay' test -DfailIfNoTests=false`
- Result: 31 test methods run, 14 failures

Recorder impact note: the committed recorder fix regenerated only the S2 CNZ
trace. CNZ physics row count stayed at 9469, input bytes were unchanged, and
all 9469 `vblank_counter` values changed from `0000` to real ROM VBlank
counter values. The focused clean CNZ replay stayed at the same first failure
before and after the recorder commit: frame 1691, 398 errors. The recorder fix
was diagnostic-impact only for CNZ, but it made VBlank-driven slot timing
debuggable.

| Test | Status | Errors | First error |
| --- | --- | ---: | --- |
| `TestTraceReplayInvariantGuard` | pass | 0 | |
| `TestTraceReplayStartPositionPolicy.s3kEndToEndTracePreLevelPrefixAdvancesMovieWithoutTickingLevel` | fail | n/a | first AIZ frame classified as `VBLANK_ONLY` instead of `FULL_LEVEL_FRAME` |
| `TestTraceReplayStartPositionPolicy.s3kMgzGameplayTraceDrivesFrameZeroWhenSonicAlreadyMoved` | fail | n/a | S3K MGZ setup prelude assertion failed |
| `TestTraceReplayStartPositionPolicy.vblankOnlyRowsAdvanceMovieButDoNotCompareGameplayState` | fail | n/a | `FULL_LEVEL_FRAME` strict-comparison assertion failed |
| `TestS1Credits00Ghz1TraceReplay` | pass | 0 | |
| `TestS1Credits01Mz2TraceReplay` | fail | 6 | frame 493, `y` |
| `TestS1Credits02Syz3TraceReplay` | pass | 0 | |
| `TestS1Credits03Lz3TraceReplay` | fail | 6 | frame 221, `y` |
| `TestS1Credits04Slz3TraceReplay` | pass | 0 | |
| `TestS1Credits05Sbz1TraceReplay` | pass | 0 | |
| `TestS1Credits06Sbz2TraceReplay` | pass | 0 | |
| `TestS1Credits07Ghz1bTraceReplay` | pass | 0 | |
| `TestS1Ghz1TraceReplay` | pass | 0 | |
| `TestS1Mz1TraceReplay` | pass | 0 | |
| `TestS2ArzLevelSelectTraceReplay` | fail | 831 | frame 304, `tails_x_speed` |
| `TestS2CnzLevelSelectTraceReplay` | fail | 413 | frame 1680, `air` |
| `TestS2CpzLevelSelectTraceReplay` | fail | 434 | frame 844, `x_speed` |
| `TestS2Ehz1TraceReplay` | pass | 0 | |
| `TestS2HtzLevelSelectTraceReplay` | fail | 398 | frame 5511, `x` |
| `TestS2MczLevelSelectTraceReplay` | fail | 394 | frame 1085, `y` |
| `TestS2OozLevelSelectTraceReplay` | fail | 1118 | frame 397, `tails_y` |
| `TestS2SczLevelSelectTraceReplay` | fail | 48 | frame 6222, `y_speed` |
| `TestS2WfzLevelSelectTraceReplay` | fail | 595 | frame 4720, `x_speed` |
| `TestS3kAizTraceReplay.playerMatchesTraceThroughFirstGiantRideVineWindow` | fail | n/a | trace frame 2876, player X |
| `TestS3kAizTraceReplay.replayMatchesTrace` | fail | 1715 | frame 1057, `tails_x` |
| `TestS3kCnzTraceReplay` | fail | 3707 | frame 4790, `tails_x` |
| `TestS3kMgzTraceReplay` | fail | 2625 | frame 1538, `y` |

Next active target: S2 CNZ slot-machine release timing. With the local
investigation edits, CNZ moved from the clean-branch frame 1691 failure to frame
1680, where the engine releases Sonic from the Point Pokey cage at VBlank
`0x105A` while ROM still reports Sonic riding the cage.

## 2026-05-17 - S2 CNZ slot-machine frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with CNZ slot-machine/bootstrap fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 353 errors
- First error: frame 3830, `x_speed` (`expected=-0833`, `actual=0x0000`)

Frontier moved from the Point Pokey cage release at frame 1691 to the later
CNZ Big Block / bumper-contact segment at frame 3830. The slot-machine fix
used the regenerated `cnz_slot_machine_state` aux rows to verify the ROM target
word (`0x0203`), VBlank seed window, and same-call Routine5->Routine6
completion path. The aux rows remain diagnostic only; replay still drives the
engine from BK2 input plus one-time native timing bootstrap.

## 2026-05-17 - S2 CNZ object-streaming vertical-filter fix

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 placement fix and Big Block trace diagnostics
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 191 errors
- First error: frame 3906, `tails_y` (`expected=0x06C0`, `actual=0x06BF`)

Frontier moved from the frame 3830 CNZ Big Block side-contact stop to frame
3906, where Tails lands/re-leaves a launcher spring one pixel lower than ROM.
The frame 3830 Big Block report showed only 39 engine updates for ObjD4 while
ROM-side ObjD4 had been active since frame 3330. The fix keeps the vertical
spawn-filter bypass scoped to Sonic 2 object placement, matching S2's
X-window-only `ObjectsManager_GoingForward` / `ObjectsManager_GoingBackward`
path (`docs/s2disasm/s2.asm:32870-32950`).

## 2026-05-17 - S2 CNZ Obj85 Tails recapture frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 launcher-spring fix and line-ending-only test-file noise
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 191 errors
- First error: frame 3957, `tails_y` (`expected=0x06F1`, `actual=0x06F0`)

Frontier moved from frame 3906 to frame 3957. The frame 3906 mismatch was a
Tails-only Obj85 vertical launcher recapture: ROM had Tails already rolling on
the previous frame, but the engine's current-frame state had been normalized
before Obj85 capture code ran, so the object applied its Tails first-capture
one-pixel lift again. The fix makes Obj85 consult the previous recorded status
bit for Tails rolling recaptures and removes the extra top-landing lift for
Tails's shorter `$0F` standing radius. The next blocker is after the launcher
sequence: Tails lands from the launch with ROM still reporting rolling
(`status=0x05`) while the engine clears rolling (`status=0x01`).

## 2026-05-17 - S2 CNZ Obj85 roll handoff and ObjD4 lower-bound frontier advancement

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 roll-preservation and ObjD4 geometry fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 227 errors
- First error: frame 4121, `x_speed` (`expected=-058C`, `actual=-0559`)

Frontier moved from frame 3957 through the Obj85 Tails landing/roll-stop
sequence and then from frame 4074 to frame 4121 after the ObjD4 lower-bound
fix. Obj85 now preserves the vertical launcher rolling handoff only for that
object's post-release path, using its inclusive right-edge capture and
object-local ground-wall follow-up rather than changing shared roll or solid
behaviour. The later frame 4074 Sonic mismatch was ObjD4 Big Block: the engine
used the taller standing-radius lower-half overlap for a rolling airborne
player, falsely classified a side contact, shifted Sonic right, and zeroed
`x_speed`. S2 `SolidObject_cont` uses the live `y_radius(a1)` for ObjD4's
bottom reject bound, so ObjD4 now opts into
`fullSolidBottomOverlapUsesCurrentYRadiusOnly(...)`.

## 2026-05-17 - S2 CNZ bumper integer trig and speed-shoes timer phase

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 CNZ bumper trig, speed-shoes feature flag, trace diagnostics, and skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.game.TestS3kCharacterSpeeds#speedShoesTimerStaysActiveForRomMovementFrames,com.openggf.game.TestPhysicsProfile#testSpeedShoesTimerPhaseCompensation_PerGame,com.openggf.game.TestHybridPhysicsFeatureSet#testExpectedHybridFeatureSetValues' test -DfailIfNoTests=false`
- Result: fail, 406 errors
- First error: frame 4351, `tails_x` (`expected=0x4000`, `actual=0x0E0D`)

Frontier moved from frame 4121 to frame 4351. The frame 4121 player
`x_speed` mismatch was the S2 CNZ map bumper angle-reflection path:
ROM uses `CalcAngle`/`CalcSine` integer tables before multiplying by
`-$A00` (`docs/s2disasm/s2.asm:32334-32677`), while the engine used a
floating-point `atan2`/`cos` approximation that rounded the incoming angle one
step differently. The later frame 4216 mismatch was speed-shoes expiry phase:
S2 `Obj01_ChkShoes` decrements the `$4B0` word timer from display after
movement and clears speed shoes on the decrement-to-zero frame
(`docs/s2disasm/s2.asm:36008-36025`), so the engine's pre-physics timer must
not add the extra phase tick for S2. S3K keeps the existing extra tick behind
`PhysicsFeatureSet.speedShoesTimerPrePhysicsExtraTicks()` because its ROM uses
a byte `(20*60)/8` timer decremented only every eighth frame
(`docs/skdisasm/sonic3k.asm:22067-22078,40818`). The next blocker is a Tails
despawn mismatch: ROM has Tails at the S2 off-screen marker `$4000,0`, while
the engine leaves her local at approximately `$0E0D,$0320`.

## 2026-05-17 - S2 CNZ Tails spawning object-control gate

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 strict sidekick spawning gate and skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 459 errors
- First error: frame 4599, `tails_x` (`expected=0x0EA5`, `actual=0x0EA4`)

Frontier moved from frame 4351 to frame 4599. The frame 4351 mismatch was an
S2 `TailsCPU_Spawning` gate: ROM tests Sonic's raw `obj_control` byte before
respawning Tails (`docs/s2disasm/s2.asm:38743-38762`), while the engine only
checked the high-level full-object-control flag and missed a movement
suppression-only object-control state from Sonic's flipper/object release. The
fix is scoped to the strict S2-style spawning gate via the existing
`sidekickSpawningRequiresGroundedLeader()` feature; S3K's catch-up path keeps
its narrower gates. The next blocker is a Tails follow/respawn X drift: after
the marker delay and respawn, engine Tails is one pixel left of ROM at frame
4599.

## 2026-05-17 - S2 CNZ post-playable bumper timing and Tails landing radii

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 CNZ post-playable zone-feature hook, Obj85 Tails-only landing preserve, and shared landing-radius gate staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 219 errors
- First error: frame 5336, `tails_x_speed` (`expected=0x00C0`, `actual=0x00CC`)

Frontier moved from frame 4599 through a temporary f5294 Sonic landing
regression and the f5328 Tails one-pixel ground snap to frame 5336. The f4599
X drift came from S2 CNZ map bumpers updating too early relative to the
playable loop; `SpecialCNZBumpers` follows player/object execution in the ROM
main loop, so CNZ now updates its bumper manager through a zone-feature
post-playable-physics hook rather than the generic pre-player feature phase.
The f5294 Sonic regression was Obj85's vertical-launch roll-preserve hook
applying to Sonic even though `Sonic_ResetOnFloor` clears rolling normally.
The f5328 Tails Y mismatch came from shared landing cleanup restoring
non-rolling custom radii for S2 Tails; S2 `Tails_ResetOnFloor` only restores
Tails's `$0F/$09` standing radii inside the rolling branch
(`docs/s2disasm/s2.asm:40629-40636`), while the unconditional non-rolling
radius restore belongs to the S3K `Player_TouchFloor` radius-delta model. The
next blocker is a Tails Obj72/conveyor-area handoff: ROM puts Tails into
air+rolling with `y_vel=-0x680` at frame 5336, while the engine remains
grounded and continues normal CPU acceleration.

## 2026-05-17 - S2 CNZ Obj85 preserved-roll delayed jump filtering

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with S2 Obj85 preserved-roll sidekick CPU jump filtering and mirrored skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 215 errors
- First error: frame 5399, `tails_y` (`expected=0x0329`, `actual=0x032A`)

Frontier moved from frame 5336 to frame 5399. The frame 5336 mismatch was the
same Obj85 vertical-stopper preserved-roll handoff, but on the sidekick CPU
input path: the engine suppressed all delayed jump state while the preserved
roll flag was set. That kept the earlier grounded stopper frames stable but
also cleared the fresh delayed Sonic jump press ROM later copies through
`TailsCPU_Normal` (`docs/s2disasm/s2.asm:38939-38946`), preventing
`Tails_Jump` from entering air+rolling with `y_vel=-$680`
(`docs/s2disasm/s2.asm:36996-37070`). The filter now remains scoped to the
grounded Obj85 preserved-roll handoff: stale held jump with no fresh delayed
press is cleared, while the later fresh delayed press is allowed through. The
next blocker is a one-pixel Tails Y mismatch after the later airborne/dead
routine handoff around frame 5399; ROM has `y=$0329`, engine has `y=$032A`.

## 2026-05-17 - S2 CNZ dead-fall ObjectMoveAndFall ordering

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with shared death-movement ordering and S2 deferred generic-dead despawn fixes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 200 errors
- First error: frame 5951, `tails_y` (`expected=0x02B1`, `actual=0x02B0`)

Frontier moved from frame 5399 through the S2 Tails dead-fall marker handoff
to frame 5951. The frame 5399 mismatch came from the shared generic death
path moving with the post-gravity `y_vel`; ROM dead/fall routines load the old
velocity for position, then store the gravity-incremented velocity for the
next frame (`docs/s2disasm/s2.asm:37901-37911,40736-40738,29967-29981`;
S1 `docs/s1disasm/_incObj/01 Sonic.asm:1792-1795`; S3K
`docs/skdisasm/sonic3k.asm:29280-29285,36068-36083`). The frame 5544 mismatch
was the same S2 `Obj02_Dead` deferred-despawn rule reached through the generic
`dead` flag instead of the sidekick CPU `DEAD_FALLING` state: ROM checks
`Tails_Max_Y_pos + $100`, branches to `TailsCPU_Despawn`, then still runs that
frame's `ObjectMoveAndFall` (`docs/s2disasm/s2.asm:40736-40759,39043-39052`).
The new blocker is a Tails air+rolling transition near an Obj74 invisible
block/elevator area: ROM sets Tails airborne+rolling with `y_vel=-$680`, while
the engine leaves Tails grounded at `y=$02B0`.

## 2026-05-17 - S2 CNZ Obj85 preserved-roll lifetime scope

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with Obj85 preserved-roll lifetime scope fix
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 197 errors
- First error: frame 5997, `x_speed` (`expected=0x0000`, `actual=0x000C`)

Frontier moved from frame 5951 to frame 5997. The frame 5951 mismatch came from
the Obj85 preserved-roll marker leaking beyond the rolling stopper handoff: by
frame 5951 Tails was grounded and non-rolling with the marker still set, so the
engine suppressed the normal-follow auto-jump that ROM takes once the preserved
rolling state is gone. The filter now requires Tails to still be rolling before
it suppresses the Obj85 stale push/jump handoff (`docs/s2disasm/s2.asm:38939-38946,
39015-39022,57611-57625`). The new blocker is not ObjD5 gameplay: the trace CSV
input edge turns RIGHT before S2 ROM `Ctrl_1_Held_Logical` reaches `Sonic_Move`
(`docs/s2disasm/s2.asm:701,1361-1387,36253-36260`), so replay currently drives
Sonic one input edge earlier than the sampled ROM physics row.

## 2026-05-17 - S2 CNZ ObjD5 riding horizontal-input phase

- Branch: `feature/ai-trace-frontier-continuation`
- Worktree state: dirty with ObjD5 riding-input phase hook, SCZ Tornado hook migration, regenerated CNZ trace v9.6 diagnostics, and mirrored skill notes staged next
- Command: `mvn -q -Dmse=off '-Dtest=com.openggf.tests.trace.s2.TestS2CnzLevelSelectTraceReplay#replayMatchesTrace,com.openggf.tests.trace.s2.TestS2Ehz1TraceReplay#replayMatchesTrace,com.openggf.tests.trace.s1.TestS1Ghz1TraceReplay#replayMatchesTrace' test -DfailIfNoTests=false`
- Result: fail, 289 errors
- First error: frame 6018, `tails_g_speed` (`expected=0x0000`, `actual=0x0018`)
- Previously-green checks: S1 GHZ and S2 EHZ both printed `All frames match trace. No divergences.`

Frontier moved from frame 5997 to frame 6018. The frame 5997 mismatch was a
V-int/logical-input phase issue while Sonic was riding S2 CNZ ObjD5. The
regenerated v9.6 recorder snapshots showed no `move_lock` or control lock;
instead, the BK2/CSV right edge was visible before the ROM physics row showed
the corresponding inertia change. The fix is object-scoped: shared movement
now asks the current riding `SolidObjectProvider` whether to suppress newly
pressed horizontal logical input for a small number of riding frames, with a
default of zero. ObjD5 opts into three frames based on its
`PlatformObjectD5`/`MvSonicOnPtfm` helper path (`docs/s2disasm/s2.asm:58435-58443,
35617-35657,35402-35420`). The existing SCZ Tornado stale logical-input
compensation was migrated onto the same object hook so shared movement no
longer hard-codes a Sonic 2 object id. The new blocker is Tails follow
acceleration after the Obj74/invisible-block jump window: Sonic matches at
frame 6018, but engine Tails has already applied follow steering
(`tails_g_speed=0x0018`) while ROM keeps Tails at zero ground speed.
