package com.openggf.graphics;

import java.util.IdentityHashMap;
import java.util.List;

/** Pure native-picture inputs, without requiring a renderer or a test-only production factory. */
public final class MotionPacketFixture {
    private MotionPacketFixture() { }
    public record Picture(SpritePresentation.Frame frame, MotionPacket packet) { }
    public static Picture owned(MotionPacket.Key key, int x, int y, int cameraX, int cameraY) {
        var tile = new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER, 1, 0, false, false, false,
                x - cameraX, y - cameraY, 8, 8, true, 15, false, 1);
        var owners = new IdentityHashMap<SpritePresentation.Tile, MotionPacket.Owner>();
        owners.put(tile, new MotionPacket.Owner(key, x, y, MotionPacket.Anchoring.FOLLOW));
        return new Picture(new SpritePresentation.Frame(List.of(tile)), new MotionPacket(cameraX, cameraY, owners));
    }
}
