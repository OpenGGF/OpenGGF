---
name: scd-implement-boss
description: Use when implementing or changing a Sonic CD boss (act 3 Good/Bad Future) or its capsule, on the shared object engine.
---

# Sonic CD bosses

Each act 3 links its own object index with the boss and its parts. A boss is
CD objects (`scd-implement-object` applies) plus a fragment, an arena, a hit
graph and a hand-off. Worked example: Palmtree Panic, `objects/boss/ppz/`
(Eggman $2A, body, legs, shoulder, arms, bumpers) and `objects/boss/CdCapsule`.

## Where things go (edit nothing outside these)

- Package `objects/boss/<zone>/`; its fragment `<Zone>BossObjects` (stub exists)
  implements `CdBossFragment`: `register` (IDs from `<Zone>/Object Index
  (Act 3).asm` or `rN/object_index_3.asm`, ID = entry position + 1),
  `registerArt`, `levelEvents`, `hostsLevelEvents`. On act 3 the registry asks
  it first, then stage-scoped zone, shared, common.
- Catalogue owner `boss<zone>`: `tools/soniccd/object_catalogue_boss<zone>.py`
  (found by name), generated `object-catalogue-boss<zone>.tsv`, accessor like
  `PpzBossCatalogue`. Anchor every mapping, script and data table (walk,
  explosion offsets, speeds); never transcribe. Regenerate:
  `python3 tools/soniccd/object_catalogue.py --media-root ~/Downloads/Sonic-CD/media --owner boss<zone>`.
- Capsule: register `CdCapsule` at your index's ID; `CdCapsule.registerArt`.
- Tests: `Test<Zone>BossNative` over `CdBossNativeReplay.check` with your
  `Spec`; your own picture and rewind classes (copy the PPZ ones).

## Parts

- Extend `CdBossPart`: 16.16 position (`xLong/addXLong`), `routine`,
  `mapFrame`, `colType/colStatus` (`TouchResponseProvider`), `animate(table)`,
  `spawn` (FindObjSlot), `explode` (explosion $18, `st oRoutine2`, FM $9E),
  `fmSound`, `subCpu`, `loadPalette(id)`, `loadCapsulePalette()`,
  `RewindRecreatable` via `shell(spawn)`. Name scratch bytes by use in a zone
  base (`PpzBossPiece`); never model slot offsets.
- Links (`var_2e/30/32`) are typed non-final fields, rewound by object id.
  Never use a name in `DefaultObjectRewindPolicies.STRUCTURAL_OBJECT_FIELD_NAMES`
  (`arm`, `shoulder`, `frontThigh`, `parent`, `boss`...): not captured, null
  after restore. Null a link nothing reads again (falling parts).
- Allocate in the asm's FindObjSlot order inside the parent's routine.
- Same offset as byte and long (thigh `var_3c`) is one field with accessors.
- Read what a subroutine leaves in registers: `sub_20C500` returns a1/a2 on a
  bumper and the shoulder, so the routine's first pass stops (FixBugs = 0).
- `bset/bclr/bchg` return the old bit (`testAndSet`/`clear`/`toggle`);
  `addq.l #4,sp; rts` skips AnimateObject/DrawObject: override `drawn()`.

## Arena, events, presentation

