# Sonic 2 Native Competition and Human-P2 Evidence Plan

> **Execution scope:** publish a reproducible REV01/product boundary and an
> executable future TDD campaign without adding a partial competition runtime.
> The architecture is defined in
> `docs/architecture/designs/2026-08-09-s2-native-competition-human-p2-design.md`.

**Goal:** Replace the broad “competition owner required” deferral with exact
ROM evidence, a production-route boundary test, current documentation, and a
file/test matrix for the coherent implementation campaign.

**Delivery rule:** This plan changes tests and documentation only. It adds no
`src/main` class, mode flag, human-P2 factory, second camera, monitor carve-out,
launch setting, or title option. Ordinary Sonic+CPU-Tails remains unchanged.

## Canonical inputs

- Base: the current fetched `origin/develop` used by the isolated feature
  worktree.
- JDK: Maven must report Java 21.
- ROM: Sonic 2 World REV01, SHA-1
  `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9`.
- Property: `-Dsonic2.rom.path=/absolute/discovered/path/to/REV01.gen`.
- Disassembly: `docs/s2disasm/s2.asm`, `gameRevision = 1` at line 20 and
  `fixBugs = 0` at line 27.
- Native competition level-order table: ROM `$008E52`, unique bytes
  `00 00 0B 00 0C 00 FF FF`, corresponding to EHZ1, MCZ1, CNZ1, and the
  special-stage sentinel.

## Task 1: Add the ROM and supported-boundary characterization

**File:**

- Create:
  `src/test/java/com/openggf/game/sonic2/competition/TestSonic2CompetitionBoundary.java`

Use Jupiter, `@RequiresRom(SonicGame.SONIC_2)`, and
`@ExtendWith(SingletonResetExtension.class)`. Do not add a test-only setter or
reflective production mutation.

### Test 1: `rev01CompetitionLevelSelectUsesEhzMczCnzAndSpecialStage`

1. Read the configured ROM through `GameServices.rom().getRom()`.
2. Compute SHA-1 from `Rom.readAllBytes()` and compare the uppercase digest to
   `8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9` before interpreting offsets.
3. Read four unsigned words at `$008E52` through `RomByteReader`.
4. Assert the hand-derived literal list
   `[0x0000, 0x0B00, 0x0C00, 0xFFFF]` in order. Do not derive expected zone IDs
   from a production constant or from the decoder under test.
5. State in the assertion message that `$FFFF` is the special-stage entry, not
   a fourth normal level.

This is an evidence test. It fails on the wrong ROM/revision/address or a stale
contract; it does not claim that OpenGGF implements the route.

### Test 2: `ordinarySonicAndTailsBootstrapKeepsP2CpuControlled`

1. Reset configuration/session state through the extension.
2. Configure ordinary main `sonic` and sidekick `tails`.
3. Call the real `GameplayTeamBootstrap.registerActiveTeam(...)` with a real
   `Sonic2GameModule`, `SpriteManager`, and configuration service.
4. Assert one main and one secondary sprite. Assert the main is not CPU
   controlled; assert the secondary is CPU controlled and has a non-null
   `SidekickCpuController`.
5. Do not assert title-provider defaults as proof that competition is absent.
   This test protects the supported ordinary route against an attempted
   “human P2 by clearing the CPU bit” shortcut.

The two tests are characterization/evidence work and are expected to pass on
the unchanged production branch. There is no synthetic RED because this plan
does not implement behavior. Any later production slice uses the explicit
RED→GREEN tests in the campaign matrix below.

Run on JDK 21 with the verified ROM:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
  mvn -Dmse=off \
  -Dsonic2.rom.path="/absolute/discovered/path/to/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
  -Dtest=com.openggf.game.sonic2.competition.TestSonic2CompetitionBoundary \
  test
```

Expected: two tests, zero failures/errors/skips.

## Task 2: Refresh current documentation

**Files:**

- Modify: `docs/status/known-discrepancies.md`
- Modify: `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- Modify: `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`
- Modify: `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`
- Modify: `CHANGELOG.md`

1. Replace “needs a dedicated design” with a link to the reviewed design and
   this plan. Mark the capability materially narrowed and unavailable, not
   implemented or resolved.
2. Record the canonical product scope: local native competition, forced Sonic
   P1/Tails P2, EHZ/MCZ/CNZ/special-stage selection, two acts per normal zone,
   independent results, dual views, and two-camera object lifetime.
3. Record the current boundary accurately: P2 bindings feed
   `SidekickCpuController`; `ObjectPlayerQuery.nativeP2OrNull()` means first
   sidekick; score/lives/rings/time, camera, viewport, and placement are
   single-owner; the generic `TWO_PLAYER` route is not a capability owner.
