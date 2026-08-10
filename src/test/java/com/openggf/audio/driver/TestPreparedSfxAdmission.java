package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.CoordFlagContext;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.PsgChip;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPreparedSfxAdmission {
    @Test
    void sfxConstructionAndPreparationDoNotMutateDriverSynthOrCoordination() {
        SmpsDriver driver = new SmpsDriver();
        AtomicInteger starts = new AtomicInteger();
        SmpsSequencerConfig config = config(countingHandler(starts));
        Object synthBefore = driver.captureSynthSnapshot();
        SmpsDriverSnapshot driverBefore = driver.captureSnapshot();

        SmpsSequencer sequencer = sequencer(driver, 0xA0, config,
                track(0, 1), track(0xC0, 2));
        Object sequencerBefore = sequencer.captureSnapshot();

        assertDeepEquals(synthBefore, driver.captureSynthSnapshot());
        assertDriverStateEquals(driverBefore, driver.captureSnapshot());
        assertDeepEquals(sequencerBefore, sequencer.captureSnapshot());
        assertEquals(0, starts.get(),
                "construction must not publish the SFX start");

        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                sequencer, 0, 2);

        assertSame(driver, admission.owner());
        assertSame(sequencer, admission.sequencer());
        assertFalse(admission.continuousExtension());
        assertEquals(0b000001, admission.affectedFmMask());
        assertEquals(0b0100, admission.affectedPsgMask());
        assertDeepEquals(synthBefore, driver.captureSynthSnapshot());
        assertDriverStateEquals(driverBefore, driver.captureSnapshot());
        assertDeepEquals(sequencerBefore, sequencer.captureSnapshot());
        assertEquals(0, starts.get(),
                "preparation must not publish the SFX start");
    }

    @Test
    void preparationFindsSameIdAndFmPsgConflictsWithoutApplyingThem() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sameId = sequencer(driver, 0xA0, config(null),
                track(1, 1));
        SmpsSequencer contended = sequencer(driver, 0xA1, config(null),
                track(0, 1), track(0xC0, 2));
        driver.addSequencer(sameId, true);
        driver.addSequencer(contended, true);
        SmpsSequencer.Track fmTrack = contended.getTracks().get(0);
        SmpsSequencer.Track psgTrack = contended.getTracks().get(1);
        driver.writeFm(contended, 0, 0xA0, 0x22);
        driver.writePsg(contended, 0xC4);
        SmpsDriverSnapshot before = driver.captureSnapshot();
        List<SmpsSequencer> orderBefore = driver.sequencersForTesting();
        SmpsSequencer replacement = sequencer(driver, 0xA0, config(null),
                track(0, 1), track(0xC0, 2));

        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                replacement, 0, 2);

        assertEquals(0b000001, admission.affectedFmMask());
        assertEquals(0b0100, admission.affectedPsgMask());
        assertIdentityOrder(orderBefore, driver.sequencersForTesting());
        assertTrue(fmTrack.active);
        assertTrue(psgTrack.active);
        assertDriverStateEquals(before, driver.captureSnapshot());

        replacement.beginSfxAdmission();
        driver.commitSfxAdmission(admission);

        assertEquals(List.of(replacement), driver.sequencersForTesting());
        assertFalse(fmTrack.active,
                "FM contention must retire the displaced track at commit");
        assertFalse(psgTrack.active,
                "PSG contention must retire the displaced track at commit");
    }

    @Test
    void mixedFm6DacAndDuplicateNewHeadersStopEachExactConflictOnceInHeaderOrder() {
        OrderedStopDriver driver = new OrderedStopDriver();
        SmpsSequencer existing = sequencer(driver, 0xA0, config(null),
                track(6, 1), track(0x16, 2));
        driver.addSequencer(existing, true);
        driver.watch(existing);
        SmpsSequencer replacement = sequencer(driver, 0xA1, config(null),
                track(0x16, 1), track(6, 2), track(6, 3));

        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                replacement, 0, 3);

        assertEquals(3, conflictArrayCapacity(admission),
                "ordered action storage must be sized by the new header");

        replacement.beginSfxAdmission();
        driver.commitSfxAdmission(admission);

        assertEquals(List.of("DAC", "FM"), driver.stopOrder,
                "duplicate FM6 headers must not stop the old FM6 track twice");
        assertFalse(existing.trackAt(0).active);
        assertFalse(existing.trackAt(1).active);
        assertEquals(List.of(replacement), driver.sequencersForTesting());
    }

    @Test
    void reversedPsgHeadersPreserveLegacyWritesAndFinalChipLatch() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer existing = sequencer(driver, 0xA0, config(null),
                track(0x80, 1), track(0xA0, 2));
        driver.addSequencer(existing, true);
        SmpsSequencer replacement = sequencer(driver, 0xA1, config(null),
                track(0xA0, 1), track(0x80, 2));
        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                replacement, 0, 2);
        var before = driver.captureSynthSnapshot();
        PsgChip legacyOracle = new PsgChip();
        legacyOracle.restoreSnapshot(before.psg());
        legacyOracle.write(0xBF);
        legacyOracle.write(0xBF);
        legacyOracle.write(0x9F);
        legacyOracle.write(0x9F);
        List<Integer> psgWrites = new java.util.ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
            }

            @Override
            public void onPsgWrite(int value) {
                psgWrites.add(value);
            }
        });

        replacement.beginSfxAdmission();
        driver.commitSfxAdmission(admission);

        assertEquals(List.of(0xBF, 0xBF, 0x9F, 0x9F), psgWrites,
                "contention silence writes retain new-header order");
        assertDeepEquals(legacyOracle.captureSnapshot(),
                driver.captureSynthSnapshot().psg());
        assertEquals(1, driver.captureSynthSnapshot().psg().latch(),
                "the final PSG latch must belong to header-last channel 0");
    }

    @Test
    void continuousExtensionPreparesWithoutASequencerOrCoordinationStart() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer existing = sequencer(driver, 0xBC, config(null),
                track(0, 1));
        driver.addSequencer(existing, true);
        driver.startContinuousSfx(0xBC, 1);
        SmpsDriverSnapshot before = driver.captureSnapshot();

        PreparedSfxAdmission admission =
                driver.prepareContinuousSfxExtension(0xBC, 1);

        assertNotNull(admission);
        assertTrue(admission.continuousExtension());
        assertNull(admission.sequencer());
        assertEquals(0, admission.affectedFmMask());
        assertEquals(0, admission.affectedPsgMask());
        assertDriverStateEquals(before, driver.captureSnapshot());

        driver.commitSfxAdmission(admission);

        SmpsDriverSnapshot committed = driver.captureSnapshot();
        assertEquals(0xBC, committed.continuousSfxId());
        assertTrue(committed.continuousSfxFlag());
        assertEquals(1, committed.contSfxLoopCnt());
        assertEquals(List.of(existing), driver.sequencersForTesting());
    }

    @Test
    void zeroTrackContinuousExtensionSkipsSequencerStart() {
        SmpsDriver driver = new SmpsDriver();
        AtomicInteger starts = new AtomicInteger();
        SmpsSequencer existing = sequencer(
                driver, 0xBC, config(countingHandler(starts)));
        driver.addSequencer(existing, true);
        starts.set(0);
        driver.startContinuousSfx(0xBC, 0);

        PreparedSfxAdmission admission =
                driver.prepareContinuousSfxExtension(0xBC, 0);

        assertNotNull(admission);
        assertTrue(admission.continuousExtension());
        assertNull(admission.sequencer());
        assertEquals(0, admission.trackCount());
        driver.commitSfxAdmission(admission);
        assertEquals(0, starts.get());
        assertTrue(driver.isContinuousSfxFlagSet());
        assertEquals(0, driver.captureSnapshot().contSfxLoopCnt());
    }

    @Test
    void nonMatchingOrDeadContinuousSfxDoesNotPrepareAnExtension() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer existing = sequencer(driver, 0xBC, config(null),
                track(0, 1));
        driver.addSequencer(existing, true);
        driver.startContinuousSfx(0xBC, 1);

        assertNull(driver.prepareContinuousSfxExtension(0xBD, 1));

        driver.stopAllSfx();
        assertNull(driver.prepareContinuousSfxExtension(0xBC, 1));
    }

    @Test
    void preparationRejectsInvalidPointersChannelsPriorityAndContinuousMetadata() {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer badPointer = sequencer(driver, 0xA0, config(null),
                track(0, 99));
        SmpsSequencer badChannel = sequencer(driver, 0xA1, config(null),
                track(0x20, 1));
        SmpsSequencer badPriority = sequencer(driver, 0xA2, config(null),
                track(0, 1));
        badPriority.setSfxPriority(-1);

        assertThrows(IllegalArgumentException.class,
                () -> driver.prepareNewSfxAdmission(badPointer, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> driver.prepareNewSfxAdmission(badChannel, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> driver.prepareNewSfxAdmission(badPriority, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> driver.prepareContinuousSfxExtension(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> driver.prepareContinuousSfxExtension(0xBC, -1));
    }

    @Test
    void commitRejectsAnotherDriverAndASecondCommitBeforeMutation() {
        CountingRollbackDriver owner = new CountingRollbackDriver();
        owner.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });
        SmpsSequencer sequencer = sequencer(owner, 0xA0, config(null),
                track(0, 1));
        PreparedSfxAdmission admission = owner.prepareNewSfxAdmission(
                sequencer, 0, 1);

        assertThrows(IllegalArgumentException.class,
                () -> new SmpsDriver().commitSfxAdmission(admission));
        assertTrue(owner.sequencersForTesting().isEmpty());

        sequencer.beginSfxAdmission();
        owner.commitSfxAdmission(admission);
        assertThrows(IllegalStateException.class,
                () -> owner.commitSfxAdmission(admission));
        assertEquals(1, owner.captureCalls,
                "an already-used claim must fail before fallback capture");
        assertEquals(List.of(sequencer), owner.sequencersForTesting());
    }

    @Test
    void observerFreeCommitDoesNotCaptureFallbackState() {
        CountingRollbackDriver driver = new CountingRollbackDriver();
        SmpsSequencer sequencer = sequencer(driver, 0xA0, config(null),
                track(0, 1));
        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                sequencer, 0, 1);

        sequencer.beginSfxAdmission();
        driver.commitSfxAdmission(admission);

        assertEquals(0, driver.captureCalls,
                "the normal observer-free path must allocate no fallback snapshot");
        assertEquals(0, driver.rollbackCalls);
    }

    @Test
    void ymObserverFailureRestoresFullStateAndReleasesAdmissionForRetry() {
        CountingRollbackDriver driver = new CountingRollbackDriver();
        SmpsSequencer sequencer = sequencer(driver, 0xA0, config(null),
                track(0, 1));
        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                sequencer, 0, 1);
        SmpsDriverSnapshot before = driver.captureSnapshot();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                throw new IllegalStateException("injected YM observer failure");
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        sequencer.beginSfxAdmission();
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> driver.commitSfxAdmission(admission));

        assertEquals("injected YM observer failure", failure.getMessage());
        assertDriverStateEquals(before, driver.captureSnapshot());
        assertTrue(driver.sequencersForTesting().isEmpty());
        assertEquals(1, driver.captureCalls);
        assertEquals(1, driver.rollbackCalls);

        driver.setChipWriteObserver(null);
        driver.commitSfxAdmission(admission);
        assertEquals(List.of(sequencer), driver.sequencersForTesting(),
                "the exact failed prepared admission must be retryable");
    }

    @Test
    void psgObserverFailureRestoresConflictTrackAndChipBeforeRetry() {
        CountingRollbackDriver driver = new CountingRollbackDriver();
        SmpsSequencer existing = sequencer(driver, 0xA0, config(null),
                track(0x80, 1));
        driver.addSequencer(existing, true);
        SmpsSequencer.Track originalTrack = existing.trackAt(0);
        SmpsSequencer replacement = sequencer(driver, 0xA1, config(null),
                track(0x80, 1));
        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                replacement, 0, 1);
        SmpsDriverSnapshot before = driver.captureSnapshot();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
            }

            @Override
            public void onPsgWrite(int value) {
                throw new IllegalStateException(
                        "injected PSG observer failure");
            }
        });

        replacement.beginSfxAdmission();
        assertThrows(IllegalStateException.class,
                () -> driver.commitSfxAdmission(admission));

        assertDriverStateEquals(before, driver.captureSnapshot());
        assertIdentityOrder(List.of(existing),
                driver.sequencersForTesting());
        assertSame(originalTrack, existing.trackAt(0),
                "live rollback preserves prepared track identities");
        assertTrue(existing.trackAt(0).active,
                "the displaced track is live again after observer rollback");
        assertEquals(1, driver.captureCalls);
        assertEquals(1, driver.rollbackCalls);

        driver.setChipWriteObserver(null);
        driver.commitSfxAdmission(admission);
        assertEquals(List.of(replacement), driver.sequencersForTesting());
        assertFalse(existing.trackAt(0).active);
    }

    @Test
    void preparedStateUsesOnlyChannelBoundedArraysAndNoGeneralCollections() {
        for (Field field : PreparedSfxAdmission.class.getDeclaredFields()) {
            assertFalse(Collection.class.isAssignableFrom(field.getType()),
                    () -> field + " must not scale with unrelated live state");
            assertFalse(Map.class.isAssignableFrom(field.getType()),
                    () -> field + " must not scale with unrelated live state");
        }
    }

    @Test
    void retainedConflictStorageDoesNotGrowWithUnrelatedLiveSfx() {
        for (int unrelatedCount : new int[] {0, 1, 8, 32, 128}) {
            SmpsDriver driver = new SmpsDriver();
            for (int index = 0; index < unrelatedCount; index++) {
                driver.addSequencer(sequencer(
                        driver, 0x200 + index, config(null)), true);
            }
            SmpsSequencer candidate = sequencer(
                    driver, 0xA0, config(null), track(0, 1));

            for (int repetition = 0; repetition < 20; repetition++) {
                PreparedSfxAdmission admission =
                        driver.prepareNewSfxAdmission(candidate, 0, 1);

                assertEquals(candidate.trackCount(),
                        admission.displacedOwners.length,
                        "owner storage must stay new-track-bounded at live size "
                                + unrelatedCount);
                assertEquals(candidate.trackCount(),
                        admission.displacedTracks.length,
                        "track storage must stay new-track-bounded at live size "
                                + unrelatedCount);
                assertNull(admission.displacedOwners[0]);
                assertNull(admission.displacedTracks[0]);
            }
        }
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver,
            int id,
            SmpsSequencerConfig config,
            FixtureTrack... tracks) {
        FixtureSfxData data = new FixtureSfxData(id, List.of(tracks));
        SmpsSequencer sequencer = new SmpsSequencer(
                data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), config);
        sequencer.setSfxPriority(0x70);
        return sequencer;
    }

    private static FixtureTrack track(int channelMask, int pointer) {
        return new FixtureTrack(channelMask, pointer, 0, 0);
    }

    private static SmpsSequencerConfig config(CoordFlagHandler handler) {
        return new SmpsSequencerConfig.Builder()
                .coordFlagHandler(handler)
                .build();
    }

    private static CoordFlagHandler countingHandler(AtomicInteger starts) {
        return new CoordFlagHandler() {
            @Override
            public void onSfxStart(int sfxId) {
                starts.incrementAndGet();
            }

            @Override
            public boolean handleFlag(CoordFlagContext context,
                    SmpsSequencer.Track track, int command) {
                return false;
            }

            @Override
            public int flagParamLength(int command) {
                return -1;
            }
        };
    }

    private static void assertDriverStateEquals(
            SmpsDriverSnapshot expected, SmpsDriverSnapshot actual) {
        assertDeepEquals(expected, actual);
    }

    private static void assertIdentityOrder(
            List<?> expected, List<?> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertSame(expected.get(index), actual.get(index));
        }
    }

    private static int conflictArrayCapacity(
            PreparedSfxAdmission admission) {
        int capacity = -1;
        for (Field field : PreparedSfxAdmission.class.getDeclaredFields()) {
            if (!field.getType().isArray()) {
                continue;
            }
            Class<?> component = field.getType().componentType();
            if (component != SmpsSequencer.class
                    && component != SmpsSequencer.Track.class) {
                continue;
            }
            try {
                field.setAccessible(true);
                int length = Array.getLength(field.get(admission));
                if (capacity == -1) {
                    capacity = length;
                } else {
                    assertEquals(capacity, length,
                            "every ordered conflict array shares one bound");
                }
            } catch (IllegalAccessException failure) {
                throw new AssertionError(failure);
            }
        }
        return capacity;
    }

    private static void assertDeepEquals(Object expected, Object actual) {
        assertDeepEquals(expected, actual, new IdentityHashMap<>());
    }

    private static void assertDeepEquals(
            Object expected, Object actual, Map<Object, Object> seen) {
        if (expected == actual) {
            return;
        }
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.getClass(), actual.getClass());
        if (expected.getClass().isArray()) {
            assertEquals(Array.getLength(expected), Array.getLength(actual));
            for (int index = 0; index < Array.getLength(expected); index++) {
                assertDeepEquals(Array.get(expected, index),
                        Array.get(actual, index), seen);
            }
            return;
        }
        if (expected instanceof Iterable<?> expectedValues
                && actual instanceof Iterable<?> actualValues) {
            var expectedIterator = expectedValues.iterator();
            var actualIterator = actualValues.iterator();
            while (expectedIterator.hasNext()) {
                assertTrue(actualIterator.hasNext());
                assertDeepEquals(expectedIterator.next(),
                        actualIterator.next(), seen);
            }
            assertFalse(actualIterator.hasNext());
            return;
        }
        if (!expected.getClass().isRecord()) {
            assertEquals(expected, actual);
            return;
        }
        if (seen.put(expected, actual) != null) {
            return;
        }
        for (RecordComponent component
                : expected.getClass().getRecordComponents()) {
            try {
                assertDeepEquals(component.getAccessor().invoke(expected),
                        component.getAccessor().invoke(actual), seen);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class FixtureSfxData extends AbstractSmpsData
            implements SmpsSfxData {
        private final List<FixtureTrack> tracks;

        private FixtureSfxData(int id, List<FixtureTrack> tracks) {
            super(new byte[16], 0);
            setId(id);
            this.tracks = tracks;
        }

        @Override
        public int getTickMultiplier() {
            return 1;
        }

        @Override
        public List<? extends SmpsSfxTrack> getTrackEntries() {
            return tracks;
        }

        @Override
        protected void parseHeader() {
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return voiceId == 0 ? new byte[25] : null;
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return null;
        }

        @Override
        public int read16(int offset) {
            return 0;
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }

    private record FixtureTrack(
            int channelMask, int pointer, int transpose, int volume)
            implements SmpsSfxData.SmpsSfxTrack {
    }

    private static final class OrderedStopDriver extends SmpsDriver {
        private final List<String> stopOrder = new java.util.ArrayList<>();
        private SmpsSequencer watched;

        private void watch(SmpsSequencer sequencer) {
            watched = sequencer;
        }

        @Override
        public void writeFm(Object source, int port, int reg, int val) {
            if (source == watched && reg == 0x28) {
                stopOrder.add("FM");
            }
            super.writeFm(source, port, reg, val);
        }

        @Override
        public void stopDac(Object source) {
            if (source == watched) {
                stopOrder.add("DAC");
            }
            super.stopDac(source);
        }
    }

    private static final class CountingRollbackDriver extends SmpsDriver {
        private int captureCalls;
        private int rollbackCalls;

        @Override
        public LiveCommandMutationToken captureLiveCommandMutation() {
            captureCalls++;
            return super.captureLiveCommandMutation();
        }

        @Override
        public void rollbackLiveCommandMutation(
                LiveCommandMutationToken token) {
            rollbackCalls++;
            super.rollbackLiveCommandMutation(token);
        }
    }
}
