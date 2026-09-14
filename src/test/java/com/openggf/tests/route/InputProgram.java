package com.openggf.tests.route;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses, derives and steps authored pad-input programs for headless route
 * tests.
 *
 * <p>Inputs: either a {@code "frames:hexMask,..."} literal ({@link #parse}) or
 * a window of a committed BK2 movie ({@link #fromRecording}); both yield
 * {@link InputRun}s in {@code AbstractPlayableSprite.INPUT_*} bits.
 *
 * <p>Origin: extracted from the FBZ2 route controller
 * ({@code TestFbzAct2TraversalPreboss}) in commit 610464952;
 * {@link #fromRecording} was added for the AIZ1 pilot so a route's fallback
 * program is derived from the fixture's movie instead of hand-copied.
 */
public final class InputProgram {
    private InputProgram() { }

    /**
     * Parses {@code "frames:hexMask,frames:hexMask,..."} into runs. Frames
     * are decimal, the mask is hexadecimal without a prefix, and whitespace
     * around an entry or its colon is ignored. A malformed entry is reported
     * with its index.
     */
    public static List<InputRun> parse(String program) {
        List<InputRun> runs = new ArrayList<>();
        String[] entries = program.split(",");
        for (int index = 0; index < entries.length; index++) {
            String[] parts = entries[index].split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "entry " + index + " is not frames:hexMask: '" + entries[index] + "'");
            }
            try {
                runs.add(new InputRun(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim(), 16)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "entry " + index + " is not frames:hexMask: '" + entries[index] + "'", e);
            }
        }
        return runs;
    }

    /**
     * Run-length encodes the P1 pad masks of movie frames
     * {@code [fromFrame, toFrame)} (0-based emulation frame indexes, so a
     * fixture row {@code r} is {@code bk2_frame_offset + r}). Start is not
     * part of the mask, as in {@link #step}.
     */
    public static List<InputRun> fromRecording(Bk2Movie movie, int fromFrame, int toFrame) {
        List<Bk2FrameInput> frames = movie.getFrames();
        if (fromFrame < 0 || toFrame > frames.size() || fromFrame > toFrame) {
            throw new IllegalArgumentException("frames [" + fromFrame + "," + toFrame
                    + ") outside the movie's " + frames.size() + " rows");
        }
        List<InputRun> runs = new ArrayList<>();
        int runMask = 0;
        int runLength = 0;
        for (int frame = fromFrame; frame < toFrame; frame++) {
            int mask = frames.get(frame).p1InputMask();
            if (runLength > 0 && mask != runMask) {
                runs.add(new InputRun(runLength, runMask));
                runLength = 0;
            }
            runMask = mask;
            runLength++;
        }
        if (runLength > 0) runs.add(new InputRun(runLength, runMask));
        return runs;
    }

    /** Renders runs in the {@link #parse} literal form. */
    public static String format(List<InputRun> runs) {
        StringBuilder builder = new StringBuilder();
        for (InputRun run : runs) {
            if (builder.length() > 0) builder.append(',');
            builder.append(run.frames()).append(':').append(Integer.toHexString(run.mask()).toUpperCase());
        }
        return builder.toString();
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

    /**
     * Cursor over a program that hands out one mask per frame and reports
     * the fixture row it is on, so a route can address its handover points
     * by row rather than by run index.
     */
    public static final class Cursor {
        private final List<InputRun> runs;
        private final int firstRow;
        private int runIndex;
        private int runFrame;
        private int row;

        /** @param firstRow the fixture row the first frame of {@code runs} plays on */
        public Cursor(List<InputRun> runs, int firstRow) {
            this.runs = runs;
            this.firstRow = firstRow;
            this.row = firstRow;
        }

        /** Fixture row the next {@link #next()} mask belongs to. */
        public int row() {
            return row;
        }

        public boolean exhausted() {
            return runIndex >= runs.size();
        }

        /** Mask for the current row; neutral once the program is exhausted. */
        public int next() {
            if (exhausted()) {
                row++;
                return 0;
            }
            InputRun run = runs.get(runIndex);
            int mask = run.mask();
            runFrame++;
            row++;
            if (runFrame >= run.frames()) {
                runIndex++;
                runFrame = 0;
            }
            return mask;
        }

        /** Repositions the cursor at {@code targetRow}, which must not precede the program's first row. */
        public void seekToRow(int targetRow) {
            if (targetRow < firstRow) {
                throw new IllegalArgumentException("row " + targetRow + " precedes the program's first row " + firstRow);
            }
            runIndex = 0;
            runFrame = 0;
            row = firstRow;
            int remaining = targetRow - firstRow;
            while (remaining > 0 && runIndex < runs.size()) {
                int left = runs.get(runIndex).frames() - runFrame;
                if (remaining >= left) {
                    remaining -= left;
                    runIndex++;
                    runFrame = 0;
                } else {
                    runFrame += remaining;
                    remaining = 0;
                }
            }
            row = targetRow;
        }
    }
}
