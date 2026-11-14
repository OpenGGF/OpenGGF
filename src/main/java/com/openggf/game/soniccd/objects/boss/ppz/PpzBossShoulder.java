package com.openggf.game.soniccd.objects.boss.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * The shoulders, object $2F ({@code BossShoulderObject}, objects/r1/boss.asm:1340-1424): width $10, height $C,
 * tile $2359, at ($18, -$C) from the body. It holds the two arms ({@link #frontArm}, {@link #backArm}). Eggman's
 * hits ask it to drop one: bit 5 ({@link #CARRY}) drops the back arm on the second-to-last hit, bit 6
 * ({@link #SWAP}) the front arm on the last but one. Routine 4 flies off with the defeat.
 */
final class PpzBossShoulder extends PpzBossPiece {
    PpzBossBody body;
    PpzBossArm frontArm;
    PpzBossArm backArm;

    PpzBossShoulder(int x, int y, PpzBossBody body) {
        super(0x2F, x, y, "PpzBossShoulder");
        this.body = body;
    }

    private PpzBossShoulder(ObjectSpawn spawn) {
        super(spawn, "PpzBossShoulder");
    }

    @Override String sheet() { return PpzBossArt.SHOULDER; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // BossShoulderObject_0_Routine0 (objects/r1/boss.asm:1356-1363), falling into routine 2.
                width = 0x10;
                yRadius = 0xC;
                tileWord = PpzBossArt.PART_TILE;
                routine = 2;
                hold();
            }
            case 2 -> hold();
            case 4 -> {
                // BossShoulderObject_0_Routine4 (objects/r1/boss.asm:1398-1424); the links are no longer read.
                body = null;
                frontArm = null;
                backArm = null;
                if (!testAndSet(STARTED)) {
                    stepX = -0x10000;
                    stepY = -0x20000;
                } else {
                    stepX += -0x600;
                    stepY += 0x1800;
                }
                fallAndCheck();
            }
            default -> { }
        }
    }

    /** BossShoulderObject_0_Routine2 (objects/r1/boss.asm:1365-1394). */
    private void hold() {
        x = (body.getX() + 0x18) & 0xFFFF;
        y = (body.getY() - 0xC) & 0xFFFF;
        if (clear(SWAP)) {
            // loc2_20CA12: the front arm falls; the link is cleared.
            frontArm.setRoutine(0xA);
            frontArm = null;
            return;
        }
        if (clear(CARRY)) {
            // loc_20C9FC: the back arm falls with frame 0; the link is cleared.
            backArm.setRoutine(0xA);
            backArm.setMapFrame(0);
            backArm = null;
        }
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new PpzBossShoulder(spawn);
    }
}
