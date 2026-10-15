# Second Strike C1-east — CNZ / ICZ / LBZ implementation checkpoint

Status: **in progress; not a complete C1-east delivery.** Pinned base `develop`
`76022570e8233823e1e36419666a0f29c768f4b6`; private tree
`.worktrees/ai-second-strike-c1-east`, branch `feature/ai-second-strike-c1-east`.
Binding scope: [campaign scope](../designs/2026-10-15-second-strike-campaign-scope.md)
(CNZ, ICZ, LBZ rows) and the [campaign delivery plan](2026-10-15-second-strike-campaign-delivery.md).

## Strategy (root-approved)

Native owners expose **finite immutable enum recipes** under
`com.openggf.game.sonic3k.difficulty` with `STOCK` as default; a supported
`@ModApi` facade (root-sequenced) constructs the native owner for an **original
record only** and returns the supported `AbstractObjectInstance`. No callbacks,
no raw placement edit, no copied native AI, no generic difficulty engine. The
mod classes bind original native placements through
`LevelPatch.nativePlacements().bind(...)` and retain original ring records.

## Source work (what exists)

| Zone | Native owner (preset) | Original records (CRC32 63522553) | Authored by the mod class |
| --- | --- | --- | --- |
| CNZ (3) | `ClamerObjectInstance` / `ClamerRecipe`: `CADENCE` range `$60` (96) → `$90` (144); `STAGGERED_CADENCE` also holds the tell frame `$20` (32) updates longer (`$2F` 47 → `$4F` 79); the owner never reads record identity; spring child unchanged | act 1 16 Clamers, act 2 11 (two stacked at `$27E8,$7B0`) | bind all Clamers `CADENCE`, act 2 records 180 and 456 (second of each adjacent pair) `STAGGERED_CADENCE`, rings 77/123/308 (act 1) and 12/117/239 (act 2), Robotnik monitors within `$100`×`$80` of a Clamer |
| ICZ (5) | `IczStalagtiteObjectInstance` / `IczStalagtiteRecipe.WARNED`: trigger `$70`→`$A0`, shake `$0F`→`$2F` | act 1 nine Stalagtites; **act 2 has none** | bind them, rings 54/135/131 (act 1), 84/164/341 (true ICZ2, x >= $930), Robotnik monitors near Stalagtites |
| LBZ (6) | `LbzAlarmObjectInstance` / `LbzAlarmRecipe.BOUNDED` + `Flybot767BadnikInstance.asBoundedEscort()`: one escort per alarm, one live at a time, escort leaves after its first attack cycle or when scripted control starts | act 1 nine alarms; **act 2 has none** | bind them, rings 109/255/171/293 (act 1), 61/210/362 (act 2), Robotnik monitors near alarms |

Mod classes: `secondstrike/campaign/{CarnivalNight,IceCap,LaunchBase}Campaign.java`
(each `public static register(ModContext)`), with shared
`secondstrike/campaign/east/EastEconomy.java`.

Preserved: speed-shoes monitors, hidden/boss reward boxes, giant rings, native
barrels/balloons/bumpers/platforms/cannons, the ICZ1 snowboard descent and ice
friction, Knuckles' cutscene/corridor objects, lives, checkpoints, saves and
results.

## Public contract requested from root

```
@ModApi enum ClamerRecipe        { STOCK, CADENCE, STAGGERED_CADENCE }  int autoCloseRange(); int tellHold()
@ModApi enum IczStalagtiteRecipe { STOCK, WARNED }    int triggerRange();  int shakeTimerStart()
@ModApi enum LbzAlarmRecipe      { STOCK, BOUNDED }
@ModApi final class CarnivalNightFactory (com.openggf.mods.objects)
    static AbstractObjectInstance clamer(ObjectSpawn, ClamerRecipe)           // objectId 0xA3
@ModApi final class IceCapFactory
    static AbstractObjectInstance stalactite(ObjectSpawn, IczStalagtiteRecipe) // 0xB5
@ModApi final class LaunchBaseFactory
    static AbstractObjectInstance alarm(ObjectSpawn, LbzAlarmRecipe)          // 0x22
```

## Open obligations (not deferred)

- **ICZ2 and LBZ2 content**: no Stalagtite/alarm records exist there; needs other
  finite owners (ICZ2 Penguinator `$AD` / Star Pointer `$AE` rows, miniboss
  `$BC`; LBZ2 Ribot/Orbinaut/Flybot rows, end boss `$CB`).
- **CNZ rhythm/platforms**: balloon (`$41`), bumper, rising platform (`$43`)
  stagger and timed shortcuts have no finite preset yet.
- **Boss pressure**: CNZ miniboss `$A6`/end boss `$A7`, ICZ miniboss `$BC`/end
  boss `$BD` (frost-puff cycle `SWING_WAIT_TIME` `$3F`), LBZ end boss `$CB`.
- **Character branches**: Knuckles late-act/exit (CNZ2 `$82/$83`, LBZ `$C5/$C8`)
  and Sonic snowboard / Tails skip / Knuckles teleporter are untouched, not
  separately authored or certified.
- **Evidence**: zero-ring checkpoint retries, cold routes, capture/restore and
  forward replay, width/donor/team matrix rows.
- **ICZ physical act 2 is two apparent acts.** Records below x `$800` (arena `$5F0-$7F0`, checkpoints `$380/$442`, rings 1/7) belong to the *carried ICZ1* miniboss approach; true ICZ2 starts after it (rings from `$930`). Retained ICZ2 rings are true-ICZ2 only (84, 164, 341). The ICZ miniboss `$BC` deletes itself for apparent act 1 of physical act 2 (`Apparent_zone_and_act == $501`); it is ICZ1's carried boss, so a miniboss recipe is **not** true-ICZ2 content and the true ICZ2 end boss (`$BD`) stays open.
- Ring identities were chosen by proximity to checkpoints/threats from the ROM
  tables; their route value is **unplaytested**.

## Validation log

| Command / state | Result |
| --- | --- |
| `mvn -Dmse=off -DskipTests compile` on frozen source | clean |
| `b40lmmi54`: focused `TestSecondStrikeEastNativeRecipes` (`maven_queue.py --lean`), first source (index-parity `tellHold(int)`), terminal | 10 run, 8 pass, 1 failure (`escortRetiresWhenPlayerControlIsScripted` CHASE vs ATTACK_WINDUP: fixture inside the native attack trigger), 1 error (stub `objectQuery()` null); 0 skipped; both test-fixture faults, fixed in source |
| `be1cpa3w8` (queue pid 3754010): same selection on corrected source (explicit CADENCE/STAGGERED_CADENCE, zone facades) | queued for a slot; **frozen, sole request** |
| next, affected classes only: `TestSecondStrikeEastCampaign` (+ `-Ds3k.rom.path` original) and existing Clamer / Stalagtite / LbzAlarm / Flybot767 / Clamer graph rewind / rewind-schema classes | not yet run |
