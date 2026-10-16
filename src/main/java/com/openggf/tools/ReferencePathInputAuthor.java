package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Authors controller-only input that follows a reference P1 path through traversal whose
 * object phases differ from the reference run (RNG- or V_int-phased platforms, clouds and
 * columns), where replaying the reference inputs with time-shift resyncs drifts off.
 *
 * <p>Progress is the latest reference index within {@code radius} pixels of P1
 * (vertical-wrap aware). While P1 tracks the reference within six pixels the search simply
 * plays the reference inputs. Otherwise it scores candidates over a horizon from a
 * whole-registry rewind snapshot — the reference inputs at several time shifts and delays,
 * reactive steer/jump policies toward a target {@code 40} indices ahead, wait-then-jump and
 * spin-dash variants — by the progress they reach (half the best strict progress, half the nearest
 * reference index at the horizon end), with penalties for hurt, ring loss and getting lost;
 * it commits the first {@code commit} inputs of the best and repeats. A rising predicted reach
 * counts as progress so slow lifts are not abandoned. Restores never cross a real load; the
 * search stops at one; deaths and touched giant rings are never committed.
 *
 * <p>Origin: cold LRZ→HPZ→SSZ campaign, lane cold3 (2026-10-15), where it carried the cold
 * pair through Sky Sanctuary act 1's cloud/vertical-wrap section to the Mecha arena after
 * two lanes' time-shift follows of the direct movie stopped there. Inputs: ROM, width,
 * roster, a cold prefix movie and length (the prefix may cross real loads), a reference movie
 * with its boot and roster, the reference index window and an empty output directory. The
 * reference run supplies only P1 positions for scoring; it never seeds the authored world.
 * Selected inputs need a separate uninterrupted replay. Radii, horizons and scores are
 * authoring choices, never gameplay rules.
 */
public final class ReferencePathInputAuthor {
    /** Search shape; the defaults are the values used for the cold campaign. */
    public record Params(int radius, int horizon, int commit, int look, int stuckLimit) {
        public static final Params DEFAULT = new Params(28, 120, 20, 300, 20);
    }

    public enum Stop { REACHED, STUCK, LEVEL_CHANGE, DEAD, EXPIRED }

    public record Result(Stop stop, int progress, int inputs) { }

    private static final int SYNC_TOLERANCE = 6;
    private static final int POLICY_LEAD = 40;
    private static final int POLICIES = 19;

    private record Checkpoint(CompositeSnapshot snapshot, Bk2FrameInput previous, int size) { }

    private record Trial(double value, int maxProg, boolean levelChange) { }

    private interface Candidate {
        Bk2FrameInput pad(int t, int prog);
    }

    private final GameplayCaptureSession session;
    private final List<Bk2FrameInput> inputs;
    private final List<int[]> reference;
    private final StringBuilder log;

    /**
     * @param inputs    the run's committed inputs so far; the search appends to it
     * @param reference reference P1 centre positions, one {x, y} per reference input index
     */
    public ReferencePathInputAuthor(GameplayCaptureSession session, List<Bk2FrameInput> inputs,
                                    List<int[]> reference, StringBuilder log) {
        this.session = session;
        this.inputs = inputs;
        this.reference = reference;
        this.log = log;
    }

