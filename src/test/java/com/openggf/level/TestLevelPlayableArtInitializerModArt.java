package com.openggf.level;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.SpriteManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.data.RomByteReader;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.withSettings;

class TestLevelPlayableArtInitializerModArt {
    @AfterEach void resetContexts() { RenderContext.reset(); }

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void equalNormalModMainKeepsDerivedWaterBesideDonatedSonic() throws Exception {
        var source = new com.openggf.game.sonic1.Sonic1GameModule().getCrossGameDonorProvider();
        var reader = RomByteReader.fromRom(TestEnvironment.currentRom());
        Palette normal = source.loadCharacterPalette(reader, "sonic");
        Palette nativeWater = source.loadUnderwaterCharacterPalette(reader, "sonic");
        org.junit.jupiter.api.Assertions.assertFalse(normal.dataEquals(nativeWater));
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty().register(mainKey,
                definition(mainKey, code -> art(4), code -> normal));
        AbstractPlayableSprite sonic = sprite(CharacterKey.SONIC, "sonic_p2");
        Fixture fixture = fixture(registry, mainKey, List.of(sonic));
        fixture.level().gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        CrossGameFeatureProvider donor = mock(CrossGameFeatureProvider.class);
        when(donor.loadPlayerSpriteArt(anyString())).thenReturn(
                source.createPlayerArtProvider(reader).loadPlayerSpriteArt("sonic"));
        when(donor.loadCharacterPalette("sonic")).thenReturn(normal);
        when(donor.loadUnderwaterCharacterPalette("sonic")).thenReturn(nativeWater);
        when(donor.getDonorRenderContext()).thenReturn(RenderContext.getOrCreateDonor(GameId.S1));

        initializeDonated(fixture, donor);

        RenderContext mainContext = capturedRenderer(fixture.main()).getRenderContext();
        RenderContext sideContext = capturedRenderer(sonic).getRenderContext();
        org.junit.jupiter.api.Assertions.assertNotSame(mainContext, sideContext);
        org.junit.jupiter.api.Assertions.assertNull(mainContext.getUnderwaterPalette(0),
                "the mod main keeps host-derived water");
        org.junit.jupiter.api.Assertions.assertSame(nativeWater, sideContext.getUnderwaterPalette(0));
        assertEquals(normal, mainContext.getPalette(0));
        assertEquals(normal, sideContext.getPalette(0));
    }

    @Test
    void equalNormalModFollowerKeepsDerivedWaterBesideDonatedMain() throws Exception {
        CharacterKey sideKey = CharacterKey.mod("owner", "side");
        Palette normal = new Palette();
        normal.colors[2].b = (byte) 255;
        Palette nativeWater = new Palette();
        nativeWater.colors[2].r = (byte) 146;
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty().register(sideKey,
                definition(sideKey, code -> art(4), code -> normal));
        AbstractPlayableSprite side = sprite(sideKey, sideKey.persisted());
        Fixture fixture = fixture(registry, CharacterKey.SONIC, List.of(side));
        CrossGameFeatureProvider donor = mock(CrossGameFeatureProvider.class);
        RenderContext shared = RenderContext.getOrCreateDonor(GameId.S1);
        shared.setPalette(0, normal);
        shared.setUnderwaterPalette(0, nativeWater);
        when(donor.getDonorRenderContext()).thenReturn(shared);
        when(donor.loadPlayerSpriteArt("sonic")).thenReturn(art(4));
        when(donor.loadCharacterPalette("sonic")).thenReturn(normal);
        when(donor.loadUnderwaterCharacterPalette("sonic")).thenReturn(nativeWater);

        initializeDonated(fixture, donor);

        RenderContext mainContext = capturedRenderer(fixture.main()).getRenderContext();
        RenderContext sideContext = capturedRenderer(side).getRenderContext();
        org.junit.jupiter.api.Assertions.assertSame(shared, mainContext);
        org.junit.jupiter.api.Assertions.assertNotSame(mainContext, sideContext);
        org.junit.jupiter.api.Assertions.assertSame(nativeWater, mainContext.getUnderwaterPalette(0));
        org.junit.jupiter.api.Assertions.assertNull(sideContext.getUnderwaterPalette(0));
        assertEquals(normal, sideContext.getPalette(0));
    }

