package com.openggf.mods.code;

import com.openggf.audio.smps.AbstractSmpsData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comparison-only S2 ROM-program oracle. Input is the ROM loader's SMPS program;
 * outputs are driver-unit attacks, top-level jumps, stops and service frames.
 * Origin: 2026-10-07 Sitar Hero S2 full-song catalogue, based on cabd66f44.
 * Implements the native stream grammar, not the production sequencer or PCM.
 */
final class SitarHeroS2NativeProgram {
    record Attack(int unit, int offset, int note, boolean noise) { }
    record Jump(int unit, int target, int firstVisit) { }
    record Track(String kind, int channel, List<Attack> attacks, List<Jump> jumps, int stopUnit) { }
    record Result(int divider, int tempo, List<Track> tracks, Map<Integer, Integer> tempoChanges) {
        /** zBGMLoad seeds TempoTimeout=CurrentTempo; TempoWait adds before walking tracks. */
        int[] serviceFrames(int lastUnit) {
            int[] frames = new int[lastUnit + 1];
            int accumulator = tempo;
            int currentTempo = tempo;
            for (int frame = 0, unit = 0; unit <= lastUnit; frame++) {
                accumulator += currentTempo;
                if (accumulator >= 256) {
                    accumulator &= 255;
                    frames[unit] = frame;
                    currentTempo = tempoChanges.getOrDefault(unit, currentTempo);
                    unit++;
                }
            }
            return frames;
        }
    }

    static Result read(AbstractSmpsData data, int horizon) {
        List<Track> tracks = new ArrayList<>();
        Map<Integer, Integer> changes = new HashMap<>();
        for (int i = 0; i < data.fmPointerCount(); i++) {
            tracks.add(walk(data, i == 0 ? "DAC" : "FM", i == 0 ? 5 : i - 1,
                    data.fmPointerAt(i) - data.getZ80StartAddress(), horizon, changes));
        }
        for (int i = 0; i < data.psgPointerCount(); i++) {
            tracks.add(walk(data, "PSG", i, data.psgPointerAt(i) - data.getZ80StartAddress(), horizon, changes));
        }
        return new Result(data.getDividingTiming(), data.getTempo(), List.copyOf(tracks), Map.copyOf(changes));
    }

    private static Track walk(AbstractSmpsData data, String kind, int channel, int pc,
                              int horizon, Map<Integer, Integer> changes) {
        List<Attack> attacks = new ArrayList<>();
        List<Jump> jumps = new ArrayList<>();
        Map<Integer, Integer> firstVisit = new HashMap<>();
        ArrayDeque<Integer> returns = new ArrayDeque<>();
        int[] loops = new int[8];
        int divider = data.getDividingTiming(), savedDuration = 0, note = 0x80, unit = 0;
        boolean tie = false, noise = false, psgFrequencyValid = true;
        // zGetNextNote / zDACUpdateTrack / zSetDuration: durations are bytes;
        // DAC ignores the melodic no-attack flag. Calls/loops retain SavedDuration.
        // zInitMusicPlayback clears Freq to 0000. Only an explicit PSG rest
        // installs FFFF; note fill silences without changing that saved word.
        for (int commands = 0; commands < 1_000_000 && unit <= horizon; commands++) {
            firstVisit.putIfAbsent(pc, unit);
            int offset = pc;
            int value = u8(data, pc++);
            if (value < 0xE0) {
                boolean noteByte = value >= 0x80;
                if (noteByte) {
                    note = value;
                    if (kind.equals("PSG")) psgFrequencyValid = note != 0x80;
                    if (u8(data, pc) < 0x80) savedDuration = u8(data, pc++) * divider & 255;
                } else savedDuration = value * divider & 255;
                // zSetDuration scales a supplied byte once. zFinishTrackUpdate
                // and the DAC note-without-duration branch reuse that saved byte
                // even when E5 has changed TempoDivider in between.
                int timeout = savedDuration == 0 ? 256 : savedDuration;
                // zPSGDoNext clears rest on duration-only data, but its note-on
                // guard tests FreqHigh bit 7 and restores rest for saved FFFF.
                // FM's rest stores 0000 and has no equivalent frequency guard.
                if ((note != 0x80 || !noteByte && !kind.equals("DAC"))
                        && (!kind.equals("PSG") || psgFrequencyValid) && (!tie || kind.equals("DAC")))
                    attacks.add(new Attack(unit, pc, note, noise)); // pointer after note+duration
                unit += timeout;
                tie = false;
                continue;
            }
            switch (value) {
                case 0xE3 -> pc = returns.pop();
                case 0xE4, 0xF2 -> {
                    return new Track(kind, channel, List.copyOf(attacks), List.copyOf(jumps), unit);
                }
                case 0xE7 -> tie = true;
                case 0xEA -> {
                    int tempo = u8(data, pc++);
                    Integer prior = changes.putIfAbsent(unit, tempo);
                    if (prior != null && prior != tempo) throw new IllegalStateException("Conflicting native tempos");
                }
                case 0xE5 -> divider = u8(data, pc++);
                case 0xEB -> throw new IllegalStateException("Global divider change requires a merged native walk");
                case 0xF3 -> noise = u8(data, pc++) != 0;
                case 0xF6 -> {
                    int target = pointer(data, pc);
                    if (returns.isEmpty() && firstVisit.containsKey(target) && unit > firstVisit.get(target))
                        jumps.add(new Jump(unit, target, firstVisit.get(target)));
                    pc = target;
                }
                case 0xF7 -> {
                    int index = u8(data, pc++), count = u8(data, pc++);
                    int target = pointer(data, pc); pc += 2;
                    // zLoop: initialize a zero counter, then decrement and branch.
                    loops[index] = ((loops[index] == 0 ? count : loops[index]) - 1) & 255;
                    if (loops[index] != 0) pc = target;
                }
                case 0xF8 -> {
                    int target = pointer(data, pc); returns.push(pc + 2); pc = target;
                }
                case 0xF0 -> pc += 4;
                case 0xF1, 0xF4 -> { }
                case 0xE0, 0xE1, 0xE2, 0xE6, 0xE8, 0xE9, 0xEC, 0xEF, 0xF5 -> pc++;
                default -> throw new IllegalStateException("Unhandled native command %02X at %04X".formatted(value, offset));
            }
        }
        if (unit <= horizon) throw new IllegalStateException("Native control flow failed to advance");
        return new Track(kind, channel, List.copyOf(attacks), List.copyOf(jumps), -1);
    }

    private static int pointer(AbstractSmpsData data, int pc) {
        return (u8(data, pc) | u8(data, pc + 1) << 8) - data.getZ80StartAddress();
    }
    private static int u8(AbstractSmpsData data, int pc) { return data.dataByteAt(pc) & 255; }
}
