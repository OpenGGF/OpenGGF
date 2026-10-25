package com.openggf.physics;

import com.openggf.game.GroundMode;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.PlayablePlacementProfile;
import java.util.Objects;

/** Read-only standing proposals through the same kernel and pair selection as native players. */
public final class NativePlayerTerrainPlacement {
    public enum Foot { LEFT, RIGHT }
    public enum Reason { NO_GEOMETRIC_SUPPORT, UNSUITABLE_ORIENTATION, OBSTRUCTED, UNSTABLE_SUPPORT, OUTSIDE_NATIVE_RANGE }
    public record Proposal(int y, int surfaceY, int angle, Foot foot, int distance) {}
    public record Result(Proposal proposal, Reason refusal, int surfaceY) {
        Result(Proposal proposal, Reason refusal) { this(proposal,refusal,proposal == null ? Integer.MIN_VALUE : proposal.surfaceY()); }
    }
    private final LevelManager level;
    private final Runnable charge;
    private final NativePlayerTerrainScan kernel = new NativePlayerTerrainScan();
    private final SensorResult left = new SensorResult(), right = new SensorResult(), ceiling = new SensorResult();

    public NativePlayerTerrainPlacement(LevelManager level, Runnable charge) {
        this.level = Objects.requireNonNull(level); this.charge = Objects.requireNonNull(charge);
    }
    public Result near(PlayablePlacementProfile profile, int x, int seedCentreY) {
        var support = pair(profile,x,seedCentreY);
        if (support.refusal() != null) return support;
        int y = seedCentreY + support.proposal().distance();
        if (y < Short.MIN_VALUE || y > Short.MAX_VALUE) return new Result(null,Reason.OUTSIDE_NATIVE_RANGE);
        return new Result(new Proposal(y,y + profile.yRadius(),support.proposal().angle(),support.proposal().foot(),0),null);
    }
    public Result exact(PlayablePlacementProfile profile, int x, int y) {
        var result = pair(profile,x,y);
        if (result.refusal() != null) return result;
        if (result.proposal().distance() != 0) return new Result(null,Reason.UNSTABLE_SUPPORT);
        // Standing ceiling clearance uses LRB solidity and native upward scans, never the object path.
        // Inspect the complete standing envelope so a buried low roof cannot sit below both head probes.
        int leftX = Math.min(profile.leftHead().x(),profile.rightHead().x());
        int rightX = Math.max(profile.leftHead().x(),profile.rightHead().x());
        int top = Math.min(profile.leftHead().y(),profile.rightHead().y());
        for (int dy = top; dy < profile.yRadius(); dy++) {
            for (int dx = leftX; dx <= rightX; dx++) {
                var head = kernel.scan(level,(short)(x+dx),(short)(y+dy),profile.lrbSolidBit(),Direction.UP,
                        false,(byte)0,false,ceiling,profile.layoutYMask(),charge);
                if (head.distance() < 0) return new Result(null,Reason.OBSTRUCTED);
            }
        }
        return result;
    }
    private Result pair(PlayablePlacementProfile profile, int x, int y) {
        var l = profile.leftFoot(); var r = profile.rightFoot();
        kernel.scan(level,(short)(x+l.x()),(short)(y+l.y()),profile.topSolidBit(),Direction.DOWN,
                false,(byte)0,false,left,profile.layoutYMask(),charge);
        boolean leftSurface = kernel.kind() == NativePlayerTerrainScan.SupportKind.GEOMETRIC_SURFACE;
        kernel.scan(level,(short)(x+r.x()),(short)(y+r.y()),profile.topSolidBit(),Direction.DOWN,
                false,(byte)0,false,right,profile.layoutYMask(),charge);
        boolean rightSurface = kernel.kind() == NativePlayerTerrainScan.SupportKind.GEOMETRIC_SURFACE;
        var selected = NativeGroundSelection.select(GroundMode.GROUND,right,left);
        boolean selectedLeft = selected == left;
        if (!(selectedLeft ? leftSurface : rightSurface)) return new Result(null,Reason.NO_GEOMETRIC_SUPPORT);
        int angle = selected.angle() & 255;
        // Odd native angle bytes request cardinal snapping (applyAngleFromSensor);
        // a stock standing entry starts at angle zero. Even angles use AnglePos's
        // asymmetric native quadrant dispatch, including its exact 0x20 boundary.
        if ((angle & 1) == 0 && CollisionSystem.anglePosQuadrant(angle) != 0) return new Result(null,Reason.UNSUITABLE_ORIENTATION,y+profile.yRadius()+selected.distance());
        return new Result(new Proposal(y,y+profile.yRadius()+selected.distance(),angle,selectedLeft?Foot.LEFT:Foot.RIGHT,selected.distance()),null);
    }
}
