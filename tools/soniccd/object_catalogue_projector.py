"""Metal Sonic projector anchors for tools/soniccd/object_catalogue.py (owner: projector).

Owned by the WARP lane of the 2026-11-13 Sonic CD re-host (scddisasm pin 1ecc5f3af); writes
src/main/resources/soniccd/object-catalogue-projector.tsv, read by objects/projector/ProjectorObjectCatalogue.
Six zones assemble the projector (Palmtree Panic and Wacky Workbench act 1 Present: Level/<Zone>/Objects/Projector/Main.asm
and objects/r4..r7/projector.asm; Collision Chaos links the Palmtree Panic body with CC_LEGACY_PROJECTOR_ABI); every stage
that links it carries one live copy of the init routine and of the hologram routine, so each table is located through the
instruction that loads it. A stage that links no projector reads '-' (optional anchors).
"""

from object_catalogue import operands
from stage_inventory import require

ANCHORS = [
    # ObjProjector_Init: move.l #MapSpr_Projector,oMap(a0); move.l #ObjProjector_ExplosionLocs,oProjectorSchedule(a0);
    # jsr ProjectorSpawn; bne.w ObjProjector_Destroy (Palmtree Panic/Objects/Projector/Main.asm:106-109). Wacky Workbench
    # sets the tile between the schedule and the spawn call (objects/r6/projector.asm).
    ('projectorMappings',
     ('217C AAAAAAAA 0004 217C ???????? 002C 4EB9 ???????? 6600 ????',
      '117C 00FB 0020 217C AAAAAAAA 0004 217C ???????? 002C 317C ???? 0002'),
     'optional',
     'ObjProjector_Init: move.l the mappings,oMap(a0) (Palmtree Panic/Objects/Projector/Main.asm:106; objects/r4/projector.asm:47)'),
    ('projectorSchedule',
     ('217C ???????? 0004 217C AAAAAAAA 002C 4EB9 ???????? 6600 ????',
      '117C 00FB 0020 217C ???????? 0004 217C AAAAAAAA 002C 317C ???? 0002'),
     'optional',
     'ObjProjector_Init: move.l the explosion records,oProjectorSchedule(a0) (Palmtree Panic/Objects/Projector/Main.asm:107; objects/r4/projector.asm:48)'),
    # ObjMetalSonicHologram: moveq #8,d0; moveq #4,d1; moveq #0,d2; tst.b oSubtype; bmi; moveq #$14,d0; moveq #$18,d1;
    # moveq #1,d2; move.b d0,oXRadius; ...; lea the hologram animations,a1 (Palmtree Panic/Objects/Projector/Main.asm:263-279).
    ('projectorAnimations',
     '7008 7204 7400 4A28 0028 6B06 7014 7218 7401 1140 0017 1140 0019 1141 0016 1142 001C 43FA PPPP',
     'optional',
     'ObjMetalSonicHologram .GotSize / .Animate: lea the hologram animations,a1 (Palmtree Panic/Objects/Projector/Main.asm:263-279)'),
]

# Palmtree Panic/Objects/Animal/Main.asm:38, 102, 140, 188 and 210. Only the Past
# copies load Art_HologramAnimals through the projector's init cue.
PPZ_PAST_ANCHORS = {
    'animalFlyingMappings': '217C 08080408 0016 217C AAAAAAAA 0004 3168 0008 002A 3168 000C 002C',
    'animalFlyingAnimations': '5828 002E 1028 002E 0200 007F 6700 ???? 43FA PPPP 4EB9 ???????? 4EF9',
    'animalGroundMappings': '217C 08080408 0016 217C AAAAAAAA 0004 3168 0008 002A 6100 ???? 4A28 0028',
    'animalGroundAnimations': '3268 003E 0C29 002E 0000 6600 ???? 4A29 003F 6600 ???? 43FA PPPP 4EB9 ???????? 4EF9',
    'animalBaseTiles': '43FA PPPP 7000 1039 ???????? E540 D039 ???????? D040 3171 0000 0002 4E75',
}
EXTRA_COLUMNS = tuple(PPZ_PAST_ANCHORS)


def extra_columns(stage: str, data: bytes) -> dict:
    row = dict.fromkeys(EXTRA_COLUMNS)
    if stage in ('R11B', 'R12B'):
        for name, signature in PPZ_PAST_ANCHORS.items():
            hits = operands(data, signature)
            require(len(hits) == 1 and 0 <= hits[0] < len(data),
                    f'{stage} {name}: expected one operand in the stage, got {hits}')
            row[name] = hits[0]
    return row
