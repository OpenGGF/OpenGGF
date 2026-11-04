package com.openggf.tools;

import com.openggf.game.sonic3k.objects.DezMinibossInstance;

import java.nio.file.Path;

/**
 * Death Egg act 1 miniboss front-end of the shared route planner: the cold movie's first
 * {@code PREFIX} rows stay verbatim and one {@code defeat} leg targets {@link DezMinibossInstance},
 * whose owner counts both eight-hit phases ({@link DezMinibossInstance#remainingHits}). The search
 * lives in {@code com.openggf.route}; this class has no loop. Moved onto the shared planner
 * 2026-11-02.
 */
public final class DezMinibossInputAuthorTool {
    private DezMinibossInputAuthorTool() { }

    enum Stop { DEFEATED, DEAD, LIMIT_REACHED }
    record Result(Stop stop, int frames) { }

    enum Encounter { DEZ }

    /** Obj_DEZMiniboss: collision_property counts up, then resets between phases. */
    static int remainingHits(int code, int hits) {
        return DezMinibossInstance.remainingHits(code, hits);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 7 || args.length > 8) {
            throw new IllegalArgumentException("Usage: DezMinibossInputAuthorTool ROM WIDTH MAIN SIDEKICK "
                    + "INPUT PREFIX OUT_DIR [MAX_NEW_INPUTS]");
        }
        var settings = new GameplayCaptureSession.Settings(Integer.parseInt(args[1]), args[2],
                "none".equalsIgnoreCase(args[3]) ? "" : args[3], "off", null, null, null);
        var result = run(Path.of(args[0]), settings, Path.of(args[4]), Integer.parseInt(args[5]),
                Encounter.DEZ, Path.of(args[6]), args.length == 8 ? Integer.parseInt(args[7]) : 6000);
        System.out.println("Stop: " + result.stop() + "; complete input frames: " + result.frames());
    }

    static Result run(Path rom, GameplayCaptureSession.Settings settings, Path input, int prefix,
                      Encounter encounter, Path output, int maxInputs) throws Exception {
        var outcome = EncounterRouteAuthor.author(rom, "s3k", settings, 11, 0, input, prefix,
                DezMinibossInstance.class.getSimpleName(), output, maxInputs);
        return new Result(outcome.defeated() ? Stop.DEFEATED : outcome.dead() ? Stop.DEAD : Stop.LIMIT_REACHED,
                outcome.frames());
    }
}
