package com.openggf.level;

import com.openggf.configuration.*;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.*;
import com.openggf.sprites.playable.*;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
class TestDonorTailsTailBanks {
    private GraphicsManager graphics;

    @org.junit.jupiter.api.BeforeEach
    void initializeGraphics() {
        GraphicsManager.destroyForReinit();
        com.openggf.game.session.EngineServices.configure(
                com.openggf.game.session.EngineContext.fromLegacySingletonsForBootstrap());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupGraphics() {
        com.openggf.game.session.SessionManager.clear();
        GraphicsManager.destroyForReinit();
    }

    @ParameterizedTest
    @CsvSource({"S1,false,false", "S1,true,false", "S3K,false,false", "S3K,true,false",
            "S1,false,true", "S1,true,true", "S3K,false,true", "S3K,true,true"})
    void donorTailHasIndependentValidBank(GameId hostId, boolean tailsSidekick, boolean prepared) throws Exception {
        var loader = new Sonic2PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()));
        SpriteArtSet tailsArt = loader.loadTails();
        SpriteArtSet sonicArt = loader.loadSonic();
        GameModule host = mock(GameModule.class);
        when(host.getGameId()).thenReturn(hostId);
        when(host.getTailsTailVramBase()).thenReturn(-1);
        LevelManager level = mock(LevelManager.class);
        level.gameModule = host;
        when(level.activeGameModule()).thenReturn(host);
        when(level.playableCharacterRegistry()).thenReturn(PlayableCharacterRegistry.empty());
        CrossGameFeatureProvider donor = mock(CrossGameFeatureProvider.class);
        when(donor.loadPlayerSpriteArt(anyString())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).startsWith("sonic") ? sonicArt : tailsArt);
        SpriteManager sprites = mock(SpriteManager.class);
        AbstractPlayableSprite main = playable(tailsSidekick ? CharacterKey.SONIC : CharacterKey.TAILS, false);
        AbstractPlayableSprite side = playable(CharacterKey.TAILS, true);
        List<AbstractPlayableSprite> sidekicks = new ArrayList<>();
        sidekicks.add(side);
        if (prepared) sidekicks.add(playable(CharacterKey.mod("test", "extra"), true));
        when(sprites.getSprite(main.getCode())).thenReturn(main);
        when(sprites.getSidekicks()).thenReturn(sidekicks);
        for (var sprite : sidekicks) {
            String name = sprite.characterKey().persisted();
            when(sprites.getSidekickCharacterName(sprite)).thenReturn(name);
        }
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        String mainCode = main.getCode();
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn(mainCode);
        try (var active = mockStatic(CrossGameFeatureProvider.class)) {
            active.when(CrossGameFeatureProvider::isActive).thenReturn(true);
            new LevelPlayableArtInitializer(level, sprites, graphics, config, donor).initialize();
        }
        List<PlayerSpriteRenderer> banks = new ArrayList<>();
        banks.add(renderer(main));
        for (var sprite : sidekicks) banks.add(renderer(sprite));
        if (!tailsSidekick) banks.add(tailRenderer(main));
        banks.add(tailRenderer(side));
        for (int i = 0; i < banks.size(); i++) {
            var bank = banks.get(i);
            assertTrue(bank.patternBankBase() >= 0, "invalid donor bank " + bank.patternBankBase());
            assertEquals(i == 0 && tailsSidekick ? 31 : 28, bank.patternBankCapacity());
            for (int j = 0; j < i; j++) {
                var other = banks.get(j);
                assertTrue(bank.patternBankBase() >= other.patternBankBase() + other.patternBankCapacity()
                        || other.patternBankBase() >= bank.patternBankBase() + bank.patternBankCapacity(), "overlapping banks");
            }
        }
        verify(host, never()).getTailsTailVramBase();
        // Obj05 directional (rolling) and spindash tail frames, against every body
        // frame: this also exercises stale slots left by larger preceding DPLCs.
        PlayerSpriteRenderer tail = tailRenderer(tailsSidekick ? side : main);
        PlayerSpriteRenderer mainRenderer = renderer(main);
        PlayerSpriteRenderer sideRenderer = renderer(side);
        for (int tailFrame : new int[]{0x49, 0x4A, 0x4B, 0x4C, 0x81, 0x82, 0x83, 0x84}) {
            var expected = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0,
                    () -> tail.drawFrame(tailFrame, 0, 0, false, false));
            assertFalse(expected.patternVersions().isEmpty());
            for (boolean sat : new boolean[]{false, true}) {
                for (boolean tailFirst : new boolean[]{false, true}) {
                    for (int bodyFrame = 0; bodyFrame < tailsArt.mappingFrames().size(); bodyFrame++) {
                        int frame = bodyFrame;
                        var combined = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                            if (sat) graphics.beginSpriteSatCollection();
                            if (tailFirst) tail.drawFrame(tailFrame, 0, 0, false, false);
                            mainRenderer.drawFrame(frame, 0, 0, false, false);
                            sideRenderer.drawFrame(frame, 0, 0, false, false);
                            if (!tailFirst) tail.drawFrame(tailFrame, 0, 0, false, false);
                            if (sat) graphics.endSpriteSatCollectionAndReplay();
                        });
                        expected.patternVersions().forEach((id, pixels) -> assertEquals(pixels,
                                combined.patternVersions().get(id), "donor tail pixels at " + id));
                    }
                }
            }
        }
    }

    @org.junit.jupiter.api.Test
    void nativeSharedTailRetainsS2Address() throws Exception {
        SpriteArtSet art = new Sonic2PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom())).loadTails();
        LevelManager level = mock(LevelManager.class);
        level.gameModule = new com.openggf.game.sonic2.Sonic2GameModule();
        var initializer = new LevelPlayableArtInitializer(level, mock(SpriteManager.class), graphics,
                mock(SonicConfigurationService.class), mock(CrossGameFeatureProvider.class));
        AbstractPlayableSprite tails = playable(CharacterKey.TAILS, false);
        var method = LevelPlayableArtInitializer.class.getDeclaredMethod("initTailsTailsLegacy",
                AbstractPlayableSprite.class, SpriteArtSet.class);
        method.setAccessible(true);
        try (var active = mockStatic(CrossGameFeatureProvider.class)) {
            active.when(CrossGameFeatureProvider::isActive).thenReturn(false);
            method.invoke(initializer, tails, art);
        }
        assertEquals(0x7B0, tailRenderer(tails).patternBankBase());
    }

    private static AbstractPlayableSprite playable(CharacterKey key, boolean sidekick) {
        AbstractPlayableSprite sprite = key.equals(CharacterKey.TAILS) ? mock(Tails.class) : mock(AbstractPlayableSprite.class);
        when(sprite.characterKey()).thenReturn(key);
        when(sprite.getCode()).thenReturn(key.persisted() + (sidekick ? "_p2" : ""));
        when(sprite.isCpuControlled()).thenReturn(sidekick);
        return sprite;
    }
    private static PlayerSpriteRenderer renderer(AbstractPlayableSprite sprite) {
        var captor = ArgumentCaptor.forClass(PlayerSpriteRenderer.class);
        verify(sprite).setSpriteRenderer(captor.capture());
        return captor.getValue();
    }
    private static PlayerSpriteRenderer tailRenderer(AbstractPlayableSprite sprite) {
        var captor = ArgumentCaptor.forClass(TailsTailsController.class);
        verify(sprite).setTailsTailsController(captor.capture());
        return captor.getValue().getRenderer();
    }
}
