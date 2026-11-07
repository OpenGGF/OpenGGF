package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.level.objects.contact.SolidContactState;
import com.openggf.level.objects.contact.SolidContactState.Ride;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CollisionModel;
import com.openggf.game.GameStateManager;
import com.openggf.game.rules.CollisionRules;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rules.ObjectInteractionRules;
import com.openggf.game.rules.PlayerMovementRules;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.physics.CollisionTrace;
import com.openggf.physics.NoOpCollisionTrace;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.game.PlayableEntity;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.game.GroundMode;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Applies solid-contact rules and player writes; {@link SolidContactState} owns contact memory. */
public final class ObjectSolidContactController {
    /**
     * Expected entry count for the per-player maps built in the solid path:
     * a leader plus its sidekicks. {@code IdentityHashMap}'s no-arg constructor
     * expects 21 entries and sizes a 64-slot backing table accordingly, which is
     * an order of magnitude more than these maps ever hold — and they are built
     * for every solid object every frame. Exceeding the hint is merely a resize,
     * never a correctness problem.
     */
    private static final int TEAM_SIZE_HINT = 4;
    private static final Runnable NO_DISPATCH_GUARD = () -> { };

    private static final Logger LOGGER = Logger.getLogger(ObjectSolidContactController.class.getName());
    private static final int OBJ85_ID = 0x85;
    private final ObjectManager objectManager;
    private final SolidContactState contactState;
    private boolean checkpointPushingLastFrame;
    private final Map<PlayableEntity, Integer> execStartPlayerCentreY =
            new IdentityHashMap<>(2);

    void captureExecStartPlayerCentreY(PlayableEntity player,
            List<? extends PlayableEntity> sidekicks) {
        execStartPlayerCentreY.clear();
        if (player != null) {
            execStartPlayerCentreY.put(player, (int) player.getCentreY());
        }
        for (PlayableEntity sidekick : sidekicks) {
            if (sidekick != null) {
                execStartPlayerCentreY.put(sidekick, (int) sidekick.getCentreY());
            }
        }
    }

    int getPlayerCentreYAtExecStart(PlayableEntity player) {
        Integer y = execStartPlayerCentreY.get(player);
        return y != null ? y : (player != null ? player.getCentreY() : 0);
    }

