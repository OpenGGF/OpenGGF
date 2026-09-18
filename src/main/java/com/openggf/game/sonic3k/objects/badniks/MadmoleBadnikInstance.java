package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * S3K SKL Obj $8C - Madmole.
 *
 * <p>ROM reference: {@code Obj_Madmole} at {@code sonic3k.asm:193075}. This
 * class is the parent ground cap. It waits for a player within {@code $A0},
 * sets its {@code $38} bit 1 busy flag and allocates the separate
 * {@link MadmoleBodyChild} with {@code CreateChild1_Normal}
 * ({@code ChildObjDat_8D9C0}). It waits until the body clears that bit on its
 * normal sink-delete path, then waits 60 frames before arming again. The cap
 * runs {@code sub_8D876} ({@code SolidObjectFull}) every frame regardless of
 * the body.
 */
public final class MadmoleBadnikInstance extends AbstractS3kBadnikInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {

    /**
     * Word 0 of this object's S3K SST holds its live ROM code pointer.
     * ROM {@code Obj_Madmole} is installed from the S3K object pointer table at
     * {@code $0008D580} (table read from the user-supplied ROM; the
     * label is defined at docs/skdisasm/sonic3k.asm:193075).
     * Its whole code block lies in one bank, so the HIGH word that
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} and compares
     * on the next off-screen on-object frame is {@code $0008}
     * (docs/skdisasm/sonic3k.asm:26816-26843).
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0008;
    }

    private static final int CAP_COLLISION_FLAGS = 0;
    private static final int CAP_MAPPING_FRAME = 0x0D;
    // word_8D9B4 (sonic3k.asm:193500): body collision_flags $0B.
    private static final int BODY_CHILD_COLLISION_SIZE_INDEX = 0x0B;
    private static final int PRIORITY_BUCKET = 5;
    private static final int CAP_RENDER_HALF_WIDTH = 0x18;
    private static final int CAP_RENDER_HALF_HEIGHT = 0x04;
    private static final int BODY_RENDER_HALF_WIDTH = 0x0C;
    private static final int BODY_RENDER_HALF_HEIGHT = 0x0C;
    private static final int WAIT_OFFSCREEN_MARGIN = 0x20;
    private static final int ACTIVATION_RANGE = 0xA0;
    private static final int RISE_SINK_FRAMES = 0x1F;
    private static final int PAUSE_FRAMES = 0x1F;
    private static final int RAW_ANIMATION_DELAY = 2;
    private static final int[] ATTACK_STARTUP_FRAMES = {0, 1, 2};
    private static final int[] SIDE_DRILL_FRAMES = {3, 3, 4, 4, 4, 4, 4, 4};
    private static final int[] SIDE_CHILD_FRAMES = {5, 6, 7, 8, 9, 10, 11, 12};
    private static final int COOLDOWN_FRAMES = 60;
    // ChildObjDat_8D9C0 (sonic3k.asm:193505-193508): body offset (0,+$10).
    private static final int BODY_CHILD_Y_OFFSET = 0x10;
    private static final int SIDE_CHILD_X_OFFSET = 0x0E;
    private static final int SIDE_CHILD_Y_OFFSET = -0x0C;
    private static final int SIDE_CHILD_COLLISION_FLAGS = 0xD8;
    private static final int RISE_Y_VELOCITY = -0x100;
    private static final int SINK_Y_VELOCITY = 0x100;
    private static final int SIDE_CHILD_STRAIGHT_X_VELOCITY = -0x600;
    private static final int SIDE_CHILD_ARC_X_VELOCITY = -0x380;
    private static final int SIDE_CHILD_ARC_Y_VELOCITY = 0x200;
    private static final int SIDE_CHILD_ARC_REBOUND_Y_VELOCITY = -0x500;
    private static final int SIDE_CHILD_ARC_RELEASE_THRESHOLD_Y_VELOCITY = 0xA00;
    private static final int SIDE_CHILD_ARC_RELEASE_PLAYER_Y_VELOCITY = -0x300;
    private static final int SIDE_CHILD_ARC_RELEASE_DRILL_Y_VELOCITY = -0x200;
    private static final int SIDE_CHILD_CAPTURE_WALL_SENSOR_OFFSET = 0x18;
    // ROM loc_8D6E6 offscreen bands (sonic3k.asm:193223-193232).
    private static final int SIDE_CHILD_OFFSCREEN_BAND_X = 0x280;
    private static final int SIDE_CHILD_OFFSCREEN_BAND_Y = 0x200;
    // ROM loc_8D746 sets y_radius(a0) = 8 for the side drill; ObjCheckFloorDist
    // probes from (x_pos, y_pos + y_radius).
    private static final int SIDE_CHILD_Y_RADIUS = 0x08;
    // ROM MoveSprite_LightGravity (sonic3k.asm:178357) uses moveq #$20,d1 as its
    // per-frame gravity, NOT the standard $38 object gravity. The arcing side
    // drill (loc_8D768/loc_8D778/loc_8D7A8) moves via MoveSprite_LightGravity.
    private static final int SIDE_CHILD_LIGHT_GRAVITY = 0x20;

    /** Parent routine: 2 = loc_8D5B0, 4 = loc_8D5D4, 6 = loc_8D5F4. */
    private enum State {
        WAIT_FOR_PLAYER,
        WAIT_FOR_BODY,
        COOLDOWN
    }

