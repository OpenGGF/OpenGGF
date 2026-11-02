package com.openggf.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * The one route search: a deterministic, count-budgeted best-first search over row prefixes.
 *
 * <p>A node owns its parent, an immutable edge of exactly the rows the host accepted, its
 * progress and its observation. A few nodes also hold a host checkpoint; any other node is
 * reconstructed by restoring its nearest checkpointed ancestor and replaying the recorded edge
 * rows, never by re-running the policy that chose them. A boundary inside a trial contaminates
 * the host, which is closed and rebuilt from the cold launch.
 *
 * <p>What progress means, which edges a node may take and when the task is done belong to an
 * {@link Expander}: reference following (verbatim source rows while P1 tracks, candidate trials
 * where it drifts) or a point goal (steering edges ranked by elapsed rows plus a distance
 * heuristic). Both share this frontier, row journal, checkpoint envelope and budget accounting.
 *
 * <p>Ranks are fixed-point integers with insertion-ordinal ties. A merge of equal states happens
 * only on a key the host certifies ({@link RouteWorld#equivalenceKey()}); live worlds certify none.
 * The result is a draft until {@link RoutePlanner} cold-replays it.
 */
final class PrefixSearch {
    private static final int FRONTIER_LIMIT = 512;

    /** Expansion policy over the shared machinery. */
    interface Expander {
        /** Checks one immutable prefix row; a reason stops the plan as {@link StopReason#PREFIX_DIVERGED}. */
        String checkPrefixRow(int row, RouteSample sample);

        /** Progress at the root, after the immutable prefix. */
        int rootProgress(RouteSample sample);

        /** Expands {@code node}: returns a child to continue from directly, or null after pushing children. */
        Node expand(PrefixSearch search, Node node, TreeSet<Node> frontier);

        boolean done(Node node);

        /** Whether a finished tape is already known to fail (a required edit's rejected hashes). */
        boolean rejected(InputTape tape);

        /** The progress value that completes the task, for progress reports. */
        int target();

        /** Completes a done node (for example with a settle tail); null stops with {@link #failure()}. */
        default Node finish(PrefixSearch search, Node done) {
            return done;
        }

        /** Why the search could not continue, when the expander knows better than "no progress". */
        default StopReason failureReason() {
            return StopReason.NO_PROGRESS;
        }

        default String failure() {
            return "";
        }
    }

    private final RouteWorldFactory hosts;
    private final LaunchContract launch;
    private final InputTape immutable;
    private final Expander expander;
    private final Cancellation cancel;
    private final SearchBudget budget;
    private final PlanProgress.Listener listener;

    long expansions;
    long steps;
    long trials;
    long captures;
    long restores;
    long freshBoots;

    private RouteWorld world;
    private Node atNode;
    RouteSample last;
    private long ordinals;
    private final Deque<Node> retained = new ArrayDeque<>();
    private final Map<Object, Long> visited = new HashMap<>();

    PrefixSearch(RouteWorldFactory hosts, LaunchContract launch, InputTape immutable, Expander expander,
                 SearchBudget budget, Cancellation cancel, PlanProgress.Listener listener) {
        this.hosts = hosts;
        this.launch = launch;
        this.immutable = immutable;
        this.expander = expander;
        this.budget = budget;
        this.cancel = cancel;
        this.listener = listener;
    }

    /** Where an edge's rows came from. */
    sealed interface Origin {
        record Prefix(int length) implements Origin { }
        record Source(int from, int length) implements Origin { }
        record Authored(int length) implements Origin { }
    }

    static final class Node {
        final long ordinal;
        final Node parent;
        final InputTape edge;
        final Origin origin;
        final int k;
        final long rank;
        final int rows;
        final int stuck;
        final int predicted;
        final int authored;
        final int windows;
        /** Expander-owned immutable state (for example a point goal's controller cursor). */
        final Object data;
        RouteSample sample;
        WorldCheckpoint checkpoint;
        boolean segmentRoot;
        boolean rejected;

        Node(long ordinal, Node parent, InputTape edge, Origin origin, int k, long rank, int stuck, int predicted,
             Object data) {
            this.ordinal = ordinal;
            this.parent = parent;
            this.edge = edge;
            this.origin = origin;
            this.k = k;
            this.rank = rank;
            this.rows = (parent == null ? 0 : parent.rows) + edge.size();
            this.stuck = stuck;
            this.predicted = predicted;
            this.data = data;
            int parentAuthored = parent == null ? 0 : parent.authored;
            int parentWindows = parent == null ? 0 : parent.windows;
            boolean opensWindow = origin instanceof Origin.Authored
                    && (parent == null || !(parent.origin instanceof Origin.Authored));
            this.authored = parentAuthored + (origin instanceof Origin.Authored ? edge.size() : 0);
            this.windows = parentWindows + (opensWindow ? 1 : 0);
        }

        PadRow lastRow() {
            for (Node n = this; n != null; n = n.parent) {
                if (n.edge.size() > 0) return n.edge.row(n.edge.size() - 1);
            }
            return PadRow.NEUTRAL;
        }
    }

    /** The search's draft: rows, their origins and the final observation, or a stop. */
    record Draft(Node end, StopReason stop, String detail, Node best) { }

    Draft run() {
        try {
            boot();
            var rows = new InputTape.Builder();
            for (int i = 0; i < immutable.size(); i++) {
                step(immutable.row(i));
                rows.add(immutable.row(i));
                String diverged = expander.checkPrefixRow(i, last);
                if (diverged != null) return new Draft(null, StopReason.PREFIX_DIVERGED, diverged, null);
            }
            int k = expander.rootProgress(last);
            Node root = new Node(ordinals++, null, rows.build(), new Origin.Prefix(immutable.size()), k, 0, 0, k, null);
            root.sample = last;
            root.segmentRoot = true;
            atNode = root;
            Node best = root;
            Node current = root;
            var frontier = new TreeSet<Node>(Comparator.comparingLong((Node n) -> -n.rank).thenComparingLong(n -> n.ordinal));
            while (true) {
                if (cancel.cancelled()) return new Draft(null, StopReason.CANCELLED, "cancelled", best);
                if (expander.done(current)) {
                    Node finished = expander.finish(this, current);
                    if (finished == null) return new Draft(null, expander.failureReason(), expander.failure(), best);
                    if (!expander.rejected(tape(finished))) return new Draft(finished, null, null, best);
                    current.rejected = true;
                    current = nextFromFrontier(frontier, Integer.MAX_VALUE);
                    if (current == null) {
                        return new Draft(null, StopReason.ALL_CANDIDATES_REJECTED, "every solution found was rejected", best);
                    }
                    continue;
                }
                if (expansions >= budget.expansions() || steps >= budget.steps() || freshBoots > budget.freshBoots()) {
                    return new Draft(null, StopReason.BUDGET_EXHAUSTED, "expansions " + expansions + ", steps " + steps
                            + ", fresh boots " + freshBoots, best);
                }
                expansions++;
                Node next = expander.expand(this, current, frontier);
                if (next != null && next.segmentRoot) frontier.clear();
                if (next == null) next = nextFromFrontier(frontier, Integer.MAX_VALUE);
                if (next == null) {
                    return expander.failure().isEmpty()
                            ? new Draft(null, StopReason.NO_PROGRESS, "every line stalled at progress " + best.k, best)
                            : new Draft(null, expander.failureReason(), expander.failure(), best);
                }
                current = next;
                if (current.k > best.k) best = current;
                listener.report(new PlanProgress("search", expansions, steps, freshBoots, current.rows, best.rows,
                        best.k, expander.target()));
            }
        } catch (RouteHostException refused) {
            return new Draft(null, StopReason.HOST_UNAVAILABLE, refused.getMessage(), null);
        } finally {
            closeWorld();
        }
    }

    /** The best frontier node that is not a stalled line. */
    Node nextFromFrontier(TreeSet<Node> frontier, int stuckLimit) {
        Node next;
        do {
            next = frontier.pollFirst();
        } while (next != null && next.stuck >= stuckLimit);
        return next;
    }

    /** Adds a child to the frontier, bounded; the worst node drops out. */
    void push(TreeSet<Node> frontier, Node child) {
        frontier.add(child);
        if (frontier.size() > FRONTIER_LIMIT) frontier.pollLast();
    }

    Node node(Node parent, InputTape edge, Origin origin, int k, long rank, int stuck, int predicted, Object data,
              RouteSample sample) {
        Node child = new Node(ordinals++, parent, edge, origin, k, rank, stuck, predicted, data);
        child.sample = sample;
        return child;
    }

    /**
     * Whether a host-certified equivalence key was already reached at an equal or lower cost; if
     * not, records it. Hosts that certify no key never merge.
     */
    boolean visitedCheaper(Object controllerState, long cost) {
        var key = world.equivalenceKey();
        if (key.isEmpty()) return false;
        Object full = java.util.List.of(key.get(), controllerState);
        Long previous = visited.get(full);
        if (previous != null && previous <= cost) return true;
        visited.put(full, cost);
        return false;
    }

    boolean cancelled() {
        return cancel.cancelled();
    }

    boolean outOfSteps() {
        return steps >= budget.steps();
    }

    /** Puts the world in {@code target}'s state: restore the nearest checkpointed ancestor, replay edge rows. */
    void materialize(Node target) {
        if (atNode == target) return;
        var chain = new ArrayList<Node>();
        Node anchor = target;
        while (anchor != null && anchor.checkpoint == null) {
            chain.add(anchor);
            anchor = anchor.parent;
        }
        if (anchor == null) {
            rebootTo(target);
            return;
        }
        world.restore(anchor.checkpoint);
        restores++;
        last = world.observe();
        requireReproduced(anchor);
        for (int i = chain.size() - 1; i >= 0; i--) {
            Node n = chain.get(i);
            for (int r = 0; r < n.edge.size(); r++) step(n.edge.row(r));
            requireReproduced(n);
        }
        atNode = target;
    }

    /** The world now holds {@code node}'s state (after stepping its edge directly). */
    void arrivedAt(Node node) {
        atNode = node;
    }

    /** The world no longer matches any node. */
    void dirty() {
        atNode = null;
    }

    /** A replayed edge must land exactly where it first did; otherwise a checkpoint is incomplete. */
    private void requireReproduced(Node n) {
        if (n.sample != null && !n.sample.equals(last)) {
            throw new IllegalStateException("restoring or replaying recorded rows did not reproduce node " + n.ordinal
                    + ": the host checkpoint is incomplete\n  first: " + n.sample + "\n  again: " + last);
        }
    }

    void ensureCheckpoint(Node node) {
        if (node.checkpoint != null) return;
        try {
            node.checkpoint = world.capture();
            captures++;
        } catch (RouteHostException refused) {
            if (refused.reason() != RouteHostException.Reason.CHECKPOINT_REFUSED) throw refused;
            return;
        }
        retained.addLast(node);
        while (retained.size() > budget.retainedCheckpoints()) {
            Node evict = null;
            for (Node candidate : retained) {
                if (!candidate.segmentRoot) {
                    evict = candidate;
                    break;
                }
            }
            if (evict == null) evict = retained.peekFirst();
            retained.remove(evict);
            evict.checkpoint = null;
        }
    }

    void dropCheckpoints() {
        for (Node node : retained) node.checkpoint = null;
        retained.clear();
    }

    /** Closes a contaminated host, boots a fresh one and replays every recorded row to {@code target}. */
    void rebootTo(Node target) {
        dropCheckpoints();
        closeWorld();
        boot();
        freshBoots++;
        var chain = new ArrayList<Node>();
        for (Node n = target; n != null; n = n.parent) chain.add(n);
        for (int i = chain.size() - 1; i >= 0; i--) {
            Node n = chain.get(i);
            for (int r = 0; r < n.edge.size(); r++) step(n.edge.row(r));
            requireReproduced(n);
        }
        atNode = target;
        ensureCheckpoint(target);
    }

    private void boot() {
        world = hosts.openSearch(launch);
        last = world.observe();
        atNode = null;
    }

    /** Offers one row; the journal records the row the host accepted. */
    PadRow step(PadRow row) {
        RecordedStep recorded = world.step(row);
        if (!recorded.accepted().equals(row)) {
            throw new IllegalStateException("host accepted " + recorded.accepted() + " for offered " + row
                    + "; a host may not remap rows silently");
        }
        last = recorded.after();
        steps++;
        return recorded.accepted();
    }

    private void closeWorld() {
        if (world != null) {
            world.close();
            world = null;
        }
    }

    /** The full output tape along {@code end}'s path. */
    static InputTape tape(Node end) {
        var path = new ArrayList<Node>();
        for (Node n = end; n != null; n = n.parent) path.add(n);
        var builder = new InputTape.Builder();
        for (int i = path.size() - 1; i >= 0; i--) builder.addAll(path.get(i).edge);
        return builder.build();
    }

    /** Output spans along {@code end}'s path; adjacent runs of one origin merge. */
    static RepairReport report(Node end, int originalRows, boolean sourceIsOriginal) {
        var path = new ArrayList<Node>();
        for (Node n = end; n != null; n = n.parent) path.add(n);
        var spans = new ArrayList<RepairReport.Span>();
        int out = 0;
        for (int i = path.size() - 1; i >= 0; i--) {
            Node n = path.get(i);
            RepairReport.Span span = switch (n.origin) {
                case Origin.Prefix p -> p.length() == 0 ? null : new RepairReport.Span.Original(0, out, p.length());
                case Origin.Source s -> sourceIsOriginal ? new RepairReport.Span.Original(s.from(), out, s.length())
                        : new RepairReport.Span.Reference(s.from(), out, s.length());
                case Origin.Authored a -> a.length() == 0 ? null : new RepairReport.Span.Authored(out, a.length());
            };
            out += n.edge.size();
            if (span == null) continue;
            if (!spans.isEmpty()) {
                RepairReport.Span merged = merge(spans.getLast(), span);
                if (merged != null) {
                    spans.set(spans.size() - 1, merged);
                    continue;
                }
            }
            spans.add(span);
        }
        return new RepairReport(originalRows, out, spans);
    }

    private static RepairReport.Span merge(RepairReport.Span a, RepairReport.Span b) {
        if (a instanceof RepairReport.Span.Original x && b instanceof RepairReport.Span.Original y
                && x.sourceStart() + x.length() == y.sourceStart() && x.outputStart() + x.length() == y.outputStart()) {
            return new RepairReport.Span.Original(x.sourceStart(), x.outputStart(), x.length() + y.length());
        }
        if (a instanceof RepairReport.Span.Reference x && b instanceof RepairReport.Span.Reference y
                && x.sourceStart() + x.length() == y.sourceStart() && x.outputStart() + x.length() == y.outputStart()) {
            return new RepairReport.Span.Reference(x.sourceStart(), x.outputStart(), x.length() + y.length());
        }
        if (a instanceof RepairReport.Span.Authored x && b instanceof RepairReport.Span.Authored y
                && x.outputStart() + x.length() == y.outputStart()) {
            return new RepairReport.Span.Authored(x.outputStart(), x.length() + y.length());
        }
        return null;
    }

    static int rootProgress(Node end) {
        Node n = end;
        while (n.parent != null) n = n.parent;
        return n.k;
    }

    WorkReport work(long qualificationSteps) {
        return new WorkReport(expansions, steps, trials, captures, restores, freshBoots, qualificationSteps);
    }
}
