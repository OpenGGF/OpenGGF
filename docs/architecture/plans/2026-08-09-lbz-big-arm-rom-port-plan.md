# LBZ Big Arm ROM Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:test-driven-development` while executing this plan. Keep the
> reviewed design and this plan green if implementation changes an assumption.

**Goal:** Replace the inert S3KL `$CC` handoff with the shipped-ROM Big Arm
fight, articulated object graph, defeat/capsule gate, Knuckles escape, rewind
coverage, and a production-route trace checkpoint.

**Architecture:** `LbzFinalBoss2Instance` owns the native root routine machine
and independently scheduled graph children. A small immutable ROM-data reader
owns source tables while `Sonic3kPlcArtRegistry` owns presentation assets. An
LBZ route-8 subclass adds the exact capsule X/lock write to the shared floating
capsule. Existing game-state, water, results, level-event, transition, touch,
and rewind owners retain their responsibilities.

**Tech Stack:** Java 21, JUnit Jupiter, Maven Surefire, S3K ROM-backed object
art and Kosinski-module queues, schema-v5 trace replay, Markdown, repository
commit-policy hooks.

## Global constraints

- Use `docs/skdisasm/sonic3k.asm:154231-155585` with `FixBugs = 0` as the
  behavior oracle. The complete-run trace is comparison-only evidence.
- Runtime art, mappings, palettes, animation scripts, lookup tables, and
  position tables come from the user-supplied ROM. Never read `docs/skdisasm`
  at runtime or commit extracted asset bytes.
- Preserve native centre coordinates, 16.16 position state, signed 8.8
  velocities, unsigned word comparisons, pre-decrement waits, same-entry
  fallthroughs, object-slot order, and RNG call order.
- Preserve the two independent post-capsule signals and their native writers:
  `_unkFAA8` is `GameStateManager.endOfLevelActive`; `_unkFAA2` is the LBZ2
  `WaterSystem` dynamic-water lock. Do not replace either with a callback,
  timer, `endOfLevelFlag`, or a single combined latch.
- Keep controller `$AD`, outer piece `$9A`, and landing child `$9C` as the only
  articulated collision owners. Segments and the joint remain collision zero.
- Do not add test-only force methods, trace-state hydration, game-name/zone
  carve-outs in shared code, object singleton calls, or a partial visible boss
  that cannot complete the production route.
- Work only in `feature/ai-lbz-big-arm-evidence`, do not merge or push, run Maven
  under JDK 21, and never bypass hooks.
- Route every subsequent tool/test temporary write through the repository's
  ignored `target/task-tmp`. Every command block below is a standalone shell:
  it captures `task_root=$PWD`, creates that directory, and exports `TMPDIR`
  and `MAVEN_OPTS` from `task_root` before doing any work. Never inspect, delete, or reuse
  unknown shared `/tmp` content; classify and remove only this generated task
  directory before the final clean-status check.

---

### Task 1: Re-establish the oracle, ROM, baseline, and rejected-attempt boundary

**Files:**

- Retain: `docs/architecture/designs/2026-08-09-lbz-big-arm-rom-port-design.md`
- Retain: `docs/architecture/plans/2026-08-09-lbz-big-arm-rom-port-plan.md`
- Inspect only: `docs/skdisasm/sonic3k.asm`
- Inspect only: `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- Inspect only: `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`
- Inspect only: commit `98d968d7f` and the documented v2 diff/evidence

**Interfaces:**

- Consumes: shipped `Obj_LBZFinalBoss2`, its shared helpers, locked-on ROM, and
  canonical Knuckles `lbz_2` schema-v5 fixture.
- Produces: a reproducible evidence ledger before production edits.

- [ ] **Step 1: Verify branch, JDK, ROM, and source configuration**

  Run:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  git status --short --branch
  git rev-parse HEAD origin/develop
  mvn -v
  sha1sum "Sonic and Knuckles & Sonic 3 (W) [!].gen"
  rg -n "FixBugs = 0|Obj_LBZFinalBoss2:|LBZFinalBoss2_Index:" \
    docs/skdisasm/sonic3k.asm docs/skdisasm/s3.asm
  ```

  Require the isolated branch tip/base
  `9de7ecf7230100626fb7084b3f678daa6a5f478c`, Java 21, and ROM SHA-1
  `cfbf98c36c776677290a872547ac47c53d2761d6`. Record the independently fetched
  **current** `origin/develop` value as upstream advancement; it is volatile and
  is not an acceptance hash. Do not rebase this reviewed candidate or touch the
  dirty main workspace.

- [ ] **Step 2: Re-run the accepted pre-change baseline**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbz2EndSequenceRegistry,com.openggf.game.sonic3k.objects.TestLbzFinalBoss1Instance" \
    test
  ```

  Expected baseline: 24 tests, zero failures/errors/skips. Record any different
  result before continuing.

- [ ] **Step 3: Validate the committed comparison fixture**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  python3 tools/traces/validate_trace_v5.py \
    src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/lbz_2
  ```

  Require valid metadata/manifest, 6,444 rows at global offset 222,779, zone 6,
  engine act 1, and Knuckles. Do not derive production constants from its rows.

### Task 2: Add exact ROM-data and presentation intake through TDD

**Files:**

- Create: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2RomData.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/constants/Sonic3kConstants.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kObjectArtKeys.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kPlcArtRegistry.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/S3kBossExplosionChild.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/TestSonic3kPlcArtRegistry.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2RomData.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestS3kBossExplosionChild.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestMhzBossObjects.java`

**Inspect-only compatibility audit (ordinary constructor remains unchanged):**

The following 25 legacy classes contain 29 ordinary-constructor call sites;
the only additional owner is Big Arm's new named-factory call.

- `AbstractS3kFloatingEndEggCapsuleInstance.java`,
  `AbstractS3kUprightEggCapsuleInstance.java`, `AizEndBossInstance.java`,
  `AizMinibossCutsceneInstance.java`, `AizMinibossInstance.java`,
  `CnzMinibossBlockExplosionControllerChild.java`, `CnzMinibossInstance.java`,
  `CutsceneKnucklesLbz1CollapseChild.java`,
  `CutsceneKnucklesSkIntroInstance.java`, `HczMinibossInstance.java`,
  `IczMinibossExplosionControllerChild.java`,
  `LbzMinibossBoxKnuxInstance.java`, `LbzMinibossInstance.java`,
  `MgzDrillingRobotnikInstance.java`, `MgzEndBossKnuxInstance.java`,
  `MgzMinibossInstance.java`, `MhzMinibossInstance.java`,
  `bosses/CnzEndBossExplosionControllerChild.java`,
  `bosses/HczEndBossEggCapsuleInstance.java`,
  `bosses/HczEndBossInstance.java`, `bosses/IczEndBossInstance.java`,
  `bosses/LbzEndBossInstance.java`, `bosses/LbzFinalBoss1Instance.java`,
  `bosses/MhzEndBossInstance.java`, and
  `bosses/MhzEndBossWeatherMachineChild.java`.

**Interfaces:**

- Consumes: `ObjectServices.romReader()/rom()`, standalone S3K art registration,
  and production Kosinski-module readiness.
- Produces: immutable signed-byte/word table access plus Big Arm/Egg Robo head
  renderers; an absent or wrong ROM fails explicitly.

- [ ] **Step 1: Write failing ROM-range and art-plan tests**

  Prove exact S&K-half code/data labels:

  - motion tables `$074F72` and `$074F7A`;
  - escape explosion positions `$074E7C`;
  - hit-flash palette offset/value tables `$075092` and `$07509E`;
  - segment animation scripts `$075194` and `$07519C`;
  - Egg Robo head raw animation `$0681D0` and mapping `$0681D4`;
  - generic boss-explosion raw animation `$083FCC` (15 bytes);
  - the 20 signed bytes of S&K-half `ScreenShakeArray` at `$04F424`, including
    entry 19 `-$05`, rather than a copied Java array;
  - Big Arm palette `$0751AA`;
  - S3-half circle tables `$360B08` and `$3629A0`, referenced by the S&K object;
  - `Map_LBZFinalBoss2` `$364A96` and `ArtKosM_LBZFinalBoss2` `$376874`;
  - `ArtKosM_EggRoboHead` `$15FDDC`.

  Use exact slice lengths: 64 bytes for each circle table, `$15C` for the Big
  Arm mapping, `$1122` for the Big Arm KosM stream, 32 bytes for its palette,
  `$28` for the Egg Robo head mapping, and `$1E2` for its KosM stream. Assert
  the two circle-table SHA-1s
  `ede65917bf9e68f1b084e1d0844f6f5c321daa7c` and
  `0f9e0656a4f242d32caed881e29eca1b408cc83e`, Big Arm mapping
  SHA-1 `2d8d99437204300961db7e431d91c8e077cd2360`, Big Arm art SHA-1
  `77a45958379d955bfc216966f6f1f0fb887c66e5`, palette SHA-1
  `9352e917efeba50717353089423f8b0f24894d79`, Egg Robo mapping SHA-1
  `6b66fa56d221f51f70f85049ef05240798567a7f`, and Egg Robo art SHA-1
  `12479274979954cf89bac77b8fc1b9337f9013bb`. The tests must also
  assert 18 Big Arm mapping frames and four Egg Robo head mapping frames.

  In
  `TestLbzFinalBoss2RomData#rawAnimationAndTimedShakeScriptsComeFromLockedOnRom`,
  assert those raw animation/shake bytes through the production reader,
  including Egg Robo `[$0F,$00,$01,$FC]`, boss-explosion terminal `$F4`, and
  `ScreenShakeArray[19]==-5`. Add a registry test requiring both new object-art keys to resolve from ROM
  ranges, with the Big Arm tile destination `ArtTile_LBZFinalBoss2=$3D9` and
  the Egg Robo head reusing `ArtTile_RobotnikShip`.

  Add exact shared-child methods:

  - `nativeInitSfxFactoryIsSilentUntilFirstOwnEntryAndPlaysOnce`;
  - `legacyConstructorPreservesCallerOwnedSilence`; and
  - `nativeInitSfxOneShotRoundTripsBeforeAndAfterFirstEntry`.

  Both modes use the ROM raw script. Construction must be silent. The ordinary
  `(x,y)` mode remains silent through every entry; the named native-init mode
  plays exactly once on its first own entry, never on allocation or later
  entries, and rewind before/after that entry neither loses nor replays it. Add
  `TestMhzBossObjects#weatherMachineExplosionRetainsWeatherSfxWithoutExplode`
  to prove the legacy MHZ weather-machine owner still plays only its separately
  sourced `WEATHER_MACHINE` effect and invents no `EXPLODE`.

- [ ] **Step 2: Run the new tests and verify red**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2RomData,com.openggf.game.sonic3k.objects.TestS3kBossExplosionChild,com.openggf.game.sonic3k.objects.TestMhzBossObjects#weatherMachineExplosionRetainsWeatherSfxWithoutExplode,com.openggf.game.sonic3k.TestSonic3kPlcArtRegistry" \
    test
  ```

  Accepted first RED: `TestS3kBossExplosionChild` fails to compile because
  `createWithNativeInitSfx` does not exist. After adding only the factory seam,
  `nativeInitSfxFactoryIsSilentUntilFirstOwnEntryAndPlaysOnce` fails because
  the child never owns first-entry audio/raw state. The ROM reader/art tests
  may also report missing constants/keys/registrations. A test that succeeds by
  reading the disassembly is not an accepted red state.

- [ ] **Step 3: Implement the smallest ROM-only owner**

  `LbzFinalBoss2RomData` reads exact slices once from injected ROM services and
  exposes bounds-checked signed byte/word lookup methods. It must not contain
  copied table arrays, cache mutable static state, or fall back to `docs/`.
  Make the existing generic `S3kBossExplosionChild` consume the ROM-backed
  `AniRaw_BossExplosion` bytes through its injected services; do not add a
  Big-Arm-only Java transcription or alter other callers' sheet ownership.
  Keep its ordinary constructor audio-silent and add only the explicit
  `createWithNativeInitSfx(x,y)` factory plus rewind-captured one-shot state.
  Factory construction remains silent; first own update plays before the raw
  cursor transition. No legacy caller selects the factory.
  Register the two sheets through `Sonic3kPlcArtRegistry`; preserve queue
  readiness and mapping decode behavior used by existing standalone art.

- [ ] **Step 4: Re-run Task 2 tests and verify green**

  Expected: all selected tests pass and each renderer remains unavailable until
  its production ROM-art job is ready.

### Task 3: Add the LBZ route-8 capsule writer without changing other routes

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/AbstractS3kFloatingEndEggCapsuleInstance.java`
- Create: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2EggCapsuleInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss1Instance.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2EggCapsuleInstance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestAiz2BossEndSequenceObjects.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss1Instance.java`

**Interfaces:**

- Consumes: shared floating capsule update stages, camera X, `WaterSystem`, and
  the wrapper entry before the Big Arm handoff.
- Produces: exact `_unkFAA2` writer/clearer timing with no change to AIZ/MGZ.

- [ ] **Step 1: Write failing capsule and wrapper lifecycle tests**

  Add exact methods
  `openingDispatchOnlySwitchesCallbackAndRunsGenericSwingMove`,
  `openedRouteMovesLeftAfterSwingAndKeepsMoveSprite2AboveThreshold`, and
  `thresholdEntryLatchesOnlyWaterLockAndSuppressesThatMoveSprite2Step`.
  Through ordinary capsule updates, prove the native callback boundary:

  - for LBZ zone index 6, the routine-8 opening entry reads `$10` from
    `byte_866A2`, switches to routine `$10`, runs only the
    generic `Swing_UpAndDown`/`MoveSprite2` tail, and does **not** execute
    `loc_866F4` or subtract X;
  - the next own dispatch is routine `$10`/`loc_866EC`; it calls `sub_868F8`
    with `d0=$12` and then reaches the first `loc_866F4` entry. While unsigned
    `capsule.x > camera.x-$60`, it subtracts two X and still applies
    `MoveSprite2` Y movement. If results start, `sub_868F8` changes routine to
    `$12` on that same entry and still falls through; later `$12` entries reach
    the same hook through `loc_86716`;
  - at `capsule.x <= camera.x-$60`, sets only the LBZ act-2 dynamic-water lock
    and suppresses that entry's vertical `MoveSprite2` step;
  - polls this path before results are eligible, on the same dispatch that
    starts results, while results are active, and after results clear;
  - latches the lock and never mutates `endOfLevelActive` itself.

  Start with a stale true lock and drive the real Knuckles FinalBoss1 wrapper
  initialization/handoff; assert it clears the lock before `$CC` starts.
  Extend AIZ/MGZ tests to prove their X/Y/results behavior is unchanged.

- [ ] **Step 2: Run the capsule tests and verify red**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2EggCapsuleInstance,com.openggf.game.sonic3k.objects.TestLbzFinalBoss1Instance,com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects" \
    test
  ```

  Accepted first RED:
  `openingDispatchOnlySwitchesCallbackAndRunsGenericSwingMove` observes the
  current opening entry subtract X by two. The LBZ class/hook and stale-lock
  wrapper checks may expose additional failures, but existing route assertions
  must stay green.

