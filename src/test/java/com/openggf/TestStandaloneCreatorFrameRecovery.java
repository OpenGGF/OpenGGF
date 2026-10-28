package com.openggf;

import com.openggf.audio.GameAudioProfile;
import com.openggf.game.AbstractStandaloneGameModule;
import com.openggf.game.Game;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameModule;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.StandaloneRecoveryTestAccess;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestStandaloneCreatorFrameRecovery {
    private static final GameDataSource NO_ROM = new GameDataSource() {
        @Override public Optional<com.openggf.data.Rom> rom() { return Optional.empty(); }
        @Override public InputStream openAsset(String path) throws IOException {
            throw new IOException("No fixture assets");
        }
        @Override public String identity() { return "standalone-recovery-fixture"; }
    };

    @BeforeEach void setUp() { TestEnvironment.resetAll(); }
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void artCallbackFailureDiscardsFrameAndReturnsToTitle(boolean preparedSheets) {
        var recovery = new Recovery();
        ObjectArtProvider art = mock(ObjectArtProvider.class);
        when(art.isReady()).thenThrow(new IllegalStateException("art callback failed"));
        Map<String, BakedSheetReader.BakedSheet> prepared = preparedSheets
                ? Map.of("owner:sheet", new BakedSheetReader.BakedSheet(
                        new Pattern[] {new Pattern()}, List.of(), Optional.empty()))
                : Map.of();
        GameModule wrapped = StandaloneRecoveryTestAccess.wrap(
                module(mock(Game.class), art), recovery.boundary, prepared);
        var rendering = new ObjectRenderManager(wrapped.getObjectArtProvider());
        var continued = new AtomicBoolean();

        boolean completed = Engine.runFrameWithModAbort(() -> {
            rendering.isReady();
            continued.set(true);
        }, recovery.discarded::incrementAndGet, recovery.title::incrementAndGet);

        recovery.assertRecovered(completed, continued.get());
    }

    @Test
    void musicCallbackFailureDiscardsFrameAndReturnsToTitle() throws Exception {
        var recovery = new Recovery();
        Game game = mock(Game.class);
        when(game.getMusicId(anyInt())).thenThrow(new IllegalStateException("music callback failed"));
        GameModule wrapped = StandaloneRecoveryTestAccess.wrap(
                module(game, null), recovery.boundary, Map.of());
        var gameplay = SessionManager.openGameplaySession(wrapped, wrapped, NO_ROM, null);
        GameplaySessionFactory.attachManagers(gameplay, EngineServices.current());
        gameplay.getLevelManager().initGameModule(0);
        var continued = new AtomicBoolean();

        boolean completed = Engine.runFrameWithModAbort(() -> {
            gameplay.getLevelManager().getCurrentLevelMusicId();
            continued.set(true);
        }, recovery.discarded::incrementAndGet, recovery.title::incrementAndGet);

        recovery.assertRecovered(completed, continued.get());
    }

    private static GameModule module(Game game, ObjectArtProvider art) {
        ZoneRegistry zones = new com.openggf.game.sonic2.Sonic2GameModule().getZoneRegistry();
        return new AbstractStandaloneGameModule() {
            @Override public String getIdentifier() { return "owner"; }
            @Override public Game createGame(GameDataSource source) { return game; }
            @Override public ZoneRegistry getZoneRegistry() { return zones; }
            @Override public ObjectArtProvider getObjectArtProvider() { return art; }
            @Override public GameRules getRules() { return GameRules.SONIC_2; }
            @Override public GameAudioProfile getAudioProfile() { return GameAudioProfile.silentNative(); }
        };
    }

    private static final class Recovery {
        private final ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        private final AtomicReference<Set<String>> disabled = new AtomicReference<>(Set.of());
        private final ModFaultBoundary boundary = new ModFaultBoundary(
                Map.of("dependent", Set.of("owner")), findings,
                owners -> { disabled.set(owners); return new ModStateSaveResult.Saved(); }, owners -> { });
        private final AtomicInteger discarded = new AtomicInteger();
        private final AtomicInteger title = new AtomicInteger();

        private void assertRecovered(boolean completed, boolean continued) {
            assertAll(
                    () -> assertFalse(completed),
                    () -> assertFalse(continued),
                    () -> assertEquals(Set.of("owner", "dependent"), disabled.get()),
                    () -> assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code()),
                    () -> assertFalse(boundary.isOwnerAvailable("owner")),
                    () -> assertEquals(1, discarded.get()),
                    () -> assertEquals(1, title.get()));
        }
    }
}
