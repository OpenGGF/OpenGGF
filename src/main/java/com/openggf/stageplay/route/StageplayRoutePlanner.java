package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.route.StageplayRouteResult.Status.*;

import com.openggf.route.Cancellation;
import com.openggf.route.GoalProgram;
import com.openggf.route.Guard;
import com.openggf.route.InitialState;
import com.openggf.route.Inlet;
import com.openggf.route.LaunchContract;
import com.openggf.route.PlanProgress;
import com.openggf.route.PlanResult;
import com.openggf.route.PlayerSlot;
import com.openggf.route.PointGoal;
import com.openggf.route.RomIdentity;
import com.openggf.route.Roster;
import com.openggf.route.RoutePlanner;
import com.openggf.route.RouteTask;
import com.openggf.route.SearchBudget;
import com.openggf.route.Seeds;
import com.openggf.sprites.playable.route.NativeRouteSteering;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayResources;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Stageplay's route front-end. Player routes: admission, then one point-goal task for the shared
 * {@link RoutePlanner}, which searches on the private static-terrain probe (a draft tier) and
 * qualifies the frozen rows by a fresh, strictly linear replay of the candidate document, whose
 * new {@link FrozenInputTrack} drives the actor. The result installs that track; there is no
 * command re-encoding. Visual object routes stay a separate, non-controller actuator.
 */
public final class StageplayRoutePlanner {
    // Authoring choice: retain at most 256 probe checkpoints (the former frontier ceiling).
    private static final int RETAINED_CHECKPOINTS = 256;

    private StageplayRoutePlanner() {}

    public static StageplayRouteResult plan(
            StageplayResources resources,
            SceneRomLibrary library,
            StageplayDocument document,
            StageplayRouteRequest request,
            String fingerprint,
            BooleanSupplier cancelled) {
        String unavailable = StageplayRouteContract.unavailableReason(document, request);
        if (!unavailable.isEmpty())
            return failure(UNSUPPORTED, unavailable, fingerprint, request, 0, 0);
        if (SessionManager.getCurrentWorldSession() != null)
            return failure(
                    UNSUPPORTED,
                    "Route planning requires its own isolated worker; an active world cannot be"
                            + " replaced",
                    fingerprint,
                    request,
                    0,
                    0);
        var object =
                document.objectActors().stream().filter(a -> a.id().equals(request.actorId())).findFirst();
        if (object.isPresent())
            return objectRoute(resources, library, document, request, fingerprint, cancelled);
        Actor actor =
                document.actors().stream()
                        .filter(a -> a.id().equals(request.actorId()))
                        .findFirst()
                        .orElse(null);
        PuppetTrack track =
                document.puppets().stream()
                        .filter(p -> p.actorId().equals(request.actorId()))
                        .findFirst()
                        .orElse(null);
        if (actor == null || track == null)
            return failure(
                    UNSUPPORTED,
                    "Player routes require an existing physical Puppet actor",
                    fingerprint,
                    request,
                    0,
                    0);
        int end = request.startFrame() + request.maxFrames();
        if (actor.keys().stream().anyMatch(k -> k.frame() > request.startFrame() && k.frame() < end))
            return failure(
                    UNSUPPORTED,
                    "A player key changes inside the route window; shorten the window to that key",
                    fingerprint,
                    request,
                    0,
                    0);
        if (actor.keys().stream()
                .filter(k -> k.frame() >= request.startFrame() && k.frame() < end)
                .anyMatch(k -> !k.visible()))
            return failure(
                    UNSUPPORTED,
                    "The routed actor must remain visible throughout the route window",
                    fingerprint,
                    request,
                    0,
                    0);
        ActorKey anchor =
                actor.keys().stream()
                        .filter(k -> k.frame() == request.startFrame())
                        .findFirst()
                        .orElse(null);
        if (anchor == null || !anchor.visible())
            return failure(
                    UNSUPPORTED,
                    "The routed actor requires its existing visible shot-entry anchor",
                    fingerprint,
                    request,
                    0,
                    0);
        if (request.footprint() != null)
            return failure(
                    UNSUPPORTED,
                    "Player clearance comes from native collision radii, not a visual object" + " footprint",
                    fingerprint,
                    request,
                    0,
                    0);
        long deadline = System.nanoTime() + request.maxMillis() * 1_000_000L;
        boolean[] timedOut = {false};
        Cancellation cancellation = () -> {
            if (cancelled.getAsBoolean()) return true;
            if (System.nanoTime() >= deadline) timedOut[0] = true;
            return timedOut[0];
        };
        var source = StageplayRouteContract.source(document, request.startFrame());
        var task = task(document, request, actor.character());
        var shot = new StageplayShotChecks(request, actor.id());
        var scene = new StageplayRouteHosts.SceneQualifier(resources, library, document, actor.id(), request.startFrame());
        PlanResult planned;
        PlayablePuppet created;
        try {
            created = resources.createRouteProbe(source, actor, anchor, track);
        } catch (RuntimeException error) {
            return failure(
                    UNSUPPORTED,
                    "Cannot initialize the native terrain probe: " + error.getMessage(),
                    fingerprint,
                    request,
                    0,
                    0);
        }
        try (var probe = created) {
            if (request.motion().startingAction() == StartAction.SPINDASH
                    && !probe.supports(com.openggf.game.CharacterDefinition.MovementAbility.SPINDASH))
                return failure(UNSUPPORTED, "This actor does not declare native spindash", fingerprint, request, 0, 0);
            planned = new RoutePlanner(scene).withDraftTier(StageplayRouteHosts.probe(probe, request.startFrame()))
                    .plan(task, cancellation, PlanProgress.Listener.NONE, (row, sample) -> shot.observe(row, sample, scene));
        }
        return switch (planned) {
            case PlanResult.Stopped stopped -> {
                var status = switch (stopped.reason()) {
                    case CANCELLED -> timedOut[0] ? BUDGET_EXCEEDED : CANCELLED;
                    case BUDGET_EXHAUSTED -> BUDGET_EXCEEDED;
                    case UNSUPPORTED_GOAL, HOST_UNAVAILABLE -> UNSUPPORTED;
                    case QUALIFICATION_FAILED -> VERIFICATION_FAILED;
                    default -> SEARCH_EXHAUSTED;
                };
                String message = switch (stopped.reason()) {
                    case CANCELLED -> timedOut[0] ? "Route search exceeded its time budget" : "Route search cancelled";
                    case BUDGET_EXHAUSTED -> stopped.detail().startsWith("expansions")
                            ? "Native terrain search exhausted its bounded work budget; reachability is not disproved"
                            : stopped.detail();
                    case NO_PROGRESS -> stopped.detail().startsWith("every line")
                            ? "No route found within the bounded terrain graph; this does not prove the destination unreachable"
                            : stopped.detail();
                    case QUALIFICATION_FAILED -> shot.failure().isEmpty()
                            ? "Cold live qualification failed: " + stopped.detail() : shot.failure();
                    default -> stopped.detail();
                };
                yield failure(status, message, fingerprint, request, (int) stopped.work().expansions(),
                        (int) (stopped.work().searchSteps() + stopped.work().qualificationSteps()), shot.path());
            }
            case PlanResult.Solved solved -> {
                String failed = shot.finish();
                int nodes = (int) solved.work().expansions();
                int ticks = (int) (solved.work().searchSteps() + solved.work().qualificationSteps());
                if (!failed.isEmpty()) yield failure(VERIFICATION_FAILED, failed, fingerprint, request, nodes, ticks, shot.path());
                var frozen = scene.track(solved.route().tape());
                yield new StageplayRouteResult(VERIFIED, "Reached and settled after a fresh linear native-world replay of frozen rows",
                        fingerprint, request, scene.candidate(solved.route().tape()), List.of(), List.of(), shot.path(),
                        nodes, ticks, true, frozen);
            }
        };

    }


