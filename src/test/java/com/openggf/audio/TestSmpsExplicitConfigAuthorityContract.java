package com.openggf.audio;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static com.openggf.audio.TestSmpsConfigAuthorityContract.*;
import static org.junit.jupiter.api.Assertions.*;

/** Additive manager authority, independently of singleton, configured root, receiver and restore sink. */
class TestSmpsExplicitConfigAuthorityContract {
    @Test
    void explicitConstructionValidatesConfigSinkAuthorityThenRateWithoutRealizingSingleton() {
        try (var scope = new Scope()) {
            scope.singleton(null);
            scope.root(null);
            var config = configuration();
            assertEquals("configService", assertThrows(NullPointerException.class,
                    () -> new HeadlessSmpsAudioBackend(null, null, -1, null, null)).getMessage());
            assertEquals("musicRestoreSink", assertThrows(NullPointerException.class,
                    () -> new HeadlessSmpsAudioBackend(config, null, -1, null, null)).getMessage());
            assertEquals("legacyConfigAuthority", assertThrows(NullPointerException.class,
                    () -> new HeadlessSmpsAudioBackend(config, null, -1, () -> {}, null)).getMessage());
            assertEquals("configService", assertThrows(NullPointerException.class,
                    () -> new LWJGLAudioBackend(null, null, null, null)).getMessage());
            assertEquals("musicRestoreSink", assertThrows(NullPointerException.class,
                    () -> new LWJGLAudioBackend(config, null, null, null)).getMessage());
            assertEquals("legacyConfigAuthority", assertThrows(NullPointerException.class,
                    () -> new LWJGLAudioBackend(config, null, () -> {}, null)).getMessage());
            var a = scope.manager(null);
            assertThrows(IllegalArgumentException.class,
                    () -> new HeadlessSmpsAudioBackend(config, null, -1, () -> {}, a));
            scope.backends.add(new HeadlessSmpsAudioBackend(config, null, 1, () -> {}, a));
            scope.backends.add(new LWJGLAudioBackend(config, null, () -> {}, a));
            assertNull(scope.singleton());
            assertNull(scope.root());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"music", "mixed", "standalone"})
    void explicitAOwnsRealCoordinationAcrossRootSingletonAndReceivingManagerChanges(String route) {
        try (var scope = new Scope()) {
            var ap = new RecordingProfile();
            var sp = new RecordingProfile();
            var bp = new RecordingProfile();
            var a = scope.manager(ap);
            var s = scope.manager(sp);
            var b = scope.manager(bp);
            scope.singleton(s);
            scope.configureRoot(b);
            var restores = new AtomicInteger();
            var backend = explicit(scope, a, restores::incrementAndGet);
            // An unrelated receiver must not rewrite the constructor's authority or sink.
            scope.manager(new Sonic3kAudioProfile()).setBackend(backend);
            backend.setAudioProfile(new Sonic3kAudioProfile());
            var first = source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertEquals(3, ap.owner.state().spindashRevCounter());
            assertEquals(3, fm(first).keyOffset);
            scope.configureRoot(scope.manager(new Sonic3kAudioProfile()));
            scope.singleton(scope.manager(new Sonic3kAudioProfile()));
            var next = source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertEquals(6, ap.owner.state().spindashRevCounter());
            assertEquals(12, fm(next).keyOffset);
            assertSame(ap.owner.handlerFor("s3k"), next.getConfig().getCoordFlagHandler());
            assertEquals(0, sp.owner.state().spindashRevCounter());
            assertEquals(0, bp.owner.state().spindashRevCounter());
            backend.playSfxSmps(sfx(Sonic3kSfx.RING_LEFT.id, (byte) 0xF2),
                    AudioTestFixtures.EMPTY_DAC, 1, S3K);
            assertEquals(0, ap.owner.state().spindashRevCounter(), "ordinary SFX resets A");
            source(backend, route, incrementProgram(route), S3K);
            render(backend, route);
            assertEquals(3, ap.owner.state().spindashRevCounter());
            AbstractSmpsData reset = route.equals("music")
                    ? music((byte) 0xFF, (byte) 0x07, (byte) 0xA0, (byte) 0x40)
                    : sfx(Sonic3kSfx.SLIDE_SKID_LOUD.id, (byte) 0xFF, (byte) 0x07, (byte) 0xA0, (byte) 0x40);
            source(backend, route, reset, S3K);
            assertEquals(3, ap.owner.state().spindashRevCounter());
            render(backend, route);
            assertEquals(0, ap.owner.state().spindashRevCounter(), "FF07 executes in A");
            first.restorePreviousMusic();
            next.restorePreviousMusic();
            assertEquals(2, restores.get(), "R remains independent of A, S, B and receiver");
            assertEquals(0, a.commandTimeline().entryCount());
            assertEquals(0, s.commandTimeline().entryCount());
            assertEquals(0, b.commandTimeline().entryCount());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"reset", "replacement"})
    void laterBindingUsesACurrentOwnerWhileOldSnapshotsAndSinkRemainRecorded(String retirement) {
        try (var scope = new Scope()) {
            var ap = new RecordingProfile();
            var a = scope.manager(ap);
            scope.singleton(scope.manager(new Sonic3kAudioProfile()));
            scope.configureRoot(scope.manager(new Sonic3kAudioProfile()));
            var restores = new AtomicInteger();
            var backend = explicit(scope, a, restores::incrementAndGet);
            var old = source(backend, "music", incrementProgram("music"), S3K);
            render(backend, "music");
            var owner = ap.owner;
            var snapshot = backend.musicDriverForTesting().captureSnapshot();
            if (retirement.equals("reset")) a.resetState();
            else a.setBackend(new NullAudioBackend());
            a.setAudioProfile(ap);
            render(backend, "music");
            assertSame(old, backend.stateForTesting().currentSmps());
            assertEquals(0, owner.state().spindashRevCounter(), "retirement clears old mutable state");
            var next = source(backend, "music", incrementProgram("music"), S3K);
            render(backend, "music");
            assertNotSame(owner, ap.owner);
            assertNotSame(old.getConfig().getCoordFlagHandler(), next.getConfig().getCoordFlagHandler());
            assertEquals(3, ap.owner.state().spindashRevCounter());
            assertSame(old.getConfig(), snapshot.sequencers().getFirst().config());
            var recreated = new SmpsDriver();
            recreated.restoreSnapshot(snapshot);
            var restored = recreated.sequencersForTesting().getFirst();
            assertSame(old.getConfig(), restored.getConfig());
            assertSame(old.getAudioManager(), restored.getAudioManager());
            restored.restorePreviousMusic();
            assertEquals(1, restores.get());
            var otherRestores = new AtomicInteger();
            recreated.restoreSnapshot(snapshot, new SmpsDriverSnapshot.DependencyResolver() {
                @Override public AbstractSmpsData resolveSmpsData(SmpsDriverSnapshot.SequencerEntry entry) {
                    return entry.smpsData();
                }
                @Override public DacData resolveDacData(SmpsDriverSnapshot.SequencerEntry entry) {
                    return entry.dacData();
                }
                @Override public MusicRestoreSink resolveAudioManager(SmpsDriverSnapshot.SequencerEntry entry) {
                    return otherRestores::incrementAndGet;
                }
                @Override public SmpsSequencerConfig resolveConfig(SmpsDriverSnapshot.SequencerEntry entry) {
                    return next.getConfig();
                }
            });
            var overridden = recreated.sequencersForTesting().getFirst();
            assertSame(next.getConfig(), overridden.getConfig());
            overridden.restorePreviousMusic();
            assertEquals(1, otherRestores.get());
            assertEquals(1, restores.get());
        }
    }

