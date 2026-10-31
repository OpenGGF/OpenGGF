package com.openggf.level;

import com.openggf.graphics.MotionPacket;
import com.openggf.graphics.SpriteMotionProjection;
import com.openggf.graphics.SpriteMotionProjection.Offset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Matches native picture generations once. Drawing evaluates only the requested weight. */
final class LevelMotionPair {
    record Metadata(int cameraBaseX, int cameraBaseY, int width, int height, int fastScrollCap, boolean wrapped) { }
    record Sample(MotionPacket motion, LevelScrollPresentation scroll, Metadata metadata, Set<MotionCut> cuts) {
        Sample { cuts = Set.copyOf(cuts); }
    }
    record Projection(LevelScrollPresentation scroll, SpriteMotionProjection sprites, Offset camera) { }
    private final Sample current;
    private final Offset cameraStep;
    private final Map<MotionPacket.Key, Offset> ownerSteps;
    private final LevelScrollPresentation.BackgroundMotion background;

    private LevelMotionPair(Sample current, Offset cameraStep, Map<MotionPacket.Key, Offset> ownerSteps,
                            LevelScrollPresentation.BackgroundMotion background) {
        this.current = current;
        this.cameraStep = cameraStep;
        this.ownerSteps = Map.copyOf(ownerSteps);
        this.background = background;
    }
    static LevelMotionPair between(Sample previous, Sample current) {
        var old = previous.metadata(); var now = current.metadata();
        if (old.width() != now.width() || old.height() != now.height() || old.wrapped() || now.wrapped()
                || current.cuts().stream().anyMatch(cut -> cut instanceof MotionCut.Camera)) return null;
        var camera = step(old.cameraBaseX(), old.cameraBaseY(), now.cameraBaseX(), now.cameraBaseY());
        if (beyond(camera, now.fastScrollCap())) return null;
        var steps = new HashMap<MotionPacket.Key, Offset>();
        for (var owner : current.motion().owners().values()) {
            if (owner.anchoring() != MotionPacket.Anchoring.FOLLOW
                    || current.cuts().stream().anyMatch(cut -> cut.matches(owner.key()))) continue;
            var prior = previous.motion().owners().get(owner.key());
            if (prior == null || prior.anchoring() != owner.anchoring()) continue;
            var step = step(prior.x(), prior.y(), owner.x(), owner.y());
            if (!beyond(step, ownerTolerance(now.fastScrollCap()))) steps.put(owner.key(), step);
        }
        return new LevelMotionPair(current, camera, steps,
                current.scroll().backgroundMotionSince(previous.scroll(), now.fastScrollCap(), now.width()));
    }
    /** A presentation tolerance, not a ROM law. Twice the native camera cap admits ordinary fast owners
     * while bounding a missed relocation to one displayed tick. Explicit cuts handle smaller teleports. */
    static int ownerTolerance(int fastScrollCap) { return 2 * fastScrollCap; }
    static Offset step(int oldX, int oldY, int x, int y) { return new Offset((short) (oldX - x), (short) (oldY - y)); }
    private static boolean beyond(Offset step, int bound) { return Math.abs(step.x()) > bound || Math.abs(step.y()) > bound; }
    Projection at(PictureSelection.Between selection) {
        double weight = 1 - selection.alpha();
        var camera = cameraStep.weighted(weight);
        return new Projection(current.scroll().forMotion(camera.x(), camera.y(), current.metadata().width(), background, weight),
                new SpriteMotionProjection(current.motion(), ownerSteps, weight, camera), camera);
    }
}
