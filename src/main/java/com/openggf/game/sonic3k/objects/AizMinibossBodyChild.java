package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TouchActorContextPolicy;
import com.openggf.level.objects.TouchAttackBouncePolicy;
import com.openggf.level.objects.TouchCategoryDecodeMode;
import com.openggf.level.objects.TouchOverlapStopPolicy;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchShieldDeflectCapability;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss body child.
 *
 * ROM: loc_686BE / loc_686E8
 * - X/Y offset from ChildObjDat_6905C: (0, +0x20)
 * - collision_flags=$9C (hurt)
 * - animated body frames while active
 */
public class AizMinibossBodyChild extends AizMinibossComponent implements TouchResponseProvider, RewindRecreatable {
    private static final int Y_OFFSET = 0x20;
    private static final int COLLISION_FLAGS = 0x9C;
    private static final int SHIELD_REACTION = 1 << 4;
    private static final int PALETTE_OVERRIDE = 0;
    private static final TouchResponseProfile TOUCH_RESPONSE_PROFILE = new TouchResponseProfile(
            TouchCategoryDecodeMode.NORMAL,
            false,
            true,
            false,
            TouchShieldDeflectCapability.NONE,
            SHIELD_REACTION,
            TouchAttackBouncePolicy.STANDARD_ENEMY_KILL,
            TouchActorContextPolicy.MAIN_FULL_SIDEKICK_HURT_ONLY,
            TouchOverlapStopPolicy.STOP_AFTER_FIRST_OVERLAP_FOR_ALL_ACTORS);

    // ROM byte_69126 / byte_4703C (s3.asm):
    //   dc.b 1, 1, 2, $FC   — Animate_Raw: delay=1, frame 1, frame 2, $FC=restart
    //   dc.b 7, 3, 4, 5, $F4 — DEAD CODE (never reached, $FC restarts to frame 1)
    //
    // AnimateRaw_Restart resets to byte[0]=timer, byte[1]=frame of the SAME script.
    // So the body child loops frames 1→2 with delay 1, forever.
    private static final int ANIM_DELAY = 1;
    private static final int FRAME_A = 1;
    private static final int FRAME_B = 2;

    private int mappingFrame = FRAME_A;
    private int animTimer = ANIM_DELAY;

    public AizMinibossBodyChild(AbstractBossInstance parent) {
        super(parent, "AIZMinibossBody", 3, 0);
        initializeAdjustedPosition(parent, 0, Y_OFFSET);
        updateDynamicSpawn();
    }

    private AizMinibossBodyChild(ObjectSpawn spawn) {
        super(spawn, "AIZMinibossBody", 3);
    }

    @Override
    public AizMinibossBodyChild recreateForRewind(RewindRecreateContext ctx) {
        return new AizMinibossBodyChild(ctx.spawn());
    }

    @Override public void syncPositionWithParent() { refreshAdjustedPosition(0, Y_OFFSET); }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!beginOwnDispatch(vIntRunCount)) return;
        syncPositionWithParent();
        updateAnimation();
        finishNativeDrawTail();
    }

    private void updateAnimation() {
        // loc_686E8 runs Animate_Raw before the separate status7 touch gate.
        animTimer--;
        if (animTimer > 0) {
            return;
        }
        // ROM: $FC restarts Animate_Raw to byte[0]=delay, byte[1]=frame.
        // Loops: frame 1 → frame 2 → restart to frame 1 → ...
        mappingFrame = (mappingFrame == FRAME_A) ? FRAME_B : FRAME_A;
        animTimer = ANIM_DELAY;
    }

    @Override
    public int getCollisionFlags() {
        if (nativeRetirementPending || parentHeader().defeated() || isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TOUCH_RESPONSE_PROFILE;
    }

    @Override
    public boolean usesCurrentTouchResponseState() {
        // loc_686E8 refreshes the child position immediately before
        // Child_DrawTouch_Sprite2 publishes its object-RAM pointer. The next
        // player slot therefore reads this live child position.
        return true;
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,1,1) — priority bit = 1
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (nativeRetirementPending || isDestroyed()) return;
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (nativeRenderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, hFlip, false, PALETTE_OVERRIDE);
    }
}
