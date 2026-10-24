package com.openggf.game.soniccd.player;

import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.resources.CdPlayerVisualAdapter;
import com.openggf.level.LevelManager;
import com.openggf.sprites.render.PlayerResidentBanks;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Source-backed adapter checks; this direct slot test does not admit an engine route. */
class TestCdPlayableSonic {
    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.soniccd.SonicCdGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0x70B);
        GameServices.graphics().initHeadless();
    }

    @AfterEach void close() { TestEnvironment.resetAll(); }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void actorProjectsOnlyTheCompletedNativeBodyAndKeepsArtServiceOutsideTheSlot(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var runtime = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", runtime, GameServices.graphics());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        var cold = runtime.captureActor();
        assertEquals(0, actor.nativeState().motion().routine());
        assertEquals(64, actor.getCentreX());
        assertEquals(1899, actor.getCentreY());
        assertEquals(24, actor.getSpriteRenderer().patternBankCapacity());
        assertEquals(0, runtime.artState().lastFrame());
        assertTrue(runtime.display().isEmpty());
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertEquals(cold, runtime.captureActor(), "an unserviced slot must not initialize the player");

        runtime.prepareInput(0);
        runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1);
        assertEquals(2, actor.nativeState().motion().routine());
        assertEquals(1899, actor.getCentreY());
        assertEquals(0, actor.getMappingFrame());
        assertTrue(runtime.display().isEmpty(), "Init is not Main or DrawObject");
        assertEquals(0, runtime.artState().lastFrame());
        assertThrows(IllegalStateException.class, () -> actor.runPlayableSlot(level, 1));
        assertThrows(IllegalStateException.class, actor::captureRewindState);
        actor.finishAfterCamera();

        runtime.prepareInput(0);
        runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 2);
        assertEquals(1898, actor.getCentreY());
        assertEquals(0xFC, actor.getAngle() & 255);
        assertEquals(0, actor.getYSubpixelRaw());
        assertEquals(actor.nativeState().animation().mapFrame(), actor.getMappingFrame());
        assertTrue(runtime.display().isPresent());
        assertEquals(0, runtime.artState().lastFrame(), "new Main pose is for the next V-int");
        assertEquals(4, actor.nativeState().motion().spriteFlags(), "DrawObject clears before the camera pass");
        actor.finishAfterCamera();
        assertEquals(0x84, actor.nativeState().motion().spriteFlags(), "SAT sets without a GPU draw");

        runtime.prepareInput(0);
        runtime.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3);
        actor.finishAfterCamera();
        var tiles = PlayerResidentBanks.capture(actor.getSpriteRenderer()).tiles();
        var expected = CdPlayerVisualAdapter.residentPatterns(runtime.artState());
        assertEquals(24, tiles.length);
        for (int i = 0; i < tiles.length; i++) {
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                assertEquals(expected[i].getPixel(x, y), tiles[i].getPixel(x, y));
            }
        }
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void rendererReplacementGenericResetAndAnotherStageRejectWithoutChangingNativeState(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var runtime = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", runtime, GameServices.graphics());
        var original = actor.getSpriteRenderer();
        var state = runtime.captureActor();
        var other = new PlayerSpriteRenderer(CdPlayerVisualAdapter.artSet(source.assets().sonic(), 0x20000),
                GameServices.graphics());
        assertThrows(UnsupportedOperationException.class, () -> actor.setSpriteRenderer(other));
        assertThrows(UnsupportedOperationException.class, actor::resetState);
        assertSame(original, actor.getSpriteRenderer());
        assertEquals(state, runtime.captureActor());
        var wrongLevel = mock(LevelManager.class);
        assertThrows(IllegalArgumentException.class, () -> actor.runPlayableSlot(wrongLevel, 1));
        assertEquals(state, runtime.captureActor());
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void unsupportedBodyAfterARealServiceClosesAdmissionInsteadOfSkippingAMain(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1); actor.finishAfterCamera();
        frame.applyScrollFeedback(new CdPlayerMovement.Bounds(0, 0x2897, 0, 0), 96);
        var beforeBody = frame.state();
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertEquals(CdPlayerBody.UnsupportedBoundary.KILL_PLAYER,
                assertThrows(CdPlayerBody.UnsupportedBody.class, () -> actor.runPlayableSlot(level, 2)).boundary());
        assertSame(beforeBody, frame.state(), "the pure Body must not publish its unfinished death continuation");
        assertTrue(frame.closed(), "the V-int already advanced; the engine must rebuild instead of retrying a missing Main");
        assertThrows(IllegalStateException.class, () -> frame.prepareInput(0));
        assertThrows(IllegalStateException.class, actor::draw);
    }

    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void foreignActorCaptureRejectionReprojectsTheRetainedNativeOwner(CdReleaseVariant variant) throws Exception {
        var source = source(variant);
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics());
        var foreignFrame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var foreign = new CdPlayableSonic("sonic", foreignFrame, GameServices.graphics());
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        for (int i = 1; i <= 25; i++) {
            foreignFrame.prepareInput(8); foreignFrame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
            foreign.runPlayableSlot(level, i); foreign.finishAfterCamera();
        }
        assertNotEquals(actor.getCentreX(), foreign.getCentreX());
        var before = frame.captureActor();
        assertThrows(IllegalArgumentException.class, () -> actor.restoreRewindState(foreign.captureRewindState()));
        assertEquals(before, frame.captureActor());
        assertEquals(before.state().motion().centreX16_16() >> 16, actor.getCentreX());
        assertEquals(before.state().motion().centreY16_16() >> 16, actor.getCentreY());
        assertEquals(before.state().motion().xVelocity(), actor.getXSpeed());
        assertFalse(frame.closed(), "an owner mismatch rejects before native restore and does not require a cold path");
        frame.close(); foreignFrame.close();
    }

    private static com.openggf.game.soniccd.CdGameDataSource source(CdReleaseVariant variant) throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply the three retail media directories");
        String directory = switch (variant) { case USA -> "USA"; case JAPAN -> "Japan"; case EUROPE -> "Europe"; };
        try (var files = Files.list(Path.of(root).resolve(directory))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size());
            return CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
    }
}
