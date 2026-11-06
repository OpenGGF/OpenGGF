package com.openggf.level.objects.contact;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.MultiPieceSolidProvider;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.sprites.Sprite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Owns solid-contact memory across object calls, frame boundaries and rewind.
 *
 * <p>Rides belong to players; standing/pushing bits belong to objects. Releasing
 * an object's bits therefore does not release a player's ride or mutate player
 * physics/status/animation. Live bits persist across frames, while pre-clear and
 * newly-established standing history expires at the next {@link #beginFrame()}.
 * Rewind captures live bits and rides, never that frame-only history.
 *
 * <p>This is an internal engine capability. Public visibility permits the existing
 * collision controller to use it across packages; it is not a creator API.
 */
public final class SolidContactState {
    /** Only identity lookup and callback-validity services are required. */
    public interface Access {
        boolean usesOccurrenceKey(ObjectInstance object);
        ObjectInstance restoredOccupant(ObjectSpawn spawn, int slotIndex);
        Runnable captureDispatchGuard();
    }

    private final Access access;
    private int frameCounter;

    public SolidContactState(Access access) {
        this.access = java.util.Objects.requireNonNull(access, "access");
    }

    private static <T> T afterCallback(Runnable guard, T value) {
        guard.run();
        return value;
    }

    // A live holder is reused to avoid allocations on continued rides. Read its
    // values before changing the same player's ride; this view is not a snapshot.
    // Native carry uses d4 from the object's routine entry, not a stored previous X.
    public static final class Ride {
        private ObjectInstance object;
        private int pieceIndex;

        Ride(ObjectInstance object, int pieceIndex) {
            set(object, pieceIndex);
        }

        private void set(ObjectInstance object, int pieceIndex) {
            this.object = object;
            this.pieceIndex = pieceIndex;
        }

        public ObjectInstance object() {
            return object;
        }
        public int pieceIndex() {
            return pieceIndex;
        }
    }
    private record PieceStandingLatchKey(Object objectKey, int pieceIndex) {}
    private final Map<PlayableEntity, Ride> ridingStates = new IdentityHashMap<>(2);

    /** Inserts or in-place-updates the player's riding state (allocation-free when present). */
    private void putRide(PlayableEntity player, ObjectInstance object, int pieceIndex) {
        Ride state = ridingStates.get(player);
        if (state != null) {
            if (state.object != object || state.pieceIndex != pieceIndex) {
                clearObjectStandingBit(player, state.object, state.pieceIndex);
            }
            state.set(object, pieceIndex);
        } else {
            ridingStates.put(player, new Ride(object, pieceIndex));
        }
    }
    private final Set<PlayableEntity> sameFrameMonitorBreakBounces =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<PlayableEntity, Set<ObjectInstance>> controllerAirborneReleaseSupports =
            new IdentityHashMap<>(2);
    // Live object bits persist until explicit release/retirement. Continued ride
    // vs fresh contact depends on this bit: SolidObjectFull2_1P loc_1DCF0
    // (sonic3k.asm:41106-41129), RideObject_SetRide (:42067-42080).
    private final Map<PlayableEntity, Set<Object>> objectStandingBitSet =
            new IdentityHashMap<>(2);
    // Per-player set of solid object spawn keys whose ROM-equivalent
    // "object pushing-bit" is currently SET on this player.
    // ROM loc_1E0A2/sub_1E0C2 only clears Status_Push when the current
    // object owned the push bit; a different off-screen solid must not
    // clear a push produced by another object in the same frame.
    private final Map<PlayableEntity, Set<Object>> objectPushingBitSet =
            new IdentityHashMap<>(2);
    // Helper-entry history survives a bit clear for the current frame's lift gate.
    private final Map<PlayableEntity, Set<Object>> objectStandingBitSnapshot =
            new IdentityHashMap<>(2);
    // Fresh standing is protected until the next frame when the selected routine
    // opts in. loc_1DC98 (sonic3k.asm:41075-41080) and loc_1DCF0
    // (:41124-41129) clear stale contacts. RideObject_SetRide (:42067-42080)
    // sets support; Tails_Jump's loc_1504C (:28583-28598) can set air later.
    private final Map<PlayableEntity, Set<Object>> standingBitEstablishedThisFrame =
            new IdentityHashMap<>(2);

    public void markStandingBitEstablishedThisFrame(PlayableEntity player, ObjectInstance instance,
            int pieceIndex) {
        if (player == null) {
            return;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        if (key == null) {
            return;
        }
        standingBitEstablishedThisFrame
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(key);
    }

    private Object latchKeyFor(ObjectInstance instance) {
        var initialDispatch = access.captureDispatchGuard();
        if (instance == null) {
            return null;
        }
        if (access.usesOccurrenceKey(instance)
                || (instance instanceof SolidObjectProvider provider
                        && afterCallback(initialDispatch, provider.usesInstanceSolidStateLatchKey()))) {
            return instance;
        }
        ObjectSpawn spawn = afterCallback(initialDispatch, instance.getSpawn());
        // Layout identity survives reload, but native bits die with the SST slot:
        // S2 DeleteObject/DeleteObject2 (docs/s2disasm/s2.asm:30329-30345),
        // S1 DeleteObject/DeleteChild (docs/s1disasm/_incObj/sub DeleteObject.asm:10-20),
        // S3K Delete_Current_Sprite/Delete_Referenced_Sprite
        // (docs/skdisasm/sonic3k.asm:36148-36165). Retirement must expire this key.
        return spawn != null ? spawn : instance;
    }

    private Object latchKeyFor(ObjectInstance instance, int pieceIndex) {
        var initialDispatch = access.captureDispatchGuard();
        Object key = latchKeyFor(instance);
        if (key == null) {
            return null;
        }
        if (pieceIndex >= 0
                && instance instanceof MultiPieceSolidProvider multiPiece
                && afterCallback(initialDispatch, multiPiece.usesPieceScopedStandingBits())) {
            return new PieceStandingLatchKey(key, pieceIndex);
        }
        return key;
    }

    public void setObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        setObjectStandingBit(player, instance, -1);
    }

    public void setObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        if (player == null) {
            return;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        if (key == null) {
            return;
        }
        Set<Object> set = objectStandingBitSet.computeIfAbsent(player, p -> new HashSet<>());
        evictPreviousStandingBitOwner(set, key);
        set.add(key);
    }

    /**
     * ROM: seating a player on an object clears the PREVIOUS owner's standing
     * bit, so at most one object holds a given player's bit at a time. Every
     * S3K seating helper carries the same prologue verbatim --
     * {@code btst #Status_OnObj,status(a1) / movea.w interact(a1),a3 /
     * bclr d6,status(a3)} -- before {@code move.w a0,interact(a1)} and
     * {@code bset d6,status(a0)}: {@code RideObject_SetRide}
     * (docs/skdisasm/sonic3k.asm:42067-42079), {@code sub_337D8} (:69821-69852),
     * {@code sub_33C34} (:70206-70226), {@code loc_422D6} (:87592-87604) and
     * {@code sub_42636} (:87835-87841). S2's {@code RideObject_SetRide}
     * (docs/s2disasm/s2.asm:35986-35998) is the same routine with a byte
     * {@code interact} index, and S1 enforces the same invariant through
     * {@code standonobject} -- {@code Plat_NoCheck}'s {@code bclr #3,obStatus(a2)}
     * is annotated in the listing as "clear platform bit for the other platform"
     * (docs/s1disasm/_incObj/sub PlatformObject &amp; SlopeObject.asm:68-80).
     * This is therefore a universal correction, not a per-game divergence.
     * <p>
     * The remaining S3K writers of the bit uphold the same invariant by the
     * other route -- {@code btst #Status_OnObj,status(a1) / bne} refuses to seat
     * an already-seated player (:84607, :84719, :84821, :84933, :85216) -- so
     * the one-object-per-player property holds across all ten ride sites.
     * <p>
     * Two S3K sites set {@code d6,status(a0)} WITHOUT it being a ride and are
     * deliberately outside this model: the HCZ water-skim marker (:75416) and
     * {@code sub_47F9C} (:94066) never write {@code Status_OnObj} and use the
     * bit as a per-player object-local flag. Every caller of this method is one
     * of this controller's own ride/landing paths, so no non-ride marker is
     * recorded here and neither site reaches this eviction.
     * <p>
     * Eviction is per OBJECT, not per piece: {@code bclr d6,status(a3)} clears
     * one object's status byte, so sibling pieces of the object being seated
     * keep their engine piece-scoped bits.
     */
    private static void evictPreviousStandingBitOwner(Set<Object> set, Object newKey) {
        if (set.isEmpty()) {
            return;
        }
        Object newOwner = ownerOf(newKey);
        set.removeIf(existing ->
                !java.util.Objects.equals(ownerOf(existing), newOwner));
    }

    /** The object a standing-bit key belongs to, collapsing piece-scoped keys. */
    private static Object ownerOf(Object key) {
        return key instanceof PieceStandingLatchKey piece ? piece.objectKey() : key;
    }

    public boolean hasObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        return hasObjectStandingBit(player, instance, -1);
    }

    public boolean hasObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        if (player == null) {
            return false;
        }
        Set<Object> set = objectStandingBitSet.get(player);
        if (set == null) {
            return false;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        return key != null && set.contains(key);
    }

    public boolean wasStandingBitEstablishedThisFrame(PlayableEntity player, ObjectInstance instance,
            int pieceIndex) {
        if (player == null) {
            return false;
        }
        Set<Object> set = standingBitEstablishedThisFrame.get(player);
        if (set == null) {
            return false;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        return key != null && set.contains(key);
    }

    public void setObjectPushingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return;
        }
        Object key = latchKeyFor(instance);
        if (key == null) {
            return;
        }
        objectPushingBitSet
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(key);
    }

    /**
     * Clears the object-side p1/p2 pushing bits for every player, without the
     * paired player-side clear or the {@code SolidObject_TestClearPush}
     * Walk/Run restart write.
     *
     * <p>ROM equivalent: the two {@code bclr ...,status(a0)} instructions an
     * object may execute on its own, outside {@code Solid_NotPushing} -- for
     * example the Obj41 horizontal spring's launch tail at {@code loc_18BAA}
     * (docs/s2disasm/s2.asm:34073-34076), which clears {@code p1_pushing_bit}
     * and {@code p2_pushing_bit} on the spring and
     * {@code status.player.pushing} on the launched character before playing
     * the spring sound. Clearing the object-side bits there is what stops the
     * following frame's {@code SolidObject_TestClearPush}
     * {@code btst d4,status(a0)} (docs/s2disasm/s2.asm:35462-35466) from
     * passing.
     */
    public void releaseObjectPushLatchForAllPlayers(ObjectInstance instance) {
        Object key = latchKeyFor(instance);
        if (key == null) {
            return;
        }
        objectPushingBitSet.values().removeIf(set -> {
            set.remove(key);
            return set.isEmpty();
        });
    }

    /**
     * Clears the object-side p1/p2 STANDING bits for every player, without
     * touching the player's own {@code Status_OnObj} or the engine's riding
     * state.
     *
     * <p>ROM equivalent: the object half of {@code DeleteObject}. Object-side
     * state lives in the SST slot and is zeroed wholesale when the slot is
     * released -- S2 {@code DeleteObject}/{@code DeleteObject2}
     * (docs/s2disasm/s2.asm:30329-30345), S1 {@code DeleteObject}/
     * {@code DeleteChild} (docs/s1disasm/_incObj/sub DeleteObject.asm:10-20),
     * S3K {@code Delete_Current_Sprite}/{@code Delete_Referenced_Sprite}
     * (docs/skdisasm/sonic3k.asm:36148-36165).
     *
     * <p>Deliberately object-side ONLY. None of the three delete routines
     * touches the player's slot, so a player standing on an object that
     * deletes itself keeps {@code Status_OnObj} set and keeps
     * {@code interact}/{@code standonobject} pointing at the freed slot until
     * some other routine changes it. Clearing the player's bit here would be a
     * second, unrelated behaviour change.
     *
     * <p>Piece-scoped keys are collapsed to their owning object, matching
     * {@code bclr d6,status(a0)} clearing one status byte for the whole object.
     */
    public void releaseObjectStandingLatchForAllPlayers(ObjectInstance instance) {
        Object owner = latchKeyFor(instance);
        if (owner == null) {
            return;
        }
        objectStandingBitSet.values().removeIf(set -> {
            set.removeIf(existing ->
                    java.util.Objects.equals(ownerOf(existing), owner));
            return set.isEmpty();
        });
    }

    /**
     * Releases the object's standing and pushing bits without changing players'
     * riding state, Status_OnObj, pushing state or release animation.
     *
     * <p>The delete routines zero the object's slot, not the player's slot; see
     * {@link #releaseObjectStandingLatchForAllPlayers}. Call this while placement
     * occurrence ownership is still available: a persistent spawn key outlives
     * the instance, but its native object-side bits must not survive a reload.
     * Instance-only dynamic retirement also evicts per-frame keys through
     * {@link #evictLatchForDestroyedInstance}.
     */
    public void releaseObjectLatchesForAllPlayers(ObjectInstance instance) {
        releaseObjectPushLatchForAllPlayers(instance);
        releaseObjectStandingLatchForAllPlayers(instance);
    }

    /**
     * Clears the object-side pushing bit for one player only, without the
     * paired player-side clear or the {@code SolidObject_TestClearPush}
     * Walk/Run restart write, and reports whether the bit had been set.
     *
     * <p>ROM equivalent: a single {@code bclr #pN_pushing_bit,status(a0)} an
     * object routine executes on its own. Many such sites are per-character --
     * S2 {@code Obj36_Sideways} clears only {@code p1_pushing_bit} in its
     * MainCharacter branch and only {@code p2_pushing_bit} in its Sidekick
     * branch (docs/s2disasm/s2.asm:29438-29450) -- so clearing both would be
     * wrong. Others are a test-and-clear whose {@code beq} decides whether the
     * rest of the routine runs at all, for example S2 {@code Obj45}'s
     * {@code loc_243EA} (docs/s2disasm/s2.asm:50543-50545); the return value is
     * that {@code bclr}'s Z flag, so a caller can branch on it exactly as the
     * ROM does.
     *
     * @return true when the object's pushing bit for that player was set
     */
    public boolean releaseObjectPushLatch(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return false;
        }
        Object key = latchKeyFor(instance);
        if (key == null) {
            return false;
        }
        Set<Object> set = objectPushingBitSet.get(player);
        if (set == null || !set.remove(key)) {
            return false;
        }
        if (set.isEmpty()) {
            objectPushingBitSet.remove(player);
        }
        return true;
    }

    public boolean hasPushingLatch(PlayableEntity player) {
        Set<Object> set = objectPushingBitSet.get(player);
        return set != null && !set.isEmpty();
    }

    public boolean clearObjectPushingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return false;
        }
        Set<Object> set = objectPushingBitSet.get(player);
        if (set == null) {
            return false;
        }
        Object key = latchKeyFor(instance);
        if (key == null || !set.remove(key)) {
            return false;
        }
        if (set.isEmpty()) {
            objectPushingBitSet.remove(player);
        }
        return true;
    }

    public void clearObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        clearObjectStandingBit(player, instance, -1);
    }

    public void clearObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        if (player == null) {
            return;
        }
        Set<Object> set = objectStandingBitSet.get(player);
        if (set == null) {
            return;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        if (key != null) {
            set.remove(key);
        }
    }

    public void snapshotObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        snapshotObjectStandingBit(player, instance, -1);
    }

    public void snapshotObjectStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        if (player == null) {
            return;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        if (key == null) {
            return;
        }
        objectStandingBitSnapshot
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(key);
    }

    /** Remember the helper-entry bit before consuming it; preserve both callback boundaries. */
    public void rememberAndClearStandingBit(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        snapshotObjectStandingBit(player, instance, pieceIndex);
        clearObjectStandingBit(player, instance, pieceIndex);
    }

    public boolean wasObjectStandingBitSetThisFrame(PlayableEntity player, ObjectInstance instance) {
        return wasObjectStandingBitSetThisFrame(player, instance, -1);
    }

    public boolean wasObjectStandingBitSetThisFrame(PlayableEntity player, ObjectInstance instance, int pieceIndex) {
        if (player == null) {
            return false;
        }
        Set<Object> set = objectStandingBitSnapshot.get(player);
        if (set == null) {
            return false;
        }
        Object key = latchKeyFor(instance, pieceIndex);
        return key != null && set.contains(key);
    }

    /**
     * Drops latch entries for {@code key} from every per-player set.
     * Called when an object is permanently destroyed (no respawn) so the
     * key — typically an instance reference for spawnless dynamic objects
     * — does not pin the slot in memory for the rest of the level.
     * Ordinary removal separately releases live object bits while occurrence
     * identity is still available. This permanent-key eviction also removes the
     * pre-clear and established-this-frame history, including piece-scoped keys;
     * it never releases a player ride.
     *
     * <p>Delete_Current_Sprite/Delete_Referenced_Sprite zero the whole object slot
     * (docs/skdisasm/sonic3k.asm:36148-36165). Neither a live object bit nor the
     * engine's remembered value of that bit can belong to a retired incarnation.
     */
    private void evictLatchKey(Object key) {
        if (key == null) {
            return;
        }
        removeOwnerKeys(objectStandingBitSet, key);
        removeOwnerKeys(objectPushingBitSet, key);
        removeOwnerKeys(objectStandingBitSnapshot, key);
        removeOwnerKeys(standingBitEstablishedThisFrame, key);
    }

    private static void removeOwnerKeys(Map<PlayableEntity, Set<Object>> latches, Object owner) {
        for (Set<Object> keys : latches.values()) {
            keys.removeIf(key -> java.util.Objects.equals(ownerOf(key), owner));
        }
    }

    public void evictLatchForDestroyedSpawn(ObjectSpawn spawn) {
        evictLatchKey(spawn);
    }

    public void evictLatchForDestroyedInstance(ObjectInstance instance) {
        var initialDispatch = access.captureDispatchGuard();
        // Mirror latchKeyFor: spawn-backed instances were keyed
        // by spawn (already evicted via evictLatchForDestroyedSpawn);
        // spawnless dynamic objects were keyed by the instance reference.
        if (access.usesOccurrenceKey(instance)
                || (instance instanceof SolidObjectProvider provider
                        && afterCallback(initialDispatch, provider.usesInstanceSolidStateLatchKey()))) {
            evictLatchKey(instance);
        } else if (instance != null && afterCallback(initialDispatch, instance.getSpawn()) == null) {
            evictLatchKey(instance);
        }
    }

    public void inheritRetainedSstContacts(SolidContactState previous,
            Collection<? extends ObjectInstance> carried) {
        var initialDispatch = access.captureDispatchGuard();
        Set<Object> retainedKeys = new HashSet<>();
        Set<ObjectInstance> retainedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ObjectInstance object : carried) {
            retainedObjects.add(object);
            retainedKeys.add(latchKeyFor(object));
        }
        inheritRetainedLatchBits(previous.objectStandingBitSet, objectStandingBitSet, retainedKeys);
        inheritRetainedLatchBits(previous.objectPushingBitSet, objectPushingBitSet, retainedKeys);
        for (var entry : previous.ridingStates.entrySet()) {
            Ride ride = entry.getValue();
            if (!retainedObjects.contains(ride.object)) {
                continue;
            }
            // Native continued riding takes its movement baseline (d4) from the
            // retained object's position at its next routine entry, which the
            // transition coordinate scan has already rebased.
            putRide(entry.getKey(), ride.object, ride.pieceIndex);
        }
    }

    private static void inheritRetainedLatchBits(Map<PlayableEntity, Set<Object>> source,
            Map<PlayableEntity, Set<Object>> target, Set<Object> retainedKeys) {
        for (var entry : source.entrySet()) {
            for (Object key : entry.getValue()) {
                if (retainedKeys.contains(ownerOf(key))) {
                    target.computeIfAbsent(entry.getKey(), player -> new HashSet<>()).add(key);
                }
            }
        }
    }

    public ObjectManagerSnapshot.SolidContactState captureRewindState() {
        List<ObjectManagerSnapshot.SolidContactRidingEntry> entries = new ArrayList<>();
        for (var entry : ridingStates.entrySet()) {
            PlayableEntity player = entry.getKey();
            Ride state = entry.getValue();
            if (player == null || state == null || state.object == null) {
                continue;
            }
            entries.add(new ObjectManagerSnapshot.SolidContactRidingEntry(
                    player,
                    state.object.getSpawn(),
                    state.object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1,
                    state.pieceIndex));
        }
        entries.sort(Comparator.comparing(entry -> playerSnapshotKey(entry.player())));
        return new ObjectManagerSnapshot.SolidContactState(
                frameCounter,
                List.copyOf(entries),
                captureLatchEntries(objectStandingBitSet),
                captureLatchEntries(objectPushingBitSet));
    }

    public ObjectManagerSnapshot.SolidContactState captureRewindState(RewindCaptureContext context) {
        var state = captureRewindState();
        var table = context.identityTable().orElse(null);
        if (table == null) return state;
        // Transform after the original live-player sort so mixed standalone/registered
        // members retain their complete order. Registered snapshots hold no incarnation.
        return new ObjectManagerSnapshot.SolidContactState(
                state.frameCounter(),
                state.riding().stream().map(entry -> {
                    var player = captureContactPlayer(entry.player(), table);
                    return new ObjectManagerSnapshot.SolidContactRidingEntry(player.player(),
                            entry.objectSpawn(), entry.objectSlotIndex(), entry.pieceIndex(),
                            player.playerId());
                }).toList(),
                captureContactLatchPlayers(state.standingBits(), table),
                captureContactLatchPlayers(state.pushingBits(), table));
    }

    private static ObjectManagerSnapshot.SolidContactPlayerBinding captureContactPlayer(
            PlayableEntity player, RewindIdentityTable table) {
        PlayerRefId id = table.encodePlayer(player);
        return new ObjectManagerSnapshot.SolidContactPlayerBinding(id == null ? player : null, id);
    }

    private static List<ObjectManagerSnapshot.SolidContactLatchEntry> captureContactLatchPlayers(
            List<ObjectManagerSnapshot.SolidContactLatchEntry> entries, RewindIdentityTable table) {
        return entries.stream().map(entry -> {
            var player = captureContactPlayer(entry.player(), table);
            return new ObjectManagerSnapshot.SolidContactLatchEntry(
                    player.player(), entry.keys(), player.playerId());
        }).toList();
    }

    private List<ObjectManagerSnapshot.SolidContactLatchEntry> captureLatchEntries(
            Map<PlayableEntity, Set<Object>> source) {
        ArrayList<ObjectManagerSnapshot.SolidContactLatchEntry> entries = new ArrayList<>();
        for (var entry : source.entrySet()) {
            PlayableEntity player = entry.getKey();
            Set<Object> keys = entry.getValue();
            if (player == null || keys == null || keys.isEmpty()) {
                continue;
            }
            ArrayList<ObjectManagerSnapshot.SolidContactLatchKey> snapshotKeys = new ArrayList<>();
            for (Object key : keys) {
                ObjectManagerSnapshot.SolidContactLatchKey snapshotKey = latchKeyForSnapshot(key);
                if (snapshotKey != null) {
                    snapshotKeys.add(snapshotKey);
                }
            }
            if (!snapshotKeys.isEmpty()) {
                snapshotKeys.sort(Comparator.comparing(SolidContactState::latchKeySnapshotKey)
                        .thenComparingInt(ObjectManagerSnapshot.SolidContactLatchKey::pieceIndex));
                entries.add(new ObjectManagerSnapshot.SolidContactLatchEntry(player, snapshotKeys));
            }
        }
        entries.sort(Comparator.comparing(entry -> playerSnapshotKey(entry.player())));
        return List.copyOf(entries);
    }

    private static String playerSnapshotKey(PlayableEntity player) {
        if (player == null) {
            return "";
        }
        if (player instanceof Sprite sprite && sprite.getCode() != null) {
            return sprite.getCode();
        }
        return player.getClass().getName();
    }

    private static String latchKeySnapshotKey(ObjectManagerSnapshot.SolidContactLatchKey key) {
        if (key == null) {
            return "";
        }
        ObjectSpawn spawn = key.spawn();
        String spawnKey = spawn == null ? "" : spawn.layoutIndex()
                + ":" + spawn.objectId()
                + ":" + spawn.x()
                + ":" + spawn.y()
                + ":" + spawn.subtype()
                + ":" + spawn.renderFlags()
                + ":" + spawn.rawYWord();
        return (key.instanceKey() ? "I:" : "S:") + spawnKey + ":" + key.slotIndex();
    }

    private ObjectManagerSnapshot.SolidContactLatchKey latchKeyForSnapshot(Object key) {
        var initialDispatch = access.captureDispatchGuard();
        if (key instanceof PieceStandingLatchKey piece) {
            var owner = latchKeyForSnapshot(piece.objectKey());
            return owner == null ? null : new ObjectManagerSnapshot.SolidContactLatchKey(
                    owner.spawn(), owner.slotIndex(), owner.instanceKey(), piece.pieceIndex());
        }
        if (key instanceof ObjectSpawn spawn) {
            return new ObjectManagerSnapshot.SolidContactLatchKey(spawn, -1, false);
        }
        if (key instanceof AbstractObjectInstance aoi) {
            return new ObjectManagerSnapshot.SolidContactLatchKey(
                    aoi.getSpawn(),
                    aoi.getSlotIndex(),
                    true);
        }
        if (key instanceof ObjectInstance instance) {
            return new ObjectManagerSnapshot.SolidContactLatchKey(
                    afterCallback(initialDispatch, instance.getSpawn()),
                    -1,
                    true);
        }
        return null;
    }

    public void restoreRewindState(ObjectManagerSnapshot.SolidContactState state) {
        restoreRewindState(state, RewindCaptureContext.none());
    }

    public void restoreRewindState(ObjectManagerSnapshot.SolidContactState state, RewindCaptureContext context) {
        if (state == null) {
            restoreResolvedRewindState(null);
            return;
        }
        // Resolve every required role before clearing or populating any runtime map.
        // The resolved copy is restore-local; saved structural snapshots stay immutable.
        var resolved = new ObjectManagerSnapshot.SolidContactState(
                state.frameCounter(),
                state.riding().stream().map(entry -> new ObjectManagerSnapshot.SolidContactRidingEntry(
                        resolveContactPlayer(entry.player(), entry.playerId(), context), entry.objectSpawn(),
                        entry.objectSlotIndex(), entry.pieceIndex())).toList(),
                resolveContactLatchPlayers(state.standingBits(), context),
                resolveContactLatchPlayers(state.pushingBits(), context));
        restoreResolvedRewindState(resolved);
    }

    private static PlayableEntity resolveContactPlayer(
            PlayableEntity player, PlayerRefId playerId, RewindCaptureContext context) {
        return playerId == null ? player : context.requireIdentityTable().resolvePlayer(playerId, true);
    }

    private static List<ObjectManagerSnapshot.SolidContactLatchEntry> resolveContactLatchPlayers(
            List<ObjectManagerSnapshot.SolidContactLatchEntry> entries, RewindCaptureContext context) {
        return entries.stream().map(entry -> new ObjectManagerSnapshot.SolidContactLatchEntry(
                resolveContactPlayer(entry.player(), entry.playerId(), context), entry.keys())).toList();
    }

    private void restoreResolvedRewindState(ObjectManagerSnapshot.SolidContactState state) {
        ridingStates.clear();
        sameFrameMonitorBreakBounces.clear();
        objectStandingBitSet.clear();
        objectPushingBitSet.clear();
        clearStandingFrameHistory();
        if (state == null) {
            frameCounter = 0;
            return;
        }
        frameCounter = state.frameCounter();
        restoreLatchEntries(objectStandingBitSet, state.standingBits());
        restoreLatchEntries(objectPushingBitSet, state.pushingBits());

        List<ObjectManagerSnapshot.SolidContactRidingEntry> entries = state.riding();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (var entry : entries) {
            PlayableEntity player = entry.player();
            ObjectInstance object = access.restoredOccupant(
                    entry.objectSpawn(), entry.objectSlotIndex());
            if (player != null && object != null) {
                putRide(player, object, entry.pieceIndex());
            }
        }
    }

    public void restoreRewindState(List<ObjectManagerSnapshot.SolidContactRidingEntry> entries) {
        restoreRewindState(ObjectManagerSnapshot.SolidContactState.fromRiding(entries));
    }

    private void restoreLatchEntries(Map<PlayableEntity, Set<Object>> target,
            List<ObjectManagerSnapshot.SolidContactLatchEntry> entries) {
        if (entries == null) {
            return;
        }
        for (var entry : entries) {
            PlayableEntity player = entry.player();
            if (player == null) {
                continue;
            }
            Set<Object> keys = target.computeIfAbsent(player, p -> new HashSet<>());
            for (var snapshotKey : entry.keys()) {
                Object restoredKey = restoreLatchKey(snapshotKey);
                if (restoredKey != null) {
                    keys.add(restoredKey);
                }
            }
            if (keys.isEmpty()) {
                target.remove(player);
            }
        }
    }

    private Object restoreLatchKey(ObjectManagerSnapshot.SolidContactLatchKey key) {
        if (key == null) {
            return null;
        }
        Object owner;
        if (!key.instanceKey() && key.spawn() != null) {
            owner = key.spawn();
        } else {
            ObjectInstance instance = access.restoredOccupant(key.spawn(), key.slotIndex());
            owner = instance != null ? latchKeyFor(instance) : key.spawn();
        }
        return owner != null && key.pieceIndex() >= 0
                ? new PieceStandingLatchKey(owner, key.pieceIndex()) : owner;
    }

    public boolean isRidingObject(PlayableEntity player) {
        if (player == null) return false;
        Ride state = ridingStates.get(player);
        return state != null && state.object != null;
    }

    public boolean isAnyPlayerRiding() {
        for (Ride state : ridingStates.values()) {
            if (state != null && state.object != null) return true;
        }
        return false;
    }

    public boolean isPlayerRiding(PlayableEntity player, ObjectInstance instance) {
        if (player == null) return false;
        Ride state = ridingStates.get(player);
        return state != null && state.object == instance;
    }

    public boolean isAnyPlayerRiding(ObjectInstance instance) {
        for (Ride state : ridingStates.values()) {
            if (state != null && state.object == instance) return true;
        }
        return false;
    }

    public void clearRidingObject(PlayableEntity player) {
        if (player != null) {
            ridingStates.remove(player);
        }
    }

    public void clearSameFrameMonitorBreakBounces() {
        sameFrameMonitorBreakBounces.clear();
    }

    public void markSameFrameMonitorBreakBounce(PlayableEntity player) {
        if (player != null) sameFrameMonitorBreakBounces.add(player);
    }

    public boolean hadSameFrameMonitorBreakBounce(PlayableEntity player) {
        return player != null && sameFrameMonitorBreakBounces.contains(player);
    }

    public ObjectInstance getRidingObject(PlayableEntity player) {
        if (player == null) return null;
        Ride state = ridingStates.get(player);
        return state != null ? state.object : null;
    }

    /** Get the piece index this player is riding, or -1 if not riding a multi-piece object. */
    public int getRidingPieceIndex(PlayableEntity player) {
        if (player == null) return -1;
        Ride state = ridingStates.get(player);
        return state != null ? state.pieceIndex : -1;
    }

    /** Read-only, reused ride holder. Read needed values before changing this player's ride. */
    public Ride rideFor(PlayableEntity player) {
        return ridingStates.get(player);
    }

    public void ride(PlayableEntity player, ObjectInstance object, int pieceIndex) {
        putRide(player, object, pieceIndex);
    }

    /** Iterates a stable membership copy; each holder remains the same live ride. */
    public void forEachRide(BiConsumer<PlayableEntity, Ride> action) {
        for (var entry : List.copyOf(ridingStates.entrySet())) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    public int frameCounter() {
        return frameCounter;
    }

    public void beginFrame() {
        frameCounter++;
        clearStandingFrameHistory();
    }

    /** These observations are rebuilt by solid calls; they never cross a restored timeline. */
    private void clearStandingFrameHistory() {
        objectStandingBitSnapshot.clear();
        standingBitEstablishedThisFrame.clear();
        controllerAirborneReleaseSupports.clear();
    }

    public void reset() {
        frameCounter = 0;
        ridingStates.clear();
        sameFrameMonitorBreakBounces.clear();
        objectStandingBitSet.clear();
        objectPushingBitSet.clear();
        clearStandingFrameHistory();
    }

    public void rememberControllerRelease(PlayableEntity player, ObjectInstance object) {
        controllerAirborneReleaseSupports.computeIfAbsent(player,
                ignored -> Collections.newSetFromMap(new IdentityHashMap<>(4))).add(object);
    }

    public boolean consumeControllerRelease(PlayableEntity player, ObjectInstance object) {
        Set<ObjectInstance> released = controllerAirborneReleaseSupports.get(player);
        if (released == null || !released.remove(object)) {
            return false;
        }
        if (released.isEmpty()) {
            controllerAirborneReleaseSupports.remove(player);
        }
        return true;
    }
}
