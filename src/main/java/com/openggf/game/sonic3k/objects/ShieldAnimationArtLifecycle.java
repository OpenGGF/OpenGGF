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
 * <p>Animate_Sprite publishes the current cursor before incrementing it. Animation
 * changes reset playback on dispatch; FD only writes anim and returns, leaving
 * initialization of the target to the next dispatch (sonic3k.asm:36157-36216).
 */
final class ShieldAnimationArtLifecycle
        implements RewindStateful<ShieldAnimationArtLifecycle.RewindState> {

    record Art(PlayerSpriteRenderer renderer, SpriteAnimationSet animationSet) {
    }

    record RewindState(int animationId, int previousAnimationId, int frameIndex, int delayCounter, int mappingFrame) {
    }

    private PlayerSpriteRenderer dplcRenderer;
    private SpriteAnimationSet animationSet;
    private PlayerSpriteRenderer boundRenderer;
    private boolean artRefreshPending;
    private int animationId;
    private int previousAnimationId = -1;
    private int frameIndex;
    private int delayCounter;
    private int mappingFrame;

    ShieldAnimationArtLifecycle(int initialAnimationId) {
        animationId = initialAnimationId;
    }

    /**
     * The Y flip every shield's draw uses, under {@code Reverse_gravity_flag}.
     *
     * <p>{@code Obj_InstaShield_Main} (sonic3k.asm:34630-34637),
     * {@code Obj_FireShield_Main} (:34702-34709), {@code Obj_LightningShield_Main}
     * (:34783-34790) and {@code Obj_BubbleShield_Main} (:34947-34954) each inherit the
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
        animationId = nextAnimationId;
    }

    void restartAnimation(int nextAnimationId) {
        animationId = nextAnimationId;
        previousAnimationId = -1;
    }

    /** The early returns in the three elemental shield Main routines precede animation. */
    static boolean elementalAnimationFrozen(com.openggf.sprites.playable.AbstractPlayableSprite player) {
        return player.getInvincibleFrames() > 0 || player.getAnimationId() == 0x1C;
    }

    void stepAnimation() {
        if (animationSet == null) {
            return;
        }
        if (animationId != previousAnimationId) {
            previousAnimationId = animationId;
            frameIndex = 0;
            delayCounter = 0;
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
        if (frameIndex >= script.frames().size()) {
            switch (script.endAction()) {
                case LOOP -> frameIndex = 0;
                case LOOP_BACK -> frameIndex = Math.max(0, frameIndex - script.endParam());
                case SWITCH -> {
                    animationId = script.endParam();
                    return;
                }
                case HOLD -> frameIndex = script.frames().size() - 1;
            }
        }
        mappingFrame = script.frames().get(frameIndex++);
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
        return new RewindState(animationId, previousAnimationId, frameIndex, delayCounter, mappingFrame);
    }

    @Override
    public void restoreRewindStateValue(RewindState state) {
        animationId = state.animationId();
        previousAnimationId = state.previousAnimationId();
        frameIndex = state.frameIndex();
        delayCounter = state.delayCounter();
        mappingFrame = state.mappingFrame();
    }

}