    @Test
    void explicitMissingRegistrationNeverFallsBackAndOrdinaryMusicStillBypasses() {
        try (var scope = new Scope()) {
            var singletonProfile = new RecordingProfile();
            scope.singleton(scope.manager(singletonProfile));
            var backend = explicit(scope, scope.manager(null), () -> {});
            assertEquals("no coord-flag handler registered for s3k", assertThrows(IllegalArgumentException.class,
                    () -> source(backend, "music", incrementProgram("music"), S3K)).getMessage());
            assertEquals(0, singletonProfile.owner.state().spindashRevCounter());
            backend.playSmps(music((byte) 0xA0, (byte) 0x40), AudioTestFixtures.EMPTY_DAC);
            assertSame(S3K, backend.stateForTesting().currentSmps().getConfig());
            assertThrows(IllegalArgumentException.class, () -> backend.playSfxSmps(
                    sfx(Sonic3kSfx.SPINDASH.id, (byte) 0xE9), AudioTestFixtures.EMPTY_DAC, 1, S3K));
            var noHandler = new SmpsSequencerConfig.Builder().build();
            backend.playSfxSmps(sfx(Sonic3kSfx.SPINDASH.id, (byte) 0xF2),
                    AudioTestFixtures.EMPTY_DAC, 1, noHandler);
            assertNull(backend.stateForTesting().musicSequencers().getLast().getConfig().getCoordFlagHandler());
        }
    }

