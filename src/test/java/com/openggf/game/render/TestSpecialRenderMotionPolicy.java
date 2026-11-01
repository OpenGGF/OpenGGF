package com.openggf.game.render;

import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TestSpecialRenderMotionPolicy {
    private static SpecialRenderEffectContext context() {
        return new SpecialRenderEffectContext(mock(Camera.class), 7,
                mock(LevelManager.class), mock(GraphicsManager.class));
    }
    @Test void undeclaredEffectsKeepTheSceneExactAtEveryStage() {
        var context = context();
        assertTrue(SpecialRenderMotionPolicy.permitsScene(null, context));
        for (var stage : SpecialRenderEffectStage.values()) {
            var registry = new SpecialRenderEffectRegistry();
            assertTrue(SpecialRenderMotionPolicy.permitsScene(registry, context));
            registry.register(new SpecialRenderEffect() {
                public SpecialRenderEffectStage stage() { return stage; }
                public void render(SpecialRenderEffectContext ignored) { fail("Policy query must not draw"); }
            });
            assertFalse(SpecialRenderMotionPolicy.permitsScene(registry, context), stage.toString());
            registry.clear();
            assertTrue(SpecialRenderMotionPolicy.permitsScene(registry, context));
        }
    }
    @Test void everyRegisteredEffectMustPermitTheCurrentPictureWithoutDrawing() {
        var registry = new SpecialRenderEffectRegistry(); var context = context();
        var first = new Conditional(); var second = new Conditional();
        registry.register(first); registry.register(second);
        assertTrue(SpecialRenderMotionPolicy.permitsScene(registry, context));
        second.active = true;
        assertFalse(SpecialRenderMotionPolicy.permitsScene(registry, context));
        second.active = false; first.active = true;
        assertFalse(SpecialRenderMotionPolicy.permitsScene(registry, context));
        first.active = false;
        assertTrue(SpecialRenderMotionPolicy.permitsScene(registry, context));
    }
    private static final class Conditional implements SpecialRenderEffect, SpecialRenderMotionPolicy {
        boolean active;
        public SpecialRenderEffectStage stage() { return SpecialRenderEffectStage.AFTER_SPRITES; }
        public boolean permitsMotion(SpecialRenderEffectContext context) { return !active; }
        public void render(SpecialRenderEffectContext context) { fail("Policy query must not draw"); }
    }
}
