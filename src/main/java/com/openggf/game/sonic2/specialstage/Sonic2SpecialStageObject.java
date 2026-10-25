package com.openggf.game.sonic2.specialstage;

import com.openggf.physics.TrigLookupTable;

/**
 * Base class for Special Stage objects (rings, bombs, emeralds).
 *
 * Special stage objects use a depth-based perspective system where objects
 * start far away and approach the player. Their screen position is calculated
 * using perspective data that maps depth and angle to X/Y coordinates.
 *
 * Key fields (matching original disassembly):
 * - angle: Position around the track (0-255, where 0x40 is bottom center)
 * - objoff_30 (depth): Depth value that decreases as object approaches
 * - anim: Current animation index (0-9 for perspective sizes, 10 for effects)
 */
public abstract class Sonic2SpecialStageObject {

    /** Object states */
    public enum State {
        ACTIVE,
        COLLECTED,
        EXPLODING,
        REMOVED
    }

    /** Current object state */
    protected State state = State.ACTIVE;

    /** Position around the track (0-255) */
    protected int angle;

    /** Depth value as 16.16 fixed-point (high word = integer part) */
    protected long depthFixed;

    /**
     * Depth decrement values from disassembly (loc_3512A in s2.asm).
     * ROM uses FIXED values that do NOT vary with speed factor:
     * - $CCCC (52428) when SSTrack_drawing_index == 4
     * - $CCCD (52429) when SSTrack_drawing_index != 4
     *
     * The drawing index cycles at different rates based on speed factor, which naturally
     * creates different total decrements per animation frame. But since there are also
     * different numbers of animation frames per second, objects approach at the same
     * real-time rate regardless of speed factor (~48 depth units per second).
     */

    /** Screen X position (calculated from perspective data) */
    protected int screenX;

    /** Screen Y position (calculated from perspective data) */
    protected int screenY;

    /** Track floor Y position (for shadow placement) - bottom of perspective ellipse */
    protected int trackFloorY;

    /** Current animation index (0-9 for sizes, 10 for effects) */
    protected int animIndex;

    /** Animation frame within current animation */
    protected int animFrame;

    /** Animation frame delay counter */
    protected int animTimer;

    /** Whether the object is visible on screen */
    protected boolean onScreen;

    /** Whether the object should be drawn with priority */
    protected boolean highPriority;

    private Sonic2SpecialStageSlotPool nativePool;
    private Sonic2SpecialStageSlotPool.Handle nativeHandle;
    private Sonic2SpecialStageSlotPool.Handle nativeShadowHandle;
    private boolean nativeInitialized;
    protected boolean effectAnimationStarted;
    private int shadowScreenX;
    private int shadowScreenY;

    /**
     * Initializes the object with starting depth and angle.
     * @param depth Integer depth value (will be converted to fixed-point)
     * @param angle Track angle (0-255)
     */
    public void initialize(int depth, int angle) {
        // Convert integer depth to 16.16 fixed-point
        this.depthFixed = (long) depth << 16;
        this.angle = angle;
        this.animIndex = 0;
        this.animFrame = 0;
        this.animTimer = 0;
        this.state = State.ACTIVE;
        this.onScreen = false;
        this.highPriority = false;
        this.nativePool = null;
        this.nativeHandle = null;
        this.nativeShadowHandle = null;
        this.nativeInitialized = false;
        this.effectAnimationStarted = false;
        this.shadowScreenX = 0;
        this.shadowScreenY = 0;
    }

    /**
     * Updates the object state for one frame.
     *
     * @param currentTrackFrame Current track mapping frame (0-55)
     * @param trackFlipped Whether the track is flipped (left turn)
     * @param speedFactor Current speed factor from track animator
     * @param drawingIndex4 True if SSTrack_drawing_index == 4 (affects depth decrement)
     */
    public abstract void update(int currentTrackFrame, boolean trackFlipped, int speedFactor, boolean drawingIndex4);

