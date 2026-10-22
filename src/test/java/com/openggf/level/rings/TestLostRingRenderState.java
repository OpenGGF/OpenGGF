package com.openggf.level.rings;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.graphics.*;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestLostRingRenderState {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @Test void injectedNativeArtProfileAndGiveRingBucketAreIndependentOfTheMotionRules() {
        for (SonicGame game : List.of(SonicGame.SONIC_1, SonicGame.SONIC_2, SonicGame.SONIC_3K)) {
            TestEnvironment.configureGameModuleFixture(game);
            GraphicsManager graphics = GraphicsManager.getInstance();
            graphics.initHeadless();
            RingManager rings = new RingManager(List.of(), TestRingNativeSlotDispatch.sheet(), null, null, null);
            rings.ensurePatternsCached(graphics, 300);
            GameModule module = GameServices.currentOrBootstrapGameModule();
            var services = new StubObjectServices() {
                @Override public GraphicsManager graphicsManager() { return graphics; }
                @Override public GameModule gameModule() { return module; }
                @Override public RingManager ringManager() { return rings; }
            };
            LostRingObjectInstance ring = LostRingObjectInstance.forTest(128, 64, 0, 0, 0, 255);
            ring.setServices(services);
            assertFalse(ring.nativeSpriteSubmissionsAuthoritative(), "ordinary factories retain legacy drawing");
            assertEquals(game == SonicGame.SONIC_3K, ring.isHighPriority());
            assertEquals(3, ring.getPriorityBucket());
            var frame = SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
                SpriteSatInternalAccess.beginCollection(graphics, SpriteSatMaskPostProcessor.Raster.h40(0, 0, 224));
                graphics.setCurrentSpriteHighPriority(ring.isHighPriority());
                ring.appendRenderCommands(new ArrayList<>());
                graphics.endSpriteSatCollectionAndReplay();
            });
            assertEquals(1, frame.tiles().size());
            assertEquals(SpritePresentation.Layer.RINGS, frame.tiles().getFirst().layer());
            assertEquals(game == SonicGame.SONIC_3K, frame.tiles().getFirst().priority());
            assertEquals(game == SonicGame.SONIC_3K ? 0 : 0xF, frame.tiles().getFirst().occlusionMask());
            ring.markCollected(0);
            assertEquals(3, ring.getPriorityBucket(), "touch writes the routine; it does not execute GiveRing");
            ring.update(1, null);
            assertEquals(1, ring.getPriorityBucket(), "the actual GiveRing dispatch writes priority$80");
        }
    }

    @Test void convertedRenderWordsSurviveDrawingPausesAndADeferredOwnerDispatch() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        GraphicsManager graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
        RingManager rings = new RingManager(List.of(), TestRingNativeSlotDispatch.sheet(), null, null, null);
        rings.ensurePatternsCached(graphics, 300);
        SpillAnimationState animation = rings.getSpillAnimationState();
        animation.restore(new int[]{255, 0, 2});
        var services = new StubObjectServices() {
            @Override public GraphicsManager graphicsManager() { return graphics; }
            @Override public RingManager ringManager() { return rings; }
        };
        LostRingObjectInstance ring = LostRingObjectInstance.fromAttractedRing(128, 64, 0, 0,
                0, 0, 1, animation, new LostRingRenderState(2, 1, true));
        ring.setServices(services);
        assertTrue(ring.nativeSpriteSubmissionsAuthoritative());
        ring.deferFirstUpdateUntilOwnerPass();
        ring.update(1, null); // this native slot has not reached the actual owner routine yet
        for (int draw = 0; draw < 3; draw++) assertEquals(301, picture(graphics, ring));
        assertEquals(2, ring.getPriorityBucket());
        ring.update(2, null);
        assertEquals(302, picture(graphics, ring), "only an actual routine2 invocation replaces retained mapping1");
        assertEquals(2, ring.getPriorityBucket(), "skipping Init permanently retains priority$100");
    }

    private static int picture(GraphicsManager graphics, LostRingObjectInstance ring) {
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            graphics.setCurrentSpriteHighPriority(ring.isHighPriority());
            ring.appendRenderCommands(new ArrayList<>());
        }).tiles().getFirst().patternId();
    }
}
