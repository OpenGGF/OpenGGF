package com.openggf.level.objects;

import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestObjectAnimationState {
    @Test
    void nativeSpriteFirstReadIsImmediateAndLaterReadsIncludeZeroTimerPass() {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        animations.addScript(0, new SpriteAnimationScript(
                0x0F, List.of(3, 4), SpriteAnimationEndAction.LOOP, 0));
        ObjectAnimationState state = new ObjectAnimationState(animations, 0, 0);
        int[] reads = {0};
        state.updateNativeSprite(() -> reads[0]++);
        assertEquals(1, reads[0]);
        assertEquals(3, state.getMappingFrame());
        ObjectAnimationState copy = state.copyForRewind();
        for (int i = 0; i < 15; i++) {
            state.updateNativeSprite(() -> reads[0]++);
            copy.updateNativeSprite(() -> {});
            assertEquals(3, state.getMappingFrame());
            assertEquals(state, copy);
        }
        assertEquals(1, reads[0]);
        state.updateNativeSprite(() -> reads[0]++);
        assertEquals(2, reads[0]);
        assertEquals(4, state.getMappingFrame());
    }

    @Test
    void nativeSpriteSwitchMarkerPreservesMappingUntilNextScriptDispatch() {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        animations.addScript(0, new SpriteAnimationScript(
                0x0F, List.of(3), SpriteAnimationEndAction.LOOP, 0));
        animations.addScript(1, new SpriteAnimationScript(
                0, List.of(7), SpriteAnimationEndAction.SWITCH, 0));
        ObjectAnimationState state = new ObjectAnimationState(animations, 1, 0);
        state.updateNativeSprite(() -> {});
        assertEquals(7, state.getMappingFrame());
        state.updateNativeSprite(() -> {});
        assertEquals(0, state.getAnimId());
        assertEquals(7, state.getMappingFrame());
        state.updateNativeSprite(() -> {});
        assertEquals(3, state.getMappingFrame());
    }

    @Test
    void switchEndActionKeepsPreviousMappingForMarkerUpdate() {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        animations.addScript(0, new SpriteAnimationScript(
                0, List.of(3), SpriteAnimationEndAction.LOOP, 0));
        animations.addScript(1, new SpriteAnimationScript(
                0, List.of(7), SpriteAnimationEndAction.SWITCH, 0));

        ObjectAnimationState state = new ObjectAnimationState(animations, 1, 0);

        state.update();
        assertEquals(7, state.getMappingFrame());
        assertEquals(1, state.getAnimId());

        state.update();
        assertEquals(7, state.getMappingFrame());
        assertEquals(0, state.getAnimId());

        state.update();
        assertEquals(3, state.getMappingFrame());
        assertEquals(0, state.getAnimId());
    }

    @Test
    void rewindCopyPreservesPendingSwitchMarkerState() {
        SpriteAnimationSet animations = new SpriteAnimationSet();
        animations.addScript(0, new SpriteAnimationScript(
                0, List.of(3), SpriteAnimationEndAction.LOOP, 0));
        animations.addScript(1, new SpriteAnimationScript(
                0, List.of(7), SpriteAnimationEndAction.SWITCH, 0));

        ObjectAnimationState state = new ObjectAnimationState(animations, 1, 0);
        state.update();

        ObjectAnimationState copy = state.copyForRewind();
        state.update();
        copy.update();

        assertEquals(state, copy);
        assertEquals(7, copy.getMappingFrame());
        assertEquals(0, copy.getAnimId());
    }
}
