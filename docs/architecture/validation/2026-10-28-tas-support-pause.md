# TAS support: paused game-visible clock (2026-10-28)

Implementation worker scope: `LevelFrameStep`, the existing bound `VIntRunCounter`
contract and precise pause/capsule/DDZ tests. Base:
`8945c0a789b27be57b2361c70f561f31872a76ca`. Branch:
`bugfix/ai-tas-support-pause-20261028`. Central classifications, changelog,
discrepancies, combined smoke and integration belong to the parent.

## Native expectation and current-code finding

The historical freeze remains a real engine defect at this base, rather than a
limitation test requiring the restoration of an old defect. Ordinary
`ObjectManager.update` advances the bound object clock; lag/title callers have
explicit clock sites. Both production `GameLoop` and `RecordingFrameDriver`
admit pause through `LevelFrameStep.serviceVBlankOnly`, which serviced prepared
art without advancing that clock. The outer `VIntRunCounter` carrier correctly
avoids double counting while a live object clock is bound.

Pinned reference revisions:

- S1: `28872a46e2d4c94c5c4818cbd3c04e6eb17edb67`.
  `PauseGame` (_inc/PauseGame.asm:21-24) waits with paused gameplay;
  `VBlank_Exit` (sonic.asm:684-685) increments the longword regardless of pause.
  `Pri_Animals` (_incObj/3E Prison Capsule.asm:168-184) gates allocation/RNG with
  the low three clock bits.
- S2: `34fc7f130a96db171e1a1c43e28e0bad81cd6ca7`.
  `Pause_Loop` (s2.asm:1594-1596) waits without running the level body;
  `VintRet` (s2.asm:507-508) increments the count.
  `loc_3F3A8` (s2.asm:85096-85121) checks the low three bits before allocation
  and RNG; `loc_3F406` (s2.asm:85128-85141) scans remaining animals before results.
- S3&K: `8b2cf3c4178f2382f844da67798de4befc50c5e0`.
  `Pause_Loop` (sonic3k.asm:1572-1574) waits;
  `VInt_Done` (sonic3k.asm:558-561) advances the same visible clock.
  `sub_82A82` (sonic3k.asm:175237-175256) suppresses a missile turn only when
  clock & 3 is zero. These are engine state transitions and integer arithmetic;
  no interrupt execution, beam/register model or observed trace rows are inputs.

The original ROMs were read in place. Verified CRC32 / SHA-1:

| ROM | CRC32 | SHA-1 |
| --- | --- | --- |
| S1 World REV01 | AFE05EEE | 69E102855D4389C3FD1A8F3DC7D193F8EEE5FE5B |
| S2 World REV01 | 7B905383 | 8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9 |
| S3&K locked-on | 63522553 | CFBF98C36C776677290A872547AC47C53D2761D6 |

## Repair

After a successful `NORMAL_PAUSE` / `SPECIAL_STAGE_PAUSE` lifecycle claim,
`serviceVBlankOnly` advances the existing object clock once, after existing
service work. Duplicate claims and claims lost to a blocking fade add no tick.
Ordinary, lag, title and fade clock owners, PLC preparation and descriptor gates
are unchanged. No GameLoop, capture, reset, public API or object mask changes
were needed. Object-manager and carrier snapshots retain the clock. A separate
internal `native-pause` Boolean adapter captures the current manager's native
pause flag; `GameplayModeContext` registers it immediately after `gamestate`.
The public `GameStateSnapshot` schema is unchanged. Reattachment binds the new
manager, and actual registry restoration of legacy snapshots missing the key
defaults to unpaused.

## Scenario qualification

- `S1_CAPSULE_RNG:2`: native clock assertion replaces the historical freeze
  assertion. Retain the prepared Final Zone PLC drain and next-descriptor gate.
  Real SLZ3 capsule button contact reaches animal release; pause durations 2–9
  cover all modulo-eight residues with matched RNG and restored forward replay.
