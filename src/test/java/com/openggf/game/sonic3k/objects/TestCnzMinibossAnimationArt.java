package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CNZ miniboss sprite-frame coverage against the S&K-side raw animation
 * labels at sonic3k.asm:145705-145711.
 */
class TestCnzMinibossAnimationArt {

    @Test
    void bossOpeningRawAnimationSelectsIntermediateMapFramesBeforeOpenFrame() {
        RenderHarness harness = new RenderHarness();
        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(harness.services());

        boss.forceRoutineForTest(8);
        boss.update(0, null);
        boss.appendRenderCommands(new ArrayList<>());

        verify(harness.renderer()).drawFrameIndex(1, 0x3240, 0x02B8, false, false);

        for (int i = 1; i <= 4; i++) {
            clearInvocations(harness.renderer(), harness.renderManager());
            boss.update(i, null);
            boss.appendRenderCommands(new ArrayList<>());
        }

        verify(harness.renderer()).drawFrameIndex(2, 0x3240, 0x02B8, false, false);
    }

    @Test
    void bossClosingRawAnimationWalksBackFromOpenFrame() {
        RenderHarness harness = new RenderHarness();
        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(harness.services());

        boss.forceOpenForTest();
        boss.simulateHitForTest();
        boss.update(0, null);
        boss.update(1, null);
        boss.appendRenderCommands(new ArrayList<>());

        verify(harness.renderer()).drawFrameIndex(5, 0x3240, 0x02B8, false, false);
    }

    @Test
    void topWait2RawGetFasterSelectsFrame8BeforeLaunchingToMain() {
        RenderHarness harness = new RenderHarness();
        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(harness.services());

        top.update(0, null);
        top.update(1, null);
        top.update(2, null);
        top.appendRenderCommands(new ArrayList<>());

        verify(harness.renderer()).drawFrameIndex(8, 0x3240, 0x0300, false, false);
    }

    private static final class RenderHarness {
        private final LevelManager levelManager = mock(LevelManager.class);
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        private final PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        private RenderHarness() {
            when(levelManager.getObjectRenderManager()).thenReturn(renderManager);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.CNZ_MINIBOSS)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
        }

        private TestObjectServices services() {
            return new TestObjectServices().withLevelManager(levelManager);
        }

        private ObjectRenderManager renderManager() {
            return renderManager;
        }

        private PatternSpriteRenderer renderer() {
            return renderer;
        }
    }
}
