package com.openggf.audio.driver;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;

/**
 * Stateless replay helper for SMPS presentation logic. Mutates a
 * {@link SmpsPresentationState} in response to SMPS commands; never touches
 * OpenAL sources, runtime stream binding, or any external state.
 *
 * <p>Two callers share this code:
 * <ul>
 *   <li>The live LWJGL backend: holds one shared {@link SmpsPresentationState}
 *       and wraps replay calls with OpenAL/runtime side effects (stream
 *       binding, source management).</li>
 *   <li>The reverse-resynth worker (planned): holds a private
 *       {@link SmpsPresentationState} per session and runs replay against the
 *       frozen session's timeline, producing PCM via the resulting drivers.
 *       It performs no OpenAL or runtime work.</li>
 * </ul>
 *
 * <p>The first extracted operation is {@link #applyToSfx} (SFX SMPS routes).
 * Additional SMPS operations (music, fade-out, restore, override stack) will
 * be migrated incrementally; until then the LWJGL backend retains its
 * original implementations for those paths.
 */
public final class SmpsPresentationReplay {

    private SmpsPresentationReplay() {
    }

    /**
     * Outcome of {@link #applyToSfx}, distinguishing the side-effect paths
     * that the caller (live backend) needs to react to (e.g. binding the
     * runtime SFX stream when a new standalone driver is created).
     */
    public enum SfxApplyResult {
        /** {@link SmpsPresentationState#sfxBlocked} was true; nothing happened. */
        BLOCKED,
        /** Continuous SFX detected, existing playback extended in place. */
        EXTENDED,
        /** SFX sequencer added to the active music driver. */
        MIXED_INTO_MUSIC,
        /** A fresh standalone SFX driver was created and the sequencer added. */
        NEW_STANDALONE_DRIVER,
        /** An existing standalone SFX driver was reused; sequencer added. */
        REUSED_STANDALONE_DRIVER
    }

    /**
     * Plays an SFX through the SMPS path, mirroring the live backend's
     * {@code playSfxSmps} semantics:
     * <ul>
     *   <li>Respects {@link SmpsPresentationState#sfxBlocked}.</li>
     *   <li>Looks up SFX priority, special-SFX, and continuous-SFX flags
     *       from {@code deps.audioProfile}.</li>
     *   <li>Continuous SFX of an already-playing same-id sound extends in
     *       place on the music driver (preferred when it's the active
     *       stream) or the standalone SFX driver.</li>
     *   <li>If a music driver is present, mixes the SFX sequencer into it.</li>
     *   <li>Else creates (or reuses) a standalone {@link SmpsDriver} on
     *       {@link SmpsPresentationState#sfxStream} and adds the sequencer
     *       to it. {@code NEW_STANDALONE_DRIVER} signals the caller to bind
     *       the runtime SFX stream.</li>
     * </ul>
     *
     * <p>The {@code config} parameter overrides {@code deps.defaultConfig}
     * when non-null; donor SFX paths use this to carry a donor-specific
     * sequencer config.
     */
    public static SfxApplyResult applyToSfx(
            SmpsPresentationState state,
            AbstractSmpsData data,
            DacData dacData,
            float pitch,
            SmpsSequencerConfig config,
            SfxReplayDependencies deps) {
        if (state.sfxBlocked) {
            return SfxApplyResult.BLOCKED;
        }

        SmpsSequencerConfig effectiveConfig = (config != null) ? config : deps.defaultConfig();

        GameAudioProfile profile = deps.audioProfile();
        int sfxPriority = (profile != null) ? profile.getSfxPriority(data.getId()) : 0x70;
        boolean specialSfx = (profile != null) && profile.isSpecialSfx(data.getId());
        boolean isContinuous = (profile != null) && profile.isContinuousSfx(data.getId());
        int contTrackCount = data.getChannels() + data.getPsgChannels();

        // Continuous SFX detection: extend existing playback rather than restart.
        if (isContinuous) {
            SmpsDriver targetDriver = null;
            if (state.musicDriver != null && state.activeMusicStream == state.musicDriver) {
                targetDriver = state.musicDriver;
            } else if (state.sfxStream instanceof SmpsDriver standalone) {
                targetDriver = standalone;
            }
            if (targetDriver != null
                    && targetDriver.extendContinuousSfx(data.getId(), contTrackCount)) {
                return SfxApplyResult.EXTENDED;
            }
        }

        AudioSourceDescriptor descriptorHint = null; // SFX always uses base-sfx routing
        SmpsSourceDescriptor sourceDescriptor =
                describeSourceDescriptor(descriptorHint, data, /*sfx=*/ true);

        if (state.musicDriver != null) {
            if (isContinuous) {
                state.musicDriver.startContinuousSfx(data.getId(), contTrackCount);
            }
            SmpsSequencer seq = buildSfxSequencer(
                    data, dacData, effectiveConfig, state.musicDriver,
                    sourceDescriptor, pitch, sfxPriority, specialSfx,
                    deps.fm6DacOff(), state.activeMusicSequencer);
            state.musicDriver.addSequencer(seq, true);
            return SfxApplyResult.MIXED_INTO_MUSIC;
        }

        SmpsDriver sfxDriver;
        boolean createdNewDriver;
        if (state.sfxStream instanceof SmpsDriver existing) {
            sfxDriver = existing;
            createdNewDriver = false;
        } else {
            sfxDriver = new SmpsDriver(deps.outputSampleRate());
            sfxDriver.setDacInterpolate(deps.dacInterpolate());
            state.sfxStream = sfxDriver;
            createdNewDriver = true;
        }
        sfxDriver.setOutputSampleRate(deps.outputSampleRate());
        sfxDriver.setPsgNoiseShiftOnEveryToggle(deps.psgNoiseShiftOnEveryToggle());
        if (isContinuous) {
            sfxDriver.startContinuousSfx(data.getId(), contTrackCount);
        }
        SmpsSequencer seq = buildSfxSequencer(
                data, dacData, effectiveConfig, sfxDriver,
                sourceDescriptor, pitch, sfxPriority, specialSfx,
                deps.fm6DacOff(), state.activeMusicSequencer);
        sfxDriver.addSequencer(seq, true);
        return createdNewDriver
                ? SfxApplyResult.NEW_STANDALONE_DRIVER
                : SfxApplyResult.REUSED_STANDALONE_DRIVER;
    }

