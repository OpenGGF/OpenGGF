package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.ghost.GhostRenderRegistry;
import com.openggf.game.run.GhostPose;
import com.openggf.game.run.PlayerPose;
import com.openggf.game.run.RunEndReason;
import com.openggf.game.run.RunHandle;
import com.openggf.game.run.RunHost;
import com.openggf.game.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestHostedRunController {
    private static final RunSpec SPEC = new RunSpec("s3k", 0, 0, "sonic", GameplayRunPolicy.isolatedAct());

    private final HostedRunController controller = new HostedRunController(mock(SonicConfigurationService.class));

    @Test
    void handleCommandsAreQueuedUntilConsumedOnce() {
        RunHandle handle = controller.begin(SPEC, new RunHost() { });
        assertTrue(controller.isActive());
        handle.retry();
        handle.leave();
        assertTrue(controller.consumeRetry());
        assertFalse(controller.consumeRetry());
        assertEquals(RunEndReason.LEFT, controller.consumeLeave());
        assertEquals(null, controller.consumeLeave());
    }

    @Test
    void endingTellsTheHostOnceAndDeactivatesTheHandle() {
        List<RunEndReason> ended = new ArrayList<>();
        RunHandle handle = controller.begin(SPEC, new RunHost() {
            @Override public void onRunEnded(RunEndReason reason) { ended.add(reason); }
        });
        controller.end(RunEndReason.ACT_COMPLETED);
        controller.end(RunEndReason.ABORTED);
        assertEquals(List.of(RunEndReason.ACT_COMPLETED), ended);
        assertFalse(handle.isActive());
        assertFalse(controller.isActive());
        handle.retry();
        assertFalse(controller.consumeRetry(), "a stale handle cannot command a later run");
    }

    @Test
    void aFailingHostAbortsTheRunInsteadOfTheEngine() {
        controller.begin(SPEC, new RunHost() {
            @Override public void drawOverlay(com.openggf.mods.ui.LevelOverlayCanvas canvas) {
                throw new IllegalStateException("creator bug");
            }
        });
        controller.drawOverlay(null);
        assertEquals(RunEndReason.ABORTED, controller.consumeLeave());
    }

    @Test
    void onlyOneRunAtATime() {
        controller.begin(SPEC, new RunHost() { });
        assertThrows(IllegalStateException.class, () -> controller.begin(SPEC, new RunHost() { }));
    }

    @Test
    void reattachingTheGhostRendererDoesNotStackRegistrations() {
        // A retry re-announces the level on the same GameplayModeContext, so the ghost layer
        // renderer must be detached before re-registering, or it draws N+1 times.
        GhostRenderRegistry registry = new GhostRenderRegistry();
        controller.begin(SPEC, new RunHost() { });
        controller.attachRenderer(registry);
        controller.attachRenderer(registry);
        assertFalse(registry.isEmpty());
        controller.end(RunEndReason.LEFT);
        assertTrue(registry.isEmpty(), "ending the run must leave no ghost renderer registered");
    }

    @Test
    void ghostPoseMapsToTheEngineGhostFrame() {
        GhostPose pose = new GhostPose("best", "tails",
                new PlayerPose(100, 200, 7, true, false, 3, true), "ME", 0.5f);
        var active = HostedRunController.toActiveGhost(pose);
        assertEquals("best", active.slotId());
        assertEquals("tails", active.characterCode());
        assertEquals(100, active.frame().x());
        assertEquals(200, active.frame().y());
        assertEquals(7, active.frame().mappingFrame());
        assertTrue(active.frame().hFlip());
        assertEquals(3, active.frame().priorityBucket());
        assertTrue(active.frame().highPriority());
        assertEquals("ME", active.nameplate());
        assertEquals(0.5f, active.opacityScale());
    }
}
