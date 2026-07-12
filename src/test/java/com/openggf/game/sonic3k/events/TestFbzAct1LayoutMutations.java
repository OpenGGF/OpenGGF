package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.level.Pattern;
import com.openggf.level.StagedBackgroundPlaneRedrawController;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzAct1LayoutMutations {
    @Test
    void everyRomCopyPlanHasExactPlaneADimensions() {
        assertEquals(List.of(
                new Sonic3kFBZEvents.LayoutCopy(96, 6, 12, 18, 4, 6),
                new Sonic3kFBZEvents.LayoutCopy(100, 5, 26, 18, 6, 4)),
                Sonic3kFBZEvents.act1LayoutCopies(1, true));
        assertEquals(List.of(
                new Sonic3kFBZEvents.LayoutCopy(108, 0, 14, 2, 10, 4),
                new Sonic3kFBZEvents.LayoutCopy(118, 0, 28, 3, 12, 3)),
                Sonic3kFBZEvents.act1LayoutCopies(2, false));
        assertEquals(List.of(
                new Sonic3kFBZEvents.LayoutCopy(96, 17, 40, 18, 6, 4),
                new Sonic3kFBZEvents.LayoutCopy(102, 15, 52, 18, 6, 4)),
                Sonic3kFBZEvents.act1LayoutCopies(3, true));
        assertEquals(List.of(
                new Sonic3kFBZEvents.LayoutCopy(110, 10, 54, 0, 8, 4),
                new Sonic3kFBZEvents.LayoutCopy(118, 10, 62, 0, 18, 5)),
                Sonic3kFBZEvents.act1LayoutCopies(4, false));
        assertEquals(Sonic3kFBZEvents.act1LayoutCopies(4, true),
                Sonic3kFBZEvents.act1LayoutCopies(5, true));
        assertEquals(List.of(new Sonic3kFBZEvents.LayoutCopy(0, 21, 0, 13, 5, 3)),
                Sonic3kFBZEvents.act1LayoutCopies(6, true));
    }

    @Test
    void copyReadsPlaneAThenWritesOnlyThroughMutationSurface() {
        int[][] planeA = new int[32][160];
        for (int y = 0; y < planeA.length; y++) for (int x = 0; x < planeA[y].length; x++) planeA[y][x] = y * 160 + x;
        RecordingSurface surface = new RecordingSurface();
        Sonic3kFBZEvents.applyLayoutCopies(planeA,
                List.of(new Sonic3kFBZEvents.LayoutCopy(10, 4, 20, 6, 3, 2)), surface);
        assertEquals(List.of(
                "0:20:6:" + (4 * 160 + 10), "0:21:6:" + (4 * 160 + 11), "0:22:6:" + (4 * 160 + 12),
                "0:20:7:" + (5 * 160 + 10), "0:21:7:" + (5 * 160 + 11), "0:22:7:" + (5 * 160 + 12)), surface.writes);
    }

    @Test
    void planeBRedrawUsesOneRowOrTwoColumnsAndOneBatchFinish() {
        RecordingPlane surface = new RecordingPlane();
        var controller = new StagedBackgroundPlaneRedrawController(surface);
        assertEquals(0x20, controller.step(StagedBackgroundPlaneRedrawController.Direction.TOP_DOWN, 2, 0x200, 0));
        assertEquals(List.of("row:512:32:32", "finish"), surface.ops);
        surface.ops.clear();
        assertEquals(0x3D0, controller.step(StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT, 1, 0x200, 0x60));
        assertEquals(List.of("col:976:96:976", "col:960:96:960", "finish"), surface.ops);
    }

    @Test
    void planeBReplayRecreatesOnlyCompletedRetainedStrips() {
        RecordingPlane surface = new RecordingPlane();
        var controller = new StagedBackgroundPlaneRedrawController(surface);
        controller.replay(StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT, 3, 0, 0);
        assertEquals(9, surface.ops.size());
        assertEquals("col:0:0:0", surface.ops.get(0));
        assertEquals("col:80:0:80", surface.ops.get(7));
    }

    @Test
    void horizontalRedrawUsesD2AsClippingWindowAndDelayedXAsSource() {
        RecordingPlane surface = new RecordingPlane();
        var controller = new StagedBackgroundPlaneRedrawController(surface);
        controller.replay(StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT, 16, 0, 0x60);
        assertEquals(48, surface.ops.size());
        assertEquals("col:0:96:0", surface.ops.get(0));
        assertEquals("col:496:96:496", surface.ops.get(46));
        surface.ops.clear();
        controller.replay(StagedBackgroundPlaneRedrawController.Direction.LEFT_TO_RIGHT, 16, 0x200, 0x60);
        assertEquals(16, surface.ops.size(), "outdoor d2 clips every left-to-right delayed column");
        surface.ops.clear();
        controller.replay(StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT, 16, 0, 0x60);
        assertEquals(16, surface.ops.size(), "indoor d2 clips every right-to-left delayed column");
        surface.ops.clear();
        controller.replay(StagedBackgroundPlaneRedrawController.Direction.RIGHT_TO_LEFT, 16, 0x200, 0x60);
        assertEquals(48, surface.ops.size());
        assertEquals("col:1008:96:1008", surface.ops.get(0));
        assertEquals("col:512:96:512", surface.ops.get(46));
    }

    @Test
    void verticalRedrawPreservesFullTwelveBitSourceYWhileDestinationWraps() {
        RecordingPlane surface = new RecordingPlane();
        var controller = new StagedBackgroundPlaneRedrawController(surface);
        assertEquals(0xA20, controller.step(
                StagedBackgroundPlaneRedrawController.Direction.TOP_DOWN, 2, 0, 0xA00));
        assertEquals(List.of("row:0:2592:32", "finish"), surface.ops);
        surface.ops.clear();
        assertEquals(0xAE0, controller.step(
                StagedBackgroundPlaneRedrawController.Direction.BOTTOM_UP, 1, 0, 0xA00));
        assertEquals(List.of("row:0:2784:224", "finish"), surface.ops);
    }

    @Test
    void normalDrawTileRowMatchesRomDirectionAndDoubleUpdate() {
        assertArrayEquals(new int[]{0x100}, Sonic3kFBZEvents.normalDrawRowPositions(0x10, 0x20));
        assertArrayEquals(new int[]{0x10}, Sonic3kFBZEvents.normalDrawRowPositions(0x20, 0x10));
        assertArrayEquals(new int[]{0x10, 0x20}, Sonic3kFBZEvents.normalDrawRowPositions(0x30, 0x10));
        assertArrayEquals(new int[]{0x100, 0x110}, Sonic3kFBZEvents.normalDrawRowPositions(0x10, 0x30));
    }

    private static final class RecordingSurface implements LevelMutationSurface {
        final List<String> writes = new ArrayList<>();
        @Override public MutationEffects setPattern(int index, Pattern pattern) { throw new UnsupportedOperationException(); }
        @Override public MutationEffects restoreChunkState(int chunkIndex, int[] state) { throw new UnsupportedOperationException(); }
        @Override public MutationEffects restoreBlockState(int blockIndex, int[] state) { throw new UnsupportedOperationException(); }
        @Override public MutationEffects setBlockInMap(int layer, int x, int y, int value) {
            writes.add(layer + ":" + x + ":" + y + ":" + value);
            return MutationEffects.NONE;
        }
    }

    private static final class RecordingPlane implements StagedBackgroundPlaneRedrawController.Surface {
        final List<String> ops = new ArrayList<>();
        @Override public void copyRow(int sx, int sy, int dy) { ops.add("row:" + sx + ':' + sy + ':' + dy); }
        @Override public void copyColumn(int sx, int sy, int dx) { ops.add("col:" + sx + ':' + sy + ':' + dx); }
        @Override public void finishBatch() { ops.add("finish"); }
    }
}