    @ParameterizedTest(name = "prepared={0}, equal water={1}")
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void equalNormalBuiltinSharingRequiresEqualWaterInBothRoutes(boolean prepared, boolean equalWater)
            throws Exception {
        CharacterKey extraKey = CharacterKey.mod("owner", "extra");
        PlayableCharacterRegistry registry = prepared
                ? PlayableCharacterRegistry.empty().register(extraKey, definition(extraKey, code -> art(4)))
                : PlayableCharacterRegistry.empty();
        AbstractPlayableSprite side = sprite(CharacterKey.KNUCKLES, "knuckles_p2");
        Fixture fixture = fixture(registry, CharacterKey.SONIC, prepared
                ? List.of(side, sprite(extraKey, extraKey.persisted())) : List.of(side));
        CrossGameFeatureProvider donor = mock(CrossGameFeatureProvider.class);
        Palette normal = new Palette();
        normal.colors[2].b = (byte) 255;
        Palette mainWater = new Palette();
        mainWater.colors[2].r = (byte) 146;
        Palette sideWater = new Palette();
        if (equalWater) sideWater.colors[2].r = (byte) 146;
        else sideWater.colors[2].b = (byte) 73;
        RenderContext shared = RenderContext.getOrCreateDonor(GameId.S3K);
        when(donor.getDonorRenderContext()).thenReturn(shared);
        when(donor.loadPlayerSpriteArt(anyString())).thenReturn(art(4));
        when(donor.loadCharacterPalette(anyString())).thenReturn(normal);
        when(donor.loadUnderwaterCharacterPalette("sonic")).thenReturn(mainWater);
        when(donor.loadUnderwaterCharacterPalette("knuckles")).thenReturn(sideWater);

        initializeDonated(fixture, donor);

        RenderContext mainContext = capturedRenderer(fixture.main()).getRenderContext();
        RenderContext sideContext = capturedRenderer(side).getRenderContext();
        if (equalWater) org.junit.jupiter.api.Assertions.assertSame(mainContext, sideContext);
        else org.junit.jupiter.api.Assertions.assertNotSame(mainContext, sideContext);
        assertEquals(146, mainContext.getUnderwaterPalette(0).red(2) & 255);
        assertEquals(equalWater ? 146 : 0, sideContext.getUnderwaterPalette(0).red(2) & 255);
        assertEquals(equalWater ? 0 : 73, sideContext.getUnderwaterPalette(0).blue(2) & 255);
    }

    private static void initializeDonated(Fixture fixture, CrossGameFeatureProvider donor) {
        try (var active = mockStatic(CrossGameFeatureProvider.class)) {
            active.when(CrossGameFeatureProvider::isActive).thenReturn(true);
            new LevelPlayableArtInitializer(fixture.level(), fixture.sprites(), mock(GraphicsManager.class),
                    fixture.config(), donor).initialize();
        }
    }

