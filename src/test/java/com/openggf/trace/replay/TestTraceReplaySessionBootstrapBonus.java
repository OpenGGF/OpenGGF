package com.openggf.trace.replay;

import com.openggf.game.BonusStageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestTraceReplaySessionBootstrapBonus {
    /**
     * Each committed bonus fixture's arm-time {@code metadata.v_int_run_count}
     * precedes row zero's {@code vblank_counter} (low word) by exactly one
     * V-int; the object-clock seed rule ({@code initialVblankCounter - 1})
     * therefore already re-establishes the recorded {@code V_int_run_count}.
     */
    @Test
    void recordedVIntRunCountPrecedesRowZeroOnEveryCommittedBonusFixture() {
        // bonus_gumball / runs/s3-knux-multibonus-ss/gumball
        TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(5529L, 0x159A);
        // bonus_slots / runs/s3-knux-multibonus-ss/slots
        TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(9097L, 0x238A);
        // runs/s3k-sonic-tails-complete-emeralds/slots (bits above the low word differ)
        TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(352564L, 0x6135);
        // legacy fixture without the field
        TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(null, 0x1234);
    }

    @Test
    void recordedVIntRunCountOffByOneIsFatal() {
        assertThrows(IllegalStateException.class, () ->
                TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(9097L, 0x2389));
        assertThrows(IllegalStateException.class, () ->
                TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(9097L, 0x238B));
        // Low-word wrap still agrees: 0xFFFF precedes 0x0000.
        TraceReplaySessionBootstrap.requireRecordedVIntRunCountAgreesWithRowZero(0x1FFFFL, 0x0000);
    }


    @Test
    void bonusTypeMappingCoversGumballAndPachinko() {
        assertEquals(BonusStageType.GUMBALL,
            TraceReplaySessionBootstrap.bonusStageTypeForToken("gumball"));
        assertEquals(BonusStageType.GLOWING_SPHERE,
            TraceReplaySessionBootstrap.bonusStageTypeForToken("pachinko"));
        assertEquals(BonusStageType.SLOT_MACHINE,
            TraceReplaySessionBootstrap.bonusStageTypeForToken("slots"));
        assertThrows(IllegalStateException.class,
            () -> TraceReplaySessionBootstrap.bonusStageTypeForToken("casino"));
        assertThrows(IllegalStateException.class,
            () -> TraceReplaySessionBootstrap.bonusStageTypeForToken(null));
    }
}
