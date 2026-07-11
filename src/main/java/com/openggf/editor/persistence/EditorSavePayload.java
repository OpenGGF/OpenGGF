package com.openggf.editor.persistence;

import java.util.List;

public record EditorSavePayload(
        List<BlockState> blocks,
        List<ChunkState> chunks,
        List<MapCell> mapCells,
        List<ObjectState> objects,
        List<RingState> rings) {

    public EditorSavePayload {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        mapCells = mapCells == null ? List.of() : List.copyOf(mapCells);
        objects = objects == null ? List.of() : List.copyOf(objects);
        rings = rings == null ? List.of() : List.copyOf(rings);
    }

    public EditorSavePayload(List<BlockState> blocks, List<ChunkState> chunks, List<MapCell> mapCells) {
        this(blocks, chunks, mapCells, List.of(), List.of());
    }

    public record BlockState(int index, int[] state) {
    }

    public record ChunkState(int index, int[] state) {
    }

    public record MapCell(int layer, int x, int y, int blockIndex) {
    }

    public record ObjectState(int placementId, int x, int y, int objectId, int subtype,
                              int renderFlags, boolean respawnTracked, int rawYWord) {
    }

    public record RingState(int placementId, int x, int y, Integer backingObjectPlacementId) {
    }
}
