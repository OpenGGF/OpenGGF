package com.openggf.audio.smps;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.rewind.SmpsSequencerSnapshot;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.game.sonic1.audio.Sonic1SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSmpsSequencerDriverCadence {

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void newlyInitializedDriverTrackStartsWithDurationOne() {
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);

        assertEquals(1, track.duration);
    }

    @Test
    void s2NonCarryVintHoldsDurationButStillServicesPsgEnvelope() {
        SmpsSequencer sequencer = sequencer(0x01,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                        .build());
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, 0);
        track.duration = 3;
        track.note = 0x81;
        track.envData = new byte[] { 1, 2, (byte) 0x80 };
        sequencer.addTrack(track);

        // Shipped FixDriverBugs=0 calls TempoWait before zUpdateMusic. The
        // non-carry extension holds the note, but zPSGUpdateVolFX still
        // consumes one envelope entry during the mandatory track service.
        sequencer.advanceBatch(1);

        assertEquals(3, track.duration);
        assertEquals(1, track.envPos);
        assertEquals(1, track.envValue);
    }

    @Test
    void s2NonCarryExtensionAppliesBeforeTrackService() {
        SmpsSequencer sequencer = sequencer(0x01,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                        .build());
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        track.duration = 1;
        sequencer.addTrack(track);

        sequencer.advanceBatch(1);

        // With shipped FixDriverBugs=0, zUpdateMusic calls TempoWait first.
        // The non-carry path extends 1 -> 2, then track service returns it to
        // 1 without consuming the next note.
        assertEquals(0, track.pos);
        assertEquals(1, track.duration);
    }

    @Test
    void s3kSeedsTempoAccumulatorAndExtendsBeforeTrackServiceOnCarry() {
        SmpsSequencer sequencer = sequencer(0xFF,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .build());
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        track.duration = 1;
        sequencer.addTrack(track);

        sequencer.advanceBatch(1);

        // BGM load seeds zTempoAccumulator with the header tempo. TempoWait is
        // before zTrackUpdLoop, so the carry extends 1 -> 2 and track service
        // returns it to 1 without consuming the next note.
        assertEquals(0, track.pos);
        assertEquals(1, track.duration);
        assertEquals(0xFE, sequencer.captureSnapshot().tempoAccumulator());
    }

    @Test
    void s1PalKeepsTheHeaderTempoWithoutSyntheticSixtyHertzScaling() {
        SmpsSequencer sequencer = sequencer(
                0x80, Sonic1SmpsSequencerConfig.CONFIG);

        sequencer.setRegion(SmpsSequencer.Region.PAL);

        // The shipped S1 driver has no PAL tempo-compensation path. It simply
        // receives its driver update at the PAL VBlank rate.
        assertEquals(0x80, sequencer.captureSnapshot().tempoWeight());
    }

    @Test
    void s1TimeoutExtensionPrecedesItsMandatoryTrackService() {
        SmpsSequencer sequencer = sequencer(
                1, Sonic1SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        sequencer.addTrack(track);

        sequencer.advanceBatch(1);

        assertEquals(0, track.pos);
        assertEquals(1, track.duration);
    }

    @Test
    void s3kSpeedTimeoutIsServicedAtSfxAndMusicTails() {
        SmpsSequencer sequencer = sequencer(0,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .build());
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, 0);
        track.duration = 20;
        track.note = 0x81;
        track.envData = new byte[20];
        sequencer.addTrack(track);
        sequencer.setSpeedMultiplier(8);

        sequencer.advanceBatch(1);

        // zDoSpeedUp is the common tail of zUpdateSFXTracks and zUpdateMusic.
        // Starting at zero, the SFX tail reloads 8 and performs an extra music
        // service; the extra and normal music tails leave the timeout at 6.
        assertEquals(2, track.envPos);
        assertEquals(6, sequencer.captureSnapshot().speedupTimeout());

        sequencer.advanceBatch(4);

        // Native music service counts per VInt are 2,1,1,1,2.
        assertEquals(7, track.envPos);
    }

    @Test
    void s2ZeroTempoStillServicesEffectsWhileHoldingDuration() {
        SmpsSequencer sequencer = sequencer(0,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                        .build());
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, 0);
        track.duration = 3;
        track.note = 0x81;
        track.envData = new byte[] { 1, 2, 3, 4 };
        sequencer.addTrack(track);

        sequencer.advanceBatch(2);

        assertEquals(3, track.duration);
        assertEquals(2, track.envPos);
    }

    @Test
    void s3kSpeedTogglePreservesTheLiveTimeoutPhase() {
        SmpsSequencer sequencer = sequencer(0,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .build());
        sequencer.setSpeedMultiplier(8);
        sequencer.advanceBatch(1);
        assertEquals(6, sequencer.captureSnapshot().speedupTimeout());

        sequencer.setSpeedMultiplier(1);
        sequencer.setSpeedMultiplier(8);

        // The input command writes zTempoSpeedup only. It does not clear or
        // reload zSpeedupTimeout when speed shoes are toggled.
        assertEquals(6, sequencer.captureSnapshot().speedupTimeout());
    }

    @Test
    void s3kEverySpeedTailCanTriggerAnExtraMusicService() {
        SmpsSequencer sequencer = sequencer(0,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .build());
        SmpsSequencer.Track track = psgEnvelopeTrack();
        sequencer.addTrack(track);
        sequencer.setSpeedMultiplier(3);

        sequencer.advanceBatch(2);

        // Frame one expires at the SFX tail; frame two expires at the normal
        // music tail. Both must schedule an extra music service.
        assertEquals(4, track.envPos);
        assertEquals(2, sequencer.captureSnapshot().speedupTimeout());
    }

    @Test
    void s2PalEligibleMusicReceivesSixServicesAcrossFiveVints() {
        SmpsSequencer sequencer = sequencer(
                0, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track track = psgEnvelopeTrack();
        sequencer.addTrack(track);
        sequencer.setRegion(SmpsSequencer.Region.PAL);
        sequencer.setSampleRate(50.0);

        sequencer.advanceBatch(5);

        assertEquals(6, track.envPos);
    }

    @Test
    void s2PalOptOutMusicReceivesOneServicePerVint() {
        MinimalMusicData data = new MinimalMusicData(
                new byte[] { (byte) 0x81, 4 }, 0);
        data.setPalSpeedupDisabled(true);
        SmpsSequencer sequencer = sequencer(
                data, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track track = psgEnvelopeTrack();
        sequencer.addTrack(track);
        sequencer.setRegion(SmpsSequencer.Region.PAL);
        sequencer.setSampleRate(50.0);

        sequencer.advanceBatch(5);

        assertEquals(5, track.envPos);
    }

    @Test
    void s2PalSfxRemainsSingleService() {
        SmpsSequencer sequencer = sequencer(
                0, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track track = psgEnvelopeTrack();
        sequencer.addTrack(track);
        sequencer.setSfxMode(true);
        sequencer.setRegion(SmpsSequencer.Region.PAL);
        sequencer.setSampleRate(50.0);

        sequencer.advanceBatch(5);

        assertEquals(5, track.envPos);
        assertEquals(5, sequencer.captureSnapshot().palUpdateCounter());
    }

    @Test
    void s2PalCounterRoundTripsAtANonDefaultPhase() {
        SmpsSequencer source = sequencer(
                0, Sonic2SmpsSequencerConfig.CONFIG);
        source.setRegion(SmpsSequencer.Region.PAL);
        source.setSampleRate(50.0);
        source.advanceBatch(2);
        SmpsSequencerSnapshot snapshot = source.captureSnapshot();
        SmpsSequencer restored = sequencer(
                0, Sonic2SmpsSequencerConfig.CONFIG);

        restored.restoreSnapshot(snapshot);

        assertEquals(3, snapshot.palUpdateCounter());
        assertEquals(3, restored.captureSnapshot().palUpdateCounter());
    }

    @Test
    void s1StreamTempoCommandResetsTheTimeoutToTheNewTempo() {
        SmpsSequencer sequencer = sequencer(
                new MinimalMusicData(new byte[] {
                        (byte) 0xEA, 0x44, (byte) 0x81, 4 }, 2),
                Sonic1SmpsSequencerConfig.CONFIG);
        setInt(sequencer, "tempoAccumulator", 2);
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        track.duration = 1;
        sequencer.addTrack(track);

        sequencer.advanceBatch(1);

        assertEquals(0x44, sequencer.captureSnapshot().tempoAccumulator());
    }

    @Test
    void s2StreamTempoCommandPreservesTheLiveAccumulator() {
        SmpsSequencer sequencer = sequencer(
                new MinimalMusicData(new byte[] {
                        (byte) 0xEA, 0x44, (byte) 0x81, 4 }, 0xFF),
                Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        track.duration = 1;
        sequencer.addTrack(track);

        sequencer.advanceBatch(1);

        assertEquals(0xFE, sequencer.captureSnapshot().tempoAccumulator());
    }

    @Test
    void s2MusicLoadedWhileSpeedShoesAreActiveSeedsTurboTempoPhase() {
        MinimalMusicData data = new MinimalMusicData(
                new byte[] { (byte) 0x81, 4 }, 0x40);
        data.setId(7);
        SmpsSequencer sequencer = sequencer(data,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                        .speedUpTempos(java.util.Map.of(7, 0x90))
                        .tempoPhasePolicy(
                                SmpsSequencerConfig.TempoPhasePolicy.PRESERVE)
                        .build());

        sequencer.initializeSpeedShoes(true);

        assertEquals(0x90, sequencer.captureSnapshot().tempoWeight());
        assertEquals(0x90, sequencer.captureSnapshot().tempoAccumulator());
    }

    @Test
    void s1LiveSpeedChangeResetsTempoPhase() {
        MinimalMusicData data = new MinimalMusicData(
                new byte[] { (byte) 0x81, 4 }, 0x40);
        data.setId(7);
        SmpsSequencer sequencer = sequencer(data,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.TIMEOUT)
                        .speedUpTempos(java.util.Map.of(7, 0x90))
                        .tempoPhasePolicy(
                                SmpsSequencerConfig.TempoPhasePolicy.RESET_TO_EFFECTIVE_TEMPO)
                        .build());
        setInt(sequencer, "tempoAccumulator", 2);

        sequencer.setSpeedShoes(true);

        assertEquals(0x90, sequencer.captureSnapshot().tempoAccumulator());
    }

    @Test
    void s2LiveSpeedChangePreservesTempoPhase() {
        MinimalMusicData data = new MinimalMusicData(
                new byte[] { (byte) 0x81, 4 }, 0x40);
        data.setId(7);
        SmpsSequencer sequencer = sequencer(data,
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW2)
                        .speedUpTempos(java.util.Map.of(7, 0x90))
                        .tempoPhasePolicy(
                                SmpsSequencerConfig.TempoPhasePolicy.PRESERVE)
                        .build());
        setInt(sequencer, "tempoAccumulator", 2);

        sequencer.setSpeedShoes(true);

        assertEquals(2, sequencer.captureSnapshot().tempoAccumulator());
    }

    @Test
    void directReadAndBatchAdvanceShareTheSameFirstDriverService() {
        SmpsSequencer direct = sequencer(
                1, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer batched = sequencer(
                1, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track directTrack = psgEnvelopeTrack();
        SmpsSequencer.Track batchedTrack = psgEnvelopeTrack();
        direct.addTrack(directTrack);
        batched.addTrack(batchedTrack);

        direct.read(new short[1]);
        batched.advanceBatch(1);

        assertEquals(1, directTrack.envPos);
        assertEquals(directTrack.envPos, batchedTrack.envPos);
    }

    @Test
    void s1AndS2PsgReleaseRestsMusicUntilItsNextNote() {
        for (SmpsSequencerConfig config : new SmpsSequencerConfig[] {
                Sonic1SmpsSequencerConfig.CONFIG,
                Sonic2SmpsSequencerConfig.CONFIG }) {
            SmpsSequencer sequencer = sequencer(1, config);
            SmpsSequencer.Track track = new SmpsSequencer.Track(
                    0, SmpsSequencer.TrackType.PSG, 0);
            track.note = 0x91;
            track.resting = false;
            sequencer.addTrack(track);

            sequencer.setChannelOverridden(
                    SmpsSequencer.TrackType.PSG, 0, true);
            sequencer.setChannelOverridden(
                    SmpsSequencer.TrackType.PSG, 0, false);

            assertEquals(true, track.resting);
        }
        assertEquals(SmpsSequencerConfig.FmSfxTakeoverMode.REGISTER_SEQUENCE,
                Sonic2SmpsSequencerConfig.CONFIG.getFmSfxTakeoverMode());
        assertEquals(SmpsSequencerConfig.FmSfxTakeoverMode.KEY_OFF_CLEAR_SSG_EG,
                Sonic3kSmpsSequencerConfig.CONFIG.getFmSfxTakeoverMode());
    }

    @Test
    void s3kPsgReleaseRemainsAvailableForSameVintMusicService() {
        SmpsSequencer sequencer = sequencer(
                1, Sonic3kSmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, 0);
        track.note = 0x91;
        track.resting = false;
        sequencer.addTrack(track);

        sequencer.setChannelOverridden(
                SmpsSequencer.TrackType.PSG, 0, true);
        sequencer.setChannelOverridden(
                SmpsSequencer.TrackType.PSG, 0, false);

        assertEquals(false, track.resting);
    }

    @Test
    void fadeTerminalDoesNotApplyAnExtraVolumeStep() {
        SmpsSequencer sequencer = sequencer(
                1, Sonic2SmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track fm = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        sequencer.addTrack(fm);

        sequencer.triggerFadeOut(1, 0);
        sequencer.advanceBatch(1);

        assertEquals(false, fm.active);
        assertEquals(0, fm.volumeOffset,
                "counter transition to zero stops before another fade step");
    }

    @Test
    void s3kFadeImmediatelyHaltsDacAndPsgAndLeavesFmForSteppedFade() {
        SmpsSequencer sequencer = sequencer(
                1, Sonic3kSmpsSequencerConfig.CONFIG);
        SmpsSequencer.Track fm = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0);
        SmpsSequencer.Track dac = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.DAC, 5);
        SmpsSequencer.Track psg = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, 0);
        sequencer.addTrack(fm);
        sequencer.addTrack(dac);
        sequencer.addTrack(psg);

        sequencer.triggerFadeOut(0x28, 6);

        assertEquals(true, fm.active);
        assertEquals(false, dac.active);
        assertEquals(false, psg.active);
    }

    @Test
    void sonicOneFadeStopsActiveSfxAtTheRequestBoundary() {
        com.openggf.audio.driver.SmpsDriver driver =
                new com.openggf.audio.driver.SmpsDriver();
        SmpsSequencer music = new SmpsSequencer(
                new MinimalMusicData(new byte[] {(byte) 0x81, 4}, 1),
                AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), Sonic1SmpsSequencerConfig.CONFIG);
        SmpsSequencer sfx = new SmpsSequencer(
                new MinimalMusicData(new byte[] {(byte) 0x81, 4}, 1),
                AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), Sonic1SmpsSequencerConfig.CONFIG);
        sfx.addTrack(new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.FM, 0));
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);

        music.triggerFadeOut(0x28, 3);

        assertEquals(1, driver.captureSnapshot().sequencers().size());
        assertEquals(false,
                driver.captureSnapshot().sequencers().getFirst().sfx());
    }

    private static void setInt(Object target, String fieldName, int value) {
        try {
            java.lang.reflect.Field field = target.getClass()
                    .getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setInt(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static SmpsSequencer.Track psgEnvelopeTrack() {
        SmpsSequencer.Track track = new SmpsSequencer.Track(
                0, SmpsSequencer.TrackType.PSG, 0);
        track.duration = 20;
        track.note = 0x81;
        track.envData = new byte[20];
        return track;
    }

    private static SmpsSequencer sequencer(
            int tempo, SmpsSequencerConfig config) {
        return sequencer(new MinimalMusicData(
                new byte[] { (byte) 0x81, 4 }, tempo), config);
    }

    private static SmpsSequencer sequencer(
            MinimalMusicData data, SmpsSequencerConfig config) {
        SmpsSequencer sequencer = new SmpsSequencer(
                data,
                AudioTestFixtures.EMPTY_DAC,
                new VirtualSynthesizer(),
                AudioManager.getInstance(),
                config);
        sequencer.setSampleRate(60.0);
        sequencer.setRegion(SmpsSequencer.Region.NTSC);
        return sequencer;
    }

    private static final class MinimalMusicData extends AbstractSmpsData {
        private MinimalMusicData(byte[] data, int tempo) {
            super(data, 0);
            this.tempo = tempo;
        }

        @Override
        protected void parseHeader() {
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return new byte[25];
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return new byte[0];
        }

        @Override
        public int read16(int offset) {
            return 0;
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }
}
