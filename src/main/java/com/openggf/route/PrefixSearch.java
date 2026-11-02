package com.openggf.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The one route search: a deterministic, count-budgeted best-first search over row prefixes.
 *
 * <p>A node owns its parent, an immutable edge of exactly the rows the host accepted, its
 * reference progress and its observation. A few nodes also hold a host checkpoint; any other node
 * is reconstructed by restoring its nearest checkpointed ancestor and replaying the recorded edge
 * rows, never by re-running the policy that chose them. Repair is a restriction on the same
 * search: source rows are mandatory edges while P1 tracks the reference, and only a drift opens
 * an authored window. A boundary inside a trial contaminates the host, which is closed and rebuilt
 * from the cold launch; a boundary the reference itself crosses starts a new segment.
 *
 * <p>Ranks are fixed-point integers with insertion-ordinal ties; there is no state-equivalence
 * merge. Budgets are counts. The result is a draft until {@link RoutePlanner} cold-replays it.
 */
final class PrefixSearch {
    private static final int FRONTIER_LIMIT = 512;

    private final RouteWorldFactory hosts;
    private final RouteTask.Repair task;
    private final Cancellation cancel;
    private final AuthorHints hints;
    private final SearchBudget budget;
    private final ReferencePath ref;
    private final ReferenceProgress progress;
    private final InputTape original;
    private final boolean sourceIsOriginal;
    private final Optional<RepairTrigger.RequiredEdit> required;

    long expansions;
    long steps;
    long trials;
    long captures;
    long restores;
    long freshBoots;

    private RouteWorld world;
    private Node atNode;
    private RouteSample last;
    private long ordinals;
    private final Deque<Node> retained = new ArrayDeque<>();

    private final PlanProgress.Listener listener;

