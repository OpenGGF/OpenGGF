package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;

public final class DeleteObjectSpawnCommand implements EditorCommand {
    private final MutableLevel level;
    private final ObjectSpawn spawn;
    private final int index;
    public DeleteObjectSpawnCommand(MutableLevel level, ObjectSpawn spawn) {
        this.level = level;
        this.spawn = spawn;
        this.index = level.objectPlacementIndex(spawn.layoutIndex());
        if (index < 0) throw new IllegalArgumentException("Unknown object placement id " + spawn.layoutIndex());
    }
    @Override public void apply() { level.removeObjectSpawn(spawn); }
    @Override public void undo() { level.restoreObjectSpawnAt(spawn, index); }
}
