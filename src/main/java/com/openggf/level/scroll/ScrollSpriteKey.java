package com.openggf.level.scroll;

/**
 * Game-owned identity of an object-drawn background component, independent of runtime SST slots.
 */
public record ScrollSpriteKey(String namespace, int index) {
    public ScrollSpriteKey {
        if (namespace == null || namespace.isBlank() || index < 0) {
            throw new IllegalArgumentException(
                    "Background sprite projection requires a namespace and native component index");
        }
    }
}
