package com.openggf.route;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An engine-derived authoring target: the P1 centre after each row of a source tape, replayed
 * from its own launch in a fresh host, plus the source rows themselves. It is a scoring target
 * only. It never boots, seeds or restores a world, and it is never trace data.
 *
 * <p>Index {@code i} is the post-row phase of source row {@code i}. A window {@code [from, to]}
 * names where following starts and the index that completes it.
 *
 * <p>A reference may also carry each player's exact post-row kinematic state ({@link PlayerState}).
 * An exact re-synchronising repair ({@link AuthorHints#exactResync()}) needs them: it keeps source
 * rows only while the players' states equal the recorded ones, field for field.
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
    /** Exact recorded states, or null; a P2 entry is null where the reference had no sidekick. */
    private final PlayerState[] p1States;
    private final PlayerState[] p2States;
    private final boolean sidekickStates;

    /**
     * One player's exact post-row state on the reference's engine: ROM {@code x_pos}/{@code y_pos}
     * with their 16-bit subpixels, raw 8.8 speeds, and the airborne and rolling status bits.
     */
    public record PlayerState(int centreX, int centreY, int xSub, int ySub, int xSpeed, int ySpeed, int groundSpeed,
                              boolean air, boolean rolling) {
        public static PlayerState of(PlayerView view) {
            return new PlayerState(view.centreX(), view.centreY(), view.xSub(), view.ySub(), view.xSpeed(),
                    view.ySpeed(), view.groundSpeed(), view.air(), view.rolling());
        }

        /**
         * How far {@code view} is from this state: position in 1/256 px, speeds in raw units, and a
         * large step per differing status bit. Zero only when every field is equal.
         */
        public long distance(PlayerView view) {
            long d = Math.abs((((long) view.centreX() << 16) | view.xSub()) - (((long) centreX << 16) | xSub)) / 256
                    + Math.abs((((long) view.centreY() << 16) | view.ySub()) - (((long) centreY << 16) | ySub)) / 256
                    + Math.abs(view.xSpeed() - xSpeed) + Math.abs(view.ySpeed() - ySpeed)
                    + Math.abs(view.groundSpeed() - groundSpeed);
            if (d == 0 && (view.xSub() != xSub || view.ySub() != ySub)) d = 1;
            if (view.air() != air) d += 1000;
            if (view.rolling() != rolling) d += 1000;
            return d;
        }
    }

    private ReferencePath(LaunchContract launch, InputTape source, int[] xs, int[] ys, long[] epochs,
                          String environment, int from, int to) {
        this(launch, source, xs, ys, epochs, environment, from, to, null, null);
    }

    private ReferencePath(LaunchContract launch, InputTape source, int[] xs, int[] ys, long[] epochs,
                          String environment, int from, int to, PlayerState[] p1States, PlayerState[] p2States) {
        this.p1States = p1States;
        this.p2States = p2States;
        boolean any = false;
        if (p2States != null) for (PlayerState state : p2States) any |= state != null;
        this.sidekickStates = any;
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
        PlayerState[] p1 = new PlayerState[count];
        PlayerState[] p2 = new PlayerState[count];
        int n = 0;
        try (ReplayWorld world = hosts.openColdReplay(launch)) {
            for (; n < count; n++) {
                RouteSample after = world.step(rows.row(n)).after();
                xs[n] = after.p1().centreX();
                ys[n] = after.p1().centreY();
                epochs[n] = after.epoch();
                p1[n] = PlayerState.of(after.p1());
                p2[n] = after.p2().map(PlayerState::of).orElse(null);
                if (after.p1().dead()) {
                    n++;
                    break;
                }
            }
        }
        return new ReferencePath(launch, rows.slice(0, n), Arrays.copyOf(xs, n), Arrays.copyOf(ys, n),
                Arrays.copyOf(epochs, n), hosts.environment(), 0, n - 1, Arrays.copyOf(p1, n), Arrays.copyOf(p2, n));
    }

    /**
     * A reference recorded by an earlier replay: P1's centre and boundary epoch after each of
     * {@code rows}, as a cold replay of those rows from {@code launch} produced them on
     * {@code environment} (for example a route's own rows on the engine they were authored
     * against). A death row, if any, is the last. The arrays are copied.
     */
    public static ReferencePath recorded(LaunchContract launch, InputTape rows, int[] xs, int[] ys, long[] epochs,
                                         String environment) {
        Objects.requireNonNull(launch, "launch");
        Objects.requireNonNull(environment, "environment");
        if (xs.length == 0 || rows.size() != xs.length || ys.length != xs.length || epochs.length != xs.length) {
            throw new IllegalArgumentException("a recorded reference needs one position and epoch per row: " + rows.size()
                    + "/" + xs.length + "/" + ys.length + "/" + epochs.length);
        }
        return new ReferencePath(launch, rows, xs.clone(), ys.clone(), epochs.clone(), environment, 0, xs.length - 1);
    }

    /**
     * The same path with each player's exact post-row state, one entry per row. {@code p2} is
     * empty for a solo reference; otherwise a null entry marks a row without a sidekick. P1's
     * state must agree with the recorded centre.
     */
    public ReferencePath withStates(List<PlayerState> p1, List<PlayerState> p2) {
        Objects.requireNonNull(p1, "p1");
        Objects.requireNonNull(p2, "p2");
        if (p1.size() != xs.length || (!p2.isEmpty() && p2.size() != xs.length)) {
            throw new IllegalArgumentException("one state per row: " + xs.length + " rows, " + p1.size() + "/" + p2.size());
        }
        PlayerState[] a = new PlayerState[xs.length];
        PlayerState[] b = new PlayerState[xs.length];
        for (int i = 0; i < xs.length; i++) {
            a[i] = Objects.requireNonNull(p1.get(i), "p1 state");
            if (a[i].centreX() != xs[i] || a[i].centreY() != ys[i]) {
                throw new IllegalArgumentException("P1 state at row " + i + " is not the recorded centre");
            }
            b[i] = p2.isEmpty() ? null : p2.get(i);
        }
        return retainParticipants(new ReferencePath(launch, source, xs, ys, epochs, environment, from, to, a, b));
    }

    private int[] participantXs, participantYs;
    private boolean[] participantPresent;

    /** Copied P2 centre observations, when a two-human reference must retain both lanes. */
    public ReferencePath withParticipantPositions(int[] xs, int[] ys) {
        boolean[] present=new boolean[xs.length];Arrays.fill(present,true);
        return withParticipantPositions(xs,ys,present);
    }

    public ReferencePath withParticipantPositions(int[] xs,int[] ys,boolean[] present) {
        if(xs.length!=this.xs.length || ys.length!=xs.length || present.length!=xs.length) throw new IllegalArgumentException("one P2 centre/presence per row");
        var copy=new ReferencePath(launch,source,this.xs,this.ys,epochs,environment,from,to,p1States,p2States);
        copy.participantXs=xs.clone();copy.participantYs=ys.clone();copy.participantPresent=present.clone();return copy;
    }

    private ReferencePath retainParticipants(ReferencePath copy) {
        copy.participantXs=participantXs;copy.participantYs=participantYs;copy.participantPresent=participantPresent;
        return copy;
    }

    double participantDistance(RouteSample sample,int index) {
        if(participantXs==null) return 0;
        if(!participantPresent[index]) return sample.p2().isEmpty()?0:1_000_000;
        if(sample.p2().isEmpty()) return 1_000_000;
        var p=sample.p2().get();
        int dy=participantYs[index]-p.centreY();int range=sample.verticalWrap();
        if(range>0) {dy=Math.floorMod(dy,range);if(dy>range/2)dy-=range;}
        return Math.hypot(participantXs[index]-p.centreX(),dy);
    }

    /** Whether the reference carries exact player states. */
    public boolean hasStates() {
        return p1States != null;
    }

    /** Whether the exact states include a sidekick. */
    boolean hasSidekickStates() {
        return sidekickStates;
    }

    /**
     * Distance of a sample's players from the recorded states at {@code index}: P1, plus P2 when
     * {@code sidekick} is set and the reference recorded one. Zero means an exact match; a
     * sidekick present on only one side counts as a mismatch.
     */
    long stateDistance(RouteSample sample, int index, boolean sidekick) {
        if (p1States == null) throw new IllegalStateException("reference has no exact states");
        long d = p1States[index].distance(sample.p1());
        if (!sidekick || !sidekickStates) return d;
        PlayerState p2 = p2States[index];
        if (p2 != null) return d + sample.p2().map(p2::distance).orElse(1_000_000L);
        return sample.p2().isPresent() ? d + 1_000_000L : d;
    }

    /** The same path, followed from index {@code from} until index {@code to} is reached. */
    public ReferencePath window(int from, int to) {
        return retainParticipants(new ReferencePath(launch, source, xs, ys, epochs, environment, from, to, p1States, p2States));
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
