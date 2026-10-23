package com.openggf.sprites.managers;

import com.openggf.game.resources.DynamicArtDecisionOwner;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.PlayerAnimationRules;
import com.openggf.game.rules.TailsTailPushDetection;
import com.openggf.physics.Direction;

import static com.openggf.sprites.managers.TailsTailPose.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

/**
 * Controls animation and rendering of Tails' tails (the rotating appendage).
 * ROM: Object 05 (Obj05) - a separate sprite that follows Tails and shows
 * his tails spinning based on his current animation state.
 *
 * During walk/run, the tails are part of the main sprite's mapping frames,
 * so Obj05 shows "Blank". During rolling, spindash, pushing, etc., the main
 * sprite's ball/body form doesn't include tails, so Obj05 renders them.
 *
 * ROM reference: s2.asm Obj05 (lines 41278-41414)
 */
@com.openggf.game.ModApi
public class TailsTailsController {
    private final AbstractPlayableSprite sprite;
    private final PlayerSpriteRenderer renderer;
    private final boolean isS3k;
    private final DynamicArtDecisionOwner dynamicArtDecisionOwner;

    private int currentAnim = ANIM_BLANK;
    private int lastParentAnim = -1;
    /** ROM anim_frame: the index of the NEXT script byte to read (s2.asm:41303). */
    private int frameIndex;
    private int frameTick;
    /** ROM mapping_frame: the frame resolved by the last script read (s2.asm:41302). */
    private int mappingFrame = -1;
    /**
     * ROM render_flags x_flip/y_flip for the directional tail. TAnim_GetTailFrame
     * calls CalcAngle on the parent's velocity, writes render_flags(a0) and adds the
     * direction bank into mapping_frame(a0) (s2.asm:41485-41516), but it is reached
     * only past TAnim_WalkRunZoom's {@code subq.b #1,anim_frame_duration(a0) /
     * bpl.s TAnim_Delay} early-out (s2.asm:41338-41339). During the countdown frames
     * neither CalcAngle nor the bank/flip write runs, so both are stored state rather
     * than values derived from the parent's current velocity. S3K's
     * Animate_Tails_Part2 gates the identical directional path the same way
     * (sonic3k.asm:29415-29416 and :29632-29644), so this is a universal correction.
     */
    private boolean dirHFlip;
    private boolean dirVFlip;

    public record RewindState(
            int currentAnim,
            int lastParentAnim,
            int frameIndex,
            int frameTick,
            int mappingFrame,
            boolean dirHFlip,
            boolean dirVFlip) {
    }

    public PlayerSpriteRenderer getRenderer() {
        return renderer;
    }

    public TailsTailsController(AbstractPlayableSprite sprite, PlayerSpriteRenderer renderer) {
        this(sprite, renderer, false, null);
    }

    public TailsTailsController(AbstractPlayableSprite sprite, PlayerSpriteRenderer renderer, boolean isS3k) {
        this(sprite, renderer, isS3k, null);
    }

    public TailsTailsController(
            AbstractPlayableSprite sprite,
            PlayerSpriteRenderer renderer,
            boolean isS3k,
            DynamicArtDecisionOwner dynamicArtDecisionOwner) {
        this.sprite = sprite;
        this.renderer = renderer;
        this.isS3k = isS3k;
        this.dynamicArtDecisionOwner = dynamicArtDecisionOwner;
    }

