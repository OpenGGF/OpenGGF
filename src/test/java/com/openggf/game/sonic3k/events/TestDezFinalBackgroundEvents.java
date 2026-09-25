package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.runtime.DezFinalBossZoneRuntimeState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestDezFinalBackgroundEvents {
    private static final class Surface implements DezFinalBackgroundEvents.Surface {
        final List<String> chunks = new ArrayList<>();
        int openingAttempts, chaseAttempts, laserUpdates, chaseX, reads;
        boolean allocation;
        public int descriptor(int x, int y) { reads++; return 0x1234; }
        public void writeChunk(int column, int row, int chunk) { chunks.add(column + ":" + row + ":" + chunk); }
        public boolean spawnOpeningCollapse() { openingAttempts++; return allocation; }
        public boolean spawnChaseCollapse(int x) { chaseAttempts++; chaseX = x; return allocation; }
        public void updateLaser() { laserUpdates++; }
    }
    private DezFinalBossZoneRuntimeState state(int routine, int base) {
        var state = new DezFinalBossZoneRuntimeState(PlayerCharacter.SONIC_ALONE);
        state.windowBase(base); state.backgroundRoutine(routine);
        state.publishPlanePosition(0x80, 0);
        state.plane().resetDrawPosition(state.planeX(), state.planeY());
        return state;
    }
    @Test void initialPublicationRefreshesFifteenRowsAndAdvancesOnce() {
        var state = state(0, 0x6C0); var surface = new Surface();
        DezFinalBackgroundEvents.update(state, 0x80, 0, surface);
        assertEquals(0, surface.reads);
        state.eventsFg5(0xFF);
        DezFinalBackgroundEvents.update(state, 0x80, 0, surface);
        assertEquals(List.of("13:2:26"), surface.chunks);
        assertEquals(15 * 21 * 4, surface.reads);
        assertEquals(0, state.eventsFg5()); assertEquals(4, state.backgroundRoutine());
    }
    @Test void openingAllocationRetriesWithoutAdvancingItsFrontier() {
        var state = state(4, 0x6C0); var surface = new Surface();
        state.screenShake().writeFlag(-1);
        DezFinalBackgroundEvents.update(state, 0x80, 0, surface);
        assertEquals(4, state.backgroundRoutine()); assertEquals(0x80, state.breakFrontier());
        surface.allocation = true;
        DezFinalBackgroundEvents.update(state, 0x80, 0, surface);
        assertEquals(2, surface.openingAttempts);
        assertEquals(8, state.backgroundRoutine()); assertEquals(0x2C0, state.breakFrontier());
    }
    @Test void firstWindowChangeRetainsHorizontalScrollForExactlyEightPasses() {
        var state = state(8, 0x2C0); var surface = new Surface();
        for (int frame = 0; frame < 8; frame++) {
            DezFinalBackgroundEvents.update(state, 0x520, 0x380, surface);
            assertEquals(frame == 7 ? 0x10 : 0xC, state.backgroundRoutine());
            assertEquals(frame == 7 ? 0 : 0x380, state.retainedPlaneX());
        }
        assertEquals(0, surface.chaseAttempts);
        assertEquals(0x1234, state.plane().descriptor(0, 0xF8));
        assertEquals(0x1234, state.plane().descriptor(0, 0));
    }
    @Test void chaseFailureDoesNotRetryOrAdvanceBreakFrontierAndLaserRunsDuringRedraw() {
        var state = state(0x10, 0x6C0); var surface = new Surface();
        state.breakFrontier(0x2C0);
        for (int frame = 0; frame < 8; frame++)
            DezFinalBackgroundEvents.update(state, 0x557, 0x2C0, surface);
        assertEquals(1, surface.chaseAttempts); assertEquals(0x530, surface.chaseX);
        assertEquals(0x2C0, state.breakFrontier());
        assertEquals(8, surface.laserUpdates, "loc_5A662 falls through while redraw is incomplete");
        assertEquals(0x18, state.backgroundRoutine());
    }
    @Test void chaseSuccessPublishesAlignedFrontier() {
        var state = state(0x10, 0x6C0); var surface = new Surface(); surface.allocation = true;
        DezFinalBackgroundEvents.update(state, 0x557, 0x2C0, surface);
        assertEquals(0x540, state.breakFrontier()); assertEquals(0x14, state.backgroundRoutine());
    }
    @Test void allFourMouthPublicationsWriteTheNativeChunkPairsAndWrap() {
        var state = state(0x18, 0x6C0); var surface = new Surface();
        for (int phase = 0; phase < 4; phase++) {
            state.eventsFg5(1);
            DezFinalBackgroundEvents.update(state, 0x80, 0, surface);
        }
        assertEquals(List.of("14:2:3", "14:3:6", "14:2:3", "14:3:9",
                "14:2:3", "14:3:6", "14:2:7", "14:3:8"), surface.chunks);
        assertEquals(0, state.mouthPhase()); assertEquals(0, state.eventsFg5());
        assertEquals(4, surface.laserUpdates);
    }
    @Test void finalWindowClearFinishesInEightPassesAndReplaysFromRetainedSnapshot() {
        var state = state(0x18, 0); var surface = new Surface();
        DezFinalBackgroundEvents.update(state, 0x900, 0x6C0, surface);
        assertEquals(0x1C, state.backgroundRoutine()); assertEquals(0x6C0, state.retainedPlaneX());
        byte[] snapshot = state.captureBytes();
        for (int i = 0; i < 7; i++) DezFinalBackgroundEvents.update(state, 0x900, 0, surface);
        byte[] expected = state.captureBytes();
        assertEquals(0x20, state.backgroundRoutine()); assertEquals(0, state.retainedPlaneX());
        state.restoreBytes(snapshot);
        for (int i = 0; i < 7; i++) DezFinalBackgroundEvents.update(state, 0x900, 0, surface);
        assertArrayEquals(expected, state.captureBytes()); assertEquals(0, surface.laserUpdates);
    }
}