    /**
     * Updates the screen position using perspective data.
     * Must be called each frame after depth is updated.
     */
    public void updateScreenPosition(Sonic2PerspectiveData perspectiveData, int currentTrackFrame, boolean trackFlipped) {
        int depth = getDepth();
        if (depth <= 0) {
            onScreen = false;
            return;
        }

        // Get perspective entry for current frame and depth
        Sonic2PerspectiveData.PerspectiveEntry entry =
            perspectiveData.getEntry(currentTrackFrame, depth);

        if (entry == null) {
            onScreen = false;
            return;
        }

        // Check visibility angle range
        if (!entry.isAngleVisible(angle, trackFlipped)) {
            onScreen = false;
            return;
        }

        // Calculate screen position
        int[] pos = entry.calculateScreenPosition(angle, trackFlipped);
        screenX = pos[0];
        screenY = pos[1];
        onScreen = true;

        // Calculate track floor Y position (bottom of perspective ellipse)
        // This is where shadows should be rendered, at yBase + yRadius
        trackFloorY = entry.yBase + entry.yRadius;
        updateNativeShadowScreenPosition(entry, trackFlipped);

        // Determine animation index based on depth
        // Lower depth = closer = larger sprite (higher anim index)
        // loc_3512A preserves animation $A for effect routines.
        if (state == State.ACTIVE) {
            animIndex = calculateAnimIndex();
        }
    }

    /**
     * Calculates the animation index based on current depth.
     * Returns 0-9 for perspective sizes.
     *
     * From disassembly byte_35180 lookup table - maps depth to anim index:
     * Depths 0-2: anim 9
     * Depths 3-4: anim 8
     * Depths 5-6: anim 7
     * Depths 7-8: anim 6
     * Depths 9-10: anim 5
     * ...etc
     */
    protected int calculateAnimIndex() {
        int depth = getDepth();
        // Use lookup table from disassembly (byte_35180)
        // This maps depth values (0-30) to anim indices (0-9)
        if (depth <= 0) return 9;
        if (depth >= 30) return 0;

        // Table from disassembly (depths 0-31 map to anim 9-0):
        // 9,9,9,8,8,7,7,6,6,5,5,4,4,3,3,3,2,2,2,1,1,1,1,1,1,1,1,1,1,1,0,0
        int[] animLookup = {
            9, 9, 9, 8, 8, 7, 7, 6, 6, 5, 5, 4, 4, 3, 3, 3,
            2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0
        };

        if (depth < animLookup.length) {
            return animLookup[depth];
        }
        return 0; // Furthest for very large depths
    }

    /**
     * Decrements the depth value using fixed-point math.
     *
     * From disassembly (loc_3512A in s2.asm):
     * - When SSTrack_drawing_index == 4: subtract $CCCC
     * - Otherwise: subtract $CCCD
     *
     * Note: The depth decrement is FIXED and does NOT vary with speed factor.
     * Track animation speed affects how fast the track moves visually, but
     * objects always approach at the same rate for consistent collision timing.
     *
     * @param drawingIndex4 True if SSTrack_drawing_index == 4
     * @param speedFactor Unused - kept for API compatibility but ROM doesn't scale by speed
     */
    public void decrementDepth(boolean drawingIndex4, int speedFactor) {
        if (depthFixed > 0) {
            // ROM uses FIXED depth decrement values (from loc_3512A in s2.asm):
            // - $CCCC when SSTrack_drawing_index == 4
            // - $CCCD otherwise
            //
            // The total decrement per animation frame varies with speed factor:
            // - speedFactor=12 (duration=5): 4 × $CCCD + 1 × $CCCC = 262144 (~4.0)
            // - speedFactor=6 (duration=10): 9 × $CCCD + 1 × $CCCC = 524289 (~8.0)
            //
            // But since there are fewer animation frames per second at lower speed factors,
            // the depth decrement per second is the same:
            // - speedFactor=12: 12 frames/sec × 4.0 = 48.0 depth/sec
            // - speedFactor=6: 6 frames/sec × 8.0 = 48.0 depth/sec
            //
            // This means objects approach at the same real-time rate regardless of speed factor.
            long decrement = drawingIndex4 ? 0xCCCC : 0xCCCD;
            depthFixed -= decrement;
            if (depthFixed < 0) {
                depthFixed = 0;
            }
        }
    }

    /**
     * Gets the integer depth value (high word of fixed-point).
     */
    public int getDepth() {
        return (int) (depthFixed >> 16);
    }

    /**
     * Checks if this object should be tested for collision.
     * In the original game, collision only happens when anim == 8.
     */
    public boolean isCollidable() {
        return state == State.ACTIVE && animIndex == 8;
    }

    /**
     * Checks if this object should be removed from the object list.
     */
    public boolean shouldRemove() {
        return state == State.REMOVED;
    }

