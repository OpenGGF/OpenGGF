package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Authors controller-only SSZ boss inputs by trying policies from engine rewind snapshots.
 * Origin: September 2026 S&K campaign, the solo Tails routes delivered in75c9caf16.
 * Inputs: ROM, viewport width, leader/sidekick, cold movie/prefix, encounter and empty output
 * directory. No position, health, ring, clock or trace seeds. The cold prefix preserves both
 * pads; the authored tail controls P1 with P2 neutral. Every selected route needs a separate
 * uninterrupted GameplayCaptureTool replay. Exploratory restore is not native parity evidence.
 * Search horizons, steering gains and scores below belong to the author, never gameplay rules.
 */
public final class SszBossInputAuthorTool {
    private static final int HORIZON = 180;
    private static final int COMMIT_INPUTS = 30;
    private static final int POLICY_COUNT = 12;
    private static final int[] OFFSETS = {0, -24, 24, -48, 48, 0, 0, 0, 0, 0, 0, 0};

    private SszBossInputAuthorTool() { }

    enum Stop { DEFEATED, DEAD, LIMIT_REACHED }
    record Result(Stop stop, int frames) { }
    record Boss(int x, int health) { }

    enum Encounter {
        GHZ(512), MTZ(5920), MECHA(6720);

        final int centre;
        Encounter(int centre) { this.centre = centre; }

