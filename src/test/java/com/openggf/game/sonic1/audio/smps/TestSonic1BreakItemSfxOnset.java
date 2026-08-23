package com.openggf.game.sonic1.audio.smps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.smps.YmSourceProgramTiming;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.audio.synth.YmWriteTimeline;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic1.audio.Sonic1YmServiceTimingProfile;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1BreakItemSfxOnset {

    private static final List<Long> SOURCE_NO_PAN_RELATIVE_CYCLES = List.of(
            0L, 2_142L, 4_270L, 6_398L, 8_526L, 10_654L, 12_782L,
            14_910L, 17_038L, 19_166L, 21_294L, 23_422L, 25_550L,
            27_678L, 29_806L, 31_934L, 34_062L, 36_190L, 38_318L,
            40_446L, 42_574L, 44_863L, 47_131L, 49_385L, 51_653L,
            53_837L, 56_000L, 61_684L, 63_973L, 66_115L);
    private static final List<Long> SOURCE_PAN_RELATIVE_CYCLES = List.of(
            0L, 2_142L, 4_270L, 6_398L, 8_526L, 10_654L, 12_782L,
            14_910L, 17_038L, 19_166L, 21_294L, 23_422L, 25_550L,
            27_678L, 29_806L, 31_934L, 34_062L, 36_190L, 38_318L,
            40_446L, 42_574L, 44_863L, 47_131L, 49_385L, 51_653L,
            53_837L, 56_721L, 59_031L, 63_973L, 66_262L, 68_404L);

    @Test
    void breakItemFm5OnsetMatchesTheDriverVisibleRegisterOrder() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(0xC1);
        SmpsDriver driver = new SmpsDriver(44_100);
        RecordingObserver observer = new RecordingObserver();
        driver.setChipWriteObserver(observer);

        SmpsSequencer sfx = new SmpsSequencer(data, loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        assertEquals(List.of(), observer.events,
                "constructing the ROM-backed C1 SFX must be chip-write-free");
        driver.addSequencer(sfx, true);
        driver.read(new short[2_000], 2_000);

        List<String> ymEvents = observer.events.stream()
                .filter(event -> event.startsWith("YM:"))
                .toList();
        int noteOn = ymEvents.indexOf("YM:0:28:F5");
        assertTrue(noteOn >= 0, "C1 FM5 note-on was not observed");
        assertEquals(List.of(
                "YM:1:B1:3C",
                "YM:1:31:0F", "YM:1:39:01", "YM:1:35:03", "YM:1:3D:01",
                "YM:1:51:1F", "YM:1:59:1F", "YM:1:55:1F", "YM:1:5D:1F",
                "YM:1:61:19", "YM:1:69:12", "YM:1:65:19", "YM:1:6D:0E",
                "YM:1:71:05", "YM:1:79:12", "YM:1:75:00", "YM:1:7D:0F",
                "YM:1:81:0F", "YM:1:89:7F", "YM:1:85:FF", "YM:1:8D:FF",
                "YM:1:41:00", "YM:1:49:80", "YM:1:45:00", "YM:1:4D:80",
                "YM:1:B5:C0",
                "YM:0:28:05", "YM:1:A5:24", "YM:1:A1:3C", "YM:0:28:F5"),
                ymEvents.subList(0, noteOn + 1));
    }

    @Test
    void breakItemKeepsTheSourceBusyCycleVectorAcrossHelpers() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver(44_100);
        SmpsSequencer sfx = new SmpsSequencer(
                loader.loadSfx(0xC1), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);

        advanceWithoutRendering(driver, sfx.getSamplesUntilNextDriverService());

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(30, pending.size());
        long rowZeroDue = pending.getFirst().dueMasterCycle();
        assertEquals(SOURCE_NO_PAN_RELATIVE_CYCLES,
                pending.stream()
                        .map(entry -> entry.dueMasterCycle() - rowZeroDue)
                        .toList());
        assertEquals(List.of(
                        0xB1, 0x31, 0x39, 0x35, 0x3D, 0x51, 0x59, 0x55, 0x5D,
                        0x61, 0x69, 0x65, 0x6D, 0x71, 0x79, 0x75, 0x7D,
                        0x81, 0x89, 0x85, 0x8D, 0x41, 0x49, 0x45, 0x4D,
                        0xB5, 0x28, 0xA5, 0xA1, 0x28),
                pending.stream().map(YmWriteTimeline.Entry::register).toList());
    }

    @Test
    void ringKeepsTheSourceBusyOptionalPanCycleVectorAcrossHelpers() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver(44_100);
        SmpsSequencer sfx = new SmpsSequencer(
                loader.loadSfx(0xB5), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);

        advanceWithoutRendering(driver, sfx.getSamplesUntilNextDriverService());

        List<YmWriteTimeline.Entry> pending = driver.captureSnapshot()
                .synthSnapshot().ymWriteTimeline().pending();
        assertEquals(31, pending.size());
        long rowZeroDue = pending.getFirst().dueMasterCycle();
        assertEquals(SOURCE_PAN_RELATIVE_CYCLES,
                pending.stream()
                        .map(entry -> entry.dueMasterCycle() - rowZeroDue)
                        .toList());
        assertEquals(List.of(
                        0xB1, 0x31, 0x39, 0x35, 0x3D, 0x51, 0x59, 0x55, 0x5D,
                        0x61, 0x69, 0x65, 0x6D, 0x71, 0x79, 0x75, 0x7D,
                        0x81, 0x89, 0x85, 0x8D, 0x41, 0x49, 0x45, 0x4D,
                        0xB5, 0xB5, 0x28, 0xA5, 0xA1, 0x28),
                pending.stream().map(YmWriteTimeline.Entry::register).toList());
    }

    @Test
    void ringPartialDrainSnapshotReplaysIdenticalPcmAndTimeline() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver(44_100);
        SmpsSequencer sfx = new SmpsSequencer(
                loader.loadSfx(0xB5), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);
        advanceWithoutRendering(driver, sfx.getSamplesUntilNextDriverService());

        driver.read(new short[64], 64);
        SmpsDriverSnapshot partial = driver.captureSnapshot();
        assertTrue(partial.synthSnapshot().ymWriteTimeline().pending().size()
                < 31);
        assertTrue(partial.synthSnapshot().ymWriteTimeline().pending().size()
                > 0);

        short[] first = new short[512];
        driver.read(first, first.length);
        SmpsDriverSnapshot firstEnd = driver.captureSnapshot();

        driver.restoreSnapshot(partial);
        short[] replay = new short[512];
        driver.read(replay, replay.length);
        SmpsDriverSnapshot replayEnd = driver.captureSnapshot();

        assertArrayEquals(first, replay);
        assertEquals(firstEnd.synthSnapshot().ymWriteTimeline(),
                replayEnd.synthSnapshot().ymWriteTimeline());
        assertEquals(firstEnd.synthSnapshot().renderedYmMasterCycle(),
                replayEnd.synthSnapshot().renderedYmMasterCycle());
        assertEquals(firstEnd.ymServiceCursor(), replayEnd.ymServiceCursor());
        assertEquals(firstEnd.nextYmServiceOrdinal(),
                replayEnd.nextYmServiceOrdinal());
        assertEquals(firstEnd.nextYmWriteOrdinal(),
                replayEnd.nextYmWriteOrdinal());
    }

    @Test
    void ringHybridAndSampleAccurateRenderingRemainIdentical() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver hybrid = driverWithSfx(loader, 0xB5);
        SmpsDriver accurate = driverWithSfx(loader, 0xB5);
        setReadMode(accurate, SmpsDriver.ReadMode.SAMPLE_ACCURATE);

        short[] hybridPcm = new short[4_000];
        short[] accuratePcm = new short[4_000];
        hybrid.read(hybridPcm, hybridPcm.length);
        accurate.read(accuratePcm, accuratePcm.length);

        assertArrayEquals(accuratePcm, hybridPcm);
        SmpsDriverSnapshot hybridEnd = hybrid.captureSnapshot();
        SmpsDriverSnapshot accurateEnd = accurate.captureSnapshot();
        assertEquals(accurateEnd.synthSnapshot().ymWriteTimeline(),
                hybridEnd.synthSnapshot().ymWriteTimeline());
        assertEquals(accurateEnd.synthSnapshot().renderedYmMasterCycle(),
                hybridEnd.synthSnapshot().renderedYmMasterCycle());
        assertEquals(accurateEnd.ymServiceCursor(), hybridEnd.ymServiceCursor());
        assertEquals(accurateEnd.nextYmServiceOrdinal(),
                hybridEnd.nextYmServiceOrdinal());
        assertEquals(accurateEnd.nextYmWriteOrdinal(),
                hybridEnd.nextYmWriteOrdinal());
    }

    private static SmpsDriver driverWithSfx(Sonic1SmpsLoader loader, int id) {
        SmpsDriver driver = new SmpsDriver(44_100);
        SmpsSequencer sfx = new SmpsSequencer(
                loader.loadSfx(id), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);
        return driver;
    }

    private static void setReadMode(
            SmpsDriver driver, SmpsDriver.ReadMode mode) {
        try {
            Method setter = SmpsDriver.class.getDeclaredMethod(
                    "setReadModeForTesting", SmpsDriver.ReadMode.class);
            setter.setAccessible(true);
            setter.invoke(driver, mode);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    @Test
    void breakItemExclusiveOwnerRejectsNMinusOneBeforeAnyPublication() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver(44_100);
        RecordingObserver observer = new RecordingObserver();
        driver.setChipWriteObserver(observer);
        fillRemainingCapacity(driver, 4_095);
        SmpsSequencer sfx = new SmpsSequencer(
                loader.loadSfx(0xC1), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);
        SmpsDriverSnapshot before = driver.captureSnapshot();
        int samplesUntilService = sfx.getSamplesUntilNextDriverService();

        assertThrows(IllegalStateException.class,
                () -> advanceWithoutRendering(driver, samplesUntilService));

        SmpsDriverSnapshot after = driver.captureSnapshot();
        assertEquals(before.synthSnapshot().ymWriteTimeline(),
                after.synthSnapshot().ymWriteTimeline());
        assertEquals(before.ymServiceCursor(), after.ymServiceCursor());
        assertEquals(before.nextYmServiceOrdinal(), after.nextYmServiceOrdinal());
        assertEquals(before.nextYmWriteOrdinal(), after.nextYmWriteOrdinal());
        assertEquals(samplesUntilService, sfx.getSamplesUntilNextDriverService());
        assertEquals(List.of(), observer.events);
    }

    @Test
    void unsupportedSetVoiceRestPathDoesNotReserveTheExclusiveQueue() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver(44_100);
        RecordingObserver observer = new RecordingObserver();
        driver.setChipWriteObserver(observer);
        fillRemainingCapacity(driver, 128);
        int occupied = driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().size();
        SmpsSequencer sfx = new SmpsSequencer(
                loader.loadSfx(0xC6), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfx, true);

        advanceWithoutRendering(driver, sfx.getSamplesUntilNextDriverService());

        assertEquals(occupied, driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending().size(),
                "SetVoice followed by rest must not reserve or append timed writes");
        assertFalse(observer.events.isEmpty(),
                "the unsupported path must retain its immediate chip publications");
    }

    @Test
    void mismatchedSourceSectionAbortsTheWholeUnpublishedTransaction() {
        Sonic1SmpsLoader loader = new Sonic1SmpsLoader(TestEnvironment.currentRom());
        SmpsDriver driver = new SmpsDriver(44_100);
        SmpsSequencer source = new SmpsSequencer(
                loader.loadSfx(0xC1), loader.loadDacData(), driver,
                () -> {}, Sonic1SmpsSequencerConfig.CONFIG);
        driver.addSequencer(source, true);
        SmpsDriverSnapshot before = driver.captureSnapshot();
        YmSourceProgramTiming.SourceProgram program =
                Sonic1YmServiceTimingProfile.PROFILE.requireProgram(
                        YmSourceProgramTiming.FirstPathShape.VOICE_NOTE, 0b1010);
        var scope = driver.beginYmSourceProgram(source, program,
                YmServiceTimingProfile.SegmentKind.FM_VOICE_UPLOAD);

        assertThrows(IllegalStateException.class, () ->
                driver.enterYmSourceProgramSection(source,
                        YmServiceTimingProfile.SegmentKind.KEY_OFF));
        assertThrows(IllegalStateException.class, scope::close);

        SmpsDriverSnapshot after = driver.captureSnapshot();
        assertEquals(before.synthSnapshot().ymWriteTimeline(),
                after.synthSnapshot().ymWriteTimeline());
        assertEquals(before.ymServiceCursor(), after.ymServiceCursor());
        assertEquals(before.nextYmServiceOrdinal(), after.nextYmServiceOrdinal());
        assertEquals(before.nextYmWriteOrdinal(), after.nextYmWriteOrdinal());
    }

    private static void fillRemainingCapacity(SmpsDriver driver, int remaining) {
        SmpsDriverSnapshot base = driver.captureSnapshot();
        int capacity = base.synthSnapshot().ymWriteTimeline().capacity();
        int occupied = capacity - remaining;
        SmpsSourceDescriptor descriptor = new SmpsSourceDescriptor(
                SmpsSourceDescriptor.Kind.UNKNOWN, 0x55,
                "s1-capacity-fixture", null, 0, 1, 1, false, 0);
        List<YmWriteTimeline.Entry> pending = new ArrayList<>(occupied);
        for (int ordinal = 0; ordinal < occupied; ordinal++) {
            pending.add(new YmWriteTimeline.Entry(
                    0, ordinal, 0, 0x22, ordinal & 0xff,
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

    private static void advanceWithoutRendering(SmpsDriver driver, int frames) {
        try {
            Method advance = SmpsDriver.class.getDeclaredMethod(
                    "advanceSequencersBatch", int.class);
            advance.setAccessible(true);
            advance.invoke(driver, frames);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onYm2612Write(int port, int register, int value) {
            events.add("YM:%d:%02X:%02X".formatted(port, register, value));
        }

        @Override
        public void onPsgWrite(int value) {
            events.add("PSG:%02X".formatted(value));
        }
    }
}