    @ParameterizedTest(name = "native follower, prepared={0}")
    @ValueSource(booleans = {false, true})
    void equalNormalNativeScriptedFollowerKeepsHostBindingInBothRoutes(boolean prepared) throws Exception {
        CharacterKey extraKey = CharacterKey.mod("owner", "extra");
        PlayableCharacterRegistry registry = prepared
                ? PlayableCharacterRegistry.empty().register(extraKey, definition(extraKey, code -> art(4)))
                : PlayableCharacterRegistry.empty();
        AbstractPlayableSprite nativeSide = sprite(CharacterKey.SONIC, "sonic_p2");
        Fixture fixture = fixture(registry, CharacterKey.SONIC, prepared
                ? List.of(nativeSide, sprite(extraKey, extraKey.persisted())) : List.of(nativeSide));
        com.openggf.data.Game host = mock(com.openggf.data.Game.class,
                withSettings().extraInterfaces(com.openggf.sprites.art.PlayerSpriteArtProvider.class));
        fixture.level().game = host;
        var hostArt = (com.openggf.sprites.art.PlayerSpriteArtProvider) host;
        Palette normal = new Palette();
        Palette nativeWater = new Palette();
        nativeWater.colors[2].r = (byte) 146;
        when(hostArt.loadPlayerSpriteArt(anyString())).thenReturn(art(4));
        when(hostArt.loadCharacterPalette(anyString())).thenReturn(normal);
        CrossGameFeatureProvider donor = mock(CrossGameFeatureProvider.class);
        RenderContext shared = RenderContext.getOrCreateDonor(GameId.S1);
        when(donor.getDonorRenderContext()).thenReturn(shared);
        when(donor.loadPlayerSpriteArt(anyString())).thenReturn(art(4));
        when(donor.loadCharacterPalette(anyString())).thenReturn(normal);
        when(donor.loadUnderwaterCharacterPalette("sonic")).thenReturn(nativeWater);
        try (var nativeBinding = mockStatic(SpriteManagerInternalAccess.class)) {
            nativeBinding.when(() -> SpriteManagerInternalAccess.usesNativeScriptedArt(
                    fixture.sprites(), nativeSide)).thenReturn(true);
            initializeDonated(fixture, donor);
        }
        org.junit.jupiter.api.Assertions.assertNull(capturedRenderer(nativeSide).getRenderContext(),
                "a native-scripted follower still uses the host's base palette binding");
        org.junit.jupiter.api.Assertions.assertSame(nativeWater, shared.getUnderwaterPalette(0));
        verify(hostArt).loadPlayerSpriteArt(prepared ? "sonic_p2" : "sonic");
    }

