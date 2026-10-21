package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.DestructionEffects;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.graphics.GLCommand;

import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * S3K Obj $8E - Monkey Dude (AIZ).
 * Core routine mapping: Obj_MonkeyDude (sonic3k.asm loc_87172..loc_876BD).
 *
 * This implementation preserves the key gameplay behavior: alternating wait/active
 * cycles, subtype-driven vertical stepping and independently dispatched coconut throws.
 */
public final class MonkeyDudeBadnikInstance extends AbstractS3kBadnikInstance implements SpawnRewindRecreatable {
    private static final int COLLISION_SIZE_INDEX = 0x0B; // ObjDat_MonkeyDude flags $0B
    private static final int PRIORITY_BUCKET = 5;         // ObjDat_MonkeyDude priority $280

    private static final int WAIT_FRAMES = 60 - 1;        // loc_87172, loc_87218
    private static final int STEP_PIXELS = 8;             // loc_87218 branches

    // byte_876B4: (7) 0,1 loop
    private static final int[] WAIT_FRAMES_SEQ = {0, 1};
    private static final int[] WAIT_DELAYS = {7, 7};
    // byte_876B8: 0,7 then 2,7 loop
    private static final int[] ACTIVE_FRAMES_SEQ = {0, 2};
    private static final int[] ACTIVE_DELAYS = {7, 7};

    private enum State {
        WAIT,
        ACTIVE
    }

    private int activeStepCount;
    private int firstStepCount;
    private boolean initialFacingLeft;
    private boolean nativeRetirementPending;
    private boolean ownedRetirementRequested;
    private boolean armRootActivated;
    /**
     * routine 0. Obj_WaitOffscreen's release pass (loc_85B02) returns without
     * dispatching and leaves routine at 0, so the first dispatch that reaches the
     * routine table runs MonkeyDude_Init, not MonkeyDude_Wait.
     */
    private boolean initPending = true;

    private State state = State.WAIT;
    private int stateTimer = WAIT_FRAMES;
    private int remainingVerticalSteps;
    private boolean movingUp;

    private int animIndex;
    private int animTimer;
    private int[] frames = WAIT_FRAMES_SEQ;
    private int[] delays = WAIT_DELAYS;

