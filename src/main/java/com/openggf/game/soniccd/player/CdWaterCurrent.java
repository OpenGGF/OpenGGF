package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;

import java.util.ArrayList;
import java.util.List;

/**
 * One 14-byte {@code WaterCurrents} record of a Tidal Tempest stage (scddisasm
 * src/r4/water_currents_1a.asm:1-4): the player's centre must lie in
 * {@code [left, right) x [top, bottom)}; the current then writes {@code xSpeed} and
 * {@code ySpeed}. {@code mode} 1 acts only while {@code switch_flags[switchIndex]} bit 6 is set;
 * mode 2 reverses both speeds while bit 5 is set ({@code CheckWaterCurrents},
 * src/r4/main.asm:776-858). The bytes are read from the loaded stage file.
 */
public record CdWaterCurrent(int left, int top, int right, int bottom, int xSpeed, int ySpeed, int mode,
                             int switchIndex) {
    /** A record is 14 bytes; a negative first byte ends the list ({@code tst.b (a2); bpl}, main.asm:860-861). */
    public static final int RECORD_BYTES = 0x0E;
    /** No stage's list comes near this; it only bounds a corrupt read. */
    private static final int MAX_RECORDS = 256;

    /** The stage's records, or an empty list when its {@code CheckWaterCurrents} has no table. */
    public static List<CdWaterCurrent> forStage(CdStagePayload payload, CdStageImage image) {
        int at = CdPlayerStageTables.entry(payload).waterCurrents();
        if (at < 0 || image == null) return List.of();
        List<CdWaterCurrent> records = new ArrayList<>();
        // The scan tests the first record before any terminator (CheckWaterCurrentsScan, main.asm:792).
        do {
            records.add(new CdWaterCurrent(image.u16(at), image.u16(at + 2), image.u16(at + 4), image.u16(at + 6),
                    (short) image.u16(at + 8), (short) image.u16(at + 0x0A), image.u8(at + 0x0C),
                    image.u8(at + 0x0D)));
            at += RECORD_BYTES;
        } while ((image.u8(at) & 0x80) == 0 && records.size() < MAX_RECORDS);
        return List.copyOf(records);
    }

    /** {@code cmp.w (a2),d0; bcs} .. {@code cmp.w 6(a2),d2; bcc} (main.asm:793-804), unsigned. */
    public boolean contains(int centreX, int centreY) {
        return centreX >= left && centreX < right && centreY >= top && centreY < bottom;
    }
}
