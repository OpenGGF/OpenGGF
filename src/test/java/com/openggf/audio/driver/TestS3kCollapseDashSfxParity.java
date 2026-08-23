package com.openggf.audio.driver;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.YmServiceTimingProfile;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCollapseDashSfxParity {

    @Test
    void collapsePsgRunsFiveTwentyFourTickBurstsThenStops() {
        Fixture fixture = fixture(Sonic3kSfx.COLLAPSE);
        assertEquals(4, fixture.sequencer.trackCount());
        SmpsSequencer.Track psg3 = fixture.sequencer.trackAt(3);
        List<Integer> burstVolumes = new ArrayList<>();

        int frames = runToCompletion(fixture, () -> {
            if (psg3.active && psg3.duration == 0x18) {
                burstVolumes.add(psg3.volumeOffset);
            }
        });

        assertEquals(List.of(0, 3, 6, 9, 12), burstVolumes,
                "Sound_59 loops the tied PSG3 note exactly five times");
        assertEquals(0x0F, psg3.volumeOffset,
                "the terminal EC +3 reaches native silence before F2");
        assertEquals(122, frames,
                "request/load frame plus five 24-tick bursts match the Z80 lifecycle");
    }

    @Test
    void collapsePsgChipWritesBeginLikeTheNativeDriver() {
        Fixture fixture = fixture(Sonic3kSfx.COLLAPSE);

        // The first engine service is the native request/admission frame. Its
        // defensive PSG takeover writes are intentionally outside the SFX
        // track-update stream compared below.
        fixture.driver.read(new short[735 * 2]);
        fixture.observer.takePsgWrites();

        fixture.driver.read(new short[735 * 2]);
        assertEquals(List.of(0xDF, 0xE7, 0xC8, 0x04, 0xF0),
                fixture.observer.takePsgWrites());

    }

    @Test
    void dashPsgChipWritesBeginLikeTheNativeDriver() {
        Fixture fixture = fixture(Sonic3kSfx.DASH);

        fixture.driver.read(new short[735 * 2]);
        fixture.observer.takePsgWrites();
        for (int frame = 1; frame <= 6; frame++) {
            fixture.driver.read(new short[735 * 2]);
            fixture.observer.takePsgWrites();
        }
        fixture.driver.read(new short[735 * 2]);
        assertEquals(List.of(0xDF, 0xE7, 0xC6, 0x01, 0xF0),
                fixture.observer.takePsgWrites());
    }

    @Test
    void collapseEffectivePsgStateMatchesEveryNativeFrame() {
        assertEquals(
                "d85bbd997725b5804d5990cb222f13a1c367ce2e76b628ab5ec61c515d81c584",
                effectivePsgDigest(Sonic3kSfx.COLLAPSE, 124));
    }

    @Test
    void dashEffectivePsgStateMatchesEveryNativeFrame() {
        assertEquals(
                "0b7d78978c85bc7c021789c333594b96f905bbf2e64f1b2b3921751f2af1e093",
                effectivePsgDigest(Sonic3kSfx.DASH, 89));
    }

    @Test
    void collapseFmTracksKeepTheirNativeStaggerAndModulation() {
        FmTrackTimeline timeline = fmTrackTimeline(
                Sonic3kSfx.COLLAPSE, 20);
        assertEquals(List.of(1, 2, 3), timeline.keyOnFrames);
        assertEquals(List.of(17, 18, 19), timeline.keyOffFrames);
        assertEquals(List.of(
                        0x284, 0x284, 0x2A4, 0x2C4,
                        0x2A4, 0x284, 0x264, 0x244,
                        0x264, 0x284, 0x2A4, 0x2C4,
                        0x2A4, 0x284, 0x264, 0x244),
                timeline.frequencies.get(3));
        assertEquals(List.of(
                        0xB2D, 0xB2D, 0xB4D, 0xB6D,
                        0xB4D, 0xB2D, 0xB0D, 0xAED,
                        0xB0D, 0xB2D, 0xB4D, 0xB6D,
                        0xB4D, 0xB2D, 0xB0D, 0xAED),
                timeline.frequencies.get(4));
        assertEquals(timeline.frequencies.get(4),
                timeline.frequencies.get(2),
                "FM3 follows the same four-step wobble one VInt after FM5");
    }

    @Test
    void collapseDelayedFm5VoiceStartsOnItsNativeSecondUpdate() {
        Fixture fixture = fixture(Sonic3kSfx.COLLAPSE);
        fixture.driver.read(new short[735 * 2]);
        fixture.observer.takeYmWrites();
        fixture.driver.read(new short[735 * 2]);
        fixture.observer.takeYmWrites();

        fixture.driver.read(new short[735 * 2]);

        List<String> writes = fixture.observer.takeYmWrites();
        assertTrue(writes.contains("p1:a5=b"));
        assertTrue(writes.contains("p1:a1=2d"));
        assertTrue(writes.contains("p0:28=f5"));
    }

    @Test
    void dashFmModulationAndTerminalMatchNative() {
        FmTrackTimeline timeline = fmTrackTimeline(Sonic3kSfx.DASH, 18);
        assertEquals(List.of(2), timeline.keyOnFrames);
        assertEquals(List.of(16), timeline.keyOffFrames);
        assertEquals(List.of(
                        0x32B7, 0x327C, 0x3241, 0x3206,
                        0x31CB, 0x3190, 0x3155, 0x311A, 0x30DF,
                        0x30A4, 0x3069, 0x302E, 0x3069, 0x30A4),
                timeline.frequencies.get(4));
    }

    @Test
    void dashFirstModulationWaitsOneNativeDriverServiceAfterKeyOn() {
        Fixture fixture = fixture(Sonic3kSfx.DASH);
        for (int frame = 0; frame < 4; frame++) {
            fixture.driver.read(new short[735 * 2]);
        }

        List<TimedYmWrite> writes = fixture.observer.timedYmWrites().stream()
                .filter(write -> (write.port == 1
                        && (write.register == 0xA5 || write.register == 0xA1))
                        || (write.port == 0 && write.register == 0x28))
                .toList();
        int keyOn = indexOf(writes, 0, 0x28, 0xF5);
        int firstModHigh = indexOfAfter(writes, keyOn, 1, 0xA5, 0x32);
        int firstModLow = indexOfAfter(writes, firstModHigh, 1, 0xA1, 0xB7);
        long keyOnCycle = writes.get(keyOn).masterCycle;
        long firstModCycle = writes.get(firstModLow).masterCycle;
        assertTrue(firstModCycle - keyOnCycle > 700_000,
                "zDoModulation's first sustain update must not collapse into "
                        + "the attack service: " + writes);
    }

    @Test
    void dashFirstAttackIncludesItsNativeOctaveLoopCost() {
        Fixture fixture = fixture(Sonic3kSfx.DASH);
        fixture.driver.read(new short[735 * 2]);
        fixture.driver.read(new short[735 * 2]);

        var pending = fixture.driver.captureSnapshot().synthSnapshot()
                .ymWriteTimeline().pending();
        long secondRelease = pending.stream()
                .filter(entry -> entry.sourceOrdinal() == 6)
                .findFirst().orElseThrow().dueMasterCycle();
        long firstRelease = Math.subtractExact(secondRelease, 3_150L);
        long keyOn = pending.stream()
                .filter(entry -> entry.port() == 0
                        && entry.register() == 0x28 && entry.value() == 0xF5)
                .findFirst().orElseThrow().dueMasterCycle();
        assertEquals(152_640L, keyOn - firstRelease,
                "nE6 executes two more 35-T-state octave loops than the "
                        + "nEb5 timing authority: 2 * 35 * 15 = 1050");
    }

    @Test
    void invincibilityFm1NoteFillKeysOffTheThreeShortD5Attacks() {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        RecordingObserver observer = new RecordingObserver();
        TimelineDriver driver = new TimelineDriver(observer);
        observer.masterCycle = driver::masterCycle;
        SmpsSequencer music = new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.INVINCIBILITY.id),
                loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(music, false);
        observer.takeYmWrites();

        List<String> fm1Keys = new ArrayList<>();
        for (int frame = 0; frame <= 220; frame++) {
            driver.read(new short[735 * 2]);
            for (String write : observer.takeYmWrites()) {
                if (write.equals("p0:28=0") || write.equals("p0:28=f0")) {
                    fm1Keys.add(frame + ":" + write.substring(6));
                }
            }
        }
        List<Integer> attacks = new ArrayList<>();
        List<Integer> keyOffs = new ArrayList<>();
        for (String event : fm1Keys) {
            int separator = event.indexOf(':');
            int frame = Integer.parseInt(event.substring(0, separator));
            if (frame < 170 || frame > 210) continue;
            if (event.endsWith(":f0")) attacks.add(frame);
            else keyOffs.add(frame);
        }
        List<Integer> fills = keyOffs.stream()
                .filter(frame -> !attacks.contains(frame)).toList();
        assertEquals(3, attacks.size());
        assertEquals(3, fills.size());
        for (int index = 0; index < attacks.size(); index++) {
            assertEquals(5, fills.get(index) - attacks.get(index),
                    "zTrackNoteFillUpdate must key off after five music "
                            + "services regardless of TempoWait duration carry");
            if (index + 1 < attacks.size()) {
                assertTrue(fills.get(index) < attacks.get(index + 1),
                        "each short D5 attack must end before the next attack");
            }
        }
    }

    @Test
    void invincibilityNoteFillIsOutputChunkPartitionInvariant() {
        int[] frameChunks = new int[221];
        java.util.Arrays.fill(frameChunks, 735);
        assertEquals(invincibilityFm1Keys(frameChunks),
                invincibilityFm1Keys(new int[] { 735 * 221 }),
                "the live NoteFillTimeout must remain a driver-service "
                        + "boundary when the host requests one large buffer");
    }

    @Test
    void dashPsgRestsSixTicksThenRunsOneSeventyNineTickNote() {
        Fixture fixture = fixture(Sonic3kSfx.DASH);
        assertEquals(2, fixture.sequencer.trackCount());
        SmpsSequencer.Track psg3 = fixture.sequencer.trackAt(1);
        List<Integer> startedDurations = new ArrayList<>();
        int[] priorDuration = {psg3.duration};

        int frames = runToCompletion(fixture, () -> {
            if (psg3.active && psg3.duration > priorDuration[0]) {
                startedDurations.add(psg3.duration);
            }
            priorDuration[0] = psg3.duration;
        });

        assertEquals(List.of(0x06, 0x4F), startedDurations,
                "Sound_B6 preserves its rest then its one modulated PSG note");
        assertTrue(psg3.envHold,
                "native tone 1D reaches its hold byte before track end");
        assertEquals(87, frames,
                "request/load frame plus 6 rest ticks and 79 note ticks match the Z80 lifecycle");
    }

    @Test
    void fm5KeysOffWhenItsTrackEndsBeforeThePsgSibling() {
        assertFm5KeyOffFrame(Sonic3kSfx.COLLAPSE, 18);
        assertFm5KeyOffFrame(Sonic3kSfx.DASH, 16);
    }

    @Test
    void fm5TrackTerminalRestoresMusicBeforePsgSiblingAndKeysOffOnce() {
        assertFm5TrackRelease(Sonic3kSfx.COLLAPSE, 18);
        assertFm5TrackRelease(Sonic3kSfx.DASH, 16);
    }

    private static void assertFm5TrackRelease(
            Sonic3kSfx sfx, int expectedFrame) {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        RecordingObserver observer = new RecordingObserver();
        SmpsDriver driver = new SmpsDriver(44_100.0, observer);
        SmpsSequencer music = new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.SPECIAL_STAGE.id),
                loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(music, false);
        driver.read(new short[735 * 8]);
        observer.takeYmWrites();

        SmpsSequencer sfxSequencer = new SmpsSequencer(
                loader.loadSfx(sfx.id), loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(sfxSequencer, true);
        SmpsSequencer.Track musicFm5 = fm5Track(music);
        assertTrue(musicFm5.overridden,
                "the SFX must own FM5 before its terminal service");

        Set<Long> completionRestoreServices = new HashSet<>();
        boolean restoredVoiceObserved = false;
        for (int frame = 0; frame < 160 && !sfxSequencer.isComplete(); frame++) {
            driver.read(new short[735 * 2]);
            List<String> writes = observer.takeYmWrites();
            driver.captureSnapshot().synthSnapshot().ymWriteTimeline()
                    .pending().stream()
                    .filter(entry -> entry.segment()
                            == YmServiceTimingProfile.SegmentKind
                                    .COMPLETION_RESTORE)
                    .map(entry -> entry.serviceOrdinal())
                    .forEach(completionRestoreServices::add);
            if (frame >= expectedFrame) {
                restoredVoiceObserved |= writes.contains("p1:b5=c0");
                assertFalse(musicFm5.overridden,
                        "FM5 must remain released after its track terminal");
            }
            if (frame == expectedFrame) {
                assertFalse(musicFm5.overridden,
                        "fix_sndbugs=0 cfStopTrack releases and restores "
                                + "the interrupted music channel immediately");
                assertFalse(sfxSequencer.isComplete(),
                        "the PSG sibling must still be active at FM5 release");
            }
        }

        assertTrue(sfxSequencer.isComplete());
        assertEquals(1, completionRestoreServices.size(),
                "whole-SFX cleanup must not repeat the per-track key-off and "
                        + "music-voice restore transaction");
        assertTrue(restoredVoiceObserved,
                "the active Special Stage FM5 voice is restored at cfStopTrack");
    }

    private static SmpsSequencer.Track fm5Track(SmpsSequencer sequencer) {
        for (int index = 0; index < sequencer.trackCount(); index++) {
            SmpsSequencer.Track track = sequencer.trackAt(index);
            if (track.type == SmpsSequencer.TrackType.FM
                    && track.channelId == 4) {
                return track;
            }
        }
        throw new AssertionError("Special Stage music must contain FM5");
    }

    private static void assertFm5KeyOffFrame(
            Sonic3kSfx sfx, int expectedFrame) {
        Fixture fixture = fixture(sfx);
        int actualFrame = -1;
        for (int frame = 0; frame < 32; frame++) {
            fixture.driver.read(new short[735 * 2]);
            if (frame >= 10
                    && fixture.observer.takeYmWrites().contains("p0:28=5")) {
                actualFrame = frame;
                break;
            } else if (frame < 10) {
                fixture.observer.takeYmWrites();
            }
        }
        assertEquals(expectedFrame, actualFrame,
                "fix_sndbugs=0 cfStopTrack keys off FM5 at its own terminal "
                        + "service even while a sibling PSG track remains active");
        assertTrue(!fixture.sequencer.isComplete(),
                "the regression requires the PSG sibling to outlive FM5");
    }

    @Test
    void collapseFmTailsRemainStereoBalancedAfterTheirTrackTerminals() {
        double[] rms = renderedStereoLevel(
                fixture(Sonic3kSfx.COLLAPSE), 124);
        double ratio = Math.max(rms[0], rms[1])
                / Math.min(rms[0], rms[1]);
        assertTrue(ratio < 1.10,
                "native Collapse is near-balanced after its staggered FM "
                        + "terminals; a missing FM5 key-off leaves the left "
                        + "tail about 1.8x louder");
    }

    private static double[] renderedStereoLevel(Fixture fixture, int frames) {
        double leftSquares = 0;
        double rightSquares = 0;
        int samples = 0;
        for (int frame = 0; frame < frames; frame++) {
            short[] pcm = new short[735 * 2];
            fixture.driver.read(pcm);
            for (int index = 0; index < pcm.length; index += 2) {
                leftSquares += (double) pcm[index] * pcm[index];
                rightSquares += (double) pcm[index + 1] * pcm[index + 1];
                samples++;
            }
        }
        double leftRms = Math.sqrt(leftSquares / samples);
        double rightRms = Math.sqrt(rightSquares / samples);
        return new double[] { leftRms, rightRms };
    }

    private static int runToCompletion(Fixture fixture, Runnable afterFrame) {
        for (int frame = 1; frame <= 512; frame++) {
            fixture.driver.read(new short[735 * 2]);
            afterFrame.run();
            if (fixture.sequencer.isComplete()) {
                return frame;
            }
        }
        throw new AssertionError("SFX remained active beyond 512 driver frames");
    }

    private static Fixture fixture(Sonic3kSfx sfx) {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        AbstractSmpsData data = loader.loadSfx(sfx.id);
        RecordingObserver observer = new RecordingObserver();
        TimelineDriver driver = new TimelineDriver(observer);
        observer.masterCycle = driver::masterCycle;
        SmpsSequencer sequencer = new SmpsSequencer(
                data, loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG);
        driver.addSequencer(sequencer, true);
        return new Fixture(driver, sequencer, observer);
    }

    private static int indexOf(List<TimedYmWrite> writes, int port,
            int register, int value) {
        return indexOfAfter(writes, -1, port, register, value);
    }

    private static List<String> invincibilityFm1Keys(int[] chunks) {
        Sonic3kSmpsLoader loader = new Sonic3kSmpsLoader(
                TestEnvironment.currentRom());
        RecordingObserver observer = new RecordingObserver();
        TimelineDriver driver = new TimelineDriver(observer);
        observer.masterCycle = driver::masterCycle;
        driver.addSequencer(new SmpsSequencer(
                loader.loadMusic(Sonic3kMusic.INVINCIBILITY.id),
                loader.loadDacData(), driver,
                Sonic3kSmpsSequencerConfig.CONFIG), false);
        observer.takeYmWrites();
        List<String> keys = new ArrayList<>();
        for (int frames : chunks) {
            driver.read(new short[frames * 2]);
            observer.takeYmWrites().stream()
                    .filter(write -> write.equals("p0:28=0")
                            || write.equals("p0:28=f0"))
                    .forEach(keys::add);
        }
        return keys;
    }

    private static int indexOfAfter(List<TimedYmWrite> writes, int after,
            int port, int register, int value) {
        for (int index = after + 1; index < writes.size(); index++) {
            TimedYmWrite write = writes.get(index);
            if (write.port == port && write.register == register
                    && write.value == value) {
                return index;
            }
        }
        throw new AssertionError("Missing YM write " + port + ":"
                + Integer.toHexString(register) + "="
                + Integer.toHexString(value) + " in " + writes);
    }

    private static String effectivePsgDigest(
            Sonic3kSfx sfx, int frames) {
        Fixture fixture = fixture(sfx);
        StringBuilder rows = new StringBuilder();
        for (int frame = 0; frame < frames; frame++) {
            fixture.driver.read(new short[735 * 2]);
            rows.append(fixture.observer.effectiveState(frame));
        }
        return sha256(rows);
    }

    private static FmTrackTimeline fmTrackTimeline(
            Sonic3kSfx sfx, int lastFrame) {
        Fixture fixture = fixture(sfx);
        int[] high = new int[6];
        int[] low = new int[6];
        boolean[] keyed = new boolean[6];
        List<Integer> keyOnFrames = new ArrayList<>();
        List<Integer> keyOffFrames = new ArrayList<>();
        java.util.Map<Integer, List<Integer>> frequencies =
                new java.util.HashMap<>();
        for (int frame = 0; frame <= lastFrame; frame++) {
            fixture.driver.read(new short[735 * 2]);
            for (TimedYmWrite write : fixture.observer.takeTimedYmWrites()) {
                if (write.port == 0 && write.register == 0x28) {
                    int channel = write.value & 7;
                    if (channel >= 4) channel -= 1;
                    boolean nextKeyed = (write.value & 0xF0) != 0;
                    if (nextKeyed && !keyed[channel]) keyOnFrames.add(frame);
                    if (!nextKeyed && keyed[channel]) keyOffFrames.add(frame);
                    keyed[channel] = nextKeyed;
                } else if (write.register >= 0xA4
                        && write.register <= 0xA6) {
                    int channel = (write.register - 0xA4)
                            + (write.port == 0 ? 0 : 3);
                    high[channel] = write.value;
                } else if (write.register >= 0xA0
                        && write.register <= 0xA2) {
                    int channel = (write.register - 0xA0)
                            + (write.port == 0 ? 0 : 3);
                    low[channel] = write.value;
                }
            }
            for (int channel = 0; channel < keyed.length; channel++) {
                if (keyed[channel]) {
                    frequencies.computeIfAbsent(channel,
                                    ignored -> new ArrayList<>())
                            .add((high[channel] << 8) | low[channel]);
                }
            }
        }
        return new FmTrackTimeline(keyOnFrames, keyOffFrames, frequencies);
    }

    private static String sha256(StringBuilder rows) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rows.toString().getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Fixture(
            TimelineDriver driver,
            SmpsSequencer sequencer,
            RecordingObserver observer) {
    }

    private record TimedYmWrite(
            long masterCycle, int port, int register, int value) {
    }

    private record FmTrackTimeline(
            List<Integer> keyOnFrames,
            List<Integer> keyOffFrames,
            java.util.Map<Integer, List<Integer>> frequencies) {
    }

    private static final class TimelineDriver extends SmpsDriver {
        private TimelineDriver(ChipWriteObserver observer) {
            super(44_100.0, observer);
        }

        private long masterCycle() {
            return renderedYmMasterCycle();
        }
    }

    private static final class RecordingObserver implements ChipWriteObserver {
        private final List<Integer> psgWrites = new ArrayList<>();
        private final List<String> ymWrites = new ArrayList<>();
        private final List<TimedYmWrite> timedYmWrites = new ArrayList<>();
        private LongSupplier masterCycle = () -> -1L;
        private int latchedChannel = -1;
        private boolean latchedVolume;
        private int tone2Period;
        private int noise;
        private int tone2Volume = 0x0F;
        private int noiseVolume = 0x0F;

        @Override
        public void onYm2612Write(int port, int register, int value) {
            ymWrites.add("p" + port + ":"
                    + Integer.toHexString(register & 0xFF) + "="
                    + Integer.toHexString(value & 0xFF));
            timedYmWrites.add(new TimedYmWrite(masterCycle.getAsLong(),
                    port, register & 0xFF, value & 0xFF));
        }


        private List<TimedYmWrite> timedYmWrites() {
            return List.copyOf(timedYmWrites);
        }

        private List<TimedYmWrite> takeTimedYmWrites() {
            List<TimedYmWrite> copy = List.copyOf(timedYmWrites);
            timedYmWrites.clear();
            return copy;
        }

        @Override
        public void onPsgWrite(int value) {
            value &= 0xFF;
            psgWrites.add(value);
            if ((value & 0x80) != 0) {
                latchedChannel = (value >> 5) & 3;
                latchedVolume = (value & 0x10) != 0;
                if (latchedVolume) {
                    if (latchedChannel == 2) {
                        tone2Volume = value & 0x0F;
                    } else if (latchedChannel == 3) {
                        noiseVolume = value & 0x0F;
                    }
                } else if (latchedChannel == 2) {
                    tone2Period = (tone2Period & 0x3F0) | (value & 0x0F);
                } else if (latchedChannel == 3) {
                    noise = value & 7;
                }
            } else if (!latchedVolume && latchedChannel == 2) {
                tone2Period = (tone2Period & 0x0F) | ((value & 0x3F) << 4);
            }
        }

        private List<Integer> takePsgWrites() {
            List<Integer> result = List.copyOf(psgWrites);
            psgWrites.clear();
            return result;
        }

        private List<String> takeYmWrites() {
            List<String> result = List.copyOf(ymWrites);
            ymWrites.clear();
            return result;
        }

        private String effectiveState(int frame) {
            return frame + ":" + tone2Period + ":" + noise + ":"
                    + tone2Volume + ":" + noiseVolume + "\n";
        }

    }
}
