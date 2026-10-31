package com.openggf.graphics;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Supplier;

/** Engine collection bridge. The disabled path creates no owner table or sidecar. */
public final class SpriteMotionInternalAccess {
    private SpriteMotionInternalAccess() { }
    static final class Collection {
        final IdentityHashMap<SpritePresentation.Tile, MotionPacket.Binding> owners = new IdentityHashMap<>();
        final java.util.Map<MotionPacket.Key, MotionPacket.Owner> anchors = new java.util.HashMap<>();
    }
    private static void engineCaller() {
        if (com.openggf.util.EngineCallerAccess.callerOutside(SpriteMotionInternalAccess.class).getClassLoader()
                != SpriteMotionInternalAccess.class.getClassLoader())
            throw new SecurityException("Motion ownership belongs to the engine presenter");
    }
    public static void installCameraOffset(GraphicsManager graphics, SpriteMotionProjection.Offset camera) {
        engineCaller();
        graphics.sceneMotionOffset = camera;
    }
    public static void withProjection(GraphicsManager graphics, SpriteMotionProjection projection, Runnable draw) {
        engineCaller();
        var previous = graphics.spriteMotionProjection;
        graphics.spriteMotionProjection = projection;
        try { draw.run(); }
        finally { graphics.spriteMotionProjection = previous; }
    }
    public static void clearProjection(GraphicsManager graphics) {
        engineCaller();
        graphics.spriteMotionProjection = null;
        graphics.sceneMotionOffset = null;
    }
    public static SpriteMotionProjection.Offset cameraOffset(GraphicsManager graphics) {
        return graphics.sceneMotionOffset == null ? SpriteMotionProjection.Offset.ZERO : graphics.sceneMotionOffset;
    }
    public static SpriteMotionProjection.Offset offset(GraphicsManager graphics, SpritePresentation.Tile tile) {
        var projection = graphics.spriteMotionProjection;
        return projection == null ? SpriteMotionProjection.Offset.ZERO : projection.offset(tile);
    }
    public record Capture(SpritePresentation.Frame frame, MotionPacket motion) { }

    public static Capture collect(GraphicsManager graphics, int cameraX, int cameraY,
                                  Supplier<SpritePresentation.Frame> producer) {
        engineCaller();
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
    public static MotionPacket.Owner playerOwner(com.openggf.sprites.managers.SpriteManager sprites,
                                                  com.openggf.game.PlayableEntity player) {
        int index = sprites.getRegisteredSidekicks().indexOf(player);
        var id = index < 0 ? com.openggf.game.rewind.identity.PlayerRefId.mainPlayer()
                : com.openggf.game.rewind.identity.PlayerRefId.sidekick(index);
        return new MotionPacket.Owner(new MotionPacket.PlayerKey(id, player),
                player.getCentreX(), player.getCentreY(), MotionPacket.Anchoring.FOLLOW);
    }
    public static boolean collecting(GraphicsManager graphics) {
        return graphics != null && graphics.spriteMotionCollection != null;
    }
    public static void withOwner(GraphicsManager graphics, MotionPacket.Binding owner, Runnable draw) {
        if (!collecting(graphics)) { draw.run(); return; }
        engineCaller();
        var previous = graphics.spriteMotionOwner;
        graphics.spriteMotionOwner = owner instanceof MotionPacket.Owner anchor
                ? graphics.spriteMotionCollection.anchors.computeIfAbsent(anchor.key(), ignored -> anchor) : owner;
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
