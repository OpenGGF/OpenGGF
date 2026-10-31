package com.openggf;

import com.openggf.DisplayCadencePolicy.Phase;
import com.openggf.DisplayCadencePolicy.Reason;
import com.openggf.DisplayCadencePolicy.Request;
import com.openggf.DisplayCadencePolicy.Verdict;
import com.openggf.configuration.DisplayPacing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic swap-return timestamps on a vblank lattice; no window, clock or device. */
class TestDisplayCadencePolicy {
    private static final long SECOND = 1_000_000_000L;

    private final DisplayCadencePolicy policy = new DisplayCadencePolicy();
    private double vblank;
    private long vblanks;
    private long lastSwap;

    private static Request request(int regionHz, int cap, int advertisedHz) {
        return new Request(DisplayPacing.AUTO, true, cap, regionHz, new DisplayOutput(1, advertisedHz));
    }

    /** Nominates, probes and drives a blocking display until it locks; returns probe seconds. */
    private double lock(Request request, double displayHz) {
        int interval = policy.nominate(request);
        assertTrue(interval > 0, () -> "nominated: " + policy.reason());
        policy.probe(request, interval);
        vblank = SECOND / displayHz;
        long start = Math.round(vblanks * vblank);
        for (int swap = 0; swap < 10_000; swap++) {
            var verdict = swapAfter(interval);
            assertNotEquals(Verdict.EXACT, verdict, () -> "refused " + policy.reason());
            if (verdict == Verdict.LOCK) return (lastSwap - start) / (double) SECOND;
        }
        return fail("never locked");
    }

    private Verdict swapAfter(int spannedVblanks) {
        return swapAfter(spannedVblanks, 0);
    }

    private Verdict swapAfter(int spannedVblanks, long jitterNanos) {
        vblanks += spannedVblanks;
        lastSwap = Math.round(vblanks * vblank) + jitterNanos;
        return policy.observeSwap(lastSwap);
    }

    @ParameterizedTest
    @CsvSource({"60,60,59.94005994", "60,59,59.94005994", "60,60,60.02", "60,61,60.02", "50,50,50.0"})
    void commonPanelsLockAtIntervalOneAfterTwoGoodWindows(int regionHz, int advertisedHz, double displayHz) {
        var request = request(regionHz, regionHz, advertisedHz);
        assertEquals(1, policy.nominate(request));
        double probed = lock(request, displayHz);
        assertTrue(probed >= 2.0 && probed < 2.1, "two one-second windows: " + probed);
        assertEquals(Phase.LOCKED, policy.phase());
        assertEquals(1, policy.interval());
        assertEquals(displayHz, policy.measuredHz(), displayHz * 1e-4);
        for (int swap = 0; swap < 600; swap++) assertEquals(Verdict.SERVICE, swapAfter(1));
    }

    @Test
    void doubleRatePanelLocksAtIntervalTwoWithOneServicePerRegionPeriod() {
        var request = request(60, 144, 120);
        assertEquals(2, policy.nominate(request));
        lock(request, 120_000.0 / 1001);
        assertEquals(2, policy.interval());
        assertEquals(60_000.0 / 1001, policy.measuredHz(), 0.01, "swaps follow the region rate, not the panel");
        assertEquals(Verdict.SERVICE, swapAfter(2));
        assertEquals(Verdict.EXACT, swapAfter(1), "returning after one of two vblanks does not honour interval 2");
        assertEquals(Reason.NOT_BLOCKING, policy.reason());
    }

    @ParameterizedTest
    @CsvSource({"60,75", "60,144", "60,165", "50,60", "60,90"})
    void refreshesAwayFromARegionMultipleStayExact(int regionHz, int advertisedHz) {
        assertEquals(0, policy.nominate(request(regionHz, 1_000, advertisedHz)));
        assertEquals(Reason.REFRESH_NOT_MULTIPLE, policy.reason());
        assertEquals(Phase.EXACT, policy.phase());
    }

    @Test
    void nominationNeedsAutoARegulatedSpeakerAFullCapAndAKnownRefresh() {
        var output = new DisplayOutput(1, 60);
        assertEquals(0, policy.nominate(new Request(DisplayPacing.AUTO, true, 30, 60, output)));
        assertEquals(Reason.CAP_BELOW_REGION, policy.reason());
        assertEquals(0, policy.nominate(new Request(DisplayPacing.EXACT, true, 60, 60, output)));
        assertEquals(Reason.PACING_EXACT, policy.reason());
        assertEquals(0, policy.nominate(new Request(DisplayPacing.AUTO, false, 60, 60, output)));
        assertEquals(Reason.SPEAKER_UNREGULATED, policy.reason());
        assertEquals(0, policy.nominate(new Request(DisplayPacing.AUTO, true, 60, 60, DisplayOutput.UNKNOWN)));
        assertEquals(Reason.REFRESH_UNKNOWN, policy.reason());
        assertEquals(1, policy.nominate(new Request(DisplayPacing.AUTO, true, 60, 60, output)));
        assertEquals(2, policy.nominate(new Request(DisplayPacing.AUTO, true, 50, 50, new DisplayOutput(1, 100))));
    }

    @Test
    void immediateReturnWhileLockedDemotesBeforeTheNextServiceAndGrantsNone() {
        lock(request(60, 60, 60), 60);
        assertEquals(Verdict.SERVICE, swapAfter(1));
        lastSwap += 1_000_000;
        assertEquals(Verdict.EXACT, policy.observeSwap(lastSwap), "a 1 ms return grants no service");
        assertEquals(Reason.NOT_BLOCKING, policy.reason());
        assertEquals(Phase.EXACT, policy.phase());
        assertEquals(1, policy.interval());
    }

