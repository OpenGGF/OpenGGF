package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.competition.Sonic2CompetitionObjectStreaming;
import com.openggf.graphics.GLCommand;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Removal-only controls: retained callback authority must not restore live registration. */
class TestObjectRemovalCallbackOwnership {
    @BeforeEach void setup() { TestEnvironment.resetAll(); }
    @AfterEach void cleanup() { SessionManager.clear(); }

    @Test void capturedRemovalOutsideAnyScopeRetainsOnlyItsExactReceiverOwner() {
        var boundary = new Boundary();
        var lookup = new OwnershipLookup();
        var router = new ObjectCallbackRouter(new Registry(Map.entry(boundary, lookup)));
        var receiver = actor("owner-a");
        var child = actor(null);
        router.register(receiver, receiver.getSpawn());
        var removal = router.captureRemovalCallback(receiver);
        router.unregister(receiver);
        lookup.forbidden = receiver;
        receiver.removing = () -> {
            assertEquals("owner-a", boundary.activeOwner);
            assertNull(router.owner(receiver));
            router.registerInherited(child);
        };

        removal.invoke();

        assertEquals(List.of("owner-a"), boundary.entries);
        assertEquals("owner-a", router.owner(child));
        assertNull(router.owner(receiver));
        assertEquals(1, receiver.removals);
        assertFalse(removal.isFor(actor("owner-a")));
        var outside = actor(null);
        router.registerInherited(outside);
        assertEquals("scoped-owner", router.owner(outside), "the captured scope was restored");
    }

    @Test void capturedRemovalInsideDifferentOwnerRestoresOuterOwnerEvenOnError() {
        var boundary = new Boundary();
        var router = new ObjectCallbackRouter(new Registry(boundary));
        var receiver = actor("owner-a");
        var outer = actor("owner-b");
        var child = actor(null);
        var after = actor(null);
        router.register(receiver, receiver.getSpawn());
        router.register(outer, outer.getSpawn());
        var removal = router.captureRemovalCallback(receiver);
        router.unregister(receiver);
        var failure = new AssertionError("removal fixture");
        receiver.removing = () -> {
            router.registerInherited(child);
            throw failure;
        };

        router.run(outer, () -> {
            assertSame(failure, assertThrows(AssertionError.class, removal::invoke));
            assertEquals("owner-b", boundary.activeOwner);
            router.registerInherited(after);
        });

        assertEquals(List.of("owner-b", "owner-a"), boundary.entries);
        assertEquals("owner-a", router.owner(child));
        assertEquals("owner-b", router.owner(after));
        assertNull(router.owner(receiver));
        var outside = actor(null);
        router.registerInherited(outside);
        assertNull(router.owner(outside));
    }

    @Test void onlyRemovalTokenElidesAnAlreadyEstablishedSameRouterOwnerBoundary() {
        var boundary = new Boundary();
        boundary.containFailures = true;
        var router = new ObjectCallbackRouter(new Registry(boundary));
        var receiver = actor("owner-a");
        router.register(receiver, receiver.getSpawn());
        var removal = router.captureRemovalCallback(receiver);
        router.unregister(receiver);
        var outer = actor(new String("owner-a"));
        router.register(outer, outer.getSpawn());
        var failure = new IllegalStateException("removal fixture");
        receiver.removing = () -> { throw failure; };
        boolean[] continued = {false};

        router.run(outer, () -> {
            removal.invoke();
            continued[0] = true;
        });

        assertFalse(continued[0]);
        assertEquals(List.of("owner-a"), boundary.entries);
        assertEquals(List.of(failure), boundary.failures);
        assertEquals(List.of("owner-a"), boundary.failureOwners);
        boundary.entries.clear();
        router.run(outer, () -> router.run(outer, () -> { }));
        assertEquals(List.of("owner-a", "owner-a"), boundary.entries,
                "normal callbacks keep their original nested boundary semantics");
    }

    @Test void anotherRoutersSameOwnerDoesNotElideTheCapturedRoutersBoundary() {
        var capturedBoundary = new Boundary();
        var outerBoundary = new Boundary();
        var capturedRouter = new ObjectCallbackRouter(new Registry(capturedBoundary));
        var outerRouter = new ObjectCallbackRouter(new Registry(outerBoundary));
        var receiver = actor("owner-a");
        var outer = actor("owner-a");
        capturedRouter.register(receiver, receiver.getSpawn());
        outerRouter.register(outer, outer.getSpawn());
        var removal = capturedRouter.captureRemovalCallback(receiver);
        capturedRouter.unregister(receiver);
        receiver.removing = () -> assertEquals("owner-a", capturedBoundary.activeOwner);

        outerRouter.run(outer, removal::invoke);

        assertEquals(List.of("owner-a"), capturedBoundary.entries);
        assertEquals(List.of("owner-a"), outerBoundary.entries);
        assertEquals(1, receiver.removals);
    }

