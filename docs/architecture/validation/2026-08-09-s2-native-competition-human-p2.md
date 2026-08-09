# Sonic 2 Native Competition and Human-P2 Boundary Validation

**Date:** 2026-08-09

**Disposition:** materially narrowed; runtime capability unavailable

## Delivery boundary

- Branch: `feature/ai-s2-competition-capability`
- Worktree: `.worktrees/s2-competition-capability`
- Base: fetched `origin/develop` at
  `e2aa50cd5980efc720f70c1c2a6209b2637b3042`
- Delivery: local evidence/test/documentation commit only; no merge or push
- Runtime delta: none; there is no `src/main` change, title option, launch
  setting, competition flag, human-P2 factory, second camera, or monitor
  exception

This work closes the ambiguity around the audited monitor branch. It does not
close the product gap. Human P2 in Sonic 2 is part of the shipped native
competition route and cannot be represented by clearing the ordinary CPU
sidekick bit.

## Reviewed architecture

The reviewed artifacts are:

- [design](../designs/2026-08-09-s2-native-competition-human-p2-design.md)
- [execution plan](../plans/2026-08-09-s2-native-competition-human-p2-plan.md)

The first design review found one blocking lifecycle omission: act-one results
must return to act two before zone results. The amended phase graph and
reset/retain matrix now model
`LEVEL(act 1) -> ACT_RESULTS -> LEVEL(act 2) -> ACT_RESULTS -> ZONE_RESULTS`.
They pin the act-one handoff writes (`Current_Act_2P=1`, both mode copies,
starposts, scores, next-life thresholds, and `GameModeID_Level`) while retaining
lives, selected zone, and the published act-one result ledger. The corrected
design also cites the REV01 build selectors at `s2.asm:20,27`. Independent
re-review reported the design green.

The plan review reported no blockers. It approved an evidence-only delivery
with two real characterization tests and an executable deferred RED-to-GREEN
campaign. The plan forbids dormant production scaffolding and keeps every
future prerequisite production-used by ordinary play before the final S2
provider/product activation.

## Source authority and audited owners

The canonical input was Sonic 2 World REV01:

```text
Path: <project-root>/Sonic The Hedgehog 2 (W) (REV01) [!].gen
SHA-1: 8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9
Build: gameRevision = 1; fixBugs = 0
```

The audit covered these native owners:

- title mode/copy setup and 2P selection: `s2.asm:4535-4571`;
- forced Sonic/Tails initialization: `s2.asm:5160-5196`;
- competition level order: `LevelSelect2P_LevelOrder`, ROM `$008E52`;
- act, zone, game, tiebreaker, and standalone-special results:
  `s2.asm:10708-11210`;
- paired camera initialization and dual follow: `s2.asm:14777-15180`;
- split-display H-int: `s2.asm:616-825,1184-1222`;
- two-camera object loading and CNZ 2P layout: `s2.asm:32920-33535`;
- per-player state: `s2.constants.asm:1754-1782` and representative live
  uses at `s2.asm:25069-25205,25756-25888,41151-41217,44768-44770`;
- human-P2/CPU-Tails monitor gate: `s2.asm:85333-85343`.

The current engine audit followed `TitleScreenManager`,
`StartupRouteResolver`, `GameLoop`, `WorldSession`, `GameplayTeamBootstrap`,
`SpriteManager`, `ObjectPlayerQuery`, `ObjectTouchResponseController`,
`GameStateManager`, `LevelGamestate`, `Camera`, `LevelManager`,
`LevelRenderer`, `ObjectManager`, `RingManager`, `HudRenderManager`,
`Sonic2LevelEventManager`, `MonitorObjectInstance`, special-stage owners, and
rewind snapshots/identities.

## Reproducible boundary evidence

`TestSonic2CompetitionBoundary` exercises two independent production seams.

### Native entry table

The test hashes the loaded ROM before reading four unsigned big-endian words
at `$008E52`. The result is:

```text
0000 0B00 0C00 FFFF
```

Those literals are EHZ act 1, MCZ act 1, CNZ act 1, and the special-stage
sentinel. `$FFFF` is not a fourth normal level. The expected values are not
derived from an engine constant or the decoder under test.

### Supported ordinary team

The second test configures ordinary Sonic plus Tails and calls the real
`GameplayTeamBootstrap.registerActiveTeam(...)` with a real
`Sonic2GameModule`, `SpriteManager`, and configuration service. It observes
exactly one human main sprite and one CPU-controlled secondary with a live
`SidekickCpuController`. This protects the supported route from the unsafe
shortcut of converting CPU Tails into human P2 in place.

These tests characterize native data and current production behavior. They do
not prove a competition session, controller isolation, split rendering,
two-camera lifetime, results, special-stage handoff, or rewind.

## Commands and outcomes

Maven used JDK 21.0.11 from `/usr/lib/jvm/java-21-openjdk`.