        Boss observe() {
            var objects = GameServices.level().getObjectManager();
            return switch (this) {
                case GHZ -> objects.activeObjectsOfType(SszGhzBossObjectInstance.class).stream()
                        .findFirst().map(b -> new Boss(b.getX(), b.hitsRemainingForTest())).orElse(null);
                case MTZ -> objects.activeObjectsOfType(SszMtzBossObjectInstance.class).stream()
                        .findFirst().map(b -> new Boss(b.getX(), b.hitsRemainingForTest())).orElse(null);
                case MECHA -> objects.activeObjectsOfType(SszMechaSonicObjectInstance.class).stream()
                        .findFirst().map(b -> new Boss(b.getX(), b.getCollisionProperty())).orElse(null);
            };
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 8 || args.length > 9) {
            throw new IllegalArgumentException("Usage: SszBossInputAuthorTool ROM WIDTH MAIN SIDEKICK "
                    + "INPUT PREFIX ghz|mtz|mecha OUT_DIR [MAX_NEW_INPUTS]");
        }
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[1]), args[2],
                "none".equalsIgnoreCase(args[3]) ? "" : args[3], "off", null, null, null);
        var result = run(Path.of(args[0]), settings, Path.of(args[4]), Integer.parseInt(args[5]),
                Encounter.valueOf(args[6].toUpperCase(Locale.ROOT)), Path.of(args[7]),
                args.length == 9 ? Integer.parseInt(args[8]) : 3000);
        System.out.println("Stop: " + result.stop() + "; complete input frames: " + result.frames());
    }

    static Result run(Path rom, GameplayCaptureSession.Settings settings, Path input, int prefix,
                      Encounter encounter, Path output, int maxInputs) throws Exception {
        if (maxInputs <= 0) throw new IllegalArgumentException("MAX_NEW_INPUTS must be positive");
        if (settings.startX() != null || settings.startY() != null || !"off".equals(settings.donor())) {
            throw new IllegalArgumentException("Author requires a cold native entry");
        }
        int end = Math.addExact(prefix, maxInputs);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(input);
        var script = new StringBuilder(GameplayInputBranchTool.prefixScript(movie, prefix));
        var rows = new StringBuilder(GameplayCaptureSession.stateHeader()).append('\n');
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty: " + output);
        }
        Result result;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 10, 0, settings);
            var level = GameServices.level().getCurrentLevel();
            Bk2FrameInput previous = null;
            int frame = 0;
            for (; frame < prefix; frame++) {
                previous = movie.getFrame(frame);
                session.step(previous);
                session.render();
                requireSameLevel(level);
                rows.append(session.stateLine(frame, previous)).append('\n');
                if (session.player().getDead()) throw new IllegalStateException("Prefix died at " + frame);
            }
            while (frame < end && !session.player().getDead() && !defeated(encounter)) {
                var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                var saved = registry.capture();
                var oldInput = previous;
                var before = encounter.observe();
                int health = before == null ? 0 : before.health();
                double best = -Double.MAX_VALUE;
                int chosen = 0;
                // Before allocation, a full-health spawn must not lose to avoiding the trigger.
                int count = health > 0 ? POLICY_COUNT : 1;
                for (int policy = 0; policy < count; policy++) {
                    registry.restore(saved);
                    session.restoreInputHistory(oldInput);
                    var trialInput = oldInput;
                    for (int n = 0; n < HORIZON; n++) {
                        trialInput = buttons(session, encounter, frame + n, policy, trialInput);
                        session.step(trialInput);
                        session.render();
                        requireSameLevel(level); // Never restore an outgoing snapshot into a new load.
                        if (session.player().getDead() || defeated(encounter)) break;
                    }
                    var after = encounter.observe();
                    double value = score(session.player().getDead(), health,
                            after == null ? null : after.health(), session.player().getRingCount(),
                            session.player().isHurt(), after == null ? 0
                                    : Math.abs(after.x() - session.player().getCentreX()));
                    if (value > best) { best = value; chosen = policy; }
                }
                registry.restore(saved);
                session.restoreInputHistory(oldInput);
                previous = oldInput;
                for (int n = 0; n < COMMIT_INPUTS && frame < end; n++) {
                    previous = buttons(session, encounter, frame, chosen, previous);
                    session.step(previous);
                    session.render();
                    requireSameLevel(level);
                    append(script, previous);
                    rows.append(session.stateLine(frame++, previous)).append('\n');
                    if (session.player().getDead() || defeated(encounter)) break;
                }
                var boss = encounter.observe();
                System.out.printf("CHOICE frame=%d policy=%d score=%.2f hp=%d rings=%d%n",
                        frame, chosen, best, boss == null ? -1 : boss.health(), session.player().getRingCount());
                write(output, script, rows);
            }
            result = new Result(session.player().getDead() ? Stop.DEAD
                    : defeated(encounter) ? Stop.DEFEATED : Stop.LIMIT_REACHED, frame);
        }
        write(output, script, rows);
        InputLogAuthorTool.author(script.toString(), output.resolve("input.bk2"), "s3k");
        Files.writeString(output.resolve("result.txt"), result + "\nROM=" + rom + "\nsettings=" + settings
                + "\ninput=" + input + "\nprefix=" + prefix + "\nencounter=" + encounter
                + "\nmaxNewInputs=" + maxInputs + "\nFresh uninterrupted replay required.\n");
        return result;
    }

    private static void requireSameLevel(Object level) {
        if (GameServices.level().getCurrentLevel() != level) {
            throw new IllegalStateException("Author crossed a level load; use a fresh session");
        }
    }

    private static boolean defeated(Encounter encounter) {
        var boss = encounter.observe();
        return boss != null && boss.health() == 0;
    }

    static double score(boolean dead, int before, Integer after, int rings, boolean hurt, int distance) {
        if (dead) return -1_000_000;
        if (before > 0 && after == null) return -500_000; // Absence is not observed defeat.
        int damage = after == null ? 0 : Math.max(0, before - after);
        return damage * 10_000 + (after != null && after == 0 ? 50_000 : 0)
                + Math.min(3, rings) * 150 - (hurt ? 100 : 0) - Math.min(250, distance) * 0.03;
    }

    private static Bk2FrameInput buttons(GameplayCaptureSession session, Encounter encounter,
                                       int frame, int policy, Bk2FrameInput previous) {
        var player = session.player();
        var boss = encounter.observe();
        int target = encounter.centre;
        if (boss != null && boss.health() > 0) {
            target = boss.x() + OFFSETS[policy];
            if (policy == 5 || policy >= 10) target = encounter.centre;
            if (policy == 6) target = encounter.centre - 136;
            if (policy == 7) target = encounter.centre + 136;
            if (policy == 8) target = encounter.centre + (player.getCentreX() < boss.x() ? -136 : 136);
            if (policy == 9) target = boss.x() + (player.getCentreX() < boss.x() ? -24 : 24);
        }
        double desired = Math.max(-4, Math.min(4, (target - player.getCentreX()) / 8.0));
        double speed = player.getXSpeed() / 256.0;
        int direction = speed > desired + 0.2 ? 4 : speed < desired - 0.2 ? 8 : 0;
        boolean held = previous != null && previous.p1ActionMask() != 0;
        int action = boss != null && boss.health() > 0 && (player.getAir() ? held : !held) ? 1 : 0;
        if (policy == 10) { direction = 2; action = held ? 0 : 1; }
        if (policy == 11) { direction |= 2; action = 0; }
        return new Bk2FrameInput(frame, direction, action, false, "");
    }

    private static void append(StringBuilder script, Bk2FrameInput input) {
        var held = new ArrayList<String>();
        String[] directions = {"U", "D", "L", "R"};
        for (int i = 0; i < directions.length; i++) {
            if ((input.p1InputMask() & (1 << i)) != 0) held.add(directions[i]);
        }
        if ((input.p1ActionMask() & 1) != 0) held.add("A");
        script.append("1 ").append(held.isEmpty() ? "-" : String.join("+", held)).append('\n');
    }

    private static void write(Path output, StringBuilder script, StringBuilder rows) throws Exception {
        Files.writeString(output.resolve("input.script"), script);
        Files.writeString(output.resolve("state.csv"), rows);
    }
}
