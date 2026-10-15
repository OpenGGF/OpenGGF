package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.M68KMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gumball_Deform (Lockon S3/Screen Events.asm:1690-1697) pins Camera_X_pos_BG_copy at $FFE0
 * and PlainDeformation writes its negation to every BG H-scroll line. The 2026-10-15 native
 * still at gumball row 250 (camera X $60) has plane-B H-scroll $0020; the engine scrolled the
 * background with the camera ($FFA0), a 128-pixel error that reads as different block content
 * because plane B repeats every 256 pixels.
 */
class SwScrlGumballTest {

    @Test
    void backgroundHorizontalScrollIsPinnedAtTheBackgroundCameraCopy() {
        SwScrlGumball handler = new SwScrlGumball();
        int[] packed = new int[M68KMath.VISIBLE_LINES];
        for (int cameraX : new int[] {0x60, 0x00, 0x1A0}) {
            handler.update(packed, cameraX, 0x89, 0, 0);
            for (int line = 0; line < packed.length; line++) {
                assertEquals((short) 0x0020, (short) packed[line], "BG H-scroll line " + line + " camera X $"
                        + Integer.toHexString(cameraX));
                assertEquals((short) -cameraX, (short) (packed[line] >> 16), "FG H-scroll line " + line);
            }
        }
    }
}
