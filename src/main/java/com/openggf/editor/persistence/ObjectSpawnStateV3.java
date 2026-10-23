package com.openggf.editor.persistence;

import com.openggf.game.ModKeySyntax;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Version-3 editor spawn identity: exactly one stock id or namespaced object key. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ObjectSpawnStateV3(int placementId, int x, int y, Integer stockObjectId,
                                 String objectKey, int subtype, int renderFlags,
                                 boolean respawnTracked, int rawYWord,
                                 com.fasterxml.jackson.databind.JsonNode authoredProperties) {
    public ObjectSpawnStateV3 {
        if ((stockObjectId == null) == (objectKey == null)) {
            throw new IllegalArgumentException("Object spawn requires exactly one identity arm");
        }
        if (placementId < 0 || x < 0 || x > 0xFFFF || y < 0 || y > 0xFFFF
                || rawYWord < 0 || rawYWord > 0xFFFF
                || subtype < 0 || subtype > 0xFF || renderFlags < 0 || renderFlags > 3) {
            throw new IllegalArgumentException("Object placement fields exceed persisted unsigned ranges");
        }
        if (stockObjectId != null && (stockObjectId < 0 || stockObjectId > 0xFF)) {
            throw new IllegalArgumentException("stockObjectId must be unsigned 8-bit");
        }
        if (objectKey != null) objectKey = ModKeySyntax.requireDisplayKey(objectKey);
        if (authoredProperties != null && !authoredProperties.isNull()) {
            authoredProperties = authoredProperties.deepCopy();
            try {
                var properties = com.openggf.level.objects.authored.AuthoredObjectPropertiesJson.parse(authoredProperties);
                if (objectKey == null || !objectKey.equals(properties.objectKey())
                        || (long) x + properties.width() > 0x10000L || (long) y + properties.height() > 0x1000L)
                    throw new IllegalArgumentException("Authored override properties do not match object identity/geometry");
            } catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid authored override properties", error); }
        }
    }

    public ObjectSpawnStateV3(int placementId, int x, int y, Integer stockObjectId, String objectKey,
                             int subtype, int renderFlags, boolean respawnTracked, int rawYWord) {
        this(placementId, x, y, stockObjectId, objectKey, subtype, renderFlags, respawnTracked, rawYWord, null);
    }

    public com.openggf.level.objects.authored.AuthoredObjectProperties properties() {
        try { return com.openggf.level.objects.authored.AuthoredObjectPropertiesJson.parse(authoredProperties); }
        catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid authored override properties", error); }
    }

    public static ObjectSpawnStateV3 stock(EditorSavePayload.ObjectState state) {
        return new ObjectSpawnStateV3(state.placementId(), state.x(), state.y(), state.objectId(), null,
                state.subtype(), state.renderFlags(), state.respawnTracked(), state.rawYWord());
    }
}
