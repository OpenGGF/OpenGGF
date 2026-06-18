package com.openggf.level.objects;

import com.openggf.game.session.EngineServices;
import static org.lwjgl.opengl.GL11.GL_LINES;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.CollisionModel;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.SpawnRefId;
import com.openggf.game.solid.ContactKind;
import com.openggf.level.objects.boss.BossChildComponent;
import com.openggf.game.solid.ObjectSolidExecutionContext;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.PlayerStandingState;
import com.openggf.game.solid.PostContactState;
import com.openggf.game.solid.PreContactState;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GLCommandGroup;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.spawn.AbstractPlacementManager;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.game.PlayableEntity;
import com.openggf.game.DamageCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.game.GroundMode;

import java.lang.reflect.Modifier;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ObjectManager {
    private static final int BUCKET_COUNT = RenderPriority.MAX - RenderPriority.MIN + 1;
    static final int ANIM_ROLL = 0x02;
    static final int ANIM_SPINDASH = 0x09;
    private static final List<DynamicObjectRewindCodec> TEST_OR_MIGRATION_REWIND_DYNAMIC_OBJECT_CODECS =
            new CopyOnWriteArrayList<>();
    private static final List<DynamicObjectRewindCodec> SHARED_REWIND_DYNAMIC_OBJECT_CODECS =
            ObjectRewindDynamicCodecs.sharedCodecs();

    private final ObjectPlacementController placement;
    private final ObjectRegistry registry;
    private final GraphicsManager graphicsManager;
    private final Camera camera;
    private final Map<ObjectSpawn, ObjectInstance> activeObjects = new IdentityHashMap<>();
    private final Map<ObjectInstance, ObjectSpawn> instanceToSpawn = new IdentityHashMap<>();
    private final List<ObjectInstance> dynamicObjects = new ArrayList<>();
    private final Set<ObjectInstance> auxiliaryDynamicObjects =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<ObjectInstance> dynamicFallbackScratch = new ArrayList<>();
    private final List<ObjectInstance> activeFallbackScratch = new ArrayList<>();
    // Per-frame scratch collections reused to avoid steady-state allocation.
    // Ownership stays inside the populating method (cleared in finally / at the
    // single reset point); never returned to callers that retain references.
    private final Set<ObjectInstance> processedInExecLoopScratch =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PlayableEntity> activePlayersScratch = new ArrayList<>(4);
    private final List<ObjectSpawn> newSpawnsScratch = new ArrayList<>();
    private final List<ObjectInstance> postPlayerHooksScratch = new ArrayList<>();
    // Cached spawn-order comparators (built lazily; placement is constructor-set).
    private Comparator<ObjectSpawn> forwardSpawnOrder;
    private Comparator<ObjectSpawn> backwardSpawnOrder;
    private final List<GLCommand> renderCommands = new ArrayList<>();
    private int frameCounter;
    private int vblaCounter;
    private boolean updating;

    // ROM parity: slot-ordered execution array for ExecuteObjects emulation.
    // The dynamic slot window is game-specific and comes from ObjectRegistry.
    // When a child is spawned at a higher slot, it's placed here directly
    // so the ongoing loop reaches it naturally (same-frame execution).
    private final ObjectSlotLayout slotLayout;
    /**
     * Per-game object load/unload windowing boundary (shared abstraction;
     * {@link ObjectWindowingStrategy#LEGACY} for S1/S3K, the ROM-exact S2
     * strategy for S2). Injected from the {@link ObjectRegistry} so this shared
     * manager never depends on a game-specific package.
     */
    private final ObjectWindowingStrategy windowingStrategy;
    private final ObjectInstance[] execOrder;
    private int currentExecSlot = -1; // -1 when not in update loop
    private final boolean skipVerticalSpawnLoadFilterForGame;

    private final ObjectServices objectServices;

    // Pre-bucketed lists for O(n) rendering instead of O(n*buckets)
    @SuppressWarnings("unchecked")
    private final List<ObjectInstance>[] lowPriorityBuckets = new ArrayList[BUCKET_COUNT];
    @SuppressWarnings("unchecked")
    private final List<ObjectInstance>[] highPriorityBuckets = new ArrayList[BUCKET_COUNT];
    private boolean bucketsDirty = true;
    // Render-input snapshot from the last bucket rebuild, used by
    // refreshRenderBucketsIfChanged() to detect priority/membership changes
    // without rebuilding every frame.
    private ObjectInstance[] bucketSnapshotInstances = new ObjectInstance[64];
    private long[] bucketSnapshotKeys = new long[64];
    private int bucketSnapshotCount;

    // Cached combined active objects list to avoid allocation in getActiveObjects()
    private final List<ObjectInstance> cachedActiveObjects = new ArrayList<>();
    private final List<ObjectInstance> cachedSolidProviderObjects = new ArrayList<>();
    private final List<ObjectInstance> cachedTouchResponseObjects = new ArrayList<>();
    private final ObjectCollisionResponseList collisionResponseList = new ObjectCollisionResponseList();
    private boolean activeObjectsCacheDirty = true;
    private final Set<ObjectInstance> deferredDynamicExecThisFrame =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // ROM parity: dynamic object slot tracking for the current game's allocatable
    // SST window. S1 uses 32..127, S2 uses 16..127, and S3K uses 4..92.
    // Occupancy/allocation authority; ObjectManager retains execOrder + objectIdInSlot
    // as the slot->occupant identity authority.
    private final SlotAllocator slotAllocator;
    private int twoAxisCameraYCoarse = Integer.MIN_VALUE;

    // ROM parity: Tracks child slots reserved by objects with getReservedChildSlotCount() > 0.
    // In S1, ring objects (obj25) allocate child ring slots via FindFreeObj. These slots
    // must be occupied to match the ROM's SST layout and give subsequent objects correct
    // slot numbers (affecting timing gates like (v_vbla_byte + d7) & 7).
    private final Map<ObjectSpawn, int[]> reservedChildSlots = new IdentityHashMap<>();

    // Rewind: captured DynamicObjectEntry payloads for player-bound dynamics
    // (Shield, Stars) that are NOT recreated by the codec on restore. The
    // post-restore callback in
    // AbstractPlayableSprite#refreshPowerUpObjectsAfterRewindRestore relinks a
    // live matching shield when one exists, otherwise re-spawns via the power-up
    // spawner. The spawner consumes the captured entry via
    // {@link #consumePendingPlayerBoundEntry(Class)} so the new instance lands
    // at the same slot the reference run had AND has the captured field surface
    // restored on top of its fresh-construction state. Without that restore,
    // animation cursors and similar non-construction-set scalars would reset to
    // zero on every rewind.
    private final Map<Class<?>, java.util.ArrayDeque<
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry>>
            pendingPlayerBoundEntries = new java.util.HashMap<>();

    // Rewind: in-place restore support. Matching live instances are reused by the
    // snapshot restore instead of destroy/recreate when the class passes the
    // non-captured-field audit (see isRewindInPlaceReuseSafeClass). The scratch map
    // indexes live instances by spawn identity for one restore pass; the
    // side-effect map latches classes whose constructors spawn children / reserve
    // child slots during an in-restore recreate (observed per session) onto the
    // recreate path, because reuse would skip those construction side effects.
    private boolean rewindInPlaceRestoreEnabled = true;
    private final Map<ObjectSpawn, ObjectInstance> rewindRestoreReuseScratch = new IdentityHashMap<>();
    private final Map<Class<?>, Boolean> rewindRestoreConstructionSideEffects = new HashMap<>();

    // Rewind: monotonically-increasing counter assigned once per object when it is
    // added to the live object set (activeObjects or dynamicObjects). Combined with
    // the spawn's layoutIndex it forms a stable ObjectRefId via ObjectRefId.forObject()
    // that survives capture→restore→re-simulation cycles because re-simulation adds
    // objects in the same order, re-minting the same ids. Captured in the snapshot
    // and restored so the counter never accidentally aliases a pre-restore id.
    private int dynamicObjectIdCounter = 0;
    // Per-object id registry: maps every live ObjectInstance to its assigned ObjectRefId
    // so capture can build the identity table without re-scanning or re-allocating ids.
    // Cleared on reset() and pruned when objects are removed.
    private final IdentityHashMap<ObjectInstance, ObjectRefId> rewindObjectIds = new IdentityHashMap<>();

    // Rewind: construction-spawned boss/object children produced while an active object is
    // reconstructed during restore. The reconstructed parent wires its back-references
    // (childComponents, named child fields) to THESE instances; the step-4 dynamic-object
    // reconciliation loop then adopts each one in place — registering it at its captured slot
    // and applying its EXACT captured state — instead of recreating a duplicate via a codec.
    // This gives exact-state fidelity for construction children while keeping the parent's
    // back-references valid (they point at the very instances the loop restores onto), and
    // avoids the double-spawn that a codec recreate would cause. Cleared at the start and end
    // of each restore so it never leaks instances across passes.
    private final List<AbstractObjectInstance> rewindReconstructionChildren = new ArrayList<>();
    private boolean rewindReconstructionChildCapture;
    private static final java.util.concurrent.ConcurrentMap<Class<?>, Boolean>
            REWIND_IN_PLACE_REUSE_SAFE_CLASSES = new java.util.concurrent.ConcurrentHashMap<>();

    private final PlaneSwitchers planeSwitchers;
    private final ObjectSolidContactController solidContacts;
    private final ObjectTouchResponseController touchResponses;

    private static final Comparator<ObjectInstance> RENDER_SLOT_DESCENDING = (a, b) -> {
        int slotA = a instanceof AbstractObjectInstance aoiA ? aoiA.getSlotIndex() : Integer.MAX_VALUE;
        int slotB = b instanceof AbstractObjectInstance aoiB ? aoiB.getSlotIndex() : Integer.MAX_VALUE;
        return Integer.compare(slotB, slotA);
    };

    public ObjectManager(List<ObjectSpawn> spawns, ObjectRegistry registry,
            int planeSwitcherObjectId, PlaneSwitcherConfig planeSwitcherConfig,
            TouchResponseTable touchResponseTable, GraphicsManager graphicsManager,
            Camera camera, ObjectServices objectServices) {
        this.registry = registry;
        this.graphicsManager = graphicsManager;
        this.camera = camera;
        this.objectServices = objectServices;
        this.slotLayout = registry != null ? registry.objectSlotLayout() : ObjectSlotLayout.SONIC_1;
        this.windowingStrategy = registry != null
                ? registry.objectWindowingStrategy()
                : ObjectWindowingStrategy.LEGACY;
        this.placement = new ObjectPlacementController(spawns,
                camera != null ? camera::getWidth : com.openggf.level.spawn.PlacementViewportWidth::current);
        this.placement.setTwoAxisCursorPlacement(slotLayout.twoAxisCursorPlacement());
        this.placement.setWindowingStrategy(windowingStrategy);
        this.execOrder = new ObjectInstance[slotLayout.dynamicSlotCount()];
        this.slotAllocator = new SlotAllocator(slotLayout,
                slotLayout.twoAxisCursorPlacement()
                        ? SlotEmptyPredicate.ROUTINE_POINTER
                        : SlotEmptyPredicate.ID_BYTE);
        this.planeSwitchers = planeSwitcherConfig != null
                ? new PlaneSwitchers(placement, planeSwitcherObjectId, planeSwitcherConfig)
                : null;
        this.solidContacts = new ObjectSolidContactController(this);
        this.touchResponses = touchResponseTable != null
                ? new ObjectTouchResponseController(this, touchResponseTable)
                : null;
        this.skipVerticalSpawnLoadFilterForGame = slotLayout == ObjectSlotLayout.SONIC_2;
        // Initialize bucket arrays
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i] = new ArrayList<>();
            highPriorityBuckets[i] = new ArrayList<>();
        }
    }

    public ObjectManager(List<ObjectSpawn> spawns, ObjectRegistry registry,
            int planeSwitcherObjectId, PlaneSwitcherConfig planeSwitcherConfig,
            TouchResponseTable touchResponseTable) {
        this(spawns, registry, planeSwitcherObjectId, planeSwitcherConfig, touchResponseTable,
                defaultServices());
    }

    private ObjectManager(List<ObjectSpawn> spawns, ObjectRegistry registry,
            int planeSwitcherObjectId, PlaneSwitcherConfig planeSwitcherConfig,
            TouchResponseTable touchResponseTable, ObjectServices services) {
        this(spawns, registry, planeSwitcherObjectId, planeSwitcherConfig, touchResponseTable,
                services.graphicsManager(),
                services.camera(),
                services);
    }

    private static ObjectServices defaultServices() {
        var gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode != null) {
            return new DefaultObjectServices(gameplayMode, EngineServices.current());
        }
        return new BootstrapObjectServices();
    }

    private boolean isManagedDynamicSlot(int slotIndex) {
        return slotLayout.isDynamicSlot(slotIndex);
    }

    private int execIndexForSlot(int slotIndex) {
        return slotLayout.toExecIndex(slotIndex);
    }

    private int executionSlotIndex(AbstractObjectInstance instance) {
        return instance.getExecutionSlotIndex();
    }

    private int slotIndexForExec(int execIndex) {
        return slotLayout.toSlotIndex(execIndex);
    }

    public void reset(int cameraX) {
        clearActiveObjects();
        dynamicObjects.clear();
        auxiliaryDynamicObjects.clear();
        deferredDynamicExecThisFrame.clear();
        reservedChildSlots.clear();
        slotAllocator.clear();
        Arrays.fill(execOrder, null);
        cachedActiveObjects.clear();
        activeObjectsCacheDirty = true;
        bucketsDirty = true;
        frameCounter = 0;
        dynamicObjectIdCounter = 0;
        rewindObjectIds.clear();
        twoAxisCameraYCoarse = Integer.MIN_VALUE;
        placement.reset(cameraX);
        if (registry != null) {
            registry.reportCoverage(placement.getAllSpawns());
        }
        if (planeSwitchers != null) {
            planeSwitchers.reset();
        }
        solidContacts.reset();
        if (touchResponses != null) {
            touchResponses.reset();
        }
        // Materialize the current ObjectPlacementController window immediately after reset.
        // S1 needs this for ROM parity at level start; for S2/S3K it keeps
        // manual camera resets and headless probes from sitting on an empty
        // active window until a later ObjectPlacementController delta occurs. Initial reset
        // materialization still honors the camera-Y filter; S2's vertical bypass is runtime-only.
        syncActiveSpawnsLoad(false);
    }

    ObjectServices services() {
        return objectServices;
    }

    public boolean usesTwoAxisCursorPlacement() {
        return slotLayout.twoAxisCursorPlacement();
    }

    /**
     * Instantiates all spawns currently in the ObjectPlacementController window. Intended for
     * trace replay and editor state restoration that need engine object
     * instances to exist before the first {@link #update} call, so external
     * state (ROM SST snapshots, editor bookmarks) can be hydrated onto them.
     *
     * <p>For S1 (counter-based respawn, {@code UNIFIED} collision model) this
     * work is already done inside {@link #reset(int)}. For S2/S3K
     * ({@code DUAL_PATH}) spawn→instance conversion is normally deferred until
     * the first {@link #update} tick; calling this method brings S2/S3K in
     * line with that ROM behavior ahead of time.
     *
     * <p>Idempotent: objects that already exist are not re-created.
     */
    public void preloadInitialSpawnsForHydration() {
        syncActiveSpawnsLoad(skipVerticalSpawnLoadFilterForGame);
    }

    /**
     * Replaces the spawn list with a new one from the editor.
     * Clears remembered/destroyed state so edited spawns can respawn.
     * Existing active objects are cleared — {@code syncActiveSpawns()} on
     * the next frame will re-instantiate objects in the camera window.
     */
    public void resyncSpawnList(List<ObjectSpawn> newSpawns) {
        clearActiveObjects();
        cachedActiveObjects.clear();
        activeObjectsCacheDirty = true;
        bucketsDirty = true;
        twoAxisCameraYCoarse = Integer.MIN_VALUE;
        placement.replaceSpawnsAndReset(newSpawns);
    }

    void resetTouchResponses() {
        if (touchResponses != null) {
            touchResponses.reset();
        }
    }

    /**
     * Forces cached render buckets to rebuild on the next draw.
     *
     * Object priority can change during update (or by following another entity's
     * priority), so add/remove-based invalidation alone is not sufficient.
     */
    public void invalidateRenderBuckets() {
        bucketsDirty = true;
    }

    /**
     * Marks the cached render buckets dirty only when their inputs actually
     * changed since the last rebuild. Object priority is exposed through
     * virtual {@link ObjectInstance#isHighPriority()} /
     * {@link ObjectInstance#getPriorityBucket()} implementations (many follow
     * other entities' state live), so there is no central mutation hook; this
     * cheap once-per-frame scan replaces the previous unconditional per-frame
     * rebuild while staying immune to untracked priority changes.
     */
    public void refreshRenderBucketsIfChanged() {
        if (bucketsDirty) {
            return;
        }
        if (renderBucketInputsChanged()) {
            bucketsDirty = true;
        }
    }

    private boolean renderBucketInputsChanged() {
        // Early-out for the common membership-change case; the per-index
        // identity comparison below would also catch it.
        if (activeObjects.size() + dynamicObjects.size() != bucketSnapshotCount) {
            return true;
        }
        int position = 0;
        for (ObjectInstance instance : activeObjects.values()) {
            if (position >= bucketSnapshotCount
                    || bucketSnapshotInstances[position] != instance
                    || bucketSnapshotKeys[position] != renderBucketKey(instance)) {
                return true;
            }
            position++;
        }
        for (ObjectInstance instance : dynamicObjects) {
            if (position >= bucketSnapshotCount
                    || bucketSnapshotInstances[position] != instance
                    || bucketSnapshotKeys[position] != renderBucketKey(instance)) {
                return true;
            }
            position++;
        }
        return position != bucketSnapshotCount;
    }

    private void captureRenderBucketSnapshot() {
        int required = activeObjects.size() + dynamicObjects.size();
        if (bucketSnapshotInstances.length < required) {
            int newLength = Math.max(required, bucketSnapshotInstances.length * 2);
            bucketSnapshotInstances = new ObjectInstance[newLength];
            bucketSnapshotKeys = new long[newLength];
        }
        int position = 0;
        for (ObjectInstance instance : activeObjects.values()) {
            bucketSnapshotInstances[position] = instance;
            bucketSnapshotKeys[position] = renderBucketKey(instance);
            position++;
        }
        for (ObjectInstance instance : dynamicObjects) {
            bucketSnapshotInstances[position] = instance;
            bucketSnapshotKeys[position] = renderBucketKey(instance);
            position++;
        }
        // Release stale references beyond the live range so removed objects
        // are not retained by the snapshot.
        for (int i = position; i < bucketSnapshotCount; i++) {
            bucketSnapshotInstances[i] = null;
        }
        bucketSnapshotCount = position;
    }

    // Packed-key layout: bit 0 = highPriority, bits 1-3 = bucket index,
    // bits 8+ = slot index. The shifted bucket index must stay below bit 8 or
    // it would bleed into the slot field and silently corrupt change detection.
    static {
        if (RenderPriority.MAX - RenderPriority.MIN >= 8) {
            throw new AssertionError("renderBucketKey bucket bits overflow");
        }
    }

    private static long renderBucketKey(ObjectInstance instance) {
        long slot = instance instanceof AbstractObjectInstance aoi ? aoi.getSlotIndex() : Integer.MAX_VALUE;
        int bucket = RenderPriority.clamp(instance.getPriorityBucket()) - RenderPriority.MIN;
        return (slot << 8) | (long) (bucket << 1) | (instance.isHighPriority() ? 1L : 0L);
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks, int touchFrameCounter) {
        update(cameraX, player, sidekicks, touchFrameCounter, true);
    }

    /**
     * Run touch responses for a single player outside the main update loop.
     * ROM order: ReactToItem runs during each player's slot within ExecuteObjects,
     * after their physics but before other objects' solid checks.
     */
    public void runTouchResponsesForPlayer(PlayableEntity player, int touchFrameCounter) {
        runTouchResponsesForPlayer(player, touchFrameCounter, false);
    }

    /**
     * Runs the inline player-slot touch pass against the frame-start snapshot.
     * ROM order: player slots scan before later level-object slots update.
     */
    public void runTouchResponsesForPlayer(PlayableEntity player, int touchFrameCounter,
                                           boolean usePreUpdateState) {
        if (touchResponses == null) {
            return;
        }
        touchResponses.getDebugState().setEnabled(
                objectServices.debugOverlay().isEnabled(DebugOverlayToggle.TOUCH_RESPONSE));
        // CPU sidekick uses separate overlap tracking and Hurt_Sidekick handling.
        if (player.isCpuControlled()) {
            touchResponses.updateSidekick(player, touchFrameCounter, usePreUpdateState);
        } else {
            touchResponses.update(player, touchFrameCounter, usePreUpdateState);
        }
    }

    /**
     * Refreshes frame-start object/camera state for inline-order touch checks.
     * S3K can instead preserve the previous dynamic Collision_response_list
     * snapshot because player slots run before that list is rebuilt.
     */
    public void snapshotTouchResponseState() { snapshotTouchResponseState(false); }

    public void snapshotTouchResponseState(boolean preservePreviousCollisionResponseList) {
        updateCameraBounds();
        collisionResponseList.setUsePrevious(preservePreviousCollisionResponseList);
        for (ObjectInstance inst : activeObjects.values()) {
            refreshTouchResponseSnapshot(inst);
        }
        for (ObjectInstance inst : dynamicObjects) {
            refreshTouchResponseSnapshot(inst);
        }
    }

    private void refreshTouchResponseSnapshot(ObjectInstance inst) {
        if (collisionResponseList.shouldRefreshFrameStartSnapshot()) {
            inst.snapshotTouchResponseState();
        } else {
            inst.clearSpawnTouchSkip(); // S3K previous-list path: see ObjectInstance.clearSpawnTouchSkip
        }
    }

    public void refreshPostCameraRenderState() {
        updateCameraBounds();
        for (ObjectInstance inst : activeObjects.values()) {
            inst.refreshPostCameraRenderState();
        }
        for (ObjectInstance inst : dynamicObjects) {
            inst.refreshPostCameraRenderState();
        }
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses) {
        update(cameraX, player, sidekicks, touchFrameCounter, enableTouchResponses, false, false);
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        List<? extends PlayableEntity> activeSidekicks = sidekicks != null ? sidekicks : List.of();
        frameCounter++;
        vblaCounter++;
        // Inline-physics path: snapshotTouchResponseState() ran earlier this
        // frame and already refreshed the cached camera bounds. The second
        // call here is harmless redundancy (the post-camera-step bounds
        // haven't shifted between snapshot and update) -- it's kept so the
        // non-inline path (which doesn't snapshot) still gets fresh bounds
        // before the exec loop. Do not consolidate without verifying both
        // call paths first.
        updateCameraBounds();
        SolidExecutionRegistry solidExecutionRegistry = objectServices.solidExecutionRegistry();
        solidExecutionRegistry.beginFrame(frameCounter, collectActivePlayers(player, activeSidekicks));
        boolean counterBased = placement.isCounterBasedRespawn();
        boolean execThenLoad = placement.usesExecThenLoadPlacement();

        if (inlineSolidResolution) {
            solidContacts.beginInlineFrame(player, activeSidekicks, solidPostMovement);
        }
        try {
            if (counterBased) {
                updateCounterBasedExecThenLoad(
                        cameraX,
                        player,
                        activeSidekicks,
                        inlineSolidResolution,
                        solidPostMovement);
            } else if (execThenLoad) {
                if (slotLayout.twoAxisCursorPlacement()) {
                    // S3K Load_Sprites runs before Process_Sprites and performs
                    // the X-cursor pass before the Y-camera pass
                    // (docs/skdisasm/sonic3k.asm:7884-7894, 37640-37762).
                    // S3K stays load-then-exec.
                    placement.update(cameraX);
                    syncActiveSpawnsLoad(false);
                }
                // S2: NO pre-exec load. ROM S2 is RunObjects (s2.asm:5095) then
                // exactly one ObjectsManager (s2.asm:5112) = exec -> one load.
                // The single S2 load runs in the post-block below
                // (RunObjects -> ObjectsManager position), after runExecLoop has
                // applied the object-side MarkObjGone self-deletes.
                cleanupDestroyedDynamicObjects();
                runExecLoop(cameraX, player, activeSidekicks, inlineSolidResolution, solidPostMovement);
            } else {
                syncActiveSpawnsUnload();
                cleanupDestroyedDynamicObjects();
                syncActiveSpawnsLoad(true);
                runExecLoop(cameraX, player, activeSidekicks, inlineSolidResolution, solidPostMovement);
            }
        } finally {
            if (inlineSolidResolution) {
                solidContacts.finishInlineFrame(player, activeSidekicks);
            }
            solidExecutionRegistry.finishFrame();
        }

        // Solid contacts now resolve during SpriteManager.update(), before animation.
        if (enableTouchResponses && touchResponses != null) {
            touchResponses.getDebugState().setEnabled(
                    objectServices.debugOverlay().isEnabled(DebugOverlayToggle.TOUCH_RESPONSE));
            touchResponses.update(player, touchFrameCounter);
            for (PlayableEntity sk : activeSidekicks) {
                touchResponses.updateSidekick(sk, touchFrameCounter);
            }
        }

        // Stream objects for the next frame; counter-based S1 defers to the
        // post-camera placement pass, while S2 keeps its single exec->load pass.
        if (!counterBased) {
            if (!execThenLoad || !slotLayout.twoAxisCursorPlacement()) {
                placement.update(cameraX);
            }
            if (execThenLoad && !slotLayout.twoAxisCursorPlacement()) {
                cleanupDestroyedDynamicObjects();
                syncActiveSpawnsLoad(true);
            }
        }
        captureCollisionResponseListForNextFrame(cameraX);
    }

    private List<PlayableEntity> collectActivePlayers(PlayableEntity player,
            List<? extends PlayableEntity> sidekicks) {
        List<? extends PlayableEntity> activeSidekicks = sidekicks != null ? sidekicks : List.of();
        List<PlayableEntity> players = activePlayersScratch;
        players.clear();
        if (player != null) {
            players.add(player);
        }
        for (PlayableEntity sidekick : activeSidekicks) {
            if (sidekick != null) {
                players.add(sidekick);
            }
        }
        return players;
    }

    private void executeObjectWithSolidContext(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        SolidExecutionRegistry registry = objectServices.solidExecutionRegistry();
        SolidExecutionMode mode = null;
        if (instance instanceof SolidObjectProvider provider) {
            SolidExecutionMode providerMode = provider.solidExecutionMode();
            if (inlineSolidResolution || providerMode == SolidExecutionMode.MANUAL_CHECKPOINT) {
                mode = providerMode;
            }
        }

        ObjectSolidExecutionContext.Resolver resolver =
                mode == SolidExecutionMode.MANUAL_CHECKPOINT
                        ? () -> solidContacts.processManualCheckpoint(
                                instance, player, sidekicks, solidPostMovement)
                        : null;
        registry.beginObject(instance, resolver);
        try {
            instance.update(vblaCounter, player);
            if (mode == SolidExecutionMode.AUTO_AFTER_UPDATE && !instance.isDestroyed()) {
                registry.publishCheckpoint(
                        solidContacts.processCompatibilityCheckpoint(
                                instance, player, sidekicks, solidPostMovement));
            }
        } finally {
            registry.endObject(instance);
        }
    }

    /**
     * ROM-accurate update flow for S1 counter-based respawn.
     * <p>
     * Matches the ROM's Level_MainLoop order:
     * <ol>
     *   <li><b>ExecuteObjects</b> — runs objects in slot order. Objects that are
     *       out of range call DeleteObject during their own routine, freeing their
     *       slot immediately. Child allocations (Ring_Main FindFreeObj, CStom FindNextFreeObj)
     *       see these freed slots in the same pass.</li>
     *   <li><b>ObjPosLoad</b> — loads new objects using FindFreeObj, which sees the
     *       post-ExecuteObjects slot landscape (all frees and child allocations applied).</li>
     * </ol>
     * <p>
     * The previous approach batch-freed all out-of-range objects before the exec loop,
     * then loaded new objects before exec. New objects could fill freed slots that should
     * have been available for child allocations, causing cumulative slot offset drift.
     */
    private void updateCounterBasedExecThenLoad(int cameraX, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        // Phase 1: Snapshot positions and build exec order from EXISTING objects.
        for (ObjectInstance inst : activeObjects.values()) {
            inst.snapshotPreUpdatePosition();
        }
        for (ObjectInstance inst : dynamicObjects) {
            inst.snapshotPreUpdatePosition();
        }

        Arrays.fill(execOrder, null);
        for (ObjectInstance inst : activeObjects.values()) {
            if (inst instanceof AbstractObjectInstance aoi && isManagedDynamicSlot(executionSlotIndex(aoi))) {
                execOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
            }
        }
        for (ObjectInstance inst : dynamicObjects) {
            if (inst instanceof AbstractObjectInstance aoi && isManagedDynamicSlot(executionSlotIndex(aoi))) {
                execOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
            }
        }
        // Phase 2: ExecuteObjects — run objects in slot order with inline out_of_range.
        updating = true;
        boolean objectsRemoved = false;
        try {
            for (currentExecSlot = 0; currentExecSlot < execOrder.length; currentExecSlot++) {
                ObjectInstance instance = execOrder[currentExecSlot];
                if (instance == null) continue;

                // ROM parity: each object checks out_of_range at the start of its
                // routine during ExecuteObjects. Freeing the slot here (not in a
                // batch pre-pass) ensures child allocations from higher slots see
                // the correct set of available slots.
                ObjectSpawn spawn = instanceToSpawn.get(instance);
                if (unloadCounterBasedOutOfRange(instance, spawn,
                        slotIndexForExec(currentExecSlot), cameraX)) {
                    execOrder[currentExecSlot] = null;
                    objectsRemoved = true;
                    continue;
                }

                executeObjectWithSolidContext(
                        instance, player, sidekicks, inlineSolidResolution, solidPostMovement);

                if (instance.isDestroyed()) {
                    int slotIndex = slotIndexForExec(currentExecSlot);
                    if (instance instanceof AbstractObjectInstance aoi3
                            && aoi3.getSlotIndex() == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    instance.onUnload();
                    execOrder[currentExecSlot] = null;

                    if (spawn != null) {
                        freeAllReservedChildSlots(spawn);
                        placement.clearStayActive(spawn);
                        dispatchDestroyRemoveFromActive(instance, spawn);
                        removeActiveObject(spawn);
                    } else {
                        removeDynamicObjectInstance(instance);
                    }
                    objectsRemoved = true;
                }
            }

            // Fallback: process dynamic objects without valid slots
            populateDynamicFallbackScratch();
            for (ObjectInstance inst : dynamicFallbackScratch) {
                if (inst.isDestroyed()) {
                    releaseSlotIfManaged(inst);
                    inst.onUnload();
                    removeDynamicObjectInstance(inst);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (unloadCounterBasedOutOfRange(inst, null, -1, cameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (inst.isDestroyed()) {
                    releaseSlotIfManaged(inst);
                    inst.onUnload();
                    removeDynamicObjectInstance(inst);
                    objectsRemoved = true;
                }
            }
            // Fallback: process active objects without valid slots
            populateActiveFallbackScratch();
            for (ObjectInstance inst : activeFallbackScratch) {
                ObjectSpawn spawn = instanceToSpawn.get(inst);
                if (spawn == null) {
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(spawn);
                    dispatchDestroyRemoveFromActive(inst, spawn);
                    removeActiveObject(spawn);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (unloadCounterBasedOutOfRange(inst, spawn, -1, cameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(spawn);
                    dispatchDestroyRemoveFromActive(inst, spawn);
                    removeActiveObject(spawn);
                    objectsRemoved = true;
                }
            }
        } finally {
            currentExecSlot = -1;
            updating = false;
            deferredDynamicExecThisFrame.clear();
            if (objectsRemoved) {
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
        }

        // Phase 3: ObjPosLoad — load new objects AFTER ExecuteObjects.
        // Slots freed during the exec loop and child slots allocated during exec
        // are now reflected in the allocator. New objects get the correct slot numbers.
        syncActiveSpawnsLoad(false);

    }

    /**
     * Runs the standard exec loop for non-counter-based respawn (S2/S3K).
     * This preserves the existing behavior where unload and load happen before exec.
     */
    private void runExecLoop(int cameraX, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        // ROM parity: Snapshot all objects' positions BEFORE their updates run.
        for (ObjectInstance inst : activeObjects.values()) {
            inst.snapshotPreUpdatePosition();
        }
        for (ObjectInstance inst : dynamicObjects) {
            inst.snapshotPreUpdatePosition();
        }

        // ROM parity: Build slot-ordered execution array.
        Arrays.fill(execOrder, null);
        for (ObjectInstance inst : activeObjects.values()) {
            if (inst instanceof AbstractObjectInstance aoi && isManagedDynamicSlot(executionSlotIndex(aoi))) {
                execOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
            }
        }
        for (ObjectInstance inst : dynamicObjects) {
            if (inst instanceof AbstractObjectInstance aoi && isManagedDynamicSlot(executionSlotIndex(aoi))) {
                execOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
            }
        }
        updating = true;
        boolean objectsRemoved = false;
        // Track objects processed by the slot-based loop so the fallback loop
        // doesn't double-update objects that lost their slot mid-frame.
        // Reused per frame; cleared in the finally block below.
        Set<ObjectInstance> processedInExecLoop = processedInExecLoopScratch;
        try {
            // ROM parity: Iterate slots in ascending order, matching ExecuteObjects.
            for (currentExecSlot = 0; currentExecSlot < execOrder.length; currentExecSlot++) {
                ObjectInstance instance = execOrder[currentExecSlot];
                if (instance == null) continue;
                processedInExecLoop.add(instance);

                executeObjectWithSolidContext(
                        instance, player, sidekicks, inlineSolidResolution, solidPostMovement);

                // ROM parity: each object calls RememberState / out_of_range
                // at the END of its routine, AFTER updating position. S2/S3K
                // objects end their routines with `jmpto JmpTo_MarkObjGone_P1`
                // (docs/s2disasm/s2.asm MarkObjGone_P1 line ~30080), which
                // checks the object's CURRENT x_pos(a0) — not its spawn x —
                // against the camera window. Moving objects (e.g. Buzzer
                // flying 174 px west of spawn) survive as long as their
                // current position is in range. This check runs for both
                // counter-based (S1) and non-counter (S2/S3K) placement.
                if (!instance.isDestroyed()) {
                    ObjectSpawn oorSpawn = instanceToSpawn.get(instance);
                    if (unloadCounterBasedOutOfRange(instance, oorSpawn,
                            slotIndexForExec(currentExecSlot), cameraX)) {
                        execOrder[currentExecSlot] = null;
                        objectsRemoved = true;
                        continue;
                    }
                }

                if (instance.isDestroyed()) {
                    int slotIndex = slotIndexForExec(currentExecSlot);
                    if (instance instanceof AbstractObjectInstance aoi3
                            && aoi3.getSlotIndex() == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    instance.onUnload();
                    execOrder[currentExecSlot] = null;

                    ObjectSpawn spawn = instanceToSpawn.get(instance);
                    if (spawn != null) {
                        freeAllReservedChildSlots(spawn);
                        placement.clearStayActive(spawn);
                        dispatchDestroyRemoveFromActive(instance, spawn);
                        removeActiveObject(spawn);
                    } else {
                        removeDynamicObjectInstance(instance);
                    }
                    objectsRemoved = true;
                }
            }

            // Fallback: process objects without valid slots
            populateDynamicFallbackScratch();
            for (ObjectInstance inst : dynamicFallbackScratch) {
                if (inst.isDestroyed()) {
                    releaseSlotIfManaged(inst);
                    inst.onUnload();
                    removeDynamicObjectInstance(inst);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (!inst.isDestroyed()
                        && unloadCounterBasedOutOfRange(inst, null, -1, cameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (inst.isDestroyed()) {
                    releaseSlotIfManaged(inst);
                    inst.onUnload();
                    removeDynamicObjectInstance(inst);
                    objectsRemoved = true;
                }
            }
            populateActiveFallbackScratch();
            for (ObjectInstance inst : activeFallbackScratch) {
                ObjectSpawn spawn = instanceToSpawn.get(inst);
                if (spawn == null) {
                    continue;
                }
                // Skip objects already processed in the slot-based exec loop.
                // This prevents double-updates when an object releases its slot mid-frame.
                if (processedInExecLoop.contains(inst)) {
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(spawn);
                    dispatchDestroyRemoveFromActive(inst, spawn);
                    removeActiveObject(spawn);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (!inst.isDestroyed()
                        && unloadCounterBasedOutOfRange(inst, spawn, -1, cameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (inst.isDestroyed()) {
                    inst.onUnload();
                    placement.clearStayActive(spawn);
                    dispatchDestroyRemoveFromActive(inst, spawn);
                    removeActiveObject(spawn);
                    objectsRemoved = true;
                }
            }
        } finally {
            currentExecSlot = -1;
            updating = false;
            deferredDynamicExecThisFrame.clear();
            processedInExecLoopScratch.clear();
            if (objectsRemoved) {
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
        }
    }

    /**
     * Extend the ObjectPlacementController active set using the post-camera X position.
     * <p>
     * ROM parity: {@code ObjPosLoad} runs <b>after</b> {@code DeformLayers}
     * (camera update), so it sees the camera's post-update position. The primary
     * {@code placement.update()} inside {@link #update} uses the pre-camera
     * position. When the camera crosses a 128px chunk boundary between those
     * two positions, the post-camera spawn window exposes a right-side gap
     * while moving forward or a left-side gap while moving backward. Objects in
     * that gap must materialize before the frame ends, matching ROM ObjPosLoad
     * timing.
     * <p>
     * This method scans the gap region (between the old and new window right
     * edges) and materializes any eligible spawns into the current frame's
     * object table, WITHOUT updating the ObjectPlacementController's internal state
     * (cursor, lastCameraChunk). This ensures that the primary ObjectPlacementController pass
     * in the next frame still processes the chunk boundary normally
     * (including left-edge removal), while the gap spawns already exist with
     * ROM-accurate end-of-frame timing. Because this runs after
     * {@code ObjectManager.update(...)} for the current frame, the newly
     * created instances do not execute until the following frame's
     * ExecuteObjects pass.
     *
     * @param postCameraX camera X position after the camera update step
     */
    public void postCameraPlacementUpdate(int postCameraX) {
        if (slotLayout.twoAxisCursorPlacement()) {
            // S3K Load_Sprites runs at the start of LevelLoop before
            // Process_Sprites/DeformBgLayer, so post-camera ObjectPlacementController catch-up
            // would create objects one ROM loader call early
            // (docs/skdisasm/sonic3k.asm:7884-7895).
            return;
        }
        placement.extendForPostCamera(postCameraX, this::inlineCreateObject);
    }

    /**
     * Inline creation callback for ROM-accurate ObjPosLoad.
     * Called during ObjectPlacementController cursor advancement to create instances immediately.
     * <p>
     * This is the post-camera ({@link #postCameraPlacementUpdate}) gap-scan
     * creation path. It must apply the SAME vertical-eligibility policy as the
     * primary {@link #syncActiveSpawnsLoad}{@code (true)} load so that a spawn
     * entering the horizontal load window on a chunk-crossing frame is
     * materialized on the SAME frame in both paths. For S2 the ROM
     * {@code ObjectsManager_GoingForward}/{@code GoingBackward} calls
     * {@code ChkLoadObj} immediately after the X-window scan with no
     * {@code Camera_Y_pos} filter (docs/s2disasm/s2.asm:33095-33136), so S2 loads
     * bypass the vertical filter ({@code allowVerticalLoadBypassForS2 = true}).
     * Passing {@code false} here previously deferred a horizontally-in-window
     * but not-yet-vertically-near spawn to the next frame's pre-camera load,
     * costing the object one update relative to the ROM (e.g. CNZ {@code ObjD4}
     * arriving 1px behind, s2.asm:58759-58799).
     */
    private boolean inlineCreateObject(ObjectSpawn spawn, int counterValue) {
        if (activeObjects.containsKey(spawn)) {
            return true; // Already exists
        }
        if (!isSpawnVerticallyEligibleForLoad(spawn, true)) {
            return false;
        }
        int preSlot = allocateSlot();
        if (preSlot < 0) {
            return false; // FindFreeObj failure equivalent
        }
        ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                () -> registry != null ? registry.create(spawn) : null);
        if (instance != null) {
            if (instance instanceof AbstractObjectInstance aoi) {
                aoi.setServices(objectServices);
                if (aoi.getSlotIndex() < 0) {
                    aoi.setSlotIndex(preSlot);
                }
                if (counterValue >= 0) {
                    aoi.setRespawnStateIndex(counterValue);
                }
            } else {
                releaseSlot(preSlot);
            }
            registerActiveObject(spawn, instance);
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
            return true;
        } else {
            releaseSlot(preSlot);
            return false;
        }
    }

    /**
     * Returns ObjectPlacementController cursor diagnostics for ROM↔engine comparison.
     * Only meaningful for S1 counter-based respawn mode.
     */
    public int[] getPlacementCursorState() {
        if (!placement.isCounterBasedRespawn()) return null;
        return new int[] {
            placement.getCursorIndex(),
            placement.getLeftCursorIndex(),
            placement.getFwdCounter(),
            placement.getBwdCounter(),
            placement.getLastCameraChunk()
        };
    }

    public void applyPlaneSwitchers(PlayableEntity player) {
        // Sonic 2 Obj03 tracks separate crossover state for MainCharacter and
        // Sidekick (objoff_34 / objoff_35) and updates both each frame.
        if (planeSwitchers != null) {
            planeSwitchers.update(player);
        }
    }

    public int getPlaneSwitcherSideState(ObjectSpawn spawn) {
        if (planeSwitchers == null) {
            return -1;
        }
        return planeSwitchers.getSideState(spawn);
    }

    public void drawLowPriority() {
        ensureBucketsPopulated();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            drawPriorityBucket(bucket, false);
        }
    }

    public void drawHighPriority() {
        ensureBucketsPopulated();
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            drawPriorityBucket(bucket, true);
        }
    }

    private void ensureBucketsPopulated() {
        if (!bucketsDirty) {
            return;
        }
        bucketsDirty = false;

        // Clear all buckets
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i].clear();
            highPriorityBuckets[i].clear();
        }

        // Bucket active objects
        for (ObjectInstance instance : activeObjects.values()) {
            int bucket = RenderPriority.clamp(instance.getPriorityBucket());
            int idx = bucket - RenderPriority.MIN;
            if (instance.isHighPriority()) {
                highPriorityBuckets[idx].add(instance);
            } else {
                lowPriorityBuckets[idx].add(instance);
            }
        }

        // Bucket dynamic objects
        for (ObjectInstance instance : dynamicObjects) {
            int bucket = RenderPriority.clamp(instance.getPriorityBucket());
            int idx = bucket - RenderPriority.MIN;
            if (instance.isHighPriority()) {
                highPriorityBuckets[idx].add(instance);
            } else {
                lowPriorityBuckets[idx].add(instance);
            }
        }

        // ROM parity: lower sprite-table indices render in front. Objects execute and
        // call Draw_Sprite in slot order, so lower SST slots must be drawn later in
        // painter's-algorithm order. Sort each bucket descending by slot so lower
        // slot indices appear on top.
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i].sort(RENDER_SLOT_DESCENDING);
            highPriorityBuckets[i].sort(RENDER_SLOT_DESCENDING);
        }

        captureRenderBucketSnapshot();
    }

    public void drawPriorityBucket(int bucket, boolean highPriority) {
        ensureBucketsPopulated();
        int targetBucket = RenderPriority.clamp(bucket);
        int idx = targetBucket - RenderPriority.MIN;
        List<ObjectInstance>[] buckets = highPriority ? highPriorityBuckets : lowPriorityBuckets;
        List<ObjectInstance> instances = buckets[idx];

        if (instances.isEmpty()) {
            return;
        }

        enableVerticalWrapIfNeeded();
        try {
            renderCommands.clear();
            for (ObjectInstance instance : instances) {
                instance.appendRenderCommands(renderCommands);
            }

            if (renderCommands.isEmpty()) {
                return;
            }
            graphicsManager.enqueueDebugLineState();
            graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
            graphicsManager.enqueueDefaultShaderState();
        } finally {
            graphicsManager.disableVerticalWrapAdjust();
        }
    }

    /**
     * Draw all objects in a single unified bucket, regardless of their isHighPriority flag.
     * Calls the provided callback before drawing each object with its high priority status.
     *
     * This supports ROM-accurate sprite-to-sprite ordering where bucket number determines
     * draw order independently of the sprite-to-tile priority (isHighPriority flag).
     *
     * @param bucket   The priority bucket to draw (0-7)
     * @param callback Called before each object draw with (object, isHighPriority)
     */
    public void drawUnifiedBucket(int bucket, ObjectDrawCallback callback) {
        ensureBucketsPopulated();
        int targetBucket = RenderPriority.clamp(bucket);
        int idx = targetBucket - RenderPriority.MIN;

        // Draw low-priority objects first (they appear behind)
        drawBucketInstances(lowPriorityBuckets[idx], false, callback);

        // Draw high-priority objects second (they appear in front)
        drawBucketInstances(highPriorityBuckets[idx], true, callback);
    }

    private void drawBucketInstances(List<ObjectInstance> instances, boolean highPriority, ObjectDrawCallback callback) {
        if (instances.isEmpty()) {
            return;
        }

        enableVerticalWrapIfNeeded();
        try {
            renderCommands.clear();
            for (ObjectInstance instance : instances) {
                if (callback != null) {
                    callback.beforeDraw(instance, highPriority);
                }
                instance.appendRenderCommands(renderCommands);
            }

            if (!renderCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
                graphicsManager.enqueueDefaultShaderState();
            }
        } finally {
            graphicsManager.disableVerticalWrapAdjust();
        }
    }

    /**
     * Callback interface for unified object drawing.
     * Called before each object is drawn to allow setting up shader uniforms.
     */
    public interface ObjectDrawCallback {
        /**
         * Called before drawing an object.
         *
         * @param instance     The object instance about to be drawn
         * @param highPriority True if object should appear above high-priority tiles
         */
        void beforeDraw(ObjectInstance instance, boolean highPriority);
    }

    /**
     * Draw all objects in a single unified bucket with per-instance priority.
     * Priority is now handled per-instance in the shader, so no batch flushing
     * is needed when switching between low and high priority objects.
     *
     * @param bucket The priority bucket to draw (0-7)
     * @param gfx    The graphics manager to use for priority state
     */
    public void drawUnifiedBucketWithPriority(int bucket, GraphicsManager gfx) {
        ensureBucketsPopulated();
        int idx = RenderPriority.clamp(bucket) - RenderPriority.MIN;

        // The BatchedPatternRenderer uses a single global priority uniform for
        // the entire batch — it cannot vary per-instance. We must flush and
        // restart the batch at each LOW→HIGH transition so that each group
        // gets its own batch with the correct priority.
        // (The InstancedPatternRenderer bakes priority per-instance and doesn't
        // need the flush, but it's harmless — empty flushes are no-ops.)

        if (!lowPriorityBuckets[idx].isEmpty()) {
            gfx.flushPatternBatch();
            gfx.setCurrentSpriteHighPriority(false);
            gfx.beginPatternBatch();
            drawBucketInstancesWithPriority(lowPriorityBuckets[idx]);
        }

        if (!highPriorityBuckets[idx].isEmpty()) {
            gfx.flushPatternBatch();
            gfx.setCurrentSpriteHighPriority(true);
            gfx.beginPatternBatch();
            drawBucketInstancesWithPriority(highPriorityBuckets[idx]);
        }
    }

    private void drawBucketInstancesWithPriority(List<ObjectInstance> instances) {
        if (instances.isEmpty()) {
            return;
        }

        enableVerticalWrapIfNeeded();
        try {
            renderCommands.clear();
            for (ObjectInstance instance : instances) {
                instance.appendRenderCommands(renderCommands);
            }

            if (!renderCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, renderCommands));
                graphicsManager.enqueueDefaultShaderState();
            }
        } finally {
            graphicsManager.disableVerticalWrapAdjust();
        }
    }

    public Collection<ObjectInstance> getActiveObjects() {
        rebuildActiveObjectCaches();
        return cachedActiveObjects;
    }

    /**
     * ROM-parity slot dereference: returns the object-pointer-table id byte of
     * the object currently occupying SST slot {@code slot}, or {@code -1} when
     * no live object occupies it.
     *
     * <p>This models the 68000 pointer arithmetic in S2
     * {@code TailsCPU_CheckDespawn} / {@code TailsCPU_UpdateObjInteract}
     * (docs/s2disasm/s2.asm:39409-39419,39435-39446) and the S3K analogue
     * {@code sub_13EFC} (docs/skdisasm/sonic3k.asm:26816-26833):
     * {@code a3 = Object_RAM + interact(a0)*object_size}; {@code id(a3)} is the
     * byte at the slot. When ROM {@code DeleteObject} (s2.asm:30324-30339) has
     * zeroed the slot, {@code id(a3)} reads {@code 0}; the engine has no
     * persistent zeroed bytes, so an empty engine slot returns {@code -1} here.
     * The sidekick despawn comparator maps that {@code -1} back to ROM id
     * {@code 0} (an emptied/deleted slot is a real id change, not "slot
     * unchanged"), so an off-screen {@code DeleteObject} of the ridden object
     * fires {@code TailsCPU_Despawn} exactly as ROM does
     * (s2.asm:39403-39429). A still-loaded same-id object returns its real id
     * and matches the snapshot, deferring to the off-screen respawn timer.
     */
    public int objectIdInSlot(int slot) {
        if (slot < 0) {
            return -1;
        }
        for (ObjectInstance instance : getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance aoi
                    && aoi.getSlotIndex() == slot
                    && !instance.isDestroyed()
                    && instance.getSpawn() != null) {
                return instance.getSpawn().objectId() & 0xFF;
            }
        }
        return -1;
    }

    /**
     * Read-only snapshot of every live dynamic-slot occupant as {@code slot ->
     * (spawn.objectId() & 0xFF)}. This is the bulk analogue of
     * {@link #objectIdInSlot(int)}: it walks the same {@link #getActiveObjects()}
     * scan and applies the same liveness predicate (non-destroyed,
     * spawn-backed), but restricts the result to managed dynamic slots
     * ({@link ObjectSlotLayout#isDynamicSlot(int)}) so it lines up with the
     * SST window the {@link SlotAllocator} owns.
     *
     * <p>It is the engine side of the comparison-only occupancy oracle's
     * extra-occupant detection: a slot present here but absent from the ROM
     * trace timeline means the engine kept an object loaded that the ROM had
     * already unloaded (the MTZ off-screen-unload failure mode). The returned
     * map is freshly built and never aliases internal state, so callers cannot
     * mutate manager state through it.
     */
    public java.util.Map<Integer, Integer> occupiedDynamicSlotIds() {
        java.util.Map<Integer, Integer> occupancy = new java.util.HashMap<>();
        for (ObjectInstance instance : getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance aoi
                    && !instance.isDestroyed()
                    && instance.getSpawn() != null) {
                int slot = aoi.getSlotIndex();
                if (slotLayout.isDynamicSlot(slot)) {
                    occupancy.put(slot, instance.getSpawn().objectId() & 0xFF);
                }
            }
        }
        return occupancy;
    }

    public List<ObjectInstance> snapshotPersistentDynamicObjectsForTransition() {
        List<ObjectInstance> snapshot = new ArrayList<>();
        for (ObjectInstance instance : dynamicObjects) {
            if (instance == null || instance.isDestroyed() || !instance.isPersistent()) {
                continue;
            }
            // ROM Load_Level clears Dynamic_object_RAM, so a boss object group does
            // not survive a level reload. Boss component children report persistent
            // only so they survive the off-screen cull during the fixed-arena fight
            // (see AbstractBossChild.isPersistent); they must NOT ride a seamless act
            // reload. Carrying them strands them un-offset in the new act — concretely
            // the placed AIZ1 miniboss cutscene is dropped on the AIZ1->AIZ2 fire
            // reload while its persistent body/arm/flame-barrel children were carried,
            // leaving an art-less (invisible) body and still-hurting flame barrels
            // partway through AIZ2.
            if (instance instanceof BossChildComponent) {
                continue;
            }
            snapshot.add(instance);
        }
        return snapshot;
    }

    /**
     * Runs post-player hooks for legacy object-order modules after the main
     * playable update has completed for the current frame.
     */
    public void runPostPlayerHooks(PlayableEntity player, int frameCounter) {
        if (player == null) {
            return;
        }
        // Snapshot the pre-hook population into a reused scratch list so hooks
        // can mutate the active set without ConcurrentModification while the
        // per-frame copy allocation is avoided. Cleared in finally; the scratch
        // never escapes this method.
        List<ObjectInstance> snapshot = postPlayerHooksScratch;
        snapshot.clear();
        snapshot.addAll(getActiveObjects());
        try {
            for (ObjectInstance instance : snapshot) {
                if (instance == null || instance.isDestroyed()) {
                    continue;
                }
                if (instance instanceof PostPlayerUpdateHook hook) {
                    hook.updatePostPlayer(frameCounter, player);
                }
            }
        } finally {
            postPlayerHooksScratch.clear();
        }
    }

    /**
     * Applies a ROM-style level-repeat coordinate shift to active level-space
     * objects. S3K MHZ uses this during the forced-scroll loop when the camera
     * wraps by $200 and the ROM's helper scans dynamic object RAM for objects
     * with {@code render_flags} bit 2 set.
     */
    public void applyLevelRepeatOffsetToActiveObjects(int offsetX, int offsetY) {
        List<ObjectInstance> snapshot = new ArrayList<>(getActiveObjects());
        for (ObjectInstance instance : snapshot) {
            if (instance == null || instance.isDestroyed() || !instance.participatesInLevelRepeatOffset()) {
                continue;
            }
            instance.applyLevelRepeatOffset(offsetX, offsetY);
        }
    }

    List<ObjectInstance> getSolidProviderObjects() {
        rebuildActiveObjectCaches();
        return cachedSolidProviderObjects;
    }

    List<ObjectInstance> getTouchResponseObjects() {
        rebuildActiveObjectCaches();
        return collisionResponseList.touchResponseObjects(cachedTouchResponseObjects);
    }

    boolean touchUsesPreviousCollisionResponseList() { return collisionResponseList.usesPrevious(); }

    private void captureCollisionResponseListForNextFrame(int cameraX) {
        rebuildActiveObjectCaches();
        collisionResponseList.captureForNextFrame(cameraX, cachedTouchResponseObjects);
    }

    private void rebuildActiveObjectCaches() {
        if (activeObjectsCacheDirty) {
            cachedActiveObjects.clear();
            cachedActiveObjects.addAll(activeObjects.values());
            cachedActiveObjects.addAll(dynamicObjects);
            // ReactToItem and SolidObject scan in SST slot order; slotless objects sort last.
            cachedActiveObjects.sort((a, b) -> {
                int slotA = a instanceof AbstractObjectInstance aoiA ? aoiA.getSlotIndex() : Integer.MAX_VALUE;
                int slotB = b instanceof AbstractObjectInstance aoiB ? aoiB.getSlotIndex() : Integer.MAX_VALUE;
                return Integer.compare(slotA, slotB);
            });
            cachedSolidProviderObjects.clear();
            cachedTouchResponseObjects.clear();
            for (ObjectInstance instance : cachedActiveObjects) {
                if (instance instanceof SolidObjectProvider) {
                    cachedSolidProviderObjects.add(instance);
                }
                if (instance instanceof TouchResponseProvider) {
                    cachedTouchResponseObjects.add(instance);
                }
            }
            activeObjectsCacheDirty = false;
        }
    }

    public int getFrameCounter() {
        return frameCounter;
    }

    public int getActiveObjectSlotCount() {
        return slotAllocator.activeCount();
    }

    public int getPeakObjectSlotCount() {
        return slotAllocator.peakSlotCount();
    }

    public int getObjectSlotCapacity() {
        return execOrder.length;
    }

    public Collection<ObjectSpawn> getActiveSpawns() {
        return placement.getActiveSpawns();
    }

    public List<ObjectSpawn> getAllSpawns() {
        return placement.getAllSpawns();
    }

    /**
     * Test seam: drive a standalone load/trim-windowing {@link ObjectPlacementController} (native
     * 320px viewport) under the supplied {@link ObjectWindowingStrategy} through a
     * sequence of camera-X positions and return the sorted X positions of the
     * spawns active after the final step.
     * <p>
     * Exercises the real {@code spawnForward}/{@code spawnBackwardNonCounter}/
     * {@code trimLeftNonCounter}/{@code trimRightNonCounter} scan with the
     * strategy's final cursor boundaries (Task 1.4b), so a unit test can pass the
     * S2 strategy and assert the ROM <em>exclusive</em> load edge
     * ({@code spawn.x < forwardLoadEdge}) without a ROM/level harness. Keeping the
     * strategy a parameter means this shared seam stays game-agnostic.
     */
    public static int[] runWindowingScanForTest(int[] spawnXs, int[] cameraSequence,
            ObjectWindowingStrategy strategy) {
        List<ObjectSpawn> spawnList = new ArrayList<>(spawnXs.length);
        for (int x : spawnXs) {
            // respawnTracked=false so every in-window spawn re-loads on cursor
            // entry (no remembered/destroyed latch interfering with the boundary).
            spawnList.add(new ObjectSpawn(x, 0x100, 0x01, 0, 0, false, 0));
        }
        ObjectPlacementController p = new ObjectPlacementController(spawnList, () -> 320);
        p.setWindowingStrategy(strategy);
        for (int cameraX : cameraSequence) {
            p.update(cameraX);
        }
        return p.getActiveSpawns().stream()
                .mapToInt(ObjectSpawn::x)
                .sorted()
                .toArray();
    }

    public ObjectInstance getActiveObjectForRewind(ObjectSpawn spawn) {
        return activeObjects.get(spawn);
    }

    public void addDynamicObject(ObjectInstance object) {
        addDynamicObjectInternal(object, false, true);
    }

    public <T extends ObjectInstance> T createDynamicObject(Supplier<T> factory) {
        return ObjectConstructionContext.construct(objectServices, () -> {
            T object = factory.get();
            addDynamicObject(object);
            return object;
        });
    }

    public void removeDynamicObject(ObjectInstance object) {
        if (object == null) {
            return;
        }
        boolean removed = removeDynamicObjectInstance(object);
        if (!removed) {
            return;
        }
        deferredDynamicExecThisFrame.remove(object);
        if (object instanceof AbstractObjectInstance aoi) {
            int slot = aoi.getSlotIndex();
            if (isManagedDynamicSlot(slot)) {
                releaseSlot(slot);
                int execIdx = execIndexForSlot(slot);
                if (execIdx >= 0 && execIdx < execOrder.length) {
                    execOrder[execIdx] = null;
                }
                aoi.setSlotIndex(-1);
            }
        }
        object.onUnload();
        bucketsDirty = true;
        activeObjectsCacheDirty = true;
    }

    private boolean removeDynamicObjectInstance(ObjectInstance object) {
        boolean removed = dynamicObjects.remove(object);
        if (removed) {
            auxiliaryDynamicObjects.remove(object);
        }
        return removed;
    }

    /**
     * Adds a dynamic object using the slot immediately after the current exec slot when
     * called from an object's update, matching ROM AllocateObjectAfterCurrent behavior.
     */
    public void addDynamicObjectAfterCurrent(ObjectInstance object) {
        addDynamicObjectInternal(object, true, true);
    }

    public void addDynamicObjectAfterCurrentNextFrame(ObjectInstance object) {
        addDynamicObjectInternal(object, true, false);
    }

    /**
     * Adds a dynamic object using AllocateObjectAfterCurrent semantics anchored
     * to an explicit parent slot. Use when ROM code calls
     * AllocateObjectAfterCurrent from a touch/collision callback rather than
     * from this manager's current object-execution cursor.
     */
    public void addDynamicObjectAfterSlot(ObjectInstance object, int parentSlot) {
        addDynamicObjectInternal(object, true, true, parentSlot);
    }

    /**
     * Adds a dynamic object that should reserve a real SST slot immediately but
     * not execute until the next frame.
     * <p>
     * Sonic 1 ExplosionItem uses this path for spawned animal/points children:
     * the slots exist in the same frame's slot dump, but their first update
     * happens on the following ExecuteObjects pass.
     */
    public void addDynamicObjectNextFrame(ObjectInstance object) {
        addDynamicObjectInternal(object, false, false);
    }

    /**
     * Adds an engine-owned auxiliary dynamic object without allocating a ROM SST slot.
     * <p>
     * Use this only for non-standard runtime extensions that must update/render with
     * objects but are not part of the original game's object pool, such as extra
     * sidekick-only overlays. ROM-modeled children, projectiles, effects, shields, and
     * other object routines must continue through {@link #addDynamicObject(ObjectInstance)}
     * or one of the explicit slot-allocation variants so trace-visible slot pressure
     * stays faithful to the original game.
     */
    public void addAuxiliaryDynamicObject(ObjectInstance object) {
        if (object == null) {
            return;
        }
        if (object instanceof AbstractObjectInstance aoi) {
            aoi.setServices(objectServices);
            aoi.setSlotIndex(-1);
        }
        dynamicObjects.add(object);
        auxiliaryDynamicObjects.add(object);
        bucketsDirty = true;
        activeObjectsCacheDirty = true;
    }

    private void addDynamicObjectInternal(ObjectInstance object,
            boolean allocateAfterCurrent,
            boolean allowSameFrameExec) {
        addDynamicObjectInternal(object, allocateAfterCurrent, allowSameFrameExec, -1);
    }

    private void addDynamicObjectInternal(ObjectInstance object,
            boolean allocateAfterCurrent,
            boolean allowSameFrameExec,
            int explicitParentSlot) {
        if (object instanceof AbstractObjectInstance aoi) {
            aoi.setServices(objectServices);
            // ROM parity: FindFreeObj allocates an SST slot for EVERY object,
            // including children spawned by other objects (lava balls, projectiles,
            // explosion effects, etc.). Without this, child objects don't consume
            // slots in the allocator, causing subsequent OPL allocations to get lower
            // slot numbers than the ROM — shifting d7 values and breaking timing
            // gates like (v_vbla_byte + d7) & 7.
            if (aoi.getSlotIndex() < 0) {
                int slot;
                if (allocateAfterCurrent && explicitParentSlot >= 0) {
                    slot = allocateSlotAfter(explicitParentSlot);
                } else if (allocateAfterCurrent && updating && currentExecSlot >= 0) {
                    slot = allocateSlotAfter(slotIndexForExec(currentExecSlot));
                } else {
                    slot = allocateSlot();
                }
                if (slot >= 0) {
                    aoi.setSlotIndex(slot);
                } else {
                    aoi.setDestroyed(true);
                    return;
                }
            } else {
                // Pre-assigned slot (e.g. from addDynamicObjectAtSlot for badnik
                // replacement). Ensure the slot is marked as used in the allocator —
                // it may have been released when the original object was destroyed.
                int slot = aoi.getSlotIndex();
                slotAllocator.reserveOrMarkUsed(slot);
            }
        }
        assignRewindObjectId(object, object.getSpawn());
        dynamicObjects.add(object);
        if (!allowSameFrameExec && updating) {
            deferredDynamicExecThisFrame.add(object);
            if (object instanceof AbstractObjectInstance aoi2) {
                aoi2.setSkipTouchThisFrame(true);
            }
        }
        if (allowSameFrameExec && updating && object instanceof AbstractObjectInstance aoi2
                && isManagedDynamicSlot(aoi2.getSlotIndex())) {
            // ROM parity: FindFreeObj places the child directly into the SST.
            // The ExecuteObjects loop processes slots sequentially, so a child
            // at a HIGHER slot than the parent will be reached and updated in
            // the same frame. A child at a LOWER slot (already processed) won't
            // run until the next frame.
            int execIdx = execIndexForSlot(aoi2.getSlotIndex());
            if (execIdx < execOrder.length && execIdx > currentExecSlot) {
                object.snapshotPreUpdatePosition();
                aoi2.setSkipTouchThisFrame(true);
                execOrder[execIdx] = object;
            }
        }
        bucketsDirty = true;
        activeObjectsCacheDirty = true;
    }

    /**
     * Adds a dynamic object at a specific slot index.
     * ROM parity: badnik destruction changes obID in-place, keeping the SST slot.
     */
    public void addDynamicObjectAtSlot(ObjectInstance object, int slotIndex) {
        if (object instanceof AbstractObjectInstance aoi) {
            aoi.setServices(objectServices);
            aoi.setSlotIndex(slotIndex);
        }
        addDynamicObject(object);
    }

    /**
     * Allocates the next available dynamic slot index for the current game's layout.
     * Equivalent to ROM's FindFreeObj — searches from the first dynamic slot forward.
     * Returns -1 if all slots are in use (overflow).
     */
    private int allocateSlot() {
        return slotAllocator.allocate();
    }

    /**
     * Reserves the next available dynamic slot for non-ObjectInstance systems
     * that still occupy ROM SST slots. Equivalent to S3K AllocateObject
     * (sonic3k.asm:37906-37909), used by attracted rings whose logic is owned
     * by {@code RingManager} rather than {@code ObjectManager}.
     */
    public int allocateDynamicSlot() {
        return slotAllocator.allocate();
    }

    /**
     * Re-reserves a specific dynamic slot while restoring a subsystem-owned SST
     * occupant from a rewind snapshot.
     */
    public boolean reserveDynamicSlot(int slotIndex) {
        return slotAllocator.reserve(slotIndex);
    }

    /**
     * Registers a spilled lost-ring object (ROM Obj37) into the dynamic-object exec
     * loop on a slot that was <em>already reserved</em> by the caller via
     * {@link #allocateSlotAfter(int)}.
     * <p>
     * Unlike {@link #addDynamicObjectAtSlot}, this does NOT re-allocate or
     * mark-used the slot — {@code RingManager.LostRingPool} reserves the slot
     * up-front through {@code allocateSlotAfter} so the legacy {@code LostRing[]}
     * twin and the object share the same slot during the parallel cutover stage
     * (total slot consumption unchanged from today).
     * <p>
     * The ring is added to {@link #dynamicObjects} (NOT {@code activeObjects}):
     * rewind capture restores {@code dynamicObjects} entries through the
     * {@code LostRingRewindCodec} dynamic recreate path (Stage 4.1), whereas
     * {@code activeObjects} entries are restored through the placement registry —
     * a lost ring placed in {@code activeObjects} would be recreated by the wrong
     * path. {@code execOrder} is wired only when same-frame execution is needed.
     */
    public void spawnLostRingObjectAtSlot(AbstractObjectInstance ring, int reservedSlot) {
        if (ring == null) {
            return;
        }
        ring.setServices(objectServices);
        // Slot is already reserved via allocateSlotAfter — do NOT re-allocate.
        ring.setSlotIndex(reservedSlot);
        dynamicObjects.add(ring);
        if (updating && isManagedDynamicSlot(reservedSlot)) {
            int execIdx = execIndexForSlot(reservedSlot);
            if (execIdx >= 0 && execIdx < execOrder.length && execIdx > currentExecSlot) {
                ring.snapshotPreUpdatePosition();
                ring.setSkipTouchThisFrame(true);
                execOrder[execIdx] = ring;
            }
        }
        bucketsDirty = true;
        activeObjectsCacheDirty = true;
    }

    /**
     * Returns all live objects of the given concrete type, in ascending slot order.
     */
    public <T extends ObjectInstance> List<T> activeObjectsOfType(Class<T> type) {
        List<T> matches = new ArrayList<>();
        Set<ObjectInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ObjectInstance instance : activeObjects.values()) {
            if (type.isInstance(instance) && seen.add(instance)) {
                matches.add(type.cast(instance));
            }
        }
        for (ObjectInstance instance : dynamicObjects) {
            if (type.isInstance(instance) && seen.add(instance)) {
                matches.add(type.cast(instance));
            }
        }
        matches.sort((a, b) -> {
            int slotA = a instanceof AbstractObjectInstance aoiA ? aoiA.getSlotIndex() : Integer.MAX_VALUE;
            int slotB = b instanceof AbstractObjectInstance aoiB ? aoiB.getSlotIndex() : Integer.MAX_VALUE;
            return Integer.compare(slotA, slotB);
        });
        return matches;
    }

    /**
     * Test helper: reserve dynamic slots (from the front of the pool) until exactly
     * {@code freeSlots} remain free. Used by the spilled-ring atomic-allocation tests
     * to drive {@code allocateSlotAfter} into returning {@code -1} (slot exhaustion)
     * after a known number of successful ring allocations. No-op once the pool already
     * has {@code <= freeSlots} free.
     */
    public void reserveAllButNFreeSlots(int freeSlots) {
        int target = Math.max(0, freeSlots);
        while ((slotLayout.dynamicSlotCount() - slotAllocator.activeCount()) > target) {
            int slot = slotAllocator.allocate();
            if (slot < 0) {
                break;
            }
        }
    }

    /**
     * Allocates the next available dynamic slot AFTER the given parent slot.
     * Equivalent to ROM's FindNextFreeObj — used by segmented objects
     * (Caterkiller body, boss sub-parts) that spawn children into slots
     * immediately following the parent's position in the SST.
     *
     * @param parentSlot the parent object's slot index
     * @return the allocated slot index, or -1 if no slot is available
     */
    public int allocateSlotAfter(int parentSlot) {
        return slotAllocator.allocateAfter(parentSlot);
    }

    /**
     * Releases a previously allocated dynamic slot index.
     */
    private void releaseSlot(int slotIndex) {
        slotAllocator.release(slotIndex);
    }

    private void releaseSlotIfManaged(ObjectInstance instance) {
        if (instance instanceof AbstractObjectInstance aoi) {
            int slot = aoi.getSlotIndex();
            if (isManagedDynamicSlot(slot)) {
                releaseSlot(slot);
            }
        }
    }

    /**
     * Releases a dynamic slot that was reserved outside the normal object lifecycle.
     * Used by systems such as spilled lost rings that occupy SST slots without
     * existing as {@link ObjectInstance} entries in this manager.
     */
    public void releaseDynamicSlot(int slotIndex) {
        if (!isManagedDynamicSlot(slotIndex)) {
            return;
        }
        releaseSlot(slotIndex);
        int execIdx = execIndexForSlot(slotIndex);
        if (execIdx >= 0 && execIdx < execOrder.length) {
            execOrder[execIdx] = null;
        }
    }

    /**
     * Enables counter-based respawn tracking (S1 v_objstate system).
     */
    public void enableCounterBasedRespawn() {
        placement.enableCounterBasedRespawn();
    }

    /**
     * Enables ROM object-order semantics for games that do not use the S1
     * counter table: ExecuteObjects runs first, then ObjPosLoad materializes
     * newly streamed spawns for the following frame.
     */
    public void enableExecThenLoadPlacement() {
        placement.enableExecThenLoadPlacement();
    }

    /**
     * Enables the permanent destroy-latch on {@code destroyedInWindow}, matching
     * S3K's ROM behavior where bit 7 of {@code Object_respawn_table} stays set
     * for the rest of the level after a player kill (see
     * {@link ObjectPlacementController#permanentDestroyLatch}).
     * <p>
     * Call this once at level setup when the active game module is S3K. S1 and
     * S2 must NOT enable it because their ROM only latches respawn-tracked
     * spawns (modeled by the engine's {@code remembered} flag); non-tracked
     * spawns must be allowed to re-spawn on cursor re-entry.
     */
    public void enablePermanentDestroyLatch() {
        placement.enablePermanentDestroyLatch();
    }

    /**
     * Adjusts the ObjectPlacementController system's tracking state after a camera wrap-back.
     * <p>
     * ROM parity: when Level_repeat_offset is non-zero, the ROM's ObjPosLoad
     * adjusts its cursor boundaries by the wrap distance so that the forward/backward
     * scan sees a continuous camera motion instead of a discontinuous jump.
     * Without this adjustment, the engine's ObjectPlacementController system detects a negative
     * camera delta, triggering a full {@code refreshWindow()} that can re-spawn
     * objects already in the scene (e.g., the AIZ2 end boss during the bombing
     * sequence camera loop).
     *
     * @param wrapDelta the positive distance the camera was moved backward
     */
    public void adjustPlacementTrackingForWrap(int wrapDelta) {
        placement.adjustForWrap(wrapDelta);
    }

    /**
     * Enables slot limit enforcement (ROM FindFreeObj simulation).
     */
    public void enforceSlotLimit() {
        placement.enforceSlotLimit(this::getDynamicObjectCount);
    }

    private int getDynamicObjectCount() {
        return dynamicObjects.size() - auxiliaryDynamicObjects.size();
    }

    /**
     * Releases this object's own slot back to the pool while keeping the object
     * alive. Used by objects (e.g., ChainedStomper) that need to continue monitoring
     * children after their parent slot should be reusable.
     * <p>
     * ROM parity: In the ROM, each ring calls DeleteObject individually after
     * Ring_Sparkle completes. The parent ring's SST slot is freed when collected,
     * but the obj25 continues monitoring children until they too complete.
     */
    public void releaseSlot(ObjectInstance object) {
        if (object instanceof AbstractObjectInstance aoi) {
            int slot = aoi.getSlotIndex();
            if (isManagedDynamicSlot(slot)) {
                releaseSlot(slot);
                // Clear execOrder so the slot can be reused
                int execIdx = execIndexForSlot(slot);
                if (execIdx >= 0 && execIdx < execOrder.length) {
                    execOrder[execIdx] = null;
                }
                // Clear the object's slot index to prevent double-release.
                // Without this, the object would be placed back into execOrder
                // on the next frame (line ~239), and when eventually destroyed
                // or unloaded, would release the slot AGAIN — potentially
                // corrupting a different object that reused the slot number.
                // With slotIndex=-1, the object falls through to the fallback
                // activeObjects loop for continued updates outside the managed slot window.
                aoi.setSlotIndex(-1);
            }
        }
    }

    /**
     * Releases an object's parent slot independently from any child slots.
     * <p>
     * ROM parity: In S1, Ring_Delete → DeleteObject frees the parent ring's SST
     * slot independently from child rings. The parent and children are separate
     * SST entries with independent lifecycles. This method releases the parent's
     * slot, allowing the object to continue running slotlessly to manage remaining
     * child lifecycles.
     *
     * @param instance the object whose parent slot should be released
     */
    public void releaseParentSlot(AbstractObjectInstance instance) {
        int slot = instance.getSlotIndex();
        if (isManagedDynamicSlot(slot)) {
            releaseSlot(slot);
            instance.setSlotIndex(-1);
        }
    }

    /**
     * Frees a reserved child slot for an object that used getReservedChildSlotCount().
     * <p>
     * ROM parity: In S1, each child object calls DeleteObject after its sequence
     * completes, freeing its SST slot. This method replicates that slot release
     * for objects using the reserved child slot mechanism (e.g., ChainedStomper).
     *
     * @param spawn the parent object's spawn (used as key for the child slot array)
     * @param index the child index (0-based) to free
     */
    public void freeReservedChildSlot(ObjectSpawn spawn, int index) {
        int[] childSlots = reservedChildSlots.get(spawn);
        if (childSlots != null && index >= 0 && index < childSlots.length) {
            int slot = childSlots[index];
            if (isManagedDynamicSlot(slot)) {
                releaseSlot(slot);
                childSlots[index] = -1; // Mark as freed
            }
        }
    }

    /**
     * Adds a dynamic child object using a pre-allocated reserved slot.
     * <p>
     * ROM parity: when ring parent objects spawn children during ExecuteObjects,
     * those children must occupy the same slot numbers allocated during the
     * parent's update via {@link #allocateChildSlots(ObjectSpawn, int)}.
     * This method places the child into the pre-allocated slot at {@code childIndex},
     * replacing the phantom reservation with a real object.
     *
     * @param object      the child object to add
     * @param parentSpawn the parent's spawn (key for the reserved slot table)
     * @param childIndex  which reserved child slot to use (0-based)
     */
    public void addDynamicObjectToReservedSlot(ObjectInstance object, ObjectSpawn parentSpawn, int childIndex) {
        int[] childSlots = reservedChildSlots.get(parentSpawn);
        if (childSlots != null && childIndex >= 0 && childIndex < childSlots.length) {
            int reservedSlot = childSlots[childIndex];
            if (isManagedDynamicSlot(reservedSlot)) {
                if (object instanceof AbstractObjectInstance aoi) {
                    aoi.setServices(objectServices);
                    aoi.setSlotIndex(reservedSlot);
                    // Slot is already marked used in the allocator from pre-allocation;
                    // no need to call allocateSlot() again.
                }
                // Mark slot as consumed in the reservation table so freeAllReservedChildSlots
                // won't double-free this slot (the real child object now owns it).
                childSlots[childIndex] = -1;
                dynamicObjects.add(object);
                if (updating) {
                    int execIdx = execIndexForSlot(reservedSlot);
                    if (execIdx >= 0 && execIdx < execOrder.length && execIdx > currentExecSlot) {
                        object.snapshotPreUpdatePosition();
                        execOrder[execIdx] = object;
                    }
                }
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
                return;
            }
        }
        // Fallback: no pre-allocated slot, use normal allocation.
        // Record a consumed sentinel in the reservation table so that
        // subsequent calls won't attempt to allocate phantom slots for this
        // parent in later frames.
        if (childSlots == null) {
            reservedChildSlots.put(parentSpawn, new int[]{-1});
        }
        addDynamicObject(object);
    }

    /**
     * Allocates reserved child slots for an object during the exec loop.
     * <p>
     * ROM parity: In S1, Ring_Main runs during ExecuteObjects and allocates
     * child ring slots via FindFreeObj. This method should be called from
     * the object's first update() to match the ROM's allocation timing.
     * ObjPosLoad allocates parent slots BEFORE ExecuteObjects runs, but
     * child slots are allocated DURING ExecuteObjects.
     *
     * @param spawn the parent object's spawn
     * @param childCount number of child slots to allocate
     * @return the allocated slot indices (may contain -1 for failed allocations)
     */
    public int[] allocateChildSlots(ObjectSpawn spawn, int childCount) {
        // Guard: if already allocated, return existing
        int[] existing = reservedChildSlots.get(spawn);
        if (existing != null) {
            return existing;
        }
        int[] childSlots = new int[childCount];
        for (int c = 0; c < childCount; c++) {
            childSlots[c] = allocateSlot();
        }
        reservedChildSlots.put(spawn, childSlots);
        return childSlots;
    }

    /**
     * Allocates child slots starting from the given parent slot, matching ROM's
     * FindNextFreeObj which scans from the parent's slot forward. Used by objects
     * like CStom_MakeParts that create children in slots adjacent to the parent.
     *
     * @param spawn      the parent's ObjectSpawn (for tracking)
     * @param childCount number of child slots to allocate
     * @param parentSlot the parent object's slot index
     * @return the allocated slot indices (may contain -1 for failed allocations)
     */
    public int[] allocateChildSlotsAfter(ObjectSpawn spawn, int childCount, int parentSlot) {
        int[] childSlots = new int[childCount];
        int lastSlot = parentSlot;
        for (int c = 0; c < childCount; c++) {
            childSlots[c] = allocateSlotAfter(lastSlot);
            if (childSlots[c] >= 0) {
                lastSlot = childSlots[c]; // Next child starts after this one
            }
        }
        reservedChildSlots.put(spawn, childSlots);
        return childSlots;
    }

    /**
     * Returns peak dynamic slot count seen during this level.
     */
    public int getPeakSlotCount() {
        return slotAllocator.peakSlotCount();
    }

    public int getAllocatedSlotCount() {
        return slotAllocator.activeCount();
    }

    public int getLastDynamicSlotExclusive() {
        return slotLayout.lastDynamicSlotExclusive();
    }

    public int getLastProcessSlotExclusive() {
        return slotLayout.lastProcessSlotExclusive();
    }

    public boolean preallocatesLostRingOwnerSlot() {
        return slotLayout.preallocatesLostRingOwnerSlot();
    }

    /**
     * Frees all reserved child slots for a given spawn, removing the tracking entry.
     * Called when the parent object is destroyed or unloaded.
     */
    private void freeAllReservedChildSlots(ObjectSpawn spawn) {
        int[] childSlots = reservedChildSlots.remove(spawn);
        if (childSlots != null) {
            for (int slot : childSlots) {
                if (isManagedDynamicSlot(slot)) {
                    releaseSlot(slot);
                }
            }
        }
    }

    /**
     * Initializes the VBla frame counter to match ROM's v_vbla_byte at trace start.
     */
    public void initVblaCounter(int initialValue) {
        this.vblaCounter = initialValue;
    }

    /**
     * Advances the VBla counter by one, mirroring ROM's v_vbla_byte increment.
     */
    public void advanceVblaCounter() {
        this.vblaCounter++;
    }

    /**
     * Returns the current VBla counter value.
     */
    public int getVblaCounter() {
        return vblaCounter;
    }

    public boolean isRemembered(ObjectSpawn spawn) {
        return placement.isRemembered(spawn);
    }

    public boolean isDormant(ObjectSpawn spawn) {
        return placement.isDormant(spawn);
    }

    public int getSpawnCounter(ObjectSpawn spawn) {
        return placement.getCounterForSpawn(spawn);
    }

    public boolean isSpawnStateBitSet(ObjectSpawn spawn, int bit) {
        return placement.isCounterStateBitSet(spawn, bit);
    }

    public void setSpawnStateBit(ObjectSpawn spawn, int bit) {
        placement.setCounterStateBit(spawn, bit);
    }

    public void clearSpawnCounterActiveBit(ObjectSpawn spawn) {
        placement.clearCounterForSpawn(spawn);
    }

    public void markRemembered(ObjectSpawn spawn) {
        // Look up the instance to check if it should stay active.
        // activeObjects is an IdentityHashMap so try identity first.
        ObjectInstance instance = activeObjects.get(spawn);
        if (instance == null) {
            // Fallback: scan by equals() in case the caller's spawn reference
            // differs from the canonical key stored in the IdentityHashMap.
            for (Map.Entry<ObjectSpawn, ObjectInstance> entry : activeObjects.entrySet()) {
                if (entry.getKey().equals(spawn)) {
                    instance = entry.getValue();
                    break;
                }
            }
        }
        if (instance != null) {
            placement.markRemembered(spawn, instance);
        } else {
            placement.markRemembered(spawn);
        }
    }

    public void clearRemembered() {
        placement.clearRemembered();
    }

    /**
     * Removes a spawn from the active set without marking it as remembered.
     * The spawn can still respawn when the camera leaves and re-enters the area.
     * Used for badniks which should respawn on camera re-entry but not immediately.
     */
    public void removeFromActiveSpawns(ObjectSpawn spawn) {
        placement.removeFromActive(spawn);
    }

    /** Is this player riding any object? */
    public boolean isRidingObject(PlayableEntity player) {
        return solidContacts.isRidingObject(player);
    }

    /** Is this specific player riding this specific object? */
    public boolean isRidingObject(PlayableEntity player, ObjectInstance instance) {
        return solidContacts.isPlayerRiding(player, instance);
    }

    /** Returns whether this object currently owns ROM's standing bit for the player. */
    public boolean hasObjectStandingBit(PlayableEntity player, ObjectInstance instance) {
        return solidContacts.hasStandingLatch(player, instance);
    }

    /** Is ANY player riding anything? */
    public boolean isAnyPlayerRiding() {
        return solidContacts.isAnyPlayerRiding();
    }

    /** Is ANY player riding this specific object? */
    public boolean isAnyPlayerRiding(ObjectInstance instance) {
        return solidContacts.isAnyPlayerRiding(instance);
    }

    public boolean isActiveObjectInstance(ObjectInstance instance) {
        return instance != null && activeObjects.containsValue(instance);
    }

    /** Clear this player's riding state. */
    public void clearRidingObject(PlayableEntity player) {
        solidContacts.clearRidingObject(player);
    }

    /**
     * One-time native bootstrap for route starts that begin with the player
     * already riding a ROM solid object.
     *
     * <p>This must only be used before the first replay/gameplay frame. It
     * publishes the same standing/riding state that SolidObject would have
     * established during the title-card object prelude; it does not copy
     * per-frame trace comparison data into the engine.
     */
    public void forceRidingObjectForBootstrap(PlayableEntity player, ObjectInstance instance) {
        solidContacts.forceRidingObjectForBootstrap(player, instance);
    }

    /**
     * Clear riding state for Sonic_Jump's status-bit release.
     * <p>
     * Most engine ride records can be removed immediately. Objects whose ROM
     * routine still applies {@code MvSonicOnPtfm2} after {@code ExitPlatform}
     * keep the record until their own inline checkpoint consumes the release.
     */
    public void clearRidingObjectForJump(PlayableEntity player) {
        solidContacts.clearRidingObjectForJump(player);
    }

    public void forceAirOnStaleObjectSupportLoss(PlayableEntity player) {
        solidContacts.forceAirOnStaleObjectSupportLoss(player);
    }

    public boolean hasPendingStaleObjectSupportLoss(PlayableEntity player) {
        return solidContacts.hasPendingStaleObjectSupportLoss(player);
    }

    /**
     * Preserves a non-solid object's ownership of the player's on-object state for the
     * current inline ExecuteObjects pass.
     * <p>
     * Some ROM objects (for example S2 Obj06 spiral) set {@code status.player.on_object}
     * without going through SolidObject. Inline solid cleanup must not clear that state at
     * frame end, so these objects can mark the player as supported for the current pass.
     */
    public void markObjectSupportThisFrame(PlayableEntity player) {
        solidContacts.markObjectSupportThisFrame(player);
    }

    /**
     * Get the object that this player is currently standing on (riding).
     * Used for balance detection at object edges.
     *
     * @param player The player to check
     * @return The object being ridden, or null if not standing on any object
     */
    public ObjectInstance getRidingObject(PlayableEntity player) {
        return solidContacts.getRidingObject(player);
    }

    /**
     * Get the piece index this player is riding on a multi-piece object, or -1.
     * Used for balance detection at piece edges (e.g., CPZ Staircase).
     */
    public int getRidingPieceIndex(PlayableEntity player) {
        return solidContacts.getRidingPieceIndex(player);
    }

    public boolean hasStandingContact(PlayableEntity player) {
        return solidContacts.hasStandingContact(player);
    }

    public int getHeadroomDistance(PlayableEntity player, int hexAngle) {
        return solidContacts.getHeadroomDistance(player, hexAngle);
    }

    public boolean latestStandingSnapshot(PlayableEntity player) {
        return solidContacts.latestStandingSnapshot(player);
    }

    public int latestHeadroomSnapshot(PlayableEntity player, int hexAngle) {
        return solidContacts.latestHeadroomSnapshot(player, hexAngle);
    }

    /**
     * Run solid contacts resolution for a player sprite.
     * This is called by the CollisionSystem as part of the unified collision pipeline.
     */
    public void updateSolidContacts(PlayableEntity player) {
        solidContacts.setDeferSideToPostMovement(false);
        solidContacts.update(player, false);
    }

    /**
     * Update solid contacts with an explicit post-movement flag. When {@code postMovement}
     * is true, the velocity classification adjustment is skipped because the player's
     * position already reflects their velocity (movement has already happened).
     * <p>Used by the S1 UNIFIED model where solid objects run AFTER Sonic's movement,
     * unlike S2/S3K where solid contacts run before movement.
     *
     * @param deferSideToPostMovement when true, side collision effects (speed zeroing,
     *     position correction) are skipped in this pass because a post-movement pass will
     *     handle them. This is used for the pre-movement pass in S1 UNIFIED, where the ROM
     *     processes solid objects AFTER Sonic's movement — side collisions at the pre-movement
     *     position are spurious.
     */
    public void updateSolidContacts(PlayableEntity player, boolean postMovement,
                                     boolean deferSideToPostMovement) {
        solidContacts.setDeferSideToPostMovement(deferSideToPostMovement);
        solidContacts.update(player, postMovement);
    }

    /**
     * Resolves a newly-created ROM helper's inline solid checkpoint immediately.
     * <p>
     * Some S3K event routines allocate an object from a background/event path after
     * this engine's normal object pass has already run for the frame, while the ROM
     * object still executes its {@code SolidObjectTop} call in that same frame. The
     * AIZ transition floor is allocated at docs/skdisasm/sonic3k.asm:104685-104687,
     * then its object routine calls {@code SolidObjectTop} at 104777-104790.
     */
    public void processImmediateInlineSolidCheckpoint(ObjectInstance object,
            PlayableEntity player, List<? extends PlayableEntity> sidekicks) {
        if (object == null) {
            return;
        }
        List<? extends PlayableEntity> activeSidekicks = sidekicks != null ? sidekicks : List.of();
        solidContacts.processCompatibilityCheckpoint(object, player, activeSidekicks, true);
    }

    /**
     * Refresh the ObjectSolidContactController riding tracking position for an object after it has moved itself.
     * Prevents the delta from that movement being double-applied to riding players.
     */
    public void refreshRidingTrackingPosition(ObjectInstance object) {
        solidContacts.refreshRidingTrackingPosition(object);
    }

    /**
     * Pre-contact player X speed, captured before solid contact resolution zeroes it.
     * ROM: objects save player velocity BEFORE SolidObjectFull (e.g. Obj_AIZLRZEMZRock $30(a0)).
     */
    public short getPreContactXSpeed() { return solidContacts.getPreContactXSpeed(); }

    /** Pre-contact player Y speed. */
    public short getPreContactYSpeed() { return solidContacts.getPreContactYSpeed(); }

    /** Pre-contact player rolling state, before landing clears it. */
    public boolean getPreContactRolling() { return solidContacts.getPreContactRolling(); }

    /** Pre-contact player animation ID, before solid contact resolution can change it. */
    public int getPreContactAnimationId() { return solidContacts.getPreContactAnimationId(); }

    public TouchResponseDebugState getTouchResponseDebugState() {
        return touchResponses != null ? touchResponses.getDebugState() : null;
    }

    boolean hadSpecialTouchThisFrame(PlayableEntity player) {
        return touchResponses != null && touchResponses.hadSpecialTouchThisFrame(player);
    }

    /**
     * Phase 1 of spawn window sync for non-counter placement.
     * <p>
     * For S2/S3K-style ObjectPlacementController, {@code activeSpawns} only tracks the current
     * ObjPosLoad candidate window. Live objects are retired by their own
     * RememberState/out_of_range-equivalent code paths during execution, not by
     * leaving the spawn candidate set.
     */
    private void syncActiveSpawnsUnload() {
        // Intentionally empty. For non-counter ObjectPlacementController, live objects are kept
        // alive until their own execution tail path retires them.
        /*
        Iterator<Map.Entry<ObjectSpawn, ObjectInstance>> iterator = activeObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ObjectSpawn, ObjectInstance> entry = iterator.next();
            ObjectSpawn spawn = entry.getKey();
            ObjectInstance instance = entry.getValue();

            boolean removedFromPlacement = !activeSpawns.contains(spawn);
            // ROM parity: do NOT run a second out-of-range check here for S1.
            // The ROM only checks out_of_range during ExecuteObjects (each
            // object's own RememberState call). The centralized check here
            // was causing 20+ extra unloads during camera backtracking because
            // it runs BEFORE objects execute — objects that would survive the
            // ROM's single check (by having moved closer to Sonic) get killed
            // before they get a chance to update their position this frame.
            boolean outOfRange = false;

            if ((removedFromPlacement || outOfRange) && !instance.isPersistent()) {
                // Release the SST slot so it can be reused by future objects
                if (instance instanceof AbstractObjectInstance aoi) {
                    int slot = aoi.getSlotIndex();
                    if (slot >= 0) {
                        releaseSlot(slot);
                    }
                }
                // Also release any reserved child slots for this spawn
                freeAllReservedChildSlots(spawn);
                // ROM parity: RememberState clears bit 7 when the object
                // actually unloads (goes off-screen). This is the engine's
                // equivalent — the ObjectInstance is being released because
                // it left the screen. Destroyed objects are handled separately
                // (removeFromActive path) and never reach here, so their bits
                // stay set.
                if (counterBased) {
                    placement.clearCounterForSpawn(spawn);
                }
                if (outOfRange) {
                    placement.removeFromActiveForUnload(spawn);
                }
                instance.onUnload();
                instanceToSpawn.remove(instance);
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
        */
    }

    /**
     * Frees slots of dynamic objects explicitly marked as destroyed.
     * <p>
     * Called after {@link #syncActiveSpawnsUnload()} to release slots held by
     * dynamic children whose parent's {@code onUnload()} set them to destroyed.
     * This is a targeted pass — only objects with {@code isDestroyed() == true}
     * are freed, matching the ROM's ExecuteObjects behavior where these objects
     * would delete themselves before ObjPosLoad runs.
     */
    private void cleanupDestroyedDynamicObjects() {
        boolean changed = false;
        Iterator<ObjectInstance> iter = dynamicObjects.iterator();
        while (iter.hasNext()) {
            ObjectInstance inst = iter.next();
            if (inst.isDestroyed()) {
                if (inst instanceof AbstractObjectInstance aoi) {
                    int slot = aoi.getSlotIndex();
                    if (slot >= 0) {
                        releaseSlot(slot);
                    }
                }
                inst.onUnload();
                solidContacts.evictLatchForDestroyedInstance(inst);
                iter.remove();
                changed = true;
            }
        }
        if (changed) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
    }

    /**
     * Pure-function limit for the viewport-scaled {@code out_of_range} variant:
     * {@code 128 (behind-camera) + viewportWidth (screen) + 192 (ahead)}.
     * <p>
     * At native viewport width (320 px, {@code DISPLAY_ASPECT = NATIVE_4_3}) this
     * returns exactly {@code 640}, reproducing the ROM {@code cmpi.w #$280,d0}
     * constant bit-for-bit.  At widescreen widths the limit widens with the
     * configured viewport so objects near the visible right edge are not
     * incorrectly despawned (declared divergence — see
     * docs/KNOWN_DISCREPANCIES.md "Object Despawn and Visibility Windows", entry #14).
     * <p>
     * Used by {@link #isOutOfRangeS1} for non-S1-counter placement and mirrored
     * in {@link AbstractObjectInstance#isInRange()}.
     */
    private static final int S1_NATIVE_OUT_OF_RANGE_LIMIT = 128 + 320 + 192;

    static int outOfRangeLimit(int viewportWidth) {
        return 128 + viewportWidth + 192;
    }

    /**
     * ROM parity: S1 {@code out_of_range} macro (Macros.asm line 261).
     * <p>
     * Computes unsigned 16-bit distance between object and screen position:
     * <pre>
     *   d0 = obX & 0xFF80
     *   d1 = (v_screenposx - 128) & 0xFF80
     *   distance = (d0 - d1) & 0xFFFF   (unsigned 16-bit)
     *   out_of_range when distance > limit  (bhi = unsigned greater)
     * </pre>
     * S1 counter-based placement uses the ROM's fixed limit
     * {@code 128 + 320 + 192}; other legacy paths keep the viewport-scaled
     * declared divergence so objects near a wider visible right edge are not
     * incorrectly despawned (see KNOWN_DISCREPANCIES.md entry #14
     * "Object Despawn and Visibility Windows").
     * Catches both left (negative wraps to large unsigned) and right out of range.
     */
    private boolean isOutOfRangeS1(int objX, int cameraX) {
        int objRounded = objX & 0xFF80;
        int screenRounded = (cameraX - 128) & 0xFF80;
        int distance = (objRounded - screenRounded) & 0xFFFF;
        int limit = placement.isCounterBasedRespawn()
                ? S1_NATIVE_OUT_OF_RANGE_LIMIT
                : outOfRangeLimit(camera.getWidth());
        return distance > limit;
    }

    /**
     * Shared out-of-range delete decision used by the standard (non-custom)
     * unload path.
     * <p>
     * S2 routes the per-instance off-screen unload through the ROM object-side
     * {@code MarkObjGone} window (the injected {@link ObjectWindowingStrategy},
     * base {@code (Camera_X_pos - $80) & $FF80}, first deleting bucket {@code $300};
     * docs/s2disasm/s2.asm MarkObjGone). S1/S3K use the {@link ObjectWindowingStrategy#LEGACY}
     * strategy and keep the S1 {@code out_of_range} macro ({@link #isOutOfRangeS1}).
     * The two share the same reference X.
     * <p>
     * <b>Coordinate semantics:</b> ROM {@code MarkObjGone} (and {@code out_of_range})
     * read {@code x_pos(a0)} — the object's ROM centre X. Both branches consume
     * {@link #outOfRangeReferenceX(ObjectInstance, ObjectSpawn)} (the object's
     * explicit ROM reference X, defaulting to its centre-aligned {@code getX()}),
     * never a sprite top-left bound, so the window is not shifted by half-width.
     */
    private boolean isObjectOutOfRange(ObjectInstance instance, ObjectSpawn spawn, int cameraX) {
        int referenceX = outOfRangeReferenceX(instance, spawn);
        if (windowingStrategy.overridesUnloadWindow()) {
            return windowingStrategy.isOutsideUnloadWindow(referenceX, cameraX);
        }
        return isOutOfRangeS1(referenceX, cameraX);
    }

    /**
     * ROM parity dispatcher for the destroy-from-active path.
     *
     * <p>When an object self-destroys via an off-screen check
     * (Sprite_OnScreen_Test family in sonic3k.asm -- see loc_1B5A0 at
     * sonic3k.asm:37271), ROM clears bit 7 of the respawn-table entry
     * ({@code bclr #7,(a2)} at sonic3k.asm:37275) so the ObjectPlacementController system
     * can re-spawn the object when the camera returns. The engine mirrors
     * this by routing those destroys to {@link ObjectPlacementController#removeFromActiveForUnload}
     * which leaves {@code destroyedInWindow} cleared.
     *
     * <p>All other destroy reasons (player kills via Touch_EnemyNormal /
     * Obj_Explosion, monitor breaks, etc.) latch through
     * {@link ObjectPlacementController#removeFromActive} so {@code permanentDestroyLatch}
     * (S3K) can lock the spawn out for the rest of the level. This matches
     * ROM's loc_1BA40 / loc_1BA64 pattern where bit 7 stays set after
     * routing through {@code Delete_Current_Sprite} without going through
     * Sprite_OnScreen_Test.
     */
    private void dispatchDestroyRemoveFromActive(ObjectInstance instance, ObjectSpawn spawn) {
        if (instance.isDestroyedRespawnable()) {
            placement.removeFromActiveForUnload(spawn);
        } else {
            if (slotLayout == ObjectSlotLayout.SONIC_2 && spawn != null && spawn.respawnTracked()) placement.markRemembered(spawn);
            placement.removeFromActive(spawn);
            solidContacts.evictLatchForDestroyedSpawn(spawn);
        }
    }

    private boolean unloadCounterBasedOutOfRange(ObjectInstance instance, ObjectSpawn spawn,
            int expectedSlotIndex, int cameraX) {
        ObjectSpawn positionSpawn = spawn != null ? spawn : instance.getSpawn();
        // Fallback dynamic children may exist briefly without any spawn-backed
        // identity. They still need update() calls, but cannot participate in
        // S1's out_of_range unload check.
        if (positionSpawn == null) {
            return false;
        }
        boolean persistent;
        try {
            persistent = instance.isPersistent();
        } catch (NullPointerException e) {
            throw new IllegalStateException(describeCounterBasedUnloadObject(instance, spawn), e);
        }
        boolean outOfRange = instance.usesCustomOutOfRangeCheck()
                ? instance.isCustomOutOfRange(cameraX)
                : isObjectOutOfRange(instance, spawn, cameraX);
        if (persistent || !outOfRange) {
            return false;
        }
        if (instance instanceof AbstractObjectInstance aoi) {
            int slotIndex = aoi.getSlotIndex();
            if (isManagedDynamicSlot(slotIndex)
                    && (expectedSlotIndex < 0 || slotIndex == expectedSlotIndex)) {
                releaseSlot(slotIndex);
            }
        }
        instance.onUnload();
        if (spawn != null) {
            freeAllReservedChildSlots(spawn);
            boolean clearsRespawnState = instance.clearsRespawnStateOnCounterBasedOutOfRange();
            if (clearsRespawnState) {
                placement.clearCounterForSpawn(spawn);
                // ROM parity: RememberState clears bit 7 but the cursor may
                // still be between this spawn's entry and the current window
                // edge, so keep it dormant until ObjPosLoad reprocesses it.
                placement.markDormant(spawn);
            } else {
                // ROM parity: direct DeleteObject tails skip RememberState,
                // leaving ObjPosLoad's bset-tested counter bit latched. Remove
                // the stale placement entry so the later bset-skip cannot be
                // materialized by syncActiveSpawnsLoad.
                placement.forgetCounterForSpawn(spawn);
                placement.removeFromActivePreservingCounterState(spawn);
            }
            removeActiveObject(spawn);
        } else {
            removeDynamicObjectInstance(instance);
        }
        return true;
    }

    private int outOfRangeReferenceX(ObjectInstance instance, ObjectSpawn spawn) {
        try {
            // ROM parity: most objects feed obX(a0) to out_of_range, but some
            // S1 objects store an alternate anchor/origin in objoff_30/32/3A.
            // Use the object's explicit ROM reference X when provided.
            return instance.getOutOfRangeReferenceX();
        } catch (NullPointerException e) {
            throw new IllegalStateException(describeCounterBasedUnloadObject(instance, spawn), e);
        }
    }

    private static String describeCounterBasedUnloadObject(ObjectInstance instance, ObjectSpawn spawn) {
        StringBuilder sb = new StringBuilder("Counter-based out_of_range check failed for ");
        sb.append(instance.getClass().getName());
        if (instance instanceof AbstractObjectInstance aoi) {
            sb.append(" slot=").append(aoi.getSlotIndex());
            sb.append(" name=").append(aoi.getName());
        }
        sb.append(" managerSpawn=");
        if (spawn == null) {
            sb.append("null");
        } else {
            sb.append(String.format("0x%04X,0x%04X id=0x%02X",
                    spawn.x() & 0xFFFF, spawn.y() & 0xFFFF, spawn.objectId() & 0xFF));
        }
        ObjectSpawn instanceSpawn = instance.getSpawn();
        sb.append(" instanceSpawn=");
        if (instanceSpawn == null) {
            sb.append("null");
        } else {
            sb.append(String.format("0x%04X,0x%04X id=0x%02X",
                    instanceSpawn.x() & 0xFFFF, instanceSpawn.y() & 0xFFFF, instanceSpawn.objectId() & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Phase 2 of spawn window sync: load new objects from the ObjectPlacementController window.
     * <p>
     * ROM parity: ObjPosLoad runs AFTER ExecuteObjects. By loading new objects
     * after the exec loop, children allocated during the exec loop (Ring_Main,
     * CStom_Main, etc.) get slots BEFORE new ObjectPlacementController objects, matching the
     * ROM's slot assignment order. Objects loaded here don't execute until the
     * next frame, matching ROM timing where ObjPosLoad objects first run during
     * the following frame's ExecuteObjects.
     */
    private void syncActiveSpawnsLoad(boolean allowVerticalLoadBypassForS2) {
        Collection<ObjectSpawn> activeSpawns = placement.getActiveSpawns();
        boolean changed = false;

        if (slotLayout.twoAxisCursorPlacement()) {
            // S3K Load_Sprites advances the X cursor before the Camera_Y pass
            // (sonic3k.asm:37640-37656, 37675-37758). X-pass entries use the
            // broad camera-Y band from loc_1B7F2/loc_1BA92; the later Y pass
            // runs only when Camera_Y_pos_coarse changes and scans the one
            // newly exposed chunk strip (sonic3k.asm:37545-37588, 37723-37771).
            int previousYCoarse = twoAxisCameraYCoarse;
            int currentYCoarse = currentCameraYCoarseForTwoAxisPlacement();
            if (previousYCoarse == Integer.MIN_VALUE) {
                previousYCoarse = currentYCoarse;
            }
            for (ObjectSpawn spawn : placement.drainPendingCursorLoadSpawns()) {
                changed |= tryLoadPlacementSpawn(spawn, allowVerticalLoadBypassForS2);
            }
            if (currentYCoarse != previousYCoarse) {
                for (ObjectSpawn spawn : placement.getDeferredVerticalLoadSpawns()) {
                    changed |= tryLoadPlacementSpawnForTwoAxisYPass(spawn, previousYCoarse, currentYCoarse);
                }
            }
            twoAxisCameraYCoarse = currentYCoarse;
            if (changed) {
                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
            return;
        }

        // ROM parity: OPL/ObjectManager forward scans process entries
        // left-to-right (a0 += 6), while backward scans process right-to-left
        // (a0 -= 6). Preserve that direction so FindFreeObj assigns the same
        // slot numbers the ROM would have used.
        // Reused scratch list + cached comparators: no constructor runs from
        // this loop ever re-enters syncActiveSpawnsLoad, and the list is
        // cleared in finally so it never leaks past this call.
        List<ObjectSpawn> sortedNewSpawns = newSpawnsScratch;
        sortedNewSpawns.clear();
        for (ObjectSpawn spawn : activeSpawns) {
            if (!activeObjects.containsKey(spawn)
                    && !(placement.isRemembered(spawn) && !placement.isStayActive(spawn))
                    && !placement.isDormant(spawn)) {
                sortedNewSpawns.add(spawn);
            }
        }
        if (forwardSpawnOrder == null) {
            forwardSpawnOrder = Comparator
                    .comparingInt(ObjectSpawn::x)
                    .thenComparingInt(placement::getSpawnIndex);
            backwardSpawnOrder = Comparator
                    .comparingInt(ObjectSpawn::x)
                    .reversed()
                    .thenComparing(Comparator.comparingInt(placement::getSpawnIndex).reversed());
        }
        sortedNewSpawns.sort(
                placement.isLastScrollBackward() ? backwardSpawnOrder : forwardSpawnOrder);

        // Allocate parent slots for all new objects (matching ObjPosLoad).
        // ROM parity: ObjPosLoad assigns one slot per object in X order.
        // Pre-allocate each parent slot BEFORE running the constructor, so that
        // if the constructor spawns children (e.g., GlassBlock's reflection),
        // getSlotIndex() returns the correct value and allocateSlotAfter()
        // gives the child a HIGHER slot (matching ROM's FindNextFreeObj).
        try {
            for (ObjectSpawn spawn : sortedNewSpawns) {
                if (!isSpawnVerticallyEligibleForLoad(spawn, allowVerticalLoadBypassForS2)) {
                    if (slotLayout.twoAxisCursorPlacement()) {
                        placement.markDeferredVerticalLoad(spawn);
                    }
                    continue;
                }
                // Pre-allocate parent slot — consumed by AbstractObjectInstance's
                // constructor via the PRE_ALLOCATED_SLOT ThreadLocal.
                int preSlot = allocateSlot();
                ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                        () -> registry != null ? registry.create(spawn) : null);
                if (instance != null) {
                    if (instance instanceof AbstractObjectInstance aoi) {
                        aoi.setServices(objectServices);
                        // Slot already set by constructor via PRE_ALLOCATED_SLOT.
                        // Ensure it's set (defensive, in case constructor didn't consume it).
                        if (aoi.getSlotIndex() < 0 && preSlot >= 0) {
                            aoi.setSlotIndex(preSlot);
                        }
                        // ROM: S1 OPL_MakeItem stores the counter value as
                        // obRespawnNo for RememberState to use on unload.
                        if (placement.isCounterBasedRespawn()) {
                            int counter = placement.getCounterForSpawn(spawn);
                            if (counter >= 0) {
                                aoi.setRespawnStateIndex(counter);
                            }
                        }
                    } else {
                        // Non-AbstractObjectInstance: release pre-allocated slot
                        if (preSlot >= 0) {
                            releaseSlot(preSlot);
                        }
                    }
                    registerActiveObject(spawn, instance);
                    changed = true;
                } else {
                    // Creation failed: release pre-allocated slot
                    if (preSlot >= 0) {
                        releaseSlot(preSlot);
                    }
                }
            }
        } finally {
            newSpawnsScratch.clear();
        }

        if (changed) {
            bucketsDirty = true;
            activeObjectsCacheDirty = true;
        }
    }

    private int currentCameraYCoarseForTwoAxisPlacement() {
        return camera != null ? (camera.getY() & 0xFF80) : 0;
    }

    private boolean tryLoadPlacementSpawn(ObjectSpawn spawn, boolean allowVerticalLoadBypassForS2) {
        if (spawn == null
                || activeObjects.containsKey(spawn)
                || (placement.isRemembered(spawn) && !placement.isStayActive(spawn))
                || placement.isDormant(spawn)) {
            return false;
        }
        if (!isSpawnVerticallyEligibleForLoad(spawn, allowVerticalLoadBypassForS2)) {
            if (slotLayout.twoAxisCursorPlacement()) {
                placement.markDeferredVerticalLoad(spawn);
            }
            return false;
        }
        // Pre-allocate parent slot — consumed by AbstractObjectInstance's
        // constructor via the PRE_ALLOCATED_SLOT ThreadLocal.
        int preSlot = allocateSlot();
        if (preSlot < 0) {
            return false;
        }
        ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                () -> registry != null ? registry.create(spawn) : null);
        if (instance != null) {
            if (instance instanceof AbstractObjectInstance aoi) {
                aoi.setServices(objectServices);
                if (aoi.getSlotIndex() < 0 && preSlot >= 0) {
                    aoi.setSlotIndex(preSlot);
                }
                if (placement.isCounterBasedRespawn()) {
                    int counter = placement.getCounterForSpawn(spawn);
                    if (counter >= 0) {
                        aoi.setRespawnStateIndex(counter);
                    }
                }
            } else if (preSlot >= 0) {
                releaseSlot(preSlot);
            }
            registerActiveObject(spawn, instance);
            placement.clearDeferredVerticalLoad(spawn);
            return true;
        }
        if (preSlot >= 0) {
            releaseSlot(preSlot);
        }
        return false;
    }

    private boolean tryLoadPlacementSpawnForTwoAxisYPass(ObjectSpawn spawn, int previousYCoarse, int currentYCoarse) {
        if (spawn == null
                || activeObjects.containsKey(spawn)
                || (placement.isRemembered(spawn) && !placement.isStayActive(spawn))
                || placement.isDormant(spawn)) {
            return false;
        }
        if (!isSpawnVerticallyEligibleForTwoAxisYPass(spawn, previousYCoarse, currentYCoarse)) {
            placement.markDeferredVerticalLoad(spawn);
            return false;
        }
        int preSlot = allocateSlot();
        if (preSlot < 0) {
            return false;
        }
        ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                () -> registry != null ? registry.create(spawn) : null);
        if (instance != null) {
            if (instance instanceof AbstractObjectInstance aoi) {
                aoi.setServices(objectServices);
                if (aoi.getSlotIndex() < 0 && preSlot >= 0) {
                    aoi.setSlotIndex(preSlot);
                }
                if (placement.isCounterBasedRespawn()) {
                    int counter = placement.getCounterForSpawn(spawn);
                    if (counter >= 0) {
                        aoi.setRespawnStateIndex(counter);
                    }
                }
            } else if (preSlot >= 0) {
                releaseSlot(preSlot);
            }
            registerActiveObject(spawn, instance);
            placement.clearDeferredVerticalLoad(spawn);
            return true;
        }
        if (preSlot >= 0) {
            releaseSlot(preSlot);
        }
        return false;
    }

    private boolean isSpawnVerticallyEligibleForTwoAxisYPass(ObjectSpawn spawn,
            int previousYCoarse, int currentYCoarse) {
        if (spawn == null || currentYCoarse == previousYCoarse) {
            return false;
        }

        int bandTop;
        int bandBottom;
        if (currentYCoarse > previousYCoarse) {
            bandTop = currentYCoarse + 0x180;
            bandBottom = bandTop + 0x80;
        } else {
            bandTop = currentYCoarse - 0x80;
            bandBottom = currentYCoarse;
        }

        int spawnY = spawn.rawYWord() & 0x0FFF;
        int wrapRange = camera != null && camera.isVerticalWrapEnabled()
                ? camera.getVerticalWrapRange()
                : 0;
        if (wrapRange > 0 && (short) (camera != null ? camera.getMinY() : 0) < 0) {
            int wrapMask = wrapRange - 1;
            bandTop &= wrapMask;
            bandBottom &= wrapMask;
            return bandTop <= bandBottom
                    ? spawnY >= bandTop && spawnY <= bandBottom
                    : spawnY >= bandTop || spawnY <= bandBottom;
        }

        if (bandTop < 0) {
            return false;
        }
        return spawnY >= bandTop && spawnY <= bandBottom;
    }


        private boolean isSpawnVerticallyEligibleForLoad(ObjectSpawn spawn, boolean allowVerticalLoadBypassForS2) {
            if (spawn == null || placement.isCounterBasedRespawn() || camera == null) {
                return true;
            }
            if (skipVerticalSpawnLoadFilterForGame && allowVerticalLoadBypassForS2) {
                return true;
            }
            // S2 ObjectsManager_GoingForward/Backward calls ChkLoadObj
            // directly after the X-window scan and has no Camera_Y_pos
            // filter (docs/s2disasm/s2.asm:32870-32950). SCZ depends on
            // this: high-Y badniks are spawned while the scripted camera is
            // still at y=$0000, then survive until the Tornado route
            // descends into them.
            int wrapRange = camera.isVerticalWrapEnabled() ? camera.getVerticalWrapRange() : 0;
            return isNonCounterSpawnVerticallyEligible(spawn, camera.getY(), camera.getMinY(), wrapRange);
        }

        static boolean isNonCounterSpawnVerticallyEligible(ObjectSpawn spawn, int cameraY, int cameraMinY) {
            return isNonCounterSpawnVerticallyEligible(spawn, cameraY, cameraMinY, 0);
        }

        static boolean isNonCounterSpawnVerticallyEligible(ObjectSpawn spawn, int cameraY, int cameraMinY,
                int verticalWrapRange) {
            if ((spawn.rawYWord() & 0x8000) != 0) {
                return true;
            }

            int cameraChunkY = cameraY & 0xFF80;
            int windowTop = cameraChunkY - 0x80;
            int windowBottom = cameraChunkY + 0x200;
            int spawnY = spawn.rawYWord() & 0x0FFF;

            if ((short) cameraMinY < 0) {
                // ROM: Load_Sprites selects loc_1BA92 (normal range) while the
                // vertical load band is inside Screen_Y_wrap_value+1, and selects
                // loc_1BA40 (split range) only when the band crosses the wrap
                // boundary (sonic3k.asm:37546-37589, 37803-37843,
                // 37846-37874). Negative Camera_min_Y_pos does not mean
                // "load every Y"; MGZ1 F667 depends on the 0x0834 bridge staying
                // unloaded while the camera band is 0x0580..0x0800.
                int wrapRange = verticalWrapRange > 0 ? verticalWrapRange : 0x1000;
                int wrapMask = wrapRange - 1;
                if (windowTop < 0) {
                    return spawnY >= (windowTop & wrapMask) || spawnY <= windowBottom;
                }
                if (windowBottom > wrapRange) {
                    return spawnY >= windowTop || spawnY <= (windowBottom & wrapMask);
                }
                return spawnY >= windowTop && spawnY <= windowBottom;
            }

            windowTop = Math.max(0, windowTop);
            return spawnY >= windowTop && spawnY <= windowBottom;
        }

    /**
     * Enables vertical wrap Y adjustment on GraphicsManager if the camera has
     * vertical wrapping active. Called before object rendering to ensure objects
     * on the "wrong side" of a wrap boundary render at correct screen positions.
     */
    private void enableVerticalWrapIfNeeded() {
        if (camera.isVerticalWrapEnabled()) {
            graphicsManager.enableVerticalWrapAdjust(camera.getVerticalWrapRange(), camera.getY());
        }
    }

    private void registerActiveObject(ObjectSpawn spawn, ObjectInstance instance) {
        activeObjects.put(spawn, instance);
        instanceToSpawn.put(instance, spawn);
        assignRewindObjectId(instance, spawn);
    }

    private void removeActiveObject(ObjectSpawn spawn) {
        ObjectInstance removed = activeObjects.remove(spawn);
        if (removed != null) {
            instanceToSpawn.remove(removed);
            // Prune the live-map so rewindObjectIds stays lean during normal play.
            // (Not strictly required — stale entries are harmless since rewindCaptureContext
            //  only iterates activeObjects/dynamicObjects, but trimming prevents unbounded growth.)
            rewindObjectIds.remove(removed);
        }
    }

    private void clearActiveObjects() {
        activeObjects.clear();
        instanceToSpawn.clear();
    }

    /**
     * Assigns a stable rewind identity to {@code instance} the first time it enters
     * the live object set. The id encodes the spawn's layout index plus the current
     * {@link #dynamicObjectIdCounter} value (incremented after assignment), making
     * it unique within a session and re-mintable in the same order on re-simulation.
     *
     * <p>If {@code spawn} is {@code null} (some internally-constructed dynamic objects
     * have no spawn), no id is assigned — the object will not appear in the identity
     * table and any object-reference fields pointing to it will encode as {@code null}.
     */
    private void assignRewindObjectId(ObjectInstance instance, ObjectSpawn spawn) {
        if (spawn == null) {
            return;
        }
        if (!rewindObjectIds.containsKey(instance)) {
            SpawnRefId spawnRef = SpawnRefId.fromSpawn(spawn);
            rewindObjectIds.put(instance, ObjectRefId.forObject(spawnRef, dynamicObjectIdCounter++));
        }
    }

    private void populateDynamicFallbackScratch() {
        dynamicFallbackScratch.clear();
        for (ObjectInstance inst : dynamicObjects) {
            if (deferredDynamicExecThisFrame.contains(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi
                    && isManagedDynamicSlot(executionSlotIndex(aoi))) {
                continue;
            }
            dynamicFallbackScratch.add(inst);
        }
    }

    private void populateActiveFallbackScratch() {
        activeFallbackScratch.clear();
        for (ObjectInstance inst : activeObjects.values()) {
            if (inst instanceof AbstractObjectInstance aoi
                    && isManagedDynamicSlot(executionSlotIndex(aoi))) {
                continue;
            }
            activeFallbackScratch.add(inst);
        }
    }

    private void updateCameraBounds() {
        int left = camera.getX();
        int top = camera.getY();
        int right = left + camera.getWidth();
        int bottom = top + camera.getHeight();
        int wrapRange = camera.isVerticalWrapEnabled() ? camera.getVerticalWrapRange() : 0;
        AbstractObjectInstance.updateCameraBounds(left, top, right, bottom, wrapRange);
    }

    public static int decodePlaneSwitcherHalfSpan(int subtype) {
        return PlaneSwitchers.decodeHalfSpan(subtype);
    }

    public static boolean isPlaneSwitcherHorizontal(int subtype) {
        return PlaneSwitchers.isHorizontal(subtype);
    }

    public static int decodePlaneSwitcherPath(int subtype, int side) {
        return PlaneSwitchers.decodePath(subtype, side);
    }

    public static boolean decodePlaneSwitcherPriority(int subtype, int side) {
        return PlaneSwitchers.decodePriority(subtype, side);
    }

    public static boolean planeSwitcherGroundedOnly(int subtype) {
        return PlaneSwitchers.onlySwitchWhenGrounded(subtype);
    }

    public static char formatPlaneSwitcherLayer(byte layer) {
        return PlaneSwitchers.formatLayer(layer);
    }

    public static char formatPlaneSwitcherPriority(boolean highPriority) {
        return PlaneSwitchers.formatPriority(highPriority);
    }

    // -------------------------------------------------------------------------
    // Rewind snapshot adapter
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link com.openggf.game.rewind.RewindSnapshottable} adapter for
     * this ObjectManager.
     *
     * <p><strong>Capture</strong> records the current slot inventory (the
     * {@link SlotAllocator} occupancy BitSet as {@code long[]}), per-instance state for every active ObjectPlacementController-managed
     * object (via {@link AbstractObjectInstance#captureRewindState()}), scalar counters
     * ({@code frameCounter}, {@code vblaCounter}, {@code currentExecSlot},
     * {@code peakSlotCount}), the render-cache dirty flag, and reserved child-slot entries.
     *
     * <p><strong>Restore</strong>:
     * <ol>
     *   <li>Clears the current active object table (without triggering ObjectPlacementController state
     *       side-effects).</li>
     *   <li>Restores scalar counters and {@link SlotAllocator} occupancy.</li>
     *   <li>Reuses the live instance in place when the snapshot entry matches it on
     *       (spawn identity, slot index, class) and the class passes the
     *       non-captured-field audit ({@link #isRewindInPlaceReuseSafeClass});
     *       otherwise re-instantiates from its {@link ObjectSpawn} using the
     *       same {@link ObjectRegistry#create} pipeline used by {@code syncActiveSpawnsLoad},
     *       with the slot pre-assigned from the snapshot.</li>
     *   <li>Calls {@link AbstractObjectInstance#restoreRewindState} on each restored
     *       instance to hydrate the captured field surface.</li>
     *   <li>Restores {@code reservedChildSlots} entries.</li>
     * </ol>
     *
     * <p><strong>Holder re-resolution contract:</strong> Holders of direct
     * {@link ObjectInstance} references (Camera target, {@code LevelEventManager} boss
     * reference, etc.) <em>must</em> re-resolve their references after restore — an
     * instance may be a fresh Java object even though it represents the same logical
     * slot (in-place reuse keeps identities only for audited matching entries).
     * Camera already re-resolves its target via {@code SpriteManager} on each snapshot
     * restore (Track C). Other subsystems should do the same.
     *
     * <p>ObjectPlacementController-managed objects are restored through their original spawn.
     * Non-ObjectPlacementController dynamic objects are restored when their class has a registered
     * {@link DynamicObjectRewindCodec}; unsupported entries remain diagnostic-only.
     */
    public com.openggf.game.rewind.RewindSnapshottable<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot> rewindSnapshottable() {
        return new com.openggf.game.rewind.RewindSnapshottable<>() {
            @Override
            public String key() {
                return "object-manager";
            }

            @Override
            public com.openggf.game.rewind.snapshot.ObjectManagerSnapshot capture() {
                com.openggf.game.rewind.schema.RewindCaptureContext rewindContext = rewindCaptureContext();
                // Capture per-active-slot state
                List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry> slots = new ArrayList<>();
                for (Map.Entry<ObjectSpawn, ObjectInstance> entry : activeObjects.entrySet()) {
                    ObjectSpawn spawn = entry.getKey();
                    ObjectInstance inst = entry.getValue();
                    if (inst instanceof AbstractObjectInstance aoi) {
                        slots.add(new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry(
                                aoi.getSlotIndex(),
                                spawn,
                                aoi.getClass().getName(),
                                captureObjectRewindState(aoi, rewindContext),
                                rewindObjectIds.get(inst)
                        ));
                    }
                }
                // Memoized sort keys: a plain extractor would rebuild the
                // spawn-key string per comparison (O(n log n) string builds
                // per capture). Keys are computed once per entry; the sorted
                // order — part of snapshot determinism — is unchanged.
                Map<Object, String> slotSpawnKeys = new java.util.IdentityHashMap<>();
                slots.sort(Comparator
                        .comparingInt(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry::slotIndex)
                        .thenComparing(entry -> slotSpawnKeys.computeIfAbsent(
                                entry, e -> stableSpawnKey(entry.spawn()))));

                // Capture reservedChildSlots
                List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ChildSpawnEntry> childSpawns = new ArrayList<>();
                for (Map.Entry<ObjectSpawn, int[]> entry : reservedChildSlots.entrySet()) {
                    int[] slotArray = entry.getValue();
                    childSpawns.add(new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ChildSpawnEntry(
                            entry.getKey(),
                            Arrays.copyOf(slotArray, slotArray.length)
                    ));
                }
                Map<Object, String> childParentKeys = new java.util.IdentityHashMap<>();
                Map<Object, String> childSlotKeys = new java.util.IdentityHashMap<>();
                childSpawns.sort(Comparator
                        .comparing((com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ChildSpawnEntry entry) ->
                                childParentKeys.computeIfAbsent(
                                        entry, e -> stableSpawnKey(entry.parentSpawn())))
                        .thenComparing(entry -> childSlotKeys.computeIfAbsent(
                                entry, e -> Arrays.toString(entry.reservedSlots()))));

                List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry> dynamicEntries =
                        new ArrayList<>();
                for (ObjectInstance inst : dynamicObjects) {
                    if (auxiliaryDynamicObjects.contains(inst)) {
                        continue;
                    }
                    if (inst instanceof AbstractObjectInstance aoi) {
                        dynamicEntries.add(
                                new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry(
                                        inst.getClass().getName(),
                                        inst.getSpawn(),
                                        aoi.getSlotIndex(),
                                        captureObjectRewindState(aoi, rewindContext),
                                        playerBoundOwner(inst),
                                        rewindObjectIds.get(inst)));
                    }
                }

                long[] bits = captureOwnedUsedSlotBits();
                com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.SolidContactState solidContactState =
                        solidContacts.captureRewindState();

                // No List.copyOf wrappers here: the ObjectManagerSnapshot
                // compact constructor already copies its list components.
                return new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot(
                        bits,
                        slots,
                        frameCounter,
                        vblaCounter,
                        currentExecSlot,
                        slotAllocator.peakSlotCount(),
                        bucketsDirty,
                        childSpawns,
                        dynamicEntries,
                        placement.captureRewindState(twoAxisCameraYCoarse),
                        solidContactState.riding(),
                        solidContactState,
                        planeSwitchers != null
                                ? planeSwitchers.captureRewindState()
                                : com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot.empty(),
                        touchResponses != null
                                ? touchResponses.captureRewindState()
                                : com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.TouchResponseOverlapState.empty(),
                        dynamicObjectIdCounter
                );
            }

            @Override
            public void restore(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot s) {
                com.openggf.game.rewind.schema.RewindCaptureContext rewindContext = rewindCaptureContext();
                // 0. Index live instances by spawn identity so matching snapshot entries
                //    can reuse them in place instead of paying a full reconstruction.
                Map<ObjectSpawn, ObjectInstance> previousActive = rewindRestoreReuseScratch;
                previousActive.clear();
                if (rewindInPlaceRestoreEnabled && !activeObjects.isEmpty()) {
                    previousActive.putAll(activeObjects);
                }
                // 1. Clear current active objects (without mutating ObjectPlacementController state).
                //    Extra live objects absent from the snapshot are dropped here exactly as the
                //    pre-reuse path dropped them (reference drop, no onUnload), via not being
                //    re-registered below.
                clearActiveObjects();
                dynamicObjects.clear();
                auxiliaryDynamicObjects.clear();
                Arrays.fill(execOrder, null);
                pendingPlayerBoundEntries.clear();
                // Capture construction-spawned children produced while active objects are
                // reconstructed below, so the step-4 reconciliation loop can adopt them in
                // place instead of recreating duplicates. (See registerRewindReconstructionChild.)
                rewindReconstructionChildren.clear();
                rewindReconstructionChildCapture = true;

                // 2. Restore scalar counters and slot occupancy
                slotAllocator.restoreFromLongArray(s.usedSlotsBits());
                frameCounter = s.frameCounter();
                vblaCounter = s.vblaCounter();
                currentExecSlot = s.currentExecSlot();
                slotAllocator.restorePeakSlotCount(s.peakSlotCount());
                bucketsDirty = s.bucketsDirty();
                activeObjectsCacheDirty = true;
                // Restore the object-id counter so any new objects spawned after restore
                // (in replay or fresh gameplay) do not alias the restored objects' ids.
                dynamicObjectIdCounter = s.dynamicObjectIdCounter();
                rewindObjectIds.clear();

                // 3. Restore each captured object: reuse the live instance in place when it
                //    is provably equivalent to a fresh reconstruction, otherwise recreate
                //    from the spawn as before. The finally-clear guarantees the scratch
                //    map never retains stale instance refs past this restore, even when a
                //    factory or per-object restore throws.
                try {
                    for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry entry : s.slots()) {
                        ObjectSpawn spawn = entry.spawn();
                        int targetSlot = entry.slotIndex();
                        ObjectInstance previous = previousActive.get(spawn);
                        ObjectInstance inst;
                        if (previous instanceof AbstractObjectInstance previousAoi
                                && canReuseForRewindRestore(previousAoi, entry)) {
                            // Reused instances keep their injected services and renderer wiring.
                            inst = previous;
                        } else {
                            // Use PRE_ALLOCATED_SLOT so the constructor picks up the correct slot
                            int dynamicCountBefore = dynamicObjects.size();
                            int reservedChildBefore = reservedChildSlots.size();
                            int reconstructionChildBefore = rewindReconstructionChildren.size();
                            inst = ObjectConstructionContext.withRewindActiveRestore(
                                    () -> ObjectConstructionContext.with(objectServices, targetSlot,
                                            () -> registry != null ? registry.create(spawn) : null));
                            if (inst != null) {
                                // Constructors that spawn children or reserve child slots have
                                // restore-relevant construction side effects that in-place reuse
                                // would skip; latch those classes onto the recreate path. Under
                                // rewind restore, child spawns are routed to
                                // rewindReconstructionChildren (not dynamicObjects), so count that
                                // growth too — otherwise a child-spawning boss would look
                                // side-effect-free and wrongly become reuse-eligible.
                                boolean constructionSideEffects =
                                        dynamicObjects.size() != dynamicCountBefore
                                        || reservedChildSlots.size() != reservedChildBefore
                                        || rewindReconstructionChildren.size() != reconstructionChildBefore;
                                rewindRestoreConstructionSideEffects.merge(
                                        inst.getClass(), constructionSideEffects, Boolean::logicalOr);
                            }
                        }
                        if (inst instanceof AbstractObjectInstance aoi) {
                            aoi.setServices(objectServices);
                            if (aoi.getSlotIndex() < 0 && targetSlot >= 0) {
                                aoi.setSlotIndex(targetSlot);
                            }
                            // 4. Restore per-instance state
                            restoreObjectRewindState(aoi, entry.state(), rewindContext);
                            registerActiveObject(spawn, inst);
                            // Override the freshly-assigned rewind id with the captured one
                            // from the snapshot (stable across re-simulation ordering).
                            if (entry.objectId() != null) {
                                rewindObjectIds.put(inst, entry.objectId());
                            }
                            // Wire into execOrder if within the managed slot window
                            int execIdx = execIndexForSlot(aoi.getSlotIndex());
                            if (execIdx >= 0 && execIdx < execOrder.length) {
                                execOrder[execIdx] = aoi;
                            }
                        } else if (inst != null) {
                            registerActiveObject(spawn, inst);
                        }
                    }
                } finally {
                    previousActive.clear();
                }

                // 5. Restore reservedChildSlots
                reservedChildSlots.clear();
                for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ChildSpawnEntry ce : s.childSpawns()) {
                    reservedChildSlots.put(ce.parentSpawn(),
                            Arrays.copyOf(ce.reservedSlots(), ce.reservedSlots().length));
                }

                // Stop routing further child spawns into the reconstruction-children scratch:
                // any spawns triggered below (e.g. by a codec's recreate) are normal restore
                // wiring, not reconstruction side effects to be adopted.
                rewindReconstructionChildCapture = false;
                for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry entry
                        : s.dynamicObjects()) {
                    // Prefer adopting the construction-spawned child the reconstructed parent
                    // already created and back-references. Adopting it in place gives exact
                    // captured state AND keeps the parent's child reference valid, with no
                    // double-spawn. Routine-spawned children (no construction counterpart) fall
                    // back to the codec recreate path.
                    AbstractObjectInstance adopted = adoptRewindReconstructionChild(entry.className());
                    ObjectInstance inst = adopted != null ? adopted : recreateDynamicObject(entry);
                    if (inst instanceof AbstractObjectInstance aoi) {
                        aoi.setServices(objectServices);
                        if (adopted != null) {
                            // Adopt at the captured slot; the construction spawn did not allocate
                            // one (the slot allocator is already restored from the snapshot).
                            int slot = entry.slotIndex();
                            if (slot >= 0) {
                                aoi.setSlotIndex(slot);
                            }
                        }
                        restoreObjectRewindState(aoi, entry.state(), rewindContext);
                        // Restore the captured rewind id so the identity table built from
                        // the next rewindCaptureContext() re-uses the pre-restore id.
                        if (entry.objectId() != null) {
                            rewindObjectIds.put(aoi, entry.objectId());
                        } else {
                            assignRewindObjectId(aoi, aoi.getSpawn());
                        }
                        dynamicObjects.add(aoi);
                        int execIdx = execIndexForSlot(aoi.getSlotIndex());
                        if (execIdx >= 0 && execIdx < execOrder.length) {
                            execOrder[execIdx] = aoi;
                        }
                    }
                }
                // Any reconstruction children NOT matched by a captured entry were spawned by a
                // parent whose child was not in dynamicObjects at capture (should not happen for
                // current bosses, but drop them rather than leak: they are unregistered and the
                // parent's reference to them is replaced by the next live restore). Clearing the
                // scratch guarantees no instances leak across restore passes.
                rewindReconstructionChildren.clear();

                if (s.placement() != null) {
                    twoAxisCameraYCoarse = placement.restoreRewindState(s.placement());
                }

                solidContacts.restoreRewindState(s.solidContactState());
                if (planeSwitchers != null) {
                    planeSwitchers.restoreRewindState(s.planeSwitchers());
                }

                // ObjectTouchResponseController' double-buffer overlap state must be restored
                // AFTER object restoration so slot lookup resolves to live
                // post-restore instances. See iter-1631 root-cause analysis in
                // docs/superpowers/plans/2026-05-07-rewind-encounter-validation.md.
                if (touchResponses != null) {
                    touchResponses.restoreRewindState(s.touchResponseOverlap());
                }

                bucketsDirty = true;
                activeObjectsCacheDirty = true;
            }
        };
    }

    private static PerObjectRewindSnapshot captureObjectRewindState(AbstractObjectInstance object,
            com.openggf.game.rewind.schema.RewindCaptureContext context) {
        if (hasLegacyRewindOverride(object.getClass(), "captureRewindState")) {
            return object.captureRewindState();
        }
        return object.captureRewindState(context);
    }

    private static void restoreObjectRewindState(AbstractObjectInstance object,
            PerObjectRewindSnapshot snapshot,
            com.openggf.game.rewind.schema.RewindCaptureContext context) {
        if (hasLegacyRewindOverride(object.getClass(), "restoreRewindState",
                PerObjectRewindSnapshot.class)) {
            object.restoreRewindState(snapshot);
            return;
        }
        object.restoreRewindState(snapshot, context);
    }

    private static boolean hasLegacyRewindOverride(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> current = type;
                current != null && current != AbstractObjectInstance.class;
                current = current.getSuperclass()) {
            try {
                var method = current.getDeclaredMethod(name, parameterTypes);
                if (!Modifier.isAbstract(method.getModifiers())
                        && !method.isSynthetic()
                        && !method.isBridge()) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Keep walking toward AbstractObjectInstance.
            }
        }
        return false;
    }

    /**
     * Test hook: toggles the in-place reuse fast path of the rewind restore.
     * When disabled, every snapshot entry is destroy/recreated (the pre-reuse
     * behavior), which the in-place restore equivalence test uses as the
     * reference path.
     */
    public void setRewindInPlaceRestoreEnabledForTest(boolean enabled) {
        this.rewindInPlaceRestoreEnabled = enabled;
    }

    private boolean canReuseForRewindRestore(AbstractObjectInstance previous,
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry entry) {
        if (previous.getSlotIndex() != entry.slotIndex()) {
            return false;
        }
        Class<?> type = previous.getClass();
        if (entry.className() == null || !type.getName().equals(entry.className())) {
            return false;
        }
        // Require one observed in-restore reconstruction of this class without
        // construction side effects before reusing instances of it.
        Boolean observed = rewindRestoreConstructionSideEffects.get(type);
        if (observed == null || observed) { // null = never observed in-restore, true = has side effects
            return false;
        }
        return isRewindInPlaceReuseSafeClass(type);
    }

    private static final Set<Class<?>> REWIND_IMMUTABLE_VALUE_TYPES = Set.of(
            Boolean.class, Byte.class, Character.class, Short.class,
            Integer.class, Long.class, Float.class, Double.class, String.class);


    /**
     * Returns true when instances of {@code type} may be reused in place by the
     * rewind restore instead of destroy/recreated.
     *
     * <p>The destroy/recreate path resets every non-captured field to its
     * constructor value on each restore; in-place reuse keeps the live value.
     * The two are equivalent only when every field declared below
     * {@link AbstractObjectInstance} is either (a) captured by the default
     * capture path (restored from the snapshot on both paths) or (b) a
     * {@code final} construction-constant — an immutable value or a structural
     * reference the constructor derives deterministically (renderer handles,
     * mapping/sheet data, services). Classes with concrete capture/restore
     * overrides (unprovable hand-written coverage), non-final non-captured
     * fields (frame-coupled mutable state), or non-captured object/player
     * references, collections, or arrays (mutable or identity-coupled content)
     * fall back to recreate.
     *
     * <p>{@code AbstractObjectInstance} itself is fully covered by the base
     * snapshot record (its only non-captured members are the final
     * {@code spawn}/{@code name} and the intentionally retained
     * {@code services} handle). {@code AbstractBadnikInstance} is hand-audited:
     * its movement fields ride {@code BadnikRewindExtra}, {@code destructionConfig}
     * is a final structural config, and the three {@code cachedDebug*} fields are
     * self-correcting render caches (the label regenerates whenever
     * {@code animFrame}/{@code facingLeft} change, so a stale cache is only kept
     * when it is already correct).
     *
     * <p>Public for the in-place restore audit test.
     */
    public static boolean isRewindInPlaceReuseSafeClass(Class<?> type) {
        return REWIND_IN_PLACE_REUSE_SAFE_CLASSES.computeIfAbsent(type,
                ObjectManager::computeRewindInPlaceReuseSafeClass);
    }

    private static boolean computeRewindInPlaceReuseSafeClass(Class<?> type) {
        if (!AbstractObjectInstance.class.isAssignableFrom(type)
                || Modifier.isAbstract(type.getModifiers())
                || type.isAnnotationPresent(com.openggf.game.rewind.RewindRecreateOnRestore.class)) {
            // The annotation pins classes whose constructors have restore-relevant
            // side effects the field audit cannot see (e.g. global zone-state
            // writes) onto the destroy/recreate path.
            return false;
        }
        boolean badnik = AbstractBadnikInstance.class.isAssignableFrom(type);
        boolean defaultCapture = badnik
                ? com.openggf.game.rewind.GenericRewindEligibility.usesDefaultBadnikSubclassCapture(type)
                : com.openggf.game.rewind.GenericRewindEligibility.usesDefaultObjectSubclassCapture(type);
        if (!defaultCapture) {
            // Concrete capture/restore override: hand-written coverage is not
            // reflectively provable, so keep the recreate semantics.
            return false;
        }
        Set<java.lang.reflect.Field> captured = new HashSet<>(
                com.openggf.game.rewind.GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(type));
        for (Class<?> cls = type;
                cls != null && cls != AbstractObjectInstance.class;
                cls = cls.getSuperclass()) {
            if (cls == AbstractBadnikInstance.class) {
                continue; // hand-audited; see method Javadoc
            }
            for (java.lang.reflect.Field field : cls.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (captured.contains(field)) {
                    continue;
                }
                if (!isRewindReuseSafeNonCapturedField(field)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isRewindReuseSafeNonCapturedField(java.lang.reflect.Field field) {
        if (!Modifier.isFinal(field.getModifiers())) {
            // Mutable non-captured state: recreate resets it, reuse cannot.
            return false;
        }
        Class<?> type = field.getType();
        if (type.isPrimitive() || type.isEnum() || REWIND_IMMUTABLE_VALUE_TYPES.contains(type)) {
            return true;
        }
        if (type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)) {
            // Non-captured mutable container content.
            return false;
        }
        if (ObjectInstance.class.isAssignableFrom(type)
                || PlayableEntity.class.isAssignableFrom(type)) {
            // Cross-instance identity link; the target may be recreated by restore.
            return false;
        }
        // Final structural reference (renderer handle, sheet/mapping data,
        // services, spawn): the constructor derives it deterministically, so the
        // live value matches what a reconstruction would compute.
        return true;
    }

    private long[] captureOwnedUsedSlotBits() {
        BitSet owned = new BitSet(execOrder.length);
        for (ObjectInstance inst : activeObjects.values()) {
            markOwnedSlot(owned, inst);
        }
        for (ObjectInstance inst : dynamicObjects) {
            markOwnedSlot(owned, inst);
        }
        for (int[] slots : reservedChildSlots.values()) {
            if (slots == null) {
                continue;
            }
            for (int slot : slots) {
                if (isManagedDynamicSlot(slot)) {
                    owned.set(execIndexForSlot(slot));
                }
            }
        }
        return owned.toLongArray();
    }

    /**
     * Builds a fresh {@link com.openggf.game.rewind.schema.RewindCaptureContext} reflecting
     * the current live object + player set. The returned context's
     * {@link com.openggf.game.rewind.identity.RewindIdentityTable} maps every live object
     * to a stable {@link com.openggf.game.rewind.identity.ObjectRefId}.
     *
     * <p>This method is called internally by the capture/restore path and is also
     * exposed publicly so test harnesses can inspect the identity table without
     * triggering a full snapshot capture.
     */
    public com.openggf.game.rewind.schema.RewindCaptureContext captureIdentityContext() {
        return rewindCaptureContext();
    }

    private com.openggf.game.rewind.schema.RewindCaptureContext rewindCaptureContext() {
        com.openggf.game.rewind.identity.RewindIdentityTable table =
                new com.openggf.game.rewind.identity.RewindIdentityTable();
        com.openggf.sprites.playable.AbstractPlayableSprite main = null;
        List<PlayableEntity> sidekicks = List.of();
        if (objectServices != null) {
            var camera = objectServices.camera();
            main = camera != null ? camera.getFocusedSprite() : null;
            List<PlayableEntity> serviceSidekicks = objectServices.sidekicks();
            if (serviceSidekicks != null) {
                sidekicks = serviceSidekicks;
            }
        }
        if (main != null) {
            table.registerPlayer(main, com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
        }
        for (int i = 0; i < sidekicks.size(); i++) {
            PlayableEntity sidekick = sidekicks.get(i);
            if (sidekick == null || sidekick == main) {
                continue;
            }
            table.registerPlayer(sidekick, com.openggf.game.rewind.identity.PlayerRefId.sidekick(i));
        }
        // Register every live object (placed + dynamic) so object-reference fields can
        // be captured as stable ObjectRefIds. The id was assigned when the object entered
        // the live set (via registerActiveObject / addDynamicObjectInternal).
        for (ObjectInstance inst : activeObjects.values()) {
            ObjectRefId id = rewindObjectIds.get(inst);
            if (id != null) {
                table.registerObject(inst, id);
            }
        }
        for (ObjectInstance inst : dynamicObjects) {
            ObjectRefId id = rewindObjectIds.get(inst);
            if (id != null) {
                table.registerObject(inst, id);
            }
        }
        return com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(table);
    }

    private void markOwnedSlot(BitSet owned, ObjectInstance inst) {
        if (inst instanceof AbstractObjectInstance aoi && isManagedDynamicSlot(aoi.getSlotIndex())) {
            owned.set(execIndexForSlot(aoi.getSlotIndex()));
        }
    }

    private static String stableSpawnKey(ObjectSpawn spawn) {
        if (spawn == null) {
            return "";
        }
        return spawn.layoutIndex()
                + ":" + spawn.objectId()
                + ":" + spawn.x()
                + ":" + spawn.y()
                + ":" + spawn.subtype()
                + ":" + spawn.renderFlags()
                + ":" + spawn.rawYWord();
    }

    static boolean isRewindRestorableDynamicObject(ObjectInstance inst) {
        return isRewindRestorableDynamicObject(inst, null);
    }

    static boolean isRewindRestorableDynamicObject(ObjectInstance inst, ObjectRegistry registry) {
        return rewindDynamicObjectCodecFor(inst, registry).isPresent();
    }

    ObjectServices objectServicesForRewind() {
        return objectServices;
    }

    /**
     * Returns the game-specific {@link ObjectRegistry} for use by
     * {@link ObjectRewindDynamicCodecs#genericRecreate} during rewind restore.
     * Package-private: accessed only through {@link DynamicObjectRecreateContext#objectRegistry()}.
     */
    ObjectRegistry rewindObjectRegistry() {
        return registry;
    }

    /**
     * Enqueues a captured {@link com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry}
     * for a player-bound dynamic class whose post-restore re-spawn happens
     * after object-manager restore (currently Shield + Stars). Called by the
     * codec's recreate path.
     */
    void enqueuePendingPlayerBoundEntry(Class<?> baseType,
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry entry) {
        if (entry == null || entry.slotIndex() < 0) return;
        pendingPlayerBoundEntries
                .computeIfAbsent(baseType, k -> new java.util.ArrayDeque<>())
                .add(entry);
    }

    /**
     * Pops the next captured entry for the given base type, or returns
     * {@code null} if none is pending. Called by {@code DefaultPowerUpSpawner}
     * during the post-restore re-spawn so the freshly-constructed instance
     * lands at the captured slot AND has its captured field surface
     * (animation cursor, timers, visibility flags, etc.) reapplied via
     * {@link AbstractObjectInstance#restoreRewindState}.
     */
    public com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry
            consumePendingPlayerBoundEntry(Class<?> baseType) {
        return consumePendingPlayerBoundEntry(baseType, entry -> true);
    }

    /**
     * Pops the first captured entry for the given player-bound base type that
     * satisfies {@code matcher}. Shield restore uses this to match owner and
     * concrete class instead of consuming another player's pending same-type
     * shield from the FIFO queue.
     */
    public com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry
            consumePendingPlayerBoundEntry(Class<?> baseType,
                    Predicate<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry> matcher) {
        java.util.ArrayDeque<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry>
                queue = pendingPlayerBoundEntries.get(baseType);
        if (queue == null || queue.isEmpty()) return null;
        Iterator<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry> iterator =
                queue.iterator();
        while (iterator.hasNext()) {
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry entry = iterator.next();
            if (matcher.test(entry)) {
                iterator.remove();
                if (queue.isEmpty()) {
                    pendingPlayerBoundEntries.remove(baseType);
                }
                return entry;
            }
        }
        return null;
    }

    private static PlayableEntity playerBoundOwner(ObjectInstance inst) {
        if (inst instanceof ShieldObjectInstance shield) {
            return shield.getPlayer();
        }
        return null;
    }

    static void registerRewindDynamicObjectCodecForTest(DynamicObjectRewindCodec codec) {
        TEST_OR_MIGRATION_REWIND_DYNAMIC_OBJECT_CODECS.add(codec);
    }

    static void clearRewindDynamicObjectCodecsForTest() {
        TEST_OR_MIGRATION_REWIND_DYNAMIC_OBJECT_CODECS.clear();
    }

    private static Optional<DynamicObjectRewindCodec> rewindDynamicObjectCodecFor(
            ObjectInstance inst, ObjectRegistry registry) {
        for (DynamicObjectRewindCodec codec : TEST_OR_MIGRATION_REWIND_DYNAMIC_OBJECT_CODECS) {
            if (codec.supports(inst)) {
                return Optional.of(codec);
            }
        }
        for (DynamicObjectRewindCodec codec : SHARED_REWIND_DYNAMIC_OBJECT_CODECS) {
            if (codec.supports(inst)) {
                return Optional.of(codec);
            }
        }
        for (DynamicObjectRewindCodec codec : registryDynamicRewindCodecs(registry)) {
            if (codec.supports(inst)) {
                return Optional.of(codec);
            }
        }
        return Optional.empty();
    }

    private static Optional<DynamicObjectRewindCodec> rewindDynamicObjectCodecForClassName(
            String className, ObjectRegistry registry) {
        for (DynamicObjectRewindCodec codec : TEST_OR_MIGRATION_REWIND_DYNAMIC_OBJECT_CODECS) {
            if (codec.className().equals(className)) {
                return Optional.of(codec);
            }
        }
        for (DynamicObjectRewindCodec codec : SHARED_REWIND_DYNAMIC_OBJECT_CODECS) {
            if (codec.className().equals(className)) {
                return Optional.of(codec);
            }
        }
        for (DynamicObjectRewindCodec codec : registryDynamicRewindCodecs(registry)) {
            if (codec.className().equals(className)) {
                return Optional.of(codec);
            }
        }
        return Optional.empty();
    }

    private static List<DynamicObjectRewindCodec> registryDynamicRewindCodecs(ObjectRegistry registry) {
        return registry == null ? List.of() : registry.dynamicRewindCodecs();
    }

    private ObjectInstance recreateDynamicObject(
            com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry entry) {
        return rewindDynamicObjectCodecForClassName(entry.className(), registry)
                .map(codec -> codec.recreate(new DynamicObjectRecreateContext(this), entry))
                .orElse(null);
    }

    /**
     * Records a child constructed while an active object is being reconstructed during a
     * rewind restore. The child is NOT inserted into {@link #dynamicObjects} and is NOT given
     * a freshly allocated slot here — the slot allocator was already restored from the
     * snapshot in restore() step 2, and the child is registered at its captured slot when the
     * step-4 reconciliation loop adopts it (see {@link #adoptRewindReconstructionChild}).
     *
     * <p>Called by {@link AbstractObjectInstance#spawnChild}/{@code spawnFreeChild} only when
     * {@link ObjectConstructionContext#isRewindActiveRestore()} is true. The reconstructed
     * parent still receives this instance as its back-reference, so adopting it in place keeps
     * the parent's child references valid while giving the child its exact captured state.
     */
    public void registerRewindReconstructionChild(AbstractObjectInstance child) {
        if (child != null && rewindReconstructionChildCapture) {
            rewindReconstructionChildren.add(child);
        }
    }

    /**
     * Finds and removes the earliest pending reconstruction child whose runtime class matches
     * {@code className}. Construction order is deterministic and equals the dynamic-object
     * capture order, so consuming the matching class in first-in order pairs each captured
     * {@link com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry} with
     * the same logical child the parent re-spawned. Returns {@code null} when no construction
     * child of that class is pending (e.g. routine-spawned children, which fall back to the
     * codec recreate path).
     */
    private AbstractObjectInstance adoptRewindReconstructionChild(String className) {
        if (className == null) {
            return null;
        }
        for (java.util.Iterator<AbstractObjectInstance> it = rewindReconstructionChildren.iterator();
                it.hasNext();) {
            AbstractObjectInstance child = it.next();
            if (child.getClass().getName().equals(className)) {
                it.remove();
                return child;
            }
        }
        return null;
    }

    ObjectInstance findRestoredRidingObject(ObjectSpawn spawn, int slotIndex) {
        if (spawn != null) {
            ObjectInstance active = activeObjects.get(spawn);
            if (active != null) {
                return active;
            }
            for (ObjectInstance dynamic : dynamicObjects) {
                if (dynamic != null && dynamic.getSpawn() == spawn) {
                    return dynamic;
                }
            }
        }
        if (isManagedDynamicSlot(slotIndex)) {
            int execIdx = execIndexForSlot(slotIndex);
            if (execIdx >= 0 && execIdx < execOrder.length) {
                return execOrder[execIdx];
            }
        }
        return null;
    }


    static final class PlaneSwitchers {
        private static final Logger LOGGER = Logger.getLogger(PlaneSwitchers.class.getName());
        private static final int[] HALF_SPANS = new int[]{0x20, 0x40, 0x80, 0x100};
        private static final int MASK_SIZE = 0x03;
        private static final int MASK_HORIZONTAL = 0x04;
        private static final int MASK_PATH_SIDE1 = 0x08;
        private static final int MASK_PATH_SIDE0 = 0x10;
        private static final int MASK_PRIORITY_SIDE1 = 0x20;
        private static final int MASK_PRIORITY_SIDE0 = 0x40;
        private static final int MASK_GROUNDED_ONLY = 0x80;

        private final ObjectPlacementController placement;
        private final int objectId;
        private final PlaneSwitcherConfig config;
        private final Map<ObjectSpawn, PlaneSwitcherState> states = new HashMap<>();

        PlaneSwitchers(ObjectPlacementController placement, int objectId, PlaneSwitcherConfig config) {
            this.placement = placement;
            this.objectId = objectId & 0xFF;
            this.config = config;
        }

        void reset() {
            states.clear();
        }

        com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot captureRewindState() {
            List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherEntry> entries =
                    new ArrayList<>();
            for (Map.Entry<ObjectSpawn, PlaneSwitcherState> entry : states.entrySet()) {
                PlaneSwitcherState state = entry.getValue();
                List<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry>
                        playerSides = new ArrayList<>();
                for (Map.Entry<PlayableEntity, Byte> sideEntry : state.sideStates.entrySet()) {
                    playerSides.add(
                            new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry(
                                    sideEntry.getKey(),
                                    sideEntry.getValue() & 0xFF));
                }
                playerSides.sort(Comparator
                        .comparing((com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry e)
                                -> stablePlayerKey(e.player()))
                        .thenComparingInt(e -> e.sideState()));
                entries.add(new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherEntry(
                        entry.getKey(),
                        state.getLastSideState(),
                        state.hasLastSideState(),
                        List.copyOf(playerSides)));
            }
            entries.sort(Comparator.comparing(entry -> stableSpawnKey(entry.spawn())));
            return new com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot(
                    List.copyOf(entries));
        }

        void restoreRewindState(
                com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherSnapshot snapshot) {
            states.clear();
            if (snapshot == null) {
                return;
            }
            for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherEntry entry
                    : snapshot.entries()) {
                ObjectSpawn spawn = entry.spawn();
                if (spawn == null) {
                    continue;
                }
                PlaneSwitcherState state = new PlaneSwitcherState(decodeHalfSpan(spawn.subtype()));
                if (entry.hasLastSideState()) {
                    state.setLastSideState(entry.lastSideState());
                }
                for (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PlaneSwitcherPlayerSideEntry side
                        : entry.playerSides()) {
                    if (side.player() != null) {
                        state.setSideState(side.player(), side.sideState());
                    }
                }
                states.put(spawn, state);
            }
        }

        private static String stablePlayerKey(PlayableEntity player) {
            if (player instanceof AbstractPlayableSprite sprite) {
                return sprite.getCode();
            }
            return player == null ? "" : player.getClass().getName();
        }

        void update(PlayableEntity player) {
            if (placement == null || player == null || config == null) {
                return;
            }
            Collection<ObjectSpawn> active = placement.getActiveSpawns();
            if (active.isEmpty()) {
                return;
            }

            int playerX = player.getCentreX();
            int playerY = player.getCentreY();

            for (ObjectSpawn spawn : active) {
                if (spawn.objectId() != objectId) {
                    continue;
                }

                int subtype = spawn.subtype();
                PlaneSwitcherState state = states.computeIfAbsent(spawn,
                        key -> new PlaneSwitcherState(decodeHalfSpan(subtype)));

                boolean horizontal = isHorizontal(subtype);
                int sideNow = horizontal
                        ? (playerY >= spawn.y() ? 1 : 0)
                        : (playerX >= spawn.x() ? 1 : 0);
                int previousSide = state.getSideState(player);
                if (previousSide < 0) {
                    state.setSideState(player, sideNow);
                    state.setLastSideState(sideNow);
                    continue;
                }

                int half = state.halfSpanPixels;
                boolean inSpan = horizontal
                        ? (playerX >= spawn.x() - half && playerX < spawn.x() + half)
                        : (playerY >= spawn.y() - half && playerY < spawn.y() + half);
                boolean groundedGate = onlySwitchWhenGrounded(subtype) && player.getAir();

                if (inSpan && !groundedGate && sideNow != previousSide) {
                    boolean skipCollisionChange = (spawn.renderFlags() & 0x1) != 0;
                    if (!skipCollisionChange) {
                        int path = decodePath(subtype, sideNow);
                        player.setLayer((byte) path);
                        if (path == 0) {
                            player.setTopSolidBit(config.getPath0TopSolidBit());
                            player.setLrbSolidBit(config.getPath0LrbSolidBit());
                        } else {
                            player.setTopSolidBit(config.getPath1TopSolidBit());
                            player.setLrbSolidBit(config.getPath1LrbSolidBit());
                        }
                        LOGGER.fine(() -> String.format(
                                "PlaneSwitcher path=%d: player(%d,%d) obj(%d,%d) sub=0x%02X side=%d→%d air=%b mode=%s",
                                path, player.getCentreX(), player.getCentreY(),
                                spawn.x(), spawn.y(), subtype, previousSide, sideNow,
                                player.getAir(), player.getGroundMode()));
                    }
                    boolean highPriority = decodePriority(subtype, sideNow);
                    player.setHighPriority(highPriority);
                }

                state.setSideState(player, sideNow);
                state.setLastSideState(sideNow);
            }

            states.keySet().removeIf(spawn -> spawn.objectId() == objectId && !active.contains(spawn));
        }

        int getSideState(ObjectSpawn spawn) {
            PlaneSwitcherState state = states.get(spawn);
            if (state == null || !state.hasLastSideState()) {
                return -1;
            }
            return state.getLastSideState();
        }

        static int decodeHalfSpan(int subtype) {
            int index = subtype & MASK_SIZE;
            if (index < 0 || index >= HALF_SPANS.length) {
                index = 0;
            }
            return HALF_SPANS[index];
        }

        static boolean isHorizontal(int subtype) {
            return (subtype & MASK_HORIZONTAL) != 0;
        }

        static int decodePath(int subtype, int side) {
            int mask = side == 1 ? MASK_PATH_SIDE1 : MASK_PATH_SIDE0;
            return (subtype & mask) != 0 ? 1 : 0;
        }

        static boolean decodePriority(int subtype, int side) {
            int mask = side == 1 ? MASK_PRIORITY_SIDE1 : MASK_PRIORITY_SIDE0;
            return (subtype & mask) != 0;
        }

        static boolean onlySwitchWhenGrounded(int subtype) {
            return (subtype & MASK_GROUNDED_ONLY) != 0;
        }

        static char formatLayer(byte layer) {
            return layer == 0 ? 'A' : 'B';
        }

        static char formatPriority(boolean highPriority) {
            return highPriority ? 'H' : 'L';
        }

        private static final class PlaneSwitcherState {
            private final int halfSpanPixels;
            private final IdentityHashMap<PlayableEntity, Byte> sideStates = new IdentityHashMap<>();
            private byte lastSideState = 0;
            private boolean hasLastSideState = false;

            private PlaneSwitcherState(int halfSpanPixels) {
                this.halfSpanPixels = halfSpanPixels;
            }

            private int getSideState(PlayableEntity player) {
                Byte value = sideStates.get(player);
                return value != null ? value & 0xFF : -1;
            }

            private void setSideState(PlayableEntity player, int sideState) {
                sideStates.put(player, (byte) sideState);
            }

            private void setLastSideState(int sideState) {
                this.lastSideState = (byte) sideState;
                this.hasLastSideState = true;
            }

            private boolean hasLastSideState() {
                return hasLastSideState;
            }

            private int getLastSideState() {
                return lastSideState & 0xFF;
            }
        }
    }
}
