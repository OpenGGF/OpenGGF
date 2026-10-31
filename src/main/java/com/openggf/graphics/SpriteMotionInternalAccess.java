package com.openggf.graphics;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Engine collection bridge. The disabled path creates no owner table or sidecar. */
public final class SpriteMotionInternalAccess {
    private SpriteMotionInternalAccess() { }
    static final class Collection {
        final IdentityHashMap<SpritePresentation.Tile, MotionPacket.Owner> owners = new IdentityHashMap<>();
        final java.util.Map<MotionPacket.Key, MotionPacket.Owner> anchors = new java.util.HashMap<>();
    }
    public record Capture(SpritePresentation.Frame frame, MotionPacket motion) { }

    public static Capture collect(GraphicsManager graphics, int cameraX, int cameraY,
                                  Supplier<SpritePresentation.Frame> producer) {
        if (graphics.spriteMotionCollection != null) throw new IllegalStateException("Nested motion collection");
        var collection = new Collection();
        graphics.spriteMotionCollection = collection;
        try {
            var frame = producer.get();
            var retained = java.util.Collections.newSetFromMap(new IdentityHashMap<SpritePresentation.Tile, Boolean>());
            retained.addAll(frame.tiles());
            collection.owners.keySet().retainAll(retained);
            return new Capture(frame, new MotionPacket(cameraX, cameraY, collection.owners));
        } finally {
            graphics.spriteMotionCollection = null;
            graphics.spriteMotionOwner = null;
        }
    }
    public static boolean collecting(GraphicsManager graphics) {
        return graphics != null && graphics.spriteMotionCollection != null;
    }
    public static void withOwner(GraphicsManager graphics, MotionPacket.Owner owner, Runnable draw) {
        if (!collecting(graphics)) { draw.run(); return; }
        var previous = graphics.spriteMotionOwner;
        graphics.spriteMotionOwner = owner == null ? null
                : graphics.spriteMotionCollection.anchors.computeIfAbsent(owner.key(), ignored -> owner);
        try { draw.run(); }
        finally { graphics.spriteMotionOwner = previous; }
    }
    static void emitted(GraphicsManager graphics, SpritePresentation.Tile tile) {
        if (collecting(graphics) && graphics.spriteMotionOwner != null && !tile.ghost())
            graphics.spriteMotionCollection.owners.put(tile, graphics.spriteMotionOwner);
    }
    /** Central composition replaces geometry without changing its native owner. */
    public static SpritePresentation.Tile replacement(GraphicsManager graphics,
            SpritePresentation.Tile source, SpritePresentation.Tile replacement) {
        Objects.requireNonNull(replacement);
        if (collecting(graphics)) {
            var owner = graphics.spriteMotionCollection.owners.get(source);
            if (owner != null) graphics.spriteMotionCollection.owners.put(replacement, owner);
        }
        return replacement;
    }
}