- `s2-pause-cycles:1`: actual Start / held Start / release / fresh Start runs
  three paused rows, then resumes exactly one gameplay row. Cross-game tests
  inspect full object/player/level/IGT/RNG snapshots, pause pixels, host holds,
  frame step, and reused sessions.
- `s2-capsule-rng:2`: the actual late capsule child spawns on the ordinary mask
  and blocks results; pause shifts its final release off-mask, consumes no RNG,
  and reaches the actual results object. The late-release state is a declared
  component prerequisite; travel, boss defeat and earlier animal release are
  outside this representative.
- `s3k-rng:1`: ordinary/resume and restore-forward controls qualify the universal
  clock. The reached falling signpost consumes RNG on its ordinary sparkle phase
  and skips that draw after pause changes the phase.
- `s3k-ddz:2`: stock launcher missiles naturally reach homing. Pause leaves
  missile position/angle frozen, then shifts the admitted homing gate and its
  two-angle turn. This qualifies the alignment phase prerequisite, not a full
  published route, successful missile/boss impact, or final DDZ completion.

Historical negative evidence remains in the 2026-10-26 verdict report and support
assessment. Renamed tests assert native behavior; the previous frozen-clock
observations are recorded here rather than retained as an engine requirement.

## Rewind prerequisites exposed by strict checks

The first S2 restore-forward check reached the native clock/RNG/results outcome,
then failed because `gamePaused` was absent from the public game-state snapshot.
The parent approved the internal `native-pause` adapter described above. Its
actual registry missing-entry path and manager reattachment controls passed;
`GameStateSnapshot` and Mod API signatures remain unchanged.

The next run exposed nine S1 replay differences only in `palette-colors.normalRgb`:
eight SLZ capsule residues and the GHZ pause lifecycle. Palette colors restored
immediately, but S1 palette timer/frame state had no rewind owner, so the next
admitted update consumed future phase. S2 and S3K already retained that state.
The parent approved a bounded S1 repair:

- `Sonic1PaletteCycler` captures its mutable cycle primitives with the existing
  `PaletteCycleStateCodec`, using a private versioned byte payload.
- `Sonic1PatternAnimator` carries that payload in the existing optional snapshot
  `extra`; the normal S1 provider pairs pattern/palette owners by level identity
  and native zone. Their separate update calls and ordering are preserved.
- All nine strict S1 replay cases then passed. Existing pattern and LZ ordinary
  controls passed too. The sole remaining precision-test failure was a fixture
  error: logical SLZ index 4 was passed directly to a ROM-index cycler constructor,
  selecting native SYZ 4 (timer 5) instead of SLZ 3 (timer 7). The fixture now uses
  the loaded level's native index; no engine timer or mask was changed for it.

Current captures retain palette phase. Legacy pattern-only `extra == null` has
no authoritative palette phase; compatibility restoration leaves the live
palette clock unchanged rather than inventing one. That limit is explicitly
checked through the normal registry restore path. The palette-color adapter,
cycling, native gameplay/RNG assertions and full composite/hardware comparisons
remain unchanged.

## Validation

Base/source is the pinned worker base above plus the owned changes. Final Java
inputs equal staged tree `6418f334e447947de5bdca460973bcd957ecb72e`; only this
validation prose and commit metadata were updated afterward. Earlier source
trees below identify the particular completed runs. No broad smoke, four
mandatory S3K gates, release suite or integration is owned by this worker.

Environment: Java 25 at `/usr/lib/jvm/java-25-openjdk`, Lua 5.4 at
`/usr/bin/lua5.4`, surfaceless EGL, `DISPLAY` and `WAYLAND_DISPLAY` unset.
Original ROM properties were absolute, with identities verified above. Commands
use those task-supplied paths through `S1_ROM`, `S2_ROM`, `S3K_ROM`:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  env -u DISPLAY -u WAYLAND_DISPLAY \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$S1_ROM" -Dsonic2.rom.path="$S2_ROM" -Ds3k.rom.path="$S3K_ROM" \
  "-Dtest=$SELECTION" test -B
