package com.openggf.data.cd;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Immutable CUE geometry. File indexes and lengths are measured in CD frames.
 * Program frames exclude the following track's stored gap. The first track's INDEX 01 is disc frame zero.
 * Stored and synthetic gaps remain distinct, including when both precede the same INDEX 01.
 */
public record DiscTrack(int number, Mode mode, String sourceFile,
                        OptionalLong index00Frame, long index01Frame,
                        long syntheticPregapFrames, long programFrames, long discIndex01Frame) {
    public enum Mode {
        AUDIO(2352, -1), MODE1_2352(2352, 16), MODE1_2048(2048, 0);

        private final int frameBytes;
        private final int userDataOffset;

        Mode(int frameBytes, int userDataOffset) {
            this.frameBytes = frameBytes;
            this.userDataOffset = userDataOffset;
        }

        public int frameBytes() {
            return frameBytes;
        }

        public boolean isMode1() {
            return this != AUDIO;
        }

        int userDataOffset() {
            return userDataOffset;
        }
    }

    public DiscTrack {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(sourceFile);
        Objects.requireNonNull(index00Frame);
        if (number < 1 || number > 99 || sourceFile.isBlank() || index01Frame < 0
                || syntheticPregapFrames < 0 || programFrames <= 0 || discIndex01Frame < 0
                || (index00Frame.isPresent()
                && (index00Frame.getAsLong() < 0 || index00Frame.getAsLong() > index01Frame))) {
            throw new IllegalArgumentException("Invalid disc track geometry");
        }
        try {
            long storedPregap = index00Frame.isPresent() ? index01Frame - index00Frame.getAsLong() : 0;
            long gap = Math.addExact(syntheticPregapFrames, storedPregap);
            Math.addExact(gap, programFrames);
            Math.addExact(discIndex01Frame, programFrames);
            Math.addExact(index01Frame, programFrames);
            Math.multiplyExact(programFrames, mode.frameBytes());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Disc track geometry overflow", exception);
        }
    }

    public long filePregapFrames() {
        return index00Frame.isPresent() ? index01Frame - index00Frame.getAsLong() : 0;
    }

    public long pregapFrames() {
        return Math.addExact(syntheticPregapFrames, filePregapFrames());
    }

    public long userDataBytes() {
        if (!mode.isMode1()) {
            throw new IllegalStateException("Audio tracks have no MODE1 user data");
        }
        return Math.multiplyExact(programFrames, 2048L);
    }
}
