package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.route.StageplayRouteResult.Status.*;

import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayResources;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Bounded native-input search followed by a fresh, strictly linear live-world qualification. */
public final class StageplayRoutePlanner {
    // Authoring-search limits, not fitted/native movement values. Retain at most 256
    // private stock-player/audio checkpoints; expanded ancestors retain metadata only.
    private static final int MAX_FRONTIER_CHECKPOINTS = 256;
    private static final int EDGE_TICKS = 12;

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
        StageplayRouteProposal proposal;
        try (var probe =
                resources.createRouteProbe(
                        StageplayRouteContract.source(document, request.startFrame()), actor, anchor, track)) {
            proposal = search(document, request, probe, cancelled, deadline);
        } catch (RuntimeException error) {
            return failure(
                    UNSUPPORTED,
                    "Cannot initialize the native terrain probe: " + error.getMessage(),
                    fingerprint,
                    request,
                    0,
                    0);
        }
        if (!proposal.found())
            return failure(
                    proposal.status(),
                    proposal.message(),
                    fingerprint,
                    request,
                    proposal.nodes(),
                    proposal.ticks(), proposal.path());
        StageplayDocument candidate;
        try {
            candidate = patch(document, request, proposal.commands());
        } catch (IllegalArgumentException error) {
            return failure(
                    UNSUPPORTED,
                    "Generated route conflicts with existing scene contracts: " + error.getMessage(),
                    fingerprint,
                    request,
                    proposal.nodes(),
                    proposal.ticks());
        }
        return verify(
                resources, library, candidate, request, fingerprint, proposal, cancelled, deadline);
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

    static StageplayRouteProposal search(
            StageplayDocument document,
            StageplayRouteRequest request,
            PlayablePuppet probe,
            BooleanSupplier cancelled,
            long deadline) {
        var initial = probe.sample();
        if (Math.abs(request.targetX() - initial.x()) > 1536
                || Math.abs(request.targetY() - initial.y()) > 768)
            return rejected(
                    UNSUPPORTED,
                    "Basic routes are bounded to 1536 horizontal and 768 vertical pixels from shot" + " entry",
                    0,
                    0);
        double minX = Math.min(initial.x(), request.targetX()) - 256,
                maxX = Math.max(initial.x(), request.targetX()) + 256;
        double minY = Math.min(initial.y(), request.targetY()) - 256,
                maxY = Math.max(initial.y(), request.targetY()) + 256;
        Comparator<Node> order =
                Comparator.comparingDouble((Node n) -> n.score).thenComparingInt(n -> n.ordinal);
        PriorityQueue<Node> frontier = new PriorityQueue<>(order);
        Node root =
                new Node(
                        null,
                        probe.capture(),
                        initial,
                        request.startFrame() - 1,
                        request.startFrame() - 2,
                        0,
                        initial.x(),
                        0,
                        0,
                        score(initial, request, 0),
                        0);
        root.edge = List.of(new StageplayRoutePoint(request.startFrame(), initial.x(), initial.y(), initial.onGround()));
        Node best = root;
        frontier.add(root);
        Map<Key, Integer> visited = new HashMap<>();
        int expanded = 0, ticks = 0, ordinal = 1;
        boolean trimmed = false;
        while (!frontier.isEmpty()) {
            if (cancelled.getAsBoolean())
                return rejected(CANCELLED, "Route search cancelled", expanded, ticks, best);
            if (System.nanoTime() >= deadline
                    || expanded >= request.maxNodes()
                    || ticks >= request.maxTicks())
                return rejected(
                        BUDGET_EXCEEDED,
                        "Native terrain search exhausted its bounded work budget; reachability is"
                                + " not disproved",
                        expanded,
                        ticks, best);
            Node current = frontier.remove();
            PlayablePuppet.Snapshot checkpoint = current.snapshot;
            current.snapshot = null;
            expanded++;
            if (arrived(current.observation, request))
                return proposal(document, request, current, expanded, ticks);
            int nextFrame = current.frame + 1;
            if (nextFrame >= request.startFrame() + request.maxFrames()) continue;
            double[] goals = {
                request.targetX(),
                current.observation.x() + 64,
                current.observation.x() - 64,
                current.observation.x() + 128,
                current.observation.x() - 128
            };
            int[] holds =
                    current.observation.onGround() && nextFrame > current.jumpEnd + 1
                            ? new int[] {0, 6, 12, 24}
                            : new int[] {0};
            for (double goal : goals)
                for (int hold : holds) {
                    if (goal < 0
                            || goal > 32767
                            || hold > request.startFrame() + request.maxFrames() - nextFrame) continue;
                    if (cancelled.getAsBoolean())
                        return rejected(CANCELLED, "Route search cancelled", expanded, ticks, best);
                    probe.restore(checkpoint);
                    int jumpEnd = hold == 0 ? current.jumpEnd : nextFrame + hold;
                    int finalFrame =
                            Math.min(nextFrame + EDGE_TICKS, request.startFrame() + request.maxFrames());
                    PlayablePuppet.Observation sample = current.observation;
                    boolean outside = false;
                    List<StageplayRoutePoint> edge = new ArrayList<>();
                    int frame = nextFrame;
                    for (; frame < finalFrame; frame++) {
                        if (ticks >= request.maxTicks() || System.nanoTime() >= deadline)
                            return rejected(
                                    BUDGET_EXCEEDED,
                                    "Native terrain search exhausted its tick/time budget",
                                    expanded,
                                    ticks, best);
                        int direction =
                                StageplayMovementIntent.direction(
                                        Math.floor(sample.x()),
                                        sample.xSpeed(),
                                        sample.runAccel(),
                                        sample.runDecel(),
                                        sample.maxSpeed(),
                                        sample.airborne(),
                                        goal,
                                        request.urgency());
                        ticks++;
                        probe.tick(direction < 0, direction > 0, frame < jumpEnd, frame);
                        sample = probe.sample();
                        edge.add(new StageplayRoutePoint(frame, sample.x(), sample.y(), sample.onGround()));
                        if (sample.x() < minX || sample.x() > maxX || sample.y() < minY || sample.y() > maxY) {
                            outside = true;
                            break;
                        }
                        if (arrived(sample, request)) {
                            frame++;
                            break;
                        }
                    }
                    if (outside) continue;
                    int last = frame - 1;
                    Key key = key(sample, last < jumpEnd, jumpEnd - last);
                    int elapsed = last - request.startFrame() + 1;
                    Integer previous = visited.get(key);
                    if (previous != null && previous <= elapsed) continue;
                    visited.put(key, elapsed);
                    Node child =
                            new Node(
                                    current,
                                    probe.capture(),
                                    sample,
                                    last,
                                    jumpEnd,
                                    hold,
                                    goal,
                                    nextFrame,
                                    elapsed,
                                    score(sample, request, elapsed),
                                    ordinal++);
                    child.edge = List.copyOf(edge);
                    if (distance(sample, request) < distance(best.observation, request)) best = child;
                    if (arrived(sample, request)) return proposal(document, request, child, expanded, ticks);
                    frontier.add(child);
                    if (frontier.size() > MAX_FRONTIER_CHECKPOINTS) {
                        Node worst = frontier.stream().max(order).orElseThrow();
                        frontier.remove(worst);
                        worst.snapshot = null;
                        trimmed = true;
                    }
                }
        }
        return rejected(
                SEARCH_EXHAUSTED,
                "No route found within the bounded terrain graph"
                        + (trimmed ? " and retained-checkpoint budget" : "")
                        + "; this does not prove the destination unreachable",
                expanded,
                ticks, best);
    }

