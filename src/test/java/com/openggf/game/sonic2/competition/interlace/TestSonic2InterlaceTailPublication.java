package com.openggf.game.sonic2.competition.interlace;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.internal.PlayerDplcDmaSink;
import com.openggf.game.resources.DynamicArtDecisionOwner;
import com.openggf.game.resources.DynamicArtLifecycleService;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.timing.HardwareTimingService;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the real late fixed-slot pass, without manufacturing an ordinary DPLC for a native-only tail. */
@Isolated
class TestSonic2InterlaceTailPublication {
    @AfterEach void resetSession() {
        TestEnvironment.resetAll();
    }

    @Test void nativeOnlyS2TailPublishesStoredFrameOnAnimationAndCountdownPasses() throws Exception {
        RecordingSink sink = new RecordingSink();
        PlayerSpriteRenderer ownerRenderer = mock(PlayerSpriteRenderer.class);
        var lifecycle = activeLifecycle();
        var owner = new DynamicArtDecisionOwner(lifecycle, GameId.S2, "tails-tails", ownerRenderer);
        var harness = harness(sink, null, false, owner, true);

        harness.advance(3);
        assertEquals(List.of(0x81, 0x81, 0x81), sink.frames());
        assertEquals(0, harness.tail().captureRewindState().frameTick());
        harness.advance(1);

        assertEquals(List.of(0x81, 0x81, 0x81, 0x82), sink.frames());
        assertTrue(sink.calls.stream().allMatch(call -> call.dplc() == null),
                "the native owner receives no synthetic ordinary-cache DPLC");
        assertEquals(0x40, harness.parent().getMappingFrame(), "the body frame cannot replace Obj05's stored frame");
        assertEquals(0x82, harness.tail().captureRewindState().mappingFrame());
        verifyNoInteractions(ownerRenderer);
        assertTrue(lifecycle.publishRow(0, false).edges().isEmpty(),
                "the existing S2 DynamicArtDecisionOwner renderer guard stays in force");
    }

    @Test void rendererBackedS2TailReportsItsActualPlanAfterTheTailAnimationPass() throws Exception {
        RecordingSink sink = new RecordingSink();
        PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
        var plan = new SpriteDplcFrame(List.of(new TileLoadRequest(17, 3)));
        when(renderer.dplcFrame(0x81)).thenReturn(plan);
        var harness = harness(sink, renderer, false, null, false);

        assertTrue(sink.calls.isEmpty());
        harness.advance(1);

        assertEquals(List.of(new TailCall(0x81, plan)), sink.calls);
        assertEquals(0x81, harness.tail().captureRewindState().mappingFrame());
        assertEquals(1, harness.tail().captureRewindState().frameIndex());
        verify(renderer).dplcFrame(0x81);
    }

    @Test void ordinaryS2WithoutSinkKeepsItsDynamicArtAndDrawPath() throws Exception {
        PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
        var plan = new SpriteDplcFrame(List.of(new TileLoadRequest(2, 1)));
        when(renderer.dplcFrame(0x81)).thenReturn(plan);
        var lifecycle = activeLifecycle();
        var owner = new DynamicArtDecisionOwner(lifecycle, GameId.S2, "tails-tails", renderer);
        var harness = harness(RuntimeArtCoordinator.NONE, renderer, false, owner, true);

        harness.advance(1);
        var first = lifecycle.publishRow(0, false);
        harness.advance(1);
        harness.tail().draw();

        assertFalse(first.edges().isEmpty(), "ordinary tail art still submits its production DPLC");
        assertTrue(first.edges().stream().allMatch(edge -> edge.mappingFrame() == 0x81
                && edge.owner().equals("tails-tails")));
        verify(renderer, times(2)).dplcFrame(0x81);
        verify(renderer, times(2)).applyRuntimeArtUpdate(eq(0x81), any());
        verify(renderer).drawFrame(0x81, harness.parent().getRenderCentreX(),
                harness.parent().getRenderCentreY(), false, false);
    }

    @Test void s3kWithoutRendererRetainsItsEarlyReturn() throws Exception {
        RecordingSink sink = new RecordingSink();
        PlayerSpriteRenderer ownerRenderer = mock(PlayerSpriteRenderer.class);
        var lifecycle = activeLifecycle();
        var owner = new DynamicArtDecisionOwner(lifecycle, GameId.S2, "tails-tails", ownerRenderer);
        var harness = harness(sink, null, true, owner, true);

        harness.advance(4);

        assertEquals(2, harness.tail().captureRewindState().mappingFrame(), "S3K animation still advances");
        assertTrue(sink.calls.isEmpty(), "S3K must not acquire S2's renderer-free callback");
        verifyNoInteractions(ownerRenderer);
    }

