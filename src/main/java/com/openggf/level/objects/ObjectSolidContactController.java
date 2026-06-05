package com.openggf.level.objects;


import com.openggf.camera.Camera;
import com.openggf.game.CollisionModel;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.spawn.AbstractPlacementManager;
import com.openggf.physics.CollisionTrace;
import com.openggf.physics.NoOpCollisionTrace;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.game.PlayableEntity;
import com.openggf.game.DamageCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.game.GroundMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class ObjectSolidContactController {
    private static final Logger LOGGER = Logger.getLogger(ObjectSolidContactController.class.getName());
    private static final int OBJ85_ID = 0x85;
    private final ObjectManager objectManager;
    private int frameCounter;

    // Per-player riding state (ROM: each player object has its own SST interact field $3E)
    private record RidingState(ObjectInstance object, int x, int y, int pieceIndex) {}
    private final Map<PlayableEntity, RidingState> ridingStates = new IdentityHashMap<>(2);
    private final Set<PlayableEntity> inlineSupportedPlayers =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<PlayableEntity> forceAirOnStaleSupportLoss =
            Collections.newSetFromMap(new IdentityHashMap<>());
    // Per-player set of solid object spawn keys whose ROM-equivalent
    // "object standing-bit" (a0.d6) is currently SET on this player.
    // ROM SolidObjectFull2_1P (sonic3k.asm:41066-41084):
    //   - btst d6, status(a0)
    //   - beq SolidObject_cont          ; bit CLEAR -> full collision check
    //   - btst Status_InAir, status(a1)
    //   - bne loc_1DCF0                 ; player airborne -> air-unseat
    //   - ... MvSonicOnPtfm continued-ride path
    //   loc_1DCF0: bclr Status_OnObj, status(a1); bset Status_InAir, status(a1);
    //              bclr d6, status(a0); moveq #0,d4; rts
    // The bit is SET by RideObject_SetRide (line 42022-42044) when a player
    // first STANDS on the object, and stays set until the air-unseat path
    // clears it.  That makes "spring kick + immediately airborne" route to
    // air-unseat (no lift) on the next frame, while a fresh contact (with
    // d6 clear) routes through SolidObject_cont where loc_1E154 fires.
    //
    // The engine's resolveContactInternal lift gate uses this latch to
    // mirror that behaviour: when the bit is set on the player for this
    // instance, skip the S3K-only loc_1E154 upward-velocity lift.
    //
    // Keyed by ObjectSpawn (or instance fallback) so the latch survives
    // when an object slot is unloaded and reloaded.
    private final Map<PlayableEntity, Set<Object>> objectStandingBitSet =
            new IdentityHashMap<>(2);
    // Per-player set of solid object spawn keys whose ROM-equivalent
    // "object pushing-bit" is currently SET on this player.
    // ROM loc_1E0A2/sub_1E0C2 only clears Status_Push when the current
    // object owned the push bit; a different off-screen solid must not
    // clear a push produced by another object in the same frame.
    private final Map<PlayableEntity, Set<Object>> objectPushingBitSet =
            new IdentityHashMap<>(2);
    // Per-frame snapshot of bits cleared during this frame's
    // processInlineObjectForPlayer. resolveContactInternal's lift gate
    // consults this AFTER the bit was cleared so it reads the value ROM
    // would have seen at SolidObjectFull2_1P entry. Cleared per-frame
    // (beginInlineFrame).
    private final Map<PlayableEntity, Set<Object>> objectStandingBitSnapshot =
            new IdentityHashMap<>(2);

    private static Object airUnseatLatchKeyFor(ObjectInstance instance) {
        if (instance == null) {
            return null;
        }
        if (instance instanceof SolidObjectProvider provider
                && provider.usesInstanceSolidStateLatchKey()) {
            return instance;
        }
        ObjectSpawn spawn = instance.getSpawn();
        // Spawn record is stable across slot reload; fall back to the
        // instance reference for dynamic / spawn-less objects.
        return spawn != null ? spawn : instance;
    }

    private void setObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return;
        }
        Object key = airUnseatLatchKeyFor(instance);
        if (key == null) {
            return;
        }
        objectStandingBitSet
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(key);
    }

    private boolean hasObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return false;
        }
        Set<Object> set = objectStandingBitSet.get(player);
        if (set == null) {
            return false;
        }
        Object key = airUnseatLatchKeyFor(instance);
        return key != null && set.contains(key);
    }

    private void setObjectPushingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return;
        }
        Object key = airUnseatLatchKeyFor(instance);
        if (key == null) {
            return;
        }
        objectPushingBitSet
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(key);
    }

    private boolean clearObjectPushingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return false;
        }
        Set<Object> set = objectPushingBitSet.get(player);
        if (set == null) {
            return false;
        }
        Object key = airUnseatLatchKeyFor(instance);
        if (key == null || !set.remove(key)) {
            return false;
        }
        if (set.isEmpty()) {
            objectPushingBitSet.remove(player);
        }
        return true;
    }

    private void clearObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return;
        }
        Set<Object> set = objectStandingBitSet.get(player);
        if (set == null) {
            return;
        }
        Object key = airUnseatLatchKeyFor(instance);
        if (key != null) {
            set.remove(key);
        }
    }

    private void snapshotObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return;
        }
        Object key = airUnseatLatchKeyFor(instance);
        if (key == null) {
            return;
        }
        objectStandingBitSnapshot
                .computeIfAbsent(player, p -> new HashSet<>())
                .add(key);
    }

    private boolean wasObjectStandingBitSetThisFrame(PlayableEntity player, ObjectInstance instance) {
        if (player == null) {
            return false;
        }
        Set<Object> set = objectStandingBitSnapshot.get(player);
        if (set == null) {
            return false;
        }
        Object key = airUnseatLatchKeyFor(instance);
        return key != null && set.contains(key);
    }

    /**
     * Drops latch entries for {@code key} from every per-player set.
     * Called when an object is permanently destroyed (no respawn) so the
     * key — typically an instance reference for spawnless dynamic objects
     * — does not pin the slot in memory for the rest of the level.
     * Out-of-range unloads must NOT call this: ROM's a0.d6 latch survives
     * spawn reloads and the engine intentionally mirrors that.
     */
    private void evictLatchKey(Object key) {
        if (key == null) {
            return;
        }
        for (Set<Object> set : objectStandingBitSet.values()) {
            set.remove(key);
        }
        for (Set<Object> set : objectPushingBitSet.values()) {
            set.remove(key);
        }
        for (Set<Object> set : objectStandingBitSnapshot.values()) {
            set.remove(key);
        }
    }

    void evictLatchForDestroyedSpawn(ObjectSpawn spawn) {
        evictLatchKey(spawn);
    }

    void evictLatchForDestroyedInstance(ObjectInstance instance) {
        // Mirror airUnseatLatchKeyFor: spawn-backed instances were keyed
        // by spawn (already evicted via evictLatchForDestroyedSpawn);
        // spawnless dynamic objects were keyed by the instance reference.
        if (instance instanceof SolidObjectProvider provider
                && provider.usesInstanceSolidStateLatchKey()) {
            evictLatchKey(instance);
        } else if (instance != null && instance.getSpawn() == null) {
            evictLatchKey(instance);
        }
    }
    private PlayableEntity currentPlayer; // set during update() for internal use

    // ROM: objects like Obj_AIZLRZEMZRock save player velocity/anim BEFORE calling
    // SolidObjectFull, then check the saved values after. Our engine runs contact
    // resolution (which zeroes velocity, clears rolling) before onSolidContact fires.
    // Snapshot the player's pre-contact state so objects can read the "before" values.
    private short preContactXSpeed;
    private short preContactYSpeed;
    private boolean preContactRolling;
    private int preContactAnimationId;

    // When true, the velocity classification adjustment in resolveContactInternal is
    // skipped. Set when this pass runs AFTER movement (S1 UNIFIED post-movement pass),
    // since the player's position already reflects their velocity.
    private boolean postMovement;

    // When true, this is a pre-movement pass for a game that will have a post-movement
    // pass (S1 UNIFIED). Side collision effects (speed zeroing, position correction)
    // should be deferred to the post-movement pass, because in the ROM, objects check
    // Sonic's position AFTER he has moved (his slot runs first in ExecuteObjects).
    // Standing/riding contacts still apply in pre-movement for platform delta tracking.
    private boolean deferSideToPostMovement;

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

    private final Map<PlayableEntity, PlayerStandingState> latestStandingSnapshots =
            new IdentityHashMap<>(2);
    private final Map<PlayableEntity, Map<Integer, Integer>> latestHeadroomSnapshots =
            new IdentityHashMap<>(2);

    ObjectSolidContactController(ObjectManager objectManager) {
        this.objectManager = objectManager;
    }

    void setDeferSideToPostMovement(boolean deferSideToPostMovement) {
        this.deferSideToPostMovement = deferSideToPostMovement;
    }

    void reset() {
        frameCounter = 0;
        ridingStates.clear();
        latestStandingSnapshots.clear();
        latestHeadroomSnapshots.clear();
        objectStandingBitSet.clear();
        objectPushingBitSet.clear();
        objectStandingBitSnapshot.clear();
    }

    ObjectManagerSnapshot.SolidContactState captureRewindState() {
        List<ObjectManagerSnapshot.SolidContactRidingEntry> entries = new ArrayList<>();
        for (var entry : ridingStates.entrySet()) {
            PlayableEntity player = entry.getKey();
            RidingState state = entry.getValue();
            if (player == null || state == null || state.object == null) {
                continue;
            }
            entries.add(new ObjectManagerSnapshot.SolidContactRidingEntry(
                    player,
                    state.object.getSpawn(),
                    state.object instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : -1,
                    state.x,
                    state.y,
                    state.pieceIndex));
        }
        entries.sort(Comparator.comparing(entry -> playerSnapshotKey(entry.player())));
        return new ObjectManagerSnapshot.SolidContactState(
                frameCounter,
                List.copyOf(entries),
                sortedPlayers(inlineSupportedPlayers),
                sortedPlayers(forceAirOnStaleSupportLoss),
                captureStandingSnapshots(),
                captureHeadroomSnapshots(),
                captureLatchEntries(objectStandingBitSet),
                captureLatchEntries(objectPushingBitSet),
                captureLatchEntries(objectStandingBitSnapshot));
    }

    private List<ObjectManagerSnapshot.SolidContactStandingSnapshotEntry> captureStandingSnapshots() {
        ArrayList<ObjectManagerSnapshot.SolidContactStandingSnapshotEntry> entries = new ArrayList<>();
        for (var entry : latestStandingSnapshots.entrySet()) {
            PlayableEntity player = entry.getKey();
            PlayerStandingState state = entry.getValue();
            if (player == null || state == null) {
                continue;
            }
            entries.add(new ObjectManagerSnapshot.SolidContactStandingSnapshotEntry(
                    player,
                    state.kind(),
                    state.standing(),
                    state.pushing()));
        }
        entries.sort(Comparator.comparing(entry -> playerSnapshotKey(entry.player())));
        return List.copyOf(entries);
    }

    private List<ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry> captureHeadroomSnapshots() {
        ArrayList<ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry> entries = new ArrayList<>();
        for (var playerEntry : latestHeadroomSnapshots.entrySet()) {
            PlayableEntity player = playerEntry.getKey();
            if (player == null || playerEntry.getValue() == null) {
                continue;
            }
            for (var angleEntry : playerEntry.getValue().entrySet()) {
                entries.add(new ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry(
                        player,
                        angleEntry.getKey(),
                        angleEntry.getValue()));
            }
        }
        entries.sort(Comparator
                .comparing((ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry entry) ->
                        playerSnapshotKey(entry.player()))
                .thenComparingInt(ObjectManagerSnapshot.SolidContactHeadroomSnapshotEntry::hexAngle));
        return List.copyOf(entries);
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
                snapshotKeys.sort(Comparator.comparing(ObjectSolidContactController::latchKeySnapshotKey));
                entries.add(new ObjectManagerSnapshot.SolidContactLatchEntry(player, snapshotKeys));
            }
        }
        entries.sort(Comparator.comparing(entry -> playerSnapshotKey(entry.player())));
        return List.copyOf(entries);
    }

    private static List<PlayableEntity> sortedPlayers(Collection<PlayableEntity> players) {
        ArrayList<PlayableEntity> sorted = new ArrayList<>();
        if (players != null) {
            for (PlayableEntity player : players) {
                if (player != null) {
                    sorted.add(player);
                }
            }
        }
        sorted.sort(Comparator.comparing(ObjectSolidContactController::playerSnapshotKey));
        return List.copyOf(sorted);
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

    private static ObjectManagerSnapshot.SolidContactLatchKey latchKeyForSnapshot(Object key) {
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
                    instance.getSpawn(),
                    -1,
                    true);
        }
        return null;
    }

    void restoreRewindState(ObjectManagerSnapshot.SolidContactState state) {
        ridingStates.clear();
        inlineSupportedPlayers.clear();
        forceAirOnStaleSupportLoss.clear();
        latestStandingSnapshots.clear();
        latestHeadroomSnapshots.clear();
        objectStandingBitSet.clear();
        objectPushingBitSet.clear();
        objectStandingBitSnapshot.clear();
        if (state == null) {
            frameCounter = 0;
            return;
        }
        frameCounter = state.frameCounter();
        inlineSupportedPlayers.addAll(state.inlineSupportedPlayers());
        forceAirOnStaleSupportLoss.addAll(state.forceAirOnStaleSupportLoss());
        for (var entry : state.standingSnapshots()) {
            if (entry.player() != null && entry.kind() != null) {
                latestStandingSnapshots.put(entry.player(), new PlayerStandingState(
                        entry.kind(),
                        entry.standing(),
                        entry.pushing()));
            }
        }
        for (var entry : state.headroomSnapshots()) {
            if (entry.player() != null) {
                latestHeadroomSnapshots
                        .computeIfAbsent(entry.player(), p -> new HashMap<>())
                        .put(entry.hexAngle() & 0xFF, entry.distance());
            }
        }
        restoreLatchEntries(objectStandingBitSet, state.standingBits());
        restoreLatchEntries(objectPushingBitSet, state.pushingBits());
        restoreLatchEntries(objectStandingBitSnapshot, state.standingBitSnapshots());

        List<ObjectManagerSnapshot.SolidContactRidingEntry> entries = state.riding();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (var entry : entries) {
            PlayableEntity player = entry.player();
            ObjectInstance object = objectManager.findRestoredRidingObject(
                    entry.objectSpawn(), entry.objectSlotIndex());
            if (player != null && object != null) {
                ridingStates.put(player, new RidingState(
                        object,
                        entry.x(),
                        entry.y(),
                        entry.pieceIndex()));
            }
        }
    }

    void restoreRewindState(List<ObjectManagerSnapshot.SolidContactRidingEntry> entries) {
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
        if (!key.instanceKey() && key.spawn() != null) {
            return key.spawn();
        }
        ObjectInstance instance = objectManager.findRestoredRidingObject(key.spawn(), key.slotIndex());
        if (instance != null) {
            return airUnseatLatchKeyFor(instance);
        }
        return key.spawn();
    }

    private void cacheStandingSnapshot(PlayableEntity player, PlayerStandingState snapshot) {
        if (player != null) {
            latestStandingSnapshots.put(player, snapshot);
        }
    }

    private void cacheHeadroomSnapshot(PlayableEntity player, int hexAngle, int distance) {
        if (player == null) {
            return;
        }
        Map<Integer, Integer> perAngle = latestHeadroomSnapshots.computeIfAbsent(
                player, p -> new HashMap<>());
        perAngle.put(hexAngle & 0xFF, distance);
    }

    boolean isRidingObject(PlayableEntity player) {
        if (player == null) return false;
        RidingState state = ridingStates.get(player);
        return state != null && state.object != null;
    }

    boolean isAnyPlayerRiding() {
        for (RidingState state : ridingStates.values()) {
            if (state != null && state.object != null) return true;
        }
        return false;
    }

    boolean isPlayerRiding(PlayableEntity player, ObjectInstance instance) {
        if (player == null) return false;
        RidingState state = ridingStates.get(player);
        return state != null && state.object == instance;
    }

    boolean isAnyPlayerRiding(ObjectInstance instance) {
        for (RidingState state : ridingStates.values()) {
            if (state != null && state.object == instance) return true;
        }
        return false;
    }

    void clearRidingObject(PlayableEntity player) {
        if (player != null) {
            ridingStates.remove(player);
            latestStandingSnapshots.remove(player);
            forceAirOnStaleSupportLoss.remove(player);
        }
    }

    private void clearGroundWallSuppressionForNormalSolidSupport(PlayableEntity player, ObjectInstance instance) {
        if (!(player instanceof AbstractPlayableSprite sprite)
                || instance == null
                || !sprite.isSuppressGroundWallCollision()
                || sprite.isObjectControlled()) {
            return;
        }
        // ROM only skips CalcRoomInFront while object_control bit 6 is set
        // (sonic3k.asm:22713,27958). Normal SolidObject/MvSonicOnPtfm
        // support, including the CNZ horizontal door's SolidObjectFull call
        // (sonic3k.asm:66249-66258), does not set that bit; if object_control
        // is already clear, an engine-only stale bit-6 analogue must not
        // survive onto the next movement frame.
        sprite.setSuppressGroundWallCollision(false);
    }

    void forceRidingObjectForBootstrap(PlayableEntity player, ObjectInstance instance) {
        if (player == null || instance == null) {
            return;
        }
        ridingStates.put(player, new RidingState(instance, instance.getX(), instance.getY(), -1));
        latestStandingSnapshots.put(player, new PlayerStandingState(ContactKind.TOP, true, false));
        inlineSupportedPlayers.add(player);
        player.setOnObject(true);
        player.setAir(false);
        player.setYSpeed((short) 0);
        if (player instanceof AbstractPlayableSprite sprite && instance.getSpawn() != null) {
            sprite.setLatchedSolidObject(instance.getSpawn().objectId(), instance);
        }
        setObjectStandingBit(player, instance);
    }

    void clearRidingObjectForJump(PlayableEntity player) {
        if (player == null) {
            return;
        }
        RidingState state = ridingStates.get(player);
        if (state != null && carriesAirborneRiderAfterExitPlatform(state.object)) {
            return;
        }
        clearRidingObject(player);
    }

    void forceAirOnStaleObjectSupportLoss(PlayableEntity player) {
        if (player != null) {
            forceAirOnStaleSupportLoss.add(player);
        }
    }

    boolean hasPendingStaleObjectSupportLoss(PlayableEntity player) {
        return player != null && forceAirOnStaleSupportLoss.contains(player);
    }

    void markObjectSupportThisFrame(PlayableEntity player) {
        if (player != null) {
            inlineSupportedPlayers.add(player);
        }
    }

    /**
     * Update the riding tracking position for a specific object without applying any delta.
     * This is used when an object moves itself (e.g. Tornado horizontal follow) AFTER
     * the player is already standing on it, to prevent SolidContacts from double-applying
     * that movement as a riding delta on the next frame.
     *
     * In the ROM, SolidObject runs inline during the object's update (before the horizontal
     * follow), so it never sees the follow delta. In our engine, SolidContacts runs after
     * all objects update, so we need this to synchronize the tracking position.
     */
    void refreshRidingTrackingPosition(ObjectInstance object) {
        for (var entry : ridingStates.entrySet()) {
            RidingState state = entry.getValue();
            if (state != null && state.object == object) {
                entry.setValue(new RidingState(object, object.getX(), object.getY(), state.pieceIndex));
            }
        }
    }

    ObjectInstance getRidingObject(PlayableEntity player) {
        if (player == null) return null;
        RidingState state = ridingStates.get(player);
        return state != null ? state.object : null;
    }

    private boolean blocksSolidContacts(PlayableEntity player, ObjectInstance candidate) {
        // ROM SolidObject_ChkBounds (docs/s2disasm/s2.asm:35181-35182):
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
                && provider.getSolidRoutineProfile().allowsObjectControlledSolidContacts()) {
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
        if (player == null) return -1;
        RidingState state = ridingStates.get(player);
        return state != null ? state.pieceIndex : -1;
    }

    /** Check if the current player (set during update()) is riding the given object. Used by internal resolve methods. */
    private boolean isRidingCurrentPlayerObject(ObjectInstance instance) {
        if (currentPlayer == null) return false;
        RidingState state = ridingStates.get(currentPlayer);
        return state != null && state.object == instance;
    }

    /** Get the piece index the current player is riding. Used by internal resolve methods. */
    private int getCurrentPlayerRidingPieceIndex() {
        if (currentPlayer == null) return -1;
        RidingState state = ridingStates.get(currentPlayer);
        return state != null ? state.pieceIndex : -1;
    }

    /** Player X speed captured before any solid contact resolution modified it. */
    short getPreContactXSpeed() { return preContactXSpeed; }
    /** Player Y speed captured before any solid contact resolution modified it. */
    short getPreContactYSpeed() { return preContactYSpeed; }
    /** Player rolling state captured before any solid contact resolution modified it. */
    boolean getPreContactRolling() { return preContactRolling; }

    void beginInlineFrame(PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            boolean postMovement) {
        this.postMovement = postMovement;
        frameCounter++;
        inlineSupportedPlayers.clear();
        // objectStandingBitSet intentionally not cleared per-frame: ROM's
        // a0.d6 standing-bit persists across frames until SolidObjectFull2_1P
        // explicitly clears it via loc_1DCF0.
        // objectStandingBitSnapshot IS cleared per-frame: it caches the
        // pre-clear value of the bit for the lift gate to read.
        objectStandingBitSnapshot.clear();
    }

    void finishInlineFrame(PlayableEntity player, List<? extends PlayableEntity> sidekicks) {
        finalizeInlinePlayer(player);
        for (PlayableEntity sidekick : sidekicks) {
            finalizeInlinePlayer(sidekick);
        }
        currentPlayer = null;
    }

    private boolean isCurrentlySupported(PlayableEntity player) {
        if (player == null || player.getDead() || player.isDebugMode()) {
            return false;
        }
        RidingState state = ridingStates.get(player);
        return state != null && state.object != null;
    }

    private void finalizeInlinePlayer(PlayableEntity player) {
        if (player == null) {
            return;
        }
        if (player.getDead() || player.isDebugMode()) {
            ridingStates.remove(player);
            latestStandingSnapshots.remove(player);
            forceAirOnStaleSupportLoss.remove(player);
            return;
        }
        if (!inlineSupportedPlayers.contains(player)) {
            // ROM parity: Status_OnObj is set by an interactive controller
            // (e.g. CNZ wire cage sub_33C34 at sonic3k.asm:70179 `bset #Status_OnObj,status(a1)`)
            // and stays set across frames until the controller itself clears it
            // (cage does so at loc_33A0E line 69989 `bclr #Status_OnObj,status(a1)`).
            // The engine SolidContacts ridingStates only tracks players riding
            // SolidObjectProvider instances; non-solid latch-and-own controllers
            // (CnzWireCageObjectInstance, CnzBarberPoleObjectInstance) record
            // their grip via setLatchedSolidObjectId(spawnId), but the
            // SolidContacts inlineSupportedPlayers set never gains the player
            // because no SolidObject is in play. Without honouring that signal,
            // finalizeInlinePlayer would clear OnObject every frame and the
            // sidekick CPU controller (sonic3k.asm:26690 loc_13DA6 reads
            // Sonic.Status_OnObj when computing leadOffset) would mis-trigger
            // the auto-jump path. Skip the clear when a latch is active.
            if (player instanceof AbstractPlayableSprite aps
                    && aps.getLatchedSolidObjectId() != 0) {
                return;
            }
            boolean forceAir = forceAirOnStaleSupportLoss.remove(player);
            ridingStates.remove(player);
            player.setOnObject(false);
            if (forceAir) {
                player.setAir(true);
            }
            latestStandingSnapshots.remove(player);
        }
    }

    SolidCheckpointBatch processManualCheckpoint(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement) {
        return resolveCheckpointBatch(instance, player, sidekicks, postMovement, false);
    }

    SolidCheckpointBatch processCompatibilityCheckpoint(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement) {
        return resolveCheckpointBatch(instance, player, sidekicks, postMovement, true);
    }

    private SolidCheckpointBatch resolveCheckpointBatch(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, boolean postMovement,
            boolean compatibilityCallbacks) {
        this.postMovement = postMovement;
        IdentityHashMap<PlayableEntity, PlayerSolidContactResult> perPlayer = new IdentityHashMap<>();
        CollisionTrace trace = collisionTrace();
        trace.onSolidCheckpointStart(instance.getClass().getSimpleName(), instance.getX(), instance.getY());
        resolveCheckpointForPlayer(instance, player, perPlayer, compatibilityCallbacks);
        for (PlayableEntity sidekick : sidekicks) {
            resolveCheckpointForPlayer(instance, sidekick, perPlayer, compatibilityCallbacks);
        }
        SolidCheckpointBatch batch = new SolidCheckpointBatch(instance, perPlayer);
        return batch;
    }

    private void resolveCheckpointForPlayer(ObjectInstance instance, PlayableEntity player,
            IdentityHashMap<PlayableEntity, PlayerSolidContactResult> perPlayer,
            boolean compatibilityCallbacks) {
        if (player == null) {
            return;
        }

        currentPlayer = player;
        preContactXSpeed = player.getXSpeed();
        preContactYSpeed = player.getYSpeed();
        preContactRolling = player.getRolling();
        preContactAnimationId = player.getAnimationId();

        PreContactState preContact = new PreContactState(
                preContactXSpeed, preContactYSpeed, preContactRolling, preContactAnimationId);
        SolidContact contact = processInlineObjectForPlayer(instance, player);
        PostContactState postContact = new PostContactState(
                player.getXSpeed(), player.getYSpeed(), player.getAir(),
                player.isOnObject(), currentPushingState(player));
        PlayerStandingState previousStanding =
                objectManager.services().solidExecutionRegistry().previousStanding(instance, player);

        if (contact == null) {
            PlayerSolidContactResult result =
                    PlayerSolidContactResult.noContact(previousStanding, preContact, postContact);
            perPlayer.put(player, result);
            cacheStandingSnapshot(player, new PlayerStandingState(
                    result.kind(), result.standingNow(), result.pushingNow()));
            cacheHeadroomSnapshot(player, player.getAngle(),
                    getHeadroomDistance(player, player.getAngle()));
            if (compatibilityCallbacks && instance instanceof SolidObjectListener listener) {
                listener.onSolidContactCleared(player, frameCounter);
            }
        } else {
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
            cacheStandingSnapshot(player, new PlayerStandingState(
                    result.kind(), result.standingNow(), result.pushingNow()));
            cacheHeadroomSnapshot(player, player.getAngle(),
                    getHeadroomDistance(player, player.getAngle()));
            if (compatibilityCallbacks && instance instanceof SolidObjectListener listener) {
                listener.onSolidContact(player, contact, frameCounter);
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

    private SolidContact processInlineObjectForPlayer(ObjectInstance instance, PlayableEntity player) {
        if (player == null || objectManager == null || player.getDead()) {
            if (player != null) {
                ridingStates.remove(player);
            }
            return null;
        }
        if (player.isDebugMode()) {
            ridingStates.remove(player);
            return null;
        }

        // Reset per-object/per-player multi-piece earlier-slot pre-resolution
        // tracking; set only when resolveEarlierMultiPieceSiblings runs below and
        // consumed by the multi-piece pass for this same instance.
        multiPieceEarlierPiecesResolvedUpTo = -1;

        RidingState state = ridingStates.get(player);
        ObjectInstance ridingObject = state != null ? state.object : null;
        int ridingX = state != null ? state.x : 0;
        int ridingY = state != null ? state.y : 0;
        int ridingPieceIndex = state != null ? state.pieceIndex : -1;

        ObjectInstance unseatedRidingObject = null;
        // S3K SolidObjectFull tests Player_2 render_flags before entering
        // the helper that clears Status_OnObj/d6 for airborne riders
        // (docs/skdisasm/sonic3k.asm:41006-41010 before 41021-41034).
        if (ridingObject != null && player.getAir()
                && !carriesAirborneRiderAfterExitPlatform(ridingObject)
                && !shouldSkipRidingAirUnseatForOffscreenSidekick(player, ridingObject)) {
            // ROM SolidObjectFull2_1P air-unseat path (sonic3k.asm:41070-41084
            // loc_1DCF0): when the object's a0.d6 standing-bit is still
            // set and Status_InAir is set on the player, the helper
            // clears the bit and returns d4=0 WITHOUT falling into
            // SolidObject_cont, so loc_1E154's position lift never fires.
            // The bit clear (loc_1DCF0 bclr d6, status(a0)) is per-object
            // and only fires when the SPRING's own SolidObjectFull2_1P
            // call runs (`instance == ridingObject`).  Other objects'
            // SolidObject calls leave this spring's d6 untouched, so we
            // must NOT clear the bit when a non-riding-object instance
            // happens to be processed first.
            unseatedRidingObject = ridingObject;
            ridingStates.remove(player);
            ridingObject = null;
            ridingPieceIndex = -1;
            player.setOnObject(false);
        }
        // ROM loc_1DCF0 bit clear (sonic3k.asm:41079-41084) runs when the
        // OBJECT's own SolidObjectFull2_1P call sees its a0.d6 set and the
        // player's Status_InAir set.  Snapshot the bit BEFORE clearing so
        // the resolveContactInternal lift gate (which checks
        // hasObjectStandingBit on this same instance) sees the pre-clear
        // state for this frame.  The snapshot lives on a per-frame map
        // (objectStandingBitSnapshot) keyed by spawn so the lift gate can
        // consult it after the bit-clear has propagated.
        if (instance != null && player.getAir()
                && hasObjectStandingBit(player, instance)
                && !shouldSkipRidingAirUnseatForOffscreenSidekick(player, instance)) {
            snapshotObjectStandingBit(player, instance);
            clearObjectStandingBit(player, instance);
            if (instance instanceof SolidObjectProvider staleStandingProvider
                    && staleStandingProvider.airborneStaleStandingBitReturnsNoContact(player)) {
                // ROM SolidObjectFull_1P stale-rider branch: when this
                // object's standing bit is set and the player is already
                // airborne, loc_1DC98 clears Status_OnObj/d6 and returns
                // d4=0 without falling through to SolidObject_cont or
                // Solid_Landed (docs/skdisasm/sonic3k.asm:41017-41035).
                // S2's shared helper follows the same contract
                // (docs/s2disasm/s2.asm:34831-34849). Keep the no-contact
                // return keyed to explicit SolidObjectFull-style providers:
                // custom objects such as CNZCylinder use standing bits for
                // object-local capture/held-rider paths, but still need the
                // pre-existing snapshot/clear behavior above.
                player.setOnObject(false);
                player.setAir(true);
                return null;
            }
        }

        // ROM SolidObjectFull*_1P / SolidObjectTop*_1P air-unseat
        // (sonic3k.asm:41021-41031 SolidObjectFull_1P loc_1DC98,
        // 41066-41084 SolidObjectFull2_1P loc_1DCF0, 41117-41128
        // sub_1DD24 loc_1DD48, 41793-41812 SolidObjectTop_1P loc_1E2E0):
        // when the helper sees its own a0.d6 standing-bit set AND the
        // player's Status_InAir set, it bclr's OnObj/d6 and returns d4=0
        // WITHOUT falling into SolidObject_cont -> loc_1E154 -> RideObject_SetRide.
        // Mirror the early `rts d4=0` so the new-contact resolution path
        // does not run for the SAME instance whose ride was just
        // air-unseated this frame.  Without this gate, the engine
        // re-lands an airborne player on the same object the same
        // frame, leaving Status_OnObj set when ROM has it cleared
        // (CNZ trace F7872 / AIZ F7381).  Scoped strictly to
        // `instance == unseatedRidingObject` to mirror ROM's per-object
        // d6/Status_InAir gate at the SolidObjectFull*_1P entry; a
        // broader gate on the bare standing-bit-snapshot would suppress
        // legitimate first-frame contacts with neighbouring objects.
        if (instance != null && instance == unseatedRidingObject) {
            return null;
        }

        if (!(instance instanceof SolidObjectProvider provider)) {
            return null;
        }
        SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();
        if (shouldSkipOffscreenSidekickFullSolid(player, instance, solidProfile)) {
            if (instance == ridingObject) {
                // ROM returns before SolidObjectFull_1P for offscreen Player_2
                // (docs/skdisasm/sonic3k.asm:41006-41010), so the existing
                // standing bit and Status_OnObj survive without a carry step.
                ridingStates.put(player, new RidingState(instance, ridingX, ridingY, ridingPieceIndex));
                setObjectStandingBit(player, instance);
                inlineSupportedPlayers.add(player);
            }
            return null;
        }

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
                    && earlyMulti.resolvesEarlierPiecesBeforeRidingPiece()
                    && !player.getAir()
                    && provider.isSolidFor(player)
                    && !blocksSolidContacts(player, instance)
                    && !instance.isSkipSolidContactThisFrame()) {
                resolveEarlierMultiPieceSiblings(
                        player, earlyMulti, instance, ridingPieceIndex,
                        frameCounter, solidProfile.stickyContactBuffer());
                multiPieceEarlierPiecesResolvedUpTo = ridingPieceIndex;
            }
            SolidContact ridingContact = processInlineRidingObject(
                    player, instance, provider, ridingX, ridingY, ridingPieceIndex);
            if (!(provider instanceof MultiPieceSolidProvider)) {
                return ridingContact;
            }
            // ROM parity for staircase-style multi-piece objects: after carrying Sonic
            // on the currently ridden piece, later sibling pieces of the same logical
            // object must still get a chance to apply side/top/bottom collision.
            // Returning early here skips the "next block in the staircase" wall hit.
        }

        if (provider.skipsCpuSidekickWhenRenderFlagOffScreen()
                && player instanceof AbstractPlayableSprite sprite
                && sprite.isCpuControlled()
                && sprite.hasRenderFlagOnScreenState()
                && !sprite.isRenderFlagOnScreen()) {
            return null;
        }
        if (!provider.isSolidFor(player)) {
            return null;
        }
        if (blocksSolidContacts(player, instance)) {
            return null;
        }
        if (instance.isSkipSolidContactThisFrame()) {
            return null;
        }

        if (provider instanceof MultiPieceSolidProvider multiPiece) {
            MultiPieceContactResult result = processMultiPieceCollision(
                    player, multiPiece, instance, frameCounter, solidProfile.stickyContactBuffer());
            if (result.pushing()) {
                player.setPushing(true);
                setObjectPushingBit(player, instance);
                provider.setPlayerPushing(player, true);
            } else if (clearObjectPushingBit(player, instance)) {
                player.setPushing(false);
                provider.setPlayerPushing(player, false);
            }
            if (result.standing()) {
                ridingStates.put(player, new RidingState(
                        instance, result.ridingX(), result.ridingY(), result.pieceIndex()));
                setObjectStandingBit(player, instance);
                clearGroundWallSuppressionForNormalSolidSupport(player, instance);
                inlineSupportedPlayers.add(player);
            }
            return result.aggregateContact();
        }

        SolidObjectParams params = provider.getSolidParams();
        boolean usePreUpdateContactPosition = provider.usesPreUpdatePositionForSolidContact(player);
        int anchorX = (usePreUpdateContactPosition ? instance.getPreUpdateX() : instance.getX())
                + params.offsetX();
        int anchorY = (usePreUpdateContactPosition ? instance.getPreUpdateY() : instance.getY())
                + params.offsetY();
        int halfHeight = solidProfile.topSolidOnly()
                && solidProfile.usesGroundHalfHeightForTopSolidContact()
                        ? params.groundHalfHeight()
                        : params.airHalfHeight();
        boolean useStickyBuffer = solidProfile.stickyContactBuffer();
        boolean wasAirborne = player.getAir();
        boolean wasRidingObject = useStickyBuffer && isRidingCurrentPlayerObject(instance);

        // ROM parity: SolidObject_cont (s2.asm:35140-35145 SolidObject_OnScreenTest,
        // sonic3k.asm:41390-41392 loc_1DF88, s1disasm/_incObj/sub SolidObject.asm:124-126
        // Solid_ChkEnter and 86-87 SolidObject2F) gates the side/top/bottom contact
        // path on the object's render_flags bit 7. Render_Sprites clears bit 7 each
        // frame (sonic3k.asm:36338) and re-sets it only when the object's bounding
        // box overlaps the screen (sonic3k.asm:36370). When clear, ROM jumps to
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
        // Gated by PhysicsFeatureSet.solidObjectOffscreenGate so we can roll out
        // the engine-wide ROM-parity behaviour incrementally without disturbing
        // existing S1/S2 trace baselines that depend on the prior (more
        // permissive) collision semantics.  See PhysicsFeatureSet definitions.
        //
        // The riding-state branch above (processInlineRidingObject) is unaffected:
        // ROM platform-ride (MvSonicOnPtfm via SolidObjectFull_1P standing branch)
        // runs BEFORE the on-screen test, so off-screen platforms still carry the
        // rider. Only the new-contact resolveContact path is gated here.
        //
        // Per-object opt-out (bypassesOffscreenSolidGate): the ROM gate at
        // loc_1DF88 lives only in SolidObjectFull_1P (sonic3k.asm:41016-41018).
        // Spring variants and several other objects route through the sibling
        // helper SolidObjectFull2_1P (sonic3k.asm:41065-41067) which falls
        // through directly to SolidObject_cont without the bit-7 test, so
        // they must continue to resolve push/side contact even when their
        // bounding box has scrolled off-screen.  Without this opt-out, the
        // AIZ trace replay F2919 horizontal spring at (0x1F39, 0x04A0)
        // failed to launch Tails because the spring's bounding box sits
        // ~0xAA px below the camera viewport at that frame.
        //
        // Top-only opt-out: ROM SolidObjectTop_1P (sonic3k.asm:41793-41819),
        // SolidObjectTopSloped_1P (sonic3k.asm:41887-41914), and
        // SolidObjectTopSloped2_1P (sonic3k.asm:41840-41867) ALL bypass
        // loc_1DF88 entirely.  When the player isn't yet standing
        // (d6,status(a0) clear), they branch directly into SolidObjCheckSloped /
        // SolidObjCheckSloped2 / loc_1E42E (sonic3k.asm:42071, 42095, 41982)
        // which do NOT test render_flags(a0). The same pattern holds in S2:
        // SlopedSolid_SingleCharacter (s2.asm:34927-34952) jumps to
        // SlopedSolid_cont (s2.asm:35066) without any on-screen test, and
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
                && !instance.isWithinSolidContactBounds()) {
            // ROM sub_1E0C2 (sonic3k.asm:41528-41532): off-screen / no-contact
            // path clears the player's push status and the object's pushing-bit
            // bookkeeping but does not touch ground_vel/x_vel. Matches both
            // S2 SolidObject_TestClearPush and S1 Solid_NotPushing.
            if (clearObjectPushingBit(player, instance)) {
                player.setPushing(false);
                provider.setPlayerPushing(player, false);
            }
            return null;
        }

        SolidContact contact;
        SlopedSolidRoutineAdapter slopedAdapter = null;
        byte[] slopeData = null;
        if (instance instanceof SlopedSolidProvider sloped) {
            slopedAdapter = SlopedSolidRoutineProfile.adapt(sloped);
            slopeData = slopedAdapter.getSlopeData();
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
            if (clearObjectPushingBit(player, instance)) {
                player.setPushing(false);
                provider.setPlayerPushing(player, false);
            }
            return null;
        }
        applyNonUnifiedTopSolidLandingHeightOverride(
                player, contact, instance, solidProfile, wasAirborne, wasRidingObject, anchorY, params);
        if ((contact.standing() || contact.touchTop())
                && instance.getSpawn() != null
                && player instanceof AbstractPlayableSprite sprite) {
            sprite.setLatchedSolidObject(instance.getSpawn().objectId(), instance);
        }
        if (contact.pushing()) {
            player.setPushing(true);
            setObjectPushingBit(player, instance);
            provider.setPlayerPushing(player, true);
        }
        if (contact.standing()) {
            int newRideBaselineX = provider.seedsNewRideCarryFromPreUpdateX()
                    ? instance.getPreUpdateX()
                    : instance.getX();
            ridingStates.put(player, new RidingState(instance, newRideBaselineX, instance.getY(), -1));
            setObjectStandingBit(player, instance);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            inlineSupportedPlayers.add(player);
        }
        return contact;
    }

    private SolidContact processInlineRidingObject(PlayableEntity player, ObjectInstance instance,
            SolidObjectProvider provider, int ridingX, int ridingY, int ridingPieceIndex) {
        SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();
        int currentX;
        int currentY;
        SolidObjectParams params;

        if (ridingPieceIndex >= 0 && instance instanceof MultiPieceSolidProvider multiPiece) {
            currentX = multiPiece.getPieceX(ridingPieceIndex);
            currentY = multiPiece.getPieceY(ridingPieceIndex);
            params = multiPiece.getPieceParams(ridingPieceIndex);
        } else {
            currentX = instance.getX();
            currentY = instance.getY();
            params = provider.getSolidParams();
        }

        if (player.getAir()) {
            if (solidProfile.carriesAirborneRiderAfterExitPlatform()) {
                // Sonic 1 Obj52 MBlock_StandOn is not a normal PlatformObject
                // continued-riding path: it calls ExitPlatform, moves the block,
                // then still calls MvSonicOnPtfm2. That preserves the platform
                // delta on the jump-off frame while leaving Sonic airborne.
                applyRidingCarry(player, instance, provider, params,
                        currentX, currentY, ridingX);
            }
            ridingStates.remove(player);
            player.setOnObject(false);
            player.setAir(true);
            return null;
        }

        int halfWidth = params.halfWidth();
        // ROM: ExitPlatform walk-off checks use the full collision width of the
        // current solid, not the narrower Solid_Landed top-landing width.
        int ridingHalfWidth = halfWidth;

        int boundsX = currentX + params.offsetX();
        int relX = player.getCentreX() - boundsX + ridingHalfWidth;
        int stickyX = 0;
        int minRelX = -stickyX;
        int maxRelXExclusive = (ridingHalfWidth * 2) + stickyX;
        boolean inBounds = relX >= minRelX && relX < maxRelXExclusive;

        boolean objectManagedRide = inBounds
                && provider.preservesObjectManagedRideWhileNotSolidFor(player);
        if (objectManagedRide) {
            Integer managedCentreY = provider.getObjectManagedRideCentreY(player, currentY, params);
            if (managedCentreY != null) {
                if (player instanceof AbstractPlayableSprite sprite) {
                    NativePositionOps.writeYPosPreserveSubpixel(sprite, managedCentreY);
                } else {
                    int newY = managedCentreY - (player.getHeight() / 2);
                    player.setY((short) newY);
                }
            }
            ridingStates.put(player, new RidingState(instance, currentX, currentY, ridingPieceIndex));
            setObjectStandingBit(player, instance);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);
            player.setOnObject(true);
            player.setAir(false);
            inlineSupportedPlayers.add(player);
            return SolidContact.STANDING;
        }

        if (inBounds && provider.isSolidFor(player) && !blocksSolidContacts(player, instance)) {
            int deltaX = currentX - ridingX;
            if (deltaX != 0 && provider.carriesRiderOnHorizontalMove(player)) {
                player.shiftX(deltaX);
            }
            // ROM: S3K Obj_CollapsingPlatform state-1 -> state-2 transition
            // (sonic3k.asm:44814 loc_20594 -> sonic3k.asm:45394
            // ObjPlatformCollapse_CreateFragments) skips its sub_205B6
            // slope-sample / y-write on the transition frame. The provider
            // signals that one-frame skip via suppressSlopeSampleThisFrame().
            // Player riding state is preserved unchanged so Sonic continues
            // standing at last frame's y_pos.
            if (provider.suppressSlopeSampleThisFrame(player)) {
                ridingStates.put(player, new RidingState(instance, currentX, currentY, ridingPieceIndex));
                setObjectStandingBit(player, instance);
                clearGroundWallSuppressionForNormalSolidSupport(player, instance);
                inlineSupportedPlayers.add(player);
                return SolidContact.STANDING;
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
            ridingStates.put(player, new RidingState(instance, currentX, currentY, ridingPieceIndex));
            setObjectStandingBit(player, instance);
            clearGroundWallSuppressionForNormalSolidSupport(player, instance);

            if (solidProfile.dropOnFloor()) {
                TerrainCheckResult floorCheck = ObjectTerrainUtils.checkFloorDist(
                        player.getCentreX(), player.getCentreY(), player.getYRadius());
                if (floorCheck.distance() <= 0) {
                    ridingStates.remove(player);
                    player.setOnObject(false);
                    // Inline solid execution runs after terrain resolution for the frame.
                    // Releasing the object here should not force airborne state; preserve
                    // whatever the terrain/object pipeline already established.
                    return null;
                }
            }

            inlineSupportedPlayers.add(player);
            return SolidContact.STANDING;
        }

        ridingStates.remove(player);
        if (provider.sampleSlopeOnRideExit(player) && instance instanceof SlopedSolidProvider sloped) {
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
            // ROM PlatformObject_SingleCharacter exit path (s2.asm:35506-35511):
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
        return object instanceof SolidObjectProvider provider
                && provider.getSolidRoutineProfile().carriesAirborneRiderAfterExitPlatform();
    }

    private void applyRidingCarry(PlayableEntity player, ObjectInstance instance,
            SolidObjectProvider provider, SolidObjectParams params,
            int currentX, int currentY, int ridingX) {
        int deltaX = currentX - ridingX;
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

    boolean hasStandingContact(PlayableEntity player) {
        if (player == null || objectManager == null || player.getDead()) {
            return false;
        }
        if (player.isDebugMode()) {
            return false;
        }
        if (player.getYSpeed() < 0) {
            return false;
        }
        PlayableEntity savedPlayer = currentPlayer;
        currentPlayer = player;
        try {
            Collection<ObjectInstance> solidObjects = objectManager.getSolidProviderObjects();
            for (ObjectInstance instance : solidObjects) {
                SolidObjectProvider provider = (SolidObjectProvider) instance;
                if (!provider.isSolidFor(player)) {
                    continue;
                }
                SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();

                if (provider instanceof MultiPieceSolidProvider multiPiece) {
                    if (hasStandingContactMultiPiece(player, multiPiece, instance)) {
                        return true;
                    }
                    continue;
                }

                SolidObjectParams params = provider.getSolidParams();
                int anchorX = instance.getX() + params.offsetX();
                int anchorY = instance.getY() + params.offsetY();
                // ROM always uses airHalfHeight (d2) for the overlap test — d3 is
                // overwritten by playerYRadius before it is read.
                int halfHeight = params.airHalfHeight();
                boolean useStickyBuffer = solidProfile.stickyContactBuffer();
                SlopedSolidRoutineAdapter slopedAdapter = null;
                byte[] slopeData = null;
                if (instance instanceof SlopedSolidProvider sloped) {
                    slopedAdapter = SlopedSolidRoutineProfile.adapt(sloped);
                    slopeData = slopedAdapter.getSlopeData();
                }
                SolidContact contact;
                if (slopeData != null
                        && shouldUseSlopeForContact(instance, slopedAdapter)) {
                    int slopeHalfHeight = params.groundHalfHeight();
                    contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(), slopeHalfHeight,
                            solidProfile.topSolidOnly(), useStickyBuffer, instance, false, slopedAdapter);
                } else {
                    contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                            solidProfile, useStickyBuffer, instance, false);
                }
                if (contact != null && contact.standing()) {
                    return true;
                }
            }
            return false;
        } finally {
            currentPlayer = savedPlayer;
        }
    }

    boolean latestStandingSnapshot(PlayableEntity player) {
        if (player == null || player.getDead() || player.isDebugMode()) {
            return false;
        }
        PlayerStandingState snapshot = latestStandingSnapshots.get(player);
        return (snapshot != null && snapshot.standing())
                || hasPreMovementGroundAttachmentSupport(player);
    }

    private boolean hasPreMovementGroundAttachmentSupport(PlayableEntity player) {
        int playerCenterX = player.getCentreX();
        int playerCenterY = player.getCentreY();
        int playerYRadius = player.getYRadius();
        for (ObjectInstance instance : objectManager.getSolidProviderObjects()) {
            if (!(instance instanceof SolidObjectProvider provider)
                    || !provider.providesPreMovementGroundAttachmentSupport()
                    || !provider.isSolidFor(player)
                    || blocksSolidContacts(player, instance)) {
                continue;
            }
            SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();
            if (!solidProfile.topSolidOnly()) {
                continue;
            }
            SolidObjectParams params = provider.getSolidParams();
            int anchorX = instance.getX() + params.offsetX();
            int anchorY = instance.getY() + params.offsetY();
            int halfWidth = params.halfWidth();
            int relX = playerCenterX - anchorX + halfWidth;
            if (relX < 0 || relX >= halfWidth * 2) {
                continue;
            }
            int maxTop = params.airHalfHeight() + playerYRadius;
            int relY = playerCenterY - anchorY + 4 + maxTop;
            if (relY >= -0x10 && relY <= 0x10) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStandingContactMultiPiece(PlayableEntity player,
            MultiPieceSolidProvider multiPiece, ObjectInstance instance) {
        int pieceCount = multiPiece.getPieceCount();
        for (int i = 0; i < pieceCount; i++) {
            SolidObjectParams params = multiPiece.getPieceParams(i);
            int anchorX = multiPiece.getPieceX(i) + params.offsetX();
            int anchorY = multiPiece.getPieceY(i) + params.offsetY();
            int halfHeight = player.getAir() ? params.airHalfHeight() : params.groundHalfHeight();
            SolidRoutineProfile solidProfile = multiPiece.getSolidRoutineProfile();
            boolean useStickyBuffer = solidProfile.stickyContactBuffer();
            SlopedSolidRoutineAdapter slopedAdapter = null;
            byte[] slopeData = null;
            if (instance instanceof SlopedSolidProvider sloped) {
                slopedAdapter = SlopedSolidRoutineProfile.adapt(sloped);
                slopeData = slopedAdapter.getSlopeData();
            }

            SolidContact contact;
            if (slopeData != null && shouldUseSlopeForContact(instance, slopedAdapter)) {
                contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(),
                        params.groundHalfHeight(), solidProfile.topSolidOnly(), useStickyBuffer,
                        instance, false, slopedAdapter);
            } else {
                // Multi-piece solids don't use monitor solidity
                contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        solidProfile, useStickyBuffer, instance, false);
            }
            if (contact != null && contact.standing()) {
                return true;
            }
        }
        return false;
    }

    int latestHeadroomSnapshot(PlayableEntity player, int hexAngle) {
        if (player == null || objectManager == null || player.getDead()) {
            return Integer.MAX_VALUE;
        }
        if (player.isDebugMode()) {
            return Integer.MAX_VALUE;
        }
        Map<Integer, Integer> perAngle = latestHeadroomSnapshots.get(player);
        if (perAngle != null) {
            Integer cached = perAngle.get(hexAngle & 0xFF);
            if (cached != null) {
                return cached;
            }
        }
        return Integer.MAX_VALUE;
    }

    int getHeadroomDistance(PlayableEntity player, int hexAngle) {
        if (player == null || objectManager == null || player.getDead()) {
            return Integer.MAX_VALUE;
        }
        if (player.isDebugMode()) {
            return Integer.MAX_VALUE;
        }

        int overheadAngle = (hexAngle + 0x80) & 0xFF;
        int quadrant = (overheadAngle + 0x20) & 0xC0;

        int minDistance = Integer.MAX_VALUE;
        int playerCenterX = player.getCentreX();
        int playerCenterY = player.getCentreY();
        int playerXRadius = player.getXRadius();
        int playerYRadius = player.getYRadius();

        Collection<ObjectInstance> solidObjects = objectManager.getSolidProviderObjects();
        for (ObjectInstance instance : solidObjects) {
            SolidObjectProvider provider = (SolidObjectProvider) instance;
            if (!provider.isSolidFor(player)) {
                continue;
            }
            SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();
            if (solidProfile.topSolidOnly()) {
                continue;
            }
            SolidObjectParams params = provider.getSolidParams();
            int anchorX = instance.getX() + params.offsetX();
            int anchorY = instance.getY() + params.offsetY();
            int halfWidth = params.halfWidth();
            int halfHeight = params.groundHalfHeight();

            int distance = calculateOverheadDistance(quadrant, playerCenterX, playerCenterY,
                    playerXRadius, playerYRadius, anchorX, anchorY, halfWidth, halfHeight);
            if (distance >= 0 && distance < minDistance) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    private int calculateOverheadDistance(int quadrant, int playerCenterX, int playerCenterY,
            int playerXRadius, int playerYRadius, int objX, int objY, int objHalfWidth, int objHalfHeight) {
        switch (quadrant) {
            case 0x40 -> {
                int objRight = objX + objHalfWidth;
                int playerLeft = playerCenterX - playerXRadius;
                if (playerLeft < objRight) {
                    return -1;
                }
                int objTop = objY - objHalfHeight;
                int objBottom = objY + objHalfHeight;
                int playerTop = playerCenterY - playerYRadius;
                int playerBottom = playerCenterY + playerYRadius;
                if (playerBottom < objTop || playerTop > objBottom) {
                    return -1;
                }
                return playerLeft - objRight;
            }
            case 0x80 -> {
                int objBottom = objY + objHalfHeight;
                int playerTop = playerCenterY - playerYRadius;
                if (playerTop < objBottom) {
                    return -1;
                }
                int objLeft = objX - objHalfWidth;
                int objRight = objX + objHalfWidth;
                int playerLeft = playerCenterX - playerXRadius;
                int playerRight = playerCenterX + playerXRadius;
                if (playerRight < objLeft || playerLeft > objRight) {
                    return -1;
                }
                return playerTop - objBottom;
            }
            case 0xC0 -> {
                int objLeft = objX - objHalfWidth;
                int playerRight = playerCenterX + playerXRadius;
                if (playerRight > objLeft) {
                    return -1;
                }
                int objTop = objY - objHalfHeight;
                int objBottom = objY + objHalfHeight;
                int playerTop = playerCenterY - playerYRadius;
                int playerBottom = playerCenterY + playerYRadius;
                if (playerBottom < objTop || playerTop > objBottom) {
                    return -1;
                }
                return objLeft - playerRight;
            }
            default -> {
                return Integer.MAX_VALUE;
            }
        }
    }

    // KNOWN ARCHITECTURAL DIFFERENCE: The ROM processes solid object contacts inline
    // during each object's update routine, so each subsequent object sees the player's
    // position as updated by earlier solid contacts within the same frame. This engine
    // instead batches all solid contacts in a single pass after object updates, meaning
    // every object sees the player's position from the START of the pass. This can cause
    // differences with adjacent or sandwiching solid objects and with crush detection
    // timing, since the cumulative position adjustments don't propagate between objects
    // within the same frame. The sticky buffer and subpixel workarounds partially
    // compensate for this, but a full fix would require per-object inline resolution
    // integrated into the object update loop.
    void update(PlayableEntity player, boolean postMovement) {
        this.postMovement = postMovement;
        frameCounter++;
        // objectStandingBitSet intentionally not cleared per-frame: see
        // beginInlineFrame note. ROM a0.d6 persists across frames.
        objectStandingBitSnapshot.clear();
        if (player == null || objectManager == null || player.getDead()) {
            if (player != null) ridingStates.remove(player);
            return;
        }

        if (player.isDebugMode()) {
            ridingStates.remove(player);
            return;
        }

        if (!isCurrentlySupported(player)) {
            latestStandingSnapshots.remove(player);
        }

        // Set currentPlayer so internal resolveContact/resolveSlopedContact can check
        // this player's riding state via isRidingCurrentPlayerObject()
        currentPlayer = player;

        // Snapshot pre-contact state before any resolveContact can modify the player.
        preContactXSpeed = player.getXSpeed();
        preContactYSpeed = player.getYSpeed();
        preContactRolling = player.getRolling();

        // Note: Do NOT clear pushing here. Terrain collision handles pushing for terrain walls,
        // and solid object collision sets pushing when appropriate. Clearing here would
        // override the pushing flag set by terrain collision earlier in the same frame.

        Collection<ObjectInstance> solidObjects = objectManager.getSolidProviderObjects();

        // Extract this player's riding state
        RidingState state = ridingStates.get(player);
        ObjectInstance ridingObject = state != null ? state.object : null;
        int ridingX = state != null ? state.x : 0;
        int ridingY = state != null ? state.y : 0;
        int ridingPieceIndex = state != null ? state.pieceIndex : -1;
        ObjectInstance dropOnFloorExclude = null;

        // ROM: Sonic_Jump does "bclr #sta_onObj,obStatus(a0)" before any
        // platform's SolidObject routine runs.  If the player is airborne,
        // they must not be repositioned by a stale riding record. S3K
        // SolidObjectFull gates offscreen Player_2 before SolidObjectFull_1P,
        // so that gate also precedes the Status_InAir riding unseat branch
        // (docs/skdisasm/sonic3k.asm:41006-41010 before 41021-41034).
        if (ridingObject != null && player.getAir()
                && !shouldSkipRidingAirUnseatForOffscreenSidekick(player, ridingObject)) {
            ridingStates.remove(player);
            ridingObject = null;
            ridingPieceIndex = -1;
            player.setOnObject(false);
        }
        if (ridingObject != null && ridingObject instanceof SolidObjectProvider provider) {
            SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();
            // ROM: S3K SolidObjectFull tests Player_2 render_flags before
            // entering SolidObjectFull_1P (sonic3k.asm:41006-41010), so an
            // offscreen CPU sidekick does not run the standing-bounds unseat
            // branch at 41021-41034. Keep the existing ride latched.
            if (!shouldSkipOffscreenSidekickFullSolid(player, ridingObject, solidProfile)) {
                int currentX;
                int currentY;
                SolidObjectParams params;

                if (ridingPieceIndex >= 0 && ridingObject instanceof MultiPieceSolidProvider multiPiece) {
                    currentX = multiPiece.getPieceX(ridingPieceIndex);
                    currentY = multiPiece.getPieceY(ridingPieceIndex);
                    params = multiPiece.getPieceParams(ridingPieceIndex);
                } else {
                    currentX = ridingObject.getX();
                    currentY = ridingObject.getY();
                    params = provider.getSolidParams();
                }

                int halfWidth = params.halfWidth();
                // ROM: continued riding uses ExitPlatform / ExitPlatform2 semantics,
                // which check the full collision width. Narrow top widths only apply
                // to new Solid_Landed-style landings.
                int ridingHalfWidth = halfWidth;

                // ROM: Bounds check uses collision-offset X (anchorX = obX + offsetX),
                // while delta tracking uses raw object X for movement following.
                int boundsX = currentX + params.offsetX();
                int relX = player.getCentreX() - boundsX + ridingHalfWidth;
                // ROM: ExitPlatform / SolidObject riding checks use exact bounds
                // (relX in [0, width*2)). Sticky margins belong to the engine's
                // new-contact jitter compensation, not continued ride support.
                int stickyX = 0;
                int minRelX = -stickyX;
                int maxRelXExclusive = (ridingHalfWidth * 2) + stickyX;
                boolean inBounds = relX >= minRelX && relX < maxRelXExclusive;
                // ROM: s2.asm:35387 — skip repositioning if obj_control bit 7 set
                if (inBounds && provider.isSolidFor(player) && !blocksSolidContacts(player, ridingObject)) {
                    // ROM: s2.asm:35377-35401 — X uses delta tracking, Y uses absolute positioning
                    int deltaX = currentX - ridingX;
                    if (deltaX != 0) {
                        player.shiftX(deltaX);
                    }
                    // ROM: Sloped objects use SlopeObject2 for riding updates,
                    // which re-samples the slope heightmap at the player's X each
                    // frame: surfaceY = obY - slopeSample; playerY = surfaceY - yRadius.
                    // Flat objects use MvSonicOnPtfm: y = obY - d3 - yRadius.
                    // d3 is the GROUND half-height (walking), NOT d2 (air/jumping).
                    // ROM: MvSonicOnPtfm uses d3 which was set by the caller before
                    // SolidObject was called. For spikes d3=$11, push blocks d3=$11,
                    // platforms d3=$10, etc.
                    int surfaceOffset;
                    if (ridingObject instanceof SlopedSolidProvider sloped) {
                        int slopeAnchorX = currentX + params.offsetX();
                        int slopeY = sampleSlopeY(player, slopeAnchorX, params.halfWidth(), sloped);
                        surfaceOffset = (slopeY != Integer.MIN_VALUE) ? slopeY : params.groundHalfHeight();
                    } else {
                        surfaceOffset = params.groundHalfHeight();
                    }
                    int newCentreY = currentY + params.offsetY() - surfaceOffset - player.getYRadius();
                    int newY = newCentreY - (player.getHeight() / 2);
                    player.setY((short) newY);
                    ridingX = currentX;
                    ridingY = currentY;
                    // Update state with new tracking position
                    ridingStates.put(player, new RidingState(ridingObject, ridingX, ridingY, ridingPieceIndex));
                    setObjectStandingBit(player, ridingObject);
                    clearGroundWallSuppressionForNormalSolidSupport(player, ridingObject);

                    // ROM: DropOnFloor (s2.asm:35810) — after repositioning the player
                    // on a platform, check if terrain is at or above the player's feet.
                    // If so, detach the player so terrain collision takes over next frame.
                    if (solidProfile.dropOnFloor()) {
                        TerrainCheckResult floorCheck = ObjectTerrainUtils.checkFloorDist(
                                player.getCentreX(), player.getCentreY(), player.getYRadius());
                        if (floorCheck.distance() <= 0) {
                            dropOnFloorExclude = ridingObject;
                            ridingStates.remove(player);
                            ridingObject = null;
                            ridingPieceIndex = -1;
                            player.setOnObject(false);
                            player.setAir(true);
                        }
                    }
                } else {
                    if (!inBounds) {
                        if (provider.sampleSlopeOnRideExit(player)
                                && ridingObject instanceof SlopedSolidProvider sloped) {
                            int slopeAnchorX = currentX + params.offsetX();
                            int slopeY = sampleSlopeY(player, slopeAnchorX, params.halfWidth(), sloped);
                            if (slopeY != Integer.MIN_VALUE) {
                                int newCentreY = currentY + params.offsetY() - slopeY - player.getYRadius();
                                int newY = newCentreY - (player.getHeight() / 2);
                                player.setY((short) newY);
                            }
                        }
                        // ROM standing-object paths clear Status_OnObj and set
                        // Status_InAir as soon as the rider leaves the object's
                        // ride bounds: SolidObjectFull_1P loc_1DC98
                        // (sonic3k.asm:41030-41034) and SolidObjectTop_1P
                        // loc_1E2E0 (sonic3k.asm:41807-41812). The interact
                        // latch can remain non-zero; it is not itself support.
                        player.setOnObject(false);
                        if (solidProfile.forceAirOnRideExit()) {
                            player.setAir(true);
                        }
                    }
                    ridingStates.remove(player);
                    ridingObject = null;
                    ridingPieceIndex = -1;
                }
            }
        }

        // ROM parity: the riding section above is the ExitPlatform equivalent.
        // In the ROM, SolidObject runs ExitPlatform first and returns BEFORE
        // Solid_ChkEnter — the two paths are mutually exclusive. If we don't
        // skip the riding object here, the collision loop re-evaluates it with
        // different math (airHalfHeight vs groundHalfHeight, different vertical
        // offsets) causing the riding state to be spuriously removed and re-added
        // on alternating frames. This is most visible on monitors where
        // groundHalfHeight > airHalfHeight — the player jitters vertically.
        ObjectInstance ridingMaintained = ridingObject;
        ObjectInstance nextRidingObject = null;
        int nextRidingX = 0;
        int nextRidingY = 0;
        int nextRidingPieceIndex = -1;
        for (ObjectInstance instance : solidObjects) {
            // DropOnFloor detached the player from this object — don't re-land on it
            // this frame. Terrain collision will handle the player next frame.
            if (instance == dropOnFloorExclude) {
                continue;
            }
            // ROM: riding section already handled this via ExitPlatform.
            // Solid_ChkEnter is never reached for the standing object. Keep as standing
            // and fire callback, but skip resolveContact which would misclassify.
            if (instance == ridingMaintained) {
                if (instance instanceof MultiPieceSolidProvider) {
                    // Keep the ROM-style ExitPlatform carry step above, but still let
                    // sibling pieces of the same logical staircase resolve side/top/bottom
                    // contact in this pass. Skipping here prevents "run into the next block"
                    // wall collisions while already riding the current block.
                } else {
                    nextRidingObject = instance;
                    nextRidingX = instance.getX();
                    nextRidingY = instance.getY();
                    nextRidingPieceIndex = ridingPieceIndex;
                    if (instance instanceof SolidObjectListener listener) {
                        listener.onSolidContact(player, SolidContact.STANDING, frameCounter);
                    }
                    continue;
                }
            }
            SolidObjectProvider provider = (SolidObjectProvider) instance;
            if (!provider.isSolidFor(player)) {
                continue;
            }
            SolidRoutineProfile solidProfile = provider.getSolidRoutineProfile();

            // ROM: SolidObject_ChkBounds (s2.asm:35175-35176) — when obj_control bit 7
            // is set, SolidObject returns "no collision". This prevents captured/spring-locked
            // players from interacting with other solid objects (avoids crush death, position
            // shifts, and state corruption while the controlling object manages the player).
            if (blocksSolidContacts(player, instance)) {
                continue;
            }

            // ROM parity: Objects skip SolidObject on their first frame because
            // obRender bit 7 hasn't been set yet (DisplaySprite hasn't run).
            // Exception: the currently-ridden object must not be skipped, since
            // the ROM's ExitPlatform path (top of SolidObject) doesn't check
            // obRender — it always processes the standing player.
            if (instance.isSkipSolidContactThisFrame() && instance != ridingObject) {
                continue;
            }

            if (provider instanceof MultiPieceSolidProvider multiPiece) {
                MultiPieceContactResult result = processMultiPieceCollision(
                        player, multiPiece, instance, frameCounter, solidProfile.stickyContactBuffer());
                if (result.pushing()) {
                    player.setPushing(true);
                    // ROM: s2.asm:35220-35226 — also set pushing bit on the object
                    setObjectPushingBit(player, instance);
                    provider.setPlayerPushing(player, true);
                } else if (clearObjectPushingBit(player, instance)) {
                    player.setPushing(false);
                    provider.setPlayerPushing(player, false);
                }
                if (result.standing()) {
                    nextRidingObject = instance;
                    nextRidingX = result.ridingX();
                    nextRidingY = result.ridingY();
                    nextRidingPieceIndex = result.pieceIndex();
                }
                if (result.aggregateContact() != null && instance instanceof SolidObjectListener listener) {
                    listener.onSolidContact(player, result.aggregateContact(), frameCounter);
                }
                continue;
            }

            SolidObjectParams params = provider.getSolidParams();
            int anchorX = instance.getX() + params.offsetX();
            int anchorY = instance.getY() + params.offsetY();
            // ROM always uses airHalfHeight (d2) for the overlap test — d3 is
            // overwritten by playerYRadius before it is read.
            int halfHeight = params.airHalfHeight();
            boolean useStickyBuffer = solidProfile.stickyContactBuffer();
            boolean wasAirborne = player.getAir();
            boolean wasRidingObject = useStickyBuffer && isRidingCurrentPlayerObject(instance);

            SolidContact contact;
            SlopedSolidRoutineAdapter slopedAdapter = null;
            byte[] slopeData = null;
            if (instance instanceof SlopedSolidProvider sloped) {
                slopedAdapter = SlopedSolidRoutineProfile.adapt(sloped);
                slopeData = slopedAdapter.getSlopeData();
            }

            if (slopeData != null
                    && shouldUseSlopeForContact(instance, slopedAdapter)) {
                // ROM parity: when already riding a sloped object, the ROM does NOT
                // re-run SolidObject2F. It only runs ExitPlatform + SlopeObject2,
                // which is handled by the riding update above. Re-running the full
                // collision check can produce false SIDE contacts when Sonic is near
                // the platform edge (absDistX <= absDistY), causing premature
                // detachment. Skip the collision check and preserve the riding state.
                if (instance == ridingObject) {
                    nextRidingObject = instance;
                    nextRidingX = instance.getX();
                    nextRidingY = instance.getY();
                    nextRidingPieceIndex = -1;
                    if (instance instanceof SolidObjectListener listener) {
                        listener.onSolidContact(player, SolidContact.STANDING, frameCounter);
                    }
                    continue;
                }
                int slopeHalfHeight = params.groundHalfHeight();
                contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(), slopeHalfHeight,
                        solidProfile.topSolidOnly(), useStickyBuffer, instance, true, slopedAdapter);
            } else {
                contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        solidProfile, useStickyBuffer, instance, true);
            }

            if (contact == null) {
                if (instance instanceof SolidObjectListener listener) {
                    listener.onSolidContactCleared(player, frameCounter);
                }
                continue;
            }
            applyNonUnifiedTopSolidLandingHeightOverride(
                    player, contact, instance, solidProfile, wasAirborne, wasRidingObject, anchorY, params);
            if ((contact.standing() || contact.touchTop())
                    && instance.getSpawn() != null
                    && player instanceof AbstractPlayableSprite sprite) {
                sprite.setLatchedSolidObject(instance.getSpawn().objectId(), instance);
            }
            if (contact.pushing()) {
                player.setPushing(true);
                // ROM: s2.asm:35220-35226 — also set pushing bit on the object
                setObjectPushingBit(player, instance);
                provider.setPlayerPushing(player, true);
            }
            if (contact.standing()) {
                nextRidingObject = instance;
                nextRidingX = instance.getX();
                nextRidingY = instance.getY();
                nextRidingPieceIndex = -1;
            }
            if (instance instanceof SolidObjectListener listener) {
                listener.onSolidContact(player, contact, frameCounter);
            }
        }

        if (nextRidingObject != null) {
            ridingStates.put(player, new RidingState(nextRidingObject, nextRidingX, nextRidingY, nextRidingPieceIndex));
            setObjectStandingBit(player, nextRidingObject);
            clearGroundWallSuppressionForNormalSolidSupport(player, nextRidingObject);
        } else {
            ridingStates.remove(player);
        }

        // ROM: bclr #status.player.on_object when not standing on any object
        // Also clear when player becomes airborne (jumping/falling off) - s2.asm has many instances
        // of this paired with bset #status.player.in_air
        if (nextRidingObject == null) {
            player.setOnObject(false);
        }

        boolean standingSnapshot = nextRidingObject != null;
        cacheStandingSnapshot(player, new PlayerStandingState(
                standingSnapshot ? ContactKind.TOP : ContactKind.NONE,
                standingSnapshot,
                currentPushingState(player)));
        cacheHeadroomSnapshot(player, player.getAngle(), getHeadroomDistance(player, player.getAngle()));

        currentPlayer = null;
    }

    private record MultiPieceContactResult(
            boolean standing,
            boolean pushing,
            int ridingX,
            int ridingY,
            int pieceIndex,
            SolidContact aggregateContact) {}

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
    private void resolveEarlierMultiPieceSiblings(PlayableEntity player,
            MultiPieceSolidProvider multiPiece, ObjectInstance instance, int ridingPieceIndex,
            int frameCounter, boolean useStickyBuffer) {
        SolidRoutineProfile solidProfile = multiPiece.getSolidRoutineProfile();
        for (int i = 0; i < ridingPieceIndex && i < multiPiece.getPieceCount(); i++) {
            SolidObjectParams params = multiPiece.getPieceParams(i);
            int anchorX = multiPiece.getPieceX(i) + params.offsetX();
            int anchorY = multiPiece.getPieceY(i) + params.offsetY();
            int halfHeight = player.getAir() ? params.airHalfHeight() : params.groundHalfHeight();
            SlopedSolidRoutineAdapter slopedAdapter = null;
            byte[] slopeData = null;
            if (instance instanceof SlopedSolidProvider sloped) {
                slopedAdapter = SlopedSolidRoutineProfile.adapt(sloped);
                slopeData = slopedAdapter.getSlopeData();
            }
            SolidContact contact;
            if (slopeData != null && shouldUseSlopeForContact(instance, slopedAdapter)) {
                contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(),
                        params.groundHalfHeight(), solidProfile.topSolidOnly(), useStickyBuffer,
                        instance, true, slopedAdapter);
            } else {
                contact = resolveContact(player, anchorX, anchorY, params.halfWidth(), halfHeight,
                        solidProfile, useStickyBuffer, instance, i, true);
            }
            if (contact != null) {
                multiPiece.onPieceContact(i, player, contact, frameCounter);
            }
        }
    }

    private MultiPieceContactResult processMultiPieceCollision(PlayableEntity player,
            MultiPieceSolidProvider multiPiece, ObjectInstance instance, int frameCounter,
            boolean useStickyBuffer) {
        int pieceCount = multiPiece.getPieceCount();

        boolean anyStanding = false;
        boolean anyPushing = false;
        boolean anyTouchTop = false;
        boolean anyTouchBottom = false;
        boolean anyTouchSide = false;

        int standingPieceIndex = -1;
        int standingPieceX = 0;
        int standingPieceY = 0;
        SolidRoutineProfile solidProfile = multiPiece.getSolidRoutineProfile();
        boolean ridingCurrentObject = isRidingCurrentPlayerObject(instance);
        int currentRidingPieceIndex = getCurrentPlayerRidingPieceIndex();

        for (int i = 0; i < pieceCount; i++) {
            // ROM slot-order parity: when the earlier slots of this ridden object
            // were already side-pushed ahead of the ride-bounds re-check
            // (resolveEarlierMultiPieceSiblings set multiPieceEarlierPiecesResolvedUpTo
            // for this instance+player), skip them here so a single ROM SolidObject
            // side-push per slot is not applied twice in one frame.
            if (i < multiPieceEarlierPiecesResolvedUpTo) {
                continue;
            }
            SolidObjectParams params = multiPiece.getPieceParams(i);
            int pieceX = multiPiece.getPieceX(i);
            int pieceY = multiPiece.getPieceY(i);
            if (ridingCurrentObject && i == currentRidingPieceIndex) {
                anyStanding = true;
                if (standingPieceIndex < 0) {
                    standingPieceIndex = i;
                    standingPieceX = pieceX;
                    standingPieceY = pieceY;
                }
                multiPiece.onPieceContact(i, player, SolidContact.STANDING, frameCounter);
                continue;
            }
            int anchorX = pieceX + params.offsetX();
            int anchorY = pieceY + params.offsetY();
            int halfHeight = player.getAir() ? params.airHalfHeight() : params.groundHalfHeight();
            SlopedSolidRoutineAdapter slopedAdapter = null;
            byte[] slopeData = null;
            if (instance instanceof SlopedSolidProvider sloped) {
                slopedAdapter = SlopedSolidRoutineProfile.adapt(sloped);
                slopeData = slopedAdapter.getSlopeData();
            }

            SolidContact contact;
            if (slopeData != null && shouldUseSlopeForContact(instance, slopedAdapter)) {
                contact = resolveSlopedContact(player, anchorX, anchorY, params.halfWidth(),
                        params.groundHalfHeight(), solidProfile.topSolidOnly(), useStickyBuffer,
                        instance, true, slopedAdapter);
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
                    && instance.getSpawn() != null
                    && player instanceof AbstractPlayableSprite sprite) {
                sprite.setLatchedSolidObject(instance.getSpawn().objectId(), instance);
            }

            if (contact.standing()) {
                anyStanding = true;
                if (standingPieceIndex < 0) {
                    standingPieceIndex = i;
                    standingPieceX = pieceX;
                    standingPieceY = pieceY;
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

            multiPiece.onPieceContact(i, player, contact, frameCounter);
        }

        SolidContact aggregateContact = (anyStanding || anyTouchTop || anyTouchBottom
                || anyTouchSide || anyPushing)
                ? new SolidContact(
                        anyStanding, anyTouchSide, anyTouchBottom, anyTouchTop, anyPushing)
                : null;

        return new MultiPieceContactResult(
                anyStanding, anyPushing, standingPieceX, standingPieceY,
                standingPieceIndex, aggregateContact);
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
        boolean topSolidOnly = profile.topSolidOnly();
        boolean monitorSolidity = profile.monitorSolidity();
        int monitorVerticalOffset = profile.monitorVerticalOffset();
        boolean inclusiveRightEdge = profile.inclusiveRightEdge();
        int playerCenterX = player.getCentreX();
        int playerCenterY = player.getCentreY();
        if (topSolidOnly && instance instanceof SolidObjectProvider provider) {
            // Provider-specific ROM ports can request a different sampled
            // player-position phase for top-solid helper geometry. S3K
            // SolidObjectTop's new-landing check reads x_pos/y_pos/y_radius
            // before RideObject_SetRide (sonic3k.asm:41982-42015).
            int historyFrames = Math.max(0,
                    provider.getTopSolidPlayerPositionHistoryFrames(player));
            if (historyFrames > 0) {
                playerCenterX = player.getCentreX(historyFrames);
                playerCenterY = player.getCentreY(historyFrames);
            }
        }

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
                                && provider.fullSolidBottomOverlapUsesCurrentYRadiusOnly(player));
        int totalHeight = useCurrentBottomRadius
                ? maxTop * 2
                : maxTop + (monitorSolidity ? maxTop : halfHeight + getSolidTopYRadius(player));
        // SPG-style monitor callers keep zero here. S3K monitors branch into
        // SolidObject_cont, which adds +4 before the d2/y_radius overlap check
        // (docs/skdisasm/sonic3k.asm:40575-40576, 41429-41432).
        int verticalOffset = monitorSolidity ? monitorVerticalOffset : 4;
        // ROM: s2.asm:35147 uses andi.w #$7FF,d0 to handle VDP Y-coordinate
        // wrapping near y_pos=0 (16-bit hardware arithmetic). The engine uses
        // 32-bit absolute coordinates with no wrapping, so the mask must NOT be
        // applied — it causes phantom collisions between objects separated by
        // ~2048px vertically in tall levels (e.g. HCZ2 FanPlatformChild at Y=608
        // falsely colliding with player at Y=2654).
        int relY = playerCenterY - anchorY + verticalOffset + maxTop;

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

        if (monitorSolidity) {
            return resolveMonitorContact(player, relX, relY, halfWidth, maxTop, playerCenterX, playerCenterY,
                    anchorX, riding, apply);
        }
        return resolveContactInternal(player, relX, relY, halfWidth, maxTop, totalHeight,
                playerCenterX, playerCenterY,
                topSolidOnly, riding, apply, instance, true);
    }

    private int getSolidTopYRadius(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            return sprite.getStandYRadius();
        }
        return player.getYRadius();
    }

    private int getTopLandingSnapAdjustment(ObjectInstance instance,
            PlayableEntity player) {
        if (!(instance instanceof SolidObjectProvider provider)) {
            return 0;
        }
        return provider.getTopLandingSnapAdjustment(player, getSolidTopYRadius(player));
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
        // ROM: SolidObject_Landed (s2.asm:35368-35387) uses playerY - relY + 3,
        // which resolveContactInternal already computes correctly. The absolute
        // anchorY-groundHalfHeight formula below only matches PlatformObject_ChkYRange
        // (s2.asm:35696-35712). Skip the snap for SolidObject-based objects so
        // resolveContactInternal's result is preserved.
        if (!solidProfile.usesPlatformLandingSnap()) {
            return;
        }
        int targetCentreY = anchorY - params.groundHalfHeight() - player.getYRadius() - 1;
        if (player instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.writeYPosPreserveSubpixel(sprite, targetCentreY);
            return;
        }
        int newY = targetCentreY - (player.getHeight() / 2);
        player.setY((short) newY);
    }

    private boolean isSignedObjectControlSideContactRejected(PlayableEntity player,
            ObjectInstance instance) {
        if (!(player instanceof AbstractPlayableSprite sprite)
                || !sprite.isObjectControlled()
                || sprite.isObjectControlAllowsCpu()
                || !(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        return provider.rejectsBit7ObjectControlSideContact(player);
    }

    private boolean isSignedObjectControlNewSolidContactRejected(PlayableEntity player,
            ObjectInstance instance) {
        if (!(player instanceof AbstractPlayableSprite sprite)
                || !sprite.isObjectControlled()
                || sprite.isObjectControlAllowsCpu()
                || !(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        return provider.rejectsBit7ObjectControlNewSolidContact(player);
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
        // ROM: Mon_Solid / SolidObject_Monitor_Sonic — rolling check runs AFTER
        // Mon_SolidSides geometry detection, not as an external gate.
        // When the player is rolling AND velY >= 0 (not moving upward), skip
        // the solid response entirely so the touch system can break the monitor.
        // When velY < 0 (moving upward), always handle as solid (side push / landing)
        // regardless of rolling state — this is the S1 "always solid when moving up" rule.
        // When standing on the monitor (sticky), always handle as solid (ride mode,
        // ROM: ob2ndRout=2 bypasses Mon_SolidSides entirely).
        if (!sticky && player.getRolling() && player.getYSpeed() >= 0) {
            return null;
        }

        // Calculate distances from center
        int distX;
        int absDistX;
        if (relX >= halfWidth) {
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
            // Landing on top
            if (player.getYSpeed() < 0) {
                return null;
            }

            if (apply) {
                int newCenterY = playerCenterY - distY + 3;
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

        // ROM SolidObject_AtEdge (s2.asm:35241-35248): for any side contact
        // when the player is not in air, set the OBJECT's push bit AND the
        // player's pushing bit, regardless of moving direction.  Position
        // correction and speed zeroing remain gated on movingInto.  This is
        // required for MCZ f862 trace parity: Sonic stands on a monitor's left
        // edge with xSpeed=0 on f860, ROM still sets the monitor's p1_pushing
        // bit so Obj26_Break can use it to airborne Sonic when the monitor
        // is broken two frames later.
        boolean leftSide = playerCenterX <= anchorX;
        boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
        boolean exactEdgeOverlap = absDistX == 0;
        boolean pushing = !player.getAir();
        boolean skipMonitorSide = deferSideToPostMovement && player.getAir();
        if (apply && movingInto && !exactEdgeOverlap && !skipMonitorSide) {
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

    private SolidContact resolveSlopedContact(PlayableEntity player, int anchorX, int anchorY, int halfWidth,
            int halfHeight, boolean topSolidOnly, boolean useStickyBuffer,
            ObjectInstance instance, boolean apply, SlopedSolidRoutineAdapter slopedAdapter) {
        byte[] slopeData = slopedAdapter.getSlopeData();
        if (slopeData == null || slopeData.length == 0) {
            return null;
        }
        SlopedSolidRoutineProfile slopedProfile = slopedAdapter.profile();
        int playerCenterX = player.getCentreX();
        int playerCenterY = player.getCentreY();

        int relX = playerCenterX - anchorX + halfWidth;
        int width2 = halfWidth * 2;
        if (relX < 0 || relX >= width2) {
            return null;
        }

        int sampleX = relX;
        if (slopedAdapter.isSlopeFlipped()) {
            // ROM: move.w d0,d5 / not.w d5 / add.w d3,d5 / lsr.w #1,d5
            // where d0=relX and d3=halfWidth*2. For in-range relX this is
            // equivalent to (width2 - relX - 1) >> 1.
            sampleX = width2 - sampleX - 1;
        }
        sampleX = sampleX >> 1;
        if (sampleX < 0 || sampleX >= slopeData.length) {
            return null;
        }

        int slopeSample = (byte) slopeData[sampleX];
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

        if (relY < minRelY || relY >= maxTop * 2) {
            return null;
        }

        // ROM parity: SlopedSolid_SingleCharacter (s2.asm:34902) when the standing
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
                && isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
            if (apply) {
                // ROM parity: S2 SlopedSolid_cont (s2.asm:34927-35099) still
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
                playerCenterX, playerCenterY, topSolidOnly, riding, apply, instance, true);

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
            boolean sticky, boolean apply, ObjectInstance instance, boolean useTopLandingWidth) {
        int distX;
        int absDistX;
        if (relX >= halfWidth) {
            distX = relX - (halfWidth * 2);
            absDistX = -distX;
        } else {
            distX = relX;
            absDistX = distX;
        }

        int distY;
        int absDistY;
        if (relY <= maxTop) {
            distY = relY;
            absDistY = distY;
        } else {
            distY = relY - 4 - totalHeight;
            absDistY = Math.abs(distY);
        }

        if (!sticky && isSignedObjectControlNewSolidContactRejected(player, instance)) {
            // S3K SolidObject_cont rejects signed object_control before
            // side/top new-contact classification
            // (sonic3k.asm:41394-41440). Continued standing-bit riding is
            // handled before this path by SolidObjectFull_1P.
            return null;
        }

        // Sonic 1 top-solid objects use PlatformObject/SlopeObject semantics:
        // top-landing is resolved purely by X-range + top Y-window (no side-priority compare).
        if (topSolidOnly && usesUnifiedCollisionModel(player)) {
            if (player.getYSpeed() < 0) {
                return null;
            }
            if (!sticky
                    && instance instanceof SolidObjectProvider provider
                    && provider.gatesNewTopSolidLandingWithPreviousPosition()) {
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
            boolean rejectsZeroDistanceTopLanding = distY == 0
                    && rejectsZeroDistanceTopSolidLanding(instance);
            if (distY < 0 || distY >= 0x10 || rejectsZeroDistanceTopLanding) {
                if (rejectsZeroDistanceTopLanding) {
                    notifyZeroDistanceTopSolidLandingRejected(instance, player);
                }
                return null;
            }
            // ROM: Solid_Landed uses narrow obActWid for NEW landings only.
            // When sticky (already riding), ROM uses ExitPlatform's full collision width.
            if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                return null;
            }
            if (apply) {
                int newCenterY = playerCenterY - distY + 3;
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
                    applyObjectLandingState(player);
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
        // gated by PhysicsFeatureSet#solidObjectBarelyPokingResolvesAsSide:
        //
        //   S3K (false): cmp d1,d5 / bhi.w loc_1E0D4 ; cmpi.w #4,d1 /
        //     bls.w loc_1E0D4 — when d5<=d1 AND d1<=4, ROM goes to the
        //     TOP/BOTTOM path (loc_1E0D4, sonic3k.asm:41463-41466,
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
            boolean leftSide = relX < halfWidth;
            boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
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
                    && instance.getSpawn().objectId() == OBJ85_ID
                    && instance.getSpawn().subtype() == 0
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
                    if (!sticky && !isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
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
                            applyObjectLandingState(player);
                        }
                        player.setOnObject(true);
                    }
                    return SolidContact.STANDING;
                }
            }

            // Determine which side player is on based on relX, not distX.
            // When distX=0 (at exact edge), distX>0 would be false for both sides,
            // causing incorrect movingInto detection for left side pushes.
            boolean leftSide = relX < halfWidth;
            // ROM loc_1E06E sets Status_Push for any grounded side contact
            // after applying the side separation. Only speed zeroing is
            // gated by moving into the object (sonic3k.asm:41473-41495).
            boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
            boolean pushing = !player.getAir();
            // ROM: sub SolidObject.asm lines 173-196
            // When d0==0 (distX==0), ROM branches to Solid_Centre which does
            // "sub.w d0,obX(a1)" (no-op) — NO speed zeroing, NO position change.
            // When d0!=0 AND movingInto, ROM hits Solid_Left which zeros obInertia
            // and obVelX, then falls through to Solid_Centre (position correction).
            // When d0!=0 AND NOT movingInto, ROM goes to Solid_Centre directly
            // (position correction only, no speed zeroing).
            // deferSideToPostMovement: only defer when player is airborne.
            // Airborne side collision is handled more accurately by post-movement pass
            // (ROM processes objects after Sonic moves). Ground side collision keeps
            // pre-movement behavior for wall alignment consistency.
            boolean skipSideEffects = deferSideToPostMovement && player.getAir();
            if (apply
                    && isSignedObjectControlSideContactRejected(player, instance)) {
                return null;
            }
            if (apply && !skipSideEffects) {
                if (postMovement) {
                    // Post-movement pass (S1 UNIFIED): ROM-accurate behavior.
                    // ROM: Solid_Left zeros speed BEFORE the airborne check.
                    // Speed is zeroed when movingInto regardless of air state.
                    // The airborne check only affects the PUSH flag, not speed.
                    // The shared helper zeroes x_vel for moving side contact
                    // across games; object-local launch routines must finish
                    // before this post-movement stop can apply.
                    boolean airborneSpecialTouchEdge = instance instanceof SolidObjectProvider solidProvider
                            && solidProvider.preservesPostSpecialTouchAirborneSideVelocity()
                            && objectManager.hadSpecialTouchThisFrame(player)
                            && player.getAir()
                            && distX == (player.getXSpeed() >> 8);
                    if (distX != 0 && movingInto) {
                        if (!airborneSpecialTouchEdge) {
                            player.setXSpeed((short) 0);
                            player.setGSpeed((short) 0);
                        } else if (airborneSpecialTouchEdge) {
                            player.setGSpeed((short) 0);
                        }
                    }
                    if (distX != 0) {
                        player.move((short) (-distX * 256), (short) 0);
                    }
                } else {
                    // Pre-movement pass (S2/S3K): compensate for batched processing architecture.
                    // Push-driven objects (preserveSubpixels=true) skip the distX==0 block
                    // to preserve ROM push cadence.
                    boolean preserveSubpixels = preservesEdgeSubpixelMotion(instance);
                    if (distX == 0 && !preserveSubpixels) {
                        player.setCentreX((short) playerCenterX);
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
                            player.setCentreX((short) (playerCenterX - distX));
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
            // PhysicsFeatureSet#solidObjectTopBranchAlwaysLiftsOnUpwardVelocity:
            //   S3K loc_1E154 (sonic3k.asm:41606-41632) writes the position
            //   lift (subq.w #1, y_pos(a1) at 41617; sub.w d3, y_pos(a1) at
            //   41624) BEFORE testing tst.w y_vel(a1) / bmi.s loc_1E198 at
            //   41625-41626.  When y_vel < 0 it skips RideObject_SetRide
            //   and returns d4=0 (no contact), but the lift has already
            //   been applied.  CNZ trace F7614 exercises this when
            //   Tails_Jump (sonic3k.asm:28519+) sets y_vel=-$680 on the
            //   same frame Obj_Spring_Horizontal (sonic3k.asm:47771+)
            //   reaches loc_1E154 with d3=1 against Tails.
            //   S1 Solid_Landed (s1disasm/_incObj/sub SolidObject.asm:278)
            //   and S2 SolidObject_Landed (s2.asm:35379-35380) test y_vel
            //   FIRST and branch to SolidObject_Miss with no lift.
            //
            // ROM only reaches loc_1E154 via SolidObjectFull2_1P's
            // SolidObject_cont branch when the object's a0.d6 standing-bit
            // is CLEAR (sonic3k.asm:41066-41067 btst d6 / beq cont).  When
            // the bit is still set from a previous landing and the player
            // is now airborne (e.g. AIZ F2090 yellow up-spring kick:
            // sub_22F98 sets Status_InAir at sonic3k.asm:47723 but does
            // not touch a0.d6), the next frame's SolidObjectFull2_1P
            // routes through loc_1DCF0 (sonic3k.asm:41079-41084) which
            // clears d6 and returns d4=0 without ever reaching
            // loc_1E154.  The engine mirrors that ROM bit via
            // objectStandingBitSet (set by RideObject_SetRide-equivalent
            // STANDING contacts, cleared at the top of
            // processInlineObjectForPlayer when the object's own pass
            // sees the bit set + air), and snapshots the pre-clear value
            // into objectStandingBitSnapshot so the gate below observes
            // ROM's "bit was set at routine entry" semantics.
            boolean objectStandingBitWasSet =
                    wasObjectStandingBitSetThisFrame(player, instance)
                            || hasObjectStandingBit(player, instance);
            boolean s3kAlwaysLifts =
                    !topSolidOnly
                            && topBranchAlwaysLiftsOnUpwardVelocity(player)
                            && !objectStandingBitWasSet;
            if (upwardVelocity && !s3kAlwaysLifts) {
                return null;
            }


            if (topSolidOnly && player.getYSpeed() < 0) {
                return null;
            }
            if (topSolidOnly && !sticky
                    && instance instanceof SolidObjectProvider provider
                    && provider.gatesNewTopSolidLandingWithPreviousPosition()) {
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
            // ROM: loc_1E154 (sonic3k.asm:41608-41616) re-checks the X
            // overlap against width_pixels(a0) before applying the lift,
            // so the upward-velocity-and-lift S3K path also needs this
            // narrow gate.  Sticky riders skip this on the standing branch
            // because ExitPlatform's full collision width is already in
            // effect; for the upward-velocity branch the player is by
            // definition not standing on the object so sticky doesn't apply.
            if (useTopLandingWidth && !sticky && !topSolidOnly
                    && !isWithinTopLandingWidth(instance, player, relX, halfWidth)) {
                return null;
            }

            if (apply) {
                int newCenterY = playerCenterY - distY + 3
                        - getTopLandingSnapAdjustment(instance, player);
                int newY = newCenterY - (player.getHeight() / 2);
                player.setY((short) newY);
                if (upwardVelocity) {
                    // ROM: loc_1E154 path with tst.w y_vel(a1) / bmi.s
                    // loc_1E198 (sonic3k.asm:41625-41626) -> moveq #0,d4 /
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
                    applyObjectLandingState(player);
                }
                // ROM: bset #status.player.on_object (s2.asm:35739)
                player.setOnObject(true);
                // ROM: RideObject_SetRide also sets the object's
                // a0.d6 standing-bit (sonic3k.asm:42034 bset d6,
                // status(a0)). Mirror that here so a subsequent
                // air-unseat correctly routes through the no-lift
                // path on the next frame.
                if (instance != null) {
                    setObjectStandingBit(player, instance);
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

        // ROM: SolidObject_InsideBottom (s2.asm:35307-35333)
        // When y_vel == 0 and player is on ground, the ROM branches to SolidObject_Squash
        // which checks horizontal overlap and kills the player if sandwiched.
        if (player.getYSpeed() == 0 && !player.getAir()) {
            // ROM: SolidObject_Squash (s2.asm:35336-35361)
            // mvabs.w d0,d4; cmpi.w #$10,d4; blo.w SolidObject_LeftRight
            // If player is near the horizontal edge (absDistX < 16), push sideways instead.
            if (absDistX < 0x10) {
                boolean leftSide = relX < halfWidth;
                boolean movingInto = leftSide ? player.getXSpeed() > 0 : player.getXSpeed() < 0;
                boolean groundedSquashEdgePush = instance instanceof SolidObjectProvider provider
                        && provider.groundedSquashEdgeSideContactSetsPush();
                boolean pushing = !player.getAir() && (movingInto || groundedSquashEdgePush);
                boolean skipSquashSide = deferSideToPostMovement && player.getAir();
                if (apply && !skipSquashSide) {
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
            // ROM: KillCharacter (s2.asm:84995) - unconditional death.
            if (apply) {
                LOGGER.fine(() -> "SolidObject crush: player sandwiched (absDistX=" + absDistX + ")");
                player.applyCrushDeath();
            }
            return SolidContact.CEILING;
        }

        // ROM: Solid_Below — only correct position when ySpeed < 0 (moving upward into object).
        // When ySpeed > 0 (falling) or ySpeed == 0 with air, ROM's Solid_TopBtmAir returns
        // d4=-1 without correcting position or zeroing speed. This lets the player fall
        // through the bottom of the object naturally, preventing spikes from trapping Sonic
        // by continuously pushing him back into the collision box.
        if (apply && player.getYSpeed() < 0) {
            int newCenterY = playerCenterY - distY;
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

    private boolean isWithinTopLandingWidth(ObjectInstance instance, PlayableEntity player, int relX,
            int collisionHalfWidth) {
        if (!(instance instanceof SolidObjectProvider provider)) {
            return true;
        }

        int configuredHalfWidth = provider.getTopLandingHalfWidth(player, collisionHalfWidth);
        int allowedHalfWidth;
        if (provider.getSolidRoutineProfile().usesCollisionHalfWidthForTopLanding()) {
            allowedHalfWidth = collisionHalfWidth;
        } else if (configuredHalfWidth < collisionHalfWidth) {
            // Provider explicitly set a narrower landing width
            allowedHalfWidth = configuredHalfWidth;
        } else {
            // ROM: SolidObjectFull's Solid_Landed re-reads obActWid (= width_pixels),
            // which is narrower than collision halfWidth (= width_pixels + $B).
            allowedHalfWidth = Math.max(0, collisionHalfWidth - 0x0B);
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
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return featureSet != null && featureSet.collisionModel() == CollisionModel.UNIFIED;
    }

    private boolean allowsZeroDistTopSolidLanding(PlayableEntity player) {
        if (player == null) {
            return true;
        }
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return featureSet == null || featureSet.topSolidLandingAllowsZeroDist();
    }

    private boolean rejectsZeroDistanceTopSolidLanding(ObjectInstance instance) {
        return rejectsZeroDistanceTopSolidLanding(instance, currentPlayer);
    }

    private boolean rejectsZeroDistanceTopSolidLanding(ObjectInstance instance, PlayableEntity player) {
        return instance instanceof SolidObjectProvider provider
                && provider.rejectsZeroDistanceTopSolidLanding(player);
    }

    private boolean providerAllowsZeroDistanceTopSolidLanding(ObjectInstance instance, PlayableEntity player) {
        return instance instanceof SolidObjectProvider provider
                && provider.allowsZeroDistanceTopSolidLanding(player);
    }

    private void notifyZeroDistanceTopSolidLandingRejected(ObjectInstance instance, PlayableEntity player) {
        if (instance instanceof SolidObjectProvider provider) {
            provider.onRejectedZeroDistanceTopSolidLanding(player);
        }
    }

    private boolean clearsGroundSpeedOnAirBottomSolidHit(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return player.getAir()
                && featureSet != null
                && featureSet.airBottomSolidHitClearsGroundSpeed();
    }

    private boolean usesCurrentYRadiusOnlyForFullSolidBottomOverlap(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return featureSet != null && featureSet.fullSolidBottomOverlapUsesCurrentYRadiusOnly();
    }

    private boolean isSolidObjectOffscreenGateEnabled(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return featureSet != null && featureSet.solidObjectOffscreenGate();
    }

    private boolean shouldSkipOffscreenSidekickFullSolid(PlayableEntity player,
                                                         ObjectInstance instance,
                                                         SolidRoutineProfile solidProfile) {
        if (!(player instanceof AbstractPlayableSprite sidekick) || !sidekick.isCpuControlled()) {
            return false;
        }
        PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
        if (featureSet == null || !featureSet.solidObjectRequiresSidekickOnScreen()) {
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
        // (docs/skdisasm/sonic3k.asm:41006-41010). This gate belongs only
        // to the regular full-solid helper; SolidObjectFull2/SolidObject_Always,
        // top-only, and sloped helpers enter their P2 routines directly.
        return true;
    }

    private boolean shouldSkipRidingAirUnseatForOffscreenSidekick(PlayableEntity player,
                                                                   ObjectInstance ridingObject) {
        if (!(ridingObject instanceof SolidObjectProvider provider)) {
            return false;
        }
        return shouldSkipOffscreenSidekickFullSolid(player, ridingObject, provider.getSolidRoutineProfile());
    }

    private boolean isVisibleForRenderFlag(AbstractPlayableSprite sprite) {
        Camera currentCamera = sprite.currentCamera();
        return currentCamera != null && currentCamera.isVisibleForRenderFlag(sprite);
    }

    /**
     * ROM: {@code loc_1E154} (sonic3k.asm:41606-41632) writes the position
     * lift before testing {@code y_vel}; {@code Solid_Landed} /
     * {@code SolidObject_Landed} (S1/S2) test {@code y_vel} first and bail
     * without lifting. Gated via
     * {@link PhysicsFeatureSet#solidObjectTopBranchAlwaysLiftsOnUpwardVelocity()}.
     */
    private boolean topBranchAlwaysLiftsOnUpwardVelocity(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return featureSet != null && featureSet.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity();
    }

    /**
     * ROM: {@code SolidObject_cont} routes a barely-poking overlap
     * ({@code d5 <= d1} with {@code d1 <= 4}) differently per game. S1/S2
     * send it to {@code SolidObject_SideAir} / {@code Solid_SideAir}, which
     * returns {@code moveq #1,d4} — a SIDE contact
     * (s2.asm:35404-35412,35447-35453;
     * s1disasm/_incObj/sub SolidObject.asm:181-184,211-214). S3K sends it to
     * {@code loc_1E0D4}, the TOP/BOTTOM path
     * (sonic3k.asm:41463-41466,41541-41546). Gated via
     * {@link PhysicsFeatureSet#solidObjectBarelyPokingResolvesAsSide()}.
     */
    private boolean solidObjectBarelyPokingResolvesAsSide(PlayableEntity player) {
        if (player == null) {
            return false;
        }
        PhysicsFeatureSet featureSet = player.getPhysicsFeatureSet();
        return featureSet != null && featureSet.solidObjectBarelyPokingResolvesAsSide();
    }

    private boolean preservesEdgeSubpixelMotion(ObjectInstance instance) {
        if (!(instance instanceof SolidObjectProvider provider)) {
            return false;
        }
        return provider.preservesEdgeSubpixelMotion();
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
        byte[] slopeData = sloped.getSlopeData();
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
        // ROM: lsr.w #1,d0 — shift BEFORE flip (matches SlopeObject2/MvSonicOnSlope)
        int sampleX = relX >> 1;
        if (sloped.isSlopeFlipped()) {
            // ROM: not.w d0 / add.w d1,d0 — where d1 = halfWidth
            // not.w gives ~sampleX = -sampleX - 1, then + halfWidth
            sampleX = halfWidth - sampleX - 1;
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
        if (player == null || player.getPinballMode()) {
            return;
        }
        if (player.getRolling()) {
            player.setRolling(false);
            player.setY((short) (player.getY() - player.getRollHeightAdjustment()));
        } else if (player instanceof AbstractPlayableSprite sprite
                && (sprite.getYRadius() != sprite.getStandYRadius()
                || sprite.getXRadius() != sprite.getStandXRadius())) {
            // ROM Player_TouchFloor (sonic3k.asm:24341-24343 / 29134-29136) unconditionally
            // resets y_radius/x_radius before testing Status_Roll. S3K despawn marker
            // writes Status_InAir directly and can leave Tails with rolling radii but no
            // roll bit — so S3K needs this reset even when not rolling.
            // S2 Tails_ResetOnFloor (s2.asm:40624-40641) uses btst/bne to gate the
            // y_radius reset on Status_Roll being set; when not rolling it skips the
            // reset, preserving any stale y_radius value from the despawn path.
            // Gate this non-rolling radius reset to S3K (landingRollClearUsesCurrentYRadiusDelta).
            PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
            if (featureSet != null && featureSet.landingRollClearUsesCurrentYRadiusDelta()) {
                sprite.applyStandingRadii(false);
            }
        }
    }

    private void applyObjectLandingState(PlayableEntity player) {
        AbstractPlayableSprite playableSprite = player instanceof AbstractPlayableSprite sprite ? sprite : null;
        boolean wasHurt = playableSprite != null && playableSprite.isHurt();
        int savedDoubleJumpFlag = player.getDoubleJumpFlag();

        if (wasHurt) {
            playableSprite.setAirAfterObjectHurtLanding();
        } else {
            player.setAir(false);
        }
        clearRollingOnLanding(player);
        player.setGroundMode(GroundMode.GROUND);

        if (wasHurt) {
            // ROM object/platform landing clears Status_InAir here, but
            // routine 4 and its velocities survive until Sonic_HurtStop runs
            // on the next player update (S2: s2.asm:37848-37861).
            return;
        }

        player.applyPostObjectLandingAbilities(savedDoubleJumpFlag);
    }
}




