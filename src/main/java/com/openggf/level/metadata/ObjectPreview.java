package com.openggf.level.metadata;

import java.util.List;

/** Static authoring pose, in native pixels relative to the placement origin. */
public record ObjectPreview(List<Part> parts) {
    public ObjectPreview { parts = List.copyOf(parts); }
    public record Part(String artKey, int frame, int x, int y, boolean hFlip, boolean vFlip) { }
    public static ObjectPreview frame(String key, int frame) {
        return new ObjectPreview(List.of(new Part(key, frame, 0, 0, false, false)));
    }
    public static ObjectPreview empty() { return new ObjectPreview(List.of()); }
}
