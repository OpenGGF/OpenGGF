package com.openggf.audio.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsSessionSnapshot {
    @Test
    void inertSnapshotContainsOnlySessionAndOnePhysicalState() {
        SmpsPhysicalPolicy policy =
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalDevice.Settings settings =
                SmpsSessionTestFixtures.settings();
        SmpsSessionProfileFingerprint profile =
                new SmpsSessionProfileFingerprint(
                        "sonic-test", 11, policy.identity(), settings);
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = new SmpsDriverSession(
                settings, policy, observer, profile);

        SmpsDriverSessionSnapshot snapshot = session.captureSnapshot();

        assertTrue(observer.events().isEmpty());
        assertEquals(false, snapshot.initialized());
        assertEquals(SmpsPendingGlobalCommand.NONE,
                snapshot.pendingGlobalCommand());
        assertSame(profile, snapshot.profile());
        assertNull(snapshot.selectedDacSource());
        assertEquals(settings, snapshot.physical().settings());
    }

    @Test
    void selectedDacProvenanceIsSeparateFromThePhysicalSnapshot() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsPhysicalPort port = session.openTestEpoch(
                SmpsSessionTestFixtures.owner(8));

        port.selectDac(new SmpsDacSelection(
                SmpsSessionTestFixtures.source(9),
                SmpsSessionTestFixtures.dac()));
        SmpsDriverSessionSnapshot snapshot = session.captureSnapshot();

        assertEquals(SmpsSessionTestFixtures.source(9),
                snapshot.selectedDacSource());
        assertEquals(SmpsSessionTestFixtures.settings(),
                snapshot.physical().settings());
    }
}
