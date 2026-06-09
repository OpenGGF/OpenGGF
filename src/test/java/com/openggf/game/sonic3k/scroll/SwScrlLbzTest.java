package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.unpackBG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwScrlLbzTest {

    @Test
    void providerRoutesLbzToDedicatedHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneIds.ZONE_LBZ);

        assertNotNull(handler);
        assertTrue(handler instanceof SwScrlLbz, "LBZ should not use the generic S3K fallback scroll handler");
    }

    @Test
    void act1UsesRomBackgroundScrollBands() {
        SwScrlLbz handler = new SwScrlLbz();
        int[] buffer = new int[VISIBLE_LINES];

        handler.update(buffer, 0x2000, 0x0400, 0, 0);

        assertEquals((short) 0x0040, handler.getVscrollFactorBG(),
                "LBZ1_Deform sets Camera_Y_pos_BG_copy = Camera_Y_pos_copy / 16");
        assertEquals(0x020A, handler.getBgCameraX(),
                "LBZ1_Deform exposes Camera_X_pos_BG_copy = Camera_X_pos_copy / 16 + $0A");
        assertEquals((short) -0x020A, unpackBG(buffer[0]),
                "visible output starts from HScroll_table+$008 = Camera_X_pos_copy / 16 + $0A");
        assertEquals((short) -0x0404, unpackBG(buffer[144]),
                "line 144 reaches the next generated LBZ1 scroll value after the BG-Y skip");
    }

    @Test
    void act1ScreenShakeOffsetsForegroundAndBackgroundVScroll() {
        SwScrlLbz handler = new SwScrlLbz();
        int[] buffer = new int[VISIBLE_LINES];

        handler.setScreenShakeOffset(3);
        handler.update(buffer, 0x2000, 0x0400, 0, 0);

        assertEquals((short) 0x0403, handler.getVscrollFactorFG(),
                "ShakeScreen_Setup adds Screen_shake_offset to Camera_Y_pos_copy for the foreground plane.");
        assertEquals((short) 0x0043, handler.getVscrollFactorBG(),
                "LBZ1 applies Screen_shake_offset after computing the normal background parallax factor.");
        assertEquals(3, handler.getShakeOffsetY(),
                "The same shake offset must propagate to sprite/camera rendering.");
    }

    @Test
    void act2UsesRomWaterlineBackgroundCameraAndParallaxBands() {
        SwScrlLbz handler = new SwScrlLbz();
        int[] buffer = new int[VISIBLE_LINES];

        handler.update(buffer, 0x2000, 0x05F0, 0, 1);

        assertEquals((short) 0x02C0, handler.getVscrollFactorBG(),
                "LBZ2_Deform bases Camera_Y_pos_BG_copy at $2C0 when the waterline delta is neutral");
        assertEquals(0x1000, handler.getBgCameraX(),
                "LBZ2_Deform exposes Camera_X_pos_BG_copy = Camera_X_pos_copy / 2");
        assertNotEquals(unpackBG(buffer[0]), unpackBG(buffer[VISIBLE_LINES - 1]),
                "LBZ2 should produce real multi-band parallax, not a flat fallback background");
    }
}
