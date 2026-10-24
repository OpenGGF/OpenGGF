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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    // Routine-4 ObjSonic_Hurt projection (Sonic/Main.asm:2351-2366). Written before production.
    @ParameterizedTest @EnumSource(CdReleaseVariant.class)
    void hurtDrawObjectIsQueuedOnBothActorPathsWhereMainDisplayWouldSkipAndReplaysAfterRestore(CdReleaseVariant variant)
            throws Exception {
        var source = source(variant);
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(source.assets()));
        var frame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics());
        var sharedFrame = CdPlayerFrameRuntime.terrainOnlyDevelopment(source.assets());
        var pass = new HurtPass(sharedFrame);
        var shared = new CdPlayableSonic("sonic", sharedFrame, GameServices.graphics(), pass);
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 1); actor.finishAfterCamera();
        sharedFrame.prepareInput(0); sharedFrame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        shared.runPlayableSlot(level, 1); shared.finishSharedAfterCamera();
        assertTrue(pass.cuts.isEmpty(), "Init has no DrawObject");
        installPitHurt(frame); actor.rebindAfterRestore();
        installPitHurt(sharedFrame); shared.rebindAfterRestore();

        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 2);
        // Debug low byte: both KillPlayer calls return unwritten, so Hurt keeps routine 4 and still draws.
        assertEquals(4, actor.nativeState().motion().routine());
        assertEquals(120, actor.nativeState().timers().hurt(), "no Display decrement");
        assertEquals(2040, actor.getCentreY()); assertTrue(actor.lastSoundRequests().isEmpty());
        assertTrue(frame.display().orElseThrow().queued());
        assertEquals(0, actor.nativeState().motion().spriteFlags() & 0x80, "DrawObject clears bit 7 before the camera pass");
        actor.finishAfterCamera();
        assertEquals(0x80, actor.nativeState().motion().spriteFlags() & 0x80, "the on-screen Hurt pose reaches DrawDone");
        assertEquals(actor.nativeState().animation().mapFrame(), actor.getMappingFrame());
        assertEquals(0x5D, actor.getMappingFrame(), "SonAni_Hurt dc.b 3,$5D,$FF (Data/Animations.asm:105)");
        var savedActor = actor.captureRewindState(); var savedNative = frame.captureActor(); var savedShared = frame.captureShared();
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3); actor.finishAfterCamera();
        var advanced = frame.captureActor();
        actor.restoreRewindState(savedActor); frame.restoreShared(savedShared); actor.rebindAfterRestore();
        assertEquals(savedNative, frame.captureActor());
        frame.prepareInput(0); frame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        actor.runPlayableSlot(level, 3); actor.finishAfterCamera();
        assertEquals(advanced, frame.captureActor());

        sharedFrame.prepareInput(0); sharedFrame.serviceVBlank(PlcLifecyclePhase.ORDINARY_LEVEL);
        shared.runPlayableSlot(level, 2);
        var published = pass.last.result();
        assertEquals(CdPlayerBody.Boundary.HURT_RETURN, published.boundary());
        var cuts = published.effects().stream().filter(CdPlayerBody.DisplayEligibility.class::isInstance)
                .map(CdPlayerBody.DisplayEligibility.class::cast).toList();
        assertEquals(cuts, pass.cuts); assertTrue(cuts.getFirst().eligible());
        sharedFrame.publishDrawObjectOnScreen(true); shared.finishSharedAfterCamera();
        assertEquals(0x80, shared.nativeState().motion().spriteFlags() & 0x80);
        frame.close(); sharedFrame.close();
    }

    /** Same-owner closed-cut edit: routine-4 pit pose, debug low byte and all three Main Display skip gates. */
    private static void installPitHurt(CdPlayerFrameRuntime frame) {
        var actor = frame.captureActor(); var s = actor.state(); var m = s.motion();
        var motion = new CdPlayerMotionState(m.centreX16_16(), 2040 << 16, 0, 0, 0, 4, m.angle(), 2, 0x84,
                m.xRadius(), m.yRadius(), 0x1A, m.previousAnimation(), 0, 0, 0, 0, 0x40,
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        frame.restoreActor(new CdPlayerFrameRuntime.ActorState(actor.owner(), new CdPlayerBody.State(motion, s.animation(),
                s.header(), new CdPlayerBody.Timers(120, 0, 0, s.timers().reset()), s.history(), s.rotationDistance()), Optional.empty()));
        var shared = frame.captureShared(); var e = shared.environment(); var g = e.globals(); var mv = e.movement();
        var environment = new CdPlayerBody.Environment(new CdPlayerMovement.Environment(mv.physics(),
                new CdPlayerMovement.Bounds(0, 0x2897, 0x710, 0), mv.gates(), mv.globals()), e.controls(),
                new CdPlayerBody.Globals(g.levelFrames(), g.cameraXCentre(), g.timeAttackMode(), 1, g.debugCheat(), 210,
                        g.invincible(), g.bossMusic()), e.dynamicSlots());
        frame.restoreShared(new CdPlayerFrameRuntime.SharedState(shared.owner(), environment, shared.artState(),
                shared.paused(), shared.vIntClock()));
    }

    /** Recording shared-owner port: the real Frame/Body runs; only the queue's bit-7 writes are simulated. */
    private static final class HurtPass implements CdPlayableSonic.ObjectPass {
        final CdPlayerFrameRuntime frame;
        final List<CdPlayerBody.DisplayEligibility> cuts = new ArrayList<>();
        CdPlayerFrameRuntime.BodyPublication last;
        HurtPass(CdPlayerFrameRuntime frame) { this.frame = frame; }
        @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
            last = frame.runBody(levelFrames); return last;
        }
        @Override public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) {
            cuts.add(cut); frame.publishDrawObjectOnScreen(false);
        }
        @Override public void close() { }
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
