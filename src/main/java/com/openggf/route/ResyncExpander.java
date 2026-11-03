package com.openggf.route;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Exact re-synchronising repair. The reference is the route's own run on the engine its rows
 * were authored against, with every player's exact post-row state. Source rows are mandatory
 * edges while the tracked players' states equal the recorded ones field for field. Where they
 * part (an engine change moved a player), the search edits single source rows in the next
 * window (a direction held or released, a jump button toggled), keeping every other row, and
 * ranks each edit by how close the following rows come to the recorded states. An edit chain
 * that brings every tracked field back into exact agreement for {@code confirm} consecutive rows
 * rejoins the source; the rows after it are the original rows, and the run after the rejoin is
 * the recorded run itself. Edits replace rows; the output keeps the original's row count, so
 * every timer a frame counter drives stays aligned.
 *
 * <p>An engine change that leaves no exactly matching state reachable within the window (a
 * hidden-state difference, a sidekick that diverged before the leader could be re-synchronised)
 * stops the search with no progress; such a stretch needs a goal program instead.
 */
final class ResyncExpander implements PrefixSearch.Expander {
    /** Rows between scratch checkpoints while sweeping a window. */
    private static final int SCRATCH_EVERY = 8;
    /** Window children kept per expansion. */
    private static final int CHILDREN = 32;
    private static final int LR = PhysicalButton.LEFT.bit() | PhysicalButton.RIGHT.bit();
    private static final int JUMP = PhysicalButton.A.bit() | PhysicalButton.B.bit() | PhysicalButton.C.bit();

    private final RouteTask.Repair task;
    private final AuthorHints hints;
    private final ReferencePath ref;
    private final boolean sidekick;

    /**
     * Node data. Absent on synchronised nodes (the prefix and source edges that matched exactly).
     * {@code Drift} marks the rows of an unsynchronised window: {@code edits} made so far in it and
     * the least state distance its trial reached.
     */
    record Drift(int edits, long distance) { }

    ResyncExpander(RouteTask.Repair task) {
        this.task = task;
        this.hints = task.hints();
        this.ref = task.reference();
        InputTape original = task.original().tape();
        if (!(ref.source().size() <= original.size() && original.slice(0, ref.source().size()).equals(ref.source()))) {
            throw new IllegalArgumentException("an exact re-synchronising repair follows the original's own recorded run");
        }
        if (!ref.hasStates()) {
            throw new IllegalArgumentException("an exact re-synchronising repair needs a reference with exact player states");
        }
        if (task.scope().trigger() instanceof RepairTrigger.RequiredEdit) {
            throw new IllegalArgumentException("an exact re-synchronising repair opens windows only where states part");
        }
        this.sidekick = hints.sidekick && ref.hasSidekickStates();
    }

    @Override public String checkPrefixRow(int row, RouteSample sample) {
        if (row < ref.size() && ref.stateDistance(sample, row, sidekick) != 0) {
            return "immutable row " + row + " does not reproduce its recorded state (distance "
                    + ref.stateDistance(sample, row, sidekick) + ")";
        }
        if (sample.p1().dead() && row < task.scope().firstEditable() - 1) return "P1 died in the immutable rows at " + row;
        return null;
    }

    @Override public int rootProgress(RouteSample sample) {
        return task.scope().firstEditable() - 1;
    }

    @Override public boolean done(PrefixSearch.Node node) {
        return node.data == null && node.k >= ref.to();
    }

    @Override public boolean rejected(InputTape tape) {
        return false;
    }

    @Override public int target() {
        return ref.to();
    }

    @Override public PrefixSearch.Node expand(PrefixSearch search, PrefixSearch.Node node, TreeSet<PrefixSearch.Node> frontier) {
        search.materialize(node);
        search.ensureCheckpoint(node);
        if (node.data == null) {
            PrefixSearch.Node tracked = follow(search, node);
            if (tracked != null) return tracked;
        }
        Drift drift = node.data instanceof Drift d ? d : new Drift(0, Long.MAX_VALUE);
        if (drift.edits() >= hints.stuckLimit || node.authored >= task.scope().maxAuthoredRows()) return null;
        if (drift.edits() == 0 && node.windows >= task.scope().maxWindows()) return null;
        return window(search, node, drift, frontier);
    }

