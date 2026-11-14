package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * An exhaust flame of Eggman's ship, object $2D ({@code BossExhaustObject}, objects/r7/boss.asm:1668-1734), two of
 * them (subtype and animation 0 and 1, sub_20CFBC). Shown only while Eggman's {@code var_2c} bit 3 is set, at his
 * position plus (-$33, $1A) or (-$30, -$E), mirrored by his facing, whose flags it copies; deleted with him.
 */
final class SszBossExhaust extends SszBossPiece {
    static final int ID = 0x2D;

    /** var_2e: Eggman. */
    SszBossEggman eggmanLink;
    private int offsetX;
    private int offsetY;
    private int kind;

    SszBossExhaust(int x, int y, SszBossEggman eggman, int kind) {
        super(ID, x, y, "SszBossExhaust");
        this.eggmanLink = eggman;
        this.kind = kind;
        this.anim = kind;                                       // move.b #n,obj.anim_id(a1)
    }

    private SszBossExhaust(ObjectSpawn spawn) {
        super(spawn, "SszBossExhaust");
    }

    @Override String sheet() { return SszBossArt.EXHAUST; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        if (routine == 0) {                                     // BossExhaustObject_0_Routine0 (:1682-1702)
            priority = 5;
            width = 0x14;
            yRadius = 8;
            tileWord = 0x2300;
            offsetX = kind == 0 ? -0x33 : -0x30;
            offsetY = kind == 0 ? 0x1A : -0xE;
            routine = 2;
        }
        // BossExhaustObject_0_Routine2 (:1704-1724).
        if (eggmanLink == null || eggmanLink.isDestroyed()) {
            eggmanLink = null;
            skipDraw = true;
            deleteObject();
            return;
        }
        if (!eggmanLink.has(SszBossEggman.EXHAUST)) {           // btst #3,$2C(a2); beq.w locret_20CE04
            skipDraw = true;
            return;
        }
        status = eggmanLink.status;                             // move.b $22(a2),obj.flags(a0)
        setXWord(eggmanLink.xWord());
        setYWord(eggmanLink.yWord());
        addX((status & 1) != 0 ? -offsetX : offsetX);
        addY(offsetY);
        animateObject(table("exhaustAnimations"));
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszBossExhaust(spawn);
    }
}