- [ ] **Step 3: Implement the route hook and subclass**

  Add protected final capsule-X access/mutation and one route hook to the base.
  Keep the opening callback switch separate: it falls through only to the
  generic swing/move tail. Invoke the LBZ hook beginning with the following
  routine-`$10` dispatch, before that same generic tail. Invoke it from both
  post-open routine `$10` and `$12` paths, including the `$10->$12` results
  start fallthrough. Preserve
  `updateSwingVelocity()` on every entry and permit the hook to suppress only
  `addYLongword(yVelocity << 8)` on the latch entry. Override it in
  `LbzFinalBoss2EggCapsuleInstance` with the exact unsigned threshold and
  two-pixel step. Clear the LBZ2 lock in the existing Knuckles wrapper init.
  Do not special-case LBZ in the base class.

- [ ] **Step 4: Re-run Task 3 and verify green**

  Expected: all selected tests pass, including the non-LBZ regression cases.

### Task 4: Port root initialization, death-plane publication, and initial graph

**Files:**

- Replace: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbz2EndSequenceRegistry.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2Instance.java`

**Interfaces:**

- Consumes: object manager child allocation, injected camera/art/palette/event
  services, touch publication, and ROM data from Task 2.
- Produces: native `$00->$02->$06->$08` startup, death-plane disable, root/head,
  and the eight articulated child slots.

**Concrete child ownership:**

All Big Arm-specific child implementations below are nested static concrete
types in `LbzFinalBoss2Instance.java`; they remain separate `ObjectManager`
objects even though they share the root source file:

| Native owner | Concrete type | Parent/link ownership |
|---|---|---|
| `Obj_RobotnikHead4` | `RobotnikHead4Child` | root parent; used once at init and once after the capsule gate |
| `loc_749D0` | `ArmControllerChild` | root parent and root's controller link |
| `loc_749AE` | `ArmAttachmentChild` | root parent |
| `loc_74B9E` | `ArmVisualJointChild` | root parent |
| `loc_74BC0` | `ArmOuterCollisionChild` | root parent; sole `$9A` owner |
| `loc_74AFA` | `ArmSegmentChild` | controller parent plus root link; two instances distinguished by subtype/ordinal |
| `loc_74A9A` | `ArmKinematicJointChild` | native parent rewired to root plus controller link |
| `loc_74C24` | `GrabOwnerChild` | controller parent plus root link |
| `loc_74C00` | `LandingCollisionChild` | root parent; sole `$9C` owner |
| `loc_74D14` | `DefeatDebrisChild` | root parent; five instances distinguished by subtype/ordinal |
| `loc_74E12` | `DefeatFollowVisualChild` | root parent |
| `loc_74D48` | `EscapeFloorChild` | root parent and root's floor link |
| `BossExplosionHitbox` | `EscapeFloorExplosionChild` | escape-floor parent; seven instances |
| `loc_74E30` | `EscapeExplosionEmitterChild` | escape-floor parent; owns one subtype-4 explosion controller |
| `Obj_CreateBossExplosion` subtype 4 | `BigArmExplosionControllerChild` | root or escape-emitter parent; creates generic visible explosions through the `CreateChild6_Simple` equivalent but retains no edge to them; `ObjectManager` is their sole owner |
| `Obj_RobotnikShipFlame` | `RobotnikShipFlameChild` | root parent and root's flame link |

`LbzFinalBoss2EggCapsuleInstance.java` separately owns the floating route-8
capsule concrete type because it subclasses the shared capsule family.
`S3kBossExplosionChild` remains the existing generic visible explosion type,
but only `BigArmExplosionControllerChild` creates it. The root and each
`loc_74E30` emitter create a subtype-4 controller through the
`Child6_CreateBossExplosion` equivalent; they never create the visible child
directly. The three `Child1_Act2LevelSize` equivalents remain state in
`Sonic3kLBZEvents`, not fabricated Big Arm children.

- [ ] **Step 1: Write failing startup/graph tests**

  Assert initialization advances routine to `$02`, HP is eight, `$38` bit 3 is
  set, root centre is camera `+$A0,-$50`, wait word is `$59`, frame is 5,
  collision is zero, palette/art are submitted, and the Robotnik/Egg Robo head
  is allocated. Drive natural object-manager passes through `Obj_Wait`; prove
  its pre-decrement callback reaches `$06`, creates `ChildObjDat_75122`, falls
  with light gravity `$20`, uses strict unsigned `y > camera+$120`, publishes
  root `$0F`, and reaches `$08` with `$7F`.

  On the controller's first own dispatch, assert the full graph creation order
  and offsets from `ChildObjDat_75122/75144`. Assert controller collision stays
  zero before parent art bit 7, then becomes `$AD`; only outer `loc_74BC0`
  becomes `$9A`; both segments and the joint stay zero; landing is absent until
  the fall boundary and then owns `$9C`.

  Add
  `nativePriorityBucketsAndInitialVisibilityComeFromObjectData`,
  `outerUsesAdjustedFlipAndEvenVIntCadenceWhileLandingAndGrabNeverDraw`, and
  `eggRoboHeadAnimationStartsOneOwnDispatchAfterInitialization`. Collect real
  render commands rather than inspecting the same production priority field.
  Pin buckets root/head 5, controller 3, attachment 4, visual 6, outer 6,
  segment Java semantic indices `0/1` (native subtype bytes `0/2`) at 1/3,
  joint 3, landing 0, and grab 0. Prove landing
  and grab issue no draw, the outer piece mirrors the parent's adjusted flip
  and draws only on even object-visible `V_int_run_count`, and a newly created
  head remains at frame 0 on init then begins the ROM script on its following
  own dispatch. The assertions must fail if a native priority word is clamped
  directly rather than divided unsigned by `$80`.

  Add a real `interceptPitDeath` test: before `$CC` initialization it returns
  false, and after the root has published its native disable it returns true.

- [ ] **Step 2: Run startup tests and verify red**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance,com.openggf.game.sonic3k.objects.TestLbz2EndSequenceRegistry" \
    test
  ```

  Expected: placeholder state/render/collision/graph assertions fail.

- [ ] **Step 3: Implement the native root and child framework**

  Keep native even routine bytes and full 16.16 fixed-point fields. Use graph
  child classes whose update methods translate the source helper they own. Store
  engine priority buckets after unsigned native-word division by `$80`; never
  feed `$200/$300` through a raw-value clamp. Preserve non-drawing landing/grab
  helpers and the outer child's adjusted-flip/even-V-int draw path. Render with
  the production Big Arm/Robotnik ship/Egg Robo sheets only when ready. Root
  initialization queues only Big Arm art; it must not eagerly queue ship,
  explosion, PLC `$71`, or post-gate head work. Add a
  semantic root query such as `hasPublishedDeathPlaneDisable()` and have the
  existing S3K pit-death owner inspect active `$CC` roots, matching the already
  established object-slot bridge pattern; do not add a shared zone carve-out.

- [ ] **Step 4: Re-run Task 4 and verify green**

  Expected: tests pass with exact root and child collision publication order.

### Task 5: Port the complete fight choreography and grab through TDD

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2Instance.java`

**Interfaces:**

- Consumes: native RNG, camera/player centres, touch response, collision system,
  and the articulated graph.
- Produces: routines `$0A-$2A` with exact action selection, boundaries, grab,
  player control, throw, and subpixel motion.

- [ ] **Step 1: Write failing natural-path fight tests**

  Drive ordinary updates from `$08` and assert the first four completed action
  cycles enter `$0A`, the fifth enters `$0C`, and RNG is consumed only at
  `loc_74F24` (`Random&6`) and `loc_74F82` (`Random&$7F + $C0`). Cover each
  bob/bounce/drop/land/rise same-entry fallthrough and strict boundary.

  In `routine0AUsesRomMotionTablesAndStrictCameraBounceFallthroughs`, pin all
  five word-table offsets, not a masked switch: offset 0 is no-op, 2 subtracts
  4 from Y velocity, 4 adds `$3C` and writes `$3C=2` only when the sum becomes
  zero, 6 is no-op, and the hit override 8 subtracts four from the root Y word.
  Add `controllerActivationDefersAngleAndBit1FreezesPosition`: the controller's
  init entry creates its graph and enters routine 2; routine 2 performs only
  the circular lookup; its activation entry publishes `$AD` and routine 4 but
  still looks up with the old angle; only the following own dispatch adjusts
  angle. Root flag bit 1 returns before both angle and position work, while bit
  2 suppresses angle only and still refreshes the circle position.

  Add `controllerFlipAndImmediateParentRefreshFollowNativeEntryOrder`. Seed the
  root and controller with opposite flips. Prove the controller initialization
  entry rewrites its native offsets and creates the nested graph without a
  refresh, so it draws once at its table-spawned position. Routine 2 and its
  activation entry retain the controller's previously latched flip; only the
  following routine-4 dispatch copies root flip immediately before circular
  lookup. Root bit 1 freezes controller coordinates *and* flip, while bit 2
  still copies flip/refreshes. Both arm segments and the grab owner must perform
  adjusted refresh against that immediate controller flip, never root flip.

  Add `segmentsHoldOffsetAndAnimationAtNativeCallbackBoundaries`. From zeroed
  raw state, the first normal `loc_74B3C` entry ends at cursor 1, mapping 4/8
  for native subtype byte 0/2 (Java semantic index 0/1), and timer 9. On a
  same-entry grab acquisition, it then overrides mapping to 7/$B while cursor 1
  and timer 9 remain unchanged; native subtype 2 commits `child_dx += 8`.
  Subsequent held entries refresh from that stored offset without animation.
  The first release entry refreshes once with +8, rewires the callback and
  subtracts eight, with ordinary refresh/animation resuming only next entry.
  Assert both controller flips and arbitrary rewind-visible signed offsets.

  Position a real Knuckles sprite inside the grab owner's native half-open
  range `x [-$10,+$20)`, `y [-$10,+$20)` with invulnerability timer zero and
  routine below six. Prove `$30=$FF`, root `$1E->$2A`, player object control
  `$81`, animation 2, zero velocities, and that acquisition falls through in
  the same grab-owner dispatch to snap the player. Seed nonzero player/root low
  words and prove the native word-coordinate writes preserve them. Prove side
  selection: root
  `x > camera+$A0` keeps facing and moves to `camera+$E0`; otherwise it flips
  and moves to `camera+$60`.

  Cover both release branches. Non-invincible release must call the standard
  hurt path; invincible release must restore control and rebound. Routine `$2A`
  applies ordinary `MoveSprite` with gravity `$38`.

  Add `grabReleaseCooldownFreezesAndReacquiresAfterSwitchOnlyExpiry`. The
  release entry seeds `$40` and immediately pre-decrements to `$3F`. Every
  `loc_74D04` wait entry freezes the grab owner's position despite controller
  motion/flip. The `0->$FFFF` expiry entry changes callback only and returns;
  adjusted refresh plus half-open-range reacquisition occurs on the following
  own entry using the controller's then-current flip. Capture/restore inside
  the wait and require identical next-entry behavior.

  Add `fightAndHeldWritesPreserveArbitraryLowWords`. Seed
  arbitrary values in all 16 low-position bits, exercise root 16.16 motion
  using signed 8.8 velocity shifted by eight, root/player high-word copies, and
  held-player X/Y writes, and require low words to survive exactly.

- [ ] **Step 2: Run the exact fight methods and verify red**

  Add and run these methods:

  - `firstFourBobCyclesChooseRoutine0AAndFifthChooses0CWithoutExtraRng`
  - `routine0AUsesRomMotionTablesAndStrictCameraBounceFallthroughs`
  - `dropLandAndRiseFollowNativeRoutineBoundaries`
  - `grabOwnerNaturallyAcquiresKnucklesInHalfOpenRange`
  - `grabSideSelectionUsesRootAgainstCameraA0`
  - `throwUsesHurtForVulnerablePlayerAndReboundForInvinciblePlayer`
  - `routine2AUsesMoveSpriteGravity38`
  - `controllerActivationDefersAngleAndBit1FreezesPosition`
  - `controllerFlipAndImmediateParentRefreshFollowNativeEntryOrder`
  - `segmentsHoldOffsetAndAnimationAtNativeCallbackBoundaries`
  - `grabReleaseCooldownFreezesAndReacquiresAfterSwitchOnlyExpiry`
  - `fightAndHeldWritesPreserveArbitraryLowWords`

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance#firstFourBobCyclesChooseRoutine0AAndFifthChooses0CWithoutExtraRng+routine0AUsesRomMotionTablesAndStrictCameraBounceFallthroughs+dropLandAndRiseFollowNativeRoutineBoundaries+grabOwnerNaturallyAcquiresKnucklesInHalfOpenRange+grabSideSelectionUsesRootAgainstCameraA0+throwUsesHurtForVulnerablePlayerAndReboundForInvinciblePlayer+routine2AUsesMoveSpriteGravity38+controllerActivationDefersAngleAndBit1FreezesPosition+controllerFlipAndImmediateParentRefreshFollowNativeEntryOrder+segmentsHoldOffsetAndAnimationAtNativeCallbackBoundaries+grabReleaseCooldownFreezesAndReacquiresAfterSwitchOnlyExpiry+fightAndHeldWritesPreserveArbitraryLowWords" \
    test
  ```

  Accepted first RED:
  `routine0AUsesRomMotionTablesAndStrictCameraBounceFallthroughs` observes the
  current offset-6 branch move Y and the hit-override offset 8 collapse to
  offset 0. `controllerActivationDefersAngleAndBit1FreezesPosition` must also
  expose the current early angle/reposition behavior.
  `controllerFlipAndImmediateParentRefreshFollowNativeEntryOrder` first fails
  because initialization refreshes and routine 2 consumes root flip;
  `segmentsHoldOffsetAndAnimationAtNativeCallbackBoundaries` first fails because
  held entries keep animating and apply/remove +8 on the wrong entry; and
  `grabReleaseCooldownFreezesAndReacquiresAfterSwitchOnlyExpiry` first fails
  because cooldown entries refresh and the zero entry reacquires one dispatch
  early. Compilation failure, missing ROM data, a forced routine, or a
  different failing prerequisite is not the accepted red state.

