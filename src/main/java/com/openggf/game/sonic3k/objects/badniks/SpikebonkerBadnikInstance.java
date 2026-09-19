package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.SwingMotion;

import java.util.List;

/**
 * S3K SKL Obj {@code $A4} — Spikebonker ({@code Obj_Spikebonker}, sonic3k.asm:198893-199124).
 *
 * <p>Death Egg's mace robot: 7 act 1 and 11 act 2 placements. It hovers on a vertical swing,
 * patrols back and forth, and when a player comes within {@code $60} px <em>on the side it is
 * walking toward</em> it stops and slams its mace.
 *
 * <p><b>It is three objects.</b> The body carries the destructible enemy hitbox
 * ({@code ObjDat_Spikebonker}, :199117-199120: priority {@code $280}, {@code $10} x {@code $14},
 * frame 0, collision flags {@code $1A}). A pivot child hangs {@code $14} px below it, and the
 * mace head hangs off that with its own attributes ({@code word_91C26}, :199121-199123: priority
 * {@code $200}, {@code $10} x {@code $10}, frame 1, collision flags {@code $9A} — bit 7 set, so
 * the mace hurts and cannot be broken). Destroying the badnik means hitting the body.
 *
 * <p><b>The patrol is an {@code Obj_Wait} countdown, and its two phases differ.</b> Init stores
 * {@code subtype - 1} in {@code $2E(a0)} and {@code subtype * 2 - 1} in {@code $3A(a0)}
 * (:198912-198918); {@code Obj_Wait} decrements {@code $2E} and calls {@code loc_91AB0} when it
 * goes negative, which negates {@code x_vel}, flips {@code render_flags} bit 0 and reloads
 * {@code $2E} from {@code $3A}. So the first leg is {@code subtype} frames from the placement and
 * every leg after it is {@code subtype * 2} — the badnik starts at one end of its beat, not in
 * the middle of it.
 *
 * <p><b>The mace's swing is horizontal, not circular.</b> {@code MoveSprite_AngleXLookupOffset}
 * (:178670-178713) reads {@code AngleLookup_1}, whose 64 entries run 0 to {@code $C}, mirrors it
 * through the angle's top two bits, and writes the result into the mace's <em>X</em> only; its Y
 * is the pivot's. The whole assembly's vertical motion is the body's own
 * {@code Swing_UpAndDown}.
 */
public final class SpikebonkerBadnikInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {

    /** {@code ObjDat_Spikebonker} collision flags (:199120). */
    private static final int COLLISION_SIZE_INDEX = 0x1A;
    /** {@code dc.w $280} (:199119): bucket 5. */
    private static final int PRIORITY_BUCKET = 5;
    /** {@code dc.b $10,$14} (:199120): width_pixels and height_pixels. */
    private static final int RENDER_HALF_WIDTH = 0x10;
    private static final int RENDER_HALF_HEIGHT = 0x14;
    private static final int WAIT_OFFSCREEN_MARGIN = 0x20;

    /** {@code move.w #-$80,d0} (:198908), negated for a {@code render_flags} bit 0 placement. */
    private static final int WALK_SPEED = 0x80;
    /** {@code move.w #$40,d0 / move.w d0,$3E(a0) / move.w d0,y_vel(a0)} (:198926-198928). */
    private static final int SWING_PEAK = 0x40;
    /** {@code move.w #4,$40(a0)} (:198929). */
    private static final int SWING_ACCELERATION = 4;
    /** {@code cmpi.w #$60,d2 / bhs.s loc_91A88} (:198936-198937). */
    private static final int DETECT_RANGE = 0x60;

    /** Body routine index {@code Spikebonker_Index} (:198901-198903). */
    private enum Routine { INIT, PATROL, BONKING }