    public void update() {
        int parentAnimId = sprite.getAnimationId();

        // ROM Obj05_Main: moveq #0,d0 / move.b anim(a2),d0, then the pushing
        // override forces d0 = 4 (TailsAni_Push) before both the change test and
        // the Obj05AniSelection lookup (docs/s2disasm/s2.asm:41744-41751;
        // docs/skdisasm/sonic3k.asm:30081-30091). Obj05AniSelection[4] = 9 =
        // Obj05Ani_Pushing (docs/s2disasm/s2.asm:41770-41776).
        if (parentPushOverrideApplies()) {
            parentAnimId = PARENT_ANIM_PUSH;
        }

        // ROM: Only update Obj05 animation when parent's animation changes
        // This allows Flick -> Swish transition without being overridden.
        // The value compared against and stored in Obj05_parent_prev_anim /
        // objoff_34 is the OVERRIDDEN d0 (docs/s2disasm/s2.asm:41755-41758;
        // docs/skdisasm/sonic3k.asm:30093-30096).
        if (parentAnimId != lastParentAnim) {
            lastParentAnim = parentAnimId;
            int obj05Anim = resolveObj05Animation(parentAnimId);
            if (obj05Anim != currentAnim) {
                // ROM: anim_frame = 0, anim_frame_duration = 0 on animation
                // change (s2.asm:41276-41278; sonic3k.asm:36203-36206).
                currentAnim = obj05Anim;
                frameIndex = 0;
                frameTick = 0;
                mappingFrame = -1;
                dirHFlip = false;
                dirVFlip = false;
            }
        }

        int[] frames = getFrames(currentAnim);
        if (frames == null) {
            return;
        }

        // ROM: subq.b #1,anim_frame_duration / bpl TAnim_Delay (s2.asm:41291-41292).
        // While time remains the mapping frame is unchanged, but Obj05's routine
        // tail still runs LoadTailsTailsDynPLC, which dedupes the unchanged
        // mapping frame against TailsTails_LastLoadedDPLC (s2.asm:41631-41651).
        int remaining = frameTick - 1;
        if (remaining >= 0) {
            frameTick = remaining;
            publishDynamicArtDecision();
            return;
        }
        // ROM: move.b d0,anim_frame_duration - reload the script's duration byte
        // (s2.asm:41293).
        frameTick = getDelay(currentAnim);

        // ROM: the end-of-script flag is handled in TAnim_End_FF / TAnim_End_FD
        // BEFORE the mapping frame is written (s2.asm:41306-41320).
        if (frameIndex >= frames.length) {
            if (currentAnim == ANIM_FLICK) {
                // Obj05Ani_Flick ends `$FD,1` -> run Obj05Ani_Swish (s2.asm:41817).
                currentAnim = ANIM_SWISH;
                frames = getFrames(currentAnim);
                frameTick = getDelay(currentAnim);
                frameIndex = 0;
            } else {
                // ROM: $FF -> restart the animation (s2.asm:41831).
                frameIndex = 0;
            }
        }

        // ROM TAnim_Do2 / TAnim_Next: read the script byte at the CURRENT
        // anim_frame, store it in mapping_frame, and only afterwards
        // addq.b #1,anim_frame (s2.asm:41295-41303). The identical
        // read-then-increment convention is used by S3K's shared
        // Animate_Sprite (sonic3k.asm:36211-36223), so this is a universal
        // correction rather than a per-game rule.
        mappingFrame = frames[frameIndex];
        frameIndex++;
        if (currentAnim == ANIM_DIRECTIONAL) {
            // ROM TAnim_GetTailFrame: CalcAngle on the parent's (x_vel,y_vel),
            // `lsr.b #3,d0 / andi.b #$C,d0` -> direction bank d3, then
            // `add.b d3,mapping_frame(a0)` plus the render_flags x_flip/y_flip write
            // (s2.asm:41485-41516). This runs only past the anim_frame_duration
            // early-out (s2.asm:41338-41339), so the bank and the flips are latched
            // here and held frozen over the countdown frames - matching
            // sonic3k.asm:29632-29644 behind :29415-29416.
            int adjustedTailAngle = computeAdjustedTailAngle();
            mappingFrame += (adjustedTailAngle >> 3) & 0x0C;

            // ROM TAnim_GetTailFrame: the high angle bit sets both flip bits;
            // the facing-direction bit then toggles x_flip (s2.asm:41505-41516).
            int directionalFlipFlags = (adjustedTailAngle & 0x80) != 0 ? 3 : 0;
            if (Direction.LEFT.equals(sprite.getDirection())) {
                directionalFlipFlags ^= 1;
            }
            dirHFlip = (directionalFlipFlags & 1) != 0;
            dirVFlip = (directionalFlipFlags & 2) != 0;
        }
        publishDynamicArtDecision();
    }

    private void publishDynamicArtDecision() {
        if (isS3k) {
            submitTailLoadPlc();
            return;
        }
        submitTailLoadPlc();
        if (renderer == null || mappingFrame < 0) {
            return;
        }
        // ROM LoadTailsTailsDynPLC reads the STORED mapping_frame(a0) and dedupes it
        // against TailsTails_LastLoadedDPLC (s2.asm:41631-41638). It runs every frame,
        // but on a countdown frame mapping_frame is unchanged, so it queues nothing.
        if (dynamicArtDecisionOwner != null) {
            dynamicArtDecisionOwner.observe(mappingFrame);
        }
    }

    /**
     * S2 Obj05_Main calls {@code LoadTailsTailsDynPLC} after animating on every frame, as does S3K's
     * {@code Tails_Tail_Load_PLC}. A game-owned native ROM art sink can receive S2's stored mapping frame without an
     * ordinary renderer; S3K retains its renderer requirement. Rendering keeps its own pattern upload path.
     */
    private void submitTailLoadPlc() {
        if ((isS3k && renderer == null) || mappingFrame < 0) {
            return;
        }
        var level = sprite.currentLevelManagerIfAvailable();
        var objects = level != null ? level.getObjectManager() : null;
        if (objects != null && objects.getObjectServices().runtimeArtCoordinator()
                instanceof com.openggf.game.internal.PlayerDplcDmaSink sink) {
            sink.tailsTailLoadPlc(mappingFrame, renderer != null ? renderer.dplcFrame(mappingFrame) : null);
        }
    }

    /** Native appendage presentation, including the animation's latched directional flips. */
    @com.openggf.game.ModApi
    public record Presentation(int mappingFrame, boolean flipX, boolean flipY) { }