    private boolean hasAnyMultiPieceStandingBitOrSnapshot(
            PlayableEntity player, ObjectInstance instance, MultiPieceSolidProvider multiPiece) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        for (int i = 0; i < InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceCount()); i++) {
            if (contactState.hasObjectStandingBit(player, instance, i)
                    || contactState.wasObjectStandingBitSetThisFrame(player, instance, i)) {
                return true;
            }
        }
        return false;
    }

    boolean hasStandingLatch(PlayableEntity player, ObjectInstance instance) {
        return contactState.hasObjectStandingBit(player, instance);
    }

    /**
     * Mark this instance's standing bit as established (by a fresh landing) on
     * the current frame, so the same-frame airborne-rider unseat is suppressed
     * for games that gate on
     * {@link CollisionRules#solidObjectKeepsOnObjWhenJumpedOffSameFrame()}.
     * Mirrors ROM's once-per-frame SolidObjectFull evaluation: the unseat
     * (loc_1DC98 / loc_1DCF0) only observes a bit set on a prior frame.
     */
    private void markStandingBitEstablishedThisFrame(PlayableEntity player, ObjectInstance instance,
            int pieceIndex) {
        if (player != null && keepsOnObjWhenAirborneAfterSameFrameStandingContact(player, instance)) {
            contactState.markStandingBitEstablishedThisFrame(player, instance, pieceIndex);
        }
    }

    private static boolean keepsOnObjWhenJumpedOffSameFrame(PlayableEntity player) {
        CollisionRules rules = collisionRulesOrNull(player);
        return rules != null && rules.solidObjectKeepsOnObjWhenJumpedOffSameFrame();
    }

    private boolean keepsOnObjWhenAirborneAfterSameFrameStandingContact(
            PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (keepsOnObjWhenJumpedOffSameFrame(player)) {
            return true;
        }
        return instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.keepsOnObjWhenAirborneAfterSameFrameStandingContact(player));
    }

    /**
     * A fresh standing write is protected only when the game/object routine opts
     * in. Read that policy before querying the frame history: provider callbacks
     * must retain their original position and short-circuit behavior.
     * ROM RideObject_SetRide (sonic3k.asm:42067-42080), Tails_Jump's
     * loc_1504C (:28583-28598).
     */
    private boolean retainsFreshStandingContact(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        return keepsOnObjWhenAirborneAfterSameFrameStandingContact(player, instance)
                && contactState.wasStandingBitEstablishedThisFrame(player, instance, pieceIndex);
    }

    /**
     * True when the given solid object signals that its ROM solid routine does
     * not execute at all on this frame ({@link SolidObjectProvider#suppressSlopeSampleThisFrame}).
     * On such a frame the object performs no rider state change -- no sloped
     * y_pos write and no airborne-rider unseat -- exactly as ROM's
     * {@code ObjPlatformCollapse_CreateFragments} (sonic3k.asm:45439) jmps to
     * {@code Play_SFX} without falling through to {@code sub_205B6}
     * (SolidObjectTopSloped2). Used to defer the generic air-unseat paths by
     * one frame so a player who jumps on the collapse-transition frame keeps
     * Status_OnObj, matching the aiz1 trace (f3317 status 0x0E -> f3318 0x06).
     */
    private boolean suppressesSolidPassThisFrame(ObjectInstance instance, PlayableEntity player) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        return instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.defersAirborneRiderUnseatThisFrame(player));
    }

    /** Clears object-side pushing bits without changing players; see {@link SolidContactState#releaseObjectPushLatchForAllPlayers}. */
    public void releaseObjectPushLatchForAllPlayers(ObjectInstance instance) {
        contactState.releaseObjectPushLatchForAllPlayers(instance);
    }

    /** Clears object-side standing bits without releasing player rides; see {@link SolidContactState#releaseObjectStandingLatchForAllPlayers}. */
    public void releaseObjectStandingLatchForAllPlayers(ObjectInstance instance) {
        contactState.releaseObjectStandingLatchForAllPlayers(instance);
    }

    /** Expires both live object-side latch kinds while occurrence ownership is available; see {@link SolidContactState#releaseObjectLatchesForAllPlayers}. */
    void releaseObjectLatchesForAllPlayers(ObjectInstance instance) {
        contactState.releaseObjectLatchesForAllPlayers(instance);
    }

    /** Clears one player's object-side push bit and reports its previous value; see {@link SolidContactState#releaseObjectPushLatch}. */
    public boolean releaseObjectPushLatch(PlayableEntity player, ObjectInstance instance) {
        return contactState.releaseObjectPushLatch(player, instance);
    }

    boolean hasPushingLatch(PlayableEntity player) {
        return contactState.hasPushingLatch(player);
    }

    // Engine-side push ownership for the synthetic off-screen release above.
    // Not a native predicate: it decides whether that gate is standing in for a
    // solid whose tail the ROM still runs.
    private void clearPushAfterSignedControlRejection(PlayableEntity player,
            ObjectInstance instance, SolidObjectProvider provider) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        // A dispatched native full-solid helper branches from its signed
        // object_control gate to the ordinary no-contact push release.
        // S3K loc_1DFFE -> loc_1E0A2 still reads this object's pushing bit;
        // controller ownership does not suppress that tail's animation word.
        if (!InitialObjectDispatchController.afterCallback(initialDispatch, instance.isSkipSolidContactThisFrame())
                && isSignedObjectControlNewSolidContactRejected(player, instance)
                && contactState.clearObjectPushingBit(player, instance)) {
            publishSolidPushReleaseAnimationWord(player, instance);
            player.setPushing(false);
            {
                provider.setPlayerPushing(player, false);
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        }
    }

    private boolean offscreenReleaseStillOwnsWalkRunWord(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return false;
        }
        if (sprite.getPushing()) {
            return true;
        }
        if (checkpointPushingLastFrame && sprite.getPushingAtFrameStart()) {
            return true;
        }
        return instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.preservesNativePushLatchAcrossSkippedSolidCheckpoints());
    }

    // Callers reach this only after contactState.clearObjectPushingBit(player, instance) has
    // returned true, which is the native `btst d4,status(a0)` on the OBJECT's own
    // per-player pushing bit. All three games gate the Walk/Run word on that bit
    // and not on the player's own Status_Push, which their own tails clear
    // immediately afterwards: S1 Solid_NoCollision (`btst #5,obStatus(a0)`,
    // _incObj/sub SolidObject.asm:253-263), S2 SolidObject_TestClearPush
    // (s2.asm:35462-35486), S3K loc_1E0A2 (sonic3k.asm:41557-41568).
    private void publishSolidPushReleaseAnimationWord(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        ObjectInteractionRules rules = objectInteractionRulesOrNull(player);
        if (rules == null
                || !rules.solidPushReleaseWritesWalkRunAnimationWord()
                || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        if (instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile()).monitorSolidity()
                && sprite.getAir()) {
            // Mon_Solid's airborne side-contact path branches directly from
            // .push to .stoppushing. It clears both push bits without passing
            // through .checkpush and its retail walk/run animation-word write.
            // (_incObj/26, 2E Monitors and Power-Ups.asm:140-160).
            return;
        }
        int rollAnimationId = sprite.resolveAnimationId(CanonicalAnimation.ROLL);
        if (rules.solidPushReleaseSkipsWalkRunWhenRolling()
                && rollAnimationId >= 0
                && sprite.getAnimationId() == rollAnimationId) {
            // S2 SolidObject_TestClearPush and S3K loc_1E0A2 both skip Roll.
            return;
        }
        int spindashAnimationId = sprite.resolveAnimationId(CanonicalAnimation.SPINDASH);
        if (rules.solidPushReleaseSkipsWalkRunWhenSpindashing()
                && spindashAnimationId >= 0
                && sprite.getAnimationId() == spindashAnimationId) {
            // S3K loc_1E0A2 also skips Spindash ($09) before the adjacent
            // Walk/Run word write (sonic3k.asm:41543-41555).
            return;
        }
        int walkAnimationId = sprite.resolveAnimationId(CanonicalAnimation.WALK);
        // Retail S1's FixBugs=0 Solid_NoCollision path executes
        // `move.w #id_Run,obAnim(a1)` before Solid_NotPushing. Because anim and
        // prev_anim are adjacent bytes, this publishes anim=Walk ($00) and
        // prev_anim=Run ($01), restarting Walk on the next player slot without
        // changing the mapping already rendered this frame. S1 exempts nothing
        // here (its FixBugs=0 walk-jump bug), S2 exempts Roll only, and S3K
        // exempts Roll and Spindash; Hurt is in none of the exemption lists, so
        // a player hurt on the same frame a solid releases his pushing bit
        // genuinely loses the hurt animation.
        // (_incObj/sub SolidObject.asm:251-263; sub SolidWall.asm:36-51).
        if (walkAnimationId >= 0) {
            sprite.setAnimationId(walkAnimationId);
        }
        sprite.publishRunAsPreviousAnimation();
    }

    private void clearObjectStandingBitOnContinuedRideExit(PlayableEntity player, ObjectInstance instance,
            int pieceIndex, SolidObjectProvider provider) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (provider != null && InitialObjectDispatchController.afterCallback(initialDispatch, provider.clearsStandingBitOnContinuedRideExit(player))) {
            contactState.clearObjectStandingBit(player, instance, pieceIndex);
        }
    }

    void evictLatchForDestroyedSpawn(ObjectSpawn spawn) {
        contactState.evictLatchForDestroyedSpawn(spawn);
    }

    void evictLatchForDestroyedInstance(ObjectInstance instance) {
        contactState.evictLatchForDestroyedInstance(instance);
    }
    private PlayableEntity currentPlayer; // set during update() for internal use

    // ROM: objects like Obj_AIZLRZEMZRock save player velocity/anim BEFORE calling
    // SolidObjectFull, then check the saved values after. Our engine runs contact
    // resolution (which zeroes velocity, clears rolling) before onSolidContact fires.
    // Snapshot the player's pre-contact state so objects can read the "before" values.
    private short preContactXSpeed;
    private short preContactYSpeed;
    private int preContactYRadius;
    private boolean preContactRolling;
    private boolean preContactAir;
    private int preContactAnimationId;
    // S3K loc_1E198 is a distinct no-contact return: when the broad solid
    // classifier selects the top branch but the narrower width_pixels landing
    // gate rejects it, the routine returns without entering loc_1E0A2 and
    // therefore leaves the object's and player's push bits untouched.
    private boolean lastContactMissPreservesPushLatch;

    // When true, the velocity classification adjustment in resolveContactInternal is
    // skipped. Set when this pass runs AFTER movement (S1 UNIFIED post-movement pass),
    // since the player's position already reflects their velocity.
    private boolean postMovement;

    // ROM Obj70 (MTZ Cog) allocates one SST slot per tooth and runs each slot's
    // SolidObject independently in ascending allocation order (s2.asm:55039-55078,
    // 55080-55141). An earlier-slot tooth therefore side-pushes Sonic BEFORE the
    // ridden tooth's standing-bit ExitPlatform branch (loc_19896, s2.asm:35196-35214)
    // re-checks the ride bounds against the pushed x_pos. The engine resolves the
    // riding/ExitPlatform branch up front (before processMultiPieceCollision applies
    // sibling side-pushes), so for providers that opt into
    // resolvesEarlierPiecesBeforeRidingPiece() we pre-apply the earlier-slot pieces'
    // side-push here and record how many were resolved so the main multi-piece pass
    // skips re-pushing them. -1 means "no earlier-piece pre-resolution this frame".
    private int multiPieceEarlierPiecesResolvedUpTo = -1;
    private ObjectInstance multiPieceEarlierPiecesInstance;
    private boolean multiPieceEarlierPiecesPushing;

    private record ProjectedPreMovementX(int centerX, int subpixel) {}

    ObjectSolidContactController(ObjectManager objectManager) {
        this.objectManager = objectManager;
        this.contactState = new SolidContactState(new SolidContactState.Access() {
            @Override
            public boolean usesOccurrenceKey(ObjectInstance object) {
                return objectManager.usesOccurrenceSolidStateKey(object);
            }

            @Override
            public ObjectInstance restoredOccupant(ObjectSpawn spawn, int slotIndex) {
                return objectManager.findRestoredRidingObject(spawn, slotIndex);
            }

            @Override
            public Runnable captureDispatchGuard() {
                var reservation = InitialObjectDispatchController.currentReservation(objectManager);
                return reservation == null
                        ? NO_DISPATCH_GUARD
                        : () -> InitialObjectDispatchController.requireCurrent(reservation);
            }
        });
    }

    void reset() {
        contactState.reset();
    }

    void inheritRetainedSstContacts(ObjectSolidContactController previous,
            Collection<? extends ObjectInstance> carried) {
        contactState.inheritRetainedSstContacts(previous.contactState, carried);
    }

    ObjectManagerSnapshot.SolidContactState captureRewindState() {
        return contactState.captureRewindState();
    }

    ObjectManagerSnapshot.SolidContactState captureRewindState(RewindCaptureContext context) {
        return contactState.captureRewindState(context);
    }

    void restoreRewindState(ObjectManagerSnapshot.SolidContactState state) {
        contactState.restoreRewindState(state);
    }

    void restoreRewindState(ObjectManagerSnapshot.SolidContactState state, RewindCaptureContext context) {
        contactState.restoreRewindState(state, context);
    }

    void restoreRewindState(List<ObjectManagerSnapshot.SolidContactRidingEntry> entries) {
        contactState.restoreRewindState(entries);
    }

    /**
     * Resolves a restored riding or latch owner from the live placed and dynamic
     * SST occupants. The manager adds only its processed-slot exec-order fallback.
     */
    static ObjectInstance findRestoredRidingOccupant(Map<ObjectSpawn, ObjectInstance> placed,
            List<ObjectInstance> dynamic, ObjectCallbackRouter callbacks, ObjectSpawn spawn, int slotIndex) {
        // A captured slot names the exact SST occupant. Spawn lookup alone is
        // ambiguous: children such as the Rockn shell keep their parent's layout
        // spawn, so placed.get(spawn) would answer the parent instead.
        ObjectInstance occupant = restoredOccupantOfSlot(placed.values(), dynamic, callbacks, slotIndex);
        if (occupant != null && (spawn == null
                || spawn.equals(callbacks.call(occupant, occupant::getSpawn)))) {
            return occupant;
        }
        if (spawn != null) {
            ObjectInstance active = placed.get(spawn);
            if (active != null) {
                return active;
            }
            for (ObjectInstance candidate : dynamic) {
                if (candidate != null && callbacks.call(candidate, candidate::getSpawn) == spawn) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static ObjectInstance restoredOccupantOfSlot(Collection<ObjectInstance> placed,
            List<ObjectInstance> dynamic, ObjectCallbackRouter callbacks, int slotIndex) {
        if (slotIndex < 0) {
            return null;
        }
        for (Collection<ObjectInstance> group : List.of(placed, dynamic)) {
            for (ObjectInstance candidate : group) {
                if (candidate instanceof AbstractObjectInstance aoi
                        && callbacks.call(candidate, aoi::getSlotIndex) == slotIndex) {
                    return candidate;
                }
            }
        }
        return null;
    }

    boolean isRidingObject(PlayableEntity player) {
        return contactState.isRidingObject(player);
    }

    boolean isAnyPlayerRiding() {
        return contactState.isAnyPlayerRiding();
    }

    boolean isPlayerRiding(PlayableEntity player, ObjectInstance instance) {
        return contactState.isPlayerRiding(player, instance);
    }

    boolean isAnyPlayerRiding(ObjectInstance instance) {
        return contactState.isAnyPlayerRiding(instance);
    }

    void suspendForVisualDirection(ObjectInstance object) {
        releaseObjectLatchesForAllPlayers(object);
        contactState.forEachRide((player, ride) -> {
            if (ride.object() == object) {
                clearRidingObject(player);
                player.setOnObject(false);
                player.setAir(true);
                player.setPushing(false);
            }
        });
    }

    void clearRidingObject(PlayableEntity player) {
        contactState.clearRidingObject(player);
    }

    void releaseRidingObject(PlayableEntity player, ObjectInstance owner) {
        if (player == null || owner == null) {
            return;
        }
        // Object-owned launch/release routines clear their own native
        // standing bit even when SolidObjectFull skipped an offscreen P2.
        contactState.clearObjectStandingBit(player, owner);
        Ride state = contactState.rideFor(player);
        if (state != null && state.object() == owner) {
            clearRidingObject(player);
        }
    }

    /**
     * ROM {@code CheckPlayerReleaseFromObj}: while an object's native standing
     * bit is set, probe the terrain under the rider and detach it when the floor
     * distance is zero or negative.
     *
     * <p>The caller owns the native dispatch gate and phase (for example
     * MGZ2's background-collision deform pass). Keeping the contact mutation
     * here makes the operation use the same riding and standing-bit state as
     * {@code SolidObjectFull} instead of reconstructing it in a zone handler.
     */
    boolean checkPlayerReleaseFromObjectFloor(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        Ride state = contactState.rideFor(player);
        if (state == null || state.object() == null
                || !contactState.hasObjectStandingBit(player, state.object(), state.pieceIndex())) {
            return false;
        }
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(
                player.getCentreX(), player.getCentreY(), player.getYRadius());
        if (floor.distance() > 0) {
            return false;
        }

        contactState.clearObjectStandingBit(player, state.object(), state.pieceIndex());
        contactState.clearRidingObject(player);
        player.setOnObject(false);
        player.setAir(true);
        return true;
    }

    /**
     * Clears the native standing state left by an earlier solid pass when a later
     * controller object makes the player airborne in the same object-slot pass.
     */
    void clearRidingObjectAfterControllerAirborneRelease(
            PlayableEntity player, ObjectInstance formerSupport) {
        if (player == null) {
            return;
        }
        // Preserve the former support's native standing bit. Its own later
        // SolidObject slot must observe Status_InAir, clear that bit, and
        // return without treating the player as a fresh landing.
        if (formerSupport != null) {
            contactState.setObjectStandingBit(player, formerSupport);
            contactState.rememberControllerRelease(player, formerSupport);
        }
        contactState.clearRidingObject(player);
        player.setOnObject(false);
        player.setAir(true);
    }

    private void clearGroundWallSuppressionForNormalSolidSupport(PlayableEntity player, ObjectInstance instance) {
        if (!(player instanceof AbstractPlayableSprite sprite)
                || instance == null
                || !sprite.isSuppressGroundWallCollision()
                || sprite.isObjectControlled()) {
            return;
        }
        // ROM only skips CalcRoomInFront while object_control bit 6 is set
        // (sonic3k.asm:22748,27998). Normal SolidObject/MvSonicOnPtfm
        // support, including the CNZ horizontal door's SolidObjectFull call
        // (sonic3k.asm:66289-66298), does not set that bit; if object_control
        // is already clear, an engine-only stale bit-6 analogue must not
        // survive onto the next movement frame.
        sprite.setSuppressGroundWallCollision(false);
    }

    void forceRidingObjectForBootstrap(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (player == null || instance == null) {
            return;
        }
        contactState.ride(player, instance, -1);
        player.setOnObject(true);
        player.setAir(false);
        player.setYSpeed((short) 0);
        if (player instanceof AbstractPlayableSprite sprite && InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()) != null) {
            sprite.setLatchedSolidObject(InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()).objectId(), instance);
        }
        contactState.setObjectStandingBit(player, instance);
    }

    void clearRidingObjectForJump(PlayableEntity player) {
        if (player == null) {
            return;
        }
        Ride state = contactState.rideFor(player);
        if (state != null && carriesAirborneRiderAfterExitPlatform(state.object())) {
            return;
        }
        clearRidingObject(player);
    }

    void clearSameFrameMonitorBreakBounces() {
        contactState.clearSameFrameMonitorBreakBounces();
    }

    public void markSameFrameMonitorBreakBounce(PlayableEntity player) {
        contactState.markSameFrameMonitorBreakBounce(player);
    }

    ObjectInstance getRidingObject(PlayableEntity player) {
        return contactState.getRidingObject(player);
    }

    private boolean blocksSolidContacts(PlayableEntity player, ObjectInstance candidate) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        // ROM SolidObject_ChkBounds (docs/s2disasm/s2.asm:35378-35379):
        //   cmpi.b   #6,routine(a1)
        //   bhs.w    SolidObject_NoCollision
        // Skips solid-object collisions when the player's routine is >= 6
        // (Dead / Gone / Respawning). For a CPU sidekick the engine
        // equivalent of routine = 6 (Obj02_Dead, s2.asm:40736-40742) is
        // SidekickCpuController.State.DEAD_FALLING. Post-warp routine = 8
        // (Gone / TailsCPU_Despawn) and routine = $A (Respawning) are
        // already gated below via the objectControlled check (ROM obj_control
        // bit 7 is set on those entries). Without this DEAD_FALLING gate,
        // MCZ Tails (kill triggered above CollapsingPlatform s17) lands on
        // the platform at trace F443 because the engine kept running solid
        // object contacts on a dead sidekick that ROM would have skipped
        // here. Sonic 3&K uses immediate-warp (objectControlled=true on
        // kill frame N+1), so its routine = 6 phase is gated by the
        // objectControlled path below — this extra check is only needed
        // for the S2 deferred-despawn window where Tails has routine = 6
        // but obj_control bit 7 is not yet set.
        if (player instanceof AbstractPlayableSprite sprite
                && sprite.isCpuControlled()) {
            SidekickCpuController cpu = sprite.getCpuController();
            if (cpu != null
                    && cpu.getState() == SidekickCpuController.State.DEAD_FALLING) {
                return true;
            }
        }
        if (!player.isObjectControlled()) {
            return false;
        }
        if (candidate instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile()).allowsObjectControlledSolidContacts()) {
            // ROM SolidObject_ChkBounds uses a signed obj_control test before
            // side/top classification (S2 s2.asm:35376-35377). Objects that
            // deliberately allow positive obj_control states, such as Obj33's
            // lift lock, may still reject bit-7 states via an object-local hook.
            if (isSignedObjectControlNewSolidContactRejected(player, candidate)) {
                return true;
            }
            return false;
        }
        // Most object-controlled states skip SolidObject entirely. MGZ top-platform
        // carry is the narrow exception: it still consumes side/top feedback from
        // its controlling platform instance while the platform owns the player.
        return !(player instanceof AbstractPlayableSprite sprite)
                || !sprite.allowsSolidContactsWhileObjectControlled(candidate);
    }

    /** Get the piece index this player is riding, or -1 if not riding a multi-piece object. */
    int getRidingPieceIndex(PlayableEntity player) {
        return contactState.getRidingPieceIndex(player);
    }

    /** Check if the current player (set during update()) is riding the given object. Used by internal resolve methods. */
    private boolean isRidingCurrentPlayerObject(ObjectInstance instance) {
        if (currentPlayer == null) return false;
        Ride state = contactState.rideFor(currentPlayer);
        return state != null && state.object() == instance;
    }

    /** Get the piece index the current player is riding. Used by internal resolve methods. */
    private int getCurrentPlayerRidingPieceIndex() {
        if (currentPlayer == null) return -1;
        Ride state = contactState.rideFor(currentPlayer);
        return state != null ? state.pieceIndex() : -1;
    }

    /** Player X speed captured before any solid contact resolution modified it. */
    short getPreContactXSpeed() { return preContactXSpeed; }
    /** Player Y speed captured before any solid contact resolution modified it. */
    short getPreContactYSpeed() { return preContactYSpeed; }
    public int getPreContactYRadius() { return preContactYRadius; }
    /** Player rolling state captured before any solid contact resolution modified it. */
    public boolean getPreContactRolling() { return preContactRolling; }

    /** In-air state captured before the currently executing solid checkpoint. */
    public boolean getPreContactAir() { return preContactAir; }

    /** Whether a solid checkpoint for {@code player} is executing, so the pre-contact getters describe it. */
    public boolean isResolvingCheckpointFor(PlayableEntity player) {
        return player != null && currentPlayer == player;
    }
    /** Player animation ID captured before any solid contact resolution modified it. */
    int getPreContactAnimationId() { return preContactAnimationId; }

    void beginInlineFrame(PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            boolean postMovement) {
        this.postMovement = postMovement;
        contactState.beginFrame();
    }

    /**
     * Resolves a checkpoint scoped to one participant. Multi-sidekick objects ask
     * for one player's contact without disturbing the other participants', so the
     * batch must contain only the requested player.
     */
    SolidCheckpointBatch processParticipantCheckpoint(
            ObjectInstance instance, PlayableEntity player, boolean postMovement,
            SolidEntryRegisters registers) {
        return processManualCheckpoint(instance, player, java.util.List.of(), postMovement, registers);
    }

    SolidCheckpointBatch processManualCheckpoint(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement,
            SolidEntryRegisters registers) {
        return resolveCheckpointBatch(instance, player, sidekicks, postMovement, false, registers);
    }

    SolidCheckpointBatch processCompatibilityCheckpoint(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement,
            SolidEntryRegisters registers) {
        return resolveCheckpointBatch(instance, player, sidekicks, postMovement, true, registers);
    }

    private SolidCheckpointBatch resolveCheckpointBatch(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement,
            boolean compatibilityCallbacks, SolidEntryRegisters registers) {
        if (ObjectDirectionAccess.suspended(objectManager, instance))
            return new SolidCheckpointBatch(instance, java.util.Map.of());
        return objectManager.callObjectCallback(instance,
                () -> resolveCheckpointBatchInternal(instance, player, sidekicks,
                        postMovement, compatibilityCallbacks, registers));
    }

    private SolidCheckpointBatch resolveCheckpointBatchInternal(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement,
            boolean compatibilityCallbacks, SolidEntryRegisters registers) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        this.postMovement = postMovement;
        // Sized for a team, not IdentityHashMap's default expectation of 21
        // entries: the default allocates a 64-slot backing table for a map
        // that holds a leader plus its sidekicks, and this is built for every
        // solid object every frame.
        IdentityHashMap<PlayableEntity, PlayerSolidContactResult> perPlayer =
                new IdentityHashMap<>(TEAM_SIZE_HINT);
        CollisionTrace trace = collisionTrace();
        trace.onSolidCheckpointStart(instance.getClass().getSimpleName(), InitialObjectDispatchController.afterCallback(initialDispatch, instance.getX()), InitialObjectDispatchController.afterCallback(initialDispatch, instance.getY()));
        // One call's registers for every participant: the helper resolves P1,
        // then P2 and the extension sidekicks, from the same d1-d4.
        resolveCheckpointForPlayer(instance, player, perPlayer, compatibilityCallbacks, registers);
        for (PlayableEntity sidekick : sidekicks) {
            resolveCheckpointForPlayer(instance, sidekick, perPlayer, compatibilityCallbacks, registers);
        }
        SolidCheckpointBatch batch = new SolidCheckpointBatch(instance, perPlayer);
        return batch;
    }

    private void resolveCheckpointForPlayer(ObjectInstance instance, PlayableEntity player,
            IdentityHashMap<PlayableEntity, PlayerSolidContactResult> perPlayer,
            boolean compatibilityCallbacks, SolidEntryRegisters registers) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (player == null) {
            return;
        }

        currentPlayer = player;
        preContactXSpeed = player.getXSpeed();
        preContactYSpeed = player.getYSpeed();
        preContactYRadius = player.getYRadius();
        preContactRolling = player.getRolling();
        preContactAir = player.getAir();
        preContactAnimationId = player.getAnimationId();

        PreContactState preContact = new PreContactState(
                preContactXSpeed, preContactYSpeed, preContactRolling, preContactAir,
                preContactAnimationId, currentPushingState(player));
        PlayerStandingState previousStanding =
                objectManager.services().solidExecutionRegistry().previousStanding(instance, player);
        boolean outerCheckpointPushingLastFrame = checkpointPushingLastFrame;
        checkpointPushingLastFrame = previousStanding.pushing();
        SolidContact contact;
        try {
            contact = processInlineObjectForPlayerInternal(instance, player, registers);
        } finally {
            if (initialDispatch == null || initialDispatch.ownsManager())
                checkpointPushingLastFrame = outerCheckpointPushingLastFrame;
        }
        PostContactState postContact = new PostContactState(
                player.getXSpeed(), player.getYSpeed(), player.getAir(),
                player.isOnObject(), currentPushingState(player));

        if (contact == null) {
            PlayerSolidContactResult result =
                    PlayerSolidContactResult.noContact(previousStanding, preContact, postContact);
            perPlayer.put(player, result);
            if (compatibilityCallbacks && instance instanceof SolidObjectListener listener) {
                listener.onSolidContactCleared(player, contactState.frameCounter());
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        } else {
            if (player instanceof AbstractPlayableSprite sprite && player.isObjectControlled()) {
                // Publish the checkpoint while preserving ROM slot order. A later
                // controller slot may consume contact flags written by this solid.
                sprite.notifyObjectControlledSolidContact(instance, contact);
            }
            PlayerSolidContactResult result = new PlayerSolidContactResult(
                    toContactKind(contact),
                    contact.standing(),
                    previousStanding.standing(),
                    contact.pushing(),
                    previousStanding.pushing(),
                    preContact,
                    postContact,
                    contact.sideDistX());
            perPlayer.put(player, result);
            if (compatibilityCallbacks && instance instanceof SolidObjectListener listener) {
                listener.onSolidContact(player, contact, contactState.frameCounter());
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        }
        CollisionTrace trace = collisionTrace();
        String playerLabel = player.isCpuControlled() ? "sidekick" : "main";
        PlayerSolidContactResult result = perPlayer.get(player);
        trace.onSolidCheckpointResult(instance.getClass().getSimpleName(), playerLabel,
                result.kind().name(), result.standingNow(), result.standingLastFrame());
        currentPlayer = null;
    }

    private CollisionTrace collisionTrace() {
        ObjectServices services = objectManager.services();
        if (services.collisionSystem() == null) {
            return NoOpCollisionTrace.INSTANCE;
        }
        return services.collisionSystem().getTrace();
    }

    /** Completes an earlier controller's airborne release without stealing a later ride. */
    private boolean finishControllerAirborneRelease(PlayableEntity player, ObjectInstance instance) {
        if (!contactState.consumeControllerRelease(player, instance)) {
            return false;
        }
        Ride currentSupport = contactState.rideFor(player);
        boolean relandedOnDifferentSupport = currentSupport != null
                && currentSupport.object() != null
                && currentSupport.object() != instance;
        if (contactState.hasObjectStandingBit(player, instance)) {
            contactState.rememberAndClearStandingBit(player, instance, -1);
        }
        if (!relandedOnDifferentSupport) {
            contactState.clearRidingObject(player);
            player.setOnObject(false);
            player.setAir(true);
        }
        return true;
    }

    /**
     * Only the ridden object's helper may release this ride. Other slots leave
     * player ownership intact. ROM SolidObject loc_1975A (s2.asm:35041-35045),
     * SolidObjectFull_1P loc_1DC98 (sonic3k.asm:41075-41079), and S1
     * .exitedPlatform (_incObj/sub ExitPlatform.asm:21-34).
     */
    private boolean unseatAirborneRiderAtOwnObject(PlayableEntity player, ObjectInstance instance,
            ObjectInstance ridingObject, int ridingPieceIndex, boolean preserveEarlierPieceRide) {
        if (ridingObject == null || !player.getAir()
                || instance != ridingObject
                || preserveEarlierPieceRide
                || carriesAirborneRiderAfterExitPlatform(ridingObject)
                || shouldSkipRidingAirUnseatForOffscreenSidekick(player, ridingObject)
                || retainsFreshStandingContact(player, ridingObject, ridingPieceIndex)
                || suppressesSolidPassThisFrame(ridingObject, player)) {
            return false;
        }
        contactState.clearRidingObject(player);
        player.setOnObject(false);
        return true;
    }

    /**
     * Consume the object's stale airborne standing bit, retaining its entry value
     * for the later lift gate. This transition does not release a player's ride.
     * ROM loc_1DCF0 (sonic3k.asm:41119-41124).
     */
    private boolean consumeAirborneStandingBit(PlayableEntity player, ObjectInstance instance) {
        if (instance == null || !player.getAir()
                || !contactState.hasObjectStandingBit(player, instance)
                || shouldSkipRidingAirUnseatForOffscreenSidekick(player, instance)
                || retainsFreshStandingContact(player, instance, -1)
                || suppressesSolidPassThisFrame(instance, player)) {
            return false;
        }
        contactState.rememberAndClearStandingBit(player, instance, -1);
        return true;
    }

    private enum StandingEntryResult {
        CONTINUE,
        OWN_RIDE_RELEASED,
        NO_CONTACT
    }

    /**
     * Resolve the standing helper's entry before fresh-contact geometry. The
     * player's ride and the object's standing bit are separate transitions;
     * their combined result decides whether this helper may continue.
     *
     * <p>Keep the original dispatch reservation across both transitions. Provider
     * callbacks remain lazy, in own-ride, object-bit, then no-contact order.
     */
    private StandingEntryResult enterStandingHelper(PlayableEntity player, ObjectInstance instance,
            ObjectInstance ridingObject, int ridingPieceIndex,
            InitialObjectDispatchController.Reservation initialDispatch) {
        boolean preserveEarlierPieceRide = shouldResolveEarlierMultiPieceSiblingsBeforeRidingPiece(
                player, ridingObject, ridingPieceIndex);
        boolean releasedOwnRide = unseatAirborneRiderAtOwnObject(
                player, instance, ridingObject, ridingPieceIndex, preserveEarlierPieceRide);
        if (consumeAirborneStandingBit(player, instance)
                && instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch,
                        provider.airborneStaleStandingBitReturnsNoContact(player))
                && !usesPieceScopedStandingBits(instance)) {
            // Full-solid loc_1DC98 returns d4=0 after clearing stale standing
            // (docs/skdisasm/sonic3k.asm:41057-41075; docs/s2disasm/s2.asm:34831-34849).
            // Capture/held-rider providers keep their ordinary continuation.
            player.setOnObject(false);
            player.setAir(true);
            return StandingEntryResult.NO_CONTACT;
        }
        // An own-object airborne exit cannot land again in the same helper.
        // Separate piece slots and neighbouring objects still get their turn:
        // loc_1DC98 (sonic3k.asm:41075-41080), loc_1DCF0 (:41124-41129),
        // loc_1E2E0 (:41852-41857).
        if (releasedOwnRide && !usesPieceScopedStandingBits(instance)) {
            return StandingEntryResult.NO_CONTACT;
        }
        return releasedOwnRide ? StandingEntryResult.OWN_RIDE_RELEASED : StandingEntryResult.CONTINUE;
    }

    private SolidContact processInlineObjectForPlayerInternal(ObjectInstance instance, PlayableEntity player,
            SolidEntryRegisters registers) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (player == null || objectManager == null) {
            return null;
        }
        if (player.getDead()) {
            clearDyingPlayerNativeSolidPush(player, instance);
            if (!clearDeadPlayerStaleStandingBit(player, instance)) {
                clearDeadPlayerOffscreenPush(player, instance);
            }
            contactState.clearRidingObject(player);
            return null;
        }
        if (player.isDebugMode()) {
            contactState.clearRidingObject(player);
            return null;
        }

        if (finishControllerAirborneRelease(player, instance)) {
            return null;
        }

        // Reset per-object/per-player multi-piece earlier-slot pre-resolution
        // tracking; set only when resolveEarlierMultiPieceSiblings runs below and
        // consumed by the multi-piece pass for this same instance.
        multiPieceEarlierPiecesResolvedUpTo = -1;
        multiPieceEarlierPiecesInstance = null;
        multiPieceEarlierPiecesPushing = false;

        Ride state = contactState.rideFor(player);
        ObjectInstance ridingObject = state != null ? state.object() : null;
        int ridingPieceIndex = state != null ? state.pieceIndex() : -1;

        StandingEntryResult entry = enterStandingHelper(
                player, instance, ridingObject, ridingPieceIndex, initialDispatch);
        if (entry == StandingEntryResult.NO_CONTACT) {
            return null;
        }
        if (entry == StandingEntryResult.OWN_RIDE_RELEASED) {
            ridingObject = null;
            ridingPieceIndex = -1;
        }

        if (!(instance instanceof SolidObjectProvider provider)) {
            return null;
        }
        SolidRoutineProfile solidProfile = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile());
        if (instance != ridingObject
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.skipsNewContactWhilePlayerAlreadyOnObject(player))
                && (ridingObject != null || player.isOnObject())) {
            // S2 PlatformObjectD5 first checks this object's own standing bit.
            // If it is clear and the player already has Status_OnObj set, the
            // helper returns d4=0 without resolving a new platform contact
            // (docs/s2disasm/s2.asm:35860-35894). That lets the current
            // riding ObjD5 remain authoritative when neighbouring elevator
            // slots overlap the rider's X range.
            return null;
        }
        if (shouldSkipOffscreenSidekickFullSolid(player, instance, solidProfile)) {
            if (instance == ridingObject) {
                // ROM returns before SolidObjectFull_1P for offscreen Player_2
                // (docs/skdisasm/sonic3k.asm:41046-41050), so the existing
                // standing bit and Status_OnObj survive without a carry step.
                contactState.ride(player, instance, ridingPieceIndex);
                contactState.setObjectStandingBit(player, instance, ridingPieceIndex);
            }
            return null;
        }

        // For multi-piece riding objects, track the riding piece re-seat Y so it can
        // be restored after sibling pieces run. In ROM, each piece occupies a separate
        // slot; earlier-slot non-riding pieces may apply SolidObject Y snaps for
        // overlapping X ranges, but the ridden piece (later slot) runs last and
        // overrides them. Mirror that order: run sibling contacts, then restore the
        // riding re-seat as the authoritative final Y.
        // docs/s1disasm/_incObj/5B SLZ Staircase.asm:72-96 (Stair_Solid / SolidObject)
        int ridingCentreYToRestore = Integer.MIN_VALUE;
        SolidContact ridingContactResult = null;

        if (instance == ridingObject) {
            // ROM Obj70 (MTZ Cog) allocates one SST slot per tooth and runs each
            // slot's SolidObject independently in ascending allocation order
            // (s2.asm:55039-55078 Obj70_Init, 55080-55141 Obj70_Main +
            // JmpTo16_SolidObject). An earlier-slot tooth therefore side-pushes the
            // rider (loc_19896 side path, s2.asm:35196-35207) BEFORE the ridden
            // tooth's standing-bit ExitPlatform branch re-checks the ride bounds
            // (loc_198B8, s2.asm:35209-35214). This engine folds all teeth into one
            // instance and resolves the ridden tooth's ExitPlatform carry
            // (processInlineRidingObject) before the sibling teeth side-push
            // (processMultiPieceCollision below), which would re-seat a rider the
            // ROM unseats. For providers that opt into
            // resolvesEarlierPiecesBeforeRidingPiece(), pre-apply the earlier-slot
            // teeth's side contact here so the ride-bounds re-check observes the
            // pushed x_pos. The multi-piece pass then skips those teeth via
            // multiPieceEarlierPiecesResolvedUpTo so a single ROM SolidObject
            // side-push per slot is not applied twice.
            if (ridingPieceIndex > 0
                    && provider instanceof MultiPieceSolidProvider earlyMulti
                    && shouldResolveEarlierMultiPieceSiblingsBeforeRidingPiece(
                            player, instance, ridingPieceIndex)
                    && InitialObjectDispatchController.afterCallback(initialDispatch, provider.isSolidFor(player))
                    && !blocksSolidContacts(player, instance)
                    && !InitialObjectDispatchController.afterCallback(initialDispatch, instance.isSkipSolidContactThisFrame())) {
                multiPieceEarlierPiecesPushing = resolveEarlierMultiPieceSiblings(
                        player, earlyMulti, instance, ridingPieceIndex,
                        contactState.frameCounter(), solidProfile.stickyContactBuffer());
                multiPieceEarlierPiecesInstance = instance;
                multiPieceEarlierPiecesResolvedUpTo = ridingPieceIndex;
            }
            SolidContact ridingContact = processInlineRidingObject(
                    player, instance, provider, registers, ridingPieceIndex);
            if (!(provider instanceof MultiPieceSolidProvider)) {
                return ridingContact;
            }
            // ROM parity for staircase-style multi-piece objects: after carrying Sonic
            // on the currently ridden piece, later sibling pieces of the same logical
            // object must still get a chance to apply side/top/bottom collision.
            // Returning early here skips the "next block in the staircase" wall hit.
            ridingContactResult = ridingContact;
            if (ridingContact != null && !player.getAir()) {
                ridingCentreYToRestore = player.getCentreY();
            }
        }

        if (InitialObjectDispatchController.afterCallback(initialDispatch, provider.skipsCpuSidekickWhenRenderFlagOffScreen())
                && player instanceof AbstractPlayableSprite sprite
                && sprite.isCpuControlled()
                && sprite.hasRenderFlagOnScreenState()
                && !sprite.isRenderFlagOnScreen()) {
            return null;
        }
        if (!InitialObjectDispatchController.afterCallback(initialDispatch, provider.isSolidFor(player))) {
            return null;
        }
        if (blocksSolidContacts(player, instance)) {
            clearPushAfterSignedControlRejection(player, instance, provider);
            return null;
        }
        if (InitialObjectDispatchController.afterCallback(initialDispatch, instance.isSkipSolidContactThisFrame())) {
            return null;
        }

        if (provider instanceof MultiPieceSolidProvider multiPiece) {
            MultiPieceContactResult result = processMultiPieceCollision(
                    player, multiPiece, instance, contactState.frameCounter(), solidProfile.stickyContactBuffer());
            boolean preservedRidingPush = player instanceof AbstractPlayableSprite sprite
                    && sprite.getPushing()
                    && result.standing()
                    && InitialObjectDispatchController.afterCallback(initialDispatch, provider.preservesRidingPushStatus(player));
            if (result.pushing()
                    || (instance == multiPieceEarlierPiecesInstance && multiPieceEarlierPiecesPushing)
                    || preservedRidingPush) {
                player.setPushing(true);
                contactState.setObjectPushingBit(player, instance);
                {
                    provider.setPlayerPushing(player, true);
                    InitialObjectDispatchController.requireCurrent(initialDispatch);
                }
            } else if (contactState.clearObjectPushingBit(player, instance)) {
                boolean foldedSiblingNoCollisionRelease = result.standing()
                        && result.aggregateContact() != null
                        && !result.aggregateContact().touchSide();
                if (result.aggregateContact() == null || foldedSiblingNoCollisionRelease) {
                    publishSolidPushReleaseAnimationWord(player, instance);
                }
                player.setPushing(false);
                {
                    provider.setPlayerPushing(player, false);
                    InitialObjectDispatchController.requireCurrent(initialDispatch);
                }
            }
            if (result.standing() && ridingCentreYToRestore == Integer.MIN_VALUE) {
                // Only update riding state from the sibling pass when there is no
                // pre-existing ride from processInlineRidingObject. When the player
                // is already riding one piece, the ridden piece's re-seat is the
                // authoritative result (see ridingCentreYToRestore restore below).
                contactState.ride(player, instance, result.pieceIndex());
                contactState.setObjectStandingBit(player, instance, result.pieceIndex());
                // Fresh multi-piece landing: latch so the same-frame
                // airborne-rider unseat is suppressed (see
                // markStandingBitEstablishedThisFrame).
                markStandingBitEstablishedThisFrame(player, instance, result.pieceIndex());
                clearGroundWallSuppressionForNormalSolidSupport(player, instance);
                preserveRidingPushStatusIfNeeded(player, instance, provider);
            }
            // Solid_ResetFloor takeover (pieceScoped multi-piece only): an adjacent
            // piece whose narrow Solid_Landed window now contains the player has
            // claimed standing and re-seated the player onto itself. Transfer the
            // riding latch to that piece and DO NOT restore the previously-ridden
            // piece's Y, mirroring ROM Solid_ResetFloor reassigning standonobject
            // (docs/s1disasm/_incObj/sub SolidObject.asm:305-375). Without this the
            // engine holds the rider on the prior piece's interpolated height
            // (SLZ staircase: piece1 at 75% vs piece0/parent at 100%), leaving the
            // rider 1px high as the descending steps deepen (SLZ1 f2872).
            if (result.overridePieceIndex() >= 0) {
                contactState.ride(player, instance, result.overridePieceIndex());
                contactState.setObjectStandingBit(player, instance, result.overridePieceIndex());
                markStandingBitEstablishedThisFrame(player, instance, result.overridePieceIndex());
                clearGroundWallSuppressionForNormalSolidSupport(player, instance);
                preserveRidingPushStatusIfNeeded(player, instance, provider);
                return result.aggregateContact();
            }
            // Restore riding piece Y after sibling-piece contacts. Non-riding sibling
            // pieces may have applied Solid_Landed Y snaps for overlapping X ranges.
            // In ROM each piece is a separate slot; the ridden piece runs LAST in slot
            // order and its MvSonicOnPtfm / SolidObject call overwrites earlier snaps.
            // docs/s1disasm/_incObj/5B SLZ Staircase.asm:72-96 (Stair_Solid / SolidObject)
            if (ridingCentreYToRestore != Integer.MIN_VALUE) {
                int newY = ridingCentreYToRestore - (player.getHeight() / 2);
                player.setY((short) newY);
                return ridingContactResult;
            }
            return result.aggregateContact();
        }

        SolidObjectParams params = registers.params(
                InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidParams()));
        boolean usePreUpdateContactPosition = InitialObjectDispatchController.afterCallback(initialDispatch, provider.usesPreUpdatePositionForSolidContact(player));
        int anchorX = (usePreUpdateContactPosition ? InitialObjectDispatchController.afterCallback(initialDispatch, instance.getPreUpdateX()) : InitialObjectDispatchController.afterCallback(initialDispatch, instance.getX()))
                + params.offsetX();
        int anchorY = (usePreUpdateContactPosition ? InitialObjectDispatchController.afterCallback(initialDispatch, instance.getPreUpdateY()) : InitialObjectDispatchController.afterCallback(initialDispatch, instance.getY()))
                + params.offsetY();
        int halfHeight = solidProfile.topSolidOnly()
                && solidProfile.usesGroundHalfHeightForTopSolidContact()
                        ? params.groundHalfHeight()
                        : params.airHalfHeight();
        boolean useStickyBuffer = solidProfile.stickyContactBuffer();
        boolean wasAirborne = player.getAir();
        boolean wasRidingObject = useStickyBuffer && isRidingCurrentPlayerObject(instance);

        // ROM parity: SolidObject_cont (s2.asm:35341-35346 SolidObject_OnScreenTest,
        // sonic3k.asm:41430-41432 loc_1DF88, s1disasm/_incObj/sub SolidObject.asm:124-126
        // Solid_ChkEnter and 86-87 SolidObject2F) gates the side/top/bottom contact
        // path on the object's render_flags bit 7. Render_Sprites clears bit 7 each
        // frame (sonic3k.asm:36378) and re-sets it only when the object's bounding
        // box overlaps the screen (sonic3k.asm:36410). When clear, ROM jumps to
        // SolidObject_TestClearPush / loc_1E0A2 which only cleans up push state and
        // exits without zeroing player ground_vel/x_vel. The S2 disasm documents
        // this as an optimisation: "if Sonic outruns the screen then he can phase
        // through solid objects".
        //
        // Without this gate, AIZ trace F2667 saw the engine zero Tails's
        // x_vel/g_speed when the camera had already scrolled past slot 22's spike
        // (camera.x=0x1C99, spike right edge at 0x1C90 -> off screen left), while
        // the ROM correctly preserved the velocity.
        //
        // Gated by CollisionRules.solidObjectOffscreenGate so we can roll out
        // the engine-wide ROM-parity behaviour incrementally without disturbing
        // existing S1/S2 trace baselines that depend on the prior (more
        // permissive) collision semantics. See typed CollisionRules definitions.
        //
        // The riding-state branch above (processInlineRidingObject) is unaffected:
        // ROM platform-ride (MvSonicOnPtfm via SolidObjectFull_1P standing branch)
        // runs BEFORE the on-screen test, so off-screen platforms still carry the
        // rider. Only the new-contact resolveContact path is gated here.
        //
        // Per-object opt-out (bypassesOffscreenSolidGate): the ROM gate at
        // loc_1DF88 lives only in SolidObjectFull_1P (sonic3k.asm:41061-41063).
        // Spring variants and several other objects route through the sibling
        // helper SolidObjectFull2_1P (sonic3k.asm:41110-41112) which falls
        // through directly to SolidObject_cont without the bit-7 test, so
        // they must continue to resolve push/side contact even when their
        // bounding box has scrolled off-screen.  Without this opt-out, the
        // AIZ trace replay F2919 horizontal spring at (0x1F39, 0x04A0)
        // failed to launch Tails because the spring's bounding box sits
        // ~0xAA px below the camera viewport at that frame.
        //
        // Top-only opt-out: ROM SolidObjectTop_1P (sonic3k.asm:41833-41859),
        // SolidObjectTopSloped_1P (sonic3k.asm:41927-41954), and
        // SolidObjectTopSloped2_1P (sonic3k.asm:41880-41907) ALL bypass
        // loc_1DF88 entirely.  When the player isn't yet standing
        // (d6,status(a0) clear), they branch directly into SolidObjCheckSloped /
        // SolidObjCheckSloped2 / loc_1E42E (sonic3k.asm:42111, 42135, 42027)
        // which do NOT test render_flags(a0). The same pattern holds in S2:
        // SlopedSolid_SingleCharacter (s2.asm:35106-35131) jumps to
        // SlopedSolid_cont (s2.asm:35263) without any on-screen test, and
        // the inline-MvSonicOnPtfm SolidObject45_alt path (s2.asm:35040)
        // also bypasses it.  The on-screen optimisation in ROM
        // ("if Sonic outruns the screen he can phase through solid objects")
        // exists only on the side-resolution path, not on the top-landing
        // path that AIZ collapsing platforms use.  Without this opt-out the
        // AIZ trace F6255 collapsing platform at (0x08B0, 0x0369) -- whose
        // 0x78-px-wide bbox right edge (0x090C) sits 0xD5 px past the
        // camera left edge (0x985) when Tails should land on it -- never
        // gets a STANDING contact for Tails, so setLatchedSolidObject for
        // slot 16 never fires and the freed-slot despawn cannot trigger.
        boolean topOnlyBypassesOffscreenGate = solidProfile.topSolidOnly();
        if (isSolidObjectOffscreenGateEnabled(player)
                && !solidProfile.bypassesOffscreenSolidGate()
                && !topOnlyBypassesOffscreenGate
                && !InitialObjectDispatchController.afterCallback(initialDispatch, instance.isWithinSolidContactBounds())) {
            // This gate is an ENGINE construct with no native counterpart: it
            // stands in for a solid whose slot the ROM would simply stop
            // dispatching. Every native no-contact branch of
            // SolidObjectFull_Offset_1P reaches loc_1E0A2 and therefore the
            // Walk/Run word (sonic3k.asm:41327-41356); an object that is not
            // executed at all reaches nothing and writes nothing. So this path
            // clears the push bookkeeping the way sub_1E0C2 would
            // (sonic3k.asm:41568-41572), but its Walk/Run word needs the
            // engine-side push ownership check below: this path conflates a
            // solid that has merely left the contact window (still dispatched by
            // the ROM, so its tail really does write) with one the ROM has
            // stopped dispatching altogether (which writes nothing). Publishing
            // unconditionally here overwrites a live routine-owned byte such as
            // a hurt $1A.
            if (contactState.clearObjectPushingBit(player, instance)) {
                if (offscreenReleaseStillOwnsWalkRunWord(player, instance)) {
                    publishSolidPushReleaseAnimationWord(player, instance);
                }
                player.setPushing(false);
                {
                    provider.setPlayerPushing(player, false);
                    InitialObjectDispatchController.requireCurrent(initialDispatch);
                }
            }
            return null;
        }

        SolidContact contact;
        SlopedSolidRoutineAdapter slopedAdapter = null;
        byte[] slopeData = null;
        lastContactMissPreservesPushLatch = false;
        if (instance instanceof SlopedSolidProvider sloped) {
            slopedAdapter = InitialObjectDispatchController.afterCallback(initialDispatch, SlopedSolidRoutineProfile.adapt(sloped));
            slopeData = InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.getSlopeData());
        }

        if (slopeData != null
                && shouldUseSlopeForContact(instance, slopedAdapter)) {
            int slopeHalfHeight = params.groundHalfHeight();
            contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(), slopeHalfHeight,
                    solidProfile.topSolidOnly(), useStickyBuffer, instance, true, slopedAdapter);
        } else {
            contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                    solidProfile, useStickyBuffer, instance, true);
        }

        if (contact == null) {
            if (lastContactMissPreservesPushLatch) {
                return null;
            }
            if (contactState.clearObjectPushingBit(player, instance)) {
                publishSolidPushReleaseAnimationWord(player, instance);
                player.setPushing(false);
                {
                    provider.setPlayerPushing(player, false);
                    InitialObjectDispatchController.requireCurrent(initialDispatch);
                }
            }
            return null;
        }
        applyNonUnifiedTopSolidLandingHeightOverride(
                player, contact, instance, solidProfile, wasAirborne, wasRidingObject, anchorY, params);
        if ((contact.standing() || contact.touchTop())
                && InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()) != null
                && player instanceof AbstractPlayableSprite sprite) {
            sprite.setLatchedSolidObject(InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()).objectId(), instance);
        }
        if (contact.pushing()) {
            player.setPushing(true);
            contactState.setObjectPushingBit(player, instance);
            {
                provider.setPlayerPushing(player, true);
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        } else if (contact.touchSide()) {
            // Solid_SideAir calls Solid_NotPushing directly when the player is
            // airborne or within four pixels of the top/bottom edge. Unlike
            // Solid_NoCollision, that entry does not execute retail S1's
            // walk-jump-bug animation-word write.
            //
            // Solid_NotPushing clears the PLAYER's pushing bit unconditionally
            // (`bclr #5,obStatus(a1)` / `bclr #status.player.pushing,status(a1)`
            // / `bclr #Status_Push,status(a1)`), with no test of whether this
            // object owns the push -- only the object's own bit clear is
            // guarded, and `bclr` on an already-clear bit is a no-op. All three
            // games agree: docs/s1disasm/_incObj/sub SolidObject.asm:246-263,
            // docs/s2disasm/s2.asm:35453-35487, docs/skdisasm/sonic3k.asm:41549,
            // 41527-41531. So the player's bit must clear here even when this
            // object never set it; a neighbouring solid that raised the push
            // this frame (S2 Obj74 blocks tile vertically, and the slot below
            // takes the four-pixel Solid_SideAir branch) is exactly the case
            // the ROM relies on to leave the player not pushing.
            contactState.clearObjectPushingBit(player, instance);
            player.setPushing(false);
            {
                provider.setPlayerPushing(player, false);
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        }
        if (contact.standing()) {
            contactState.ride(player, instance, -1);
            contactState.setObjectStandingBit(player, instance);
            // Fresh new-contact landing: latch so the same-frame airborne-rider
            // unseat is suppressed (ROM evaluates SolidObjectFull once/frame;
            // see markStandingBitEstablishedThisFrame).
            markStandingBitEstablishedThisFrame(player, instance, -1);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            if (solidProfile.dropOnFloor()) {
                TerrainCheckResult floorCheck = ObjectTerrainUtils.checkFloorDist(
                        player.getCentreX(), player.getCentreY(), player.getYRadius());
                if (floorCheck.distance() <= 0) {
                    contactState.clearRidingObject(player);
                    contactState.clearObjectStandingBit(player, instance);
                    player.setOnObject(false);
                    // Obj30 calls DropOnFloor immediately after SolidObject_Always;
                    // if ChkFloorEdge2 reports zero/negative distance, ROM clears
                    // OnObj and sets InAir for the same frame
                    // (docs/s2disasm/s2.asm:49598-49604, 36114-36128).
                    player.setAir(true);
                    return null;
                }
            }
        }
        return contact;
    }

    /**
     * The standing branch of a solid helper for the object the player rides.
     * The rider is carried by {@code x_pos(a0) - d4}, where d4 is the riding
     * slot's x when its routine was entered ({@link com.openggf.game.solid.SolidCall}).
     */
    private SolidContact processInlineRidingObject(PlayableEntity player, ObjectInstance instance,
            SolidObjectProvider provider, SolidEntryRegisters registers, int ridingPieceIndex) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        SolidRoutineProfile solidProfile = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile());
        int currentX;
        int currentY;
        SolidObjectParams params;

        if (ridingPieceIndex >= 0 && instance instanceof MultiPieceSolidProvider multiPiece) {
            currentX = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceX(ridingPieceIndex));
            currentY = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceY(ridingPieceIndex));
            params = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceParams(ridingPieceIndex));
        } else {
            currentX = InitialObjectDispatchController.afterCallback(initialDispatch, instance.getX());
            currentY = InitialObjectDispatchController.afterCallback(initialDispatch, instance.getY());
            params = registers.params(
                    InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidParams()));
        }
        int d4 = registers.d4(ridingPieceIndex);

        // The collapse-fragment tail skips its solid helper for this frame:
        // loc_20594 -> ObjPlatformCollapse_CreateFragments (sonic3k.asm:44858,
        // 45394-45434). Preserve ride/status before checking airborne exit.
        if (InitialObjectDispatchController.afterCallback(initialDispatch, provider.suppressSlopeSampleThisFrame(player))) {
            contactState.ride(player, instance, ridingPieceIndex);
            contactState.setObjectStandingBit(player, instance, ridingPieceIndex);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            preserveRidingPushStatusIfNeeded(player, instance, provider);
            return SolidContact.STANDING;
        }

        if (player.getAir()
                && retainsFreshStandingContact(player, instance, ridingPieceIndex)) {
            contactState.ride(player, instance, ridingPieceIndex);
            contactState.setObjectStandingBit(player, instance, ridingPieceIndex);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            player.setOnObject(true);
            return SolidContact.STANDING;
        }

        if (player.getAir()) {
            if (solidProfile.carriesAirborneRiderAfterExitPlatform()) {
                // Sonic 1 Obj52 MBlock_StandOn is not a normal PlatformObject
                // continued-riding path: it calls ExitPlatform, moves the block,
                // then still calls MvSonicOnPtfm2. That preserves the platform
                // delta on the jump-off frame while leaving Sonic airborne.
                applyRidingCarry(player, instance, provider, params,
                        currentX, currentY, d4);
            }
            clearObjectStandingBitOnContinuedRideExit(player, instance, ridingPieceIndex, provider);
            contactState.clearRidingObject(player);
            player.setOnObject(false);
            player.setAir(true);
            return null;
        }

        int halfWidth = params.halfWidth();
        // ROM: ExitPlatform walk-off checks use the full collision width of the
        // current solid, not the narrower Solid_Landed top-landing width.
        int ridingHalfWidth = halfWidth;

        // ROM ExitPlatform (S1 Obj18 routine 4, docs/s1disasm/_incObj/18 Platforms.asm:74-87)
        // runs BEFORE Plat_Move, so its walk-off bounds check observes the
        // platform's PRE-move x_pos (docs/s1disasm/_incObj/sub ExitPlatform.asm:20-27).
        // The rider is then carried by the post-move delta via MvSonicOnPtfm2.
        // Using the post-move currentX here drops a rider one frame early when the
        // platform moves under him (s1_syz2 trace f6845: SonicX 0x211C, platform
        // pre-move 0x20FD -> relX 0x3F stays; post-move 0x20FB -> relX 0x41 exits).
        // Objects whose solid helper runs before their body move opt in via
        // usesPreUpdatePositionForSolidContact(); the carry delta below is
        // unchanged because it is still measured against the same pre-move d4.
        int boundsRefX = InitialObjectDispatchController.afterCallback(initialDispatch, provider.usesPreUpdatePositionForSolidContact(player))
                ? d4
                : currentX;
        int boundsX = boundsRefX + params.offsetX();
        int relX = player.getCentreX() - boundsX + ridingHalfWidth;
        int stickyX = 0;
        int minRelX = -stickyX;
        int maxRelXExclusive = (ridingHalfWidth * 2) + stickyX;
        boolean inBounds = relX >= minRelX && relX < maxRelXExclusive;

        boolean objectManagedRide = inBounds
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.preservesObjectManagedRideWhileNotSolidFor(player));
        if (objectManagedRide) {
            Integer managedCentreY = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getObjectManagedRideCentreY(player, currentY, params));
            if (managedCentreY != null) {
                if (player instanceof AbstractPlayableSprite sprite) {
                    NativePositionOps.writeYPosPreserveSubpixel(sprite, managedCentreY);
                } else {
                    int newY = managedCentreY - (player.getHeight() / 2);
                    player.setY((short) newY);
                }
            }
            contactState.ride(player, instance, ridingPieceIndex);
            contactState.setObjectStandingBit(player, instance, ridingPieceIndex);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            player.setOnObject(true);
            player.setAir(false);
            return SolidContact.STANDING;
        }

        if (inBounds && InitialObjectDispatchController.afterCallback(initialDispatch, provider.isSolidFor(player)) && !blocksSolidContacts(player, instance)) {
            // MvSonicOnPtfm: x_pos(a1) -= d4 - x_pos(a0).
            int deltaX = currentX - d4;
            if (deltaX != 0 && InitialObjectDispatchController.afterCallback(initialDispatch, provider.carriesRiderOnHorizontalMove(player))) {
                player.shiftX(deltaX);
            }
            // Object-local escape hatch for ROM routines whose current body Y has
            // advanced but whose continued top ride remains seated on the prior
            // surface for this transition frame.
            int rideY = InitialObjectDispatchController.afterCallback(initialDispatch, provider.usesPreUpdateYForContinuedRide(player))
                    ? InitialObjectDispatchController.afterCallback(initialDispatch, instance.getPreUpdateY())
                    : currentY;
            // NOTE: the S3K Obj_CollapsingPlatform transition-frame skip
            // (suppressSlopeSampleThisFrame) is handled at the top of this
            // method, before the player.getAir() unseat branch, so a player
            // who jumps on the transition frame keeps Status_OnObj for that
            // one frame (matching ROM ObjPlatformCollapse_CreateFragments
            // skipping sub_205B6 entirely; sonic3k.asm:44875, 45434).
            int surfaceOffset;
            if (instance instanceof SlopedSolidProvider sloped) {
                int slopeAnchorX = currentX + params.offsetX();
                int slopeY = sampleSlopeY(player, slopeAnchorX, params.halfWidth(), sloped);
                surfaceOffset = (slopeY != Integer.MIN_VALUE) ? slopeY : params.groundHalfHeight();
            } else {
                surfaceOffset = params.groundHalfHeight();
            }
            int rideAdjustment = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getContinuedRideSnapAdjustment(player, getSolidTopYRadius(player)));
            // ROM MvSonicOnPtfm (sonic3k.asm:41688-41693): upright it computes
            // `d0 = y_pos(a0) - d3` and writes `y_pos(a1) = d0 - y_radius(a1)` (loc_1E1CA,
            // :41707-41722); under Reverse_gravity_flag it branches to loc_1E1AA, computes
            // `d0 = y_pos(a0) + d3` and writes `y_pos(a1) = d0 + y_radius(a1)` (loc_1E1F4,
            // :41730-41742). The rider hangs under the platform, both terms mirrored. The
            // unused S1 leftover at :41701 (the +/-9 branch) is unreachable and not ported.
            int rideReach = surfaceOffset + player.getYRadius() + rideAdjustment;
            int newCentreY = isReverseGravityActive(player)
                    ? rideY + params.offsetY() + rideReach
                    : rideY + params.offsetY() - rideReach;
            int newY = newCentreY - (player.getHeight() / 2);
            player.setY((short) newY);
            contactState.ride(player, instance, ridingPieceIndex);
            contactState.setObjectStandingBit(player, instance, ridingPieceIndex);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            preserveRidingPushStatusIfNeeded(player, instance, provider);

            if (solidProfile.dropOnFloor()) {
                TerrainCheckResult floorCheck = ObjectTerrainUtils.checkFloorDist(
                        player.getCentreX(), player.getCentreY(), player.getYRadius());
                if (floorCheck.distance() <= 0) {
                    contactState.clearRidingObject(player);
                    contactState.clearObjectStandingBit(player, instance, ridingPieceIndex);
                    player.setOnObject(false);
                    // ROM DropOnFloor clears OnObj and sets InAir when ChkFloorEdge2
                    // reports zero or negative distance (docs/s2disasm/s2.asm:36123-36128).
                    player.setAir(true);
                    return null;
                }
            }

            return SolidContact.STANDING;
        }

        clearObjectStandingBitOnContinuedRideExit(player, instance, ridingPieceIndex, provider);
        contactState.clearRidingObject(player);
        if (solidProfile.carriesAirborneRiderAfterExitPlatform()
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.carriesRiderOnHorizontalMove(player))) {
            // ROM S1 Obj18 routine 4 (Plat_Action2) calls ExitPlatform first --
            // which clears the on-object bit when the rider walks past the
            // pre-move edge -- but STILL runs Plat_Move then unconditionally
            // MvSonicOnPtfm2 (docs/s1disasm/_incObj/18 Platforms.asm:74-87;
            // sub MvSonicOnPtfm.asm:18-41). MvSonicOnPtfm2 does not test the
            // on-object bit, so the rider receives one final post-move carry on
            // the exit frame. Without it the rider keeps the full self-movement
            // for that frame and ends 1px right of ROM (s1_syz2 f6846: ROM x
            // 0x211F vs un-carried 0x2120).
            int exitDeltaX = currentX - d4;
            if (exitDeltaX != 0) {
                player.shiftX(exitDeltaX);
            }
            // MvSonicOnPtfm2 also re-seats the rider's Y unconditionally on the
            // exit frame: obY(rider) = platform_Y - 9 - obHeight (sub MvSonicOnPtfm
            // .asm:20-36). When the platform moved vertically on the exit frame
            // (e.g. Obj18 Plat_Nudge bob), skipping this leaves the rider one px
            // off ROM, holding the pre-move surface Y. S1 SYZ3 f3476: ROM seats the
            // rider to the platform's post-nudge Y=0x02DD -> centre 0x02C1, the
            // engine kept the pre-nudge centre 0x02C0. The sloped-exit re-seat
            // below (sampleSlopeOnRideExit) covers the slope case; this covers the
            // flat MvSonicOnPtfm2 path used by Obj18/52/59 (which all opt into
            // carriesAirborneRiderAfterExitPlatform).
            if (!(InitialObjectDispatchController.afterCallback(initialDispatch, provider.sampleSlopeOnRideExit(player)) && instance instanceof SlopedSolidProvider)) {
                int exitCentreY = currentY + params.offsetY()
                        - params.groundHalfHeight() - player.getYRadius();
                int exitNewY = exitCentreY - (player.getHeight() / 2);
                player.setY((short) exitNewY);
            }
        }
        if (InitialObjectDispatchController.afterCallback(initialDispatch, provider.sampleSlopeOnRideExit(player)) && instance instanceof SlopedSolidProvider sloped) {
            int slopeAnchorX = currentX + params.offsetX();
            int slopeY = sampleSlopeY(player, slopeAnchorX, params.halfWidth(), sloped);
            if (slopeY != Integer.MIN_VALUE) {
                int newCentreY = currentY + params.offsetY() - slopeY - player.getYRadius();
                int newY = newCentreY - (player.getHeight() / 2);
                player.setY((short) newY);
            }
        }
        player.setOnObject(false);
        if (solidProfile.forceAirOnRideExit() && !usesUnifiedCollisionModel(player)) {
            // ROM PlatformObject_SingleCharacter exit path (s2.asm:35735-35740):
            //   bclr #status.player.on_object,status(a1)
            //   bset #status.player.in_air,status(a1)
            // Bridge-specific helpers such as PlatformObject11_cont clear only
            // on_object so terrain handoff can happen without an airborne frame.
            // S1's unified model resolves platform walk-off one frame before the
            // airborne transition, so preserve the pre-object air state there.
            player.setAir(true);
        }
        return null;
    }

    private boolean carriesAirborneRiderAfterExitPlatform(ObjectInstance object) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        return object instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile()).carriesAirborneRiderAfterExitPlatform();
    }

    private boolean shouldResolveEarlierMultiPieceSiblingsBeforeRidingPiece(
            PlayableEntity player, ObjectInstance instance, int ridingPieceIndex) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (player == null
                || ridingPieceIndex <= 0
                || !(instance instanceof MultiPieceSolidProvider multiPiece)
                || !InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.resolvesEarlierPiecesBeforeRidingPiece())) {
            return false;
        }
        if (!player.getAir()) {
            return true;
        }
        return InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.usesPieceScopedStandingBits())
                && InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.airborneStaleStandingBitReturnsNoContact(player))
                && contactState.hasObjectStandingBit(player, instance, ridingPieceIndex);
    }

    private boolean usesPieceScopedStandingBits(ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        return instance instanceof MultiPieceSolidProvider multiPiece
                && InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.usesPieceScopedStandingBits());
    }

    private void preserveRidingPushStatusIfNeeded(PlayableEntity player, ObjectInstance instance,
            SolidObjectProvider provider) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(player instanceof AbstractPlayableSprite sprite)
                || !sprite.getPushing()
                || !InitialObjectDispatchController.afterCallback(initialDispatch, provider.preservesRidingPushStatus(player))) {
            return;
        }
        player.setPushing(true);
        contactState.setObjectPushingBit(player, instance);
        {
            provider.setPlayerPushing(player, true);
            InitialObjectDispatchController.requireCurrent(initialDispatch);
        }
    }

    private void applyRidingCarry(PlayableEntity player, ObjectInstance instance,
            SolidObjectProvider provider, SolidObjectParams params,
            int currentX, int currentY, int d4) {
        int deltaX = currentX - d4;
        if (deltaX != 0) {
            player.shiftX(deltaX);
        }

        int surfaceOffset;
        if (instance instanceof SlopedSolidProvider sloped) {
            int slopeAnchorX = currentX + params.offsetX();
            int slopeY = sampleSlopeY(player, slopeAnchorX, params.halfWidth(), sloped);
            surfaceOffset = (slopeY != Integer.MIN_VALUE) ? slopeY : params.groundHalfHeight();
        } else {
            surfaceOffset = params.groundHalfHeight();
        }
        int newCentreY = currentY + params.offsetY() - surfaceOffset - player.getYRadius();
        int newY = newCentreY - (player.getHeight() / 2);
        player.setY((short) newY);
    }

    private ContactKind toContactKind(SolidContact contact) {
        if (contact == null) {
            return ContactKind.NONE;
        }
        if (contact.standing() || contact.touchTop()) {
            return ContactKind.TOP;
        }
        if (contact.touchSide()) {
            return ContactKind.SIDE;
        }
        if (contact.touchBottom()) {
            return ContactKind.BOTTOM;
        }
        return ContactKind.NONE;
    }

    private boolean currentPushingState(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            return sprite.getPushing();
        }
        return false;
    }

    private record MultiPieceContactResult(
            boolean standing,
            boolean pushing,
            int pieceIndex,
            SolidContact aggregateContact,
            int overridePieceIndex) {}

    /**
     * Resolve the side/push contact of the sibling pieces allocated before the
     * ridden piece, ahead of the ridden piece's continued-ride ExitPlatform bounds
     * check. Mirrors ROM Obj70 (MTZ Cog) where each tooth's SolidObject runs in slot
     * allocation order (s2.asm:55039-55141), so an earlier-slot tooth side-pushes the
     * rider (loc_19896 / SolidObject side path) before the ridden tooth's standing-bit
     * branch re-checks the ride bounds (s2.asm:35196-35214). Only the horizontal
     * side-push matters here — the ridden piece's own riding/Y handling runs
     * afterward — so pieces that would register STANDING are left to the main pass.
     */
    private boolean resolveEarlierMultiPieceSiblings(PlayableEntity player,
            MultiPieceSolidProvider multiPiece, ObjectInstance instance, int ridingPieceIndex,
            int frameCounter, boolean useStickyBuffer) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        boolean anyPushing = false;
        SolidRoutineProfile solidProfile = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getSolidRoutineProfile());
        for (int i = 0; i < ridingPieceIndex && i < InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceCount()); i++) {
            boolean standingBitWasSetAtEntry = contactState.hasObjectStandingBit(player, instance, i)
                    || contactState.wasObjectStandingBitSetThisFrame(player, instance, i);
            SolidObjectParams params = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceParams(i));
            int anchorX = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceX(i)) + params.offsetX();
            int anchorY = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceY(i)) + params.offsetY();
            int halfHeight = player.getAir() ? params.airHalfHeight() : params.groundHalfHeight();
            SlopedSolidRoutineAdapter slopedAdapter = null;
            byte[] slopeData = null;
            if (instance instanceof SlopedSolidProvider sloped) {
                slopedAdapter = InitialObjectDispatchController.afterCallback(initialDispatch, SlopedSolidRoutineProfile.adapt(sloped));
                slopeData = InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.getSlopeData());
            }
            SolidContact contact;
            if (slopeData != null && shouldUseSlopeForContact(instance, slopedAdapter)) {
                SolidContact probe = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(),
                        params.groundHalfHeight(), solidProfile.topSolidOnly(), useStickyBuffer,
                        instance, false, slopedAdapter);
                if (probe != null && probe.standing() && player.getAir()
                        && InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.defersNewSlopedPieceLanding(i, player, frameCounter))) {
                    continue;
                }
                contact = probe == null ? null : resolveSlopedContact(
                        player, anchorX, anchorY, params.halfWidth(), params.groundHalfHeight(),
                        solidProfile.topSolidOnly(), useStickyBuffer, instance, true, slopedAdapter);
            } else {
                contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        solidProfile, useStickyBuffer, instance, i, true);
            }
            if (contact != null) {
                if (contact.pushing()) {
                    anyPushing = true;
                }
                { multiPiece.onPieceContact(i, player, contact, frameCounter,
                        standingBitWasSetAtEntry); InitialObjectDispatchController.requireCurrent(initialDispatch); }
            }
        }
        return anyPushing;
    }

    private MultiPieceContactResult processMultiPieceCollision(PlayableEntity player,
            MultiPieceSolidProvider multiPiece, ObjectInstance instance, int frameCounter,
            boolean useStickyBuffer) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        int pieceCount = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceCount());

        boolean anyStanding = false;
        boolean anyPushing = false;
        boolean anyTouchTop = false;
        boolean anyTouchBottom = false;
        boolean anyTouchSide = false;

        int standingPieceIndex = -1;
        // ROM Solid_ResetFloor takeover: while the player rides one piece (held by
        // its full-width continued window), an ADJACENT piece whose narrow
        // Solid_Landed window (obActWid*2 = piece spacing) now contains the
        // player's foot claims standing and reassigns standonobject to itself
        // (docs/s1disasm/_incObj/sub SolidObject.asm:305-324 Solid_Landed ->
        // Solid_ResetFloor:344-375). The narrow per-piece windows are contiguous
        // and non-overlapping, so exactly one piece claims. Only providers that
        // opt into usesPieceScopedStandingBits() (separate ROM SST slots folded
        // into one instance) take this path.
        int overridePieceIndex = -1;
        SolidRoutineProfile solidProfile = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getSolidRoutineProfile());
        boolean ridingCurrentObject = isRidingCurrentPlayerObject(instance);
        int currentRidingPieceIndex = getCurrentPlayerRidingPieceIndex();
        boolean pieceScopedStanding = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.usesPieceScopedStandingBits());

        // ROM ExecuteObjects runs each object's SolidObject in SST slot order, and
        // the rider's stand-on-object is re-seated by the slot it currently stands
        // on (Solid_ResetFloor). When the player walks from a HIGHER-slot solid
        // object onto a LOWER-slot one, the lower-slot object's SolidObject runs
        // FIRST -- BEFORE the higher-slot ridden object re-seats the rider this
        // frame -- so it tests its new top-landing against the rider's pre-re-seat
        // (previous-frame settled) y. This engine resolves solids after the object
        // updates, so a lower-slot object would otherwise observe the higher-slot
        // ridden object's just-applied re-seat and steal the ride one frame early.
        // Detect that case here so the new-landing detection below can use the
        // pre-re-seat y (SLZ1 f3520: ROM keeps the rider on the higher-slot @0B10
        // staircase at x=0x0B83 and only lands the lower-slot @0B90 staircase at
        // x=0x0B84). docs/s1disasm/sonic.asm ExecuteObjects (slot order);
        // docs/s1disasm/_incObj/sub SolidObject.asm:262-318 (Solid_Landed/ResetFloor).
        ObjectInstance riddenObjectForSlotGate = getRidingObject(player);
        boolean crossSlotLowerTakeover =
                !player.getAir()
                && riddenObjectForSlotGate != null
                && riddenObjectForSlotGate != instance
                && instance instanceof AbstractObjectInstance thisAoiGate
                && riddenObjectForSlotGate instanceof AbstractObjectInstance riddenAoiGate
                && thisAoiGate.getSlotIndex() >= 0
                && riddenAoiGate.getSlotIndex() >= 0
                && thisAoiGate.getSlotIndex() < riddenAoiGate.getSlotIndex();

        for (int i = 0; i < pieceCount; i++) {
            boolean standingBitWasSetAtEntry = contactState.hasObjectStandingBit(player, instance, i)
                    || contactState.wasObjectStandingBitSetThisFrame(player, instance, i);
            // ROM slot-order parity: when the earlier slots of this ridden object
            // were already side-pushed ahead of the ride-bounds re-check
            // (resolveEarlierMultiPieceSiblings set multiPieceEarlierPiecesResolvedUpTo
            // for this instance+player), skip them here so a single ROM SolidObject
            // side-push per slot is not applied twice in one frame.
            if (instance == multiPieceEarlierPiecesInstance && i < multiPieceEarlierPiecesResolvedUpTo) {
                continue;
            }
            if (InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.usesPieceScopedStandingBits())
                    && player.getAir()
                    && contactState.hasObjectStandingBit(player, instance, i)
                    // Same-frame unseat suppression (ROM once-per-frame
                    // SolidObjectFull): keep a piece ride established this frame.
                    && !retainsFreshStandingContact(player, instance, i)) {
                contactState.rememberAndClearStandingBit(player, instance, i);
                if (InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.airborneStaleStandingBitReturnsNoContact(player))) {
                    continue;
                }
            }
            SolidObjectParams params = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceParams(i));
            int pieceX = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceFreshContactX(i, player));
            int pieceY = InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.getPieceFreshContactY(i, player));
            if (ridingCurrentObject && i == currentRidingPieceIndex) {
                anyStanding = true;
                if (standingPieceIndex < 0) {
                    standingPieceIndex = i;
                }
                {
                    multiPiece.onPieceContact(i, player, SolidContact.STANDING, frameCounter, true);
                    InitialObjectDispatchController.requireCurrent(initialDispatch);
                }
                continue;
            }
            int anchorX = pieceX + params.offsetX();
            int anchorY = pieceY + params.offsetY();
            // ROM SolidObject (S1 sub SolidObject.asm:170-176; S2 s2.asm:35158-35169)
            // always uses d2 (object top half-height = airHalfHeight) in Solid_ChkCollision
            // for the Y overlap window, regardless of whether Sonic is grounded or airborne.
            // The grounded path uses d3 (groundHalfHeight) only inside MvSonicOnPtfm /
            // MvSonicOnPtfm2 (the continued-ride re-seat), not for fresh-contact detection.
            // Using groundHalfHeight here inflates maxTop by 1, giving distY one more than
            // ROM (engine distY=4 vs ROM distY=3 for the staircase), causing a 1px downward
            // snap. Use airHalfHeight for all non-riding-piece fresh contacts.
            int halfHeight = params.airHalfHeight();
            SlopedSolidRoutineAdapter slopedAdapter = null;
            byte[] slopeData = null;
            if (instance instanceof SlopedSolidProvider sloped) {
                slopedAdapter = InitialObjectDispatchController.afterCallback(initialDispatch, SlopedSolidRoutineProfile.adapt(sloped));
                slopeData = InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.getSlopeData());
            }

            SolidContact contact;
            if (slopeData != null && shouldUseSlopeForContact(instance, slopedAdapter)) {
                contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(),
                        params.groundHalfHeight(), solidProfile.topSolidOnly(), useStickyBuffer,
                        instance, true, slopedAdapter);
            } else if (crossSlotLowerTakeover) {
                // Cross-object hand-off from a higher-slot ridden object onto this
                // lower-slot one (see crossSlotLowerTakeover above). A NEW top
                // landing must be tested against the rider's pre-re-seat
                // (previous-frame) y, matching ROM slot order. Detect-only first
                // (apply=false) to see whether this would be a fresh top landing;
                // if so, re-test at the previous-frame y and DEFER the hand-off one
                // frame when the rider had not yet descended into this object's
                // landing band last frame. Side/corner contacts are unaffected.
                SolidContact curProbe = resolveContact(player, anchorX, anchorY,
                        params.halfWidth(), halfHeight, solidProfile, useStickyBuffer,
                        instance, i, false);
                if (curProbe != null && curProbe.standing() && !curProbe.touchSide()) {
                    short curCentreY = player.getCentreY();
                    // getCentreY(0) is the most recent recorded (post-collision)
                    // position -- i.e. the rider's settled y at the END of the
                    // previous frame, which is exactly the y a ROM lower-slot object
                    // observes when it runs BEFORE this frame's higher-slot ride
                    // re-seat. The live getCentreY() already reflects that re-seat.
                    short prevCentreY = player.getCentreY(0);
                    boolean prevLands = prevCentreY == curCentreY;
                    if (!prevLands) {
                        NativePositionOps.writeYPosPreserveSubpixel(player, prevCentreY);
                        SolidContact prevProbe = resolveContact(player, anchorX, anchorY,
                                params.halfWidth(), halfHeight, solidProfile, useStickyBuffer,
                                instance, i, false);
                        NativePositionOps.writeYPosPreserveSubpixel(player, curCentreY);
                        prevLands = prevProbe != null && prevProbe.standing();
                    }
                    if (!prevLands) {
                        continue;
                    }
                }
                contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        solidProfile, useStickyBuffer, instance, i, true);
            } else {
                // Multi-piece solids don't use monitor solidity
                // Pass piece index so sticky buffer only applies to the piece being ridden
                contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        solidProfile, useStickyBuffer, instance, i, true);
            }
            if (contact == null) {
                continue;
            }
            if ((contact.standing() || contact.touchTop())
                    && InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()) != null
                    && player instanceof AbstractPlayableSprite sprite) {
                sprite.setLatchedSolidObject(InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()).objectId(), instance);
            }

            if (contact.standing()) {
                anyStanding = true;
                if (standingPieceIndex < 0) {
                    standingPieceIndex = i;
                }
                // Solid_ResetFloor takeover: a non-ridden sibling piece freshly
                // standing while the player rides a different piece of this same
                // instance. resolveContact already re-seated the player onto this
                // piece (apply=true), so record it so the riding latch transfers.
                // Gate on the NARROW Solid_Landed window (obActWid, not the wider
                // collision half-width): adjacent pieces' full collision boxes
                // overlap, so without this gate two neighbouring pieces both
                // register standing and the takeover thrashes between them every
                // frame. The narrow windows are contiguous/non-overlapping, so
                // exactly one piece owns the player (ROM Solid_Landed obActWid
                // gate, docs/s1disasm/_incObj/sub SolidObject.asm:307-315).
                // ROM slot order: each folded piece is its own SST slot and each
                // landing calls RideObject_SetRide, which overwrites the rider's
                // stand-on-object with the piece that ran most recently
                // (docs/s2disasm/s2.asm:35619-35626). Pieces are iterated in slot
                // order, so the LAST claiming piece owns the rider. For objects
                // whose narrow Solid_Landed windows are contiguous and
                // non-overlapping only one piece ever claims, so this is a no-op
                // for them.
                if (pieceScopedStanding && ridingCurrentObject && !player.getAir()
                        && i != currentRidingPieceIndex) {
                    // Same gate the fresh-landing path uses: ROM
                    // SolidObject_Landed re-reads width_pixels(a0) and requires
                    // x_pos(a1) + width_pixels - x_pos(a0) in [0, width_pixels*2)
                    // (docs/s2disasm/s2.asm:35588-35597; S1's obActWid equivalent
                    // at docs/s1disasm/_incObj/sub SolidObject.asm:307-315). Going
                    // through isWithinTopLandingWidth keeps the takeover window
                    // identical to the landing window instead of re-deriving it.
                    int relPiece = player.getCentreX() - (pieceX + params.offsetX())
                            + params.halfWidth();
                    if (isWithinTopLandingWidth(instance, player, relPiece,
                            params.halfWidth(), i)) {
                        overridePieceIndex = i;
                    }
                }
            }
            if (contact.touchTop()) {
                anyTouchTop = true;
            }
            if (contact.touchBottom()) {
                anyTouchBottom = true;
            }
            if (contact.touchSide()) {
                anyTouchSide = true;
            }
            if (contact.pushing()) {
                anyPushing = true;
            }

            { multiPiece.onPieceContact(i, player, contact, frameCounter,
                    standingBitWasSetAtEntry); InitialObjectDispatchController.requireCurrent(initialDispatch); }
        }

        SolidContact aggregateContact = (anyStanding || anyTouchTop || anyTouchBottom
                || anyTouchSide || anyPushing)
                ? new SolidContact(
                        anyStanding, anyTouchSide, anyTouchBottom, anyTouchTop, anyPushing)
                : null;

        return new MultiPieceContactResult(
                anyStanding, anyPushing,
                standingPieceIndex, aggregateContact,
                overridePieceIndex);
    }

    /**
     * Resolve contact for single-piece objects (backwards compatibility).
     */
    private SolidContact resolveContact(PlayableEntity player,
            int anchorX, int anchorY, int halfWidth, int halfHeight, SolidRoutineProfile profile,
            boolean useStickyBuffer, ObjectInstance instance, boolean apply) {
        return resolveContact(player, anchorX, anchorY, halfWidth, halfHeight, profile,
                useStickyBuffer, instance, -1, apply);
    }

    private boolean shouldUseSlopeForContact(ObjectInstance instance, SlopedSolidRoutineAdapter adapter) {
        return adapter.profile().usesSlopeForNewLanding() || isRidingCurrentPlayerObject(instance);
    }

    /**
     * Resolve contact with piece index support for multi-piece objects.
     * @param pieceIndex The piece index being checked, or -1 for single-piece objects
     */
    private SolidContact resolveContact(PlayableEntity player,
            int anchorX, int anchorY, int halfWidth, int halfHeight, SolidRoutineProfile profile,
            boolean useStickyBuffer, ObjectInstance instance, int pieceIndex, boolean apply) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (ObjectDirectionAccess.suspended(objectManager, instance)) return null;
        boolean topSolidOnly = profile.topSolidOnly();
        boolean monitorSolidity = profile.monitorSolidity();
        int monitorVerticalOffset = profile.monitorVerticalOffset();
        boolean inclusiveRightEdge = profile.inclusiveRightEdge();
        int playerCenterX = player.getCentreX();
        int playerCenterY = player.getCentreY();

        int width2 = halfWidth * 2;
        int relXRaw = playerCenterX - anchorX + halfWidth;

        int playerYRadius = player.getYRadius();
        int maxTop = halfHeight + playerYRadius;
        // Full-solid underside overlap is game-sensitive. S1 uses the player's
        // current y-radius on both halves to avoid premature rolling underside
        // hits, while S2/S3K keep the taller underside half used by existing
        // solid/spring parity and the AIZ trace-replay spring contact.
        boolean useCurrentBottomRadius =
                usesCurrentYRadiusOnlyForFullSolidBottomOverlap(player)
                        || (instance instanceof SolidObjectProvider provider
                                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.fullSolidBottomOverlapUsesCurrentYRadiusOnly(player)));
        int totalHeight = useCurrentBottomRadius
                ? maxTop * 2
                : maxTop + (monitorSolidity ? maxTop : halfHeight + getSolidTopYRadius(player));
        // SPG-style monitor callers keep zero here. S3K monitors branch into
        // SolidObject_cont, which adds +4 before the d2/y_radius overlap check
        // (docs/skdisasm/sonic3k.asm:40615-40616, 41469-41472).
        int verticalOffset = monitorSolidity ? monitorVerticalOffset : 4;
        // ROM: s2.asm:35147 uses andi.w #$7FF,d0 to handle VDP Y-coordinate
        // wrapping near y_pos=0 (16-bit hardware arithmetic). The engine uses
        // 32-bit absolute coordinates with no wrapping, so the mask must NOT be
        // applied — it causes phantom collisions between objects separated by
        // ~2048px vertically in tall levels (e.g. HCZ2 FanPlatformChild at Y=608
        // falsely colliding with player at Y=2654).
        // ROM: SolidObject_cont's reverse-gravity branch (sonic3k.asm:41452-41464) is
        // loc_1DFD6 (:41466-41480) with one extra instruction — `neg.w d3` on the
        // player-minus-object delta, before the +4 and the d2 add. Everything else, the
        // default_y_radius/y_radius pair included, is identical. anchorY is the object's
        // centre and maxTop is the ROM's d2 (object half-height + player y_radius), so
        // mirroring that one term is the whole row.
        boolean reverseGravity = isReverseGravityActive(player);
        int centreDeltaY = reverseGravity
                ? anchorY - playerCenterY
                : playerCenterY - anchorY;
        int relY = centreDeltaY + verticalOffset + maxTop;

        boolean riding = useStickyBuffer && isRidingCurrentPlayerObject(instance);
        // For multi-piece objects, only apply sticky buffer (-16px) when checking
        // the specific piece being ridden, not all pieces of the same object.
        // pieceIndex < 0 means single-piece object (always apply sticky buffer if riding)
        int currentRidingPieceIndex = getCurrentPlayerRidingPieceIndex();
        boolean ridingThisPiece = riding && (pieceIndex < 0 || pieceIndex == currentRidingPieceIndex);
        // Sticky solids approximate SolidObject's old/new X handoff behavior in the
        // original engine by allowing a small horizontal retention window on the
        // ridden piece. Without this, fast moving platforms (ObjB2 Tornado, etc.)
        // can drop contact for one frame at edges.
        int stickyX = ridingThisPiece ? 16 : 0;
        int rightLimit = width2 + stickyX;
        ProjectedPreMovementX projectedPreMovementX = projectedPreMovementXForSolidContact(
                player, instance, anchorX, halfWidth, relXRaw, rightLimit, inclusiveRightEdge, apply);
        if (projectedPreMovementX != null) {
            playerCenterX = projectedPreMovementX.centerX();
            relXRaw = playerCenterX - anchorX + halfWidth;
        }
        if (relXRaw < -stickyX || (inclusiveRightEdge ? relXRaw > rightLimit : relXRaw >= rightLimit)) {
            return null;
        }
        // Clamp sticky overflow back into the normal box before side/top resolution.
        // Objects that opt into the ROM's inclusive right edge must keep relX == width2:
        // SolidObject_cont treats that as a zero-distance side contact, not a 1px shove.
        int relX = relXRaw;
        if (relX < 0) {
            relX = 0;
        } else if (relX > width2 || (!inclusiveRightEdge && relX == width2)) {
            relX = width2 - 1;
        }

        int minRelY = ridingThisPiece ? -16 : 0;

        if (relY < minRelY || relY >= totalHeight) {
            return null;
        }

        SolidContact contact;
        if (monitorSolidity) {
            contact = resolveMonitorContact(player, relX, relY, halfWidth, maxTop, playerCenterX, playerCenterY,
                    anchorX, riding, apply);
        } else {
            contact = resolveContactInternal(player, relX, relY, halfWidth, maxTop, totalHeight,
                    playerCenterX, playerCenterY,
                    topSolidOnly, riding, apply, instance, true, pieceIndex,
                    projectedPreMovementX != null);
        }
        preserveProjectedXSubpixelAfterSideContact(player, contact, projectedPreMovementX);
        return contact;
    }

    private ProjectedPreMovementX projectedPreMovementXForSolidContact(PlayableEntity player,
            ObjectInstance instance, int anchorX, int halfWidth, int relXRaw, int rightLimit,
            boolean inclusiveRightEdge, boolean apply) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!apply
                || !(instance instanceof SolidObjectProvider provider)
                || !InitialObjectDispatchController.afterCallback(initialDispatch, provider.projectsPreMovementGroundXForSolidContact(player))
                || !(player instanceof AbstractPlayableSprite sprite)) {
            return null;
        }
        boolean expandedObjectControlledCarry = sprite.isObjectControlled()
                && sprite.isObjectControlSuppressesMovement()
                && sprite.getYRadius() > sprite.getStandYRadius();
        if (!expandedObjectControlledCarry
                && (player.getAir()
                        || sprite.getGroundMode() != GroundMode.GROUND
                        || (sprite.getAngle() & 0xFF) != 0
                        || sprite.getRolling()
                        || sprite.getSpindash())) {
            return null;
        }
        // A normal post-movement pass has already applied the player step.
        // Native bit-0 object control is different: the shared player pass is
        // suppressed and the controller will apply x_vel later in its object
        // slot, so an earlier solid still needs the pending projection.
        if (postMovement
                && !(sprite.isObjectControlled() && sprite.isObjectControlSuppressesMovement())) {
            return null;
        }

        boolean leftOfBox = relXRaw < 0;
        boolean rightOfBox = inclusiveRightEdge ? relXRaw > rightLimit : relXRaw >= rightLimit;
        if (!leftOfBox && !rightOfBox) {
            return null;
        }

        Short controlledProjectedXSpeed = expandedObjectControlledCarry
                ? com.openggf.sprites.playable.PlayableSpriteInternalAccess
                        .projectedObjectControlledSolidContactXSpeed(sprite, instance)
                : null;
        if (expandedObjectControlledCarry && controlledProjectedXSpeed == null) {
            return null;
        }
        short projectedXSpeed = controlledProjectedXSpeed != null
                ? controlledProjectedXSpeed
                : projectedFlatGroundXSpeed(sprite);
        if ((leftOfBox && projectedXSpeed <= 0) || (rightOfBox && projectedXSpeed >= 0)) {
            return null;
        }

        int projectedFullX = ((int) sprite.getCentreX() << 16)
                | (sprite.getXSubpixelRaw() & 0xFFFF);
        projectedFullX += (int) projectedXSpeed << 8;
        int projectedCenterX = projectedFullX >> 16;
        int projectedRelX = projectedCenterX - anchorX + halfWidth;
        boolean projectedInside = projectedRelX >= 0
                && (inclusiveRightEdge ? projectedRelX <= rightLimit : projectedRelX < rightLimit);
        if (!projectedInside) {
            return null;
        }
        return new ProjectedPreMovementX(projectedCenterX, projectedFullX & 0xFFFF);
    }

    private short projectedFlatGroundXSpeed(AbstractPlayableSprite sprite) {
        short gSpeed = sprite.getGSpeed();
        short runAccel = sprite.getRunAccel();
        short runDecel = sprite.getRunDecel();
        short friction = sprite.getFriction();
        short max = sprite.getMax();
        boolean left = sprite.isLeftPressed();
        boolean right = sprite.isRightPressed();
        boolean moveLockActive = sprite.getMoveLockTimer() > 0;

        if (!moveLockActive) {
            if (left) {
                if (gSpeed > 0) {
                    gSpeed -= runDecel;
                    if (gSpeed < 0) {
                        gSpeed = (short) -128;
                    }
                } else {
                    gSpeed = accelerateLeftForProjection(sprite, gSpeed, runAccel, max);
                }
            }
            if (right) {
                if (gSpeed < 0) {
                    gSpeed += runDecel;
                    if (gSpeed >= 0) {
                        gSpeed = (short) 128;
                    }
                } else {
                    gSpeed = accelerateRightForProjection(sprite, gSpeed, runAccel, max);
                }
            }
        }

        if (!left && !right) {
            gSpeed = applyFrictionForProjection(gSpeed, friction);
        }
        return gSpeed;
    }

    private short accelerateLeftForProjection(AbstractPlayableSprite sprite, short gSpeed,
            short runAccel, short max) {
        PlayerMovementRules rules = playerMovementRulesOrNull(sprite);
        boolean alwaysCap = rules != null && rules.inputAlwaysCapsGroundSpeed();
        if (alwaysCap || gSpeed > -max) {
            gSpeed -= runAccel;
            if (gSpeed < -max) {
                gSpeed = (short) -max;
            }
        }
        return gSpeed;
    }

    private short accelerateRightForProjection(AbstractPlayableSprite sprite, short gSpeed,
            short runAccel, short max) {
        PlayerMovementRules rules = playerMovementRulesOrNull(sprite);
        boolean alwaysCap = rules != null && rules.inputAlwaysCapsGroundSpeed();
        if (alwaysCap || gSpeed < max) {
            gSpeed += runAccel;
            if (gSpeed > max) {
                gSpeed = max;
            }
        }
        return gSpeed;
    }

    private short applyFrictionForProjection(short speed, short friction) {
        if (speed > 0) {
            speed -= friction;
            if (speed < 0) {
                speed = 0;
            }
        } else if (speed < 0) {
            speed += friction;
            if (speed > 0) {
                speed = 0;
            }
        }
        return speed;
    }

    private void preserveProjectedXSubpixelAfterSideContact(PlayableEntity player, SolidContact contact,
            ProjectedPreMovementX projectedPreMovementX) {
        if (projectedPreMovementX == null
                || contact == null
                || !contact.touchSide()
                || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        sprite.setSubpixelRaw(projectedPreMovementX.subpixel(), sprite.getYSubpixelRaw());
    }

    private int getSolidTopYRadius(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            return sprite.getStandYRadius();
        }
        return player.getYRadius();
    }

    private int getTopLandingSnapAdjustment(ObjectInstance instance,
            PlayableEntity player) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(instance instanceof SolidObjectProvider provider)) {
            return 0;
        }
        return InitialObjectDispatchController.afterCallback(initialDispatch, provider.getTopLandingSnapAdjustment(player, getSolidTopYRadius(player)));
    }

    private void applyNonUnifiedTopSolidLandingHeightOverride(PlayableEntity player,
            SolidContact contact, ObjectInstance instance, SolidRoutineProfile solidProfile,
            boolean wasAirborne, boolean wasRidingObject, int anchorY, SolidObjectParams params) {
        if (contact != SolidContact.STANDING
                || !solidProfile.topSolidOnly()
                || !wasAirborne
                || wasRidingObject
                || instance instanceof SlopedSolidProvider
                || usesUnifiedCollisionModel(player)) {
            return;
        }
        // ROM: SolidObject_Landed (s2.asm:35588-35607) uses playerY - relY + 3,
        // which resolveContactInternal already computes correctly. The absolute
        // anchorY-groundHalfHeight formula below only matches PlatformObject_ChkYRange
        // (s2.asm:35696-35712). Skip the snap for SolidObject-based objects so
        // resolveContactInternal's result is preserved.
        if (!solidProfile.usesPlatformLandingSnap()) {
            return;
        }
        // S3K SolidObjectTop loc_1E45A snaps above the top with the -1 bias.
        // Its reverse branch loc_1E4D6 instead ends exactly at object bottom
        // plus the live radius (SUB.W d1,d2; SUBQ.W #4,d2). It is not the
        // same +/-3 lift used by SolidObjectFull. This final height override
        // must preserve that asymmetry after Player_TouchFloor restores radii.
        int targetCentreY = isReverseGravityActive(player)
                ? anchorY + params.groundHalfHeight() + player.getYRadius()
                : anchorY - params.groundHalfHeight() - player.getYRadius() - 1;
        if (player instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.writeYPosPreserveSubpixel(sprite, targetCentreY);
            return;
        }
        int newY = targetCentreY - (player.getHeight() / 2);
        player.setY((short) newY);
    }

    private boolean isSignedObjectControlSideContactRejected(PlayableEntity player,
            ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(player instanceof AbstractPlayableSprite sprite)
                || !sprite.isObjectControlled()
                || sprite.isObjectControlAllowsCpu()
                || !(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        return InitialObjectDispatchController.afterCallback(initialDispatch, provider.rejectsBit7ObjectControlSideContact(player));
    }

    private boolean isSignedObjectControlNewSolidContactRejected(PlayableEntity player,
            ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(player instanceof AbstractPlayableSprite sprite)
                || !sprite.isObjectControlled()
                || sprite.isObjectControlAllowsCpu()
                || !(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        return InitialObjectDispatchController.afterCallback(initialDispatch, provider.rejectsBit7ObjectControlNewSolidContact(player));
    }

    /**
     * Monitor-specific collision resolution (SPG: "Item Monitor").
     * Differences from normal solid objects:
     * - Landing only if playerY - topCombinedBox < 16 AND within monitor X ± (halfWidth + 4)
     * - Never pushes player downward, only to sides
     */
    private SolidContact resolveMonitorContact(PlayableEntity player, int relX, int relY,
            int halfWidth, int maxTop, int playerCenterX, int playerCenterY, int anchorX,
            boolean sticky, boolean apply) {
        // ROM: Mon_Solid / SolidObject_Monitor_Sonic — roll-animation check runs AFTER
        // Mon_SolidSides geometry detection, not as an external gate.
        // When the player is in the native roll animation AND velY >= 0 (not
        // moving upward), skip the solid response entirely so the touch system
        // can break the monitor.
        // When velY < 0 (moving upward), always handle as solid (side push / landing)
        // regardless of rolling state — this is the S1 "always solid when moving up" rule.
        // When standing on the monitor (sticky), always handle as solid (ride mode,
        // ROM: ob2ndRout=2 bypasses Mon_SolidSides entirely).
        if (!sticky && isMonitorRollAnimation(player) && player.getYSpeed() >= 0) {
            return null;
        }

        // Calculate distances from center
        int distX;
        int absDistX;
        if (relX > halfWidth) {
            distX = relX - (halfWidth * 2);
            absDistX = -distX;
        } else {
            distX = relX;
            absDistX = distX;
        }

        int distY;
        if (relY <= maxTop) {
            distY = relY;
        } else {
            // No +4 offset for monitor bottom collision (since we didn't add it in overlap)
            distY = relY - (maxTop * 2);
        }

        // ROM: Mon_SolidSides checks cmpi.w #$10,d3 — relY < 16 means near-top zone
        boolean canLand = distY >= 0 && distY < 16;

        // ROM: Mon_SolidSides narrow top landing zone uses obActWid+4, NOT
        // the full collision halfWidth. obActWid = halfWidth - $B (the $B comes
        // from the "addi.w #$B,d1" that creates the collision box from obActWid).
        // So the landing margin = (halfWidth - $B) + 4 = halfWidth - 7.
        int xFromCenter = relX - halfWidth;
        int landingXMargin = halfWidth - 7;
        boolean withinLandingX = Math.abs(xFromCenter) <= landingXMargin;

        if (canLand && withinLandingX) {
            // ROM Mon_Solid -> SolidObject top-overlap path gates the floor
            // re-seat (Solid_ResetFloor / RideObject_SetRide: clear Status_InAir,
            // set Status_OnObj, zero y_vel) on `tst.w y_vel(a1) / bmi` — a rising
            // player crossing the top upward gets no re-seat (SolidObjectFull's
            // upward-velocity branch, sonic3k.asm:41665-41666 loc_1E198 moveq #0,d4
            // / rts). Without this, a player who JUMPS off the monitor's top loses
            // his airborne state on the jump frame (SLZ3 f1187: a rolling jump off
            // a Monitor — ROM keeps Sonic rising, the engine re-grounded him).
            //
            // We additionally require the JUMP status bit (`Status_Jump`), NOT just
            // y_vel<0, to scope the release to the genuine jump-off case. ROM's gate
            // is y_vel<0 alone, but a separate engine object-ordering issue can give
            // a NON-jumping player a transient negative y_vel at the monitor pass
            // (MZ1 f1260: Sonic falls, a broken monitor's break-bounce flips his
            // y_vel up just before he should land on an ADJACENT monitor — ROM lands
            // him because ROM's y_vel is still >=0 at that solid pass). Releasing
            // that case regressed MZ1; gating on Status_Jump too keeps the fix to the
            // player who actually jumped, without masking that ordering bug. Sticky
            // (already-riding) contacts are resolved by processInlineRidingObject
            // before reaching here, so this only affects a NEW top contact.
            boolean risingNewContact = !sticky && player.getYSpeed() < 0
                    && player instanceof AbstractPlayableSprite aps && aps.isJumping()
                    && !contactState.hadSameFrameMonitorBreakBounce(player);
            if (risingNewContact) {
                // ROM loc_1E198: the player leaving the top upward is not penetrating,
                // so neither the d3 vertical separation nor the OnObj/air-clear apply.
                // Return no contact so the jump's airborne state and position survive.
                return null;
            }
            if (apply) {
                // S1 Mon_SolidSides returns d3 as the vertical penetration and
                // Mon_Solid aligns with `sub.w d3,obY(a1)`, unlike the generic
                // SolidObject_Landed path that applies a +3 landing bias.
                int newCenterY = playerCenterY - distY;
                int newY = newCenterY - (player.getHeight() / 2);
                player.setY((short) newY);
                // ROM: Solid_ResetFloor unconditionally sets these.
                player.setAngle((byte) 0);
                player.setYSpeed((short) 0);
                player.setGSpeed(player.getXSpeed());
                if (player.getAir()) {
                    LOGGER.fine(() -> "Monitor landing at (" + player.getX() + "," + player.getY() +
                        ") distY=" + distY);
                    applyObjectLandingState(player);
                }
                // ROM: bset #status.player.on_object (s2.asm:35739)
                player.setOnObject(true);
            }
            return SolidContact.STANDING;
        }

        // ROM SolidObject_AtEdge (s2.asm:35438-35445): for any side contact
        // when the player is not in air, set the OBJECT's push bit AND the
        // player's pushing bit, regardless of moving direction.  Position
        // correction and speed zeroing remain gated on movingInto.  This is
        // required for MCZ f862 trace parity: Sonic stands on a monitor's left
        // edge with xSpeed=0 on f860, ROM still sets the monitor's p1_pushing
        // bit so Obj26_Break can use it to airborne Sonic when the monitor
        // is broken two frames later.
        boolean leftSide = playerCenterX <= anchorX;
        // ROM Mon_Solid `.sidetouch` skips the horizontal correction only when Sonic is
        // already moving AWAY from the monitor, not merely when he is not moving into it
        // (docs/s1disasm/_incObj/"26, 2E Monitors and Power-Ups.asm":121-141). With Sonic
        // in the monitor's left half (d0 > 0) `.sonicright` runs `tst.w obVelX(a1) /
        // bmi.s .push`, so a zero x_vel falls through to `.stopsonic` and IS aligned;
        // only a strictly-negative (leftward, i.e. away) x_vel skips it. The right half
        // is the mirror: `.sonicleft` runs `bpl.s .push`, so there the correction needs a
        // strictly-negative x_vel. Requiring a strictly-positive x_vel on the left half
        // dropped the second of the two corrections the ROM applies in SYZ1 f8396 of the
        // complete-emerald run, where Sonic falls with x_vel = 0 between a spring at
        // $1F4E and this monitor at $1F7E: the spring's generic Solid_AlignToSide shoves
        // him right to $1F69 and the monitor must shove him back to $1F64.
        boolean movingInto = leftSide ? player.getXSpeed() >= 0 : player.getXSpeed() < 0;
        boolean exactEdgeOverlap = absDistX == 0;
        boolean pushing = !player.getAir();
        if (apply && movingInto && !exactEdgeOverlap) {
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            int pushDist = leftSide ? -absDistX : absDistX;
            if (postMovement) {
                // ROM: sub.w d0,obX(a1) — pixel-only, subpixel preserved
                player.move((short) (pushDist * 256), (short) 0);
            } else {
                player.setCentreX((short) (playerCenterX + pushDist));
            }
        }
        return pushing ? SolidContact.SIDE_PUSH : SolidContact.SIDE_NO_PUSH;
    }

    private static boolean isMonitorRollAnimation(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite
                && sprite.getAnimationProfile() instanceof ScriptedVelocityAnimationProfile profile) {
            return sprite.getAnimationId() == profile.getRollAnimId();
        }
        return player.getRolling();
    }

    private SolidContact resolveSlopedContact(PlayableEntity player, int anchorX, int anchorY, int halfWidth,
            int halfHeight, boolean topSolidOnly, boolean useStickyBuffer,
            ObjectInstance instance, boolean apply, SlopedSolidRoutineAdapter slopedAdapter) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        byte[] slopeData = InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.getSlopeData());
        if (slopeData == null || slopeData.length == 0) {
            return null;
        }
        SlopedSolidRoutineProfile slopedProfile = slopedAdapter.profile();
        int playerCenterX = player.getCentreX();
        int playerCenterY = player.getCentreY();

        int relX = playerCenterX - anchorX + halfWidth;
        int width2 = halfWidth * 2;
        boolean inclusiveRightEdge = instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.usesInclusiveRightEdge());
        if (relX < 0 || (inclusiveRightEdge ? relX > width2 : relX >= width2)) {
            return null;
        }

        int sampleX = relX & 0xFFFF;
        if (InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.isSlopeFlipped())) {
            // ROM: move.w d0,d5 / not.w d5 / add.w d3,d5 / lsr.w #1,d5
            // where d0=relX and d3=halfWidth*2. This is 16-bit word
            // arithmetic; at the inclusive right edge, d5 becomes $7FFF
            // rather than a signed -1.
            sampleX = ((~sampleX) + width2) & 0xFFFF;
        }
        sampleX = sampleX >>> InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.provider().getSlopeSampleShift());
        Integer slopeSampleValue = InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.provider().sampleSlopeByte(sampleX));
        if (slopeSampleValue == null) {
            return null;
        }

        int slopeSample = slopeSampleValue;
        int slopeBase = slopedProfile.slopeBaseline();
        boolean riding = useStickyBuffer && isRidingCurrentPlayerObject(instance);
        int minRelY = riding ? -16 : 0;

        int slopeOffset = slopeSample - slopeBase;
        int baseY = anchorY - slopeOffset;

        int playerYRadius = player.getYRadius();
        int maxTop = halfHeight + playerYRadius;
        // ROM SolidObjCheckSloped2 samples an absolute surface Y
        // (objectY - slopeSample), then compares it directly against
        // playerY + yRadius + 4. Most sloped helpers do not add the object's
        // half-height here: baseY is already the sampled top surface, unlike
        // flat solids where anchorY still refers to the object's centre.
        // S1 SolidObject2F is an explicit exception: it adds the slope catch
        // range into d2 before adding d2 to the vertical overlap value.
        int verticalOverlapCompensation = playerYRadius;
        if (slopedProfile.addsSlopeCatchRangeToVerticalOverlap()) {
            verticalOverlapCompensation += halfHeight;
        }
        int relY = playerCenterY - baseY + 4 + verticalOverlapCompensation;

        Integer directTopLimit = topSolidOnly && !riding
                ? InitialObjectDispatchController.afterCallback(initialDispatch, slopedAdapter.provider().getDirectTopLandingOverlapLimit()) : null;
        // S3K loc_1E45A: BHI rejects a positive surface-minus-feet result;
        // CMP.W #-$10 / BLO also rejects zero. Only overlaps 1..16 land.
        if (directTopLimit != null) minRelY = 1;
        int verticalLimit = directTopLimit != null ? directTopLimit : maxTop * 2;
        if (relY < minRelY || relY >= verticalLimit) {
            return null;
        }

        // ROM parity: SlopedSolid_SingleCharacter (s2.asm:35124) when the standing
        // bit is already set (player is riding the slope) does NOT use the generic
        // side-vs-top classification. It only checks:
        //   1. Is the player in the air? → unseat
        //   2. Is the player outside X range? → unseat (already checked above)
        //   3. Otherwise → keep standing, adjust Y via MvSonicOnSlope
        // The engine equivalent of "standing bit already set" is riding=true.
        // Bypass resolveContactInternal to avoid the side misclassification that
        // occurs when horizontal penetration < vertical penetration on slopes.
        if (riding && !player.getAir()) {
            if (apply) {
                int rawSample = sampleSlopeY(player, anchorX, halfWidth, slopedAdapter.provider());
                if (rawSample != Integer.MIN_VALUE) {
                    int targetCentreY = anchorY - (rawSample & 0xFF) - playerYRadius;
                    int newY = targetCentreY - (player.getHeight() / 2);
                    player.setY((short) newY);
                }
            }
            return SolidContact.STANDING;
        }

        if (!riding
                && !player.getAir()
                && slopedProfile.usesGroundedStandingCatchWindow()
                && relY >= 0
                && relY <= maxTop
                && isWithinTopLandingWidth(instance, player, relX, halfWidth, -1)) {
            if (apply) {
                // ROM parity: S2 SlopedSolid_cont (s2.asm:35106-35278) still
                // enters SolidObject_Landed (s2.asm:35178-35383) before Obj41's
                // diagonal launch reads y_pos and applies its +6 nudge
                // (s2.asm:34028-34088).  The grounded catch window only
                // bypasses side classification; it must not bypass the Y snap.
                int landedCentreY = playerCenterY - relY + 3;
                player.setY((short) (landedCentreY - (player.getHeight() / 2)));
                player.setAngle((byte) 0);
                player.setYSpeed((short) 0);
                player.setGSpeed(player.getXSpeed());
                player.setOnObject(true);
            }
            return SolidContact.STANDING;
        }

        SolidContact result = resolveContactInternal(player, relX, relY, halfWidth, maxTop, maxTop * 2,
                playerCenterX, playerCenterY, topSolidOnly, riding, apply, instance, true, -1, false);

        // Continued riding uses SolidObjSloped2/SlopeObject2 to re-sample
        // the absolute surface after the generic standing check. New
        // landings already matched loc_1C100 above; reapplying here after
        // Player_TouchFloor has cleared rolling would use the new standing
        // radius and push roll landings down by 5 px.
        if (result == SolidContact.STANDING && apply && riding) {
            int rawSample = sampleSlopeY(player, anchorX, halfWidth, slopedAdapter.provider());
            if (rawSample != Integer.MIN_VALUE) {
                int targetCentreY = anchorY - (rawSample & 0xFF) - playerYRadius;
                int newY = targetCentreY - (player.getHeight() / 2);
                player.setY((short) newY);
            }
        }

        return result;
    }

    private SolidContact resolveContactInternal(PlayableEntity player, int relX, int relY, int halfWidth,
            int maxTop, int totalHeight, int playerCenterX, int playerCenterY, boolean topSolidOnly,
            boolean sticky, boolean apply, ObjectInstance instance, boolean useTopLandingWidth,
            int pieceIndex, boolean projectedPreMovementX) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        int distX;
        int absDistX;
        // All three native SolidObject routines use cmp.w d0,d1 / bhs for
        // this split, so equality belongs to the left half.
        if (relX > halfWidth) {
            distX = relX - (halfWidth * 2);
            absDistX = -distX;
        } else {
            distX = relX;
            absDistX = distX;
        }

        Integer directTopLimit = topSolidOnly && !sticky
                ? directTopLandingOverlapLimit(instance) : null;
        int distY;
        int absDistY;
        // Direct top helpers compare feet-relative overlap, even when it exceeds
        // the radius of a rolling player; they have no bottom-contact branch.
        if (directTopLimit != null || relY <= maxTop) {
            distY = relY;
            absDistY = distY;
        } else {
            distY = relY - 4 - totalHeight;
            absDistY = Math.abs(distY);
        }

        if (!sticky && isSignedObjectControlNewSolidContactRejected(player, instance)) {
            // S3K SolidObject_cont rejects signed object_control before
            // side/top new-contact classification
            // (sonic3k.asm:41434-41480). Continued standing-bit riding is
            // handled before this path by SolidObjectFull_1P.
            return null;
        }

        // Sonic 1 top-solid objects use PlatformObject/SlopeObject semantics:
        // top-landing is resolved purely by X-range + top Y-window (no side-priority compare).
        if (topSolidOnly && (directTopLimit != null || usesUnifiedCollisionModel(player))) {
            if (player.getYSpeed() < 0) {
                return null;
            }
            if (!sticky
                    && instance instanceof SolidObjectProvider provider
                    && InitialObjectDispatchController.afterCallback(initialDispatch, provider.gatesNewTopSolidLandingWithPreviousPosition())) {
                int prevCenterX = player.getCentreX(1);
                int prevCenterY = player.getCentreY(1);
                int anchorX = playerCenterX - (relX - halfWidth);
                int anchorY = playerCenterY - (relY - 4 - maxTop);
                int prevRelX = prevCenterX - anchorX + halfWidth;
                int prevRelY = prevCenterY - anchorY + 4 + maxTop;
                int width2 = halfWidth * 2;
                if (prevRelX < 0 || prevRelX >= width2 || prevRelY < 0 || prevRelY >= 0x10) {
                    return null;
                }
            }
            // ROM PlatformObject detects a NEW landing against the same entry
            // surface it later snaps to (obY-8 for Obj18), while continued riding
            // uses MvSonicOnPtfm2's obY-9. Some objects model the riding surface in
            // getSolidParams (so distY is computed against obY-9) and recover the
            // entry surface only through getTopLandingSnapAdjustment, which fixes the
            // landing POSITION but not the detection TIMING. Apply that same offset
            // to the detection band for new landings (sticky=false) so the detect
            // surface matches the snap surface; riding (sticky=true) is unchanged.
            int detectionDistY = distY;
            if (!sticky) {
                detectionDistY = distY + getTopLandingSnapAdjustment(instance, player);
            }
            boolean rejectsZeroDistanceTopLanding = detectionDistY == 0
                    && (directTopLimit != null || rejectsZeroDistanceTopSolidLanding(instance));
            int overlapLimit = directTopLimit != null ? directTopLimit : 0x10;
            if (detectionDistY < 0 || detectionDistY >= overlapLimit || rejectsZeroDistanceTopLanding) {
                if (rejectsZeroDistanceTopLanding) {
                    notifyZeroDistanceTopSolidLandingRejected(instance, player);
                }
                return null;
            }
            // ROM: Solid_Landed uses narrow obActWid for NEW landings only.
            // When sticky (already riding), ROM uses ExitPlatform's full collision width.
            if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth, pieceIndex)) {
                return null;
            }
            if (apply) {
                // ROM loc_1E154 (sonic3k.asm:41646-41672). Upright: `subq.w #4,d3`,
                // `subq.w #1,y_pos(a1)`, `sub.w d3,y_pos(a1)` — a net `y - d3 + 3`, which
                // is the form here. Under the flag the ROM inserts `neg.w d3` and
                // `addq.w #2,y_pos(a1)` between them (:41663-41668), giving `y + d3 - 3`:
                // the same landing, taken against the object's other face. The asymmetry
                // between +3 and -3 is the ROM's own — the upright path's extra
                // `subq.w #1` is what makes the constant 3 rather than 4.
                int landingSnap = distY - 3 + getTopLandingSnapAdjustment(instance, player);
                // SolidObjCheckSloped/Sloped2 branch straight to loc_1E45A,
                // bypassing loc_1E44C's reverse-gravity test (S3K :42116-42160).
                // Their direct top-helper contract keeps the upright position
                // arithmetic even when the world flag is set. Flat top-solid
                // callers still select loc_1E4D6 through their normal path.
                boolean rawSlopedTopSurface = directTopLimit != null
                        && instance instanceof SlopedSolidProvider;
                int newCenterY = !rawSlopedTopSurface && isReverseGravityActive(player)
                        ? playerCenterY + landingSnap
                        : playerCenterY - landingSnap;
                int newY = newCenterY - (player.getHeight() / 2);
                player.setY((short) newY);
                // ROM: Solid_ResetFloor / PlatformObject loc_74DC unconditionally
                // sets these three values for ALL new platform landings, regardless
                // of air state. This handles terrain-to-platform transitions (air=false)
                // where the platform surface angle (0) differs from terrain angle.
                //   move.b  #0,obAngle(a1)
                //   move.w  #0,obVelY(a1)
                //   move.w  obVelX(a1),obInertia(a1)
                player.setAngle((byte) 0);
                player.setYSpeed((short) 0);
                player.setGSpeed(player.getXSpeed());
                // ROM: Sonic_ResetOnFloor is only called when Sonic is airborne
                // (btst #1,obStatus(a1) / beq.s .notinair). This clears the air
                // flag, resets rolling, and sets ground mode.
                if (player.getAir()) {
                    applyObjectLandingState(player, instance);
                }
                player.setOnObject(true);
            }
            return SolidContact.STANDING;
        }

        // ROM velocity classification adjustment:
        // In the ROM, Sonic (slot 0) runs his physics first within
        // ExecuteObjects — xSpeed is applied to position BEFORE other
        // objects execute SolidObject checks. Our engine runs objects
        // before physics, so solid checks see the pre-physics position.
        // Adjust the side-vs-topbottom classification to account for
        // the pending X velocity. Only applied when the adjustment
        // INCREASES absDistX (shifting Side→TopBottom), never when it
        // decreases it (TopBottom→Side), since we don't compensate
        // ySpeed/gravity which would offset the decrease in the ROM.
        //
        // Skip when postMovement=true (S1 UNIFIED post-movement pass):
        // the player's position already reflects their velocity, so
        // the adjustment would double-count and misclassify side contacts.
        int classifyAbsDistX = absDistX;
        int velocityAdjustX = postMovement ? 0 : (player.getXSpeed() >> 8);
        if (velocityAdjustX != 0) {
            int adjustedRelX = relX + velocityAdjustX;
            if (adjustedRelX >= 0 && adjustedRelX < halfWidth * 2) {
                int adjusted;
                if (adjustedRelX >= halfWidth) {
                    adjusted = (halfWidth * 2) - adjustedRelX;
                } else {
                    adjusted = adjustedRelX;
                }
                // Only apply when it increases absDistX (Side→TopBottom).
                if (adjusted > absDistX) {
                    classifyAbsDistX = adjusted;
                }
            }
        }

        // ROM: SolidObjectTop is a separate routine that ONLY checks for top
        // landing — it has no side/vertical classification at all (sonic3k.asm
        // SolidObjectTop_1P, lines 41793-41819).  Skip the side path entirely
        // for topSolidOnly so contacts always reach the vertical landing check.
        //
        // For SolidObjectFull (not topSolidOnly):
        // ROM: cmp.w d1,d5; b{hi|ls} <branch by game>
        //      cmpi.w #4,d1; b{ls|bls} <branch by game>
        //
        // Per-game divergence on the d1<=4 ("barely poking") boundary,
        // gated by CollisionRules#solidObjectBarelyPokingResolvesAsSide:
        //
        //   S3K (false): cmp d1,d5 / bhi.w loc_1E0D4 ; cmpi.w #4,d1 /
        //     bls.w loc_1E0D4 — when d5<=d1 AND d1<=4, ROM goes to the
        //     TOP/BOTTOM path (loc_1E0D4, sonic3k.asm:41503-41506,
        //     41541-41546). Falls through to the vertical landing path.
        //
        //   S1/S2 (true): cmp d1,d5 / bhi <TopBottom> ; cmpi.w #4,d1 /
        //     bls.s <SideAir> — when d5<=d1 AND d1<=4, ROM goes to
        //     SolidObject_SideAir / Solid_SideAir (s2.asm:35404-35412;
        //     s1disasm/_incObj/sub SolidObject.asm:181-184). SideAir
        //     (s2.asm:35447-35453, s1 SolidObject.asm:211-214) does
        //     bsr Solid_NotPushing then returns moveq #1,d4 — a SIDE
        //     contact with NO position correction (no sub.w d0,x_pos) and
        //     NO x_vel/inertia zeroing. The comment in the ROM explains
        //     this lets the player walk over objects barely poking out of
        //     the ground; it also lets MTZ Obj66 Spring Wall see
        //     touchSide()=true while in_air and fire its -$800,-$800
        //     diagonal bounce (s2.asm:53221-53232, loc_2704C at
        //     s2.asm:53283-53340). Handled as a dedicated early return so
        //     the full LeftRight side-effect path (which DOES correct
        //     position and zero speed) is not entered.
        if (!topSolidOnly
                && solidObjectBarelyPokingResolvesAsSide(player)
                && classifyAbsDistX <= absDistY
                && absDistY <= 4) {
            // SolidObject_SideAir: clear push, no position/speed change.
            // distX is the signed horizontal distance to the nearer edge;
            // movingInto mirrors Solid_Left/Solid_Right intent but is NOT
            // acted on here (SideAir skips StopCharacter entirely).
            boolean leftSide = relX <= halfWidth;
            boolean movingInto = isMovingIntoSideContact(player, instance, leftSide);
            if (apply
                    && isSignedObjectControlSideContactRejected(player, instance)) {
                return null;
            }
            // pushing=false: SideAir routes through Solid_NotPushing, which
            // clears the push flags (s2.asm:35477-35482).
            return SolidContact.side(false, distX, movingInto);
        }

        // ROM: SolidObject_LeftRight side resolution (S1/S2/S3K) when the
        // horizontal penetration does not exceed the vertical and the
        // vertical penetration is greater than 4. This is the pushing /
        // stop-character path with position correction.
        if (!topSolidOnly && classifyAbsDistX <= absDistY && absDistY > 4) {
            if (instance != null
                    && InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()).objectId() == OBJ85_ID
                    && InitialObjectDispatchController.afterCallback(initialDispatch, instance.getSpawn()).subtype() == 0
                    && player.getYSpeed() >= 0) {
                int anchorX = playerCenterX - (relX - halfWidth);
                int anchorY = playerCenterY - (relY - 4 - maxTop);
                // S2 Obj85 vertical capture calls SolidObject_Always before
                // it writes rolling/y_radius=$E (s2.asm:57531-57538). Even
                // if the engine still has pinball/rolling state from a
                // previous launcher, this landing geometry must use the
                // standing radius that Solid_ResetFloor would have restored.
                int landingMaxTop = maxTop + getTopLandingSnapAdjustment(instance, player);
                int landingRelY = playerCenterY - anchorY + 4 + landingMaxTop;
                int landingThreshold = 0x14; // ROM: Obj85 uses 0x14 in SolidObject_TopBottom
                int landingFrame = 0;
                int landingPrevRelX = 0;
                int landingPrevRelY = 0;
                boolean landingCrossedTop = false;
                int[] prevRelX = new int[3];
                int[] prevRelY = new int[3];
                for (int framesBehind = 1; framesBehind <= 3; framesBehind++) {
                    short prevCenterX = player.getCentreX(framesBehind);
                    short prevCenterY = player.getCentreY(framesBehind);
                    int idx = framesBehind - 1;
                    prevRelX[idx] = prevCenterX - anchorX + halfWidth;
                    prevRelY[idx] = prevCenterY - anchorY + 4 + landingMaxTop;
                    if (landingFrame == 0
                            && prevRelX[idx] >= 0 && prevRelX[idx] <= halfWidth * 2
                            && prevRelY[idx] < landingThreshold) {
                        boolean withinTop = prevRelY[idx] >= 0 && prevRelY[idx] <= landingMaxTop;
                        boolean crossedTop = prevRelY[idx] < 0 && landingRelY >= 0;
                        if (!withinTop && !crossedTop) {
                            continue;
                        }
                        landingFrame = framesBehind;
                        landingPrevRelX = prevRelX[idx];
                        landingPrevRelY = prevRelY[idx];
                        landingCrossedTop = crossedTop;
                    }
                }
                if (landingFrame > 0) {
                    // ROM: Solid_Landed narrow width only for NEW landings
                    if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth, pieceIndex)) {
                        return null;
                    }
                    if (apply) {
                        int newCenterY = anchorY - landingMaxTop - 1;
                        int newY = newCenterY - (player.getHeight() / 2);
                        player.setY((short) newY);
                        // ROM: Solid_ResetFloor unconditionally sets these.
                        player.setAngle((byte) 0);
                        player.setYSpeed((short) 0);
                        player.setGSpeed(player.getXSpeed());
                        if (player.getAir()) {
                            applyObjectLandingState(player, instance);
                        }
                        player.setOnObject(true);
                    }
                    return SolidContact.STANDING;
                }
            }

            // Determine which side player is on based on relX, not distX.
            // When distX=0 (at exact edge), distX>0 would be false for both sides,
            // causing incorrect movingInto detection for left side pushes.
            boolean leftSide = relX <= halfWidth;
            // ROM loc_1E06E sets Status_Push for any grounded side contact
            // after applying the side separation. Only speed zeroing is
            // gated by moving into the object (sonic3k.asm:41513-41535).
            boolean movingInto = isMovingIntoSideContact(player, instance, leftSide);
            boolean pushing = !player.getAir();
            // ROM: sub SolidObject.asm lines 173-196
            // When d0==0 (distX==0), ROM branches to Solid_Centre which does
            // "sub.w d0,obX(a1)" (no-op) — NO speed zeroing, NO position change.
            // When d0!=0 AND movingInto, ROM hits Solid_Left which zeros obInertia
            // and obVelX, then falls through to Solid_Centre (position correction).
            // When d0!=0 AND NOT movingInto, ROM goes to Solid_Centre directly
            // (position correction only, no speed zeroing).
            if (instance instanceof SolidObjectProvider solidProvider
                    && sideContactProviderReturnsNoContact(player, instance, pieceIndex, solidProvider)) {
                return null;
            }
            if (apply
                    && isSignedObjectControlSideContactRejected(player, instance)) {
                return null;
            }
            if (apply) {
                if (postMovement) {
                    // Post-movement pass (S1 UNIFIED): ROM-accurate behavior.
                    // ROM: Solid_Left zeros speed BEFORE the airborne check.
                    // Speed is zeroed when movingInto regardless of air state.
                    // The airborne check only affects the PUSH flag, not speed.
                    // The shared helper zeroes x_vel for moving side contact
                    // across games; object-local launch routines must finish
                    // before this post-movement stop can apply.
                    boolean airborneSpecialTouchEdge = instance instanceof SolidObjectProvider solidProvider
                            && InitialObjectDispatchController.afterCallback(initialDispatch, solidProvider.preservesPostSpecialTouchAirborneSideVelocity())
                            && objectManager.hadSpecialTouchThisFrame(player)
                            && player.getAir()
                            && distX == (player.getXSpeed() >> 8);
                    if (distX != 0 && movingInto) {
                        if (!airborneSpecialTouchEdge) {
                            player.setXSpeed((short) 0);
                        }
                        player.setGSpeed((short) 0);
                    }
                    if (distX != 0) {
                        if (projectedPreMovementX) {
                            writeNativePlayerCentreX(player, playerCenterX - distX);
                        } else {
                            player.move((short) (-distX * 256), (short) 0);
                        }
                    }
                } else {
                    // Pre-movement pass (S2/S3K): compensate for batched processing architecture.
                    // Push-driven objects (preserveSubpixels=true) skip the distX==0 block
                    // to preserve ROM push cadence.
                    boolean preserveSubpixels = preservesEdgeSubpixelMotion(instance);
                    boolean preserveZeroDistanceMotion = instance instanceof SolidObjectProvider provider
                            && InitialObjectDispatchController.afterCallback(initialDispatch, provider.preservesZeroDistanceSideContactMotion());
                    if (distX == 0 && !preserveSubpixels && !preserveZeroDistanceMotion) {
                        writeNativePlayerCentreX(player, playerCenterX);
                        if (movingInto) {
                            player.setXSpeed((short) 0);
                            player.setGSpeed((short) 0);
                        }
                    }
                    if (distX != 0 && movingInto) {
                        player.setXSpeed((short) 0);
                        player.setGSpeed((short) 0);
                    }
                    if (distX != 0) {
                        if (preserveSubpixels) {
                            player.move((short) (-distX * 256), (short) 0);
                        } else {
                            writeSnappedPlayerCentreX(player, playerCenterX - distX);
                        }
                    }
                }
            }
            return SolidContact.side(pushing, distX, movingInto);
        }

        if (distY >= 0 || (sticky && distY >= -16)) {
            boolean upwardVelocity = player.getYSpeed() < 0;
            // ROM divergence (S3K loc_1E154 vs S1/S2 Solid_Landed),
            // gated by
            // CollisionRules#solidObjectTopBranchAlwaysLiftsOnUpwardVelocity:
            //   S3K loc_1E154 (sonic3k.asm:41646-41672) writes the position
            //   lift (subq.w #1, y_pos(a1) at 41617; sub.w d3, y_pos(a1) at
            //   41624) BEFORE testing tst.w y_vel(a1) / bmi.s loc_1E198 at
            //   41625-41626.  When y_vel < 0 it skips RideObject_SetRide
            //   and returns d4=0 (no contact), but the lift has already
            //   been applied.  CNZ trace F7614 exercises this when
            //   Tails_Jump (sonic3k.asm:28559+) sets y_vel=-$680 on the
            //   same frame Obj_Spring_Horizontal (sonic3k.asm:47816+)
            //   reaches loc_1E154 with d3=1 against Tails.
            //   S1 Solid_Landed (s1disasm/_incObj/sub SolidObject.asm:278)
            //   and S2 SolidObject_Landed (s2.asm:35589-35590) test y_vel
            //   FIRST and branch to SolidObject_Miss with no lift.
            //
            // ROM only reaches loc_1E154 via SolidObjectFull2_1P's
            // SolidObject_cont branch when the object's a0.d6 standing-bit
            // is CLEAR (sonic3k.asm:41106-41107 btst d6 / beq cont).  When
            // the bit is still set from a previous landing and the player
            // is now airborne (e.g. AIZ F2090 yellow up-spring kick:
            // sub_22F98 sets Status_InAir at sonic3k.asm:47763 but does
            // not touch a0.d6), the next frame's SolidObjectFull2_1P
            // routes through loc_1DCF0 (sonic3k.asm:41119-41124) which
            // clears d6 and returns d4=0 without ever reaching
            // loc_1E154.  The engine mirrors that ROM bit via
            // objectStandingBitSet (set by RideObject_SetRide-equivalent
            // STANDING contacts, cleared at the top of
            // processInlineObjectForPlayer when the object's own pass
            // sees the bit set + air), and snapshots the pre-clear value
            // into objectStandingBitSnapshot so the gate below observes
            // ROM's "bit was set at routine entry" semantics.
            boolean objectStandingBitWasSet =
                    contactState.wasObjectStandingBitSetThisFrame(player, instance, pieceIndex)
                            || contactState.hasObjectStandingBit(player, instance, pieceIndex);
            boolean s3kAlwaysLifts =
                    !topSolidOnly
                            && topBranchAlwaysLiftsOnUpwardVelocity(player)
                            && !objectStandingBitWasSet;
            if (upwardVelocity && !s3kAlwaysLifts) {
                return null;
            }

            // fixBugs (s2.asm:27 `fixBugs = 0`): the upward-velocity rejection below is
            // unconditional on the shipped ROM -- `tst.w y_vel(a1) / bmi.s ..._Miss` in
            // SolidObject_Landed (s2.asm:35615-35616) and the same test in
            // PlatformObject11_cont, PlatformObject_cont, SlopedPlatform_cont and
            // PlatformObject2_cont (s2.asm:35920, 36086, 36056, 36095). The fixBugs=1
            // branch (s2.asm:35599-35619 and the mirrors at 35909-35924, 35935-35950,
            // 36045-36060, 36084-36099) skips the velocity test entirely when the player
            // is on the ground with an angle inside [-$10,$10], so a shallow slope meeting
            // a wall can no longer let HTZ's rising floor pass through the player. The
            // engine models the shipped branch: no angle bypass anywhere in this family.
            if (topSolidOnly && player.getYSpeed() < 0) {
                return null;
            }
            if (topSolidOnly && !sticky
                    && instance instanceof SolidObjectProvider provider
                    && InitialObjectDispatchController.afterCallback(initialDispatch, provider.gatesNewTopSolidLandingWithPreviousPosition())) {
                int prevCenterX = player.getCentreX(1);
                int prevCenterY = player.getCentreY(1);
                int anchorX = playerCenterX - (relX - halfWidth);
                int anchorY = playerCenterY - (relY - 4 - maxTop);
                int prevRelX = prevCenterX - anchorX + halfWidth;
                int prevRelY = prevCenterY - anchorY + 4 + maxTop;
                int width2 = halfWidth * 2;
                if (prevRelX < 0 || prevRelX >= width2 || prevRelY < 0 || prevRelY >= 0x10) {
                    return null;
                }
            }

            // ROM: SolidObjectFull (s2.asm:35298) uses "cmpi.w #$10,d3; blo
            // Solid_Landed" — signed branch semantics, landing range d3 in
            // [0, 15] (equivalently engine distY in [0, 15]).
            // PlatformObject differs per game on the exact boundary:
            // S2's PlatformObject_ChkYRange excludes d0==0, which maps to
            // distY==0 here, while the current S1/S3K shared path keeps that
            // boundary as a valid landing.
            boolean rejectsZeroDistanceTopLanding = topSolidOnly
                    && distY == 0
                    && rejectsZeroDistanceTopSolidLanding(instance);
            if (topSolidOnly
                    ? distY > 0x10
                            || (!allowsZeroDistTopSolidLanding(player)
                                    && !providerAllowsZeroDistanceTopSolidLanding(instance, player)
                                    && distY == 0)
                            || rejectsZeroDistanceTopLanding
                    : distY >= 0x10) {
                if (rejectsZeroDistanceTopLanding) {
                    notifyZeroDistanceTopSolidLandingRejected(instance, player);
                }
                return null;
            }
            // ROM: SolidObject_Landed re-reads the narrower obActWid for NEW landings,
            // including the SlopedSolid_cont path after it falls through
            // SolidObject_ChkBounds -> SolidObject_TopBottom -> SolidObject_Landed.
            // SolidObjectTop uses full d1 (same as the initial collision box we
            // already passed), so no further width check is needed there. Sticky
            // riders follow the MvSonicOnPtfm / MvSonicOnSlope path instead.
            //
            // ROM: loc_1E154 (sonic3k.asm:41648-41656) re-checks the X
            // overlap against width_pixels(a0) before applying the lift,
            // so the upward-velocity-and-lift S3K path also needs this
            // narrow gate.  Sticky riders skip this on the standing branch
            // because ExitPlatform's full collision width is already in
            // effect; for the upward-velocity branch the player is by
            // definition not standing on the object so sticky doesn't apply.
            if (useTopLandingWidth && !sticky && !topSolidOnly
                    && !isWithinTopLandingWidth(instance, player, relX, halfWidth, pieceIndex)) {
                // ROM loc_1E154 branches to loc_1E198 here. Unlike the ordinary
                // no-overlap path at loc_1E0A2, this does not call sub_1E0C2, so
                // a previously set native pushing bit remains authoritative.
                lastContactMissPreservesPushLatch = true;
                return null;
            }

            if (apply) {
                // ROM loc_1E154 (sonic3k.asm:41646-41672). Upright: `subq.w #4,d3`,
                // `subq.w #1,y_pos(a1)`, `sub.w d3,y_pos(a1)` — a net `y - d3 + 3`, which
                // is the form here. Under the flag the ROM inserts `neg.w d3` and
                // `addq.w #2,y_pos(a1)` between them (:41663-41668), giving `y + d3 - 3`:
                // the same landing, taken against the object's other face. The asymmetry
                // between +3 and -3 is the ROM's own — the upright path's extra
                // `subq.w #1` is what makes the constant 3 rather than 4.
                int landingSnap = distY - 3 + getTopLandingSnapAdjustment(instance, player);
                int newCenterY = isReverseGravityActive(player)
                        ? playerCenterY + landingSnap
                        : playerCenterY - landingSnap;
                int newY = newCenterY - (player.getHeight() / 2);
                player.setY((short) newY);
                if (upwardVelocity) {
                    // ROM: loc_1E154 path with tst.w y_vel(a1) / bmi.s
                    // loc_1E198 (sonic3k.asm:41680-41681) -> moveq #0,d4 /
                    // rts at 41636-41637.  The position lift fires but
                    // RideObject_SetRide does not, so we leave angle,
                    // y_vel, ground_vel, on_object, in_air, rolling, and
                    // ground_mode untouched.  Reflect this with a "no
                    // contact" return so the SolidContact pipeline (push
                    // bookkeeping, riding state, latched solid object)
                    // matches ROM's d4=0 outcome.
                    return null;
                }
                // ROM: Solid_ResetFloor / PlatformObject loc_74DC unconditionally
                // sets angle, ySpeed, and gSpeed for ALL platform landings.
                player.setAngle((byte) 0);
                player.setYSpeed((short) 0);
                player.setGSpeed(player.getXSpeed());
                if (player.getAir()) {
                    LOGGER.fine(() -> "Solid object landing at (" + player.getX() + "," + player.getY() +
                        ") distY=" + distY);
                    applyObjectLandingState(player, instance);
                }
                // ROM: bset #status.player.on_object (s2.asm:35739)
                player.setOnObject(true);
                // ROM: RideObject_SetRide also sets the object's
                // a0.d6 standing-bit (sonic3k.asm:42074 bset d6,
                // status(a0)). Mirror that here so a subsequent
                // air-unseat correctly routes through the no-lift
                // path on the next frame.
                if (instance != null) {
                    contactState.setObjectStandingBit(player, instance, pieceIndex);
                    // ROM SolidObjectFull evaluates each object once per frame:
                    // the air-unseat (loc_1DC98 / loc_1DCF0) that clears
                    // Status_OnObj can only observe the bit on a SUBSEQUENT
                    // frame, never on this RideObject_SetRide frame. Latch the
                    // fresh landing so a same-frame jump-off (Tails_Jump sets
                    // Status_InAir without clearing Status_OnObj,
                    // sonic3k.asm:28593-28594) does not get unseated this frame.
                    markStandingBitEstablishedThisFrame(player, instance, pieceIndex);
                }
            } else if (upwardVelocity) {
                // apply=false geometry probe (collision sensor / debug):
                // ROM returns d4=0 (no contact) when y_vel < 0 in the lift
                // branch.  Surface that as null to keep probe semantics
                // consistent with the apply=true branch above.
                return null;
            }
            return SolidContact.STANDING;
        }

        if (topSolidOnly) {
            return null;
        }

        // ROM: SolidObject_InsideBottom (s2.asm:35486-35512)
        // When y_vel == 0 and player is on ground, the ROM branches to SolidObject_Squash
        // which checks horizontal overlap and kills the player if sandwiched.
        if (!player.getAir() && (player.getYSpeed() == 0
                || (instance instanceof SolidObjectProvider provider
                    && InitialObjectDispatchController.afterCallback(initialDispatch, provider.groundedBottomContactAlwaysSquashes())))) {
            // ROM: SolidObject_Squash (s2.asm:35533-35558)
            // mvabs.w d0,d4; cmpi.w #$10,d4; blo.w SolidObject_LeftRight
            // If player is near the horizontal edge (absDistX < 16), push sideways instead.
            if (absDistX < 0x10) {
                boolean leftSide = relX <= halfWidth;
                boolean movingInto = isMovingIntoSideContact(player, instance, leftSide);
                boolean groundedSquashEdgePush = instance instanceof SolidObjectProvider provider
                        && InitialObjectDispatchController.afterCallback(initialDispatch, provider.groundedSquashEdgeSideContactSetsPush());
                boolean pushing = !player.getAir() && (movingInto || groundedSquashEdgePush);
                if (apply) {
                    if (postMovement) {
                        if (distX != 0 && movingInto) {
                            player.setXSpeed((short) 0);
                            player.setGSpeed((short) 0);
                        }
                        if (distX != 0) {
                            player.move((short) (-distX * 256), (short) 0);
                        }
                    } else {
                        if (movingInto) {
                            player.setXSpeed((short) 0);
                            player.setGSpeed((short) 0);
                        }
                        if (distX != 0) {
                            player.setCentreX((short) (playerCenterX - distX));
                        }
                    }
                }
                return SolidContact.side(pushing, distX, movingInto);
            }
            // Player is well inside horizontally - crush death.
            // ROM: KillCharacter (s2.asm:85671) - unconditional death.
            if (apply) {
                LOGGER.fine(() -> "SolidObject crush: player sandwiched (absDistX=" + absDistX + ")");
                player.applyCrushDeath();
            }
            return SolidContact.CEILING;
        }

        // S1/S2 Solid_Below correct only a player moving upward into the object.
        // S3K loc_1E0E0 instead routes every airborne bottom overlap through
        // loc_1E0F6/loc_1E0FC: it clears ground speed, applies the vertical
        // separation, and zeros y_vel even when y_vel is zero or positive
        // (sonic3k.asm:41598-41617). This is a game-wide shared-solid branch,
        // owned by CollisionRules rather than an MGZ carrier exception.
        boolean alwaysSeparatesAirBottomHit = alwaysSeparatesAirBottomSolidHit(player);
        if (apply && (player.getYSpeed() < 0 || alwaysSeparatesAirBottomHit)) {
            // ROM loc_1E0FC (sonic3k.asm:41609-41614): `neg.w d3` under the flag, then the
            // same `sub.w d3,y_pos(a1)`. The separation is the same size; it points the
            // other way, because the object's "underside" is its top when gravity is
            // inverted.
            int newCenterY = isReverseGravityActive(player)
                    ? playerCenterY + distY
                    : playerCenterY - distY;
            int newY = newCenterY - (player.getHeight() / 2);
            player.setY((short) newY);
            LOGGER.fine(() -> "Solid object ceiling hit, zeroing ySpeed from " + player.getYSpeed());
            if (clearsGroundSpeedOnAirBottomSolidHit(player)) {
                player.setGSpeed((short) 0);
            }
            player.setYSpeed((short) 0);
        }
        return SolidContact.CEILING;
    }

    private void writeNativePlayerCentreX(PlayableEntity player, int centreX) {
        if (player instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        } else {
            player.setCentreX((short) centreX);
        }
    }

    private void writeSnappedPlayerCentreX(PlayableEntity player, int centreX) {
        if (player instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.writeXPosResetSubpixel(sprite, centreX);
        } else {
            player.setCentreX((short) centreX);
        }
    }

    private boolean sideContactProviderReturnsNoContact(PlayableEntity player, ObjectInstance instance,
            int pieceIndex, SolidObjectProvider provider) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!InitialObjectDispatchController.afterCallback(initialDispatch, provider.sideContactReturnsNoContact(player))) {
            return false;
        }
        if (instance instanceof MultiPieceSolidProvider multiPiece
                && InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.usesPieceScopedStandingBits())) {
            return contactState.hasObjectStandingBit(player, instance, pieceIndex)
                    || contactState.wasObjectStandingBitSetThisFrame(player, instance, pieceIndex)
                    // Preserve ROM airborne inward-side no-contact when a real
                    // leftward speed was already present at contact entry
                    // (SolidObject_InsideLeft -> AtEdge -> SideAir), and add
                    // only the folded Obj70 zero-speed stale-latch case. ROM
                    // Obj70 keeps each tooth in a real SST slot, so a tooth's
                    // standing-bit air-unseat can return before a folded sibling
                    // can run SolidObject_StopCharacter (docs/s2disasm/s2.asm:
                    // 35413-35436,55084-55191,35021-35040).
                    || (player.getAir() && !isHurtRoutineSideStopCandidate(player)
                            && ((preContactXSpeed <= -0x100 && player.getXSpeed() < 0)
                                    || (player.getXSpeed() <= 0
                                            && hasAnyMultiPieceStandingBitOrSnapshot(player, instance, multiPiece))))
                    || (!player.getAir() && preContactXSpeed <= -0x100 && player.getXSpeed() < 0);
        }
        return true;
    }

    private boolean isHurtRoutineSideStopCandidate(PlayableEntity player) {
        // S2 Obj02_Hurt runs ObjectMove/gravity, then Tails_DoLevelCollision,
        // and only clears x_vel after landing (s2.asm:41063-41110). If hurt
        // Tails is still airborne, Obj70's clear-d6 standing branch is not the
        // source of the stop; a clear-bit side overlap must reach
        // SolidObject_StopCharacter instead (s2.asm:35413-35436, 55189).
        return player instanceof AbstractPlayableSprite sprite && sprite.isHurt();
    }

    private boolean isWithinTopLandingWidth(ObjectInstance instance, PlayableEntity player, int relX,
            int collisionHalfWidth, int pieceIndex) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(instance instanceof SolidObjectProvider provider)) {
            return true;
        }

        boolean pieceConfigured = pieceIndex >= 0
                && provider instanceof MultiPieceSolidProvider multiPiece
                && InitialObjectDispatchController.afterCallback(initialDispatch, multiPiece.usesPieceSpecificLandingHalfWidths());
        int configuredHalfWidth = pieceConfigured
                ? InitialObjectDispatchController.afterCallback(initialDispatch, ((MultiPieceSolidProvider) provider).getPieceLandingHalfWidth(pieceIndex))
                : InitialObjectDispatchController.afterCallback(initialDispatch, provider.getTopLandingHalfWidth(player, collisionHalfWidth));
        int allowedHalfWidth;
        if (InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile()).usesCollisionHalfWidthForTopLanding()) {
            // Caller's d1 already equals the object's own width byte (e.g. S2
            // Obj70 passes d1 = width_pixels = $10, s2.asm:55111, 35588-35620).
            allowedHalfWidth = collisionHalfWidth;
        } else {
            // ROM's landing clamp re-reads the object's own width byte, not the
            // caller's collision d1: S1 Solid_Landed re-reads obActWid
            // (_incObj/sub SolidObject.asm:318-336), S2 SolidObject_Landed and
            // S3K Solid_Landed / loc_1E154 re-read width_pixels(a0)
            // (s2.asm:35588+, sonic3k.asm:41651-41661). The provider models that
            // field first-class: its family default reconstructs the width byte
            // (full-solid d1 = width_pixels + $B; top-solid d1 used directly per
            // SolidObjectTop_1P, sonic3k.asm:41838-41865), and objects whose
            // caller broke the idiom override with the ObjDat width — used
            // verbatim, narrower OR wider (e.g. the MHZ1 cutscene button's
            // d1 = $1B against width_pixels = $80).
            allowedHalfWidth = configuredHalfWidth;
        }

        // ROM: Solid_Landed checks: d1 = playerX + obActWid - objX,
        // bmi.s Solid_Miss (d1 < 0), cmp.w d2,d1 / bhs.s Solid_Miss (d1 >= width*2).
        // Range for d1: [0, allowedHalfWidth*2) — inclusive left, exclusive right.
        // Using abs() would be symmetric and include the right boundary, causing
        // false landings at the exact right edge.
        int xFromCenter = relX - collisionHalfWidth;
        int d1 = xFromCenter + allowedHalfWidth;
        return d1 >= 0 && d1 < allowedHalfWidth * 2;
    }

    private boolean usesUnifiedCollisionModel(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return rules != null && rules.collisionModel() == CollisionModel.UNIFIED;
    }

    private boolean allowsZeroDistTopSolidLanding(PlayableEntity player) {
        if (player == null) {
            return true;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return rules == null || rules.topSolidLandingAllowsZeroDist();
    }

    private Integer directTopLandingOverlapLimit(ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (instance instanceof TopSolidLandingWindowProvider provider) {
            return InitialObjectDispatchController.afterCallback(initialDispatch,
                    provider.getDirectTopLandingOverlapLimit());
        }
        if (instance instanceof SlopedSolidProvider provider) {
            return InitialObjectDispatchController.afterCallback(initialDispatch,
                    provider.getDirectTopLandingOverlapLimit());
        }
        return null;
    }

    private boolean rejectsZeroDistanceTopSolidLanding(ObjectInstance instance) {
        return rejectsZeroDistanceTopSolidLanding(instance, currentPlayer);
    }

    private boolean rejectsZeroDistanceTopSolidLanding(ObjectInstance instance, PlayableEntity player) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        return instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.rejectsZeroDistanceTopSolidLanding(player));
    }

    private boolean providerAllowsZeroDistanceTopSolidLanding(ObjectInstance instance, PlayableEntity player) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        return instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.allowsZeroDistanceTopSolidLanding(player));
    }

    private void notifyZeroDistanceTopSolidLandingRejected(ObjectInstance instance, PlayableEntity player) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (instance instanceof SolidObjectProvider provider) {
            provider.onRejectedZeroDistanceTopSolidLanding(player);
            InitialObjectDispatchController.requireCurrent(initialDispatch);
        }
    }

    private void clearDyingPlayerNativeSolidPush(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        var services = objectManager.services();
        var module = services == null ? null : services.gameModule();
        var policy = module == null ? null : module.getGameService(
                com.openggf.game.internal.DyingSolidPushPolicy.class);
        if (policy == null || !policy.clearsPushOnDyingSolidPass()
                || !(instance instanceof SolidObjectProvider provider)) return;
        var profile = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile());
        if (profile.topSolidOnly() || profile.monitorSolidity()
                || instance instanceof SlopedSolidProvider
                || InitialObjectDispatchController.afterCallback(initialDispatch, instance.isSkipSolidContactThisFrame())
                || !InitialObjectDispatchController.afterCallback(initialDispatch, provider.isSolidFor(player))
                || suppressesSolidPassThisFrame(instance, player)
                || shouldSkipOffscreenSidekickFullSolid(player, instance, profile)) return;
        // S1 Solid_Collision takes Solid_NoCollision in REV00, even on-screen;
        // REV01 takes Solid_Debug and leaves the push latch untouched
        // (_incObj/sub SolidObject.asm:185-190,253-263; FixBugs=0).
        if (contactState.clearObjectPushingBit(player, instance)) {
            publishSolidPushReleaseAnimationWord(player, instance);
            player.setPushing(false);
            {
                provider.setPlayerPushing(player, false);
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        }
    }

    private void clearDeadPlayerOffscreenPush(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(instance instanceof SolidObjectProvider provider)) {
            return;
        }
        SolidRoutineProfile profile = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile());
        if (!isSolidObjectOffscreenGateEnabled(player)
                || profile.bypassesOffscreenSolidGate()
                || profile.topSolidOnly()
                || profile.monitorSolidity()
                || instance instanceof SlopedSolidProvider
                || !InitialObjectDispatchController.afterCallback(initialDispatch, provider.isSolidFor(player))
                || suppressesSolidPassThisFrame(instance, player)
                || shouldSkipOffscreenSidekickFullSolid(player, instance, profile)
                || InitialObjectDispatchController.afterCallback(initialDispatch, instance.isWithinSolidContactBounds())) {
            return;
        }
        // Retail SolidObjectFull checks the object's render bounds before
        // routine >= 6. Its offscreen loc_1DF88 -> loc_1E0A2 tail still writes
        // WORD #1 to anim when this object's push bit survives Kill_Character
        // (sonic3k.asm:41327-41356,41557-41568; FixBugs=0).
        // S1 SolidObject and S2 SolidObject_OnScreenTest use the same ordering.
        if (contactState.clearObjectPushingBit(player, instance)) {
            publishSolidPushReleaseAnimationWord(player, instance);
            player.setPushing(false);
            {
                provider.setPlayerPushing(player, false);
                InitialObjectDispatchController.requireCurrent(initialDispatch);
            }
        }
    }

    private boolean clearDeadPlayerStaleStandingBit(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        Ride ridingState = player != null ? contactState.rideFor(player) : null;
        boolean rideRecordOwnsInstance = ridingState != null && ridingState.object() == instance;
        boolean standingBitSet = player != null && instance != null
                && contactState.hasObjectStandingBit(player, instance);
        if (player == null
                || instance == null
                || !player.getDead()
                || !player.getAir()
                || (!standingBitSet && !rideRecordOwnsInstance)
                || !(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        SolidRoutineProfile solidProfile = InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile());
        if (shouldSkipOffscreenSidekickFullSolid(player, instance, solidProfile)
                || suppressesSolidPassThisFrame(instance, player)) {
            return false;
        }

        // S2 Obj6E calls the shared SolidObject helper after updating position
        // (docs/s2disasm/s2.asm:54979-55010). SolidObject still checks on-screen
        // Player 2 even when Tails is in Obj02_Dead; if the object's standing
        // bit is set and Status_InAir is already set, it clears Status_OnObj and
        // the object's P2 standing bit before returning (s2.asm:35022-35044).
        // The explicit ride record is the folded runtime's equivalent of the
        // native object's standing ownership. An earlier solid checkpoint may
        // already have consumed the emulated d6 latch, while the later ridden
        // solid still executes after Kill_Character in this same object pass.
        // Native SolidObjectFull_1P loc_1DC98 clears Status_OnObj when that
        // later owner observes Status_InAir (sonic3k.asm:41057-41075).
        if (standingBitSet) {
            contactState.rememberAndClearStandingBit(player, instance, -1);
        }
        contactState.clearRidingObject(player);
        player.setOnObject(false);
        player.setAir(true);
        return true;
    }

    private boolean clearsGroundSpeedOnAirBottomSolidHit(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return player.getAir()
                && rules != null
                && rules.air() != null
                && rules.air().bottomSolidHitClearsGroundSpeed();
    }

    private boolean alwaysSeparatesAirBottomSolidHit(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return player.getAir()
                && rules != null
                && rules.air() != null
                && rules.air().bottomSolidHitAlwaysSeparates();
    }

    private boolean usesCurrentYRadiusOnlyForFullSolidBottomOverlap(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return rules != null && rules.fullSolidBottomOverlapUsesCurrentYRadiusOnly();
    }

    private boolean isSolidObjectOffscreenGateEnabled(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return rules != null && rules.solidObjectOffscreenGate();
    }

    private boolean shouldSkipOffscreenSidekickFullSolid(PlayableEntity player,
                                                         ObjectInstance instance,
                                                         SolidRoutineProfile solidProfile) {
        if (!(player instanceof AbstractPlayableSprite sidekick) || !sidekick.isCpuControlled()) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(sidekick);
        if (rules == null || !rules.solidObjectRequiresSidekickOnScreen()) {
            return false;
        }
        if (solidProfile.bypassesOffscreenSolidGate()
                || solidProfile.topSolidOnly()
                || instance instanceof SlopedSolidProvider) {
            return false;
        }
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isVisibleForRenderFlag(sidekick);
        if (onScreen) {
            return false;
        }
        // ROM: S2 SolidObject tests Sidekick render_flags.on_screen and
        // returns before the P2 solid pass when clear
        // (docs/s2disasm/s2.asm:34800-34804). S3K SolidObjectFull does the
        // same for Player_2 before adding the P2 standing-bit delta
        // (docs/skdisasm/sonic3k.asm:41046-41050). This gate belongs only
        // to the regular full-solid helper; SolidObjectFull2/SolidObject_Always,
        // top-only, and sloped helpers enter their P2 routines directly.
        return true;
    }

    private boolean shouldSkipRidingAirUnseatForOffscreenSidekick(PlayableEntity player,
                                                                   ObjectInstance ridingObject) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(ridingObject instanceof SolidObjectProvider provider)) {
            return false;
        }
        return shouldSkipOffscreenSidekickFullSolid(player, ridingObject, InitialObjectDispatchController.afterCallback(initialDispatch, provider.getSolidRoutineProfile()));
    }

    /**
     * ROM {@code Reverse_gravity_flag} ($FFFFF7C6), read for the solid-object rows.
     *
     * <p>{@code SolidObject_cont} (sonic3k.asm:41443-41464), {@code sub_1E0C2}'s
     * {@code loc_1E0FC} (:41609-41613) and {@code loc_1E154} (:41663-41668),
     * {@code MvSonicOnPtfm} (:41688-41693) branch on it. In {@code sub_1E410},
     * {@code loc_1E44C} (:42038-42040) chooses the reverse-gravity target
     * {@code loc_1E4D6} (:42093-42113). The flag is the whole
     * gate — the ROM never checks the zone — so this stays engine-internal state rather
     * than a {@code GameRules} member, and S1 and S2 (which never set it) are unaffected.
     */
    private static boolean isReverseGravityActive(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return false;
        }
        GameStateManager gameState = sprite.currentGameStateOrNull();
        return gameState != null && gameState.isReverseGravityActive();
    }

    private boolean isVisibleForRenderFlag(AbstractPlayableSprite sprite) {
        Camera currentCamera = sprite.currentCamera();
        return currentCamera != null && currentCamera.isVisibleForRenderFlag(sprite);
    }

    /**
     * ROM: {@code loc_1E154} (sonic3k.asm:41646-41672) writes the position
     * lift before testing {@code y_vel}; {@code Solid_Landed} /
     * {@code SolidObject_Landed} (S1/S2) test {@code y_vel} first and bail
     * without lifting. Gated via
     * {@link CollisionRules#solidObjectTopBranchAlwaysLiftsOnUpwardVelocity()}.
     */
    private boolean topBranchAlwaysLiftsOnUpwardVelocity(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return rules != null && rules.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity();
    }

    /**
     * ROM: {@code SolidObject_cont} routes a barely-poking overlap
     * ({@code d5 <= d1} with {@code d1 <= 4}) differently per game. S1/S2
     * send it to {@code SolidObject_SideAir} / {@code Solid_SideAir}, which
     * returns {@code moveq #1,d4} — a SIDE contact
     * (s2.asm:35404-35412,35447-35453;
     * s1disasm/_incObj/sub SolidObject.asm:181-184,211-214). S3K sends it to
     * {@code loc_1E0D4}, the TOP/BOTTOM path
     * (sonic3k.asm:41503-41506,41581-41586). Gated via
     * {@link CollisionRules#solidObjectBarelyPokingResolvesAsSide()}.
     */
    private boolean solidObjectBarelyPokingResolvesAsSide(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        CollisionRules rules = collisionRulesOrNull(player);
        return rules != null && rules.solidObjectBarelyPokingResolvesAsSide();
    }

    private boolean preservesEdgeSubpixelMotion(ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        if (!(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        return InitialObjectDispatchController.afterCallback(initialDispatch, provider.preservesEdgeSubpixelMotion());
    }

    private boolean isMovingIntoSideContact(PlayableEntity player, ObjectInstance instance, boolean leftSide) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        short xSpeed = player.getXSpeed();
        if (!leftSide) {
            return xSpeed < 0;
        }
        if (instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.zeroXSpeedStopsOnLeftSideContact())) {
            return xSpeed >= 0;
        }
        return xSpeed > 0;
    }

    /**
     * ROM: SlopeObject2 / MvSonicOnSlope slope sampling for riding updates.
     * Returns the raw slope sample (surface offset from object Y), matching
     * the ROM formula: surfaceY = obY - slopeSample.
     *
     * Important: unlike resolveSlopedContact (landing path) which normalises
     * slope values via getSlopeBaseline(), the riding path uses the raw sample
     * exactly as the ROM does — no baseline subtraction.
     *
     * Shift/flip ordering matches the ROM: shift first (lsr.w #1), then flip
     * (not.w + add.w halfWidth). This differs from SlopeObject (landing) which
     * flips the full relX before shifting.
     */
    private int sampleSlopeY(PlayableEntity player, int objectX,
            int halfWidth, SlopedSolidProvider sloped) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        byte[] slopeData = InitialObjectDispatchController.afterCallback(initialDispatch, sloped.getSlopeData());
        if (slopeData == null || slopeData.length == 0) {
            return Integer.MIN_VALUE;
        }
        int playerCenterX = player.getCentreX();
        int relX = playerCenterX - objectX + halfWidth;
        int width2 = halfWidth * 2;
        // Clamp relX to the valid collision width. The riding bounds check
        // allows a sticky buffer (up to 16px beyond the collision edge),
        // so the player can be slightly outside the slope data range.
        if (relX < 0) {
            relX = 0;
        } else if (relX >= width2) {
            relX = width2 - 1;
        }
        // Sloped2 shifts before flip; full-resolution Sloped omits the shift.
        int sampleShift = InitialObjectDispatchController.afterCallback(initialDispatch, sloped.getSlopeSampleShift());
        int sampleX = relX >> sampleShift;
        if (InitialObjectDispatchController.afterCallback(initialDispatch, sloped.isSlopeFlipped())) {
            // NOT.W then add the table width: width - sampleX - 1.
            sampleX = (width2 >> sampleShift) - sampleX - 1;
        }
        if (sampleX < 0) {
            sampleX = 0;
        } else if (sampleX >= slopeData.length) {
            sampleX = slopeData.length - 1;
        }
        // ROM: move.b (a2,d0.w),d1 / ext.w d1 (S2) or moveq #0,d1 / move.b (S1)
        // Java byte is signed, matching S2's ext.w. S1 values are positive so no difference.
        return (byte) slopeData[sampleX];
    }

    private void clearRollingOnLanding(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite ball
                && com.openggf.game.mode.ControlledFrameRuntime.retainRolling(ball)) return;
        if (player == null || player.getPinballMode()) {
            return;
        }
        if (player.getRolling()) {
            if (player instanceof AbstractPlayableSprite sprite
                    && usesCurrentYRadiusDeltaOnLanding(sprite)) {
                int oldYRadius = sprite.getYRadius();
                int centreY = sprite.getCentreY();
                sprite.setRolling(false);
                int delta = oldYRadius - sprite.getStandYRadius();
                NativePositionOps.writeYPosPreserveSubpixel(sprite, centreY + delta);
            } else {
                player.setRolling(false);
                player.setY((short) (player.getY() - player.getRollHeightAdjustment()));
            }
            if (player instanceof AbstractPlayableSprite sprite) {
                int walkAnimationId = sprite.resolveAnimationId(CanonicalAnimation.WALK);
                if (walkAnimationId >= 0) {
                    sprite.setAnimationId(walkAnimationId);
                }
            }
        } else if (player instanceof AbstractPlayableSprite sprite
                && (sprite.getYRadius() != sprite.getStandYRadius()
                || sprite.getXRadius() != sprite.getStandXRadius())) {
            // ROM Player_TouchFloor (sonic3k.asm:24381-24383 / 29134-29136) unconditionally
            // resets y_radius/x_radius before testing Status_Roll. S3K despawn marker
            // writes Status_InAir directly and can leave Tails with rolling radii but no
            // roll bit — so S3K needs this reset even when not rolling.
            // S2 Tails_ResetOnFloor (s2.asm:41012-41029) uses btst/bne to gate the
            // y_radius reset on Status_Roll being set; when not rolling it skips the
            // reset, preserving any stale y_radius value from the despawn path.
            // Gate this non-rolling radius reset to S3K (landingRollClearUsesCurrentYRadiusDelta).
            if (usesCurrentYRadiusDeltaOnLanding(sprite)) {
                sprite.applyStandingRadii(false);
            }
        }
    }

    private boolean usesCurrentYRadiusDeltaOnLanding(AbstractPlayableSprite sprite) {
        PlayerMovementRules rules = playerMovementRulesOrNull(sprite);
        return rules != null && rules.landing().landingRollClearUsesCurrentYRadiusDelta();
    }

    private static CollisionRules collisionRulesOrNull(PlayableEntity player) {
        if (player == null) {
            return null;
        }
        GameRules rules = player.getGameRules();
        if (rules != null && rules.collision() != null) {
            return rules.collision();
        }
        return null;
    }

    private static PlayerMovementRules playerMovementRulesOrNull(PlayableEntity player) {
        if (player == null) {
            return null;
        }
        GameRules rules = player.getGameRules();
        if (rules != null && rules.playerMovement() != null) {
            return rules.playerMovement();
        }
        return null;
    }

    private static ObjectInteractionRules objectInteractionRulesOrNull(PlayableEntity player) {
        if (player == null) {
            return null;
        }
        GameRules rules = player.getGameRules();
        return rules != null ? rules.objectInteraction() : null;
    }

    private void applyObjectLandingState(PlayableEntity player) {
        applyObjectLandingState(player, null);
    }

    private void applyObjectLandingState(PlayableEntity player, ObjectInstance instance) {
        var initialDispatch = InitialObjectDispatchController.currentReservation(objectManager);
        AbstractPlayableSprite playableSprite = player instanceof AbstractPlayableSprite sprite ? sprite : null;
        boolean wasHurt = playableSprite != null && playableSprite.isHurt();
        int savedDoubleJumpFlag = player.getDoubleJumpFlag();

        if (wasHurt) {
            playableSprite.setAirAfterObjectHurtLanding();
        } else {
            player.setAir(false);
        }
        // ROM: most object/platform landings run Sonic_ResetOnFloor /
        // Solid_ResetFloor, which clears the rolling flag and restores the
        // standing radii. A few object routines (e.g. S2 Obj85's vertical
        // launcher capture, s2.asm:57947-57968) snap the player via
        // SolidObject_Always_SingleCharacter / RideObject_SetRide WITHOUT ever
        // calling Sonic_ResetOnFloor, so the player stays curled and y_pos is
        // left exactly where the snap placed it. Honour that per-object choice
        // so the asymmetric roll-exit/roll-enter y adjustments do not shift the
        // landing centre (s2 cnz2 frame 4292: a rolling re-landing must stay at
        // objY-$20-y_radius-1 = 0x03F4, not 0x03EF).
        boolean preservesRolling = instance instanceof SolidObjectProvider provider
                && InitialObjectDispatchController.afterCallback(initialDispatch, provider.landingPreservesRolling(player));
        if (!preservesRolling) {
            clearRollingOnLanding(player);
            // RideObject_SetRide dispatches to ResetOnFloor_Part2/Part3 for
            // every ordinary airborne landing. Part3 clears Status_Push even
            // when a prior solid's object-side pushing latch remains set.
            player.setPushing(false);
            if (playableSprite != null) {
                // Sonic_ResetOnFloor_Part3 clears the full tumble state after
                // ordinary platform landings. RideObject-only captures opt out
                // through landingPreservesRolling because they never call it.
                playableSprite.setFlipAngle(0);
                playableSprite.setFlipType(0);
                playableSprite.setFlipTurned(false);
                playableSprite.setFlipsRemaining(0);
                playableSprite.setLookDelayCounter((short) 0);
                // loc_17B6A (sonic3k.asm:32894-32907), Knux_TouchFloor's
                // non-rolling tail, also runs for ordinary object landings.
                // Clear the raw glide animation after the player animation pass;
                // this frame's mapping remains owned by that earlier pass.
                if (playableSprite.getSecondaryAbility() == SecondaryAbility.GLIDE
                        && (playableSprite.getAnimationId() & 0xFF) >= 0x20) {
                    playableSprite.setAnimationId(0);
                }

                if (!playableSprite.getPinballMode()
                        && instance instanceof SolidObjectProvider provider
                        && InitialObjectDispatchController.afterCallback(initialDispatch, provider.nonRollingLandingPublishesWalk(player))) {
                    // S2 Solid_ResetFloor reaches Sonic_ResetOnFloor, whose
                    // entry publishes Walk even when Status_Roll was already
                    // clear. Only concrete routines that call that entry opt in.
                    int walkAnimationId = playableSprite.resolveAnimationId(CanonicalAnimation.WALK);
                    if (walkAnimationId >= 0) {
                        playableSprite.setAnimationId(walkAnimationId);
                    }
                }
            }
        }
        player.setGroundMode(GroundMode.GROUND);

        if (playableSprite != null) com.openggf.game.mutators.PlayableAudioEvents.landed(playableSprite);

        if (wasHurt) {
            // ROM object/platform landing clears Status_InAir here, but
            // routine 4 and its velocities survive until Sonic_HurtStop runs
            // on the next player update (S2: s2.asm:37848-37861).
            return;
        }

        player.applyPostObjectLandingAbilities(savedDoubleJumpFlag);
    }
}
