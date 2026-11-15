package com.openggf.game.soniccd.audio;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLogicalWriteTarget;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FM sound effects from the disc's driver bank through the shared sequencer.
 *
 * <p>The native check compares each effect's track, tick by tick, with the Sonic CD driver's
 * own RAM: the retail Sound Test (Title Screen/Secrets/Sound Test.asm {@code SelectFM},
 * :288-304) played fifteen effects under BizHawk 2.11 gpgx, and the probe dumped the driver
 * variables 1C00h-1D5Fh after every frame
 * ({@code <native.root>/aud1-fixtures/soundtest-fm}; probe and schedule in
 * {@code ~/Downloads/Sonic-CD/native/aud1-tools}). For every note the driver parses, the data
 * pointer it stores ({@code ftrkDataPtr}), the frequency ({@code ftrkFreq}) and the duration it
 * loads ({@code ftrkDurCnt}) must equal the shared sequencer's.
 */
class TestCdFmSoundEffects {
    private static final DacData NO_DAC = new DacData(Map.of(), Map.of());
    private static byte[] bank;
    private static CdFmSoundAssets assets;

    @BeforeAll
    static void loadBank() throws IOException {
        try (var programs = CdAudioTestMedia.usaPrograms()) {
            assets = CdFmSoundAssets.fromPrograms(programs);
        }
        bank = assets.bank().copy();
    }

    /** One note the driver or the sequencer parsed: where it left the stream, its pitch and length. */
    record Parse(int pointer, int frequency, int duration, int at) {
        @Override public boolean equals(Object o) {
            return o instanceof Parse p && p.pointer == pointer && p.frequency == frequency && p.duration == duration;
        }
        @Override public int hashCode() { return Objects.hash(pointer, frequency, duration); }
    }

    @Test
    void everyShippedEffectRunsToItsStopOnTheCommandsTheHandlerModels() {
        List<String> failures = new ArrayList<>();
        for (int id = CdFmSmpsLoader.FIRST_SFX; id <= CdFmSmpsLoader.LAST_SFX; id++) {
            Run run = run(id, CdFmSequencerConfig.standalone(), 2048);
            if (run.writes == 0) failures.add(String.format("%02X wrote nothing", id));
            if (!run.finished) failures.add(String.format("%02X did not stop in 2048 ticks", id));
        }
        // D5's header declares two tracks for one (SFX/D5.asm:5, FixBugs=0): its second
        // "track" is the next six bytes, which point FM1 into driver code. Only D5 may differ.
        failures.removeIf(f -> f.startsWith("D5"));
        assertEquals(List.of(), failures);
    }

    @Test
    void nativeDriverStateMatchesTheSequencerForEverySoundTestEffect() throws IOException {
        Native capture = Native.load();
        // Sound Test .IDs (Sound Test.asm:309-336): indices 0-14.
        int[] ids = {0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9B, 0x9D, 0x9E};
        int compared = 0;
        int admissionsRejected = 0;
        for (int p = 0; p < capture.plays.size(); p++) {
            int[] play = capture.plays.get(p);
            int frame = play[0];
            int window = p + 1 < capture.plays.size() ? capture.plays.get(p + 1)[0] - frame : 85;
            int id = ids[play[1]];
            CdFmSfxData data = sfx(id);
            // SelectFM writes FMDrvQueue2, the driver's first priority slot (Sound Test.asm:300-301).
            var dispatch = CdFmSoundRequestService.service(new CdFmSoundRequestService.Snapshot(
                    0, 0, 0, 0x80, id, 0, 0, capture.curPriority(frame - 1)), assets::nativePriorityByte);
            boolean modelAdmits = dispatch.consequence() instanceof com.openggf.audio.rewind.AudioCommand.PlaySfx sfx
                    && sfx.sfxId() == id;
            var first = data.getTrackEntries().getFirst();
            boolean nativeAdmits = !capture.parses(frame, slot(first.channelMask()), window, first.pointer()).isEmpty();
            assertEquals(nativeAdmits, modelAdmits, String.format("admission of %02X at frame %d", id, frame));
            if (!nativeAdmits) {
                admissionsRejected++;
                continue;
            }

            for (int track = 0; track < data.getTrackEntries().size(); track++) {
                var entry = data.getTrackEntries().get(track);
                List<Parse> expected = capture.parses(frame, slot(entry.channelMask()), window, entry.pointer());
                List<Parse> actual = run(id, CdFmSequencerConfig.standalone(), 85).parses.get(track);
                if (id == 0x95 && frame == 2600) {
                    // The left-hand ring: TrkCmd_SwapRingChn lets this tick play the right-hand
                    // effect and queues FM_RINGL (FM/_Driver.asm:1741-1760), which the request
                    // service admits at the next tick from scratch on FM4.
                    CdFmDriverState driver = new CdFmDriverState();
                    driver.setRingChannel(0x80);
                    List<Parse> right = run(id, CdFmSequencerConfig.create(new CdFmCoordFlagHandler(driver)), 1).parses.get(track);
                    assertEquals(expected.getFirst(), right.getFirst(), "native first tick is the right-hand ring");
                    assertEquals(CdFmCoordFlagHandler.RING_LEFT, driver.trackQueued(), "FM_RINGL is queued");
                    expected = expected.subList(1, expected.size());
                    actual = run(CdFmCoordFlagHandler.RING_LEFT, CdFmSequencerConfig.standalone(), 85).parses.get(track);
                }
                assertFalse(expected.isEmpty(), String.format("native %02X at %d parsed nothing", id, frame));
                List<Parse> visible = visibleAtFrameEnds(actual, expected);
                assertEquals(expected, visible,
                        String.format("effect %02X track %d requested at frame %d", id, track, frame));
                // One tick per frame, give or take the tick that straddles a frame end
                // (59.45 ticks for 59.92 frames per second).
                for (int k = 1; k < expected.size(); k++) {
                    int nativeFrames = expected.get(k).at() - expected.get(k - 1).at();
                    int ticks = visible.get(k).at() - visible.get(k - 1).at();
                    assertTrue(Math.abs(nativeFrames - ticks) <= 1, String.format(
                            "effect %02X parse %d: %d native frames, %d ticks", id, k, nativeFrames, ticks));
                }
                compared += expected.size();
            }
        }
        assertTrue(compared > 40, "compared " + compared + " parses");
        assertEquals(1, admissionsRejected, "9D at frame 3320 loses to 9B's 7Ah");
    }

