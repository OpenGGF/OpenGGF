package com.openggf.editor.persistence;

import java.util.List;

/** Editor envelope v3; terrain/rings retain v2 shapes while object identity becomes tagged. */
public record EditorSavePayloadV3(
        List<EditorSavePayload.BlockState> blocks,
        List<EditorSavePayload.ChunkState> chunks,
        List<EditorSavePayload.MapCell> mapCells,
        List<ObjectSpawnStateV3> objects,
        List<EditorSavePayload.RingState> rings) {
    public EditorSavePayloadV3 {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        mapCells = mapCells == null ? List.of() : List.copyOf(mapCells);
        objects = objects == null ? List.of() : List.copyOf(objects);
        rings = rings == null ? List.of() : List.copyOf(rings);
    }
}