    /** Returns null when the native tail animation is blank. Does not advance animation. */
    public Presentation presentation() {
        if (currentAnim == ANIM_BLANK || mappingFrame < 0) return null;
        if (currentAnim == ANIM_DIRECTIONAL) {
            return new Presentation(mappingFrame, dirHFlip, dirVFlip);
        }
        // S3K loc_1613C composes reverse-gravity at draw time, except for the
        // directional animation whose stored flags already contain both flips.
        var gameState = sprite.currentGameStateOrNull();
        return new Presentation(mappingFrame, Direction.LEFT.equals(sprite.getDirection()),
                gameState != null && gameState.isReverseGravityActive());
    }

    public void draw() {
        Presentation pose = presentation();
        if (pose == null || renderer == null) return;
        int originX = sprite.getRenderCentreX();
        int originY = sprite.getRenderCentreY();
        com.openggf.sprites.playable.PlayableMutatorPresentation.draw(sprite,
                com.openggf.graphics.SpritePresentation.Part.APPENDAGE,
                () -> renderer.drawFrame(pose.mappingFrame(), originX, originY, pose.flipX(), pose.flipY()));
    }

    public RewindState captureRewindState() {
        return new RewindState(
                currentAnim, lastParentAnim, frameIndex, frameTick, mappingFrame,
                dirHFlip, dirVFlip);
    }

    public void restoreRewindState(RewindState state) {
        if (state == null) {
            currentAnim = ANIM_BLANK;
            lastParentAnim = -1;
            frameIndex = 0;
            frameTick = 0;
            mappingFrame = -1;
            dirHFlip = false;
            dirVFlip = false;
            return;
        }
        currentAnim = state.currentAnim();
        lastParentAnim = state.lastParentAnim();
        frameIndex = state.frameIndex();
        frameTick = state.frameTick();
        mappingFrame = state.mappingFrame();
        dirHFlip = state.dirHFlip();
        dirVFlip = state.dirVFlip();
    }

    /**
     * ROM Obj05_Main's pushing override predicate. S2 REV01 ships FixBugs = 0, so
     * the test is the bare pushing status bit (docs/s2disasm/s2.asm:41748-41751);
     * the FixBugs = 1 mapping_frame $63..$66 form (docs/s2disasm/s2.asm:41743-41746)
     * is not the recorded behaviour. S3K additionally requires the parent's
     * mapping_frame to lie in $A9..$AC (docs/skdisasm/sonic3k.asm:30086-30091).
     * S3K's {@code tst.b (WindTunnel_flag_P2).w} gate (sonic3k.asm:30085) has no
     * engine-side state yet; the mapping-frame range is the narrow gate that keeps
     * the override from firing outside the parent's pushing frames.
     */
    private boolean parentPushOverrideApplies() {
        TailsTailPushDetection detection = pushDetectionOrNull();
        if (detection == null || !detection.supported()) {
            return false;
        }
        if (!sprite.getPushing()) {
            return false;
        }
        if (detection.requiresPushMappingFrameRange()) {
            int parentMappingFrame = sprite.getMappingFrame();
            if (parentMappingFrame < detection.pushMappingFrameLow()
                    || parentMappingFrame > detection.pushMappingFrameHigh()) {
                return false;
            }
        }
        return true;
    }

    private TailsTailPushDetection pushDetectionOrNull() {
        GameRules rules = sprite.getGameRules();
        if (rules == null) {
            return null;
        }
        PlayerAnimationRules animationRules = rules.playerAnimation();
        return animationRules == null ? null : animationRules.tailsTailPushDetection();
    }

    private int resolveObj05Animation(int parentAnimId) {
        return TailsTailPose.animation(isS3k, parentAnimId);
    }

    private int[] getFrames(int anim) {
        return TailsTailPose.frames(isS3k, anim);
    }

    private int getDelay(int anim) {
        return TailsTailPose.delay(anim);
    }

    /**
     * Computes the velocity angle after TAnim_GetTailFrame has adjusted it for
     * the parent's facing direction and added its quadrant bias. The caller uses
     * the same ROM value for both mapping-bank selection and render flips.
     */
    private int computeAdjustedTailAngle() {
        // ROM TAnim_GetTailFrame calls CalcAngle UNCONDITIONALLY
        // (docs/s2disasm/s2.asm:41484-41487); there is no zero-velocity
        // short-circuit. CalcAngle_Zero itself returns $40
        // (docs/s2disasm/s2.asm:4039-4040,4076-4078), which then banks to 8 for a
        // right-facing Tails, not 0. S3K's GetArcTan has the same zero return
        // (docs/skdisasm/sonic3k.asm:3075), so this is a universal correction.
        short xVel = sprite.getXSpeed();
        short yVel = sprite.getYSpeed();

        // ROM: TAnim_GetTailFrame (s2.asm:41478-41481) calls CalcAngle
        // (s2.asm:4037-4081, Angle_Data table); S3K's tail routine calls the
        // identical GetArcTan (sonic3k.asm:3075, ArcTanTable).
        // 0=right, 64=down, 128=left, 192=up (Genesis convention, Y-down).
        boolean facingLeft = Direction.LEFT.equals(sprite.getDirection());
        return TailsTailPose.adjustedAngle(xVel, yVel, facingLeft);
    }
}
