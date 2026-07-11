package com.openggf.editor.commands;

import com.openggf.editor.EditorCollisionPath;
import com.openggf.editor.EditorCommand;
import com.openggf.level.Chunk;
import com.openggf.level.MutableLevel;

import java.util.Arrays;
import java.util.Objects;

/**
 * Reassigns a chunk collision shape through tracked whole-state replacement.
 * The primary path ({@code 0x3000}, sensor bits {@code 0x0C/0x0D}) uses
 * {@link Chunk#getSolidTileIndex()}; the secondary path ({@code 0xC000}, sensor
 * bits {@code 0x0E/0x0F}) uses {@link Chunk#getSolidTileAltIndex()}.
 */
public final class SetChunkSolidTileIndexCommand implements EditorCommand {
    private final MutableLevel level;
    private final int chunkIndex;
    private final int[] beforeState;
    private final int[] afterState;

    public SetChunkSolidTileIndexCommand(MutableLevel level,
                                         int chunkIndex,
                                         EditorCollisionPath path,
                                         int newIndex) {
        this.level = Objects.requireNonNull(level, "level");
        Objects.requireNonNull(path, "path");
        if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
            throw new IllegalArgumentException("chunkIndex out of range: " + chunkIndex);
        }
        if (newIndex < 0 || (newIndex != 0 && newIndex >= level.getSolidTileCount())) {
            throw new IllegalArgumentException("solid tile index is not present in the loaded level: " + newIndex);
        }
        this.chunkIndex = chunkIndex;
        this.beforeState = level.getChunk(chunkIndex).saveState();
        this.afterState = Arrays.copyOf(beforeState, beforeState.length);
        int stateIndex = Chunk.PATTERNS_PER_CHUNK
                + (path == EditorCollisionPath.PRIMARY ? 0 : 1);
        afterState[stateIndex] = newIndex;
    }

    @Override public void apply() { level.restoreChunkState(chunkIndex, afterState); }
    @Override public void undo() { level.restoreChunkState(chunkIndex, beforeState); }
}
