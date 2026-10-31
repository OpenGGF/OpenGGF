package com.openggf.graphics;

import java.util.Map;

/** Immutable per-picture geometry deltas, applied after current art and presentation composition. */
public final class SpriteMotionProjection {
    public record Offset(int x, int y) {
        public static final Offset ZERO = new Offset(0, 0);
        public Offset weighted(double weight) {
            return new Offset((int) Math.rint(x * weight), (int) Math.rint(y * weight));
        }
    }
    private final MotionPacket packet;
    private final Map<MotionPacket.Key, Offset> steps;
    private final double weight;
    private final Offset camera;

    public SpriteMotionProjection(MotionPacket packet, Map<MotionPacket.Key, Offset> steps,
                                  double weight, Offset camera) {
        this.packet = packet;
        this.steps = Map.copyOf(steps);
        this.weight = weight;
        this.camera = camera;
    }
    public Offset offset(SpritePresentation.Tile tile) {
        if (tile.layer().isHud()) return camera;
        var owner = packet.owner(tile);
        if (owner == null || tile.ghost()) return Offset.ZERO;
        return switch (owner.anchoring()) {
            case WORLD -> Offset.ZERO;
            case SCREEN -> camera;
            case FOLLOW -> steps.getOrDefault(owner.key(), Offset.ZERO).weighted(weight);
        };
    }
}
