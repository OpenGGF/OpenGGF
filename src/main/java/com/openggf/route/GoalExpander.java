package com.openggf.route;

import com.openggf.sprites.playable.route.NativeRouteSteering;

import java.util.ArrayList;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Goal programs on the shared frontier: legs complete in order; every row of a leg obeys the
 * route guard and the leg's guard. From each node, every candidate family runs a bounded trial;
 * a trial that completes the leg becomes a child ending at that row, otherwise its first rows
 * become a child ranked by what the trial reached (the leg's completion, damage to a target that
 * counts hits, distance to the leg's target). Families: steer toward the leg's target with or
 * without a jump hold, hold still, and, for a defeat leg, the encounter policies moved here from
 * {@code SszBossInputAuthorTool} (September 2026 S&K campaign): approach offsets around the
 * target, arena centre and edges, and repeated jumps from the ground.
 */
final class GoalExpander implements PrefixSearch.Expander {
    private static final int HORIZON = 120;
    private static final int ENCOUNTER_HORIZON = 180;
    private static final int COMMIT = 30;
    private static final int[] HOLDS = {0, 6, 12, 24};
    private static final int[] OFFSETS = {0, -24, 24, -48, 48, 0, 0, 0, 0, 0, 0, 0};
    private static final int ENCOUNTER_POLICIES = 12;
    private static final int A = PhysicalButton.A.bit();
    private static final int LEFT = PhysicalButton.LEFT.bit();
    private static final int RIGHT = PhysicalButton.RIGHT.bit();
    private static final int DOWN = PhysicalButton.DOWN.bit();

    private final GoalProgram goals;
    private final GoalProgram.Cursor prefixCursor;

    /** Controller cursor: the current leg, where it began, and the target's hits when it began. */
    record State(int leg, int legStart, int startHits, int jumpEnd) { }

    GoalExpander(GoalProgram goals, int prefixRows) {
        if (goals.legs().isEmpty()) throw new IllegalArgumentException("an authored route needs at least one leg");
        this.goals = goals;
        this.prefixCursor = goals.cursor();
    }

    @Override public void beginPrefix(RouteSample entry) { prefixCursor.begin(entry); }

    @Override public String checkPrefixRow(int row, RouteSample sample) {
        return prefixCursor.afterRow(row,sample);
    }

    @Override public int rootProgress(RouteSample sample) {
        return prefixCursor.completedLegs()*1000;
    }

    @Override public int target() {
        return goals.legs().size();
    }

    @Override public boolean rejected(InputTape tape) {
        return false;
    }

    @Override public boolean done(PrefixSearch.Node node) {
        return node.data instanceof State state ? state.leg() >= goals.legs().size() : prefixCursor.completedLegs() >= goals.legs().size();
    }

    private State state(PrefixSearch.Node node) {
        if (node.data instanceof State state) return state;
        int leg=prefixCursor.completedLegs();
        return new State(leg, prefixCursor.legStartRow(), hits(goals.legs().get(leg), node.sample), -1);
    }

    @Override public PrefixSearch.Node expand(PrefixSearch search, PrefixSearch.Node node, TreeSet<PrefixSearch.Node> frontier) {
        State state = state(node);
        Leg leg = goals.legs().get(state.leg());
        if (node.rows - state.legStart() >= leg.maxRows()) return null;
        search.materialize(node);
        search.ensureCheckpoint(node);
        // A production control lock is an observation, not an invitation to
        // navigate. Author neutral input until it releases, preserving button
        // edges when native movie export inserts unsupported physical waits.
        if (node.sample.facts().getOrDefault("input.locked", 0) != 0) {
            PrefixSearch.Node child = trial(search, node, state, leg,
                    (t, now, previous) -> PadRow.NEUTRAL, HORIZON);
            if (child != null) search.push(frontier, child);
            return null;
        }
        // A guarded, fact-only menu observation is a bounded neutral trial, not
        // a request to navigate. Keep its complete observed hold if it succeeds;
        // menu hosts may deliberately refuse checkpoints outside gameplay.
        if (node.sample.facts().getOrDefault("input.menu",0)!=0 && leg.guard().holds().isPresent()
                && leg.goal() instanceof RouteGoal.Achieve a && factsOnly(a.condition())) {
            PrefixSearch.Node child=trial(search,node,state,leg,(t,now,previous)->PadRow.NEUTRAL,leg.maxRows());
            if(child!=null) search.push(frontier,child);
            return null;
        }
        boolean encounter = defeatTarget(leg).isPresent();
        int horizon = encounter ? ENCOUNTER_HORIZON : HORIZON;
        var families = new ArrayList<Policy>();
        for (int hold : HOLDS) families.add(steer(leg, hold, hold));
        if (players(leg).size() > 1) {
            for (int h1 : HOLDS) for (int h2 : HOLDS)
                if (h1 != h2) families.add(steer(leg, h1, h2));
        }
        families.add(charge(leg));
        families.add(rolling(leg));
        families.add((t, now, previous) -> PadRow.NEUTRAL);
        // A menu observation held under a leg invariant requests no menu advance.
        if(node.sample.facts().getOrDefault("input.menu",0)!=0 && leg.guard().holds().isEmpty())
            families.addFirst((t, now, previous) -> new PadRow(t==0?PhysicalButton.START.bit():0,0));
        if (encounter) for (int p = 0; p < ENCOUNTER_POLICIES; p++) families.add(encounter(leg, p));
        for (Policy family : families) {
            if (search.cancelled() || search.outOfSteps()) break;
            PrefixSearch.Node child = trial(search, node, state, leg, family, horizon);
            if (child != null && child.sample.epoch()!=node.sample.epoch()) {
                child.segmentRoot=true;
                search.dropCheckpoints();
                search.arrivedAt(child);
                return child;
            }
            if (child != null) search.push(frontier, child);
        }
        return null;
    }

    /** A row policy over copied observations. */
    interface Policy {
        PadRow row(int t, RouteSample now, PadRow previous);
    }

    private PrefixSearch.Node trial(PrefixSearch search, PrefixSearch.Node node, State state, Leg leg, Policy policy,
                                    int horizon) {
        search.materialize(node);
        search.dirty();
        search.trials++;
        var rows = new InputTape.Builder();
        RouteSample previous = node.sample;
        RouteSample end = node.sample;
        PadRow lastRow = node.lastRow();
        RouteSample commitSample = null;
        boolean hurt = false;
        int completedAt = -1;
        boolean boundaryLeg = leg.goal() instanceof RouteGoal.EnterBoundary;
        for (int t = 0; t < horizon; t++) {
            if(search.cancelled() || search.outOfSteps() || node.rows+t-state.legStart()>=leg.maxRows()) break;
            PadRow row = search.step(policy.row(t, search.last, lastRow));
            rows.add(row);
            lastRow = row;
            RouteSample now = search.last;
            String violation = goals.routeGuard().violation(previous, now, boundaryLeg);
            if (violation == null) violation = leg.guard().violation(previous, now, boundaryLeg);
            if (violation != null) {
                if (now.epoch() != previous.epoch()) search.rebootTo(node);
                if (t < COMMIT) return null;
                break;
            }
            end = now;
            hurt |= now.p1().hurt();
            if (achieved(leg, previous, now)) {
                completedAt = t;
                commitSample = now;
                break;
            }
            if (t == COMMIT - 1) commitSample = now;
            previous = now;
        }
        if (commitSample == null) return null;
        int keep = completedAt >= 0 ? completedAt + 1 : Math.min(COMMIT, rows.size());
        InputTape edge = rows.build().slice(0, keep);
        double value;
        State next;
        if (completedAt >= 0) {
            value = 1_000_000 - completedAt;
            int nextLeg = state.leg() + 1;
            int nextHits = nextLeg < goals.legs().size() ? hits(goals.legs().get(nextLeg), commitSample) : -1;
            next = new State(nextLeg, node.rows + keep, nextHits, -1);
        } else {
            value = score(leg, state, end, hurt);
            next = new State(state.leg(), state.legStart(), state.startHits(), -1);
        }
        long rank = (long) next.leg() * 1_000_000_000_000L + Math.round(value * 1000);
        int k = next.leg() * 1000 + (int) Math.max(0, Math.min(999, value / 1000));
        return search.node(node, edge, new PrefixSearch.Origin.Authored(keep), k, rank, 0, k, next, commitSample);
    }

    /** Whether {@code now} completes the leg (a boundary leg completes on the epoch change). */
    private static boolean achieved(Leg leg, RouteSample previous, RouteSample now) {
        return switch (leg.goal()) {
            case RouteGoal.Achieve achieve -> achieve.condition().test(now);
            case RouteGoal.EnterBoundary ignored -> now.epoch() != previous.epoch();
        };
    }

    /**
     * How far a trial got toward an unfinished leg: observed damage to a target that counts hits
     * (absence is not defeat), rings kept, damage taken, and distance to the leg's target.
     */
    private static double score(Leg leg, State state, RouteSample end, boolean hurt) {
        PlayerView p = end.p1();
        Optional<ObjectView> target = target(leg, end);
        int targetX = target.map(ObjectView::centreX).orElse(regionX(leg).orElse(p.centreX()));
        int targetY = target.map(ObjectView::centreY).orElse(regionY(leg).orElse(p.centreY()));
        var defeat = defeatTarget(leg);
        if (defeat.isPresent()) {
            var boss = defeat.get().resolve(end, PlayerSlot.P1);
            return encounterScore(p.dead(), state.startHits(), boss.map(ObjectView::hitsRemaining).orElse(null),
                    p.rings(), hurt, Math.abs(targetX - p.centreX()));
        }
        double distance = 0;
        for (PlayerSlot slot : players(leg)) {
            if(slot==PlayerSlot.P2 && end.p2().isEmpty()) { distance+=1_000_000;continue; }
            var actor = end.player(slot);
            var region = playerRegion(leg, slot);
            int x = region.map(r -> (r.left() + r.right()) / 2).orElse(targetX);
            int y = region.map(r -> (r.top() + r.bottom()) / 2).orElse(targetY);
            distance += Math.abs(x - actor.centreX()) + Math.abs(y - actor.centreY());
        }
        // Copied numeric facts can express a deliberate wait for an owner's counter.
        // This ranks bounded candidates only; the exact predicate and linear replay
        // still decide completion, and missing facts are never inferred.
        if (leg.goal() instanceof RouteGoal.Achieve a) distance += factDistance(a.condition(), end);
        return -distance - (hurt ? 100 : 0);
    }

    private static boolean factsOnly(SamplePredicate predicate) {
        return switch (predicate) {
            case SamplePredicate.FactEquals ignored -> true;
            case SamplePredicate.FactsEqual ignored -> true;
            case SamplePredicate.All all -> !all.clauses().isEmpty() && all.clauses().stream().allMatch(GoalExpander::factsOnly);
            default -> false;
        };
    }

    private static long factDistance(SamplePredicate predicate, RouteSample sample) {
        return switch (predicate) {
            case SamplePredicate.FactEquals f -> sample.facts().containsKey(f.key())
                    ? Math.abs((long) sample.facts().get(f.key()) - f.value()) : 1_000_000;
            case SamplePredicate.FactsEqual f -> sample.facts().containsKey(f.left()) && sample.facts().containsKey(f.right())
                    ? Math.abs((long) sample.facts().get(f.left()) - sample.facts().get(f.right())) : 1_000_000;
            case SamplePredicate.All all -> all.clauses().stream().mapToLong(p -> factDistance(p, sample)).sum();
            default -> 0;
        };
    }

    private Policy steer(Leg leg, int hold1, int hold2) {
        int[] jumpStart = {-1, -1};
        return (t, now, previous) -> {
            PadRow row = PadRow.NEUTRAL;
            for (PlayerSlot slot : players(leg)) {
                if(slot==PlayerSlot.P2 && now.p2().isEmpty()) continue;
                var p = now.player(slot);
                int index = slot == PlayerSlot.P1 ? 0 : 1;
                int hold = index == 0 ? hold1 : hold2;
                int tx = playerRegion(leg, slot).map(r -> (r.left() + r.right()) / 2)
                        .orElse(target(leg, now).map(ObjectView::centreX).orElse(p.centreX()));
                // Same native numeric owner used by StageplayRouteSteering.
                int direction = NativeRouteSteering.movementDirection(Math.floor(p.x()), p.xSpeed(),
                        p.motion().runAccel(), p.motion().runDecel(), Math.max(p.motion().maxSpeed(), 0x600),
                        p.air(), tx, 1);
                var region = playerRegion(leg, slot);
                if (direction == 0 && Math.abs(p.xSpeed()) < 64 && region.isPresent()) {
                    if(p.centreX() < region.get().left()) direction = 1;
                    if(p.centreX() > region.get().right()) direction = -1;
                }
                if (hold > 0 && jumpStart[index] < 0 && !p.air() && t > 0) jumpStart[index] = t;
                boolean jump = jumpStart[index] >= 0 && t - jumpStart[index] < hold;
                row = row.with(slot, (direction < 0 ? LEFT : direction > 0 ? RIGHT : 0) | (jump ? A : 0));
            }
            return row;
        };
    }

    /** Same native-state-gated charge policy as StageplayRouteSteering, followed by normal steering. */
    private Policy charge(Leg leg) {
        Policy travel=steer(leg,0,0);
        return (t,now,previous)->{
            PadRow row=travel.row(t,now,previous);
            for(var slot:players(leg)) {
                if(slot==PlayerSlot.P2 && now.p2().isEmpty()) continue;
                var p=now.player(slot);
                int tx=playerRegion(leg,slot).map(r->(r.left()+r.right())/2)
                        .orElse(target(leg,now).map(ObjectView::centreX).orElse(p.centreX()));
                var b=NativeRouteSteering.charge(p.x(),tx,t,p.groundSpeed(),p.air(),p.crouching(),
                        p.spindash(),p.motion().friction(),p.motion().slopeImpulse());
                if(!b.finished()) row=row.with(slot,(b.direction()<0?LEFT:b.direction()>0?RIGHT:0)
                        |(b.down()?DOWN:0)|(b.jump()?A:0));
            }
            return row;
        };
    }

    /** Down while moving requests the native roll gate; no footprint or speed is written. */
    private Policy rolling(Leg leg) {
        Policy travel=steer(leg,0,0);
        return (t,now,previous)->{
            PadRow row=travel.row(t,now,previous);
            for(var slot:players(leg)) {
                if(slot==PlayerSlot.P2 && now.p2().isEmpty()) continue;
                var p=now.player(slot);
                if(!p.air() && p.groundSpeed()!=0) row=row.with(slot,row.lane(slot)|DOWN);
            }
            return row;
        };
    }

    private static java.util.List<PlayerSlot> players(Leg leg) {
        var slots = new java.util.LinkedHashSet<PlayerSlot>();
        if (leg.goal() instanceof RouteGoal.Achieve a) collectPlayers(a.condition(), slots);
        if (slots.isEmpty()) slots.add(PlayerSlot.P1);
        return java.util.List.copyOf(slots);
    }

    private static void collectPlayers(SamplePredicate p, java.util.Set<PlayerSlot> slots) {
        switch (p) {
            case SamplePredicate.PlayerIn in -> slots.add(in.player());
            case SamplePredicate.Attached a -> slots.add(a.player());
            case SamplePredicate.Riding r -> slots.add(r.player());
            case SamplePredicate.Settled s -> slots.add(s.player());
            case SamplePredicate.All all -> all.clauses().forEach(c -> collectPlayers(c, slots));
            default -> { }
        }
    }

    private static Optional<Region> playerRegion(Leg leg, PlayerSlot slot) {
        return leg.goal() instanceof RouteGoal.Achieve a ? playerRegion(a.condition(), slot) : Optional.empty();
    }

    private static Optional<Region> playerRegion(SamplePredicate predicate, PlayerSlot slot) {
        return switch (predicate) {
            case SamplePredicate.PlayerIn in -> in.player() == slot ? Optional.of(in.region()) : Optional.empty();
            case SamplePredicate.All all -> all.clauses().stream().map(p -> playerRegion(p, slot))
                    .flatMap(Optional::stream).findFirst();
            default -> Optional.empty();
        };
    }

    /**
     * The boss author's encounter policies: approach offsets around the target, the arena centre
     * or its edges, a back-off, then repeated jumps from the ground; two rolling/ducking variants.
     * The arena centre is the target's position when the leg began.
     */
    private Policy encounter(Leg leg, int policy) {
        return (t, now, previous) -> {
            PlayerView p = now.p1();
            var boss = defeatTarget(leg).flatMap(target -> target.resolve(now, PlayerSlot.P1));
            int centre = boss.map(ObjectView::centreX).orElse(p.centreX());
            int target = centre;
            if (boss.isPresent() && boss.get().hitsRemaining() > 0) {
                int bx = boss.get().centreX();
                target = bx + OFFSETS[policy];
                if (policy == 6) target = bx - 136;
                if (policy == 7) target = bx + 136;
                if (policy == 8) target = bx + (p.centreX() < bx ? -136 : 136);
                if (policy == 9) target = bx + (p.centreX() < bx ? -24 : 24);
            }
            double desired = Math.max(-4, Math.min(4, (target - p.centreX()) / 8.0));
            double speed = p.xSpeed() / 256.0;
            int lane = speed > desired + 0.2 ? LEFT : speed < desired - 0.2 ? RIGHT : 0;
            boolean held = (previous.p1() & A) != 0;
            boolean jump = boss.isPresent() && boss.get().hitsRemaining() > 0 && (p.air() ? held : !held);
            if (policy == 10) {
                lane = DOWN;
                jump = !held;
            }
            if (policy == 11) {
                lane |= DOWN;
                jump = false;
            }
            return new PadRow(lane | (jump ? A : 0), 0);
        };
    }

    private static Optional<ObjectTarget> defeatTarget(Leg leg) {
        if (leg.goal() instanceof RouteGoal.Achieve achieve && achieve.condition() instanceof SamplePredicate.Defeated d) {
            return Optional.of(d.target());
        }
        return Optional.empty();
    }

    private static int hits(Leg leg, RouteSample sample) {
        return defeatTarget(leg).flatMap(t -> t.resolve(sample, PlayerSlot.P1)).map(ObjectView::hitsRemaining).orElse(-1);
    }

    /** The object a leg's goal names, resolved in {@code sample}. */
    private static Optional<ObjectView> target(Leg leg, RouteSample sample) {
        if (!(leg.goal() instanceof RouteGoal.Achieve achieve)) return Optional.empty();
        return objectTarget(achieve.condition()).flatMap(t -> t.resolve(sample, PlayerSlot.P1));
    }

    private static Optional<ObjectTarget> objectTarget(SamplePredicate predicate) {
        return switch (predicate) {
            case SamplePredicate.Riding r -> Optional.of(r.target());
            case SamplePredicate.Attached a -> Optional.of(a.target());
            case SamplePredicate.PhaseIs p -> Optional.of(p.target());
            case SamplePredicate.Defeated d -> Optional.of(d.target());
            case SamplePredicate.All all -> all.clauses().stream().map(GoalExpander::objectTarget)
                    .flatMap(Optional::stream).findFirst();
            default -> Optional.empty();
        };
    }

    private static Optional<Integer> regionX(Leg leg) {
        return region(leg).map(r -> (r.left() + r.right()) / 2);
    }

    private static Optional<Integer> regionY(Leg leg) {
        return region(leg).map(r -> (r.top() + r.bottom()) / 2);
    }

    private static Optional<Region> region(Leg leg) {
        if (!(leg.goal() instanceof RouteGoal.Achieve achieve)) return Optional.empty();
        return region(achieve.condition());
    }

    private static Optional<Region> region(SamplePredicate predicate) {
        return switch (predicate) {
            case SamplePredicate.PlayerIn in -> Optional.of(in.region());
            case SamplePredicate.All all -> all.clauses().stream().map(GoalExpander::region).flatMap(Optional::stream)
                    .findFirst();
            default -> Optional.empty();
        };
    }

    /** Scores used to rank encounter trials; exposed for the boss author's unit test. */
    static double encounterScore(boolean dead, int before, Integer after, int rings, boolean hurt, int distance) {
        if (dead) return -1_000_000;
        if (before > 0 && after == null) return -500_000; // Absence is not observed defeat.
        int damage = after == null ? 0 : Math.max(0, before - after);
        return damage * 10_000 + (after != null && after == 0 ? 50_000 : 0)
                + Math.min(3, rings) * 150 - (hurt ? 100 : 0) - Math.min(250, distance) * 0.03;
    }

}