- [ ] **Step 3: Implement in source order**

  Translate `loc_74370-loc_746A4` and controller `loc_749D0-loc_74AFA` entry by
  entry. Dispatch the `$0A` table by exact offset 0/2/4/6/8. Use the ROM-backed
  motion and circle tables from Task 2, full 16.16 position longs, signed 8.8
  velocity shifted by eight, and `NativePositionOps` high-word writes that
  preserve player/root low words. Compose grab control exactly as `$81`.
  Preserve same-entry grab acquisition/snap, engine
  hurt/control APIs, and every RNG draw at the exact native branch. Give the
  controller its own captured flip; retain its table position on init and its
  prior flip through routine-2 activation. Make adjusted children consume
  their immediate parent. Model segment normal/held/release callbacks and grab
  normal/carry/cooldown/switch-only-expiry as explicit captured phases, with no
  Animate_Raw or position refresh in callbacks where the source omits them.

- [ ] **Step 4: Re-run the full Big Arm unit class and verify green**

  Expected: all startup, graph, choreography, and grab tests pass together.

### Task 6: Port hits, palette flash, defeat, and debris

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/S3kBossExplosionChild.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2Instance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestS3kBossExplosionChild.java`

**Interfaces:**

- Consumes: continuously polled enemy touch, palette ownership, shared boss
  defeat/explosion/score support, and ROM table data.
- Produces: eight-hit lifecycle, `$3C` flash restoration, `FixBugs=0` player
  branch, native articulated defeat helpers, ROM raw-explosion animation,
  delayed level-music fade, five debris objects, and capsule launch readiness.

- [ ] **Step 1: Write failing hit/defeat tests**

  Apply eight genuine attackable touch responses while respecting continuous
  overlap polling. After each nonfinal hit assert root collision clears, the
  six exact line-1 colors alternate from normal to white via the ROM offset
  table, and `$3C` expiry restores root collision `$0F` plus controller `$AD`
  while outer remains its native `$9A`; both segments and the joint remain
  collision zero.

  On the eighth hit assert collision remains zero, score increases by the
  shared boss amount, defeat timer is `$3F`, explosion subtype is 4, and the
  callback is `loc_746D8`. Prove subtype 4 selects timer `$80`, ranges
  `$20/$20`, follows the root, and emits once on its creation dispatch with
  `$39` still `$80`, then every three own entries with `$39` unchanged because
  signed-negative `$80` takes `bmi` before the decrement. Prove it never
  self-deletes and terminates only when `Obj_WaitForParent` observes root `$38`
  bit 5 at `loc_74710` (or a zero parent code pointer). Exercise the shipped
  `FixBugs=0` branch: root `$30` zero restores player control; root `$30`
  nonzero skips restore. Require `eighthHitRunsShippedFixBugsZeroBranch` for
  the unheld `$30==0` branch and
  `naturallyHeldFinalHitRetainsControlUntilCapsuleGate` for the shipped bug:
  acquire Knuckles through the real `loc_74C24` half-open range, deliver the
  ordinary eighth touch-response hit before the later grab-owner slot, then
  execute that held `loc_74CCC` slot. It must delete without `loc_74C7A`, leave
  player control exactly `$81`, and the inverted `loc_7506E` branch must skip
  restore while `$30!=0`; `$81` persists through capsule wait and is restored
  only at `loc_7473A` before autowalk lock. Add the required `FixBugs=0` comment:
  the fixed build would restore only when held.

  Add `finalHitSkipsBossHitAndOnlySuccessfulVisibleInitPlaysExplode`. The
  collision-property-zero eighth hit branches from `sub_74FD2` to `loc_75046`
  before the `loc_74FFA` `BossHit` call, so the hit and subtype-4 controller
  creation/dispatch are silent. After a successful later visible-child
  allocation, only that child's own init entry plays one `EXPLODE`; with the
  visible slot exhausted it plays neither `BossHit` nor `EXPLODE`.

  Add `slotExhaustionSkipsExplosionRngAndSfx`. At an object-slot-exhausted
  controller dispatch, assert `CreateChild6_Simple` is attempted first at the
  parent's unoffset coordinates, no visible child exists, RNG state is
  byte-for-byte unchanged, no offset is computed, and no explosion SFX plays.
  With one slot free, assert allocation precedes exactly one `Random_Number`
  state advance; its low word supplies X and its swapped high word supplies Y.
  The allocation/controller dispatch remains silent, and only the created
  visible child's first own entry plays the SFX. A second RNG call or `>>8` Y
  extraction must fail.

  In `TestS3kBossExplosionChild`, add
  `genericInitFallsThroughToCursorTwoMappingZeroTimerOneAndSameEntrySfx`.
  Construct this child through `createWithNativeInitSfx`. Starting with
  cursor/timer zero, one own initialization entry must install
  the animation callback, pre-decrement the timer, advance the ROM raw cursor
  `0->2`, read raw bytes 2/3, publish mapping frame 0 and timer 1, and play one
  explosion SFX on that same entry. It must fail for cursor 0, mapping 1, a
  Java-copied script, or next-entry SFX.

  Add `finalHitTransitionsArticulatedChildrenBySourceSlotLifecycle`. After the
  root hit entry, advance each later child slot independently. Controller,
  segments, and joint must take `Child_DrawTouch_Sprite_FlickerMove`: set own
  status 7, collision zero, install flicker-move, and load the exact
  `Obj_VelocityIndex` words selected by `$0C + 2*subtype` (including X flip),
  then obey native gravity/draw cadence/offscreen deletion. Outer and landing
  delete on their own parent-status-7 checks. An unheld grab owner reaches
  `loc_74C7A`, clears player control and deletes without a controller callback;
  a held `loc_74CCC` owner instead deletes through `loc_74BFA` without clearing
  `$81`. Attachment and visual survive status 7, then delete only when root `$38` bit 4 is
  observed; the new defeat-follow child uses that same bit-4 lifetime. Assert
  native collision byte zero for generic child pieces instead of applying a
  root-wide defeat mask immediately.

  Add `defeatDebrisUsesAdjustedFlipIndexedVelocityAndFullFlickerMove`. On each
  debris init entry, assert adjusted refresh, root-flip latch, subtype mapping,
  source indexed velocity, X negation when flipped, callback install, and one
  draw. Later entries must retain full 16.16 `MoveSprite`, gravity `$38`, and
  alternating flicker draws, then delete on either exact native coarse-X or
  unsigned-Y window boundary. A one-sided Y cutoff, lost low word, missing
  flip, or unconditional draw must fail.

  Add `rootAndDefeatFollowDrawOnlyOnNativeCallbacks`. Collect production render
  commands over consecutive own entries. Pin ordinary/final-hit and nonexpired
  fade draws; no draw on fade expiry; `loc_746F4` draws at and above
  `camera-$40` but not on the first strictly-below transition; capsule/gate/
  autowalk/target callbacks do not draw. The defeat-follow visual does not draw
  on `loc_74E12` setup, then uses unadjusted root-relative refresh with no
  inherited root flip and first draws on the following `loc_74E24` entry before
  deleting on root bit 4. Escape-specific root callbacks continue in Task 8.

  After the shared delay, assert mapping 5, `$38` bit 4, and exactly five
  debris children with subtypes `0,2,4,6,8` and indexed velocities
  `(-$100,-$100),(+$100,-$100),(-$200,-$200),(+$200,-$200),(-$300,-$200)`.
  Assert the root rises one pixel per own entry and switches only when strictly
  above camera `-$40`.

  Add `defeatDelayCreatesLevelMusicFadeBeforeDebrisCallback`. The retained
  `$3F` word pre-decrements; on its expiry entry the root allocates the existing
  semantic `SongFadeTransitionInstance`/`Obj_Song_Fade_ToLevelMusic` owner,
  seeds native `$2E=119`, and only then enters `loc_746D8`. Assert the fade
  owner/timer and object-slot order, not only an audio mock call.

- [ ] **Step 2: Run the exact hit/defeat methods and verify red**

  Add and run these methods:

  - `nonFinalHitClearsCollisionAndPublishesExactSixColourFlash`
  - `flashExpiryRestoresOnlyRootAndControllerCollision`
  - `eighthHitRunsShippedFixBugsZeroBranch`
  - `naturallyHeldFinalHitRetainsControlUntilCapsuleGate`
  - `finalHitSkipsBossHitAndOnlySuccessfulVisibleInitPlaysExplode`
  - `slotExhaustionSkipsExplosionRngAndSfx`
  - `finalHitTransitionsArticulatedChildrenBySourceSlotLifecycle`
  - `defeatDebrisUsesAdjustedFlipIndexedVelocityAndFullFlickerMove`
  - `rootAndDefeatFollowDrawOnlyOnNativeCallbacks`
  - `defeatDelayCreatesLevelMusicFadeBeforeDebrisCallback`
  - `defeatCreatesFiveIndexedDebrisInNativeOrder`
  - `defeatRiseUsesStrictCameraMinus40ThresholdBeforeCapsule`

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance#nonFinalHitClearsCollisionAndPublishesExactSixColourFlash+flashExpiryRestoresOnlyRootAndControllerCollision+eighthHitRunsShippedFixBugsZeroBranch+naturallyHeldFinalHitRetainsControlUntilCapsuleGate+finalHitSkipsBossHitAndOnlySuccessfulVisibleInitPlaysExplode+slotExhaustionSkipsExplosionRngAndSfx+finalHitTransitionsArticulatedChildrenBySourceSlotLifecycle+defeatDebrisUsesAdjustedFlipIndexedVelocityAndFullFlickerMove+rootAndDefeatFollowDrawOnlyOnNativeCallbacks+defeatDelayCreatesLevelMusicFadeBeforeDebrisCallback+defeatCreatesFiveIndexedDebrisInNativeOrder+defeatRiseUsesStrictCameraMinus40ThresholdBeforeCapsule,com.openggf.game.sonic3k.objects.TestS3kBossExplosionChild#genericInitFallsThroughToCursorTwoMappingZeroTimerOneAndSameEntrySfx" \
    test
  ```

  Accepted first RED:
  `finalHitTransitionsArticulatedChildrenBySourceSlotLifecycle` finds the
  controller/segments/joint remain in their fight callbacks after root status
  7; `naturallyHeldFinalHitRetainsControlUntilCapsuleGate` observes the current
  held-owner branch clear `$81`; `defeatDebrisUsesAdjustedFlipIndexedVelocityAndFullFlickerMove`
  observes missing flip/indexed-X/full-window behavior; and
  `rootAndDefeatFollowDrawOnlyOnNativeCallbacks` observes unconditional root and
  setup-entry follow draws. The generic animation method observes the current
  wrong first-entry cursor/mapping boundary, while
  `finalHitSkipsBossHitAndOnlySuccessfulVisibleInitPlaysExplode` catches a final
  `BossHit` or controller-time `EXPLODE`. A touch-dispatch/setup failure or
  regression in a Task-5 fight method is not the accepted red state.

- [ ] **Step 3: Implement `sub_74FD2`, shared defeat, and `loc_746D8`**

  Route palette writes through the established palette owner and read the
  tables from ROM. Implement `Child6_CreateBossExplosion` as the separate
  rewindable `BigArmExplosionControllerChild`; do not tick a plain mutable
  helper from the root or replace the controller with one visible explosion.
  Preserve its creation-time callback, three-entry cadence, parent-follow and
  parent-bit-5 teardown, object-slot child creation order, and gravity `$38`
  on debris. Create the visible child at the parent's unoffset coordinates
  through `createWithNativeInitSfx`, observe whether ObjectManager assigned a
  live slot, and only then consume one RNG value and rewrite X from its low word
  and Y from its swapped high word. Let only the created visible child own
  first-entry SFX. Port the child helpers as distinct
  later-slot lifecycles—do not use a root-wide generic defeat mask or invent a
  child-to-controller cleanup callback. Keep the held grab owner's status-7
  delete path separate from the unheld control-clearing path. Port debris
  through adjusted refresh, latched flip, indexed velocity and the shared full
  `S3kBossFlickerMove` contract. Track per-entry root/follow render submission,
  including setup/switch-only no-draw callbacks. Bypass `BossHit` on the final
  collision-property-zero branch and leave controller allocation/dispatch
  silent. Allocate the existing semantic song
  fade owner at the retained wait expiry with timer 119 before debris. Read the
  raw explosion script from ROM and reproduce its same-entry cursor/timer
  transition.

