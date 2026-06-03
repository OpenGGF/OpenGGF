package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.level.scroll.M68KMath;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;

import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SwScrlMhzTest {

    @Test
    void providerUsesMhzDeformForMushroomHill() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());
        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneConstants.ZONE_MHZ);
        int[] hScroll = new int[M68KMath.VISIBLE_LINES];

        handler.update(hScroll, 0x0400, 0x0400, 0, 0);

        assertEquals((short) 0x0116, handler.getVscrollFactorBG(),
                "MHZ_Deform uses Camera_Y_pos_copy*5/32+$76, not the default 1/4 BG Y");
        assertEquals(packScrollWords(negWord(0x0400), negWord(0x0180)), hScroll[0],
                "MHZ_Deform publishes Camera_X_pos_BG_copy as 3/8 of Events_fg_1");
        assertEquals(hScroll[0], hScroll[M68KMath.VISIBLE_LINES - 1],
                "MHZ_Deform finishes with PlainDeformation, so all visible lines use the same scroll word");
    }

    @Test
    void endBossArenaRepeatUsesRomLoopAdjustedEventsFg1ForBgDeform() {
        SwScrlMhz handler = new SwScrlMhz();
        int[] hScroll = new int[M68KMath.VISIBLE_LINES];

        handler.update(hScroll, 0x427C, 0x0280, 1, 1);
        handler.update(hScroll, 0x4080, 0x0280, 2, 1);

        assertEquals(0x18F0, handler.getBgCameraX() & 0xFFFF,
                "MHZ_Deform should use Adjust_BGDuringLoop Events_fg_1=$4280 after the $200 end-boss arena wrap");
        assertEquals(packScrollWords(negWord(0x4080), negWord(0x18F0)), hScroll[0],
                "PlainDeformation should use the loop-adjusted BG camera X while foreground scroll uses wrapped camera X");
    }
}