4. Preserve the existing monitor behavior and tests. State that its human-P2
   branch is the final consumer of participant role plus active competition,
   not a first implementation slice.
5. Name the next safe production changes in order: fail-close the unsupported
   title route; migrate ordinary live paths to explicit participant roles;
   migrate one-player state and one view; then build/activate the complete S2
   product campaign.
6. Do not advertise a title option, launch profile, shared-screen co-op, or
   arbitrary character pairing. `CONFIGURATION.md` is already accurate and is
   left unchanged.
7. Append a dated follow-up to the earlier validation rather than rewriting
   its recorded 2026-08-08 test outcomes.

## Task 3: Publish the validation record

**File:**

- Create:
  `docs/architecture/validation/2026-08-09-s2-native-competition-human-p2.md`

Record:

- branch/worktree/base commit and the no-merge/no-push delivery boundary;
- JDK and verified ROM path/SHA-1;
- design and plan peer-review outcomes and resolved issues;
- source routines and current engine owners audited;
- the `$008E52` table result and ordinary bootstrap role result;
- the exact focused baseline and final commands/counts;
- that no native two-player BK2/trace-v5 or rendered split-screen capture exists;
- why existing one-player EHZ and special-stage traces do not prove
  `Two_player_mode` behavior;
- every activation gate still open; and
- the first future RED test and production change from the campaign matrix.

Do not call the evidence test an implementation, do not claim a trace frontier
moved, and do not update `docs/status/trace-frontier-log.md` unless a real trace
is recorded or changes outcome.

## Deferred production TDD campaign

The following is the executable future campaign. It is recorded now but is not
run by this evidence-only plan. Each slice must be independently designed and
reviewed against the parent design before production edits begin.

### Slice A: fail closed for unsupported title actions

**Modify:**

- `src/test/java/com/openggf/TestGameLoop.java`
- `src/main/java/com/openggf/GameLoop.java`

**RED:** Replace
`testDoExitTitleScreenRoutesTwoPlayerAwayFromDataSelect` with
`unsupportedTwoPlayerTitleActionDoesNotStartOrdinaryLevel`. Assert that a
module with no live competition provider finishes the route in
`GameMode.TITLE_SCREEN`, does not initialize data select, and never calls
`LevelManager.loadZoneAndAct(...)`. It fails because `executeTitleActionRoute`
currently groups `TWO_PLAYER` with `LEVEL`.

**GREEN:** Give the existing `TWO_PLAYER` switch arm a fail-closed handler.
That handler restores the title presentation after `doExitTitleScreen()` has
reset it, retains `GameMode.TITLE_SCREEN`, and does not mutate zone/act. Do not
add `LocalCompetitionProvider` yet. Re-run the complete `TestGameLoop` class
and title/startup route tests.

### Slice B: production-used participant roles

**Create:**

- `src/main/java/com/openggf/game/session/PlayableSlot.java`
- `src/main/java/com/openggf/game/session/PlayableControlRole.java`
- `src/main/java/com/openggf/game/session/GameplayParticipant.java`
- `src/main/java/com/openggf/game/session/GameplayParticipantRegistry.java`
- `src/test/java/com/openggf/game/session/TestGameplayParticipantRegistry.java`

**Modify:**

- `src/main/java/com/openggf/game/session/GameplayTeamBootstrap.java`
- `src/main/java/com/openggf/sprites/managers/SpriteManager.java`
- `src/main/java/com/openggf/level/objects/ObjectPlayerQuery.java`
- `src/main/java/com/openggf/level/objects/ObjectManager.java`
- `src/main/java/com/openggf/level/objects/ObjectTouchResponseController.java`
- `src/main/java/com/openggf/game/rewind/identity/PlayerRefId.java`
- `src/test/java/com/openggf/sprites/managers/TestSpriteManagerUpdateOrder.java`
- `src/test/java/com/openggf/level/objects/TestObjectPlayerQuery.java`
- `src/test/java/com/openggf/game/rewind/identity/TestRewindIdentityTable.java`
- `src/test/java/com/openggf/game/rewind/schema/TestRewindPlayerReferenceCodecs.java`
- `src/test/java/com/openggf/level/objects/TestObjectManagerRewindReferenceClosure.java`

**RED:** Add literal role/slot tests for ordinary P1/HUMAN plus
P2/CPU_SIDEKICK; P1/P2 input source selection; native P2 lookup by slot rather
than sidekick position; CPU-sidekick touch behavior; and `PlayerRefId.player(0)`
/ `player(1)` round trips with `assertSame`. Mutations that swap slots, feed P1
input to P2, or restore P2 as `sidekick(0)` must fail.