    /** The comparison fails when the configuration loses one cited driver difference. */
    @Test
    void nativeComparisonRejectsTheSonic3kFrequencyBase() throws IOException {
        Native capture = Native.load();
        CdFmSfxData data = sfx(0x92);
        List<Parse> expected = capture.parses(2330, 0, 85, data.getTrackEntries().getFirst().pointer());
        SmpsSequencer broken = new SmpsSequencer(new OffsetData(data), NO_DAC, new Recorder(),
                () -> { }, CdFmSequencerConfig.standalone());
        List<Parse> actual = new ArrayList<>();
        SmpsSequencer.Track t = broken.getTracks().getFirst();
        for (int tick = 0; tick < 85 && t.active; tick++) {
            int before = t.pos;
            broken.serviceOuterFrame();
            if (t.pos != before) actual.add(new Parse(t.pos, (t.baseBlock << 11) | t.baseFnum, t.duration, tick));
        }
        assertNotEquals(expected, actual.subList(0, Math.min(actual.size(), expected.size())));
    }

    /**
     * The sequencer's parses as frame-end dumps can show them. A tick still running when a
     * frame ends finishes in the next frame together with the following tick, so a note
     * lasting one tick can be replaced before any dump sees it. Such a parse is dropped only
     * where the native sequence goes straight to the parse after it.
     */
    private static List<Parse> visibleAtFrameEnds(List<Parse> actual, List<Parse> expected) {
        List<Parse> out = new ArrayList<>();
        int j = 0;
        for (Parse e : expected) {
            if (j + 1 < actual.size() && !actual.get(j).equals(e) && actual.get(j).duration() == 1
                    && actual.get(j + 1).equals(e)) {
                j++;
            }
            if (j < actual.size()) out.add(actual.get(j++));
        }
        return out;
    }

    private int idOfPointer(int pointer) {
        int best = -1;
        int bestHeader = -1;
        for (int id = CdFmSmpsLoader.FIRST_SFX; id <= CdFmSmpsLoader.LAST_SFX; id++) {
            int header = assets.sfxHeaderAddress(id);
            if (header < pointer && header > bestHeader) {
                best = id;
                bestHeader = header;
            }
        }
        return best;
    }

    /** {@code GetSFXTrackPtr}: channels 4-6 drop one to index the six track slots (FM/_Driver.asm:877-892). */
    private static int slot(int channel) {
        return (channel & 4) != 0 ? channel - 1 : channel;
    }

    private static CdFmSfxData sfx(int id) {
        return new CdFmSfxData(bank, id, assets.sfxHeaderAddress(id));
    }

    private static final class Run {
        final List<List<Parse>> parses = new ArrayList<>();
        int writes;
        boolean finished;
    }

    private static Run run(int id, SmpsSequencerConfig config, int ticks) {
        Recorder recorder = new Recorder();
        SmpsSequencer sequencer = new SmpsSequencer(sfx(id), NO_DAC, recorder, () -> { }, config);
        Run run = new Run();
        List<SmpsSequencer.Track> tracks = sequencer.getTracks();
        // Track order is the driver's RAM walk; keep the header's order for the caller. A
        // header channel maps to the sequencer's linear FM channel like GetSFXTrackPtr.
        List<SmpsSequencer.Track> byHeader = new ArrayList<>();
        for (var entry : sfx(id).getTrackEntries()) {
            tracks.stream().filter(t -> t.channelId == slot(entry.channelMask()) && !byHeader.contains(t))
                    .findFirst().ifPresent(byHeader::add);
        }
        for (int i = 0; i < byHeader.size(); i++) run.parses.add(new ArrayList<>());
        int[] last = byHeader.stream().mapToInt(t -> t.pos).toArray();
        for (int tick = 0; tick < ticks && byHeader.stream().anyMatch(t -> t.active); tick++) {
            sequencer.serviceOuterFrame();
            for (int i = 0; i < byHeader.size(); i++) {
                SmpsSequencer.Track t = byHeader.get(i);
                if (t.active && t.pos != last[i]) {
                    run.parses.get(i).add(new Parse(t.pos, (t.baseBlock << 11) | t.baseFnum, t.duration, tick));
                    last[i] = t.pos;
                }
            }
        }
        run.finished = byHeader.stream().noneMatch(t -> t.active);
        run.writes = recorder.writes;
        return run;
    }

