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
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
                withSettings().extraInterfaces(com.openggf.data.PlayerSpriteArtProvider.class));
        fixture.level().game = game;
        SpriteArtSet stock = art(8);
        when(((com.openggf.data.PlayerSpriteArtProvider) game).loadPlayerSpriteArt("sonic"))
                .thenReturn(stock);

        fixture.initializer().initialize();

        verify((com.openggf.data.PlayerSpriteArtProvider) game).loadPlayerSpriteArt("sonic");
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
                withSettings().extraInterfaces(com.openggf.data.PlayerSpriteArtProvider.class));
        level.game = game;
        SpriteArtSet stock = art(8, 0, 0x1234);
        when(((com.openggf.data.PlayerSpriteArtProvider) game).loadPlayerSpriteArt("tails"))
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
                withSettings().extraInterfaces(com.openggf.data.SpindashDustArtProvider.class));
        fixture.level().game = game;
        when(((com.openggf.data.SpindashDustArtProvider) game).loadSpindashDustArt(anyString()))
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
