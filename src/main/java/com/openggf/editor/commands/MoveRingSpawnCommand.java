package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.rings.RingSpawn;

public final class MoveRingSpawnCommand implements EditorCommand {
    private final MutableLevel level;
    private final RingSpawn before;
    private final RingSpawn after;
    private final int originalIndex;
    private final com.openggf.level.objects.ObjectSpawn beforeBackingObject;
    private final com.openggf.level.objects.ObjectSpawn afterBackingObject;
    private final java.util.List<RingSpawn> beforeGroup;
    private final java.util.List<RingSpawn> afterGroup;
    private final MutableLevel.ObjectBackedRingState beforeState;
    public MoveRingSpawnCommand(MutableLevel level, RingSpawn before, RingSpawn after) {
        this(level, before, after, null, null);
    }
    public MoveRingSpawnCommand(MutableLevel level, RingSpawn before, RingSpawn after,
                                com.openggf.level.objects.ObjectSpawn beforeBackingObject,
                                com.openggf.level.objects.ObjectSpawn afterBackingObject) {
        this.level = level;
        this.before = before;
        this.after = after;
        this.beforeBackingObject = beforeBackingObject;
        this.afterBackingObject = afterBackingObject;
        this.beforeState = beforeBackingObject == null ? null : level.snapshotObjectBackedRingState();
        this.beforeGroup = beforeBackingObject == null ? java.util.List.of()
                : java.util.List.copyOf(level.ringObjectPlacementMapping().get(beforeBackingObject));
        if (beforeBackingObject != null && beforeGroup.isEmpty()) {
            throw new IllegalArgumentException("Unknown object-backed ring group");
        }
        int dx = after.x() - before.x();
        int dy = after.y() - before.y();
        this.afterGroup = beforeGroup.stream()
                .map(ring -> movedRing(ring, dx, dy))
                .toList();
        this.originalIndex = level.ringPlacementIndex(before.placementId());
        if (originalIndex < 0) throw new IllegalArgumentException("Unknown ring placement id " + before.placementId());
    }
    @Override public void apply() {
        if (beforeBackingObject == null) level.moveRingSpawn(before, after);
        else level.moveObjectBackedRingGroup(beforeBackingObject, afterBackingObject, beforeGroup, afterGroup);
    }
    @Override public void undo() {
        if (beforeBackingObject == null) level.restoreMovedRingSpawn(after, before, originalIndex);
        else level.restoreObjectBackedRingState(beforeState);
    }

    private static RingSpawn movedRing(RingSpawn ring, int dx, int dy) {
        int x = ring.x() + dx;
        int y = ring.y() + dy;
        if (x < 0 || x >= 0xFFFF || y < 0 || y > 0xFFFF) {
            throw new IllegalArgumentException("Moved ring group exceeds native coordinates");
        }
        return new RingSpawn(x, y, ring.placementId());
    }
}
