package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A firefly's shot, object $39 with subtype $FF ({@code HotaruObject_1}, objects/r8/hotaru.asm:304-376): a hurting
 * spark ($98) that falls 8 pixels a frame to the floor, then plays animation 9 twice and is deleted.
 */
final class MmzBossFireflyShot extends MmzBossPiece {
    /** HotaruObject_1_Routine0: move.b #$98,obj.collide_type(a0) (scddisasm/src/objects/r8/hotaru.asm:320-327). */
    static final int COLLISION = 0x98;

    private int countdown;
    private int rounds;

    MmzBossFireflyShot(ObjectSpawn spawn) {
        super(spawn, "MmzBossFireflyShot");
    }

    @Override String sheet() { return MmzBossArt.HOTARU; }

    @Override protected boolean drawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // HotaruObject_1_Routine0 (scddisasm/src/objects/r8/hotaru.asm:320-327).
                priority = 3;
                tileWord = MmzBossArt.HOTARU_TILE;
                routine = 2;
                yRadius = 8;
                width = 4;
                setCollision(COLLISION, 0);
            }
            case 2 -> {
                // HotaruObject_1_Routine2 (scddisasm/src/objects/r8/hotaru.asm:331-342): CheckBlockDown, below-floor ends the fall.
                anim = 8;
                addY(8);
                animate(table("hotaruAnimations"));
                int distance = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(x, y, yRadius).distance();
                if (distance < 0) {
                    routine = 4;
                    countdown = 0;
                    rounds = 0;
                }
            }
            case 4 -> burst();
            default -> { }
        }
    }

    /** HotaruObject_1_Routine4 (scddisasm/src/objects/r8/hotaru.asm:347-376). */
    private void burst() {
        if (countdown == 0) {
            countdown = 2;
            anim = 9;
            if (rounds != 0) return;                              // move.b #9,obj.anim_id(a0); rts
        }
        animate(table("hotaruAnimations"));
        countdown = (countdown - 1) & 0xFF;
        if (countdown != 0) return;
        rounds = (rounds + 1) & 0xFF;
        if (rounds >= 2) deleteObject();
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossFireflyShot(spawn);
    }
}
