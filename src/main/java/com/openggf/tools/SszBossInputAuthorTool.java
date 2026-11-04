package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.observe.HitPointSource;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Boss encounter front-end of the shared route planner (SSZ replicas and Mecha, Lava Reef's
 * miniboss and end boss): the cold movie's first {@code PREFIX} rows stay verbatim and one
 * {@code defeat} leg targets the encounter's boss as its owner counts hits. The search, its
 * encounter policies and its scoring live in {@code com.openggf.route}; this class has no loop.
 *
 * <p>Origin: September 2026 S&K campaign, the solo Tails routes delivered in 75c9caf16; the cold
 * LRZ→HPZ→SSZ campaign lane (2026-10-15) added {@code --boot ZONE:ACT} (one-based act),
 * {@code mecha2}, {@code lrz1} and {@code lrz3}; moved onto the shared planner 2026-11-02. Inputs:
 * ROM, viewport width, leader/sidekick, cold movie/prefix, encounter and empty output directory.
 * No position, health, ring, clock or trace seeds.
 */
public final class SszBossInputAuthorTool {
    private SszBossInputAuthorTool() { }

    enum Stop { DEFEATED, DEAD, LIMIT_REACHED }
    record Result(Stop stop, int frames) { }
    record Boss(int x, int health) { }

    enum Encounter {
        GHZ(SszGhzBossObjectInstance.class), MTZ(SszMtzBossObjectInstance.class),
        MECHA(SszMechaSonicObjectInstance.class), MECHA2(SszMechaSonicObjectInstance.class),
        LRZ1(com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance.class),
        LRZ3(com.openggf.game.sonic3k.objects.LrzEndBossObjectInstance.class);

        final Class<? extends ObjectInstance> boss;

        Encounter(Class<? extends ObjectInstance> boss) { this.boss = boss; }

        /** The live boss's position and remaining hits as its owner reports them, or null. */
        Boss observe() {
            return GameServices.level().getObjectManager().activeObjectsOfType(boss).stream().findFirst()
                    .map(b -> new Boss(b.getX(), b instanceof HitPointSource hp ? hp.hitsRemaining() : -1)).orElse(null);
        }
    }

    public static void main(String[] args) throws Exception {
        int bootZone = 10, bootAct = 0;
        if (args.length >= 2 && args[0].equals("--boot")) {
            String[] boot = args[1].split(":");
            bootZone = Integer.decode(boot[0]);
            bootAct = Integer.parseInt(boot[1]) - 1;
            args = java.util.Arrays.copyOfRange(args, 2, args.length);
        }
        if (args.length < 8 || args.length > 9) {
            throw new IllegalArgumentException("Usage: SszBossInputAuthorTool [--boot ZONE:ACT] ROM WIDTH MAIN "
                    + "SIDEKICK INPUT PREFIX ghz|mtz|mecha|mecha2|lrz1|lrz3 OUT_DIR [MAX_NEW_INPUTS]");
        }
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[1]), args[2],
                "none".equalsIgnoreCase(args[3]) ? "" : args[3], "off", null, null, null);
        var result = run(Path.of(args[0]), settings, bootZone, bootAct, Path.of(args[4]), Integer.parseInt(args[5]),
                Encounter.valueOf(args[6].toUpperCase(Locale.ROOT)), Path.of(args[7]),
                args.length == 9 ? Integer.parseInt(args[8]) : 3000);
        System.out.println("Stop: " + result.stop() + "; complete input frames: " + result.frames());
    }

    static Result run(Path rom, GameplayCaptureSession.Settings settings, Path input, int prefix,
                      Encounter encounter, Path output, int maxInputs) throws Exception {
        return run(rom, settings, 10, 0, input, prefix, encounter, output, maxInputs);
    }

    static Result run(Path rom, GameplayCaptureSession.Settings settings, int bootZone, int bootAct, Path input,
                      int prefix, Encounter encounter, Path output, int maxInputs) throws Exception {
        var outcome = EncounterRouteAuthor.author(rom, "s3k", settings, bootZone, bootAct, input, prefix,
                encounter.boss.getSimpleName(), output, maxInputs);
        return new Result(outcome.defeated() ? Stop.DEFEATED : outcome.dead() ? Stop.DEAD : Stop.LIMIT_REACHED,
                outcome.frames());
    }
}