```

The initial request remained alive while queued. Source inspection and preserved
native/historical negatives established the clock omission; the approved repair
was applied before admission. Run A therefore qualified the candidate, not a
newly executed baseline red. Requests were never bypassed, duplicated or
restarted because admission was slow.

| Run | Tested tree | Cases / failures / errors / skips | Queue wait | Maven execution | Observed result |
| --- | --- | --- | --- | --- | --- |
| Build-only | clock repair before pause adapter | 0 / 0 / 0 / 0 | no test admission | 1m26s | `test-compile -B`, exit 0 |
| A | `6e628e977caee4e791bd30125866d7a3f3fbbe5a` | 3 / 1 / 0 / 0 | 1423s | 1m35s | exit 1; S1/S3K clock pass, missing pause snapshot exposed |
| B | `895bc0dd1cb203d551082a7ceacc5cd96ba5c7e7` | 62 / 9 / 0 / 0 | 3594s | 1m44s | exit 1; 53 pass, nine S1 palette replay failures |
| C | same runtime source as B | 4 / 0 / 0 / 0 | 3331s | 37.482s | Maven `BUILD SUCCESS`; outer command 143 after completion |
| D | `25b9df1e2b5a3bb6ce26c42f2d9d0a5bcd4fcd23` | 24 / 1 / 0 / 0 | 180s | 1m40s | exit 1; all nine S1 scenarios pass, one new native-index fixture error |
| E | `6418f334e447947de5bdca460973bcd957ecb72e` | 3 / 0 / 0 / 0 | 91s | 33.844s | Maven success and observed outer exit 0 |

The final receiving command E has a complete observed exit. C's complete
three registry cases and one S2 capsule case passed in Maven, but its outer
143 is recorded separately; it was not resubmitted unchanged to erase that
signal. Successful unrelated coverage is reused by source/consumer attribution:
S1-only palette state and its fixture correction do not change S2/S3K,
clock/admission/host/lag/fade/PLC/DDZ owners. The two clock successes from A are
also retained. In total 83 distinct focused cases have successful completed
JUnit observations after the fixes (85 successful case executions, including
two rechecks in E), with zero skips. This is bounded validation, not a full-suite
or whole-route qualification. Queue intervals overlap and must not be summed
as elapsed delivery time; unmeasured time is unknown.

Selections:

- A: `TestTasS1PausePlcVerdicts#nativeStartPauseAdvancesCapsulePhaseClockWithoutGameplay,TestTasS2CapsuleManipulation,TestTasS3kPauseClock`
- B: `TestPausedVBlankAdmission,TestNativePauseClockLifecycle,TestTasS1CapsuleVerdicts,TestTasS1PausePlcVerdicts#startPauseServicesPreparedFinalZoneArtWithoutRunningGameplay,TestTasS2CapsuleRng,TestTasS3kDdzMissileSteering,TestTasS3kSignpostRng,TestVIntRunCounter,TestGameLoopVIntRunCounter,TestInGamePause,TestRecordedInputNativePause,TestRecordedInputHostPause,TestGameLoopTitleCardPause,TestTraceSuppressedRowClosure,TestPlcFrameLifecycleCoordinator#suppressedLagLeavesAnActiveNativeFadePaused,TestLevelSeamlessTransitionExecutor#reloadFrameBridgeAdvancesStoredLevelSpriteVintAndOscillationClocks`
- C: `TestTasS2CapsuleManipulation,TestGameplayModeContextRewindRegistry#registryHasAtomicAdaptersAfterAttach+exactlySixteenAtomicKeysAfterAttach+reattachRebuildsRegistry`
- D: `TestTasS1CapsuleVerdicts#pauseResiduesShiftRealCapsuleAnimalAndRngPhaseAndReplay,TestNativePauseClockLifecycle#sonic1PauseAndRestoredInputReplayKeepOneClockTickPerRow,TestSonic1PaletteClockRewind,TestSonic1PatternAnimatorRewindSnapshot,TestSonic1PaletteCyclerLz`
- E: `TestSonic1PaletteClockRewind#ordinaryPaletteTicksKeepTheirNativePeriod+replacingTheLevelBindsOnlyItsOwnPaletteClock`

