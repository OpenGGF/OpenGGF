package com.openggf.game.sonic3k.audio;

import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;

import java.util.Collections;

/**
 * SMPS sequencer configuration for Sonic 3 &amp; Knuckles.
 *
 * <p>From DefDrv.txt (S&amp;K final):
 * <ul>
 *   <li>PtrFmt = Z80 (relativePointers=false)</li>
 *   <li>TempoMode = OVERFLOW</li>
 *   <li>ModAlgo = Z80 (applyModOnNote=true, halveModSteps=true)</li>
 *   <li>VolMode = BIT7</li>
 *   <li>NoteOnPrevent = HOLD</li>
 *   <li>DelayFreq = KEEP</li>
 *   <li>PSG envelope 80 = RESET</li>
 *   <li>FadeOutSteps = 0x28, FadeOutDelay = 6</li>
 *   <li>FadeInSteps = 0x40, FadeInDelay = 2</li>
 *   <li>FMChnOrder = 16 0 1 2 4 5 6 (same as S2)</li>
 * </ul>
 *
 * <p>S3K has no speed-up tempo table; speed shoes use frame multiplier instead
 * (Z80 RAM 0x1C08). The speed-up tempos map is empty.
 */
public final class Sonic3kSmpsSequencerConfig {

    /** Tempo modulo base — same across all games, references shared default. */
    public static final int TEMPO_MOD_BASE = SmpsSequencerConfig.DEFAULT_TEMPO_MOD_BASE;

    /** FM channel order — same across all games, references shared default. */
    public static final int[] FM_CHANNEL_ORDER = SmpsSequencerConfig.DEFAULT_FM_CHANNEL_ORDER;

    /** PSG channel order — same across all games, references shared default. */
    public static final int[] PSG_CHANNEL_ORDER = SmpsSequencerConfig.DEFAULT_PSG_CHANNEL_ORDER;

    /** Pre-built sequencer config instance for S3K. */
    public static final SmpsSequencerConfig CONFIG;

    static {
        CONFIG = create(new Sonic3kCoordFlagHandler());
    }

    public static SmpsSequencerConfig create(CoordFlagHandler coordFlagHandler) {
        return new SmpsSequencerConfig.Builder()
                .speedUpTempos(Collections.emptyMap())
                .tempoModBase(TEMPO_MOD_BASE)
                .fmChannelOrder(FM_CHANNEL_ORDER)
                .psgChannelOrder(PSG_CHANNEL_ORDER)
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .palServicePolicy(SmpsSequencerConfig.PalServicePolicy.FULL_DRIVER_REPEAT_EVERY_SIXTH)
                .sfxPriorityPolicy(SmpsSequencerConfig.SfxPriorityPolicy.NONE)
                .driverServiceOrder(SmpsSequencerConfig.DriverServiceOrder.SFX_THEN_MUSIC)
                .sfxStartTiming(
                        SmpsSequencerConfig.SfxStartTiming.NEXT_DRIVER_UPDATE)
                .fadeOutChannelPolicy(
                        SmpsSequencerConfig.FadeOutChannelPolicy
                                .HALT_DAC_AND_PSG_FADE_FM)
                .musicOverrideSpeedPolicy(
                        SmpsSequencerConfig.MusicOverrideSpeedPolicy
                                .NORMAL_DURING_OVERRIDE)
                .musicOverrideRestorePolicy(
                        SmpsSequencerConfig.MusicOverrideRestorePolicy
                                .DRIVER_FADE_IN)
                .musicOverrideSfxReleasePolicy(
                        SmpsSequencerConfig.MusicOverrideSfxReleasePolicy
                                .ON_RESTORE)
                .fadeInChannelPolicy(
                        SmpsSequencerConfig.FadeInChannelPolicy.FM_ONLY)
                .applyModOnNote(true)       // ModAlgo = Z80
                .halveModSteps(true)        // Z80 driver halves mod steps (srl a)
                .relativePointers(false)    // PtrFmt = Z80 (absolute addresses)
                .fmVoiceWriteProfile(SmpsSequencerConfig.FmVoiceWriteProfile.S3K_Z80)
                .ymServiceTimingProfile(Sonic3kYmServiceTimingProfile.PROFILE)
                // fix_sndbugs=0 zPlaySound keys off the incumbent and clears
                // all four SSG-EG registers. It does not reset the YM2612's
                // internal envelope phase as the legacy engine path did.
                .fmSfxTakeoverMode(
                        SmpsSequencerConfig.FmSfxTakeoverMode
                                .KEY_OFF_CLEAR_SSG_EG)
                // fix_sndbugs=0 cfStopTrack keys the SFX off and restores the
                // overridden music voice. Only explicit silence/stop-all
                // paths call zFMSilenceChannel and write TL $7F.
                .fmSfxReleaseMode(
                        SmpsSequencerConfig.FmSfxReleaseMode
                                .RESTORE_MUSIC_DIRECTLY)
                .volMode(SmpsSequencerConfig.VolMode.BIT7)
                .psgEnvCmd80(SmpsSequencerConfig.PsgEnvCmd80.RESET)
                .noteOnPrevent(SmpsSequencerConfig.NoteOnPrevent.HOLD)
                .delayFreq(SmpsSequencerConfig.DelayFreq.KEEP)
                .coordFlagHandler(coordFlagHandler)
                .modAlgo(SmpsSequencerConfig.ModAlgo.MOD_Z80)
                .fadeOutDelay(6)            // FadeOutDelay = 6
                .fadeOutSteps(0x28)         // FadeOutSteps = 28h
                .fadeInSteps(0x40)          // FadeInSteps = 40h
                .fadeInDelay(2)             // FadeInDelay = 2
                .pausePolicy(SmpsSequencerConfig.PausePolicy.S3K_FM1_TO_5)
                .build();
    }

    private Sonic3kSmpsSequencerConfig() {
    }
}
