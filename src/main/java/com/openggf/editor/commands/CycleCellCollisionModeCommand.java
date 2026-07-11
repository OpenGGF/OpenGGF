package com.openggf.editor.commands;

import com.openggf.editor.EditorCollisionPath;
import com.openggf.editor.EditorCommand;
import com.openggf.level.Block;
import com.openggf.level.MutableLevel;

import java.util.Arrays;
import java.util.Objects;

/**
 * Cycles one block-cell collision mode without mutating a live descriptor.
 * Primary collision uses descriptor mask {@code 0x3000} (sensor bits
 * {@code 0x0C/0x0D}); secondary collision uses {@code 0xC000} (sensor bits
 * {@code 0x0E/0x0F}).
 */
public final class CycleCellCollisionModeCommand implements EditorCommand {
    private static final int PRIMARY_SHIFT = 12;
    private static final int SECONDARY_SHIFT = 14;
    private static final int MODE_MASK = 0x3;

    private final MutableLevel level;
    private final int blockIndex;
    private final int[] beforeState;
    private final int[] afterState;

    public CycleCellCollisionModeCommand(MutableLevel level,
                                         int blockIndex,
                                         int cellIndex,
                                         EditorCollisionPath path) {
        this.level = Objects.requireNonNull(level, "level");
        Objects.requireNonNull(path, "path");
        if (blockIndex < 0 || blockIndex >= level.getBlockCount()) {
            throw new IllegalArgumentException("blockIndex out of range: " + blockIndex);
        }
        Block block = level.getBlock(blockIndex);
        int cellCount = block.getGridSide() * block.getGridSide();
        if (cellIndex < 0 || cellIndex >= cellCount) {
            throw new IllegalArgumentException("cellIndex out of range: " + cellIndex);
        }
        this.blockIndex = blockIndex;
        this.beforeState = block.saveState();
        this.afterState = Arrays.copyOf(beforeState, beforeState.length);
        int shift = path == EditorCollisionPath.PRIMARY ? PRIMARY_SHIFT : SECONDARY_SHIFT;
        int mask = MODE_MASK << shift;
        int nextMode = (((afterState[cellIndex] >>> shift) & MODE_MASK) + 1) & MODE_MASK;
        afterState[cellIndex] = (afterState[cellIndex] & ~mask) | (nextMode << shift);
    }

    @Override public void apply() { level.restoreBlockState(blockIndex, afterState); }
    @Override public void undo() { level.restoreBlockState(blockIndex, beforeState); }
}