    public static void main(String[] args) throws Exception {
        int bootZone = 9, bootAct = 0;
        if (args.length >= 2 && args[0].equals("--boot")) {
            int[] boot = boot(args[1]);
            bootZone = boot[0];
            bootAct = boot[1];
            args = java.util.Arrays.copyOfRange(args, 2, args.length);
        }
        if (args.length != 11) {
            throw new IllegalArgumentException("Usage: ReferencePathInputAuthor [--boot ZONE:ACT] ROM WIDTH MAIN "
                    + "SIDEKICK|none PREFIX.bk2 PREFIX_LENGTH REFERENCE.bk2 REF_ZONE:ACT[/MAIN/SIDEKICK|none] "
                    + "FROM_INDEX TO_INDEX OUT_DIR");
        }
        Path rom = Path.of(args[0]);
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[1]), args[2],
                "none".equalsIgnoreCase(args[3]) ? "" : args[3], "off", null, null, null);
        String[] refSpec = args[7].split("/");
        var refSettings = refSpec.length < 3 ? settings : new GameplayCaptureSession.Settings(settings.width(),
                refSpec[1], "none".equalsIgnoreCase(refSpec[2]) ? "" : refSpec[2], "off", null, null, null);
        int[] refBoot = boot(refSpec[0]);
        var loader = new Bk2MovieLoader();
        var referenceMovie = loader.loadMovieOrInputLog(Path.of(args[6]));
        int to = Integer.parseInt(args[9]);
        var reference = referencePath(rom, refSettings, refBoot[0], refBoot[1], referenceMovie,
                Math.min(referenceMovie.getFrameCount(), to + Params.DEFAULT.look() + 1));
        Path output = Path.of(args[10]);
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty: " + output);
        }
        var prefixMovie = loader.loadMovieOrInputLog(Path.of(args[4]));
        int prefix = Integer.parseInt(args[5]);
        var inputs = new ArrayList<Bk2FrameInput>();
        var log = new StringBuilder();
        Result result;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, bootZone, bootAct, settings);
            for (int f = 0; f < prefix; f++) {
                var pad = prefixMovie.getFrame(f);
                session.step(pad);
                session.renderFrame();
                inputs.add(pad);
                if (session.player().getDead()) throw new IllegalStateException("Prefix died at " + f);
            }
            result = new ReferencePathInputAuthor(session, inputs, reference, log)
                    .seek(referenceMovie, Integer.parseInt(args[8]), to, Params.DEFAULT, () -> false);
        }
        String script = GameplayInputBranchTool.prefixScript(
                new Bk2Movie(null, "Input Log.txt", Map.of(), inputs, 0), inputs.size());
        Files.writeString(output.resolve("input.script"), script);
        InputLogAuthorTool.author(script, output.resolve("input.bk2"), "s3k");
        Files.writeString(output.resolve("result.txt"), result + "\nROM=" + rom + "\nsettings=" + settings
                + "\nprefix=" + args[4] + " " + prefix + "\nreference=" + args[6] + " " + args[7]
                + "\nwindow=" + args[8] + ".." + to + "\nFresh uninterrupted replay required.\n" + log);
        System.out.println("Stop: " + result);
    }

    /** Replays a reference movie from a fresh boot and records P1 centre positions per input. */
    public static List<int[]> referencePath(Path rom, GameplayCaptureSession.Settings settings, int zone, int act,
                                            Bk2Movie movie, int count) throws Exception {
        var path = new ArrayList<int[]>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, zone, act, settings);
            for (int f = 0; f < count; f++) {
                session.step(movie.getFrame(f));
                GameServices.graphics().runPendingRenderThreadTasks();
                path.add(new int[]{session.player().getCentreX(), session.player().getCentreY()});
                if (session.player().getDead()) break;
            }
        }
        return path;
    }

    /** Searches from the current state until reference index {@code kTo}, a stop or {@code expired}. */
    public Result seek(Bk2Movie movie, int kFrom, int kTo, Params p, BooleanSupplier expired) {
        var level = GameServices.level().getCurrentLevel();
        int k = progressStrict(kFrom - 60, kFrom + p.look(), p.radius());
        if (k < 0) k = kFrom;
        int stuck = 0, bestK = k, predicted = k;
        Checkpoint bestCp = current();
        while (k < kTo) {
            if (expired.getAsBoolean()) return new Result(Stop.EXPIRED, k, inputs.size());
            // Follow the reference inputs while P1 tracks the reference closely.
            var base = current();
            int n = 0;
            boolean inSync = true;
            for (; n < p.commit() && k + 1 + n < movie.getFrameCount(); n++) {
                if (!step(movie.getFrame(k + 1 + n), level)) {
                    if (levelChanged(level)) return new Result(Stop.LEVEL_CHANGE, k + 1 + n, inputs.size());
                    inSync = false;
                    break;
                }
                int[] r = reference.get(Math.min(reference.size() - 1, k + 1 + n));
                if (wrapDist(r[0], r[1]) > SYNC_TOLERANCE || session.player().isHurt()) { inSync = false; break; }
            }
            if (inSync && n > 0) {
                k += n;
                if (k > bestK) { bestK = k; bestCp = current(); }
                stuck = 0;
                continue;
            }
            restore(base);
            boolean wide = stuck >= p.stuckLimit() / 2;
            int horizon = wide ? p.horizon() * 2 : p.horizon();
            int commit = wide ? p.commit() * 2 : p.commit();
            Trial best = null;
            Candidate chosen = null;
            for (var candidate : candidates(movie, k, kTo, wide)) {
                restore(base);
                var trial = trial(candidate, k, kTo, horizon, p, level);
                // A trial that crossed a real load is kept: no outgoing snapshot may be restored.
                if (trial.levelChange()) return new Result(Stop.LEVEL_CHANGE, k, inputs.size());
                if (best == null || trial.value() > best.value()) { best = trial; chosen = candidate; }
            }
            restore(base);
            int prog = k;
            for (int t = 0; t < commit; t++) {
                if (!step(chosen.pad(t, prog), level)) {
                    restore(bestCp);
                    return new Result(Stop.DEAD, bestK, inputs.size());
                }
                int q = progressStrict(prog - 10, prog + p.look(), p.radius());
                if (q > prog) prog = q;
            }
            if (prog > k) { k = prog; stuck = 0; }
            else if (best.maxProg() > predicted) stuck = 0;
            else stuck++;
            predicted = Math.max(predicted, best.maxProg());
            if (k > bestK) { bestK = k; bestCp = current(); }
            if (stuck >= p.stuckLimit()) {
                log.append("stuck at ").append(k).append("; restoring best ").append(bestK).append('\n');
                restore(bestCp);
                return new Result(Stop.STUCK, bestK, inputs.size());
            }
        }
        return new Result(Stop.REACHED, k, inputs.size());
    }

    private List<Candidate> candidates(Bk2Movie movie, int k, int kTo, boolean wide) {
        var list = new ArrayList<Candidate>();
        int[] shifts = wide ? new int[]{0, -3, 3, -8, 8, -16, 16, -30, 30} : new int[]{0, -4, 4, -12, 12};
        for (int s : shifts) {
            int from = k + 1 + s;
            list.add((t, prog) -> movie.getFrame(Math.max(0, Math.min(movie.getFrameCount() - 1, from + t))));
        }
        for (int w : wide ? new int[]{6, 12, 24, 40, 64} : new int[]{8, 20}) {
            int from = k + 1;
            list.add((t, prog) -> t < w ? null
                    : movie.getFrame(Math.max(0, Math.min(movie.getFrameCount() - 1, from + t - w))));
        }
        for (int policy = 0; policy < POLICIES; policy++) {
            int pol = policy;
            list.add((t, prog) -> {
                int[] r = target(prog, kTo);
                return policyPad(pol, t, r[0]);
            });
        }
        for (int charges : wide ? new int[]{1, 2, 4} : new int[]{1, 3}) {
            list.add(new SpinDash(charges, false, kTo));
            if (wide) list.add(new SpinDash(charges, true, kTo));
        }
        int[] waits = wide ? new int[]{0, 8, 16, 24, 32, 44, 56, 72, 96} : new int[]{0, 12, 24, 40};
        for (int dirMode = 0; dirMode < 3; dirMode++) {
            for (int w : waits) {
                list.add(new WaitThenJump(dirMode, w, kTo));
            }
        }
        return list;
    }

    /** Holds toward the target (or neutral), jumps once grounded after {@code wait}, holds A 24 inputs. */
    private final class WaitThenJump implements Candidate {
        private final int dirMode, wait, kTo;
        private int jumpAt = -1;

        WaitThenJump(int dirMode, int wait, int kTo) { this.dirMode = dirMode; this.wait = wait; this.kTo = kTo; }

        @Override
        public Bk2FrameInput pad(int t, int prog) {
            if (t == 0) jumpAt = -1;
            int[] r = target(prog, kTo);
            var player = session.player();
            int dx = r[0] - player.getCentreX();
            int toward = Math.abs(dx) <= 2 ? 0 : dx > 0 ? 8 : 4;
            int dir = dirMode == 0 ? toward : dirMode == 1 ? 0 : (t < wait ? 0 : toward);
            if (jumpAt < 0 && t >= wait && !player.getAir()) jumpAt = t;
            boolean jump = jumpAt >= 0 && t - jumpAt < 24;
            return new Bk2FrameInput(0, dir, jump ? 1 : 0, false, "");
        }
    }

    /**
     * Faces the target for one input, holds Down until P1 stops and ducks, charges with
     * {@code charges} A presses, then releases Down (neutral, or holding toward the target).
     */
    private final class SpinDash implements Candidate {
        private static final int DUCK_INPUTS = 12;
        private final int charges, kTo;
        private final boolean holdToward;

        SpinDash(int charges, boolean holdToward, int kTo) {
            this.charges = charges; this.holdToward = holdToward; this.kTo = kTo;
        }

        @Override
        public Bk2FrameInput pad(int t, int prog) {
            int[] r = target(prog, kTo);
            int toward = r[0] >= session.player().getCentreX() ? 8 : 4;
            if (t == 0) return new Bk2FrameInput(0, toward, 0, false, "");
            if (t < DUCK_INPUTS) return new Bk2FrameInput(0, 2, 0, false, "");
            int c = t - DUCK_INPUTS;
            if (c < charges * 2 + 2) return new Bk2FrameInput(0, 2, c < charges * 2 && c % 2 == 0 ? 1 : 0, false, "");
            return new Bk2FrameInput(0, holdToward ? toward : 0, 0, false, "");
        }
    }

    private int[] target(int prog, int kTo) {
        return reference.get(Math.min(kTo, Math.min(reference.size() - 1, prog + POLICY_LEAD)));
    }

    private Trial trial(Candidate c, int k, int kTo, int horizon, Params p, Object level) {
        int rings = session.player().getRingCount();
        int prog = k;
        boolean hurt = false;
        for (int t = 0; t < horizon; t++) {
            if (!step(c.pad(t, prog), level, false)) {
                return new Trial(-1e9, prog, levelChanged(level));
            }
            hurt |= session.player().isHurt();
            int q = progressStrict(prog - 10, prog + p.look(), p.radius());
            if (q > prog) prog = q;
            if (prog >= kTo) break;
        }
        double[] near = nearest(k - 10, Math.max(prog, k) + p.look());
        int lost = rings - session.player().getRingCount();
        double value = 0.5 * prog + 0.5 * (near[1] <= 64 ? near[0] : k - 100);
        if (hurt) value -= 400;
        if (lost > 0) value -= 20 * lost;
        value -= 0.05 * near[1];
        return new Trial(value, prog, false);
    }

    /** Latest reference index in [lo, hi] within {@code radius} of P1, or -1. */
    int progressStrict(int lo, int hi, int radius) {
        lo = Math.max(0, lo);
        hi = Math.min(reference.size() - 1, hi);
        for (int i = hi; i >= lo; i--) {
            int[] r = reference.get(i);
            if (wrapDist(r[0], r[1]) <= radius) return i;
        }
        return -1;
    }

    private double[] nearest(int lo, int hi) {
        lo = Math.max(0, lo);
        hi = Math.min(reference.size() - 1, hi);
        double best = Double.MAX_VALUE;
        int idx = lo;
        for (int i = lo; i <= hi; i++) {
            int[] r = reference.get(i);
            double d = wrapDist(r[0], r[1]);
            if (d <= best) { best = d; idx = i; }
        }
        return new double[]{idx, best};
    }

    /** Reactive pad: direction mode (hold toward, steer, neutral, hold away) by jump mode. */
    private Bk2FrameInput policyPad(int policy, int t, int tx) {
        var player = session.player();
        var prev = previous();
        boolean held = prev != null && (prev.p1ActionMask() & 1) != 0;
        int dx = tx - player.getCentreX();
        int dirMode, jumpMode;
        if (policy == 12) { dirMode = 2; jumpMode = 0; }
        else if (policy > 12) { dirMode = (policy - 13) / 2; jumpMode = 4 + (policy - 13) % 2; }
        else { dirMode = policy / 4 == 2 ? 3 : policy / 4; jumpMode = policy % 4; }
        int toward = dx > 0 ? 8 : dx < 0 ? 4 : 0;
        int dir = 0;
        if (dirMode == 0) dir = Math.abs(dx) > 2 ? toward : 0;
        else if (dirMode == 1) {
            double desired = Math.max(-6, Math.min(6, dx / 8.0));
            double speed = player.getXSpeed() / 256.0;
            dir = speed > desired + 0.2 ? 4 : speed < desired - 0.2 ? 8 : 0;
        } else if (dirMode == 3) dir = toward == 8 ? 4 : toward == 4 ? 8 : 0;
        boolean jump = switch (jumpMode) {
            case 1 -> player.getAir() ? held : !held;
            case 2 -> t == 0 ? !player.getAir() && !held : held && player.getAir();
            case 3 -> player.getAir() ? held && t % 40 < 8 : !held;
            case 4 -> true;
            case 5 -> t % 10 < 5;
            default -> false;
        };
        return new Bk2FrameInput(0, dir, jump ? 1 : 0, false, "");
    }

    private double wrapDist(int x, int y) {
        var player = session.player();
        int dx = x - player.getCentreX(), dy = y - player.getCentreY();
        var camera = GameServices.camera();
        if (camera.isVerticalWrapEnabled()) {
            int range = camera.getVerticalWrapRange();
            dy = ((dy % range) + range) % range;
            if (dy > range / 2) dy -= range;
        }
        return Math.hypot(dx, dy);
    }

    /** Steps one committed input (drawn); false on death or a real load. */
    private boolean step(Bk2FrameInput pad, Object level) {
        return step(pad, level, true);
    }

    /** Trials skip scene drawing but still run render-thread work later ticks need. */
    private boolean step(Bk2FrameInput pad, Object level, boolean draw) {
        var copy = new Bk2FrameInput(inputs.size(), pad == null ? 0 : pad.p1InputMask(),
                pad == null ? 0 : pad.p1ActionMask(), false, pad == null ? 0 : pad.p2InputMask(),
                pad == null ? 0 : pad.p2ActionMask(), false, "");
        session.step(copy);
        if (draw) session.renderFrame(); else GameServices.graphics().runPendingRenderThreadTasks();
        inputs.add(copy);
        return !session.player().getDead() && !levelChanged(level) && !enteringBonus();
    }

    /**
     * A touched giant ring starts its flash before the special stage registers its own rewind
     * subsystem; a route search treats that like a death (never committed, never restored past).
     */
    private static boolean enteringBonus() {
        return !GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.Sonic3kSSEntryFlashObjectInstance.class).isEmpty();
    }

    private static boolean levelChanged(Object level) {
        return GameServices.level().getCurrentLevel() != level;
    }

    private Bk2FrameInput previous() { return inputs.isEmpty() ? null : inputs.getLast(); }

    private Checkpoint current() {
        return new Checkpoint(SessionManager.getCurrentGameplayMode().getRewindRegistry().capture(),
                previous(), inputs.size());
    }

    private void restore(Checkpoint c) {
        SessionManager.getCurrentGameplayMode().getRewindRegistry().restore(c.snapshot());
        session.restoreInputHistory(c.previous());
        while (inputs.size() > c.size()) inputs.removeLast();
    }

    private static int[] boot(String spec) {
        String[] parts = spec.split(":");
        return new int[]{Integer.decode(parts[0]), Integer.parseInt(parts[1]) - 1};
    }
}