Citation validation: all 10 native record citations resolve against immutable
pins (9 exact anchors, one within-routine anchor); the three added clock source
citations and two palette-period test citations are exact matches. A full strict
check of `LevelFrameStep` reports six inherited stale citations, identical at
base and candidate. They were preserved outside scope.

Historical negative evidence remains unchanged. Current selector changes are:
`currentLimitationNativeStartPauseFreezesCapsulePhaseClock` →
`nativeStartPauseAdvancesCapsulePhaseClockWithoutGameplay`;
`currentLimitationPauseFreezesVintAndCannotOmitTheLateAnimal` →
`pauseAdvancesVintAndOmitsTheLateAnimal`;
`currentLimitationStartPauseFreezesVintAlongWithLevelAndPlayer` →
`startPauseAdvancesVintWhileLevelAndPlayerStayFrozen`. DDZ's new coupled phase
control is `nativePauseShiftsReachedHomingAndSignpostRngTogether` (0/3 paused
ticks), alongside the unchanged ordinary homing controls.

## Integrated competition consumer correction (2026-10-28)

The parent resumed this worker after broad smoke reached two consumers on
`88b2e5d483e7aa24c23dbf8c1da103071a254c18`, which contains the original pause
commit and all final streams/playback fixes. The existing owned worktree was
clean at `ce9005f0bf9ee7dde76de3969ad1c27f206c5a5f` and was fast-forwarded to
that local parent commit. No main checkout, fetch, new checkout or delegate was
used. The parent-reported failures are the red evidence for this correction:

- `TestNativeCompetitionIteration#nativePauseWaitIncludingResumePrecedesTheNextOrdinaryWaitAndWorldBody`
  rejected the new level-manager clock lookups at its isolated pause boundary.
- `TestSonic2NativeCompetitionHost#sixRealActsKeepIndependentHumansCanonicalTimersRewindAndTheActLedger`
  expected VInt 147 and observed 148 after a native pause wait. The S2 host
  incremented the actual object clock after `NativeCompetitionWorld.pauseVBlank`,
  which now also reached the shared pause tick. Both spent the same wait.

The correction makes the pause clock an explicit callback to the existing
`LevelFrameStep.serviceVBlankOnly` service. Its original entry point resolves
the current level object clock lazily and once; the competition world supplies
its current context's object clock. In both paths, only the first successful
pause claim invokes the callback, after existing art service. No game-name gate
selects ownership. S2's extra host pause increment is removed. Its 25 native
title-leave wait increments and ordinary object-body tick remain unchanged.
The S3K host already relied exclusively on the shared world pause service;
inspection found no duplicate host increment and required no S3K runtime edit.

The native reference remains one tick per wait, including the pause wait that
observes resume: S2 `Pause_Loop` (s2.asm:1595-1597) reaches
`VintRet` (s2.asm:507-510), and S3K `Pause_Loop` (sonic3k.asm:1572-1574) reaches
`VInt_Done` (sonic3k.asm:558-561). These anchors were checked against the
immutable S2/S3K pins above. The six inherited strict citation warnings in
`LevelFrameStep` have identical ranges/details at parent source and candidate;
they remain outside this correction. Original ROM CRC32/SHA-1 values were
reverified in place and match the table above.