    @Test void nativeNullOwnerCallAndRemovalKeepExistingAmbientOwner() {
        var boundary = new Boundary();
        var router = new ObjectCallbackRouter(new Registry(boundary));
        var outer = actor("owner-a");
        var nativeReceiver = actor(null);
        var callChild = actor(null);
        var removalChild = actor(null);
        router.register(outer, outer.getSpawn());
        var removal = router.captureRemovalCallback(nativeReceiver);
        nativeReceiver.removing = () -> router.registerInherited(removalChild);

        router.run(outer, () -> {
            router.run(nativeReceiver, () -> router.registerInherited(callChild));
            removal.invoke();
        });

        assertEquals("owner-a", router.owner(callChild));
        assertEquals("owner-a", router.owner(removalChild));
        assertEquals(List.of("owner-a"), boundary.entries);
        assertNull(router.owner(nativeReceiver));
    }

    @Test void missingBoundaryDirectCallAndRemovalDoNotClearConstructScope() {
        var router = new ObjectCallbackRouter(new Registry(null));
        var nativeReceiver = actor(null);
        var ownedReceiver = actor("owner-b");
        var callChild = actor(null);
        var removalChild = actor(null);
        router.register(ownedReceiver, ownedReceiver.getSpawn());
        var removal = router.captureRemovalCallback(ownedReceiver);
        router.unregister(ownedReceiver);
        ownedReceiver.removing = () -> router.registerInherited(removalChild);

        router.construct("owner-a", () -> {
            router.run(nativeReceiver, () -> router.registerInherited(callChild));
            removal.invoke();
            return nativeReceiver;
        });

        assertEquals("owner-a", router.owner(callChild));
        assertEquals("owner-a", router.owner(removalChild));
        assertNull(router.owner(ownedReceiver));
    }

    @Test void destroyedPlacedAndDynamicExecAndFallbackPathsRetainRemovalOwnerAndOrder() {
        for (boolean counterBased : new boolean[] {false, true}) {
            for (boolean placed : new boolean[] {false, true}) {
                for (boolean fallback : new boolean[] {false, true}) {
                    for (boolean destroyedBeforeUpdate : new boolean[] {false, true}) {
                        var f = new Rig(placed, counterBased);
                        if (fallback) f.receiver.setSlotIndex(-1);
                        f.receiver.setDestroyed(destroyedBeforeUpdate);
                        f.receiver.updating = () -> f.receiver.setDestroyed(true);
                        f.receiver.unloading = () -> f.events.add("unload");
                        f.receiver.removing = () -> {
                            assertNull(f.manager.objectCallbacks().owner(f.receiver));
                            assertEquals("owner-a", f.boundary.activeOwner);
                            f.events.add("removed");
                        };

                        f.execute();

                        assertEquals(List.of("unload", "removed"), f.events,
                                "counter=" + counterBased + ", placed=" + placed + ", fallback=" + fallback
                                        + ", alreadyDestroyed=" + destroyedBeforeUpdate);
                        assertFalse(f.manager.getActiveObjects().contains(f.receiver));
                        assertNull(f.manager.objectCallbacks().owner(f.receiver));
                    }
                }
            }
        }
    }

    @Test void outOfRangePlacedAndDynamicPathsRetainRemovalOwnerAndOrder() {
        for (boolean counterBased : new boolean[] {false, true}) {
            for (boolean placed : new boolean[] {false, true}) {
                var f = new Rig(placed, counterBased);
                f.receiver.outOfRange = true;
                f.receiver.unloading = () -> f.events.add("unload");
                f.receiver.removing = () -> {
                    assertNull(f.manager.objectCallbacks().owner(f.receiver));
                    assertEquals("owner-a", f.boundary.activeOwner);
                    f.events.add("removed");
                };

                f.execute();

                assertEquals(List.of("unload", "removed"), f.events);
                assertFalse(f.manager.getActiveObjects().contains(f.receiver));
            }
        }
    }

    @Test void cleanupNotifiesBeforeIteratorRemovalWithoutRestoringRegistration() {
        var f = new Rig(false, false);
        f.receiver.setDestroyed(true);
        f.receiver.unloading = () -> f.events.add("unload");
        f.receiver.removing = () -> {
            assertEquals("owner-a", f.boundary.activeOwner);
            assertNull(f.manager.objectCallbacks().owner(f.receiver));
            assertTrue(f.manager.getActiveObjects().contains(f.receiver));
            f.events.add("removed");
        };

        f.manager.cleanupDestroyedDynamicObjects();

        assertEquals(List.of("unload", "removed"), f.events);
        assertFalse(f.manager.getActiveObjects().contains(f.receiver));
    }