- [ ] **Step 4: Re-run the full Big Arm unit class and verify green**

  Then run the audited legacy-consumer compatibility fleet:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestS3kBossExplosionChild,com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects,com.openggf.game.sonic3k.objects.TestAizEndBossInstance,com.openggf.game.sonic3k.objects.TestAizMinibossCutsceneInstance,com.openggf.game.sonic3k.objects.TestCnzMinibossDefeatPhase,com.openggf.tests.TestCnzEndBossExplosionController,com.openggf.game.sonic3k.objects.TestCutsceneKnucklesLbz1CollapseChild,com.openggf.game.sonic3k.objects.TestHczEndBossInstance,com.openggf.tests.TestS3kIczEndBossObject,com.openggf.game.sonic3k.objects.TestLbzEndBossInstance,com.openggf.game.sonic3k.objects.TestLbzFinalBoss1Instance,com.openggf.game.sonic3k.objects.TestMgzDrillingRobotnikInstance,com.openggf.game.sonic3k.objects.TestMgzEndBossKnuxInstance,com.openggf.game.sonic3k.objects.TestMgzMinibossInstance,com.openggf.game.sonic3k.objects.TestMhzBossObjects" \
    test
  ```

  Require the existing caller-owned SFX choices to remain green, including the
  explicit MHZ weather-machine assertion of `WEATHER_MACHINE` with no
  `EXPLODE`. The ordinary constructor must remain audio-silent in all audited
  consumers.

### Task 7: Implement the two-signal capsule/results gate as a production lifecycle

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Use: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2EggCapsuleInstance.java`
- Create: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2ProductionRoute.java`

**Interfaces:**

- Consumes: real boss defeat, floating capsule button/animals/results object,
  `GameStateManager`, `WaterSystem`, and object-manager slot order.
- Produces: `Boss_LoadEggCapsuleAndAnimals` and an exact three-stage
  `loc_7473A` gate with no test-set signals.

- [ ] **Step 1: Write mutation-sensitive production lifecycle characterizations**

  In
  `realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute`, create the live
  Knuckles `LbzFinalBoss1Instance` wrapper through the production registry,
  drive its ordinary handoff until its later-slot `$CC` root exists, and prove
  the wrapper established nonzero `GameStateManager.currentBossId`. Deliver all
  eight attacks with real Knuckles attack state/overlap through the ordinary
  `ObjectTouchResponseController`; do not construct `$CC` directly or call
  `onPlayerAttack` (especially with a null controller). Drive the resulting
  final hit until the real route-8 capsule exists. The root must set `$38` bit
  5 and `endOfLevelActive=true` before allocating the capsule. Use the same
  Knuckles sprite to hit the capsule button and let the real results object run.
  The test may preset only the stale lock before the FinalBoss1 wrapper; it
  must not call either signal setter, invoke a results callback, force a
  routine, or use elapsed-time substitution.

  Observe three distinct states:

  1. before X reaches `camera-$60`: active true, lock false, root waits;
  2. after capsule writes the lock: active true, lock true, root still waits;
  3. after the results object normally clears active: active false, lock true,
     root advances only on its next own-slot dispatch.

  Also assert the capsule/results writers occupy later object slots than the
  retained root, making each write visible to the next root dispatch. Root
  `$38` bit 5 must remain set through all three states; the gate must not clear
  it.

  Add `routeRequiresWrapperAndOrdinaryTouchOwnership` as the negative-control
  companion. With no FinalBoss1 wrapper, a bounded ordinary ObjectManager run
  must never allocate `$CC`. With the real wrapper/root present but Knuckles
  non-attacking, repeated overlapping touch passes must leave HP at eight;
  enabling ordinary attack state must make `ObjectTouchResponseController`
  decrement exactly one hit without any direct boss call. Assert wrapper/root
  and root/capsule/results relative SST order. These controls fail if the test
  removes/bypasses the wrapper, injects direct hits, omits touch registration,
  or corrupts later-slot writer visibility.

- [ ] **Step 2: Run and record the production characterization**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute" \
    test
  ```

  This ownership slice is explicitly a characterization, not an invented RED:
  the repository already has separate FinalBoss1 handoff and touch machinery,
  so the replacement test may pass before production changes. Its negative
  controls are the mutation proof. If the first run is red, record the exact
  failing JUnit assertion/output before fixing it; do not substitute the prior
  static review observation as a failure. A manually injected root/hit or a
  test without both negative controls remains an invalid green.

- [ ] **Step 3: Implement the native helper and gate**

  In the root, set active before `spawnFreeChild` and create
  `LbzFinalBoss2EggCapsuleInstance` with route-init semantics. In
  `loc_7473A`, read active first, then the LBZ2 lock, and write neither. Preserve
  next-slot visibility and retain root `$38` bit 5; do not add a combined
  callback, notification, or gate-time bit clear.

- [ ] **Step 4: Re-run Task 7 and verify green**

  Expected: the three stages and next-dispatch transition all pass.

### Task 8: Port the complete Knuckles escape and MHZ transition

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/S3kTransitionEventBridge.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/S3kTransitionWriteSupport.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kLBZEvents.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/runtime/LbzZoneRuntimeState.java`
- Modify if required by the existing copy boundary:
  `src/main/java/com/openggf/game/sonic3k/scroll/SwScrlLbz.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2Instance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2ProductionRoute.java`
- Create: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kLbzBigArmTransitionBridge.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kLbzRewindRoundTrip.java`

**Interfaces:**

- Consumes: injected level-event bridge, current LBZ gradual-boundary worker
  owner with a Big-Arm-specific literal-target entry, player control, PLC `$71`, ship/head
  art, base camera and semantic camera-copy state, timed-shake event state,
  camera bounds, and `ObjectServices.requestZoneAndAct`.
- Produces: `loc_7473A-loc_7498E`, falling floor/debris, ship/flame escape,
  carried Knuckles animation, native timed-screen-shake ordering, and
  `StartNewLevel $0700`.

- [ ] **Step 1: Extend the production test to fail after the two-signal gate**

  Assert, in order: player restore/lock and auto-walk to camera `+$50`; PLC
  `$71` plus Egg Robo head queue; head/ship/flame graph; falling FinalBoss1
  floor child frame `$16` with radius `$10` and gravity `$38`; its bounce/wait;
  the source explosion-position table and exactly 127 qualified every-four-
  V-int escape-emitter allocation attempts (`$39=$7F`, qualifying-entry
  pre-decrement, attempt for `$7E..$00`, then `bmi` deletes on the next
  qualifying decrement to `$FF`). Successful emitters are SST-slot-dependent
  and number at most 127. Each successful emitter selects its table position,
  makes one separate one-shot subtype-4 controller attempt, and then the
  emitter—not the controller—waits `$60`. Assert each successfully allocated
  controller's immediate explosion with signed-negative `$39=$80` unchanged
  and three-entry cadence with no countdown or `$60` timer. When the emitter's
  wait expires it sets its own bit 5; the controller observes that parent bit
  on its later entry and tears down;
  floor `$38` bit 3; `loc_74DA4` publishing stored targets max-Y/target-max-Y
  `$1000`, max-X `$6000`, and min-Y zero without snapping current bounds,
  followed by the retained three Act2-size workers' independent `$4000/$4000/$8000`
  accumulators and first-entry zero-motion/later fixed-point sequence;
  direct invocation of those retained workers through exact clamp/deletion; walk to `$4510`;
  player object control `$83`; immediate frame `$8C`, first 11-entry expiry
  selecting `$8C` again, then `$8D` on the second expiry and alternating every
  11 entries thereafter; and request for MHZ zone 7, act 0 only after carried Y reaches
  `_unkFAB0+$200`.

  Pin global/source order in that same real route. Root `$38` bit 5 remains set
  through the two-signal wait and is cleared only at the autowalk target, after
  PLC `$71` and Egg Robo head art are submitted and before the replacement head
  is allocated. The ship's first own entry crossing unsigned camera `+$1C0`
  clears `GameStateManager.currentBossId` exactly there. Neither event occurs at
  the gate. Assert root init did not preload ship/explosion/post-gate assets,
  while the gate's `sub_7302E` queues the raw ship/explosion PLC.

  Add
  `TestLbzFinalBoss2ProductionRoute#nativeEscapePrioritiesVisibilityAndFloorAnimationBoundary`
  and
  `TestLbzFinalBoss2ProductionRoute#escapeMotionAndCarrierPreserveFullLowWords`,
  plus
  `TestLbzFinalBoss2ProductionRoute#rootEscapeDrawsThroughFloorWaitExpiryAndStopsAtShipCrossing`.
  Collect render commands and pin
  debris bucket 2, defeat-follow 4, flame 5, escape floor 6, and floor
  explosion 1. During its emitter phase, the floor clears native render bit 7
  on every own entry and remains invisible. The hitbox wait-expiry dispatch
  changes only callback/routine and produces no render/raw-cursor advance; its
  following own dispatch advances the ROM raw cursor `0->2`, publishes mapping
  0/timer 1, and later reaches the ROM end callback. Seed arbitrary 16-bit low
  words on root, debris, floor, and player; require every signed-8.8 motion step
  and high-word carrier copy to preserve them. Pin carrier control `$83` as the
  exact bit composition, not merely a nonzero lock.

  For the root render method, collect commands on consecutive callbacks:
  `loc_747D6` resumes drawing on its first ship-rise entry; rise transition,
  cruise, floor allocation, `loc_74894` floor wait including the wait-expiry
  entry, and pre-cross escape entries all draw. The entry reaching unsigned
  `camera+$1C0` runs `loc_748D0`, clears render bit 7 and does not draw; later
  carried-player callbacks remain drawless. This must stay distinct from the
  capsule/gate/autowalk no-draw schedule proven in Task 6.

  Move `cameraCopyFightStatesUseAppliedOffsetRatherThanBaseCamera` into this
  task, alongside its complete semantic owner. With base camera Y fixed and a
  nonzero prepared/applied shake offset, naturally enter root routines
  `$10/$12/$14` and the grab-floor `$24` approach and prove each reads semantic
  `Camera_Y_pos_copy`; mutate only the applied offset and require each boundary
  result to change. A base-camera read must fail, while states whose source uses
  `Camera_Y_pos` retain base-camera behavior. `escapeMotionAndCarrierPreserveFullLowWords`
  separately owns carrier `$83` composition and carrier low-word preservation;
  Task 5 owns only fight/root/held writes.

  Add exact timed-shake methods to
  `TestSonic3kLbzBigArmTransitionBridge`:

  - `grabFloorImpactPublishesTimedShakeInNativeFgBgOrder` drives the real
    grab-floor approach to impact and proves the object writes semantic
    `Screen_shake_flag=$14` before ScreenEvents. Seed a nonzero old prepared
    offset; on the trigger frame the LBZ foreground phase consumes that old
    offset into semantic `Camera_Y_pos_copy`, then the background
    `ShakeScreen_Setup` pre-decrements 20 to 19 and reads ROM
    `ScreenShakeArray[19]==-5` for the next frame. On the following frame the
    foreground applies -5. Assert the exact base/copy values and countdown;
  - `timedShakePausesAndPublishesZeroForDeadPlayer` begins at countdown 19,
    sets Player routine 6, proves setup leaves 19 unchanged while publishing a
    zero next offset, then restores an eligible routine and proves the next
    setup resumes at 18 with the ROM table value.

  These tests must use the active LBZ event/runtime owner and actual FG/BG
  service order. A bare game-state boolean, the unrelated
  `deathEggRumble`/LBZ1 continuous-shake gate, same-trigger-frame -5, or a local
  boss timer is an invalid green.

  Add exact LBZ event tests:

  - `TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence`
    installs a real act-2 `Sonic3kLBZEvents`, seeds non-target current bounds,
    invokes only the semantic bridge, and observes literal stored targets plus
    the exact three-worker accumulator/current-bound/clamp sequence;
  - `TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgeIsNoOpWithoutActiveLbzAct2Handler`
    proves the bridge neither creates a handler nor mutates another zone/act;
  - retain and run
    `TestSonic3kLbzRewindRoundTrip#postTitleBoundaryWorkersRoundTripThroughZoneSidecar`
    to prove the delegated worker owner remains rewind-covered.

