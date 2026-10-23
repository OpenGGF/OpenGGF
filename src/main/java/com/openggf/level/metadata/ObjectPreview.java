package com.openggf.level.metadata;

import java.util.List;

/** Static authoring pose, in native pixels relative to the placement origin. */
public record ObjectPreview(List<Part> parts) {
    public ObjectPreview { parts = List.copyOf(parts); }
    /** Absolute base palette override, or -1 to use the production sheet's palette. */
    public record Part(String artKey, int frame, int x, int y, boolean hFlip, boolean vFlip, int paletteLine) {
        public Part(String artKey, int frame, int x, int y, boolean hFlip, boolean vFlip) {
            this(artKey, frame, x, y, hFlip, vFlip, -1);
        }
        public Part {
            if (paletteLine < -1 || paletteLine > 3) throw new IllegalArgumentException("Preview palette must be -1 or 0..3");
        }
    }
    public static ObjectPreview frame(String key, int frame) {
        return new ObjectPreview(List.of(new Part(key, frame, 0, 0, false, false)));
    }
    public static ObjectPreview empty() { return new ObjectPreview(List.of()); }
}
