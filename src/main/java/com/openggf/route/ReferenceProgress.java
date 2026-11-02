package com.openggf.route;

/**
 * Reference progress: the latest reference index within a radius of P1, aware of vertical
 * wrap. Search scoring and replay qualification share this one evaluator.
 */
final class ReferenceProgress {
    private final ReferencePath reference;

    ReferenceProgress(ReferencePath reference) {
        this.reference = reference;
    }

    ReferencePath reference() {
        return reference;
    }

    /** Distance from P1 to reference index {@code index}, vertical-wrap aware. */
    double distance(RouteSample sample, int index) {
        int dx = reference.x(index) - sample.p1().centreX();
        int dy = reference.y(index) - sample.p1().centreY();
        int range = sample.verticalWrap();
        if (range > 0) {
            dy = ((dy % range) + range) % range;
            if (dy > range / 2) dy -= range;
        }
        return Math.hypot(dx, dy);
    }

    /** Latest index in {@code [lo, hi]} within {@code radius} of P1, or -1. */
    int strict(RouteSample sample, int lo, int hi, int radius) {
        lo = Math.max(0, lo);
        hi = Math.min(reference.size() - 1, hi);
        for (int i = hi; i >= lo; i--) {
            if (distance(sample, i) <= radius) return i;
        }
        return -1;
    }

    /** Nearest index in {@code [lo, hi]} (the later one on ties) and its distance. */
    double[] nearest(RouteSample sample, int lo, int hi) {
        lo = Math.max(0, lo);
        hi = Math.min(reference.size() - 1, hi);
        double best = Double.MAX_VALUE;
        int index = lo;
        for (int i = lo; i <= hi; i++) {
            double d = distance(sample, i);
            if (d <= best) {
                best = d;
                index = i;
            }
        }
        return new double[]{index, best};
    }

    /** Reference position a policy steers toward from progress {@code prog}. */
    int[] target(int prog) {
        int index = Math.min(reference.to(), Math.min(reference.size() - 1, prog + ReferenceCandidates.POLICY_LEAD));
        return new int[]{reference.x(index), reference.y(index)};
    }

    /** Qualification: progress tracked over the replayed rows must reach the window's end. */
    ReplayCheck reaches(int fromRow, int startProgress, AuthorHints hints) {
        return new ReplayCheck() {
            private int prog = startProgress;

            @Override public String name() {
                return "reference-progress>=" + reference.to();
            }

            @Override public String afterRow(int row, RouteSample sample) {
                if (row >= fromRow) {
                    int q = strict(sample, prog - 10, prog + hints.look, hints.radius);
                    if (q > prog) prog = q;
                }
                return null;
            }

            @Override public String finish(RouteSample last) {
                return prog >= reference.to() ? null : "reference progress " + prog + " short of " + reference.to();
            }
        };
    }

    /** Qualification: rows {@code [0, rows)} must keep P1 within tolerance of the same source index. */
    ReplayCheck tracksPrefix(int rows, int tolerance) {
        return new ReplayCheck() {
            @Override public String name() {
                return "prefix-tracks-reference<=" + tolerance + "px";
            }

            @Override public String afterRow(int row, RouteSample sample) {
                if (row < rows && distance(sample, row) > tolerance) {
                    return "immutable row " + row + " left its recorded path by " + (int) distance(sample, row) + " px";
                }
                return null;
            }

            @Override public String finish(RouteSample last) {
                return null;
            }
        };
    }
}
