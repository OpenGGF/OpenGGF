package com.openggf.game;

import com.openggf.game.rules.GameRules;

import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.art.PlayerSpriteArtProvider;
import com.openggf.data.RomManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.RomTestUtils;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TestCrossGameFeatureProviderRefactor {

    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonicFamiliesKeepS1HostTiming() throws Exception { assertSonicFamilyTiming(new com.openggf.game.sonic1.Sonic1GameModule()); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonicFamiliesKeepS2HostTiming() throws Exception { assertSonicFamilyTiming(new com.openggf.game.sonic2.Sonic2GameModule()); }

    private void assertSonicFamilyTiming(GameModule host) throws Exception {
        GameModuleRegistry.setCurrent(host);
        SessionManager.openGameplaySession(host);
        var configuration = GameServices.configuration();
        String previous = configuration.getString(SonicConfiguration.SONIC_3K_ROM);
        configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath());
        try {
            var provider = CrossGameFeatureProvider.getInstance();
            provider.initialize("s3k");
            var donated = provider.loadPlayerSpriteArt("sonic");
            var sprite = new com.openggf.tests.TestablePlayableSprite("sonic", (short) 0, (short) 0);
            sprite.setAnimationSet(donated.animationSet());
            sprite.setAnimationProfile(donated.animationProfile());
            var completed = java.util.Set.of(CanonicalAnimation.STOP, CanonicalAnimation.FLOAT3,
                    CanonicalAnimation.FLOAT4, CanonicalAnimation.GET_AIR, CanonicalAnimation.BURNT,
                    CanonicalAnimation.WATER_SLIDE, CanonicalAnimation.SLIDE, CanonicalAnimation.NULL_ANIM,
                    CanonicalAnimation.HURT2, CanonicalAnimation.TAILS_CARRIED, CanonicalAnimation.HANG2,
                    CanonicalAnimation.BLINK, CanonicalAnimation.GET_UP, CanonicalAnimation.WAIT);
            for (var animation : completed) {
                int id = provider.resolveDonatedAnimation(animation);
                assertTrue(id >= 0, animation.name());
                assertNotNull(donated.animationSet().getScript(id), animation.name());
                assertEquals(id, sprite.resolveAnimationId(animation), animation.name());
            }
            java.util.List<? extends AnimationId> nativeIds = host instanceof com.openggf.game.sonic1.Sonic1GameModule
                    ? java.util.Arrays.asList(com.openggf.game.sonic1.constants.Sonic1AnimationIds.values())
                    : java.util.Arrays.asList(com.openggf.game.sonic2.constants.Sonic2AnimationIds.values());
            for (var animation : nativeIds) if (animation.toCanonical() != null && completed.contains(animation.toCanonical())) {
                sprite.setAnimationId(animation);
                assertEquals(provider.resolveDonatedAnimation(animation.toCanonical()), sprite.getAnimationId());
            }
            var nativeHost = host.getDonorCapabilities().getPlayerArtProvider(
                    com.openggf.data.RomByteReader.fromRom(RomManager.getInstance().getRom())).loadPlayerSpriteArt("sonic");
            var nativeProfile = (ScriptedVelocityAnimationProfile) nativeHost.animationProfile();
            var translatedProfile = (ScriptedVelocityAnimationProfile) donated.animationProfile();
            assertEquals(provider.resolveFamilyAnimation(CanonicalAnimation.WAIT), translatedProfile.getIdleAnimId());
            assertEquals(nativeProfile.getBlinkAnimId() < 0 ? -1 : provider.resolveFamilyAnimation(CanonicalAnimation.BLINK),
                    translatedProfile.getBlinkAnimId());
            assertEquals(nativeProfile.getGetUpAnimId() < 0 ? -1 : provider.resolveFamilyAnimation(CanonicalAnimation.GET_UP),
                    translatedProfile.getGetUpAnimId());
            int count = 0;
            for (var entry : host.getDonorCapabilities().getAnimationFamilies().entrySet()) {
                var animation = entry.getValue().animation();
                if (animation == null) continue;
                int id = provider.resolveFamilyAnimation(animation);
                if (id < 0) continue;
                var script = donated.animationSet().getScript(id);
                var original = nativeHost.animationSet().getScript(entry.getKey());
                assertEquals(original.delay(), script.delay(), animation.name());
                assertEquals(original.endAction(), script.endAction(), animation.name());
                assertEquals(original.frames().size(), script.frames().size(), animation.name());
                if (script.endAction() != com.openggf.sprites.animation.SpriteAnimationEndAction.SWITCH)
                    assertEquals(original.endParam(), script.endParam(), animation.name());
                count++;
            }
            assertTrue(count > 0, "host-specific families are installed");
        } finally { configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, previous); }
    }


    @Nested
    @RequiresRom(SonicGame.SONIC_3K)
    class S3kTailsDonationIntegration {

        @Test
        void s3kTailsScriptsSurviveTranslationIntoS1Host() throws Exception {
            assertS3kTailsScriptsSurviveTranslation(new com.openggf.game.sonic1.Sonic1GameModule());
        }

            @Test
        void s3kTailsScriptsSurviveTranslationIntoS2Host() throws Exception {
            assertS3kTailsScriptsSurviveTranslation(new com.openggf.game.sonic2.Sonic2GameModule());
        }

        private void assertS3kTailsScriptsSurviveTranslation(GameModule host) throws Exception {
            GameModuleRegistry.setCurrent(host);
            SessionManager.openGameplaySession(host);
            var configuration = GameServices.configuration();
            String previousDonor = configuration.getString(SonicConfiguration.SONIC_3K_ROM);
            // @RequiresRom installs the primary ROM. The provider independently opens
            // its secondary ROM through the runtime catalogue, which needs this path too.
            var donorRom = RomTestUtils.ensureSonic3kRomAvailable();
            assertNotNull(donorRom);
            configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM,
                    donorRom.getAbsolutePath());
            try {
                CrossGameFeatureProvider provider = new CrossGameFeatureProvider(
                        RomManager.getInstance(), configuration);
                provider.initialize("s3k");

                SpriteArtSet donated = provider.loadPlayerSpriteArt("tails");

                assertNotNull(donated);
                assertInstanceOf(ScriptedVelocityAnimationProfile.class, donated.animationProfile());
                assertNotNull(donated.animationSet());
                for (int animationId = 0x20; animationId <= 0x28; animationId++) {
                    assertNotNull(donated.animationSet().getScript(animationId),
                            "host " + host.getGameId() + " lost donated Tails script 0x"
                                    + Integer.toHexString(animationId));
                }
            } finally {
                configuration.setConfigValue(SonicConfiguration.SONIC_3K_ROM, previousDonor);
            }
        }
    }

    @BeforeEach
    void setUp() {
        // Clear lingering session/runtime state from prior tests in the same fork
        // so resolveHostGameId() falls back to the GameModuleRegistry bootstrap
        // default that this fixture configures via setCurrent().
        SessionManager.clear();
        TestEnvironment.resetEngineOnly();
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
    void hybridRulesReflectDonorCapabilities() {
        DonorCapabilities s1Caps = new com.openggf.game.sonic1.Sonic1GameModule()
                .getDonorCapabilities();
        assertFalse(s1Caps.hasSpindash());
        assertFalse(s1Caps.hasSuperTransform());
        assertFalse(s1Caps.hasInstaShield());
    }

    @Test
    void hybridRulesPreserveBaseBoundaryAndSidekickFlags() throws Exception {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic2.Sonic2GameModule());
        CrossGameFeatureProvider provider = new CrossGameFeatureProvider(null, null);
        setField(provider, "donorGameId", GameId.S3K);
        setField(provider, "donorCapabilities", new StubDonorCapabilities());

        GameRules hybrid = invokeBuildHybridRules(provider);
        GameRules base = GameRules.SONIC_2;

        assertHybridPreservesBaseExceptDonatedCapabilities(base, hybrid);
        assertTrue(hybrid.playerCapability().glideAttacksEnabled(),
                "The S3K donor advertises Knuckles glide attacks");
        assertEquals(base.sidekickCpu().sidekickCpuUsesLevelFrameCounter(), hybrid.sidekickCpu().sidekickCpuUsesLevelFrameCounter());
        assertEquals(base.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta(),
                hybrid.playerMovement().landing().landingRollClearUsesCurrentYRadiusDelta());
        assertEquals(base.playerMovement().levelBoundaryRightStrict(), hybrid.playerMovement().levelBoundaryRightStrict());
        assertEquals(base.playerMovement().levelBoundaryUsesCentreY(), hybrid.playerMovement().levelBoundaryUsesCentreY());
        assertEquals(base.collision().solidObjectTopBranchAlwaysLiftsOnUpwardVelocity(),
                hybrid.collision().solidObjectTopBranchAlwaysLiftsOnUpwardVelocity());
        assertEquals(base.objectInteraction().sidekickNormalCpuSkipsHurtRoutine(), hybrid.objectInteraction().sidekickNormalCpuSkipsHurtRoutine());
    }

    @Test
    void loadPlayerSpriteArt_refreshesDonorPaletteForRequestedCharacter() throws Exception {
        Palette sonicPalette = paletteWithBlueMarker();
        Palette knucklesPalette = paletteWithRedMarker();
        CrossGameFeatureProvider provider = spy(new CrossGameFeatureProvider(null, null));
        doReturn(sonicPalette).when(provider).loadCharacterPalette(null);
        doReturn(sonicPalette).when(provider).loadCharacterPalette("sonic");
        doReturn(knucklesPalette).when(provider).loadCharacterPalette("knuckles");
        Palette sonicWater = paletteWithRedMarker();
        Palette knucklesWater = paletteWithBlueMarker();
        doReturn(sonicWater).when(provider).loadUnderwaterCharacterPalette("sonic");
        doReturn(knucklesWater).when(provider).loadUnderwaterCharacterPalette("knuckles");
        RenderContext context = RenderContext.getOrCreateDonor(GameId.S3K);
        context.setPalette(0, sonicPalette);
        setField(provider, "donorRenderContext", context);
        setField(provider, "donorGameId", GameId.S3K);
        setField(provider, "donorCapabilities", new StubDonorCapabilities());
        setField(provider, "donorPlayerArtProvider", StubDonorCapabilities.PROVIDER);
        setField(provider, "donorReader", new com.openggf.data.RomByteReader(new byte[0]));

        provider.loadPlayerSpriteArt("knuckles");

        assertSame(knucklesPalette, context.getPalette(0));
        assertSame(knucklesWater, context.getUnderwaterPalette(0));
        assertSame(context, provider.getDonorRenderContext());

        provider.loadPlayerSpriteArt("sonic");
        assertSame(sonicPalette, context.getPalette(0));
        assertSame(sonicWater, context.getUnderwaterPalette(0));
        doReturn(null).when(provider).loadUnderwaterCharacterPalette("sonic");
        provider.loadPlayerSpriteArt("sonic");
        assertNull(context.getUnderwaterPalette(0), "a missing native row clears the prior selection");

        provider.loadPlayerSpriteArt(null);
        assertSame(sonicPalette, context.getPalette(0), "null retains the standalone default selection");
        assertSame(context, provider.getDonorRenderContext());
    }

    @Test
    void tailsFlightDonorRejectsMissingRequiredAnimationContract() throws Exception {
        CrossGameFeatureProvider provider = configuredArtProvider(
                new StubDonorCapabilities(true, CanonicalAnimation.TAILS_SWIM_CARRY));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.loadPlayerSpriteArt("tails"));

        assertTrue(error.getMessage().contains("TAILS_SWIM_CARRY"));
    }

    @Test
    void tailsFlightDonorAcceptsCompleteRequiredAnimationContract() throws Exception {
        CrossGameFeatureProvider provider = configuredArtProvider(
                new StubDonorCapabilities(true, null));

        assertNotNull(provider.loadPlayerSpriteArt("tails"));
    }

    @Test
    void tailsFlightDonorRejectsMissingAnimationSetAfterArtLoad() throws Exception {
        CrossGameFeatureProvider provider = configuredArtProvider(
                new StubDonorCapabilities(true, null),
                characterCode -> new SpriteArtSet(
                        new Pattern[0], List.of(), List.of(), 0, 0, 0, 1, null, null));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.loadPlayerSpriteArt("tails"));

        assertTailsContractDiagnostic(error, CanonicalAnimation.TAILS_FLY, 0x20);
    }

    @Test
    void tailsFlightDonorRejectsMissingRequiredNativeScriptAfterArtLoad() throws Exception {
        CrossGameFeatureProvider provider = configuredArtProvider(
                new StubDonorCapabilities(true, null),
                characterCode -> tailsArtMissingScript(0x27));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.loadPlayerSpriteArt("tails"));

        assertTailsContractDiagnostic(error, CanonicalAnimation.TAILS_SWIM_CARRY, 0x27);
    }

    @Test
    void tailsFlightDonorPreservesNullArtSemantics() throws Exception {
        CrossGameFeatureProvider provider = configuredArtProvider(
                new StubDonorCapabilities(true, null), characterCode -> null);

        assertNull(provider.loadPlayerSpriteArt("tails"));
    }

    private static void assertTailsContractDiagnostic(
            IllegalStateException error, CanonicalAnimation canonical, int nativeId) {
        assertAll(
                () -> assertTrue(error.getMessage().contains("s3k")),
                () -> assertTrue(error.getMessage().contains("tails")),
                () -> assertTrue(error.getMessage().contains(canonical.name())),
                () -> assertTrue(error.getMessage().contains("0x" + Integer.toHexString(nativeId))));
    }

    private static SpriteArtSet tailsArtMissingScript(int missingId) {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        for (int id = 0x20; id <= 0x28; id++) {
            if (id != missingId) {
                animations.addScript(id, new SpriteAnimationScript(
                        1, List.of(0), SpriteAnimationEndAction.LOOP, 0));
            }
        }
        return new SpriteArtSet(
                new Pattern[0], List.of(), List.of(), 0, 0, 0, 1, null, animations);
    }

    private static CrossGameFeatureProvider configuredArtProvider(DonorCapabilities capabilities) throws Exception {
        return configuredArtProvider(capabilities, StubDonorCapabilities.PROVIDER);
    }

    private static CrossGameFeatureProvider configuredArtProvider(
            DonorCapabilities capabilities, PlayerSpriteArtProvider artProvider) throws Exception {
        CrossGameFeatureProvider provider = spy(new CrossGameFeatureProvider(null, null));
        doReturn(null).when(provider).loadCharacterPalette("tails");
        setField(provider, "donorGameId", GameId.S3K);
        setField(provider, "donorCapabilities", capabilities);
        setField(provider, "donorPlayerArtProvider", artProvider);
        return provider;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = CrossGameFeatureProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static GameRules invokeBuildHybridRules(CrossGameFeatureProvider provider) throws Exception {
        Method method = CrossGameFeatureProvider.class.getDeclaredMethod("buildHybridRules", GameRules.class);
        method.setAccessible(true);
        return (GameRules) method.invoke(provider, GameRules.SONIC_3K);
    }

    private static void assertHybridPreservesBaseExceptDonatedCapabilities(
            GameRules base, GameRules hybrid) throws Exception {
        Set<String> donorFields = Set.of(
                "spindashEnabled",
                "spindashSpeedTable",
                "elementalShieldsEnabled",
                "instaShieldEnabled",
                "lightningShieldEnabled",
                "glideAttacksEnabled");
        for (RecordComponent component : GameRules.class.getRecordComponents()) {
            if ("playerCapability".equals(component.getName())) {
                continue;
            }
            Method accessor = component.getAccessor();
            assertEquals(accessor.invoke(base), accessor.invoke(hybrid),
                    "Hybrid rules must preserve base component " + component.getName());
        }
        for (RecordComponent component : base.playerCapability().getClass().getRecordComponents()) {
            if (donorFields.contains(component.getName())) {
                continue;
            }
            Method accessor = component.getAccessor();
            Object expected = accessor.invoke(base.playerCapability());
            Object actual = accessor.invoke(hybrid.playerCapability());
            String message = "Hybrid rules must preserve host player capability component " + component.getName();
            if (expected instanceof short[] expectedArray && actual instanceof short[] actualArray) {
                assertArrayEquals(expectedArray, actualArray, message);
            } else {
                assertEquals(expected, actual, message);
            }
        }
    }

    private static final class StubDonorCapabilities implements DonorCapabilities {
        private static final PlayerSpriteArtProvider PROVIDER = characterCode -> tailsArtMissingScript(-1);
        private final boolean tailsFlight;
        private final CanonicalAnimation missingAnimation;

        private StubDonorCapabilities() {
            this(false, null);
        }

        private StubDonorCapabilities(boolean tailsFlight, CanonicalAnimation missingAnimation) {
            this.tailsFlight = tailsFlight;
            this.missingAnimation = missingAnimation;
        }

        @Override public java.util.Set<PlayerCharacter> getPlayableCharacters() { return java.util.Set.of(PlayerCharacter.SONIC_ALONE, PlayerCharacter.KNUCKLES); }
        @Override public boolean hasSpindash() { return false; }
        @Override public boolean hasSuperTransform() { return false; }
        @Override public boolean hasHyperTransform() { return false; }
        @Override public boolean hasInstaShield() { return false; }
        @Override public boolean hasTailsFlight() { return tailsFlight; }
        @Override public boolean hasElementalShields() { return false; }
        @Override public boolean hasSidekick() { return false; }
        @Override public Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() { return Map.of(); }
        @Override public int resolveNativeId(CanonicalAnimation canonical) {
            if (canonical == missingAnimation) {
                return -1;
            }
            return switch (canonical) {
                case TAILS_FLY -> 0x20;
                case TAILS_FLY_ASCEND -> 0x21;
                case TAILS_FLY_CARRY -> 0x22;
                case TAILS_FLY_CARRY_ASCEND -> 0x23;
                case TAILS_FLY_TIRED -> 0x24;
                case TAILS_SWIM -> 0x25;
                case TAILS_SWIM_ASCEND -> 0x26;
                case TAILS_SWIM_CARRY -> 0x27;
                case TAILS_SWIM_TIRED -> 0x28;
                default -> -1;
            };
        }
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
