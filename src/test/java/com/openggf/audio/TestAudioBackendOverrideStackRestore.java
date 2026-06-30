package com.openggf.audio;

import com.openggf.audio.rewind.AudioBackendLogicalSnapshot;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind restores must rebuild the music override stack — the saved zone
 * music descriptors captured while a 1-up/invincibility jingle plays. The
 * previous restore cleared the stack and dead-stored pendingRestore, so after
 * any rewind through an override window the jingle's end (restoreMusic /
 * endMusicOverride) found an empty stack: zone music never resumed and a
 * blocked-SFX latch could never clear.
 */
class TestAudioBackendOverrideStackRestore {

    private static final int ZONE_MUSIC_ID = 0x82;
    private static final int JINGLE_MUSIC_ID = 0x2A;

    @Test
    void overrideStackRoundTripsThroughLogicalRestore() {
        HeadlessSmpsAudioBackend backend = backendWithZoneMusicUnderJingle();

        AudioBackendLogicalSnapshot snapshot = backend.captureLogicalSnapshot();
        assertEquals(List.of(AudioSourceDescriptor.baseMusic(ZONE_MUSIC_ID)), snapshot.overrideStack());

        backend.restoreLogicalSnapshot(snapshot);

        AudioBackendLogicalSnapshot restored = backend.captureLogicalSnapshot();
        assertEquals(List.of(AudioSourceDescriptor.baseMusic(ZONE_MUSIC_ID)), restored.overrideStack(),
                "logical restore must rebuild the saved zone music override state");
    }

    @Test
    void zoneMusicResumesWhenTheOverrideEndsAfterARestore() {
        HeadlessSmpsAudioBackend backend = backendWithZoneMusicUnderJingle();
        backend.restoreLogicalSnapshot(backend.captureLogicalSnapshot());

        backend.restoreMusic();
        backend.doRestoreMusic();

        AudioBackendLogicalSnapshot after = backend.captureLogicalSnapshot();
        assertEquals(AudioSourceDescriptor.baseMusic(ZONE_MUSIC_ID), after.currentMusic(),
                "ending the override after a rewind restore must resume the saved zone music");
        assertTrue(after.overrideStack().isEmpty());
        assertNotNull(backend.musicDriverForTesting());
    }

    @Test
    void pendingRestoreSurvivesWhenTheStackIsRebuildable() {
        HeadlessSmpsAudioBackend backend = backendWithZoneMusicUnderJingle();
        AudioBackendLogicalSnapshot captured = backend.captureLogicalSnapshot();
        AudioBackendLogicalSnapshot pending = new AudioBackendLogicalSnapshot(
                captured.currentMusic(),
                captured.sfxBlocked(),
                true,
                captured.speedShoesEnabled(),
                captured.speedMultiplier(),
                captured.overrideStack(),
                captured.musicDriver(),
                captured.standaloneSfxDriver());

        backend.restoreLogicalSnapshot(pending);

        assertTrue(backend.captureLogicalSnapshot().pendingRestore(),
                "a captured in-flight restore must stay pending when the stack was rebuilt");
    }

    @Test
    void sfxBlockedClearsWhenNoOverrideOrFadeCanEverUnblockIt() {
        HeadlessSmpsAudioBackend backend = newBackend();
        playZoneMusic(backend);
        AudioBackendLogicalSnapshot captured = backend.captureLogicalSnapshot();
        AudioBackendLogicalSnapshot blocked = new AudioBackendLogicalSnapshot(
                captured.currentMusic(),
                true,
                false,
                captured.speedShoesEnabled(),
                captured.speedMultiplier(),
                List.of(),
                captured.musicDriver(),
                captured.standaloneSfxDriver());

        backend.restoreLogicalSnapshot(blocked);

        assertFalse(backend.captureLogicalSnapshot().sfxBlocked(),
                "with no override stack and no active fade-in, the SFX block latch must clear");
    }

    @Test
    void sfxBlockedStaysSetWhenARestoredOverrideWillUnblockIt() {
        HeadlessSmpsAudioBackend backend = backendWithZoneMusicUnderJingle();
        AudioBackendLogicalSnapshot captured = backend.captureLogicalSnapshot();
        AudioBackendLogicalSnapshot blocked = new AudioBackendLogicalSnapshot(
                captured.currentMusic(),
                true,
                false,
                captured.speedShoesEnabled(),
                captured.speedMultiplier(),
                captured.overrideStack(),
                captured.musicDriver(),
                captured.standaloneSfxDriver());

        backend.restoreLogicalSnapshot(blocked);

        assertTrue(backend.captureLogicalSnapshot().sfxBlocked(),
                "the override-end path owns unblocking when a rebuilt override exists");
    }

    private static HeadlessSmpsAudioBackend backendWithZoneMusicUnderJingle() {
        HeadlessSmpsAudioBackend backend = newBackend();
        playZoneMusic(backend);

        AbstractSmpsData jingle = new AudioTestFixtures.StubSmpsData("jingle");
        jingle.setId(JINGLE_MUSIC_ID);
        backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(JINGLE_MUSIC_ID));
        backend.playSmps(jingle, dacData(), config(), true);
        return backend;
    }

    private static void playZoneMusic(HeadlessSmpsAudioBackend backend) {
        AbstractSmpsData zone = new AudioTestFixtures.StubSmpsData("zone");
        zone.setId(ZONE_MUSIC_ID);
        backend.prepareLogicalMusicSource(AudioSourceDescriptor.baseMusic(ZONE_MUSIC_ID));
        backend.playSmps(zone, dacData(), config(), false);
    }

    private static HeadlessSmpsAudioBackend newBackend() {
        HeadlessSmpsAudioBackend backend = new HeadlessSmpsAudioBackend(
                SonicConfigurationService.getInstance(), null);
        backend.init();
        return backend;
    }

    private static SmpsSequencerConfig config() {
        return new SmpsSequencerConfig.Builder().build();
    }

    private static DacData dacData() {
        return new DacData(
                Map.of(1, new byte[] { 0, 24, 64, 127, (byte) 255, (byte) 196, 96, 32, 8, 0 }),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295);
    }
}
