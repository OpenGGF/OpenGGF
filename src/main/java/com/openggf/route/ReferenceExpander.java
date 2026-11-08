package com.openggf.route;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Reference following (and route repair): source rows are mandatory edges while P1 tracks the
 * reference within tolerance; a drift scores every reference candidate family from the node and
 * pushes each surviving trial's first rows as a child. An authored node rejoins the source at the
 * nearest tracking index (see {@link #rejoinIndex}).
 */
final class ReferenceExpander implements PrefixSearch.Expander {
    private static final int SEAM_BUTTONS = PhysicalButton.A.bit() | PhysicalButton.B.bit() | PhysicalButton.C.bit()
            | PhysicalButton.START.bit();

    private final RouteTask.Repair task;
    private final AuthorHints hints;
    private final ReferencePath ref;
    private final ReferenceProgress progress;
    private final boolean sourceIsOriginal;
    private final Optional<RepairTrigger.RequiredEdit> required;

    ReferenceExpander(RouteTask.Repair task) {
        this.task = task;
        this.hints = task.hints();
        this.ref = task.reference();
        this.progress = new ReferenceProgress(ref);
        InputTape original = task.original().tape();
        this.sourceIsOriginal = ref.source().size() <= original.size()
                && original.slice(0, ref.source().size()).equals(ref.source());
        this.required = task.scope().trigger() instanceof RepairTrigger.RequiredEdit edit ? Optional.of(edit) : Optional.empty();
    }

    boolean sourceIsOriginal() {
        return sourceIsOriginal;
    }

    @Override public String checkPrefixRow(int row, RouteSample sample) {
        if (sourceIsOriginal && row < ref.size() && progress.distance(sample, row) > hints.syncTolerance) {
            return "immutable row " + row + " is " + (int) progress.distance(sample, row) + " px off its recorded path";
        }
        if (sample.p1().dead() && row < task.scope().firstEditable() - 1) {
            return "P1 died in the immutable rows at " + row;
        }
        return null;
    }

    @Override public int rootProgress(RouteSample sample) {
        if (sourceIsOriginal) return task.scope().firstEditable() - 1;
        int k = progress.strict(sample, ref.from() - 60, ref.from() + hints.look, hints.radius);
        return k < 0 ? ref.from() : k;
    }

    @Override public boolean done(PrefixSearch.Node node) {
        return node.k >= ref.to();
    }

    @Override public boolean rejected(InputTape tape) {
        return required.isPresent() && required.get().rejected().contains(tape.hash());
    }

    @Override public int target() {
        return ref.to();
    }

    @Override public PrefixSearch.Node expand(PrefixSearch search, PrefixSearch.Node node, TreeSet<PrefixSearch.Node> frontier) {
        if (node.stuck >= hints.stuckLimit) return null;
        search.materialize(node);
        search.ensureCheckpoint(node);
        if (required.isPresent() && node.k+1==required.get().sourceFrom()
                && node.authored<task.scope().maxAuthoredRows() && node.windows<task.scope().maxWindows()) {
            var local=singleRowSeam(search,node);
            if(local!=null) {search.arrivedAt(local);return local;}
        }
        int base = node.origin instanceof PrefixSearch.Origin.Authored ? rejoinIndex(node) : node.k;
        var rows = new InputTape.Builder();
        int n = 0;
        boolean tracked = base >= -1;
        boolean crossed = false;
        boolean contaminated = false;
        for (; tracked && n < hints.commit && base + 1 + n < ref.size() && !search.cancelled() && !search.outOfSteps(); n++) {
            int src = base + 1 + n;
            if (required.isPresent() && src >= required.get().sourceFrom() && !passedRequired(node)) {
                tracked = false;
                break;
            }
            long epochBefore = search.last.epoch();
            rows.add(search.step(ref.sourceRow(src)));
            if (search.last.epoch() != epochBefore) {
                if (!ref.crossesBoundary(src)) {
                    tracked = false;
                    contaminated = true;
                    break;
                }
                crossed = true;
            }
            if (search.last.p1().dead() || search.last.p1().hurt()
                    || progress.distance(search.last, src) > hints.syncTolerance) {
                tracked = false;
                break;
            }
        }
        if(search.cancelled() || search.outOfSteps()) return null;
        if (tracked && n > 0) {
            int k = base + n;
            PrefixSearch.Node child = search.node(node, rows.build(), new PrefixSearch.Origin.Source(base + 1, n), k,
                    k * 1000L + 500, 0, Math.max(node.predicted, k), null, search.last);
            child.segmentRoot = crossed;
            if (crossed) search.dropCheckpoints();
            search.arrivedAt(child);
            return child;
        }
        search.dirty();
        if (contaminated) search.rebootTo(node);
        if (node.authored >= task.scope().maxAuthoredRows()) return null;
        boolean opensWindow = !(node.origin instanceof PrefixSearch.Origin.Authored);
        if (opensWindow && node.windows >= task.scope().maxWindows()) return null;
        boolean wide = node.stuck >= hints.stuckLimit / 2;
        int horizon = wide ? hints.horizon * 2 : hints.horizon;
        int commit = wide ? hints.commit * 2 : hints.commit;
        List<Supplier<ReferenceCandidates.RowPolicy>> families = ReferenceCandidates.families(progress, node.k, wide);
        for (Supplier<ReferenceCandidates.RowPolicy> family : families) {
            if (search.cancelled() || search.outOfSteps()) break;
            PrefixSearch.Node child = trial(search, node, family.get(), horizon, commit);
            if (child != null) search.push(frontier, child);
        }
        return null;
    }

    /** A bounded one-button seam family; its literal suffix must track or satisfy the stored goals. */
    private PrefixSearch.Node singleRowSeam(PrefixSearch search,PrefixSearch.Node node) {
        int from=node.k+1;
        var buttons=new PhysicalButton[]{PhysicalButton.START,PhysicalButton.A,PhysicalButton.B,PhysicalButton.C,
                PhysicalButton.LEFT,PhysicalButton.RIGHT,PhysicalButton.UP,PhysicalButton.DOWN};
        for(var slot:PlayerSlot.values()) for(var button:buttons) {
            if(search.cancelled() || search.outOfSteps()) return null;
            search.materialize(node);search.dirty();search.trials++;
            var original=ref.sourceRow(from);
            var edited=original.with(slot,(slot==PlayerSlot.P1?original.p1():original.p2())^button.bit());
            search.step(edited);var first=search.last;
            if(first.epoch()!=node.sample.epoch() || progress.distance(first,from)>hints.syncTolerance) continue;
            var suffix=new InputTape.Builder();boolean tracks=true;
            boolean goalCandidate=!task.original().checks().legs().isEmpty() && task.original().checks().startRow()>=node.rows;
            var goals=task.original().checks().cursor();goals.begin(node.sample);
            String violation=goalCandidate?goals.afterRow(node.rows,first):null;
            for(int i=from+1;i<=ref.to();i++) {
                if(search.cancelled() || search.outOfSteps()) {tracks=false;break;}
                suffix.add(search.step(ref.sourceRow(i)));
                if(search.last.epoch()!=first.epoch() || search.last.p1().dead()) {tracks=false;break;}
                if(goalCandidate && violation==null) violation=goals.afterRow(node.rows+i-from,search.last);
                if(!goalCandidate && progress.distance(search.last,i)>hints.syncTolerance) {tracks=false;break;}
            }
            if(!tracks || (goalCandidate && (violation!=null || goals.finish(search.last)!=null))) continue;
            var edit=search.node(node,InputTape.of(List.of(edited)),new PrefixSearch.Origin.Authored(1),from,
                    from*1000L,0,from,null,first);
            if(suffix.size()==0) return edit;
            return search.node(edit,suffix.build(),new PrefixSearch.Origin.Source(from+1,suffix.size()),ref.to(),
                    ref.to()*1000L,0,ref.to(),null,search.last);
        }
        return null;
    }

    /** Whether the path already authored rows at or after the required window's first source row. */
    private boolean passedRequired(PrefixSearch.Node node) {
        for (PrefixSearch.Node n = node; n.parent != null; n = n.parent) {
            if (n.origin instanceof PrefixSearch.Origin.Authored && n.parent.k + 1 >= required.get().sourceFrom()) return true;
        }
        return false;
    }

    /**
     * The source index an authored node may rejoin: the nearest reference index within tracking
     * tolerance, after the last source row the path already used and no further than the
     * lookahead, whose source row holds the same action and Start buttons as the node's last row
     * (a seam with a different held jump is not the same seam). -2 when there is none; the
     * literal source rows that follow still have to track, and the final cold replay decides.
     */
    private int rejoinIndex(PrefixSearch.Node node) {
        int lastSource = -1;
        for (PrefixSearch.Node n = node; n != null; n = n.parent) {
            if (n.origin instanceof PrefixSearch.Origin.Source source) {
                lastSource = source.from() + source.length() - 1;
                break;
            }
            if (n.origin instanceof PrefixSearch.Origin.Prefix && sourceIsOriginal) {
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

    /** Runs one candidate from {@code node}; returns its first {@code commit} rows as a child, or null. */
    private PrefixSearch.Node trial(PrefixSearch search, PrefixSearch.Node node, ReferenceCandidates.RowPolicy policy,
                                    int horizon, int commit) {
        search.materialize(node);
        search.dirty();
        search.trials++;
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
            if(search.cancelled() || search.outOfSteps()) break;
            PadRow row = search.step(policy.row(t, search.last, previous, prog));
            rows.add(row);
            previous = row;
            if (search.last.epoch() != epoch) {
                search.rebootTo(node);
                return null;
            }
            if (search.last.p1().dead()) return null;
            hurt |= search.last.p1().hurt();
            int q = progress.strict(search.last, prog - 10, prog + hints.look, hints.radius);
            if (q > prog) prog = q;
            maxProg = Math.max(maxProg, prog);
            if (t == commit - 1) {
                progAtCommit = prog;
                commitSample = search.last;
            }
            if (prog >= ref.to()) {
                if (t < commit) {
                    progAtCommit = prog;
                    commitSample = search.last;
                }
                break;
            }
        }
        if(rows.size()==0) return null;
        double[] near = progress.nearest(search.last, node.k - 10, Math.max(prog, node.k) + hints.look);
        int lost = rings - search.last.p1().rings();
        double value = 0.5 * prog + 0.5 * (near[1] <= 64 ? near[0] : node.k - 100);
        if (hurt) value -= 400;
        if (lost > 0) value -= 20 * lost;
        value -= 0.05 * near[1];
        int keep = Math.min(commit, rows.size());
        InputTape edge = rows.build().slice(0, keep);
        int stuck = progAtCommit > node.k ? 0 : maxProg > node.predicted ? 0 : node.stuck + 1;
        return search.node(node, edge, new PrefixSearch.Origin.Authored(keep), progAtCommit, Math.round(value * 1000),
                stuck, Math.max(node.predicted, maxProg), null, commitSample != null ? commitSample : search.last);
    }
}