    @Test void explicitDynamicRemovalStillNotifiesBeforeSlotClearAndUnload() {
        var f = new Rig(false, false);
        int slot = f.receiver.getSlotIndex();
        f.receiver.removing = () -> {
            assertEquals(slot, f.receiver.getSlotIndex());
            assertEquals("owner-a", f.boundary.activeOwner);
            assertFalse(f.manager.getActiveObjects().contains(f.receiver));
            f.events.add("removed");
        };
        f.receiver.unloading = () -> {
            assertEquals(-1, f.receiver.getSlotIndex());
            f.events.add("unload");
        };

        f.manager.removeDynamicObject(f.receiver);

        assertEquals(List.of("removed", "unload"), f.events);
        assertNull(f.manager.objectCallbacks().owner(f.receiver));
    }

    @Test void directPlacedRemovalCapturesBeforeUnregisterAndNotifiesAfterGraphRetirement() {
        var f = new Rig(true, false);
        f.receiver.removing = () -> {
            assertFalse(f.manager.getActiveObjects().contains(f.receiver));
            assertNull(f.manager.objectCallbacks().owner(f.receiver));
            assertEquals("owner-a", f.boundary.activeOwner);
        };

        f.manager.removeActiveObject(f.spawn);

        assertEquals(1, f.receiver.removals);
        assertEquals(0, f.receiver.unloads);
        assertNull(f.manager.objectCallbacks().owner(f.receiver));
    }

    @Test void unloadErrorStillUnregistersAndSkipsPreviouslyUnreachedRemoval() {
        for (String path : List.of("ordinary-placed", "ordinary-dynamic", "counter-placed", "counter-dynamic", "cleanup")) {
            var f = new Rig(path.endsWith("placed"), path.startsWith("counter"));
            f.receiver.setDestroyed(true);
            var failure = new AssertionError("unload fixture");
            f.receiver.unloading = () -> { throw failure; };

            assertSame(failure, assertThrows(AssertionError.class, () -> {
                if (path.equals("cleanup")) f.manager.cleanupDestroyedDynamicObjects();
                else f.execute();
            }));

            assertEquals(0, f.receiver.removals);
            assertTrue(f.manager.getActiveObjects().contains(f.receiver));
            assertNull(f.manager.objectCallbacks().owner(f.receiver));
            assertNull(f.boundary.activeOwner);
        }
    }

    @Test void postUnregisterRemovalChildInheritsOriginalOwnerWithoutRevivingReceiver() {
        var f = new Rig(true, false);
        var child = actor(null);
        f.receiver.setDestroyed(true);
        f.receiver.removing = () -> {
            assertNull(f.manager.objectCallbacks().owner(f.receiver));
            f.manager.addDynamicObject(child);
        };

        f.execute();

        assertEquals("owner-a", f.manager.objectCallbacks().owner(child));
        assertNull(f.manager.objectCallbacks().owner(f.receiver));
        assertTrue(f.manager.getActiveObjects().contains(child));
    }

    @Test void reentrantPlacedReplacementUsesActualReceiverAndItsStillRegisteredOwner() {
        var f = new Rig(true, false);
        var replacement = actor("owner-b");
        f.receiver.setDestroyed(true);
        f.receiver.unloading = () -> {
            f.manager.registerActiveObject(f.spawn, replacement);
            f.manager.objectCallbacks().register(replacement, replacement.getSpawn());
        };
        replacement.removing = () -> {
            assertEquals("owner-b", f.boundary.activeOwner);
            assertNull(f.manager.objectCallbacks().owner(replacement));
        };

        f.execute();

        assertEquals(0, f.receiver.removals);
        assertEquals(1, replacement.removals);
        assertNull(f.manager.objectCallbacks().owner(f.receiver));
        assertNull(f.manager.objectCallbacks().owner(replacement));
    }

    @Test void nativeFullDeleteRemovalFailureIsCaughtOnceAndSkipsLaterUnload() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        var boundary = new Boundary();
        boundary.containFailures = true;
        var camera = new Camera(SonicConfigurationService.getInstance());
        var roster = mock(NativeCompetitionRoster.class);
        when(roster.camera(0)).thenReturn(camera);
        when(roster.camera(1)).thenReturn(camera);
        var module = mock(GameModule.class);
        var policy = mock(WorldSessionPolicyState.class);
        when(policy.getService(NativeCompetitionRoster.class)).thenReturn(roster);
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(world -> policy);
        var world = new WorldSession(module);
        var spawn = ownedSpawn("owner-a");
        ObjectManager[] holder = new ObjectManager[1];
        var manager = new ObjectManager(List.of(spawn), new Registry(boundary), -1, null, null, null, camera,
                new StubObjectServices() {
                    @Override public ObjectManager objectManager() { return holder[0]; }
                    @Override public Camera camera() { return camera; }
                    @Override public WorldSession worldSession() { return world; }
                });
        holder[0] = manager;
        List<Integer> clears = new ArrayList<>();
        ObjectManagerInternalAccess.enableNativeCompetitionStreaming(manager, new Sonic2CompetitionObjectStreaming(), slot -> {
            clears.add(slot);
            NativeSstSlotAccess.clearWord2C(manager, slot);
        });
        manager.reset(0);
        var receiver = (Actor) manager.getActiveObjects().iterator().next();
        int slot = receiver.getSlotIndex();
        var child = actor(null);
        var failure = new AssertionError("native removal fixture");
        receiver.removing = () -> {
            assertEquals("owner-a", boundary.activeOwner);
            assertNull(manager.objectCallbacks().owner(receiver));
            assertEquals(-1, receiver.getSlotIndex());
            assertNull(NativeSstSlotAccess.currentOwner(manager, slot));
            manager.objectCallbacks().registerInherited(child);
            throw failure;
        };