    /** The point goal a request names; Stageplay motion enums translate to native steering settings. */
    static PointGoal goal(StageplayRouteRequest request) {
        RouteMotion motion = request.motion();
        var steering = new NativeRouteSteering.Settings(
                motion.travelSpeed() == TravelSpeed.WALK ? NativeRouteSteering.TravelSpeed.WALK : NativeRouteSteering.TravelSpeed.RUN,
                switch (motion.endingAction()) {
                    case GENTLE_STOP -> NativeRouteSteering.EndingAction.GENTLE_STOP;
                    case HARD_STOP -> NativeRouteSteering.EndingAction.HARD_STOP;
                    case OVERSHOOT -> NativeRouteSteering.EndingAction.OVERSHOOT;
                });
        PointGoal.Start start = switch (motion.startingAction()) {
            case WALK -> PointGoal.Start.WALK;
            case JUMP -> PointGoal.Start.JUMP;
            case SPINDASH -> PointGoal.Start.SPINDASH;
        };
        PointGoal.Facing facing = switch (motion.endingFacing()) {
            case INHERIT -> PointGoal.Facing.INHERIT;
            case LEFT -> PointGoal.Facing.LEFT;
            case RIGHT -> PointGoal.Facing.RIGHT;
        };
        return new PointGoal(PlayerSlot.P1, request.targetX(), request.targetY(), request.maxFrames(), start, steering,
                facing, request.urgency());
    }

