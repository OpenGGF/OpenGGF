package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;

public final class MoveObjectSpawnCommand implements EditorCommand {
    private final MutableLevel level;
    private final ObjectSpawn before;
    private final ObjectSpawn after;
    private final int originalIndex;
    public MoveObjectSpawnCommand(MutableLevel level, ObjectSpawn before, ObjectSpawn after) {
        this.level = level;
        this.before = before;
        this.after = after;
        this.originalIndex = level.objectPlacementIndex(before.layoutIndex());
        if (originalIndex < 0) throw new IllegalArgumentException("Unknown object placement id " + before.layoutIndex());
    }
    @Override public void apply() { level.moveObjectSpawn(before, after); }
    @Override public void undo() { level.restoreMovedObjectSpawn(after, before, originalIndex); }
}
