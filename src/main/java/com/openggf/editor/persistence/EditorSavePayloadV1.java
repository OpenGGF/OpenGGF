package com.openggf.editor.persistence;

import java.util.List;

/** Historical three-field editor payload. Its raw canonical JSON tree is hash-stable. */
public record EditorSavePayloadV1(
        List<EditorSavePayload.BlockState> blocks,
        List<EditorSavePayload.ChunkState> chunks,
        List<EditorSavePayload.MapCell> mapCells) {
    public EditorSavePayloadV1 {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        mapCells = mapCells == null ? List.of() : List.copyOf(mapCells);
    }
}