    /** The scene launch identity: the set's game, the routed character, the scene width and the shot entry. */
    static LaunchContract launch(StageplayDocument document, SetSource source, String character, int startFrame) {
        RomIdentity rom;
        try {
            rom = RomIdentity.standard(source.gameId());
        } catch (IllegalArgumentException unknown) {
            rom = new RomIdentity(source.gameId(), 0, "0".repeat(40));
        }
        String scene;
        try {
            scene = com.openggf.route.ContentHash.of(com.openggf.stageplay.StageplayStore.encode(document)).sha256();
        } catch (java.io.IOException impossible) {
            throw new java.io.UncheckedIOException(impossible);
        }
        return new LaunchContract(rom, new Inlet.Scene(scene, startFrame), new Roster(character, ""),
                document.width(), "off", new InitialState(0, Seeds.NONE));
    }

    /** The point-goal task for {@code request}, with count budgets taken from it. */
    static RouteTask.Point task(StageplayDocument document, StageplayRouteRequest request, String character) {
        return new RouteTask.Point(launch(document, StageplayRouteContract.source(document, request.startFrame()), character,
                request.startFrame()), goal(request), GoalProgram.of(new Guard(false, true, false, false)),
                new SearchBudget(request.maxNodes(), request.maxTicks(), RETAINED_CHECKPOINTS, 0));
    }

    /**
     * Searches on the probe and replays the rows on the same probe, for focused tests of the draft
     * tier; production qualification is the scene replay in {@link #plan}.
     */
    static PlanResult draft(StageplayDocument document, StageplayRouteRequest request, PlayablePuppet probe,
                            BooleanSupplier cancelled) {
        String character = document.actors().stream().filter(a -> a.id().equals(request.actorId())).findFirst()
                .map(Actor::character).orElse("probe");
        return new RoutePlanner(StageplayRouteHosts.probeOnly(probe, request.startFrame()))
                .plan(task(document, request, character), cancelled::getAsBoolean);
    }

