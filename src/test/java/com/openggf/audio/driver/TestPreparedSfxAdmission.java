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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

        assertDeepEquals(synthBefore, driver.captureSynthSnapshot());
        assertDriverStateEquals(driverBefore, driver.captureSnapshot());
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
        SmpsDriver owner = new SmpsDriver();
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
        assertEquals(List.of(sequencer), owner.sequencersForTesting());
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
        assertEquals(expected.region(), actual.region());
        assertEquals(expected.readMode(), actual.readMode());
        assertEquals(expected.continuousSfxId(), actual.continuousSfxId());
        assertEquals(expected.continuousSfxFlag(), actual.continuousSfxFlag());
        assertEquals(expected.contSfxLoopCnt(), actual.contSfxLoopCnt());
        assertArrayEquals(expected.fmLockSequencerIds(),
                actual.fmLockSequencerIds());
        assertArrayEquals(expected.psgLockSequencerIds(),
                actual.psgLockSequencerIds());
        assertEquals(expected.sequencers().size(), actual.sequencers().size());
        assertDeepEquals(expected.synthSnapshot(), actual.synthSnapshot());
    }

    private static void assertIdentityOrder(
            List<?> expected, List<?> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertSame(expected.get(index), actual.get(index));
        }
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
}
