package com.openggf.game.soniccd.player;

import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.soniccd.CdStockLaunch;
import com.openggf.game.soniccd.level.SonicCdLevel;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.level.LevelManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual supplied USA player, with a recording shared-pass port; not World/queue admission. */
class TestCdPlayableSonicObjectPass {
    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.soniccd.SonicCdGameModule());
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
    }
    @AfterEach void close() { TestEnvironment.resetAll(); }

    @Test void sharedPassGetsOneBodyAndEligibleCutWithoutThePrivateSpriteBuild() throws Exception {
        var f = fixture();
        assertThrows(IllegalStateException.class, () -> f.actor.runPlayableSlot(f.level, 1));
        assertEquals(0, f.pass.calls); assertEquals(0, f.pass.closes); assertFalse(f.frame.closed());
        service(f.frame); f.actor.runPlayableSlot(f.level, 1);
        assertEquals(1, f.pass.calls); assertTrue(f.pass.cuts.isEmpty());
        assertThrows(IllegalStateException.class, f.actor::captureRewindState);
        assertThrows(IllegalStateException.class, f.actor::finishAfterCamera);
        f.actor.finishSharedAfterCamera();
        service(f.frame); f.actor.runPlayableSlot(f.level, 2);
        assertEquals(2, f.pass.calls); assertEquals(1, f.pass.cuts.size());
        var cut = f.pass.cuts.getFirst();
        assertEquals(24, cut.width()); assertEquals(2, cut.priority());
        assertEquals(0, f.frame.state().motion().spriteFlags() & 0x80);
        // The shared queue owns this write; finishing the actor only projects it.
        f.frame.publishDrawObjectOnScreen(true);
        f.actor.finishSharedAfterCamera();
        assertEquals(0x80, f.actor.nativeState().motion().spriteFlags() & 0x80);
        var capture = f.actor.captureRewindState(); var nativeState = f.frame.captureActor();
        f.actor.draw(); f.actor.draw();
        assertEquals(nativeState, f.frame.captureActor());
        assertEquals(2, f.pass.calls); assertEquals(1, f.pass.cuts.size());
        f.actor.restoreRewindState(capture); f.actor.rebindAfterRestore();
        assertEquals(nativeState, f.frame.captureActor());
    }

    @Test void sharedEnqueueFailureClosesBothOwnersAfterTheCompletedBody() throws Exception {
        var f = fixture(); service(f.frame); f.actor.runPlayableSlot(f.level, 1); f.actor.finishSharedAfterCamera();
        f.pass.enqueueFailure = new IllegalStateException("shared queue failed");
        service(f.frame);
        assertSame(f.pass.enqueueFailure,
                assertThrows(IllegalStateException.class, () -> f.actor.runPlayableSlot(f.level, 2)));
        assertTrue(f.frame.closed()); assertEquals(1, f.pass.closes);
        assertEquals(2, f.frame.environment().globals().levelFrames(), "No fake Body rollback after enqueue failure");
        assertThrows(IllegalStateException.class, () -> f.frame.prepareInput(0));
        assertThrows(IllegalStateException.class, f.actor::draw);
    }

    @Test void sharedBodyFailureClosesBothOwnersAfterService() throws Exception {
        var f = fixture(); f.pass.bodyFailure = new IllegalStateException("body port failed");
        service(f.frame);
        assertSame(f.pass.bodyFailure,
                assertThrows(IllegalStateException.class, () -> f.actor.runPlayableSlot(f.level, 1)));
        assertTrue(f.frame.closed()); assertEquals(1, f.pass.closes); assertTrue(f.pass.cuts.isEmpty());
    }

    @Test void failedRendererAdmissionAlsoClosesTheSuppliedSharedPass() throws Exception {
        var assets = source().assets();
        var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
        var pass = new RecordingPass(frame, CdPlayerObjectTouch.fromAssets(assets.openingObjects()));
        assertThrows(NullPointerException.class, () -> new CdPlayableSonic("sonic", frame, null, pass));
        assertTrue(frame.closed()); assertEquals(1, pass.closes);
    }

    private static Fixture fixture() throws Exception {
        var assets = source().assets();
        var frame = CdPlayerFrameRuntime.externalTouchRequiredDevelopment(assets);
        var pass = new RecordingPass(frame, CdPlayerObjectTouch.fromAssets(assets.openingObjects()));
        var actor = new CdPlayableSonic("sonic", frame, GameServices.graphics(), pass);
        var level = mock(LevelManager.class);
        when(level.getCurrentLevel()).thenReturn(new SonicCdLevel(assets));
        return new Fixture(frame, pass, actor, level);
    }
    private record Fixture(CdPlayerFrameRuntime frame, RecordingPass pass, CdPlayableSonic actor, LevelManager level) { }
    private static void service(CdPlayerFrameRuntime frame) {
        frame.serviceVBlank(frame.prepareInput(0).phase());
    }
    private static final class RecordingPass implements CdPlayableSonic.ObjectPass {
        final CdPlayerFrameRuntime frame; final CdPlayerObjectTouch scanner;
        final List<CdPlayerBody.DisplayEligibility> cuts = new ArrayList<>();
        int calls, closes; RuntimeException bodyFailure, enqueueFailure;
        CdPlayerBody.Boundary lastBoundary;
        RecordingPass(CdPlayerFrameRuntime frame, CdPlayerObjectTouch scanner) { this.frame = frame; this.scanner = scanner; }
        @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
            calls++;
            if (bodyFailure != null) throw bodyFailure;
            var publication = frame.runBody(levelFrames, Optional.of(new CdPlayerBody.TouchContext(scanner, new CdObjectPool().capture())));
            lastBoundary = publication.result().boundary();
            return publication;
        }
        @Override public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) {
            if (enqueueFailure != null) throw enqueueFailure;
            cuts.add(cut); frame.publishDrawObjectOnScreen(false);
        }
        @Override public void close() { closes++; }
    }
    private static com.openggf.game.soniccd.CdGameDataSource source() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Supply soniccd.media.root");
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var cues = files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size()); return CdStockLaunch.prepare(cues.getFirst(), CdStockLaunch.Request.r11a());
        }
    }
    @Test void sharedPassQueuesTheGenuineControlledReturnButKeepsItsDisplayIneligibleCountercase() throws Exception {
        var f = fixture(); service(f.frame); f.actor.runPlayableSlot(f.level, 1); f.actor.finishSharedAfterCamera();
        var cut = f.frame.captureActor(); var state = cut.state(); var m = state.motion();
        var controlled = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                0x2D, 0x2D, m.playerJump(), m.charge(), m.moveLock(), m.stick(), 1,
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(cut.owner(),
                new CdPlayerBody.State(controlled, new CdPlayerBody.AnimationCursor(3, 0, 0x4C),
                        state.header(), state.timers(), state.history(), state.rotationDistance(), state.objectLinks(), state.rotationAngle()),
                cut.display()));
        f.actor.rebindAfterRestore();
        service(f.frame); f.actor.runPlayableSlot(f.level, 2);
        assertEquals(CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN, f.pass.lastBoundary);
        assertEquals(1, f.pass.cuts.size(), "The actual completed controlled Display must reach the shared queue");
        assertEquals(0x1F, f.frame.state().animation().mapFrame()); assertFalse(f.frame.closed());
        f.actor.finishSharedAfterCamera();

        cut = f.frame.captureActor(); state = cut.state(); m = state.motion();
        var hidden = new CdPlayerMotionState(m.centreX16_16(), m.centreY16_16(), m.xVelocity(), m.yVelocity(),
                m.groundVelocity(), m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(),
                m.animation(), m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), 0x41,
                m.previousPrimaryAngle(), m.previousSecondaryAngle());
        f.frame.restoreActor(new CdPlayerFrameRuntime.ActorState(cut.owner(),
                new CdPlayerBody.State(hidden, state.animation(), state.header(), state.timers(), state.history(),
                        state.rotationDistance(), state.objectLinks(), state.rotationAngle()), cut.display()));
        f.actor.rebindAfterRestore();
        service(f.frame); f.actor.runPlayableSlot(f.level, 3);
        assertEquals(CdPlayerBody.Boundary.CONTROLLED_MAIN_RETURN, f.pass.lastBoundary);
        assertEquals(1, f.pass.cuts.size(), "Control bit6 keeps the genuine Display ineligible");
        assertFalse(f.frame.closed()); assertEquals(3, f.pass.calls);
        f.actor.finishSharedAfterCamera();
    }

}
