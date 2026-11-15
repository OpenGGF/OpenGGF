---
name: scd-implement-object
description: Use when implementing or changing a Sonic CD object or badnik from the scddisasm, on the shared object engine.
---

# Sonic CD objects

Sonic CD's object system is Sonic 1's: 32 reserved slots, then 96 `dynObjects`;
5-byte mapping pieces; `AnimateObject` is AnimateSprite. Port the routine, not
the slot: never model `oVar` bytes or addresses.
Find routines and the stage's dialect with `scddisasm-guide`.

## Where things go

- One class per object: `game/soniccd/objects/<zone>/` (zone lanes) or
  `objects/common/` (common lane). Extend `objects/common/CdObject`.
- Register only in your fragment: `CommonObjects` or `<Zone>Objects`
  (`registrar.object(id, "Name", (spawn, r) -> new CdX(spawn))`). IDs come from
  that stage's `Object Index (...).asm`; a zone ID overrides the common one. A
  routine whose ID varies by stage uses `registrar.stageObject("<obj>Id", ...)`
  over a catalogue ID column (`ROUTINE_IDS` in `object_catalogue_common.py`).
- Examples: `CdRing` (children with `addDynamicObjectAfterCurrent` =
  FindNextObjSlot), `CdMonitor` (touch, solid, break, children with
  `addDynamicObject` = FindObjSlot), `CdSpring` (four orientations, player
  writes), `CdSpikes`/`CdBoulder` (SetObjectTileID), `CdFloatBlock` (time
  zone, TopSolidObject), `CdCheckpoint` (child, saved state), `CdTestBadnik`
  and `PpzBadnik` (`killAsEnemy(player, layer, fromBadnik)`), `CdExplosion`/`CdPoints` (script + delete),
  `CdTitleCard`/`CdHud`/`CdResults` (reserved slots, screen space).
- In-place slot rewrites (`move.b #id,oID(a0)`): `detachSlotForTransfer` +
  `addReplacementAtTransferredSlot` (`CdExplosion` -> `CdFlower`).

## Slots and execution order

- Allocate as the routine does: `findObjSlot(child)` for `FindObjSlot` /
  `SpawnObject` (first free dynamic slot; the child may run next frame),
  `findNextObjSlot(child)` for `FindNextObjSlot` / `SpawnObjectAfter` (after
  this slot; the child runs later in this pass). Both return false when full.
- A child that tests its parent (`movea.w var,a1; cmpi.b #id,oID(a1)`) keeps
  the parent's **slot number** and calls `slotHoldsId(slot, id)` every turn
  (`slotOccupant(slot)` for the object there). Never hold the parent by Java
  reference: a parent removed by `CheckObjDespawn` is unloaded, not destroyed,
  and a child holding it outlives native's. Examples: `ObjSnakeBlocks`, the
  spike chain, tube door caps.
- A byte copy of a spawner's slot copies its draw state too: `copyDrawState`.
- The spawner walks the layout in stored order (`StoredOrderObjectLayout`), and
  the spawner runs before the object pass (`SpawnerBeforeObjectPass`).

## Semantics `CdObject` already gives you

- `x`, `y` (centre, `oX`/`oY`), `priority` (`oPriority` → `RenderPriority.bucket`),
  `tileWord` (`oTile`: palette and priority), `width`, `yRadius`.
