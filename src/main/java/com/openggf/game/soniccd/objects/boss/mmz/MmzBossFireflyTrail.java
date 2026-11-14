package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A firefly's trail, object $39 with subtype $FD (loc_20F644, objects/r8/hotaru.asm:381-400): animation 4 for $18
 * frames at priority 4, then deleted (not drawn on that frame).
 */
final class MmzBossFireflyTrail extends MmzBossPiece {
    private int countdown;
    private boolean gone;

    MmzBossFireflyTrail(ObjectSpawn spawn) {
        super(spawn, "MmzBossFireflyTrail");
    }

    @Override String sheet() { return MmzBossArt.HOTARU; }

    @Override protected boolean drawn() { return !gone; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            priority = 4;
            tileWord = MmzBossArt.HOTARU_TILE;
            countdown = 0x18;
            anim = 4;
            routine = 2;
        }
        animate(table("hotaruAnimations"));
        countdown = (countdown - 1) & 0xFF;
        if (countdown == 0) {
            gone = true;
            deleteObject();
        }
    }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossFireflyTrail(spawn);
    }
}