    private static StageplayRouteResult objectRoute(
            StageplayResources resources,
            SceneRomLibrary library,
            StageplayDocument document,
            StageplayRouteRequest request,
            String fingerprint,
            BooleanSupplier cancelled) {
        long deadline = System.nanoTime() + request.maxMillis() * 1_000_000L;
        StageplayRouteResult proposed;
        int ticks = 0;
        try (var world = StageplayLiveWorld.openOffline(library)) {
            resources.setLiveWorld(world);
            for (int frame = 0; frame <= request.startFrame(); frame++) {
                if (cancelled.getAsBoolean())
                    return failure(
                            CANCELLED, "Object route preparation cancelled", fingerprint, request, 0, ticks);
                if (System.nanoTime() >= deadline)
                    return failure(
                            BUDGET_EXCEEDED,
                            "Object route native preparation exceeded the time budget",
                            fingerprint,
                            request,
                            0,
                            ticks);
                int cost = coldTickCost(document, frame);
                if (cost > request.maxTicks() - ticks)
                    return failure(
                            BUDGET_EXCEEDED,
                            "Native object-route preparation exhausted its tick budget",
                            fingerprint,
                            request,
                            0,
                            ticks);
                resources.resolveFrame(document, StageplayTimeline.sample(document, frame));
                ticks += cost;
            }
            var level = SessionManager.getCurrentGameplayMode().getLevelManager();
            if (ticks >= request.maxTicks())
                return failure(
                        BUDGET_EXCEEDED,
                        "Native preparation exhausted the route tick budget",
                        fingerprint,
                        request,
                        0,
                        ticks);
            var remaining =
                    new StageplayRouteRequest(
                            request.actorId(),
                            request.startFrame(),
                            request.targetX(),
                            request.targetY(),
                            request.maxFrames(),
                            request.maxNodes(),
                            request.maxTicks() - ticks,
                            request.maxMillis(),
                            request.urgency(),
                            request.footprint(),
                            request.objectSpeed());
            proposed =
                    StageplayObjectRoutePlanner.plan(
                            document,
                            remaining,
                            fingerprint,
                            resources.routeTerrainProbe(level),
                            () -> cancelled.getAsBoolean() || System.nanoTime() >= deadline);
        } catch (RuntimeException error) {
            return failure(
                    UNSUPPORTED,
                    "Cannot inspect the native object-route collision context: " + error.getMessage(),
                    fingerprint,
                    request,
                    0,
                    ticks);
        }
        ticks += proposed.simulatedTicks();
        if (ticks > request.maxTicks())
            return failure(
                    BUDGET_EXCEEDED,
                    "Object planning and native preparation exhausted the total tick budget",
                    fingerprint,
                    request,
                    proposed.expandedNodes(),
                    ticks);
        if (!proposed.success())
            return failure(
                    !cancelled.getAsBoolean() && System.nanoTime() >= deadline
                            ? BUDGET_EXCEEDED
                            : proposed.status(),
                    proposed.message(),
                    fingerprint,
                    request,
                    proposed.expandedNodes(),
                    ticks, proposed.path());
        List<StageplayRoutePoint> path = new ArrayList<>();
        com.openggf.level.Level initialLevel = null;
        int nativeZone = -1, nativeAct = -1;
        try (var world = StageplayLiveWorld.openOffline(library)) {
            resources.setLiveWorld(world);
            int end = request.startFrame() + request.maxFrames();
            for (int frame = 0; frame < end; frame++) {
                if (cancelled.getAsBoolean())
                    return failure(
                            CANCELLED,
                            "Visual route qualification cancelled",
                            fingerprint,
                            request,
                            proposed.expandedNodes(),
                            ticks, path);
                if (System.nanoTime() >= deadline)
                    return failure(
                            BUDGET_EXCEEDED,
                            "Visual route qualification exceeded its time budget",
                            fingerprint,
                            request,
                            proposed.expandedNodes(),
                            ticks, path);
                int cost = coldTickCost(proposed.candidate(), frame);
                if (cost > request.maxTicks() - ticks)
                    return failure(
                            BUDGET_EXCEEDED,
                            "Visual route qualification exhausted its total tick budget",
                            fingerprint,
                            request,
                            proposed.expandedNodes(),
                            ticks, path);
                resources.resolveFrame(
                        proposed.candidate(), StageplayTimeline.sample(proposed.candidate(), frame));
                ticks += cost;
                if (frame < request.startFrame()) continue;
                var level = SessionManager.getCurrentGameplayMode().getLevelManager();
                if (initialLevel == null) {
                    initialLevel = level.getCurrentLevel();
                    nativeZone = level.getCurrentZone();
                    nativeAct = level.getCurrentAct();
                } else if (initialLevel != level.getCurrentLevel()
                        || nativeZone != level.getCurrentZone()
                        || nativeAct != level.getCurrentAct())
                    return failure(
                            VERIFICATION_FAILED,
                            "The visual route's native level reloaded or changed",
                            fingerprint,
                            request,
                            proposed.expandedNodes(),
                            ticks, path);
                var position = world.routeObjectPosition(request.actorId());
                if (position == null)
                    return failure(
                            VERIFICATION_FAILED,
                            "The routed object is no longer a visible Puppet",
                            fingerprint,
                            request,
                            proposed.expandedNodes(),
                            ticks, path);
                String invalid =
                        StageplayObjectRoutePlanner.validateSample(
                                resources.routeTerrainProbe(level),
                                request.footprint(),
                                position.x(),
                                position.y());
                if (!invalid.isEmpty())
                    return failure(
                            VERIFICATION_FAILED,
                            "Live visual terrain qualification failed: " + invalid,
                            fingerprint,
                            request,
                            proposed.expandedNodes(),
                            ticks, path);
                path.add(position);
            }
            var finalPoint = path.getLast();
            if (Math.abs(finalPoint.x() - request.targetX()) > 1
                    || Math.abs(finalPoint.y() - request.targetY()) > 1)
                return failure(
                        VERIFICATION_FAILED,
                        "The live visual object route did not reach its destination",
                        fingerprint,
                        request,
                        proposed.expandedNodes(),
                        ticks, path);
            return new StageplayRouteResult(
                    VISUAL_TERRAIN,
                    "Visual slopes, steps up to 8 pixels and floor/head/side clearance checked"
                            + " at every tick in a fresh live replay; no gaps or detours;"
                            + " native object AI remains suspended",
                    fingerprint,
                    request,
                    proposed.candidate(),
                    List.of(),
                    proposed.objectCommands(),
                    path,
                    proposed.expandedNodes(),
                    ticks,
                    false);
        } catch (RuntimeException error) {
            return failure(
                    VERIFICATION_FAILED,
                    "Live visual route qualification failed: " + error.getMessage(),
                    fingerprint,
                    request,
                    proposed.expandedNodes(),
                    ticks, path);
        }
    }

    /** Presented world ticks plus the explicit native assembly scan at each new native shot. */
    private static int coldTickCost(StageplayDocument document, int frame) {
        boolean load = frame == 0 || document.sceneChanges().stream().anyMatch(c -> c.frame() == frame);
        return load ? 2 : 1;
    }

    private static StageplayRouteResult failure(
            StageplayRouteResult.Status status, String message, String fingerprint,
            StageplayRouteRequest request, int nodes, int ticks, List<StageplayRoutePoint> path) {
        return StageplayRouteResult.failure(status, message, fingerprint, request, nodes, ticks, path);
    }

    private static StageplayRouteResult failure(
            StageplayRouteResult.Status status,
            String message,
            String fingerprint,
            StageplayRouteRequest request,
            int nodes,
            int ticks) {
        return StageplayRouteResult.failure(status, message, fingerprint, request, nodes, ticks);
    }
}
