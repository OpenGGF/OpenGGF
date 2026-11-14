package com.openggf.game.soniccd.audio;

import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.SmpsSequencerConfig;

import java.util.Collections;

/**
 * Shared-sequencer configuration for the Sonic CD FM sound-effect driver
 * (src/Sound Drivers/FM/_Driver.asm). It is an SMPS Z80 driver of the Sonic 3&amp;K family, so the
 * settings start from that driver's and differ where this one's source does:
 *
 * <ul>
 *   <li>Service clock: the main loop polls YM2612 Timer B, reloaded with C8h, and updates the
 *       tracks on each overflow ({@code InitDriver}, {@code ResetTimerB}, FM/_Driver.asm:87-126), not on
 *       V-int. Timer B counts once every 16 samples of 144 master clocks
 *       ({@code NukedOpn2.doTimerB}, ym3438.c:1115), so one service is
 *       144 * 16 * (256 - C8h) = 129024 clocks of the 53693175/7 Hz NTSC YM clock.</li>
 *   <li>Command numbering: {@link CdFmCoordFlagHandler}.</li>
 *   <li>A new effect is updated in the service that admits it: {@code UpdateTracks} calls
 *       {@code PlaySoundID} before walking the tracks ({@code UpdateTracks}, FM/_Driver.asm:132-150); Sonic 3&amp;K walks first.</li>
 *   <li>A resting track does not step its vibrato: {@code UpdateTrack.Update} returns on the
 *       rest flag before calling {@code UpdateVibrato} ({@code UpdateTrack}, FM/_Driver.asm:157-189).</li>
 *   <li>The track walk is the fixed FM1..FM6 RAM order ({@code UpdateTracks}, FM/_Driver.asm:132-150); there
 *       are no PSG or DAC tracks and no music to give a channel back to.</li>
 * </ul>
 *
 * <p>Shared with the Sonic 3&amp;K driver: frequencies from a C-based {@code FreqTable}
 * (FM/_Driver.asm:1117-1119), carriers marked by bit 7 of the voice's total levels ({@code UpdateVolume},
 * FM/_Driver.asm:1608-1633), the Z80 register order of {@code InstrumentRegs} (FM/_Driver.asm:1094-1103), halved vibrato
 * steps ({@code InitVibrato}, FM/_Driver.asm:433-461), every note-going pass sending the frequency
 * ({@code UpdateFreq} falls through, FM/_Driver.asm:183-211), a duration-only byte keeping the frequency
 * (FM/_Driver.asm:362-372), key-off and SSG-EG clear at admission ({@code PlaySFX}, FM/_Driver.asm:826-860), and the
 * vibrato speed and low frequency byte cleared on a new note ({@code .Finish}, FM/_Driver.asm:372-391).
 */
public final class CdFmSequencerConfig {
    /** NTSC master clock; the YM2612 runs at a seventh of it. */
    public static final int MASTER_CLOCK_HZ = 53_693_175;
    /** Master clocks per service: 7 * 144 * 16 * (256 - C8h). */
    public static final int MASTER_CLOCKS_PER_SERVICE = 7 * 144 * 16 * (256 - 0xC8);
    /** About 59.45 Hz. */
    public static final double SERVICE_RATE_HZ = (double) MASTER_CLOCK_HZ / MASTER_CLOCKS_PER_SERVICE;

    private CdFmSequencerConfig() {
    }

    public static SmpsSequencerConfig create(CoordFlagHandler handler) {
        return new SmpsSequencerConfig.Builder()
                .speedUpTempos(Collections.emptyMap())
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .palUpdateMode(SmpsSequencerConfig.PalUpdateMode.NONE)
                .applyModOnNote(true)
                .halveModSteps(true)
                .relativePointers(false)
                .tempoOnFirstTick(true)
                .advancePsgEnvelopeOnRest(false)
                .writeFmPanOnNote(false)
                .dacNoteKeysOffFm6AndRestoresFm3(false)
                .enableDacOnSequencerStart(false)
                .fmVoiceWriteProfile(SmpsSequencerConfig.FmVoiceWriteProfile.S3K_Z80)
                .volMode(SmpsSequencerConfig.VolMode.BIT7)
                .noteOnPrevent(SmpsSequencerConfig.NoteOnPrevent.HOLD)
                .delayFreq(SmpsSequencerConfig.DelayFreq.KEEP)
                .coordFlagHandler(handler)
                .modAlgo(SmpsSequencerConfig.ModAlgo.MOD_Z80)
                .noteGoingFreqSend(SmpsSequencerConfig.NoteGoingFreqSend.EVERY_PASS)
                .stepModulationAtRest(false)
                .noteResetAliasesModulationState(true)
                .fmNoteGoingReturnsAtRest(true)
                .sfxWalkPrecedesRequest(false)
                .sfxTrackWalkMode(SmpsSequencerConfig.SfxTrackWalkMode.CHANNEL_RAM_ORDER)
                .sfxAdmissionKeyOffAndClearsSsgEg(true)
                .fmSfxTakeoverMode(SmpsSequencerConfig.FmSfxTakeoverMode.REGISTER_SEQUENCE)
                .fmSfxReleaseMode(SmpsSequencerConfig.FmSfxReleaseMode.ROM_VOICE_RESTORE_PRESERVE_REST)
                .sfxChannelOwnershipMode(SmpsSequencerConfig.SfxChannelOwnershipMode.ADMISSION)
                .serviceClockNumerator(MASTER_CLOCK_HZ)
                .serviceClockDenominator(MASTER_CLOCKS_PER_SERVICE)
                .build();
    }

    /** Configuration with a handler over its own runtime state, for direct sequencer use. */
    public static SmpsSequencerConfig standalone() {
        return create(new CdFmCoordFlagHandler(new CdFmDriverState()));
    }
}