**GREEN:** Route every listed live ordinary path through the registry while
retaining the existing `cpuControlled` value as a temporary mirror. The new
registry must be installed and consumed in ordinary production bootstrap in
the same change; no unused human-P2 construction path is permitted.

### Slice C: slot-indexed one-player state

**Create:**

- `src/main/java/com/openggf/game/PlayerProgressState.java`
- `src/main/java/com/openggf/game/PlayerProgressRegistry.java`
- `src/main/java/com/openggf/game/rewind/snapshot/PlayerProgressSnapshot.java`
- `src/test/java/com/openggf/game/TestPlayerProgressRegistry.java`
- `src/test/java/com/openggf/game/rewind/TestPlayerProgressRewindSnapshot.java`

**Modify:**

- `src/main/java/com/openggf/game/GameStateManager.java`
- `src/main/java/com/openggf/game/LevelGamestate.java`
- `src/main/java/com/openggf/sprites/playable/AbstractPlayableSprite.java`
- `src/main/java/com/openggf/level/LevelLostRingSpawnCoordinator.java`
- `src/main/java/com/openggf/level/objects/ObjectTouchResponseController.java`
- `src/main/java/com/openggf/level/objects/HudRenderManager.java`
- `src/main/java/com/openggf/game/rewind/snapshot/GameStateSnapshot.java`
- `src/main/java/com/openggf/game/rewind/snapshot/LevelSnapshot.java`
- existing game-state/level-state/HUD/damage/checkpoint/monitor tests

**RED:** Prove P1 score/lives/rings/time behavior remains exact and a distinct
P2/HUMAN fixture mutates only slot P2; prove P2/CPU_SIDEKICK retains no
independent human death/ring-scatter path. Rewind must restore both records and
extra-life thresholds without aliasing.

**GREEN:** Make the slot registry the live one-player state path before any
competition mode is created. Compatibility getters on `GameStateManager` and
`LevelGamestate` delegate to P1; they may not remain a second authority.

### Slice D: one-view semantic rendering and lifetime

**Create:**

- `src/main/java/com/openggf/game/render/GameplayView.java`
- `src/main/java/com/openggf/game/render/GameplayViewRegistry.java`
- `src/main/java/com/openggf/game/rewind/snapshot/GameplayViewsSnapshot.java`
- `src/test/java/com/openggf/game/render/TestGameplayViewRegistry.java`
- `src/test/java/com/openggf/level/TestSingleGameplayViewParity.java`

**Modify:**

- `src/main/java/com/openggf/level/LevelManager.java`
- `src/main/java/com/openggf/level/LevelFrameRuntimeUpdater.java`
- `src/main/java/com/openggf/level/LevelRenderer.java`
- `src/main/java/com/openggf/level/objects/ObjectManager.java`
- `src/main/java/com/openggf/level/rings/RingManager.java`
- `src/main/java/com/openggf/level/objects/HudRenderManager.java`
- `src/main/java/com/openggf/camera/Camera.java`
- camera/renderer/object-placement/ring/rewind tests named in the design audit

**RED:** A one-element view registry must reproduce the current camera update,
render viewport, object/ring placement calls, post-camera scan, HUD, and rewind
snapshot exactly. Record literal call order and visible/loaded identities, not
screenshots computed by the code under test.

**GREEN:** Replace direct singleton-camera consumers with the one-element
registry. No second camera or split-screen branch lands in this slice. The S1,
S2, and S3K focused camera/render/placement tests and representative ROM routes
must remain unchanged.

### Slice E: coherent S2 competition activation

**Create:**

- `src/main/java/com/openggf/game/LocalCompetitionProvider.java`
- `src/main/java/com/openggf/game/sonic2/competition/Sonic2CompetitionSession.java`
- `src/main/java/com/openggf/game/sonic2/competition/Sonic2CompetitionSnapshot.java`
- `src/main/java/com/openggf/game/sonic2/competition/Sonic2CompetitionLevelProfile.java`
- `src/main/java/com/openggf/game/sonic2/competition/Sonic2CompetitionPlacementPolicy.java`
- `src/main/java/com/openggf/game/sonic2/competition/Sonic2CompetitionItemPolicy.java`
- `src/main/java/com/openggf/game/sonic2/competition/Sonic2CompetitionPresentation.java`
- tests under `src/test/java/com/openggf/game/sonic2/competition/` for session,
  title route, players/input, state, views, placement, items, results,
  special-stage handoff, rewind, and production-route integration

**Modify:**