    private static double distance(PlayablePuppet.Observation point, StageplayRouteRequest request) {
        return Math.hypot(point.x() - request.targetX(), point.y() - request.targetY());
    }

    private static List<StageplayRoutePoint> observedPath(Node end) {
        List<Node> chain = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) chain.add(node);
        java.util.Collections.reverse(chain);
        List<StageplayRoutePoint> path = new ArrayList<>();
        for (Node node : chain) path.addAll(node.edge);
        return path;
    }

    private static StageplayRouteProposal rejected(
            StageplayRouteResult.Status status, String message, int nodes, int ticks, Node best) {
        return new StageplayRouteProposal(status, message, List.of(), observedPath(best), nodes, ticks);
    }

    private static StageplayRouteProposal proposal(
            StageplayDocument document, StageplayRouteRequest request, Node found, int nodes, int ticks) {
        List<Node> chain = new ArrayList<>();
        for (Node node = found; node.parent != null; node = node.parent) chain.add(node);
        java.util.Collections.reverse(chain);
        Set<String> ids = StageplayRouteContract.identities(document);
        List<PuppetCommand> commands = new ArrayList<>();
        List<StageplayRoutePoint> path = new ArrayList<>();
        for (Node node : chain) {
            commands.add(
                    new PuppetCommand(
                            StageplayRouteContract.nextId(ids, "route.move."),
                            node.start,
                            PuppetCommandKind.MOVE_TO,
                            node.goalX,
                            request.targetY(),
                            request.urgency(),
                            0));
            if (node.hold > 0)
                commands.add(
                        new PuppetCommand(
                                StageplayRouteContract.nextId(ids, "route.jump."),
                                node.start,
                                PuppetCommandKind.JUMP,
                                0,
                                0,
                                0,
                                node.hold));
            path.add(
                    new StageplayRoutePoint(
                            node.frame, node.observation.x(), node.observation.y(), node.observation.onGround()));
        }
        int terminal = Math.min(request.startFrame() + request.maxFrames() - 1, found.frame + 1);
        if (commands.isEmpty() || terminal > commands.getLast().frame())
            commands.add(
                    new PuppetCommand(
                            StageplayRouteContract.nextId(ids, "route.stop."),
                            Math.max(request.startFrame(), terminal),
                            PuppetCommandKind.STOP,
                            0,
                            0,
                            0,
                            0));
        return new StageplayRouteProposal(
                null,
                "Static native-terrain hypothesis requires cold live verification",
                List.copyOf(commands),
                List.copyOf(path),
                nodes,
                ticks);
    }

    private static StageplayDocument patch(
            StageplayDocument document, StageplayRouteRequest request, List<PuppetCommand> replacement) {
        int end = request.startFrame() + request.maxFrames();
        return document.withPuppets(
                document.puppets().stream()
                        .map(
                                track -> {
                                    if (!track.actorId().equals(request.actorId())) return track;
                                    List<PuppetCommand> commands = new ArrayList<>();
                                    track.commands().stream()
                                            .filter(c -> c.frame() < request.startFrame())
                                            .forEach(commands::add);
                                    commands.addAll(replacement);
                                    track.commands().stream().filter(c -> c.frame() >= end).forEach(commands::add);
                                    return new PuppetTrack(
                                            track.actorId(), track.snapToFloor(), track.collisionPath(), commands);
                                })
                        .toList());
    }

    private static StageplayRouteResult verify(
            StageplayResources resources,
            SceneRomLibrary library,
            StageplayDocument candidate,
            StageplayRouteRequest request,
            String fingerprint,
            StageplayRouteProposal proposal,
            BooleanSupplier cancelled,
            long deadline) {
        List<StageplayRoutePoint> path = new ArrayList<>();
        int ticks = proposal.ticks();
        int end = request.startFrame() + request.maxFrames();
        StageplayTimeline.Frame sample = null;
        int nativeZone = -1, nativeAct = -1;
        com.openggf.level.Level nativeLevel = null;
        try (var world = StageplayLiveWorld.openOffline(library)) {
            resources.setLiveWorld(world);
            for (int frame = 0; frame < end; frame++) {
                if (cancelled.getAsBoolean())
                    return failure(
                            CANCELLED,
                            "Cold live qualification cancelled",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                if (System.nanoTime() >= deadline)
                    return failure(
                            BUDGET_EXCEEDED,
                            "Cold live qualification exceeded the route time budget",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                int cost = coldTickCost(candidate, frame);
                if (cost > request.maxTicks() - ticks)
                    return failure(
                            BUDGET_EXCEEDED,
                            "Cold qualification exhausted the combined search/preparation/replay"
                                    + " tick budget",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                sample = resources.resolveFrame(candidate, StageplayTimeline.sample(candidate, frame));
                ticks += cost;
                if (frame < request.startFrame()) continue;
                var loaded = SessionManager.getCurrentGameplayMode().getLevelManager();
                if (nativeLevel == null) {
                    nativeLevel = loaded.getCurrentLevel();
                    nativeZone = loaded.getCurrentZone();
                    nativeAct = loaded.getCurrentAct();
                } else if (loaded.getCurrentLevel() != nativeLevel
                        || loaded.getCurrentZone() != nativeZone
                        || loaded.getCurrentAct() != nativeAct) {
                    return failure(
                            VERIFICATION_FAILED,
                            "The cold live route reloaded or changed its native level",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                }
                var pose =
                        sample.actors().stream()
                                .filter(p -> p.actorId().equals(request.actorId()))
                                .findFirst()
                                .orElseThrow();
                var state =
                        sample.puppets().stream()
                                .filter(p -> p.actorId().equals(request.actorId()))
                                .findFirst()
                                .orElseThrow();
                var nativeActor =
                        sample.world() == null
                                ? null
                                : sample.world().actors().stream()
                                        .filter(p -> p.actorId().equals(request.actorId()))
                                        .findFirst()
                                        .orElse(null);
                if (state.status() == StageplayTimeline.PuppetStatus.DEAD
                        || state.status() == StageplayTimeline.PuppetStatus.TIMED_OUT
                        || state.status() == StageplayTimeline.PuppetStatus.INACTIVE
                        || nativeActor == null
                        || nativeActor.dead()
                        || nativeActor.hurt())
                    return failure(
                            VERIFICATION_FAILED,
                            "The cold live route encountered death, damage, timeout or an inactive" + " actor",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                if (!sample.set().equals(StageplayRouteContract.source(candidate, request.startFrame())))
                    return failure(
                            VERIFICATION_FAILED,
                            "The cold live route changed its authored set",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                if (sample.world().events().stream()
                        .anyMatch(
                                event ->
                                        event.frame() >= request.startFrame()
                                                && event.actorId().equals(request.actorId())
                                                && (event.kind().equals("WAIT_TIMED_OUT")
                                                        || event.kind().equals("INTENT_TIMED_OUT"))))
                    return failure(
                            VERIFICATION_FAILED,
                            "The cold live route timed out an intent",
                            fingerprint,
                            request,
                            proposal.nodes(),
                            ticks, path);
                path.add(new StageplayRoutePoint(frame, pose.x(), pose.y(), !state.airborne()));
            }
            var pose =
                    sample.actors().stream()
                            .filter(p -> p.actorId().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow();
            var state =
                    sample.puppets().stream()
                            .filter(p -> p.actorId().equals(request.actorId()))
                            .findFirst()
                            .orElseThrow();
            if (Math.abs(pose.x() - request.targetX()) > 6
                    || Math.abs(pose.y() - request.targetY()) > 12
                    || state.airborne()
                    || Math.abs(state.velocityX()) > .25
                    || Math.abs(state.groundSpeed()) > .25)
                return failure(
                        VERIFICATION_FAILED,
                        "Static terrain proposal did not arrive and settle in the real live world;"
                                + " objects, events or water may differ",
                        fingerprint,
                        request,
                        proposal.nodes(),
                        ticks, path);
            return new StageplayRouteResult(
                    VERIFIED,
                    "Reached and settled after a fresh linear native-world replay",
                    fingerprint,
                    request,
                    candidate,
                    proposal.commands(),
                    List.of(),
                    path,
                    proposal.nodes(),
                    ticks,
                    true);
        } catch (RuntimeException error) {
            return failure(
                    VERIFICATION_FAILED,
                    "Cold live qualification failed: " + error.getMessage(),
                    fingerprint,
                    request,
                    proposal.nodes(),
                    ticks, path);
        }
    }

    /** Presented world ticks plus the explicit native assembly scan at each new native shot. */
    private static int coldTickCost(StageplayDocument document, int frame) {
        boolean load = frame == 0 || document.sceneChanges().stream().anyMatch(c -> c.frame() == frame);
        return load ? 2 : 1;
    }

    private static boolean arrived(PlayablePuppet.Observation sample, StageplayRouteRequest request) {
        return sample.onGround()
                && Math.abs(sample.x() - request.targetX()) <= 6
                && Math.abs(sample.y() - request.targetY()) <= 12
                && Math.abs(sample.xSpeed()) <= 64
                && Math.abs(sample.groundSpeed()) <= 64;
    }

    private static double score(
            PlayablePuppet.Observation sample, StageplayRouteRequest request, int elapsed) {
        return elapsed
                + 2
                        * (Math.abs(request.targetX() - sample.x())
                                + 2 * Math.abs(request.targetY() - sample.y()))
                        / Math.max(1, sample.maxSpeed() / 256.0);
    }

    private static Key key(PlayablePuppet.Observation sample, boolean held, int jumpRemaining) {
        return new Key(
                (int) Math.floor(sample.x() / 4),
                (int) Math.floor(sample.y() / 4),
                sample.xSpeed() / 64,
                sample.ySpeed() / 64,
                sample.angle(),
                sample.airborne(),
                held,
                Math.max(0, jumpRemaining),
                sample.xRadius(),
                sample.yRadius());
    }

    private record Key(
            int x,
            int y,
            int vx,
            int vy,
            int angle,
            boolean air,
            boolean jump,
            int jumpRemaining,
            int radiusX,
            int radiusY) {}

    private static final class Node {
        final Node parent;
        List<StageplayRoutePoint> edge = List.of();
        PlayablePuppet.Snapshot snapshot;
        final PlayablePuppet.Observation observation;
        final int frame, jumpEnd, hold;
        final double goalX;
        final int start, elapsed;
        final double score;
        final int ordinal;

        Node(
                Node parent,
                PlayablePuppet.Snapshot snapshot,
                PlayablePuppet.Observation observation,
                int frame,
                int jumpEnd,
                int hold,
                double goalX,
                int start,
                int elapsed,
                double score,
                int ordinal) {
            this.parent = parent;
            this.snapshot = snapshot;
            this.observation = observation;
            this.frame = frame;
            this.jumpEnd = jumpEnd;
            this.hold = hold;
            this.goalX = goalX;
            this.start = start;
            this.elapsed = elapsed;
            this.score = score;
            this.ordinal = ordinal;
        }
    }

    private static StageplayRouteProposal rejected(
            StageplayRouteResult.Status status, String message, int nodes, int ticks) {
        return new StageplayRouteProposal(status, message, List.of(), List.of(), nodes, ticks);
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
