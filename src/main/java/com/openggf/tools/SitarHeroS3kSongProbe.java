package com.openggf.tools;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.*;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reports native ROM stream control flow, attacks, tempo changes and natural stops.
 * Input: absolute locked-on S3K ROM path, optional hexadecimal music IDs; no audio files.
 * Origin: 2026-10-07 Sitar Hero full S3K song catalogue, shared contract cabd66f44.
 * The production sequencer executes shipped-ROM bytes with its S3K coordination handler.
 * All executed F6 jumps are reported as candidates; F7 pattern loops are not song form.
 * Compare every active channel and the owning score, including never-returning calls.
 */
public final class SitarHeroS3kSongProbe {
    private SitarHeroS3kSongProbe() { }

    public record Boundary(int frame, int units, int source, int target, int returnDepth) { }
    public record Tempo(int frame, int units, int value) { }
    public record Part(String kind, int channel, int start, int stopFrame, int stopUnits,
                       int attacks, int minimumNote, int maximumNote, boolean noise, long signature,
                       List<Integer> firstDacUnits, List<Boundary> jumps) { }
    public record Result(int musicId, int romOffset, int headerTempo, int divider,
                         int naturalEndFrame, List<Tempo> tempos, List<Part> parts) { }

    private static final class State {
        final List<Boundary> jumps = new ArrayList<>();
        final List<Integer> firstAttacks = new ArrayList<>();
        final int start;
        int stopFrame = -1, stopUnits = -1, attacks, minimum = 256, maximum = -1;
        boolean noise;
        long signature = 0xcbf29ce484222325L;
        State(int start) { this.start = start; }
    }

    /** Executes native service frames without rendering PCM or altering track state. */
    public static Result inspect(Sonic3kSmpsLoader loader, DacData dac, int id, int frames) throws Exception {
        AbstractSmpsData data = Objects.requireNonNull(loader.loadMusic(id), "Missing ROM music " + id);
        return inspectData(loader, dac, id, frames, data);
    }

    /** Native bank address space also admits real cross-song calls before the header. */
    public static Result inspectBank(Sonic3kSmpsLoader loader, DacData dac, int id, int frames) throws Exception {
        var data = (Sonic3kSmpsData) Objects.requireNonNull(loader.loadMusic(id), "Missing ROM music " + id);
        return inspectData(loader, dac, id, frames, new BankProgram(data));
    }

    private static final class BankProgram extends AbstractSmpsData {
        private final Sonic3kSmpsData source;
        BankProgram(Sonic3kSmpsData source) {
            super(Objects.requireNonNull(source.getBankData(), "Missing native ROM bank"), source.getBankZ80Base(), true);
            this.source = source;
            voicePtr = source.getVoicePtr(); channels = source.getChannels(); psgChannels = source.getPsgChannels();
            dividingTiming = source.getDividingTiming(); tempo = source.getTempo(); dacPointer = source.getDacPointer();
            fmPointers = source.getFmPointers(); fmKeyOffsets = source.getFmKeyOffsets(); fmVolumeOffsets = source.getFmVolumeOffsets();
            psgPointers = source.getPsgPointers(); psgKeyOffsets = source.getPsgKeyOffsets(); psgVolumeOffsets = source.getPsgVolumeOffsets();
            psgModEnvs = source.getPsgModEnvs(); psgInstruments = source.getPsgInstruments(); id = source.getId();
        }
        protected void parseHeader() { }
        public byte[] getVoice(int voice) { return source.getVoice(voice); }
        public byte[] getPsgEnvelope(int envelope) { return source.getPsgEnvelope(envelope); }
        public byte[] getModEnvelope(int envelope) { return source.getModEnvelope(envelope); }
        public int getBaseNoteOffset() { return source.getBaseNoteOffset(); }
        public int read16(int offset) { return (data[offset] & 255) | ((data[offset + 1] & 255) << 8); }
    }

