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
import com.openggf.audio.synth.PsgChip;
import com.openggf.audio.synth.Synthesizer;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.audio.synth.YmWriteTimeline;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertEquals(List.of(), driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending());
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
    void overriddenMusicHelperConsumesOneAuditedSlotBeforeSfxKeyOn() {
        // Break caught: an authentic music note-off rejected by the FM5 SFX
        // lock consumes no source slot, so the following SFX key-on either
        // shifts early or the two-slot scope aborts at close.
        YmServiceTimingProfile twoAttempts = YmServiceTimingProfile.of(2,
                new Segment(SegmentKind.COMPLETION_RESTORE, VARIANT,
                        new long[] { 0, 3_150 }));
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = sequencer(
                driver, YmServiceTimingProfile.none(), 0x81);
        music.trackAt(0).channelId = 4;
        driver.addSequencer(music, false);
        SmpsSequencer sfx = sequencer(driver, twoAttempts, 0xA0);
        sfx.trackAt(0).channelId = 4;
        driver.addSequencer(sfx, true);
        setFmLock(driver, 4, sfx);
        music.trackAt(0).overridden = true;

        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(sfx,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                sfx, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            music.stopNote(music.trackAt(0));
            driver.writeFm(sfx, 0, 0x28, 0xF5);
        }
        driver.endSequencerService(service);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(1, pending.size(),
                "the suppressed music helper publishes no entry");
        assertEquals(3_150L, pending.getFirst().dueMasterCycle(),
                "the following SFX key-on retains its declared second slot");
        assertEquals(0x28, pending.getFirst().register());
        assertEquals(0xF5, pending.getFirst().value());
    }

    @Test
    void suppressedOnlyServiceCarriesItsCommittedCursorIntoNextService() {
        // Break caught: a suppressed final slot advances no pending entry, so
        // the next service re-anchors at zero and schedules backwards.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        SmpsDriverServiceObserver.ServiceEvent suppressedService =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            timing.consumeSuppressedHardwareAttempt();
            timing.consumeSuppressedHardwareAttempt();
        }
        driver.endSequencerService(suppressedService);

        assertEquals(3_150L, driver.captureSnapshot().ymServiceCursor());
        SmpsDriverServiceObserver.ServiceEvent nextService =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.KEY_OFF, VARIANT)) {
            driver.writeFm(source, 0, 0x28, 0x00);
        }
        driver.endSequencerService(nextService);

        YmWriteTimeline.Entry pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending().getFirst();
        assertEquals(3_150L, pending.dueMasterCycle());
        assertEquals(0L, pending.sourceOrdinal());
        assertEquals(1L, pending.serviceOrdinal());
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
    void rejectedArbitrationConsumesExactlyOneAuditedSuppressedSlot() {
        // Break caught: OpenGGF arbitration leaves a rejected native hardware
        // attempt unclassified, or requires a caller to double-account it.
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer incumbent = prioritySequencer(driver, 0xA0);
        SmpsSequencer challenger = prioritySequencer(driver, 0xA1);
        incumbent.setSfxPriority(0x60);
        challenger.setSfxPriority(0x20);
        driver.addSequencer(incumbent, true);
        driver.writeFm(incumbent, 0, 0x28, 0x00);
        driver.addSequencer(challenger, false);
        challenger.setIsSfx(true);

        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(challenger,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                challenger, SegmentKind.KEY_OFF, VARIANT)) {
            driver.writeFm(challenger, 0, 0x28, 0x00);
        }
        driver.endSequencerService(service);

        assertEquals(List.of(), driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending());
        assertEquals(0L, driver.captureSnapshot().ymServiceCursor());
    }

    @Test
    void profiledSetInstrumentIsRejectedBeforeDirectChipMutation() {
        // Break caught: VirtualSynthesizer.setInstrument expands directly into
        // thirty YM writes and bypasses timing slots and the drain callback.
        List<String> profiledCallbacks = new ArrayList<>();
        SmpsDriver profiled = new SmpsDriver(
                44_100.0, recordingObserver(profiledCallbacks));
        profiledCallbacks.clear();
        SmpsSequencer timedSource = sequencer(profiled, PROFILE, 0xA0);
        profiledCallbacks.clear();
        SmpsDriverSnapshot before = profiled.captureSnapshot();

        assertThrows(IllegalStateException.class,
                () -> profiled.setInstrument(
                        timedSource, 0, new byte[25]));
        assertEquals(List.of(), profiledCallbacks);
        assertDeepEquals(before, profiled.captureSnapshot());

        List<String> immediateCallbacks = new ArrayList<>();
        SmpsDriver immediate = new SmpsDriver(
                44_100.0, recordingObserver(immediateCallbacks));
        immediateCallbacks.clear();
        SmpsSequencer immediateSource = sequencer(
                immediate, YmServiceTimingProfile.none(), 0x81);
        immediateCallbacks.clear();
        immediate.setInstrument(immediateSource, 0, new byte[25]);

        assertEquals(30, immediateCallbacks.size());
        assertEquals("0:28:00", immediateCallbacks.getFirst());
        assertTrue(immediate.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().isEmpty());
    }

    @Test
    void psgWriteBeforeYmPoisonPublishesNoHardwareOrLogicalCallback() {
        // Break caught: PSG mutation is immediate while the sibling YM journal
        // is transactional, so a later YM count poison cannot retract it.
        List<String> chip = new ArrayList<>();
        List<String> logical = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                44_100.0, recordingAllObserver(chip));
        chip.clear();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        driver.addSequencer(source, true);
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                logical.add("begin:" + event.ordinal());
            }
        });
        chip.clear();
        SmpsDriverSnapshot before = driver.captureSnapshot();
        driver.beginSequencerService(source,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        driver.writePsg(source, 0x9F);
        Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT);
        driver.writeFm(source, 0, 0xB0, 0x01);

        assertThrows(IllegalStateException.class, timing::close);
        assertEquals(List.of(), chip);
        assertEquals(List.of(), logical);
        assertDeepEquals(before, driver.captureSnapshot());
    }

    @Test
    void throwingLogicalObserverCannotBlockCommittedPsgPublication() {
        // Break caught: begin, PSG, and end publications share one fail-fast
        // list, so the committed PSG write is skipped when begin throws.
        List<String> publications = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                44_100.0, recordingAllObserver(publications));
        publications.clear();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        publications.clear();
        IllegalStateException injected =
                new IllegalStateException("injected logical begin failure");
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                publications.add("logical:begin");
                throw injected;
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                publications.add("logical:end");
            }
        });
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        driver.writePsg(source, 0x9F);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> driver.endSequencerService(service));

        assertSame(injected, failure);
        assertEquals(List.of("PSG:9F", "logical:begin", "logical:end"),
                publications);
        assertEquals(1L,
                driver.captureSnapshot().nextYmServiceOrdinal());
    }

    @Test
    void logicalPublicationFailuresDrainInOrderWithLaterFailuresSuppressed() {
        // Break caught: fail-fast publication leaves the paired end callback
        // unobserved and makes the diagnostic state depend on which callback
        // throws first.
        List<String> publications = new ArrayList<>();
        IllegalStateException beginFailure =
                new IllegalStateException("injected begin failure");
        IllegalStateException endFailure =
                new IllegalStateException("injected end failure");
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                publications.add("begin");
                throw beginFailure;
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                publications.add("end");
                throw endFailure;
            }
        });
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> driver.endSequencerService(service));

        assertSame(beginFailure, failure);
        assertEquals(List.of("begin", "end"), publications);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(endFailure, failure.getSuppressed()[0]);
    }

    @Test
    void postCommitLogicalFailureDoesNotRollbackEnclosingDriverAdvance() {
        // Break caught: a diagnostic failure thrown after service commit is
        // mistaken for a construction failure by the outer reservation, so
        // committed state is restored and retry repeats service identity 0.
        IllegalStateException injected =
                new IllegalStateException("injected post-commit failure");
        List<String> retriedEvents = new ArrayList<>();
        int[] remainingFailures = { 1 };
        MinimalData sharedData = data(0xA0);
        SmpsSequencerConfig sharedConfig = timedPalConfig(PROFILE);
        SmpsDriver retried = new SmpsDriver();
        AggregateWritingSequencer retriedSource =
                new AggregateWritingSequencer(
                        retried, sharedData, sharedConfig);
        retried.addSequencer(retriedSource, false);
        retried.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                retriedEvents.add("begin:" + event.ordinal());
                if (remainingFailures[0]-- > 0) {
                    throw injected;
                }
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                retriedEvents.add("end:" + event.ordinal());
            }
        });

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> advanceSequencersWithoutRender(retried, 1));

        assertSame(injected, failure);
        assertEquals(List.of("begin:0", "end:0"), retriedEvents);
        SmpsDriverSnapshot committed = retried.captureSnapshot();
        assertEquals(1L, committed.nextYmServiceOrdinal());
        assertEquals(8L, committed.nextYmWriteOrdinal());
        assertEquals(8, committed.synthSnapshot()
                .ymWriteTimeline().pending().size());

        advanceSequencersWithoutRender(retried, 1);
        assertEquals(List.of(
                "begin:0", "end:0", "begin:1", "end:1"),
                retriedEvents);

        List<String> cleanEvents = new ArrayList<>();
        SmpsDriver clean = new SmpsDriver();
        AggregateWritingSequencer cleanSource =
                new AggregateWritingSequencer(
                        clean, sharedData, sharedConfig);
        clean.addSequencer(cleanSource, false);
        clean.setServiceObserver(recordingStringServiceObserver(cleanEvents));
        advanceSequencersWithoutRender(clean, 1);
        advanceSequencersWithoutRender(clean, 1);

        assertEquals(cleanEvents, retriedEvents);
        assertDeepEquals(clean.captureSnapshot(),
                retried.captureSnapshot());
    }

    @Test
    void outerBatchPublishesOnlyAfterEverySiblingCommits() {
        // Break caught: service A publishes its begin/end callbacks before
        // sibling B poisons, then the outer rollback erases A and retry
        // duplicates identities which an observer has already seen.
        IllegalStateException observerFailure =
                new IllegalStateException("injected batch observer failure");
        List<String> retriedEvents = new ArrayList<>();
        List<String> retriedChipEvents = new ArrayList<>();
        List<Long> retriedEndSnapshotOrdinals = new ArrayList<>();
        List<SmpsDriverSnapshot> retriedEndSnapshots = new ArrayList<>();
        int[] remainingPoisons = { 1 };
        MinimalData sharedMusicData = data(0x81);
        MinimalData sharedSfxData = data(0xA0);
        SmpsSequencerConfig sharedConfig = timedPalConfig(PROFILE);
        SmpsDriver retried = new SmpsDriver(
                44_100.0, recordingAllObserver(retriedChipEvents));
        AggregateWritingSequencer retriedMusic =
                new AggregateWritingSequencer(
                        retried, sharedMusicData, sharedConfig,
                        null, 0x91);
        AggregateWritingSequencer retriedSfx =
                new AggregateWritingSequencer(
                        retried, sharedSfxData, sharedConfig,
                        remainingPoisons, 0x93);
        retried.addSequencer(retriedMusic, false);
        retried.addSequencer(retriedSfx, false);
        retriedChipEvents.clear();
        retried.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                retriedEvents.add("begin:" + event.ordinal());
                if (event.ordinal() == 0) {
                    throw observerFailure;
                }
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                retriedEvents.add("end:" + event.ordinal());
                retriedEndSnapshotOrdinals.add(
                        snapshot.nextYmServiceOrdinal());
                retriedEndSnapshots.add(snapshot);
            }
        });
        SmpsDriverSnapshot before = retried.captureSnapshot();

        assertThrows(IllegalStateException.class,
                () -> advanceSequencersWithoutRender(retried, 1));

        assertEquals(List.of(), retriedEvents);
        assertEquals(List.of(), retriedChipEvents);
        assertEquals(List.of(), retriedEndSnapshotOrdinals);
        assertEquals(List.of(), retriedEndSnapshots);
        assertDeepEquals(before, retried.captureSnapshot());

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> advanceSequencersWithoutRender(retried, 1));

        assertSame(observerFailure, failure);
        assertEquals(List.of(
                "begin:0", "end:0", "begin:1", "end:1"),
                retriedEvents);
        assertEquals(List.of("PSG:91", "PSG:93"),
                retriedChipEvents);
        assertEquals(List.of(1L, 2L), retriedEndSnapshotOrdinals);
        assertEquals(2, retriedEndSnapshots.size());
        PsgChip psgOracle = new PsgChip();
        psgOracle.restoreSnapshot(before.synthSnapshot().psg());
        psgOracle.write(0x91);
        assertEquals(psgOracle.captureSnapshot(), retriedEndSnapshots.get(0)
                .synthSnapshot().psg());
        assertEquals(8, retriedEndSnapshots.get(0).synthSnapshot()
                .ymWriteTimeline().pending().size());
        psgOracle.write(0x93);
        assertEquals(psgOracle.captureSnapshot(), retriedEndSnapshots.get(1)
                .synthSnapshot().psg());
        assertEquals(16, retriedEndSnapshots.get(1).synthSnapshot()
                .ymWriteTimeline().pending().size());
        SmpsDriverSnapshot committed = retried.captureSnapshot();
        assertEquals(2L, committed.nextYmServiceOrdinal());
        assertEquals(16L, committed.nextYmWriteOrdinal());
        assertEquals(16, committed.synthSnapshot()
                .ymWriteTimeline().pending().size());

        advanceSequencersWithoutRender(retried, 1);
        assertEquals(List.of(
                "begin:0", "end:0", "begin:1", "end:1",
                "begin:2", "end:2", "begin:3", "end:3"),
                retriedEvents);
        assertEquals(List.of(
                "PSG:91", "PSG:93", "PSG:91", "PSG:93"),
                retriedChipEvents);
        assertEquals(List.of(1L, 2L, 3L, 4L),
                retriedEndSnapshotOrdinals);

        List<String> cleanEvents = new ArrayList<>();
        List<String> cleanChipEvents = new ArrayList<>();
        List<SmpsDriverSnapshot> cleanEndSnapshots = new ArrayList<>();
        SmpsDriver clean = new SmpsDriver(
                44_100.0, recordingAllObserver(cleanChipEvents));
        AggregateWritingSequencer cleanMusic =
                new AggregateWritingSequencer(
                        clean, sharedMusicData, sharedConfig,
                        null, 0x91);
        AggregateWritingSequencer cleanSfx =
                new AggregateWritingSequencer(
                        clean, sharedSfxData, sharedConfig,
                        null, 0x93);
        clean.addSequencer(cleanMusic, false);
        clean.addSequencer(cleanSfx, false);
        cleanChipEvents.clear();
        clean.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                cleanEvents.add("begin:" + event.ordinal());
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                cleanEvents.add("end:" + event.ordinal());
                cleanEndSnapshots.add(snapshot);
            }
        });
        advanceSequencersWithoutRender(clean, 1);
        advanceSequencersWithoutRender(clean, 1);

        assertEquals(cleanEvents, retriedEvents);
        assertEquals(cleanChipEvents, retriedChipEvents);
        assertDeepEquals(cleanEndSnapshots, retriedEndSnapshots);
        assertDeepEquals(clean.captureSnapshot(),
                retried.captureSnapshot());
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
        // Break caught: each sequencer transaction recomputes the whole bound,
        // so the first of four eight-write services consumes capacity which
        // makes the second spuriously reject.
        int aggregateBound = 32; // (music 8 + SFX 8) * (normal + PAL repeat)
        SmpsDriver exact = new SmpsDriver();
        exact.setRegion(SmpsSequencer.Region.PAL);
        fillRemainingCapacity(exact, aggregateBound);
        AggregateWritingSequencer exactMusic =
                new AggregateWritingSequencer(exact, 0x81);
        AggregateWritingSequencer exactSfx =
                new AggregateWritingSequencer(exact, 0xA0);
        exact.addSequencer(exactMusic, false);
        exact.addSequencer(exactSfx, true);
        setPalFullUpdateCounter(exact, 0);
        long serviceOrdinalBefore =
                exact.captureSnapshot().nextYmServiceOrdinal();

        advanceSequencersWithoutRender(exact, 1);

        assertEquals(4_096,
                exact.captureSnapshot().synthSnapshot()
                        .ymWriteTimeline().pending().size());
        assertEquals(1, exactMusic.advanceCalls);
        assertEquals(1, exactMusic.repeatCalls);
        assertEquals(1, exactSfx.advanceCalls);
        assertEquals(1, exactSfx.repeatCalls);
        assertEquals(serviceOrdinalBefore + 4,
                exact.captureSnapshot().nextYmServiceOrdinal());

        List<String> logical = new ArrayList<>();
        List<String> chip = new ArrayList<>();
        SmpsDriver shortDriver = new SmpsDriver(
                44_100.0, recordingObserver(chip));
        shortDriver.setRegion(SmpsSequencer.Region.PAL);
        fillRemainingCapacity(shortDriver, aggregateBound - 1);
        AggregateWritingSequencer shortMusic =
                new AggregateWritingSequencer(shortDriver, 0x81);
        AggregateWritingSequencer shortSfx =
                new AggregateWritingSequencer(shortDriver, 0xA0);
        shortDriver.addSequencer(shortMusic, false);
        shortDriver.addSequencer(shortSfx, true);
        setPalFullUpdateCounter(shortDriver, 0);
        shortDriver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                logical.add("begin");
            }
        });
        chip.clear();
        SmpsDriverSnapshot before = shortDriver.captureSnapshot();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> advanceSequencersWithoutRender(shortDriver, 1));

        assertTrue(failure.getMessage().contains("aggregate service bound 32"));
        assertEquals(0, shortMusic.advanceCalls);
        assertEquals(0, shortMusic.repeatCalls);
        assertEquals(0, shortSfx.advanceCalls);
        assertEquals(0, shortSfx.repeatCalls);
        assertEquals(List.of(), logical);
        assertEquals(List.of(), chip);
        assertDeepEquals(before, shortDriver.captureSnapshot());
    }

    @Test
    void aggregateCapacityCountsActivePendingRemovalOwnerOnlyOnce() {
        // Break caught: one sequencer present in both the active list and the
        // pending-removal queue contributes its profile maximum twice.
        SmpsDriver driver = new SmpsDriver();
        fillRemainingCapacity(driver, 8);
        SmpsSequencer source = sequencer(driver, PROFILE, 0xA0);
        driver.addSequencer(source, true);
        addPendingRemoval(driver, source);

        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind
                                .COMPLETION_CLEANUP);
        try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                source, SegmentKind.KEY_OFF, VARIANT)) {
            driver.writeFm(source, 0, 0x22, 0x08);
        }
        driver.endSequencerService(service);

        assertEquals(4_089, driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().size());
    }

    @Test
    void aggregateCapacityCoversNormalAndCompletionLifecycleTogether() {
        // Break caught: the outer reservation is cleared after the normal
        // service, so same-advance completion re-preflights the full owner
        // maximum against only the reservation's unused tail.
        YmServiceTimingProfile lifecycleProfile =
                YmServiceTimingProfile.of(4,
                        new Segment(SegmentKind.KEY_OFF, VARIANT,
                                new long[] { 0, 100 }),
                        new Segment(SegmentKind.COMPLETION_RESTORE, VARIANT,
                                new long[] { 0, 100 }));
        SmpsDriver exact = new SmpsDriver();
        fillRemainingCapacity(exact, 4);
        LifecycleCompletingSequencer exactSource =
                new LifecycleCompletingSequencer(
                        exact, lifecycleProfile, 0xA0);
        exact.addSequencer(exactSource, false);
        exactSource.setIsSfx(true);
        setFmLock(exact, 0, exactSource);
        long serviceOrdinalBefore =
                exact.captureSnapshot().nextYmServiceOrdinal();

        exact.read(new short[2]);

        assertEquals(1, exactSource.advanceCalls);
        assertEquals(1, exactSource.completionCalls);
        assertTrue(exact.sequencersForTesting().isEmpty());
        assertEquals(serviceOrdinalBefore + 2,
                exact.captureSnapshot().nextYmServiceOrdinal());
        assertEquals(4_096L,
                exact.captureSnapshot().nextYmWriteOrdinal());

        SmpsDriver shortDriver = new SmpsDriver();
        fillRemainingCapacity(shortDriver, 3);
        LifecycleCompletingSequencer shortSource =
                new LifecycleCompletingSequencer(
                        shortDriver, lifecycleProfile, 0xA1);
        shortDriver.addSequencer(shortSource, false);
        shortSource.setIsSfx(true);
        setFmLock(shortDriver, 0, shortSource);
        SmpsDriverSnapshot before = shortDriver.captureSnapshot();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> shortDriver.read(new short[2]));

        assertTrue(failure.getMessage().contains(
                "aggregate service bound 4"));
        assertEquals(0, shortSource.advanceCalls);
        assertEquals(0, shortSource.completionCalls);
        assertDeepEquals(before, shortDriver.captureSnapshot());
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
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.SAMPLE_ACCURATE);
        assertDeepEquals(hybrid.captureSnapshot(),
                accurate.captureSnapshot());
    }

    @Test
    void hybridStaysSampleBoundedWhenTimedWorkAppearsMidRead() {
        // Break caught: HYBRID starts with no pending entry, schedules delayed
        // work at a later sample, then returns to chunk rendering in the same
        // call and diverges in resampler phase from SAMPLE_ACCURATE.
        List<String> hybridCallbacks = new ArrayList<>();
        MinimalData sharedData = data(0xA0);
        SmpsSequencerConfig sharedConfig = timedConfig(PROFILE);
        SmpsDriver hybrid = new SmpsDriver(
                44_100.0, recordingObserver(hybridCallbacks));
        MidReadTimedSequencer hybridSource =
                new MidReadTimedSequencer(
                        hybrid, sharedData, sharedConfig, 16);
        hybrid.addSequencer(hybridSource, true);
        hybridCallbacks.clear();
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.HYBRID);

        List<String> accurateCallbacks = new ArrayList<>();
        SmpsDriver accurate = new SmpsDriver(
                44_100.0, recordingObserver(accurateCallbacks));
        MidReadTimedSequencer accurateSource =
                new MidReadTimedSequencer(
                        accurate, sharedData, sharedConfig, 16);
        accurate.addSequencer(accurateSource, true);
        accurateCallbacks.clear();
        accurate.setReadModeForTesting(SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[512];
        short[] accuratePcm = new short[512];
        hybrid.read(hybridPcm);
        accurate.read(accuratePcm);

        assertEquals(0, hybrid.getHybridChunkCountForTesting(),
                "a timed owner fences the complete read to sample boundaries");
        assertArrayEquals(accuratePcm, hybridPcm);
        assertEquals(accurateCallbacks, hybridCallbacks);
        assertEquals(List.of("0:A4:22", "0:A0:69", "0:28:F0"),
                hybridCallbacks);
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.SAMPLE_ACCURATE);
        assertDeepEquals(accurate.captureSnapshot(),
                hybrid.captureSnapshot());
    }

    @Test
    void hybridChunksWhenTimedOwnerCannotServiceInsideRequestedHorizon() {
        // Break caught: owner existence alone disables HYBRID batching even
        // when the real scheduler places its next service after this buffer.
        MinimalData sharedData = data(0xA0);
        SmpsSequencerConfig sharedConfig = timedConfig(PROFILE);
        SmpsDriver hybrid = new SmpsDriver();
        MidReadTimedSequencer hybridSource =
                new MidReadTimedSequencer(
                        hybrid, sharedData, sharedConfig, 1_024);
        hybrid.addSequencer(hybridSource, false);
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.HYBRID);

        SmpsDriver accurate = new SmpsDriver();
        MidReadTimedSequencer accurateSource =
                new MidReadTimedSequencer(
                        accurate, sharedData, sharedConfig, 1_024);
        accurate.addSequencer(accurateSource, false);
        accurate.setReadModeForTesting(SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[512];
        short[] accuratePcm = new short[512];
        hybrid.read(hybridPcm);
        accurate.read(accuratePcm);

        assertEquals(1, hybrid.getHybridChunkCountForTesting());
        assertArrayEquals(accuratePcm, hybridPcm);
        assertEquals(0L,
                hybrid.captureSnapshot().nextYmServiceOrdinal());
        assertEquals(List.of(), hybrid.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending());
    }

    @Test
    void hybridBatchesAcrossDeferredS3kBoundaryWhenRealServiceIsOutsideRead() {
        // Break caught: S3K admission defers the first tempo boundary, but the
        // HYBRID horizon treated that no-op boundary as a possible timed
        // publication and needlessly sample-fenced the complete read.
        assertEquals(SmpsSequencerConfig.SfxStartTiming.NEXT_DRIVER_UPDATE,
                Sonic3kSmpsSequencerConfig.CONFIG.getSfxStartTiming());
        MinimalData sharedData = data(0xA0);

        List<String> hybridCallbacks = new ArrayList<>();
        SmpsDriver hybrid = new SmpsDriver(
                44_100.0, recordingObserver(hybridCallbacks));
        SmpsSequencer hybridSource = deferredS3kSfx(
                hybrid, sharedData);
        hybridCallbacks.clear();
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.HYBRID);

        List<String> accurateCallbacks = new ArrayList<>();
        SmpsDriver accurate = new SmpsDriver(
                44_100.0, recordingObserver(accurateCallbacks));
        SmpsSequencer accurateSource = deferredS3kSfx(
                accurate, sharedData);
        accurateCallbacks.clear();
        accurate.setReadModeForTesting(
                SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[96 * 2];
        short[] accuratePcm = new short[96 * 2];
        hybrid.read(hybridPcm);
        accurate.read(accuratePcm);

        assertTrue(hybrid.getHybridChunkCountForTesting() > 0,
                "the deferred first boundary must not fence a read which cannot reach the real service");
        assertArrayEquals(accuratePcm, hybridPcm);
        assertEquals(accurateCallbacks, hybridCallbacks);
        assertEquals(List.of(), hybridCallbacks);
        assertEquals(0L, hybrid.captureSnapshot().nextYmServiceOrdinal());
        assertEquals(0L, accurate.captureSnapshot().nextYmServiceOrdinal());
        assertFalse(hybridSource.captureSnapshot().deferNextDriverService());
        assertFalse(accurateSource.captureSnapshot().deferNextDriverService());
    }

    @Test
    void hybridSampleFencesWhenDeferredS3kRealServiceIsInsideRead() {
        // Break caught: accounting for the deferred boundary must not move the
        // fence past the following real S3K SFX service.
        MinimalData sharedData = data(0xA0);

        List<String> hybridCallbacks = new ArrayList<>();
        SmpsDriver hybrid = new SmpsDriver(
                44_100.0, recordingObserver(hybridCallbacks));
        deferredS3kSfx(hybrid, sharedData);
        hybridCallbacks.clear();
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.HYBRID);

        List<String> accurateCallbacks = new ArrayList<>();
        SmpsDriver accurate = new SmpsDriver(
                44_100.0, recordingObserver(accurateCallbacks));
        deferredS3kSfx(accurate, sharedData);
        accurateCallbacks.clear();
        accurate.setReadModeForTesting(
                SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[128 * 2];
        short[] accuratePcm = new short[128 * 2];
        hybrid.read(hybridPcm);
        accurate.read(accuratePcm);

        assertEquals(0, hybrid.getHybridChunkCountForTesting(),
                "the real service at the second boundary fences the complete read");
        assertArrayEquals(accuratePcm, hybridPcm);
        assertEquals(accurateCallbacks, hybridCallbacks);
        assertFalse(hybridCallbacks.isEmpty(),
                "the following real service must publish its chip callbacks");
        hybrid.setReadModeForTesting(
                SmpsDriver.ReadMode.SAMPLE_ACCURATE);
        assertDeepEquals(accurate.captureSnapshot(),
                hybrid.captureSnapshot());
    }

    @Test
    void hybridCountsPalRepeatAsServiceAtDeferredS3kBoundary() {
        // Break caught: the ordinary SFX pass consumes the admission defer,
        // then a due locked-on PAL full-update repeat services that same SFX
        // at the first boundary after all.
        MinimalData sharedData = data(0xA0);

        List<String> hybridCallbacks = new ArrayList<>();
        SmpsDriver hybrid = new SmpsDriver(
                44_100.0, recordingObserver(hybridCallbacks));
        deferredS3kSfx(hybrid, sharedData);
        hybrid.setRegion(SmpsSequencer.Region.PAL);
        setPalFullUpdateCounter(hybrid, 0);
        hybridCallbacks.clear();
        hybrid.setReadModeForTesting(SmpsDriver.ReadMode.HYBRID);

        List<String> accurateCallbacks = new ArrayList<>();
        SmpsDriver accurate = new SmpsDriver(
                44_100.0, recordingObserver(accurateCallbacks));
        deferredS3kSfx(accurate, sharedData);
        accurate.setRegion(SmpsSequencer.Region.PAL);
        setPalFullUpdateCounter(accurate, 0);
        accurateCallbacks.clear();
        accurate.setReadModeForTesting(
                SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[96 * 2];
        short[] accuratePcm = new short[96 * 2];
        hybrid.read(hybridPcm);
        accurate.read(accuratePcm);

        assertEquals(0, hybrid.getHybridChunkCountForTesting());
        assertArrayEquals(accuratePcm, hybridPcm);
        assertEquals(accurateCallbacks, hybridCallbacks);
        assertFalse(hybridCallbacks.isEmpty());
        hybrid.setReadModeForTesting(
                SmpsDriver.ReadMode.SAMPLE_ACCURATE);
        assertDeepEquals(accurate.captureSnapshot(),
                hybrid.captureSnapshot());
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

    @Test
    void poisonedServiceRetryReusesDenseDiagnosticIdentities() {
        // Break caught: rollback restores audio state but leaks the rejected
        // service and sequencer observer ordinals into the retry.
        List<SmpsDriverServiceObserver.ServiceEvent> retriedEvents =
                new ArrayList<>();
        MinimalData sharedData = data(0xA0);
        SmpsSequencerConfig sharedConfig = timedConfig(PROFILE);
        SmpsDriver retried = new SmpsDriver();
        SmpsSequencer retriedSource = sequencer(
                retried, sharedData, sharedConfig);
        retried.addSequencer(retriedSource, true);
        retried.setServiceObserver(recordingServiceObserver(retriedEvents));
        SmpsDriverSnapshot before = retried.captureSnapshot();

        retried.beginSequencerService(retriedSource,
                SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        Synthesizer.YmTimingScope poison = retried.beginYmTiming(
                retriedSource, SegmentKind.COMPLETION_RESTORE, VARIANT);
        retried.writeFm(retriedSource, 0, 0xB0, 0x01);
        assertThrows(IllegalStateException.class, poison::close);
        assertDeepEquals(before, retried.captureSnapshot());
        assertEquals(List.of(), retriedEvents);

        publishFrequencyService(retried, retriedSource);

        List<SmpsDriverServiceObserver.ServiceEvent> cleanEvents =
                new ArrayList<>();
        SmpsDriver clean = new SmpsDriver();
        SmpsSequencer cleanSource = sequencer(
                clean, sharedData, sharedConfig);
        clean.addSequencer(cleanSource, true);
        clean.setServiceObserver(recordingServiceObserver(cleanEvents));
        publishFrequencyService(clean, cleanSource);

        assertEquals(1, retriedEvents.size());
        assertEquals(0L, retriedEvents.getFirst().ordinal());
        assertEquals(0L, retriedEvents.getFirst().sequencer()
                .instanceOrdinal());
        assertEquals(cleanEvents, retriedEvents);
        assertEquals(clean.nextServiceSequencerOrdinalForTesting(),
                retried.nextServiceSequencerOrdinalForTesting());
        assertDeepEquals(clean.captureSnapshot(), retried.captureSnapshot());
    }

    @Test
    void ordinaryCompletionRetainsCommittedDelayedWriteUntilOneDrain() {
        // Break caught: removing a completed sequencer treats its committed
        // self-contained entry like an unpublished journal and cancels it.
        YmServiceTimingProfile delayed = YmServiceTimingProfile.of(2,
                new Segment(SegmentKind.COMPLETION_RESTORE, VARIANT,
                        new long[] { 0, 100_000 }));
        List<String> callbacks = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver(
                44_100.0, recordingObserver(callbacks));
        CompletingSequencer source =
                new CompletingSequencer(driver, delayed, 0xA0);
        driver.addSequencer(source, true);
        callbacks.clear();
        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(source,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        try (Synthesizer.YmTimingScope timing = driver.beginYmTiming(
                source, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
            timing.consumeSuppressedHardwareAttempt();
            driver.writeFm(source, 0, 0x22, 0x08);
        }
        driver.endSequencerService(service);

        driver.read(new short[2]);

        assertTrue(driver.sequencersForTesting().isEmpty());
        assertEquals(1, driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().size());
        assertEquals(List.of(), callbacks);
        driver.render(new short[512]);
        assertEquals(List.of("0:22:08"), callbacks);
        driver.render(new short[512]);
        assertEquals(1, callbacks.size());
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

    private static ChipWriteObserver recordingAllObserver(
            List<String> events) {
        return new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
                events.add("YM:%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                events.add("PSG:%02X".formatted(value));
            }
        };
    }

    private static SmpsDriverServiceObserver recordingServiceObserver(
            List<SmpsDriverServiceObserver.ServiceEvent> events) {
        return new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                events.add(event);
            }
        };
    }

    private static SmpsDriverServiceObserver recordingStringServiceObserver(
            List<String> events) {
        return new SmpsDriverServiceObserver() {
            @Override
            public void onServiceBegin(ServiceEvent event) {
                events.add("begin:" + event.ordinal());
            }

            @Override
            public void onServiceEnd(
                    ServiceEvent event, SmpsDriverSnapshot snapshot) {
                events.add("end:" + event.ordinal());
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

    private static SmpsSequencer prioritySequencer(
            SmpsDriver driver, int id) {
        MinimalData data = data(id);
        SmpsSequencer sequencer = new SmpsSequencer(
                data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(),
                new SmpsSequencerConfig.Builder()
                        .ymServiceTimingProfile(PROFILE)
                        .fmSfxTakeoverMode(
                                SmpsSequencerConfig.FmSfxTakeoverMode
                                        .REGISTER_SEQUENCE)
                        .sfxPriorityPolicy(
                                SmpsSequencerConfig.SfxPriorityPolicy.NONE)
                        .build());
        sequencer.addTrack(track());
        return sequencer;
    }

    private static SmpsSequencer sequencer(
            SmpsDriver driver, MinimalData data,
            SmpsSequencerConfig config) {
        SmpsSequencer sequencer = new SmpsSequencer(
                data, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), config);
        sequencer.addTrack(track());
        return sequencer;
    }

    private static SmpsSequencer deferredS3kSfx(
            SmpsDriver driver, MinimalData data) {
        SmpsSequencer sequencer = sequencer(
                driver, data, Sonic3kSmpsSequencerConfig.CONFIG);
        // 64 samples per NTSC VInt gives the read a useful batch window on
        // both sides of the first, deliberately deferred S3K boundary.
        sequencer.setSampleRate(3_840.0);
        driver.addSequencer(sequencer, false);
        sequencer.setIsSfx(true);
        sequencer.setSfxMode(true);
        sequencer.beginSfxAdmission();
        return sequencer;
    }

    private static void setPalFullUpdateCounter(
            SmpsDriver driver, int value) {
        try {
            Field field = SmpsDriver.class.getDeclaredField(
                    "palFullUpdateCounter");
            field.setAccessible(true);
            field.setInt(driver, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void setFmLock(
            SmpsDriver driver, int channel, SmpsSequencer owner) {
        try {
            Field field = SmpsDriver.class.getDeclaredField("fmLocks");
            field.setAccessible(true);
            ((SmpsSequencer[]) field.get(driver))[channel] = owner;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addPendingRemoval(
            SmpsDriver driver, SmpsSequencer sequencer) {
        try {
            Field field = SmpsDriver.class.getDeclaredField(
                    "pendingRemovals");
            field.setAccessible(true);
            ((List<SmpsSequencer>) field.get(driver)).add(sequencer);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void advanceSequencersWithoutRender(
            SmpsDriver driver, int frames) {
        try {
            Method method = SmpsDriver.class.getDeclaredMethod(
                    "advanceSequencersBatch", int.class);
            method.setAccessible(true);
            method.invoke(driver, frames);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
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

    private static final class AggregateWritingSequencer
            extends SmpsSequencer {
        private final SmpsDriver driver;
        private final int[] remainingPoisons;
        private final Integer stagedPsgWrite;
        private int advanceCalls;
        private int repeatCalls;

        private AggregateWritingSequencer(
                SmpsDriver driver, int id) {
            this(driver, data(id));
        }

        private AggregateWritingSequencer(
                SmpsDriver driver, MinimalData data) {
            this(driver, data, timedPalConfig(PROFILE));
        }

        private AggregateWritingSequencer(
                SmpsDriver driver, MinimalData data,
                SmpsSequencerConfig config) {
            this(driver, data, config, null);
        }

        private AggregateWritingSequencer(
                SmpsDriver driver, MinimalData data,
                SmpsSequencerConfig config, int[] remainingPoisons) {
            this(driver, data, config, remainingPoisons, null);
        }

        private AggregateWritingSequencer(
                SmpsDriver driver, MinimalData data,
                SmpsSequencerConfig config, int[] remainingPoisons,
                Integer stagedPsgWrite) {
            super(data, AudioTestFixtures.EMPTY_DAC, driver,
                    AudioManager.getInstance(), config);
            this.driver = driver;
            this.remainingPoisons = remainingPoisons;
            this.stagedPsgWrite = stagedPsgWrite;
        }

        @Override
        public int advanceBatchAndCountDriverFrames(int samples) {
            advanceCalls++;
            publishEightWrites();
            return 1;
        }

        @Override
        public void repeatDriverService() {
            repeatCalls++;
            publishEightWrites();
        }

        @Override
        public int getSamplesUntilNextTempoFrame() {
            return 1;
        }

        @Override
        public int getSamplesUntilNextObservableEvent() {
            return 1;
        }

        @Override
        public boolean isComplete() {
            return false;
        }

        private void publishEightWrites() {
            SmpsDriverServiceObserver.ServiceEvent service =
                    driver.beginSequencerService(this,
                            SmpsDriverServiceObserver.ServiceKind
                                    .SEQUENCER_TICK);
            if (stagedPsgWrite != null) {
                driver.writePsg(this, stagedPsgWrite);
            }
            if (remainingPoisons != null
                    && remainingPoisons[0]-- > 0) {
                try (Synthesizer.YmTimingScope ignored =
                             driver.beginYmTiming(
                                     this, SegmentKind.COMPLETION_RESTORE,
                                     VARIANT)) {
                    driver.writeFm(this, 0, 0xB0, 0x01);
                }
                throw new AssertionError("poison scope unexpectedly closed");
            }
            try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                    this, SegmentKind.SFX_ADMISSION_PREP, VARIANT)) {
                for (int index = 0; index < 5; index++) {
                    driver.writeFm(this, 0, 0x22, index);
                }
            }
            try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                    this, SegmentKind.FREQUENCY_AND_KEY_ON, VARIANT)) {
                for (int index = 0; index < 3; index++) {
                    driver.writeFm(this, 0, 0x22, 5 + index);
                }
            }
            driver.endSequencerService(service);
        }
    }

    private static final class MidReadTimedSequencer
            extends SmpsSequencer {
        private final SmpsDriver driver;
        private final int publishAtSample;
        private int elapsed;
        private boolean published;

        private MidReadTimedSequencer(
                SmpsDriver driver, MinimalData data,
                SmpsSequencerConfig config, int publishAtSample) {
            super(data, AudioTestFixtures.EMPTY_DAC, driver,
                    AudioManager.getInstance(), config);
            this.driver = driver;
            this.publishAtSample = publishAtSample;
        }

        @Override
        public int advanceBatchAndCountDriverFrames(int samples) {
            elapsed += samples;
            if (!published && elapsed >= publishAtSample) {
                published = true;
                publishFrequencyService(driver, this);
            }
            return 0;
        }

        @Override
        public int getSamplesUntilNextTempoFrame() {
            return published ? Integer.MAX_VALUE
                    : Math.max(1, publishAtSample - elapsed);
        }

        @Override
        public int getSamplesUntilNextObservableEvent() {
            return getSamplesUntilNextTempoFrame();
        }

        @Override
        public boolean isComplete() {
            return false;
        }
    }

    private static final class CompletingSequencer extends SmpsSequencer {
        private CompletingSequencer(
                SmpsDriver driver, YmServiceTimingProfile profile, int id) {
            super(data(id), AudioTestFixtures.EMPTY_DAC, driver,
                    AudioManager.getInstance(), timedConfig(profile));
        }

        @Override
        public boolean isComplete() {
            return true;
        }
    }

    private static final class LifecycleCompletingSequencer
            extends SmpsSequencer {
        private final SmpsDriver driver;
        private boolean complete;
        private int advanceCalls;
        private int completionCalls;

        private LifecycleCompletingSequencer(
                SmpsDriver driver, YmServiceTimingProfile profile, int id) {
            super(data(id), AudioTestFixtures.EMPTY_DAC, driver,
                    AudioManager.getInstance(), timedConfig(profile));
            this.driver = driver;
        }

        @Override
        public int advanceBatchAndCountDriverFrames(int samples) {
            advanceCalls++;
            SmpsDriverServiceObserver.ServiceEvent service =
                    driver.beginSequencerService(this,
                            SmpsDriverServiceObserver.ServiceKind
                                    .SEQUENCER_TICK);
            try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                    this, SegmentKind.KEY_OFF, VARIANT)) {
                driver.writeFm(this, 0, 0x22, 0x08);
                driver.writeFm(this, 0, 0x22, 0x09);
            }
            driver.endSequencerService(service);
            complete = true;
            return 1;
        }

        @Override
        public void forceSilence(TrackType type, int channelId) {
            completionCalls++;
            try (Synthesizer.YmTimingScope ignored = driver.beginYmTiming(
                    this, SegmentKind.COMPLETION_RESTORE, VARIANT)) {
                driver.writeFm(this, 0, 0x22, 0x0A);
                driver.writeFm(this, 0, 0x22, 0x0B);
            }
        }

        @Override
        public int getSamplesUntilNextTempoFrame() {
            return 1;
        }

        @Override
        public int getSamplesUntilNextObservableEvent() {
            return 1;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }
    }

    private static MinimalData data(int id) {
        MinimalData data = new MinimalData();
        data.setId(id);
        return data;
    }

    private static SmpsSequencerConfig timedConfig(
            YmServiceTimingProfile profile) {
        return new SmpsSequencerConfig.Builder()
                .ymServiceTimingProfile(profile)
                .fmSfxTakeoverMode(
                        SmpsSequencerConfig.FmSfxTakeoverMode
                                .REGISTER_SEQUENCE)
                .sfxPriorityPolicy(
                        SmpsSequencerConfig.SfxPriorityPolicy.NONE)
                .build();
    }

    private static SmpsSequencerConfig timedPalConfig(
            YmServiceTimingProfile profile) {
        return new SmpsSequencerConfig.Builder()
                .ymServiceTimingProfile(profile)
                .fmSfxTakeoverMode(
                        SmpsSequencerConfig.FmSfxTakeoverMode
                                .REGISTER_SEQUENCE)
                .sfxPriorityPolicy(
                        SmpsSequencerConfig.SfxPriorityPolicy.NONE)
                .driverServiceOrder(
                        SmpsSequencerConfig.DriverServiceOrder
                                .SFX_THEN_MUSIC)
                .palServicePolicy(
                        SmpsSequencerConfig.PalServicePolicy
                                .FULL_DRIVER_REPEAT_EVERY_SIXTH)
                .build();
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
