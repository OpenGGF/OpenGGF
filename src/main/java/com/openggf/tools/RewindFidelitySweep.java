package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dense rewind-fidelity sweep over a controller-only route movie in the production capture harness.
 *
 * <p>Every {@code stride} inputs it captures the whole rewind registry, plays {@code horizon} inputs,
 * restores, checks that the restore reproduces the capture, replays the same inputs and compares P1
 * rows plus every registry key ({@link RewindSnapshotDiff}). Live play then continues from the
 * replayed state. Checkpoints whose horizon crosses an act, level, object-manager, mode or pending
 * fade-completion boundary are skipped: live rewind never restores across them. The movie only
 * supplies controller input; no world state is seeded from it.
 *
 * <p>Origin: mhzrewind lane, 2026-10-16 (fixes in {@code 902ba8a10}). Forty-five-frame spot checks
 * passed while this sweep located the MHZ mushroom-cap, curled-vine, catapult and platform rider
 * restore gaps, a dangling Mushmeanie shell parent and a lost act-carried shield. Usage:
 * {@code RewindFidelitySweep ROM WIDTH MAIN SIDEKICK|none MOVIE ZONE:ACT FROM TO STRIDE HORIZON}.
 */
public final class RewindFidelitySweep {
    /** One checkpoint whose restore or replay differed from the live run. */
    public record Drift(int checkpoint, boolean restoreIdentical, int firstRowFrame, String liveRow,
                        String replayRow, List<String> differences) { }

    /** Sweep totals; {@code refusals} are checkpoints whose registry capture threw. */
    public record Report(int checks, int skips, List<String> refusals, List<Drift> drifts) {
        public boolean clean() {
            return refusals.isEmpty() && drifts.isEmpty();
        }
    }

    private RewindFidelitySweep() {
    }

    /**
     * Sweeps checkpoints {@code from, from+stride, ...} up to {@code to} on a session already booted
     * at input 0. Stops after {@code maxDrifts} drifts or when {@code horizon} inputs no longer fit.
     */
    public static Report sweep(GameplayCaptureSession session, List<Bk2FrameInput> movie, int from, int to,
                               int stride, int horizon, int maxDrifts) {
        RewindRegistry registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var drifts = new ArrayList<Drift>();
        var refusals = new ArrayList<String>();
        int checks = 0, skips = 0;
        int pos = -1;
        while (pos < from) session.step(movie.get(++pos));
        while (pos <= to && drifts.size() < maxDrifts) {
            int h = Math.min(horizon, movie.size() - 1 - pos);
            if (h <= 0) break;
            CompositeSnapshot saved;
            try {
                saved = registry.capture();
            } catch (RuntimeException refused) {
                refusals.add(pos + ": " + refused.getMessage());
                for (int n = 0; n < stride && pos + 1 < movie.size(); n++) session.step(movie.get(++pos));
                continue;
            }
            var level = GameServices.level();
            Object level0 = level.getCurrentLevel(), manager0 = level.getObjectManager();
            int act0 = level.getCurrentAct();
            var live = new ArrayList<String>();
            boolean crossed = false;
            for (int n = 1; n <= h; n++) {
                session.step(movie.get(pos + n));
                live.add(row(session));
                if (level.getCurrentLevel() != level0 || level.getObjectManager() != manager0
                        || level.getCurrentAct() != act0 || session.loop().getCurrentGameMode() != GameMode.LEVEL
                        || GameServices.fade().hasPendingCompletion()) {
                    pos += n;
                    crossed = true;
                    break;
                }
            }
            if (crossed) {
                skips++;
                continue;
            }
            CompositeSnapshot expected = registry.capture();
            checks++;
            registry.restore(saved);
            List<String> restoreDiffs = differences(saved, registry.capture());
            session.restoreInputHistory(movie.get(pos));
            var replay = new ArrayList<String>();
            for (int n = 1; n <= h; n++) {
                session.step(movie.get(pos + n));
                replay.add(row(session));
            }
            int first = -1;
            for (int n = 0; n < h; n++) {
                if (!live.get(n).equals(replay.get(n))) {
                    first = n;
                    break;
                }
            }
            List<String> endDiffs = differences(expected, registry.capture());
            if (!restoreDiffs.isEmpty() || first >= 0 || !endDiffs.isEmpty()) {
                var all = new ArrayList<>(restoreDiffs);
                all.addAll(endDiffs);
                drifts.add(new Drift(pos, restoreDiffs.isEmpty(), first < 0 ? -1 : pos + 1 + first,
                        first < 0 ? "" : live.get(first), first < 0 ? "" : replay.get(first),
                        List.copyOf(all.subList(0, Math.min(all.size(), 24)))));
            }
            // Continue live from the checkpoint, so later checks start from an unreplayed timeline.
            registry.restore(saved);
            session.restoreInputHistory(movie.get(pos));
            int advance = Math.min(stride, h);
            for (int n = 1; n <= advance; n++) session.step(movie.get(pos + n));
            pos += advance;
        }
        return new Report(checks, skips, List.copyOf(refusals), List.copyOf(drifts));
    }

    /** P1 state compared after each replayed input. */
    public static String row(GameplayCaptureSession session) {
        var p = session.player();
        return p.getCentreX() + "," + p.getCentreY() + " sub " + p.getXSubpixelRaw() + "," + p.getYSubpixelRaw()
                + " v " + p.getXSpeed() + "," + p.getYSpeed() + " g " + p.getGSpeed() + " air " + p.getAir()
                + " onObj " + p.isOnObject() + " rng " + Long.toHexString(GameServices.rng().getSeed())
                + " vint " + GameServices.level().getObjectManager().getVblaCounter();
    }

    private static List<String> differences(CompositeSnapshot expected, CompositeSnapshot actual) {
        var out = new ArrayList<String>();
        if (!expected.entries().keySet().equals(actual.entries().keySet())) {
            out.add("keys " + expected.entries().keySet() + " vs " + actual.entries().keySet());
        }
        for (String key : expected.entries().keySet()) {
            out.addAll(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 10) {
            throw new IllegalArgumentException("Usage: RewindFidelitySweep ROM WIDTH MAIN SIDEKICK|none MOVIE "
                    + "ZONE:ACT FROM TO STRIDE HORIZON");
        }
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[1]), args[2],
                "none".equalsIgnoreCase(args[3]) ? "" : args[3], "off", null, null, null);
        Bk2Movie movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of(args[4]));
        var inputs = new ArrayList<Bk2FrameInput>();
        for (int i = 0; i < movie.getFrameCount(); i++) inputs.add(movie.getFrame(i));
        String[] boot = args[5].split(":");
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(Path.of(args[0]), Integer.parseInt(boot[0]), Integer.parseInt(boot[1]), settings);
            var report = sweep(session, inputs, Integer.parseInt(args[6]), Integer.parseInt(args[7]),
                    Integer.parseInt(args[8]), Integer.parseInt(args[9]), 20);
            System.out.println("checks=" + report.checks() + " skips=" + report.skips()
                    + " refusals=" + report.refusals().size() + " drifts=" + report.drifts().size());
            report.refusals().forEach(r -> System.out.println("REFUSED " + r));
            report.drifts().forEach(d -> System.out.println("DRIFT " + d));
        }
    }
}