- `renderFlagOnScreen()` = `tst.b oSprFlags` (last frame's DrawObjects).
- Despawn: override `despawnsByCheckObjDespawn()` → true when the routine ends
  in `CheckObjDespawn`; otherwise the object is never range-unloaded. Override
  `isCustomOutOfRange` for an own check (see `CdSpring`, initial X).
- `subtype2()` = `oSubtype2` of the layout entry; `timeZone()`, `zone()`, `act()`.
- `flags()` = `CdLevelFlags`: `ctrlLocked`, `scrollLock`, `goodFuture`,
  `specialStage`, `timeOver`, `projDestroyed`, `playerCtrlHold`.
- `animator(e -> e.someAnimations(), anim)` → `CdAnimator.step()` returns 2 on
  `$FC` (add it to your routine).

## Art

- Object art is VRAM: `CdObjectArtProvider` decodes the level PLC and PLC 1 into
  `CdStageVram`; a sheet is the window at the object's `oTile`. Draw with
  `drawFrame(key, frame, hFlip, vFlip)` (uses `tileWord`'s palette line).
- **A zone lane owns three catalogue files** (`<z>` = ccz, ttz, qqz, wwz, ssz,
  mmz; `common` and `ppz` are taken): `tools/soniccd/object_catalogue_<z>.py`
  (its `ANCHORS`: column, signature, mode, citation), the generated
  `src/main/resources/soniccd/object-catalogue-<z>.tsv`, and
  `objects/<z>/<Z>ObjectCatalogue.java` (accessor; add named methods if you
  like). Its object fragment `<Z>Objects` registers sheets in
  `registerArt(art)`; keep per-load state in `art.zoneArt(Type.class, ...)`
  (`PpzObjectArt` is the example). Shared core files stay untouched.
- Anchors: the instruction that loads the table, e.g. `217C AAAAAAAA 0004 317C
  05A8 0002`; `PPPP` = d16(PC), `00QQ` = d8(PC,Xn); modes `required`,
  `optional`, `ambiguous`. Regenerate/check:
  `python3 tools/soniccd/object_catalogue.py --media-root ~/Downloads/Sonic-CD/media
  --owner <z> [--check]`. Never transcribe tables; never read the disassembly at
  runtime.
- Later-PLC or copied art: build the sheet from `art.vram().copy()` with the cue
  decoded or the bytes written (`CommonObjectArt.registerLevelEnd`).
- Badnik art comes from later cues (title card: PLC 2; section art:
  `Section Art Load.asm`). Load them through `CdStageVram.loadPlc` before
  registering the sheet; queue timing is not modelled.
- `SetObjectTileID`: `provider.objectTileId(index, subtype2())`; sheets: `art.objectTileIds(index)`.

## Rules

- Cite every constant and branch as `Label (File.asm:a-b)`. The label must sit
  at or before the range (≤250 lines); `python3 tools/disasm/disasm_citations.py check`
  runs in the pre-commit hook. Model `FixBugs = 0`.
- No zone-name tests in shared code; a per-stage difference is catalogue data or
  a profile value (e.g. the explosion tile word column).
- Player writes use the shared playable API as `CdSpring` does; touch via
  `TouchResponseProvider`/`Listener` (enemy kill: `TouchResponseAttackable` +
  `killAsEnemy`).
- Collision types `$C0` decode as BOSS; a special (non-boss) `$C0` object
  overrides `usesS3kTouchSpecialPropertyResponse()`. Native tests a special
  touch on **every** frame of overlap, so such an object also overrides
  `requiresContinuousTouchCallbacks()` to return true; the engine default fires
  once per overlap (`CczBumper`, `ObjBreakableWall`, the bumper platform).
- PLC waits (`tst.l plcBuffer`): `CdPlcQueue.framesToDrain` (level frames;
  lag frames do not decode). `CdTitleCard` and `CdResults` show the countdown.

## Solids: CD's SolidObject, exactly

- Implement `ReevaluatingSolidObject` and call `solidObject(Entry.SOLID | TOP |
  BOTTOM)` from `update` where the routine calls `SolidObject`,
  `TopSolidObject` or `BtmSolidObject`. It returns d0 (top/bottom contact).
  Call it twice if the routine does (`CdFloatBlock`).
- The routine reads `oWidth`/`oYRadius` (`width`, `yRadius`), the retained
  on-screen bit, and `solidXVelocity()`/`solidYVelocity()` for riders. Never
  return a `SolidObjectParams` of your own.
- ID tests in Object Solid.asm are `solidRole()`: `SPRING` ($A),
  `MONITOR` ($19), `PASS_UNDER` (spinning disc, 9), `CRUSH_EXEMPT` (capsule,
  $15); `solidSpringFacesUp()`, `solidVerticallyFlipped()`.
- Object bits: `stoodOn(player)` (bit 3), `pushedBy(player)` (bit 5),
  `clearStoodOn()`, `clearPushedBy(player)`; an object's own `jsr
  GetOffObject` is `ObjectManagerInternalAccess.releaseFromReevaluatingSolid`.
- `.CheckFloor` runs for still objects: a solid whose top is level with the
  terrain releases its rider. The routine is
  `level/objects/ReevaluatingSolidRoutine` (S1 differences in its javadoc).
- Two dialects exist (`src/Level/_Objects` and `src/objects` + `rN`); check
  which one the stage includes (`<Zone>/Act N <Period>.asm`).

## Tests and native checks

- Focused behaviour: add a case to a class like `TestSonicCdCommonObjects`
  (boots R11A from the disc, `add(new CdX(spawn))`, steps, asserts cited values).
- Spawner/slots: `TestSonicCdR11aObjectAppearances` compares every dynamic
  slot appearance (ID, slot, levelFrames) with the K2 trace `aux_state.jsonl`
  over the frames where player and camera match. A new object in that route
  must keep it green; record other routes the same way.
- Pictures: `TestSonicCdR11aObjectPicture` (native frame region compare). The
  picture shown on row R is the state after level frame lf(R)-1 (V-INT DMAs
  what the previous iteration wrote; load = level frame 1). Pair by where the
  shot was taken: a fixture `probe.sh` shot at offset+row+1 is row+1's, so
  compare the engine at lf(row+1)-1 (`TestMmzObjectsPictures`); a boss
  recording's shot of its own trace row pairs at lf(row)-1
  (`TestPpzBossPictures`). No best-of pairing; list a shot no frame pairs as
  uncertified, failing if it starts to pass.
- Any fixture: `CdFixtureReplay.replay(tempDir, payload, lastRow, afterRow)`
  replays a `rehost-fixtures` movie and returns the first divergent row
  (`TestSonicCdSolidObjectTrace`, `TestSonicCdCommonObjectsNative`). Each
  engine row is compared with the last native row of the same level frame
  (`CdFixtureReplay.completedRow`): native samples some rows mid-pass. It only
  selects the native row; never move the engine to match one.
- Run every test through the queue, never plain `mvn test`:
  `python3 tools/testing/maven_queue.py --normal -Dmse=off
  "-Dsoniccd.media.root=$HOME/Downloads/Sonic-CD/media" -Dtest=... test -B`;
  GL tests add `-Dopenggf.headless.gl=surfaceless-egl` with `DISPLAY` unset.
  Native captures default to `<media root>/../native` (`-Dsoniccd.native.root`);
  without the disc or captures the tests skip, classified in
  `tools/testing/release-skip-policy.json`.
  Break one native comparison on purpose once to prove it can fail.
