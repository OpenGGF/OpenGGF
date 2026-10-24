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
        RecordingPass(CdPlayerFrameRuntime frame, CdPlayerObjectTouch scanner) { this.frame = frame; this.scanner = scanner; }
        @Override public CdPlayerFrameRuntime.BodyPublication runBody(int levelFrames) {
            calls++;
            if (bodyFailure != null) throw bodyFailure;
            return frame.runBody(levelFrames, Optional.of(new CdPlayerBody.TouchContext(scanner, new CdObjectPool().capture())));
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
}
