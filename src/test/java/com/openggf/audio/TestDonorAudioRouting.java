package com.openggf.audio;

import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.ResolvedSmpsSfxSource;
import com.openggf.audio.presentation.SmpsAssetKey;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.data.Rom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests donor audio routing in AudioManager.
 * Verifies that base game sounds take priority, donor sounds fill gaps,
 * and cleanup works correctly. No ROM or OpenGL required.
 */
public class TestDonorAudioRouting {

    private AudioManager audioManager;
    private RecordingBackend backend;

    private static final DacData EMPTY_DAC = new DacData(
            Collections.emptyMap(), Collections.emptyMap(), 288);

    @BeforeEach
    public void setUp() {
        audioManager = AudioManager.getInstance();
        audioManager.resetState();
        backend = new RecordingBackend();
        audioManager.setBackend(backend);
    }

    @AfterEach
    public void tearDown() {
        audioManager.resetState();
    }

    @Test
    public void testBaseMiss_DonorBindingExists_PlaysDonor() {
        // Base sound map does NOT contain SPINDASH_CHARGE (simulates S1)
        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        baseMap.put(GameSound.JUMP, 0x90);
        audioManager.setSoundMap(baseMap);

        // Register donor loader with spindash
        StubSmpsLoader donorLoader = new StubSmpsLoader();
        donorLoader.sfxResults.put(0xE0, new StubSmpsData("donor-spindash"));
        audioManager.registerDonorLoader("s2", donorLoader, EMPTY_DAC);
        audioManager.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);

