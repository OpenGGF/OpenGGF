package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A heart over Amy, object $34 ({@code HeartObject}, objects/r7/amy_rose.asm:529-589): it rises at $60 and sways
 * (X speed the sine of 3 times its count, / 4), grows at count $14, stops at $6E and is deleted at $78.
 */
final class SszAmyHeart extends SszBossPiece {
    static final int ID = 0x34;

    /** var_3a: frames alive; var_3c: stopped. */
    private int count;
    private boolean stopped;

    SszAmyHeart(int x, int y) {
        super(ID, x, y, "SszAmyHeart");
    }

    private SszAmyHeart(ObjectSpawn spawn) {
        super(spawn, "SszAmyHeart");
    }

    @Override String sheet() { return SszBossArt.HEART; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        if (routine == 0) {                                     // HeartObject_0_Routine0 (:545-552)
            routine = 2;
            tileWord = 0x3CB;
            mapFrame = 8;
            ySpeed = -0x60;
            priority = 3;
        }
        if (!stopped) {                                         // HeartObject_0_Routine2 (:554-589)
            int angle = (count + count + count) & 0xFF;          // add.b d0,d0; add.b obj.var_3a(a0),d0
            xSpeed = (short) (com.openggf.physics.TrigLookupTable.sinHex(angle) >> 2);
        }
        addXLong((long) (short) xSpeed << 8);
        addYLong((long) (short) ySpeed << 8);
        count = (count + 1) & 0xFF;
        if (count == 0x14) mapFrame++;
        if (count == 0x6E) {
            mapFrame++;
            ySpeed = 0;
            xSpeed = 0;
            stopped = true;
        }
        if (count == 0x78) {
            skipDraw = true;
            deleteObject();
        }
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return despawnOutOfRange(cameraX); }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszAmyHeart(spawn);
    }
}