    @ParameterizedTest(name = "prepared team: {0}")
    @ValueSource(booleans = {false, true})
    void donatedTeamPublishesMainNormalAndEachCharactersNativeWater(boolean prepared) throws Exception {
        CharacterKey extraKey = CharacterKey.mod("owner", "extra");
        PlayableCharacterRegistry registry = prepared
                ? PlayableCharacterRegistry.empty().register(extraKey, definition(extraKey, code -> art(4)))
                : PlayableCharacterRegistry.empty();
        AbstractPlayableSprite knuckles = sprite(CharacterKey.KNUCKLES, "knuckles_p2");
        List<AbstractPlayableSprite> sidekicks = prepared
                ? List.of(knuckles, sprite(extraKey, extraKey.persisted())) : List.of(knuckles);
        Fixture fixture = fixture(registry, CharacterKey.SONIC, sidekicks);
        CrossGameFeatureProvider donor = mock(CrossGameFeatureProvider.class);
        GraphicsManager graphics = mock(GraphicsManager.class);
        RenderContext shared = RenderContext.getOrCreateDonor(GameId.S3K);
        Palette sonic = new Palette();
        sonic.colors[2].b = (byte) 255;
        Palette knux = new Palette();
        knux.colors[2].r = (byte) 255;
        Palette sonicWater = new Palette();
        sonicWater.colors[2].g = (byte) 73;
        Palette knuxWater = new Palette();
        knuxWater.colors[2].r = (byte) 146;
        when(donor.getDonorRenderContext()).thenReturn(shared);
        when(donor.loadCharacterPalette("sonic")).thenReturn(sonic);
        when(donor.loadCharacterPalette("knuckles")).thenReturn(knux);
        when(donor.loadUnderwaterCharacterPalette("sonic")).thenReturn(sonicWater);
        when(donor.loadUnderwaterCharacterPalette("knuckles")).thenReturn(knuxWater);
        when(donor.loadPlayerSpriteArt(anyString())).thenAnswer(invocation -> {
            boolean isKnuckles = invocation.<String>getArgument(0).startsWith("knuckles");
            // Reproduce the provider's existing standalone refresh contract.
            shared.setPalette(0, isKnuckles ? knux : sonic);
            shared.setUnderwaterPalette(0, isKnuckles ? knuxWater : sonicWater);
            return art(8);
        });
        try (var active = mockStatic(CrossGameFeatureProvider.class)) {
            active.when(CrossGameFeatureProvider::isActive).thenReturn(true);
            new LevelPlayableArtInitializer(fixture.level(), fixture.sprites(), graphics,
                    fixture.config(), donor).initialize();
        }
        RenderContext mainContext = capturedRenderer(fixture.main()).getRenderContext();
        RenderContext sideContext = capturedRenderer(knuckles).getRenderContext();
        org.junit.jupiter.api.Assertions.assertSame(shared, mainContext);
        org.junit.jupiter.api.Assertions.assertNotSame(mainContext, sideContext);
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(255, mainContext.getPalette(0).blue(2) & 255),
                () -> assertEquals(255, sideContext.getPalette(0).red(2) & 255),
                () -> org.junit.jupiter.api.Assertions.assertNotNull(mainContext.getUnderwaterPalette(0)),
                () -> org.junit.jupiter.api.Assertions.assertNotNull(sideContext.getUnderwaterPalette(0)));
        assertEquals(73, mainContext.getUnderwaterPalette(0).green(2) & 255);
        assertEquals(146, sideContext.getUnderwaterPalette(0).red(2) & 255);
        verify(graphics).cachePaletteTexture(sonic, shared.getEffectivePaletteLine(0));
        verify(graphics).cachePaletteTexture(knux, sideContext.getEffectivePaletteLine(0));
    }

    @Test
    void supplierFailureLeavesExistingRendererAndPaletteContextsUntouched() throws Exception {
        CharacterKey key = CharacterKey.mod("owner", "main");
        CharacterDefinition definition = definition(key, code -> { throw new IOException("bad"); });
        Fixture fixture = fixture(PlayableCharacterRegistry.empty().register(key, definition), key, List.of());
        Object existing = fixture.main().getSpriteRenderer();
        RenderContext.createSidekickContext(GameId.S2);
        int paletteLines = RenderContext.getTotalPaletteLines();

        fixture.initializer().initialize();

        verify(fixture.main(), never()).setSpriteRenderer(any());
        assertEquals(existing, fixture.main().getSpriteRenderer());
        assertEquals(paletteLines, RenderContext.getTotalPaletteLines());
    }

    @Test
    void sidekickCapacityOverflowPublishesNoMainOrSidekickRenderer() {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        CharacterKey oneKey = CharacterKey.mod("owner", "one");
        CharacterKey twoKey = CharacterKey.mod("owner", "two");
        int capacity = com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size();
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(1)))
                .register(oneKey, definition(oneKey, code -> art(capacity - 1)))
                .register(twoKey, definition(twoKey, code -> art(1)));
        AbstractPlayableSprite one = sprite(oneKey, "owner:one");
        AbstractPlayableSprite two = sprite(twoKey, "owner:two");
        Fixture fixture = fixture(registry, mainKey, List.of(one, two));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> fixture.initializer().initialize());

        verify(fixture.main(), never()).setSpriteRenderer(any());
        verify(one, never()).setSpriteRenderer(any());
        verify(two, never()).setSpriteRenderer(any());
    }

    @Test
    void builtinRegistryMissFallsThroughToGameProviderWithoutChangingArt() throws Exception {
        Fixture fixture = fixture(PlayableCharacterRegistry.empty(), CharacterKey.SONIC, List.of());
        com.openggf.data.Game game = mock(com.openggf.data.Game.class,
                withSettings().extraInterfaces(com.openggf.sprites.art.PlayerSpriteArtProvider.class));
        fixture.level().game = game;
        SpriteArtSet stock = art(8);
        when(((com.openggf.sprites.art.PlayerSpriteArtProvider) game).loadPlayerSpriteArt("sonic"))
                .thenReturn(stock);

        fixture.initializer().initialize();

        verify((com.openggf.sprites.art.PlayerSpriteArtProvider) game).loadPlayerSpriteArt("sonic");
        assertEquals(stock, rendererArt(capturedRenderer(fixture.main())));
    }

    @Test
    void builtinSharedTailsArtRetainsDedicatedTailVramBaseAndNoAnimationProfile() throws Exception {
        LevelManager level = mock(LevelManager.class);
        when(level.playableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        GameModule module = mock(GameModule.class);
        when(module.hasSeparateTailsTailArt()).thenReturn(false);
        when(module.getTailsTailVramBase()).thenReturn(0x3456);
        level.gameModule = module;

        com.openggf.data.Game game = mock(com.openggf.data.Game.class,
                withSettings().extraInterfaces(com.openggf.sprites.art.PlayerSpriteArtProvider.class));
        level.game = game;
        SpriteArtSet stock = art(8, 0, 0x1234);
        when(((com.openggf.sprites.art.PlayerSpriteArtProvider) game).loadPlayerSpriteArt("tails"))
                .thenReturn(stock);

        SpriteManager sprites = mock(SpriteManager.class);
        com.openggf.sprites.playable.Tails tails = mock(com.openggf.sprites.playable.Tails.class);
        when(tails.characterKey()).thenReturn(CharacterKey.TAILS);
        when(tails.getCode()).thenReturn("tails");
        when(sprites.getSprite("tails")).thenReturn(tails);
        when(sprites.getSidekicks()).thenReturn(List.of());
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn("tails");
        LevelPlayableArtInitializer initializer = new LevelPlayableArtInitializer(level, sprites,
                mock(GraphicsManager.class), config, mock(CrossGameFeatureProvider.class));

        initializer.initialize();

        ArgumentCaptor<com.openggf.sprites.managers.TailsTailsController> captor =
                ArgumentCaptor.forClass(com.openggf.sprites.managers.TailsTailsController.class);
        verify(tails).setTailsTailsController(captor.capture());
        SpriteArtSet tailArt = rendererArt(captor.getValue().getRenderer());
        assertEquals(0x3456, tailArt.basePatternIndex());
        org.junit.jupiter.api.Assertions.assertNull(tailArt.animationProfile());
    }

    @Test
    void duplicateModSidekicksPublishDistinctPreflightedBanks() {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        CharacterKey duplicateKey = CharacterKey.mod("owner", "duplicate");
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(4)))
                .register(duplicateKey, definition(duplicateKey, code -> art(8)));
        AbstractPlayableSprite first = sprite(duplicateKey, duplicateKey.persisted());
        AbstractPlayableSprite second = sprite(duplicateKey, duplicateKey.persisted());
        Fixture fixture = fixture(registry, mainKey, List.of(first, second));

        fixture.initializer().initialize();

        verify(fixture.main()).setSpriteRenderer(any(com.openggf.sprites.render.PlayerSpriteRenderer.class));
        verify(first).setSpriteRenderer(any(com.openggf.sprites.render.PlayerSpriteRenderer.class));
        verify(second).setSpriteRenderer(any(com.openggf.sprites.render.PlayerSpriteRenderer.class));
        try {
            assertEquals(LevelManager.SIDEKICK_PATTERN_BASE,
                    rendererArt(capturedRenderer(fixture.main())).basePatternIndex());
            assertEquals(LevelManager.SIDEKICK_PATTERN_BASE + 4,
                    rendererArt(capturedRenderer(first)).basePatternIndex());
            assertEquals(LevelManager.SIDEKICK_PATTERN_BASE + 12,
                    rendererArt(capturedRenderer(second)).basePatternIndex());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @Test
    void modMainCapacityOverflowPublishesNothing() {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        int capacity = com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size();
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(capacity + 1)));
        Fixture fixture = fixture(registry, mainKey, List.of());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> fixture.initializer().initialize());

        verify(fixture.main(), never()).setSpriteRenderer(any());
    }

    @Test
    void modMainForeignMetaBaseIsIgnoredAndRebasedInsideOwnedRange() throws Exception {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        SpriteArtSet foreign = art(4, 0, 0x7000_0000);
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> foreign));
        Fixture fixture = fixture(registry, mainKey, List.of());

        fixture.initializer().initialize();

        assertEquals(LevelManager.SIDEKICK_PATTERN_BASE,
                rendererArt(capturedRenderer(fixture.main())).basePatternIndex());
    }

    @Test
    void duplicateDustOverflowAfterCharacterBanksStillPublishesNothing() throws Exception {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        CharacterKey sideKey = CharacterKey.mod("owner", "side");
        int capacity = com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size();
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(1)))
                .register(sideKey, definition(sideKey, code -> art(capacity - 1)));
        AbstractPlayableSprite sidekick = sprite(sideKey, sideKey.persisted());
        Fixture fixture = fixture(registry, mainKey, List.of(sidekick));
        com.openggf.data.Game game = mock(com.openggf.data.Game.class,
                withSettings().extraInterfaces(com.openggf.sprites.art.SpindashDustArtProvider.class));
        fixture.level().game = game;
        when(((com.openggf.sprites.art.SpindashDustArtProvider) game).loadSpindashDustArt(anyString()))
                .thenReturn(art(1));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> fixture.initializer().initialize());

        verify(fixture.main(), never()).setSpriteRenderer(any());
        verify(sidekick, never()).setSpriteRenderer(any());
    }

    @Test
    void rejectedDirectReservationDoesNotAdvanceCursor() {
        Fixture fixture = fixture(PlayableCharacterRegistry.empty(), CharacterKey.SONIC, List.of());
        int capacity = com.openggf.graphics.PatternAtlasRange.SIDEKICK_BANKS.size();

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> fixture.initializer().reserveSidekickPatternBank(capacity + 1));

        assertEquals(LevelManager.SIDEKICK_PATTERN_BASE,
                fixture.initializer().reserveSidekickPatternBank(1));
    }

    @Test
    void suppliedPaletteUsesThePlayableSheetsNonzeroPaletteLine() throws Exception {
        CharacterKey key = CharacterKey.mod("owner", "main");
        Palette palette = new Palette();
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(key, definition(key, code -> art(4, 2), code -> palette));
        Fixture fixture = fixture(registry, key, List.of());

        fixture.initializer().initialize();

        com.openggf.sprites.render.PlayerSpriteRenderer renderer = capturedRenderer(fixture.main());
        RenderContext context = rendererContext(renderer);
        assertEquals(palette, context.getPalette(2));
        assertEquals(context.getPaletteLineBase() + 2, context.getEffectivePaletteLine(2));
    }

    @Test
    void missingSidekickManagerNameUsesPersistedKeyInsteadOfGlobalConfig() throws Exception {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        CharacterKey sideKey = CharacterKey.mod("owner", "side");
        Palette mainPalette = new Palette();
        Palette sidePalette = new Palette();
        sidePalette.colors[0].r = 1;
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(4), code -> mainPalette))
                .register(sideKey, definition(sideKey, code -> art(4), code -> sidePalette));
        AbstractPlayableSprite sidekick = sprite(sideKey, sideKey.persisted());
        Fixture fixture = fixture(registry, mainKey, List.of(sidekick));
        when(fixture.sprites().getSidekickCharacterName(sidekick)).thenReturn(null);
        when(fixture.config().getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE))
                .thenReturn(mainKey.persisted());

        fixture.initializer().initialize();

        RenderContext mainContext = rendererContext(capturedRenderer(fixture.main()));
        RenderContext sideContext = rendererContext(capturedRenderer(sidekick));
        org.junit.jupiter.api.Assertions.assertNotSame(mainContext, sideContext);
        assertEquals(sidePalette, sideContext.getPalette(0));
    }

    @Test
    void sameKeyAndDifferentKeySidekicksWithMainPaletteReuseMainContext() throws Exception {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        CharacterKey otherKey = CharacterKey.mod("owner", "other");
        Palette sharedPalette = new Palette();
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(4, 1), code -> sharedPalette))
                .register(otherKey, definition(otherKey, code -> art(4, 1), code -> sharedPalette));
        AbstractPlayableSprite duplicate = sprite(mainKey, mainKey.persisted());
        AbstractPlayableSprite equalPalette = sprite(otherKey, otherKey.persisted());
        Fixture fixture = fixture(registry, mainKey, List.of(duplicate, equalPalette));

        fixture.initializer().initialize();

        RenderContext mainContext = rendererContext(capturedRenderer(fixture.main()));
        org.junit.jupiter.api.Assertions.assertSame(mainContext,
                rendererContext(capturedRenderer(duplicate)));
        org.junit.jupiter.api.Assertions.assertSame(mainContext,
                rendererContext(capturedRenderer(equalPalette)));
    }

    @Test
    void equalPaletteOnDifferentLogicalLineDoesNotReuseAnEmptyMainContextSlot() throws Exception {
        CharacterKey mainKey = CharacterKey.mod("owner", "main");
        CharacterKey sideKey = CharacterKey.mod("owner", "side");
        Palette sharedPalette = new Palette();
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(mainKey, definition(mainKey, code -> art(4, 2), code -> sharedPalette))
                .register(sideKey, definition(sideKey, code -> art(4, 1), code -> sharedPalette));
        AbstractPlayableSprite sidekick = sprite(sideKey, sideKey.persisted());
        Fixture fixture = fixture(registry, mainKey, List.of(sidekick));

        fixture.initializer().initialize();

        RenderContext mainContext = rendererContext(capturedRenderer(fixture.main()));
        RenderContext sideContext = rendererContext(capturedRenderer(sidekick));
        org.junit.jupiter.api.Assertions.assertNotSame(mainContext, sideContext);
        assertEquals(sharedPalette, sideContext.getPalette(1));
    }

    @Test
    void sidekickRuntimeCodeStillSubmitsItsCharacterDplcBank() throws Exception {
        AbstractPlayableSprite tails = sprite(CharacterKey.TAILS, "tails_p2");
        Fixture fixture = fixture(PlayableCharacterRegistry.empty(), CharacterKey.SONIC, List.of(tails));
        GameModule module = mock(GameModule.class);
        var rules = mock(com.openggf.game.rules.GameRules.class);
        when(module.getRules()).thenReturn(rules);
        when(module.getGameId()).thenReturn(GameId.S2);
        when(rules.dynamicArtDmaService()).thenReturn(com.openggf.game.rules.DynamicArtDmaServiceModel.SONIC_2_PROCESS_DMA_QUEUE);
        fixture.level().gameModule = module;
        com.openggf.data.Game game = mock(com.openggf.data.Game.class,
                withSettings().extraInterfaces(com.openggf.sprites.art.PlayerSpriteArtProvider.class));
        fixture.level().game = game;
        when(((com.openggf.sprites.art.PlayerSpriteArtProvider) game).loadPlayerSpriteArt(anyString()))
                .thenReturn(art(8));
        var animation = mock(com.openggf.sprites.managers.PlayableSpriteAnimation.class);
        when(tails.getAnimationManager()).thenReturn(animation);
        var lifecycle = mock(com.openggf.game.resources.DynamicArtLifecycleService.class);
        when(lifecycle.isRunActive()).thenReturn(true);
        when(lifecycle.observePlayerDplc(any(), anyString(), anyInt(), any(), any(), any()))
                .thenReturn(new com.openggf.game.resources.DynamicArtLifecycleService.ArtUpdate(false, -1, List.of()));

        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::dynamicArtLifecycleOrNull).thenReturn(lifecycle);
            services.when(GameServices::graphics).thenReturn(mock(GraphicsManager.class));
            fixture.initializer().initialize();
        }
        var owner = ArgumentCaptor.forClass(com.openggf.game.resources.DynamicArtDecisionOwner.class);
        verify(animation).setDynamicArtDecisionOwner(owner.capture());
        org.junit.jupiter.api.Assertions.assertNotNull(owner.getValue());
        owner.getValue().observe(0);
        verify(lifecycle).observePlayerDplc(eq(GameId.S2), eq("tails"), eq(0), any(),
                eq(com.openggf.game.resources.DynamicArtLifecycleService.DecisionKind.NORMAL_OBJECT), isNull());
    }

    private static Fixture fixture(PlayableCharacterRegistry registry, CharacterKey mainKey,
                                   List<AbstractPlayableSprite> sidekicks) {
        LevelManager level = mock(LevelManager.class);
        when(level.playableCharacterRegistry()).thenReturn(registry);
        SpriteManager sprites = mock(SpriteManager.class);
        AbstractPlayableSprite main = sprite(mainKey, mainKey.persisted());
        when(sprites.getSprite(mainKey.persisted())).thenReturn(main);
        when(sprites.getSidekicks()).thenReturn(sidekicks);
        for (AbstractPlayableSprite sidekick : sidekicks) {
            String persisted = sidekick.characterKey().persisted();
            when(sprites.getSidekickCharacterName(sidekick)).thenReturn(persisted);
        }
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn(mainKey.persisted());
        return new Fixture(new LevelPlayableArtInitializer(level, sprites,
                mock(GraphicsManager.class), config, mock(CrossGameFeatureProvider.class)),
                main, level, sprites, config);
    }

    private static AbstractPlayableSprite sprite(CharacterKey key, String code) {
        AbstractPlayableSprite sprite = mock(AbstractPlayableSprite.class);
        when(sprite.characterKey()).thenReturn(key);
        when(sprite.getCode()).thenReturn(code);
        com.openggf.sprites.render.PlayerSpriteRenderer renderer =
                mock(com.openggf.sprites.render.PlayerSpriteRenderer.class);
        when(sprite.getSpriteRenderer()).thenReturn(renderer);
        return sprite;
    }

    private static CharacterDefinition definition(CharacterKey key,
                                                  CharacterDefinition.ArtSupplier supplier) {
        return new CharacterDefinition(key, key.persisted(), (code, x, y) -> null, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false, supplier);
    }

    private static CharacterDefinition definition(CharacterKey key,
                                                  CharacterDefinition.ArtSupplier supplier,
                                                  CharacterDefinition.PaletteSupplier paletteSupplier) {
        return new CharacterDefinition(key, key.persisted(), (code, x, y) -> null, null,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false, supplier, paletteSupplier);
    }

    private static SpriteArtSet art(int bankSize) {
        return art(bankSize, 0);
    }

    private static SpriteArtSet art(int bankSize, int paletteIndex) {
        return art(bankSize, paletteIndex, 0);
    }

    private static SpriteArtSet art(int bankSize, int paletteIndex, int basePatternIndex) {
        return new SpriteArtSet(new Pattern[]{new Pattern()},
                List.of(new SpriteMappingFrame(List.of())),
                List.of(new SpriteDplcFrame(List.of(new TileLoadRequest(0, 1, 0)))),
                paletteIndex, basePatternIndex, 1, bankSize, null, null);
    }

    private static com.openggf.sprites.render.PlayerSpriteRenderer capturedRenderer(
            AbstractPlayableSprite playable) {
        ArgumentCaptor<com.openggf.sprites.render.PlayerSpriteRenderer> captor =
                ArgumentCaptor.forClass(com.openggf.sprites.render.PlayerSpriteRenderer.class);
        verify(playable).setSpriteRenderer(captor.capture());
        return captor.getValue();
    }

    private static RenderContext rendererContext(
            com.openggf.sprites.render.PlayerSpriteRenderer renderer) throws Exception {
        var field = com.openggf.sprites.render.PlayerSpriteRenderer.class
                .getDeclaredField("renderContext");
        field.setAccessible(true);
        return (RenderContext) field.get(renderer);
    }

    private static SpriteArtSet rendererArt(
            com.openggf.sprites.render.PlayerSpriteRenderer renderer)
            throws ReflectiveOperationException {
        var field = com.openggf.sprites.render.PlayerSpriteRenderer.class.getDeclaredField("artSet");
        field.setAccessible(true);
        return (SpriteArtSet) field.get(renderer);
    }

    private record Fixture(LevelPlayableArtInitializer initializer,
                           AbstractPlayableSprite main, LevelManager level,
                           SpriteManager sprites, SonicConfigurationService config) { }
}