    /** Source rows while every tracked state matches exactly; null when the very next row parts. */
    private PrefixSearch.Node follow(PrefixSearch search, PrefixSearch.Node node) {
        var rows = new InputTape.Builder();
        RouteSample at = node.sample;
        boolean crossed = false;
        boolean parted = false;
        int n = 0;
        for (; n < hints.commit && node.k + 1 + n <= ref.to(); n++) {
            int src = node.k + 1 + n;
            long epochBefore = search.last.epoch();
            PadRow row = search.step(ref.sourceRow(src));
            boolean boundary = search.last.epoch() != epochBefore;
            if (boundary && !ref.crossesBoundary(src)) {
                search.rebootTo(node);
                return null;
            }
            if (ref.stateDistance(search.last, src, sidekick) != 0 || search.last.p1().dead()) {
                parted = true;
                break;
            }
            crossed |= boundary;
            rows.add(row);
            at = search.last;
        }
        if (n == 0) {
            search.dirty();
            return null;
        }
        int k = node.k + n;
        PrefixSearch.Node child = search.node(node, rows.build(), new PrefixSearch.Origin.Source(node.k + 1, n), k,
                rankOf(k, 0), 0, k, null, at);
        child.segmentRoot = crossed;
        if (crossed) search.dropCheckpoints();
        if (parted) search.dirty();
        else search.arrivedAt(child);
        return child;
    }

    /**
     * One window from {@code node}: every single-row edit of the next {@code look} source rows,
     * each trialled over the rows after it. The first edit that brings every tracked state back
     * into exact agreement for {@code confirm} rows is returned as a rejoined source edge; the
     * others become window children ranked by the least distance they reached.
     */
    private PrefixSearch.Node window(PrefixSearch search, PrefixSearch.Node node, Drift drift,
                                     TreeSet<PrefixSearch.Node> frontier) {
        int first = node.k + 1;
        int last = Math.min(ref.to() - 1, node.k + hints.look);
        if (first > last) return null;
        // Sweep the unedited source rows once: scratch checkpoints and the unedited samples. A sweep
        // that crosses a boundary contaminates the host; it is rebuilt and the window ends before it.
        var scratch = new TreeMap<Integer, WorldCheckpoint>();
        var unedited = new ArrayList<RouteSample>();
        RouteSample before = node.sample;
        boolean swept = false;
        while (!swept) {
            scratch.clear();
            unedited.clear();
            search.dirty();
            search.materialize(node);
            swept = true;
            for (int row = first; row <= last; row++) {
                if ((row - first) % SCRATCH_EVERY == 0) {
                    WorldCheckpoint checkpoint = search.captureScratch();
                    if (checkpoint != null) scratch.put(row, checkpoint);
                }
                long epoch = search.last.epoch();
                search.step(ref.sourceRow(row));
                if (search.last.epoch() != epoch) {
                    last = row - 1;
                    search.rebootTo(node);
                    swept = false;
                    break;
                }
                if (search.last.p1().dead()) {
                    last = row - 1;
                    break;
                }
                unedited.add(search.last);
            }
            if (first > last) return null;
        }
        search.dirty();
        long generation = search.hostGeneration();
        var children = new ArrayList<PrefixSearch.Node>();
        for (int j = first; j <= last; j++) {
            for (int lane : alternatives(ref.sourceRow(j).p1())) {
                if (search.cancelled() || search.outOfSteps()) return keep(search, frontier, children);
                if (search.hostGeneration() != generation || scratch.floorEntry(j) == null) {
                    return keep(search, frontier, children);
                }
                Trial trial = trial(search, node, scratch, unedited, first, j, lane, before);
                if (trial == null) continue;
                if (trial.rejoined != null) return trial.rejoined;
                if (trial.child != null && trial.distance < drift.distance()) children.add(trial.child);
            }
        }
        return keep(search, frontier, children);
    }

    private PrefixSearch.Node keep(PrefixSearch search, TreeSet<PrefixSearch.Node> frontier,
                                   List<PrefixSearch.Node> children) {
        children.sort(Comparator.comparingLong((PrefixSearch.Node n) -> -n.rank).thenComparingLong(n -> n.ordinal));
        for (int i = 0; i < Math.min(CHILDREN, children.size()); i++) search.push(frontier, children.get(i));
        search.dirty();
        return null;
    }

    /** The result of one edit: a rejoined chain, or a window child and its least distance. */
    private record Trial(PrefixSearch.Node rejoined, PrefixSearch.Node child, long distance) { }

