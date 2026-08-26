package com.openggf.audio.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestS3kFinalPcmParityProbe {

    @Test
    void finalPcmDigestAndOnsetAreInvariantAcrossChunkPartitions() {
        short[] pcm = {0, 0, 4, 0, 8, -3, 0, -2, 0, 0};
        AudioPresentationParityProbe.FinalPcmDiagnostic whole =
                new AudioPresentationParityProbe.FinalPcmDiagnostic(5);
        whole.accept(pcm, 0, 5);
        AudioPresentationParityProbe.FinalPcmDiagnostic partitioned =
                new AudioPresentationParityProbe.FinalPcmDiagnostic(5);
        partitioned.accept(pcm, 0, 1);
        partitioned.accept(pcm, 1, 3);
        partitioned.accept(pcm, 4, 1);

        var expected = whole.finish();
        assertEquals(expected, partitioned.finish());
        assertEquals(1, expected.leftOnset());
        assertEquals(2, expected.leftTail());
        assertEquals(2, expected.rightOnset());
        assertEquals(3, expected.rightTail());
        assertEquals("2e0fd7007ede0d8b08b0e3c68d1eba3f589e11264d936b382687f46290202319",
                expected.pcmSha256());
    }

    @Test
    void finalPcmProbeFailsClosedAtItsFixedFrameCap() {
        AudioPresentationParityProbe.FinalPcmDiagnostic probe =
                new AudioPresentationParityProbe.FinalPcmDiagnostic(1);
        probe.accept(new short[] {1, 2}, 0, 1);
        assertThrows(IllegalArgumentException.class,
                () -> probe.accept(new short[] {3, 4}, 0, 1));
    }
}
