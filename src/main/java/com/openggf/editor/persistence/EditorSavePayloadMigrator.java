package com.openggf.editor.persistence;

/** Explicit historical payload migration without changing v1/v2 DTO serialization. */
public final class EditorSavePayloadMigrator {
    private EditorSavePayloadMigrator() {}

    public static EditorSavePayloadV3 fromV1(EditorSavePayloadV1 source) {
        return new EditorSavePayloadV3(source.blocks(), source.chunks(), source.mapCells(),
                java.util.List.of(), java.util.List.of());
    }

    public static EditorSavePayloadV3 fromV2(EditorSavePayload source) {
        return new EditorSavePayloadV3(source.blocks(), source.chunks(), source.mapCells(),
                source.objects().stream().map(ObjectSpawnStateV3::stock).toList(), source.rings());
    }
}
