package com.openggf.game.sonic2.kis2;

import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager;

/** Sonic 2's half-pipe lifecycle with chip-backed KiS2 presentation and targets. */
public final class Kis2SpecialStageProvider extends Sonic2SpecialStageProvider {
    private final LockOnAddressSpace addressSpace;

    public Kis2SpecialStageProvider(LockOnAddressSpace addressSpace) {
        super(new Sonic2SpecialStageManager(rom -> new Kis2SpecialStageDataLoader(rom, addressSpace)));
        if (!addressSpace.hasChip()) throw new IllegalArgumentException("KiS2 special stages require the chip");
        this.addressSpace = addressSpace;
    }

    @Override
    public int getDebugCompletionRingCount(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= 7) return 100;
        // SpecialStage_RingReq_Alone: quarter 2 is the emerald gate. The
        // fourth entry exists in the ROM but is unused by the shipped route.
        var read = addressSpace.require(Kis2SpecialStageDataLoader.RING_REQUIREMENTS + stageIndex * 4 + 2);
        return read.reader().readU8(read.localAddress());
    }
}