    PrefixSearch(RouteWorldFactory hosts, RouteTask.Repair task, Cancellation cancel, PlanProgress.Listener listener) {
        this.hosts = hosts;
        this.listener = listener;
        this.task = task;
        this.cancel = cancel;
        this.hints = task.hints();
        this.budget = task.budget();
        this.ref = task.reference();
        this.progress = new ReferenceProgress(ref);
        this.original = task.original().tape();
        this.sourceIsOriginal = ref.source().size() <= original.size()
                && original.slice(0, ref.source().size()).equals(ref.source());
        this.required = task.scope().trigger() instanceof RepairTrigger.RequiredEdit edit ? Optional.of(edit) : Optional.empty();
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
        RouteSample sample;
        WorldCheckpoint checkpoint;
        boolean segmentRoot;
        boolean rejected;

        Node(long ordinal, Node parent, InputTape edge, Origin origin, int k, long rank, int stuck, int predicted) {
            this.ordinal = ordinal;
            this.parent = parent;
            this.edge = edge;
            this.origin = origin;
            this.k = k;
            this.rank = rank;
            this.rows = (parent == null ? 0 : parent.rows) + edge.size();
            this.stuck = stuck;
            this.predicted = predicted;
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
            Node root = root();
            if (root == null) return lastStop;
            Node best = root;
            Node current = root;
            var frontier = new TreeSet<Node>(Comparator.comparingLong((Node n) -> -n.rank).thenComparingLong(n -> n.ordinal));
            while (true) {
                if (cancel.cancelled()) return new Draft(null, StopReason.CANCELLED, "cancelled", best);
                if (current.k >= ref.to()) {
                    if (!rejected(current)) return new Draft(current, null, null, best);
                    current.rejected = true;
                    current = nextFromFrontier(frontier);
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
                Node next = expand(current, frontier);
                if (next != null && next.segmentRoot) frontier.clear();
                if (next == null) next = nextFromFrontier(frontier);
                if (next == null) {
                    return new Draft(null, StopReason.NO_PROGRESS, "every line stalled at progress " + best.k, best);
                }
                current = next;
                if (current.k > best.k) best = current;
                listener.report(new PlanProgress("search", expansions, steps, freshBoots, current.rows, best.rows,
                        best.k, ref.to()));
            }
        } catch (RouteHostException refused) {
            return new Draft(null, StopReason.HOST_UNAVAILABLE, refused.getMessage(), null);
        } finally {
            closeWorld();
        }
    }

    private Draft lastStop;

    private Node nextFromFrontier(TreeSet<Node> frontier) {
        Node next;
        do {
            next = frontier.pollFirst();
        } while (next != null && next.stuck >= hints.stuckLimit);
        return next;
    }

    private boolean rejected(Node end) {
        return required.isPresent() && required.get().rejected().contains(tape(end).hash());
    }

    /** Plays the immutable rows, checks them when they are the reference's own rows, and roots the search. */
    private Node root() {
        boot();
        int prefix = task.scope().firstEditable();
        var rows = new InputTape.Builder();
        for (int i = 0; i < prefix; i++) {
            step(original.row(i));
            rows.add(original.row(i));
            if (sourceIsOriginal && i < ref.size() && progress.distance(last, i) > hints.syncTolerance) {
                lastStop = new Draft(null, StopReason.PREFIX_DIVERGED, "immutable row " + i + " is "
                        + (int) progress.distance(last, i) + " px off its recorded path", null);
                return null;
            }
            if (last.p1().dead() && i < prefix - 1) {
                lastStop = new Draft(null, StopReason.PREFIX_DIVERGED, "P1 died in the immutable rows at " + i, null);
                return null;
            }
        }
        int k;
        if (sourceIsOriginal) {
            k = prefix - 1;
        } else {
            k = progress.strict(last, ref.from() - 60, ref.from() + hints.look, hints.radius);
            if (k < 0) k = ref.from();
        }
        Node root = new Node(ordinals++, null, rows.build(), new Origin.Prefix(prefix), k, 0, 0, k);
        root.sample = last;
        root.segmentRoot = true;
        atNode = root;
        return root;
    }

    /**
     * Fast-follows source rows while P1 tracks; on a drift, scores every candidate family from the
     * node and pushes each surviving trial's first rows as a child. Returns a tracked child to
     * continue from, or null to take the frontier's best.
     */
    private Node expand(Node node, TreeSet<Node> frontier) {
        materialize(node);
        ensureCheckpoint(node);
        int base = node.origin instanceof Origin.Authored ? rejoinIndex(node) : node.k;
        var rows = new InputTape.Builder();
        int n = 0;
        boolean tracked = base >= -1;
        boolean crossed = false;
        boolean contaminated = false;
        for (; tracked && n < hints.commit && base + 1 + n < ref.size(); n++) {
            int src = base + 1 + n;
            if (required.isPresent() && src >= required.get().sourceFrom() && !passedRequired(node)) {
                tracked = false;
                break;
            }
            long epochBefore = last.epoch();
            PadRow row = ref.sourceRow(src);
            step(row);
            rows.add(row);
            if (last.epoch() != epochBefore) {
                if (!ref.crossesBoundary(src)) {
                    tracked = false;
                    contaminated = true;
                    break;
                }
                crossed = true;
            }
            if (last.p1().dead() || last.p1().hurt() || progress.distance(last, src) > hints.syncTolerance) {
                tracked = false;
                break;
            }
        }
        if (tracked && n > 0) {
            int k = base + n;
            Node child = new Node(ordinals++, node, rows.build(), new Origin.Source(base + 1, n), k,
                    rank(k), 0, Math.max(node.predicted, k));
            child.sample = last;
            child.segmentRoot = crossed;
            if (crossed) dropCheckpoints();
            atNode = child;
            return child;
        }
        atNode = null;
        if (contaminated) rebootTo(node);
        if (node.authored >= task.scope().maxAuthoredRows()) return null;
        boolean opensWindow = !(node.origin instanceof Origin.Authored);
        if (opensWindow && node.windows >= task.scope().maxWindows()) return null;
        boolean wide = node.stuck >= hints.stuckLimit / 2;
        int horizon = wide ? hints.horizon * 2 : hints.horizon;
        int commit = wide ? hints.commit * 2 : hints.commit;
        List<Supplier<ReferenceCandidates.RowPolicy>> families = ReferenceCandidates.families(progress, node.k, wide);
        for (Supplier<ReferenceCandidates.RowPolicy> family : families) {
            if (cancel.cancelled() || steps >= budget.steps()) break;
            Node child = trial(node, family.get(), horizon, commit);
            if (child != null) {
                frontier.add(child);
                if (frontier.size() > FRONTIER_LIMIT) frontier.pollLast();
            }
        }
        return null;
    }

    /**
     * The source index an authored node may rejoin: the nearest reference index within tracking
     * tolerance, after the last source row the path already used and no further than the
     * lookahead, whose source row holds the same action and Start buttons as the node's last row
     * (a seam with a different held jump is not the same seam). -2 when there is none; the
     * literal source rows that follow still have to track, and the final cold replay decides.
     */
    private int rejoinIndex(Node node) {
        int lastSource = -1;
        for (Node n = node; n != null; n = n.parent) {
            if (n.origin instanceof Origin.Source source) {
                lastSource = source.from() + source.length() - 1;
                break;
            }
            if (n.origin instanceof Origin.Prefix && sourceIsOriginal) {
                lastSource = n.edge.size() - 1;
                break;
            }
        }
        int seam = node.lastRow().p1() & SEAM_BUTTONS;
        int best = -2;
        double bestDistance = Double.MAX_VALUE;
        int hi = Math.min(ref.size() - 2, node.k + hints.look);
        for (int i = Math.max(lastSource + 1, 0); i <= hi; i++) {
            double d = progress.distance(node.sample, i);
            if (d <= hints.syncTolerance && d <= bestDistance && (ref.sourceRow(i).p1() & SEAM_BUTTONS) == seam) {
                best = i;
                bestDistance = d;
            }
        }
        return best;
    }

    private static final int SEAM_BUTTONS = PhysicalButton.A.bit() | PhysicalButton.B.bit() | PhysicalButton.C.bit()
            | PhysicalButton.START.bit();

    /** Whether the path already authored rows at or after the required window's first source row. */
    private boolean passedRequired(Node node) {
        for (Node n = node; n.parent != null; n = n.parent) {
            if (n.origin instanceof Origin.Authored && n.parent.k + 1 >= required.get().sourceFrom()) return true;
        }
        return false;
    }

    /** Runs one candidate from {@code node}; returns its first {@code commit} rows as a child, or null. */
    private Node trial(Node node, ReferenceCandidates.RowPolicy policy, int horizon, int commit) {
        materialize(node);
        atNode = null;
        trials++;
        int rings = node.sample.p1().rings();
        long epoch = node.sample.epoch();
        int prog = node.k;
        int maxProg = node.k;
        int progAtCommit = node.k;
        boolean hurt = false;
        RouteSample commitSample = null;
        var rows = new InputTape.Builder();
        PadRow previous = node.lastRow();
        for (int t = 0; t < horizon; t++) {
            PadRow row = policy.row(t, last, previous, prog);
            step(row);
            rows.add(row);
            previous = row;
            if (last.epoch() != epoch) {
                rebootTo(node);
                return null;
            }
            if (last.p1().dead()) return null;
            hurt |= last.p1().hurt();
            int q = progress.strict(last, prog - 10, prog + hints.look, hints.radius);
            if (q > prog) prog = q;
            maxProg = Math.max(maxProg, prog);
            if (t == commit - 1) {
                progAtCommit = prog;
                commitSample = last;
            }
            if (prog >= ref.to()) {
                if (t < commit) {
                    progAtCommit = prog;
                    commitSample = last;
                }
                break;
            }
        }
        double[] near = progress.nearest(last, node.k - 10, Math.max(prog, node.k) + hints.look);
        int lost = rings - last.p1().rings();
        double value = 0.5 * prog + 0.5 * (near[1] <= 64 ? near[0] : node.k - 100);
        if (hurt) value -= 400;
        if (lost > 0) value -= 20 * lost;
        value -= 0.05 * near[1];
        int keep = Math.min(commit, rows.size());
        InputTape edge = rows.build().slice(0, keep);
        int stuck = progAtCommit > node.k ? 0 : maxProg > node.predicted ? 0 : node.stuck + 1;
        Node child = new Node(ordinals++, node, edge, new Origin.Authored(keep), progAtCommit,
                Math.round(value * 1000), stuck, Math.max(node.predicted, maxProg));
        child.sample = commitSample != null ? commitSample : last;
        return child;
    }

    private static long rank(int k) {
        return k * 1000L + 500;
    }

    /** Puts the world in {@code target}'s state: restore the nearest checkpointed ancestor, replay edge rows. */
    private void materialize(Node target) {
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

    /** A replayed edge must land exactly where it first did; otherwise a checkpoint is incomplete. */
    private void requireReproduced(Node n) {
        if (n.sample != null && !n.sample.equals(last)) {
            throw new IllegalStateException("restoring or replaying recorded rows did not reproduce node " + n.ordinal
                    + ": the host checkpoint is incomplete\n  first: " + n.sample + "\n  again: " + last);
        }
    }

    private void ensureCheckpoint(Node node) {
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

    private void dropCheckpoints() {
        for (Node node : retained) node.checkpoint = null;
        retained.clear();
    }

    /** Closes a contaminated host, boots a fresh one and replays every recorded row to {@code target}. */
    private void rebootTo(Node target) {
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
        world = hosts.openSearch(task.original().launch());
        last = world.observe();
        atNode = null;
    }

    private void step(PadRow row) {
        last = world.step(row).after();
        steps++;
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

    /** Output spans along {@code end}'s path; adjacent source runs merge. */
    RepairReport report(Node end) {
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
                case Origin.Authored a -> new RepairReport.Span.Authored(out, a.length());
            };
            out += n.edge.size();
            if (span == null) continue;
            if (!spans.isEmpty()) {
                RepairReport.Span previous = spans.getLast();
                RepairReport.Span merged = merge(previous, span);
                if (merged != null) {
                    spans.set(spans.size() - 1, merged);
                    continue;
                }
            }
            spans.add(span);
        }
        return new RepairReport(original.size(), out, spans);
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

    boolean sourceIsOriginal() {
        return sourceIsOriginal;
    }

    int rootProgress(Node end) {
        Node n = end;
        while (n.parent != null) n = n.parent;
        return n.k;
    }

    WorkReport work(long qualificationSteps) {
        return new WorkReport(expansions, steps, trials, captures, restores, freshBoots, qualificationSteps);
    }

    Optional<RepairTrigger.RequiredEdit> required() {
        return required;
    }

}
