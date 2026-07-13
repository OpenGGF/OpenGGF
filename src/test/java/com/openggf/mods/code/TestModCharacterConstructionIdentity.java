package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.SonicRespawnStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestModCharacterConstructionIdentity {
    private static final CharacterKey EXPECTED = CharacterKey.mod("owner-a", "runner");
    private static final CharacterKey SPOOFED = CharacterKey.mod("owner-b", "runner");

    @AfterEach
    void tearDown() {
        GameModuleRegistry.reset();
    }

    @Test
    void spoofedOwnerIsRejectedAfterOnlyExpectedOwnerProviderQueriesAndBeforePublication() {
        Fixture fixture = fixture(SpoofedKeySprite::new);
        SpriteManager sprites = new SpriteManager(fixture.config());

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> GameplayTeamBootstrap.registerActiveTeam(
                        fixture.module(), sprites, fixture.config()));

        assertEquals("owner-a", aborted.owner());
        assertEquals(Set.of("owner-a"), aborted.disabledOwners());
        assertEquals(List.of("profile:owner-a:runner", "init:owner-a:runner"),
                fixture.provider().queries);
        assertEquals(Set.of("owner-a"), fixture.disabled());
        assertNull(sprites.getSprite(EXPECTED.persisted()));
        assertNull(sprites.getSprite(SPOOFED.persisted()));
    }

    @Test
    void nullDeclaredKeyIsAnExpectedOwnerCallbackFailureAfterScopedProviderLookup() {
        Fixture fixture = fixture(NullKeySprite::new);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> fixture.definition().spriteFactory().create(EXPECTED.persisted(), 0, 0));

        assertEquals("owner-a", aborted.owner());
        assertEquals(List.of("profile:owner-a:runner", "init:owner-a:runner"),
                fixture.provider().queries);
        assertEquals(Set.of("owner-a"), fixture.disabled());
    }

    @Test
    void throwingDeclaredKeyIsAnExpectedOwnerCallbackFailureAfterScopedProviderLookup() {
        Fixture fixture = fixture(ThrowingKeySprite::new);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> fixture.definition().spriteFactory().create(EXPECTED.persisted(), 0, 0));

        assertEquals("owner-a", aborted.owner());
        assertEquals(List.of("profile:owner-a:runner", "init:owner-a:runner"),
                fixture.provider().queries);
        assertEquals(Set.of("owner-a"), fixture.disabled());
    }

    @Test
    void keyThatMutatesBetweenConstructionAndReturnIsRejectedByExpectedOwner() {
        Fixture fixture = fixture(MutatingKeySprite::new);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> fixture.definition().spriteFactory().create(EXPECTED.persisted(), 0, 0));

        assertEquals("owner-a", aborted.owner());
        assertEquals(List.of("profile:owner-a:runner", "init:owner-a:runner"),
                fixture.provider().queries);
        assertEquals(Set.of("owner-a"), fixture.disabled());
    }

    @Test
    void runtimeRebindUsesImmutableConstructionIdentityAfterPublicKeyChanges() {
        SwitchableKeySprite[] published = new SwitchableKeySprite[1];
        Fixture fixture = fixture(() -> published[0] = new SwitchableKeySprite());
        fixture.definition().spriteFactory().create(EXPECTED.persisted(), 0, 0);
        published[0].spoof = true;
        GameModule replacement = mock(GameModule.class);
        when(replacement.getIdentifier()).thenReturn("replacement-host");
        when(replacement.getPhysicsProvider()).thenReturn(fixture.provider());
        GameModuleRegistry.setCurrent(replacement);

        published[0].resolveAnimationId(CanonicalAnimation.WAIT);

        assertEquals(List.of(
                "profile:owner-a:runner", "init:owner-a:runner",
                "profile:owner-a:runner", "init:owner-a:runner"), fixture.provider().queries);
    }

    @Test
    void fieldBackedDeclaredKeyIsValidatedOnlyAfterConstructionCompletes() {
        Fixture fixture = fixture(FieldBackedKeySprite::new);

        AbstractPlayableSprite sprite = fixture.definition().spriteFactory()
                .create(EXPECTED.persisted(), 0, 0);

        assertSame(EXPECTED, sprite.characterKey());
        assertEquals(List.of("profile:owner-a:runner", "init:owner-a:runner"),
                fixture.provider().queries);
        assertEquals(Set.of(), fixture.disabled());
    }

    @Test
    void throwingCharacterArtSupplierUsesExpectedOwnerFaultBoundary() {
        Fixture fixture = fixture(FieldBackedKeySprite::new, code -> {
            throw new java.io.IOException("art failure");
        });

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> fixture.definition().artSupplier().load(EXPECTED.persisted()));

        assertEquals("owner-a", aborted.owner());
        assertEquals(Set.of("owner-a"), fixture.disabled());
    }

    @Test
    void nullCharacterArtAndPaletteResultsUseExpectedOwnerFaultBoundary() {
        Fixture nullArt = fixture(FieldBackedKeySprite::new, code -> null);
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> nullArt.definition().artSupplier().load(EXPECTED.persisted()));

        Fixture nullPalette = fixture(FieldBackedKeySprite::new, code -> SpriteArtSet.EMPTY,
                code -> null);
        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> nullPalette.definition().paletteSupplier().load(EXPECTED.persisted()));
    }

    @Test
    void throwingAbilityHookUsesExpectedOwnerFaultBoundary() {
        Fixture fixture = fixture(ThrowingAbilitySprite::new);
        AbstractPlayableSprite sprite = fixture.definition().spriteFactory()
                .create(EXPECTED.persisted(), 0, 0);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> com.openggf.sprites.playable.CharacterRuntimeHooks.activateAbility(sprite));

        assertEquals("owner-a", aborted.owner());
        assertEquals(Set.of("owner-a"), fixture.disabled());
    }

    @Test
    void constructionScopeRestoresAfterNestedAndExceptionalFactories() {
        assertThrows(IllegalStateException.class, () -> CharacterConstructionScope.call(EXPECTED,
                () -> { throw new IllegalStateException("factory"); }));
        assertSame(SPOOFED, CharacterConstructionScope.bindExpectedOrDefault(SPOOFED),
                "exceptional scope must not leak its expected owner");

        CharacterConstructionScope.call(EXPECTED, () -> {
            assertSame(EXPECTED, CharacterConstructionScope.call(EXPECTED,
                    () -> CharacterConstructionScope.bindExpectedOrDefault(SPOOFED)));
            assertThrows(IllegalArgumentException.class,
                    () -> CharacterConstructionScope.call(SPOOFED, () -> null));
            assertSame(EXPECTED, CharacterConstructionScope.bindExpectedOrDefault(SPOOFED),
                    "rejected nested scope must preserve its outer expected owner");
            return null;
        });
        assertSame(SPOOFED, CharacterConstructionScope.bindExpectedOrDefault(SPOOFED),
                "nested scope must restore the unbound caller state");
    }

    @Test
    void callbackBoundaryScopeDoesNotLeakAfterSuccessfulOrThrowingConstruction() {
        java.util.concurrent.atomic.AtomicInteger callbackCalls =
                new java.util.concurrent.atomic.AtomicInteger();
        CharacterConstructionScope.CallbackInvoker boundary = callback -> {
            callbackCalls.incrementAndGet();
            return callback.get();
        };
        AbstractPlayableSprite scoped = CharacterConstructionScope.call(
                EXPECTED, boundary, FieldBackedKeySprite::new);

        assertFalse(com.openggf.sprites.playable.CharacterRuntimeHooks.activateAbility(scoped));
        assertEquals(1, callbackCalls.get());
        assertThrows(IllegalStateException.class, () -> CharacterConstructionScope.call(
                EXPECTED, boundary, () -> { throw new IllegalStateException("factory"); }));

        AbstractPlayableSprite unscoped = new FieldBackedKeySprite();
        assertFalse(com.openggf.sprites.playable.CharacterRuntimeHooks.activateAbility(unscoped));
        assertEquals(1, callbackCalls.get(),
                "construction callback boundary must be removed after success and failure");
    }

    private static Fixture fixture(Supplier<? extends AbstractPlayableSprite> spriteFactory) {
        return fixture(spriteFactory, code -> SpriteArtSet.EMPTY);
    }

    private static Fixture fixture(Supplier<? extends AbstractPlayableSprite> spriteFactory,
                                   CharacterDefinition.ArtSupplier artSupplier) {
        return fixture(spriteFactory, artSupplier, null);
    }

    private static Fixture fixture(Supplier<? extends AbstractPlayableSprite> spriteFactory,
                                   CharacterDefinition.ArtSupplier artSupplier,
                                   CharacterDefinition.PaletteSupplier paletteSupplier) {
        RecordingPhysicsProvider provider = new RecordingPhysicsProvider();
        GameModule base = mock(GameModule.class);
        when(base.getIdentifier()).thenReturn("identity-host");
        when(base.getPlayableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        when(base.getPhysicsProvider()).thenReturn(provider);
        when(base.supportsSidekick()).thenReturn(true);
        CharacterDefinition contributed = new CharacterDefinition(EXPECTED, "Runner",
                (code, x, y) -> spriteFactory.get(), SonicRespawnStrategy::new,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                artSupplier, paletteSupplier);
        Set<String> disabled = new java.util.LinkedHashSet<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), disabled::addAll);
        GameModule decorated = new ModBackedGamePatch(
                ModRegistrationPlan.characterOnly("owner-a", "s2", Map.of(EXPECTED, contributed)),
                boundary).apply(base, new PatchContext(ignored -> null,
                mock(SonicConfigurationService.class)));
        GameModuleRegistry.setCurrent(decorated);
        CharacterDefinition definition = decorated.getPlayableCharacterRegistry()
                .find(EXPECTED).orElseThrow();
        return new Fixture(decorated, definition, provider, disabled,
                config(EXPECTED.persisted()));
    }

    private static SonicConfigurationService config(String main) {
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn(main);
        when(config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE)).thenReturn(null);
        return config;
    }

    private record Fixture(GameModule module, CharacterDefinition definition,
                           RecordingPhysicsProvider provider, Set<String> disabled,
                           SonicConfigurationService config) { }

    private static final class RecordingPhysicsProvider implements PhysicsProvider {
        private final List<String> queries = new ArrayList<>();

        @Override public PhysicsProfile getProfile(String characterType) {
            queries.add("profile:" + characterType);
            return PhysicsProfile.SONIC_2_SONIC;
        }

        @Override public PhysicsProfile getInitProfile(String characterType) {
            queries.add("init:" + characterType);
            return null;
        }

        @Override public PhysicsModifiers getModifiers() { return PhysicsModifiers.STANDARD; }
        @Override public GameRules getRules() { return GameRules.SONIC_2; }
    }

    private abstract static class TestSprite extends AbstractPlayableSprite {
        private TestSprite() { super("runtime", (short) 0, (short) 0); }
        @Override public void draw() { }
        @Override public void defineSpeeds() {
            runAccel = 12; runDecel = 128; friction = 12; max = 1536; jump = 1664;
            slopeRunning = 32; slopeRollingDown = 80; slopeRollingUp = 20;
            rollDecel = 32; minStartRollSpeed = 128; minRollSpeed = 128;
            maxRoll = 4096; rollHeight = 28; runHeight = 38;
        }
        @Override protected void createSensorLines() { }
    }

    private static final class SpoofedKeySprite extends TestSprite {
        @Override public CharacterKey characterKey() { return SPOOFED; }
    }

    private static final class NullKeySprite extends TestSprite {
        @Override public CharacterKey characterKey() { return null; }
    }

    private static final class ThrowingKeySprite extends TestSprite {
        @Override public CharacterKey characterKey() { throw new IllegalStateException("identity"); }
    }

    private static final class MutatingKeySprite extends TestSprite {
        private int calls;
        @Override public CharacterKey characterKey() {
            return calls++ == 0 ? EXPECTED : SPOOFED;
        }
    }

    private static final class SwitchableKeySprite extends TestSprite {
        private boolean spoof;
        @Override public CharacterKey characterKey() { return spoof ? SPOOFED : EXPECTED; }
    }

    private static final class FieldBackedKeySprite extends TestSprite {
        private final CharacterKey key;

        private FieldBackedKeySprite() {
            super();
            key = EXPECTED;
        }

        @Override public CharacterKey characterKey() { return key; }
    }

    private static final class ThrowingAbilitySprite extends TestSprite {
        @Override public CharacterKey characterKey() { return EXPECTED; }
        @Override protected boolean onAbilityActivate(
                boolean up, boolean down, boolean left, boolean right) {
            throw new IllegalStateException("ability failure");
        }
    }
}
