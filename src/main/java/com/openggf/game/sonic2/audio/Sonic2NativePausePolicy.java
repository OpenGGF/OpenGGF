package com.openggf.game.sonic2.audio;

import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.session.SmpsChipWrite;
import com.openggf.audio.session.SmpsNativePausePolicy;
import com.openggf.audio.session.SmpsWriteProgram;
import com.openggf.audio.smps.SmpsSequencer.TrackType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shipped S2 Z80 pause writes, projected from production-owned logical state.
 * zPauseMusic/zResumeTrack (s2.sounddriver.asm:1422-1490) preserve track RAM;
 * this policy neither advances tracks nor restores a saved music override.
 * Origin: native audio evidence gap closure, 2026-10-22.
 */
public final class Sonic2NativePausePolicy implements SmpsNativePausePolicy {
    public static final Sonic2NativePausePolicy INSTANCE = new Sonic2NativePausePolicy();

    private static final int VOICE_SIZE = 25;
    private static final int[] CARRIER_MASKS = {0x08, 0x08, 0x08, 0x08, 0x0C, 0x0E, 0x0E, 0x0F};
    private static final int[] PARAMETER_REGISTERS = {0x30, 0x50, 0x60, 0x70, 0x80};
    private static final SmpsWriteProgram PAUSE = pauseProgram();

    private Sonic2NativePausePolicy() { }

    @Override
    public void requireSupported() { }

    @Override
    public boolean resumeContinuesService() {
        // zVInt returns to zUpdateDAC after BOTH pause and resume (sd:403-407).
        return false;
    }

    @Override
    public SmpsWriteProgram pause() {
        return PAUSE;
    }

    @Override
    public SmpsWriteProgram resume(SmpsDriverSnapshot logical) {
        Objects.requireNonNull(logical, "logical");
        RestoreVoice[] music = new RestoreVoice[7]; // DAC, FM1..FM6.
        RestoreVoice[] sfx = new RestoreVoice[3]; // FM3, FM4, FM5.
        for (SmpsDriverSnapshot.SequencerEntry entry : logical.sequencers()) {
            for (SmpsTrackSnapshot track : entry.snapshot().tracks()) {
                if (!track.active() || track.overridden() || track.type() == TrackType.PSG) {
                    continue;
                }
                RestoreVoice[] slots = entry.sfx() ? sfx : music;
                int slot = nativeSlot(entry.sfx(), track);
                if (slots[slot] != null) {
                    throw new IllegalStateException("ambiguous S2 native resume slot: "
                            + (entry.sfx() ? "SFX " : "music ") + slot);
                }
                slots[slot] = new RestoreVoice(track, resumeVoice(entry, track));
            }
        }

        // Validate every eligible owner/voice before constructing any writes.
        List<SmpsChipWrite> writes = new ArrayList<>();
        appendVoices(writes, music);
        appendVoices(writes, sfx);
        return new SmpsWriteProgram(writes);
    }

    private static int nativeSlot(boolean sfx, SmpsTrackSnapshot track) {
        if (track.type() == TrackType.DAC && !sfx && track.channelId() == 5) {
            return 0;
        }
        if (track.type() == TrackType.FM) {
            int channel = track.channelId();
            if (sfx && channel >= 2 && channel <= 4) return channel - 2;
            if (!sfx && channel >= 0 && channel < 6) return channel + 1;
        }
        throw new IllegalStateException("track does not identify a shipped S2 native resume slot");
    }

    private static byte[] resumeVoice(SmpsDriverSnapshot.SequencerEntry entry, SmpsTrackSnapshot track) {
        byte[] voice = track.voiceData();
        if (voice == null) {
            // Native track RAM starts with VoiceIndex zero, even before a
            // cfSetVoice. Java's DAC has no FM cache; an FM track can also
            // reach pause before uploading its first voice. The selected
            // ROM-backed program still owns the default/current instrument.
            if (track.voiceId() < 0 || track.voiceId() > 0xFF) {
                throw new IllegalStateException("invalid S2 DAC resume voice index");
            }
            voice = entry.smpsData().getVoice(track.voiceId());
        }
        if (voice == null || voice.length != VOICE_SIZE) {
            throw new IllegalStateException("active S2 native resume track has no complete 25-byte voice");
        }
        return voice.clone();
    }

    private static void appendVoices(List<SmpsChipWrite> writes, RestoreVoice[] slots) {
        for (RestoreVoice restore : slots) {
            if (restore == null) continue;
            SmpsTrackSnapshot track = restore.track();
            byte[] voice = restore.voice();
            int port = track.channelId() < 3 ? 0 : 1;
            int channel = track.channelId() % 3;
            int pan = (track.pan() & 0xC0) | ((track.ams() & 3) << 4) | (track.fms() & 7);

            // OptimiseDriver=0 zResumeTrack writes pan before cfSetVoiceCont;
            // zSetVoice writes it again after the operator parameter groups.
            writes.add(new SmpsChipWrite.Ym2612(port, 0xB4 + channel, pan));
            writes.add(new SmpsChipWrite.Ym2612(port, 0xB0 + channel, voice[0] & 0xFF));
            for (int group = 0; group < PARAMETER_REGISTERS.length; group++) {
                for (int operator = 0; operator < 4; operator++) {
                    writes.add(new SmpsChipWrite.Ym2612(port,
                            PARAMETER_REGISTERS[group] + operator * 4 + channel,
                            voice[1 + group * 4 + operator] & 0xFF));
                }
            }
            writes.add(new SmpsChipWrite.Ym2612(port, 0xB4 + channel, pan));
            int carriers = CARRIER_MASKS[voice[0] & 7];
            for (int operator = 0; operator < 4; operator++) {
                int tl = voice[21 + operator] & 0xFF;
                if ((carriers & (1 << operator)) != 0) {
                    // FixDriverBugs=0: native zSetFMTLs adds in eight bits,
                    // without forcing bit 7, clamping or masking to seven bits.
                    tl = (tl + track.volumeOffset()) & 0xFF;
                }
                writes.add(new SmpsChipWrite.Ym2612(port, 0x40 + operator * 4 + channel, tl));
            }
        }
    }

    private static SmpsWriteProgram pauseProgram() {
        List<SmpsChipWrite> writes = new ArrayList<>(202);
        // zFMSilenceAll (sd:2518-2540): B counts down 3..1, C=B-1.
        for (int channel : new int[] {2, 6, 1, 5, 0, 4}) {
            writes.add(new SmpsChipWrite.Ym2612(0, 0x28, channel));
        }
        for (int register = 0x30; register < 0x90; register++) {
            writes.add(new SmpsChipWrite.Ym2612(0, register, 0xFF));
            writes.add(new SmpsChipWrite.Ym2612(1, register, 0xFF));
        }
        writes.addAll(SmpsWriteProgram.SILENCE_ALL_PSG.writes());
        return new SmpsWriteProgram(writes);
    }

    private record RestoreVoice(SmpsTrackSnapshot track, byte[] voice) { }
}