    private Trial trial(PrefixSearch search, PrefixSearch.Node node, TreeMap<Integer, WorldCheckpoint> scratch,
                        List<RouteSample> unedited, int first, int j, int lane, RouteSample before) {
        var entry = scratch.floorEntry(j);
        search.restoreScratch(entry.getValue());
        search.trials++;
        for (int row = entry.getKey(); row < j; row++) search.step(ref.sourceRow(row));
        RouteSample beforeEdit = j == first ? before : unedited.get(j - 1 - first);
        PadRow edited = ref.sourceRow(j).with(PlayerSlot.P1, lane);
        search.step(edited);
        RouteSample afterEdit = search.last;
        // An edit that changes nothing observable, or kills P1, is no candidate.
        if (afterEdit.equals(unedited.get(j - first)) || afterEdit.p1().dead()) return null;
        long least = ref.stateDistance(afterEdit, j, sidekick);
        int end = Math.min(ref.to(), j + hints.horizon);
        var samples = new ArrayList<RouteSample>();
        int matched = 0;
        int rejoinAt = -1;
        for (int row = j + 1; row <= end; row++) {
            long epoch = search.last.epoch();
            search.step(ref.sourceRow(row));
            if (search.last.epoch() != epoch && !ref.crossesBoundary(row)) {
                search.rebootTo(node);
                return null;
            }
            if (search.last.p1().dead()) return null;
            samples.add(search.last);
            long d = ref.stateDistance(search.last, row, sidekick);
            least = Math.min(least, d);
            if (d != 0) {
                matched = 0;
                continue;
            }
            if (matched++ == 0) rejoinAt = row;
            if (matched >= hints.confirm || row == ref.to()) break;
        }
        // The window's unedited rows before the edit are kept original rows; the edit is authored.
        PrefixSearch.Node parent = node;
        if (j > first) {
            parent = search.node(node, ref.source().slice(first, j), new PrefixSearch.Origin.Source(first, j - first),
                    node.k, Long.MIN_VALUE, 0, node.k, new Drift(0, Long.MAX_VALUE), beforeEdit);
        }
        Drift drift = node.data instanceof Drift d ? d : new Drift(0, Long.MAX_VALUE);
        Drift edits = new Drift(drift.edits() + 1, least);
        boolean rejoined = matched >= hints.confirm || (matched > 0 && rejoinAt + matched - 1 == ref.to());
        if (!rejoined) {
            PrefixSearch.Node child = search.node(parent, InputTape.of(List.of(edited)), new PrefixSearch.Origin.Authored(1),
                    j, rankOf(node.k, least) - edits.edits(), 0, node.k, edits, afterEdit);
            return new Trial(null, child, least);
        }
        PrefixSearch.Node edit = search.node(parent, InputTape.of(List.of(edited)), new PrefixSearch.Origin.Authored(1),
                j, Long.MIN_VALUE, 0, node.k, edits, afterEdit);
        // Source rows after the edit that do not match yet stay drifted; from rejoinAt they are exact.
        PrefixSearch.Node approach = edit;
        if (rejoinAt > j + 1) {
            approach = search.node(edit, ref.source().slice(j + 1, rejoinAt), new PrefixSearch.Origin.Source(j + 1, rejoinAt - j - 1),
                    rejoinAt - 1, Long.MIN_VALUE, 0, node.k, edits, samples.get(rejoinAt - 1 - (j + 1)));
        }
        int k = rejoinAt + matched - 1;
        PrefixSearch.Node exact = search.node(approach, ref.source().slice(rejoinAt, k + 1),
                new PrefixSearch.Origin.Source(rejoinAt, matched), k, rankOf(k, 0), 0, k, null, samples.get(k - (j + 1)));
        search.arrivedAt(exact);
        return new Trial(exact, null, 0);
    }

    /** Frontier rank: synchronised progress first, then the least remaining distance. */
    private static long rankOf(int k, long distance) {
        return (long) k * 1_000_000_000L - Math.min(distance, 999_999_999L) * 1_000L;
    }

    /** Single-row edits of P1's lane: the direction held or released either way, or the jump buttons toggled. */
    private static List<Integer> alternatives(int lane) {
        int dirless = lane & ~LR;
        var out = new LinkedHashSet<Integer>();
        out.add(dirless);
        out.add(dirless | PhysicalButton.LEFT.bit());
        out.add(dirless | PhysicalButton.RIGHT.bit());
        out.add((lane & JUMP) != 0 ? lane & ~JUMP : lane | PhysicalButton.B.bit());
        out.remove(lane);
        return List.copyOf(out);
    }

    /**
     * Output rows that must reproduce the recorded states exactly on the cold replay: the
     * immutable prefix and every source edge after it was rejoined; window rows are excluded.
     */
    static BitSet exactRows(PrefixSearch.Node end) {
        var exact = new BitSet();
        for (PrefixSearch.Node n = end; n != null; n = n.parent) {
            if (n.data != null) continue;
            int start = n.rows - n.edge.size();
            exact.set(start, n.rows);
        }
        return exact;
    }
}