- [ ] **Step 2: Run the extended route test and verify red**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance#cameraCopyFightStatesUseAppliedOffsetRatherThanBaseCamera,com.openggf.game.sonic3k.events.TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence+bigArmFloorBridgeIsNoOpWithoutActiveLbzAct2Handler+grabFloorImpactPublishesTimedShakeInNativeFgBgOrder+timedShakePausesAndPublishesZeroForDeadPlayer,com.openggf.game.sonic3k.events.TestSonic3kLbzRewindRoundTrip#postTitleBoundaryWorkersRoundTripThroughZoneSidecar" \
    test
  ```

  Accepted first RED:
  `grabFloorImpactPublishesTimedShakeInNativeFgBgOrder` fails because no
  semantic timed-shake bridge/state exists, while
  `nativeEscapePrioritiesVisibilityAndFloorAnimationBoundary` exposes the
  current early floor-animation frame and wrong buckets/visibility, while
  `rootEscapeDrawsThroughFloorWaitExpiryAndStopsAtShipCrossing` exposes the
  current unconditional drawing across no-draw callbacks, and
  `cameraCopyFightStatesUseAppliedOffsetRatherThanBaseCamera` fails before the
  semantic applied-copy owner/accessor exists. The amended
  `bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence` first
  fails because current bounds are snapped and targets come from the loaded
  level; after removing that snap it must still fail until the first logical
  worker entry advances its `$4000/$4000/$8000` accumulators with zero visible
  motion and later entries reproduce the exact source sequence. After
  adding only a bridge signature, the first shake behavioral RED must show the
  old-offset/next-offset order is absent. The retained rewind-sidecar method
  must remain green throughout.

- [ ] **Step 3: Implement the continuation in source order**

  Add one semantic S3K transition bridge method for the Big Arm floor to
  delegate to a dedicated `Sonic3kLBZEvents.prepareBigArmFloorTransition()`
  literal-target entry; do not reuse generic current-level targets. Keep its
  three logical worker callbacks/accumulators in the existing rewound LBZ event
  owner and do not write current bounds on the floor entry. Add a separate semantic
  timed-shake bridge write and keep its countdown, previously prepared offset,
  and currently applied camera-copy offset in the active rewound LBZ
  event/runtime sidecar. Expose the semantic applied `Camera_Y_pos_copy` read
  through that owner and use it only in Big Arm `$10/$12/$14/$24`; keep other
  source states on base camera Y. Preserve object -> foreground consumes old prepared
  offset -> background prepares next offset ordering, including Player routine
  6 pause/zero. Do not recreate either owner as object-local timers or route a
  timed shake through the unrelated continuous-rumble flag.

  Read the explosion position table, Egg Robo animation, boss-explosion raw
  animation, and timed-shake bytes from ROM; reuse existing
  ship/capsule/explosion/final-boss-1 renderers. Convert exact native priority
  words to buckets, preserve floor invisibility and raw-animation callback
  boundaries, and use full 16.16 position longs. At the autowalk target, queue
  PLC `$71`/head art, clear bit 5, then create the head. Clear Boss flag only at
  the ship threshold and request the typed destination only at the source Y
  threshold.

- [ ] **Step 4: Re-run the route and LBZ event tests and verify green**

  Expected: the production lifecycle reaches the pending MHZ request with
  correct floor/camera worker ownership.

### Task 9: Close spawn, graph, and global-signal rewind coverage

**Files:**

- Modify: Big Arm/capsule production classes from Tasks 3-8
- Create: `src/test/java/com/openggf/game/rewind/TestS3kLbzFinalBoss2GraphRewind.java`
- Modify only if required by actual schema: the applicable rewind schema owner

**Interfaces:**

- Consumes: `SpawnRewindRecreatable`, phase-one exact-class restore shells,
  phase-two graph rewiring, game-state snapshot,
  WaterSystem snapshot, LBZ event snapshot, and object-manager round trip.
- Produces: deterministic restore/re-execution of every root/child/capsule edge
  and both gate signals.

**Rewind-ID edge matrix:**

Every mutable object reference in this matrix is captured as an object ID and
resolved during phase-two recreation. Constructor-only subtype/offset/ordinal
values are captured as scalars or annotated final constructor-derived fields;
none is hidden behind `@RewindTransient` without reconstruction proof.

The restore owner split is exact. Each nested concrete `BossChild` owns a
private `ObjectSpawn` constructor that creates only a null-parent, exact-class
shell and any final structural collections. ObjectManager phase one restores
its captured slot and registers its captured ID. ObjectManager phase two then
restores the root collections and every reference in the matrix from IDs. No
child performs a nearest-root/sibling search, no phase-one shell adopts itself
into a provisional root, no identity collection is marked deferred, and no
shared probe-constructor heuristic is widened for this boss.

| Source owner | Target edge(s) | Restore assertion |
|---|---|---|
| root | current `RobotnikHead4Child` | head resolves to recreated root; init and post-gate variants retain phase/animation |
| root | `ArmControllerChild` | exact controller ID restored; controller back-reference, routine/angle, own flip, coordinates, and activation-entry render state restore |
| root | `ArmAttachmentChild`, `ArmVisualJointChild`, `ArmOuterCollisionChild` | each initial sibling retains allocation ordinal, offset, routine, and root parent |
| root inventory / each segment | two `ArmSegmentChild` instances | both IDs and Java semantic-index order `0,1` (native bytes `0,2`) restore; each segment's own controller/root links, normal/held/release callback, signed offset, mapping, raw cursor/timer, and flip resolve exactly; controller retains no outgoing collection |
| root inventory / joint | `ArmKinematicJointChild` | native parent is recreated root and the joint's separate controller link resolves to recreated controller; controller retains no outgoing pointer |
| root inventory / grab owner | `GrabOwnerChild` | the child's controller parent and root link both resolve; held-player reference, own flip, frozen coordinates, `$3F..$FFFF` cooldown and callback phase restore; after status-bit-7 deletion no controller back-edge survives |
| root | `LandingCollisionChild` | ID/offset/collision `$9C` and root parent restore when allocated |
| root | subtype-4 `BigArmExplosionControllerChild` | exact controller ID and root parent restore; constant signed-negative `$80`, `$20/$20` ranges, interval, and followed coordinates restore |
| root | five `DefeatDebrisChild` instances | ordered IDs/subtypes `0,2,4,6,8`, fixed positions, velocities, flip/flicker/render phase, and root parent restore |
| root | `DefeatFollowVisualChild` | ID/offset/frame, setup-versus-draw callback phase, unadjusted coordinates/flip, and root parent restore |
| root | `LbzFinalBoss2EggCapsuleInstance` | capsule ID, route phase, 16.16 coordinates, swing state, and slot after root restore; capsule has no invented parent write |
| root | post-gate `RobotnikHead4Child` and `RobotnikShipFlameChild` | both IDs/offsets/animation restore and point to recreated root |
| root | `EscapeFloorChild` | floor ID/routine/timer/velocity and root signal edge restore |
| floor | seven `EscapeFloorExplosionChild` instances | ordered IDs/offsets and floor parent restore |
| floor | live `EscapeExplosionEmitterChild` instances | IDs/waits/absolute ROM-selected positions/RNG state and floor parent restore |
| emitter | subtype-4 `BigArmExplosionControllerChild` | exact controller ID and emitter parent restore; constant signed-negative `$80`, `$20/$20` ranges, interval, followed coordinates, and parent-bit-5 lifecycle restore |
| `ObjectManager` only | active generic `S3kBossExplosionChild` instances | scan the independently managed visible objects before/after restore and retain each active object's exact ID, slot, coordinates, raw cursor/timer/mapping, native-init-SFX mode, and fired/not-fired state; neither root nor controller owns a collection/back-edge |

The final row deliberately has no graph edge. In `sub_83E84`, the controller
uses `CreateChild6_Simple`; the resulting `Obj_BossExplosion1` never reads its
creator and self-deletes through its animation callback. A visible child can
delete in a later slot after the controller/root already ran, and
`ObjectManager` removes its identity in that same pass. Retaining it in an
invented captured owner collection would leave a stale unresolvable ID at the
frame boundary. ObjectManager-only ownership also preserves native controller
teardown: surviving visible explosions continue independently with no cascade
delete or detach callback.

In addition to graph IDs, assert one snapshot row each for root-before-capsule,
capsule-before-threshold, capsule-after-threshold, results-active, and
results-cleared/root-not-yet-dispatched. Those rows jointly restore
`GameStateSnapshot.endOfLevelActive`, the LBZ2
`WaterSystemSnapshot.DynamicWaterEntry.locked` value, object-slot order, and
the existing LBZ event-owner worker state.

- [ ] **Step 1: Write failing round-trip tests**

  Add and run these exact methods in
  `TestS3kLbzFinalBoss2GraphRewind`:

  - `articulatedGrabGraphRestoresFreshWithExactIdsSlotsAndLinks`;
  - `defeatDebrisControllerAndVisibleExplosionsRestoreByExactId`;
  - `visibleExplosionsRestoreAndOutliveControllerWithoutOwnerEdges`;
  - `capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute`;
  - `sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes`;
  - `fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries`; and
  - `failedSstAllocationsNeverEnterCapturedGraphAndRetryOnlyAtNativeBoundaries`.

  Snapshot/restore an articulated live-grab graph and a post-defeat graph both
  before and after the capsule X threshold. Assert root/controller/segment/
  joint/grab/landing/debris/capsule/head/flame/floor references rewire by object
  ID; routine/timers/fixed coordinates/collisions restore; every root, player,
  debris, floor, capsule, and visible-explosion 16-bit position low word
  restores from arbitrary nonzero values; capsule subpixel X/Y,
  active flag, water lock, and slot order restore together; and the next update
  after restore produces the same state and RNG sequence. Separately scan active
  `S3kBossExplosionChild` instances directly from ObjectManager and compare
  their own IDs, slots, X/Y, raw cursor/mapping/delay, native-init-SFX mode,
  and fired/not-fired one-shot state across an
  out-of-place restore. At both the root defeat-controller and an escape-emitter
  controller boundary, snapshot immediately before teardown, re-execute through
  controller deletion, prove a still-live pre-existing explosion retains its ID
  and continues animation, then step until its independent animation self-deletes.
  Assert root `graphChildren` contains no generic visible explosion and the
  controller class declares no live-explosion collection/back-edge.
  At the late root-controller boundary, also assert the already-deleted grab
  owner is absent from the root inventory and that `ArmControllerChild`
  declares no `segments`, `joint`, or `grabOwner` outgoing fields. The live
  articulated children themselves retain the exact native-directed controller
  and root IDs. Capture their original allocation order and exact SST slots
  before deletion. After the real status-bit-7 child deletion, independently
  enumerate the surviving articulated objects from ObjectManager and assert
  that the root inventory contains exactly that set in the original relative
  order with unchanged slots. Repeat the equality after out-of-place restore
  and one re-executed boundary entry. The method must fail for a
  child-to-controller cleanup callback/mutation, an optional/deferred reference
  codec, or any missing required ID.

  `failedSstAllocationsNeverEnterCapturedGraphAndRetryOnlyAtNativeBoundaries`
  must exhaust real later SST slots through the production ObjectManager, not a
  mock allocator. Capture in the same frame boundary as each selected failure
  and require strict out-of-place restore. For an initial or later multi-entry
  `CreateChild1_Normal` table, retain successful earlier entries, stop at the
  first failed entry, attempt no later table entry, and preserve exact manager
  IDs, slots and relative order with no failed dedicated field or collection
  edge. Failed construction consumes no successful child ordinal; after a
  controlled free slot the next success has the next ordinal with no phantom
  identity gap. One-shot owners, including an escape emitter's controller, do
  not retry. The root's defeat-time subtype-4 controller allocation is also a
  one-shot: if that controller itself cannot allocate, freeing a slot later
  does not recreate it. This is distinct from a controller that *did* allocate:
  only that live controller retries its own failed visible-explosion child on
  the next three-own-entry emission callback. A failed `loc_74DEA` escape-emitter
  attempt still consumes the qualified `$7E..$00` counter result and can try
  again only at the next four-V-int boundary. `loc_74DA4` stops its seven-child
  hitbox table on failure but still performs later camera writes and level-size
  allocation. Every root/floor/emitter edge must enumerate to a live manager
  ID; missing required IDs stay fatal and no optional/deferred codec is allowed.

  Cover the route-8 capsule as its own non-`BossChild` failure row. At
  `beginCapsuleHandoff`, `_unkFAA8`/`endOfLevelActive` is written before the
  later-slot allocation attempt. Exhaust that real slot, then assert the signal
  remains true but `capsuleChild` is null, `capsuleSpawned` is false, no graph/
  child-order edge or successful ordinal is retained, and same-boundary strict
  capture/restore succeeds. Free one slot and re-execute subsequent capsule-wait
  entries: the one-shot handoff callback must not retry. This row must fail if
  the temporary destroyed/slotless capsule is assigned to the dedicated field
  before allocation success is known.

  `sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes` must use a
  literal source oracle, never an intersection with the production result.
  Immediately after the root final-hit slot, all pre-existing children retain
  their pre-hit callbacks. As their later slots execute, controller, both
  segments, and joint remain active as status-7 flicker-move objects with
  collision zero and exact indexed velocity; outer, landing, and grab are
  absent; attachment and visual remain in original relative order until root
  bit 4, then are absent; defeat-follow has its source parent/bit-4 lifecycle.
  Assert the exact expected type order and original SST slots after each named
  boundary, out-of-place restore, and one re-executed entry. Do not derive the
  expected list by filtering whichever objects production happened to keep.

  `fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries` snapshots and
  re-executes: a 16.16 movement/high-word-copy entry; shake state immediately
  after the trigger-frame foreground consumes the old offset but before
  background prepares timer 19/-5; the next foreground application; Player
  routine-6 pause/zero; Boss flag immediately before/after ship crossing; root
  bit 5 immediately before/after the autowalk target; the PLC `$71`/head queue;
  and the level-music fade owner at timer 119. Require identical exact state
  and next-entry results after fresh restore.

  The same method also snapshots/re-executes the new callback-sensitive state:
  controller routine-2 activation with its prior flip; segment first-held and
  first-release entries with cursor 1/timer 9/signed +8 offset; grab cooldown
  `$3F`, switch-only `$FFFF` expiry and following reacquisition; debris
  alternating flicker; defeat-follow setup/no-draw then first draw; and root
  draw/no-draw boundaries on fade expiry, capsule/autowalk, floor-wait expiry,
  and ship crossing. Exact render-command presence, not a scalar alone, must
  match after out-of-place restore and one re-executed own entry.

  For the shipped `FixBugs=0` path, the named method must use the naturally
  acquired held player and snapshot out of place at four exact boundaries:
  immediately before the later grab-owner slot observes root status 7;
  immediately after that child deletes without `loc_74C7A`; during capsule
  wait while root `$30!=0` and player control remains exactly `$81`; and just
  before/after `loc_7473A` passes both gate signals. Restore and re-execute each
  boundary. The pre-gate result must keep `$81`; the gate entry must clear
  object-control ownership, install the production autowalk input lock/zero
  mask, and fall through into autowalk with the same result as uninterrupted
  execution. A child deletion callback, early restore, lost `$81`, or merely
  forward-only assertion is an invalid green.

  Run the mutation-sensitive ownership/lifetime method by itself first:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind#visibleExplosionsRestoreAndOutliveControllerWithoutOwnerEdges" \
    test
  ```

  The method must enumerate the pre-existing visible explosion only from
  ObjectManager, snapshot immediately before its subtype-4 controller tears
  down, and re-execute the deletion. The same visible ID and slot must remain,
  its coordinates/animation must advance independently, and its own animation
  must subsequently self-finish and remove the ID. Reintroducing a root or
  controller collection/back-edge must make the method fail.

  Run the allocation-closure method by itself at the same-boundary capture:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind#failedSstAllocationsNeverEnterCapturedGraphAndRetryOnlyAtNativeBoundaries" \
    test
  ```

  Accepted first RED: current `spawnChild` returns a destroyed/unregistered
  object when later-slot allocation fails, and `recordChild`, floor/emitter
  collections, or a dedicated field retain it. Immediate strict capture reports
  a missing ObjectManager identity. After failed-edge filtering, the method
  must remain red if a table continues past failure, a failed construction
  consumes an ordinal, a one-shot owner retries, or recurring retry occurs
  before the exact three-entry/four-V-int boundary.

  Run the new source-disposition/fixed-global methods explicitly before the
  guard sweep:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind#sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes+fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries" \
    test
  ```

  Expected first RED: the source-fixed method observes articulated children
  retaining fight callbacks and the fixed/global method loses arbitrary low
  words plus the not-yet-owned shake state. A test that constructs its expected
  survivors from production output is not accepted.

