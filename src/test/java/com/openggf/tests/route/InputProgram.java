package com.openggf.tests.route;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;

import java.util.ArrayList;
import java.util.List;

/** Parses and steps authored pad-input programs for headless route tests. */
public final class InputProgram {
    private InputProgram() { }

    /**
     * Parses {@code "frames:hexMask,frames:hexMask,..."} into runs. The mask is
     * hexadecimal without a prefix; frames are decimal.
     */
    public static List<InputRun> parse(String program) {
        List<InputRun> runs = new ArrayList<>();
        for (String entry : program.split(",")) {
            String[] parts = entry.trim().split(":");
            runs.add(new InputRun(Integer.parseInt(parts[0]), Integer.parseInt(parts[1], 16)));
        }
        return runs;
    }

    /** Steps one engine frame with the pad state encoded by {@code mask}. */
    public static void step(HeadlessTestFixture fixture, int mask) {
        fixture.stepFrame(
                (mask & AbstractPlayableSprite.INPUT_UP) != 0,
                (mask & AbstractPlayableSprite.INPUT_DOWN) != 0,
                (mask & AbstractPlayableSprite.INPUT_LEFT) != 0,
                (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                (mask & AbstractPlayableSprite.INPUT_JUMP) != 0);
    }

    /** Total frames across {@code runs}. */
    public static int frames(List<InputRun> runs) {
        return runs.stream().mapToInt(InputRun::frames).sum();
    }
}