    public MonkeyDudeBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "MonkeyDude",
                Sonic3kObjectArtKeys.MONKEY_DUDE, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);

        // ROM init:
        // d0 = subtype >> 2; subtype = d0; $39 = d0 >> 1
        int subtypeShift2 = Math.max(1, (spawn.subtype() & 0xFF) >> 2);
        this.activeStepCount = subtypeShift2;
        this.firstStepCount = Math.max(1, subtypeShift2 >> 1);
        this.initialFacingLeft = facingLeft;
        this.remainingVerticalSteps = firstStepCount;
        this.mappingFrame = WAIT_FRAMES_SEQ[0];
        this.animTimer = 0;
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        // ROM entry point begins with Obj_WaitOffscreen, so the state machine,
        // arm animation, and throw cadence must not advance until visible.
        if (isDestroyed()) return;
        if (nativeRetirementPending) {
            // loc_85094 published status7 on the preceding own dispatch;
            // Delete_Current_Sprite now clears the complete physical record.
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
            return;
        }
        if (ownedRetirementRequested) {
            // An authored owner can withdraw after this lower body/arm chain
            // has already run. Publish loc_85094's status7 only HERE, at the
            // body's own slot, so every higher Child_Draw_Sprite sees it this
            // pass before Delete_Current_Sprite clears the body next pass.
            ownedRetirementRequested = false;
            nativeRetirementPending = true;
            return;
        }

        boolean withinWaitOffscreenBounds = isOnScreen(0x20);
        if (!armRootActivated) {
            if (!withinWaitOffscreenBounds) {
                // loc_85AD2/loc_85AFC has not restored the body continuation:
                // it clears immediately, unlike normal tail loc_85094's
                // status7 marker followed by a later Delete_Current_Sprite.
                if (isOutsideNativeCoarseWindow()) {
                    ObjectLifetimeOps.releaseSpawnForRespawn(services().objectManager(),this,spawn);
                    ObjectLifetimeOps.destroyRespawnableOffscreen(this);
                }
                return;
            }
            armRootActivated = true;
            // Obj_WaitOffscreen's release pass is loc_85B02: it restores the
            // saved operation into (a0) and `rts` back to Process_Sprites
            // (sonic3k.asm:180390-180392). It does NOT re-enter Obj_MonkeyDude,
            // so the release frame runs no routine at all -- routine 0 is
            // dispatched on the FOLLOWING frame. Returning here keeps that
            // dispatch separate from the Init dispatch below; both frames exist
            // in the ROM and both must be consumed.
            return;
        }

        if (initPending) {
            // MonkeyDude_Init (sonic3k.asm:182801-182819) calls
            // SetUp_ObjAttributes (tail `addq.b #2,routine(a0)` / `rts`,
            // sonic3k.asm:176992-177010), rewrites subtype = subtype >> 2 and
            // $39 = subtype >> 3, points $30 at AniRaw_MonkeyDudeWait, sets
            // $2E = 60-1 and $34 = MonkeyDude_StartActive, and ends by jumping to
            // CreateChild4_LinkListRepeated for the arm chain. Every path RETURNS
            // rather than falling through to MonkeyDude_Wait, so this dispatch
            // runs no animation, no facing check and no throw cadence; routine 2
            // starts on the next dispatch. The constructor already applies those
            // field writes; the native child records are created here.
            initPending = false;
            createNativeArmChain();
            checkNativeRetirement();
            return;
        }

        // Obj_WaitOffscreen restores the continuation ($8715A) once, so the
        // body and children keep running offscreen until normal unload.

        // MonkeyDude_Wait/loc_85652 and MonkeyDude_Active never write body
        // render_flags or x_pos. Preserve the literal placement flip and X;
        // only the independent arm root uses Find_SonicTails to select attacks.
        // Process_Sprites dispatches the body before its newly allocated children.
        switch (state) {
            case WAIT -> updateWait();
            case ACTIVE -> updateActive();
        }
        checkNativeRetirement();
    }

    @Override
    public int getCollisionFlags() {
        // collision_flags is written by SetUp_ObjAttributes inside
        // MonkeyDude_Init (sonic3k.asm:177001), so the SST slot still reads zero
        // for the whole Init dispatch: the frame's touch scan runs at the player
        // slot before this object's routine.
        return initPending || nativeRetirementPending ? 0 : super.getCollisionFlags();
    }

    private void updateWait() {
        tickAnimation();

        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.ACTIVE;
        setAnimation(ACTIVE_FRAMES_SEQ, ACTIVE_DELAYS);
    }

    private void updateActive() {
        if (tickAnimation()) {
            applyVerticalStepOnAnimationEdge();
        }
    }

    private boolean tickAnimation() {
        if (animTimer > 0) {
            animTimer--;
            return false;
        }
        animIndex = (animIndex + 1) % frames.length;
        mappingFrame = frames[animIndex];
        animTimer = delays[animIndex];
        return true;
    }

    private void setAnimation(int[] frames, int[] delays) {
        this.frames = frames;
        this.delays = delays;
        this.animIndex = 0;
        this.mappingFrame = frames[0];
        // loc_871C2/loc_87218 clear anim_frame_timer; Animate_RawMultiDelay
        // decrements first, so the next dispatch publishes the next frame.
        this.animTimer = 0;
    }

    private void applyVerticalStepOnAnimationEdge() {
        if (!movingUp) {
            if (mappingFrame != 0) {
                return;
            }
            currentY += STEP_PIXELS;
            remainingVerticalSteps--;
            if (remainingVerticalSteps <= 1) {
                resetToWaitAndToggleDirection();
            }
            return;
        }

        if (mappingFrame != 2) {
            return;
        }
        remainingVerticalSteps--;
        if (remainingVerticalSteps <= 0) {
            resetToWaitAndToggleDirection();
            return;
        }
        currentY -= STEP_PIXELS;
    }

    private void resetToWaitAndToggleDirection() {
        state = State.WAIT;
        stateTimer = WAIT_FRAMES;
        movingUp = !movingUp;
        remainingVerticalSteps = activeStepCount;
        setAnimation(WAIT_FRAMES_SEQ, WAIT_DELAYS);
    }

    private void createNativeArmChain() {
        if (tryServices()==null || services().objectManager()==null) return;
        int previous=getSlotIndex();
        // CreateChild4_LinkListRepeated calls checked AfterCurrent from the
        // body five times, copying the same word positions into subtype0/2/4/6/8.
        for (int index=0;index<5;index++) {
            int subtype=index*2, predecessor=previous;
            var child=spawnChild(() -> new MonkeyDudeArmObjectInstance(
                    new ObjectSpawn(currentX,currentY,spawn.objectId(),subtype,
                            initialFacingLeft?0:1,false,0),predecessor));
            if (child==null || child.isDestroyed()) break;
            previous=child.getSlotIndex();
        }
    }

    private void checkNativeRetirement() {
        // Sprite_CheckDeleteTouch/loc_85088 uses coarse-back X at the routine
        // tail. Keep the existing viewport extension, native $280 at320px.
        if (isOutsideNativeCoarseWindow()) {
            nativeRetirementPending=true;
            ObjectLifetimeOps.releaseSpawnForRespawn(services().objectManager(),this,spawn);
        }
    }

    private boolean isOutsideNativeCoarseWindow() {
        return tryServices()!=null && services().camera()!=null
                && isCoarseXOutOfRange(currentX,services().camera().getX(),coarseXCullRange());
    }

    int nativeMappingFrame() { return mappingFrame; }
    boolean nativeRetirementPending() { return nativeRetirementPending; }

    /** Withdraws an authored native lookout without damage rewards or a slot transfer. */
    public void requestOwnedRetirement() {
        // Do not turn a late caller into a pending setter: the body must first
        // publish that state in its own dispatch. A published/killed body must
        // neither rearm nor affect a replacement in its former physical slot.
        if (!isDestroyed() && !nativeRetirementPending) ownedRetirementRequested = true;
    }

    @Override public boolean isPersistent() { return true; }

    private DestructionEffects.DestructionConfig linkedDestruction() {
        int displacedMapping=mappingFrame;
        return new DestructionEffects.DestructionConfig(
                    S3K_DESTRUCTION_CONFIG.sfxId(),S3K_DESTRUCTION_CONFIG.animalFactory(),false,
                    S3K_DESTRUCTION_CONFIG.pointsFactory(),
                    (x,y,services,points) -> new MonkeyDudeExplosionObjectInstance(
                            x,y,services,points,displacedMapping),false);
    }

    @Override protected void destroyBadnik(PlayableEntity player) {
        destroyAsS3kBadnik(this,getBodyAnchorX(),getBodyAnchorY(),spawn,player,services(),
                false,linkedDestruction());
    }
    @Override public void onPoweredScreenAttack(PlayableEntity player) {
        destroyAsS3kBadnik(this,getBodyAnchorX(),getBodyAnchorY(),spawn,player,services(),
                true,linkedDestruction());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || nativeRetirementPending) return;
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.MONKEY_DUDE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int anchorX = getRenderAnchorX();
        boolean hFlip = !facingLeft;
        renderer.drawFrameIndex(mappingFrame, anchorX, currentY, hFlip, false);
    }

}
