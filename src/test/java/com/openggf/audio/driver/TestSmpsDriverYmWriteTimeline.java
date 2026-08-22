package com.openggf.audio.driver;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmServiceTimingProfile.PathKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Segment;
import com.openggf.audio.smps.YmServiceTimingProfile.SegmentKind;
import com.openggf.audio.smps.YmServiceTimingProfile.Variant;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.Synthesizer;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.audio.synth.YmWriteTimeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsDriverYmWriteTimeline {
    private static final Variant VARIANT = new Variant(
            0, 4, false, false, 0, PathKind.FIRST_VOICE_ATTACK);
    private static final Variant WRONG_VARIANT = new Variant(
            1, 4, false, false, 0, PathKind.FIRST_VOICE_ATTACK);
    private static final YmServiceTimingProfile PROFILE =
            YmServiceTimingProfile.of(8,
                    new Segment(SegmentKind.SFX_ADMISSION_PREP,
                            VARIANT,
                            new long[] { 0, 3_570, 3_150, 3_150, 3_150 }),
                    new Segment(SegmentKind.COMPLETION_RESTORE,
                            VARIANT, new long[] { 0, 3_150 }),
                    new Segment(SegmentKind.KEY_OFF,
                            VARIANT, new long[] { 0 }),
                    new Segment(SegmentKind.FREQUENCY_AND_KEY_ON,
                            VARIANT, new long[] { 0, 2_700, 2_880 }));

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void oneServiceCursorOrdersProfiledUnprofiledAndLaterMethodWrites() {
        // Break caught: a new scope or Java helper resets the cursor and lets a
        // later immediate write overtake delayed completion/restore writes.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        SmpsSequencer music = sequencer(
                driver, YmServiceTimingProfile.none(), 0x81);

        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            driver.writeFm(source, 0, 0xB0, 0x01);
            driver.writeFm(music, 0, 0xB4, 0xC0);
        }
        publishFromAnotherJavaMethod(driver, music, 0x22, 0x08);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.KEY_OFF, VARIANT)) {
            driver.writeFm(source, 0, 0x28, 0x00);
        }
        driver.endSequencerService(service);

        SmpsDriverSnapshot snapshot = driver.captureSnapshot();
        List<YmWriteTimeline.Entry> pending =
                snapshot.synthSnapshot().ymWriteTimeline().pending();
        assertEquals(List.of(0L, 3_150L, 3_150L, 3_150L),
                pending.stream().map(YmWriteTimeline.Entry::dueMasterCycle)
                        .toList());
        assertEquals(List.of(0L, 1L, 2L, 3L),
                pending.stream().map(YmWriteTimeline.Entry::sourceOrdinal)
                        .toList());
        assertEquals(List.of(0xB0, 0xB4, 0x22, 0x28),
                pending.stream().map(YmWriteTimeline.Entry::register).toList());
        assertEquals(3_150L, snapshot.ymServiceCursor());
        assertEquals(1L, snapshot.nextYmServiceOrdinal());
        assertEquals(4L, snapshot.nextYmWriteOrdinal());
        assertEquals(1L, snapshot.driverGeneration(),
                "constructor full-silence establishes generation one");
    }

    @Test
    void noneProfileRetainsImmediateWriteAndCallbackBehavior() {
        // Break caught: S1/S2's absent profile starts queuing otherwise-immediate writes.
        List<String> callbacks = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                44_100.0, recordingObserver(callbacks));
        callbacks.clear();
        SmpsSequencer source = sequencer(
                driver, YmServiceTimingProfile.none(), 0x81);
        callbacks.clear();

        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.KEY_OFF, VARIANT)) {
            driver.writeFm(source, 0, 0x28, 0x00);
        }
        driver.endSequencerService(service);

        assertEquals(List.of("0:28:00"), callbacks);
        assertTrue(driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().isEmpty());
    }

    @Test
    void suppressedNativeAttemptAdvancesCursorWithoutPublishingWrite() {
        // Break caught: a native helper's audited suppression either emits a
        // phantom chip write or fails to advance the following source slot.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);

        try (Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            driver.writeFm(source, 0, 0xB0, 0x01);
            timing.consumeSuppressedHardwareAttempt();
        }
        driver.writeFm(source, 0, 0x22, 0x08);
        driver.endSequencerService(service);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(List.of(0L, 3_150L),
                pending.stream().map(YmWriteTimeline.Entry::dueMasterCycle)
                        .toList());
        assertEquals(List.of(0xB0, 0x22),
                pending.stream().map(YmWriteTimeline.Entry::register).toList());
        assertEquals(List.of(0L, 1L),
                pending.stream().map(YmWriteTimeline.Entry::sourceOrdinal)
                        .toList());
    }

    @Test
    void nestedScopePoisonsAndRollsBackTheWholeService() {
        // Break caught: nested source scopes reset cursor/count state or publish
        // the outer scope's partial journal.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        StableState before = stableState(driver);
        driver.beginSequencerService(source,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope outer = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(source, 0, 0xB0, 0x01);

        assertThrows(IllegalStateException.class,
                () -> driver.beginYmTiming(
                        source, SegmentKind.KEY_OFF, VARIANT));
        assertThrows(IllegalStateException.class, outer::close);
        assertStableState(before, driver);
    }

    @Test
    void missingAndExcessWritesPoisonWithoutPublishingCallbacks() {
        // Break caught: count mismatch commits an N-1 prefix or invokes a chip
        // callback which rollback cannot retract.
        List<String> callbacks = new ArrayList<>();
        SmpsDriver missingDriver = new SmpsDriver(
                44_100.0, recordingObserver(callbacks));
        callbacks.clear();
        SmpsSequencer missingSource = sequencer(
                missingDriver, PROFILE, 0xA0);
        callbacks.clear();
        StableState missingBefore = stableState(missingDriver);
        missingDriver.beginSequencerService(missingSource,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope missing = missingDriver.beginYmTiming(
                missingSource, SegmentKind.COMPLETION_RESTORE, VARIANT);
        missingDriver.writeFm(missingSource, 0, 0xB0, 0x01);

        assertThrows(IllegalStateException.class, missing::close);
        assertEquals(List.of(), callbacks);
        assertStableState(missingBefore, missingDriver);

        SmpsDriver excessDriver = new SmpsDriver(
                44_100.0, recordingObserver(callbacks));
        callbacks.clear();
        SmpsSequencer excessSource = sequencer(
                excessDriver, PROFILE, 0xA1);
        callbacks.clear();
        StableState excessBefore = stableState(excessDriver);
        excessDriver.beginSequencerService(excessSource,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope excess = excessDriver.beginYmTiming(
                excessSource, SegmentKind.KEY_OFF, VARIANT);
        excessDriver.writeFm(excessSource, 0, 0x28, 0x00);

        assertThrows(IllegalStateException.class,
                () -> excessDriver.writeFm(
                        excessSource, 0, 0x28, 0x00));
        assertThrows(IllegalStateException.class, excess::close);
        assertEquals(List.of(), callbacks);
        assertStableState(excessBefore, excessDriver);
    }

    @Test
    void wrongVariantAndClosedScopeConsumptionCannotMutateStableState() {
        // Break caught: lookup failure or stale scope use consumes an ordinal or
        // leaves the service transaction half-open.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        StableState before = stableState(driver);
        driver.beginSequencerService(source,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);

        assertThrows(IllegalArgumentException.class,
                () -> driver.beginYmTiming(
                        source, SegmentKind.KEY_OFF, WRONG_VARIANT));
        assertStableState(before, driver);

        SmpsDriver retry = new SmpsDriver();
        SmpsSequencer retrySource = sequencer(retry, PROFILE, 0xA1);
        SmpsDriverServiceObserver.ServiceEvent service =
                retry.beginSequencerService(retrySource,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope timing = retry.beginYmTiming(
                retrySource, SegmentKind.KEY_OFF, VARIANT);
        retry.writeFm(retrySource, 0, 0x28, 0x00);
        timing.close();
        assertThrows(IllegalStateException.class,
                timing::consumeSuppressedHardwareAttempt);
        retry.endSequencerService(service);
        assertEquals(1, retry.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().size());
    }

    @Test
    void admissionPreparationUsesOneAuthorizedPublicationBoundary() {
        // Break caught: admission key-off/SSG-EG clear re-enters arbitration,
        // skips a timing slot, or invokes chip callbacks before timeline drain.
        List<String> chipCallbacks = new ArrayList<>();
        List<SfxContentionObserver.Arbitration> arbitrations =
                new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                44_100.0, recordingObserver(chipCallbacks));
        SmpsSequencer source = sequencer(
                driver, PROFILE, 0xA0,
                SmpsSequencerConfig.FmSfxTakeoverMode
                        .KEY_OFF_CLEAR_SSG_EG);
        driver.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onRoleArbitrated(
                    SfxContentionObserver.Arbitration arbitration) {
                arbitrations.add(arbitration);
            }
        });
        chipCallbacks.clear();
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);

        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.SFX_ADMISSION_PREP, VARIANT)) {
            driver.applyAdmissionFmPreparation(source);
        }
        driver.endSequencerService(service);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(List.of(0x28, 0x90, 0x98, 0x94, 0x9C),
                pending.stream().map(YmWriteTimeline.Entry::register).toList());
        assertEquals(List.of(0L, 3_570L, 6_720L, 9_870L, 13_020L),
                pending.stream().map(YmWriteTimeline.Entry::dueMasterCycle)
                        .toList());
        assertTrue(pending.stream().allMatch(entry ->
                entry.segment() == SegmentKind.SFX_ADMISSION_PREP));
        assertEquals(List.of(), arbitrations,
                "already-authorized admission preparation must not arbitrate");
        assertEquals(List.of(), chipCallbacks,
                "scheduled writes must not callback before drain");

        driver.render(new short[64]);

        assertEquals(List.of(
                "0:28:00", "0:90:00", "0:98:00", "0:94:00",
                "0:9C:00"), chipCallbacks);
    }

    @Test
    void admissionOwnsOneOuterTransactionUntilAllMutationCompletes() {
        // Break caught: an implicit preparation scope publishes its five writes
        // before the enclosing admission has finished, so a later admission
        // failure leaves a committed timeline prefix behind.
        List<String> chipCallbacks = new ArrayList<>();
        FailingTimedAdmissionDriver driver =
                new FailingTimedAdmissionDriver(chipCallbacks);
        SmpsSequencer candidate = sequencer(
                driver, PROFILE, 0xA0,
                SmpsSequencerConfig.FmSfxTakeoverMode
                        .KEY_OFF_CLEAR_SSG_EG);
        PreparedSfxAdmission admission = driver.prepareNewSfxAdmission(
                candidate, 0, 1);
        candidate.beginSfxAdmission();
        chipCallbacks.clear();
        StableState before = stableState(driver);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> driver.commitSfxAdmission(admission));

        assertEquals("failure after timed preparation", failure.getMessage());
        assertStableState(before, driver);
        assertTrue(driver.sequencersForTesting().isEmpty());
        assertEquals(List.of(), chipCallbacks);
    }

    @Test
    void poisonedServicePublishesNoLogicalOrChipObserverEventsAndRetryOnce() {
        // Break caught: begin/arbitration notifications escape before count
        // validation, then reappear as duplicates when the service is retried.
        List<String> logical = new ArrayList<>();
        List<String> chip = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                44_100.0, recordingObserver(chip));
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                logical.add("begin");
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                logical.add("end:" + snapshot.nextYmWriteOrdinal());
            }
        });
        driver.setSfxContentionObserver(new SfxContentionObserver() {
            @Override
            public void onRoleArbitrated(Arbitration arbitration) {
                logical.add("arbitration:" + arbitration.acquired());
            }
        });
        driver.addSequencer(source, true);
        logical.clear();
        chip.clear();
        StableState before = stableState(driver);
        driver.beginSequencerService(source,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope missing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(source, 0, 0xB0, 0x01);

        assertThrows(IllegalStateException.class, missing::close);
        assertEquals(List.of(), logical);
        assertEquals(List.of(), chip);
        assertStableState(before, driver);

        SmpsDriverServiceObserver.ServiceEvent retry =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            driver.writeFm(source, 0, 0xB0, 0x01);
            driver.writeFm(source, 0, 0xB4, 0xC0);
        }
        driver.endSequencerService(retry);

        assertEquals(List.of(
                "begin", "arbitration:true", "arbitration:true", "end:2"),
                logical);
        assertEquals(List.of(), chip);
        driver.render(new short[32]);
        assertEquals(List.of("0:B0:01", "0:B4:C0"), chip);
    }

    @Test
    void aggregateCapacityNCommitsAndNMinusOneFailsBeforeMutation() {
        // Break caught: capacity is sized to one segment instead of every live
        // music/SFX profile plus the PAL repeated service horizon.
        int aggregateBound = 32; // (music 8 + SFX 8) * (normal + PAL repeat)
        SmpsDriver exact = new SmpsDriver();
        exact.setRegion(SmpsSequencer.Region.PAL);
        SmpsSequencer exactMusic = palSequencer(exact, 0x81);
        SmpsSequencer exactSfx = palSequencer(exact, 0xA0);
        exact.addSequencer(exactMusic, false);
        exact.addSequencer(exactSfx, true);
        fillRemainingCapacity(exact, aggregateBound);
        exactMusic = exact.sequencersForTesting().getFirst();
        SmpsDriverServiceObserver.ServiceEvent exactService =
                exact.beginSequencerService(exactMusic,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = exact.beginYmTiming(
                exactMusic, SegmentKind.KEY_OFF, VARIANT)) {
            exact.writeFm(exactMusic, 0, 0x22, 0x08);
        }
        exact.endSequencerService(exactService);
        assertEquals(4_096 - aggregateBound + 1,
                exact.captureSnapshot().synthSnapshot()
                        .ymWriteTimeline().pending().size());

        List<String> logical = new ArrayList<>();
        List<String> chip = new ArrayList<>();
        SmpsDriver shortDriver = new SmpsDriver(
                44_100.0, recordingObserver(chip));
        shortDriver.setRegion(SmpsSequencer.Region.PAL);
        SmpsSequencer shortMusic = palSequencer(shortDriver, 0x81);
        SmpsSequencer shortSfx = palSequencer(shortDriver, 0xA0);
        shortDriver.addSequencer(shortMusic, false);
        shortDriver.addSequencer(shortSfx, true);
        fillRemainingCapacity(shortDriver, aggregateBound - 1);
        SmpsSequencer restoredShortMusic =
                shortDriver.sequencersForTesting().getFirst();
        shortDriver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                logical.add("begin");
            }
        });
        chip.clear();
        StableState before = stableState(shortDriver);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> shortDriver.beginSequencerService(
                        restoredShortMusic,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK));

        assertTrue(failure.getMessage().contains("aggregate service bound 32"));
        assertEquals(List.of(), logical);
        assertEquals(List.of(), chip);
        assertStableState(before, shortDriver);
    }

    @Test
    void committedSnapshotsRestoreBeforeAndAfterPartialDrainExactlyOnce() {
        // Break caught: rewind captures an unpublished journal, loses the
        // partially drained suffix, or replays a drained callback twice.
        List<String> originalCallbacks = new ArrayList<>();
        SmpsDriver original = new SmpsDriver(
                Ym2612Chip.getInternalRate(),
                recordingObserver(originalCallbacks));
        SmpsSequencer source = sequencer(original, PROFILE, 0xA0);
        originalCallbacks.clear();
        publishFrequencyService(original, source);
        SmpsDriverSnapshot beforeDrain = original.captureSnapshot();
        assertEquals(3, beforeDrain.synthSnapshot()
                .ymWriteTimeline().pending().size());

        original.render(new short[2]);
        assertEquals(List.of("0:A4:22"), originalCallbacks);
        SmpsDriverSnapshot partial = original.captureSnapshot();
        assertEquals(List.of(0xA0, 0x28), partial.synthSnapshot()
                .ymWriteTimeline().pending().stream()
                .map(YmWriteTimeline.Entry::register).toList());

        DrainResult first = restoreAndDrain(partial);
        DrainResult second = restoreAndDrain(partial);
        assertArrayEquals(first.pcm(), second.pcm());
        assertEquals(List.of("0:A0:69", "0:28:F0"), first.callbacks());
        assertEquals(first.callbacks(), second.callbacks());
        assertEquals(first.finalSynth(), second.finalSynth());

        DrainResult complete = restoreAndDrain(beforeDrain);
        assertEquals(List.of("0:A4:22", "0:A0:69", "0:28:F0"),
                complete.callbacks());
    }

    @Test
    void stopAllSfxRetainsCommittedWritesUntilTheirSingleDrain() {
        // Break caught: ordinary SFX removal is treated as a generation barrier
        // and erases self-contained writes from a completed source.
        List<String> callbacks = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                Ym2612Chip.getInternalRate(), recordingObserver(callbacks));
        SmpsSequencer sfx = sequencer(driver, PROFILE, 0xA0);
        driver.addSequencer(sfx, true);
        callbacks.clear();
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(sfx,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                sfx, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            driver.writeFm(sfx, 0, 0x22, 0x08);
            driver.writeFm(sfx, 0, 0x27, 0x30);
        }
        driver.endSequencerService(service);

        driver.stopAllSfx();

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(11, pending.size());
        assertEquals(List.of(0x22, 0x27), pending.subList(0, 2).stream()
                .map(YmWriteTimeline.Entry::register).toList());
        assertEquals(List.of(), callbacks);
        driver.render(new short[16]);
        assertEquals(List.of("0:22:08", "0:27:30"),
                callbacks.subList(0, 2));
        assertEquals(11, callbacks.size());
        driver.render(new short[16]);
        assertEquals(11, callbacks.size());
    }

    @Test
    void stopAllSfxCancelsOnlyItsUnpublishedSfxService() {
        // Break caught: the stop command either retains a half-built SFX
        // journal or treats older atomically committed entries as removable
        // sequencer-owned state.
        List<String> callbacks = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                Ym2612Chip.getInternalRate(), recordingObserver(callbacks));
        SmpsSequencer sfx = sequencer(driver, PROFILE, 0xA0);
        driver.addSequencer(sfx, true);
        callbacks.clear();
        SmpsDriverServiceObserver.ServiceEvent committedService =
                driver.beginSequencerService(sfx,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                sfx, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            driver.writeFm(sfx, 0, 0xB0, 0x01);
            driver.writeFm(sfx, 0, 0xB4, 0xC0);
        }
        driver.endSequencerService(committedService);
        SmpsDriverSnapshot committed = driver.captureSnapshot();

        driver.beginSequencerService(sfx,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope unpublished = driver.beginYmTiming(
                sfx, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(sfx, 0, 0x22, 0x08);

        driver.stopAllSfx();

        assertThrows(IllegalStateException.class, unpublished::close);
        SmpsDriverSnapshot stopped = driver.captureSnapshot();
        assertEquals(committed.ymServiceCursor(), stopped.ymServiceCursor());
        assertEquals(committed.nextYmServiceOrdinal() + 1,
                stopped.nextYmServiceOrdinal());
        assertEquals(committed.nextYmWriteOrdinal() + 9,
                stopped.nextYmWriteOrdinal());
        assertEquals(committed.driverGeneration(),
                stopped.driverGeneration());
        assertTrue(driver.sequencersForTesting().isEmpty());
        assertEquals(11, stopped.synthSnapshot().ymWriteTimeline()
                .pending().size());
        assertTrue(stopped.synthSnapshot().ymWriteTimeline().pending()
                .stream().noneMatch(entry -> entry.register() == 0x22));
        assertEquals(List.of(), callbacks,
                "the shipped stop sequence remains drain-bound");
        driver.render(new short[16]);
        assertEquals(List.of("0:B0:01", "0:B4:C0"),
                callbacks.subList(0, 2));
        assertEquals(11, callbacks.size());
    }

    @Test
    void fullSilenceBarrierDiscardsOnlyTheUnpublishedServiceJournal() {
        // Break caught: a full-silence generation barrier leaves an old-epoch
        // transaction alive, then a later close rolls the barrier itself back.
        List<String> logical = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        long generation = driver.captureSnapshot().driverGeneration();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                logical.add("begin");
            }
        });
        driver.beginSequencerService(source,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(source, 0, 0xB0, 0x01);

        driver.silenceAll();

        assertThrows(IllegalStateException.class, timing::close);
        SmpsDriverSnapshot after = driver.captureSnapshot();
        assertEquals(generation + 1, after.driverGeneration());
        assertTrue(after.synthSnapshot().ymWriteTimeline().pending().isEmpty());
        assertEquals(0, after.nextYmWriteOrdinal());
        assertEquals(List.of(), logical,
                "the unpublished service-begin notification is discarded");
    }

    @Test
    void adoptionRemapsPendingSfxToTargetGenerationByDescriptor() {
        // Break caught: replacement copies the source epoch/object identity or
        // discards committed SFX writes when retaining newly loaded music.
        SmpsDriver sourceDriver = new SmpsDriver(
                Ym2612Chip.getInternalRate());
        SmpsSequencer sfx = sequencer(sourceDriver, PROFILE, 0xA0);
        sourceDriver.addSequencer(sfx, true);
        SmpsDriverServiceObserver.ServiceEvent sourceService =
                sourceDriver.beginSequencerService(sfx,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = sourceDriver.beginYmTiming(
                sfx, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            sourceDriver.writeFm(sfx, 0, 0xB0, 0x01);
            sourceDriver.writeFm(sfx, 0, 0xB4, 0xC0);
        }
        sourceDriver.endSequencerService(sourceService);
        SmpsSourceDescriptor sfxDescriptor = sfx.getSourceDescriptor();

        List<String> callbacks = new ArrayList<>();
        SmpsDriver target = new SmpsDriver(
                Ym2612Chip.getInternalRate(), recordingObserver(callbacks));
        target.silenceAll();
        SmpsSequencer music = sequencer(
                target, YmServiceTimingProfile.none(), 0x81);
        target.addSequencer(music, false);
        callbacks.clear();
        assertEquals(2L, target.captureSnapshot().driverGeneration());

        target.adoptActiveSfxFrom(sourceDriver);

        SmpsDriverSnapshot adopted = target.captureSnapshot();
        assertEquals(2L, adopted.driverGeneration());
        assertEquals(2, adopted.sequencers().size());
        assertEquals(2, adopted.synthSnapshot()
                .ymWriteTimeline().pending().size());
        assertTrue(adopted.synthSnapshot().ymWriteTimeline().pending()
                .stream().allMatch(entry ->
                        entry.driverGeneration() == 2
                                && entry.sourceDescriptor()
                                .equals(sfxDescriptor)));
        assertEquals(List.of("0:2B:80", "0:2B:80"), callbacks,
                "restore reconstruction retains its established callbacks");
        callbacks.clear();
        target.render(new short[16]);
        assertEquals(List.of("0:B0:01", "0:B4:C0"), callbacks);
        target.render(new short[16]);
        assertEquals(2, callbacks.size());
    }

    @Test
    void hybridAndSampleAccurateDrainTheSamePendingService() {
        // Break caught: hybrid chunk rendering skips an internal timeline drain
        // or changes callback/source order relative to sample-accurate reads.
        SmpsDriver seed = new SmpsDriver();
        SmpsSequencer source = sequencer(seed, PROFILE, 0xA0);
        publishFrequencyService(seed, source);
        SmpsDriverSnapshot pending = seed.captureSnapshot();

        List<String> hybridCallbacks = new ArrayList<>();
        SmpsDriver hybrid = new SmpsDriver(
                44_100.0, recordingObserver(hybridCallbacks));
        hybrid.restoreSnapshot(pending);
        hybridCallbacks.clear();
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.HYBRID);

        List<String> accurateCallbacks = new ArrayList<>();
        SmpsDriver accurate = new SmpsDriver(
                44_100.0, recordingObserver(accurateCallbacks));
        accurate.restoreSnapshot(pending);
        accurateCallbacks.clear();
        accurate.setReadModeForTesting(SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[256];
        short[] accuratePcm = new short[256];
        hybrid.read(hybridPcm);
        accurate.read(accuratePcm);

        assertArrayEquals(hybridPcm, accuratePcm);
        assertEquals(hybridCallbacks, accurateCallbacks);
        assertEquals(List.of("0:A4:22", "0:A0:69", "0:28:F0"),
                hybridCallbacks);
        assertDeepEquals(hybrid.captureSnapshot().synthSnapshot(),
                accurate.captureSnapshot().synthSnapshot());
    }

    @Test
    void cursorOverflowAndNegativeProfileAdvanceRejectWithoutMutation() {
        // Break caught: checked cycle arithmetic wraps a later slot to the
        // front of the queue, or a negative source delay reaches the driver.
        assertThrows(IllegalArgumentException.class,
                () -> new Segment(SegmentKind.KEY_OFF, VARIANT,
                        new long[] { 0, -1 }));

        SmpsDriver driver = driverWithPendingDueAtLongMax();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        StableState before = stableState(driver);
        driver.beginSequencerService(source,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(source, 0, 0xB0, 0x01);

        assertThrows(ArithmeticException.class,
                () -> driver.writeFm(source, 0, 0xB4, 0xC0));
        assertThrows(IllegalStateException.class, timing::close);
        assertStableState(before, driver);
    }

    @Test
    void inFlightSnapshotsAndTokensAreRejectedWithoutPoisoningService() {
        // Break caught: a rewind or command token exposes a transaction-local
        // prefix which can later be restored as though it were committed.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(source, 0, 0xB0, 0x01);

        assertThrows(IllegalStateException.class, driver::captureSnapshot);
        assertThrows(IllegalStateException.class,
                driver::captureLiveCommandMutation);

        driver.writeFm(source, 0, 0xB4, 0xC0);
        timing.close();
        driver.endSequencerService(service);
        assertEquals(2, driver.captureSnapshot().nextYmWriteOrdinal());
    }

    @Test
    void liveCommandRollbackRestoresCommittedTimelineCursorAndOrdinals() {
        // Break caught: command rollback restores sequencers/locks but leaves a
        // committed delayed write or consumes its stable service identity.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        SmpsDriver.LiveCommandMutationToken token =
                driver.captureLiveCommandMutation();
        StableState before = stableState(driver);

        publishFrequencyService(driver, source);
        assertEquals(3, driver.captureSnapshot().nextYmWriteOrdinal());

        driver.rollbackLiveCommandMutation(token);

        assertStableState(before, driver);
    }

    private static void publishFromAnotherJavaMethod(
            SmpsDriver driver, SmpsSequencer source,
            int register, int value) {
        driver.writeFm(source, 0, register, value);
    }

    private static void publishFrequencyService(
            SmpsDriver driver, SmpsSequencer source) {
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.FREQUENCY_AND_KEY_ON, VARIANT)) {
            driver.writeFm(source, 0, 0xA4, 0x22);
            driver.writeFm(source, 0, 0xA0, 0x69);
            driver.writeFm(source, 0, 0x28, 0xF0);
        }
        driver.endSequencerService(service);
    }

    private static DrainResult restoreAndDrain(
            SmpsDriverSnapshot snapshot) {
        List<String> callbacks = new ArrayList<>();
        SmpsDriver restored = new SmpsDriver(
                Ym2612Chip.getInternalRate(),
                recordingObserver(callbacks));
        restored.restoreSnapshot(snapshot);
        callbacks.clear();
        short[] pcm = new short[32];
        restored.render(pcm);
        return new DrainResult(pcm, List.copyOf(callbacks),
                restored.captureSnapshot().synthSnapshot());
    }

    private static ChipWriteObserver recordingObserver(List<String> events) {
        return new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                events.add("%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
            }
        };
    }

    private static StableState stableState(SmpsDriver driver) {
        SmpsDriverSnapshot snapshot = driver.captureSnapshot();
        return new StableState(
                snapshot.synthSnapshot(),
                snapshot.fmLockSequencerIds(),
                snapshot.psgLockSequencerIds(),
                snapshot.ymServiceCursor(),
                snapshot.nextYmServiceOrdinal(),
                snapshot.nextYmWriteOrdinal(),
                snapshot.driverGeneration());
    }

    private static void assertStableState(
            StableState expected, SmpsDriver driver) {
        SmpsDriverSnapshot actual = driver.captureSnapshot();
        assertEquals(expected.synthSnapshot(), actual.synthSnapshot());
        assertArrayEquals(expected.fmLocks(), actual.fmLockSequencerIds());
        assertArrayEquals(expected.psgLocks(), actual.psgLockSequencerIds());
        assertEquals(expected.serviceCursor(), actual.ymServiceCursor());
        assertEquals(expected.nextServiceOrdinal(),
                actual.nextYmServiceOrdinal());
        assertEquals(expected.nextWriteOrdinal(),
                actual.nextYmWriteOrdinal());
        assertEquals(expected.driverGeneration(), actual.driverGeneration());
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

    private static SmpsSequencer sequencer(
            SmpsDriver driver, YmServiceTimingProfile profile, int id) {
        return sequencer(driver, profile, id,
                SmpsSequencerConfig.FmSfxTakeoverMode.REGISTER_SEQUENCE);
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver, YmServiceTimingProfile profile, int id,
            SmpsSequencerConfig.FmSfxTakeoverMode takeoverMode) {
        MinimalData data = new MinimalData();
        data.setId(id);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .ymServiceTimingProfile(profile)
                        .fmSfxTakeoverMode(takeoverMode)
                        .build());
        sequencer.addTrack(track());
        return sequencer;
    }

    private static SmpsSequencer palSequencer(
            SmpsDriver driver, int id) {
        MinimalData data = new MinimalData();
        data.setId(id);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .ymServiceTimingProfile(PROFILE)
                        .fmSfxTakeoverMode(
                                SmpsSequencerConfig.FmSfxTakeoverMode
                                        .REGISTER_SEQUENCE)
                        .palServicePolicy(
                                SmpsSequencerConfig.PalServicePolicy
                                        .FULL_DRIVER_REPEAT_EVERY_SIXTH)
                        .build());
        sequencer.addTrack(track());
        return sequencer;
    }

    private static void fillRemainingCapacity(
            SmpsDriver driver, int remaining) {
        SmpsDriverSnapshot base = driver.captureSnapshot();
        int capacity = base.synthSnapshot().ymWriteTimeline().capacity();
        int occupied = capacity - remaining;
        SmpsSourceDescriptor descriptor = new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.UNKNOWN, 0x55,
                "capacity-fixture", null, 0, 1, 1, false, 0);
        List<YmWriteTimeline.Entry> pending = new ArrayList<>(occupied);
        for (int ordinal = 0; ordinal < occupied; ordinal++) {
            pending.add(new YmWriteTimeline.Entry(
                    0, ordinal, 0, 0x22, ordinal & 0xFF,
                    base.driverGeneration(), 0, descriptor, null));
        }
        VirtualSynthesizer.Snapshot synth = base.synthSnapshot();
        VirtualSynthesizer.Snapshot filledSynth =
                new VirtualSynthesizer.Snapshot(
                        synth.outputSampleRate(), synth.ym(), synth.psg(),
                        new YmWriteTimeline.Snapshot(
                                capacity, occupied, pending),
                        synth.renderedYmMasterCycle(),
                        synth.ymTimelineGeneration());
        driver.restoreSnapshot(new SmpsDriverSnapshot(
                base.region(), base.readMode(), base.palFullUpdateCounter(),
                base.sfxPriorityLatch(), base.spindashRevPlayingCounter(),
                base.spindashRevFrequencyIndex(), base.continuousSfxId(),
                base.continuousSfxFlag(), base.contSfxLoopCnt(),
                base.sequencers(), base.fmLockSequencerIds(),
                base.psgLockSequencerIds(), filledSynth, 0, 0, occupied,
                synth.ymTimelineGeneration()));
    }

    private static SmpsDriver driverWithPendingDueAtLongMax() {
        SmpsDriver driver = new SmpsDriver();
        SmpsDriverSnapshot base = driver.captureSnapshot();
        SmpsSourceDescriptor descriptor = new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.UNKNOWN, 0x55,
                "overflow-fixture", null, 0, 1, 1, false, 0);
        YmWriteTimeline.Entry pending = new YmWriteTimeline.Entry(
                Long.MAX_VALUE, 0, 0, 0x22, 0x08,
                base.driverGeneration(), 0, descriptor, null);
        VirtualSynthesizer.Snapshot synth = base.synthSnapshot();
        VirtualSynthesizer.Snapshot filledSynth =
                new VirtualSynthesizer.Snapshot(
                        synth.outputSampleRate(), synth.ym(), synth.psg(),
                        new YmWriteTimeline.Snapshot(
                                synth.ymWriteTimeline().capacity(), 1,
                                List.of(pending)),
                        synth.renderedYmMasterCycle(),
                        synth.ymTimelineGeneration());
        driver.restoreSnapshot(new SmpsDriverSnapshot(
                base.region(), base.readMode(), base.palFullUpdateCounter(),
                base.sfxPriorityLatch(), base.spindashRevPlayingCounter(),
                base.spindashRevFrequencyIndex(), base.continuousSfxId(),
                base.continuousSfxFlag(), base.contSfxLoopCnt(),
                base.sequencers(), base.fmLockSequencerIds(),
                base.psgLockSequencerIds(), filledSynth,
                Long.MAX_VALUE, 1, 1, base.driverGeneration()));
        return driver;
    }

    private static SmpsSequencer.Track track() {
        try {
            var constructor = SmpsSequencer.Track.class
                    .getDeclaredConstructor(int.class,
                            SmpsSequencer.TrackType.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    0, SmpsSequencer.TrackType.FM, 0);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class FailingTimedAdmissionDriver
            extends SmpsDriver {
        private FailingTimedAdmissionDriver(List<String> callbacks) {
            super(44_100.0, recordingObserver(callbacks));
        }

        @Override
        void applyAdmissionFmPreparation(SmpsSequencer sequencer) {
            try (Synthesizer.YmTimingScope ignored = beginYmTiming(
                    sequencer, SegmentKind.SFX_ADMISSION_PREP, VARIANT)) {
                super.applyAdmissionFmPreparation(sequencer);
            }
            throw new IllegalStateException(
                    "failure after timed preparation");
        }
    }

    private record StableState(
            com.openggf.audio.synth.VirtualSynthesizer.Snapshot synthSnapshot,
            int[] fmLocks,
            int[] psgLocks,
            long serviceCursor,
            long nextServiceOrdinal,
            long nextWriteOrdinal,
            long driverGeneration) {
    }

    private record DrainResult(
            short[] pcm,
            List<String> callbacks,
            VirtualSynthesizer.Snapshot finalSynth) {
    }

    private static final class MinimalData extends AbstractSmpsData {
        private MinimalData() {
            super(new byte[] { (byte) 0x81, 20 }, 0);
        }

        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int id) { return new byte[25]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }
    }
}
