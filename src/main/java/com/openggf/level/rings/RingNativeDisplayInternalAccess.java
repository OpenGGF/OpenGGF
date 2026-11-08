package com.openggf.level.rings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only native per-player ring display input; never samples the union of active spawns. */
public final class RingNativeDisplayInternalAccess {
    private RingNativeDisplayInternalAccess() { }
    public record Row(int index, int x, int y, boolean collected, int sparkleStartFrame) { }
    public record Window(int start, int end, int animationFrame, int sparkleStartIndex,
                         int sparkleFrameCount, int sparkleFrameDelay, List<Row> rows) {
        public Window { rows = List.copyOf(rows); }
    }
    public static Window window(RingManager manager, List<RingSpawn> nativeRows, int slot, int frameCounter) {
        Objects.requireNonNull(manager); nativeRows = List.copyOf(nativeRows);
        if (slot < 0 || slot > 1) throw new IllegalArgumentException("Native player slot");
        var streaming = manager.nativeDisplayStreaming();
        int start = streaming.start(slot), end = streaming.end(slot);
        Objects.checkFromToIndex(start, end, nativeRows.size());
        var rows = new ArrayList<Row>(end - start);
        for (int i = start; i < end; i++) {
            var spawn = nativeRows.get(i);
            rows.add(new Row(i, spawn.x(), spawn.y(), manager.isCollected(spawn), manager.getSparkleStartFrame(spawn)));
        }
        return new Window(start, end, manager.nativeDisplayStageFrame(frameCounter), manager.getSparkleStartIndex(),
                manager.getSparkleFrameCount(), manager.getSparkleFrameDelay(), rows);
    }
}