    private static Result inspectData(Sonic3kSmpsLoader loader, DacData dac, int id, int frames, AbstractSmpsData data) throws Exception {
        var states = new IdentityHashMap<SmpsSequencer.Track, State>();
        var tempos = new ArrayList<Tempo>();
        int[] clock = {0, 0}; // service frame and progressed duration units
        var nativeHandler = new Sonic3kCoordFlagHandler();
        var observingHandler = new CoordFlagHandler() {
            public int flagParamLength(int cmd) { return nativeHandler.flagParamLength(cmd); }
            public boolean handleFlag(CoordFlagContext ctx, SmpsSequencer.Track track, int cmd) {
                State state = states.get(track);
                int source = track.pos - 1;
                int target = cmd == 0xF6 ? data.read16(track.pos) - data.getZ80StartAddress() : -1;
                if (cmd == 0xF6 && state.jumps.stream().filter(boundary -> boundary.source() == source).count() < 4) {
                    state.jumps.add(new Boundary(clock[0], clock[1], source, target,
                            track.returnSp));
                }
                if (cmd == 0xFF && data.dataByteAt(track.pos) == 0)
                    tempos.add(new Tempo(clock[0], clock[1], data.dataByteAt(track.pos + 1) & 255));
                boolean handled = nativeHandler.handleFlag(ctx, track, cmd);
                return handled;
            }
        };
        var sequence = new SmpsSequencer(data, dac, new SmpsDriver(), () -> { },
                Sonic3kSmpsSequencerConfig.create(observingHandler));
        for (var track : sequence.getTracks()) {
            states.put(track, new State(track.pos));
        }
        sequence.setNoteListener(new SmpsNoteListener() {
            public void release(SmpsSequencer.Track track) { }
            public void attack(SmpsSequencer.Track track) {
                State state = states.get(track);
                for (int value : new int[] {clock[0], track.note, track.keyOffset, track.rawDuration,
                        track.voiceId, track.volumeOffset, track.pan, Arrays.hashCode(track.voiceData),
                        track.instrumentId}) state.signature = (state.signature ^ value) * 0x100000001b3L;
                state.attacks++; state.minimum = Math.min(state.minimum, track.note);
                state.maximum = Math.max(state.maximum, track.note);
                state.noise |= track.type == SmpsSequencer.TrackType.PSG && track.noiseMode;
                if (track.type == SmpsSequencer.TrackType.DAC && state.firstAttacks.size() < 8)
                    state.firstAttacks.add(clock[1]);
            }
        });
        Field accumulator = SmpsSequencer.class.getDeclaredField("tempoAccumulator"); accumulator.setAccessible(true);
        Field weight = SmpsSequencer.class.getDeclaredField("tempoWeight"); weight.setAccessible(true);
        int naturalEnd = 0;
        for (int frame = 0; frame < frames; frame++) {
            clock[0] = frame;
            if (frame > 0 && accumulator.getInt(sequence) + weight.getInt(sequence) < 256) clock[1]++;
            sequence.serviceOuterFrame();
            for (var track : sequence.getTracks()) {
                State state = states.get(track);
                if (!track.active && state.stopFrame < 0) { state.stopFrame = frame; state.stopUnits = clock[1]; }
            }
            if (sequence.getTracks().stream().noneMatch(track -> track.active)) { naturalEnd = frame + 1; break; }
        }
        var parts = new ArrayList<Part>();
        for (var track : sequence.getTracks()) {
            State state = states.get(track);
            var first = new ArrayList<Integer>();
            for (int i = 1; i < state.firstAttacks.size(); i++) first.add(state.firstAttacks.get(i) - state.firstAttacks.get(i - 1));
            parts.add(new Part(track.type.name(), track.channelId, state.start, state.stopFrame,
                    state.stopUnits, state.attacks, state.minimum, state.maximum, state.noise, state.signature,
                    List.copyOf(first), List.copyOf(state.jumps)));
        }
        return new Result(id, loader.findMusicOffset(id), data.getTempo(), data.getDividingTiming(),
                naturalEnd, List.copyOf(tempos), List.copyOf(parts));
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Usage: ROM [hex music IDs ...]");
        Logger.getLogger("").setLevel(Level.SEVERE);
        try (var rom = new Rom()) {
            rom.open(args[0]); var loader = new Sonic3kSmpsLoader(rom); var dac = loader.loadDacData();
            var ids = new ArrayList<Integer>();
            if (args.length == 1) { for (int id = 1; id <= 0x33; id++) ids.add(id); for (int id = 1; id <= 0x32; id++) ids.add(0x100 | id); }
            else for (int i = 1; i < args.length; i++) ids.add(Integer.parseInt(args[i].replace("0x", ""), 16));
            for (int id : ids) System.out.println(inspectBank(loader, dac, id, 36_001));
        }
    }
}
