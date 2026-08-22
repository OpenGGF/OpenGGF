package com.openggf.audio.driver;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.YmWriteTimeline;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        admit(driver, data, loader);

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
        assertTrue(blue.trackAt(0).firstFm5AdmissionVoicePending,
                "the post-other admission is a fresh source path");
        driver.read(new short[735 * 3]);
        driver.read(new short[735 * 3]);

        assertFalse(blue.trackAt(0).firstFm5AdmissionVoicePending);
        assertFalse(blue.trackAt(0).firstFm5AdmissionAttackPending,
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
        assertTrue(track.firstFm5AdmissionVoicePending);
        assertFalse(track.firstFm5AdmissionAttackPending);

        var snapshot = sequencer.captureSnapshot();
        track.firstFm5AdmissionVoicePending = false;
        track.firstFm5AdmissionAttackPending = true;
        sequencer.restoreSnapshot(snapshot);
        assertTrue(sequencer.trackAt(0).firstFm5AdmissionVoicePending);
        assertFalse(sequencer.trackAt(0).firstFm5AdmissionAttackPending);

        SmpsSequencer.Track identityTrack = sequencer.trackAt(0);
        var token = sequencer.captureLiveCommandMutation();
        identityTrack.firstFm5AdmissionVoicePending = false;
        identityTrack.firstFm5AdmissionAttackPending = true;
        sequencer.rollbackLiveCommandMutation(token);
        assertEquals(identityTrack, sequencer.trackAt(0));
        assertTrue(identityTrack.firstFm5AdmissionVoicePending);
        assertFalse(identityTrack.firstFm5AdmissionAttackPending);
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
        assertFalse(sequencer.trackAt(0).firstFm5AdmissionAttackPending);

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
    void inactiveFm5TrackCannotOpenACompletionRestoreScope() {
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

        assertNull(music.completionRestoreVariant(4));
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
}
