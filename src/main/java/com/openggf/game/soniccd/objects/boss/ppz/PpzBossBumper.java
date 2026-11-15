package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;

/**
 * A bumper, object $32 ({@code BossBumperObject}, objects/r1/boss.asm:1697-1778): width $C, height $10, tile
 * $2359, collision $FF while its arm is on. It sits at -$C from its forearm (+8 or +$10 for the forearm's frames
 * 1 and 2); a bounce off it ({@link #PLANTED}, set by Eggman) pulls it back 4 pixels for one frame. Routine 4
 * drops it, harmless.
 */
final class PpzBossBumper extends PpzBossPiece implements TouchResponseListener {
    PpzBossForearm forearm;

    PpzBossBumper(int x, int y, PpzBossForearm forearm) {
        super(0x32, x, y, "PpzBossBumper");
        this.forearm = forearm;
    }

    private PpzBossBumper(ObjectSpawn spawn) {
        super(spawn, "PpzBossBumper");
    }

    @Override String sheet() { return PpzBossArt.BUMPER; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // BossBumperObject_0_Routine0 (objects/r1/boss.asm:1713-1720), falling into routine 2.
                width = 0xC;
                yRadius = 0x10;
                tileWord = PpzBossArt.PART_TILE;
                routine = 2;
                follow();
            }
            case 2 -> follow();
            case 4 -> {
                // BossBumperObject_0_Routine4 (objects/r1/boss.asm:1749-1778); the link is no longer read.
                forearm = null;
                if (!testAndSet(STARTED)) {
                    phase = 0;
                    setCollision(0, 0);
                    stepX = 0;
                    stepY = -0x18000;
                } else {
                    stepX += -0x500;
                    stepY += 0x1800;
                }
                fallAndCheck();
            }
            default -> { }
        }
    }

    /** BossBumperObject_0_Routine2 (objects/r1/boss.asm:1722-1745). */
    private void follow() {
        int px = forearm.getX() - 0xC;
        int frame = forearm.mapFrame();
        if (frame == 1) px += 8;
        else if (frame != 0) px += 0x10;
        if (clear(PLANTED)) px -= 4;
        x = px & 0xFFFF;
        y = forearm.getY();
    }

    /** {@code Player_TouchSpecial} size $3F: see {@link PpzBossEggman#onTouchResponse}. */
    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        PpzBossEggman.touched(this, player);
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossBumper(spawn);
    }
}
