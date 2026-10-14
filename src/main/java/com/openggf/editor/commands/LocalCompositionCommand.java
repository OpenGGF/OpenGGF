package com.openggf.editor.commands;

import com.openggf.editor.EditorCommand;
import com.openggf.level.MutableLevel;

/** Atomic local block/chunk replacement. Captures complete slot states for exact undo/redo. */
public final class LocalCompositionCommand implements EditorCommand {
    private final MutableLevel level;
    private final int layer,x,y,beforeMap,block,chunk;
    private final int[] beforeBlock,afterBlock,beforeChunk,afterChunk;
    public LocalCompositionCommand(MutableLevel level,int layer,int x,int y,int block,int[] blockState,
                                   int chunk,int[] chunkState) {
        this.level=level;this.layer=layer;this.x=x;this.y=y;this.block=block;this.chunk=chunk;
        beforeMap=Byte.toUnsignedInt(level.getMap().getValue(layer,x,y));
        beforeBlock=level.getBlock(block).saveState().clone();afterBlock=blockState.clone();
        beforeChunk=chunk<0?null:level.getChunk(chunk).saveState().clone();
        afterChunk=chunkState==null?null:chunkState.clone();
    }
    @Override public void apply() {
        if(chunk>=0)level.restoreChunkState(chunk,afterChunk);
        level.restoreBlockState(block,afterBlock);level.setBlockInMap(layer,x,y,block);
    }
    @Override public void undo() {
        level.setBlockInMap(layer,x,y,beforeMap);level.restoreBlockState(block,beforeBlock);
        if(chunk>=0)level.restoreChunkState(chunk,beforeChunk);
    }
}
