package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.continuescreen.Sonic2ContinueScreenProvider;
import com.openggf.game.sonic2.credits.Sonic2EndingArt;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestKis2EndingContinue {
    private LockOnAddressSpace rom;
    private Kis2PlayerArt player;

    @BeforeEach void load() throws Exception {
        var dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        var sk = dump.window(0, 0x200000);
        rom = LockOnAddressSpace.tierTwo(sk, RomByteReader.fromRom(TestEnvironment.currentRom()), dump);
        player = new Kis2PlayerArt(sk, BuiltInRomDetectors.forGame(GameId.S3K).createModule()
                .getCrossGameDonorProvider().createPlayerArtProvider(sk));
        GameServices.graphics().initHeadless();
    }

    @Test void continueUsesOneKnucklesObjectAndExitsAtItsOwnBoundary() throws Exception {
        var profile = new Kis2ContinuePresentation(player);
        var chip = new Kis2ChipArt(rom);
        var screen = new Sonic2ContinueScreenProvider(() -> chip.load(Kis2ChipArt.Asset.CONTINUE_ICON), profile);
        screen.initialize(3);
        for (int i = 0; i < 250; i++) { screen.update(false, false); screen.draw(); }
        var tailsField = Sonic2ContinueScreenProvider.class.getDeclaredField("tails");
        tailsField.setAccessible(true);
        assertNull(tailsField.get(screen), "KiS2 ContinueScreen never creates Sidekick");
        screen.update(true, false); // acceleration starts in the acceptance frame
        for (int i = 0; i < 77; i++) { screen.update(false, false); screen.draw(); }
        assertFalse(screen.isFinished());
        screen.update(false, false); // 64 acceleration frames + 15 position updates to x=$18C
        assertTrue(screen.isFinished());
        assertTrue(screen.isAccepted());
        screen.reset();
        screen.initialize(3);
        for (int i = 0; i < 658; i++) screen.update(false, false);
        assertTrue(screen.isFinished());
        assertFalse(screen.isAccepted());
    }

    @Test void bothEmeraldOutcomesCompleteTheCutsceneWithKnucklesPresentation() throws Exception {
        var presentation = new Kis2EndingPresentation(rom, player);
        for (boolean allEmeralds : new boolean[]{false, true}) {
            if (allEmeralds) for (int i = 0; i < 7; i++) GameServices.gameState().markEmeraldCollected(i);
            assertEquals(allEmeralds ? Sonic2EndingArt.EndingRoutine.SUPER_SONIC : Sonic2EndingArt.EndingRoutine.SONIC,
                    presentation.endingRoutine());
            var manager = new com.openggf.game.sonic2.credits.Sonic2EndingCutsceneManager();
            manager.setPresentation(presentation);
            manager.initialize(TestEnvironment.currentRom());
            for (int i = 0; i < 6000 && !manager.isDone(); i++) manager.update();
            assertTrue(manager.isDone(), "Cutscene must reach credits for emerald outcome " + allEmeralds);
        }
    }

    @Test void endingAndBannerDecodeOnlyFromRomAndAllPiecesHaveLoadedArt() throws Exception {
        var presentation = new Kis2EndingPresentation(rom, player);
        var art = new Sonic2EndingArt();
        art.loadArt(TestEnvironment.currentRom(), Sonic2EndingArt.EndingRoutine.SONIC);
        art.loadPalettes(TestEnvironment.currentRom(), Sonic2EndingArt.EndingRoutine.SONIC);
        presentation.applyArt(art);
        assertTrue(art.getCharacterPatterns().length > 100);
        assertEquals(player.loadKnuckles().artTiles().length, art.getPlayerPatterns().length);
        assertTrue(presentation.objectFrames().size() >= 0x19);
        for (int frame : presentation.floatingFrames()) assertTrue(frame < presentation.playerArt().mappingFrames().size());
        var logo = presentation.logo();
        var patterns = logo.patterns(new com.openggf.level.Pattern[0]);
        var frames = logo.frames(new com.openggf.level.render.SpriteMappingFrame(java.util.List.of()));
        assertEquals(3, frames.size());
        for (var frame : frames) for (var piece : frame.pieces()) {
            assertTrue(piece.tileIndex() + piece.widthTiles() * piece.heightTiles() <= patterns.length);
            assertNotNull(patterns[piece.tileIndex()]);
        }
        assertEquals(4, art.getEndingPalettes().length);
        assertNotNull(logo.bannerPalette());
    }
}
