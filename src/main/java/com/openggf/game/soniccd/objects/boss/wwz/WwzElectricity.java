package com.openggf.game.soniccd.objects.boss.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Electricity, object $40 ({@code ElectricityObject}, objects/r6/boss.asm:1068-1101): a pair Eggman sends across the
 * player's floor from both walls before he electrifies it (BossSpawnSpikePair, :1834-1862; subtype 1 mirrored). It
 * waits $1E frames, then shows frames 0-4 for 3 frames each and is deleted. The unused {@code ElectricityAnims}
 * script is not read.
 */
final class WwzElectricity extends WwzBossPiece {
    static final int ID = 0x40;
    private int side;

    WwzElectricity(int px, int py, int side) {
        super(ID, px, py, "WwzElectricity");
        setXWord(px);
        setYWord(py);
        this.side = side;
    }

    private WwzElectricity(ObjectSpawn spawn) {
        super(spawn, "WwzElectricity");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {                                  // ElectricityInit (objects/r6/boss.asm:1092-1119)
            routine = 2;
            priority = 1;
            width = 0xD0;
            yRadius = 8;
            tileWord = WwzBossArt.ELECTRICITY_TILE;
        }
        timer = (timer + 1) & 0xFF;                          // ElectricityAnimate (:1101-1119)
        int d0 = (timer - 0x1E) & 0xFF;
        if (timer < 0x1E) return;                            // subi.b #$1E,d0; bcs.s ElectricityDraw
        int frame = d0 / 3;
        if (frame == 5) {
            deleteObject();
            return;
        }
        mapFrame = frame;
    }

    @Override String sheet() { return WwzBossArt.ELECTRICITY; }

    @Override
    public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) {
        if (isDestroyed() || !drawn() || artProvider() == null) return;
        drawFrame(WwzBossArt.key(sheet()), mapFrame, side != 0, false);   // bset #0,obj.sprite_flags(a0)
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new WwzElectricity(spawn);
    }
}