    private State state = State.WAIT_FOR_PLAYER;
    private int timer;
    private boolean waitingForOnscreen = true;
    private boolean initialized;
    // ROM $38(a0) bit 1. Set by loc_8D5BE when the body is allocated; cleared
    // only by the body's loc_8D6D6 sink-delete callback.
    private boolean bodyBusy;
    // ROM status(a0) bit 7 as the body's Child_DrawTouch_Sprite reads it after
    // Sprite_CheckDelete removed this cap (the engine unload path, not a kill).
    private boolean romStatusDeleted;

    public MadmoleBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Madmole", Sonic3kObjectArtKeys.MADMOLE,
                CAP_COLLISION_FLAGS, PRIORITY_BUCKET);
        mappingFrame = CAP_MAPPING_FRAME;
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        if (waitingForOnscreen) {
            // Obj_WaitOffscreen restores Obj_Madmole on the first on-screen frame
            // and returns before the routine dispatch.
            if (isOnScreen(WAIT_OFFSCREEN_MARGIN)) {
                waitingForOnscreen = false;
            }
            updateDynamicSpawn(currentX, currentY);
            return;
        }
        if (!initialized) {
            // Routine 0, loc_8D5A6: SetUp_ObjAttributes only.
            initialized = true;
            updateDynamicSpawn(currentX, currentY);
            return;
        }

        switch (state) {
            case WAIT_FOR_PLAYER -> updateWaitForPlayer(playerEntity);
            case WAIT_FOR_BODY -> updateWaitForBody();
            case COOLDOWN -> updateCooldown();
        }

        updateDynamicSpawn(currentX, currentY);
    }

    /** loc_8D5B0 / loc_8D5BE (sonic3k.asm:193101-193117). */
    private void updateWaitForPlayer(PlayableEntity playerEntity) {
        PlayableEntity target = closestNativePlayerByHorizontalDistance(playerEntity);
        if (target == null) {
            return;
        }
        if (findSonicTailsHorizontalDistance(target) >= ACTIVATION_RANGE) {
            return;
        }
        state = State.WAIT_FOR_BODY;
        bodyBusy = true;
        // CreateChild1_Normal -> AllocateObjectAfterCurrent (sonic3k.asm:176924-176930):
        // the body takes the next free slot after the cap and runs its routine 0
        // later in this same object pass.
        int bodyX = currentX;
        int bodyY = currentY + BODY_CHILD_Y_OFFSET;
        spawnChild(() -> new MadmoleBodyChild(this, bodyX, bodyY));
    }

    /** loc_8D5D4 / loc_8D5DE (sonic3k.asm:193119-193131). */
    private void updateWaitForBody() {
        // Uses the shipped branch: bit 1 is cleared only by loc_8D6D6. If the body
        // is destroyed through Touch_EnemyNormal/EnemyDefeated it becomes an
        // explosion without reaching loc_8D6D6, so the cap stays here forever as a
        // solid stump. A fixed version would clear the bit on defeat and re-arm.
        if (bodyBusy) {
            return;
        }
        state = State.COOLDOWN;
        timer = COOLDOWN_FRAMES;
    }

    /** loc_8D5F4 -> Obj_Wait, then $34 = loc_8D5FA (sonic3k.asm:193133-193140). */
    private void updateCooldown() {
        timer--;
        if (timer >= 0) {
            return;
        }
        state = State.WAIT_FOR_PLAYER;
        timer = 0;
    }

    /** loc_8D6D6: {@code bclr #1,$38(a1)} on the body's parent3. */
    void clearBodyBusy() {
        bodyBusy = false;
    }

    /** status(a1) bit 7 as tested by the body's Child_DrawTouch_Sprite. */
    boolean romStatusDeleted() {
        return romStatusDeleted || isDestroyed();
    }

    @Override
    public void onUnload() {
        // Sprite_CheckDelete / loc_85094 sets status bit 7 before the slot is freed.
        romStatusDeleted = true;
    }

    @Override
    public int getCollisionFlags() {
        // ObjDat_Madmole collision_flags = 0 (sonic3k.asm:193493-193497); the cap
        // never calls Add_SpriteToCollisionResponseList.
        return CAP_COLLISION_FLAGS;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return CAP_RENDER_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return CAP_RENDER_HALF_HEIGHT;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // sub_8D876: d1=$1F, d2=4, d3=5 at the cap's own x_pos/y_pos.
        return SolidObjectParams.of(0x1F, 4, 5, 0, 0);
    }

    @Override
    public boolean usesInclusiveRightEdge() {
        // sub_8D876 runs SolidObjectFull with d1 = $1F; SolidObject_cont's X
        // gate rejects with bhi (sonic3k.asm:41394-41400), so a player resting
        // exactly at obj_x + d1 (d0 == d1 * 2) is a zero-distance side contact.
        // loc_1E042 takes the d0 == 0 branch straight to loc_1E06E, which
        // re-sets Status_Push on the grounded player every frame
        // (sonic3k.asm:41498-41512) — the state Tails' CPU push-bypass
        // auto-jump reads at loc_13DD0/loc_13E9C.
        return true;
    }

    @Override
    public int getTopLandingHalfWidth(PlayableEntity player, int collisionHalfWidth) {
        // ROM Solid_Landed / loc_1E154 (sonic3k.asm:41611-41621) re-reads
        // width_pixels(a0) for the landing X gate. The cap keeps
        // ObjDat_Madmole's width_pixels = $18 (sonic3k.asm:193493-193497),
        // wider than the default d1 - $B = $14 heuristic for sub_8D876's
        // d1 = $1F.
        return 0x18;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.MADMOLE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(CAP_MAPPING_FRAME, currentX, currentY, false, false);
    }

    public String getStateName() {
        return state.name();
    }

    public int getTimer() {
        return timer;
    }

    boolean isBodyBusy() {
        return bodyBusy;
    }

    /**
     * Madmole body: ChildObjDat_8D9C0 code {@code loc_8D602}
     * (sonic3k.asm:193142-193216). It owns its SST slot, rises, pauses, drills,
     * sinks, clears the cap's busy bit and deletes through Go_Delete_Sprite.
     */
    static final class MadmoleBodyChild extends AbstractS3kBadnikInstance implements RewindRecreatable {

        /** Body routine: 2 = loc_8D636, 4 = loc_8D656, 6 = loc_8D67A, 8 = loc_8D6CA. */
        private enum BodyState {
            RISING,
            PAUSING,
            DRILLING,
            SINKING
        }

        // parent3. The central "parent" object-field policy keeps it transient;
        // recreateForRewind relinks it to the live cap at the same position.
        private final MadmoleBadnikInstance parent;
        private BodyState state = BodyState.RISING;
        private boolean initialized;
        private int timer;
        private boolean sideDrillActive;
        // Child_DrawTouch_Sprite result for this pass: false once the cap's
        // status bit 7 sent the body through Go_Delete_Sprite.
        private boolean drawTouchThisFrame = true;
        // Go_Delete_Sprite installed Delete_Current_Sprite; freed on the next pass.
        private boolean deletePending;

        MadmoleBodyChild(MadmoleBadnikInstance parent, int x, int y) {
            // CreateChild1_Normal does not copy render_flags, so bit 0 starts clear.
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0, -1, null, null),
                    "MadmoleBody", Sonic3kObjectArtKeys.MADMOLE,
                    BODY_CHILD_COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
            this.parent = parent;
            this.currentX = x;
            this.currentY = y;
            this.mappingFrame = 0;
        }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            MadmoleBadnikInstance liveParent = findLiveParentForRewind(ctx);
            return liveParent == null ? null
                    : new MadmoleBodyChild(liveParent, ctx.spawn().x(), ctx.spawn().y());
        }

        private static MadmoleBadnikInstance findLiveParentForRewind(RewindRecreateContext ctx) {
            if (ctx == null || ctx.spawn() == null || ctx.objectServices() == null
                    || ctx.objectServices().objectManager() == null) {
                return null;
            }
            MadmoleBadnikInstance best = null;
            long bestDistance = Long.MAX_VALUE;
            for (ObjectInstance instance : ctx.objectServices().objectManager().getActiveObjects()) {
                if (!(instance instanceof MadmoleBadnikInstance cap) || cap.isDestroyed()) {
                    continue;
                }
                // The body never moves horizontally, so x_pos identifies its cap.
                long dx = (long) cap.getX() - ctx.spawn().x();
                long dy = (long) cap.getY() - ctx.spawn().y();
                long distance = dx * dx + dy * dy;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = cap;
                }
            }
            return best;
        }

        @Override
        protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
            if (deletePending) {
                ObjectLifetimeOps.expireDynamic(this);
                return;
            }
            switch (state) {
                case RISING -> updateRising(playerEntity);
                case PAUSING -> updatePausing(playerEntity);
                case DRILLING -> updateDrilling();
                case SINKING -> updateSinking();
            }
            // Child_DrawTouch_Sprite (sonic3k.asm:178053-178058) runs after the
            // routine: if parent3's status bit 7 is set, Go_Delete_Sprite skips
            // both the touch list and Draw_Sprite for this pass.
            if (parent.romStatusDeleted()) {
                drawTouchThisFrame = false;
                deletePending = true;
            } else {
                drawTouchThisFrame = true;
            }
            updateDynamicSpawn(currentX, currentY);
        }

        /** loc_8D620 falling through to loc_8D636 (sonic3k.asm:193158-193177). */
        private void updateRising(PlayableEntity playerEntity) {
            if (!initialized) {
                initialized = true;
                yVelocity = RISE_Y_VELOCITY;
                timer = RISE_SINK_FRAMES;
            }
            faceTowardPlayer(playerEntity);
            moveWithVelocity();
            timer--;
            if (timer >= 0) {
                return;
            }
            // loc_8D648: y_vel is left at -$100 but routine 4 never moves.
            state = BodyState.PAUSING;
            timer = PAUSE_FRAMES;
        }

        /** loc_8D656 / loc_8D662. */
        private void updatePausing(PlayableEntity playerEntity) {
            faceTowardPlayer(playerEntity);
            timer--;
            if (timer >= 0) {
                return;
            }
            state = BodyState.DRILLING;
            animFrame = 0;
            animTimer = 0;
            sideDrillActive = false;
        }

        /** loc_8D67A Animate_Raw over byte_8D9D8 then byte_8D9DD. */
        private void updateDrilling() {
            if (!sideDrillActive) {
                if (animateRaw(ATTACK_STARTUP_FRAMES)) {
                    // loc_8D680: switch script, play sfx, CreateChild1_Normal from
                    // the body's own slot (AllocateObjectAfterCurrent).
                    sideDrillActive = true;
                    services().playSfx(Sonic3kSfx.SPIKE_MOVE.id);
                    int xOffset = facingLeft ? -SIDE_CHILD_X_OFFSET : SIDE_CHILD_X_OFFSET;
                    int x = currentX + xOffset;
                    int y = currentY + SIDE_CHILD_Y_OFFSET;
                    boolean left = facingLeft;
                    spawnChild(() -> new SideDrillChild(x, y, left));
                }
                return;
            }
            if (!animateRaw(SIDE_DRILL_FRAMES)) {
                return;
            }
            // loc_8D6AE.
            state = BodyState.SINKING;
            timer = RISE_SINK_FRAMES;
            yVelocity = SINK_Y_VELOCITY;
        }

        private boolean animateRaw(int[] frames) {
            animTimer--;
            if (animTimer >= 0) {
                return false;
            }
            animFrame++;
            if (animFrame >= frames.length) {
                // loc_84428 clears anim_frame; AnimateRaw_CustomCode clears the timer.
                animFrame = 0;
                animTimer = 0;
                return true;
            }
            mappingFrame = frames[animFrame];
            animTimer = RAW_ANIMATION_DELAY;
            return false;
        }

        /** loc_8D6CA: MoveSprite2 then Obj_Wait with $34 = loc_8D6D6. */
        private void updateSinking() {
            moveWithVelocity();
            timer--;
            if (timer >= 0) {
                return;
            }
            // loc_8D6D6: bclr #1,$38(parent3) then Go_Delete_Sprite. The body is
            // still drawn and added to the touch list by Child_DrawTouch_Sprite
            // this pass; Delete_Current_Sprite frees the slot next pass. The cap
            // runs earlier in slot order, so it observes the clear next frame.
            parent.clearBodyBusy();
            deletePending = true;
        }

        /** sub_8D886: Find_SonicTails and set render_flags bit 0 when d0 != 0. */
        private void faceTowardPlayer(PlayableEntity playerEntity) {
            PlayableEntity target = closestNativePlayerByHorizontalDistance(playerEntity);
            if (target == null) {
                return;
            }
            facingLeft = !findSonicTailsTargetIsRight(target);
        }

        @Override
        public int getCollisionFlags() {
            return drawTouchThisFrame ? BODY_CHILD_COLLISION_SIZE_INDEX : 0;
        }

        @Override
        public int getOnScreenHalfWidth() {
            return BODY_RENDER_HALF_WIDTH;
        }

        @Override
        public int getOnScreenHalfHeight() {
            return BODY_RENDER_HALF_HEIGHT;
        }

        @Override
        public boolean usesCustomOutOfRangeCheck() {
            // loc_8D602 ends in Child_DrawTouch_Sprite, never Sprite_CheckDelete;
            // the body only leaves through its parent check or loc_8D6D6.
            return true;
        }

        @Override
        public boolean isCustomOutOfRange(int cameraX) {
            return false;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!drawTouchThisFrame) {
                return;
            }
            super.appendRenderCommands(commands);
        }

        String getStateName() {
            return state.name();
        }

        int getTimer() {
            return timer;
        }

        int getYVelocity() {
            return yVelocity;
        }

        int getMappingFrame() {
            return mappingFrame;
        }

        boolean isDeletePending() {
            return deletePending;
        }

        MadmoleBadnikInstance parentForTests() {
            return parent;
        }
    }

    static final class SideDrillChild extends AbstractObjectInstance
            implements TouchResponseProvider, TouchResponseListener, RewindRecreatable {
        private static final int RENDER_HALF_WIDTH = 0x08;
        private static final int RENDER_HALF_HEIGHT = 0x08;
        private static final int PRIORITY_BUCKET = 5;

        private int currentX;
        private int currentY;
        private int xVelocity;
        private int yVelocity;
        private int xSubpixel;
        private int ySubpixel;
        // Un-final so the generic field capturer reapplies it after a rewind
        // recreate (the hook recovers it from spawn.renderFlags()).
        private boolean facingLeft;
        private boolean initialized;
        private boolean arcing;
        private boolean postCaptureDrift;
        private boolean straightTouchConsumed;
        // ROM sub_8D94A sets routine 8 during the arm's own routine-4 execution
        // (loc_8D778), but loc_8D778 still runs MoveSprite_LightGravity to
        // completion without carrying the player that frame. The carry routine
        // (loc_8D7A8) only runs the following frame. This latch defers the first
        // carry by one frame accordingly.
        private boolean awaitingCarryRoutine;
        private AbstractPlayableSprite capturedPlayer;
        // ROM $44(a0). Written by the straight drill's touch response sub_8D8E6
        // (move.w a2,$44(a0), sonic3k.asm:193439) and by the arc grab sub_8D94A
        // (sonic3k.asm:193477), and never cleared afterwards: the wall and floor
        // release paths (loc_8D820/loc_8D85E into loc_8D834,
        // sonic3k.asm:193337-193346 and 193363-193367) only set Status_InAir,
        // clear object_control and drop the arm to routine 6. The stale
        // back-reference is what loc_8D724 (sonic3k.asm:193222-193228) re-uses
        // when the arm later scrolls off-camera, so it must outlive
        // capturedPlayer rather than be nulled at release.
        private AbstractPlayableSprite releaseTargetPlayer;
        // Player recorded by this frame's TouchResponse pass (the engine equivalent
        // of collision_property). Applied during the arm's own update, so only the
        // last player to overlap is grabbed and any earlier overlapping player
        // (e.g. the human-controlled lead) is never modified. This is per-frame
        // scratch state: it is cleared at the start of every update (before any
        // rewind snapshot boundary), so it never needs to be captured for rewind.
        // Its non-capture disposition lives centrally in DefaultObjectRewindPolicies
        // (TRANSIENT) rather than as a per-object rewind annotation.
        private AbstractPlayableSprite pendingCapturePlayer;
        private int priorityBucket = PRIORITY_BUCKET;
        private int mappingFrame = SIDE_CHILD_FRAMES[0];
        private int animFrame;
        private int animTimer;

        private SideDrillChild(int x, int y, boolean facingLeft) {
            super(new ObjectSpawn(x, y, 0, 0, facingLeft ? 0 : 1, false, 0), "MadmoleSideDrill");
            this.currentX = x;
            this.currentY = y;
            this.facingLeft = facingLeft;
        }

        SideDrillChild(int x, int y, int ignoredSubtype, boolean facingLeft) {
            this(x, y, facingLeft);
        }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            ObjectSpawn spawn = ctx.spawn();
            return new SideDrillChild(spawn.x(), spawn.y(), spawn.renderFlags() == 0);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!initialized) {
                initializeMotion();
                updateDynamicSpawn(currentX, currentY);
                return;
            }

            // TouchResponse only writes collision_property. The arm consumes it
            // later in its own SST slot: sub_8D8E6 for a straight drill or
            // sub_8D94A for an arcing drill (sonic3k.asm:193250-193266,
            // 193427-193452). Apply that pending response before the arm moves.
            if (arcing) {
                applyPendingArcCapture();
            } else {
                applyPendingStraightLaunch();
            }
            if (capturedPlayer != null && !awaitingCarryRoutine) {
                // ROM routine 8 (loc_8D7A8): pin the captured player to the arm's
                // current (pre-move) x_pos/y_pos, THEN MoveSprite_LightGravity
                // advances the arm, THEN the wall-impact check runs on the moved
                // arm.
                carryCapturedPlayer();
                move();
                boolean releasedByWall = false;
                if (capturedPlayer != null) {
                    releasedByWall = releaseCapturedPlayerOnWallImpact();
                }
                if (!releasedByWall) {
                    // ROM loc_8D80A: with no wall ahead, a downward-moving arm runs
                    // ObjHitFloor_DoRoutine, whose $34(a0) hook is loc_8D846.
                    objHitFloorDoRoutine(this::runCarriedFloorImpact, vIntRunCount);
                }
            } else {
                // ROM routine 4 (loc_8D768/loc_8D778) before capture, and the
                // capture frame itself: MoveSprite advances the arm but the player
                // is not carried until routine 8 runs next frame.
                move();
                if (arcing && !postCaptureDrift) {
                    // ROM loc_8D778 (routine 4) also ends in ObjHitFloor_DoRoutine.
                    // Its $34(a0) hook is loc_8D794 while the arc is free-flying, but
                    // sub_8D94A installs loc_8D846 during the capture frame itself,
                    // before loc_8D778's MoveSprite_LightGravity and floor test run.
                    objHitFloorDoRoutine(capturedPlayer != null
                            ? this::runCarriedFloorImpact
                            : unused -> yVelocity = SIDE_CHILD_ARC_REBOUND_Y_VELOCITY,
                            vIntRunCount);
                }
                awaitingCarryRoutine = false;
            }
            animateRawLoop(vIntRunCount);
            updateDynamicSpawn(currentX, currentY);
            checkDeleteAndReleaseCapturedPlayer();
        }

        @Override
        public int getCollisionFlags() {
            return SIDE_CHILD_COLLISION_FLAGS;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public boolean usesS3kTouchSpecialPropertyResponse() {
            return true;
        }

        @Override
        public boolean requiresContinuousTouchCallbacks() {
            return true;
        }

        @Override
        public boolean usesCurrentTouchResponseState() {
            // ROM loc_8D6E6 runs the side-drill routine (loc_8D768/loc_8D778,
            // which move the arm via MoveSprite2 / MoveSprite_LightGravity) and
            // only THEN calls Add_SpriteToCollisionResponseList before Draw_Sprite
            // (sonic3k.asm:193231-193243). The list therefore holds the arm's
            // post-move coordinates, so the next frame's TouchResponse (which runs
            // before this object updates again) must read the current x/y, not the
            // two-frames-stale pre-update snapshot.
            return true;
        }

        @Override
        public TouchResponseProfile getTouchResponseProfile() {
            return TouchResponseProfile.fromProvider(this);
        }

        @Override
        public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
            return TouchResponseProfile.fromProvider(this, multiRegionSource);
        }

        @Override
        public int getPriorityBucket() {
            return priorityBucket;
        }

        @Override
        public int getOnScreenHalfWidth() {
            return RENDER_HALF_WIDTH;
        }

        @Override
        public int getOnScreenHalfHeight() {
            return RENDER_HALF_HEIGHT;
        }

        @Override
        public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
            if (!initialized || player == null
                    || player.getInvincibleFrames() != 0
                    || player.isObjectControlled()
                    || postCaptureDrift) {
                return;
            }

            if (arcing) {
                // Record the overlapping player as the capture candidate; the last
                // one to overlap this frame wins (ROM collision_property is
                // overwritten by each player's TouchResponse, Player_2 running
                // after Player_1). The grab itself is applied in update().
                if (player instanceof AbstractPlayableSprite sprite) {
                    pendingCapturePlayer = sprite;
                }
                return;
            }

            if (straightTouchConsumed) {
                return;
            }

            if (player instanceof AbstractPlayableSprite sprite) {
                // Player_2's later TouchResponse overwrites Player_1 in the
                // shared collision_property byte; the last overlap wins.
                pendingCapturePlayer = sprite;
            }
        }

        private void applyPendingStraightLaunch() {
            AbstractPlayableSprite player = pendingCapturePlayer;
            pendingCapturePlayer = null;
            if (player == null || straightTouchConsumed || postCaptureDrift
                    || player.getInvincibleFrames() != 0
                    || player.isObjectControlled()) {
                return;
            }

            straightTouchConsumed = true;
            int launchX = xVelocity * 2;
            player.setXSpeed((short) launchX);
            player.setGSpeed((short) launchX);
            player.setYSpeed((short) -0x200);
            player.setAir(true);
            // ROM sub_8D8E6 move.w a2,$44(a0) (sonic3k.asm:193439), the same
            // back-reference the arc grab writes. The straight knock-back does
            // not carry the player, so this is the only record the arm keeps of
            // whom to detach when loc_8D724 despawns it off-camera.
            releaseTargetPlayer = player;
            player.setAnimationId(0x1A);
            player.setSpindash(false);
            if (tryServices() != null) {
                tryServices().playSfx(Sonic3kSfx.FLIPPER.id);
            }
        }

        private void applyPendingArcCapture() {
            AbstractPlayableSprite candidate = pendingCapturePlayer;
            pendingCapturePlayer = null;
            if (candidate == null || capturedPlayer != null || postCaptureDrift) {
                return;
            }
            captureArcingPlayer(candidate);
        }

        private void captureArcingPlayer(PlayableEntity player) {
            if (!(player instanceof AbstractPlayableSprite sprite)) {
                return;
            }

            capturedPlayer = sprite;
            releaseTargetPlayer = sprite;
            // ROM sub_8D94A sets routine 8, but loc_8D778 (routine 4) still runs
            // to completion this frame without carrying; the carry (loc_8D7A8)
            // starts next frame.
            awaitingCarryRoutine = true;
            priorityBucket = 0;
            sprite.setAir(true);
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(sprite);
            sprite.setAnimationId(0x1A);
            sprite.setSpindash(false);
            if (tryServices() != null) {
                tryServices().playSfx(Sonic3kSfx.FLIPPER.id);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = tryServices() != null ? tryServices().renderManager() : null;
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.MADMOLE);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, !facingLeft, false);
        }

        private void initializeMotion() {
            initialized = true;
            int random = tryServices() != null && tryServices().rng() != null
                    ? tryServices().rng().nextRaw()
                    : 0;
            arcing = (random & 0x80) != 0;
            xVelocity = arcing ? SIDE_CHILD_ARC_X_VELOCITY : SIDE_CHILD_STRAIGHT_X_VELOCITY;
            yVelocity = arcing ? SIDE_CHILD_ARC_Y_VELOCITY : 0;
            if (!facingLeft) {
                xVelocity = -xVelocity;
            }
        }

        private void move() {
            SubpixelMotion.State state = new SubpixelMotion.State(
                    currentX, currentY, xSubpixel, ySubpixel, xVelocity, yVelocity);
            if (arcing && !postCaptureDrift) {
                SubpixelMotion.moveSprite(state, SIDE_CHILD_LIGHT_GRAVITY);
                yVelocity = state.yVel;
            } else {
                SubpixelMotion.moveSprite2(state);
            }
            currentX = state.x;
            currentY = state.y;
            xSubpixel = state.xSub;
            ySubpixel = state.ySub;
        }

        private void carryCapturedPlayer() {
            if (capturedPlayer == null) {
                return;
            }
            if (!capturedPlayer.isObjectControlled()) {
                enterPostCaptureDrift();
                return;
            }

            int xOffset = xVelocity < 0 ? -8 : 8;
            // ROM loc_8D7D4 does move.w to x_pos(a1)/y_pos(a1), which leaves the
            // captured player's subpixel words untouched. Preserve them here so a
            // carried CPU keeps its ROM x_sub/y_sub (e.g. F600/2E00).
            NativePositionOps.writeXPosPreserveSubpixel(capturedPlayer, currentX + xOffset);
            NativePositionOps.writeYPosPreserveSubpixel(capturedPlayer, currentY + 8);
        }

        /**
         * ROM {@code ObjHitFloor_DoRoutine} (sonic3k.asm:177964-177981): only a
         * downward-moving object probes the floor, and the {@code $34(a0)} hook
         * runs after the object is snapped onto the surface.
         */
        private void objHitFloorDoRoutine(IntConsumer onImpact, int vIntRunCount) {
            if (yVelocity < 0) {
                return;
            }
            TerrainCheckResult floor =
                    ObjectTerrainUtils.checkFloorDist(currentX, currentY, SIDE_CHILD_Y_RADIUS);
            if (floor == null || !floor.hasCollision()) {
                return;
            }
            currentY += floor.distance();
            onImpact.accept(vIntRunCount);
        }

        private boolean releaseCapturedPlayerOnWallImpact() {
            TerrainCheckResult wall = xVelocity >= 0
                    ? ObjectTerrainUtils.checkRightWallDist(currentX + SIDE_CHILD_CAPTURE_WALL_SENSOR_OFFSET, currentY)
                    : ObjectTerrainUtils.checkLeftWallDist(currentX - SIDE_CHILD_CAPTURE_WALL_SENSOR_OFFSET, currentY);
            if (!wall.hasCollision()) {
                return false;
            }

            int reboundVelocity = -xVelocity;
            capturedPlayer.setXSpeed((short) reboundVelocity);
            xVelocity = reboundVelocity >> 1;
            capturedPlayer.setAir(true);
            ObjectControlState.none().applyTo(capturedPlayer);
            enterPostCaptureDrift();
            return true;
        }

        /**
         * ROM {@code loc_8D6E6} (sonic3k.asm:193218-193243): after the routine
         * dispatch, the arm tests a coarse horizontal band
         * ({@code (x_pos & $FF80) - Camera_X_pos_coarse_back > $280}) and a
         * vertical band ({@code y_pos - Camera_Y_pos + $80 > $200}), both
         * unsigned. Either one falls through to {@code loc_8D724}, which sets
         * {@code Status_InAir} and clears {@code object_control} on the player in
         * {@code $44(a0)} — leaving its velocities untouched — and deletes the arm.
         *
         * <p>{@code loc_8D724} tests {@code $44(a0)} itself, not whether the arm is
         * still carrying. Because nothing ever clears {@code $44}, an arm that
         * already released its player (routine 6 drift) still detaches that player
         * when it finally scrolls off-camera. Measured on hardware at MHZ
         * complete-run frame 3246: slot 20 (code {@code $0008D6E6}, routine 6,
         * {@code $44 = $B000}) despawns and sets {@code Status_InAir} on a Sonic
         * who is running normally with {@code object_control = 0}, immediately
         * after {@code Player_AnglePos} returned him grounded. See
         * docs/architecture/validation/trace/2026-08-01-mhz-f3246-findfloor-probe.md.
         */
        private void checkDeleteAndReleaseCapturedPlayer() {
            int cameraX = cameraLeft();
            int cameraY = cameraTop();
            int coarseBack = ((cameraX & 0xFFFF) - 0x80) & 0xFF80;
            int bandX = ((currentX & 0xFF80) - coarseBack) & 0xFFFF;
            int bandY = ((currentY - cameraY) + 0x80) & 0xFFFF;
            if (bandX <= SIDE_CHILD_OFFSCREEN_BAND_X && bandY <= SIDE_CHILD_OFFSCREEN_BAND_Y) {
                return;
            }

            if (releaseTargetPlayer != null) {
                releaseTargetPlayer.setAir(true);
                ObjectControlState.none().applyTo(releaseTargetPlayer);
                capturedPlayer = null;
                releaseTargetPlayer = null;
            }
            setDestroyedByOffscreen();
        }

        private void animateRawLoop(int vIntRunCount) {
            animTimer--;
            if (animTimer >= 0) {
                return;
            }

            animFrame++;
            if (animFrame >= SIDE_CHILD_FRAMES.length) {
                // ROM byte_8D9E7 terminates with $FC (AnimateRaw_Restart), so the
                // raw animation script never invokes the $34(a0) hook; the arm's
                // bounce/release is driven purely by ObjHitFloor_DoRoutine.
                animFrame = 0;
                mappingFrame = SIDE_CHILD_FRAMES[0];
                animTimer = RAW_ANIMATION_DELAY;
                return;
            }

            mappingFrame = SIDE_CHILD_FRAMES[animFrame];
            animTimer = RAW_ANIMATION_DELAY;
        }

        /**
         * ROM {@code loc_8D846} (sonic3k.asm:193353-193367), installed as
         * {@code $34(a0)} by the capture in {@code sub_8D94A} and invoked from
         * {@code ObjHitFloor_DoRoutine} when the carrying arm lands.
         */
        private void runCarriedFloorImpact(int frameCounter) {
            if (yVelocity < SIDE_CHILD_ARC_RELEASE_THRESHOLD_Y_VELOCITY) {
                yVelocity = SIDE_CHILD_ARC_REBOUND_Y_VELOCITY;
                if (tryServices() != null) {
                    tryServices().playSfx(Sonic3kSfx.FLIPPER.id);
                }
                return;
            }

            capturedPlayer.setYSpeed((short) SIDE_CHILD_ARC_RELEASE_PLAYER_Y_VELOCITY);
            capturedPlayer.setXSpeed((short) xVelocity);
            capturedPlayer.releaseFromObjectControl(frameCounter);
            yVelocity = SIDE_CHILD_ARC_RELEASE_DRILL_Y_VELOCITY;
            enterPostCaptureDrift();
        }

        private void enterPostCaptureDrift() {
            postCaptureDrift = true;
            capturedPlayer = null;
        }
    }
}
