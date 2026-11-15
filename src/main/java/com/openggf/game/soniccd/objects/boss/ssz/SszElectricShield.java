package com.openggf.game.soniccd.objects.boss.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Metal Sonic's electric shield, object $30 ({@code ElectricShieldObject}, objects/r7/boss.asm:617-666): on him
 * while his animation is $A, alternating frames 0 and 1 every fifth frame, and not drawn on the frame it changes.
 */
final class SszElectricShield extends SszBossPiece {
    static final int ID = 0x30;

    /** var_2e: Metal Sonic. */
    SszMetalSonic metalLink;
    /** var_2a: frames toward the next frame change. */
    private int ticks;

    SszElectricShield(SszMetalSonic metal) {
        super(ID, 0, 0, "SszElectricShield");
        this.metalLink = metal;
    }

    private SszElectricShield(ObjectSpawn spawn) {
        super(spawn, "SszElectricShield");
    }

    @Override String sheet() { return SszBossArt.SHIELD; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        skipDraw = false;
        if (routine == 0) {                                     // ElectricShieldObject_0_Routine0 (:632-639)
            priority = 5;
            width = 0x18;
            yRadius = 0x18;
            tileWord = 0x37C;
            routine = 2;
        }
        // ElectricShieldObject_0_Routine2 (:641-666).
        if (metalLink == null || metalLink.animId() != 0xA) {
            metalLink = null;
            skipDraw = true;
            deleteObject();
            return;
        }
        setXWord(metalLink.xWord());
        setYWord(metalLink.yWord());
        ticks = (ticks + 1) & 0xFF;
        if (ticks != 5) return;
        ticks = 0;
        mapFrame = mapFrame + 1 == 2 ? 0 : mapFrame + 1;
        skipDraw = true;                                        // rts without DrawObject
    }

    @Override
    protected com.openggf.game.soniccd.objects.boss.CdBossPart shell(ObjectSpawn spawn) {
        return new SszElectricShield(spawn);
    }
}
