package com.openggf;

import com.openggf.data.RomManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStandaloneEngineLevelLoad {
    private EngineContext previous;

    @AfterEach void restoreServices() {
        SessionManager.clear();
        if (previous != null) EngineServices.configure(previous);
    }

    @Test
    void noRomStandalonePathStillLoadsFirstZoneAndAct() throws Exception {
        previous = EngineServices.current();
        RomManager roms = mock(RomManager.class);
        EngineContext services = new EngineContext(previous.configuration(), previous.graphics(),
                previous.audio(), roms, previous.profiler(), previous.debugOverlay(),
                previous.playbackDebug(), previous.romDetection(), previous.crossGameFeatures(),
                previous.moduleResolutionService());
        Engine engine = new Engine(services);
        LevelManager levels = mock(LevelManager.class);
        Field field = Engine.class.getDeclaredField("levelManager");
        field.setAccessible(true);
        field.set(engine, levels);

        engine.loadDefaultStartingLevel(false);

        verify(levels).loadZoneAndAct(0, 0);
        verify(roms, never()).isRomAvailable();
    }

    @Test
    void standaloneFailureCleanupClosesPublishedWorldSession() {
        previous = EngineServices.current();
        Engine engine = new Engine(previous);
        var module = mock(com.openggf.game.GameModule.class);
        SessionManager.openGameplaySession(module, module, new com.openggf.game.GameDataSource() {
            @Override public java.util.Optional<com.openggf.data.Rom> rom() {
                return java.util.Optional.empty();
            }
            @Override public java.io.InputStream openAsset(String path) {
                return java.io.InputStream.nullInputStream();
            }
            @Override public String identity() { return "standalone:test"; }
        }, null);

        engine.showStandaloneLaunchError();

        assertNull(SessionManager.getCurrentWorldSession());
        assertThrows(IllegalStateException.class, SessionManager::requireCurrentGameModule);
    }

    @Test
    void standaloneDefaultIsAlwaysDeterministicFirstRegisteredCharacter() {
        com.openggf.game.CharacterKey first = com.openggf.game.CharacterKey.mod("owner", "first");
        com.openggf.game.CharacterKey second = com.openggf.game.CharacterKey.mod("owner", "second");
        com.openggf.game.PlayableCharacterRegistry registry =
                com.openggf.game.PlayableCharacterRegistry.empty()
                        .register(first, definition(first))
                        .register(second, definition(second));

        assertEquals(first, Engine.standaloneDefaultCharacter(registry));
        assertThrows(IllegalStateException.class, () -> Engine.standaloneDefaultCharacter(
                com.openggf.game.PlayableCharacterRegistry.empty()));
    }

    @Test
    void standaloneContinuePinsEverySavedSidekick() throws Exception {
        previous = EngineServices.current();
        Engine engine = new Engine(previous);
        var team = new com.openggf.game.save.SelectedTeam(
                "owner:runner", java.util.List.of("owner:friend", "owner:friend-two"));
        var method = Engine.class.getDeclaredMethod("pinStandaloneTeam",
                com.openggf.game.save.SelectedTeam.class);
        method.setAccessible(true);

        method.invoke(engine, team);

        assertEquals("owner:runner", previous.configuration().getString(
                com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("owner:friend,owner:friend-two", previous.configuration().getString(
                com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE));
    }

    private static com.openggf.game.CharacterDefinition definition(
            com.openggf.game.CharacterKey key) {
        return new com.openggf.game.CharacterDefinition(key, key.persisted(),
                (code, x, y) -> null, null, com.openggf.game.PlayerCharacter.SONIC_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE, false,
                code -> mock(com.openggf.sprites.art.SpriteArtSet.class));
    }
}