    @Test
    void nonBlockingSwapRefusesTheProbeAndUnchangedOutputIsNotProbedAgain() {
        var request = request(60, 60, 60);
        policy.probe(request, policy.nominate(request));
        assertEquals(Verdict.MEASURED, policy.observeSwap(0));
        assertEquals(Verdict.EXACT, policy.observeSwap(500_000));
        assertEquals(Reason.NOT_BLOCKING, policy.reason());
        policy.boundaryChanged();
        assertEquals(0, policy.nominate(request), "a boundary change does not requalify a non-blocking output");
        assertEquals(Reason.NOT_BLOCKING, policy.reason());
        assertEquals(0, policy.nominate(request(60, 60, 60)), "an equal observation is the same output");
        var moved = new Request(DisplayPacing.AUTO, true, 60, 60, new DisplayOutput(2, 60));
        assertEquals(1, policy.nominate(moved), "a new output epoch may probe again");
    }

    @Test
    void outOfBandPanelRefusesTheProbeUntilTheConfigurationChanges() {
        var request = request(60, 60, 60);
        int interval = policy.nominate(request);
        policy.probe(request, interval);
        vblank = SECOND / 59.5;
        Verdict verdict = Verdict.MEASURED;
        while (verdict == Verdict.MEASURED) verdict = swapAfter(1);
        assertEquals(Verdict.EXACT, verdict);
        assertEquals(Reason.OUT_OF_BAND, policy.reason());
        assertEquals(0, policy.nominate(request));
        assertEquals(1, policy.nominate(request(60, 120, 60)), "a changed picture cap is a new configuration");
    }

    @Test
    void jitteredReturnsStayLockedForAnHourWithExactlyOneServicePerSwap() {
        lock(request(60, 60, 60), 60_000.0 / 1001);
        var random = new SplittableRandom(0x5EED);
        long swaps = 0, services = 0;
        long hour = lastSwap + 3_600 * SECOND;
        while (lastSwap < hour) {
            var verdict = swapAfter(1, random.nextLong(-2_000_000, 2_000_001));
            swaps++;
            if (verdict == Verdict.SERVICE) services++;
            else fail("demoted after " + swaps + " swaps: " + verdict + " " + policy.reason());
        }
        assertEquals(swaps, services);
        assertEquals(Phase.LOCKED, policy.phase());
        assertEquals(60_000.0 / 1001, policy.measuredHz(), 0.5);
    }

    @Test
    void oneMissedVblankIsOneSlowIntervalButRepeatedMissesDemote() {
        lock(request(60, 60, 60), 60);
        assertEquals(Verdict.SERVICE, swapAfter(2), "one missed vblank still grants exactly one service");
        for (int swap = 0; swap < 120; swap++) assertEquals(Verdict.SERVICE, swapAfter(1));
        assertEquals(Phase.LOCKED, policy.phase());
        for (int miss = 0; miss < DisplayCadencePolicy.MAX_MISSES_PER_SECOND; miss++) {
            assertEquals(Verdict.SERVICE, swapAfter(2));
        }
        assertEquals(Verdict.EXACT, swapAfter(2));
        assertEquals(Reason.MISSES, policy.reason());
        var request = request(60, 60, 60);
        assertEquals(0, policy.nominate(request), "misses wait for the next boundary change");
        policy.boundaryChanged();
        assertEquals(1, policy.nominate(request));
    }

    @Test
    void aOneSecondStallResetsTheWindowWithoutDemotingOrCatchingUp() {
        lock(request(60, 60, 60), 60);
        assertEquals(Verdict.SERVICE, swapAfter(60), "one stalled second grants one service, not sixty");
        assertEquals(Phase.LOCKED, policy.phase());
        for (int swap = 0; swap < 600; swap++) assertEquals(Verdict.SERVICE, swapAfter(1));
    }

    @Test
    void lockedRateShiftDemotesWithinTheRollingWindow() {
        lock(request(60, 60, 60), 60);
        for (int swap = 0; swap < 600; swap++) assertEquals(Verdict.SERVICE, swapAfter(1));
        long shifted = lastSwap;
        vblanks = 0;
        vblank = SECOND / 57.0;
        Verdict verdict = Verdict.SERVICE;
        while (verdict == Verdict.SERVICE) {
            vblanks++;
            lastSwap = shifted + Math.round(vblanks * vblank);
            verdict = policy.observeSwap(lastSwap);
        }
        assertEquals(Reason.OUT_OF_BAND, policy.reason());
        assertTrue(lastSwap - shifted <= 3 * SECOND, "5% slow is caught within a few windows");
    }

    @Test
    void boundaryChangeRestartsTheProbe() {
        var request = request(60, 60, 60);
        policy.probe(request, policy.nominate(request));
        vblank = SECOND / 60.0;
        for (int swap = 0; swap < 90; swap++) assertEquals(Verdict.MEASURED, swapAfter(1));
        policy.boundaryChanged();
        long restarted = Math.round(vblanks * vblank);
        Verdict verdict = Verdict.MEASURED;
        while (verdict == Verdict.MEASURED) verdict = swapAfter(1);
        assertEquals(Verdict.LOCK, verdict);
        assertTrue(lastSwap - restarted >= 2 * SECOND, "both windows follow the boundary");
    }
}
