package com.openggf.tools.audio.parity.s3k;

import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.rewind.SmpsTrackSnapshot;
import com.openggf.audio.smps.SmpsSequencer.TrackType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts an OpenGGF S3K driver snapshot into the driver-RAM-shaped oracle
 * vocabulary of {@link S3kAudioTick}.
 *
 * <p>Field mappings are the ones inventoried by {@link S3kAudioFieldRegistry};
 * values are re-expressed as the ROM's raw bytes (unsigned) so both sides of
 * the comparison share one coordinate system. Fields the engine does not
 * model are emitted as {@code null} and stay DIAGNOSTIC in the registry.
 */
public final class S3kAudioStateNormalizer {
    /** {@code Sonic3kAudioProfile.getSpeedMultiplierValue()}'s ROM value (zTempoSpeedup = 8). */
    private static final int SPEED_SHOES_TEMPO = 8;

    private S3kAudioStateNormalizer() {
    }

    public static S3kAudioTick normalize(int ordinal, List<Integer> mailbox,
            SmpsDriverSnapshot driver) {
        Objects.requireNonNull(driver, "driver");
        SmpsSequencerSnapshot music = null;
        List<SmpsSequencerSnapshot> sfx = new ArrayList<>();
        for (SmpsDriverSnapshot.SequencerEntry entry : driver.sequencers()) {
            if (entry.sfx()) {
                sfx.add(entry.snapshot());
            } else if (music == null) {
                music = entry.snapshot();
            }
        }

        List<S3kAudioTrackState> tracks = new ArrayList<>(S3kAudioParitySchema.ROLES.size());
        for (int index = 0; index < S3kAudioParitySchema.MUSIC_ROLE_COUNT; index++) {
            tracks.add(musicSlot(index, music));
        }
        for (int index = 0; index < S3kAudioParitySchema.SFX_ROLE_COUNT; index++) {
            tracks.add(sfxSlot(index, sfx));
        }
        return new S3kAudioTick(ordinal, false, mailbox, global(music, driver), tracks, List.of());
    }

    private static S3kAudioTick.GlobalState global(
            SmpsSequencerSnapshot music, SmpsDriverSnapshot driver) {
        if (music == null) {
            return new S3kAudioTick.GlobalState(0, 0, 0, 0, null, null, null, null, null, null,
                    null, null, driver.palUpdateCounter());
        }
        return new S3kAudioTick.GlobalState(
                music.tempoWeight() & 0xff,
                music.tempoAccumulator() & 0xff,
                music.speedShoes() ? SPEED_SHOES_TEMPO : 0,
                music.speedupTimeout() & 0xff,
                null, null, null, null, null, null, null, null,
                driver.palUpdateCounter());
    }

    /**
     * Music slot order is the ROM's: FM6/DAC, FM1..FM5, PSG1..PSG3
     * (zTracksStart, D:176-186).
     */
    private static S3kAudioTrackState musicSlot(int slot, SmpsSequencerSnapshot music) {
        String role = S3kAudioParitySchema.ROLES.get(slot);
        if (music == null) {
            return S3kAudioTrackState.idle(role);
        }
        for (SmpsTrackSnapshot track : music.tracks()) {
            if (matchesMusicSlot(slot, track)) {
                return track.active() ? normalizeTrack(role, track) : S3kAudioTrackState.idle(role);
            }
        }
        return S3kAudioTrackState.idle(role);
    }

    private static boolean matchesMusicSlot(int slot, SmpsTrackSnapshot track) {
        TrackType type = track.type();
        return switch (slot) {
            case 0 -> type == TrackType.DAC;
            case 1, 2, 3, 4, 5 -> type == TrackType.FM && track.channelId() == slot - 1;
            default -> type == TrackType.PSG && track.channelId() == slot - 6;
        };
    }

    /** SFX slot order: FM3..FM6, PSG1..PSG3 (zTracksSFXStart, D:190-206). */
    private static S3kAudioTrackState sfxSlot(int slot, List<SmpsSequencerSnapshot> sfx) {
        String role = S3kAudioParitySchema.ROLES.get(S3kAudioParitySchema.MUSIC_ROLE_COUNT + slot);
        S3kAudioTrackState result = S3kAudioTrackState.idle(role);
        for (SmpsSequencerSnapshot sequencer : sfx) {
            for (SmpsTrackSnapshot track : sequencer.tracks()) {
                if (!track.active()) {
                    continue;
                }
                boolean matches = slot < 4
                        ? track.type() == TrackType.FM && track.channelId() == slot + 2
                        : track.type() == TrackType.PSG && track.channelId() == slot - 4;
                if (matches) {
                    // The ROM has one struct per hardware slot; the latest
                    // admitted SFX owns it, and sequencers iterate in
                    // admission order.
                    result = normalizeTrack(role, track);
                }
            }
        }
        return result;
    }

    private static S3kAudioTrackState normalizeTrack(String role, SmpsTrackSnapshot track) {
        boolean psg = track.type() == TrackType.PSG;
        boolean dac = track.type() == TrackType.DAC;
        Integer frequency;
        if (dac) {
            frequency = null; // FreqLow doubles as SavedDAC; mapping not pinned.
        } else if (psg) {
            frequency = track.baseFnum() & 0xffff;
        } else {
            int freqHigh = ((track.baseBlock() & 0x07) << 3) | ((track.baseFnum() >> 8) & 0x07);
            frequency = (freqHigh << 8) | (track.baseFnum() & 0xff);
        }
        Integer amsFmsPan = psg ? null
                : ((track.pan() & 0xc0) | ((track.ams() & 0x03) << 4) | (track.fms() & 0x07));
        return new S3kAudioTrackState(role, true,
                track.overridden(),
                track.tieNext(),
                track.resting(),
                voiceControl(track),
                track.dividingTiming() & 0xff,
                null,
                track.keyOffset() & 0xff,
                track.volumeOffset() & 0xff,
                modulationCtrl(track),
                (psg ? track.instrumentId() : track.voiceId()) & 0xff,
                amsFmsPan,
                track.duration() & 0xff,
                track.scaledDuration() & 0xff,
                frequency,
                track.detune() & 0xff,
                null, null, null);
    }

    /**
     * VoiceControl (offset 01): FM bits 0-1 channel + bit 2 part II; PSG
     * 80h|A0h|C0h; the FM6/DAC slot initialises to 06h (zFMDACInitBytes,
     * D:1899-1907; zPSGInitBytes, D:1913-1916).
     */
    private static Integer voiceControl(SmpsTrackSnapshot track) {
        return switch (track.type()) {
            case DAC -> 0x06;
            case FM -> track.channelId() < 3 ? track.channelId() : track.channelId() + 1;
            case PSG -> 0x80 | (track.channelId() << 5);
        };
    }

    /**
     * ModulationCtrl (offset 07): 0 off, 80h normal modulation, 1..8 mod
     * envelope index+1 (map §2, §7).
     */
    private static Integer modulationCtrl(SmpsTrackSnapshot track) {
        if (track.modEnvId() > 0) {
            return track.modEnvId() & 0xff;
        }
        return track.modEnabled() ? 0x80 : 0;
    }
}
