"""Quartz Quadrant boss anchors for tools/soniccd/object_catalogue.py (owner: bossqqz).

Owned by the Quartz Quadrant boss lane; writes src/main/resources/soniccd/object-catalogue-bossqqz.tsv,
read by objects/boss/qqz/QqzBossCatalogue. Only the act 3 stages (R53C, R53D) link the boss
(objects/r5/boss.asm, with the act 3 object index r5/object_index_3_act3.asm). Each init writes oTile then
oMap; the part's oWidth and oYRadius before them tell the parts apart. The data tables (belt palette,
booth drop steps, spark and explosion offsets, small-bomb launch and acceleration) are found through the
instruction that reads them, never transcribed.
"""

def STAGES_COVERED(stage: str) -> bool:
    return stage.startswith("R53")


def _mappings(width: str, height: str, tile: str) -> str:
    return f'117C {width} 0019 117C {height} 0016 317C {tile} 0002 217C AAAAAAAA 0004'


# lea Anims,a1; jsr AnimateObject; jmp DrawObject; then the routine table, whose first word is its size.
def _anims(table: str) -> str:
    return f'43F9 AAAAAAAA 4EB9 ???????? 4EF9 ???????? {table}'


ANCHORS = [
    ('eggmanMappings', _mappings('0010', '0010', '02BC'), 'optional',
     'EggmanObject_0_Routine0 (objects/r5/boss.asm:25-33): width $10, height $10, tile $2BC, EggmanSprites'),
    ('eggmanAnimations', '4A28 001C 670C 43F9 AAAAAAAA 4EB9', 'optional',
     'EggmanObject (objects/r5/boss.asm:3-14): tst.b obj.anim_id; beq.s; lea EggmanAnims,a1; jsr AnimateObject'),
    ('topMappings', _mappings('0020', '0018', '22BC'), 'optional',
     'BossTopObject_0_Routine0 (objects/r5/boss.asm:114-126): width $20, height $18, tile $22BC, BossTopSprites'),
    ('boothMappings', _mappings('0020', '0070', '22BC'), 'optional',
     'BossBoothObject_0_Routine0 (objects/r5/boss.asm:271-285): width $20, height $70, tile $22BC, BossBoothSprites'),
    ('boothBackMappings', _mappings('0010', '0010', '22BC'), 'optional',
     'BossBoothBackObject_0_Routine0 (objects/r5/boss.asm:531-538): width $10, height $10, tile $22BC, BossBackSprites'),
    ('pipesMappings', _mappings('0008', '0030', '22BC'), 'optional',
     'BossPipesObject_0_Routine0 (objects/r5/boss.asm:570-577): width 8, height $30, tile $22BC, BossPipesSprites'),
    ('dropperMappings', _mappings('0010', '000C', '02BC'), 'optional',
     'BombDropperObject_0_Routine0 (objects/r5/boss.asm:611-618): width $10, height $C, tile $2BC, BombDropSprites'),
    ('dropperAnimations', _anims('000E'), 'optional',
     'BombDropperObject (objects/r5/boss.asm:589-607): lea BombDropAnims,a1, then the seven-routine table off_20B8F8'),
    ('bombMappings', (_mappings('000C', '000C', '22BC'), _mappings('0008', '0008', '22BC')), 'optional',
     'LargeBombObject_0_Routine0 (objects/r5/boss.asm:777-788) and SmallBombObject_0_Routine0 (:877-887): BombSprites'),
    ('sparksMappings', _mappings('0008', '0004', '22BC'), 'optional',
     'BossSparksObject_0_Routine0 (objects/r5/boss.asm:1002-1015): width 8, height 4, tile $22BC, SparksSprites'),
    ('sparksAnimations', _anims('0006 ???? ???? 5428 0024 117C 0004 0001 117C 0002 0018'), 'optional',
     'BossSparksObject (objects/r5/boss.asm:984-1005): lea SparksAnims,a1; three routines; Routine0 layer 2'),
    ('spikeWallMappings', _mappings('0020', '0020', '42BC'), 'optional',
     'SpikeWallObject_0_Routine0 (objects/r5/boss.asm:1071-1080): width $20, height $20, tile $42BC, SpikeWallSprites'),
    ('explosionMappings', '117C 0003 0018 ' + _mappings('0010', '0010', '0680'), 'optional',
     'BossExplosionObject_0_Routine0 (objects/r5/boss.asm:1511-1518): layer 3, width $10, height $10, tile $680, ExplosionSprites'),
    ('explosionAnimations', _anims('0006 ???? ???? 5428 0024 117C 0004 0001 117C 0003 0018'), 'optional',
     'BossExplosionObject (objects/r5/boss.asm:1493-1514): lea BossExplodeAnims,a1; three routines; Routine0 layer 3'),
    ('beltPaletteBad', '47FB 00QQ 4A39 ???????? 6704 47FB 00??', 'optional',
     'loc_20C0B0 (objects/r5/boss.asm:1431-1437): lea word_20C0D4(pc,d0.w),a3 (word_20C0D4, :1448-1451)'),
    ('beltPaletteGood', '4A39 ???????? 6704 47FB 00QQ 287C', 'optional',
     'loc_20C0B0 (objects/r5/boss.asm:1435-1440): tst.b good_future; lea word_20C0E6(pc,d0.w),a3 (:1453-1456)'),
    ('boothDropTable', 'D040 303B 00QQ 3168 000C 0038', 'optional',
     'loc_20B75C (objects/r5/boss.asm:429-442): move.w word_20B78E(pc,d0.w),d0 (word_20B78E, :446-450)'),
    ('sparkYOffsets', 'D844 383B 40QQ 2648', 'optional',
     'sub_20C1C4 (objects/r5/boss.asm:1555-1562): move.w word_20C1B8(pc,d4.w),d4 (word_20C1B8, :1545-1551)'),
    ('sparkOffsets', '2648 323C 0007 45F9 AAAAAAAA', 'optional',
     'sub_20C1C4 (objects/r5/boss.asm:1560-1562): move.w #7,d1; lea word_20C218,a2 (word_20C218, :1584-1592)'),
    ('explosionOffsets', '6634 45F9 AAAAAAAA D4C2', 'optional',
     'sub_20C238 (objects/r5/boss.asm:1612-1615): bne.s; lea word_20C29E,a2; adda.w d2,a2 (word_20C29E, :1632-1636)'),
    ('smallBombStarts', '43F9 AAAAAAAA 7000 1028 0028 C0FC 0014 D2C0 3019 3219', 'optional',
     'SmallBombObject_0_Routine0 (objects/r5/boss.asm:888-894): lea word_20BCA8,a1; subtype * $14 (word_20BCA8, :957-961)'),
    ('smallBombAccel', '43F9 AAAAAAAA 7000 1028 0028 C0FC 0014 D2C0 2019 2211', 'optional',
     'SmallBombObject_0_Routine4 (objects/r5/boss.asm:926-932): lea dword_20BCB4,a1; subtype * $14 (dword_20BCB4, :963-980)'),
]