- [ ] **Step 2: Run the graph test plus guards and verify red**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard,com.openggf.game.rewind.TestEveryObjectRewindRoundTrip,com.openggf.game.rewind.TestGraphCoveredIsolatedProbeClassification,com.openggf.game.rewind.TestRewindArchitectureGuard" \
    test
  ```

  Expected: uncovered new scalar/reference/recreate paths or failed graph
  identity; no baseline exception is acceptable. The accepted first RED is
  `articulatedGrabGraphRestoresFreshWithExactIdsSlotsAndLinks` failing while
  restoring the root collection with `Missing required object reference` for
  dynamic ID 2. That ID is the first `RobotnikHead4Child`: its
  `(LbzFinalBoss2Instance, boolean)` constructor cannot be built by the generic
  rewind probe, so phase one never registers it and phase two correctly rejects
  the incomplete table. A failure that merely compares stale Java instances or
  omits exact IDs is not accepted. After the shells expose the next boundary,
  the accepted second RED is
  `defeatDebrisControllerAndVisibleExplosionsRestoreByExactId` /
  `capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute` finding a
  now-removed `S3kBossExplosionChild` with no ObjectManager ID in the invented
  `liveExplosions`/root graph lists. That is deletion-after-owner-slot evidence,
  not permission for an optional/deferred codec. After those lists are removed,
  the accepted third RED is
  `visibleExplosionsRestoreAndOutliveControllerWithoutOwnerEdges` reaching the
  late pre-capsule frame after `loc_74C24` has deleted, while the invented
  `ArmControllerChild.grabOwner` still names its unregistered dynamic ID. The
  disassembly's `CreateChild1_Normal` stores `parent3` in each child and no
  returned child address in the controller, so this is evidence to remove all
  three non-native controller outgoing fields, not to add a deletion callback
  or optional reference codec.

- [ ] **Step 3: Implement recreate and rewire contracts**

  Add a null-parent restore-shell constructor to `BossChild` and a private
  `ObjectSpawn` constructor to every concrete nested child:
  `RobotnikHead4Child`, `ArmControllerChild`, `ArmAttachmentChild`,
  `ArmVisualJointChild`, `ArmOuterCollisionChild`, `LandingCollisionChild`,
  `ArmSegmentChild`, `ArmKinematicJointChild`, `GrabOwnerChild`,
  `BigArmExplosionControllerChild`, `DefeatDebrisChild`,
  `DefeatFollowVisualChild`, `RobotnikShipFlameChild`, `EscapeFloorChild`,
  `EscapeFloorExplosionChild`, and `EscapeExplosionEmitterChild`. Make the
  family use `SpawnRewindRecreatable`; remove its nearest-object recreation and
  provisional adoption path. Capture mutable scalars normally, graph
  references and collections as rewind IDs, and final constructor-derived
  subtype/offset fields with the repository annotation. Remove the controller's
  non-native `segments`, `joint`, and `grabOwner` outgoing fields and policies;
  preserve the root active inventory and each articulated child's exact
  controller/root IDs, and derive native allocation order from the root/slot
  order. Child deletion prunes only the root active inventory; it must not call
  back through the native controller pointer or mutate the controller. Keep
  required object-reference decoding strict and add no optional/deferred
  workaround. Remove the non-native
  controller `liveExplosions` collection, its rewind policy, and root
  `graphChildren` registration for generic visible explosions; assert them from
  ObjectManager's active-ID inventory instead. Add no child-to-controller
  cleanup edge, cascade delete, mutable static state, shared constructor-probe
  branch, deferred identity collection, or coverage-baseline exemption.
  Capture full 16-bit position fractions and the LBZ timed-shake countdown,
  applied offset, and prepared-next offset in their existing scalar sidecar;
  preserve strict required-ID failure. Include `currentBossId`, root bit 5,
  song-fade owner/timer, and post-target PLC/head submission only through their
  existing production snapshot owners—do not mirror them into boss-local
  shadow fields.

  Make boss-local allocation success explicit: construct with a peeked
  successful ordinal, register through ObjectManager, and only after verifying
  a live SST slot commit that ordinal and retain the object in any collection
  or dedicated field. Return failure/null otherwise and stop native multi-entry
  table loops immediately. Every callsite must distinguish success before
  assigning an edge. Apply the same live-slot predicate to the non-`BossChild`
  route-8 capsule before assigning `capsuleChild`, `capsuleSpawned`, graph order
  or ordinal; preserve the already-written results-active signal on failure.
  Keep source-specific retry scheduling in its owning
  callback; do not add a generic retry queue.

- [ ] **Step 4: Re-run Task 9 and verify green**

  Expected: all seven named graph methods plus the five guard/round-trip classes
  pass. The teardown methods must fail if production reintroduces either owner
  list, cascades controller deletion into visible children, loses an active
  visible ID/state on restore, lets a visible child outlive its own animation,
  leaves root inventory unequal to the ObjectManager surviving articulated set,
  changes surviving relative order/SST slots, or adds a child-to-controller
  deletion callback.

### Task 9A: Correct reviewed deferred callbacks, flicker ownership, and floor targets

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Reuse without semantic change:
  `src/main/java/com/openggf/game/sonic3k/objects/bosses/S3kBossFlickerMove.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/S3kBossExplosionChild.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kLBZEvents.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2Instance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestS3kBossExplosionChild.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2ProductionRoute.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kLbzBigArmTransitionBridge.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kLbzRewindRoundTrip.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestS3kLbzFinalBoss2GraphRewind.java`
- Verify through guards, but do not add an exception to:
  `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

**Interfaces:**

- Consumes: native `Set_IndexedVelocity`, `Obj_FlickerMove`,
  `Go_Delete_Sprite`, `Go_Delete_Sprite_2`, `Go_Delete_Sprite_3`,
  `AnimateRaw_CustomCode`, `Obj_WaitForParent`, `loc_74E70`, `loc_74DA4`,
  and the existing rewind-owned LBZ gradual-boundary owner.
- Produces: child-own flip velocity, shared coarse-back culling, exact
  signal-entry/pending/next-delete callbacks, terminal raw render/touch
  behavior, literal Big Arm stored targets, and three rewind-visible gradual
  workers.

- [ ] **Step 1: Add the exact focused mutation tests and observe RED**

  Add these methods with no production workaround:

  - `TestLbzFinalBoss2Instance#articulatedFlickerVelocityUsesTransitioningChildOwnFlip`;
  - `TestLbzFinalBoss2Instance#articulatedFlickerCullUsesCameraCoarseBackWindow`;
  - `TestLbzFinalBoss2Instance#flickerCullInstallsDeleteCallbackBeforeNextEntryRemoval`;
  - `TestS3kLbzFinalBoss2GraphRewind#bit4ChildrenRefreshThenDeferRemovalAcrossRestore`;
  - `TestS3kBossExplosionChild#terminalRawCustomCodeDrawsOldFrameThenDeletesNextEntry`;
  - `TestLbzFinalBoss2ProductionRoute#floorExplosionTerminalEntryDrawsTouchesAndDefersRemoval`;
  - `TestLbzFinalBoss2ProductionRoute#emitterAndControllersDeferGoDeleteAcrossLaterSlots`;
  - `TestLbzFinalBoss2ProductionRoute#realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute`; and
  - `TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence`.

  The own-flip test naturally initializes a controller with its own flip clear,
  changes the root flip before the status-7 transition, and requires indexed X
  `+$200`; the current root-read implementation yields `-$200`. Repeat with a
  segment whose immediate-controller flip opposes the root. The coarse test
  uses camera X `$1080`: native coarse-back is `$1000`, so an unmoving child at
  `$1000` survives and one at `$1300` installs delete; `cameraX&$FF80` reverses
  those boundary results.

  The flicker-delete test runs both an articulated child and defeat debris.
  Their cull entry must retain manager ID, SST slot and root edge, install the
  captured pending phase, and submit no draw; the following own entry performs
  no second movement/gravity/draw and removes the ID/edge. The bit-4 test covers
  all three `Child_Draw_Sprite2` consumers: `loc_749BE` attachment and
  `loc_74BAE` visual perform adjusted refresh, `loc_74E24` defeat-follow
  performs unadjusted refresh, then each retains identity/pending state with no
  draw. Restore each signal boundary out of place; the next own entry must not
  refresh/draw again and must remove the manager/root entry. It must fail an
  immediate delete, skipped first refresh, second refresh, draw, or uncaptured
  callback phase.

  The shared and floor raw tests advance ROM `AniRaw_BossExplosion` to `$F4`.
  The terminal entry resets cursor/timer to zero but retains the old mapping,
  ID and slot and draws it; the floor hitbox also executes its ordinary
  zero-collision touch-list tail and retains floor/root edges. The next entry
  deletes only. Both shared audio modes must remain construction-silent; only
  native-init mode retains its already-played exactly-once SFX scalar, and no
  terminal/delete entry repeats it. Do not change the source-correct floor
  mapping `$16`/collision zero or the hitbox `$97`-then-parent-clear behavior.

  The emitter test advances the real `$60` wait. `loc_74E70` sets the emitter
  stop bit and installs pending delete without pruning root/floor edges; its
  later controller slot sees bit 5 and installs its own pending delete. Both
  IDs/slots survive that pass and delete on their following own entries without
  emission or bespoke child/back-edge mutation. Repeat for the root-owned
  defeat controller after root bit 5.

  The floor-target test seeds current max X/min Y/max Y away from
  `$6000/0/$1000`. The bridge must leave them unsnapped, publish those exact
  stored targets plus target max Y `$1000`, and start three logical workers.
  Their independent accumulator/high-word sequences are max X/min Y
  `$4000: 0,0,0,1` and max Y `$8000: 0,1,1,2`; run through exact target clamps,
  including max-Y equality surviving until the next `bgt` overshoot.
  Update the real-route method independently so it captures seeded current
  max X/min Y/max Y immediately before the floor-settlement callback. That
  callback must leave all three current bounds untouched, publish literal
  max-Y target `$1000`, and leave max X/min Y at their captured values; only
  the ordinary later camera/effect phase may ease current max Y toward `$1000`
  by its normal two-pixel step. With prior max Y `$1200`, the first such later
  easing result is `$11FE`, never the candidate's direct-write result `$0FFE`.

  Run:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance#articulatedFlickerVelocityUsesTransitioningChildOwnFlip+articulatedFlickerCullUsesCameraCoarseBackWindow+flickerCullInstallsDeleteCallbackBeforeNextEntryRemoval,com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind#bit4ChildrenRefreshThenDeferRemovalAcrossRestore,com.openggf.game.sonic3k.objects.TestS3kBossExplosionChild#terminalRawCustomCodeDrawsOldFrameThenDeletesNextEntry,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute#floorExplosionTerminalEntryDrawsTouchesAndDefersRemoval+emitterAndControllersDeferGoDeleteAcrossLaterSlots+realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute,com.openggf.game.sonic3k.events.TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence" \
    test
  ```

  Accepted first REDs are exact: own-flip sign is root-derived; native
  coarse-back survivor is deleted; articulated/debris and all three bit-4
  children disappear on the signal entry; shared/floor `$F4` objects disappear
  without terminal old-frame draw/touch; emitter/controllers disappear on
  signal observation; current bounds snap to `$6000/0/$1000`, targets come from
  the loaded level, and the first worker call does not retain the source
  accumulator state. The real-route method's first accepted RED is its current
  `$0FFE` max Y, `$6000` max X and zero min Y immediately after the floor's
  direct setters; after seeding prior max Y `$1200`, it must remain red until
  the floor leaves the current values intact and the later ordinary easing
  produces `$11FE`. The bridge test already passing does not satisfy this
  production-owner RED. A fixture/setup error is not an accepted RED.

- [ ] **Step 2: Strengthen graph/slot restore oracles and observe RED**

  Extend, without adding a production-derived expected list:

  - `sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes` for own
    flip, coarse-back, `Go_Delete_Sprite_2/_3`, exact signal-entry refresh,
    pending IDs/slots/order, and next-entry removal;
  - `bit4ChildrenRefreshThenDeferRemovalAcrossRestore` as a separate
    mutation-sensitive graph method covering attachment, arm visual, and
    defeat-follow signal-entry refresh, exact IDs/SST slots/root order,
    out-of-place pending-phase restore, and next-entry removal;
  - `visibleExplosionsRestoreAndOutliveControllerWithoutOwnerEdges` for the
    ObjectManager-only `$F4` pending-terminal snapshot and next delete;
  - `fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries` for literal
    targets, three worker active/completed phases and accumulators before/after
    zero/nonzero steps; and
  - `capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute` for floor
    hitbox terminal, emitter bit-5 signal, later-controller pending callback,
    exact root/floor/emitter edges and subsequent removal.

  Run:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind#sourceFixedArticulatedDefeatDispositionRestoresAndReexecutes+bit4ChildrenRefreshThenDeferRemovalAcrossRestore+visibleExplosionsRestoreAndOutliveControllerWithoutOwnerEdges+fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries+capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute" \
    test
  ```

  Accepted RED: a signal/cull/terminal snapshot cannot find the source-required
  still-live ID/slot because production already removed it; existing source
  oracles also derive flicker sign from root flip and coarse survival from the
  wrong camera mask, while the worker snapshot contains current-level targets
  and no exact first-entry accumulator phase. Optional IDs, stale-instance
  comparisons, and expected sets intersected with production are invalid fixes.

- [ ] **Step 3: Implement the reviewed callback and target owners**

  On `BossChild`, add one captured semantic pending-delete phase that
  distinguishes ordinary `Go_Delete_Sprite`, bit-4 `Go_Delete_Sprite_2`, and
  flicker `Go_Delete_Sprite_3` setup where the tests need the source callback.
  The first line of each later update executes pending deletion only and prunes
  its ordinary manager/root/floor inventory; it performs no callback body,
  movement, refresh, draw, emission, or bespoke child/back-edge mutation.
  `enterFlickerMoveIfDefeated` negates from the child's `hFlip`, and
  `updateFlickerMove` calls `S3kBossFlickerMove.isOutsideNativeBounds`.
  A cull schedules pending without `forgetChild`/`expireDynamic`. Move
  attachment/visual/follow bit-4 checks after their source refresh, schedule
  pending with no draw, and remove only next entry. Keep head/flame/outer/
  landing/grab direct-delete paths unchanged.

  Give `S3kBossExplosionChild` a captured pending-delete scalar. `$F4` leaves
  old mapping, resets cursor/timer, schedules pending and returns to the draw
  tail; next update sets destroyed before animation/audio. Use the analogous
  `BossChild` phase for `EscapeFloorExplosionChild`, keeping floor/root edges
  and terminal render/touch until next entry. Preserve the isolated native-init
  audio mode and every legacy silent/caller-owned audio choice.

  On emitter `$60` expiry, set the parent-stop signal and schedule pending but
  keep root/floor/controller edges. A controller that sees root/emitter bit 5
  schedules its own pending deletion; it does not emit or prune on that entry.
  Ordinary next-entry deletion owns inventory pruning. Preserve later-slot
  visibility on uninterrupted and restored passes.

  Remove the floor's direct current-bound setters. Add
  `Sonic3kLBZEvents.prepareBigArmFloorTransition()` with literal targets
  `$6000/0/$1000`, leaving the generic current-level-size entry unchanged for
  its other caller. Track three independent active/completed booleans and
  16.16 accumulators; execute the first logical worker entry rather than merely
  clearing a created-this-pass flag. Apply the source high-word sequence and
  exact `bhs/ble/bgt` clamp/deletion rules. Keep target max Y `$1000`, and let
  ordinary camera boundary easing remain its later separate phase. Capture all
  added scalar state through the existing LBZ event/object rewind owners; add
  no guard exception or parallel static manager.

