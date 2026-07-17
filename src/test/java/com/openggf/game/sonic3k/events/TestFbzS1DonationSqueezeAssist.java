package com.openggf.game.sonic3k.events;

import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState.S1DonationSqueezeAssistState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestFbzS1DonationSqueezeAssist {

    @Test
    void exactRomGapBoundaryRequiresTwentyNinePixelsForRollingSonic() {
        assertEquals(false,
                FbzS1DonationSqueezeAssist.isFullSolidGapSafe(28, 19, 14));
        assertEquals(true,
                FbzS1DonationSqueezeAssist.isFullSolidGapSafe(29, 19, 14));
    }

    @Test
    void s1OrdinaryRollReceivesTraceGroundedFloorOnlyForExactSafeEpisode() {
        var decision = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.ARMED,
                true, false,
                true, true, true,
                false, true,
                0x0318);

        assertEquals(0x0800, decision.groundSpeed());
        assertEquals(S1DonationSqueezeAssistState.CONSUMED,
                decision.nextState());
    }

    @Test
    void ablationLeavesUnsafeS1EpisodeUnassistedWhenExactPairIsAbsent() {
        var decision = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.ARMED,
                true, false,
                false, true, true,
                false, true,
                0x0318);

        assertEquals(0x0318, decision.groundSpeed());
        assertEquals(S1DonationSqueezeAssistState.ARMED,
                decision.nextState());
    }

    @Test
    void nativeAndSpindashCapableDonationNeverConsumeS1Assist() {
        for (boolean donationActive : new boolean[]{false, true}) {
            var decision = FbzS1DonationSqueezeAssist.resolve(
                    S1DonationSqueezeAssistState.ARMED,
                    donationActive, true,
                    true, true, true,
                    false, true,
                    0x0318);
            assertEquals(0x0318, decision.groundSpeed());
            assertEquals(S1DonationSqueezeAssistState.ARMED,
                    decision.nextState());
        }
    }

    @Test
    void consumedEpisodeRearmsOnlyAfterExactPairEnds() {
        var retained = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.CONSUMED,
                true, false,
                true, true, true,
                false, true,
                0x0800);
        assertEquals(S1DonationSqueezeAssistState.CONSUMED,
                retained.nextState());

        var ended = FbzS1DonationSqueezeAssist.resolve(
                S1DonationSqueezeAssistState.CONSUMED,
                true, false,
                false, false, false,
                false, false,
                0x0700);
        assertEquals(S1DonationSqueezeAssistState.ARMED,
                ended.nextState());
    }
}
