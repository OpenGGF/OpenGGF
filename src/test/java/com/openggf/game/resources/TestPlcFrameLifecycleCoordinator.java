package com.openggf.game.resources;

import com.openggf.game.GameModule;
import org.junit.jupiter.api.Test;
import com.openggf.game.rules.DynamicArtDmaServiceModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestPlcFrameLifecycleCoordinator {

    @Test
    void moduleWithoutTypedRulesUsesNeutralDynamicArtServicePolicy() {
        GameModule module = mock(GameModule.class);
        when(module.getGameService(PlcLifecycleService.class))
                .thenReturn(recording(new ArrayList<>()));
        DynamicArtLifecycleService dynamicArt =
                new DynamicArtLifecycleService();
        dynamicArt.beginRun();
        dynamicArt.openComparisonSegment();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(module, dynamicArt);

        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.TITLE_SCREEN);
            return null;
        });

        assertEquals(0, dynamicArt.latestSnapshot().frame());
    }

    @Test
    void sonic2CrossArmFifoWaitsForSpecialStageServiceClaim() {
        DynamicArtLifecycleService dynamicArt =
                new DynamicArtLifecycleService();
        dynamicArt.beginRun();
        dynamicArt.observePlayerDplc(
                com.openggf.game.GameId.S2, "tails-tails", 13,
                new com.openggf.level.render.SpriteDplcFrame(List.of(
                        new com.openggf.level.render.TileLoadRequest(110, 12))));
        dynamicArt.openComparisonSegment();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(
                        recording(new ArrayList<>()), dynamicArt,
                        DynamicArtDmaServiceModel.SONIC_2_PROCESS_DMA_QUEUE);

        for (int row = 0; row < 126; row++) {
            coordinator.runLogicalIteration(() -> { }, frame -> {
                frame.claim(PlcLifecyclePhase.PALETTE_FADE);
                frame.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
                return null;
            });
            assertEquals(List.of(0L),
                    dynamicArt.latestSnapshot().outstandingTransferIds());
        }
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            return null;
        });

        assertTrue(dynamicArt.latestSnapshot().outstandingTransferIds().isEmpty());
        assertEquals("completed",
                dynamicArt.latestSnapshot().edges().getFirst().phase());
    }

    @Test
    void claimRetiresPreviousS2FifoAndFinishPublishesProductionRow() {
        DynamicArtLifecycleService dynamicArt =
                new DynamicArtLifecycleService();
        dynamicArt.beginRun();
        dynamicArt.openComparisonSegment();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(
                        recording(new ArrayList<>()), dynamicArt);

        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ENDING);
            dynamicArt.observePlayerDplc(
                    com.openggf.game.GameId.S2, "sonic", 1,
                    new com.openggf.level.render.SpriteDplcFrame(List.of(
                            new com.openggf.level.render.TileLoadRequest(0, 1))));
            return null;
        });
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.ENDING);
            return null;
        });

        DynamicArtDiagnosticsSnapshot snapshot =
                dynamicArt.latestSnapshot();
        assertEquals(1, snapshot.frame());
        assertEquals("completed", snapshot.edges().getFirst().phase());
        assertTrue(snapshot.outstandingTransferIds().isEmpty());
    }

    @Test
    void immutableSnapshotCanOnlyBePulledAfterFinishPublishesIt() {
        DynamicArtLifecycleService dynamicArt =
                new DynamicArtLifecycleService();
        dynamicArt.beginRun();
        dynamicArt.openComparisonSegment();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(
                        recording(new ArrayList<>()), dynamicArt);
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            dynamicArt.observeRamDplc(
                    "ss-sonic", 2,
                    List.of(new com.openggf.level.render.TileLoadRequest(0, 1)),
                    0xFF0000, 0x5CA0);
            assertEquals(-1, dynamicArt.latestSnapshot().frame());
            return null;
        });

        DynamicArtDiagnosticsSnapshot first = dynamicArt.latestSnapshot();
        assertEquals(0, first.frame());
        assertEquals("submitted",
                first.edges().getFirst().phase());
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            return null;
        });

        assertEquals(1, dynamicArt.latestSnapshot().frame());
        dynamicArt.finishRun();
        dynamicArt.beginRun();
    }

    @Test
    void rewindRestoreDoesNotPublishSnapshot() {
        DynamicArtLifecycleService dynamicArt =
                new DynamicArtLifecycleService();
        dynamicArt.beginRun();
        dynamicArt.openComparisonSegment();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(
                        recording(new ArrayList<>()), dynamicArt);
        DynamicArtLifecycleService.RewindState saved = dynamicArt.capture();

        dynamicArt.restore(saved);
        assertEquals(-1, dynamicArt.latestSnapshot().frame());
        coordinator.runLogicalIteration(() -> { }, frame -> {
            frame.claim(PlcLifecyclePhase.SPECIAL_STAGE);
            return null;
        });

        assertEquals(0, dynamicArt.latestSnapshot().frame());
    }

    @Test
    void nativeFadeKeepsOutgoingTokenAcrossCompletionCallback() {
        List<String> events = new ArrayList<>();
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(events));
        NativeFadeLifecycle.NativeBlockingFade fade = coordinator.beginNativeBlockingFade();
        var frame = coordinator.latchBeforeFadeUpdate();

        fade.wrapCompletion(() -> events.add("callback")).run();
        assertFalse(frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        frame.prepareAfterLoop(PlcLifecyclePhase.PALETTE_FADE);
        frame.finish();

        var next = coordinator.latchBeforeFadeUpdate();
        assertTrue(next.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        next.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        next.finish();
        org.junit.jupiter.api.Assertions.assertEquals(List.of(
                "service:PALETTE_FADE", "callback", "prepare:PALETTE_FADE",
                "service:ORDINARY_LEVEL", "prepare:ORDINARY_LEVEL"), events);
    }

    @Test
    void tokenRejectsReuseDuplicatePreparationAndMissingPreparation() {
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(new ArrayList<>()));
        var missing = coordinator.latchBeforeFadeUpdate();
        missing.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class, missing::finish);
        missing.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        missing.finish();
        assertThrows(IllegalStateException.class,
                () -> missing.claim(PlcLifecyclePhase.LAG));

        var duplicate = coordinator.latchBeforeFadeUpdate();
        duplicate.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
        duplicate.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL);
        assertThrows(IllegalStateException.class,
                () -> duplicate.prepareAfterLoop(PlcLifecyclePhase.ORDINARY_LEVEL));
        duplicate.finish();
    }

    @Test
    void preparationValidationDoesNotMaskThePrimaryIterationFailure() {
        PlcFrameLifecycleCoordinator coordinator =
                new PlcFrameLifecycleCoordinator(recording(new ArrayList<>()));
        IllegalArgumentException primary = new IllegalArgumentException("primary");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> coordinator.runLogicalIteration(() -> { }, frame -> {
                    frame.claim(PlcLifecyclePhase.ORDINARY_LEVEL);
                    throw primary;
                }));

        assertSame(primary, thrown);
        org.junit.jupiter.api.Assertions.assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0].getMessage()
                .contains("missing PLC preparation for ORDINARY_LEVEL"));
    }

    private static PlcLifecycleService recording(List<String> events) {
        return new PlcLifecycleService() {
            @Override
            public void serviceVBlank(PlcLifecyclePhase phase) {
                events.add("service:" + phase);
            }

            @Override
            public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
                return phase == PlcLifecyclePhase.PALETTE_FADE
                        || phase == PlcLifecyclePhase.ORDINARY_LEVEL;
            }

            @Override
            public void prepareAfterLoop(PlcLifecyclePhase phase) {
                events.add("prepare:" + phase);
            }
        };
    }
}
