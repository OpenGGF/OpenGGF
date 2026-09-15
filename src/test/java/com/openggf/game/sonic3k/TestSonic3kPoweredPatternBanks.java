package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SuperState;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kPoweredPatternBanks {
    private RomByteReader reader;

    @BeforeEach
    void setup() throws Exception {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
        GameServices.level().resetLevelGamestate(GameModuleRegistry.getCurrent().createLevelState());
        GameServices.sprites().clearAllSprites();
        for (int i = 0; i < 7; i++) GameServices.gameState().markEmeraldCollected(i);
        reader = new RomByteReader(Files.readAllBytes(Path.of(System.getProperty("s3k.rom.path"))));
    }

    @Test
    void sonicMainAndTwoPoweredOwnersKeepIndependentFullBanksAcrossRevertAndRewind() throws Exception {
        verifyTeam(new Sonic("sonic", (short) 0, (short) 0));
    }

    @Test
    void knucklesMainAndPoweredSonicKeepIndependentFullBanksAcrossRevertAndRewind() throws Exception {
        verifyTeam(new Knuckles("knuckles", (short) 0, (short) 0));
    }

    private void verifyTeam(AbstractPlayableSprite main) throws Exception {
        GameServices.sprites().addSprite(main, main instanceof Knuckles ? "knuckles" : "sonic");
        var art = new Sonic3kPlayerArt(reader).loadFormArtSet("sonic", S3kFormTier.NORMAL);
        assertEquals(29, art.bankSize());
        main.setSpriteRenderer(new PlayerSpriteRenderer(main instanceof Knuckles
                ? new Sonic3kPlayerArt(reader).loadFormArtSet("knuckles", S3kFormTier.NORMAL) : art));
        Sonic first = secondary(art, "sonic_p2");
        Sonic second = secondary(art, "sonic_p3");
        var normal = first.getSpriteRenderer();
        var firstController = controller(first);
        var secondController = controller(second);
        activate(firstController);
        activate(secondController);
        var powered = first.getSpriteRenderer();
        assertEquals(33, powered.patternBankCapacity());
        assertTrue(PatternAtlasRange.SIDEKICK_BANKS.contains(powered.patternBankBase()));
        assertTrue(powered.patternBankBase() >= second.getSpriteRenderer().patternBankBase()
                + second.getSpriteRenderer().patternBankCapacity()
                || second.getSpriteRenderer().patternBankBase() >= powered.patternBankBase() + 33);
        assertTrue(powered.patternBankBase() >= normal.patternBankBase() + normal.patternBankCapacity());
        assertEquals(0x680, main.getSpriteRenderer().patternBankBase());
        assertPublishedPixels(main, first, second);
        var snapshot = first.captureRewindState();
        firstController.debugDeactivate();
        assertSame(normal, first.getSpriteRenderer());
        first.restoreRewindState(snapshot);
        assertSame(powered, first.getSpriteRenderer());
        assertPublishedPixels(main, first, second);
        firstController.debugDeactivate();
        first.setRingCount(50);
        // The movement loop expires the one-frame revert invincibility grace.
        first.setInvincibleFrames(0);
        activate(firstController);
        assertSame(powered, first.getSpriteRenderer(), "repeated activation must retain the allocated bank");
        if (main instanceof Sonic) {
            activate(controller(main));
            assertEquals(0x680, main.getSpriteRenderer().patternBankBase(), "native Player_1 addressing stays native");
            assertPublishedPixels(main, first, second);
        }
    }

    private void assertPublishedPixels(AbstractPlayableSprite... players) {
        var graphics = GameServices.graphics();
        var expected = new java.util.HashMap<Integer, com.openggf.graphics.SpritePresentation.PatternVersion>();
        for (int i = 0; i < players.length; i++) {
            int frame = i + 1;
            var renderer = players[i].getSpriteRenderer();
            var isolated = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0,
                    () -> renderer.drawFrame(frame, 0, 0, false, false));
            assertFalse(isolated.patternVersions().isEmpty());
            expected.putAll(isolated.patternVersions());
        }
        for (boolean deferred : new boolean[]{false, true}) {
            for (boolean reversed : new boolean[]{false, true}) {
                var combined = com.openggf.level.render.SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                    if (deferred) graphics.beginSpriteSatCollection();
                    for (int j = 0; j < players.length; j++) {
                        int i = reversed ? players.length - 1 - j : j;
                        players[i].getSpriteRenderer().drawFrame(i + 1, i * 64, 0, false, false);
                    }
                    if (deferred) graphics.endSpriteSatCollectionAndReplay();
                });
                assertEquals(expected, combined.patternVersions(), "each owner must retain its isolated ROM pixels");
            }
        }
    }

    private Sonic secondary(SpriteArtSet art, String name) {
        int base = GameServices.level().reserveSidekickPatternBank(art.bankSize());
        var shifted = new SpriteArtSet(art.artTiles(), art.mappingFrames(), art.dplcFrames(),
                art.paletteIndex(), base, art.frameDelay(), art.bankSize(), art.animationProfile(), art.animationSet());
        Sonic player = new Sonic(name, (short) 0, (short) 0);
        player.setCpuControlled(true);
        player.setSpriteRenderer(new PlayerSpriteRenderer(shifted));
        return player;
    }

    private Sonic3kSuperStateController controller(AbstractPlayableSprite player) {
        player.setRingCount(50);
        var controller = new Sonic3kSuperStateController(player);
        player.setSuperStateController(controller);
        controller.loadRomData(reader);
        return controller;
    }

    private void activate(Sonic3kSuperStateController controller) {
        assertTrue(controller.activateFromAirAbility());
        for (int i = 0; i < 30; i++) controller.update();
        assertEquals(SuperState.SUPER, controller.getState());
    }
}
