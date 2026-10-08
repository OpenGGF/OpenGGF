package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Authors ordinary DEZ Act 1 boss inputs from a cold movie and engine rewind snapshots.
 * Origin: S&K widescreen bring-up, 2026-09-28. Adapted from SszBossInputAuthorTool's
 * controller search; horizons, offsets and scores are authoring choices, never gameplay.
 * Reads both native eight-hit phases and the separate eye's position. No position,
 * health, ring, clock or trace state is seeded. Every result requires an independent
 * uninterrupted GameplayCaptureTool replay before it becomes route evidence.
 */
public final class DezMinibossInputAuthorTool {
    private static final int HORIZON = 300;
    private static final int COMMIT_INPUTS = 30;
    private static final int POLICY_COUNT = 18;
    private static final int[] OFFSETS = {0, -24, 24, -48, 48, 0, 0, 0, 0, 0, 0, 0, 0, -24, 24, 0, -48, 48};

    private DezMinibossInputAuthorTool() { }

    enum Stop { DEFEATED, DEAD, LIMIT_REACHED }
    record Result(Stop stop, int frames) { }
    record Boss(int x, int health) { }

    enum Encounter {
        DEZ(0x3740);

        final int centre;

        Encounter(int centre) { this.centre = centre; }

        Boss observe() {
            var objects = GameServices.level().getObjectManager();
            var root = objects.activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.DezMinibossInstance.class)
                    .stream().findFirst().orElse(null);
            if (root == null) return null;
            int health = remainingHits(field(root, "codePointer"), field(root, "collisionProperty"));
            var eye = java.util.stream.StreamSupport.stream(objects.getActiveObjects().spliterator(), false)
                    .filter(object -> object.getClass().getSimpleName().equals("DezMinibossEye"))
                    .findFirst().orElse(null);
            return new Boss(eye == null ? root.getX() : eye.getX(), health);
        }
    }

    /** Obj_DEZMiniboss: collision_property counts UP, then resets between phases. */
    static int remainingHits(int code, int hits) {
        // $7DF8C is the first phase's transition; $7E0A6 owns the second eight
        // hits. $85668 is the observed defeat countdown, not object absence.
        return code == 0x85668 ? 0 : code == 0x7E0A6 ? 8 - hits
                : code == 0x7DF8C ? 8 : 16 - hits;
    }

    // Diagnostic reads of native sprite words avoid adding author-only runtime API.
    private static int field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(object);
            } catch (NoSuchFieldException ignored) {
                // Native words live in the shared sprite superclass.
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }
        throw new IllegalArgumentException(name);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 7 || args.length > 8) {
            throw new IllegalArgumentException("Usage: DezMinibossInputAuthorTool ROM WIDTH MAIN SIDEKICK "
                    + "INPUT PREFIX OUT_DIR [MAX_NEW_INPUTS]");
        }
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[1]), args[2],
                "none".equalsIgnoreCase(args[3]) ? "" : args[3], "off", null, null, null);
        var result = run(Path.of(args[0]), settings, Path.of(args[4]), Integer.parseInt(args[5]),
                Encounter.DEZ, Path.of(args[6]),
                args.length == 8 ? Integer.parseInt(args[7]) : 6000);
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
        if (prefix < 0 || prefix > movie.getFrameCount()) {
            throw new IllegalArgumentException("PREFIX must be inside the supplied movie");
        }
        var script = new StringBuilder(GameplayInputBranchTool.prefixScript(movie, prefix));
        var rows = new StringBuilder(GameplayCaptureSession.stateHeader()).append('\n');
        Files.createDirectories(output);
        try (var existing = Files.list(output)) {
            if (existing.findAny().isPresent()) throw new IllegalArgumentException("Output is not empty: " + output);
        }
        Result result;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom, 11, 0, settings);
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
            if (policy == 5 || policy == 10 || policy == 11) target = encounter.centre;
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
        if (policy >= 12) action = frame % (policy < 15 ? 8 : 16) < 4 ? 1 : 0;
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
