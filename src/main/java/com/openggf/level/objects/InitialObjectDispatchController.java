package com.openggf.level.objects;

import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.solid.SolidExecutionFrames;
import com.openggf.level.LevelManager;
import com.openggf.level.LevelRewindBoundaryAccess;
import com.openggf.game.PlayableEntity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Owns the exceptional initial object-dispatch scope and its fixed-slot set. */
final class InitialObjectDispatchController {
    private final ObjectManager objects;
    private Set<ObjectInstance> fixedObjects =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private Scope active;
    private Object incarnation = new Object();

    void retireState() {
        if (active != null) objects.retireInitialExecution();
        incarnation = new Object();
        active = null;
        fixedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    Reservation capture() { return new Reservation(); }

    Reservation currentReservation() {
        return active != null && active.original.isCurrent() ? active.original : null;
    }

    static Reservation currentReservation(ObjectManager manager) {
        var controller = manager.initialDispatch();
        return controller != null ? controller.currentReservation() : null;
    }

    static void requireCurrent(Reservation original) {
        if (original != null) original.requireCurrent();
    }

    static <T> T afterCallback(Reservation original, T result) {
        requireCurrent(original);
        return result;
    }

    final class Reservation {
        private final Object originalIncarnation = incarnation;
        private final LevelManager level = objects.services() instanceof DefaultObjectServices actual
                ? actual.initialDispatchLevel() : null;
        private final ObjectManager attachment = level != null
                ? LevelRewindBoundaryAccess.attachedObjectManager(level) : null;
        private SolidExecutionFrames.Frame frame = SolidExecutionFrames.capture(
                objects.services() instanceof DefaultObjectServices actual
                        ? actual.initialDispatchSolids() : objects.solidExecutionRegistry());

        boolean ownsManager() {
            return incarnation == originalIncarnation && (level == null
                    || LevelRewindBoundaryAccess.attachedObjectManager(level) == attachment);
        }

        boolean isCurrent() { return ownsManager() && frame.isCurrent(); }

        void requireCurrent() {
            if (!isCurrent()) throw ObjectManagerInternalAccess.supersededInitialDispatch();
        }

        void beginSolidFrame(int counter, List<? extends PlayableEntity> players) {
            requireCurrent();
            frame = frame.begin(counter, players);
            requireCurrent();
        }
    }

    InitialObjectDispatchController(ObjectManager objects) {
        this.objects = objects;
    }

    /**
     * RunObjects while the S2 title bit remains set (s2.asm:29805-29819).
     * The caller already ran both human slots. Preserve the existing native
     * execution/solid owners, but neither dispatch ObjectsManager nor count a
     * VBlank here: the leading scan has none and each later wait owns its own.
     */
    void runNativeCompetitionTitleObjectPass(ObjectSlotLayout slotLayout, BooleanSupplier updating) {
        var roster = com.openggf.game.competition.NativeCompetitionRoster.from(objects.services().worldSession());
        if (roster == null || roster.player(0) == null || roster.player(1) == null
                || roster.player(0) == roster.player(1)
                || roster.player(0).isCpuControlled() || roster.player(1).isCpuControlled()
                || slotLayout != ObjectSlotLayout.SONIC_2) {
            throw new IllegalStateException("S2 native title scan requires two supplied human slots");
        }
        if (updating.getAsBoolean() || objects.hasActiveInitialProcessSpritesDispatch()) {
            throw new IllegalStateException("Native title object scans cannot nest another object pass");
        }
        var player = roster.player(0);
        var second = List.of(roster.player(1));
        int cameraX = roster.camera(0).getX();
        SolidExecutionRegistry registry = objects.beginInitialSolidExecution(player, second);
        try {
            objects.solidContacts().beginInlineFrame(player, second, true);
            objects.assignPendingPlayerSlotAllocations();
            objects.cleanupDestroyedDynamicObjects();
            objects.runExecLoop(cameraX, player, second, true, true);
            objects.flushPostExecDynamicSpawns();
        } finally {
            objects.solidContacts().finishInlineFrame(player, second);
            registry.finishFrame();
        }
        objects.captureCollisionResponseListForNextFrame();
    }

    InitialObjectDispatchScope begin(
            int cameraX,
            PlayableEntity player,
            List<? extends PlayableEntity> sidekicks) {
        return begin(capture(), cameraX, player, sidekicks);
    }

    InitialObjectDispatchScope begin(Reservation original, int cameraX,
            PlayableEntity player, List<? extends PlayableEntity> sidekicks) {
        original.requireCurrent();
        if (active != null && active.original.isCurrent()) {
            throw new IllegalStateException("initial Process_Sprites dispatch already active");
        }
        if (active != null) {
            fixedObjects = Collections.newSetFromMap(new IdentityHashMap<>());
            active = null;
        }
        List<? extends PlayableEntity> activeSidekicks = sidekicks != null ? sidekicks : List.of();
        objects.beginInitialSolidExecution(player, activeSidekicks, original);
        original.requireCurrent();
        active = new Scope(cameraX, player, activeSidekicks, original, fixedObjects);
        return active;
    }

    void loadDynamicSlots(InitialObjectDispatchScope scope) {
        Scope dispatch = require(scope);
        objects.runTwoAxisLoadThenExecutePlacement(dispatch.cameraX, true);
        dispatch.original.requireCurrent();
        objects.cleanupDestroyedDynamicObjects();
        dispatch.original.requireCurrent();
    }

    void processAbsoluteDynamicSlot3(InitialObjectDispatchScope scope) {
        require(scope);
        if (objects.firstDynamicSlot() != 4) {
            throw new IllegalStateException(
                    "initial absolute slot 3 invariant requires managed slots to start at 4");
        }
        for (ObjectInstance instance : objects.getActiveObjects()) {
            if (instance instanceof AbstractObjectInstance object
                    && objects.callObjectCallback(object, object::getSlotIndex) == 3) {
                throw new IllegalStateException(
                        "fresh initial Process_Sprites unexpectedly registered absolute slot 3");
            }
        }
    }

    void processDynamicSlots(InitialObjectDispatchScope scope) {
        Scope dispatch = require(scope);
        objects.runExecLoop(
                dispatch.cameraX, dispatch.player, dispatch.sidekicks, false, false);
        dispatch.original.requireCurrent();
        objects.flushPostExecDynamicSpawns();
        dispatch.original.requireCurrent();
    }

    void freezeCollisionReadView() {
        requireActive();
        objects.initialCollisionResponseList().freezePreviousReadView();
        objects.snapshotTouchResponseState(true);
        requireActive();
    }

    void resetCollisionBuild() {
        requireActive();
        objects.initialCollisionResponseList().resetCurrentBuild();
    }

    void markDynamicCollisionBuildComplete() {
        requireActive();
        objects.initialCollisionResponseList().markDynamicBuildComplete();
    }

    void captureCollisionBuild() {
        Scope dispatch = requireActive();
        objects.initialCollisionResponseList().captureCompletedBuild();
        dispatch.original.requireCurrent();
        dispatch.finished = true;
    }

    void finish(InitialObjectDispatchScope scope) {
        require(scope);
        captureCollisionBuild();
    }

    boolean isActive() {
        return currentReservation() != null;
    }

    boolean excludesFromDynamicPass(ObjectInstance object) {
        return isActive() && active.fixedObjects.contains(object);
    }

    void registerFixedObject(ObjectInstance object) {
        if (object != null) {
            fixedObjects.add(object);
        }
    }

    void processFixedObject(InitialObjectDispatchScope scope, ObjectInstance object) {
        processFixedObject(require(scope), object);
    }

    void processFixedObject(ObjectInstance object) {
        if (active == null) {
            throw new IllegalStateException("no active initial Process_Sprites dispatch");
        }
        processFixedObject(active, object);
    }

    private void processFixedObject(Scope dispatch, ObjectInstance object) {
        dispatch.original.requireCurrent();
        if (object == null) return;
        boolean destroyed = objects.callObjectCallback(object, object::isDestroyed);
        dispatch.original.requireCurrent();
        if (destroyed || !dispatch.fixedObjects.contains(object)) return;
        objects.executeObjectWithSolidContext(
                object, dispatch.player, dispatch.sidekicks, false, false);
        dispatch.original.requireCurrent();
        objects.publishInitialCollisionResponse(object);
        dispatch.original.requireCurrent();
    }

    private Scope requireActive() {
        if (active == null) throw ObjectManagerInternalAccess.supersededInitialDispatch();
        active.original.requireCurrent();
        return active;
    }

    private Scope require(InitialObjectDispatchScope scope) {
        if (!(scope instanceof Scope dispatch) || dispatch != active || dispatch.closed) {
            throw new IllegalArgumentException("scope does not own the active initial dispatch");
        }
        dispatch.original.requireCurrent();
        return dispatch;
    }

    private final class Scope implements InitialObjectDispatchScope {
        private final int cameraX;
        private final PlayableEntity player;
        private final List<? extends PlayableEntity> sidekicks;
        private final Reservation original;
        private final Set<ObjectInstance> fixedObjects;
        private boolean finished;
        private boolean closed;

        private Scope(
                int cameraX,
                PlayableEntity player,
                List<? extends PlayableEntity> sidekicks,
                Reservation original, Set<ObjectInstance> fixedObjects) {
            this.cameraX = cameraX;
            this.player = player;
            this.sidekicks = sidekicks;
            this.original = original;
            this.fixedObjects = fixedObjects;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (active == this && original.isCurrent()) {
                    if (!finished) objects.initialCollisionResponseList().abortCurrentBuild();
                    original.frame.finish();
                }
            } finally {
                if (active == this) {
                    fixedObjects.clear();
                    active = null;
                }
            }
        }
    }
}
