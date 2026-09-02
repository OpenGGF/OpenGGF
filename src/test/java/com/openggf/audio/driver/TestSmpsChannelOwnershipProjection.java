package com.openggf.audio.driver;

import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.AudioManager;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.sonic3k.audio.S3kE4Projection;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSfxData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsChannelOwnershipProjection {
    private static final List<S3kE4SlotExpectation> E4_SLOT_LAYOUT = List.of(
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.FM3, 2, 0x02),
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.FM4, 3, 0x04),
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.FM5, 4, 0x05),
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.FM6, 5, 0x06),
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.PSG1, 0, 0x80),
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.PSG2, 1, 0xA0),
            new S3kE4SlotExpectation(S3kE4Projection.S3kE4Slot.PSG3, 2, 0xC0));

    @Test
    void nativeE4SlotTableHasExactChannelAndVoiceControlValues() {
        List<S3kE4Projection.S3kE4Slot> slots = List.of(
                S3kE4Projection.S3kE4Slot.values());

        assertEquals(E4_SLOT_LAYOUT.size(), slots.size());
        for (int index = 0; index < E4_SLOT_LAYOUT.size(); index++) {
            S3kE4SlotExpectation expected = E4_SLOT_LAYOUT.get(index);
            S3kE4Projection.S3kE4Slot actual = slots.get(index);
            assertEquals(expected.slot(), actual);
            assertEquals(expected.channel(), actual.channel());
            assertEquals(expected.rawVoiceControl(), actual.rawVoiceControl());
        }
    }

    @Test
    void activeDeclaredSfxClaimIsProjectedBeforeItAcquiresAnyWriteLock()
            throws Exception {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = sequencer(music(0x81), driver);
        music.addTrack(track(SmpsSequencer.TrackType.FM, 2));
        SmpsSequencer sfx = sequencer(sfx(0xA0, 0x02), driver);
        SmpsSequencer.Track declared = sfx.getTracks().getFirst();
        declared.voiceData = new byte[] {1, 2, 3};
        declared.voiceId = 7;
        declared.volumeOffset = 9;
        declared.pan = 0x80;
        declared.ssgEg[1] = 0x0E;
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);

        SmpsChannelOwnershipProjection projection =
                driver.captureOwnershipProjection();
        SmpsChannelOwnershipProjection.RoleOwnership role = projection.role(
                new SmpsChannelOwnershipProjection.PhysicalChannel(
                        SmpsChannelOwnershipProjection.Bus.FM, 2)).orElseThrow();

        assertTrue(role.sfxOccupied(),
                "declared SFX occupancy must not wait for a chip-write lock");
        assertEquals(1, role.sfxClaims().size());
        assertEquals(1, role.musicTracks().size());
        assertEquals(1, role.sfxClaims().getFirst().coordinate().sequencerIndex());
        assertEquals(0, role.sfxClaims().getFirst().coordinate().trackIndex());
        assertFalse(role.sfxClaims().getFirst().track().overridden());

        S3kE4Projection e4 = S3kE4Projection.capture(projection);
        assertTrue(e4.complete());
        S3kE4Projection.SlotProjection fm3 = e4.slots().getFirst();
        assertEquals(S3kE4Projection.Availability.AVAILABLE, fm3.availability());
        S3kE4Projection.S3kE4Track view = fm3.sfx();
        assertEquals(0x02, view.canonicalVoiceControl());
        assertFalse(view.noiseOrFm3Special());
        assertFalse(view.rawPlaybackFlags().isPresent(),
                "the engine retains semantic flags, not an invented raw Z80 byte");
        assertEquals(7, view.voiceId());
        assertEquals(9, view.volume());
        assertEquals(0x80, view.pan());
        assertArrayEquals(new int[] {0, 0x0E, 0, 0}, view.ssgEg());
        byte[] copied = view.materializedVoice();
        copied[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, view.materializedVoice());
    }

    @Test
    void psgNoiseProjectionRetainsPsgNoiseSemantics() throws Exception {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sfx = sequencer(sfx(0xA0, 0xC0), driver);
        SmpsSequencer.Track declared = sfx.getTracks().getFirst();
        declared.noiseMode = true;
        declared.psgNoiseParam = 0x07;
        declared.voiceData = new byte[] {1, 2, 3};
        declared.voiceId = 7;
        declared.volumeOffset = 9;
        declared.pan = 0x80;
        declared.ssgEg[1] = 0x0E;
        driver.addSequencer(sfx, true);

        S3kE4Projection.SlotProjection psg3 = S3kE4Projection.capture(
                driver.captureOwnershipProjection()).slots().getLast();

        assertEquals(S3kE4Projection.Availability.AVAILABLE, psg3.availability());
        assertTrue(psg3.sfx().noiseOrFm3Special());
        assertEquals(0x01, psg3.sfx().canonicalPlaybackFlags() & 0x01);
        assertEquals(0x07, psg3.sfx().psgNoise());
        assertEquals(0xC0, psg3.sfx().canonicalVoiceControl());
        assertFalse(psg3.sfx().rawPlaybackFlags().isPresent(),
                "the engine retains semantic flags, not an invented raw Z80 byte");
        assertEquals(7, psg3.sfx().voiceId());
        assertEquals(9, psg3.sfx().volume());
        assertEquals(0x80, psg3.sfx().pan());
        assertArrayEquals(new int[] {0, 0x0E, 0, 0}, psg3.sfx().ssgEg());
        byte[] copied = psg3.sfx().materializedVoice();
        copied[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, psg3.sfx().materializedVoice());
    }

    @Test
    void fm3SpecialProjectionSurvivesLogicalSnapshotRestore() throws Exception {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer music = sequencer(music(0x81), driver);
        music.addTrack(track(SmpsSequencer.TrackType.FM, 2));
        SmpsSequencer sfx = sequencer(sfx(0xA0, 0x02), driver);
        sfx.getTracks().getFirst().fm3SpecialMode = true;
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);

        S3kE4Projection before = S3kE4Projection.capture(
                driver.captureOwnershipProjection());
        driver.restoreSnapshot(driver.captureSnapshot());
        S3kE4Projection restored = S3kE4Projection.capture(
                driver.captureOwnershipProjection());

        assertTrue(before.complete());
        assertTrue(restored.complete());
        assertTrue(before.slots().getFirst().sfx().noiseOrFm3Special());
        assertTrue(restored.slots().getFirst().sfx().noiseOrFm3Special());
        assertEquals(0x01, restored.slots().getFirst().sfx()
                .canonicalPlaybackFlags() & 0x01);
    }

    @Test
    void captureIsObservationalAndRoundTripsWithTheLogicalSnapshot()
            throws Exception {
        AtomicInteger writes = new AtomicInteger();
        SmpsDriver driver = SmpsDriverTestAccess.create(48_000, new com.openggf.audio.synth.ChipWriteObserver() {
            @Override public void onYm2612Write(int port, int register, int value) {
                writes.incrementAndGet();
            }
            @Override public void onPsgWrite(int value) {
                writes.incrementAndGet();
            }
        });
        SmpsSequencer music = sequencer(music(0x81), driver);
        music.addTrack(track(SmpsSequencer.TrackType.PSG, 1));
        SmpsSequencer sfx = sequencer(sfx(0xA0, 0xA0), driver);
        driver.addSequencer(music, false);
        driver.addSequencer(sfx, true);
        var before = driver.captureSnapshot();
        writes.set(0);

        SmpsChannelOwnershipProjection first =
                driver.captureOwnershipProjection();
        driver.restoreSnapshot(before);
        SmpsChannelOwnershipProjection restored =
                driver.captureOwnershipProjection();

        assertEquals(0, writes.get(),
                "capturing and restoring logical ownership must not write the chip");
        SmpsChannelOwnershipProjection.RoleOwnership beforeRole =
                first.roles().values().iterator().next();
        SmpsChannelOwnershipProjection.RoleOwnership restoredRole =
                restored.roles().values().iterator().next();
        assertEquals(beforeRole.channel(), restoredRole.channel());
        assertEquals(beforeRole.sfxClaims().getFirst().coordinate(),
                restoredRole.sfxClaims().getFirst().coordinate());
        assertEquals(beforeRole.sfxClaims().getFirst().track().type(),
                restoredRole.sfxClaims().getFirst().track().type());
        assertEquals(beforeRole.sfxClaims().getFirst().track().channelId(),
                restoredRole.sfxClaims().getFirst().track().channelId());
        assertEquals(beforeRole.musicTracks().getFirst().coordinate(),
                restoredRole.musicTracks().getFirst().coordinate());
    }

    @Test
    void unsupportedActiveSfxRoleFailsClosedForS3kE4() throws Exception {
        SmpsDriver driver = new SmpsDriver();
        SmpsSequencer sfx = sequencer(sfx(0xA0, 0x00), driver);
        driver.addSequencer(sfx, true);

        S3kE4Projection projection = S3kE4Projection.capture(
                driver.captureOwnershipProjection());

        assertFalse(projection.complete());
    }

    private static SmpsSequencer sequencer(AbstractSmpsData source,
            SmpsDriver driver) {
        return new SmpsSequencer(source, AudioTestFixtures.EMPTY_DAC, driver,
                AudioManager.getInstance(), Sonic3kSmpsSequencerConfig.CONFIG);
    }

    private static SmpsSequencer.Track track(SmpsSequencer.TrackType type,
            int channel) throws Exception {
        Constructor<SmpsSequencer.Track> constructor =
                SmpsSequencer.Track.class.getDeclaredConstructor(int.class,
                        SmpsSequencer.TrackType.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, type, channel);
    }

    private static AbstractSmpsData music(int id) {
        Sonic3kSmpsData data = new Sonic3kSmpsData(new byte[64], 0);
        data.setId(id);
        return data;
    }

    private static AbstractSmpsData sfx(int id, int channel) {
        byte[] data = new byte[64];
        data[2] = 1; // tick multiplier
        data[3] = 1; // track count
        data[4] = (byte) 0x80; // S3K shipped SFX playbackFlags
        data[5] = (byte) channel;
        data[6] = 0x20;
        data[7] = 0;
        data[0x20] = (byte) 0xF2; // terminal stream at the declared pointer
        Sonic3kSfxData result = new Sonic3kSfxData(data, 0, 0, 0);
        result.setId(id);
        return result;
    }

    private record S3kE4SlotExpectation(
            S3kE4Projection.S3kE4Slot slot,
            int channel,
            int rawVoiceControl) {
    }
}
