package com.openggf.game.sonic3k.objects;

import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestShieldAnimationArtLifecycle {

    @Test
    void publishesCurrentCursorAndDefersFdTargetUntilNextDispatch() {
        ShieldAnimationArtLifecycle lifecycle = new ShieldAnimationArtLifecycle(0);
        lifecycle.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
        assertEquals(0, lifecycle.mappingFrame(), "binding art does not execute Animate_Sprite");
        lifecycle.stepAnimation();
        assertEquals(10, lifecycle.mappingFrame());
        assertEquals(1, lifecycle.frameIndex(), "native cursor points to the next script byte");
        lifecycle.stepAnimation();
        assertEquals(11, lifecycle.mappingFrame());
        lifecycle.stepAnimation();
        assertEquals(10, lifecycle.mappingFrame(), "FF publishes frame zero on the command dispatch");

        lifecycle.setAnimation(1);
        assertEquals(10, lifecycle.mappingFrame(), "writing anim does not execute it");
        lifecycle.stepAnimation();
        assertEquals(20, lifecycle.mappingFrame());
        lifecycle.stepAnimation();
        assertEquals(21, lifecycle.mappingFrame());
        lifecycle.stepAnimation();
        assertEquals(0, lifecycle.animationId(), "FD only writes the target anim");
        assertEquals(21, lifecycle.mappingFrame(), "FD retains the last source mapping");
        lifecycle.stepAnimation();
        assertEquals(10, lifecycle.mappingFrame());

        lifecycle.setAnimation(2);
        for (int i = 0; i < 4; i++) lifecycle.stepAnimation();
        assertEquals(31, lifecycle.mappingFrame(), "FE publishes the backtracked cursor");
    }

    @Test
    void delayByteHoldsEachPublishedFrameForDelayPlusOneDispatches() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        set.addScript(0, new SpriteAnimationScript(2, List.of(10, 11), SpriteAnimationEndAction.LOOP, 0));
        ShieldAnimationArtLifecycle lifecycle = new ShieldAnimationArtLifecycle(0);
        lifecycle.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, set));
        int[] expected = {10, 10, 10, 11, 11, 11, 10};
        for (int frame : expected) {
            lifecycle.stepAnimation();
            assertEquals(frame, lifecycle.mappingFrame());
        }
    }

    @Test
    void rewindRestorePreservesCursorAndInvalidatesBoundDplcRenderer() {
        PlayerSpriteRenderer renderer = mock(PlayerSpriteRenderer.class);
        ShieldAnimationArtLifecycle lifecycle = new ShieldAnimationArtLifecycle(0);
        lifecycle.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(renderer, animations()));
        lifecycle.setAnimation(1);
        lifecycle.stepAnimation();
        ShieldAnimationArtLifecycle.RewindState saved = lifecycle.captureRewindStateValue();

        lifecycle.stepAnimation();
        lifecycle.restoreRewindStateValue(saved);
        assertEquals(saved, lifecycle.captureRewindStateValue(), "rewind restores the playback cursor");

        clearInvocations(renderer);
        lifecycle.refreshArtAfterRewindRestore();
        lifecycle.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(renderer, animations()));
        verify(renderer, times(2)).invalidateDplcCache();
    }

    @Test
    void restoreBeforeArtBindingKeepsCursorUntilNewAnimationIntentSupersedesIt() {
        ShieldAnimationArtLifecycle captured = new ShieldAnimationArtLifecycle(0);
        captured.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
        captured.setAnimation(1);
        captured.stepAnimation();
        ShieldAnimationArtLifecycle.RewindState saved = captured.captureRewindStateValue();

        ShieldAnimationArtLifecycle restored = new ShieldAnimationArtLifecycle(0);
        restored.restoreRewindStateValue(saved);
        restored.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
        assertEquals(saved, restored.captureRewindStateValue(),
                "late art binding must not reset a restored cursor");

        ShieldAnimationArtLifecycle superseded = new ShieldAnimationArtLifecycle(0);
        superseded.restoreRewindStateValue(saved);
        superseded.setAnimation(0);
        superseded.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
        assertEquals(0, superseded.animationId());
        assertEquals(saved.frameIndex(), superseded.frameIndex(), "writing anim retains the cursor until dispatch");
        assertEquals(saved.mappingFrame(), superseded.mappingFrame(),
                "new animation intent before art binding must win over restored playback");
    }

    @Test
    void recreationAtFdBoundaryPreservesPendingAnimationChangeAndForwardPlayback() {
        ShieldAnimationArtLifecycle original = new ShieldAnimationArtLifecycle(1);
        original.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
        for (int i = 0; i < 3; i++) original.stepAnimation();
        assertEquals(0, original.animationId());
        assertEquals(21, original.mappingFrame());
        ShieldAnimationArtLifecycle recreated = new ShieldAnimationArtLifecycle(0);
        recreated.restoreRewindStateValue(original.captureRewindStateValue());
        recreated.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
        for (int i = 0; i < 12; i++) {
            original.stepAnimation();
            recreated.stepAnimation();
            assertEquals(original.captureRewindStateValue(), recreated.captureRewindStateValue());
        }
    }

    @Test
    void elementalOwnersFreezeForInvincibilityAndBlankButResumeSameCursor() throws Exception {
        var player = new com.openggf.tests.TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var services = new com.openggf.level.objects.StubObjectServices();
        for (var shield : List.of(new FireShieldObjectInstance(player),
                new BubbleShieldObjectInstance(player), new LightningShieldObjectInstance(player))) {
            shield.setServices(services);
            var field = shield.getClass().getDeclaredField("animationLifecycle");
            field.setAccessible(true);
            var lifecycle = (ShieldAnimationArtLifecycle) field.get(shield);
            lifecycle.ensureArtLoaded(() -> new ShieldAnimationArtLifecycle.Art(null, animations()));
            shield.update(0, player);
            assertEquals(10, lifecycle.mappingFrame());
            var before = lifecycle.captureRewindStateValue();
            player.setInvincibleFrames(2);
            shield.update(1, player);
            assertEquals(before, lifecycle.captureRewindStateValue());
            player.setInvincibleFrames(0);
            player.setAnimationId(0x1C);
            shield.update(2, player);
            assertEquals(before, lifecycle.captureRewindStateValue());
            player.setAnimationId(0);
            shield.update(3, player);
            assertEquals(11, lifecycle.mappingFrame());
        }
    }

    private static SpriteAnimationSet animations() {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        animations.addScript(0, new SpriteAnimationScript(
                0, List.of(10, 11), SpriteAnimationEndAction.LOOP, 0));
        animations.addScript(1, new SpriteAnimationScript(
                0, List.of(20, 21), SpriteAnimationEndAction.SWITCH, 0));
        animations.addScript(2, new SpriteAnimationScript(
                0, List.of(30, 31, 32), SpriteAnimationEndAction.LOOP_BACK, 2));
        animations.addScript(3, new SpriteAnimationScript(
                0, List.of(40, 41), SpriteAnimationEndAction.HOLD, 0));
        return animations;
    }
}
