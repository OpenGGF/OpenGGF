package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindStateful;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared S3K shield playback and DPLC renderer lifecycle.
 *
 * <p>The playback order intentionally differs from {@code ObjectAnimationState}:
 * each update advances the frame before publishing it, and a {@code SWITCH} end
 * action immediately initializes and publishes its target animation. This is the
 * Obj_FireShield / Obj_BubbleShield / Obj_LightningShield / Obj_InstaShield
 * convention, not the generic AnimateSprite convention.
 */
final class ShieldAnimationArtLifecycle
        implements RewindStateful<ShieldAnimationArtLifecycle.RewindState> {

    record Art(PlayerSpriteRenderer renderer, SpriteAnimationSet animationSet) {
    }

    record RewindState(int animationId, int frameIndex, int delayCounter, int mappingFrame) {
    }

    private PlayerSpriteRenderer dplcRenderer;
    private SpriteAnimationSet animationSet;
    private PlayerSpriteRenderer boundRenderer;
    private boolean artRefreshPending;
    /** Rewind can restore the cursor before this reconstructed shield has bound ROM art. */
    private boolean restoredPlaybackAwaitingArt;
    private int animationId;
    private int frameIndex;
    private int delayCounter;
    private int mappingFrame;

    ShieldAnimationArtLifecycle(int initialAnimationId) {
        animationId = initialAnimationId;
    }

    /**
     * The Y flip every shield's draw uses, under {@code Reverse_gravity_flag}.
     *
     * <p>{@code Obj_InstaShield_Main} (sonic3k.asm:34590-34597),
     * {@code Obj_FireShield_Main} (:34662-34669), {@code Obj_LightningShield_Main}
     * (:34743-34750) and {@code Obj_BubbleShield_Main} (:34907-34914) each inherit the
     * player's {@code status}, mask it down to the orientation bit with
     * {@code andi.b #1,status(a0)}, and then {@code ori.b #2,status(a0)} while the flag is
     * set. Bit 1 has already been cleared by the mask, so — despite the ROM's own comment
     * — this is a <em>set</em>: the shield's Y-flip equals the flag every frame. Porting it
     * as the XOR the comment describes would alternate the sprite, the same mistake the
     * player's own render mirror had to avoid.
     */
    static boolean reverseGravityMirror(com.openggf.level.objects.ObjectServices services) {
        return services != null && services.gameState() != null
                && services.gameState().isReverseGravityActive();
    }

    void ensureArtLoaded(Supplier<Art> artSupplier) {
        Objects.requireNonNull(artSupplier, "artSupplier");
        if (artRefreshPending) {
            boundRenderer = null;
            invalidateDplcCache();
            artRefreshPending = false;
        }
        Art art = artSupplier.get();
        if (art == null) {
            return;
        }
        if (art.renderer() != null && dplcRenderer != art.renderer()) {
            dplcRenderer = art.renderer();
            if (dplcRenderer != boundRenderer) {
                dplcRenderer.invalidateDplcCache();
                boundRenderer = dplcRenderer;
            }
        }
        if (animationSet == null && art.animationSet() != null) {
            animationSet = art.animationSet();
            if (!restoredPlaybackAwaitingArt) {
                initializeAnimation(animationId);
            }
            restoredPlaybackAwaitingArt = false;
        }
    }

    void refreshArtAfterRewindRestore() {
        artRefreshPending = true;
        boundRenderer = null;
        invalidateDplcCache();
    }

    void invalidateDplcCache() {
        if (dplcRenderer != null) {
            dplcRenderer.invalidateDplcCache();
        }
    }

    void setAnimation(int nextAnimationId) {
        if (nextAnimationId != animationId) {
            restoredPlaybackAwaitingArt = false;
            initializeAnimation(nextAnimationId);
        }
    }

    void restartAnimation(int nextAnimationId) {
        restoredPlaybackAwaitingArt = false;
        initializeAnimation(nextAnimationId);
    }

    void stepAnimation() {
        if (animationSet == null) {
            return;
        }
        SpriteAnimationScript script = animationSet.getScript(animationId);
        if (script == null || script.frames().isEmpty()) {
            return;
        }
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }
        delayCounter = script.delay();

        frameIndex++;
        if (frameIndex >= script.frames().size()) {
            switch (script.endAction()) {
                case LOOP -> frameIndex = 0;
                case LOOP_BACK -> frameIndex = Math.max(0, script.frames().size() - script.endParam());
                case SWITCH -> {
                    initializeAnimation(script.endParam());
                    return;
                }
                case HOLD -> frameIndex = script.frames().size() - 1;
            }
        }
        mappingFrame = script.frames().get(frameIndex);
    }

    PlayerSpriteRenderer renderer() {
        return dplcRenderer;
    }

    int animationId() {
        return animationId;
    }

    int frameIndex() {
        return frameIndex;
    }

    int delayCounter() {
        return delayCounter;
    }

    int mappingFrame() {
        return mappingFrame;
    }

    @Override
    public RewindState captureRewindStateValue() {
        return new RewindState(animationId, frameIndex, delayCounter, mappingFrame);
    }

    @Override
    public void restoreRewindStateValue(RewindState state) {
        animationId = state.animationId();
        frameIndex = state.frameIndex();
        delayCounter = state.delayCounter();
        mappingFrame = state.mappingFrame();
        restoredPlaybackAwaitingArt = true;
    }

    private void initializeAnimation(int nextAnimationId) {
        animationId = nextAnimationId;
        frameIndex = 0;
        if (animationSet == null) {
            return;
        }
        SpriteAnimationScript script = animationSet.getScript(nextAnimationId);
        if (script == null) {
            return;
        }
        delayCounter = script.delay();
        if (!script.frames().isEmpty()) {
            mappingFrame = script.frames().getFirst();
        }
    }
}