The original no-level/sprite-interaction assertion stays in place in the
isolated competition pause test. It now also supplies the object-clock owner
and verifies one clock call after each of two VBlank services, with no other
object-manager calls. A new supplied-clock admission control covers both pause
phases, repeated tokens, lag and a losing fade claim without consulting the
level. The existing S2 six-act test retains all clock, actor, bank, loader,
results and rewind assertions. The exact S3K native pause test now checks every
paused tick, power-on/object-clock agreement, restored clock, full composite
forward equality over all registered keys, resume wait and following ordinary
body. Existing strict comparisons and native expectations were not relaxed.

One focused command ran on Java/test input tree
`c70b4fa2399fb1118def62f193d8f6f22eb94795` (parent source plus this bounded
correction). Java and test source stayed frozen throughout the request; only
this validation prose and commit metadata followed. Command below uses the
same task-supplied absolute original ROM properties through `S1_ROM`, `S2_ROM`
and `S3K_ROM`, as above; selectors and all other arguments are exact:

```bash
JAVA_HOME=/usr/lib/jvm/java-25-openjdk LUA_BIN=/usr/bin/lua5.4 \
  env -u DISPLAY -u WAYLAND_DISPLAY \
  python3 tools/testing/maven_queue.py -Dmse=off \
  -Dopenggf.headless.gl=surfaceless-egl \
  -Dsonic1.rom.path="$S1_ROM" \
  -Dsonic2.rom.path="$S2_ROM" \
  -Ds3k.rom.path="$S3K_ROM" \
  '-Dtest=TestNativeCompetitionIteration,TestSonic2NativeCompetitionHost#sixRealActsKeepIndependentHumansCanonicalTimersRewindAndTheActLedger,TestS3kCompetitionNativeHost#nativePauseUsesPriorP1SampleKeepsEveryWorldOwnerFrozenAndResumesAtASeparateWait+bothHumansUseNativeInputsRealRomTerrainAndExactlyOneWorldClock,TestPausedVBlankAdmission,TestNativePauseClockLifecycle,TestTasS1PausePlcVerdicts,TestTasS2CapsuleManipulation,TestTasS3kPauseClock,TestVIntRunCounter,TestGameLoopVIntRunCounter,TestPlcFrameLifecycleCoordinator#suppressedLagLeavesAnActiveNativeFadePaused' test -B
```

Request/session `75646` completed with **35 tests, 0 failures, 0 errors,
0 skips**, Maven `BUILD SUCCESS` and observed outer exit **0**. The exact 35
JUnit identities in 11 XML reports were inspected. Queue wait was **385s**;
Maven execution including recompilation was **1m37s**, finishing
`2026-10-28T23:32:11Z`. Admission used the normal **7 GiB / 8-core** budget.
There was one request and one observer, no resource override, bypass or restart.

| Consumer / control | Passing cases |
| --- | ---: |
| Native competition iteration | 9 |
| S2 actual six-act host | 1 |
| S3K actual native pause/rewind and ordinary human-input control | 2 |
| Pause clock admission | 3 |
| S1/S2/S3K native pause lifecycle, registry and legacy restore | 5 |
| S1 pause clock and prepared PLC/next-descriptor gate | 2 |
| S2 real capsule animal/RNG/results consequence | 1 |
| S3K native pause clock | 1 |
| Bound/carried VInt counter | 8 |
| GameLoop VInt session/rewind consumers | 2 |
| Lag suppression during active native fade | 1 |

This run qualifies the bounded integrated consumers, not broad smoke or the
parent's mandatory S3K gates. It does not add route or native-probe evidence.
The five original scenario IDs and representative route limits are unchanged;
DDZ remains a phase-prerequisite qualification. Earlier leaf-source results
and the separate run C Maven-success/outer-143 observation remain recorded
above; they are not relabelled as runs on this integrated source. Consumed
Maven diagnostics were removed after inspection. No owned test/native probe
remains active; the clean committed branch/worktree is retained for the parent.