- [ ] **Step 4: Re-run the exact focused and graph slices to GREEN**

  Re-run both Step-1 and Step-2 commands unchanged. Then run the complete
  production-route class and graph/guard gate:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute,com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard,com.openggf.game.rewind.TestEveryObjectRewindRoundTrip,com.openggf.game.rewind.TestGraphCoveredIsolatedProbeClassification,com.openggf.game.rewind.TestRewindArchitectureGuard" \
    test
  ```

  Require exact route and graph counts from fresh Surefire reports. The graph
  gate must fail if a pending scalar is uncaptured, if any required edge is
  optional, or if a terminal/signal ID or slot disappears one entry early.

- [ ] **Step 5: Restart the full evidence and review loop**

  These source/test changes invalidate the prior 1,470/1,470 report and every
  documentation row derived from it. Run Task 12 Step 1's complete consolidated
  selector and report its new exact count without inference. Run Task 10's
  trace lane separately, update every Task-12 Step-2 Big Arm count/status row,
  and rerun the exact stale-count, 127-attempt, placeholder and whitespace
  ratchets. Obtain a new full independent code/test/docs review. Any further
  source/test review change must also rerun Task 9B Step 4's legacy-fade,
  route/graph/rewind-guard and architectural-source-guard checks before this
  consolidated/trace/docs loop; commit remains held until explicit PASS,
  followed by the plan's docs-only resolved-wording review.

### Task 9B: Correct final-review raw, fade, worker, draw, status, and art-priority boundaries

**Files:**

- Modify: `src/main/java/com/openggf/game/sonic3k/objects/bosses/LbzFinalBoss2Instance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/objects/SongFadeTransitionInstance.java`
- Modify: `src/main/java/com/openggf/game/sonic3k/events/Sonic3kLBZEvents.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2Instance.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/objects/TestLbzFinalBoss2ProductionRoute.java`
- Modify: `src/test/java/com/openggf/game/sonic3k/events/TestSonic3kLbzBigArmTransitionBridge.java`
- Modify: `src/test/java/com/openggf/game/rewind/TestS3kLbzFinalBoss2GraphRewind.java`
- Verify legacy shared-fade behavior without changing:
  `src/test/java/com/openggf/game/sonic3k/objects/TestCnzCutsceneFadeTiming.java`
  and
  `src/test/java/com/openggf/game/sonic3k/objects/TestS3kSelfContainedTransientRewind.java`
- Verify real SST order without changing:
  `src/main/java/com/openggf/level/objects/ObjectManager.java`
- Verify through guards, but add no exception to:
  `src/main/java/com/openggf/game/rewind/schema/DefaultObjectRewindPolicies.java`

**Interfaces:**

- Consumes: `Animate_RawNoSST`, `AnimateRaw_Restart`,
  `Wait_FadeToLevelMusic`, `Obj_Song_Fade_ToLevelMusic`,
  `AllocateObject`, `CreateChild1_Normal`, `SetUp_ObjAttributes3`,
  `loc_746D8`, `loc_748D0`, `loc_74DA4`, `loc_74DEA`, and the
  reviewed pending-delete/object-graph contracts from Task 9A.
- Produces: exact segment raw cursor indexing/restart, controller deletion with
  no emitter callback, delayed root mapping 5, source-order fade counters,
  same-pass floor-worker creation entries, ship status bit 6, continuous floor
  draw/touch, and art-tile high priority independent of numeric sprite bucket.

- [ ] **Step 1: Add the exact behavior tests and observe the current REDs**

  Add or strengthen these methods before changing production:

  - `TestLbzFinalBoss2Instance#segmentFirstRawStepUsesCursorPlusOneWithoutGrabMask`;
  - `TestLbzFinalBoss2Instance#shipCrossingSetsStatusBit6AndRetainsArtPriority`;
  - `TestLbzFinalBoss2Instance#artTilePriorityPropagatesIndependentlyFromPriorityBucket`;
  - `TestLbzFinalBoss2ProductionRoute#nativeEscapePrioritiesVisibilityAndFloorAnimationBoundary`;
  - `TestLbzFinalBoss2ProductionRoute#emitterAndControllersDeferGoDeleteAcrossLaterSlots`;
  - `TestLbzFinalBoss2ProductionRoute#realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute`; and
  - `TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence`.

  The segment method keeps grab clear and starts each semantic subtype from raw
  cursor/timer zero. Its first own entry must store cursor 1, read script byte
  2 through native `1(a1,d0.w)`, publish mapping 4/8, and reload timer 9. A
  second branch enables grab only after recording that result and proves the
  same entry overrides mapping to 7/$B without changing cursor 1/timer 9.

  The ship-threshold method crosses exactly `camera+$1C0` and asserts status
  bit 6, root flags 4/5, `currentBossId==0`, no root draw, and retained root
  high-art priority. The art-priority method collects actual render-bucket
  inputs, not only private booleans. It distinguishes numeric bucket from
  `isHighPriority()` for root/head/controller/attachment/visual/segments/joint/
  outer and high-ObjDat debris/follow/floor. Floor hitboxes remain low because
  `ObjDat_BossExplosionHitbox` and the LBZ rewrite both keep art priority zero;
  independently managed generic `S3kBossExplosionChild` remains high. It checks
  the root before/after `loc_74340`, delayed `Child_GetPriorityOnce` and
  controller/outer collision boundaries, capsule handoff, and ship escape.
  For `RobotnikShipFlameChild`, construct otherwise identical low/high roots,
  allocate the flame through `CreateChild1_Normal` semantics, then toggle both
  roots: the flame must retain the copied low/high value. A constant `true` or
  dynamic parent mirror is invalid.

  Keep the floor render/touch wrapper observable on a nonqualifying emitter
  entry and a `V_int_run_count&3==0` allocation entry. Clearing the transient
  native on-screen bit must not suppress either tail. In the controller test,
  retain a direct reference to the already-deleted emitter shell; after the
  later controller's pending-delete entry and following deletion, its
  `explosionController` field must still identify that controller. The
  controller may read its parent stop bit, but must not call
  `forgetController` or otherwise mutate that shell.

  At floor settlement, both the bridge and production-route methods require
  current bounds unchanged, targets `$6000/0/$1000`, and immediate worker
  accumulators `[$4000,$4000,$8000]`. The next ordinary event-owner update is
  the workers' second own entry. Preserve the already-reviewed later high-word
  sequence and max-Y `bgt` equality boundary.

  Run the behavior RED slice:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance#segmentFirstRawStepUsesCursorPlusOneWithoutGrabMask+shipCrossingSetsStatusBit6AndRetainsArtPriority+artTilePriorityPropagatesIndependentlyFromPriorityBucket,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute#nativeEscapePrioritiesVisibilityAndFloorAnimationBoundary+emitterAndControllersDeferGoDeleteAcrossLaterSlots+realCapsuleResultsFloorAndCarrierCompleteTheKnucklesRoute,com.openggf.game.sonic3k.events.TestSonic3kLbzBigArmTransitionBridge#bigArmFloorBridgePublishesLiteralTargetsAndRunsNativeWorkerCadence" \
    test
  ```

  Accepted current REDs are exact: no-grab segment mapping is 7/$B because
  production reads script byte 1; ship status bit 6 is absent; root and every
  Big Arm `BossChild` report low render priority and capsule handoff clears the
  root art bit; a high-root flame remains low; the generic explosion is already
  high and the floor hitbox already low, so those are mutation guards rather
  than accepted REDs; emitter-phase floor draw count is zero; controller
  deletion clears the retained emitter shell's controller field; and floor
  settlement returns zero worker accumulators. A setup error,
  missing method selector, or scalar-only priority assertion is not an accepted
  RED.

- [ ] **Step 2: Add raw-restart, fade-slot, and graph round-trip tests and observe RED**

  Add/strengthen:

  - `TestS3kLbzFinalBoss2GraphRewind#segmentRawRestartReadsFcAndRoundTripsBeforeOwnEntry`;
  - `TestS3kLbzFinalBoss2GraphRewind#finalHitPreservesMappingUntilFadeExpiryAndRunsNativeFadeCounters`;
  - `TestS3kLbzFinalBoss2GraphRewind#fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries`; and
  - `TestS3kLbzFinalBoss2GraphRewind#capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute`.

  The raw-restart test advances each no-grab segment subtype to cursor 5/timer
  0, snapshots out of place, and executes one original/restored own entry. It
  must store cursor 6, read script byte 7 `$FC`, publish mapping 7/$B and timer
  9, then clear cursor to 0. Current production instead reads script byte 6,
  leaves cursor 6 and mapping 4/8; that assertion is the accepted first RED.

  The fade method seeds a non-5 fight mapping, reaches the eighth hit through
  ordinary touch response, and snapshots both a nonexpired fade entry and the
  expiry callback. Final-hit and nonexpired entries preserve the seeded
  mapping; expiry publishes mapping 5 and root `$2E=119` before allocation.
  Exercise real ObjectManager layouts with a free SST slot below the root and
  with only a slot above it. Immediately after the root allocation callback,
  the new native fade mode reports remaining 120 in either layout. Record and
  assert the actual owner/root slot relation. Only the fade owner's first own
  dispatch changes 120 to 119; a lower-slot owner waits for the next pass,
  while a higher-slot owner may run later in the current pass. Snapshot the
  owner at 120 and 119 and prove no music restore through own entry 120, then
  exact restore/deletion on own entry 121. Current production changes mapping
  to 5 on final hit, leaves the root counter at -1, represents the child as
  elapsed timer 0/1, and restores on entry 120; those are the accepted REDs.

  Extend the fixed-point/global graph method to capture root/child art priority,
  root status bit 6, root fade timer, native fade remaining/initialized phase,
  and the same-settlement-pass worker accumulators. Extend the capsule/floor
  graph method to snapshot the floor's visible emitter tail and the controller
  deletion boundary while asserting the already-deleted emitter shell is not
  mutated. All exact IDs, slots and required edges remain strict.

  Run the graph RED slice:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind#segmentRawRestartReadsFcAndRoundTripsBeforeOwnEntry+finalHitPreservesMappingUntilFadeExpiryAndRunsNativeFadeCounters+fixedPointLowWordsAndTimedGlobalsRoundTripAtNativeBoundaries+capsuleSignalsShipAndFloorGraphsRoundTripThroughProductionRoute" \
    test
  ```

  Require assertion failures at the source values above. Expected graph state
  must come from the native formulas and pre-capture inventory, never from
  intersecting with production survivors or assuming the fade owner is a later
  slot.

- [ ] **Step 3: Implement the source-owned behavior without widening shared contracts**

  In `ArmSegmentChild`, implement the byte cursor separately from Java array
  indexing: increment the stored cursor, read `script[1+cursor]`, and on `$FC`
  publish `script[1]`, reload `script[0]`, then clear cursor to zero. Keep the
  same-entry held override after animation. Preserve cursor/timer in generic
  rewind state.

  Remove the controller-to-emitter delete callback and delete
  `EscapeExplosionEmitterChild.forgetController`; controller pending deletion
  prunes only its own ordinary manager/root inventory. Do not make the edge
  optional and do not add a replacement callback.

  Remove mapping-5 publication from `startDefeat`. Preserve the interrupted
  fight mapping through final hit and every nonexpired `Wait_FadeToLevelMusic`
  entry. On expiry write the root defeat timer to 119 before allocating the
  fade owner, then publish mapping 5 in the `loc_746D8` transition. Add a named,
  isolated `SongFadeTransitionInstance` native-level-fade factory/mode whose
  remaining state is 120 after construction/allocation, whose first own update
  issues fade-out and falls through to 119, and whose own entry 121 restores
  level music and deletes. Do not change the public constructors or timing used
  by CNZ/MHZ/other legacy callers; capture the new mode/remaining/initialized
  scalars through the established rewind path.

  In the Big Arm floor bridge, retain the creation-pass marker but execute the
  three logical worker creation entries immediately after publishing the
  literal targets. This changes only their accumulators to
  `$4000/$4000/$8000`; current bounds remain unchanged. The next centralized
  `updatePostTitleAct2SizeWorkers` call clears the marker and executes the
  second entries. Do not move the general event-owner phase or alter the
  non-Big-Arm `Change_Act2Sizes` caller.

  At `loc_748D0`, set native root status bit 6 before entering the floor-signal
  wait. Do not couple it to an active hit-flash timer. Remove the capsule-time
  clear of root art priority and override root `isHighPriority()` from its
  captured art bit. Add captured art-priority state to `BossChild` and publish
  source-specific ownership: head dynamically mirrors root through
  `Child_GetPriority`; controller/attachment/visual/segments/joint/outer latch
  only at their reviewed source boundary; landing/grab copy their allocation
  value; debris/follow/floor use high ObjDat values; floor hitboxes stay low
  from `ObjDat_BossExplosionHitbox`, while generic `S3kBossExplosionChild`
  retains its existing high BossExplosion art contract; flame copies the root
  once at creation because `ObjDat3_RoboShipFlame` contains no art word. Keep
  all numeric priority buckets unchanged.

  Remove the emitter-phase visibility gate from `EscapeFloorChild` rendering.
  The floor still schedules/deletes at the reviewed callback boundary, but
  every returning emitter entry rejoins the normal render/touch tail.

- [ ] **Step 4: Re-run the exact slices and compatibility fleet to GREEN**

  Re-run the Step-1 and Step-2 commands unchanged. Then verify the isolated
  fade mode did not change legacy timing, and rerun the complete route/graph
  guard gate:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestCnzCutsceneFadeTiming,com.openggf.game.sonic3k.objects.TestS3kSelfContainedTransientRewind,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute,com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard,com.openggf.game.rewind.TestEveryObjectRewindRoundTrip,com.openggf.game.rewind.TestGraphCoveredIsolatedProbeClassification,com.openggf.game.rewind.TestRewindArchitectureGuard" \
    test
  ```

  Record fresh per-class counts. The legacy fade tests must keep their exact
  existing 91/121 post-init behavior. The graph gate must fail for an uncaptured
  art bit, status bit, raw cursor, fade phase/counter, worker accumulator, or
  changed required edge.

  Run the architectural source guard separately because its base branch has two
  accepted line-budget failures:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 \
    "-Dtest=com.openggf.tests.TestArchitecturalSourceGuard" \
    test
  ```

  Require exactly the established two failures and no new or worsened row:
  `ObjectManager.java` actual 3036 over limit 2914 and
  `AbstractPlayableSprite.java` actual 3175 over limit 3161. A changed actual
  count, changed limit, third failure, error, or unexpected pass must be
  investigated and compared with the recorded base rather than hidden.

- [ ] **Step 5: Restart all evidence, documentation counts, and final review**

  These changes invalidate the current 7/7 production-route, 8/8 graph,
  944/944 graph/guard, and 1,477/1,477 consolidated reports and every status row
  derived from them. First rerun Task 9B Step 4 in full, including both legacy
  fade classes, all rewind guards, and the separate architectural-source-guard
  baseline. Then run Task 12 Step 1's complete selector, Task 10's trace lane,
  and Task 12 Step 2's exact ratchets; replace all nine status-document
  rows only from fresh reports. Obtain another full independent code/test/docs
  review. Any further review-driven source/test change repeats this entire
  step. Commit remains held until explicit PASS and the subsequent docs-only
  wording review.

### Task 10: Add comparison-only Knuckles LBZ route evidence

**Files:**

- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kKnucklesLbz2BigArmTraceReplay.java`
- Modify: `docs/status/trace-frontier-log.md`

**Interfaces:**

- Consumes: committed run directory
  `src/test/resources/traces/s3k/runs/s3k-knuckles-complete-superemeralds/lbz_2`
  through `AbstractTraceReplayTest` and the ordinary frame-closure driver.
- Produces: an independent route checkpoint or exact earlier frontier report;
  never gameplay authority.