    private static SmpsSequencer buildSfxSequencer(
            AbstractSmpsData data,
            DacData dacData,
            SmpsSequencerConfig config,
            SmpsDriver driver,
            SmpsSourceDescriptor sourceDescriptor,
            float pitch,
            int sfxPriority,
            boolean specialSfx,
            boolean fm6DacOff,
            SmpsSequencer activeMusicSequencer) {
        SmpsSequencer seq = new SmpsSequencer(data, dacData, driver, config);
        seq.setSourceDescriptor(sourceDescriptor);
        seq.setSampleRate(driver.getOutputSampleRate());
        seq.setFm6DacOff(fm6DacOff);
        seq.setSfxMode(true);
        seq.setPitch(pitch);
        seq.setSfxPriority(sfxPriority);
        seq.setSpecialSfx(specialSfx);
        if (activeMusicSequencer != null) {
            seq.setFallbackVoiceData(activeMusicSequencer.getSmpsData());
        }
        return seq;
    }

    /**
     * Mirrors {@code LWJGLAudioBackend.describeSmpsSource} for SFX (no
     * descriptor hint case). Donor / fallback routing is encoded by the
     * source descriptor on the {@code AudioCommand} itself in the live path;
     * SFX calls here always pass {@code descriptorHint == null}, matching
     * the existing backend behaviour at line 461.
     */
    private static SmpsSourceDescriptor describeSourceDescriptor(
            AudioSourceDescriptor descriptorHint,
            AbstractSmpsData data,
            boolean sfx) {
        if (descriptorHint == null) {
            return sfx
                    ? SmpsSourceDescriptor.baseSfx(data)
                    : SmpsSourceDescriptor.baseMusic(data);
        }
        return switch (descriptorHint.route()) {
            case BASE_MUSIC_ID, FALLBACK_MUSIC_ID -> SmpsSourceDescriptor.baseMusic(data);
            case BASE_SFX_ID -> SmpsSourceDescriptor.baseSfx(data);
            case BASE_SFX_NAME, FALLBACK_SFX_NAME ->
                    SmpsSourceDescriptor.baseNamedSfx(descriptorHint.name(), data);
            case DONOR_MUSIC_ID ->
                    SmpsSourceDescriptor.donorMusic(descriptorHint.donorGameId(), data);
            case DONOR_SFX_ID ->
                    SmpsSourceDescriptor.donorSfx(descriptorHint.donorGameId(), data);
            case SYSTEM_COMMAND -> SmpsSourceDescriptor.from(data);
        };
    }

