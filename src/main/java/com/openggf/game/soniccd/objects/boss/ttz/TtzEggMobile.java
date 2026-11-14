package com.openggf.game.soniccd.objects.boss.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Egg Mobile, object $4D ({@code EggMobileObject}, objects/r4/boss.asm:519-547): the boss's craft, allocated by
 * Eggman's init. It copies Eggman's X and Y words every frame and draws; Eggman flips it ({@code oSprFlags} and
 * {@code oFlags} bit 0) as he turns, and deletes it when he leaves.
 */
public final class TtzEggMobile extends TtzBossPart {
    /** The body mirrors by {@code oFlags} bit 0 ({@link TtzBossPart#facingByFlags}). */
    @Override
    public com.openggf.level.objects.ObjectVisualFacing.Basis nativeFacing() {
        return facingByFlags();
    }

    /** Object $4D: EggMobileObject is index entry $4C (r4/object_index_3.asm:3-78). */
    public static final int ID = 0x4D;

    /** var_2e: Eggman. */
    TtzBossEggman eggman;

    TtzEggMobile(int x, int y, TtzBossEggman eggman) {
        super(ID, x, y, "TtzEggMobile");
        this.eggman = eggman;
    }

    private TtzEggMobile(ObjectSpawn spawn) {
        super(spawn, "TtzEggMobile");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                             // EggMobileObject_0_Routine0 (:534-541)
            routine = 2;
            priority = 4;
            width = 0x20;
            yRadius = 0x1C;
            tileWord = TtzBossArt.BOSS_TILE;
        }
        if (eggman != null) {                                           // EggMobileObject_0_Routine2 (:543-547)
            setXWord(eggman.getX());
            setYWord(eggman.getY());
        }
    }

    @Override String sheet() { return TtzBossArt.EGG_MOBILE; }

    @Override
    protected TtzEggMobile shell(ObjectSpawn spawn) {
        return new TtzEggMobile(spawn);
    }
}
