package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameDataSource;
import com.openggf.game.GameId;
import com.openggf.game.InitStep;
import com.openggf.game.InitialProcessSpritesLifecycle;
import com.openggf.game.LevelAssemblyKind;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.mode.GameplayFrameDisposition;
import com.openggf.game.rules.PaletteWhiteFadeModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TestSonicCdModuleContracts {
    @Test void foreignSourcesAreRejectedBeforeAnyCapabilityIsRead() {
        var module = new SonicCdGameModule();
        GameDataSource foreign = new GameDataSource() {
            @Override public Optional<com.openggf.data.Rom> rom() { throw new AssertionError("ROM queried"); }
            @Override public java.io.InputStream openAsset(String path) { throw new AssertionError("asset queried"); }
            @Override public String identity() { throw new AssertionError("claimed identity queried"); }
        };
        assertThrows(IllegalArgumentException.class, () -> module.createGame(foreign));
        assertThrows(IllegalArgumentException.class, () -> module.createTouchResponseTable(foreign));
        assertThrows(IllegalArgumentException.class, () -> new SonicCd(foreign));
        assertThrows(IllegalArgumentException.class, () -> module.createGame(null));
    }

    @Test void moduleIsStockAndOwnsFreshProvidersWithoutRegisteringNativeActorsOrJobs() {
        var first = new SonicCdGameModule();
        var second = new SonicCdGameModule();
        assertEquals(GameId.SCD, first.getGameId());
        assertEquals("scd", first.getGameCode());
        assertTrue(first.getPlayableCharacterRegistry().definitions().isEmpty());
        assertNotSame(first.getLevelInitProfile(), second.getLevelInitProfile());
        assertNotSame(first.getPhysicsProvider(), second.getPhysicsProvider());
        assertNotSame(first.getAudioProfile(), second.getAudioProfile());
        assertNotSame(first.gameplayFrameController(), second.gameplayFrameController());
        assertNotSame(first.getScrollHandlerProvider(), second.getScrollHandlerProvider());
        assertNotSame(first.createLevelState(), first.createLevelState());
        assertNotSame(first.createRespawnState(), second.createRespawnState());
        assertTrue(first.rewindAdapters().isEmpty(), "level/camera owners already capture static state");
        assertNull(first.getLevelEventProvider());
        assertNull(first.getObjectArtProvider());
        assertNull(first.getZoneFeatureProvider());
        assertNull(first.getDonorCapabilities());
        assertSame(com.openggf.game.profiles.trace.TracePlaybackProfile.DISABLED, first.getTracePlaybackProfile());
        assertTrue(first.suppressesLevelSelect());
        assertNull(first.getZoneRegistry().modZoneRuntimeContribution(0));
        assertThrows(UnsupportedOperationException.class, first::getObjectPlacementEncoding);
        assertThrows(UnsupportedOperationException.class, first::getPlaneSwitcherConfig);
        assertThrows(UnsupportedOperationException.class, () -> first.onNewGameFromTitle(null));
        assertThrows(UnsupportedOperationException.class, first::getSaveSnapshotProvider);
        assertThrows(UnsupportedOperationException.class, () -> first.createObjectRegistry().objectSlotLayout());
        assertThrows(UnsupportedOperationException.class, () -> first.createLevelState().setRings(1));
        assertThrows(UnsupportedOperationException.class, () -> first.createLevelState().resumeTimer());
        assertThrows(UnsupportedOperationException.class,
                () -> first.createRespawnState().restoreFromSaved(1, 2, 3, 4, 1));
        assertEquals(List.of(GameDataSource.class), java.util.Arrays.stream(SonicCdGameModule.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("createGame"))
                .map(method -> method.getParameterTypes()[0]).toList(), "only the canonical source factory");
        assertTrue(java.util.Arrays.stream(SonicCd.class.getMethods()).noneMatch(method -> method.getName().equals("getRom")));
    }

    @Test void staticRulesOnlyDescribeConsultedPresentationPaths() {
        var module = new SonicCdGameModule();
        var rules = module.getRules();
        assertEquals(PaletteWhiteFadeModel.SCREEN_OVERLAY, rules.paletteWhiteFade());
        assertFalse(rules.camera().waterShimmerEnabled());
        assertNotNull(rules.dynamicArtDmaService());
        assertNull(rules.playerMovement(), "native player rules are not implemented");
        assertNull(rules.collision(), "converted drawing words do not implement CD collision");
        assertThrows(UnsupportedOperationException.class, () -> module.getPhysicsProvider().getProfile("sonic"));
        assertThrows(UnsupportedOperationException.class, () -> module.getPhysicsProvider().getModifiers());
        assertEquals(GameplayFrameDisposition.HOLD,
                module.gameplayFrameController().beforeFrame(null, LogicalInputSnapshot.neutral()));
        assertTrue(module.gameplayFrameController().requiresExclusiveControl());
        assertTrue(module.gameplayFrameController().drawScene());
        assertThrows(IllegalStateException.class, () -> module.gameplayFrameController().afterTick(null, true));
        assertNull(module.getAudioProfile().createSmpsLoader(null));
        assertNull(module.getAudioProfile().getSequencerConfig());
        assertEquals("scd", module.getAudioProfile().presentationGameId());
        assertTrue(module.getAudioProfile().getSoundMap().isEmpty());
    }

    @Test void profileDeclaresEveryExecutedStepAndRejectsUnsupportedAssemblyRequests() {
        var profile = new SonicCdGameModule().getLevelInitProfile();
        var context = SonicCdLevelInitProfile.staticStageContext();
        List<String> names = profile.levelLoadSteps(context).stream().map(InitStep::name).toList();
        assertEquals(List.of("AdmitStaticStage", "ResetStaticRuntime", "InitGameModule",
                "RetirePriorAudio", "ConfigureStaticAudio", "LoadLevelData", "InitStaticLevelState",
                "InitStaticCamera", "InitStaticBackground", "InitZoneFeatures", "InitBackgroundRenderer",
                "RegisterStaticLevelAdapters"), names);
        assertEquals(InitialProcessSpritesLifecycle.NONE, profile.initialProcessSpritesLifecycle());
        assertEquals(0, profile.freshMainPlayablePreludeFrames());
        assertFalse(profile.isLevelMusicPublicationPending());
        assertFalse(profile.levelTeardownSteps().isEmpty());
        assertFalse(profile.perTestResetSteps().isEmpty());
        assertTrue(profile.postTeardownFixups().isEmpty());

        context.setShowTitleCard(true);
        assertThrows(UnsupportedOperationException.class, () -> profile.levelLoadSteps(context));
        context.setShowTitleCard(false);
        context.setQueueFreshLevelRuntimeArt(true);
        assertThrows(UnsupportedOperationException.class, () -> profile.levelLoadSteps(context));
        context.setQueueFreshLevelRuntimeArt(false);
        context.setAssemblyKind(LevelAssemblyKind.STATE_RESTORATION);
        assertThrows(UnsupportedOperationException.class, () -> profile.levelLoadSteps(context));
        context.setAssemblyKind(LevelAssemblyKind.FRESH_LEVEL_ASSEMBLY);
        context.setLoadCause(LevelLoadCause.FULL_DEATH_RELOAD);
        assertThrows(UnsupportedOperationException.class, () -> profile.levelLoadSteps(context));
        context.setLoadCause(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        context.setLoadMode(LevelLoadMode.PREVIEW_CAPTURE);
        assertThrows(UnsupportedOperationException.class, () -> profile.levelLoadSteps(context));
        assertDoesNotThrow(() -> profile.levelLoadSteps(new LevelLoadContext()));
    }
}