    /**
     * Immutable per-session inputs to {@link #applyToSfx}. Snapshotted from
     * config + audio profile at the start of a held-rewind session so the
     * worker never reaches back into the live backend.
     */
    public record SfxReplayDependencies(
            double outputSampleRate,
            boolean dacInterpolate,
            boolean fm6DacOff,
            boolean psgNoiseShiftOnEveryToggle,
            GameAudioProfile audioProfile,
            SmpsSequencerConfig defaultConfig) {
    }

    /**
     * Outcome of {@link #applyToMusicBase}. The returned music driver is the
     * one to be bound to the runtime music stream by the live caller.
     */
    public record MusicApplyResult(SmpsDriver musicDriver, SmpsSourceDescriptor sourceDescriptor) {
    }

    /**
     * Starts a new SMPS music sequencer on a fresh driver and writes it back
     * onto the presentation state. Mirrors the bottom half of
     * {@code LWJGLAudioBackend.playSmps} — driver creation, region/DAC/PSG
     * configuration, sequencer setup (speed flags, fallback voice data),
     * and {@code addSequencer}.
     *
     * <p>Callers (the live backend or the resynth worker) are responsible
     * for any prerequisite teardown (stopping a previous stream, override
     * stack pushes) and for any follow-up bindings (runtime music stream,
     * synthesiser config). Those are NOT SMPS-logical state and remain in
     * the caller. This helper writes the new driver onto
     * {@link SmpsPresentationState#musicDriver},
     * {@link SmpsPresentationState#activeMusicStream}, and
     * {@link SmpsPresentationState#activeMusicSequencer}.
     */
    public static MusicApplyResult applyToMusicBase(
            SmpsPresentationState state,
            AbstractSmpsData data,
            DacData dacData,
            SmpsSequencerConfig config,
            AudioSourceDescriptor descriptorHint,
            MusicReplayDependencies deps) {
        SmpsDriver driver = new SmpsDriver(deps.outputSampleRate());
        driver.setRegion(deps.region());
        driver.setDacInterpolate(deps.dacInterpolate());
        driver.setOutputSampleRate(deps.outputSampleRate());
        driver.setPsgNoiseShiftOnEveryToggle(deps.psgNoiseShiftOnEveryToggle());

        SmpsSourceDescriptor sourceDescriptor =
                describeSourceDescriptor(descriptorHint, data, /*sfx=*/ false);

        SmpsSequencer seq = new SmpsSequencer(data, dacData, driver, config);
        seq.setSourceDescriptor(sourceDescriptor);
        seq.setSampleRate(driver.getOutputSampleRate());
        seq.setSpeedShoes(deps.speedShoesEnabled());
        seq.setSpeedMultiplier(deps.speedMultiplier());
        seq.setFm6DacOff(deps.fm6DacOff());
        // Music is the primary voice source for SFX fallback.
        seq.setFallbackVoiceData(data);
        driver.addSequencer(seq, false);

        state.musicDriver = driver;
        state.activeMusicSequencer = seq;
        state.activeMusicStream = driver;

        return new MusicApplyResult(driver, sourceDescriptor);
    }

    /**
     * Immutable per-session inputs to {@link #applyToMusicBase}. Snapshotted
     * from config + audio profile + the current speed/region state at the
     * start of a held-rewind session so the worker never reaches back into
     * the live backend.
     */
    public record MusicReplayDependencies(
            double outputSampleRate,
            SmpsSequencer.Region region,
            boolean dacInterpolate,
            boolean fm6DacOff,
            boolean psgNoiseShiftOnEveryToggle,
            boolean speedShoesEnabled,
            int speedMultiplier) {
    }
}
