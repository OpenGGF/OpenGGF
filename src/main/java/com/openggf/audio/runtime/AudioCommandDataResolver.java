package com.openggf.audio.runtime;

import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencerConfig;

/**
 * Loads the data behind an {@link AudioCommand} on behalf of the reverse-
 * resynth worker. Replaces the live backend's direct
 * {@code smpsLoader} / {@code donorLoaders} / {@code dacData} field access
 * with an explicit dependency so the worker can replay timeline commands
 * against its private {@code SmpsPresentationState} without touching live
 * backend state.
 *
 * <p>Built once at {@code AudioManager.beginReverseAudioPresentation} from
 * the audio profile, loaders, and donor maps that were active when the
 * held-rewind session began. Treated as immutable for the lifetime of the
 * session.
 *
 * <p>Routes that have no SMPS data — {@code MusicRoute.FALLBACK_WAV},
 * {@code SfxRoute.FALLBACK_NAME}, {@code SfxRoute.RING_RESOLVED} — return
 * null, signalling the worker to skip the command (matches the existing
 * spec edge case 9 for WAV-fallback SFX).
 */
public interface AudioCommandDataResolver {

    /**
     * @return resolved data for {@code command}, or null when the command
     *         has no replayable SMPS data (FALLBACK_WAV, SYSTEM_COMMAND,
     *         or a missing loader/dacData entry).
     */
    MusicData resolveMusic(AudioCommand.PlayMusic command);

    /**
     * @return resolved data for {@code command}, or null when the command
     *         has no replayable SMPS data (FALLBACK_NAME, RING_RESOLVED,
     *         or a missing loader/dacData entry).
     */
    SfxData resolveSfx(AudioCommand.PlaySfx command);

    /**
     * Resolved music command data ready to feed into
     * {@code SmpsPresentationReplay.applyToMusicBase}.
     *
     * @param descriptor the {@link AudioSourceDescriptor} hint that
     *                   matches the route (e.g. base vs donor); passed
     *                   into the replay helper so the sequencer carries
     *                   the right source descriptor.
     */
    record MusicData(
            AbstractSmpsData data,
            DacData dacData,
            SmpsSequencerConfig config,
            AudioSourceDescriptor descriptor) {
    }

    /**
     * Resolved SFX command data ready to feed into
     * {@code SmpsPresentationReplay.applyToSfx}.
     */
    record SfxData(
            AbstractSmpsData data,
            DacData dacData,
            SmpsSequencerConfig config) {
    }
}
