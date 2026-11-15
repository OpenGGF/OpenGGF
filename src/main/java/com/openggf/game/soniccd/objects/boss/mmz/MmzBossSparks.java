package com.openggf.game.soniccd.objects.boss.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.boss.CdBossPart;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sparks, object $42 ({@code SparksObject}, objects/r8/boss.asm:3005-3085): two per hit (and every 60 frames of the
 * escape) at Eggman, side 0 and side 1 ({@code var_34}, sub_20CF62). Each frame it sits at Eggman plus the side's
 * offset row ({@code off_20CEE4}, three rows stepped every 4 frames), cycles frames 0-2 (frame 2 is not drawn) and
 * deletes itself after $96 frames.
 */
final class MmzBossSparks extends MmzBossPiece {
    static final int ID = 0x42;

    MmzBossEggman eggman;
    private int side;
    private boolean hidden;

    MmzBossSparks(int x, int y, MmzBossEggman eggman, int side) {
        super(ID, x, y, "MmzBossSparks");
        this.eggman = eggman;
        this.side = side;
    }

    private MmzBossSparks(ObjectSpawn spawn) {
        super(spawn, "MmzBossSparks");
    }

    @Override String sheet() { return MmzBossArt.SPARKS; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            // SparksObject_0_Routine0 (:3020-3027), falling into routine 2.
            routine = 2;
            priority = 4;
            width = 8;
            yRadius = 8;
            tileWord = MmzBossArt.EGGMAN_TILE;
        }
        hidden = follow();
    }

    /** SparksObject_0_Routine2 (:3029-3069); true when it ends without DrawObject. */
    private boolean follow() {
        if (eggman != null) {
            x = eggman.ox();
            y = eggman.oy();
        }
        CdStageImage image = image();
        int base = table("sparksOffsets");
        if (image != null && base >= 0) {
            int row = base + (short) image.u16(base + side * 2) + step;
            addX((short) image.u16(row));
            addY((short) image.u16(row + 2));
        }
        setTimerHigh(timerHigh() + 1);
        if (timerHigh() == 0x96) {
            deleteObject();                                       // loc_20CB9A: addq.l #4,sp; jmp DeleteObject
            return true;
        }
        setTimerLow(timerLow() + 1);
        if (timerLow() == 4) {
            step += 4;
            if ((byte) step > 8) step = 0;
            setTimerLow(0);
            mapFrame = (mapFrame + 1) & 0xFF;
            if ((byte) mapFrame > 2) mapFrame = 0;
        }
        return mapFrame == 2;
    }

    @Override protected boolean drawn() { return !hidden && routine != 0; }

    @Override
    protected CdBossPart shell(ObjectSpawn spawn) {
        return new MmzBossSparks(spawn);
    }
}
