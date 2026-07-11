package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.rings.RingSpawn;

public final class PlaceRingSpawnCommand implements EditorCommand {
    private final MutableLevel level;
    private final RingSpawn spawn;
    private final com.openggf.level.objects.ObjectSpawn backingObject;
    public PlaceRingSpawnCommand(MutableLevel level, RingSpawn spawn) { this(level, spawn, null); }
    public PlaceRingSpawnCommand(MutableLevel level, RingSpawn spawn,
                                 com.openggf.level.objects.ObjectSpawn backingObject) {
        this.level = level;
        this.spawn = spawn;
        this.backingObject = backingObject;
    }
    @Override public void apply() {
        if (backingObject == null) level.addRingSpawn(spawn);
        else level.addObjectBackedRingSpawn(spawn, backingObject);
    }
    @Override public void undo() {
        if (backingObject == null) level.removeRingSpawn(spawn);
        else level.removeObjectBackedRingSpawn(spawn, backingObject);
    }
}
