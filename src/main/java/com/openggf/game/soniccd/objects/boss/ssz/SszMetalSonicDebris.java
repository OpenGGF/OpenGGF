package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * A piece of Metal Sonic, object $31 ({@code MetalSonicDebrisObject}, objects/r7/boss.asm:1738-1772), from his
 * break at the door (sub_20CF14): it falls under its gravity and is deleted at Y $270. Its routine byte stays 0
 * (the init falls through every frame), and it draws every frame until deleted.
 */
final class SszMetalSonicDebris extends SszBossPiece {
    static final int ID = 0x31;

    SszMetalSonicDebris(SszMetalSonic metal, int frame, int xSpeed, int gravity) {
        super(ID, metal.getX(), metal.getY(), "SszMetalSonicDebris");
        this.mapFrame = frame;
        this.xSpeed = xSpeed;
        this.ySpeed = -0x200;
        this.gravity = gravity;
    }

    private SszMetalSonicDebris(ObjectSpawn spawn) {
        super(spawn, "SszMetalSonicDebris");
    }

    @Override String sheet() { return SszBossArt.METAL_DEBRIS; }

    @Override protected boolean drawn() { return !skipDraw; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        priority = 1;                                           // MetalSonicDebrisObject_0_Routine0 (:1753-1759)
        width = 8;
        yRadius = 8;
        tileWord = 0x37C;
        accelerateAndMove();                                    // MetalSonicDebrisObject_0_Routine2 (:1761-1772)
        if (yWord() >= 0x270) {
            skipDraw = true;
            deleteObject();
        }
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszMetalSonicDebris(spawn);
    }
}
