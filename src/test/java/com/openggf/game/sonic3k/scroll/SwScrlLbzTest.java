package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.LbzZoneRuntimeState;
import com.openggf.game.session.SessionManager;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.unpackBG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwScrlLbzTest {

    @BeforeEach
    void clearRuntimeState() {
        SessionManager.clear();
        SessionManager.clear();
    }

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

    @Test
    void act2DeathEggDeformOnlyActivatesDuringLaunchMode() {
        TestEnvironment.activeGameplayMode();
        SwScrlLbz normalHandler = new SwScrlLbz();
        int[] normal = new int[VISIBLE_LINES];
        normalHandler.update(normal, 0x4380, 0x0668, 8, 1);

        SwScrlLbz repeatHandler = new SwScrlLbz();
        int[] repeat = new int[VISIBLE_LINES];
        repeatHandler.update(repeat, 0x4380, 0x0668, 8, 1);

        assertEquals(unpackBG(normal[0]), unpackBG(repeat[0]),
                "inactive LBZ2 output must remain stable across handler instances");
        assertEquals(unpackBG(normal[VISIBLE_LINES - 1]), unpackBG(repeat[VISIBLE_LINES - 1]),
                "normal LBZ2 deform should not drift when launch state is absent");

        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        state.setLaunchActive(true);
        state.setFgAccum(0x0002_0000);
        state.setBgAccum(0x0001_8000);
        GameServices.zoneRuntimeRegistry().install(state);

        SwScrlLbz launchHandler = new SwScrlLbz();
        int[] launch = new int[VISIBLE_LINES];
        launchHandler.update(launch, 0x4380, 0x0668, 8, 1);

        assertNotEquals(unpackBG(normal[0]), unpackBG(launch[0]),
                "launch-active LBZ2 should use the Death Egg deformation bands");
        assertEquals(repeatHandler.getVscrollFactorBG(), normalHandler.getVscrollFactorBG(),
                "normal LBZ2 vscroll baseline remains unchanged while inactive");
    }

    @Test
    void act2DeathEggDeformUsesPersistentWrapLatchAndDeathEggRowCopy() {
        TestEnvironment.activeGameplayMode();
        LbzZoneRuntimeState state = new LbzZoneRuntimeState(1, PlayerCharacter.SONIC_ALONE);
        state.setLaunchActive(true);
        state.setFgAccum(0x0300_0000);
        GameServices.zoneRuntimeRegistry().install(state);

        SwScrlLbz handler = new SwScrlLbz();
        int[] buffer = new int[VISIBLE_LINES];
        handler.update(buffer, 0x4380, 0x0668, 12, 1);

        int firstLatch = state.getDeathEggDeformWrapLatch();
        assertTrue(firstLatch >= 0x200,
                "Death Egg deform should persistently accumulate $100 wrap steps while adjusted BG Y is negative");
        assertEquals(handler.getLbz2HScrollWordForTest(8), handler.getLbz2HScrollWordForTest(0),
                "Death Egg deform copies HScroll_table+$010 over the first four longs");
        assertEquals(handler.getLbz2HScrollWordForTest(15), handler.getLbz2HScrollWordForTest(7),
                "Death Egg row copy should cover all eight copied words, not just the first row");

        handler.update(buffer, 0x4380, 0x0668, 13, 1);

        assertEquals(firstLatch, state.getDeathEggDeformWrapLatch(),
                "wrap latch remains stable after the first frame instead of recomputing as frame-local floorMod");
    }
}
