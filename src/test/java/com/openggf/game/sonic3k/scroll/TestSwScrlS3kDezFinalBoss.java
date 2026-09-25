package com.openggf.game.sonic3k.scroll;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestSwScrlS3kDezFinalBoss {
    @Test void providerSeparatesFinalBossFromSanctuary() throws Exception {
        var provider = new Sonic3kScrollHandlerProvider();
        provider.load(new com.openggf.data.Rom());
        assertInstanceOf(SwScrlS3kDezFinalBoss.class, provider.getHandler(23, 0));
        assertInstanceOf(SwScrlHpz.class, provider.getHandler(23, 1));
    }
    @Test void displayedWindowChangesOnlyAfterRetainedScrollIsReleasedAndRewinds() {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        state.retainedPlaneX(0x580); state.windowBase(0x2C0);
        state.publishDisplayedWindow();
        assertEquals(0x6C0, state.displayedWindowBase());
        byte[] saved = state.captureBytes();
        state.retainedPlaneX(0); state.publishDisplayedWindow();
        assertEquals(0x2C0, state.displayedWindowBase());
        state.restoreBytes(saved);
        assertEquals(0x6C0, state.displayedWindowBase());
    }
    @Test void initialPlaneWindowAndFloorSplitMatchTheNativeWords() {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        state.publishPlanePosition(0x80, 0);
        assertEquals(0x380, state.planeX());
        assertEquals(0xA8, state.planeY());
        var scroll = new SwScrlS3kDezFinalBoss(); var buffer = new int[224];
        scroll.render(buffer, 0x80, state);
        for (int line = 0; line < 224; line++) {
            assertEquals((short) -0x380, (short) (buffer[line] >>> 16));
            assertEquals(line < 192 ? 0 : (short) -0x80, (short) buffer[line]);
        }
        assertEquals(0xA8, scroll.getVscrollFactorFG());
        assertEquals(0x20, scroll.getVscrollFactorBG());
    }
    @Test void shakeMovesTheBandBoundaryAndBothVerticalWordsWithoutScaling() {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        state.publishPlanePosition(0x223, -3);
        var scroll = new SwScrlS3kDezFinalBoss(); var buffer = new int[224];
        scroll.render(buffer, 0x223, state);
        assertEquals(0, (short) buffer[194]);
        assertEquals((short) -0x223, (short) buffer[195]);
        assertEquals(0xA5, scroll.getVscrollFactorFG());
        assertEquals(0x1D, scroll.getVscrollFactorBG());
        assertEquals(0x22, scroll.getBgCameraX());
        assertEquals(512, scroll.getBgPeriodWidth());
    }
    @Test void redrawRetainsOnlyHorizontalBossScrollAndZeroBaseClearsIt() {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        state.windowBase(0x2C0); state.bossPosition(0x500, 0x110);
        state.publishPlanePosition(0x520, 2); state.retainedPlaneX(0x380);
        var scroll = new SwScrlS3kDezFinalBoss(); var buffer = new int[224];
        scroll.render(buffer, 0x520, state);
        assertEquals((short) -0x380, (short) (buffer[0] >>> 16));
        assertEquals(0x92, scroll.getVscrollFactorFG());
        state.retainedPlaneX(0); state.windowBase(0); state.publishPlanePosition(0x7FF, 0);
        scroll.render(buffer, 0x7FF, state);
        assertEquals(0, (short) (buffer[0] >>> 16));
        assertEquals((short) -0x7FF, (short) buffer[223]);
    }
    @Test void stateRestoresEverySharedEventWordAndPreservesWordArithmetic() {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.TAILS_ALONE);
        state.windowBase(0xFFFF); state.bossPosition(0xFFF0, 0xFFFE);
        state.redrawRequest(0xFF20); state.breakRequest(0xFFE0); state.mouthPhase(6);
        state.retainedPlaneX(0x8000); state.laserOffset(0x20); state.uploadedLaserOffset(0x18);
        state.breakFrontier(0x900); state.foregroundRoutine(4); state.backgroundRoutine(0x18);
        state.bossSignals(0x83); state.eventsFg5(0xFFFF); state.publishPlanePosition(0x20, -1);
        byte[] saved = state.captureBytes();
        var restored = new DezFinalBossZoneRuntimeState(PlayerCharacter.TAILS_ALONE); restored.restoreBytes(saved);
        assertArrayEquals(saved, restored.captureBytes());
        assertEquals(0x2F, restored.planeX()); assertEquals(0x1A1, restored.planeY());
        assertEquals(0x17, restored.zoneIndex()); assertEquals(0, restored.actIndex());
    }
}
