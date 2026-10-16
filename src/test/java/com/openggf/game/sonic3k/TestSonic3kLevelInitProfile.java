package com.openggf.game.sonic3k;

import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.StaticFixup;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class TestSonic3kLevelInitProfile {
    @com.openggf.tests.rules.RequiresRom(com.openggf.tests.rules.SonicGame.SONIC_3K)
    @Test
    public void nativeEndingLoadCreatesNoTitleOwnerEvenWhenPresentationIsOmitted() {
        com.openggf.tests.HeadlessTestFixture.builder().withZoneAndAct(0x0D,1)
                .withFreshLevelStartLifecycle().build();
        assertFalse(com.openggf.game.GameServices.level().isTitleCardRequested());
        assertFalse(com.openggf.game.GameServices.camera().isLevelStarted(),
                "Ending_ScreenInit retains native D01 HUD suppression after fresh initialization");
        var provider=com.openggf.game.GameServices.module().getTitleCardProvider();
        assertFalse(provider.ownsInLevelPlayerControlLock(),"$D01 must not retain a title PLC/player owner");
        var title=(com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager)provider;
        assertFalse(title.ownsOmittedFreshLevelPresentation());
        assertTrue(title.capture().artHandles().isEmpty(),"native early deletion submits no title archives");
    }

    @Test
    void authoredNativeLevelDoesNotDispatchAStaleStockPlayerIntro() {
        verifyPlayerIntroOwnership(false);
    }

    @Test
    void stockNativeLevelRetainsItsPlayerIntroDispatch() {
        verifyPlayerIntroOwnership(true);
    }

    private void verifyPlayerIntroOwnership(boolean stockIdentity) {
        var events = org.mockito.Mockito.mock(Sonic3kLevelEventManager.class);
        var manager = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        var loaded = org.mockito.Mockito.mock(Sonic3kLevel.class);
        org.mockito.Mockito.when(manager.getCurrentLevel()).thenReturn(loaded);
        org.mockito.Mockito.when(loaded.hasStockRomZoneIdentity()).thenReturn(stockIdentity);
        try (var services = org.mockito.Mockito.mockStatic(com.openggf.game.GameServices.class)) {
            services.when(com.openggf.game.GameServices::levelOrNull).thenReturn(manager);
            services.when(com.openggf.game.GameServices::level).thenReturn(manager);
            new Sonic3kLevelInitProfile(events).initZonePlayerStateStep(new LevelLoadContext()).execute();
        }
        if (stockIdentity) org.mockito.Mockito.verify(events).applyZonePlayerStateForLoad(false);
        else org.mockito.Mockito.verifyNoInteractions(events);
    }

    private final Sonic3kLevelInitProfile profile =
            new Sonic3kLevelInitProfile(new Sonic3kLevelEventManager());

    @Test
    public void teardownHasExpectedSteps() {
        List<InitStep> steps = profile.levelTeardownSteps();

        assertTrue(steps.size() >= 10, "Should have at least 10 teardown steps");

        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Audio")), "Should include audio reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("S3kLevelEvents")), "Should include S3k level event reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Parallax")), "Should include parallax reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("LevelManager")), "Should include level manager reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Sprites")), "Should include sprite reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Collision")), "Should include collision reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Camera")), "Should include camera reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Graphics")), "Should include graphics reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Fade")), "Should include fade reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("GameState")), "Should include game state reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Timers")), "Should include timer reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Water")), "Should include water reset");
    }

    @Test
    public void perTestResetHasExpectedSteps() {
        List<InitStep> steps = profile.perTestResetSteps();

        assertTrue(steps.size() >= 7, "Should have at least 7 per-test reset steps");

        assertTrue(steps.stream().anyMatch(s -> s.name().contains("AizIntroPhaseState")), "Should include AIZ intro phase reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Parallax")), "Should include parallax reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Sprites")), "Should include sprite reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Collision")), "Should include collision reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Camera")), "Should include camera reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Fade")), "Should include fade reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("GameState")), "Should include game state reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Timers")), "Should include timer reset");
        assertTrue(steps.stream().anyMatch(s -> s.name().contains("Water")), "Should include water reset");
    }

    @Test
    public void postTeardownFixupsContainAizIntroPhaseReset() {
        List<StaticFixup> fixups = profile.postTeardownFixups();

        assertTrue(fixups.size() >= 1, "Should have at least 1 post-teardown fixup");
        assertTrue(fixups.stream().anyMatch(f -> f.name().contains("AizIntroPhaseState")), "Should include AIZ intro phase fixup");
    }

    @Test
    public void levelLoadStepsWithoutPostLoadHasMinimumSteps() {
        List<InitStep> steps = profile.levelLoadSteps(new LevelLoadContext());
        assertTrue(steps.size() >= 11, "Should have at least 11 level load steps without post-load");
    }

    @Test
    public void levelLoadStepsWithPostLoadHasExpectedSteps() {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setIncludePostLoadAssembly(true);
        List<InitStep> steps = profile.levelLoadSteps(ctx);

        assertTrue(steps.size() >= 17, "Should have at least 17 level load steps with post-load");

        // Verify key ROM-aligned resource loading steps are present
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitGameModule")), "Should include InitGameModule");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitAudio")), "Should include InitAudio");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("LoadLevelData")), "Should include LoadLevelData");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitAnimatedContent")), "Should include InitAnimatedContent");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitObjectSystem")), "Should include InitObjectSystem");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitGameplayState")), "Should include InitGameplayState");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitRings")), "Should include InitRings");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitZoneFeatures")), "Should include InitZoneFeatures");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitArt")), "Should include InitArt");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitPlayerAndCheckpoint")), "Should include InitPlayerAndCheckpoint");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitWater")), "Should include InitWater");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitBackgroundRenderer")), "Should include InitBackgroundRenderer");

        // Verify key post-load assembly steps are present
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("RestoreCheckpoint")), "Should include RestoreCheckpoint");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("SpawnPlayer")), "Should include SpawnPlayer");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("ResetPlayerState")), "Should include ResetPlayerState");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitCamera")), "Should include InitCamera");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("InitLevelEvents")), "Should include InitLevelEvents");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("SpawnSidekick")), "Should include SpawnSidekick");
        assertTrue(steps.stream().anyMatch(s -> s.name().equals("RequestTitleCard")), "Should include RequestTitleCard");
    }

}