    private Routine routine = Routine.INIT;
    /** {@code $2E(a0)}: the {@code Obj_Wait} countdown to the next turn. */
    private int walkTimer;
    /** {@code $3A(a0)}: the countdown every leg after the first. */
    private int walkTimerReload;
    /** Bit 0 of {@code $38(a0)}: {@code Swing_UpAndDown}'s direction. */
    private boolean swingDirectionDown;
    /** Bit 3 of {@code $38(a0)}: the mace is mid-slam. */
    private boolean bonkInProgress;

    private boolean waitingForOnscreen = true;
    private boolean placeholderRenderedOnscreen;
    @RewindTransient(reason = "Structural child link; the mace is recreated independently and "
            + "relinks itself to the nearest live body in recreateForRewind.")
    private SpikebonkerMace mace;

    public SpikebonkerBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Spikebonker", Sonic3kObjectArtKeys.SPIKEBONKER,
                COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        mappingFrame = 0;
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        // Obj_WaitOffscreen (:198894): the whole object is parked until it has been drawn.
        if (waitingForOnscreen) {
            if (placeholderRenderedOnscreen) {
                waitingForOnscreen = false;
                placeholderRenderedOnscreen = false;
            }
            updateDynamicSpawn(currentX, currentY);
            return;
        }
        switch (routine) {
            case INIT -> init();
            case PATROL -> patrol(playerEntity);
            case BONKING -> {
                // loc_91AC2 (:198969-198973): nothing happens until the mace clears bit 3.
                if (!bonkInProgress) {
                    routine = Routine.PATROL;
                }
            }
            default -> { }
        }
        updateDynamicSpawn(currentX, currentY);
    }

    /** {@code loc_91A0C} :198906-198932. */
    private void init() {
        xVelocity = facingLeft ? -WALK_SPEED : WALK_SPEED;
        int subtype = spawn.subtype() & 0xFF;
        walkTimer = subtype - 1;
        walkTimerReload = subtype * 2 - 1;
        yVelocity = SWING_PEAK;
        swingDirectionDown = false;
        bonkInProgress = false;
        mace = spawnChild(() -> new SpikebonkerMace(spawn, this));
        routine = Routine.PATROL;
    }

    /** {@code loc_91A6A} :198934-198958. */
    private void patrol(PlayableEntity playerEntity) {
        PlayableEntity target = mainPlayerForDetection(playerEntity);
        if (target != null && shouldBonk(target)) {
            // loc_91A9A (:198952-198956).
            routine = Routine.BONKING;
            bonkInProgress = true;
            if (tryServices() != null) {
                services().playSfx(Sonic3kSfx.BOUNCY.id);
            }
            return;
        }
        // loc_91A88 (:198946-198949).
        SwingMotion.Result swing = SwingMotion.update(
                SWING_ACCELERATION, yVelocity, SWING_PEAK, swingDirectionDown);
        yVelocity = swing.velocity();
        swingDirectionDown = swing.directionDown();
        moveWithVelocity();
        // Obj_Wait (:180237-180243): the handler runs on the update the counter goes negative.
        if (--walkTimer < 0) {
            turnAround();
        }
    }

    /**
     * {@code sub.w x_pos(a1),d2 / cmpi.w #$60,d2 / bhs} then
     * {@code btst #0,render_flags(a0) / subq.w #2,d0 / tst.w d0 / beq} (:198934-198944).
     *
     * <p>{@code Find_OtherObject} leaves {@code d0} at 0 when the object's X is at or above the
     * player's — that is, the player is to its left — and 2 otherwise. The
     * {@code render_flags} test then subtracts 2, so the zero test passes exactly when the
     * player is on the side the badnik is walking toward. {@code Obj_Spikebonker} only ever
     * reads {@code Player_1}: the sidekick never triggers a slam.
     */
    private boolean shouldBonk(PlayableEntity target) {
        int distance = Math.abs((currentX & 0xFFFF) - (target.getCentreX() & 0xFFFF));
        if (distance >= DETECT_RANGE) {
            return false;
        }
        boolean playerIsLeft = (currentX & 0xFFFF) >= (target.getCentreX() & 0xFFFF);
        return playerIsLeft == facingLeft;
    }

    /** {@code loc_91AB0} :198960-198964. */
    private void turnAround() {
        xVelocity = -xVelocity;
        facingLeft = !facingLeft;
        walkTimer = walkTimerReload;
    }

    /** {@code lea (Player_1).w,a1} (:198935): the slam never answers to the sidekick. */
    private PlayableEntity mainPlayerForDetection(PlayableEntity updatePlayer) {
        if (tryServices() == null || services().playerQuery() == null) {
            return updatePlayer;
        }
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        return main == null ? updatePlayer : main;
    }

    /**
     * The mace is recreated by finding the nearest live body, the way every other S3K child
     * object in the engine is: a rewind restore rebuilds objects independently, so a captured
     * child cannot hold a reference to the parent it had before the restore.
     */
    private static SpikebonkerBadnikInstance findLiveBodyForRewind(RewindRecreateContext ctx) {
        if (ctx == null || ctx.spawn() == null || ctx.objectManager() == null) {
            return null;
        }
        SpikebonkerBadnikInstance best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance instance : ctx.objectManager().getActiveObjects()) {
            if (!(instance instanceof SpikebonkerBadnikInstance body) || body.isDestroyed()) {
                continue;
            }
            long dx = body.getX() - ctx.spawn().x();
            long dy = body.getY() - ctx.spawn().y();
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = body;
            }
        }
        return best;
    }

    void attachMaceForRewind(SpikebonkerMace restored) {
        this.mace = restored;
    }

    /** {@code bclr #3,$38(a1)} in {@code loc_91B56} (:199049-199053). */
    void clearBonkFlag() {
        bonkInProgress = false;
    }

    boolean isBonkInProgress() {
        return bonkInProgress;
    }

    @Override
    public void refreshPostCameraRenderState() {
        if (waitingForOnscreen) {
            placeholderRenderedOnscreen = isWithinRenderSpriteBounds(
                    WAIT_OFFSCREEN_MARGIN, WAIT_OFFSCREEN_MARGIN);
        }
    }

    @Override
    public int getCollisionFlags() {
        return waitingForOnscreen || routine == Routine.INIT ? 0 : super.getCollisionFlags();
    }

    @Override
    public int getOnScreenHalfWidth() {
        return RENDER_HALF_WIDTH;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return RENDER_HALF_HEIGHT;
    }

    // --- test accessors ---

    public int walkTimerForTest() {
        return walkTimer;
    }

    public int walkTimerReloadForTest() {
        return walkTimerReload;
    }

    public int xVelocityForTest() {
        return xVelocity;
    }

    public int yVelocityForTest() {
        return yVelocity;
    }

    public boolean isFacingLeftForTest() {
        return facingLeft;
    }

    public boolean isBonkingForTest() {
        return routine == Routine.BONKING;
    }

    public SpikebonkerMace maceForTest() {
        return mace;
    }

    /**
     * The mace head. The ROM splits this into a pivot ({@code loc_91AD2}) whose only job is to
     * carry the angle and a drawn head ({@code loc_91BA8}); the pivot has no attributes of its
     * own beyond the {@code (0,$14)} offset it refreshes at, so the two are one object here and
     * the offset is applied directly.
     */
    public static final class SpikebonkerMace extends AbstractObjectInstance
            implements TouchResponseProvider, RewindRecreatable {

        /** {@code ChildObjDat_91C2C} (:199124-199126): the pivot hangs {@code $14} px below. */
        private static final int PIVOT_Y_OFFSET = 0x14;
        /** {@code word_91C26} (:199121-199123). */
        private static final int COLLISION_FLAGS = 0x9A;
        private static final int HALF_SIZE = 0x10;
        /** {@code subq.b #8,d0} (:199010, :199042): the angle walks down by eight an update. */
        private static final int ANGLE_STEP = 8;
        /** {@code moveq #-4,d0} (:199021), negated by {@code render_flags} bit 0. */
        private static final int SLAM_SPEED = 4;
        /** {@code move.w #$1F,$2E(a0)} (:199027, :199047). */
        private static final int SLAM_FRAMES = 0x1F;
        /** {@code cmpi.b #$80,d0 / beq} (:199039-199040): the far end of the slam sweep. */
        private static final int SLAM_ANGLE_END = 0x80;

        /** {@code AngleLookup_1} (sonic3k.asm:201847-201850), 64 bytes, 0 to {@code $C}. */
        private static final int[] ANGLE_LOOKUP_1 = {
            0, 0, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 5, 5, 5, 5,
            6, 6, 6, 6, 7, 7, 7, 7, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 0xA, 0xA,
            0xA, 0xA, 0xA, 0xA, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB,
            0xC, 0xC, 0xC, 0xC, 0xC, 0xC, 0xC, 0xC, 0xC, 0xC, 0xC
        };

        /** {@code byte_91C0E} (:199115-199116): (upper bound, frame) pairs, tested with {@code bls}. */
        private static final int[][] FRAME_TABLE = {
            {0x00, 1}, {0x30, 1}, {0x50, 2}, {0xB0, 3}, {0xD0, 2}, {0xFF, 1}
        };

        private enum Phase { ORBIT, SLAM_OUT, SWEEP, SLAM_BACK }

        @RewindTransient(reason = "Structural parent link; restored by relinking to the "
                + "nearest live Spikebonker body in recreateForRewind.")
        private final SpikebonkerBadnikInstance body;
        private Phase phase = Phase.ORBIT;
        /** {@code $3C(a0)} of the drawn head. */
        private int angle;
        private int slamOffset;
        private int slamVelocity;
        private int slamTimer;
        private int mappingFrame = 1;
        private int priorityBucket = 4;
        /**
         * The engine collapses the ROM's pivot slot ({@code loc_91AEC}) and drawn-head slot
         * ({@code loc_91BA8}) into this object. Keep the preceding published head position for
         * the player-slot touch pass: S3K's collision-response list holds the drawn-head slot
         * pointer from the preceding {@code Process_Sprites} pass, before either child slot
         * advances again (sonic3k.asm:20660-20681, 199001-199098).
         */
        private int previousTouchX;
        private int previousTouchY;
        private boolean previousTouchPositionValid;

        SpikebonkerMace(ObjectSpawn spawn, SpikebonkerBadnikInstance body) {
            super(spawn, "SpikebonkerMace");
            this.body = body;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (body == null || body.isDestroyed()) {
                // Child_CheckParent (:180545-180557) deletes a child whose parent slot is gone.
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
            previousTouchX = getX();
            previousTouchY = getY();
            previousTouchPositionValid = true;
            switch (phase) {
                case ORBIT -> orbit();
                case SLAM_OUT -> slide(Phase.SWEEP);
                case SWEEP -> sweep();
                case SLAM_BACK -> slide(Phase.ORBIT);
                default -> { }
            }
            // loc_91BD4 (:199091-199098): the frame and the plane the head draws on both come
            // from the angle, and only then is the position written.
            mappingFrame = frameForAngle(angle);
            // addi.b #$40,d0 / bpl: the far half of the sweep drops behind the body.
            priorityBucket = (byte) (angle + 0x40) >= 0 ? 4 : 5;
            updateDynamicSpawn(pivotX() + horizontalOffset(), pivotY());
        }

        @Override
        public int getPreUpdateCollisionX() {
            return previousTouchPositionValid ? previousTouchX : super.getPreUpdateCollisionX();
        }

        @Override
        public int getPreUpdateCollisionY() {
            return previousTouchPositionValid ? previousTouchY : super.getPreUpdateCollisionY();
        }

        /** {@code loc_91AEC} :199001-199014. */
        private void orbit() {
            if (angle == 0 && body.isBonkInProgress()) {
                // loc_91B14 (:199019-199028).
                phase = Phase.SLAM_OUT;
                slamVelocity = body.isFacingLeftForTest() ? -SLAM_SPEED : SLAM_SPEED;
                slamTimer = SLAM_FRAMES;
                return;
            }
            angle = (angle - ANGLE_STEP) & 0xFF;
        }

        /** {@code loc_91B3E} :199033-199037, counted out by {@code Obj_Wait}. */
        private void slide(Phase next) {
            slamOffset += slamVelocity;
            if (--slamTimer < 0) {
                if (next == Phase.SWEEP) {
                    // loc_91B68 (:199057-199059) hands over to the sweep.
                    phase = Phase.SWEEP;
                } else {
                    // loc_91B56 (:199049-199053): the body is released and the orbit resumes.
                    slamOffset = 0;
                    phase = Phase.ORBIT;
                    body.clearBonkFlag();
                }
            }
        }

        /** {@code loc_91B70} :199061-199070. */
        private void sweep() {
            if (angle == SLAM_ANGLE_END) {
                // loc_91B8A (:199072-199077): reverse and slide back.
                phase = Phase.SLAM_BACK;
                slamVelocity = -slamVelocity;
                slamTimer = SLAM_FRAMES;
                return;
            }
            angle = (angle - ANGLE_STEP) & 0xFF;
        }

        /**
         * {@code MoveSprite_AngleXLookupOffset} :178670-178713. The angle's top two bits pick
         * one of four readings of the same 64-entry table, and the result is added to X only.
         */
        static int angleOffset(int angleByte) {
            int a = angleByte & 0xFF;
            return switch ((a >> 6) & 3) {
                case 0 -> ANGLE_LOOKUP_1[a];
                case 1 -> ANGLE_LOOKUP_1[0x7F - a];
                case 2 -> -ANGLE_LOOKUP_1[a & 0x3F];
                default -> -ANGLE_LOOKUP_1[0xFF - a];
            };
        }

        /** {@code sub_91BFA} :199101-199110 over {@code byte_91C0E}. */
        static int frameForAngle(int angleByte) {
            int a = angleByte & 0xFF;
            for (int[] row : FRAME_TABLE) {
                if (a <= row[0]) {
                    return row[1];
                }
            }
            return FRAME_TABLE[FRAME_TABLE.length - 1][1];
        }

        private int horizontalOffset() {
            int offset = angleOffset(angle);
            // btst #0,render_flags(a1) / neg.w d1 (:178692-178695): mirrored with the parent.
            return (body.isFacingLeftForTest() ? offset : -offset) + slamOffset;
        }

        private int pivotX() {
            return body.getX();
        }

        private int pivotY() {
            return body.getY() + PIVOT_Y_OFFSET;
        }

        @Override
        public int getCollisionFlags() {
            return body == null || body.isDestroyed() ? 0 : COLLISION_FLAGS;
        }

        /**
         * {@code word_91C26} carries no {@code collision_property} byte (:199121-199123), so
         * the mace keeps the zero {@code SetUp_ObjAttributes3} leaves: it is a hurt box, not a
         * boss with a hit counter.
         */
        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            SpikebonkerBadnikInstance parent = findLiveBodyForRewind(ctx);
            if (parent == null) {
                return null;
            }
            ObjectSpawn capturedSpawn = ctx.spawn() != null ? ctx.spawn() : parent.spawn;
            SpikebonkerMace restored = new SpikebonkerMace(capturedSpawn, parent);
            parent.attachMaceForRewind(restored);
            return restored;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public int getOnScreenHalfWidth() {
            return HALF_SIZE;
        }

        @Override
        public int getOnScreenHalfHeight() {
            return HALF_SIZE;
        }

        @Override
        public int getPriorityBucket() {
            return priorityBucket;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = getRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer =
                    renderManager.getRenderer(Sonic3kObjectArtKeys.SPIKEBONKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                    body != null && !body.isFacingLeftForTest(), false);
        }

        // --- test accessors ---

        public int angleForTest() {
            return angle;
        }

        public int mappingFrameForTest() {
            return mappingFrame;
        }

        public int slamOffsetForTest() {
            return slamOffset;
        }
    }
}
