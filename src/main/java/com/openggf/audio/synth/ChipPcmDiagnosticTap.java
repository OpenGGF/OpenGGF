package com.openggf.audio.synth;

/** Package-confined diagnostic samples at native chip boundaries. */
sealed interface ChipPcmSample permits YmMixStereo, PsgNativeStereo, DacLatch {
    long masterCycle();
    long ordinal();
}

record YmMixStereo(long masterCycle, long ordinal, int left, int right)
        implements ChipPcmSample { }

record PsgNativeStereo(long masterCycle, long ordinal, int left, int right)
        implements ChipPcmSample { }

record DacLatch(long masterCycle, long ordinal, int signedCode)
        implements ChipPcmSample { }

@FunctionalInterface
interface ChipPcmDiagnosticTap {
    ChipPcmDiagnosticTap NONE = sample -> { };

    void onSample(ChipPcmSample sample);
}

final class ChipPcmDiagnosticFactory {
    private ChipPcmDiagnosticFactory() { }

    static void install(Ym2612Chip chip, ChipPcmDiagnosticTap tap) {
        chip.installPcmDiagnosticTap(tap);
    }

    static void install(PsgChip chip, ChipPcmDiagnosticTap tap) {
        chip.installPcmDiagnosticTap(tap);
    }
}
