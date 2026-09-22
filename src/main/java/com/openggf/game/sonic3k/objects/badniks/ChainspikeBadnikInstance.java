package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * S3K SKL Obj {@code $A5} — Chainspike ({@code Obj_Chainspike}, sonic3k.asm:199132-199420).
 *
 * <p>Death Egg's charging spike robot: 6 act 1 and 12 act 2 placements. S3KL {@code $A5} is
 * {@code Obj_Batbot}, so the factory is zone-set bound the way {@code $A4}'s is.
 *
 * <p><b>It is five objects.</b> The body carries the destructible hitbox
 * ({@code ObjDat_Chainspike}, :199406-199409: priority {@code $280}, {@code $18} x {@code $C},
 * frame 0, collision flags {@code $1A}). {@code ChildObjDat_91EEC} (:199412-199420) creates four
 * children: two extendable spikes at {@code (0,+$14)} and {@code (0,-$14)} running
 * {@code loc_91D52}, and two fixed {@code 8} x {@code 8} hurt boxes at {@code (+$14,0)} and
 * {@code (-$14,0)} running {@code loc_91E5A}. The fixed pair is what a player running into the
 * badnik hits.
 *
 * <p><b>The charge is a decelerating launch, not a patrol.</b> {@code $2E(a0)} is zero out of
 * the RAM wipe and {@code SetUp_ObjAttributes} does not write it (:41043-41052), so
 * {@code Obj_Wait}'s first {@code subq.w #1} goes negative immediately (:180237-180243) and the
 * body launches on its first update in routine 2. {@code loc_91CA6} (:199180-199186) loads
 * {@code x_vel} from {@code $3E(a0)} — {@code -$1200}, negated for a {@code render_flags} bit 0
 * placement (:199172-199179) — and {@code $40(a0)} from {@code $3C(a0)} ({@code $180}), then
 * plays {@code sfx_TunnelBooster}.
 *
 * <p><b>The deceleration ramp runs down, not up.</b> Each update {@code loc_91CC2}
 * (:199188-199204) moves {@code $40(a0)} by {@code $C} <em>towards zero</em> — the {@code bmi}
 * at :199193 chooses {@code +$C} for a negative accumulator and {@code -$C} for anything else —
 * and adds the new value to {@code x_vel}. So the largest correction is the first one and it
 * shrinks by {@code $C} an update. The stop test is {@code smi d2} on that sum, inverted by the
 * sign of the original launch (:199197-199201): the body stops the moment its velocity would
 * cross zero, waits {@code (2*60)-1} updates, and negates both {@code $3E} and {@code $3C} so
 * the next charge goes the other way.
 *
 * <p><b>A player within {@code $10} px interrupts everything.</b> {@code sub_91E7E}
 * (:199353-199366) runs in both routine 2 and routine 4, and on a hit it saves the current
 * routine in {@code $3A(a0)} and the current timer in {@code $26(a0)}, switches to routine 6,
 * points the raw animation at {@code byte_91F06} and {@code $34(a0)} at {@code loc_91D12} — and
 * then does {@code addq.w #4,sp}, discarding its caller's return address so the rest of that
 * routine never runs. Routine 6 plays {@code Animate_RawGetFaster} until it ends, which sets bit
 * 1 of {@code $38(a0)}: the extend signal the two vertical spikes watch. Routine 8 waits for
 * them to clear it, routine {@code $A} counts out {@code $1F} more updates, and then the saved
 * routine and timer come back.
 */
public final class ChainspikeBadnikInstance extends AbstractS3kBadnikInstance
        implements SpawnRewindRecreatable {

    /** {@code ObjDat_Chainspike} (:199406-199409). */
    private static final int COLLISION_SIZE_INDEX = 0x1A;
    private static final int PRIORITY_BUCKET = 5;
    private static final int RENDER_HALF_WIDTH = 0x18;
    private static final int RENDER_HALF_HEIGHT = 0x0C;
    private static final int WAIT_OFFSCREEN_MARGIN = 0x20;

    /** {@code move.w #-$1200,d0} (:199172). */
    private static final int LAUNCH_X_VEL = -0x1200;
    /** {@code move.w #$180,d1} (:199173). */
    private static final int LAUNCH_RAMP = 0x180;
    /** {@code moveq #$C,d2} (:199191). */
    private static final int RAMP_STEP = 0xC;
    /** {@code move.w #(2*60)-1,$2E(a0)} (:199208). */
    private static final int REST_FRAMES = (2 * 60) - 1;
    /** {@code cmpi.w #$10,d2 / bhs} (:199355-199356). */
    private static final int REACT_RANGE = 0x10;
    /** {@code move.b #$1F,$39(a0)} (:199234). */
    private static final int RECOVER_FRAMES = 0x1F;

    /** {@code byte_91F06} (:199421-199424): delay, loop limit, then the frame list. */
    private static final int RAW_ANIM_DELAY = 5;
    private static final int RAW_ANIM_LOOPS = 6;
    private static final int[] RAW_ANIM_FRAMES = { 0, 3 };

    /** {@code Chainspike_Index} (:199140-199146). */
    private enum Routine { INIT, WAITING, CHARGING, REACTING, HOLDING, RECOVERING }

    private Routine routine = Routine.INIT;
    /** {@code $3E(a0)}: the launch velocity, negated at the end of every charge. */
    private int launchVelocity;
    /** {@code $3C(a0)}: the deceleration ramp's starting value, negated with it. */
    private int launchRamp;
    /** {@code $40(a0)}: the live ramp. */
    private int ramp;
    /** {@code $2E(a0)}: the {@code Obj_Wait} countdown. */
    private int waitTimer;
    /** {@code $26(a0)} and {@code $3A(a0)}: the timer and routine {@code sub_91E7E} saved. */
    private int savedWaitTimer;
    private Routine savedRoutine = Routine.WAITING;
    /** {@code $39(a0)}. */
    private int recoverTimer;
    /** Bit 1 of {@code $38(a0)}: the signal the vertical spikes extend on. */
    private boolean extendSignal;

    /** {@code Animate_RawGetFaster} state: {@code $2E}, {@code $2F}, anim frame and timer. */
    private int rawDelay;
    private int rawLoops;
    private int rawFrameIndex;
    private int rawFrameTimer;
    private boolean rawAnimRunning;

    private boolean waitingForOnscreen = true;
    private boolean placeholderRenderedOnscreen;

    @RewindTransient(reason = "Structural child links; each child is recreated independently "
            + "and relinks itself to the nearest live Chainspike body in recreateForRewind.")
    private final List<ChainspikeChild> children = new ArrayList<>();

    public ChainspikeBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Chainspike", Sonic3kObjectArtKeys.CHAINSPIKE,
                COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        mappingFrame = 0;
    }

    @Override
    protected void updateMovement(int vIntRunCount, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }
        // Obj_WaitOffscreen (:199133).
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
            case WAITING -> {
                // loc_91C9A :199149-199152.
                if (!reactToNearbyPlayer(playerEntity)) {
                    objWait();
                }
            }
            case CHARGING -> {
                // loc_91CC2 :199188-199204.
                if (!reactToNearbyPlayer(playerEntity)) {
                    charge();
                }
            }
            case REACTING -> animateRawGetFaster();
            case HOLDING -> {
                // loc_91D20 :199229-199234.
                if (!extendSignal) {
                    routine = Routine.RECOVERING;
                    recoverTimer = RECOVER_FRAMES;
                }
            }
            case RECOVERING -> {
                // loc_91D36 :199238-199243.
                if (--recoverTimer < 0) {
                    routine = savedRoutine;
                    waitTimer = savedWaitTimer;
                }
            }
            default -> { }
        }
        updateDynamicSpawn(currentX, currentY);
    }

    /** {@code loc_91C62} :199167-199186. */
    private void init() {
        // btst #0,render_flags(a0) / beq / neg.w d0 / neg.w d1 (:199174-199178): the placement
        // flip negates both words, so an unflipped chainspike charges towards -X.
        boolean flipped = (spawn.renderFlags() & 1) != 0;
        launchVelocity = flipped ? -LAUNCH_X_VEL : LAUNCH_X_VEL;
        launchRamp = flipped ? -LAUNCH_RAMP : LAUNCH_RAMP;
        // $2E(a0) is zero out of the RAM wipe and SetUp_ObjAttributes never writes it.
        waitTimer = 0;
        for (int index = 0; index < ChainspikeChild.CHILD_COUNT; index++) {
            final int childIndex = index;
            ChainspikeChild child = spawnChild(() -> new ChainspikeChild(spawn, this, childIndex));
            if (child != null) {
                children.add(child);
            }
        }
        routine = Routine.WAITING;
    }

    /** {@code Obj_Wait} :180237-180243, whose handler here is {@code loc_91CA6}. */
    private void objWait() {
        if (--waitTimer >= 0) {
            return;
        }
        // loc_91CA6 :199180-199186.
        routine = Routine.CHARGING;
        xVelocity = launchVelocity;
        ramp = launchRamp;
        if (tryServices() != null) {
            services().playSfx(Sonic3kSfx.TUNNEL_BOOSTER.id);
        }
    }

    /** {@code loc_91CC2} :199188-199211. */
    private void charge() {
        // moveq #$C,d2 / move.w $40(a0),d1 / bmi / neg.w d2 (:199191-199195): the ramp always
        // steps towards zero, so the first correction is the largest one.
        int step = ramp < 0 ? RAMP_STEP : -RAMP_STEP;
        ramp = (short) (ramp + step);
        int next = (short) (xVelocity + ramp);
        // smi d2 / tst.w $3E(a0) / bpl / not.b d2 (:199197-199201): the stop test is "the
        // velocity has crossed zero relative to the launch".
        boolean crossed = launchVelocity < 0 ? next >= 0 : next < 0;
        if (crossed) {
            // loc_91CF6 :199206-199211.
            routine = Routine.WAITING;
            waitTimer = REST_FRAMES;
            launchVelocity = -launchVelocity;
            launchRamp = -launchRamp;
            return;
        }
        xVelocity = next;
        moveWithVelocity();
    }

    /**
     * {@code sub_91E7E} :199353-199366. The {@code addq.w #4,sp} at :199362 throws away the
     * caller's return address, so routines 2 and 4 stop where they are on the update this
     * fires.
     */
    private boolean reactToNearbyPlayer(PlayableEntity playerEntity) {
        PlayableEntity target = closestNativePlayerByHorizontalDistance(playerEntity);
        if (target == null || findSonicTailsHorizontalDistance(target) >= REACT_RANGE) {
            return false;
        }
        savedRoutine = routine;
        savedWaitTimer = waitTimer;
        routine = Routine.REACTING;
        rawAnimRunning = false;
        return true;
    }

    /**
     * {@code Animate_RawGetFaster} :181386-181420 over {@code byte_91F06}: two frames a loop,
     * the per-frame delay counting {@code 5,4,3,2,1,0} as it wraps, then six more wraps at zero
     * before {@code $2F} reaches the script's loop limit and {@code $34(a0)} is called.
     */
    private void animateRawGetFaster() {
        if (!rawAnimRunning) {
            rawAnimRunning = true;
            rawDelay = RAW_ANIM_DELAY;
            rawLoops = 0;
            rawFrameIndex = 0;
            rawFrameTimer = 0;
        }
        if (--rawFrameTimer >= 0) {
            return;
        }
        int next = rawFrameIndex + 1;
        if (next < RAW_ANIM_FRAMES.length) {
            rawFrameIndex = next;
            mappingFrame = RAW_ANIM_FRAMES[next];
            rawFrameTimer = rawDelay;
            return;
        }
        // The script's terminator is negative, so the frame index wraps to zero.
        rawFrameIndex = 0;
        mappingFrame = RAW_ANIM_FRAMES[0];
        if (rawDelay != 0) {
            rawDelay--;
            rawFrameTimer = rawDelay;
            return;
        }
        rawFrameTimer = 0;
        if (++rawLoops < RAW_ANIM_LOOPS) {
            return;
        }
        // loc_84790's tail (:181416-181420): bclr #5,$38(a0) then jsr ($34(a0)) = loc_91D12.
        rawAnimRunning = false;
        routine = Routine.HOLDING;
        extendSignal = true;
    }

    /** Bit 1 of {@code $38(a0)}, read by the spikes in {@code loc_91D8C} (:199289-199290). */
    boolean isExtendSignalSet() {
        return extendSignal;
    }

    /** {@code bclr #1,$38(a1)} in {@code loc_91E22} (:199330-199331). */
    void clearExtendSignal() {
        extendSignal = false;
    }

    /** {@code btst #1,render_flags(a0)} of the parent: this placement's Y flip. */
    boolean isYFlipped() {
        return (spawn.renderFlags() & 2) != 0;
    }

    void attachChildForRewind(ChainspikeChild restored) {
        children.add(restored);
    }

    static ChainspikeBadnikInstance findLiveBodyForRewind(RewindRecreateContext ctx) {
        if (ctx == null || ctx.spawn() == null || ctx.objectManager() == null) {
            return null;
        }
        ChainspikeBadnikInstance best = null;
        long bestDistance = Long.MAX_VALUE;
        for (ObjectInstance instance : ctx.objectManager().getActiveObjects()) {
            if (!(instance instanceof ChainspikeBadnikInstance body) || body.isDestroyed()) {
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

    public int xVelocityForTest() {
        return xVelocity;
    }

    public int rampForTest() {
        return ramp;
    }

    public int launchVelocityForTest() {
        return launchVelocity;
    }

    public int waitTimerForTest() {
        return waitTimer;
    }

    public String routineForTest() {
        return routine.name();
    }

    public boolean extendSignalForTest() {
        return extendSignal;
    }

    public List<ChainspikeChild> childrenForTest() {
        return List.copyOf(children);
    }

    public void forceOnscreenForTest() {
        waitingForOnscreen = false;
    }

    /**
     * The four children of {@code ChildObjDat_91EEC} (:199412-199420). Indices 0 and 1 are the
     * extendable spikes at {@code (0,±$14)} running {@code loc_91D52}; indices 2 and 3 are the
     * fixed {@code 8} x {@code 8} hurt boxes at {@code (±$14,0)} running {@code loc_91E5A},
     * which only ever refresh their position and add themselves to the touch list
     * (:199344-199348).
     */
    public static final class ChainspikeChild extends AbstractObjectInstance
            implements TouchResponseProvider, RewindRecreatable {

        static final int CHILD_COUNT = 4;
        /** {@code dc.b 0,$14 / 0,-$14 / $14,0 / -$14,0} (:199414-199420). */
        private static final int[] OFFSET_X = { 0, 0, 0x14, -0x14 };
        private static final int[] OFFSET_Y = { 0x14, -0x14, 0, 0 };

        /** {@code word_91EE6} (:199410-199411): priority {@code $280}, 8 x {@code $80}, frame 2. */
        private static final int SPIKE_COLLISION_FLAGS = 0x98;
        /** {@code move.b #$18,collision_flags(a0)} (:199294, :199341). */
        private static final int SPIKE_EXTENDED_FLAGS = 0x18;
        /** {@code move.b #$98,collision_flags(a0)} (:199327, :199336). */
        private static final int FIXED_COLLISION_FLAGS = 0x98;
        private static final int FIXED_HALF_SIZE = 8;
        private static final int SPIKE_HALF_WIDTH = 8;
        private static final int SPIKE_HALF_HEIGHT = 0x80;

        /** {@code moveq #8,d0} (:199296), negated when the parent is Y-flipped. */
        private static final int EXTEND_SPEED = 8;
        /** {@code move.w #$17,$2E(a0)} (:199302). */
        private static final int EXTEND_FRAMES = 0x17;
        /** {@code move.w #$5F,$2E(a0)} (:199338). */
        private static final int RETRACT_FRAMES = 0x5F;
        /** {@code RawAni_91ED4} (:199403-199404) over {@code moveq #$18,d3} steps (:199393). */
        private static final int[] EXTENSION_FRAMES = { 7, 6, 5, 4, 2 };
        private static final int EXTENSION_STEP = 0x18;

        private enum Phase { IDLE, EXTENDING }

        @RewindTransient(reason = "Structural parent link; restored by relinking to the nearest "
                + "live Chainspike body in recreateForRewind.")
        private final ChainspikeBadnikInstance body;
        /**
         * Which of {@code ChildObjDat_91EEC}'s four entries this is. Not {@code final}: a final
         * scalar is a rewind-coverage gap by {@code TestRewindCoverageGuard}'s reckoning, and
         * this one is cheaper to capture than to justify, even though
         * {@link #recreateForRewind} already reconstructs it.
         */
        private int index;
        private Phase phase = Phase.IDLE;
        private int extendOffset;
        private int extendVelocity;
        private int extendTimer;
        private int collisionFlags;
        private int mappingFrame;

        ChainspikeChild(ObjectSpawn spawn, ChainspikeBadnikInstance body, int index) {
            super(spawn, "ChainspikeChild" + index);
            this.body = body;
            this.index = index;
            this.collisionFlags = isSpike() ? SPIKE_COLLISION_FLAGS : FIXED_COLLISION_FLAGS;
            this.mappingFrame = isSpike() ? 2 : 0;
        }

        /** Indices 0 and 1 run {@code loc_91D52}; 2 and 3 run {@code loc_91E5A}. */
        private boolean isSpike() {
            return index < 2;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (body == null || body.isDestroyed()) {
                // Child_CheckParent (:180545-180557).
                ObjectLifetimeOps.deleteNoRespawn(this);
                return;
            }
            if (isSpike()) {
                updateSpike();
            }
            updateDynamicSpawn(anchorX(), anchorY());
        }

        /** {@code loc_91D8C} / {@code loc_91DE2} :199287-199351. */
        private void updateSpike() {
            if (phase == Phase.IDLE) {
                if (!body.isExtendSignalSet()) {
                    return;
                }
                // btst #1,render_flags(a1) / sne d0 / tst.b subtype(a0) / not.b d0
                // (:199289-199293): the spike on the side the parent's Y flip points at is the
                // one that extends, and the other never does.
                boolean parentFlipped = body.isYFlipped();
                boolean thisOne = index == 0 ? !parentFlipped : parentFlipped;
                if (!thisOne) {
                    return;
                }
                phase = Phase.EXTENDING;
                collisionFlags = SPIKE_EXTENDED_FLAGS;
                extendVelocity = parentFlipped ? -EXTEND_SPEED : EXTEND_SPEED;
                extendOffset = 0;
                extendTimer = EXTEND_FRAMES;
                return;
            }
            // loc_91DE2 :199307-199318.
            extendOffset += extendVelocity;
            mappingFrame = frameForExtension(Math.abs(extendOffset));
            if (extendOffset == 0) {
                // loc_91E22 :199326-199332: fully retracted, and the parent is released.
                phase = Phase.IDLE;
                collisionFlags = SPIKE_COLLISION_FLAGS;
                mappingFrame = 2;
                body.clearExtendSignal();
                return;
            }
            // btst #1,render_flags(a0) / sne d0 / tst.w y_vel(a0) / bmi / not.b d0
            // (:199311-199316): only the outbound half looks for a floor.
            boolean outbound = extendVelocity > 0;
            if (outbound) {
                // tst.w d1 / bmi.s loc_91E46 (:199323-199324): the floor turns it around.
                if (hitFloor()) {
                    bounceBack();
                    return;
                }
                // tst.b collision_flags(a0) / beq.s loc_91E40 (:199325): a spike whose flags
                // have been cleared is the one that turns around here; a live one falls
                // through to the ordinary Obj_Wait below.
                if (collisionFlags == 0) {
                    collisionFlags = SPIKE_EXTENDED_FLAGS;
                    bounceBack();
                    return;
                }
            }
            // loc_91E1C :199320: both the inbound half and a live outbound spike fall through
            // to Obj_Wait, whose handler is loc_91E46.
            if (--extendTimer < 0) {
                bounceBack();
            }
        }

        /** {@code loc_91E46} :199338-199343. */
        private void bounceBack() {
            extendTimer = RETRACT_FRAMES;
            extendVelocity = -(extendVelocity >> 2);
        }

        /** {@code sub_91EB0} :199368-199401 over {@code RawAni_91ED4}. */
        static int frameForExtension(int distance) {
            int bound = 0;
            for (int i = 0; i < EXTENSION_FRAMES.length - 1; i++) {
                bound += EXTENSION_STEP;
                if (distance <= bound) {
                    return EXTENSION_FRAMES[i];
                }
            }
            return EXTENSION_FRAMES[0];
        }

        /**
         * {@code bsr.w ObjCheckFloorDist / tst.w d1 / bmi} (:199322-199323). The ROM probes
         * from the object's own position with {@code height_pixels} as the radius.
         */
        private boolean hitFloor() {
            var floor = com.openggf.physics.ObjectTerrainUtils.checkFloorDist(
                    anchorX(), anchorY(), SPIKE_HALF_HEIGHT);
            return floor.hasCollision() && floor.distance() < 0;
        }

        private int anchorX() {
            return body.getX() + OFFSET_X[index];
        }

        private int anchorY() {
            return body.getY() + OFFSET_Y[index] + extendOffset;
        }

        @Override
        public int getCollisionFlags() {
            return body == null || body.isDestroyed() ? 0 : collisionFlags;
        }

        @Override
        public int getCollisionProperty() {
            // move.b #-1,collision_property(a0) (:199295) while extended; SetUp_ObjAttributes3
            // leaves it zero otherwise.
            return phase == Phase.EXTENDING ? -1 : 0;
        }

        @Override
        public int getOnScreenHalfWidth() {
            return isSpike() ? SPIKE_HALF_WIDTH : FIXED_HALF_SIZE;
        }

        @Override
        public int getOnScreenHalfHeight() {
            return isSpike() ? SPIKE_HALF_HEIGHT : FIXED_HALF_SIZE;
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }

        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
            ChainspikeBadnikInstance parent = findLiveBodyForRewind(ctx);
            if (parent == null) {
                return null;
            }
            ObjectSpawn capturedSpawn = ctx.spawn() != null ? ctx.spawn() : parent.spawn;
            ChainspikeChild restored = new ChainspikeChild(capturedSpawn, parent, index);
            parent.attachChildForRewind(restored);
            return restored;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (!isSpike()) {
                // loc_91E5A's children have no mappings of their own; they are touch boxes.
                return;
            }
            ObjectRenderManager renderManager = getRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer =
                    renderManager.getRenderer(Sonic3kObjectArtKeys.CHAINSPIKE);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            // tst.b subtype(a0) / bset #1,render_flags(a0) (:199277-199279): the upper spike is
            // drawn Y-flipped.
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, index == 1);
        }

        // --- test accessors ---

        public int indexForTest() {
            return index;
        }

        public boolean isExtendingForTest() {
            return phase == Phase.EXTENDING;
        }

        public int extendOffsetForTest() {
            return extendOffset;
        }

        public int extendVelocityForTest() {
            return extendVelocity;
        }

        public int collisionFlagsForTest() {
            return collisionFlags;
        }

        public int mappingFrameForTest() {
            return mappingFrame;
        }
    }
}
