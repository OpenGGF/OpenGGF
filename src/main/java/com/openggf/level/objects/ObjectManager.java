package com.openggf.level.objects;

import com.openggf.game.ModApi;
import com.openggf.game.session.EngineServices;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.GameModule;
import com.openggf.game.rules.GameRules;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.TransitionSstOccupant;
import com.openggf.level.render.SpriteSlotEmission;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.managers.SpriteManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.IntFunction;
import java.util.function.Supplier;

@ModApi
public class ObjectManager {
    private static final int BUCKET_COUNT = RenderPriority.MAX - RenderPriority.MIN + 1;
    static final int ANIM_ROLL = 0x02;
    static final int ANIM_SPINDASH = 0x09;
    final ObjectPlacementController placement;
    private final ObjectPlacementAdmission placementAdmission;
    private final ObjectManagerInternalAccess.Streaming competitionStreaming;
    final ObjectRegistry registry;
    private final GraphicsManager graphicsManager;
    private final Camera camera;
    private final List<ObjectInstance> dynamicFallbackScratch = new ArrayList<>();
    private final List<ObjectInstance> activeFallbackScratch = new ArrayList<>();
    // Per-frame scratch collections reused to avoid steady-state allocation.
    // Ownership stays inside the populating method (cleared in finally / at the
    // single reset point); never returned to callers that retain references.
    private final Set<ObjectInstance> processedInExecLoopScratch =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final BitSet slotsFreedDuringObjectPass = new BitSet();
    private final List<PlayableEntity> activePlayersScratch = new ArrayList<>(4);
    private final List<ObjectSpawn> newSpawnsScratch = new ArrayList<>();
    // Cached spawn-order comparators (built lazily; placement is constructor-set).
    private Comparator<ObjectSpawn> forwardSpawnOrder;
    private Comparator<ObjectSpawn> backwardSpawnOrder;
    private final List<GLCommand> renderCommands = new ArrayList<>();
    private final ObjectDrawInternalAccess.Drawing objectDrawing;
    private int frameCounter;
    private int vblaCounter;
    private boolean updating;
    private final InitialObjectDispatchController initialDispatch =
            new InitialObjectDispatchController(this);
    private final ObjectExecutionController execution = new ObjectExecutionController(this);

    // ROM parity: slot-ordered execution array for ExecuteObjects emulation.
    // This covers the complete processed SST range, while SlotAllocator below
    // remains restricted to the game's independently-defined allocatable window.
    // When a child is spawned at a higher slot, it's placed here directly
    // so the ongoing loop reaches it naturally (same-frame execution).
    /**
     * Per-game object load/unload windowing boundary (shared abstraction;
     * {@link ObjectWindowingStrategy#LEGACY} for S1/S3K, the ROM-exact S2
     * strategy for S2). Injected from the {@link ObjectRegistry} so this shared
     * manager never depends on a game-specific package.
     */
    private final ObjectWindowingStrategy windowingStrategy;
    /** Objects whose ROM retirement belongs to their owned slot rather than a borrowed exec slot. */
    private final ObjectSlotRetirementController slotRetirement;
    private final int[] playerCentreXAtSlotStart;
    private final int[] playerCentreYAtSlotStart;
    private final boolean[] playerCentreAtSlotStartValid;
    private int currentExecSlot = -1; // -1 when not in update loop

    /** ROM object-loop counter for the current pass; see ObjectLoopSlotBudget. */
    private final ObjectLoopSlotBudget objectLoopBudget = new ObjectLoopSlotBudget();

    /**
     * Rewrites the remaining length of this frame's object walk from the calling
     * object's own slot, modelling a ROM routine that writes the loop counter
     * register. {@code remainingSlots} is the ROM's own number; callers cite
     * where it comes from and this method invents nothing.
     */
    public void overrideRemainingObjectLoopSlots(ObjectInstance source, int remainingSlots) {
        if (source instanceof AbstractObjectInstance aoi) objectLoopBudget.overrideFrom(
                executionSlotIndex(aoi), remainingSlots);
    }

    /**
     * Whether this frame's walk got past the managed dynamic window and so would
     * have reached the fixed-in-level slots the ROM places straight after it --
     * S2 {@code LevelOnly_Object_RAM} (docs/s2disasm/s2.constants.asm:1145-1150),
     * S3K {@code Level_object_RAM}.
     */
    public boolean objectLoopReachedFixedInLevelSlots() {
        return objectLoopBudget.reaches(population.slots.lastProcessSlotExclusive());
    }
    private final boolean skipVerticalSpawnLoadFilterForGame;
    private final ObjectSlotLayout allocationLayout;

    final ObjectServices objectServices;
    /** NATIVE widescreen draw mode's presentation-only frozen layout objects. */
    final DormantObjectLayer dormantLayer = new DormantObjectLayer(this);
    StageplayObjectDirection stageplayDirection;
    private final ObjectStageplayController stageplayController;
    final StageplayActivationPolicy stageplayActivation;
    final ObjectCallbackRouter objectCallbacks;
    private final ObjectPopulation population;
    private final ObjectRewindLifecycle rewind;
    private final NativeChildReservationLedger childReservationLedger;
    public void setRewindClassResolver(RewindClassResolver resolver) { rewind.setClassResolver(resolver); }

    // Pre-bucketed lists for O(n) rendering instead of O(n*buckets)
    @SuppressWarnings("unchecked")
    private final List<ObjectRenderParts.RenderEntry>[] lowPriorityBuckets = new ArrayList[BUCKET_COUNT];
    @SuppressWarnings("unchecked")
    private final List<ObjectRenderParts.RenderEntry>[] highPriorityBuckets = new ArrayList[BUCKET_COUNT];
    @SuppressWarnings("unchecked")
    private final List<ObjectRenderParts.RenderEntry>[] unifiedBuckets = new ArrayList[BUCKET_COUNT];
    boolean bucketsDirty = true;
    private final ObjectNativeSstWord2C nativeSstWord2C;
    private final NativeSpriteSubmissionQueue nativeSpriteSubmissions =
            new NativeSpriteSubmissionQueue(() -> bucketsDirty = true);
    // Presentation binding only. RingManager owns its live DTOs and rewind state.
    private IntFunction<SpriteSlotEmission> nativeSpriteExternalResolver;
    private NativeSpriteSubmissionAccess.ExternalSlotDispatch nativeSpriteExternalSlotDispatch;

    NativeSpriteSubmissionQueue nativeSpriteSubmissions() {
        return nativeSpriteSubmissions;
    }

    void bindNativeSpriteExternalResolver(IntFunction<SpriteSlotEmission> resolver) {
        nativeSpriteExternalResolver = resolver;
        bucketsDirty = true;
    }

    void bindNativeSpriteExternalSlotDispatch(NativeSpriteSubmissionAccess.ExternalSlotDispatch dispatch) {
        nativeSpriteExternalSlotDispatch = dispatch;
    }

    private void beginNativeSpriteFrame() {
        nativeSpriteSubmissions.beginFrame();
        if (nativeSpriteExternalSlotDispatch != null) nativeSpriteExternalSlotDispatch.beginPass();
    }

    private void dispatchExternalSpriteSlot(int cameraX, PlayableEntity player) {
        int slot = slotIndexForExec(currentExecSlot);
        // The owner lookup scans every object, so ask the dispatcher first: an empty
        // execution slot with no reserved occupant (no attracted ring) needs neither.
        if (nativeSpriteExternalSlotDispatch != null && nativeSpriteExternalSlotDispatch.mayDispatch(slot)
                && currentNativeSstOwner(slot) == null) {
            nativeSpriteExternalSlotDispatch.dispatch(slot, player, vblaCounter, cameraX);
        }
    }
    private final ObjectRenderBucketSnapshot renderBucketSnapshot =
            new ObjectRenderBucketSnapshot();

    // Cached combined active objects list to avoid allocation in getActiveObjects()
    private final List<ObjectInstance> cachedActiveObjects = new ArrayList<>();
    private final List<ObjectInstance> cachedSolidProviderObjects = new ArrayList<>();
    private final List<ObjectInstance> cachedTouchResponseObjects = new ArrayList<>();
    private final ObjectCollisionResponseList collisionResponseList = new ObjectCollisionResponseList();
    private boolean activeObjectsCacheDirty = true;
    private final NativeObjectIndex nativeObjectIndex = new NativeObjectIndex();
    final Set<ObjectInstance> deferredDynamicExecThisFrame =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final ObjectManagerRuntimeState runtimeState = new ObjectManagerRuntimeState();

    private int s2LatchedObjectManagerCameraX = Integer.MIN_VALUE;
    private int twoAxisCameraYCoarse = Integer.MIN_VALUE;
    private int vIntRunCounterPhaseOffset;

    // Position projection and native word-index/width operations publish this
    // input for later sine/cosine chain arithmetic. Capture it with the world
    // so rewind repeats the same producer and consumer ordering.
    private int sineCosineInputFraction;

    private final FixedSstObjectInstaller fixedSstObjects;
    private final ObjectPlaneSwitcherController planeSwitchers;
    private final ObjectSolidContactController solidContacts;
    private final ObjectTouchResponseController touchResponses;

    // Per-player centre Y captured at the start of the object exec pass
    // (post-physics, before any object re-seats the player this frame). See
    // captureExecStartPlayerCentreY / getPlayerCentreYAtExecStart.
    private final java.util.Map<PlayableEntity, Integer> execStartPlayerCentreY =
            new java.util.IdentityHashMap<>(2);