    /**
     * Marks the object for removal.
     */
    public void markForRemoval() {
        state = State.REMOVED;
    }

    /** Bind a real allocation before this owner's first native RunObjects turn. */
    public final void attachNativeSlot(Sonic2SpecialStageSlotPool pool,
                                       Sonic2SpecialStageSlotPool.Handle handle) {
        if (pool == null || !pool.isLive(handle)) {
            throw new IllegalArgumentException("Object must bind a live special-stage allocation");
        }
        nativePool = pool;
        nativeHandle = handle;
    }

    /**
     * Obj60_Init/Obj61_Init fall through into movement after allocating their
     * shadow. The allocator runs once, including when allocation fails
     * ({@code Obj60_Init}/{@code Obj61_Init}, s2.asm:70795-70813,70881-70898).
     */
    public void initializeNativeOwner() {
        if (nativeInitialized || nativePool == null || !nativePool.isLive(nativeHandle)) {
            return;
        }
        nativeInitialized = true;
        nativePool.setPriority(nativeHandle, isEmerald() ? 4 : 3);
        nativePool.setRoutine(nativeHandle, 2);
        if ((angle & 0x80) == 0) {
            // loc_3529C uses the parent's ID with routine 4, not Obj63.
            int shadowComponent = angle <= 0x10 || angle > 0x70 ? 2
                    : angle <= 0x30 || angle > 0x50 ? 1 : 0;
            Sonic2SpecialStageSlotPool.Entry parent = nativePool.entry(nativeHandle);
            nativeShadowHandle = nativePool.allocateAfterCurrent(nativeHandle,
                    Sonic2SpecialStageSlotPool.Kind.OBJECT_SHADOW, parent.objectId(),
                    5, shadowComponent, nativeHandle);
            nativePool.setRoutine(nativeShadowHandle, 4);
        }
    }

    /**
     * Publish writes performed by this owner's turn. Child deletion is a flag
     * write; the child remains allocated until its own loc_3533A execution
     * ({@code loc_3507A}/{@code loc_3516C}/{@code loc_3533A},
     * s2.asm:70939-70947,71061-71069,71254-71262).
     */
    public final void updateNativeTopology() {
        if (nativePool == null || !nativePool.isLive(nativeHandle)) {
            return;
        }
        nativePool.setRoutine(nativeHandle, getNativeRoutine());
        if (state == State.COLLECTED || state == State.EXPLODING || state == State.REMOVED) {
            nativePool.markForDeletion(nativeShadowHandle);
            nativeShadowHandle = null;
        }
        if (state == State.REMOVED) {
            nativePool.release(nativeHandle);
        }
    }

    protected int getNativeRoutine() {
        if (!nativeInitialized) {
            return 0;
        }
        return state == State.COLLECTED || state == State.EXPLODING ? 6 : 2;
    }

    /** loc_34F90 tests the pre-movement integer depth on effect turns. */
    protected final void updateNativeEffectPriority() {
        if (nativePool != null && getDepth() < 4) {
            nativePool.setPriority(nativeHandle, 1);
        }
    }

    public final Sonic2SpecialStageSlotPool.Handle getNativeHandle() {
        return nativeHandle;
    }

    public final Sonic2SpecialStageSlotPool.Handle getNativeShadowHandle() {
        return nativeShadowHandle;
    }

    public final int getNativeSlot() {
        return nativeHandle == null ? -1 : nativeHandle.slot();
    }

    public final int getNativeAddress() {
        return nativeHandle == null ? -1 : nativeHandle.address();
    }

    public final int getNativePriorityBucket() {
        Sonic2SpecialStageSlotPool.Entry entry = nativePool == null ? null : nativePool.entry(nativeHandle);
        return entry == null ? (isEmerald() ? 4 : 3) : entry.priorityBucket();
    }