    /** The driver variables 1C00h-1D5Fh after every native frame. */
    private record Native(byte[] ram, List<int[]> plays) {
        private static final int BYTES = 0x160;
        private static final int TRACKS = 0x40;
        private static final int TRACK_BYTES = 0x30;

        static Native load() throws IOException {
            Path dir = CdAudioTestMedia.nativeDir("aud1-fixtures/soundtest-fm");
            byte[] ram = Files.readAllBytes(dir.resolve("z80-1C00-1D60.bin"));
            String json = Files.readString(dir.resolve("soundtest-fm-plays.json"));
            List<int[]> plays = new ArrayList<>();
            var m = java.util.regex.Pattern.compile("\\[(\\d+), (\\d+)]").matcher(json);
            while (m.find()) plays.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
            return new Native(ram, plays);
        }

        /**
         * A new data pointer marks a parse (_Variables.inc:41-75 for the track layout). The
         * Z80 runs on while the frame ends, so a dump can fall inside a tick: between
         * {@code PlaySFX}'s initialisation and the first parse the pointer is the track's
         * start, and the counter may already be decremented. The loaded duration
         * ({@code ftrkDuration}) is compared, not the counter.
         */
        List<Parse> parses(int requestFrame, int slot, int frames, int trackStart) {
            List<Parse> out = new ArrayList<>();
            int last = word(requestFrame - 1, slot, 3);
            for (int f = requestFrame; f < requestFrame + frames; f++) {
                int flags = u8(f, slot, 0);
                int pointer = word(f, slot, 3);
                if ((flags & 0x80) == 0) {
                    if (out.isEmpty()) continue; // not started yet: the Z80 admits at its next tick
                    break;
                }
                if (pointer != last && pointer != trackStart) {
                    out.add(new Parse(pointer, word(f, slot, 0x0D), u8(f, slot, 0x0C), f));
                    last = pointer;
                }
            }
            return out;
        }

        /** {@code curPriority} (1C18h, _Variables.inc:82). */
        int curPriority(int frame) {
            return ram[(frame - 1) * BYTES + 0x18] & 0xFF;
        }

        private int u8(int frame, int slot, int offset) {
            return ram[(frame - 1) * BYTES + TRACKS + slot * TRACK_BYTES + offset] & 0xFF;
        }

        private int word(int frame, int slot, int offset) {
            return u8(frame, slot, offset) | (u8(frame, slot, offset + 1) << 8);
        }
    }

    /** Write target that counts chip writes. */
    private static final class Recorder implements SmpsLogicalWriteTarget {
        int writes;
        @Override public void writeFm(Object source, int port, int reg, int val) { writes++; }
        @Override public void writePsg(Object source, int val) { writes++; }
        @Override public void setInstrument(Object source, int channelId, byte[] voice) { }
        @Override public void playDac(Object source, int note) { }
        @Override public void stopDac(Object source) { }
        @Override public void setDacData(DacData data) { }
        @Override public void setFmMute(int channel, boolean mute) { }
        @Override public void setPsgMute(int channel, boolean mute) { }
        @Override public void setDacInterpolate(boolean interpolate) { }
        @Override public void silenceAll() { }
        @Override public void selectDac(SmpsSourceDescriptor source, DacData data) { }
    }

    /** The effect read with Sonic 3&amp;K's note base shifted by one semitone. */
    private static final class OffsetData extends com.openggf.audio.smps.AbstractSmpsData
            implements com.openggf.audio.smps.SmpsSfxData {
        private final CdFmSfxData inner;
        OffsetData(CdFmSfxData inner) {
            super(bank, 0, true);
            this.inner = Objects.requireNonNull(inner);
            setId(inner.getId());
            voicePtr = inner.getVoicePtr();
            dividingTiming = inner.getDividingTiming();
        }
        @Override protected void parseHeader() { }
        @Override public byte[] getVoice(int voiceId) { return inner.getVoice(voiceId); }
        @Override public byte[] getPsgEnvelope(int id) { return null; }
        @Override public int read16(int offset) { return inner.read16(offset); }
        @Override public int getBaseNoteOffset() { return 1; }
        @Override public int getTickMultiplier() { return inner.getTickMultiplier(); }
        @Override public List<? extends SmpsSfxTrack> getTrackEntries() { return inner.getTrackEntries(); }
    }
}
