package com.openggf.game.sonic3k;

import com.openggf.game.*;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.SuperState;
import com.openggf.game.sonic2.Sonic2SuperStateController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestSonic3kModSuperFormGate {
    @AfterEach void clearSession() throws Exception { setWorldSession(null); }

    @Test
    void registeredUnsupportedModCharacterCannotTransformFromAirOrMonitor() throws Exception {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        installWorld(PlayableCharacterRegistry.empty().register(key, definition(key)));
        AbstractPlayableSprite player = eligiblePlayer(key);
        Sonic3kSuperStateController controller = new Sonic3kSuperStateController(player);

        assertFalse(controller.activateFromAirAbility());
        assertFalse(controller.activateFromMonitor());
        assertEquals(SuperState.NORMAL, controller.getState());
        assertEquals(50, player.getRingCount());
        verify(player, never()).setSuperSonic(true);
    }

    @Test
    void unknownModCharacterIsDeniedButBuiltinSonicRemainsEligible() throws Exception {
        installWorld(PlayableCharacterRegistry.empty());
        Sonic3kSuperStateController unknown = new Sonic3kSuperStateController(
                eligiblePlayer(CharacterKey.mod("owner", "unknown")));
        assertFalse(unknown.activateFromAirAbility());

        Sonic3kSuperStateController builtin = new Sonic3kSuperStateController(
                eligiblePlayer(CharacterKey.SONIC));
        assertTrue(builtin.activateFromAirAbility());
        assertEquals(SuperState.TRANSFORMING, builtin.getState());
    }

    @Test
    void sharedGateAlsoPreventsSonic2SuperArtOnModCharacters() throws Exception {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        installWorld(PlayableCharacterRegistry.empty().register(key, definition(key)));

        Sonic2SuperStateController controller = new Sonic2SuperStateController(eligiblePlayer(key));

        assertFalse(controller.activateFromAirAbility());
        assertEquals(SuperState.NORMAL, controller.getState());
    }

    @Test
    void mutablePublicIdentityCannotSpoofBuiltinToBypassGate() throws Exception {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        installWorld(PlayableCharacterRegistry.empty().register(key, definition(key)));
        AbstractPlayableSprite player = eligiblePlayer(key);
        when(player.characterKey()).thenReturn(CharacterKey.SONIC);

        Sonic3kSuperStateController controller = new Sonic3kSuperStateController(player);

        assertFalse(controller.activateFromMonitor());
        assertEquals(SuperState.NORMAL, controller.getState());
    }

    @Test
    void unsupportedModIsDeniedFromDebugAndSonic2AutomaticTriggerWithoutMutatingRings()
            throws Exception {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        installWorld(PlayableCharacterRegistry.empty().register(key, definition(key)));
        AbstractPlayableSprite player = eligiblePlayer(key);
        when(player.getAir()).thenReturn(true);
        when(player.isJumping()).thenReturn(true);
        when(player.getYSpeed()).thenReturn((short) 0);

        Sonic3kSuperStateController debug = new Sonic3kSuperStateController(player);
        debug.debugActivate();
        assertEquals(SuperState.NORMAL, debug.getState());
        assertEquals(50, player.getRingCount());
        verify(player, never()).addRings(anyInt());

        Sonic2SuperStateController automatic = new Sonic2SuperStateController(player);
        automatic.update();
        assertEquals(SuperState.NORMAL, automatic.getState());
        assertEquals(50, player.getRingCount());
        verify(player, never()).setSuperSonic(true);
    }

    private static CharacterDefinition definition(CharacterKey key) {
        return new CharacterDefinition(key, "Runner", (code, x, y) -> null, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                code -> SpriteArtSet.EMPTY);
    }

    private static AbstractPlayableSprite eligiblePlayer(CharacterKey key) {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        GameStateManager state = mock(GameStateManager.class);
        when(state.hasAllEmeralds()).thenReturn(true);
        when(player.currentGameState()).thenReturn(state);
        when(player.characterKey()).thenReturn(key);
        when(player.getRingCount()).thenReturn(50);
        try {
            Field identity = AbstractPlayableSprite.class.getDeclaredField("boundCharacterKey");
            identity.setAccessible(true);
            identity.set(player, key);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
        return player;
    }

    private static void installWorld(PlayableCharacterRegistry registry) throws Exception {
        GameModule module = mock(GameModule.class);
        when(module.getIdentifier()).thenReturn("super-gate-test");
        when(module.getPlayableCharacterRegistry()).thenReturn(registry);
        setWorldSession(new WorldSession(module));
    }

    private static void setWorldSession(WorldSession session) throws Exception {
        Field field = SessionManager.class.getDeclaredField("currentWorldSession");
        field.setAccessible(true);
        field.set(null, session);
    }
}
