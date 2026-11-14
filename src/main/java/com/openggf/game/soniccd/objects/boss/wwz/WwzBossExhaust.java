package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The machine's exhaust, object $37 ({@code BossExhaustObject}, objects/r6/boss.asm:823-879), allocated by Eggman:
 * $40 pixels below Eggman, a hazard ($BE, written every frame before its routine) whose animation is Eggman's
 * {@code var_35} (0 idle, 1 thrust); a negative value (the defeat) deletes it before it animates or draws.
 */
final class WwzBossExhaust extends WwzBossPiece {
    static final int ID = 0x37;
    /** move.b #$BE,obj.collide_type(a0); move.b #2,obj.collide_status(a0) (objects/r6/boss.asm:824-825). */
    static final int COL_TYPE = 0xBE, COL_STATUS = 2;
    // var_2e: Eggman.
    WwzBossEggman eggman;

    WwzBossExhaust(WwzBossEggman eggman) {
        super(ID, 0, 0, "WwzBossExhaust");
        this.eggman = eggman;
    }

    private WwzBossExhaust(ObjectSpawn spawn) {
        super(spawn, "WwzBossExhaust");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        setCollision(COL_TYPE, COL_STATUS);
        if (routine == 0) {                                  // BossExhaustInit (objects/r6/boss.asm:844-855)
            routine = 2;
            priority = 5;
            width = 0x10;
            yRadius = 0x10;
            tileWord = WwzBossArt.PART_TILE;
        } else if (eggman != null) {                         // BossExhaustFollowParent (:859-867)
            setXWord(eggman.xWord());
            setYWord(eggman.yWord() + 0x40);
            anim = eggman.exhaustAnim & 0xFF;
            if ((anim & 0x80) != 0) {                        // BossExhaustDelete: addq.l #4,sp; jmp DeleteObject
                deleteObject();
                return;
            }
        }
        var level = cdLevel();
        animate(WwzBossCatalogue.offset(level == null ? null : level.payload(), "exhaustAnimations"));
    }

    @Override String sheet() { return WwzBossArt.EXHAUST; }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzBossExhaust(spawn);
    }
}
