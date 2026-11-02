package com.openggf.stageplay.route;

import com.openggf.game.session.SessionManager;
import com.openggf.route.RouteSample;
import com.openggf.stageplay.StageplayDocument.EndingAction;
import com.openggf.stageplay.StageplayDocument.EndingFacing;
import com.openggf.stageplay.StageplayDocument.StartAction;
import com.openggf.stageplay.StageplayDocument.TravelSpeed;
import com.openggf.stageplay.StageplayTimeline;

import java.util.ArrayList;
import java.util.List;

/**
 * The shot obligations a Stageplay player route's cold replay must meet, read from the scene
 * host's timeline frames as the qualification replay plays the frozen rows: no reload, death,
 * damage, timeout or set change; no overshoot past the point (unless asked); no skid under a
 * gentle stop; the stroll cap under Walk; then, at the window's end, settled at the point with
 * any requested spindash and facing. It observes only; the rows are already frozen.
 */
final class StageplayShotChecks {
    private final StageplayRouteRequest request;
    private final String actorId;
    private final List<StageplayRoutePoint> path = new ArrayList<>();
    private String failure = "";
    private com.openggf.level.Level nativeLevel;
    private int nativeZone = -1;
    private int nativeAct = -1;
    private boolean charged;
    private boolean released;
    private int approach;
    private String end = "The cold live route played no rows";

    StageplayShotChecks(StageplayRouteRequest request, String actorId) {
        this.request = request;
        this.actorId = actorId;
    }

    List<StageplayRoutePoint> path() {
        return List.copyOf(path);
    }

    String failure() {
        return failure;
    }

    void observe(int row, RouteSample sample, StageplayRouteHosts.SceneQualifier scene) {
        StageplayTimeline.Frame frame = scene.frame();
        if (frame == null) return;
        int f = request.startFrame() + row;
        var pose = frame.actors().stream().filter(p -> p.actorId().equals(actorId)).findFirst().orElseThrow();
        var state = frame.puppets().stream().filter(p -> p.actorId().equals(actorId)).findFirst().orElseThrow();
        path.add(new StageplayRoutePoint(f, pose.x(), pose.y(), !state.airborne()));
        if (!failure.isEmpty()) return;
        var loaded = SessionManager.getCurrentGameplayMode().getLevelManager();
        if (nativeLevel == null) {
            nativeLevel = loaded.getCurrentLevel();
            nativeZone = loaded.getCurrentZone();
            nativeAct = loaded.getCurrentAct();
            approach = request.targetX() < pose.x() ? -1 : 1;
        } else if (loaded.getCurrentLevel() != nativeLevel || loaded.getCurrentZone() != nativeZone
                || loaded.getCurrentAct() != nativeAct) {
            failure = "The cold live route reloaded or changed its native level";
            return;
        }
        var nativeActor = frame.world() == null ? null
                : frame.world().actors().stream().filter(p -> p.actorId().equals(actorId)).findFirst().orElse(null);
        if (state.status() == StageplayTimeline.PuppetStatus.DEAD || state.status() == StageplayTimeline.PuppetStatus.TIMED_OUT
                || state.status() == StageplayTimeline.PuppetStatus.INACTIVE || nativeActor == null || nativeActor.dead()
                || nativeActor.hurt()) {
            failure = "The cold live route encountered death, damage, timeout or an inactive actor";
            return;
        }
        if (!frame.set().equals(scene.set())) {
            failure = "The cold live route changed its authored set";
            return;
        }
        if (frame.world().events().stream().anyMatch(event -> event.frame() >= request.startFrame()
                && event.actorId().equals(actorId)
                && (event.kind().equals("WAIT_TIMED_OUT") || event.kind().equals("INTENT_TIMED_OUT")))) {
            failure = "The cold live route timed out an intent";
            return;
        }
        if (request.motion().endingAction() != EndingAction.OVERSHOOT && (pose.x() - request.targetX()) * approach > 6) {
            failure = "Native motion cannot stop before the point with this start; choose Walk start or a farther destination";
            return;
        }
        var motion = scene.world().routeMotionState(actorId);
        charged |= motion.spindash();
        released |= charged && !motion.spindash() && motion.groundSpeed() != 0;
        if (request.motion().endingAction() == EndingAction.GENTLE_STOP && motion.skidding()) {
            failure = "Native terrain forced a skid; Gentle Stop cannot be qualified here";
            return;
        }
        if (request.motion().travelSpeed() == TravelSpeed.WALK && Math.max(Math.abs(motion.groundSpeed()),
                Math.abs(motion.xSpeed())) > com.openggf.sprites.playable.route.NativeRouteSteering.STROLL_CAP) {
            failure = "Walk controller exceeded the stroll cap near x=" + Math.round(pose.x()) + "; route could not be qualified";
            return;
        }
        // The host closes after its last row, so the end-of-window obligations are read here.
        if (row == request.maxFrames() - 1) end = endOfWindow(pose, state, motion);
    }

    /** The first failed obligation, or empty when the shot qualified. */
    String finish() {
        return failure.isEmpty() ? end : failure;
    }

    private String endOfWindow(StageplayTimeline.ActorPose pose, StageplayTimeline.PuppetSample state,
                               com.openggf.stageplay.runtime.StageplayLiveWorld.MotionState motion) {
        if (Math.abs(pose.x() - request.targetX()) > 6 || Math.abs(pose.y() - request.targetY()) > 12 || state.airborne()
                || Math.abs(state.velocityX()) > .25 || Math.abs(state.groundSpeed()) > .25) {
            return "Static terrain proposal did not arrive and settle in the real live world; objects, events or water may differ";
        }
        if (request.motion().startingAction() == StartAction.SPINDASH && (!charged || !released)) {
            return "Cold replay did not observe native spindash charge and release";
        }
        if (request.motion().endingFacing() != EndingFacing.INHERIT
                && motion.facingLeft() != (request.motion().endingFacing() == EndingFacing.LEFT)) {
            return "Native final facing did not hold";
        }
        return "";
    }
}
