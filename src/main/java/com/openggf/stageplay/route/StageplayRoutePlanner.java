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
        if (request.maxNodes() < 4 || request.maxTicks() < 4)
            return searchPass(document, request, probe, cancelled, deadline, false);
        var entry = probe.capture();
        boolean walk = request.motion().travelSpeed() == TravelSpeed.WALK;
        int reserveNodes = walk ? request.maxNodes()/4 : 0;
        int reserveTicks = walk ? request.maxTicks()/4 : 0;
        var direct = searchPass(document, budget(request, request.maxNodes()/3, request.maxTicks()/3,
                request.motion()), probe, cancelled, deadline, false);
        if (direct.found() || direct.status() == CANCELLED || direct.status() == UNSUPPORTED) return direct;
        if (cancelled.getAsBoolean() || System.nanoTime() >= deadline) return direct;
        probe.restore(entry);
        var detour = searchPass(document, budget(request, request.maxNodes()-direct.nodes()-reserveNodes,
                request.maxTicks()-direct.ticks()-reserveTicks, request.motion()), probe, cancelled, deadline, true);
        int nodes = direct.nodes()+detour.nodes(), ticks = direct.ticks()+detour.ticks();
        if (detour.found()) return new StageplayRouteProposal(null, detour.message(), detour.commands(),
                detour.path(), nodes, ticks);
        if (detour.status() == CANCELLED) return new StageplayRouteProposal(detour.status(), detour.message(),
                List.of(), detour.path(), nodes, ticks);
        String advice = "";
        // Diagnose speed only with a successful matched native-terrain Run search.
        // A wall, cancellation or tiny budget must never receive speculative speed advice.
        if (walk && nodes < request.maxNodes() && ticks < request.maxTicks()
                && !cancelled.getAsBoolean() && System.nanoTime() < deadline) {
            probe.restore(entry);
            var motion = new RouteMotion(request.motion().startingAction(), TravelSpeed.RUN,
                    request.motion().endingAction(), request.motion().endingFacing());
            var run = searchPass(document, budget(request, request.maxNodes()-nodes,
                    request.maxTicks()-ticks, motion), probe, cancelled, deadline, false);
            nodes += run.nodes(); ticks += run.ticks();
            if (run.found() && !direct.path().isEmpty()) {
                var stall = direct.path().getLast();
                advice = "; terrain near x=" + Math.round(stall.x())
                        + ": needs Run (matched native-terrain probe reaches and settles)";
            }
            if (run.status() == CANCELLED) return new StageplayRouteProposal(CANCELLED,
                    "Route speed diagnosis cancelled", List.of(), detour.path(), nodes, ticks);
        }
        return new StageplayRouteProposal(detour.status(), detour.message()+advice,
                List.of(), detour.path(), nodes, ticks);
    }

    private static StageplayRouteRequest budget(StageplayRouteRequest request, int nodes, int ticks, RouteMotion motion) {
        return new StageplayRouteRequest(request.actorId(), request.startFrame(), request.targetX(), request.targetY(),
                request.maxFrames(), Math.max(1,nodes), Math.max(1,ticks), request.maxMillis(), request.urgency(),
                request.footprint(), request.objectSpeed(), motion);
    }

    static StageplayRouteProposal searchPass(StageplayDocument document, StageplayRouteRequest request,
            PlayablePuppet probe, BooleanSupplier cancelled, long deadline, boolean detours) {
        var initial = probe.sample();
        if (request.motion().startingAction() == StartAction.SPINDASH &&
                !probe.supports(com.openggf.game.CharacterDefinition.MovementAbility.SPINDASH))
            return rejected(UNSUPPORTED, "This actor does not declare native spindash", 0, 0);
        int preparationTicks = 0;
        List<StageplayRoutePoint> preparation = new ArrayList<>();
        preparation.add(new StageplayRoutePoint(request.startFrame(), initial.x(), initial.y(), initial.onGround()));
        // A falling spawn lands before its starting action. Never manufacture grounding.
        while (initial.airborne()) {
            if (cancelled.getAsBoolean()) return rejected(CANCELLED,"Route preparation cancelled",0,preparationTicks);
            if (preparationTicks >= request.maxTicks() || preparationTicks >= request.maxFrames() || System.nanoTime() >= deadline)
                return new StageplayRouteProposal(BUDGET_EXCEEDED,"Starting actor did not land within the route budget",
                        List.of(),List.copyOf(preparation),0,preparationTicks);
            probe.tick(false,false,false,request.startFrame()+preparationTicks++);
            initial = probe.sample();
            preparation.add(new StageplayRoutePoint(request.startFrame()+preparationTicks-1,initial.x(),initial.y(),initial.onGround()));
        }
        int actionStart = request.startFrame()+preparationTicks;
        if (request.motion().startingAction() == StartAction.SPINDASH) {
            boolean charged = false, released = false;
            for (int elapsed=0;elapsed<=StageplayRouteSteering.CHARGE_FRAMES+1;elapsed++) {
                if (cancelled.getAsBoolean()) return rejected(CANCELLED,"Route charge cancelled",0,preparationTicks);
                if (preparationTicks >= request.maxTicks() || preparationTicks >= request.maxFrames() || System.nanoTime() >= deadline)
                    return rejected(BUDGET_EXCEEDED,"Spindash charge exceeded route budget",0,preparationTicks);
                var buttons = StageplayRouteSteering.charge(initial.x(), request.targetX(), elapsed,
                        initial.groundSpeed(), initial.airborne(), initial.crouching(), initial.spindash(),
                        initial.friction(), initial.slopeImpulse());
                probe.tick(buttons.direction()<0,buttons.direction()>0,buttons.down(),buttons.jump(),
                        request.startFrame()+preparationTicks++);
                initial=probe.sample();
                charged |= initial.spindash();
                released |= charged && !initial.spindash() && !buttons.down() && initial.groundSpeed()!=0;
                preparation.add(new StageplayRoutePoint(request.startFrame()+preparationTicks-1,initial.x(),initial.y(),initial.onGround()));
            }
            if (!charged || !released)
                return new StageplayRouteProposal(SEARCH_EXHAUSTED,
                    "Native spindash charge and release did not occur near x=" + Math.round(initial.x())
                            + "; could not settle and establish Duck within the charge window",
                    List.of(), List.copyOf(preparation), 0, preparationTicks);
        }
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
                        request.startFrame() + preparationTicks - 1,
                        request.startFrame() - 2,
                        0,
                        initial.x(),
                        0,
                        0,
                        score(initial, request, 0),
                        0);
        root.edge = List.copyOf(preparation);
        root.actionStart = actionStart;
        root.approach = request.targetX() < initial.x() ? -1 : 1;
        Node best = root;
        frontier.add(root);
        Map<Key, Integer> visited = new HashMap<>();
        int expanded = 0, ticks = preparationTicks, ordinal = 1;
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
            if (arrived(current.observation, request) && (request.motion().endingAction()!=EndingAction.OVERSHOOT || current.returning)
                    && !(current.parent==null && request.motion().startingAction()!=StartAction.WALK))
                return proposal(document, request, current, expanded, ticks);
            int nextFrame = current.frame + 1;
            if (nextFrame >= request.startFrame() + request.maxFrames()) continue;
            // A fallback chooses one run-up at the root and completes it before
            // heading to the destination. It cannot branch into arbitrary waypoint noise.
            boolean completingDetour = current.goalX != request.targetX() && current.parent != null
                    && (Math.abs(current.observation.x()-current.goalX)>6
                        || Math.abs(current.observation.groundSpeed())>12);
            double[] goals = detours && current.parent == null
                    ? new double[] {request.targetX(), initial.x()-64, initial.x()+64, initial.x()-128, initial.x()+128}
                    : new double[] {completingDetour ? current.goalX : request.targetX()};
            int[] holds = current.parent == null && request.motion().startingAction() == StartAction.JUMP
                    ? new int[] {12}
                    : current.observation.onGround() && nextFrame > current.jumpEnd + 1
                            ? new int[] {0,6,12,24} : new int[] {0};
            for (double goal : goals)
                for (int hold : holds) {
                    if (goal != request.targetX() && hold > 0) continue;
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
                    boolean returning = current.returning;
                    for (; frame < finalFrame; frame++) {
                        if (ticks >= request.maxTicks() || System.nanoTime() >= deadline)
                            return rejected(
                                    BUDGET_EXCEEDED,
                                    "Native terrain search exhausted its tick/time budget",
                                    expanded,
                                    ticks, best);
                        if (goal == request.targetX() && request.motion().endingAction() == EndingAction.OVERSHOOT)
                            returning=StageplayRouteSteering.returning(Math.floor(sample.x()),goal,current.approach,returning);
                        int direction = StageplayRouteSteering.direction(Math.floor(sample.x()),sample.xSpeed(),
                                sample.groundSpeed(),sample.runAccel(),sample.runDecel(),sample.friction(),
                                sample.maxSpeed(),sample.slopeImpulse(),sample.airborne(),goal,request.urgency(),steeringMotion(request,goal),returning,current.approach);
                        ticks++;
                        probe.tick(direction < 0, direction > 0, frame < jumpEnd, frame);
                        sample = probe.sample();
                        // Discard an unsafe launch/landing branch before it can become a
                        // proposal. Cold replay independently enforces the same Walk bound.
                        if (request.motion().travelSpeed() == TravelSpeed.WALK
                                && Math.max(Math.abs(sample.groundSpeed()),Math.abs(sample.xSpeed()))
                                    > StageplayRouteSteering.STROLL_CAP) {
                            outside = true;
                            break;
                        }
                        edge.add(new StageplayRoutePoint(frame, sample.x(), sample.y(), sample.onGround()));
                        if (sample.x() < minX || sample.x() > maxX || sample.y() < minY || sample.y() > maxY) {
                            outside = true;
                            break;
                        }
                        if (arrived(sample, request) && (request.motion().endingAction()!=EndingAction.OVERSHOOT || returning)) {
                            frame++;
                            break;
                        }
                    }
                    if (outside) continue;
                    int last = frame - 1;
                    Key key = key(sample, last < jumpEnd, jumpEnd - last, returning, goal==request.targetX()?0:(int)goal+1);
                    int elapsed = last - request.startFrame() + 1;
                    int jumpCost = current.jumpCost + (hold > 0 && request.motion().startingAction()!=StartAction.JUMP ? 2000 : 0);
                    int detourCost = current.detourCost + (current.parent==null && goal != request.targetX()
                            ? 600+(int)Math.abs(goal-initial.x()) : 0);
                    int cost = elapsed+jumpCost+detourCost;
                    Integer previous = visited.get(key);
                    if (previous != null && previous <= cost) continue;
                    visited.put(key, cost);
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
                                    score(sample, request, elapsed) + jumpCost + detourCost,
                                    ordinal++);
                    child.edge = List.copyOf(edge);
                    child.returning=returning; child.approach=current.approach;
                    child.actionStart=current.actionStart; child.jumpCost=jumpCost; child.detourCost=detourCost;
                    if (distance(sample, request) < distance(best.observation, request)) best = child;
                    // Arrival is accepted only when popped: a jump may discover the goal
                    // before the cheaper, grounded route has finished.

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
        Node root=found;
        while(root.parent!=null) root=root.parent;
        int moveStart=root.actionStart;
        if(request.motion().startingAction()==StartAction.SPINDASH) {
            commands.add(command(ids,"route.charge.",moveStart,PuppetCommandKind.SPINDASH,
                    request.targetX(),request.targetY(),request.urgency(),StageplayRouteSteering.CHARGE_FRAMES,request.motion()));
            moveStart+=StageplayRouteSteering.CHARGE_FRAMES+2;
        }
        double previousGoal = Double.NaN;
        for (Node node : chain) {
            if (node.goalX != previousGoal) {
                commands.add(command(ids,"route.move.",Double.isNaN(previousGoal)?moveStart:node.start,
                        PuppetCommandKind.MOVE_TO,node.goalX,request.targetY(),request.urgency(),0,
                        steeringMotion(request,node.goalX)));
                previousGoal = node.goalX;
            }
            if(node.hold>0) commands.add(command(ids,"route.jump.",node.start,PuppetCommandKind.JUMP,0,0,0,node.hold,request.motion()));
            path.addAll(node.edge);
        }
        if (chain.isEmpty()) commands.add(command(ids,"route.move.",moveStart,PuppetCommandKind.MOVE_TO,
                request.targetX(),request.targetY(),request.urgency(),0,request.motion()));
        int terminal=Math.max(found.frame+1,moveStart+1);
        int end=request.startFrame()+request.maxFrames();
        if(terminal>=end) return rejected(BUDGET_EXCEEDED,"Route needs more frames to settle",nodes,ticks,found);
        commands.add(command(ids,"route.stop.",terminal,PuppetCommandKind.STOP,0,0,0,0,request.motion()));
        if(request.motion().endingFacing()!=EndingFacing.INHERIT) {
            // Let the last fractional velocity coast away before the single native facing tap.
            int facingFrame=terminal+8;
            if(facingFrame>=end) return rejected(BUDGET_EXCEEDED,"Route needs more frames for its final facing tap",nodes,ticks,found);
            commands.add(command(ids,"route.face.",facingFrame,
                    request.motion().endingFacing()==EndingFacing.LEFT?PuppetCommandKind.FACE_LEFT:PuppetCommandKind.FACE_RIGHT,
                    0,0,0,0,request.motion()));
        }
        return new StageplayRouteProposal(
                null,
                "Static native-terrain hypothesis requires cold live verification",
                List.copyOf(commands),
                List.copyOf(path),
                nodes,
                ticks);
    }

    private static PuppetCommand command(Set<String> ids,String prefix,int frame,PuppetCommandKind kind,
            double x,double y,double urgency,int hold,RouteMotion motion) {
        return new PuppetCommand(StageplayRouteContract.nextId(ids,prefix),frame,kind,x,y,urgency,hold,
                ObjectTarget.none(),WaitCondition.NONE,0,motion);
    }

    private static RouteMotion steeringMotion(StageplayRouteRequest request, double goal) {
        return goal == request.targetX() ? request.motion() : new RouteMotion(request.motion().startingAction(),
                request.motion().travelSpeed(), EndingAction.HARD_STOP, EndingFacing.INHERIT);
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
        boolean charged = false, released = false;
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
                int approach=path.isEmpty()? (request.targetX()<pose.x()?-1:1)
                        : request.targetX()<path.getFirst().x()?-1:1;
                if(request.motion().endingAction()!=EndingAction.OVERSHOOT &&
                        (pose.x()-request.targetX())*approach>6)
                    return failure(VERIFICATION_FAILED,"Native motion cannot stop before the point with this start; choose Walk start or a farther destination",
                            fingerprint,request,proposal.nodes(),ticks,path);
                var motionState=world.routeMotionState(request.actorId());
                charged |= motionState.spindash();
                released |= charged && !motionState.spindash() && motionState.groundSpeed()!=0;
                if (request.motion().endingAction()==EndingAction.GENTLE_STOP && motionState.skidding())
                    return failure(VERIFICATION_FAILED,"Native terrain forced a skid; Gentle Stop cannot be qualified here",
                            fingerprint,request,proposal.nodes(),ticks,path);
                if (request.motion().travelSpeed()==TravelSpeed.WALK &&
                        Math.max(Math.abs(motionState.groundSpeed()),Math.abs(motionState.xSpeed()))>StageplayRouteSteering.STROLL_CAP)
                    return failure(VERIFICATION_FAILED,"Walk controller exceeded the stroll cap near x=" + Math.round(pose.x()) + "; route could not be qualified",
                            fingerprint,request,proposal.nodes(),ticks,path);
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
            if (request.motion().startingAction()==StartAction.SPINDASH && (!charged || !released))
                return failure(VERIFICATION_FAILED,"Cold replay did not observe native spindash charge and release",
                        fingerprint,request,proposal.nodes(),ticks,path);
            var finalMotion=world.routeMotionState(request.actorId());
            if (request.motion().endingFacing()!=EndingFacing.INHERIT && finalMotion.facingLeft()!=(request.motion().endingFacing()==EndingFacing.LEFT))
                return failure(VERIFICATION_FAILED,"Native final facing did not hold",fingerprint,request,proposal.nodes(),ticks,path);
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
                && Math.abs(sample.xSpeed()) <= 12
                && Math.abs(sample.groundSpeed()) <= 12;
    }

    private static double score(
            PlayablePuppet.Observation sample, StageplayRouteRequest request, int elapsed) {
        return elapsed
                + 2
                        * (Math.abs(request.targetX() - sample.x())
                                + 2 * Math.abs(request.targetY() - sample.y()))
                        / Math.max(1, sample.maxSpeed() / 256.0);
    }

    private static Key key(PlayablePuppet.Observation sample, boolean held, int jumpRemaining, boolean returning, int detourGoal) {
        return new Key(
                (int) Math.floor(sample.x()),
                (int) Math.floor(sample.y()),
                sample.xSpeed(),
                sample.ySpeed(),
                sample.angle(),
                sample.airborne(),
                held,
                Math.max(0, jumpRemaining),
                sample.xRadius(),
                sample.yRadius(), returning, detourGoal);
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
            int radiusY, boolean returning, int detourGoal) {}

    private static final class Node {
        final Node parent;
        boolean returning;
        int approach, actionStart, jumpCost, detourCost;
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
