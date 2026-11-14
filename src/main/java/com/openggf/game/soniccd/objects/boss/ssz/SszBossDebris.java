package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Debris the laser knocks off the track, object $37 ({@code DebrisObject}, objects/r7/boss.asm:1624-1664), spat by
 * the laser's last link every 10 frames (sub_20D0A8) 2 forward and $10 below it with Eggman's X speed. Its init
 * ({@code bset #0,obj.routine}: routine 1) adds the remainder of a random word / $1C0 to the X speed and picks Y speed
 * -$180, gravity $10 + (random & $F) and frame random & 3; then it falls until $E8 below the camera's top.
 */
final class SszBossDebris extends SszBossPiece {
    static final int ID = 0x37;

    SszBossDebris(SszBossLaser link, SszBossEggman eggman) {
        super(ID, link.xWord() + 2, link.yWord() + 0x10, "SszBossDebris");
        this.xSpeed = eggman == null ? 0 : eggman.xSpeed;
    }

    private SszBossDebris(ObjectSpawn spawn) {
        super(spawn, "SszBossDebris");
    }

    @Override String sheet() { return SszBossArt.DEBRIS; }

    @Override protected boolean drawn() { return !skipDraw; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if ((routine & 1) == 0) {                               // bset #0,obj.routine(a0); bne.w loc_20CD30
            routine |= 1;
            priority = 5;
            width = 4;
            yRadius = 4;
            tileWord = 0x451C;
            int d0 = (short) services().rng().nextWord();       // Random; andi.l #$FFFF,d0; ext.l d0
            xSpeed = (short) (xSpeed + d0 % 0x1C0);             // divs.w #$1C0,d1; swap d1
            ySpeed = -0x180;
            int low = d0 & 0x7FFF;
            gravity = 0x10 + (low & 0xF);
            mapFrame = low & 3;
            skipDraw = true;                                    // rts: no DrawObject on the init frame
            return;
        }
        skipDraw = false;
        accelerateAndMove();                                    // loc_20CD30 (:1652-1664)
        var camera = services().camera();
        int bottom = (camera == null ? 0 : camera.getY()) + 0xE8;
        if ((short) (yWord() - bottom) > 0) {
            skipDraw = true;
            deleteObject();
        }
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszBossDebris(spawn);
    }
}