    @Test void s3kWithRendererRetainsItsDplcCallbacksAndSkipsS2DecisionOwner() throws Exception {
        RecordingSink sink = new RecordingSink();
        PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
        PlayerSpriteRenderer ownerRenderer = mock(PlayerSpriteRenderer.class);
        var plan = new SpriteDplcFrame(List.of(new TileLoadRequest(5, 4)));
        when(renderer.dplcFrame(1)).thenReturn(plan);
        var owner = new DynamicArtDecisionOwner(activeLifecycle(), GameId.S2, "tails-tails", ownerRenderer);
        var harness = harness(sink, renderer, true, owner, true);

        harness.advance(2);

        assertEquals(List.of(new TailCall(1, plan), new TailCall(1, plan)), sink.calls);
        verify(renderer, times(2)).dplcFrame(1);
        verifyNoInteractions(ownerRenderer);
    }

    @Test void destructiveTailRestoreReplaysTheSameLatePublicationSequence() throws Exception {
        RecordingSink sink = new RecordingSink();
        var harness = harness(sink, null, false, null, true);
        harness.advance(2);
        var saved = harness.tail().captureRewindState();
        sink.calls.clear();
        harness.advance(11);
        var expectedCalls = List.copyOf(sink.calls);
        var expectedState = harness.tail().captureRewindState();
        assertTrue(expectedCalls.stream().map(TailCall::mappingFrame).distinct().count() >= 3,
                "the replay must cross actual script reads as well as countdowns");

        harness.parent().setAnimationId(5);
        harness.advance(20);
        assertNotEquals(expectedState, harness.tail().captureRewindState());
        harness.parent().setAnimationId(9);
        harness.tail().restoreRewindState(saved);
        sink.calls.clear();
        harness.advance(11);

        assertEquals(expectedCalls, sink.calls);
        assertEquals(expectedState, harness.tail().captureRewindState());
    }

    private static DynamicArtLifecycleService activeLifecycle() {
        var lifecycle = new DynamicArtLifecycleService();
        lifecycle.beginRun();
        lifecycle.openComparisonSegment();
        return lifecycle;
    }

    private static Harness harness(RuntimeArtCoordinator coordinator, PlayerSpriteRenderer renderer,
            boolean isS3k, DynamicArtDecisionOwner owner, boolean sidekick) throws Exception {
        GameModule module = isS3k ? new Sonic3kGameModule() {
            @Override public RuntimeArtCoordinator createRuntimeArtCoordinator(HardwareTimingService timing) {
                return coordinator;
            }
        } : new Sonic2GameModule() {
            @Override public RuntimeArtCoordinator createRuntimeArtCoordinator(HardwareTimingService timing) {
                return coordinator;
            }
        };
        TestEnvironment.configureGameModuleFixture(module);
        var gameplay = TestEnvironment.activeGameplayMode();
        var objects = new ObjectManager(List.of(), null, 0, null, null, null, null,
                TestEnvironment.objectServices());
        var objectManagerField = LevelManager.class.getDeclaredField("objectManager");
        objectManagerField.setAccessible(true);
        objectManagerField.set(gameplay.getLevelManager(), objects);
        assertSame(coordinator, objects.getObjectServices().runtimeArtCoordinator());

        SpriteManager sprites = gameplay.getSpriteManager();
        var parent = new TestablePlayableSprite("tails", (short) 100, (short) 80);
        parent.setAnimationId(9); // TailsAni_Spindash -> Obj05 animation 7.
        parent.setMappingFrame(0x40);
        var tail = new TailsTailsController(parent, renderer, isS3k, owner);
        parent.setTailsTailsController(tail);
        if (sidekick) {
            sprites.addSprite(new TestablePlayableSprite("sonic", (short) 100, (short) 80));
            parent.setCpuControlled(true);
        }
        sprites.addSprite(parent, "tails");
        return new Harness(sprites, parent, tail);
    }

    private record Harness(SpriteManager sprites, TestablePlayableSprite parent, TailsTailsController tail) {
        void advance(int count) {
            for (int i = 0; i < count; i++) sprites.advancePlayableFixedSlotsAfterObjectExecution();
        }
    }

    private record TailCall(int mappingFrame, SpriteDplcFrame dplc) { }

    private static final class RecordingSink implements RuntimeArtCoordinator, PlayerDplcDmaSink {
        final List<TailCall> calls = new ArrayList<>();
        List<Integer> frames() { return calls.stream().map(TailCall::mappingFrame).toList(); }
        @Override public void tailsTailLoadPlc(int mappingFrame, SpriteDplcFrame dplc) {
            calls.add(new TailCall(mappingFrame, dplc));
        }
        @Override public void playerLoadPlc(AbstractPlayableSprite player, int mappingFrame, SpriteDplcFrame dplc) {
            fail("the late tail slot must not call the body DPLC routine");
        }
        @Override public void playerLoadPlcOwnedByObject(AbstractPlayableSprite player) {
            fail("the late tail slot must not change body DPLC ownership");
        }
    }
}
