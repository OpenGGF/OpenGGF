package com.openggf.game;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TestCrossGameFeatureProviderRefactor {

    @BeforeEach
    void setUp() {
        // Clear lingering session/runtime state from prior tests in the same fork
        // so resolveHostGameId() falls back to the GameModuleRegistry bootstrap
        // default that this fixture configures via setCurrent().
        SessionManager.clear();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void cleanup() {
        SessionManager.clear();
        CrossGameFeatureProvider.getInstance().resetState();
        GameModuleRegistry.reset();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
    }

    @Test
    void sameGameDonationIsDisabled() {
        GameModuleRegistry.setCurrent(new com.openggf.game.sonic2.Sonic2GameModule());
        try {
            CrossGameFeatureProvider.getInstance().initialize("s2");
        } catch (Exception e) {
            // ROM not available, but guard should fire before ROM access
        }
        assertFalse(CrossGameFeatureProvider.isActive(),
                "Same-game donation should be disabled");
    }

    @Test
    void hybridFeatureSetReflectsDonorCapabilities() {
        DonorCapabilities s1Caps = new com.openggf.game.sonic1.Sonic1GameModule()
                .getDonorCapabilities();
        assertFalse(s1Caps.hasSpindash());
        assertFalse(s1Caps.hasSuperTransform());
        assertFalse(s1Caps.hasInstaShield());
    }

    @Test
    void hybridFeatureSetPreservesBaseBoundaryAndSidekickFlags() throws Exception {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic2.Sonic2GameModule());
        CrossGameFeatureProvider provider = new CrossGameFeatureProvider(null, null);
        setField(provider, "donorGameId", GameId.S3K);
        setField(provider, "donorCapabilities", new StubDonorCapabilities());

        PhysicsFeatureSet hybrid = invokeBuildHybridFeatureSet(provider);
        PhysicsFeatureSet base = PhysicsFeatureSet.SONIC_2;

        assertHybridPreservesBaseExceptDonatedCapabilities(base, hybrid);
        assertEquals(base.sidekickPushBypassUsesGraceStatus(), hybrid.sidekickPushBypassUsesGraceStatus());
        assertEquals(base.sidekickClearsStalePushVelocityBeforeGroundMove(),
                hybrid.sidekickClearsStalePushVelocityBeforeGroundMove());
        assertEquals(base.sidekickCpuUsesLevelFrameCounter(), hybrid.sidekickCpuUsesLevelFrameCounter());
        assertEquals(base.landingRollClearUsesCurrentYRadiusDelta(),
                hybrid.landingRollClearUsesCurrentYRadiusDelta());
        assertEquals(base.levelBoundaryRightStrict(), hybrid.levelBoundaryRightStrict());
        assertEquals(base.levelBoundaryUsesCentreY(), hybrid.levelBoundaryUsesCentreY());
        assertEquals(base.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity(),
                hybrid.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity());
        assertEquals(base.sidekickNormalCpuSkipsHurtRoutine(), hybrid.sidekickNormalCpuSkipsHurtRoutine());
    }

    @Test
    void loadPlayerSpriteArt_refreshesDonorPaletteForRequestedCharacter() throws Exception {
        Palette sonicPalette = paletteWithBlueMarker();
        Palette knucklesPalette = paletteWithRedMarker();
        CrossGameFeatureProvider provider = spy(new CrossGameFeatureProvider(null, null));
        doReturn(sonicPalette).when(provider).loadCharacterPalette(null);
        doReturn(sonicPalette).when(provider).loadCharacterPalette("sonic");
        doReturn(knucklesPalette).when(provider).loadCharacterPalette("knuckles");
        RenderContext context = RenderContext.getOrCreateDonor(GameId.S3K);
        context.setPalette(0, sonicPalette);
        setField(provider, "donorRenderContext", context);
        setField(provider, "donorGameId", GameId.S3K);
        setField(provider, "donorCapabilities", new StubDonorCapabilities());
        setField(provider, "donorPlayerArtProvider", StubDonorCapabilities.PROVIDER);
        setField(provider, "donorReader", new com.openggf.data.RomByteReader(new byte[0]));

        provider.loadPlayerSpriteArt("knuckles");

        assertSame(knucklesPalette, context.getPalette(0));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = CrossGameFeatureProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static PhysicsFeatureSet invokeBuildHybridFeatureSet(CrossGameFeatureProvider provider) throws Exception {
        Method method = CrossGameFeatureProvider.class.getDeclaredMethod("buildHybridFeatureSet");
        method.setAccessible(true);
        return (PhysicsFeatureSet) method.invoke(provider);
    }

    private static void assertHybridPreservesBaseExceptDonatedCapabilities(
            PhysicsFeatureSet base, PhysicsFeatureSet hybrid) throws Exception {
        Set<String> donorFields = Set.of(
                "spindashEnabled",
                "spindashSpeedTable",
                "elementalShieldsEnabled",
                "instaShieldEnabled",
                "lightningShieldEnabled");
        for (RecordComponent component : PhysicsFeatureSet.class.getRecordComponents()) {
            if (donorFields.contains(component.getName())) {
                continue;
            }
            Method accessor = component.getAccessor();
            assertEquals(accessor.invoke(base), accessor.invoke(hybrid),
                    "Hybrid physics must preserve base component " + component.getName());
        }
    }

    private static final class StubDonorCapabilities implements DonorCapabilities {
        private static final PlayerSpriteArtProvider PROVIDER = characterCode -> new SpriteArtSet(
                new Pattern[0], List.of(), List.of(), 0, 0, 0, 1, null, null);

        @Override public java.util.Set<PlayerCharacter> getPlayableCharacters() { return java.util.Set.of(PlayerCharacter.SONIC_ALONE, PlayerCharacter.KNUCKLES); }
        @Override public boolean hasSpindash() { return false; }
        @Override public boolean hasSuperTransform() { return false; }
        @Override public boolean hasHyperTransform() { return false; }
        @Override public boolean hasInstaShield() { return false; }
        @Override public boolean hasElementalShields() { return false; }
        @Override public boolean hasSidekick() { return false; }
        @Override public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() { return java.util.Map.of(); }
        @Override public int resolveNativeId(CanonicalAnimation canonical) { return -1; }
        @Override public PlayerSpriteArtProvider getPlayerArtProvider(com.openggf.data.RomByteReader reader) { return PROVIDER; }
    }

    private static Palette paletteWithBlueMarker() {
        Palette palette = new Palette();
        palette.setColor(1, new Palette.Color((byte) 0x22, (byte) 0x44, (byte) 0xEE));
        return palette;
    }

    private static Palette paletteWithRedMarker() {
        Palette palette = new Palette();
        palette.setColor(1, new Palette.Color((byte) 0xEE, (byte) 0x22, (byte) 0x22));
        return palette;
    }
}