- `src/main/java/com/openggf/game/GameModule.java`
- `src/main/java/com/openggf/GameLoop.java`
- `src/main/java/com/openggf/game/sonic2/Sonic2GameModule.java`
- `src/main/java/com/openggf/game/sonic2/titlescreen/TitleScreenManager.java`
- `src/main/java/com/openggf/game/sonic2/Sonic2LevelInitProfile.java`
- `src/main/java/com/openggf/game/sonic2/Sonic2LevelEventManager.java`
- `src/main/java/com/openggf/game/sonic2/objects/MonitorObjectInstance.java`
- S2 monitor contents/checkpoint/signpost/results/special-stage/resource owners
  identified by the final `Two_player_mode`/copy call-site inventory

**RED sequence:**

1. Title selection enters the native 2P level select without a direct level
   mutation.
2. Literal `$008E52` entries route to EHZ1/MCZ1/CNZ1/special stage.
3. Production bootstrap creates Sonic P1/HUMAN and Tails P2/HUMAN with direct,
   isolated logical inputs and no CPU controller.
4. EHZ act one results reset the exact per-act fields, retain lives/act-one
   result ledger, restore both mode words, and enter act two; act two publishes
   zone results.
5. Two divergent player positions produce two independent view/culling
   regions; an object block needed only by P2 remains the same live instance,
   then unloads only after both windows release it.
6. P2/HUMAN breaks a rolling monitor and receives contents in P2 state;
   P2/CPU_SIDEKICK remains blocked in ordinary play.
7. Zone tie and standalone special-stage series return through their exact
   result phases.
8. Rewind captures both players, states, views, placement cursors, results, and
   object-to-player references; resolve restored P1/P2 by slot with
   `assertSame` before replay.
9. A title-authored headless route covers selection through act-one result,
   act-two entry, zone result, level-select return, and title return without
   private state injection.

**GREEN:** Implement every activation gate from the design in one reviewed
campaign. The provider contract and S2 implementation land together. The title
emits `TWO_PLAYER` only after all production-route, rewind, trace-v5, and
rendered split-screen evidence passes. No partial subset is mergeable.

## Task 4: Final verification and commit

1. Verify JDK and ROM:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk mvn -v
   sha1sum "/absolute/discovered/path/to/Sonic The Hedgehog 2 (W) (REV01) [!].gen"
   ```

2. Run the new boundary test and adjacent supported-route suite:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
     mvn -Dmse=off \
     -Dsonic2.rom.path="/absolute/discovered/path/to/Sonic The Hedgehog 2 (W) (REV01) [!].gen" \
     -Dtest=com.openggf.game.sonic2.competition.TestSonic2CompetitionBoundary,com.openggf.TestGameLoop#testDoExitTitleScreenRoutesTwoPlayerAwayFromDataSelect,com.openggf.game.session.TestActiveGameplayTeamResolver,com.openggf.sprites.managers.TestSpriteManagerUpdateOrder,com.openggf.level.objects.TestObjectPlayerQuery,com.openggf.game.sonic2.objects.TestMonitorObjectInstance,com.openggf.tests.trace.s2.TestS2Ehz1MonitorBreakRegression,com.openggf.game.sonic2.TestSonic2LevelEventRewindSnapshot,com.openggf.camera.TestCameraRewindSnapshot,com.openggf.game.TestGameStateRewindSnapshot \
     test
   ```

   Expected before implementation: the existing boundary suite remains green;
   the new class adds exactly two passing tests.

3. Run proportionate guards:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
     mvn -Dmse=off \
     -Dtest=com.openggf.game.TestProductionSingletonClosureGuard,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.tests.TestArchitecturalSourceGuard \
     test
   ```

4. If a Maven run regenerates
   `docs/status/rewind-round-trip-gaps.md`, inspect it and restore it to `HEAD`
   when it contains only test-generated output. Never stage it as part of this
   evidence plan.
5. Run `git diff --check`, inspect the complete diff and untracked files, and
   confirm there is no `src/main` change.
6. Stage the design, plan, boundary test, validation record, current ledgers,
   and changelog. Run `.githooks/run-policy pre-commit`; never bypass hooks.
7. Commit on `feature/ai-s2-competition-capability` with policy trailers:
   `Changelog: updated`, `Known-Discrepancies: updated`, and `n/a` (with any
   required reason) for unaffected mapped categories. Do not merge or push.

## Completion criteria

- Independent design and plan reviews are green.
- The verified REV01 test pins the native competition entry table without
  claiming runtime support.
- The ordinary bootstrap test proves P2 remains a CPU sidekick in the only
  supported two-character route.
- Current docs agree on native competition scope, owners, lifecycle, evidence,
  next RED test, and unavailable status.
- No production runtime or user-facing capability changes.
- JDK 21 focused tests, adjacent route tests, selected guards, diff check, and
  staged commit policy pass.
- The isolated branch contains the committed evidence package and remains
  unmerged and unpushed.
