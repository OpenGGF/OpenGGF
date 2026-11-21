package com.openggf.game.soniccd.special;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.NativeGameStateOps;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The special stage results ({@code Results}, scddisasm/src/Special Stage/Main.asm:281-402): the fade from
 * white before the tally, the tally itself, Start, the points handed to the campaign and the picture.
 */
class TestCdSpecialStageResults {
    /** 10 rings and 5 seconds left: a 200-point ring bonus and a 100-point time bonus. */
    private static CdSpecialStageResult result(int timeStones) {
        return new CdSpecialStageResult(0, true, 0, timeStones, 1, 10, 5, false, true);
    }

    @Test
    void theTallyWaitsForTheFadeFromWhite() {
        var results = new CdSpecialStageResults(result(1), 0, 5000, 3);
        for (int f = 0; f < CdSpecialStageResults.FADE_IN_FRAMES; f++) {
            results.frame(true); // Start is not read until Results_MainLoop
            assertEquals(0, results.score(), "fade frame " + f);
            assertFalse(results.isComplete());
        }
        results.frame(false);
        assertEquals(20, results.score(), "the first main-loop pass moves 20 points");
        assertEquals(List.of(CdSpecialSound.TALLY), results.sounds());
    }

    @Test
    void theRingBonusTalliesBeforeTheTimeBonusThenKaChing() {
        var results = new CdSpecialStageResults(result(1), 0, 5000, 3);
        for (int f = 0; f < CdSpecialStageResults.FADE_IN_FRAMES; f++) results.frame(false);
        for (int f = 0; f < 10; f++) results.frame(false);
        assertEquals(0, results.ringBonus());
        assertEquals(100, results.timeBonus());
        for (int f = 0; f < 5; f++) results.frame(false);
        assertEquals(300, results.score());
        assertEquals(List.of(CdSpecialSound.TALLY, CdSpecialSound.KACHING), results.sounds());
    }

    @Test
    void startEndsTheScreenAndTransfersTheTally() {
        var awarded = new AtomicReference<CdSpecialStageResults.Tally>();
        var results = new CdSpecialStageResults(result(1), 4990, 5000, 3, null, awarded::set);
        for (int f = 0; f < CdSpecialStageResults.FADE_IN_FRAMES + 3; f++) results.frame(false);
        assertNull(awarded.get(), "the tally transfers only when the screen ends");
        results.frame(true);
        assertTrue(results.isComplete());
        assertEquals(new CdSpecialStageResults.Tally(5290, 10_000, 4), awarded.get(),
                ".Done adds the remaining bonus (Main.asm:393-396)");
        assertEquals(4, results.lives(), "5000 crossed once");
        assertEquals(CdSpecialSound.WARP, results.sounds().getLast());
    }

    @Test
    void theScreenEndsAfterEightSeconds() {
        var results = new CdSpecialStageResults(result(1), 0, 5000, 3);
        int frames = 0;
        while (!results.isComplete() && frames < 10_000) {
            results.frame(false);
            frames++;
        }
        assertEquals(CdSpecialStageResults.FADE_IN_FRAMES + 8 * 60, frames);
    }

    @ParameterizedTest
    @CsvSource({"248, 249", "250, 249", "255, 1"})
    void specialStageAwardsTwoLivesToTheCampaignUsingTheDiscLimit(int startingLives, int expectedLives)
            throws Exception {
        try (var disc = CdSpecialNative.disc(); var boot = new HeadlessGameBoot(320, 224)) {
            var module = new SonicCdGameModule();
            var loop = boot.boot(disc, module, 0, 0);
            var campaign = GameServices.gameState();
            while (campaign.getLives() < startingLives) campaign.addLife();

            loop.enterSpecialStage();
            var provider = (CdSpecialStageProvider) module.getSpecialStageProvider();
            // Direct entry has no campaign route to advance when the stage ends.
            provider.onEnded(ignored -> { });
            var stage = provider.session().stage();
            stage.addRings(500); // The results award 10,000 native score units, crossing two thresholds.
            stage.stageOver = true;

            for (int frame = 0; frame < 120 && loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE_RESULTS; frame++) {
                loop.step();
            }
            var results = (CdSpecialStageResults) loop.getResultsScreen();
            for (int frame = 0; frame < CdSpecialStageResults.FADE_IN_FRAMES; frame++) results.frame(false);
            results.frame(true);

            assertEquals(expectedLives, campaign.getLives(), "campaign lives after the special-stage tally");
        }
    }

    @Test
    void specialStageAwardCrossesTheCampaignsFiftyThousandPointThreshold() throws Exception {
        try (var disc = CdSpecialNative.disc(); var boot = new HeadlessGameBoot(320, 224)) {
            var module = new SonicCdGameModule();
            var loop = boot.boot(disc, module, 0, 0);
            var campaign = GameServices.gameState();
            int startingLives = campaign.getLives();
            campaign.addScore(49_800);

            loop.enterSpecialStage();
            var provider = (CdSpecialStageProvider) module.getSpecialStageProvider();
            provider.onEnded(ignored -> { });
            var stage = provider.session().stage();
            stage.addRings(1);
            stage.timer = 0;
            stage.stageOver = true;

            for (int frame = 0; frame < 120 && loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE_RESULTS; frame++) {
                loop.step();
            }
            var results = (CdSpecialStageResults) loop.getResultsScreen();
            for (int frame = 0; frame < CdSpecialStageResults.FADE_IN_FRAMES; frame++) results.frame(false);
            results.frame(true);

            assertEquals(50_000, campaign.getScore());
            assertEquals(startingLives + 1, campaign.getLives());
        }
    }