- After the object pass the level-event hook runs, in the main loop's order:
  `CdStageExit.afterObjectPass` (levelRestart), then only while `scrollLock`
  is clear `CdBossLevelEvents.run` (your `levelEvents`), then `CdStageEvents`
  (RunLevelEvents' bottom-boundary tail, 4 px a frame, all stages). Return
  `hostsLevelEvents() = true` once you host the act 3 branch: the tail then
  takes the target from the camera's max-Y target that your boss and events
  write, instead of its own act 3 fallback.
- The boss owns `bossFight`/`bossFlags` (`bossFight()`, `bossFlags()`); events
  and zone inputs read it (QQZ `setBossFlags`, WWZ `WwzStageSignals.bossFlags`,
  MMZ `boss_door_flag`, PPZ/CCZ picture masks). `bossFight` also drops the
  player's 56-px right extension: `gameState().setScreenLocked(bossFight)`.
- Camera: `leftBound` = `setMinX`, `rightBound` = `setMaxX`, bottom and
  target = `setMaxY`, target = `setMaxYTarget`, current = `setMaxYCurrent`,
  `camYCenter` = `get/setYPosBias`. CamBounds: +2 left, +4 right.
  `CdBossLevelEvents.checkBossStart` is `CheckBossStart`/`SetBossBounds`.
- Art: `CdBossArt.vramAfter(art, TITLE_CARD_PLC, bossPlc)`, `register`, `key`.
- Sound: FM by native ID, sub-CPU by command ID (`subcpu-audio-commands.tsv`:
  $67 `SCMD_BOSSMUS`, $7E `SCMD_STOMPSFX`, zone future music after escape).

## Touch and hits

- `$C0` types of size $3C-$3F go through `Player_TouchEnemy` while
  `bossFight` is 1: the shared BOSS branch rebounds or hurts. A
  `TouchResponseListener` clears `colType` (`PpzBossEggman.touched`). Other
  `$C0` types: `usesS3kTouchSpecialPropertyResponse() = true`. `$80` hazards.
- The boss polls part `colType`s for hits; keep quirks (PPZ: a non-rolling
  touch hurts the player and still counts).

## Act end

`CdCapsule` runs `StartResults`, holds the player's input while `ctrlLocked`
forces `playerCtrlHold`, and spawns `CdResults`, whose `NextLevel` reports
`CdStageExit.actCleared`; IPX goes to the next zone (stage select: the title).
MMZ hands off from its own last routine.

## Native recording (worked for PPZ)

Tools in `~/Downloads/Sonic-CD/native/rehost-boss-ppz/_tools/` (README):
1. `drive.py <R?3C> <name> <inputs.json> <frames>`: boss probe run (~2 s), env
   `BOSS_IDS=2A,2B,..` picks the logged slot IDs; `events.py` summarises.
2. Reach the arena with Right and periodic jumps from `rehost-fixtures`' route.
3. `chunks.py` (greedy random 24-frame chunks scored by hits), copy it for
   other goals (`bumper_search.py`); run one search at a time.
4. `record_boss.py <payload> <name> <inputs.json> <frames> <shots>` with
   `BOSS_OUT=<native dir>`: BK2, v5 trace, `probe/` RAM CSVs, screenshots.
Record a clean fight, a hit, a death, the act end and each special contact.

## Checks

- `CdBossNativeReplay`: player/camera rows through the capsule's break;
  appearances (row, ID); every boss slot's ID, X, Y, routine, main object's
  collision and hit count vs `probe/objects.csv`; bounds vs `globals.csv`.
- Pictures (`TestPpzBossPictures`): native row R shows the state after level
  frame lf(R)-1 (V-INT copies the sprite table and scroll). A `record_boss.py`
  shot is of its own trace row: compare the engine after lf(row)-1. A fixture
  `probe.sh` shot at offset+row+1 is row+1's instead: lf(row+1)-1. No best-of
  pairing; list unpairable shots as uncertified; mask = gameplay render !=
  tiles-only render; >= 95%.
- Rewind (`TestPpzBossRewind`); act end (`flow/TestPpzBossActEndFlow`, flow harness).
- Children that test a part (`cmpi.b #id,oID(a1)` on a stored slot) use
  `slotHoldsId`, as in `scd-implement-object`; boss links rewound by object id
  stay typed fields.
- Break one comparison on purpose, then restore.
- Guards by name: `TestCameraRelativeDrawGuard`, `TestDrawPathFieldWriteGuard`,
  `TestNativeHolderReleaseGuard`, `TestObjectPhysicsStandardizationGuard`,
  `TestBroadCatchSwallowingGuard`, `TestArchitecturalSourceGuard`,
  `TestArchUnitRules`. Cite `Label (file.asm:a-b)`;
  `python3 tools/disasm/disasm_citations.py check` must pass.