    /**
     * loc_35202 widens the original ellipse for the shadow after positioning
     * the owner; x uses an unsigned quarter and y a signed quarter. Emeralds
     * use the same original radii here after their separate 75% body projection.
     * See {@code loc_35202} (s2.asm:71114-71152) and {@code loc_36088}
     * (s2.asm:72496-72542).
     */
    protected final void updateNativeShadowScreenPosition(
            Sonic2PerspectiveData.PerspectiveEntry entry, boolean trackFlipped) {
        if (nativePool == null || !nativePool.isLive(nativeShadowHandle)) {
            return;
        }
        int xRadius = (short) entry.xRadius;
        int yRadius = (short) entry.yRadius;
        xRadius = (short) (xRadius + ((xRadius & 0xFFFF) >>> 2));
        yRadius = (short) (yRadius + (yRadius >> 2));
        int effectiveXBase = trackFlipped ? 0x100 - entry.xBase : entry.xBase;
        shadowScreenX = (short) (effectiveXBase + ((TrigLookupTable.cosHex(angle) * xRadius) >> 8));
        // PerspectiveEntry may have arrived through a signed-byte decoder;
        // the ROM performs one ext.w on that byte, never a second extension.
        shadowScreenY = (short) ((byte) entry.yBase + ((TrigLookupTable.sinHex(angle) * yRadius) >> 8));
    }

    public final int getNativeShadowScreenX() {
        return shadowScreenX;
    }

    public final int getNativeShadowScreenY() {
        return shadowScreenY;
    }

    public final int getNativeShadowMappingFrame() {
        Sonic2SpecialStageSlotPool.Entry shadow = nativePool == null
                ? null : nativePool.entry(nativeShadowHandle);
        if (shadow == null) {
            return -1;
        }
        // loc_3534E uses 9 for animation 0, otherwise 10 - animation.
        int size = animIndex == 0 ? 9 : (10 - animIndex) & 0xFF;
        return size * 3 + shadow.component();
    }

    public final boolean isNativeShadowXFlip() {
        return (angle & 0xFF) <= 0x30;
    }

    public final boolean isNativeShadowYFlip() {
        return false;
    }

    public final boolean isNativeShadowVisible() {
        return onScreen && nativePool != null && nativePool.isLive(nativeShadowHandle)
                && !nativePool.isPendingDeletion(nativeShadowHandle);
    }

    /** Rebind only the service; all native identities are restored from values. */
    final void bindNativePoolAfterRestore(Sonic2SpecialStageSlotPool pool) {
        nativePool = pool;
    }

    // Getters

    public State getState() {
        return state;
    }

    public int getAngle() {
        return angle;
    }

    public int getScreenX() {
        return screenX;
    }

    public int getScreenY() {
        return screenY;
    }

    public int getTrackFloorY() {
        return trackFloorY;
    }

    public int getAnimIndex() {
        return animIndex;
    }

    public int getAnimFrame() {
        return animFrame;
    }

    public boolean isOnScreen() {
        return onScreen;
    }

    public boolean isHighPriority() {
        return highPriority;
    }

    Sonic2SpecialStageSnapshot.BaseObjectSnapshot captureBaseRewindSnapshot() {
        return new Sonic2SpecialStageSnapshot.BaseObjectSnapshot(
                state,
                angle,
                depthFixed,
                screenX,
                screenY,
                trackFloorY,
                animIndex,
                animFrame,
                animTimer,
                onScreen,
                highPriority,
                nativeHandle,
                nativeShadowHandle,
                nativeInitialized,
                effectAnimationStarted,
                shadowScreenX,
                shadowScreenY);
    }

    void restoreBaseRewindSnapshot(Sonic2SpecialStageSnapshot.BaseObjectSnapshot snapshot) {
        state = snapshot.state();
        angle = snapshot.angle();
        depthFixed = snapshot.depthFixed();
        screenX = snapshot.screenX();
        screenY = snapshot.screenY();
        trackFloorY = snapshot.trackFloorY();
        animIndex = snapshot.animIndex();
        animFrame = snapshot.animFrame();
        animTimer = snapshot.animTimer();
        onScreen = snapshot.onScreen();
        highPriority = snapshot.highPriority();
        nativeHandle = snapshot.nativeHandle();
        nativeShadowHandle = snapshot.nativeShadowHandle();
        nativeInitialized = snapshot.nativeInitialized();
        effectAnimationStarted = snapshot.effectAnimationStarted();
        shadowScreenX = snapshot.shadowScreenX();
        shadowScreenY = snapshot.shadowScreenY();
        nativePool = null;
    }

    abstract Sonic2SpecialStageSnapshot.ObjectSnapshot captureRewindSnapshot();

    /**
     * Returns whether this object is a ring.
     */
    public abstract boolean isRing();

    /**
     * Returns whether this object is a bomb.
     */
    public abstract boolean isBomb();

    /**
     * Returns whether this object is an emerald.
     */
    public abstract boolean isEmerald();
}
