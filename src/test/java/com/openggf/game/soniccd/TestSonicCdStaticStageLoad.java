package com.openggf.game.soniccd;

import com.openggf.LevelFrameResult;
import com.openggf.GameLoop;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameServices;
import com.openggf.game.GameMode;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.mode.ControlledFrameRuntime;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Parent validation supplies soniccd.media.root; no synthetic CD source can admit this test. */
@Isolated
class TestSonicCdStaticStageLoad {
    @BeforeEach void prepareEngine() {
        TestEnvironment.resetAll();
        GameServices.graphics().initHeadless();
    }

    @AfterEach void releaseEngine() {
        SessionManager.clear();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest(name = "{0}: actual lifecycle, held presentation, restore and reset")
    @EnumSource(CdReleaseVariant.class)
    void authenticatedStageLoadsThroughTheNormalLifecycle(CdReleaseVariant variant) throws Exception {
        var source = CdStockLaunch.prepare(suppliedCue(variant), CdStockLaunch.Request.r11a());
        var module = new SonicCdGameModule();
        var otherModule = new SonicCdGameModule();
        var firstGame = module.createGame(source);
        var otherGame = otherModule.createGame(source);
        var detachedFirst = (SonicCdLevel) firstGame.loadLevel(0);
        var detachedOther = (SonicCdLevel) otherGame.loadLevel(0);
        assertNotSame(detachedFirst, detachedOther);
        assertNotSame(detachedFirst.getPattern(1), detachedOther.getPattern(1));
        assertNotSame(detachedFirst.getPalette(0), detachedOther.getPalette(0));
        assertTrue(firstGame.isCompatible());
        assertFalse(firstGame.canSave());
        assertFalse(firstGame.canRelocateLevels());
        assertThrows(UnsupportedOperationException.class, () -> firstGame.loadLevel(1));
        assertThrows(UnsupportedOperationException.class, () -> module.createTouchResponseTable(source));
        assertThrows(UnsupportedOperationException.class, () -> firstGame.save(0, detachedFirst));
        assertThrows(UnsupportedOperationException.class, () -> firstGame.relocateLevels(false));
        if (variant == CdReleaseVariant.USA) {
            var anotherDisc = CdStockLaunch.prepare(suppliedCue(CdReleaseVariant.JAPAN), CdStockLaunch.Request.r11a());
            assertThrows(IllegalArgumentException.class, () -> module.createGame(anotherDisc),
                    "one module cannot silently replace its observed whole-disc identity");
            assertDoesNotThrow(() -> new SonicCdGameModule().createGame(anotherDisc));
        }

        var audio = GameServices.audio();
        var backend = new RetirementBackend();
        audio.setBackend(backend);
        var prior = GameAudioProfile.silentNative();
        audio.setAudioProfile(prior);
        var mode = SessionManager.openGameplaySession(module, module, source, null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        var loop = new GameLoop(EngineServices.current());
        loop.setGameplayMode(mode);
        loop.setInputHandler(new InputHandler());
        loop.setGameMode(GameMode.LEVEL);
        var manager = mode.getLevelManager();
        var context = SonicCdLevelInitProfile.staticStageContext();
        int priorStops = backend.stops;
        manager.loadLevel(0, LevelLoadMode.FULL, context);
        var level = assertInstanceOf(SonicCdLevel.class, manager.getCurrentLevel());
        assertSame(level, context.getLevel());
        assertSame(source.assets(), level.assets());
        assertSame(module.getAudioProfile(), audio.getAudioProfile());
        assertNotSame(prior, audio.getAudioProfile());
        assertTrue(backend.stops > priorStops, "the profile must stop previous backend playback");
        assertTrue(mode.getWorldSession().getDataSource().rom().isEmpty());
        assertNull(mode.getCamera().getFocusedSprite());
        assertTrue(mode.getCamera().getFrozen());
        assertEquals(0, mode.getCamera().getX());
        assertEquals(0x70B, mode.getCamera().getY(), "native centre 0x76B minus camera centre 0x60");
        assertEquals(source.assets().loadPlan().bounds().bottom(), mode.getCamera().getMaxY());
        assertNull(manager.getObjectManager());
        assertNull(manager.getRingManager());
        assertNotNull(manager.getTilemapManager());
        assertNotNull(GameServices.graphics().getPatternAtlasEntry(0));
        assertNotNull(GameServices.graphics().getPatternAtlasEntry(level.getPatternCount() - 1));
        assertNotNull(manager.getLevelGamestate());
        assertTrue(manager.getLevelGamestate().isTimerPaused());
        assertTrue(level.getObjects().isEmpty());
        assertTrue(level.getRings().isEmpty());
        assertFalse(level.nativePlacements().records().isEmpty(), "raw native placements remain data");
        assertFalse(manager.hasPendingInitialProcessSpritesPass());
        assertFalse(ControlledFrameRuntime.prepareSetup(mode), "no fabricated native setup pass");
        int frame = manager.getFrameCounter();
        long timer = manager.getLevelGamestate().getTimerFrames();
        assertEquals(LevelFrameResult.HELD, ControlledFrameRuntime.step(mode,
                new InputHandler(), LogicalInputSnapshot.neutral()));
        assertEquals(frame, manager.getFrameCounter());
        assertEquals(timer, manager.getLevelGamestate().getTimerFrames());
        assertNull(manager.getObjectManager(), "HOLD must not lazily create native actors");
        assertTrue(mode.hardwareTiming().pendingHandles().isEmpty(), "static entry submits no artificial device jobs");
        assertEquals(GameMode.LEVEL, loop.getCurrentGameMode());

        var snapshot = mode.getRewindRegistry().capture();
        short cameraY = mode.getCamera().getY();
        mode.getCamera().setY((short) (cameraY - 16));
        mode.getRewindRegistry().restore(snapshot);
        assertEquals(cameraY, mode.getCamera().getY());
        assertSame(level, manager.getCurrentLevel());

        var unsupported = SonicCdLevelInitProfile.staticStageContext();
        unsupported.setShowTitleCard(true);
        assertThrows(IOException.class, () -> manager.loadLevel(0, LevelLoadMode.FULL, unsupported));
        assertSame(level, manager.getCurrentLevel(), "rejected requests cannot destroy the admitted view");

        for (InitStep step : module.getLevelInitProfile().perTestResetSteps()) step.execute();
        assertSame(level, manager.getCurrentLevel(), "per-test reset retains decoded terrain");
        assertNull(mode.getCamera().getFocusedSprite());
        assertEquals(cameraY, mode.getCamera().getY());
        assertFalse(ControlledFrameRuntime.prepareSetup(mode));
        for (InitStep step : module.getLevelInitProfile().levelTeardownSteps()) step.execute();
        assertNull(manager.getCurrentLevel());
    }

    private static final class RetirementBackend extends NullAudioBackend {
        private int stops;
        @Override public void stopPlayback() { stops++; }
    }

    private static Path suppliedCue(CdReleaseVariant variant) throws IOException {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Set soniccd.media.root for supplied discs");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var paths = Files.list(Path.of(root).resolve(directory))) {
            var cues = paths.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return cues.getFirst();
        }
    }
}