        assertEquals("donor-spindash", backend.lastSfxName);
    }

    @Test
    public void testBaseMiss_NoDonorBinding_FallsThrough() {
        // Base sound map does NOT contain SPINDASH_CHARGE, no donor registered
        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        audioManager.setSoundMap(baseMap);

        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);

        // Should fall through to backend.playSfx(name)
        assertEquals("SPINDASH_CHARGE", backend.lastFallbackName);
        assertNull(backend.lastSfxName, "SMPS data should not have been played");
    }

    @Test
    public void testBaseHit_DonorNotConsulted() {
        // Base sound map has SPINDASH_CHARGE mapped to a base SFX ID
        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        baseMap.put(GameSound.SPINDASH_CHARGE, 0xA5);
        audioManager.setSoundMap(baseMap);

        // Set up a base SMPS loader that handles 0xA5
        StubSmpsLoader baseLoader = new StubSmpsLoader();
        baseLoader.sfxResults.put(0xA5, new StubSmpsData("base-roll"));
        audioManager.setAudioProfile(new StubAudioProfile(baseLoader));
        // setRom triggers smpsLoader creation via audioProfile
        // We need to manually poke the loader â€” use the profile's createSmpsLoader
        // Actually setRom(null) will call audioProfile.createSmpsLoader(null) which returns our stub
        audioManager.setRom(null);

        // Also register donor with the same sound
        StubSmpsLoader donorLoader = new StubSmpsLoader();
        donorLoader.sfxResults.put(0xE0, new StubSmpsData("donor-spindash"));
        audioManager.registerDonorLoader("s2", donorLoader, EMPTY_DAC);
        audioManager.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);

        // Base loader should have handled it
        assertEquals("base-roll", backend.lastSfxName);
    }

    @Test
    public void testClearDonorAudio_RemovesAllState() {
        // Register donor
        StubSmpsLoader donorLoader = new StubSmpsLoader();
        donorLoader.sfxResults.put(0xE0, new StubSmpsData("donor-spindash"));
        audioManager.registerDonorLoader("s2", donorLoader, EMPTY_DAC);
        audioManager.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        // Clear
        audioManager.clearDonorAudio();

        // Now play â€” should fall through to backend
        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        audioManager.setSoundMap(baseMap);
        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);
        assertEquals("SPINDASH_CHARGE", backend.lastFallbackName);
    }

    @Test
    public void testSetRom_DoesNotWipeDonorBindings() {
        // Simulates real init: donor registered, THEN setRom called during level load.
        // setRom must NOT clear donor state.
        StubSmpsLoader donorLoader = new StubSmpsLoader();
        donorLoader.sfxResults.put(0xE0, new StubSmpsData("donor-spindash"));
        audioManager.registerDonorLoader("s2", donorLoader, EMPTY_DAC);
        audioManager.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        // Level load calls setAudioProfile + setRom + setSoundMap
        StubSmpsLoader baseLoader = new StubSmpsLoader();
        audioManager.setAudioProfile(new StubAudioProfile(baseLoader));
        audioManager.setRom(null);  // This MUST NOT clear donor bindings

        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        baseMap.put(GameSound.JUMP, 0x90);
        audioManager.setSoundMap(baseMap);

        // Donor spindash should still work
        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);
        assertEquals("donor-spindash", backend.lastSfxName, "Donor spindash must survive setRom()");
    }

    @Test
    public void testMultipleDonorGames_CorrectLoaderUsed() {
        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        audioManager.setSoundMap(baseMap);

        // Register S2 donor
        StubSmpsLoader s2Loader = new StubSmpsLoader();
        s2Loader.sfxResults.put(0xE0, new StubSmpsData("s2-spindash"));
        audioManager.registerDonorLoader("s2", s2Loader, EMPTY_DAC);
        audioManager.registerDonorSound(GameSound.SPINDASH_CHARGE, "s2", 0xE0);

        // Register S3K donor
        DacData s3kDac = new DacData(Collections.emptyMap(), Collections.emptyMap(), 297);
        StubSmpsLoader s3kLoader = new StubSmpsLoader();
        s3kLoader.sfxResults.put(0x54, new StubSmpsData("s3k-fire-shield"));
        audioManager.registerDonorLoader("s3k", s3kLoader, s3kDac);
        audioManager.registerDonorSound(GameSound.FIRE_SHIELD, "s3k", 0x54);

        // Play spindash â€” should route to S2
        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);
        assertEquals("s2-spindash", backend.lastSfxName);

        // Play fire shield â€” should route to S3K
        audioManager.playSfx(GameSound.FIRE_SHIELD, 1.0f);
        assertEquals("s3k-fire-shield", backend.lastSfxName);
    }

    @Test
    public void testDonorSfx_UsesProvidedSequencerConfig() {
        Map<GameSound, Integer> baseMap = new EnumMap<>(GameSound.class);
        audioManager.setSoundMap(baseMap);

        SmpsSequencerConfig donorConfig = new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .build();

        StubSmpsLoader donorLoader = new StubSmpsLoader();
        donorLoader.sfxResults.put(0xE0, new StubSmpsData("donor-spindash"));
        audioManager.registerDonorLoader("s3k", donorLoader, EMPTY_DAC, donorConfig);
        audioManager.registerDonorSound(GameSound.SPINDASH_CHARGE, "s3k", 0xE0);

        audioManager.playSfx(GameSound.SPINDASH_CHARGE, 1.0f);

        assertNotNull(backend.lastDonorConfig, "Donor config should be passed to backend");
        assertEquals(SmpsSequencerConfig.TempoMode.OVERFLOW, backend.lastDonorConfig.getTempoMode());
    }

    @Test
    public void presentationFactoryPreservesDonorRouteAndSequencerConfig() {
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(() -> true, handlers);
        SmpsSequencerConfig donorConfig =
                new SmpsSequencerConfig.Builder()
                        .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                        .build();
        SmpsAssetKey key = new SmpsAssetKey(
                "s2", SmpsAssetKey.Route.DONOR_ID, 0xE0, null);
        factory.warmSmpsSfxAsset(
                key, new StubSmpsData("donor-spindash"),
                EMPTY_DAC, donorConfig);
        ResolvedSmpsSfxSource source = factory.resolveSmpsSfx(
                1, key, 1 << 16, 0x70, 0, 0, 2_048);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> {
                });

        registry.apply(new AudioPresentationCommand.AddSmpsSfx(source));

        SmpsCompositeVoice voice =
                (SmpsCompositeVoice) registry.orderedVoiceAt(0);
        assertEquals(SmpsSequencerConfig.TempoMode.OVERFLOW,
                voice.driver().captureSnapshot().sequencers().get(0)
                        .config().getTempoMode());
        assertEquals("s2",
                voice.driver().captureSnapshot().sequencers().get(0)
                        .source().donorGameId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"s1", "s2"})
    void hostSessionS3kDonorMusicAndSfxUseSharedOwnersOnBothPaths(
            String hostGameId) {
        HeadlessSmpsAudioBackend realBackend =
                new HeadlessSmpsAudioBackend(
                        SonicConfigurationService.getInstance(), null);
        audioManager.setBackend(realBackend);
        audioManager.setAudioProfile(
                new HostAudioProfile(hostGameId, new StubSmpsLoader()));
        audioManager.setRom(null);

        StubSmpsLoader donor = new StubSmpsLoader();
        StubSmpsData music = new StubSmpsData("s3k-donor-music");
        music.setId(0x21);
        StubSmpsData sfx = new StubSmpsData("s3k-donor-sfx");
        sfx.setId(0xA4);
        donor.musicResults.put(0x21, music);
        donor.sfxResults.put(0xA4, sfx);
        Sonic3kAudioProfile donorProfile = new Sonic3kAudioProfile();
        audioManager.registerDonorLoader(
                "s3k", donor, EMPTY_DAC,
                donorProfile.getSequencerConfig(), donorProfile);

        audioManager.playDonorMusic("s3k", 0x21);
        audioManager.playDonorSfx("s3k", 0xA4);
        audioManager.presentOuterFrame(PresentationMode.FORWARD);

        var legacyOwner = realBackend.legacyCoordFlagHandlersForTesting();
        var presentationOwner =
                audioManager.presentationCoordFlagHandlersForTesting();
        assertNotSame(legacyOwner, presentationOwner,
                "audible and shadow sessions must not share mutable state");
        assertNotSame(legacyOwner.state(), presentationOwner.state());
        assertSame(legacyOwner.handlerFor("s3k"),
                legacyOwner.handlerFor("s3k"));
        assertSame(presentationOwner.handlerFor("s3k"),
                presentationOwner.handlerFor("s3k"));

        var snapshot = realBackend.captureLogicalSnapshot();
        assertNotNull(snapshot.musicDriver());
        assertEquals(2, snapshot.musicDriver().sequencers().size(),
                "donor SFX must join donor music's driver");
        var musicHandler = snapshot.musicDriver().sequencers().get(0)
                .config().getCoordFlagHandler();
        var sfxHandler = snapshot.musicDriver().sequencers().get(1)
                .config().getCoordFlagHandler();
        assertSame(legacyOwner.handlerFor("s3k"), musicHandler);
        assertSame(musicHandler, sfxHandler,
                "legacy donor music and SFX must share the counter owner");
    }

    // --- Test doubles ---

    /** Minimal SmpsData stub that carries a name for assertion. */
    private static class StubSmpsData extends AbstractSmpsData {
        final String name;

        StubSmpsData(String name) {
            super(new byte[0], 0);
            this.name = name;
        }

        @Override protected void parseHeader() {}
        @Override public byte[] getVoice(int voiceId) { return new byte[0]; }
        @Override public byte[] getPsgEnvelope(int id) { return new byte[0]; }
        @Override public int read16(int offset) { return 0; }
        @Override public int getBaseNoteOffset() { return 0; }

        @Override
        public String toString() {
            return name;
        }
    }

    /** SmpsLoader stub that returns pre-configured results by sfxId. */
    private static class StubSmpsLoader implements SmpsLoader {
        final Map<Integer, AbstractSmpsData> musicResults = new HashMap<>();
        final Map<Integer, AbstractSmpsData> sfxResults = new HashMap<>();

        @Override
        public AbstractSmpsData loadMusic(int musicId) {
            return musicResults.get(musicId);
        }

        @Override
        public AbstractSmpsData loadSfx(int sfxId) {
            return sfxResults.get(sfxId);
        }

        @Override
        public AbstractSmpsData loadSfx(String sfxName) {
            return null;
        }

        @Override
        public DacData loadDacData() {
            return EMPTY_DAC;
        }
    }

    private static final class HostAudioProfile extends StubAudioProfile {
        private final String gameId;

        private HostAudioProfile(String gameId, SmpsLoader loader) {
            super(loader);
            this.gameId = gameId;
        }

        @Override
        public String presentationGameId() {
            return gameId;
        }

        @Override
        public SmpsSequencerConfig getSequencerConfig() {
            return new SmpsSequencerConfig.Builder().build();
        }
    }

    /** Stub audio profile that returns the given loader. */
    private static class StubAudioProfile implements GameAudioProfile {
        private final SmpsLoader loader;

        StubAudioProfile(SmpsLoader loader) {
            this.loader = loader;
        }

        @Override
        public SmpsLoader createSmpsLoader(Rom rom) {
            return loader;
        }

        @Override
        public SmpsSequencerConfig getSequencerConfig() {
            return null;
        }

        @Override
        public int getSpeedShoesOnCommandId() { return -1; }

        @Override
        public int getSpeedShoesOffCommandId() { return -1; }

        @Override
        public int getInvincibilityMusicId() { return -1; }

        @Override
        public int getExtraLifeMusicId() { return -1; }

        @Override
        public int getDrowningMusicId() { return -1; }

        @Override
        public Map<GameSound, Integer> getSoundMap() {
            return Map.of();
        }
    }

    /** Records the last SFX played for assertion. */
    private static class RecordingBackend extends NullAudioBackend {
        String lastSfxName;
        String lastFallbackName;
        SmpsSequencerConfig lastDonorConfig;

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch) {
            lastSfxName = data.toString();
            lastFallbackName = null;
            lastDonorConfig = null;
        }

        @Override
        public void playSfxSmps(AbstractSmpsData data, DacData dacData, float pitch,
                                SmpsSequencerConfig config) {
            lastSfxName = data.toString();
            lastFallbackName = null;
            lastDonorConfig = config;
        }

        @Override
        public void playSfx(String sfxName, float pitch) {
            lastFallbackName = sfxName;
            lastSfxName = null;
            lastDonorConfig = null;
        }
    }
}