    @Test
    void campaignKeepsTheSpecialStageLifeEarnedAtTheScoreCap() throws Exception {
        try (var disc = CdSpecialNative.disc(); var boot = new HeadlessGameBoot(320, 224)) {
            var module = new SonicCdGameModule();
            var loop = boot.boot(disc, module, 0, 0);
            var campaign = GameServices.gameState();
            for (int threshold = 1; threshold < 200; threshold++) campaign.addScore(50_000);
            campaign.addScore(49_990);
            NativeGameStateOps.restoreLives(campaign, 3);
            campaign.addScore(10);
            assertEquals(3, campaign.getLives(), "level AddPoints tests the capped score");

            loop.enterSpecialStage();
            var provider = (CdSpecialStageProvider) module.getSpecialStageProvider();
            provider.onEnded(ignored -> { });
            var stage = provider.session().stage();
            stage.addRings(1);
            stage.stageOver = true;

            for (int frame = 0; frame < 120 && loop.getCurrentGameMode() != GameMode.SPECIAL_STAGE_RESULTS; frame++) {
                loop.step();
            }
            var results = (CdSpecialStageResults) loop.getResultsScreen();
            for (int frame = 0; frame < CdSpecialStageResults.FADE_IN_FRAMES; frame++) results.frame(false);
            results.frame(true);

            assertEquals(4, results.lives(), "the uncapped tally crosses 1,000,000 native score units");
            assertEquals(4, campaign.getLives(), "the campaign receives the tally's extra life");
            assertEquals(9_999_990, campaign.getScore(), "the stored score stays capped in points");
            assertEquals(10_050_000, module.getGameService(
                    com.openggf.game.soniccd.program.CdSceneCoordinator.class).scorePolicy().nextLifeScore(),
                    "the campaign advances its 1-Up threshold");
        }
    }

    @Test
    void levelScoreOneUpWrapsTheLivesByte() throws Exception {
        try (var disc = CdSpecialNative.disc(); var boot = new HeadlessGameBoot(320, 224)) {
            boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var campaign = GameServices.gameState();
            NativeGameStateOps.restoreLives(campaign, 255);

            campaign.addScore(50_000);

            assertEquals(0, campaign.getLives(), "AddPoints increments the lives byte");
        }
    }

    @Test
    void levelScoreOneUpCanPassTheSpecialStageLimit() throws Exception {
        try (var disc = CdSpecialNative.disc(); var boot = new HeadlessGameBoot(320, 224)) {
            boot.boot(disc, new SonicCdGameModule(), 0, 0);
            var campaign = GameServices.gameState();
            while (campaign.getLives() < 249) campaign.addLife();

            campaign.addScore(50_000);

            assertEquals(250, campaign.getLives(), "campaign lives after a level score 1-Up");
        }
    }

    @Test
    void thePictureFadesInAndDrawsTheTallyFromTheDisc() throws Exception {
        try (CdDiscDataSource disc = CdSpecialNative.disc()) {
            var assets = CdSpecialStageAssets.load(disc);
            var results = new CdSpecialStageResults(result(0b0000011), 0, 5000, 3, assets, points -> { });
            var picture = results.picture();
            assertEquals(0x0EEE, picture.colour(5), "PrepFadeFromWhite: all white before the first frame");
            for (int f = 0; f < CdSpecialStageResults.FADE_IN_FRAMES; f++) results.frame(false);
            short[] pal = CdSpecialStageAssets.words(assets.raw("Pal_Results"));
            for (int i = 0; i < 64; i++) assertEquals(pal[i] & 0xFFFF, picture.colour(i), "colour " + i + " after the fade");
            for (int f = 0; f < 15; f++) results.frame(false);
            assertEquals(300, results.score());
            // Score at $C730 (row 14, column 24): 300 units draw as 3000, four leading blanks.
            for (int c = 0; c < 4; c++) assertEquals(0, picture.planeA(14, 24 + c), "blank " + c);
            assertEquals(picture.digitUpper(3) & 0xFFFF, picture.planeA(14, 28));
            for (int c = 29; c < 32; c++) assertEquals(picture.digitUpper(0) & 0xFFFF, picture.planeA(14, c));
            // Both bonuses are zero: seven blanks then one zero (.DrawZero).
            assertEquals(0, picture.planeA(17, 30));
            assertEquals(picture.digitUpper(0) & 0xFFFF, picture.planeA(17, 31));
            assertNotEquals(null, results.resultsBackdropColor());
        }
    }
}
