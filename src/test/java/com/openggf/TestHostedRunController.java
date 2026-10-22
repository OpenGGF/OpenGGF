package com.openggf;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.ghost.GhostRenderRegistry;
import com.openggf.mods.run.GhostPose;
import com.openggf.mods.run.PlayerPose;
import com.openggf.mods.run.RunEndReason;
import com.openggf.mods.run.RunHandle;
import com.openggf.mods.run.RunHost;
import com.openggf.mods.run.RunSpec;
import com.openggf.game.session.GameplayRunPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void playerAppearanceIsHeldForTheRunAndResetByTheNextOne() {
        var red = new com.openggf.net.identity.PlayerAppearance(0x00E, List.of());
        RunHandle handle = controller.begin(SPEC, new RunHost() { });
        assertEquals(com.openggf.net.identity.PlayerAppearance.STOCK, controller.localLook());
        handle.setPlayerAppearance(red);
        assertEquals(red, controller.localLook());
        assertThrows(NullPointerException.class, () -> handle.setPlayerAppearance(null));
        controller.end(RunEndReason.LEFT);
        handle.setPlayerAppearance(red);
        assertEquals(com.openggf.net.identity.PlayerAppearance.STOCK, controller.localLook(),
                "an ended run's handle no longer changes the look");
        controller.begin(SPEC, new RunHost() { });
        assertEquals(com.openggf.net.identity.PlayerAppearance.STOCK, controller.localLook());
    }

    @Test
    void retryOnActCompletionDefaultsToFalse() {
        assertFalse(controller.retryOnActCompletion(), "no run, nothing to retry");
        controller.begin(SPEC, new RunHost() { });
        assertFalse(controller.retryOnActCompletion(), "a host that does not opt in ends the run");
        assertEquals(null, controller.consumeLeave());
    }

    @Test
    void retryOnActCompletionReportsTheHostAnswerUnlessALeaveIsPending() {
        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        RunHandle handle = controller.begin(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() {
                asked.incrementAndGet();
                return true;
            }
        });
        assertTrue(controller.retryOnActCompletion());
        assertTrue(controller.retryOnActCompletion(), "a query, not a consumed command");
        assertEquals(2, asked.get());
        handle.leave();
        assertFalse(controller.retryOnActCompletion(), "a pending leave wins over a retry");
        assertEquals(2, asked.get(), "the host is not asked once it is leaving");
        assertEquals(RunEndReason.LEFT, controller.consumeLeave());
    }

    @Test
    void actCompletionRetriesOnARequestOrTheHostAnswerAndOtherwiseEnds() {
        assertEquals(RunEndReason.ACT_COMPLETED, controller.resolveActCompletion(), "no run to retry");
        boolean[] answer = {false};
        RunHandle handle = controller.begin(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() { return answer[0]; }
        });
        handle.retry();
        assertNull(controller.resolveActCompletion(), "a retry requested this frame wins over the host return");
        assertFalse(controller.consumeRetry(), "and is consumed by the completion");
        answer[0] = true;
        assertNull(controller.resolveActCompletion(), "the host asks for a retry");
        answer[0] = false;
        assertEquals(RunEndReason.ACT_COMPLETED, controller.resolveActCompletion());
        handle.retry();
        handle.leave();
        assertEquals(RunEndReason.ACT_COMPLETED, controller.resolveActCompletion(), "a pending leave wins");
        assertEquals(null, controller.consumeLeave(), "the leave is consumed by the completion");
    }

    @Test
    void aHostFailureAtActCompletionEndsTheRunAsAbortedInsteadOfRetrying() {
        int[] asked = {0};
        controller.begin(SPEC, new RunHost() {
            @Override public void drawOverlay(com.openggf.mods.ui.LevelOverlayCanvas canvas) {
                throw new IllegalStateException("creator bug");
            }
            @Override public boolean retryOnActCompletion() {
                asked[0]++;
                return true;
            }
        });
        controller.drawOverlay(null); // fails before the completion boundary
        assertEquals(RunEndReason.ABORTED, controller.resolveActCompletion());
        assertEquals(0, asked[0], "a failed host is not asked to retry");

        controller.end(RunEndReason.ABORTED);
        controller.begin(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() { throw new IllegalStateException("creator bug"); }
        });
        assertEquals(RunEndReason.ABORTED, controller.resolveActCompletion(), "the failed query aborts the run");
    }

    @Test
    void aLeaveRaisedWhileAnsweringWinsOverTheRetry() {
        RunHandle[] handle = new RunHandle[1];
        handle[0] = controller.begin(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() {
                handle[0].leave();
                return true;
            }
        });
        assertFalse(controller.retryOnActCompletion(), "a reload would only precede the leave");
        assertEquals(RunEndReason.LEFT, controller.consumeLeave());
    }

    @Test
    void aFailingRetryQueryAbortsTheRunAndAnswersFalse() {
        controller.begin(SPEC, new RunHost() {
            @Override public boolean retryOnActCompletion() {
                throw new IllegalStateException("creator bug");
            }
        });
        assertFalse(controller.retryOnActCompletion());
        assertTrue(controller.isActive(), "the abort ends the run at the caller's boundary");
        assertEquals(RunEndReason.ABORTED, controller.consumeLeave());
    }

    @Test
    void anOwnedRetryQueryForwardsThroughTheOwnerBoundary() {
        controller.begin(SPEC, ownedFactory(new java.util.concurrent.atomic.AtomicReference<>())
                .guard(new RunHost() {
                    @Override public boolean retryOnActCompletion() { return true; }
                }));
        assertTrue(controller.retryOnActCompletion(), "the guard must not hide the creator's answer");
    }

    @Test
    void anOwnedRetryQueryFaultRetiresTheRunLikeOtherCallbacks() {
        var disabled = new java.util.concurrent.atomic.AtomicReference<java.util.Set<String>>();
        List<RunEndReason> endings = new ArrayList<>();
        RunHandle handle = controller.begin(SPEC, ownedFactory(disabled).guard(new RunHost() {
            @Override public boolean retryOnActCompletion() {
                throw new IllegalStateException("bad retry query");
            }
            @Override public void onRunEnded(RunEndReason reason) { endings.add(reason); }
        }));
        var aborted = assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                controller::retryOnActCompletion);
        assertEquals("bad retry query", aborted.getCause().getMessage());
        assertEquals(java.util.Set.of("racer"), disabled.get());
        assertFalse(controller.isActive());
        assertFalse(handle.isActive());
        assertEquals(List.of(RunEndReason.ABORTED), endings);
    }

    @Test
    void theLoadThatFinishesSatisfiesARetryRequestedBeforeIt() {
        // A second retry press during the reload fade must not reload the new attempt again.
        RunHandle handle = controller.begin(SPEC, new RunHost() { });
        handle.retry();
        assertTrue(controller.consumeRetry());
        handle.retry();
        controller.onLevelReady(false);
        assertFalse(controller.consumeRetry(), "the retry load already happened");
    }

    @Test
    void aRetryRequestedAfterTheLoadIsStillConsumedOnce() {
        List<String> calls = new ArrayList<>();
        RunHandle[] handle = new RunHandle[1];
        handle[0] = controller.begin(SPEC, new RunHost() {
            @Override public void onLevelReady(com.openggf.mods.run.RunLevelStart start) {
                calls.add("ready");
                if (calls.size() == 1) {
                    handle[0].retry(); // requested from inside the announcement, after the load
                }
            }
        });
        controller.onLevelReady(false);
        assertTrue(controller.consumeRetry());
        assertFalse(controller.consumeRetry());
        controller.onLevelReady(false);
        handle[0].retry();
        assertTrue(controller.consumeRetry());
        assertFalse(controller.consumeRetry());
        assertEquals(List.of("ready", "ready"), calls);
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

    private static com.openggf.mods.code.OwnedSceneFactory ownedFactory(
            java.util.concurrent.atomic.AtomicReference<java.util.Set<String>> disabled) {
        var boundary = new com.openggf.mods.code.ModFaultBoundary(java.util.Map.of(),
                new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), disabled::set);
        return com.openggf.mods.code.ModContextTestAccess.ownedScene("racer", () -> null, boundary);
    }

    @Test
    void ownerFaultRetiresRunAndRenderersBeforeReachingOuterEngineRecovery() {
        var disabled = new java.util.concurrent.atomic.AtomicReference<java.util.Set<String>>();
        List<RunEndReason> endings = new ArrayList<>();
        GhostRenderRegistry registry = new GhostRenderRegistry();
        RunHandle handle = controller.begin(SPEC, ownedFactory(disabled).guard(new RunHost() {
            @Override public void drawOverlay(com.openggf.mods.ui.LevelOverlayCanvas canvas) {
                throw new IllegalStateException("bad overlay");
            }
            @Override public void onRunEnded(RunEndReason reason) {
                endings.add(reason);
                throw new IllegalStateException("bad cleanup");
            }
        }));
        controller.attachRenderer(registry);
        var aborted = assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                () -> controller.drawOverlay(null));
        assertEquals("bad overlay", aborted.getCause().getMessage());
        assertEquals(0, aborted.getSuppressed().length, "successful engine cleanup must remain recoverable");
        assertEquals(java.util.Set.of("racer"), disabled.get());
        assertFalse(controller.isActive());
        assertFalse(handle.isActive());
        assertTrue(registry.isEmpty());
        assertEquals(List.of(RunEndReason.ABORTED), endings);
    }

    @Test
    void ownerFailureDuringRunEndStillReachesEngineRecoveryAfterCleanup() {
        GhostRenderRegistry registry = new GhostRenderRegistry();
        RunHandle handle = controller.begin(SPEC, ownedFactory(new java.util.concurrent.atomic.AtomicReference<>())
                .guard(new RunHost() {
                    @Override public void onRunEnded(RunEndReason reason) {
                        throw new IllegalStateException("creator end failed");
                    }
                }));
        controller.attachRenderer(registry);
        assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class,
                () -> controller.end(RunEndReason.ACT_COMPLETED));
        assertFalse(controller.isActive());
        assertFalse(handle.isActive());
        assertTrue(registry.isEmpty());
    }

    @Test
    void copyingCreatorGhostListsStaysInsideTheOwnerBoundary() {
        var disabled = new java.util.concurrent.atomic.AtomicReference<java.util.Set<String>>();
        RunHost host = ownedFactory(disabled).guard(new RunHost() {
            @Override public List<GhostPose> ghosts() {
                return java.util.Arrays.asList((GhostPose) null);
            }
        });
        assertThrows(com.openggf.mods.code.ModFaultBoundary.CallbackAborted.class, host::ghosts);
        assertEquals(java.util.Set.of("racer"), disabled.get());
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

    @Test
    void aSceneOverlayOwnsStartAndTicksBeforeTheHostDecides() {
        List<String> order = new ArrayList<>();
        controller.begin(SPEC, new RunHost() {
            @Override public boolean admitStep(com.openggf.mods.run.RunInput input) {
                order.add("host");
                return true;
            }
        });
        assertFalse(controller.ownsStart(com.openggf.game.GameMode.LEVEL), "a plain run keeps host pause");
        controller.setSceneOverlay(new HostedRunController.SceneOverlay() {
            @Override public void update(com.openggf.control.InputHandler input) { order.add("scene"); }
            @Override public void draw(int width, int height) { }
        });
        assertTrue(controller.ownsStart(com.openggf.game.GameMode.LEVEL));
        assertFalse(controller.ownsStart(com.openggf.game.GameMode.TITLE_CARD));
        assertTrue(controller.admitStep(new com.openggf.control.InputHandler()));
        assertEquals(List.of("scene", "host"), order);
        controller.end(RunEndReason.LEFT);
        assertFalse(controller.ownsStart(com.openggf.game.GameMode.LEVEL), "the overlay ends with its run");
    }

    @Test
    void aFailingSceneOverlayAbortsTheRunInsteadOfTheEngine() {
        controller.begin(SPEC, new RunHost() { });
        controller.setSceneOverlay(new HostedRunController.SceneOverlay() {
            @Override public void update(com.openggf.control.InputHandler input) {
                throw new IllegalStateException("scene bug");
            }
            @Override public void draw(int width, int height) { }
        });
        assertTrue(controller.admitStep(new com.openggf.control.InputHandler()));
        assertEquals(RunEndReason.ABORTED, controller.consumeLeave());
        assertThrows(IllegalStateException.class, () -> new HostedRunController(
                mock(SonicConfigurationService.class)).setSceneOverlay(new HostedRunController.SceneOverlay() {
                    @Override public void update(com.openggf.control.InputHandler input) { }
                    @Override public void draw(int width, int height) { }
                }), "an overlay needs a run");
    }

    @Test
    void runAreasValidateTheirFence() {
        assertThrows(IllegalArgumentException.class, () -> new com.openggf.mods.run.RunArea(0x50, 0x50, 0x100, 0x80, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new com.openggf.mods.run.RunArea(-1, 0, 0, 0, 0, 0));
        var area = new com.openggf.mods.run.RunArea(0x50, 0x3B0, 0, 0x280, 0x300, 0x300);
        assertEquals(area, new RunSpec("s1", 0, 0, "sonic", GameplayRunPolicy.isolatedAct(), area).area());
        assertNull(SPEC.area(), "the five-component spec plays the whole act");
    }

    @Test
    void aCoveringMenuIsHeldForTheRunAndClearedWithIt() {
        RunHandle handle = controller.begin(SPEC, new RunHost() { });
        assertFalse(controller.levelCovered());
        handle.setLevelCovered(true);
        assertTrue(controller.levelCovered());
        controller.end(RunEndReason.LEFT);
        assertFalse(controller.levelCovered(), "the cover ends with its run");
        handle.setLevelCovered(true);
        assertFalse(controller.levelCovered(), "an ended run's handle no longer covers the level");
    }
}