    public ObjectManager(List<ObjectSpawn> spawns, ObjectRegistry registry,
            int planeSwitcherObjectId, PlaneSwitcherConfig planeSwitcherConfig,
            TouchResponseTable touchResponseTable, GraphicsManager graphicsManager,
            Camera camera, ObjectServices objectServices) {
        this.registry = registry;
        this.graphicsManager = graphicsManager;
        this.camera = camera;
        this.objectServices = objectServices;
        this.stageplayActivation = objectServices == null ? null
                : com.openggf.game.session.WorldSessionPolicyAccess.getService(
                        objectServices.worldSession(), StageplayActivationPolicy.class);
        this.stageplayController = new ObjectStageplayController(this, spawns, objectServices);
        var directionPolicy =
                objectServices == null
                        ? null
                        : com.openggf.game.session.WorldSessionPolicyAccess.getService(
                                objectServices.worldSession(), StageplayObjectLoadPolicy.class);
        if (directionPolicy != null) {
            stageplayDirection = new StageplayObjectDirection(this, directionPolicy);
        }
        this.placementAdmission = new ObjectPlacementAdmission(registry, objectServices);
        this.objectCallbacks = new ObjectCallbackRouter(registry, initialDispatch);
        ObjectSlotLayout slots = registry != null ? registry.objectSlotLayout() : ObjectSlotLayout.SONIC_1;
        this.allocationLayout = registry instanceof ObjectManagerInternalAccess.AllocationSemantics semantics
                ? semantics.allocationLayout() : slots;
        this.population = new ObjectPopulation(objectCallbacks, slots,
                this::markActiveObjectCachesDirty, this::projectPublishedPopulationIdentity,
                () -> stageplayDirection);
        this.rewind = new ObjectRewindLifecycle(this, population);
        this.liveObjectQuery = ObjectInstanceQueries.liveQuery(this, population.identities);
        this.nativeSstWord2C = new ObjectNativeSstWord2C(population.slots.lastProcessSlotExclusive(), this::currentNativeSstOwner, objectCallbacks, () -> rewind.capturesConstructionChildren());
        this.windowingStrategy = registry != null
                ? registry.objectWindowingStrategy()
                : ObjectWindowingStrategy.LEGACY;
        this.placement = new ObjectPlacementController(stageplayController.sourceSpawns(),
                camera != null ? camera::getWidth : com.openggf.level.spawn.PlacementViewportWidth::current);
        this.placement.setStageplayActivation(stageplayActivation);
        this.placement.setTwoAxisCursorPlacement(population.slots.twoAxisCursorPlacement());
        this.placement.setWindowingStrategy(windowingStrategy);
        // Indexed by execution slot, which spans the process slots (next's fixed
        // SST slots sit above the dynamic range), not just the dynamic count.

        this.slotRetirement = new ObjectSlotRetirementController(population.slots.processSlotCount());
        this.playerCentreXAtSlotStart = new int[population.executionOrder.length];
        this.playerCentreYAtSlotStart = new int[population.executionOrder.length];
        this.playerCentreAtSlotStartValid = new boolean[population.executionOrder.length];
        this.competitionStreaming = new ObjectManagerInternalAccess.Streaming(placement, placementAdmission,
                registry, population.allocator, population.slots, objectServices, objectCallbacks, population.placed,
                population.spawnByInstance, population.dynamic, this::registerActiveObject, this::removeActiveObject,
                this::removeDynamicObjectInstance, this::releaseSlot, () -> {
                    bucketsDirty = true;
                    activeObjectsCacheDirty = true;
                });
        this.childReservationLedger = new NativeChildReservationLedger(objectCallbacks, population.slots,
                population.reservedChildren, population.spawnByInstance, population.placed, population.dynamic,
                population.identities::containsKey, this::managedSpawn, this::allocateSlotAfter,
                this::releaseChildSlotAtItsOwnExecPosition);
        this.fixedSstObjects = new FixedSstObjectInstaller(
                registry, objectServices, population.slots, population.allocator,
                this::addDynamicObject, this::releaseSlot);
        this.planeSwitchers = planeSwitcherConfig != null
                ? new ObjectPlaneSwitcherController(placement, planeSwitcherObjectId, planeSwitcherConfig)
                : null;
        this.solidContacts = new ObjectSolidContactController(this);
        this.touchResponses = touchResponseTable != null
                ? new ObjectTouchResponseController(this, touchResponseTable)
                : null;
        this.skipVerticalSpawnLoadFilterForGame = allocationLayout == ObjectSlotLayout.SONIC_2;
        // Initialize bucket arrays
        for (int i = 0; i < BUCKET_COUNT; i++) {
            lowPriorityBuckets[i] = new ArrayList<>();
            highPriorityBuckets[i] = new ArrayList<>();
            unifiedBuckets[i] = new ArrayList<>();
        }
        this.objectDrawing = new ObjectDrawInternalAccess.Drawing(this, graphicsManager, camera, objectServices,
                objectCallbacks, renderCommands, lowPriorityBuckets, highPriorityBuckets, unifiedBuckets,
                this::ensureBucketsPopulated, this::currentNativeSstOwner, () -> nativeSpriteExternalResolver);
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

    /**
     * Slots visited by Process_Sprites. This spans the whole process range, not
     * just the allocatable dynamic window: next's fixed SST slots sit above that
     * window and must still execute in global slot order.
     */
    private boolean isManagedDynamicSlot(int slotIndex) {
        return population.slots.isDynamicSlot(slotIndex);
    }

    void runObjectCallback(ObjectInstance instance, Runnable callback) { objectCallbacks.run(instance, callback); }

    void bindObjectServices(AbstractObjectInstance object) {
        object.setServices(objectServices);
        object.bindStageplayActivation(stageplayActivation);
    }

    <T> T callObjectCallback(ObjectInstance instance, Supplier<T> callback) {
        return objectCallbacks.call(instance, callback);
    }

    void inheritObjectCallbackOwners(ObjectManager source, Iterable<? extends ObjectInstance> instances) {
        objectCallbacks.inheritOwners(source.objectCallbacks, instances);
    }

    ObjectPopulation population() { return population; }

    ObjectCallbackRouter objectCallbacks() { return objectCallbacks; }

    InitialObjectDispatchController initialDispatch() { return initialDispatch; }

    void retireInitialExecution() {
        updating = false;
        currentExecSlot = -1;
        processedInExecLoopScratch.clear();
        deferredDynamicExecThisFrame.clear();
    }

    ObjectSpawn managedSpawn(ObjectInstance instance) {
        ObjectSpawn placed = population.spawnByInstance.get(instance);
        return placed != null ? placed
                : instance instanceof AbstractObjectInstance object ? object.managedSpawn() : null;
    }

    void registerInheritedCallbackOwner(ObjectInstance instance) {
        objectCallbacks.registerInherited(instance);
        if (objectCallbacks.owner(instance) == null) {
            rewind.classResolver().ownerOf(instance.getClass())
                    .ifPresent(owner -> objectCallbacks.registerOwnerIfAbsent(instance, owner));
        }
    }

    private boolean isProcessedSstSlot(int slotIndex) {
        return slotIndex >= 0 && slotIndex < population.slots.lastProcessSlotExclusive();
    }

    // Identity: next's fixed SST slots can sit below the dynamic window, so an
    // offset mapping would index them negatively.
    private int execIndexForSlot(int slotIndex) {
        return slotIndex;
    }

    private int executionSlotIndex(AbstractObjectInstance instance) {
        return objectCallbacks.call(instance, instance::getExecutionSlotIndex);
    }

    private int slotIndexForExec(int execIndex) {
        return execIndex;
    }

    private void indexOwnSlotRetirement(AbstractObjectInstance object) {
        int ownSlot = objectCallbacks.call(object, object::getSlotIndex);
        int execSlot = executionSlotIndex(object);
        slotRetirement.index(object, ownSlot, execSlot, isManagedDynamicSlot(ownSlot));
    }

    /** Retires a consolidated object at its native owned-slot position in the SST walk. */
    private boolean retireBorrowedExecutionSlotOwnerAtOwnSlot(int cameraX) {
        ObjectInstance owner = slotRetirement.takeOwner(currentExecSlot);
        if (owner == null) {
            return false;
        }
        if (!(owner instanceof AbstractObjectInstance object)
                || objectCallbacks.call(owner, owner::isDestroyed)) {
            return false;
        }
        int execSlot = executionSlotIndex(object);
        if (!isProcessedSstSlot(execSlot) || population.executionOrder[execIndexForSlot(execSlot)] != owner) {
            return false;
        }
        ObjectSpawn spawn = population.spawnByInstance.get(owner);
        int ownSlot = objectCallbacks.call(object, object::getSlotIndex);
        if (!unloadIfOutsideLifetimeWindow(owner, spawn, ownSlot, cameraX)) {
            return false;
        }
        population.executionOrder[execIndexForSlot(execSlot)] = null;
        return true;
    }

    public void reset(int cameraX) {
        reset(cameraX, null, true, true);
    }

    /**
     * Resets the Act-2 placement cursors without materializing their window.
     * ScreenEvents runs after Process_Sprites, so those placements first become
     * live in the next frame's ExecuteObjects pass.
     */
    public void resetForSynchronousActTransition(int cameraX) {
        reset(cameraX, null, false, true);
    }

    /**
     * Resets placement state without materializing the initial window so that
     * level-init objects written to fixed SST slots can be restored first.
     * Trace prelude setup then calls {@link #preloadInitialSpawnsForHydration()}
     * to reproduce the native Level_Init-before-ObjPosLoad allocation order.
     */
    public void resetForPreludeHydration(int cameraX) {
        reset(cameraX, null, false, true);
    }

    /**
     * Resets this manager and reapplies a persistent respawn table before the
     * initial placement window is materialized. Bonus-stage returns use this
     * atomic path so remembered layout objects cannot be created during reset
     * and left live by a later bit-only restore.
     */
    public void reset(int cameraX, PersistentRespawnState persistentRespawnState) {
        reset(cameraX, persistentRespawnState, true, true);
    }

    void reset(int cameraX, PersistentRespawnState persistentRespawnState,
            boolean materializeInitialWindow, boolean installFixedObjects) {
        initialDispatch.retireState();
        if (stageplayDirection != null) {
            stageplayDirection.close();
        }
        rewind.clearPendingPlayerBindings();
        nativeSpriteSubmissions.clear();
        // The external resolver/dispatch is owner wiring, not SST state: keep it.
        // Resets that leave the RingManager alive (trace-replay prelude, S3K
        // title-only object system) never rebind, and an unbound pass leaves
        // Obj_Attracted_Ring (sonic3k.asm:35750-35787) without its Draw_Sprite.
        // A replacement RingManager rebinds itself on construction.
        clearActiveObjects();
        population.clearDynamicForReset(objectCallbacks::clear);
        deferredDynamicExecThisFrame.clear();
        runtimeState.clearPostExecDynamicSpawns();
        population.clearNativeReservationsAndExecution();
        slotRetirement.clearOwners();
        Arrays.fill(playerCentreAtSlotStartValid, false);
        cachedActiveObjects.clear();
        nativeObjectIndex.clear();
        markActiveObjectCachesDirty();
        bucketsDirty = true;
        frameCounter = 0;
        population.restoreIdentitySequence(0);
        // A full SST reset also retires both collision buffers. They cannot
        // retain erased Java owners or alias replacement actors in reused slots.
        collisionResponseList.restoreRewindState(null, ignored -> null);
        s2LatchedObjectManagerCameraX = cameraX;
        twoAxisCameraYCoarse = Integer.MIN_VALUE;
        placement.reset(cameraX, persistentRespawnState, competitionStreaming.upfrontActive());
        if (competitionStreaming.active()) placement.resetNativeCompetitionStreaming(cameraX);
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
        if (installFixedObjects)
            ConfiguredObjectInstaller.install(fixedSstObjects, placement, this::addRewindableAuxiliaryDynamicObject);
        // Materialize the current ObjectPlacementController window immediately after reset.
        // S1 needs this for ROM parity at level start; for S2/S3K it keeps
        // manual camera resets and headless probes from sitting on an empty
        // active window until a later ObjectPlacementController delta occurs.
        // S2 ObjectsManager_Init falls through to the same X-only loading pass
        // as runtime ObjectsManager (s2.asm: ObjectsManager_Init / loc_17B3E).
        if (materializeInitialWindow) {
            syncActiveSpawnsLoad(skipVerticalSpawnLoadFilterForGame);
        }
    }

    ObjectServices services() {
        return objectServices;
    }

    void installDeferredFixedLevelObjects() {
        if (registry instanceof com.openggf.game.internal.DeferredLevelFixedObjectInstallation deferred
                && deferred.defersFixedLevelObjects())
            deferred.installDeferredFixedLevelObjects(objectServices.romZoneId(), objectServices.currentAct(), fixedSstObjects);
    }

    /** The services this manager injects into every object it owns. */
    public ObjectServices getObjectServices() {
        return objectServices;
    }

    public boolean usesTwoAxisCursorPlacement() {
        return population.slots.twoAxisCursorPlacement();
    }

    public boolean usesCounterBasedRespawn() {
        return placement.isCounterBasedRespawn();
    }

    /**
     * Captures the persistent respawn-remember state (broken/collected/remembered
     * spawns) to carry across a bonus-stage or special-stage round-trip reload. See
     * {@link PersistentRespawnState} for the ROM {@code Respawn_table_keep} model.
     */
    public PersistentRespawnState capturePersistentRespawn() {
        return placement.capturePersistentRespawn();
    }

    /**
     * Re-establishes respawn-remember state captured by
     * {@link #capturePersistentRespawn()} onto this (freshly reloaded) manager's
     * placement, so stage-return respawns broken/collected objects in the state
     * the player left them (e.g. a monitor broken before the bonus stays broken).
     */
    public void restorePersistentRespawn(PersistentRespawnState state) {
        placement.restorePersistentRespawn(state);
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

    /** Initializes Load_Sprites cursors after installing a fresh layout, retaining fixed SST owners. */
    void prepareInitialPlacementWindow(List<ObjectSpawn> spawns, int cameraX, PersistentRespawnState respawn) {
        placement.replaceSpawnsAndReset(spawns, competitionStreaming.upfrontActive());
        // Prefix-only construction had no layout; size state before restoring it.
        placement.setTwoAxisCursorPlacement(population.slots.twoAxisCursorPlacement());
        placement.reset(cameraX, respawn, competitionStreaming.upfrontActive());
        twoAxisCameraYCoarse = Integer.MIN_VALUE;
        syncActiveSpawnsLoad(skipVerticalSpawnLoadFilterForGame);
    }

    /**
     * Replaces the spawn list with a new one from the editor.
     * Clears remembered/destroyed state so edited spawns can respawn.
     * Existing active objects are cleared — {@code syncActiveSpawns()} on
     * the next frame will re-instantiate objects in the camera window.
     */
    public void resyncSpawnList(List<ObjectSpawn> newSpawns) {
        competitionStreaming.releaseUpfrontPlacementSlots();
        // Retire only discarded placed owners and their unconsumed child reservations.
        // Native competition already released each upfront placement's own SST above.
        for (var entry : population.placed.entrySet()) {
            if (!competitionStreaming.upfrontActive()) releaseSlot(entry.getValue());
            freeAllReservedChildSlots(entry.getKey());
        }
        nativeSpriteSubmissions.clear();
        // External slot wiring survives a spawn-list resync, as in reset().
        clearActiveObjects();
        cachedActiveObjects.clear();
        nativeObjectIndex.clear();
        markActiveObjectCachesDirty();
        bucketsDirty = true;
        twoAxisCameraYCoarse = Integer.MIN_VALUE;
        placement.replaceSpawnsAndReset(newSpawns, competitionStreaming.upfrontActive());
        if (planeSwitchers != null) {
            planeSwitchers.reset();
        }
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
        if (renderBucketSnapshot.inputsChanged(population.placed.values(), population.dynamic)) {
            bucketsDirty = true;
        }
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks, int touchFrameCounter) { update(cameraX, player, sidekicks, touchFrameCounter, true); }

    /**
     * Run touch responses for a single player outside the main update loop.
     * ROM order: ReactToItem runs during each player's slot within ExecuteObjects,
     * after their physics but before other objects' solid checks.
     */
    public void runTouchResponsesForPlayer(PlayableEntity player, int touchFrameCounter) {
        runTouchResponsesForPlayer(player, touchFrameCounter, false);
    }

    public PoweredAttackSurface poweredAttacks() { return new PoweredAttackSurface(touchResponses, collisionResponseList); }

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
        solidContacts.clearSameFrameMonitorBreakBounces();
        updateCameraBounds();
        collisionResponseList.setUsePrevious(preservePreviousCollisionResponseList);
        for (ObjectInstance inst : population.placed.values()) {
            refreshTouchResponseSnapshot(inst);
        }
        for (ObjectInstance inst : population.dynamic) {
            refreshTouchResponseSnapshot(inst);
        }
    }

    private void refreshTouchResponseSnapshot(ObjectInstance inst) {
        // ROM Touch_Loop stores object RAM POINTERS, not snapshots: `movea.w (a4)+,a1`
        // then `x_pos(a1)` (docs/skdisasm/sonic3k.asm:20696-20699, 20710-20717; the S1
        // ReactToItem and S2 Touch_Response forms are the same shape). Every placed
        // object sits in a slot after the player, so the player always sees each
        // object's END-OF-PREVIOUS-FRAME position.
        //
        // This runs from LevelManager.prepareTouchResponseSnapshots before any object
        // updates this frame, so the snapshot captures exactly that position for every
        // object, whatever phase its own update falls in. The S3K previous-list path
        // used to skip it, leaving the cache holding whatever the object's own last
        // update wrote -- still end-of-previous-frame for an object updated before the
        // touch pass, but one pass older for a spawnChild-created object updated after
        // it. Collision flags are unaffected: the previous-list path reads them live at
        // the touch site rather than from this cache.
        if (!ObjectDirectionAccess.suspended(this, inst))
            objectCallbacks.run(inst, inst::snapshotTouchResponseState);
    }

    /**
     * The end-of-frame BuildSprites / Render_Sprites pass: records every object's
     * {@code render_flags.on_screen} for the next frame's logic, then lets objects
     * with their own retained render state refresh it.
     */
    public void refreshPostCameraRenderState() {
        updateRenderCameraBounds();
        boolean heightPixelsTest = renderSpritesTestsHeightPixels();
        for (ObjectInstance inst : population.placed.values()) {
            refreshPostCameraRenderState(inst, heightPixelsTest);
        }
        for (ObjectInstance inst : population.dynamic) {
            refreshPostCameraRenderState(inst, heightPixelsTest);
        }
    }

    private void refreshPostCameraRenderState(ObjectInstance inst, boolean heightPixelsTest) {
        if (ObjectDirectionAccess.suspended(this, inst)) {
            return;
        }
        if (inst instanceof AbstractObjectInstance aoi) {
            objectCallbacks.run(inst, () -> aoi.captureRenderFlagOnScreen(heightPixelsTest));
        }
        objectCallbacks.run(inst, inst::refreshPostCameraRenderState);
    }

    private boolean renderSpritesTestsHeightPixels() {
        // S3K Render_Sprites always tests height_pixels; S1/S2 BuildSprites
        // assume a 32-px Y radius unless the object sets the explicit-height flag.
        // The same rule selects the playable sprite's render-flag Y test
        // (Camera.isVisibleForRenderFlag).
        GameModule module = objectServices != null ? objectServices.gameModule() : null;
        GameRules rules = module != null ? module.getRules() : null;
        return rules != null && rules.camera().useScreenYWrapValueForVisibility();
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses) {
        update(cameraX, player, sidekicks, touchFrameCounter, enableTouchResponses, false, false);
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        update(cameraX, player, sidekicks, touchFrameCounter, enableTouchResponses,
                inlineSolidResolution, solidPostMovement, null);
    }

    public void update(int cameraX, PlayableEntity player, List<? extends PlayableEntity> sidekicks,
            int touchFrameCounter, boolean enableTouchResponses,
            boolean inlineSolidResolution, boolean solidPostMovement,
            Runnable afterExecBeforePlacement) {
        List<? extends PlayableEntity> activeSidekicks = sidekicks != null ? sidekicks : List.of();
        frameCounter++;
        vblaCounter++;
        beginNativeSpriteFrame();
        // Inline-physics path: snapshotTouchResponseState() ran earlier this
        // frame and already refreshed the cached camera bounds. The second
        // call here is harmless redundancy (the post-camera-step bounds
        // haven't shifted between snapshot and update) -- it's kept so the
        // non-inline path (which doesn't snapshot) still gets fresh bounds
        // before the exec loop. Do not consolidate without verifying both
        // call paths first.
        updateCameraBounds();
        // Snapshot each player's centre Y at the start of the object exec pass
        // (post-physics, before any object re-seats him this frame). ROM objects
        // that re-position the player (e.g. the SLZ staircase ride-seat) run in
        // their own slot; an object reading the player's position before that
        // re-seat (because its slot is lower than the re-seating object) sees
        // this pre-seat value. The engine folds the staircase into a lower slot
        // than the fan, so the fan would otherwise read the already-seated Y.
        // Objects that need ROM's "player position when my slot ran, before
        // later-slot objects moved him" read this via getPlayerCentreYAtExecStart.
        activateStageplayWindows();
        solidContacts.captureExecStartPlayerCentreY(player, activeSidekicks);
        SolidExecutionRegistry solidExecutionRegistry = objectServices.solidExecutionRegistry();
        solidExecutionRegistry.beginFrame(
                frameCounter,
                execution.collectActivePlayers(player, activeSidekicks, activePlayersScratch));
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
                if (population.slots.twoAxisCursorPlacement()) {
                    // S3K Load_Sprites runs before Process_Sprites and performs
                    // the X-cursor pass before the Y-camera pass
                    // (docs/skdisasm/sonic3k.asm:7916-7926, 37680-37802).
                    // S3K stays load-then-exec.
                    runTwoAxisLoadThenExecutePlacement(cameraX, false);
                }
                assignPendingPlayerSlotAllocations();
                // S2: NO pre-exec load. ROM S2 is RunObjects (s2.asm:5095) then
                // exactly one ObjectsManager (s2.asm:5112) = exec -> one load.
                // The single S2 load runs in the post-block below
                // (RunObjects -> ObjectsManager position), after runExecLoop has
                // applied the object-side MarkObjGone self-deletes.
                cleanupDestroyedDynamicObjects();
                runExecLoop(cameraX, player, activeSidekicks, inlineSolidResolution, solidPostMovement);
                runAfterExecBeforePlacement(afterExecBeforePlacement);
            } else {
                cleanupDestroyedDynamicObjects();
                syncActiveSpawnsLoad(true);
                assignPendingPlayerSlotAllocations();
                runExecLoop(cameraX, player, activeSidekicks, inlineSolidResolution, solidPostMovement);
            }
            flushPostExecDynamicSpawns();
        } finally {
            // No solid pass runs after the objects: each helper already wrote
            // Status_OnObj / Status_InAir at its own call site.
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
            if (!execThenLoad || !population.slots.twoAxisCursorPlacement()) {
                if (!competitionStreaming.active()) placement.update(cameraX);
            }
            if (execThenLoad && !population.slots.twoAxisCursorPlacement()
                    && !ObjectManagerInternalAccess.placementAtWorldTail(this)) {
                cleanupDestroyedDynamicObjects();
                syncActiveSpawnsLoad(true);
            }
        }
        captureCollisionResponseListForNextFrame();
    }

    public InitialObjectDispatchScope beginInitialProcessSprites(
            int cameraX, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks) {
        return initialDispatch.begin(cameraX, player, sidekicks);
    }

    public void loadInitialDynamicSlots(InitialObjectDispatchScope scope) {
        initialDispatch.loadDynamicSlots(scope);
    }

    public void processInitialAbsoluteDynamicSlot3(InitialObjectDispatchScope scope) {
        initialDispatch.processAbsoluteDynamicSlot3(scope);
    }

    public void processInitialDynamicSlots(InitialObjectDispatchScope scope) {
        initialDispatch.processDynamicSlots(scope);
    }

    public void finishInitialProcessSprites(InitialObjectDispatchScope scope) {
        initialDispatch.finish(scope);
    }

    public void freezeInitialCollisionResponseReadView() {
        initialDispatch.freezeCollisionReadView();
    }

    public void resetInitialCollisionResponseBuild() {
        initialDispatch.resetCollisionBuild();
    }

    public void markInitialDynamicCollisionBuildComplete() {
        initialDispatch.markDynamicCollisionBuildComplete();
    }

    public void captureInitialCollisionResponseBuild() {
        initialDispatch.captureCollisionBuild();
    }

    SolidExecutionRegistry beginInitialSolidExecution(
            PlayableEntity player, List<? extends PlayableEntity> sidekicks) {
        return beginInitialSolidExecution(player, sidekicks, null);
    }

    SolidExecutionRegistry beginInitialSolidExecution(PlayableEntity player,
            List<? extends PlayableEntity> sidekicks, InitialObjectDispatchController.Reservation original) {
        frameCounter++;
        beginNativeSpriteFrame();
        InitialObjectDispatchController.requireCurrent(original);
        updateCameraBounds();
        InitialObjectDispatchController.requireCurrent(original);
        solidContacts.captureExecStartPlayerCentreY(player, sidekicks);
        InitialObjectDispatchController.requireCurrent(original);
        SolidExecutionRegistry registry = objectServices.solidExecutionRegistry();
        InitialObjectDispatchController.requireCurrent(original);
        var players = execution.collectActivePlayers(player, sidekicks, activePlayersScratch);
        if (original == null) registry.beginFrame(frameCounter, players);
        else original.beginSolidFrame(frameCounter, players);
        InitialObjectDispatchController.requireCurrent(original);
        return registry;
    }

    void runNativeCompetitionTitleObjectPass() {
        initialDispatch.runNativeCompetitionTitleObjectPass(allocationLayout, () -> updating);
    }

    int firstDynamicSlot() {
        return population.slots.firstDynamicSlot();
    }

    /**
     * Admits a newly allocated native contact encounter before the player slot. The native
     * collision dispatcher latches contact; the object's own later slot resolves its response.
     */
    public boolean admitTransientContactBeforePlayer(AbstractObjectInstance object) {
        if (object == null) return false;
        return objectCallbacks.call(object, () -> {
            if (ObjectDirectionAccess.suspended(this, object)
                    || objectCallbacks.call(object, object::isDestroyed)
                    || objectCallbacks.call(object, object::getSlotIndex) < 0
                    || objectQuery().identityOf(object).isEmpty()) return false;
            objectCallbacks.run(object, object::snapshotTouchResponseState);
            return collisionResponseList.admitBeforePlayer(object, objectCallbacks);
        });
    }

    ObjectCollisionResponseList initialCollisionResponseList() {
        return collisionResponseList;
    }

    void runTwoAxisLoadThenExecutePlacement(int cameraX, boolean observeSetupOrder) {
        placement.update(cameraX);
        syncActiveSpawnsLoad(false);
    }

    private void runAfterExecBeforePlacement(Runnable afterExecBeforePlacement) {
        if (afterExecBeforePlacement != null) {
            afterExecBeforePlacement.run();
        }
    }

    void executeObjectWithSolidContext(ObjectInstance instance, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        objectCallbacks.run(instance, () -> execution.execute(
                instance, player, sidekicks, inlineSolidResolution, solidPostMovement));
    }

    SolidExecutionRegistry solidExecutionRegistry() {
        return objectServices.solidExecutionRegistry();
    }

    int vblaCounter() {
        return vblaCounter;
    }

    SolidCheckpointBatch processManualSolidCheckpoint(
            ObjectInstance instance,
            PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean solidPostMovement,
            SolidEntryRegisters registers) {
        return solidContacts.processManualCheckpoint(
                instance, player, sidekicks, solidPostMovement, registers);
    }

    SolidCheckpointBatch processCompatibilitySolidCheckpoint(
            ObjectInstance instance,
            PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean solidPostMovement,
            SolidEntryRegisters registers) {
        return solidContacts.processCompatibilityCheckpoint(
                instance, player, sidekicks, solidPostMovement, registers);
    }

    // Initial dispatch callers publish only after their native delete/range tail.
    // Sprite_CheckDeleteTouch3 (loc_1B666) rejects range before Add_SpriteToCollisionResponseList.
    void publishInitialCollisionResponse(ObjectInstance instance) {
        if (initialDispatch.isActive()) {
            // Touch publication records the physical SST, even if its routine
            // replaced the Java code owner during this dispatch.
            if (NativeCompetitionRoster.from(objectServices.worldSession()) != null && instance instanceof AbstractObjectInstance object) {
                int slot = objectCallbacks.call(object, object::getSlotIndex);
                if (!isProcessedSstSlot(slot)) return;
                instance = currentNativeSstOwner(slot);
                if (instance == null) return;
            }
            collisionResponseList.addToCurrentBuild(instance, objectCallbacks);
        }
    }

    private boolean excludesFromOrdinaryDynamicPass(ObjectInstance instance) {
        return initialDispatch.excludesFromDynamicPass(instance)
                || (instance instanceof AbstractObjectInstance object
                    && FixedRuntimeObjectInstance.ownsFixedPass(object, population.slots));
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
        for (ObjectInstance inst : population.placed.values()) {
            if (!ObjectDirectionAccess.suspended(this, inst))
                objectCallbacks.run(inst, inst::snapshotPreUpdatePosition);
        }
        for (ObjectInstance inst : population.dynamic) {
            if (!ObjectDirectionAccess.suspended(this, inst))
                objectCallbacks.run(inst, inst::snapshotPreUpdatePosition);
        }

        Arrays.fill(population.executionOrder, null);
        slotRetirement.clearOwners();
        Arrays.fill(playerCentreAtSlotStartValid, false);
        slotsFreedDuringObjectPass.clear();
        slotRetirement.clear();
        for (ObjectInstance inst : population.placed.values()) {
            if (excludesFromOrdinaryDynamicPass(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi && population.slots.isExecutableSlot(executionSlotIndex(aoi))) {
                population.executionOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
                indexOwnSlotRetirement(aoi);
            }
        }
        for (ObjectInstance inst : population.dynamic) {
            if (excludesFromOrdinaryDynamicPass(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi && population.slots.isExecutableSlot(executionSlotIndex(aoi))) {
                population.executionOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
                indexOwnSlotRetirement(aoi);
            }
        }
        // Phase 2: ExecuteObjects — run objects in slot order with inline out_of_range.
        updating = true;
        boolean objectsRemoved = false;
        objectLoopBudget.reset();
        try {
            for (currentExecSlot = 0; currentExecSlot < population.executionOrder.length; currentExecSlot++) {
                if (objectLoopBudget.walkEndedBefore(slotIndexForExec(currentExecSlot))) break;
                capturePlayerCentreAtSlotStart(player);
                if (slotRetirement.consumeChildRelease(currentExecSlot)) {
                    releaseSlot(slotIndexForExec(currentExecSlot));
                }
                if (retireBorrowedExecutionSlotOwnerAtOwnSlot(cameraX)) {
                    objectsRemoved = true;
                }
                ObjectInstance instance = population.executionOrder[currentExecSlot];
                if (instance == null) {
                    dispatchExternalSpriteSlot(cameraX, player);
                    continue;
                }
                if (ObjectDirectionAccess.suspended(this, instance)) continue;

                // ROM parity: most S1 objects check out_of_range at the START of
                // their routine during ExecuteObjects (static scenery / fixed
                // anchors), so the pre-execute check on the previous frame's
                // position matches ROM. Freeing the slot here (not in a batch
                // pre-pass) ensures child allocations from higher slots see the
                // correct set of available slots.
                //
                // Objects that move then check out_of_range at the END of their
                // routine (RememberState-after-SpeedToPos badniks) opt out via
                // checksOutOfRangeAfterRoutine(); for them the check runs
                // post-execute below, on the moved position, matching ROM
                // (docs/s1disasm/_incObj/sub RememberState.asm:9). Checking such
                // an object before its routine runs unloaded it one frame late
                // (S1 LZ2 Jaws f196 -> f1068 slot cascade).
                ObjectSpawn spawn = population.spawnByInstance.get(instance);
                boolean checksOutOfRangeAfter = objectCallbacks.call(
                        instance, instance::checksOutOfRangeAfterRoutine);
                if (!checksOutOfRangeAfter
                        && unloadIfOutsideLifetimeWindow(instance, spawn,
                                slotIndexForExec(currentExecSlot), cameraX)) {
                    population.executionOrder[currentExecSlot] = null;
                    objectsRemoved = true;
                    continue;
                }

                executeObjectWithSolidContext(
                        instance, player, sidekicks, inlineSolidResolution, solidPostMovement);

                if (checksOutOfRangeAfter
                        && !objectCallbacks.call(instance, instance::isDestroyed)
                        && unloadIfOutsideLifetimeWindow(instance, spawn,
                                slotIndexForExec(currentExecSlot), cameraX)) {
                    population.executionOrder[currentExecSlot] = null;
                    objectsRemoved = true;
                    continue;
                }

                if (objectCallbacks.call(instance, instance::isDestroyed)) {
                    int slotIndex = slotIndexForExec(currentExecSlot);
                    if (instance instanceof AbstractObjectInstance aoi3
                            && objectCallbacks.call(instance, aoi3::getSlotIndex) == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    var removalCallback = unloadDestroyedObject(instance);
                    population.executionOrder[currentExecSlot] = null;

                    if (spawn != null) {
                        freeAllReservedChildSlots(spawn);
                        finishDestroyedPlacement(instance, spawn, removalCallback);
                    } else {
                        removeDynamicObjectInstance(instance, removalCallback);
                    }
                    objectsRemoved = true;
                } else {
                    publishInitialCollisionResponse(instance);
                }
            }

            // Fallback: process dynamic objects without valid slots
            populateDynamicFallbackScratch();
            for (ObjectInstance inst : dynamicFallbackScratch) {
                if (ObjectDirectionAccess.suspended(this, inst)) continue;
                if (excludesFromOrdinaryDynamicPass(inst)) {
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    retireDestroyedDynamicFallback(inst);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (unloadIfOutsideLifetimeWindow(inst, null, -1, cameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    retireDestroyedDynamicFallback(inst);
                    objectsRemoved = true;
                } else {
                    publishInitialCollisionResponse(inst);
                }
            }
            // Fallback: process active objects without valid slots
            populateActiveFallbackScratch();
            for (ObjectInstance inst : activeFallbackScratch) {
                if (ObjectDirectionAccess.suspended(this, inst)) continue;
                if (excludesFromOrdinaryDynamicPass(inst)) {
                    continue;
                }
                ObjectSpawn spawn = population.spawnByInstance.get(inst);
                if (spawn == null) {
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    var removalCallback = unloadDestroyedObject(inst);
                    finishDestroyedPlacement(inst, spawn, removalCallback);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (unloadIfOutsideLifetimeWindow(inst, spawn, -1, cameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    var removalCallback = unloadDestroyedObject(inst);
                    finishDestroyedPlacement(inst, spawn, removalCallback);
                    objectsRemoved = true;
                } else {
                    publishInitialCollisionResponse(inst);
                }
            }
        } finally {
            slotRetirement.drainChildReleases(
                    execIndex -> releaseSlot(slotIndexForExec(execIndex)));
            currentExecSlot = -1;
            updating = false;
            deferredDynamicExecThisFrame.clear();
            if (objectsRemoved) {
                bucketsDirty = true;
                markActiveObjectCachesDirty();
            }
        }

        // Phase 3: ObjPosLoad — load new objects AFTER ExecuteObjects.
        // Slots freed during the exec loop and child slots allocated during exec
        // are now reflected in the allocator. New objects get the correct slot numbers.
        syncActiveSpawnsLoad(false);

    }

    /**
     * Runs the non-counter slot pass (S2/S3K). Each live object's manager-owned
     * lifetime check follows its routine, on its updated reference coordinate.
     * The caller schedules placement loading: S2 loads afterward; S3K's two-axis
     * placement loads before this pass. Object-owned self-deletes remain in their routines.
     */
    void runExecLoop(int cameraX, PlayableEntity player,
            List<? extends PlayableEntity> sidekicks,
            boolean inlineSolidResolution, boolean solidPostMovement) {
        var originalDispatch = initialDispatch.currentReservation();
        int unloadCameraX = objectUnloadCameraX(cameraX);
        // ROM parity: Snapshot all objects' positions BEFORE their updates run.
        for (ObjectInstance inst : population.placed.values()) {
            if (!ObjectDirectionAccess.suspended(this, inst))
                objectCallbacks.run(inst, inst::snapshotPreUpdatePosition);
        }
        for (ObjectInstance inst : population.dynamic) {
            if (!ObjectDirectionAccess.suspended(this, inst))
                objectCallbacks.run(inst, inst::snapshotPreUpdatePosition);
        }

        // ROM parity: Build slot-ordered execution array.
        slotsFreedDuringObjectPass.clear();
        slotRetirement.clear();
        Arrays.fill(population.executionOrder, null);
        slotRetirement.clearOwners();
        Arrays.fill(playerCentreAtSlotStartValid, false);
        for (ObjectInstance inst : population.placed.values()) {
            if (excludesFromOrdinaryDynamicPass(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi && population.slots.isExecutableSlot(executionSlotIndex(aoi))) {
                population.executionOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
                indexOwnSlotRetirement(aoi);
            }
        }
        for (ObjectInstance inst : population.dynamic) {
            if (excludesFromOrdinaryDynamicPass(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi && population.slots.isExecutableSlot(executionSlotIndex(aoi))) {
                population.executionOrder[execIndexForSlot(executionSlotIndex(aoi))] = inst;
                indexOwnSlotRetirement(aoi);
            }
        }
        updating = true;
        // A placement can be destroyed before this walk and replaced in its
        // own slot. Its folded children still occupy their native slots until
        // each child runs and notices the changed parent ID (S2 ObjA9_Main and
        // ObjAA_Main, s2.asm:77049-77053, 77077-77080). Schedule those releases
        // before the replacement executes, but do not free them ahead of the walk.
        currentExecSlot = -1;
        for (var entry : population.placed.entrySet()) {
            ObjectInstance instance = entry.getValue();
            if (objectCallbacks.call(instance, instance::isDestroyed)) {
                freeAllReservedChildSlots(entry.getKey());
            }
        }
        boolean objectsRemoved = false;
        // Track objects processed by the slot-based loop so the fallback loop
        // doesn't double-update objects that lost their slot mid-frame.
        // Reused per frame; cleared in the finally block below.
        Set<ObjectInstance> processedInExecLoop = processedInExecLoopScratch;
        objectLoopBudget.reset();
        try {
            // ROM parity: Iterate slots in ascending order, matching ExecuteObjects.
            for (currentExecSlot = 0; currentExecSlot < population.executionOrder.length; currentExecSlot++) {
                if (objectLoopBudget.walkEndedBefore(slotIndexForExec(currentExecSlot))) break;
                capturePlayerCentreAtSlotStart(player);
                if (slotRetirement.consumeChildRelease(currentExecSlot)) {
                    releaseSlot(slotIndexForExec(currentExecSlot));
                }
                if (retireBorrowedExecutionSlotOwnerAtOwnSlot(unloadCameraX)) {
                    objectsRemoved = true;
                }
                ObjectInstance instance = population.executionOrder[currentExecSlot];
                if (instance == null) {
                    dispatchExternalSpriteSlot(cameraX, player);
                    continue;
                }
                if (ObjectDirectionAccess.suspended(this, instance)) continue;
                processedInExecLoop.add(instance);

                executeObjectWithSolidContext(
                        instance, player, sidekicks, inlineSolidResolution, solidPostMovement);

                // This pass retires out-of-range objects after their routine,
                // using getOutOfRangeReferenceX() (live X unless the ROM uses an anchor).
                // The separate S1 counter pass selects pre/post-routine timing through
                // checksOutOfRangeAfterRoutine(); this pass always checks afterward.
                if (!objectCallbacks.call(instance, instance::isDestroyed)) {
                    ObjectSpawn oorSpawn = population.spawnByInstance.get(instance);
                    if (unloadIfOutsideLifetimeWindow(instance, oorSpawn,
                            slotIndexForExec(currentExecSlot), unloadCameraX)) {
                        population.executionOrder[currentExecSlot] = null;
                        objectsRemoved = true;
                        continue;
                    }
                }

                if (objectCallbacks.call(instance, instance::isDestroyed)) {
                    int slotIndex = slotIndexForExec(currentExecSlot);
                    if (instance instanceof AbstractObjectInstance aoi3
                            && objectCallbacks.call(instance, aoi3::getSlotIndex) == slotIndex) {
                        releaseSlot(slotIndex);
                    }
                    var removalCallback = unloadDestroyedObject(instance);
                    population.executionOrder[currentExecSlot] = null;

                    ObjectSpawn spawn = population.spawnByInstance.get(instance);
                    if (spawn != null) {
                        freeAllReservedChildSlots(spawn);
                        finishDestroyedPlacement(instance, spawn, removalCallback);
                    } else {
                        removeDynamicObjectInstance(instance, removalCallback);
                    }
                    objectsRemoved = true;
                } else {
                    publishInitialCollisionResponse(instance);
                }
            }

            // Fallback: process objects without valid slots
            populateDynamicFallbackScratch();
            for (ObjectInstance inst : dynamicFallbackScratch) {
                if (ObjectDirectionAccess.suspended(this, inst)) continue;
                if (excludesFromOrdinaryDynamicPass(inst)) {
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    retireDestroyedDynamicFallback(inst);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (!objectCallbacks.call(inst, inst::isDestroyed)
                        && unloadIfOutsideLifetimeWindow(inst, null, -1, unloadCameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    retireDestroyedDynamicFallback(inst);
                    objectsRemoved = true;
                } else {
                    publishInitialCollisionResponse(inst);
                }
            }
            populateActiveFallbackScratch();
            for (ObjectInstance inst : activeFallbackScratch) {
                if (ObjectDirectionAccess.suspended(this, inst)) continue;
                if (excludesFromOrdinaryDynamicPass(inst)) {
                    continue;
                }
                ObjectSpawn spawn = population.spawnByInstance.get(inst);
                if (spawn == null) {
                    continue;
                }
                // Skip objects already processed in the slot-based exec loop.
                // This prevents double-updates when an object releases its slot mid-frame.
                if (processedInExecLoop.contains(inst)) {
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    var removalCallback = unloadDestroyedObject(inst);
                    finishDestroyedPlacement(inst, spawn, removalCallback);
                    objectsRemoved = true;
                    continue;
                }
                executeObjectWithSolidContext(
                        inst, player, sidekicks, inlineSolidResolution, solidPostMovement);
                if (!objectCallbacks.call(inst, inst::isDestroyed)
                        && unloadIfOutsideLifetimeWindow(inst, spawn, -1, unloadCameraX)) {
                    objectsRemoved = true;
                    continue;
                }
                if (objectCallbacks.call(inst, inst::isDestroyed)) {
                    var removalCallback = unloadDestroyedObject(inst);
                    finishDestroyedPlacement(inst, spawn, removalCallback);
                    objectsRemoved = true;
                } else {
                    publishInitialCollisionResponse(inst);
                }
            }
        } finally {
            if (originalDispatch == null || originalDispatch.ownsManager()) {
                slotRetirement.drainChildReleases(
                        execIndex -> releaseSlot(slotIndexForExec(execIndex)));
                currentExecSlot = -1;
                updating = false;
                deferredDynamicExecThisFrame.clear();
                processedInExecLoopScratch.clear();
                if (objectsRemoved) {
                    bucketsDirty = true;
                    markActiveObjectCachesDirty();
                }
            }
        }
    }

    private int objectUnloadCameraX(int cameraX) {
        if (allocationLayout != ObjectSlotLayout.SONIC_2) {
            return cameraX;
        }
        // S2 RunObjects consumes Camera_X_pos_coarse from the previous
        // ObjectsManager pass. ObjectsManager_Main rewrites that coarse value
        // after BuildSprites (docs/s2disasm/s2.asm:5111-5112, 33033-33036),
        // so object-side MarkObjGone/MarkObjGone2 checks in the next
        // RunObjects pass must use the latched post-camera value rather than
        // recomputing from the live pre-camera value.
        return s2LatchedObjectManagerCameraX != Integer.MIN_VALUE
                ? s2LatchedObjectManagerCameraX
                : cameraX;
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
        if (competitionStreaming.active() || competitionStreaming.upfrontActive()) return;
        if (allocationLayout == ObjectSlotLayout.SONIC_2) {
            s2LatchedObjectManagerCameraX = postCameraX;
        }
        if (population.slots.twoAxisCursorPlacement()) {
            // S3K Load_Sprites runs at the start of LevelLoop before
            // Process_Sprites/DeformBgLayer, so post-camera ObjectPlacementController catch-up
            // would create objects one ROM loader call early
            // (docs/skdisasm/sonic3k.asm:7916-7927).
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
        if (population.placed.containsKey(spawn) || !placementAdmission.allows(spawn)) return true;
        if (!stageplayAllowsPlacement(spawn)) {
            noteStageplayFactorySuppression(spawn);
            return true; // Loader state advances, but authoring admission forbids native AI.
        }
        if (!isSpawnVerticallyEligibleForLoad(spawn, true)) {
            return false;
        }
        int preSlot = allocateSlot();
        if (preSlot < 0) {
            return false; // FindFreeObj failure equivalent
        }
        ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                () -> registry != null ? objectCallbacks.construct(
                        spawn, () -> registry.create(spawn)) : null);
        if (instance != null) {
            if (instance instanceof AbstractObjectInstance aoi) {
                objectCallbacks.run(aoi, () -> bindObjectServices(aoi));
                if (objectCallbacks.call(aoi, aoi::getSlotIndex) < 0) {
                    objectCallbacks.run(aoi, () -> aoi.setSlotIndex(preSlot));
                }
                if (counterValue >= 0) {
                    objectCallbacks.run(aoi, () -> aoi.setRespawnStateIndex(counterValue));
                }
            } else {
                releaseSlot(preSlot);
            }
            registerActiveObject(spawn, instance);
            bucketsDirty = true;
            markActiveObjectCachesDirty();
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
            planeSwitchers.update(player, spawn -> !stageplayAllowsPlacement(spawn) || hasInlinePlaneSwitcher(spawn));
        }
    }

    /** Applies one inline path-switch SST at its native object-slot position. */
    public void applyInlinePlaneSwitcher(ObjectSpawn spawn, PlayableEntity player) {
        if (planeSwitchers != null && spawn != null && player != null) {
            planeSwitchers.updateSpawn(spawn, player);
        }
    }

    /** Compatibility entry point for ROM plane-switch objects executing in their SST slot. */
    public void applyPlaneSwitcher(ObjectSpawn spawn, PlayableEntity player) {
        applyInlinePlaneSwitcher(spawn, player);
    }

    /** True when this frame's ExecuteObjects walk has already passed {@code object}. */
    public boolean isSlotAlreadyExecutedThisFrame(ObjectInstance object) {
        return slotRetirement.isAlreadyExecuted(
                object, updating, currentExecSlot, this::isManagedDynamicSlot);
    }

    private boolean hasInlinePlaneSwitcher(ObjectSpawn spawn) {
        return population.hasInlinePlaneSwitcher(spawn);
    }

    ObjectPlaneSwitcherController nativeTeleportPlaneSwitcherState() { return planeSwitchers; }

    public int getPlaneSwitcherSideState(ObjectSpawn spawn) {
        if (planeSwitchers == null) {
            return -1;
        }
        return planeSwitchers.getSideState(spawn);
    }

    public void drawLowPriority() {
        objectDrawing.drawLowPriority();
    }

    public void drawHighPriority() {
        objectDrawing.drawHighPriority();
    }

    private void ensureBucketsPopulated() {
        if (!bucketsDirty) {
            return;
        }
        bucketsDirty = false;
        objectDrawing.populateBuckets(population.placed.values(), population.dynamic, nativeSpriteSubmissions);
        renderBucketSnapshot.capture(population.placed.values(), population.dynamic);
    }

    public void drawPriorityBucket(int bucket, boolean highPriority) {
        objectDrawing.drawPriorityBucket(bucket, highPriority);
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
        objectDrawing.drawUnifiedBucket(bucket, callback);
    }

    /**
     * Callback interface for unified object drawing.
     * Called before each object is drawn to allow setting up shader uniforms.
     */
    @ModApi
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
        objectDrawing.drawUnifiedBucketWithPriority(bucket, gfx);
    }

    ObjectDrawInternalAccess.Drawing nativeDisplayDrawing() {
        return objectDrawing;
    }

    void drawBucketWithSlotEmissions(int bucket, GraphicsManager graphics,
            List<com.openggf.level.render.SpriteSlotEmission> external) {
        objectDrawing.drawBucketWithSlotEmissions(bucket, graphics, external);
    }

    private void drawBucketInstancesWithPriority(List<ObjectRenderParts.RenderEntry> entries, int bucket,
            GraphicsManager gfx, List<com.openggf.level.render.SpriteSlotEmission> external) {
        objectDrawing.drawBucketInstancesWithPriority(entries, bucket, gfx, external);
    }

    /** Retirement reads membership directly, without rebuilding render/slot caches. */
    List<ObjectInstance> contactRetirementCandidates() {
        List<ObjectInstance> candidates = new ArrayList<>(population.placed.values());
        candidates.addAll(population.dynamic);
        return candidates;
    }

    boolean containsContactRetirementCandidate(ObjectInstance candidate) {
        for (ObjectInstance object : population.placed.values()) if (object == candidate) return true;
        for (ObjectInstance object : population.dynamic) if (object == candidate) return true;
        return false;
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
     * {@code sub_13EFC} (docs/skdisasm/sonic3k.asm:26856-26873):
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
        return slot < 0 ? -1 : ObjectInstanceQueries.objectIdInSlot(
                getActiveObjects(), objectCallbacks, slot);
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
        return population.occupiedDynamicSlotIds(getActiveObjects());
    }

    public static final int SLOT_STATE_RESERVED_CHILD = -2;
    public static final int SLOT_STATE_UNATTRIBUTED = -3;

    public java.util.Map<Integer, Integer> occupiedDynamicSlotIdsWithReservations() {
        return population.occupiedDynamicSlotIdsWithReservations(getActiveObjects());
    }

    public List<TransitionSstOccupant> snapshotPersistentTransitionOccupants() {
        return population.snapshotPersistent();
    }

    /**
     * Captures every live SST-backed occupant for ROM reloads that leave
     * Object_RAM intact. This is deliberately separate from the persistent-only
     * carry policy and from the later coordinate-offset scan.
     */
    public List<TransitionSstOccupant> snapshotAllLiveSstObjectsForTransition() {
        return population.snapshotAllLiveSstObjects();
    }

    /**
     * Applies a ROM-style level-repeat coordinate shift to active level-space
     * objects. S3K MHZ uses this during the forced-scroll loop when the camera
     * wraps by $200 and the ROM's helper scans dynamic object RAM for objects
     * with {@code render_flags} bit 2 set.
     */
    public void applyLevelRepeatOffsetToActiveObjects(int offsetX, int offsetY) {
        population.applyLevelRepeatOffset(stageplayActiveRoutineObjects(), offsetX, offsetY);
    }

    List<ObjectInstance> getSolidProviderObjects() {
        rebuildActiveObjectCaches();
        return stageplayDirection == null || !stageplayDirection.hasSuspension()
                ? cachedSolidProviderObjects
                : cachedSolidProviderObjects.stream()
                        .filter(o -> !stageplayDirection.suspended(o))
                        .toList();
    }

    List<ObjectInstance> getTouchResponseObjects() {
        rebuildActiveObjectCaches();
        var result = collisionResponseList.touchResponseObjects(cachedTouchResponseObjects);
        return stageplayDirection == null || !stageplayDirection.hasSuspension()
                ? result
                : result.stream().filter(o -> !stageplayDirection.suspended(o)).toList();
    }

    boolean touchUsesPreviousCollisionResponseList() { return collisionResponseList.usesPrevious(); }

    /**
     * Publishes the {@code Collision_response_list} that a <em>represented</em>
     * (already-reconstructed, never dispatched) initial {@code Process_Sprites}
     * pass would have built, so the first {@code LevelLoop} pass's player slots
     * read a populated list rather than an empty one.
     * <p>
     * ROM: level entry runs {@code Load_Sprites} then {@code Process_Sprites}
     * once at {@code loc_6468} (docs/skdisasm/sonic3k.asm:7880-7886), before
     * {@code LevelLoop} begins. Every object executed in that pass tail-calls
     * {@code Add_SpriteToCollisionResponseList}
     * (docs/skdisasm/sonic3k.asm:21235-21243), so the list is already populated
     * when the first {@code LevelLoop} pass's Player_1/Player_2 slots walk it in
     * {@code Touch_Response} (docs/skdisasm/sonic3k.asm:20692).
     * <p>
     * Callers that <em>execute</em> the setup pass must not call this — the
     * dispatch publishes the list itself. It exists only for entry paths that
     * reconstruct the pass's resulting object state directly and would otherwise
     * leave S3K's previous-list read view empty for one pass.
     */
    public void publishRepresentedInitialCollisionResponseList() {
        captureCollisionResponseListForNextFrame();
    }

    void captureCollisionResponseListForNextFrame() {
        rebuildActiveObjectCaches();
        var touchObjects =
                stageplayDirection == null || !stageplayDirection.hasSuspension()
                        ? cachedTouchResponseObjects
                        : cachedTouchResponseObjects.stream()
                                .filter(object -> !stageplayDirection.suspended(object))
                                .toList();
        collisionResponseList.captureForNextFrame(touchObjects, objectCallbacks);
    }

    private void rebuildActiveObjectCaches() {
        activeObjectsCacheDirty = population.rebuildCachesIfDirty(activeObjectsCacheDirty,
                cachedActiveObjects, cachedSolidProviderObjects, cachedTouchResponseObjects);
    }

    void markActiveObjectCachesDirty() {
        activeObjectsCacheDirty = true;
        nativeObjectIndex.invalidate();
    }

    void invalidateNativeObjectIndex() { nativeObjectIndex.invalidate(); }

    NativeObjectIndex nativeObjectIndex() {
        // Native queries need a stable raw snapshot, not the general slot-sorted
        // rendering/collision caches or callbacks on unrelated object types.
        nativeObjectIndex.prepare(population.placed.values(), population.dynamic, objectCallbacks);
        return nativeObjectIndex;
    }

    public int getFrameCounter() {
        return frameCounter;
    }

    public boolean hasActiveInitialProcessSpritesDispatch() {
        return initialDispatch.isActive();
    }

    public int getActiveObjectSlotCount() {
        return population.allocator.activeCount();
    }

    public int getPeakObjectSlotCount() {
        return population.allocator.peakSlotCount();
    }

    public int getObjectSlotCapacity() {
        return population.slots.dynamicSlotCount();
    }

    /**
     * Captures the live Player 1 target visible when each SST slot begins.
     * {@code Obj_Attracted_Ring} reads Player 1 directly in its own object
     * routine, so a carrier in an earlier slot has already moved that target
     * while one in a later slot has not (sonic3k.asm:35750-35768,
     * 35795-35841).
     */
    private void capturePlayerCentreAtSlotStart(PlayableEntity player) {
        if (player == null) {
            return;
        }
        playerCentreXAtSlotStart[currentExecSlot] = player.getCentreX();
        playerCentreYAtSlotStart[currentExecSlot] = player.getCentreY();
        playerCentreAtSlotStartValid[currentExecSlot] = true;
    }

    public boolean hasPlayerCentreAtObjectSlotStart(int slotIndex) {
        int execIndex = execIndexForSlot(slotIndex);
        return execIndex >= 0
                && execIndex < playerCentreAtSlotStartValid.length
                && playerCentreAtSlotStartValid[execIndex];
    }

    public int getPlayerCentreXAtObjectSlotStart(int slotIndex) {
        return playerCentreXAtSlotStart[execIndexForSlot(slotIndex)];
    }

    public int getPlayerCentreYAtObjectSlotStart(int slotIndex) {
        return playerCentreYAtSlotStart[execIndexForSlot(slotIndex)];
    }

    public Collection<ObjectSpawn> getActiveSpawns() {
        return placement.getActiveSpawns();
    }

    Camera camera() {
        return camera;
    }

    /** Re-derives the static on-screen bounds from the (restored) simulation camera. */
    void refreshCameraBoundsAfterPresentationProbe() {
        updateCameraBounds();
    }

    /**
     * NATIVE widescreen draw mode: bake frozen poses for layout objects at a frame
     * boundary. No-op unless the display is wider than the native simulation window.
     */
    void prepareDormantPresentation(com.openggf.game.rewind.RewindRegistry registry) {
        dormantLayer.prepareAtFrameBoundary(registry);
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
        return population.placed.get(spawn);
    }

    public void addDynamicObject(ObjectInstance object) {
        addDynamicObjectInternal(object, false, true);
    }

    public <T extends ObjectInstance> T createDynamicObject(Supplier<T> factory) {
        return ObjectConstructionContext.construct(objectServices, () -> {
            T object = factory.get();
            addDynamicObject(object);
            return object instanceof AbstractObjectInstance aoi && (aoi.getSlotIndex() < 0 || aoi.isDestroyed()) ? null : object;
        });
    }

    ObjectNativeSstWord2C nativeSstWord2C() { return nativeSstWord2C; }

    AbstractObjectInstance currentNativeSstOwner(int slot) {
        return ObjectNativeSstOverwrite.currentOwner(population.placed.values(), population.dynamic, objectCallbacks, slot);
    }

    AbstractObjectInstance createUncheckedDynamicObject(
            java.util.function.Function<NativeSstState, AbstractObjectInstance> partialWriter) {
        return ObjectNativeSstOverwrite.create(nativeSstSurface(), partialWriter);
    }

    ObjectNativeSstOverwrite.Surface nativeSstSurface() {
        return new ObjectNativeSstOverwrite.Surface(
                objectServices, population.slots, population.allocator,
                population.placed, population.spawnByInstance, population.dynamic, objectCallbacks,
                population.dynamicOwnership, population.identities, deferredDynamicExecThisFrame,
                population.executionOrder, this::execIndexForSlot, updating, currentExecSlot,
                this::addDynamicObject, this::markRewindAdoptionDirty, nativeSstWord2C);
    }

    public ObjectRewindDynamicCodecs.ExactAdoptionSurface exactRewindAdoptionSurface() {
        return new ObjectRewindDynamicCodecs.ExactAdoptionSurface(
                objectServices, population.slots, population.allocator, population.identities,
                population.dynamic, population.dynamicOwnership.nonRewindableObjects(),
                population.executionOrder, this::execIndexForSlot, slot -> objectIdInSlot(slot) >= 0,
                this::markRewindAdoptionDirty);
    }
    void markRewindAdoptionDirty() {
        bucketsDirty = true;
        markActiveObjectCachesDirty();
    }

    /**
     * Constructs an object in a ROM-selected dynamic SST slot.
     *
     * <p>This is for fixed-slot setup paths, not ordinary {@code AllocateObject}
     * calls. For example, S3K's {@code SpawnLevelMainSprites} writes the AIZ
     * intro controller directly to dynamic object slot 2 (absolute SST slot 5)
     * instead of scanning for the first free slot.
     */
    public <T extends ObjectInstance> T createDynamicObjectAtSlot(
            Supplier<T> factory, int slotIndex) {
        return ObjectFixedSlotConstruction.create(objectServices,population.allocator,this::isManagedDynamicSlot,
                this::releaseSlot,objectCallbacks,this::registerInheritedCallbackOwner,this::addDynamicObject,factory,slotIndex);
    }

    /**
     * Constructs a dynamic object after reserving its SST slot.
     * <p>
     * Use only for ROM paths where the parent object already occupies {@code a0}
     * before constructor-time side effects spawn children. Ordinary event and
     * helper spawns should use {@link #createDynamicObject(Supplier)} so existing
     * constructor-time child allocation remains unchanged.
     */
    public <T extends ObjectInstance> T createDynamicObjectWithReservedSlot(Supplier<T> factory) {
        return ObjectFixedSlotConstruction.createWithReservedSlot(objectServices, objectCallbacks, this::allocateSlot,
                this::releaseSlot, this::registerInheritedCallbackOwner, this::addDynamicObject, factory);
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
            int slot = objectCallbacks.call(object, aoi::getSlotIndex);
            if (isManagedDynamicSlot(slot)) {
                releaseSlot(slot);
                int execIdx = execIndexForSlot(slot);
                if (execIdx >= 0 && execIdx < population.executionOrder.length) {
                    population.executionOrder[execIdx] = null;
                }
                objectCallbacks.run(object, () -> aoi.setSlotIndex(-1));
            }
        }
        objectCallbacks.runAndUnregister(object, object::onUnload);
        bucketsDirty = true;
        markActiveObjectCachesDirty();
    }

    boolean removeDynamicObjectInstance(ObjectInstance object) {
        return removeDynamicObjectInstance(object, null);
    }

    private boolean removeDynamicObjectInstance(ObjectInstance object,
            ObjectCallbackRouter.RemovalCallback removalCallback) {
        // Delete_Current_Sprite clears object-side contact memory before the slot's
        // ownership disappears (sonic3k.asm:36148-36165); player rides remain alone.
        return population.retireDynamic(object, removalCallback,
                solidContacts::evictRetiredDynamic, this::notifyObjectManagerRemoval);
    }

    private void notifyObjectManagerRemoval(ObjectInstance object,
            ObjectCallbackRouter.RemovalCallback removalCallback) {
        if (stageplayDirection != null) stageplayDirection.retired(object);
        // The deleted SST slot carries nothing into later touch passes: S3K
        // Delete_Current_Sprite zeroes it (docs/skdisasm/sonic3k.asm:36148-36165)
        // and Collision_response_list is rebuilt by every ExecuteObjects pass.
        if (touchResponses != null) {
            touchResponses.forgetRemovedObject(object);
        }
        removalCallback.invoke();
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
     * Queues an allocation for the tail of the current Process_Sprites pass.
     * S3K fixed player-effect SSTs execute after Dynamic_object_RAM, so children
     * they create reserve slots only after every ordinary dynamic object has run.
     */
    public void queueDynamicObjectAfterExec(ObjectInstance object) { runtimeState.queueDynamicObjectAfterExec(object); }

    /**
     * Routes an engine-owned power-up object through its native fixed SST slot
     * during the initial pass instead of the managed dynamic fallback.
     */
    public void registerInitialFixedDispatchObject(ObjectInstance object) {
        initialDispatch.registerFixedObject(object);
    }

    public void processInitialFixedDispatchObject(
            InitialObjectDispatchScope scope, ObjectInstance object) {
        objectCallbacks.run(object, () -> initialDispatch.processFixedObject(scope, object));
    }

    public void processInitialFixedDispatchObject(ObjectInstance object) {
        objectCallbacks.run(object, () -> initialDispatch.processFixedObject(object));
    }

    /**
     * Reserves SSTs that the player's own slot allocated during a player step the
     * engine runs before the object load (lightning-shield attracted rings).
     */
    void assignPendingPlayerSlotAllocations() {
        var rings = objectServices.ringManager();
        if (rings != null) {
            rings.assignPendingAttractedRingSlots();
        }
    }

    /** ROM {@code AllocateObject} availability for allocations deferred past the object load. */
    public boolean hasFreeDynamicSlotForDeferredAllocation(int alreadyDeferred) {
        return competitionStreaming.hasFreeDynamicSlotForDeferredAllocation(alreadyDeferred);
    }

    void flushPostExecDynamicSpawns() {
        var spawns = runtimeState.drainPostExecDynamicSpawns();
        if (spawns.isEmpty()) {
            return;
        }
        // These spawns model AllocateObject calls from fixed SSTs after
        // Dynamic_object_RAM; attracted rings deleted earlier in the pass have
        // already cleared their SSTs.
        var rings = objectServices.ringManager();
        if (rings != null) {
            rings.releaseAttractedRingsDeletingThisPass();
        }
        spawns.forEach(this::addDynamicObjectNextFrame);
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
        if (object == null) return;
        registerInheritedCallbackOwner(object);
        if (objectCallbacks.call(object,
                () -> population.admitNonRewindableAuxiliary(object, objectServices))) {
            if (object instanceof AbstractObjectInstance nativeObject)
                objectCallbacks.run(object, () -> nativeObject.bindStageplayActivation(stageplayActivation));
            bucketsDirty = true;
            markActiveObjectCachesDirty();
        }
    }

    /**
     * Adds a rewind-owned engine extension without consuming a ROM SST slot.
     *
     * <p>Unlike {@link #addAuxiliaryDynamicObject(ObjectInstance)}, this path is
     * included in object rewind snapshots. Use it for extra-player gameplay
     * objects whose lifetime must survive rewind but must not alter native slot
     * pressure.
     */
    public void addRewindableAuxiliaryDynamicObject(ObjectInstance object) {
        if (object == null) return;
        registerInheritedCallbackOwner(object);
        if (objectCallbacks.call(object,
                () -> population.admitRewindableAuxiliary(object, objectServices,
                        deferredDynamicExecThisFrame, updating))) {
            if (object instanceof AbstractObjectInstance nativeObject)
                objectCallbacks.run(object, () -> nativeObject.bindStageplayActivation(stageplayActivation));
            bucketsDirty = true;
            markActiveObjectCachesDirty();
        }
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
        registerInheritedCallbackOwner(object);
        if (stageplayDirection != null && !objectCallbacks.call(object, () -> stageplayDirection.allowsObject(object))) {
            objectCallbacks.run(object, () -> stageplayDirection.removeDynamic(object));
            return;
        }
        if (stageplayDirection != null)
            objectCallbacks.run(object, () -> stageplayDirection.noteNativeAdmission(object));
        if (object instanceof AbstractObjectInstance aoi) {
            objectCallbacks.run(object, () -> bindObjectServices(aoi));
            // ROM parity: FindFreeObj allocates an SST slot for EVERY object,
            // including children spawned by other objects (lava balls, projectiles,
            // explosion effects, etc.). Without this, child objects don't consume
            // slots in the allocator, causing subsequent OPL allocations to get lower
            // slot numbers than the ROM, shifting d7 values and breaking timing
            // gates like (v_vbla_byte + d7) & 7.
            if (objectCallbacks.call(object, aoi::getSlotIndex) < 0) {
                int slot;
                if (allocateAfterCurrent && explicitParentSlot >= 0) {
                    slot = allocateSlotAfter(explicitParentSlot);
                } else if (allocateAfterCurrent && updating && currentExecSlot >= 0) {
                    slot = allocateSlotAfter(slotIndexForExec(currentExecSlot));
                } else {
                    slot = allocateSlot();
                }
                if (slot >= 0) {
                    objectCallbacks.run(object, () -> aoi.setSlotIndex(slot));
                } else {
                    objectCallbacks.run(object, () -> aoi.setDestroyed(true));
                    return;
                }
            } else {
                // Pre-assigned slot (e.g. from addDynamicObjectAtSlot for badnik
                // replacement). Ensure the slot is marked as used in the allocator —
                // it may have been released when the original object was destroyed.
                int slot = objectCallbacks.call(object, aoi::getSlotIndex);
                population.allocator.reserveOrMarkUsed(slot);
            }
        }
        population.publishDynamicAdmission(object, objectCallbacks.call(object, object::getSpawn), () -> {
            if (!allowSameFrameExec && updating) {
                deferredDynamicExecThisFrame.add(object);
                if (object instanceof AbstractObjectInstance aoi2) {
                    objectCallbacks.run(object, () -> aoi2.setSkipTouchThisFrame(true));
                }
            }
            if (allowSameFrameExec && updating && object instanceof AbstractObjectInstance aoi2
                    && isProcessedSstSlot(objectCallbacks.call(object, aoi2::getSlotIndex))) {
                // ROM parity: FindFreeObj places the child directly into the SST.
                // The ExecuteObjects loop processes slots sequentially, so a child
                // at a HIGHER slot than the parent will be reached and updated in
                // the same frame. A child at a LOWER slot (already processed) won't
                // run until the next frame.
                int execIdx = execIndexForSlot(objectCallbacks.call(object, aoi2::getSlotIndex));
                if (execIdx < population.executionOrder.length && execIdx > currentExecSlot) {
                    objectCallbacks.run(object, object::snapshotPreUpdatePosition);
                    objectCallbacks.run(object, () -> aoi2.setSkipTouchThisFrame(true));
                    population.executionOrder[execIdx] = object;
                } else {
                    // Child placed into a slot at or below the parent's current
                    // execution slot: ExecuteObjects has already passed it this frame,
                    // so it will not run (nor set obRender bit 7 via DisplaySprite)
                    // until the next frame's pass. ROM ReactToItem skips objects whose
                    // obRender bit 7 is clear, so this child must stay touch-ineligible
                    // for one extra frame relative to a same-frame (higher-slot) child
                    // -- i.e. until the frame after its first own execution.
                    // (docs/s1disasm/_incObj/sub ReactToItem.asm:50-51)
                    objectCallbacks.run(object, aoi2::markAwaitingFirstTouchExecution);
                }
            }
            bucketsDirty = true;
        });
    }

    /**
     * Adds a dynamic object at a specific slot index.
     * ROM parity: badnik destruction changes obID in-place, keeping the SST slot.
     */
    public void addDynamicObjectAtSlot(ObjectInstance object, int slotIndex) {
        registerInheritedCallbackOwner(object);
        if (object instanceof AbstractObjectInstance aoi) {
            objectCallbacks.run(object, () -> bindObjectServices(aoi));
            objectCallbacks.run(object, () -> aoi.setSlotIndex(slotIndex));
        }
        addDynamicObject(object);
    }

    /** Preserve the captured creator fault boundary when player refresh recreates a native effect. */
    void restoreDynamicCallbackOwner(ObjectInstance object, String owner) {
        objectCallbacks.registerOwnerIfAbsent(object, owner);
    }

    /** Complete a captured late player-effect admission through the rewind owner. */
    boolean completePendingPlayerBoundObject(ObjectInstance object) {
        return rewind.completePendingPlayerBoundObject(object);
    }

    void bindRestoredCollisionObject(ObjectRefId identity, ObjectInstance object) {
        collisionResponseList.bindRestoredObject(identity, object);
    }

    /**
     * Allocates the next available dynamic slot index for the current game's layout.
     * Equivalent to ROM's FindFreeObj — searches from the first dynamic slot forward.
     * Returns -1 if all slots are in use (overflow).
     */
    int allocateSlot() {
        // Shipped S2 AllocateObject shortens only its plain scan in competition;
        // AllocateObjectAfterCurrent retains its separate end-of-RAM bound
        // (AllocateObject, s2.asm:33680-33694).
        if (firstFreeDynamicSlot() < 0) return -1;
        return population.allocator.allocate();
    }

    /**
     * Non-mutating ROM FindFreeObj probe: true if a free dynamic SST slot is
     * available (an {@code allocate()} would succeed). Used by object routines
     * that branch on FindFreeObj success/failure without spawning, for example
     * the S1 LZ drowning countdown retries its bubble RNG when the pool is full
     * (docs/s1disasm/_incObj/0A LZ Drowning Countdown.asm:283-284).
     */
    public boolean hasFreeDynamicSlot() {
        return firstFreeDynamicSlot() >= 0;
    }

    /** Non-mutating {@code FindFreeObj} result for ROM owner-boundary decisions. */
    public int firstFreeDynamicSlot() {
        return competitionStreaming.firstFreeDynamicSlot();
    }

    /**
     * Reserves the next available dynamic slot for non-ObjectInstance systems
     * that still occupy ROM SST slots. Equivalent to S3K AllocateObject
     * (sonic3k.asm:37946-37949), used by attracted rings whose logic is owned
     * by {@code RingManager} rather than {@code ObjectManager}.
     */
    public int allocateDynamicSlot() {
        return allocateSlot();
    }

    /**
     * Returns whether a newly reserved SST slot is behind the live object-loop
     * cursor and therefore cannot execute until the next Process_Sprites pass.
     */
    public boolean reservedSlotWaitsForNextObjectPass(int slotIndex) {
        return placement.reservedSlotWaitsForNextObjectPass(
                slotIndex, updating, currentExecSlot, population.slots);
    }

    /** Read-only retirement probe for the ring manager's legacy mirror. */
    public boolean hasLiveLostRingAtSlot(int slotIndex) {
        return ObjectInstanceQueries.hasLiveLostRingAtSlot(population.dynamic, slotIndex);
    }

    /**
     * Reassigns an already-loaded placement object through the ROM's first-free
     * allocator. This is used when a widened engine placement window materializes
     * an initializer before its native camera gate; once that gate succeeds, the
     * object must occupy the slot it would have received from the native load pass.
     */
    public void reallocateToFirstFreeDynamicSlot(AbstractObjectInstance object) {
        if (placement.reallocateToFirstFreeDynamicSlot(
                object, population.allocator, population.slots, population.executionOrder,
                updating, slotsFreedDuringObjectPass, this::allocateSlot)) {
            bucketsDirty = true;
            markActiveObjectCachesDirty();
        }
    }

    /**
     * Reserves the next free dynamic slot for a deferred S2 Obj37 owner. S2
     * {@code HurtCharacter} calls {@code AllocateObject} before later dynamic slots
     * finish and delete themselves; reserving the owner slot immediately captures
     * the first slot that is free at the ROM hurt instant.
     * Slots already freed earlier in the pass are visible to the ROM allocator, so
     * the scan must use the live allocator state instead of the per-pass freed-slot
     * ledger. {@code Obj37_Init} can then use later holes for child rings via ordinary
     * {@code AllocateObject}
     * (docs/s2disasm/s2.asm:85444-85461, 25125-25146).
     */
    public int allocateDynamicSlotAvoidingCurrentPassFrees() {
        return allocateDynamicSlot();
    }

    /**
     * Re-reserves a specific dynamic slot while restoring a subsystem-owned SST
     * occupant from a rewind snapshot.
     */
    public boolean reserveDynamicSlot(int slotIndex) {
        return population.allocator.reserve(slotIndex);
    }

    /**
     * Registers a spilled lost-ring object (ROM Obj37) into the dynamic-object exec
     * loop on a slot that was <em>already reserved</em> by the caller via
     * {@link #allocateDynamicSlot()} or {@link #allocateSlotAfter(int)}.
     * <p>
     * Unlike {@link #addDynamicObjectAtSlot}, this does NOT re-allocate or
     * mark-used the slot — {@code RingManager.LostRingPool} reserves the slot
     * up-front so the legacy {@code LostRing[]}
     * twin and the object share the same slot during the parallel cutover stage
     * (total slot consumption unchanged from today).
     * <p>
     * The ring joins the population's dynamic membership, so rewind uses the
     * {@code LostRingObjectInstance} recreation hook. Placed actors instead use
     * the placement registry. Execution-slot publication is needed only when the
     * newly admitted ring can execute in this frame.
     */
    public void spawnLostRingObjectAtSlot(AbstractObjectInstance ring, int reservedSlot) {
        if (ring == null) {
            return;
        }
        registerInheritedCallbackOwner(ring);
        objectCallbacks.run(ring, () -> bindObjectServices(ring));
        // Slot is already reserved by RingManager — do NOT re-allocate.
        objectCallbacks.run(ring, () -> ring.setSlotIndex(reservedSlot));
        population.publishDynamicAdmission(ring, objectCallbacks.call(ring, ring::getSpawn), () -> {
            if (updating && isManagedDynamicSlot(reservedSlot)) {
                int execIdx = execIndexForSlot(reservedSlot);
                if (execIdx >= 0 && execIdx < population.executionOrder.length && execIdx > currentExecSlot) {
                    objectCallbacks.run(ring, ring::snapshotPreUpdatePosition);
                    objectCallbacks.run(ring, () -> ring.setSkipTouchThisFrame(true));
                    population.executionOrder[execIdx] = ring;
                }
            }
            bucketsDirty = true;
        });
    }

    /**
     * Registers an Obj37 continuation that has no distinct Java allocator slot.
     * S3K's after-current spill ordering can outlive the engine's managed-slot
     * projection when other native SST occupants are represented by consolidated
     * Java objects. The ring remains ordered logically by the caller, executes in
     * the slotless fallback, and neither consumes nor releases a physical slot.
     */
    public void spawnLogicalLostRingOverflow(AbstractObjectInstance ring) {
        if (ring == null) {
            return;
        }
        registerInheritedCallbackOwner(ring);
        objectCallbacks.run(ring, () -> bindObjectServices(ring));
        objectCallbacks.run(ring, () -> ring.setSlotIndex(-1));
        population.publishDynamicAdmission(ring, objectCallbacks.call(ring, ring::getSpawn), () -> {
            bucketsDirty = true;
        });
    }

    /**
     * Returns all live objects of the given concrete type, in ascending slot order.
     */
    public <T extends ObjectInstance> List<T> activeObjectsOfType(Class<T> type) {
        return ObjectInstanceQueries.activeObjectsOfType(population.placed, population.dynamic, type);
    }

    private final ObjectQuery liveObjectQuery;
    private final ObjectQuery objectQuery = new ObjectQuery() {
        @Override public <T extends ObjectInstance> List<T> activeObjectsOfType(Class<T> type) {
            return liveObjectQuery.activeObjectsOfType(type);
        }
        @Override public java.util.Optional<ObjectRefId> identityOf(ObjectInstance object) {
            return liveObjectQuery.identityOf(object);
        }
        @Override public java.util.Optional<ObjectInstance> resolve(ObjectRefId identity) {
            return liveObjectQuery.resolve(identity);
        }
    };

    public ObjectQuery objectQuery() { return objectQuery; }

    /**
     * Test helper: reserve dynamic slots (from the front of the pool) until exactly
     * {@code freeSlots} remain free. Used by the spilled-ring atomic-allocation tests
     * to drive {@code allocateSlotAfter} into returning {@code -1} (slot exhaustion)
     * after a known number of successful ring allocations. No-op once the pool already
     * has {@code <= freeSlots} free.
     */
    public void reserveAllButNFreeSlots(int freeSlots) {
        int target = Math.max(0, freeSlots);
        while ((population.slots.dynamicSlotCount() - population.allocator.activeCount()) > target) {
            int slot = population.allocator.allocate();
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
        return population.allocator.allocateAfter(parentSlot);
    }

    /**
     * Releases a previously allocated dynamic slot index.
     */
    void releaseSlot(int slotIndex) {
        if (updating && isManagedDynamicSlot(slotIndex)) {
            slotsFreedDuringObjectPass.set(slotIndex);
        }
        population.allocator.release(slotIndex);
    }

    void releaseSlotIfManaged(ObjectInstance instance) {
        if (instance instanceof AbstractObjectInstance aoi) {
            int slot = objectCallbacks.call(instance, aoi::getSlotIndex);
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
        if (execIdx >= 0 && execIdx < population.executionOrder.length) {
            population.executionOrder[execIdx] = null;
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

    /** Package-private: {@code Seek_Object_Manager}, reached through {@link ObjectPlacementSeek}. */
    void seekPlacementCursors(int cameraX) {
        placement.seekCursors(cameraX);
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
        return population.dynamicOwnership.nativeCount(population.dynamic.size());
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
            int slot = objectCallbacks.call(object, aoi::getSlotIndex);
            if (isManagedDynamicSlot(slot)) {
                releaseSlot(slot);
                // Clear population.executionOrder so the slot can be reused
                int execIdx = execIndexForSlot(slot);
                if (execIdx >= 0 && execIdx < population.executionOrder.length) {
                    population.executionOrder[execIdx] = null;
                }
                // Clear the object's slot index to prevent double-release.
                // Without this, the object would be placed back into population.executionOrder
                // on the next frame (line ~239), and when eventually destroyed
                // or unloaded, would release the slot AGAIN — potentially
                // corrupting a different object that reused the slot number.
                // With slotIndex=-1, the object falls through to the fallback
                // population.placed loop for continued updates outside the managed slot window.
                objectCallbacks.run(object, () -> aoi.setSlotIndex(-1));
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
        int slot = objectCallbacks.call(instance, instance::getSlotIndex);
        if (isManagedDynamicSlot(slot)) {
            releaseSlot(slot);
            objectCallbacks.run(instance, () -> instance.setSlotIndex(-1));
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
        int[] childSlots = population.reservedChildren.get(spawn);
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
        int[] childSlots = population.reservedChildren.get(parentSpawn);
        if (childSlots != null && childIndex >= 0 && childIndex < childSlots.length) {
            int reservedSlot = childSlots[childIndex];
            if (isManagedDynamicSlot(reservedSlot)) {
                registerInheritedCallbackOwner(object);
                if (object instanceof AbstractObjectInstance aoi) {
                    objectCallbacks.run(aoi, () -> bindObjectServices(aoi));
                    objectCallbacks.run(aoi, () -> aoi.setSlotIndex(reservedSlot));
                    // Slot is already marked used in the allocator from pre-allocation;
                    // no need to call allocateSlot() again.
                }
                // Mark slot as consumed in the reservation table so freeAllReservedChildSlots
                // won't double-free this slot (the real child object now owns it).
                childSlots[childIndex] = -1;
                population.publishDynamicAdmission(object, object.getSpawn(), () -> {
                    if (updating) {
                        int execIdx = execIndexForSlot(reservedSlot);
                        if (execIdx >= 0 && execIdx < population.executionOrder.length && execIdx > currentExecSlot) {
                            objectCallbacks.run(object, object::snapshotPreUpdatePosition);
                            population.executionOrder[execIdx] = object;
                        }
                    }
                    bucketsDirty = true;
                });
                return;
            }
        }
        // Fallback: no pre-allocated slot, use normal allocation.
        // Record a consumed sentinel in the reservation table so that
        // subsequent calls won't attempt to allocate phantom slots for this
        // parent in later frames.
        if (childSlots == null) {
            population.reservedChildren.put(parentSpawn, new int[]{-1});
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
        int[] existing = population.reservedChildren.get(spawn);
        if (existing != null) {
            return existing;
        }
        int[] childSlots = new int[childCount];
        for (int c = 0; c < childCount; c++) {
            childSlots[c] = allocateSlot();
        }
        population.reservedChildren.put(spawn, childSlots);
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
        return childReservationLedger.reserveFixedAfter(spawn, childCount, parentSlot);
    }

    /**
     * Reserves one folded native child's SST after its predecessor for a current placed parent.
     * The fixed prefix must already exist; only released tail cells are reused. Dynamic parents
     * are unsupported because their changing spawn identity cannot use this snapshot mapping.
     * Engine-only: exposed across packages by {@link NativeChildSlotReservations}, not Mod API.
     */
    int reservePlacedChildSlotAfter(ObjectInstance parent, int predecessorSlot, int protectedPrefix) {
        return childReservationLedger.reserveAfter(parent, predecessorSlot, protectedPrefix);
    }

    /**
     * Releases a folded tail reservation only for its current placed parent. The native caller
     * must clear its saved slot after release; a numeric slot is not a same-owner generation token.
     */
    boolean releasePlacedChildSlot(ObjectInstance parent, int reservedSlot, int protectedPrefix) {
        return childReservationLedger.release(parent, reservedSlot, protectedPrefix);
    }

    /**
     * Returns peak dynamic slot count seen during this level.
     */
    public int getPeakSlotCount() {
        return population.allocator.peakSlotCount();
    }

    public int getAllocatedSlotCount() {
        return population.allocator.activeCount();
    }

    public int getLastDynamicSlotExclusive() {
        return population.slots.lastDynamicSlotExclusive();
    }

    public int getLastProcessSlotExclusive() {
        return population.slots.lastProcessSlotExclusive();
    }

    public boolean preallocatesLostRingOwnerSlot() {
        return population.slots.preallocatesLostRingOwnerSlot();
    }

    public boolean lostRingRemainderAllocatesAfterOwnerSlot() {
        return population.slots.lostRingRemainderAllocatesAfterOwnerSlot();
    }

    /**
     * Frees all reserved child slots for a given spawn, removing the tracking entry.
     * Called when the parent object is destroyed or unloaded.
     */
    void freeAllReservedChildSlots(ObjectSpawn spawn) {
        int[] childSlots = population.reservedChildren.remove(spawn);
        if (childSlots != null) {
            for (int slot : childSlots) {
                if (isManagedDynamicSlot(slot)) {
                    releaseChildSlotAtItsOwnExecPosition(slot);
                }
            }
        }
    }

    /**
     * Releases a consolidated child's reserved SST slot when the ascending ROM
     * object walk reaches that slot, rather than prematurely at its parent's slot.
     */
    private void releaseChildSlotAtItsOwnExecPosition(int slot) {
        if (updating) {
            int execIndex = execIndexForSlot(slot);
            if (slotRetirement.deferChildRelease(
                    execIndex, true, currentExecSlot, population.executionOrder.length)) {
                return;
            }
        }
        releaseSlot(slot);
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

    /**
     * Initializes the low-bit V-int phase observed by spilled-ring floor probes.
     * Trace recorders capture this independently because legacy S3K CSV fixtures
     * stored the adjacent V-int word rather than {@code V_int_run_count+2}.
     */
    public void initRingFloorCheckCounterPhase(int phase) { runtimeState.initRingFloorCheckCounterPhase(phase); }

    public void inheritRingFloorCheckCounterPhase(int phase) { runtimeState.inheritRingFloorCheckCounterPhase(phase); }

    public int getRingFloorCheckCounterPhase() { return runtimeState.ringFloorCheckCounterPhase(); }

    public boolean hasInheritedRingCounterPhase() { return runtimeState.hasInheritedRingCounterPhase(); }

    /**
     * Sets the phase difference between the general object-update clock and
     * S3K's {@code V_int_run_count}. They normally advance together, but old
     * trace schemas captured the adjacent life-count word instead of the
     * run counter and therefore need an independently reconstructed low bit.
     */
    public void initVIntRunCounterPhaseOffset(int phaseOffset) {
        vIntRunCounterPhaseOffset = phaseOffset;
    }

    public int getVIntRunCounterPhaseOffset() {
        return vIntRunCounterPhaseOffset;
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

    /**
     * Mirrors an object-local S1 {@code bclr #7,2(a2,d0.w)} while preserving
     * ObjPosLoad cursor cadence until the cursor naturally reprocesses the entry.
     */
    public void clearSpawnCounterActiveBitAndMarkDormant(ObjectSpawn spawn) {
        if (!placement.isCounterBasedRespawn()) {
            return;
        }
        placement.clearCounterForSpawn(spawn);
        placement.markDormant(spawn);
    }

    public void markRemembered(ObjectSpawn spawn) {
        placement.markRemembered(spawn, population.placed);
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

    /**
     * Moves a placement's active-lifetime ownership to an already allocated child.
     * Mirrors copying respawn_addr to a child while the transformed parent
     * ceases to own the placement lifetime. Both SST slots, execution order
     * and rewind identities stay intact.
     */
    public boolean transferPlacementOwnership(ObjectInstance from, ObjectInstance to) {
        boolean transferred = placement.transferPlacementOwnership(from, to,
                population.placed, population.spawnByInstance, population.dynamic);
        if (transferred) {
            if (from instanceof AbstractObjectInstance parent
                    && to instanceof AbstractObjectInstance child) {
                // Native loc_41D12 (sonic3k.asm:87117-87123) and loc_41D82
                // (sonic3k.asm:87161-87166) copy respawn_addr to the last column.
                // The active child's captured address rebuilds its occurrence
                // association after rewind; moving only the maps loses it.
                int respawnIndex = objectCallbacks.call(parent, parent::getRespawnStateIndex);
                objectCallbacks.run(child, () -> child.setRespawnStateIndex(respawnIndex));
            }
            bucketsDirty = true;
            markActiveObjectCachesDirty();
        }
        return transferred;
    }

    /**
     * Clears a placement's loaded/active bit while leaving its current object
     * instance alive.
     * <p>
     * Some ROM objects transform their original SST slot into a fragment and
     * then clear {@code respawn_addr} bit 7. Object placement may therefore
     * create a fresh copy while the transformed instance is still falling.
     * This is deliberately different from {@link #removeFromActiveSpawns},
     * which records an in-window destruction latch.
     */
    public void releaseSpawnForRespawn(ObjectInstance transformedInstance, ObjectSpawn spawn) {
        if (placement.releaseSpawnForRespawn(transformedInstance, spawn,
                population.placed, population.spawnByInstance, population.dynamic)) {
            bucketsDirty = true;
            markActiveObjectCachesDirty();
        }
    }

    /**
     * The player's centre Y at the start of this frame's object exec pass —
     * post-physics, before any object re-seated him. Objects whose ROM slot runs
     * before a player-re-seating object (e.g. the SLZ Fan reading Sonic's Y
     * before the higher-slot staircase ride-seat lifts him) should read this
     * instead of the live centre Y, so the engine's folded-into-a-lower-slot
     * re-seating object does not feed them an already-updated position. Falls
     * back to the live centre Y when no snapshot exists (player absent at
     * exec-start, e.g. spawned mid-pass).
     */
    public int getPlayerCentreYAtExecStart(PlayableEntity player) {
        return solidContacts.getPlayerCentreYAtExecStart(player);
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

    /**
     * Returns whether a solid object's native per-player pushing latch is live.
     * This distinguishes a Status_Push bit owned by SolidObject processing from
     * stale player-only residue without inspecting an object id or zone.
     */
    public boolean hasObjectPushingBit(PlayableEntity player) {
        return solidContacts.hasPushingLatch(player);
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
        return instance != null && population.placed.containsValue(instance);
    }

    /** Clear this player's riding state. */
    public void clearRidingObject(PlayableEntity player) {
        solidContacts.clearRidingObject(player);
    }

    /**
     * Release one object's native standing ownership and its folded ride record.
     * Other objects' standing latches and rides are left untouched.
     */
    public void releaseRidingObject(PlayableEntity player, ObjectInstance owner) {
        solidContacts.releaseRidingObject(player, owner);
    }

    /**
     * Run the shared ROM {@code CheckPlayerReleaseFromObj} terrain-release
     * operation for the player's current solid-object ride.
     */
    public boolean checkPlayerReleaseFromObjectFloor(PlayableEntity player) {
        return solidContacts.checkPlayerReleaseFromObjectFloor(player);
    }

    /**
     * Clear support established by an earlier solid slot after a later controller
     * object has made the player airborne in the same ExecuteObjects pass.
     */
    public void clearRidingObjectAfterControllerAirborneRelease(
            PlayableEntity player, ObjectInstance formerSupport) {
        solidContacts.clearRidingObjectAfterControllerAirborneRelease(player, formerSupport);
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

    public ObjectSolidContactController solidContacts() { return solidContacts; }

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

    /** Player y-radius captured before the currently executing solid checkpoint. */
    public int getPreContactYRadius() {
        return solidContacts.getPreContactYRadius();
    }

    /**
     * Resolves a newly-created ROM helper's inline solid checkpoint immediately.
     * <p>
     * Some S3K event routines allocate an object from a background/event path after
     * this engine's normal object pass has already run for the frame, while the ROM
     * object still executes its {@code SolidObjectTop} call in that same frame. The
     * AIZ transition floor is allocated at docs/skdisasm/sonic3k.asm:104731-104733,
     * then its object routine calls {@code SolidObjectTop} at 104777-104790.
     */
    public void processImmediateInlineSolidCheckpoint(ObjectInstance object,
            PlayableEntity player, List<? extends PlayableEntity> sidekicks) {
        if (object == null) {
            return;
        }
        List<? extends PlayableEntity> activeSidekicks = sidekicks != null ? sidekicks : List.of();
        if (!(object instanceof SolidObjectProvider provider)) {
            return;
        }
        // d4 is the object's current x: either it was allocated after the pass
        // reached its slot, or it already carried its riders at its own call
        // this frame and must not carry them again.
        solidContacts.processCompatibilityCheckpoint(object, player, activeSidekicks, true,
                SolidEntryRegisters.capture(this, object, provider));
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

    /** Capture the removal owner before onUnload unregisters the destroyed object. */
    private ObjectCallbackRouter.RemovalCallback unloadDestroyedObject(ObjectInstance instance) {
        var removalCallback = objectCallbacks.captureRemovalCallback(instance);
        objectCallbacks.runAndUnregister(instance, instance::onUnload);
        return removalCallback;
    }

    /**
     * Completes placed self-destruction after unload. Callers retain their original
     * placement lookup timing and release reserved children only on the slot path.
     */
    private void finishDestroyedPlacement(ObjectInstance instance, ObjectSpawn spawn,
            ObjectCallbackRouter.RemovalCallback removalCallback) {
        placement.clearStayActive(spawn);
        resetRespawnStateForOffscreenSelfDelete(instance, spawn);
        dispatchDestroyRemoveFromActive(instance, spawn);
        removeActiveObject(spawn, removalCallback);
    }

    /** Dynamic fallbacks release any managed slot before unload and population retirement. */
    private void retireDestroyedDynamicFallback(ObjectInstance instance) {
        releaseSlotIfManaged(instance);
        var removalCallback = unloadDestroyedObject(instance);
        removeDynamicObjectInstance(instance, removalCallback);
    }

    /**
     * Frees slots of dynamic objects explicitly marked as destroyed.
     * <p>
     * Releases slots held by dynamic children whose parent's {@code onUnload()}
     * set them to destroyed, before the next placement load or execution pass.
     * This is a targeted pass — only objects with {@code isDestroyed() == true}
     * are freed, matching the ROM's ExecuteObjects behavior where these objects
     * would delete themselves before ObjPosLoad runs.
     */
    void cleanupDestroyedDynamicObjects() {
        if (population.retireDestroyedDynamics(this::releaseSlot,
                solidContacts::evictRetiredDynamic, this::notifyObjectManagerRemoval)) {
            bucketsDirty = true;
            markActiveObjectCachesDirty();
        }
    }

    /**
     * Selects the legacy unload bound; {@link ObjectRangeOps} owns the ROM's
     * chunk alignment and unsigned word comparison. S1 counter placement keeps
     * the native 640 bound even at widescreen. Other legacy paths use
     * 128 + viewport width + 192 (848 at width 528). An object's isPersistent()
     * may veto the native decision using its own viewport-aware lifetime check;
     * Buzz Bomber does this. Keep these caller policies distinct.
     * See docs/status/known-discrepancies.md, "Object Despawn and Visibility Windows".
     */
    private boolean isOutOfRangeS1(int objX, int cameraX) {
        int limit = placement.isCounterBasedRespawn()
                ? ObjectRangeOps.OUT_OF_RANGE_BOUND
                : ObjectRangeOps.viewportOutOfRangeBound(camera.getWidth());
        return ObjectRangeOps.outOfRangeX(objX, cameraX, limit);
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
        // Block retirement and routine-tail lifetime are independent authorities.
        if (competitionStreaming.active()) {
            var profile = com.openggf.game.session.WorldSessionPolicyAccess.getService(
                    objectServices.worldSession(), ObjectRoutineLifetime.class);
            return ObjectRoutineLifetime.outOfRange(instance, profile);
        }
        if (stageplayActivation != null && objectCallbacks.call(instance, () -> stageplayActivation.keepsOutsideNativeView(instance, camera))) return false;
        int referenceX = outOfRangeReferenceX(instance, spawn);
        if (competitionStreaming.upfrontActive()) return competitionStreaming.isOutsideUpfrontUnloadWindow(referenceX);
        if (windowingStrategy.overridesUnloadWindow()) {
            if (windowingStrategy instanceof ViewportAwareObjectWindowingStrategy viewportAware) return viewportAware.isOutsideUnloadWindow(referenceX, cameraX, camera.getWidth());
            return windowingStrategy.isOutsideUnloadWindow(referenceX, cameraX);
        }
        return isOutOfRangeS1(referenceX, cameraX);
    }

    /**
     * ROM parity dispatcher for the destroy-from-active path.
     *
     * <p>When an object self-destroys via an off-screen check
     * (Sprite_OnScreen_Test family in sonic3k.asm -- see loc_1B5A0 at
     * sonic3k.asm:37311), ROM clears bit 7 of the respawn-table entry
     * ({@code bclr #7,(a2)} at sonic3k.asm:37315) so the ObjectPlacementController system
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
        boolean latchedDestroy = objectCallbacks.call(instance,
                () -> placement.dispatchDestroyRemoveFromActive(
                        instance, spawn, allocationLayout == ObjectSlotLayout.SONIC_2));
        if (latchedDestroy) {
            if (usesOccurrenceSolidStateKey(instance)) {
                solidContacts.evictLatchForDestroyedInstance(instance);
            } else {
                solidContacts.evictLatchForDestroyedSpawn(spawn);
            }
        }
    }

    /**
     * Mirror of the S1 {@code RememberState}/{@code Cat_Despawn} respawn-table
     * bit-7 clear for objects that delete themselves off-screen instead of
     * routing through {@link #unloadIfOutsideLifetimeWindow}.
     * <p>
     * Some S1 objects own their {@code out_of_range} tail (they set
     * {@link ObjectInstance#usesCustomOutOfRangeCheck()} with a no-op
     * {@link ObjectInstance#isCustomOutOfRange(int)}) and instead call
     * {@code setDestroyedByOffscreen()} from their own routine — e.g. the
     * Caterkiller, whose {@code Cat_Despawn} (docs/s1disasm/_incObj/78 Badnik -
     * Caterkiller.asm:139-148) runs after its fragment-entry check and clears
     * {@code bit 7} of its {@code v_objstate} entry so the placement cursor
     * re-spawns it when the player returns. Those self-deletes reach the
     * destroyed-removal path, which previously only called
     * {@code clearStayActive} and left the counter bit latched, so the badnik
     * never respawned. Reset the placement counter exactly as the
     * out_of_range path does, but only for off-screen self-deletes
     * ({@code destroyedRespawnable}); player kills keep the bit set and must
     * not respawn.
     */
    private void resetRespawnStateForOffscreenSelfDelete(ObjectInstance instance, ObjectSpawn spawn) {
        if (spawn == null
                || !placement.isCounterBasedRespawn()
                || !objectCallbacks.call(instance, instance::isDestroyedRespawnable)
                || !objectCallbacks.call(instance, instance::clearsRespawnStateOnCounterBasedOutOfRange)) {
            return;
        }
        placement.clearCounterForSpawn(spawn);
        // ROM parity: bit 7 is cleared but the cursor may still sit between this
        // spawn's entry and the window edge, so keep it dormant until ObjPosLoad
        // reprocesses it (matches unloadIfOutsideLifetimeWindow).
        placement.markDormant(spawn);
    }

    /**
     * Shared manager retirement for counter and non-counter execution. The caller
     * chooses when to check; the object's persistence hook can veto retirement,
     * and its reference/custom predicate chooses what is outside the window.
     * Sprite visibility is a separate question. Once admitted here, removal frees
     * the owned slot, calls onUnload and applies the placement's respawn policy.
     */
    private boolean unloadIfOutsideLifetimeWindow(ObjectInstance instance, ObjectSpawn spawn,
            int expectedSlotIndex, int cameraX) {
        // Authoring may bind a placed actor outside the primary window between ticks.
        // Its already-published native kill must reach the destruction dispatcher; a
        // geometric pre-routine unload must not clear its respawn byte first.
        if (stageplayActivation != null && objectCallbacks.call(instance, instance::isDestroyed)) return false;
        if (stageplayActivation != null && objectCallbacks.call(instance, () -> stageplayActivation.keepsOutsideNativeView(instance, camera))) return false;
        ObjectSpawn positionSpawn = spawn != null ? spawn
                : objectCallbacks.call(instance, instance::getSpawn);
        // Fallback dynamic children may exist briefly without any spawn-backed
        // identity. They still need update() calls, but this shared retirement
        // path requires a spawn record before consulting lifetime hooks.
        if (positionSpawn == null) {
            return false;
        }
        boolean vetoesRangeUnload;
        try {
            vetoesRangeUnload = objectCallbacks.call(instance, instance::isPersistent);
        } catch (NullPointerException e) {
            throw new IllegalStateException(objectCallbacks.describeCounterBasedUnloadObject(instance, spawn), e);
        }
        boolean outOfRange = objectCallbacks.call(instance, instance::usesCustomOutOfRangeCheck)
                ? objectCallbacks.call(instance, () -> instance.isCustomOutOfRange(cameraX))
                : isObjectOutOfRange(instance, spawn, cameraX);
        if (vetoesRangeUnload || !outOfRange) {
            return false;
        }
        if (instance instanceof AbstractObjectInstance aoi) {
            int slotIndex = objectCallbacks.call(instance, aoi::getSlotIndex);
            if (isManagedDynamicSlot(slotIndex)
                    && (expectedSlotIndex < 0 || slotIndex == expectedSlotIndex)) {
                releaseSlot(slotIndex);
            }
        }
        if (competitionStreaming.active() && instance instanceof AbstractObjectInstance nativeOwner)
            placement.clearNativeCompetitionRespawnLatch(nativeOwner.getRespawnStateIndex());
        var removalCallback = objectCallbacks.captureRemovalCallback(instance);
        objectCallbacks.runAndUnregister(instance, instance::onUnload);
        if (spawn != null) {
            freeAllReservedChildSlots(spawn);
            if (!placement.isCounterBasedRespawn()) {
                // S2/S3K MarkObjGone clears the live respawn bit and removes
                // the instance. S2 waits for an X-cursor crossing; S3K may
                // reconsider an entry still between the X cursors from the
                // next newly exposed Y strip.
                placement.removeFromActiveForUnload(spawn);
            } else {
                boolean clearsRespawnState = objectCallbacks.call(
                        instance, instance::clearsRespawnStateOnCounterBasedOutOfRange);
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
            }
            removeActiveObject(spawn, removalCallback);
        } else {
            removeDynamicObjectInstance(instance, removalCallback);
        }
        return true;
    }

    private int outOfRangeReferenceX(ObjectInstance instance, ObjectSpawn spawn) {
        try {
            // ROM parity: most objects feed obX(a0) to out_of_range, but some
            // S1 objects store an alternate anchor/origin in objoff_30/32/3A.
            // Use the object's explicit ROM reference X when provided.
            return objectCallbacks.call(instance, instance::getOutOfRangeReferenceX);
        } catch (NullPointerException e) {
            throw new IllegalStateException(objectCallbacks.describeCounterBasedUnloadObject(instance, spawn), e);
        }
    }


    /**
     * Materializes the ObjectPlacementController's candidate window. Candidates
     * leaving that window do not retire live objects; execution owns retirement.
     * <p>
     * The caller chooses loading order. S1/S2 load after their object pass, so
     * routine-created children receive slots before new placements and the new
     * placements first execute next frame. S3K's two-axis path loads before its
     * pass; reset also uses this method to materialize the initial window.
     */
    private void syncActiveSpawnsLoad(boolean allowVerticalLoadBypassForS2) {
        if (competitionStreaming.initializeUpfrontPlacement()) return;
        if (competitionStreaming.active()) {
            competitionStreaming.update();
            return;
        }
        Collection<ObjectSpawn> activeSpawns = placement.getActiveSpawns();
        boolean changed = false;

        if (population.slots.twoAxisCursorPlacement()) {
            // S3K Load_Sprites advances the X cursor before the Camera_Y pass
            // (sonic3k.asm:37680-37696, 37715-37798). X-pass entries use the
            // broad camera-Y band from loc_1B7F2/loc_1BA92; the later Y pass
            // runs only when Camera_Y_pos_coarse changes and scans the one
            // newly exposed chunk strip (sonic3k.asm:37585-37628, 37763-37811).
            int previousYCoarse = twoAxisCameraYCoarse;
            int currentYCoarse = currentCameraYCoarseForTwoAxisPlacement();
            if (previousYCoarse == Integer.MIN_VALUE) {
                previousYCoarse = currentYCoarse;
            }
            // Native allocation and cursor advancement form one operation. The
            // per-pass search continues strictly after the consumed SST, while
            // the next pass starts at the first empty slot again.
            int[] nextSlot = { population.allocator.firstFreeSlot() };
            changed |= placement.loadTwoAxisCursorPass(
                    (source, respawn) -> tryLoadTwoAxisOccurrence(source, respawn,
                            isSpawnVerticallyEligibleForLoad(source, allowVerticalLoadBypassForS2), nextSlot),
                    () -> nextSlot[0] >= 0);
            if (currentYCoarse != previousYCoarse) {
                nextSlot[0] = population.allocator.firstFreeSlot();
                for (ObjectSpawn source : placement.twoAxisYPassSpawns()) {
                    if (nextSlot[0] < 0) break;
                    changed |= tryLoadTwoAxisOccurrence(source, placement.twoAxisYRespawnIndex(source),
                            isSpawnVerticallyEligibleForTwoAxisYPass(source, previousYCoarse, currentYCoarse),
                            nextSlot);
                }
            }
            twoAxisCameraYCoarse = currentYCoarse;
            if (changed) {
                bucketsDirty = true;
                markActiveObjectCachesDirty();
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
            if (!stageplayAllowsPlacement(spawn) && !population.placed.containsKey(spawn))
                noteStageplayFactorySuppression(spawn);
            if (stageplayAllowsPlacement(spawn)
                    && placementAdmission.allowsNewSpawn(spawn, population.placed, placement)) {
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
                    if (population.slots.twoAxisCursorPlacement()) {
                        placement.markDeferredVerticalLoad(spawn);
                    }
                    continue;
                }
                // Pre-allocate parent slot — consumed by AbstractObjectInstance's
                // constructor via the PRE_ALLOCATED_SLOT ThreadLocal.
                int preSlot = allocateSlot();
                ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                        () -> registry != null ? objectCallbacks.construct(
                                spawn, () -> registry.create(spawn)) : null);
                if (instance != null) {
                    if (instance instanceof AbstractObjectInstance aoi) {
                        objectCallbacks.run(aoi, () -> bindObjectServices(aoi));
                        // Slot already set by constructor via PRE_ALLOCATED_SLOT.
                        // Ensure it's set (defensive, in case constructor didn't consume it).
                        if (objectCallbacks.call(aoi, aoi::getSlotIndex) < 0 && preSlot >= 0) {
                            objectCallbacks.run(aoi, () -> aoi.setSlotIndex(preSlot));
                        }
                        // ROM: S1 OPL_MakeItem stores the counter value as
                        // obRespawnNo for RememberState to use on unload.
                        if (placement.isCounterBasedRespawn()) {
                            int counter = placement.getCounterForSpawn(spawn);
                            if (counter >= 0) {
                                objectCallbacks.run(aoi, () -> aoi.setRespawnStateIndex(counter));
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
            markActiveObjectCachesDirty();
        }
    }

    /** An opted-in world owns this sole post-camera/display placement boundary. */
    void completeDeferredPlacement() {
        if (!competitionStreaming.active() || !ObjectManagerInternalAccess.placementAtWorldTail(this))
            throw new IllegalStateException("This world does not own deferred placement");
        cleanupDestroyedDynamicObjects();
        syncActiveSpawnsLoad(true);
    }

    ObjectManagerInternalAccess.Streaming nativeCompetitionStreaming() {
        return competitionStreaming;
    }

    private int currentCameraYCoarseForTwoAxisPlacement() {
        return camera != null ? (camera.getY() & 0xFF80) : 0;
    }

    private boolean tryLoadPlacementSpawn(ObjectSpawn spawn, boolean allowVerticalLoadBypassForS2) {
        if (!stageplayAllowsPlacement(spawn)
                || !placementAdmission.allowsNewSpawn(spawn, population.placed, placement)) {
            placement.completePendingCursorLoad(spawn);
            return false;
        }
        if (!isSpawnVerticallyEligibleForLoad(spawn, allowVerticalLoadBypassForS2)) {
            if (population.slots.twoAxisCursorPlacement()) {
                placement.markDeferredVerticalLoad(spawn);
            }
            placement.completePendingCursorLoad(spawn);
            return false;
        }
        // Pre-allocate parent slot — consumed by AbstractObjectInstance's
        // constructor via the PRE_ALLOCATED_SLOT ThreadLocal.
        int preSlot = allocateSlot();
        if (preSlot < 0) {
            return false;
        }
        ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                () -> registry != null ? objectCallbacks.construct(
                        spawn, () -> registry.create(spawn)) : null);
        if (instance != null) {
            if (instance instanceof AbstractObjectInstance aoi) {
                objectCallbacks.run(aoi, () -> bindObjectServices(aoi));
                if (objectCallbacks.call(aoi, aoi::getSlotIndex) < 0 && preSlot >= 0) {
                    objectCallbacks.run(aoi, () -> aoi.setSlotIndex(preSlot));
                }
                if (placement.isCounterBasedRespawn()) {
                    int counter = placement.getCounterForSpawn(spawn);
                    if (counter >= 0) {
                        objectCallbacks.run(aoi, () -> aoi.setRespawnStateIndex(counter));
                    }
                }
            } else if (preSlot >= 0) {
                releaseSlot(preSlot);
            }
            if (population.slots.twoAxisCursorPlacement() && instance instanceof AbstractObjectInstance object) {
                int respawn = placement.getSpawnIndex(spawn);
                placement.bindTwoAxisOccurrence(spawn, respawn);
                placement.setTwoAxisRespawnLoaded(respawn);
                objectCallbacks.run(object, () -> object.setRespawnStateIndex(respawn));
            }
            registerActiveObject(spawn, instance);
            placement.clearDeferredVerticalLoad(spawn);
            placement.completePendingCursorLoad(spawn);
            return true;
        }
        if (preSlot >= 0) {
            releaseSlot(preSlot);
        }
        placement.completePendingCursorLoad(spawn);
        return false;
    }

    private boolean tryLoadTwoAxisOccurrence(ObjectSpawn source, int respawnIndex,
            boolean verticallyEligible, int[] nextSlot) {
        // Policy and geometry use the canonical ROM source. A second occurrence
        // is admitted only by its actual independently selected clear ORT byte.
        if (!stageplayAllowsPlacement(source) || !placementAdmission.allows(source)
                || !placement.canLoadTwoAxisRespawn(respawnIndex)) return false;
        if (!verticallyEligible) {
            placement.markDeferredVerticalLoad(source);
            return false;
        }
        int preSlot = nextSlot[0];
        if (preSlot < 0 || !population.allocator.reserve(preSlot)) return false;
        ObjectSpawn occurrence = population.placed.containsKey(source)
                ? placement.twoAxisOccurrenceKey(source, respawnIndex, preSlot)
                : source;
        placement.bindTwoAxisOccurrence(occurrence, respawnIndex);
        placement.setTwoAxisRespawnLoaded(respawnIndex);
        ObjectInstance instance = ObjectConstructionContext.with(objectServices, preSlot,
                () -> registry != null ? objectCallbacks.construct(
                        occurrence, () -> registry.create(occurrence)) : null);
        if (instance == null) {
            releaseSlot(preSlot);
            placement.clearCounterForSpawn(occurrence);
            placement.forgetTwoAxisOccurrence(occurrence);
            return false;
        }
        if (instance instanceof AbstractObjectInstance object) {
            objectCallbacks.run(object, () -> bindObjectServices(object));
            objectCallbacks.run(object, () -> object.setRespawnStateIndex(respawnIndex));
        } else {
            releaseSlot(preSlot);
        }
        registerActiveObject(occurrence, instance);
        placement.clearDeferredVerticalLoad(source);
        if (!(instance instanceof AbstractObjectInstance)) {
            // Logical fallback actors release their loaned slot. Preserve that
            // existing contract: no native SST was consumed by this factory.
            nextSlot[0] = preSlot;
            return true;
        }
        nextSlot[0] = -1;
        for (int slot = preSlot + 1; slot < population.slots.lastDynamicSlotExclusive(); slot++) {
            if (population.allocator.isEmpty(slot)) {
                nextSlot[0] = slot;
                break;
            }
        }
        return true;
    }

    private boolean isSpawnVerticallyEligibleForTwoAxisYPass(ObjectSpawn spawn,
            int previousYCoarse, int currentYCoarse) {
        return placement.isSpawnVerticallyEligibleForTwoAxisYPass(
                spawn, previousYCoarse, currentYCoarse, camera);
    }

        private boolean isSpawnVerticallyEligibleForLoad(ObjectSpawn spawn, boolean allowVerticalLoadBypassForS2) {
            if (stageplayActivation != null && stageplayActivation.loads(spawn)) return true;
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
            return ObjectPlacementController.isNonCounterSpawnVerticallyEligible(
                    spawn, camera.getY(), camera.getMinY(), wrapRange);
        }

        static boolean isNonCounterSpawnVerticallyEligible(ObjectSpawn spawn, int cameraY, int cameraMinY) {
            return ObjectPlacementController.isNonCounterSpawnVerticallyEligible(
                    spawn, cameraY, cameraMinY, 0);
        }

        static boolean isNonCounterSpawnVerticallyEligible(ObjectSpawn spawn, int cameraY, int cameraMinY,
                int verticalWrapRange) {
            return ObjectPlacementController.isNonCounterSpawnVerticallyEligible(
                    spawn, cameraY, cameraMinY, verticalWrapRange);
        }

    /** Derived solely from managed placement ownership, rebuilt by registration
     * on restore. Equal coordinates or record values never select occurrence keys.
     */
    boolean usesOccurrenceSolidStateKey(ObjectInstance instance) {
        ObjectSpawn occurrence = population.spawnByInstance.get(instance);
        return occurrence != null && population.slots.twoAxisCursorPlacement()
                && placement.isNoncanonicalOccurrence(occurrence);
    }

    void registerActiveObject(ObjectSpawn spawn, ObjectInstance instance) {
        population.admitPlaced(spawn, instance);
    }

    void removeActiveObject(ObjectSpawn spawn) {
        removeActiveObject(spawn, null);
    }

    private void removeActiveObject(ObjectSpawn spawn,
            ObjectCallbackRouter.RemovalCallback removalCallback) {
        population.retirePlaced(spawn, removalCallback,
                solidContacts::releaseObjectLatchesForAllPlayers,
                (removed, capturedCallback) -> {
                    placement.forgetTwoAxisOccurrence(spawn);
                    notifyObjectManagerRemoval(removed, capturedCallback);
                });
    }

    void clearActiveObjects() {
        population.clearPlaced();
    }

    private void projectPublishedPopulationIdentity(ObjectInstance instance, boolean firstIdentity) {
        nativeSstWord2C.projectPublished(instance, firstIdentity);
    }

    private void populateDynamicFallbackScratch() {
        dynamicFallbackScratch.clear();
        for (ObjectInstance inst : population.dynamic) {
            if (deferredDynamicExecThisFrame.contains(inst)) {
                continue;
            }
            if (inst instanceof AbstractObjectInstance aoi
                    && isProcessedSstSlot(executionSlotIndex(aoi))) {
                continue;
            }
            dynamicFallbackScratch.add(inst);
        }
    }

    private void populateActiveFallbackScratch() {
        activeFallbackScratch.clear();
        for (ObjectInstance inst : population.placed.values()) {
            if (inst instanceof AbstractObjectInstance aoi
                    && isProcessedSstSlot(executionSlotIndex(aoi))) {
                continue;
            }
            activeFallbackScratch.add(inst);
        }
    }

    private void updateCameraBounds() {
        int left = camera.getX();
        int top = camera.getY();
        updateCameraBounds(left, top);
    }

    private void updateRenderCameraBounds() {
        // BuildSprites / Render_Sprites read Camera_X_pos_copy/Camera_Y_pos_copy
        // after the zone's scroll/screen-event routine has added the screen-shake
        // offset (S2 BuildSprites s2.asm:30567,30589-30611, shake e.g. SwScrl_ARZ
        // s2.asm:17709-17728; S3K sonic3k.asm:36364,36383-36401 after ScreenEvents),
        // while object logic and placement use the live camera words. Keep this
        // phase-specific so the copied coordinates do not leak into the next
        // object pass.
        int left = (short) (camera.getXCopy() + camera.getShakeOffsetX());
        int top = (short) (camera.getYCopy() + camera.getShakeOffsetY());
        updateCameraBounds(left, top);
    }

    private void updateCameraBounds(int left, int top) {
        int right = left + camera.getWidth();
        int bottom = top + camera.getHeight();
        int wrapRange = camera.isVerticalWrapEnabled() ? camera.getVerticalWrapRange() : 0;
        AbstractObjectInstance.updateCameraBounds(left, top, right, bottom, wrapRange);
    }

    public static int decodePlaneSwitcherHalfSpan(int subtype) {
        return ObjectPlaneSwitcherController.decodeHalfSpan(subtype);
    }

    public static boolean isPlaneSwitcherHorizontal(int subtype) {
        return ObjectPlaneSwitcherController.isHorizontal(subtype);
    }

    public static int decodePlaneSwitcherPath(int subtype, int side) {
        return ObjectPlaneSwitcherController.decodePath(subtype, side);
    }

    public static boolean decodePlaneSwitcherPriority(int subtype, int side) {
        return ObjectPlaneSwitcherController.decodePriority(subtype, side);
    }

    public static boolean planeSwitcherGroundedOnly(int subtype) {
        return ObjectPlaneSwitcherController.onlySwitchWhenGrounded(subtype);
    }

    public static char formatPlaneSwitcherLayer(byte layer) {
        return ObjectPlaneSwitcherController.formatLayer(layer);
    }

    public static char formatPlaneSwitcherPriority(boolean highPriority) {
        return ObjectPlaneSwitcherController.formatPriority(highPriority);
    }

    // -------------------------------------------------------------------------
    // Rewind snapshot adapter
    // -------------------------------------------------------------------------

    /**
     * World-object snapshot adapter. {@link ObjectRewindLifecycle} captures and
     * reconstructs actors in the shared {@link ObjectPopulation}; this coordinator
     * adds world clocks, placement cursors and contact buffers in their native order.
     *
     * <p>After restore, holders of direct object references must resolve the restored
     * population again: matching actors may be reused, while others are fresh instances.
     * Dynamic actors opt into reconstruction through {@link RewindRecreatable}.
     */
    public com.openggf.game.rewind.RewindSnapshottable<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot> rewindSnapshottable() {
        return new com.openggf.game.rewind.RewindSnapshottable<>() {
            @Override
            public String key() {
                return "object-manager";
            }

            @Override
            public ObjectManagerSnapshot capture() {
                RewindCaptureContext rewindContext = rewind.captureIdentityContext();
                ObjectRewindLifecycle.CapturedPopulation actors = rewind.capture(rewindContext);
                ObjectManagerSnapshot.SolidContactState solidContactState =
                        solidContacts.captureRewindState(rewindContext);
                return new ObjectManagerSnapshot(
                        actors.usedSlotsBits(), actors.slots(), frameCounter, vblaCounter, currentExecSlot,
                        population.allocator.peakSlotCount(), bucketsDirty,
                        actors.childSpawns(), actors.dynamicObjects(),
                        placement.captureRewindState(twoAxisCameraYCoarse, s2LatchedObjectManagerCameraX),
                        solidContactState.riding(), solidContactState,
                        planeSwitchers != null ? planeSwitchers.captureRewindState()
                                : ObjectManagerSnapshot.PlaneSwitcherSnapshot.empty(),
                        touchResponses != null ? touchResponses.captureRewindState()
                                : ObjectManagerSnapshot.TouchResponseOverlapState.empty(),
                        population.nextIdentityOrdinal(),
                        collisionResponseList.captureRewindState(population.identities::get),
                        sineCosineInputFraction);
            }

            @Override
            public void restore(ObjectManagerSnapshot s) {
                restoreObjectSnapshot(s);
            }
        };
    }

    private void restoreObjectSnapshot(ObjectManagerSnapshot s) {
        initialDispatch.retireState();
        rewind.clearPendingPlayerBindings();
        sineCosineInputFraction = s.sineCosineInputFraction();
        RewindCaptureContext rewindContext = rewind.restorePopulation(s, () -> {
            frameCounter = s.frameCounter();
            vblaCounter = s.vblaCounter();
            currentExecSlot = s.currentExecSlot();
            population.allocator.restorePeakSlotCount(s.peakSlotCount());
            bucketsDirty = s.bucketsDirty();
            markActiveObjectCachesDirty();
        });
        RewindIdentityTable restoreTable = rewindContext.requireIdentityTable();

        if (s.placement() != null) {
            twoAxisCameraYCoarse = placement.restoreRewindState(s.placement());
            if (population.slots.twoAxisCursorPlacement()) {
                for (var entry : population.placed.entrySet()) {
                    if (entry.getValue() instanceof AbstractObjectInstance object
                            && object.getRespawnStateIndex() >= 0) {
                        placement.restoreTwoAxisOccurrence(entry.getKey(),
                                object.getRespawnStateIndex(), object.getSlotIndex());
                    }
                }
            }
            // The S2 post-camera coarse-X unload latch must rewind with the
            // placement cursors; a stale later-frame latch makes the first
            // replayed frames' MarkObjGone checks unload against a future
            // camera edge (legacy snapshots carry MIN_VALUE = live fallback).
            s2LatchedObjectManagerCameraX = s.placement().s2LatchedCameraX();
        }

        solidContacts.restoreRewindState(s.solidContactState(), rewindContext);
        if (planeSwitchers != null) {
            planeSwitchers.restoreRewindState(s.planeSwitchers());
        }

        // ObjectTouchResponseController' double-buffer overlap state must be restored
        // AFTER object restoration so slot lookup resolves to live
        // post-restore instances. See iter-1631 root-cause analysis in
        // docs/architecture/plans/2026-05-07-rewind-encounter-validation.md.
        if (touchResponses != null) {
            touchResponses.restoreRewindState(s.touchResponseOverlap());
        }
        collisionResponseList.restoreRewindState(
                s.collisionResponseState(), restoreTable::resolve);
        population.restoreCapturedOrder();

        bucketsDirty = true;
        markActiveObjectCachesDirty();
    }


    /**
     * Test hook: toggles the in-place reuse fast path of the rewind restore.
     * When disabled, every snapshot entry is destroy/recreated (the pre-reuse
     * behavior), which the in-place restore equivalence test uses as the
     * reference path.
     */
    public void setRewindInPlaceRestoreEnabledForTest(boolean enabled) {
        rewind.setInPlaceRestoreEnabled(enabled);
    }


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
        return ObjectRewindTypeSafety.isSafe(type);
    }

    void inheritRetainedSstContacts(ObjectManager previous, List<ObjectInstance> carried) {
        solidContacts.inheritRetainedSstContacts(previous.solidContacts, carried);
    }

    /** Retains rewind identities when a transition carries the exact live SST occupants. */
    public void inheritTransitionObjectIdentities(ObjectManager previous,
                                                   List<ObjectInstance> carriedObjects) {
        population.inheritIdentities(previous.population, carriedObjects);
    }

    /**
     * Builds a fresh {@link RewindCaptureContext} reflecting
     * the current live object + player set. The returned context's
     * {@link RewindIdentityTable} maps every live object
     * to a stable {@link com.openggf.game.rewind.identity.ObjectRefId}.
     *
     * <p>This method is called internally by the capture/restore path and is also
     * exposed publicly so test harnesses can inspect the identity table without
     * triggering a full snapshot capture.
     */
    public com.openggf.game.rewind.schema.RewindCaptureContext captureIdentityContext() {
        return rewind.captureIdentityContext();
    }

    /**
     * Verifies that every identity-bearing field captured by the compact default
     * object path points at an object or player registered in the current rewind
     * identity context.
     */
    public void validateRewindReferenceClosure() {
        ObjectRewindReferenceClosureValidator.validate(
                population.placed.values(), population.dynamic,
                population.dynamicOwnership.nonRewindableObjects(), rewind.captureIdentityContext());
    }


    static boolean isRewindRestorableDynamicObject(ObjectInstance inst) {
        return isRewindRestorableDynamicObject(inst, null);
    }

    static boolean isRewindRestorableDynamicObject(ObjectInstance inst, ObjectRegistry registry) {
        return inst instanceof RewindRecreatable;
    }

    ObjectServices objectServicesForRewind() {
        return objectServices;
    }

    String stageplayAuthoredExecutionParent() {
        if (stageplayDirection == null
                || !updating
                || currentExecSlot < 0
                || currentExecSlot >= population.executionOrder.length) return null;
        return stageplayDirection.authoredId(population.executionOrder[currentExecSlot]);
    }

    void stageplayBucketsChanged() {
        bucketsDirty = true;
    }

    boolean isStageplayRingPlacement(ObjectSpawn spawn) {
        return stageplayController.isRingPlacement(spawn);
    }

    boolean isStageplayPlayerAttachmentOrRing(ObjectInstance object) {
        return objectCallbacks.call(object, () -> ObjectDirectionAccess.playerAttachmentOrRing(this, registry, object));
    }

    private Collection<ObjectInstance> stageplayActiveRoutineObjects() {
        return ObjectDirectionAccess.routineObjects(this, getActiveObjects());
    }

    private void noteStageplayFactorySuppression(ObjectSpawn spawn) {
        stageplayController.noteFactorySuppression(spawn);
    }

    boolean stageplayAllowsPlacement(ObjectSpawn spawn) {
        return stageplayDirection == null || stageplayDirection.allowsPlacement(spawn);
    }

    private void activateStageplayWindows() {
        stageplayController.activateWindows(camera, this::hasFreeDynamicSlot, spawn -> {
            if (!tryLoadPlacementSpawn(spawn, false)) return false;
            bucketsDirty = true;
            markActiveObjectCachesDirty();
            return true;
        });
    }

    void suspendStageplayContacts(ObjectInstance object) {
        objectCallbacks.run(object, object::releasePlayersForVisualDirection);
        solidContacts.suspendForVisualDirection(object);
        if (touchResponses != null) touchResponses.forgetRemovedObject(object);
    }

    StageplayObjectDirection.PlacementIdentity stageplayPlacementIdentity(ObjectSpawn spawn) {
        return stageplayController.identity(spawn);
    }

    Optional<ObjectInstance> findStageplayPlacement(StageplayObjectDirection.PlacementIdentity identity) {
        return stageplayController.find(identity);
    }

    void retireStageplayObject(ObjectInstance object) { stageplayController.retire(object); }
    ObjectInstance materializeStageplayPlacement(StageplayObjectDirection.PlacementIdentity identity) {
        return stageplayController.materialize(identity);
    }
    ObjectInstance materializeStageplayPlacement(ObjectSpawn spawn) {
        return stageplayController.materialize(spawn);
    }

    /**
     * Returns the game-specific {@link ObjectRegistry} for use by {@link
     * ObjectRewindDynamicCodecs#genericRecreate} during rewind restore. Package-private: shared by
     * verified derivative admission and rewind recreation.
     */
    ObjectRegistry objectRegistry() {
        return registry;
    }

    /**
     * Enqueues a captured {@link DynamicObjectEntry}
     * for a player-bound dynamic class whose post-restore re-spawn happens
     * after object-manager restore (currently Shield + Stars). Called by the
     * recreate path.
     */
    void enqueuePendingPlayerBoundEntry(Class<?> baseType,
            DynamicObjectEntry entry) {
        rewind.enqueuePendingPlayerBoundEntry(baseType, entry);
    }

    /** Checked native restitution retains this restore's admission and pending order. */
    ObjectRewindPlayerBindings.PendingReturn claimPendingPlayerBoundReturn(Class<?> baseType,
            DynamicObjectEntry entry, PlayableEntity owner) {
        return rewind.claimPendingPlayerBoundReturn(baseType, entry, owner);
    }

    /**
     * Pops the next captured entry for the given base type, or returns
     * {@code null} if none is pending. Called by {@code DefaultPowerUpSpawner}
     * during the post-restore re-spawn so the freshly-constructed instance
     * lands at the captured slot AND has its captured field surface
     * (animation cursor, timers, visibility flags, etc.) reapplied via
     * {@link AbstractObjectInstance#restoreRewindState}.
     */
    DynamicObjectEntry
            consumePendingPlayerBoundEntry(Class<?> baseType) {
        return consumePendingPlayerBoundEntry(baseType, entry -> true);
    }

    /**
     * Pops the first captured entry for the given player-bound base type that
     * satisfies {@code matcher}. Shield restore uses this to match owner and
     * concrete class instead of consuming another player's pending same-type
     * shield from the FIFO queue.
     */
    DynamicObjectEntry
            consumePendingPlayerBoundEntry(Class<?> baseType,
                    Predicate<DynamicObjectEntry> matcher) {
        return rewind.consumePendingPlayerBoundEntry(baseType, matcher);
    }


    /** Routes constructor-created actors to the active rewind adoption session. */
    void registerRewindReconstructionObject(AbstractObjectInstance object) {
        rewind.recordConstructionChild(object);
    }

    /** Compatibility name for construction-spawned structural children. */
    public void registerRewindReconstructionChild(AbstractObjectInstance child) {
        registerRewindReconstructionObject(child);
    }


    /** Unsigned fractional input passed between native game arithmetic owners in execution order. */
    int sineCosineInputFraction() {
        return sineCosineInputFraction;
    }

    /** Presentation-only playables cannot publish live native arithmetic. */
    void publishSineCosineInputFraction(
            com.openggf.sprites.playable.AbstractPlayableSprite producer, int fraction) {
        var sprites = producer.currentSpriteManagerOrNull();
        if (sprites != null && sprites.getAllSprites().contains(producer)) {
            publishSineCosineInputFraction(fraction);
        }
    }

    void publishSineCosineInputFraction(int fraction) {
        sineCosineInputFraction = fraction & 0xFFFF;
    }

    ObjectInstance findRestoredRidingObject(ObjectSpawn spawn, int slotIndex) {
        ObjectInstance occupant = ObjectSolidContactController.findRestoredRidingOccupant(
                population.placed, population.dynamic, objectCallbacks, spawn, slotIndex);
        if (occupant != null || !isProcessedSstSlot(slotIndex)) {
            return occupant;
        }
        int execIdx = execIndexForSlot(slotIndex);
        return execIdx >= 0 && execIdx < population.executionOrder.length ? population.executionOrder[execIdx] : null;
    }


}