        manager.nativeCompetitionStreaming().unload(slot, 1);

        assertEquals(List.of(slot), clears);
        assertEquals(List.of(failure), boundary.failures);
        assertEquals(List.of("owner-a"), boundary.failureOwners);
        assertEquals(1, receiver.removals);
        assertEquals(0, receiver.unloads);
        assertEquals("owner-a", manager.objectCallbacks().owner(child));
        assertNull(manager.objectCallbacks().owner(receiver));
        assertNull(boundary.activeOwner);
        assertTrue(manager.reserveDynamicSlot(slot));
    }

    private static ObjectSpawn ownedSpawn(String owner) {
        return new ObjectSpawn(0x20, 0x100, 1, 0, 0, true, 0x100, 0,
                owner, owner == null ? null : owner + ":removal-fixture");
    }

    private static Actor actor(String owner) { return new Actor(ownedSpawn(owner)); }

    private static final class Actor extends AbstractObjectInstance {
        int removals, unloads;
        boolean outOfRange;
        @RewindTransient(reason = "test-owned callback binding") Runnable removing = () -> { };
        @RewindTransient(reason = "test-owned callback binding") Runnable unloading = () -> { };
        @RewindTransient(reason = "test-owned callback binding") Runnable updating = () -> { };
        Actor(ObjectSpawn spawn) { super(spawn, "removal ownership fixture"); }
        @Override public int getLiveObjectId() { return 1; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) { return outOfRange; }
        @Override protected void onRemovedFromObjectManager() { removals++; removing.run(); }
        @Override public void onUnload() { unloads++; unloading.run(); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { updating.run(); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class Boundary implements ObjectCallbackBoundary {
        final List<String> entries = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        final List<String> failureOwners = new ArrayList<>();
        String activeOwner;
        boolean containFailures;
        @Override public <T> T call(String owner, Supplier<T> callback) {
            entries.add(owner);
            String previous = activeOwner;
            activeOwner = owner;
            try { return callback.get(); }
            catch (RuntimeException | Error failure) {
                failures.add(failure);
                failureOwners.add(owner);
                if (!containFailures) throw failure;
                return null;
            } finally { activeOwner = previous; }
        }
    }

    private static final class OwnershipLookup implements Supplier<String>, Function<ObjectInstance, String> {
        ObjectInstance forbidden;
        @Override public String get() { return "scoped-owner"; }
        @Override public String apply(ObjectInstance receiver) {
            assertNotSame(forbidden, receiver, "removal must not re-read creation ownership");
            return null;
        }
    }

    private record Registry(Object callbackBinding) implements ObjectRegistry, Supplier<Object> {
        @Override public Object get() { return callbackBinding; }
        @Override public ObjectInstance create(ObjectSpawn spawn) { return new Actor(spawn); }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int id) { return "removal ownership fixture"; }
        @Override public boolean hasObjectKey(String key) { return true; }
        @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_2; }
    }

    private static final class Rig {
        final Boundary boundary = new Boundary();
        final List<String> events = new ArrayList<>();
        final ObjectSpawn spawn = ownedSpawn("owner-a");
        final ObjectManager manager;
        final Actor receiver;
        final boolean counterBased;
        Rig(boolean placed, boolean counterBased) {
            this.counterBased = counterBased;
            manager = new ObjectManager(placed ? List.of(spawn) : List.of(), new Registry(boundary), -1, null, null);
            if (counterBased) manager.enableCounterBasedRespawn();
            manager.reset(0);
            if (placed) receiver = (Actor) manager.getActiveObjects().iterator().next();
            else {
                receiver = actor("owner-a");
                manager.objectCallbacks().register(receiver, spawn);
                manager.addDynamicObject(receiver);
            }
            boundary.entries.clear();
        }
        void execute() {
            if (counterBased) manager.update(0, null, null, 1, false);
            else manager.runExecLoop(0, null, List.of(), false, false);
        }
    }
}