- [ ] **Step 1: Add the scenario class**

  Configure S3K, zone 6, engine act 1, and the committed run path. Do not add a
  new trace payload, tolerance, bootstrap state override, or copied aux values.

- [ ] **Step 2: Run the trace lane**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.tests.trace.s3k.TestS3kKnucklesLbz2BigArmTraceReplay" \
    test
  ```

  If green, record total frames/checkpoints. If red before Big Arm, record exact
  first frame/field, error count, and remaining rows; do not adjust this port to
  absorb an earlier divergence. Acceptance still requires the independently
  driven production lifecycle test from Tasks 7-8.

- [ ] **Step 3: Update the frontier log**

  Record the exact command, branch/commit context, pass/fail, error count, and
  first-error frame/field. State explicitly whether the canonical production
  route reached Big Arm.

### Task 11: Update current documentation without outrunning final review

**Files:**

- Modify: `CHANGELOG.md`
- Modify: `README.md`
- Modify: `docs/guide/playing/game-status.md`
- Modify: `docs/status/s3k-known-bugs.md`
- Modify: `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`
- Modify: `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`
- Modify: `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`
- Modify: `docs/architecture/research/s3k-zones/lbz-analysis.md`
- Retain: this design and plan

**Interfaces:**

- Consumes: fresh implementation/test/trace evidence.
- Produces: accurate current status while preserving both rejected-attempt
  histories and any still-red trace frontier.

- [ ] **Step 1: Update release and player-facing status**

  Keep Unreleased/dated entries explicitly at candidate/final-review status
  while the mandatory code/docs review is pending. State only the bounded
  native Big Arm graph/fight/defeat, ROM art/data, two-signal capsule
  continuation, rewind and route evidence actually green at that point. Describe
  the falling-floor loop as exactly 127 qualified escape-emitter allocation
  attempts `$7E..$00`, at most 127 successful emitters depending on free SST
  slots, and a separate one-shot controller attempt per successful emitter.
  Remove “inert handoff” only after Tasks 2-9 are green, but do not label the
  remediation resolved until Task 12's independent review passes. Do not claim
  full LBZ trace parity if Task 10 remains red. A direct `$CC` constructor or
  `onPlayerAttack` call is not production evidence.

- [ ] **Step 2: Close the remediation item without rewriting history**

  In the audit/validation/roadmap/LBZ analysis, retain `98d968d7f` and v2 as
  rejected evidence, then record the current candidate branch/base, source span,
  production lifecycle test, rewind result and exact trace result. Keep the
  blocker in final-review state. `s3k-known-bugs.md` may move/mark only the
  inert-handoff blocker resolved after Task 12's independent PASS; retain any
  concrete parity gap the trace still proves. Describe raw frame 6314 as a
  schema-valid completion the strict schedule compiler cannot yet represent,
  never as an intrinsically invalid trace row.

- [ ] **Step 3: Self-review documentation and diff**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  rg -n -i "T[B]D|T[O]DO|implement[ ]later|fill[ ]in details|appropriate[ ]error handling" \
    docs/architecture/designs/2026-08-09-lbz-big-arm-rom-port-design.md \
    docs/architecture/plans/2026-08-09-lbz-big-arm-rom-port-plan.md
  ! rg -n "127[-]emission|127 explosion emission[s]|127 later-slot emission[s]|127 qualified explosion-controller allocation attempt[s]" \
    CHANGELOG.md \
    README.md \
    docs/guide/playing/game-status.md \
    docs/status/s3k-known-bugs.md \
    docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md \
    docs/architecture/designs/2026-08-09-lbz-big-arm-rom-port-design.md \
    docs/architecture/plans/2026-08-09-lbz-big-arm-rom-port-plan.md \
    docs/architecture/validation/2026-08-08-unfinished-code-remediation.md \
    docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md \
    docs/architecture/research/s3k-zones/lbz-analysis.md
  git diff --check
  ```

  Expected: no placeholder matches and no whitespace errors.

### Task 12: Run the final focused suite, policy, and local commit

**Files:**

- Verify every intended source, test, and documentation file above.
- Create no extracted ROM asset, generated report, or trace payload.

**Interfaces:**

- Consumes: completed port and fresh JDK 21 ROM-backed test state.
- Produces: one reviewed, policy-compliant local commit ready for parent
  integration evaluation.

- [ ] **Step 1: Run the consolidated focused suite**

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  mvn -Dmse=off -Dsurefire.forkCount=1 -Dsurefire.runOrder=alphabetical \
    "-Ds3k.rom.path=${task_root}/Sonic and Knuckles & Sonic 3 (W) [!].gen" \
    "-Dtest=com.openggf.game.sonic3k.objects.TestLbzFinalBoss2RomData,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2EggCapsuleInstance,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2Instance,com.openggf.game.sonic3k.objects.TestS3kBossExplosionChild,com.openggf.game.sonic3k.objects.TestLbzFinalBoss2ProductionRoute,com.openggf.game.sonic3k.objects.TestLbzFinalBoss1Instance,com.openggf.game.sonic3k.objects.TestLbz2EndSequenceRegistry,com.openggf.game.sonic3k.objects.TestAiz2BossEndSequenceObjects,com.openggf.game.sonic3k.objects.TestAizEndBossInstance,com.openggf.game.sonic3k.objects.TestAizMinibossCutsceneInstance,com.openggf.game.sonic3k.objects.TestCnzMinibossDefeatPhase,com.openggf.tests.TestCnzEndBossExplosionController,com.openggf.game.sonic3k.objects.TestCutsceneKnucklesLbz1CollapseChild,com.openggf.game.sonic3k.objects.TestHczEndBossInstance,com.openggf.tests.TestS3kIczEndBossObject,com.openggf.game.sonic3k.objects.TestLbzEndBossInstance,com.openggf.game.sonic3k.objects.TestMgzDrillingRobotnikInstance,com.openggf.game.sonic3k.objects.TestMgzEndBossKnuxInstance,com.openggf.game.sonic3k.objects.TestMgzMinibossInstance,com.openggf.game.sonic3k.objects.TestMhzBossObjects,com.openggf.game.sonic3k.events.TestSonic3kLbzBigArmTransitionBridge,com.openggf.game.sonic3k.events.TestSonic3kLbzRewindRoundTrip,com.openggf.game.sonic3k.TestSonic3kPlcArtRegistry,com.openggf.game.rewind.TestS3kLbzFinalBoss2GraphRewind,com.openggf.game.rewind.coverage.TestRewindCoverageGuard,com.openggf.game.rewind.coverage.TestStaticStateRewindCoverageGuard,com.openggf.game.rewind.TestEveryObjectRewindRoundTrip,com.openggf.game.rewind.TestGraphCoveredIsolatedProbeClassification,com.openggf.game.rewind.TestRewindArchitectureGuard,com.openggf.tests.TestS3kAiz1SkipHeadless,com.openggf.tests.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kLevelLoading,com.openggf.game.sonic3k.TestSonic3kBootstrapResolver,com.openggf.game.sonic3k.TestSonic3kDecodingUtils" \
    test
  ```

  Report the exact test count; do not infer it in advance. Then run the trace
  command from Task 10 separately so its frontier is not hidden among unit
  results.

- [ ] **Step 2: Replace provisional documentation counts with fresh evidence**

  From the just-completed Surefire reports, record actual per-class and total
  executed/pass/fail/error/skip counts; do not carry forward the candidate's
  now-invalid `7/7` route, `8/8` graph, `944/944` graph/guard or
  `1,477/1,477` consolidated results, nor the prior `5/5` route, `7/7`
  graph, `936/936` graph/guard, `1,470/1,470` consolidated, four-graph-method,
  four-route-method, `4/4`, `6/6`, `1,133/1,133`, `933/933`,
  `1,462/1,462`, or `935/935` numbers. Update every Big Arm evidence row in:

  - `CHANGELOG.md`;
  - `README.md`;
  - `docs/guide/playing/game-status.md`;
  - `docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md`;
  - `docs/architecture/validation/2026-08-08-unfinished-code-remediation.md`;
  - `docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md`;
  - `docs/status/s3k-known-bugs.md`;
  - `docs/architecture/research/s3k-zones/lbz-analysis.md`; and
  - `docs/status/trace-frontier-log.md`.

  Then run the exact stale-evidence ratchet:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  ! rg -n "four (out-of-place )?exact-ID graph|four Big Arm graph|four-method Big Arm production-route|Big Arm graph tests pass 5/5|Big Arm graph round trips pass 5/5|production-route class passes 4/4|passes 4/4, including the real FinalBoss1 wrapper|Earlier 4/4 (route|production-route), 6/6 graph|candidate production evidence recorded 4/4 route, 6/6 graph|six out-of-place graph methods pass 6/6|methods pass 6/6\. The fresh|production route 5/5|5/5, exact-ID graph 7/7|passes 5/5 production-route methods|7/7 exact-ID graph methods|passes 5/5 route, 7/7 graph|results are 5/5 production route, 7/7 exact-ID graph|records 5/5 route, 7/7 graph|route, 7/7 exact-ID graph|All five production-route methods pass|all seven out-of-place exact-ID graph methods pass|7/7, exact-ID graph 8/8|7/7 production route, 8/8 exact-ID graph|7/7 route, 8/8 exact-ID graph|7/7 route, 8/8 graph|8/8 exact-ID graph methods|8/8 graph methods|all eight out-of-place exact-ID|1,477/1,477|944/944|1,470/1,470|936/936|suite passes 1,462/1,462|1,462/1,462 focused/rewind|graph/guard subset passes 935/935|935/935 graph/guard|1,133/1,133|933/933" \
    CHANGELOG.md \
    README.md \
    docs/guide/playing/game-status.md \
    docs/architecture/audits/2026-08-08-dead-and-unfinished-code.md \
    docs/architecture/validation/2026-08-08-unfinished-code-remediation.md \
    docs/architecture/plans/2026-08-08-unfinished-code-remediation-roadmap.md \
    docs/status/s3k-known-bugs.md \
    docs/architecture/research/s3k-zones/lbz-analysis.md \
    docs/status/trace-frontier-log.md
  ```

  Require no match. Any new numeric claim must come from the fresh reports and
  must distinguish the production-route, graph, compatibility, focused/guard,
  and trace-lane outcomes rather than summing incompatible invocations.

- [ ] **Step 3: Obtain a fresh independent final code/docs review**

  Give the reviewer the green design, this green plan, current uncommitted
  production/test/docs diff, skdisasm spans, and fresh RED->GREEN command
  outputs. Require explicit PASS for dispatch/callback cadence, fixed-point
  low words, raw animation, render commands/priorities, articulated lifetimes,
  global/shake order, production wrapper/touch route, rewind identity, docs,
  and policy. Hold commit for any concrete blocker; if a fix changes a reviewed
  assumption, amend design and plan and repeat their separate green reviews
  before changing production. Any review-driven source or test change then
  restarts at Task 9B Step 4: rerun its legacy-fade compatibility classes,
  complete route/graph/rewind guards and separate architectural-source-guard
  baseline before Task 12 Step 1's consolidated suite. Then rerun the Task-10
  trace lane, regenerate every count/status row in Step 2, and obtain another
  full independent review. Never reuse the pre-change reports or proceed
  directly to a docs-only closeout.

- [ ] **Step 4: Finalize resolution wording only after review PASS**

  Only after the reviewer returns explicit code/test PASS, replace candidate/final-review
  language with a bounded dated resolution in the eight Task-11 status files.
  Use only the fresh Step-1/Step-2 counts; retain both rejected attempts and the
  still-red trace compiler boundary. Re-run the stale-evidence ratchet and
  `git diff --check`, then send the mechanical docs-only diff back to the same
  reviewer and require no remaining documentation blocker before staging. A
  docs-only correction requested at this phase is fixed and re-reviewed as a
  docs-only diff; any request that changes source or tests restarts Task 9B
  Step 4, Task 12 Step 1, trace execution, counts, docs, and the full review
  loop again.

- [ ] **Step 5: Inspect and stage only intended work**

  Confirm no ROM, extracted asset, disassembly file, generated trace, or
  unrelated work is present. Inspect but do not remove `target/task-tmp` yet:
  commit and pre-push hooks may create further task-local output. Stage every
  task artifact, including the green design and plan.

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  git diff --check
  git diff --stat
  git diff
  git status --short
  find "$task_root/target/task-tmp" -mindepth 1 -maxdepth 3 -print
  git add CHANGELOG.md README.md docs src
  git diff --cached --check
  git status --short
  ```

- [ ] **Step 6: Run policy and commit**

  Run policy, then commit normally with the required subject and trailers:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  .githooks/run-policy pre-commit
  git commit -m "feat(s3k): port LBZ Big Arm from ROM" \
    -m $'Changelog: updated\nGuide: updated\nKnown-Discrepancies: n/a\nS3K-Known-Discrepancies: n/a\nAgent-Docs: n/a\nConfiguration-Docs: n/a\nSkills: n/a'
  ```

  Do not use `--no-verify`.

- [ ] **Step 7: Verify the committed local branch**

  Re-run `git status --short --branch`, `git show --stat --oneline HEAD`, then
  feed the policy hook a synthetic update range so it validates the actual
  committed candidate rather than empty stdin:

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  candidate_tip=$(git rev-parse HEAD)
  printf '%s %s %s %s\n' \
    refs/heads/feature/ai-lbz-big-arm-evidence "$candidate_tip" \
    refs/heads/feature/ai-lbz-big-arm-evidence \
    9de7ecf7230100626fb7084b3f678daa6a5f478c \
    | .githooks/run-policy pre-push origin
  ```

  Require the policy range to pass. Keep the feature branch local; do not merge
  or push.

- [ ] **Step 8: Classify and remove only task-local generated output**

  After every Maven invocation and hook has completed, inspect the exact
  ignored task directory one final time. Confirm every entry is generated by
  this task, remove only that directory, and then record the final branch and
  clean tracked-worktree state. Do not inspect or remove unknown shared `/tmp`
  content.

  ```bash
  task_root=$PWD
  mkdir -p "$task_root/target/task-tmp"
  export TMPDIR="$task_root/target/task-tmp"
  export MAVEN_OPTS="-Djava.io.tmpdir=$task_root/target/task-tmp"
  test "$(git rev-parse --show-toplevel)" = "$task_root"
  find "$task_root/target/task-tmp" -mindepth 1 -maxdepth 3 -print
  rm -rf -- "$task_root/target/task-tmp"
  git status --short --branch
  git show --stat --oneline HEAD
  ```

  Require a clean tracked worktree and report the local branch tip/base. Empty
  ignored build output outside the classified task directory is not part of
  this cleanup.