    @Test
    void explicitSourceRestoresIndependentRealManagerForegroundThroughExplicitIntent() {
        try (var scope = new Scope()) {
            scope.singleton(null);
            var r = AudioManager.getInstance(); // Deliberately create an owned, non-silent logical manager.
            try {
                var loader = new AudioTestFixtures.StubSmpsLoader();
                loader.musicResults.put(0x81, new AudioTestFixtures.StubSmpsData("base"));
                loader.musicResults.put(0x82, new AudioTestFixtures.StubSmpsData("jingle"));
                r.setAudioProfile(new AudioTestFixtures.StubAudioProfile(loader) {
                    @Override public int getExtraLifeMusicId() { return 0x82; }
                    @Override public SmpsSequencerConfig getSequencerConfig() {
                        return com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig.CONFIG;
                    }
                });
                r.setRom(new Rom());
                r.playMusic(0x81);
                r.presentFrame(com.openggf.audio.presentation.PresentationMode.SILENT);
                r.playMusic(0x82);
                r.presentFrame(com.openggf.audio.presentation.PresentationMode.SILENT);
                assertEquals(0x82, r.captureLogicalSnapshot().presentation().activeMusic().musicId());
                var a = scope.manager(new Sonic3kAudioProfile());
                var singleton = scope.manager(new Sonic3kAudioProfile());
                scope.singleton(singleton);
                scope.configureRoot(scope.manager(new Sonic3kAudioProfile()));
                var backend = explicit(scope, a, r);
                var seq = source(backend, "music", incrementProgram("music"), S3K);
                var intents = new java.util.ArrayList<AudioMutatorAccess.Kind>();
                try (var ignored = AudioMutatorAccess.bind(r, intent -> intents.add(intent.kind()))) {
                    seq.restorePreviousMusic();
                }
                assertEquals(java.util.List.of(AudioMutatorAccess.Kind.RESTORE), intents);
                assertEquals(new com.openggf.audio.rewind.AudioCommand.RestoreMusic(
                        com.openggf.audio.rewind.AudioCommand.RestoreCause.EXPLICIT),
                        r.commandTimeline().entries().getLast().command());
                r.presentFrame(com.openggf.audio.presentation.PresentationMode.SILENT);
                assertEquals(0x81, r.captureLogicalSnapshot().presentation().activeMusic().musicId());
                assertEquals(0, a.commandTimeline().entryCount());
                assertEquals(0, singleton.commandTimeline().entryCount());
            } finally { r.destroy(); }
        }
    }

    @ParameterizedTest
    @CsvSource({"music,false", "mixed,false", "standalone,false",
            "music,true", "mixed,true", "standalone,true"})
    @RequiresRom(SonicGame.SONIC_3K)
    void explicitAuthorityMatchesIndependentSessionAndOriginalGoldens(String route, boolean distinct) throws Exception {
        try (var rom = new Rom()) {
            assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
            var oracle = new TestSmpsConfigAuthorityRomContract();
            var control = oracle.observe(rom, route, true, false, distinct);
            var actual = oracle.observe(rom, route, false, true, distinct);
            assertEquals(control, actual);
            assertEquals(TestSmpsConfigAuthorityRomContract.expected(route), actual);
        }
    }

    private static HeadlessSmpsAudioBackend explicit(Scope scope, AudioManager authority, MusicRestoreSink sink) {
        var backend = new HeadlessSmpsAudioBackend(configuration(), null, sink, authority);
        scope.backends.add(backend);
        backend.setAudioProfile(new Sonic3kAudioProfile());
        return backend;
    }
}
