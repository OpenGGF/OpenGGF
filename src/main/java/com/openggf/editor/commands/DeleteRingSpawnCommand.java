package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.rings.RingSpawn;

public final class DeleteRingSpawnCommand implements EditorCommand {
    private final MutableLevel level;
    private final RingSpawn spawn;
    private final int index;
    private final com.openggf.level.objects.ObjectSpawn backingObject;
    private final MutableLevel.ObjectBackedRingState beforeState;
    public DeleteRingSpawnCommand(MutableLevel level, RingSpawn spawn) { this(level, spawn, null); }
    public DeleteRingSpawnCommand(MutableLevel level, RingSpawn spawn,
                                  com.openggf.level.objects.ObjectSpawn backingObject) {
        this.level = level;
        this.spawn = spawn;
        this.backingObject = backingObject;
        this.beforeState = backingObject == null ? null : level.snapshotObjectBackedRingState();
        this.index = level.ringPlacementIndex(spawn.placementId());
        if (index < 0) throw new IllegalArgumentException("Unknown ring placement id " + spawn.placementId());
    }
    @Override public void apply() {
        if (backingObject == null) level.removeRingSpawn(spawn);
        else level.removeObjectBackedRingGroup(backingObject);
    }
    @Override public void undo() {
        if (backingObject == null) level.restoreRingSpawnAt(spawn, index);
        else level.restoreObjectBackedRingState(beforeState);
    }
}
