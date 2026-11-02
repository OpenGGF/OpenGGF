package com.openggf.route;

import java.util.Arrays;
import java.util.Objects;

/**
 * An engine-derived authoring target: the P1 centre after each row of a source tape, replayed
 * from its own launch in a fresh host, plus the source rows themselves. It is a scoring target
 * only. It never boots, seeds or restores a world, and it is never trace data.
 *
 * <p>Index {@code i} is the post-row phase of source row {@code i}. A window {@code [from, to]}
 * names where following starts and the index that completes it.
 */
public final class ReferencePath {
    private final LaunchContract launch;
    private final InputTape source;
    private final int[] xs;
    private final int[] ys;
    private final long[] epochs;
    private final String environment;
    private final int from;
    private final int to;

    private ReferencePath(LaunchContract launch, InputTape source, int[] xs, int[] ys, long[] epochs,
                          String environment, int from, int to) {
        this.launch = launch;
        this.source = source;
        this.xs = xs;
        this.ys = ys;
        this.epochs = epochs;
        this.environment = environment;
        if (from < 0 || to < from || to >= xs.length) {
            throw new IllegalArgumentException("window " + from + ".." + to + " outside reference of " + xs.length);
        }
        this.from = from;
        this.to = to;
    }

    /**
     * Replays {@code rows[0, count)} from {@code launch} in a fresh host and records P1's centre
     * after every row, stopping after a death (the death row is the last index).
     */
    public static ReferencePath extract(RouteWorldFactory hosts, LaunchContract launch, InputTape rows, int count) {
        Objects.requireNonNull(hosts, "hosts");
        if (count < 1 || count > rows.size()) {
            throw new IllegalArgumentException("reference length " + count + " outside source of " + rows.size());
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        long[] epochs = new long[count];
        int n = 0;
        try (ReplayWorld world = hosts.openColdReplay(launch)) {
            for (; n < count; n++) {
                RouteSample after = world.step(rows.row(n)).after();
                xs[n] = after.p1().centreX();
                ys[n] = after.p1().centreY();
                epochs[n] = after.epoch();
                if (after.p1().dead()) {
                    n++;
                    break;
                }
            }
        }
        return new ReferencePath(launch, rows.slice(0, n), Arrays.copyOf(xs, n), Arrays.copyOf(ys, n),
                Arrays.copyOf(epochs, n), hosts.environment(), 0, n - 1);
    }

    /** The same path, followed from index {@code from} until index {@code to} is reached. */
    public ReferencePath window(int from, int to) {
        return new ReferencePath(launch, source, xs, ys, epochs, environment, from, to);
    }

    public int size() {
        return xs.length;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public int x(int index) {
        return xs[index];
    }

    public int y(int index) {
        return ys[index];
    }

    /** Boundary epoch after source row {@code index}, counted from the reference's own boot. */
    long epoch(int index) {
        return epochs[index];
    }

    /** True when source row {@code index} crossed a boundary in the reference run. */
    boolean crossesBoundary(int index) {
        return index > 0 && epochs[index] != epochs[index - 1];
    }

    public PadRow sourceRow(int index) {
        return source.row(index);
    }

    public InputTape source() {
        return source;
    }

    public LaunchContract launch() {
        return launch;
    }

    /** Engine and host identity the reference was replayed on. */
    public String environment() {
        return environment;
    }

    @Override
    public String toString() {
        return "ReferencePath[" + xs.length + " rows of " + source.hash().shortForm() + ", window " + from + ".." + to
                + ", " + environment + "]";
    }
}
