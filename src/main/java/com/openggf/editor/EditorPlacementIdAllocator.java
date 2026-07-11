package com.openggf.editor;

import com.openggf.level.Level;

/** Per-editor-level allocator for stable object and ring placement identities. */
public final class EditorPlacementIdAllocator {
    private int nextObjectId;
    private int nextRingId;

    public EditorPlacementIdAllocator(Level level) {
        nextObjectId = level.getObjects().stream().mapToInt(s -> s.layoutIndex()).max().orElse(-1) + 1;
        nextRingId = level.getRings().stream().mapToInt(s -> s.placementId()).max().orElse(-1) + 1;
    }

    public int nextObjectId() { return checkedNext(true); }
    public int nextRingId() { return checkedNext(false); }

    private int checkedNext(boolean object) {
        int next = object ? nextObjectId : nextRingId;
        if (next < 0 || next == Integer.MAX_VALUE) {
            throw new IllegalStateException((object ? "object" : "ring") + " placement id space exhausted");
        }
        if (object) nextObjectId = next + 1;
        else nextRingId = next + 1;
        return next;
    }
}
