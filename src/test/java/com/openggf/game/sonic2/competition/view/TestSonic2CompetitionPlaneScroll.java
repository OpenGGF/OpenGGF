package com.openggf.game.sonic2.competition.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId.*;

class TestSonic2CompetitionPlaneScroll {
    @Test void boundaryLatchesFollowBothDirectionsAndFrozenCallsRetainPositionsWithoutFlags() {
        var scroll = new Sonic2CompetitionPlaneScroll();
        scroll.initialize(0, 0, 112, 64);
        scroll.completeCameraPass(0, 0, 0, 0, 0, false); // LevelSizeLoad seeded P1 latches to $10/$10
        scroll.copyAtVInt(false);
        assertEquals(0, scroll.copy(0).flags());
        scroll.completeCameraPass(0, 0, 0, 15, 15, false); scroll.copyAtVInt(false);
        assertEquals(0, scroll.copy(0).flags());
        scroll.completeCameraPass(0, 15, 15, 16, 16, false); scroll.copyAtVInt(false);
        assertEquals(10, scroll.copy(0).flags());
        scroll.completeCameraPass(0, 16, 16, 15, 15, false); scroll.copyAtVInt(false);
        assertEquals(5, scroll.copy(0).flags());
        scroll.completeCameraPass(0, 15, 15, 160, 160, true); scroll.copyAtVInt(false);
        assertEquals(15, scroll.copy(0).x()); assertEquals(15, scroll.copy(0).y());
        assertEquals(0, scroll.copy(0).flags());
    }
    @Test void titleCopiesOnlyP1AndTeleportSwapsCopiedAndLiveWordsButNeverPlaneImages() {
        var scroll = new Sonic2CompetitionPlaneScroll(); scroll.initialize(0, 0, 32, 64);
        var p2 = scroll.copy(1);
        scroll.completeCameraPass(1, 32, 64, 48, 80, false);
        scroll.copyAtVInt(true); assertEquals(p2, scroll.copy(1));
        scroll.copyAtVInt(false); assertEquals(48, scroll.copy(1).x());
        var before = scroll.capture();
        var planes = new Sonic2CompetitionPlanes();
        planes.write(FOREGROUND_P1, 0, 0, 0x1234); planes.write(FOREGROUND_P2, 0, 0, 0xABCD);
        scroll.swapPlayers(); assertEquals(before.copied().get(1), scroll.copy(0));
        assertEquals(0x1234, planes.cell(FOREGROUND_P1, 0, 0));
        assertEquals(0xABCD, planes.cell(FOREGROUND_P2, 0, 0));
        scroll.restore(before); assertEquals(before, scroll.capture());
    }
    @Test void descriptorsIgnoreBit10AndExchangeCellHalvesForNativeVerticalFlip() {
        var planes = new Sonic2CompetitionPlanes();
        for (int value = 0; value < 65536; value++) {
            planes.write(FOREGROUND_P1, 63, 31, value);
            int word = planes.fieldPlane(FOREGROUND_P1).descriptorAt(-1, -1);
            assertEquals(value & 0xF800, word & 0xF800);
            assertEquals((value & 1023) * 2, word & 2046);
            assertEquals((value & 0x1000) != 0 ? 0 : 1, word & 1);
        }
        var saved = planes.capture();
        planes.write(FOREGROUND_P1, 63, 31, 0);
        planes.restore(saved);
        assertEquals(0xFFFF, planes.cell(FOREGROUND_P1, 63, 31));
        assertSame(planes.fieldPlane(BACKGROUND), planes.source(FOREGROUND_P1).backgroundPlane());
        assertSame(planes.fieldPlane(BACKGROUND), planes.source(FOREGROUND_P2).backgroundPlane());
    }
    @org.junit.jupiter.api.Test void explicitPostLoadClearSurvivesUntilCompletedDeformationAndRestores() {
        var scroll = new Sonic2CompetitionPlaneScroll();
        scroll.initialize(0, 560, 0, 565);
        assertTrue(scroll.postLoadScrollClear());
        var saved = scroll.capture();
        scroll.completeCameraPass(0, 0, 560, 0, 560, false);
        assertTrue(scroll.postLoadScrollClear(), "P1 alone is not a completed two-camera deformation");
        scroll.completeCameraPass(1, 0, 565, 0, 565, true);
        assertTrue(scroll.postLoadScrollClear(), "Scroll_lock retains the post-load display words");
        scroll.completeCameraPass(1, 0, 565, 0, 565, false);
        assertFalse(scroll.postLoadScrollClear());
        scroll.restore(saved);
        assertTrue(scroll.postLoadScrollClear());
    }
    @Test void coldInitializationSeedsOnlyP1LatchesAndLeavesCameraCopiesCleared() {
        var scroll = new Sonic2CompetitionPlaneScroll();
        scroll.initialize(17, 33, 49, 65);
        var state = scroll.capture();
        assertEquals(16, state.live().get(0).horizontalLatch());
        assertEquals(16, state.live().get(0).verticalLatch());
        assertEquals(0, state.live().get(1).horizontalLatch());
        assertEquals(0, state.live().get(1).verticalLatch());
        for (int slot = 0; slot < 2; slot++) {
            assertEquals(0, scroll.copy(slot).x()); assertEquals(0, scroll.copy(slot).y());
        }
        scroll.copyAtVInt(true);
        assertEquals(17, scroll.copy(0).x()); assertEquals(33, scroll.copy(0).y());
        assertEquals(0, scroll.copy(1).x()); assertEquals(0, scroll.copy(1).y());
        scroll.copyAtVInt(false);
        assertEquals(49, scroll.copy(1).x()); assertEquals(65, scroll.copy(1).y());
    }
    @Test void explicitRetainedLevelInputAndMenuClearHaveDifferentNativeEffects() {
        var prior = new Sonic2CompetitionPlaneScroll(); prior.initialize(0, 0, 0, 0);
        prior.completeCameraPass(1, 0, 0, 32, 32, false);
        prior.copyAtVInt(false); prior.requestRedraw();
        var retained = prior.capture();
        var next = new Sonic2CompetitionPlaneScroll();
        next.initializeLevel(retained, 100, 200, 300, 400);
        var loaded = next.capture();
        assertEquals(16, loaded.live().get(0).horizontalLatch());
        assertEquals(16, loaded.live().get(1).horizontalLatch());
        assertEquals(16, loaded.live().get(1).verticalLatch());
        assertEquals(300, loaded.live().get(1).x()); assertEquals(400, loaded.live().get(1).y());
        assertEquals(32, next.copy(1).x()); assertEquals(32, next.copy(1).y());
        assertEquals(0, loaded.live().get(1).flags()); assertEquals(0, next.copy(1).flags());
        assertFalse(loaded.redraw()); assertTrue(loaded.postLoadScrollClear());
        assertEquals(10, retained.copied().get(1).flags(), "retained input is immutable");
        next.menuClear();
        for (var row : next.capture().live()) assertEquals(new Sonic2CompetitionPlaneScroll.Player(0,0,0,0,0), row);
        for (var row : next.capture().copied()) assertEquals(new Sonic2CompetitionPlaneScroll.Player(0,0,0,0,0), row);
        next.initializeLevel(next.capture(), 11, 12, 13, 14);
        assertEquals(0, next.capture().live().get(1).horizontalLatch());
        assertEquals(0, next.copy(1).x()); assertEquals(0, next.copy(1).y());
    }
}
