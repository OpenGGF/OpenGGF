package com.openggf.game.sonic2.competition.view;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId.*;

class TestSonic2CompetitionPlaneScroll {
    @Test void boundaryLatchesFollowBothDirectionsAndFrozenCallsRetainPositionsWithoutFlags() {
        var scroll = new Sonic2CompetitionPlaneScroll();
        scroll.initialize(0, 0, 112, 64);
        scroll.completeCameraPass(0, 0, 0, 0, 0, false); // native initial equality toggles the zero latches
        scroll.copyAtVInt(false);
        assertEquals(10, scroll.copy(0).flags());
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
    @Test void descriptorsIgnoreBit10AndExchangeFieldBankForNativeVerticalFlip() {
        var planes = new Sonic2CompetitionPlanes();
        for (int value = 0; value < 65536; value++) {
            planes.write(FOREGROUND_P1, 63, 31, value);
            int word = planes.fieldPlane(FOREGROUND_P1).descriptorAt(-1, -1);
            assertEquals(value & 0xF800, word & 0xF800);
            assertEquals(value & 1023, word & 1023);
            assertEquals((value & 0x1000) != 0 ? 1024 : 0, word & 1024);
        }
        var saved = planes.capture();
        planes.write(FOREGROUND_P1, 63, 31, 0);
        planes.restore(saved);
        assertEquals(0xFFFF, planes.cell(FOREGROUND_P1, 63, 31));
        assertSame(planes.fieldPlane(BACKGROUND), planes.source(FOREGROUND_P1).backgroundPlane());
        assertSame(planes.fieldPlane(BACKGROUND), planes.source(FOREGROUND_P2).backgroundPlane());
    }
}
