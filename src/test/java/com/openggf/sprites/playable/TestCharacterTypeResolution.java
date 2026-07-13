package com.openggf.sprites.playable;

import com.openggf.game.CharacterKey;
import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestCharacterTypeResolution {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void builtinSpritesExposeStableBuiltinKeysIndependentOfRuntimeCode() {
        install(new RecordingPhysicsProvider());

        assertSame(CharacterKey.SONIC, new Sonic("sonic_p2", (short) 0, (short) 0).characterKey());
        assertSame(CharacterKey.TAILS, new Tails("tails_p3", (short) 0, (short) 0).characterKey());
        assertSame(CharacterKey.KNUCKLES, new Knuckles("knuckles_preview", (short) 0, (short) 0).characterKey());
        assertSame(CharacterKey.SONIC, new BaseSprite("anything").characterKey());
    }

    @Test
    void modCharacterQueriesOnlyItsFullyOwnedPersistedKey() {
        RecordingPhysicsProvider provider = new RecordingPhysicsProvider();
        install(provider);

        ModOwnerASprite sprite = CharacterConstructionScope.call(
                CharacterKey.mod("owner-a", "modchar"),
                () -> new ModOwnerASprite("owner-a:modchar_p2"));

        assertEquals(CharacterKey.mod("owner-a", "modchar"), sprite.characterKey());
        assertEquals(List.of("profile:owner-a:modchar", "init:owner-a:modchar"), provider.queries);
    }

    @Test
    void equalLocalNamesFromDifferentOwnersRemainDistinctProviderQueries() {
        RecordingPhysicsProvider provider = new RecordingPhysicsProvider();
        install(provider);

        ModOwnerASprite ownerA = CharacterConstructionScope.call(
                CharacterKey.mod("owner-a", "modchar"),
                () -> new ModOwnerASprite("runtime-a"));
        ModOwnerBSprite ownerB = CharacterConstructionScope.call(
                CharacterKey.mod("owner-b", "modchar"),
                () -> new ModOwnerBSprite("runtime-b"));

        assertEquals(CharacterKey.mod("owner-a", "modchar"), ownerA.characterKey());
        assertEquals(CharacterKey.mod("owner-b", "modchar"), ownerB.characterKey());
        assertEquals(List.of(
                "profile:owner-a:modchar", "init:owner-a:modchar",
                "profile:owner-b:modchar", "init:owner-b:modchar"), provider.queries);
    }

    private static void install(PhysicsProvider provider) {
        GameModule module = mock(GameModule.class);
        when(module.getIdentifier()).thenReturn("identity-test");
        when(module.getPhysicsProvider()).thenReturn(provider);
        GameModuleRegistry.setCurrent(module);
    }

    private static final class RecordingPhysicsProvider implements PhysicsProvider {
        private final List<String> queries = new ArrayList<>();

        @Override
        public PhysicsProfile getProfile(String characterType) {
            queries.add("profile:" + characterType);
            return PhysicsProfile.SONIC_2_SONIC;
        }

        @Override
        public PhysicsProfile getInitProfile(String characterType) {
            queries.add("init:" + characterType);
            return null;
        }

        @Override public PhysicsModifiers getModifiers() { return PhysicsModifiers.STANDARD; }
        @Override public GameRules getRules() { return GameRules.SONIC_2; }
    }

    private static class BaseSprite extends AbstractPlayableSprite {
        private BaseSprite(String code) {
            super(code, (short) 0, (short) 0);
        }

        @Override public void draw() { }

        @Override
        public void defineSpeeds() {
            runAccel = 12;
            runDecel = 128;
            friction = 12;
            max = 1536;
            jump = 1664;
            slopeRunning = 32;
            slopeRollingDown = 80;
            slopeRollingUp = 20;
            rollDecel = 32;
            minStartRollSpeed = 128;
            minRollSpeed = 128;
            maxRoll = 4096;
            rollHeight = 28;
            runHeight = 38;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
        }

        @Override protected void createSensorLines() { }
    }

    private static final class ModOwnerASprite extends BaseSprite {
        private ModOwnerASprite(String code) { super(code); }
        @Override public CharacterKey characterKey() { return CharacterKey.mod("owner-a", "modchar"); }
    }

    private static final class ModOwnerBSprite extends BaseSprite {
        private ModOwnerBSprite(String code) { super(code); }
        @Override public CharacterKey characterKey() { return CharacterKey.mod("owner-b", "modchar"); }
    }
}
