package com.openggf.audio.driver;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.audio.synth.YmWriteTimeline;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kBlueSphereSfxParity {

    @Test
    void admissionPreparesFm5OneVintBeforeTheFirstSfxService() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        List<String> writes = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                writes.add("%d:%02X:%02X".formatted(port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        admit(driver, data, loader);
        driver.read(new short[64]);
        assertTrue(writes.contains("0:28:05"),
                "admission key-off drains before the deferred first service");
        writes.clear();

        driver.read(new short[735 * 2]);
        assertTrue(writes.isEmpty(),
                "the admission VInt has already run before zPlaySound");

        driver.read(new short[735 * 2]);
        assertTrue(writes.containsAll(List.of(
                        "1:81:FF", "1:85:FF", "1:89:FF", "1:8D:FF")),
                "the following VInt executes cfSetVoice and starts the SFX");
    }

    @Test
    void admissionPreparationUsesTheAuditedSourceCycleDeltas() {
        // Break caught: S3K admission writes collapse onto one chip sample or
        // acquire timing from a sound-id-specific branch.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();

        admit(driver, data, loader);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(List.of(0L, 3_570L, 6_720L, 9_870L, 13_020L),
                pending.stream().map(YmWriteTimeline.Entry::dueMasterCycle)
                        .toList());
        assertEquals(List.of(0x28, 0x91, 0x99, 0x95, 0x9D),
                pending.stream().map(YmWriteTimeline.Entry::register).toList());
        assertEquals(List.of(0x05, 0, 0, 0, 0),
                pending.stream().map(YmWriteTimeline.Entry::value).toList());
    }

    @Test
    void repeatedBlueSphereAdmissionsRestartTheExactVolumeSequence() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        assertNotNull(data);

        SmpsDriver driver = new SmpsDriver();
        List<Integer> fm5CarrierLevels = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (port == 1 && (register == 0x49
                        || register == 0x45 || register == 0x4D)) {
                    fm5CarrierLevels.add(value);
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        SmpsSequencer sequencer = admit(driver, data, loader);
        driver.read(new short[24_000]);
        List<Integer> first = List.copyOf(fm5CarrierLevels);
        assertEquals(-0x30, sequencer.trackAt(0).modCurrentDelta,
                "Sound_65's $D0 modulation delta is sign-extended by zDoModulation");

        driver.read(new short[246]);
        fm5CarrierLevels.clear();
        admit(driver, data, loader);
        driver.read(new short[24_000]);

        assertEquals(List.of(5, 5, 5, 10, 10, 10), first,
                "Sound_65 starts at header attenuation 5 then applies its intentional +5 delta");
        assertEquals(first, fm5CarrierLevels,
                "same-ID retrigger must not inherit the previous track's attenuation");
    }

    @Test
    void firstBlueSphereAttackMatchesTheSourceRelativeCycleTimeline() {
        // Break caught: operation-local segments omit the audited source time
        // between cfSetVoice, instrument upload, key-off, and frequency/key-on.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        admit(driver, data, loader);

        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(33, pending.size(),
                "the anchor write drains on the service-boundary sample");
        long anchor = pending.getFirst().dueMasterCycle() - 3_150L;
        assertEquals(151_590L,
                pending.getLast().dueMasterCycle() - anchor);
        assertEquals(List.of(0L, 9_450L, 15_885L, 107_325L,
                        115_380L, 146_010L, 148_710L, 151_590L),
                List.of(
                        0L,
                        pending.get(2).dueMasterCycle() - anchor,
                        pending.get(3).dueMasterCycle() - anchor,
                        pending.get(28).dueMasterCycle() - anchor,
                        pending.get(29).dueMasterCycle() - anchor,
                        pending.get(30).dueMasterCycle() - anchor,
                        pending.get(31).dueMasterCycle() - anchor,
                        pending.get(32).dueMasterCycle() - anchor));

        List<String> complete = new ArrayList<>();
        complete.add("0:1:129:255");
        for (YmWriteTimeline.Entry entry : pending) {
            complete.add("%d:%d:%d:%d".formatted(
                    entry.dueMasterCycle() - anchor,
                    entry.port(), entry.register(), entry.value()));
        }
        assertEquals(List.of(
                "0:1:129:255", "3150:1:133:255",
                "6300:1:137:255", "9450:1:141:255",
                "15885:1:181:192", "19110:1:177:5",
                "22875:1:49:7", "26445:1:57:18",
                "30015:1:53:34", "33585:1:61:50",
                "37155:1:81:10", "40725:1:89:15",
                "44295:1:85:15", "47865:1:93:15",
                "51435:1:97:0", "55005:1:105:0",
                "58575:1:101:0", "62145:1:109:0",
                "65715:1:113:0", "69285:1:121:16",
                "72855:1:117:16", "76425:1:125:16",
                "79995:1:129:15", "83565:1:137:15",
                "87135:1:133:15", "90705:1:141:15",
                "95850:1:65:33", "99675:1:73:5",
                "103500:1:69:5", "107325:1:77:5",
                "115380:0:40:5", "146010:1:165:35",
                "148710:1:161:63", "151590:0:40:245"),
                complete,
                "all 34 normalized cycles and register/value bytes match "
                        + "corrected native group 7");
    }

    @Test
    void firstBlueSphereNoteWritesOnlyTheFinalModulatedFrequency() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        List<String> writes = new ArrayList<>();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(int port, int register, int value) {
                if (port == 1 && (register == 0xB5
                        || register == 0xA5 || register == 0xA1)) {
                    writes.add("%02X:%02X".formatted(register, value));
                }
            }

            @Override
            public void onPsgWrite(int value) {
            }
        });

        admit(driver, data, loader);
        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);
        driver.read(new short[400]);

        assertEquals(List.of("B5:C0", "A5:23", "A1:3F"), writes,
                "zUpdateFMorPSGTrack applies modulation before its sole "
                        + "zFMSendFreq write");
    }

    @Test
    void spikeHitUsesTheSameOperationProfileWithItsBit7CarrierMask() {
        // Break caught: first-attack timing is selected from Sound_65 or from
        // algorithm carriers instead of the stored BIT7 TL semantics used by
        // locked-on zSendTL.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.SPIKE_HIT.id);
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sequencer = admit(driver, data, loader);

        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(33, pending.size());
        long anchor = pending.getFirst().dueMasterCycle() - 3_150L;
        assertEquals(151_020L,
                pending.getLast().dueMasterCycle() - anchor,
                "Sound_37 has only stored operator four in the BIT7 timing path");
        assertEquals(List.of(0x29, 0x20, 0x0F, 0x00),
                pending.subList(25, 29).stream()
                        .map(YmWriteTimeline.Entry::value).toList());
        assertEquals(0x03, pending.get(4).value() & 0x07,
                "voice algorithm 3 remains ROM voice data");
        assertEquals(List.of(0x28, 0xA5, 0xA1, 0x28),
                pending.subList(29, 33).stream()
                        .map(YmWriteTimeline.Entry::register).toList());

        SmpsSequencer.Track fm5 = sequencer.trackAt(0);
        assertEquals(0xCF, fm5.note,
                "Sound_37 begins with source-authentic nFs6");
        long firstUnusedOrdinal = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().nextOrdinal();
        List<SmpsDriverSnapshot> laterServices = new ArrayList<>();
        driver.setServiceObserver(new SmpsDriverServiceObserver() {
            @Override
            public void onServiceEnd(ServiceEvent event,
                    SmpsDriverSnapshot snapshot) {
                laterServices.add(snapshot);
            }
        });

        for (int tick = 0; tick < 6; tick++) {
            driver.read(new short[735 * 2]);
        }

        assertEquals(0xD7, fm5.note,
                "the five-tick nFs6 advances to source-authentic nD7");
        Map<Long, YmWriteTimeline.Entry> laterWrites = new java.util.TreeMap<>();
        for (SmpsDriverSnapshot snapshot : laterServices) {
            for (YmWriteTimeline.Entry entry : snapshot.synthSnapshot()
                    .ymWriteTimeline().pending()) {
                if (entry.sourceOrdinal() >= firstUnusedOrdinal) {
                    laterWrites.put(entry.sourceOrdinal(), entry);
                }
            }
        }
        assertTrue(laterWrites.values().stream().anyMatch(entry ->
                        entry.register() == 0xA5 || entry.register() == 0xA1),
                "the later note/modulation frequency writes executed");
        assertTrue(laterWrites.values().stream().anyMatch(entry ->
                        entry.register() == 0x28),
                "the ordinary transition key-off/key-on writes executed");
        assertTrue(laterWrites.values().stream().allMatch(entry ->
                        entry.segment() == null),
                "later key-off, frequency, key-on, and modulation writes "
                        + "must not reopen the audited first-attack scope");
    }

    @Test
    void overlappingFm5AdmissionIsClassifiedAsTheSurvivingFirstAttack() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver();
        admit(driver, loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id), loader);
        admit(driver, loader.loadSfx(Sonic3kSfx.SPIKE_HIT.id), loader);

        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(33, pending.size(),
                "the displaced pre-service FM5 owner never opens an attack");
        long anchor = pending.getFirst().dueMasterCycle() - 3_150L;
        assertEquals(151_020L,
                pending.getLast().dueMasterCycle() - anchor,
                "the surviving Sound_37 attack keeps its semantic mask");
    }

    @Test
    void blueSphereAfterAnotherCompletedFm5SfxReopensOneFirstPath() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver();
        admit(driver, loader.loadSfx(Sonic3kSfx.SPIKE_HIT.id), loader);
        driver.read(new short[24_000]);

        SmpsSequencer blue = admit(driver,
                loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id), loader);
        assertTrue(pending(blue.trackAt(0), "firstFm5AdmissionVoicePending"),
                "the post-other admission is a fresh source path");
        driver.read(new short[735 * 3]);
        driver.read(new short[735 * 3]);

        assertFalse(pending(blue.trackAt(0), "firstFm5AdmissionVoicePending"));
        assertFalse(pending(blue.trackAt(0), "firstFm5AdmissionAttackPending"),
                "completed-then-idle ownership must consume exactly one first path");
    }

    @Test
    void rewindAndLiveRollbackPreserveTheUnconsumedFirstAdmissionPath() {
        // Break caught: restoring after zPlaySound loses or duplicates the
        // one-shot cfSetVoice/first-attack timing state.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sequencer = admit(driver, data, loader);
        SmpsSequencer.Track track = sequencer.trackAt(0);
        assertTrue(pending(track, "firstFm5AdmissionVoicePending"));
        assertFalse(pending(track, "firstFm5AdmissionAttackPending"));

        var snapshot = sequencer.captureSnapshot();
        setPending(track, "firstFm5AdmissionVoicePending", false);
        setPending(track, "firstFm5AdmissionAttackPending", true);
        sequencer.restoreSnapshot(snapshot);
        assertTrue(pending(sequencer.trackAt(0), "firstFm5AdmissionVoicePending"));
        assertFalse(pending(sequencer.trackAt(0), "firstFm5AdmissionAttackPending"));

        SmpsSequencer.Track identityTrack = sequencer.trackAt(0);
        var token = sequencer.captureLiveCommandMutation();
        setPending(identityTrack, "firstFm5AdmissionVoicePending", false);
        setPending(identityTrack, "firstFm5AdmissionAttackPending", true);
        sequencer.rollbackLiveCommandMutation(token);
        assertEquals(identityTrack, sequencer.trackAt(0));
        assertTrue(pending(identityTrack, "firstFm5AdmissionVoicePending"));
        assertFalse(pending(identityTrack, "firstFm5AdmissionAttackPending"));

        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);
        assertFalse(pending(identityTrack, "firstFm5AdmissionVoicePending"));
        assertFalse(pending(identityTrack, "firstFm5AdmissionAttackPending"));
        assertEquals(33, driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().size(),
                "restored admission executes exactly one 34-attempt path; "
                        + "the first write drains at the boundary");
    }

    @Test
    void ordinaryInstrumentRefreshDoesNotReopenFirstAttackTiming() {
        // Break caught: a music refresh or later cfSetVoice consumes the
        // one-shot first-admission profile again.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id);
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sequencer = admit(driver, data, loader);
        driver.read(new short[735 * 2]);
        driver.read(new short[735 * 2]);
        assertFalse(pending(sequencer.trackAt(0), "firstFm5AdmissionAttackPending"));

        SmpsDriverServiceObserver.ServiceEvent service =
                driver.beginSequencerService(sequencer,
                        SmpsDriverServiceObserver.ServiceKind.SEQUENCER_TICK);
        sequencer.refreshInstrument(sequencer.trackAt(0));
        driver.endSequencerService(service);

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(59, pending.size());
        assertEquals(1, pending.subList(33, 59).stream()
                .map(YmWriteTimeline.Entry::dueMasterCycle).distinct().count(),
                "ordinary refresh contributes no audited source delay");
    }

    @Test
    void completionKeyOffAndMusicRestoreShareOneAuditedScope() {
        // Break caught: cfStopTrack's key-off commits separately from the
        // same-source Special Stage FM5 voice restore, collapsing its delay.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.SPECIAL_STAGE.id),
                loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(music, false);
        driver.read(new short[735 * 8]);
        admit(driver, loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id), loader);

        List<YmWriteTimeline.Entry> completion = List.of();
        for (int frame = 0; frame < 100 && completion.isEmpty(); frame++) {
            driver.read(new short[735 * 2]);
            completion = driver.captureSnapshot().synthSnapshot()
                    .ymWriteTimeline().pending().stream()
                    .filter(entry -> entry.segment()
                            == com.openggf.audio.smps.YmServiceTimingProfile
                                    .SegmentKind.COMPLETION_RESTORE)
                    .toList();
        }

        assertEquals(26, completion.size(),
                "the boundary key-off drains while 26 restore writes remain");
        long keyOffDue = completion.getFirst().dueMasterCycle() - 16_170L;
        assertEquals(107_040L,
                completion.getLast().dueMasterCycle() - keyOffDue);
        assertEquals(0xB5, completion.getFirst().register());
        assertEquals(0xC0, completion.getFirst().value(),
                "ring/Special Stage panning remains the ROM value");
    }

    @Test
    void inactiveFm5TrackCannotOpenACompletionRestoreScope() throws Exception {
        // Break caught: stale voice data on an inactive music track selects a
        // 27-write restore scope even though override release emits no upload.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsSequencer music = new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.SPECIAL_STAGE.id),
                loader.loadDacData(), new SmpsDriver(),
                Sonic3kSmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track fm5 = null;
        for (int index = 0; index < music.trackCount(); index++) {
            if (music.trackAt(index).type == SmpsSequencer.TrackType.FM
                    && music.trackAt(index).channelId == 4) {
                fm5 = music.trackAt(index);
                break;
            }
        }
        assertNotNull(fm5);
        fm5.active = false;

        Method selector = SmpsDriver.class.getDeclaredMethod(
                "completionRestoreVariant", SmpsSequencer.class, int.class);
        selector.setAccessible(true);
        assertNull(selector.invoke(null, music, 4));
    }

    @Test
    void multiTrackRingLossFitsTheAggregateSfxServiceHorizon() {
        // Break caught: capacity is sized to one 34-write FM5 operation rather
        // than all sibling tracks serviced by the same locked-on SFX owner.
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver();
        admit(driver, loader.loadSfx(Sonic3kSfx.RING_LOSS.id), loader);

        assertDoesNotThrow(() -> {
            driver.read(new short[735 * 2]);
            driver.read(new short[735 * 2]);
        });
        assertTrue(driver.captureSnapshot().nextYmWriteOrdinal() > 5,
                "both FM4 and FM5 service writes commit atomically");
    }

    @Test
    void sourceTimingPreservesTempoDacPsgAndRingPanningBytes() {
        PlaybackInvariant timed = playbackInvariant(
                Sonic3kSmpsSequencerConfig.CONFIG);
        PlaybackInvariant atomic = playbackInvariant(
                withoutYmTiming(Sonic3kSmpsSequencerConfig.CONFIG));

        assertEquals(atomic.psgWrites(), timed.psgWrites(),
                "ordered PSG bytes remain identical");
        assertEquals(atomic.normalTempo(), timed.normalTempo());
        assertEquals(atomic.tempoWeight(), timed.tempoWeight());
        assertEquals(atomic.tempoAccumulator(), timed.tempoAccumulator(),
                "Special Stage retains its normal tempo phase");
        assertEquals(atomic.dacState(), timed.dacState(),
                "DAC identity, latch, position and enable state remain identical");
        assertEquals(atomic.ymWrites().stream()
                        .filter(write -> write.startsWith("YM:1:B5:"))
                        .toList(),
                timed.ymWrites().stream()
                        .filter(write -> write.startsWith("YM:1:B5:"))
                        .toList(),
                "FM5 panning register/value bytes remain identical");
        assertFalse(timed.psgWrites().isEmpty(),
                "four-track Collapse exercises the unchanged PSG path");
    }

    @Test
    void sourceTimingChangesOnlyDueCyclesAcrossCompleteBlueSpherePlayback() {
        List<String> timed = completeBlueSphereYmStream(
                Sonic3kSmpsSequencerConfig.CONFIG);
        List<String> atomic = completeBlueSphereYmStream(
                withoutYmTiming(Sonic3kSmpsSequencerConfig.CONFIG));

        assertEquals(atomic, timed,
                "the whole ordered YM port/register/value stream remains "
                        + "identical when only due cycles are profiled");
        assertTrue(timed.stream().filter(write -> write.startsWith("1:A5:"))
                        .count() > 2,
                "the scenario includes ordinary note/modulation frequency "
                        + "writes after the first attack");
        assertTrue(timed.stream().filter(write -> write.equals("0:28:05"))
                        .count() > 2,
                "the scenario reaches SFX completion and music restoration");
        int restoredPanning = timed.lastIndexOf("1:B5:C0");
        assertTrue(restoredPanning > timed.indexOf("0:28:05"),
                "completion restores the music FM5 panning before ordinary "
                        + "music playback continues");
    }

    @Test
    void lockedOnAggregateCapacityNCommitsAndNMinusOneRollsBack() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsDriver exact = new SmpsDriver();
        SmpsSequencer exactSfx = admit(exact,
                loader.loadSfx(Sonic3kSfx.COLLAPSE.id), loader);
        fillRemainingCapacity(exact, 136);
        long exactOrdinal = exact.captureSnapshot().nextYmWriteOrdinal();

        assertDoesNotThrow(() -> advanceWithoutRender(exact, 735 * 2));
        assertTrue(exact.captureSnapshot().nextYmWriteOrdinal()
                        > exactOrdinal,
                "N=136 admits the complete four-track owner service");
        assertFalse(pending(exactSfx.trackAt(0), "firstFm5AdmissionVoicePending")
                        && exactSfx.trackAt(0).channelId == 4,
                "the admitted service actually executed");

        List<String> callbacks = new ArrayList<>();
        SmpsDriver shortDriver = new SmpsDriver(44_100.0,
                new ChipWriteObserver() {
                    @Override
                    public void onYm2612Write(
                            int port, int register, int value) {
                        callbacks.add("YM");
                    }

                    @Override
                    public void onPsgWrite(int value) {
                        callbacks.add("PSG");
                    }
                });
        admit(shortDriver, loader.loadSfx(Sonic3kSfx.COLLAPSE.id), loader);
        fillRemainingCapacity(shortDriver, 135);
        callbacks.clear();
        SmpsDriverSnapshot before = shortDriver.captureSnapshot();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> advanceWithoutRender(shortDriver, 735 * 2));

        assertTrue(failure.getMessage().contains(
                "aggregate service bound 136"));
        assertEquals(List.of(), callbacks,
                "N-1 publishes no chip or PSG callback");
        assertArrayEquals(before.fmLockSequencerIds(),
                shortDriver.captureSnapshot().fmLockSequencerIds());
        assertArrayEquals(before.psgLockSequencerIds(),
                shortDriver.captureSnapshot().psgLockSequencerIds());
        assertDeepEquals(before, shortDriver.captureSnapshot());
    }

    @Test
    void pendingFenceAdmitsMaximalMusicServiceAtNAndRollsBackAtNMinusOne() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        SmpsDriver measured = freshSpecialStageMusic(loader);
        fillRemainingCapacity(measured, 4_095);
        long measuredOrdinal = measured.captureSnapshot()
                .nextYmWriteOrdinal();

        assertDoesNotThrow(() -> advanceWithoutRender(measured, 735 * 2));
        int musicHorizon = Math.toIntExact(measured.captureSnapshot()
                .nextYmWriteOrdinal() - measuredOrdinal);
        assertEquals(169, musicHorizon,
                "the locked-on Special Stage maximum six-FM initial service "
                        + "has its source-produced YM horizon frozen");

        SmpsDriver exact = freshSpecialStageMusic(loader);
        fillRemainingCapacity(exact, musicHorizon);
        assertDoesNotThrow(() -> advanceWithoutRender(exact, 735 * 2));

        List<String> callbacks = new ArrayList<>();
        SmpsDriver shortDriver = freshSpecialStageMusic(loader, callbacks);
        fillRemainingCapacity(shortDriver, musicHorizon - 1);
        callbacks.clear();
        SmpsDriverSnapshot before = shortDriver.captureSnapshot();

        assertThrows(IllegalStateException.class,
                () -> advanceWithoutRender(shortDriver, 735 * 2));
        assertEquals(List.of(), callbacks);
        assertDeepEquals(before, shortDriver.captureSnapshot());
    }

    @Test
    void eachCollapseSiblingServicePathFitsTheThirtyFourAttemptBound() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(Sonic3kSfx.COLLAPSE.id);
        for (int selected = 0; selected < data.getChannels()
                + data.getPsgChannels(); selected++) {
            SmpsDriver driver = new SmpsDriver();
            SmpsSequencer sfx = admit(driver,
                    loader.loadSfx(Sonic3kSfx.COLLAPSE.id), loader);
            for (int index = 0; index < sfx.trackCount(); index++) {
                sfx.trackAt(index).active = index == selected;
            }
            long before = driver.captureSnapshot().nextYmWriteOrdinal();

            advanceWithoutRender(driver, 735 * 2);

            long attempts = driver.captureSnapshot().nextYmWriteOrdinal()
                    - before;
            if (sfx.trackAt(selected).type == SmpsSequencer.TrackType.FM) {
                assertTrue(attempts > 0 && attempts <= 34,
                        "FM sibling " + selected + " uses " + attempts
                                + " of its 34 reserved attempts");
            } else {
                assertEquals(0, attempts,
                        "PSG sibling consumes no YM reservation attempts");
            }
        }
    }

    @Test
    void sourceTimingAddsNoPublicMutableRuntimeSurface() {
        List<String> leaked = new ArrayList<>();
        for (Class<?> type : List.of(SmpsSequencer.class,
                SmpsSequencer.Track.class, SmpsSequencerConfig.class,
                SmpsSequencerConfig.Builder.class,
                YmServiceTimingProfile.Segment.class)) {
            List.of(type.getDeclaredMethods()).stream()
                    .filter(method -> Modifier.isPublic(
                            method.getModifiers()))
                    .map(Method::getName)
                    .filter(TestS3kBlueSphereSfxParity::isTimingLeak)
                    .map(name -> type.getSimpleName() + ".method:" + name)
                    .forEach(leaked::add);
            List.of(type.getDeclaredFields()).stream()
                    .filter(field -> Modifier.isPublic(field.getModifiers()))
                    .map(field -> field.getName())
                    .filter(TestS3kBlueSphereSfxParity::isTimingLeak)
                    .map(name -> type.getSimpleName() + ".field:" + name)
                    .forEach(leaked::add);
            List.of(type.getDeclaredClasses()).stream()
                    .filter(nested -> Modifier.isPublic(nested.getModifiers()))
                    .map(Class::getSimpleName)
                    .filter(TestS3kBlueSphereSfxParity::isTimingLeak)
                    .map(name -> type.getSimpleName() + ".type:" + name)
                    .forEach(leaked::add);
        }

        assertEquals(List.of(), leaked,
                "source-path flags, controls, owner switches, and leading "
                        + "delay accessors remain outside the public API");
    }

    private static boolean isTimingLeak(String name) {
        String normalized = name.toLowerCase();
        return normalized.contains("firstfm5")
                || normalized.contains("fm5completion")
                || normalized.contains("ymtimingowner")
                || normalized.contains("advancebeforefirst")
                || normalized.equals("completionrestorevariant");
    }

    private static PlaybackInvariant playbackInvariant(
            SmpsSequencerConfig config) {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        List<String> ymWrites = new ArrayList<>();
        List<String> psgWrites = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
                ymWrites.add("YM:%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) {
                psgWrites.add("PSG:%02X".formatted(value));
            }
        });
        SmpsSequencer music = new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.SPECIAL_STAGE.id),
                loader.loadDacData(), driver, config);
        driver.addSequencer(music, false);
        driver.read(new short[735 * 8]);
        ymWrites.clear();
        psgWrites.clear();
        SmpsSequencer collapse = new SmpsSequencer(
                loader.loadSfx(Sonic3kSfx.COLLAPSE.id),
                loader.loadDacData(), driver, config);
        driver.addSequencer(collapse, true);
        driver.read(new short[24_000]);

        var musicState = music.captureSnapshot();
        var ym = driver.captureSnapshot().synthSnapshot().ym();
        return new PlaybackInvariant(
                List.copyOf(ymWrites), List.copyOf(psgWrites),
                musicState.normalTempo(),
                musicState.tempoWeight(), musicState.tempoAccumulator(),
                List.of((long) ym.currentDacSampleId(),
                        (long) ym.dacLatchedValue(),
                        Double.doubleToLongBits(ym.dacPos()),
                        Double.doubleToLongBits(ym.dacStep()),
                        ym.dacEnabled() ? 1L : 0L,
                        ym.dacHasLatched() ? 1L : 0L));
    }

    private static List<String> completeBlueSphereYmStream(
            SmpsSequencerConfig config) {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        List<String> writes = new ArrayList<>();
        SmpsDriver driver = new SmpsDriver();
        driver.setChipWriteObserver(new ChipWriteObserver() {
            @Override
            public void onYm2612Write(
                    int port, int register, int value) {
                writes.add("%d:%02X:%02X".formatted(
                        port, register, value));
            }

            @Override
            public void onPsgWrite(int value) { }
        });
        SmpsSequencer music = new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.SPECIAL_STAGE.id),
                loader.loadDacData(), driver, config);
        driver.addSequencer(music, false);
        driver.read(new short[735 * 8]);
        writes.clear();
        SmpsSequencer blueSphere = new SmpsSequencer(
                loader.loadSfx(Sonic3kSfx.BLUE_SPHERE.id),
                loader.loadDacData(), driver, config);
        driver.addSequencer(blueSphere, true);
        driver.read(new short[80_000]);
        assertTrue(blueSphere.isComplete(),
                "comparison window includes complete SFX playback");
        return List.copyOf(writes);
    }

    private static SmpsSequencerConfig withoutYmTiming(
            SmpsSequencerConfig source) {
        return new SmpsSequencerConfig.Builder()
                .speedUpTempos(source.getSpeedUpTempos())
                .tempoModBase(source.getTempoModBase())
                .fmChannelOrder(source.getFmChannelOrder())
                .psgChannelOrder(source.getPsgChannelOrder())
                .tempoMode(source.getTempoMode())
                .palServicePolicy(source.getPalServicePolicy())
                .tempoPhasePolicy(source.getTempoPhasePolicy())
                .sfxPriorityPolicy(source.getSfxPriorityPolicy())
                .driverServiceOrder(source.getDriverServiceOrder())
                .sfxStartTiming(source.getSfxStartTiming())
                .coordFlagParamOverrides(source.getCoordFlagParamOverrides())
                .applyModOnNote(source.isApplyModOnNote())
                .halveModSteps(source.isHalveModSteps())
                .extraTrkEndFlags(source.getExtraTrkEndFlags())
                .relativePointers(source.isRelativePointers())
                .direct68kDriver(source.isDirect68kDriver())
                .fmSfxTakeoverMode(source.getFmSfxTakeoverMode())
                .fmSfxReleaseMode(source.getFmSfxReleaseMode())
                .psgSfxReleaseMode(source.getPsgSfxReleaseMode())
                .fadeOutChannelPolicy(source.getFadeOutChannelPolicy())
                .musicOverrideSpeedPolicy(
                        source.getMusicOverrideSpeedPolicy())
                .musicOverrideRestorePolicy(
                        source.getMusicOverrideRestorePolicy())
                .musicOverridePriorityPolicy(
                        source.getMusicOverridePriorityPolicy())
                .musicOverrideSfxReleasePolicy(
                        source.getMusicOverrideSfxReleasePolicy())
                .musicOverrideDacRestorePolicy(
                        source.getMusicOverrideDacRestorePolicy())
                .fadeInChannelPolicy(source.getFadeInChannelPolicy())
                .pausePolicy(source.getPausePolicy())
                .sfxRequestTransformPolicy(
                        source.getSfxRequestTransformPolicy())
                .fadeOutClearsSpeedShoes(
                        source.isFadeOutClearsSpeedShoes())
                .fadeOutStopsSfxImmediately(
                        source.isFadeOutStopsSfxImmediately())
                .fmVoiceWriteProfile(source.getFmVoiceWriteProfile())
                .ymServiceTimingProfile(YmServiceTimingProfile.none())
                .volMode(source.getVolMode())
                .psgEnvCmd80(source.getPsgEnvCmd80())
                .noteOnPrevent(source.getNoteOnPrevent())
                .delayFreq(source.getDelayFreq())
                .coordFlagHandler(source.getCoordFlagHandler())
                .modAlgo(source.getModAlgo())
                .fadeOutDelay(source.getFadeOutDelay())
                .fadeOutSteps(source.getFadeOutSteps())
                .fadeInSteps(source.getFadeInSteps())
                .fadeInDelay(source.getFadeInDelay())
                .build();
    }

    private record PlaybackInvariant(
            List<String> ymWrites,
            List<String> psgWrites,
            int normalTempo,
            int tempoWeight,
            int tempoAccumulator,
            List<Long> dacState) {
    }

    private static void advanceWithoutRender(
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
                "s3k-capacity-fixture", null, 0, 1, 1, false, 0);
        List<YmWriteTimeline.Entry> pending = new ArrayList<>(occupied);
        for (int ordinal = 0; ordinal < occupied; ordinal++) {
            pending.add(new YmWriteTimeline.Entry(
                    base.ymServiceCursor(), ordinal,
                    0, 0x22, ordinal & 0xFF,
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
                base.psgLockSequencerIds(), filledSynth,
                base.ymServiceCursor(), base.nextYmServiceOrdinal(),
                occupied, base.driverGeneration()));
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

    private static boolean pending(
            SmpsSequencer.Track track, String fieldName) {
        try {
            var field = SmpsSequencer.Track.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(track);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void setPending(
            SmpsSequencer.Track track, String fieldName, boolean value) {
        try {
            var field = SmpsSequencer.Track.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(track, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static SmpsSequencer admit(
            SmpsDriver driver,
            AbstractSmpsData data,
            Sonic3kSmpsLoader loader) {
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                loader.loadDacData(),
                driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(sequencer, true);
        return sequencer;
    }

    private static SmpsDriver freshSpecialStageMusic(
            Sonic3kSmpsLoader loader) {
        return freshSpecialStageMusic(loader, null);
    }

    private static SmpsDriver freshSpecialStageMusic(
            Sonic3kSmpsLoader loader, List<String> callbacks) {
        SmpsDriver driver = callbacks == null
                ? new SmpsDriver()
                : new SmpsDriver(44_100.0, new ChipWriteObserver() {
                    @Override
                    public void onYm2612Write(
                            int port, int register, int value) {
                        callbacks.add("YM");
                    }

                    @Override
                    public void onPsgWrite(int value) {
                        callbacks.add("PSG");
                    }
                });
        driver.addSequencer(new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.SPECIAL_STAGE.id),
                loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG), false);
        return driver;
    }
}
