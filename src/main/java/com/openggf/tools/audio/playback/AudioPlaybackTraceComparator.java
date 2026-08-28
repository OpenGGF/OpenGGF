package com.openggf.tools.audio.playback;

import java.util.Arrays;
import java.util.List;

/** Exact comparison with the first useful diagnostic mismatch. */
public final class AudioPlaybackTraceComparator {
    private AudioPlaybackTraceComparator() {
    }

    public static Result compare(
            AudioPlaybackTraceSnapshot expected,
            AudioPlaybackTraceSnapshot actual) {
        List<AudioPlaybackTraceEvent> expectedEvents = expected.events();
        List<AudioPlaybackTraceEvent> actualEvents = actual.events();
        int eventLimit = Math.min(expectedEvents.size(), actualEvents.size());
        for (int index = 0; index < eventLimit; index++) {
            if (!expectedEvents.get(index).equals(actualEvents.get(index))) {
                return new Result(index, firstPcmMismatch(expected, actual),
                        "event " + index + " differs: expected "
                                + expectedEvents.get(index) + ", actual "
                                + actualEvents.get(index));
            }
        }
        if (expectedEvents.size() != actualEvents.size()) {
            return new Result(eventLimit, firstPcmMismatch(expected, actual),
                    "event count differs: expected " + expectedEvents.size()
                            + ", actual " + actualEvents.size());
        }
        int pcmMismatch = firstPcmMismatch(expected, actual);
        return new Result(-1, pcmMismatch,
                pcmMismatch < 0 ? "traces match"
                        : "PCM sample " + pcmMismatch + " differs");
    }

    private static int firstPcmMismatch(
            AudioPlaybackTraceSnapshot expected,
            AudioPlaybackTraceSnapshot actual) {
        short[] expectedPcm = expected.pcm();
        short[] actualPcm = actual.pcm();
        int mismatch = Arrays.mismatch(expectedPcm, actualPcm);
        return mismatch < 0 && expectedPcm.length != actualPcm.length
                ? Math.min(expectedPcm.length, actualPcm.length)
                : mismatch;
    }

    public record Result(
            int firstEventMismatch,
            int firstPcmMismatch,
            String description) {
        public boolean matches() {
            return firstEventMismatch < 0 && firstPcmMismatch < 0;
        }
    }
}