Before the new class was added, the adjacent boundary suite established the
branch baseline:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -Dmse=off \
  -Dsonic2.rom.path="<project-root>/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.TestGameLoop#testDoExitTitleScreenRoutesTwoPlayerAwayFromDataSelect,com.openggf.game.session.TestActiveGameplayTeamResolver,com.openggf.sprites.managers.TestSpriteManagerUpdateOrder,com.openggf.level.objects.TestObjectPlayerQuery,com.openggf.game.sonic2.objects.TestMonitorObjectInstance,com.openggf.tests.trace.s2.TestS2Ehz1MonitorBreakRegression,com.openggf.game.sonic2.TestSonic2LevelEventRewindSnapshot,com.openggf.camera.TestCameraRewindSnapshot,com.openggf.game.TestGameStateRewindSnapshot \
  test
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
```

The new class alone passed:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -Dmse=off \
  -Dsonic2.rom.path="<project-root>/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.game.sonic2.competition.TestSonic2CompetitionBoundary \
  test
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
```

The final combined focused run added those two tests to the same adjacent
suite:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -Dmse=off \
  -Dsonic2.rom.path="<project-root>/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.game.sonic2.competition.TestSonic2CompetitionBoundary,com.openggf.TestGameLoop#testDoExitTitleScreenRoutesTwoPlayerAwayFromDataSelect,com.openggf.game.session.TestActiveGameplayTeamResolver,com.openggf.sprites.managers.TestSpriteManagerUpdateOrder,com.openggf.level.objects.TestObjectPlayerQuery,com.openggf.game.sonic2.objects.TestMonitorObjectInstance,com.openggf.tests.trace.s2.TestS2Ehz1MonitorBreakRegression,com.openggf.game.sonic2.TestSonic2LevelEventRewindSnapshot,com.openggf.camera.TestCameraRewindSnapshot,com.openggf.game.TestGameStateRewindSnapshot \
  test
Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
```

The selected guard run was:

```text
JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -Dmse=off \
  -Dtest=com.openggf.game.TestProductionSingletonClosureGuard,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.tests.TestArchitecturalSourceGuard \
  test
Tests run: 112, Failures: 2, Errors: 0, Skipped: 0
```

`TestProductionSingletonClosureGuard` passed 42/42 and
`TestRewindCoverageGuard` passed 1/1. The two failures are existing size
ratchets in production files this branch does not change:

```text
ObjectManager.java: 3036 effective lines; budget 2914
AbstractPlayableSprite.java: 3180 effective lines; budget 3161
```

`git diff HEAD -- src/main` is empty, so this evidence-only branch neither
causes nor worsens those baseline failures. The remaining 67 architectural
guard methods passed. The baseline failures are reported rather than hidden or
reclassified as a successful guard run.

## Evidence not available

No native two-player BK2, trace-v5 stream, or rendered split-screen capture is
available in this worktree. Existing one-player EHZ and special-stage traces
keep `Two_player_mode` zero, use the ordinary CPU-sidekick/bootstrap path, and
contain no dual camera/result state. They cannot prove competition behavior.
No trace frontier moved, so `docs/status/trace-frontier-log.md` is unchanged.

The engine still lacks every activation gate that would turn this boundary
into a feature:

1. a fail-closed `TWO_PLAYER` host route and complete S2 provider;
2. native title, four-entry selection, act/zone/game/special result phases;
3. explicit P1/HUMAN, P2/HUMAN, and P2/CPU_SIDEKICK roles and isolated input;
4. independent player state, checkpoints, damage/death, and HUDs;
5. dual views, scrolling, culling, 2P art, and rendered seam parity;
6. two-window object/ring cursors, shared lifetime, order, and CNZ layout;
7. an owner/test for every reachable `Two_player_mode` and copy read;
8. slot-correct monitor/items/signpost/results behavior;
9. complete rewind identity/state/reference closure;
10. native REV01 trace-v5 plus rendered evidence for EHZ, MCZ, CNZ, and the
    special/results route; and
11. JDK 21 S2/full-regression evidence with no new failure.

## Exact next production action

The first future RED replaces
`TestGameLoop#testDoExitTitleScreenRoutesTwoPlayerAwayFromDataSelect` with
`unsupportedTwoPlayerTitleActionDoesNotStartOrdinaryLevel`. It must assert that
an unsupported module finishes in `GameMode.TITLE_SCREEN`, does not initialize
data select, does not mutate zone/act, and never calls
`LevelManager.loadZoneAndAct(...)`. It currently fails because
`GameLoop.executeTitleActionRoute` groups `TWO_PLAYER` with `LEVEL`.

The corresponding GREEN gives the existing `TWO_PLAYER` arm a fail-closed
title restoration without adding a provider interface. Participant roles,
slot-indexed state, and the one-view registry then replace live ordinary owners
in separate reviewed TDD slices. Only after those prerequisites does the full
S2 competition provider/product land atomically. The monitor branch remains a
final consumer of active competition plus P2/HUMAN, never an object-local
starting point.
