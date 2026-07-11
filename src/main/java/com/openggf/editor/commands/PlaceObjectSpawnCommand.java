package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;
import com.openggf.level.objects.ObjectSpawn;

public record PlaceObjectSpawnCommand(MutableLevel level, ObjectSpawn spawn) implements EditorCommand {
    @Override public void apply() { level.addObjectSpawn(spawn); }
    @Override public void undo() { level.removeObjectSpawn(spawn); }
}
