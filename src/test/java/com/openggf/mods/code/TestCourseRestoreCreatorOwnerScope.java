package com.openggf.mods.code;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServiceBundle;
import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.CourseControlProbeAccess;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.HCZCGZFanObjectInstance;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.io.ModAssetRoot;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A course checkpoint restored from inside a creator callback (a frame controller's
 * {@code beforeTick}, where the Typing of the Hedgehog course restores its stands) rebuilds
 * engine objects as engine objects. Hydrocity's original-slot sliding platform allocates a
 * spawnless dynamic fan. Before native slot roles were corrected the platform was the child;
 * a leaked creator owner caused that child to be dropped during restoration. Keep exercising
 * the actual dynamic fan identity through the same callback boundary after the role repair.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestCourseRestoreCreatorOwnerScope {
    static final String OWNER = "restore-owner";
    private SharedLevel bootstrap;

    @AfterEach void dispose() {
        if (bootstrap != null) bootstrap.dispose();
        bootstrap = null;
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    static final class Neutral implements GameplayFrameController,
            com.openggf.game.rewind.RewindSnapshottable<Integer> {
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) { return true; }
        @Override public void afterTick(CourseControl course, boolean advanced) { }
        @Override public String key() { return "neutral-course"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer value) { }
    }

    /** HCZ1 with a mod whose registered object makes the object registry creator-aware. */
    private HeadlessTestFixture launch(int x, int y) throws Exception {
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_HCZ, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var context = new ModContext(OWNER, "s3k", ModAssetRoot.forTests(OWNER));
        context.registerObject("marker", (spawn, registry) -> null);
        context.registerServiceBundle("course", () -> GameServiceBundle.builder()
                .frameController("course", new Neutral()).build());
        var boundary = new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), owners -> { });
        GameModule effective = new ModBackedGamePatch(context.freeze(), boundary).apply(GameServices.module(), null);
        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0);
        if (x >= 0) builder.startPosition((short) x, (short) y).startPositionIsCentre();
        return builder.build();
    }

    private static List<ObjectInstance> fans() {
        var query = GameServices.level().getObjectManager().objectQuery();
        List<ObjectInstance> out = new ArrayList<>();
        for (ObjectInstance o : query.activeObjectsOfType(ObjectInstance.class)) {
            if (o instanceof HCZCGZFanObjectInstance && (o.getSpawn().subtype() & 0x80) != 0) out.add(o);
        }
        return out;
    }

    private static ObjectRefId identity(ObjectInstance o) {
        return GameServices.level().getObjectManager().objectQuery().identityOf(o).orElse(null);
    }

    @Test void restoreInsideACreatorCallbackRebuildsTheFanPlatformAsAnEngineObject() throws Exception {
        restoreBesideTheFan(true);
    }

    /** Control: the same restore with no creator callback in scope always rebuilt the platform. */
    @Test void restoreOutsideACreatorCallbackRebuildsTheFanPlatform() throws Exception {
        restoreBesideTheFan(false);
    }

    private void restoreBesideTheFan(boolean insideCreatorCallback) throws Exception {
        // Find a fan whose subtype spawns the sliding platform (bit 7), then start beside it.
        launch(-1, -1);
        ObjectSpawn fan = GameServices.level().getObjectManager().getAllSpawns().stream()
                .filter(s -> s.objectId() == Sonic3kObjectIds.HCZ_CGZ_FAN && (s.subtype() & 0x80) != 0)
                .findFirst().orElseThrow(() -> new AssertionError("HCZ1 has a platform fan"));
        dispose();
        var fixture = launch(fan.x() - 0x40, fan.y() - 0x20);
        for (int i = 0; i < 120 && fans().isEmpty(); i++) fixture.stepFrame(false, false, false, false, false);
        List<ObjectInstance> before = fans();
        assertFalse(before.isEmpty(), "the platform's fan child is live beside its platform");
        ObjectRefId captured = identity(before.getFirst());
        assertNotNull(captured, "the dynamic fan has a rewind identity at capture");
        assertEquals(-1, captured.spawnId(), "the fan is a spawnless dynamic child");

        CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        var checkpoint = course.capture();
        for (int i = 0; i < 30; i++) fixture.stepFrame(false, false, false, false, false);

        // The course restores its stands from inside the mod's frame-controller callback.
        if (insideCreatorCallback) {
            OwnerCallbackScope.call(OWNER, () -> {
                course.restore(checkpoint);
                assertEquals(OWNER, OwnerCallbackScope.current(), "the creator's own frame is reinstated after the restore");
                return null;
            });
            assertNull(OwnerCallbackScope.current(), "no owner frame outlives the callback");
        } else {
            course.restore(checkpoint);
        }

        List<ObjectInstance> after = fans();
        assertTrue(after.stream().anyMatch(p -> captured.equals(identity(p))),
                "the restore rebuilds the fan under its captured identity " + captured
                        + "; live fans " + after.stream().map(TestCourseRestoreCreatorOwnerScope::identity).toList());
        for (ObjectInstance p : after) {
            assertNull(callbackOwner(p), "an engine object rebuilt by a restore has no creator owner");
        }
    }

    /** The object manager's callback owner for {@code object} (package-private engine state). */
    private static String callbackOwner(ObjectInstance object) throws Exception {
        var manager = GameServices.level().getObjectManager();
        var routerMethod = manager.getClass().getDeclaredMethod("objectCallbacks");
        routerMethod.setAccessible(true);
        Object router = routerMethod.invoke(manager);
        var owner = router.getClass().getDeclaredMethod("owner", ObjectInstance.class);
        owner.setAccessible(true);
        return (String) owner.invoke(router, object);
    }

    @Test
    void capturedPlacementOwnerSurvivesRestoreInsideADifferentRegisteredController() throws Exception {
        restoreRegisteredOwnerGraph(false);
    }

    @Test
    void failedReconstructionRestoresTheOuterOwnerAndKeepsTheOriginalFaultAttribution() throws Exception {
        restoreRegisteredOwnerGraph(true);
    }

    private void restoreRegisteredOwnerGraph(boolean failReconstruction) throws Exception {
        String capturedOwner = "captured-restore-owner";
        String outerOwner = "outer-restore-owner";
        assertNotEquals(capturedOwner, outerOwner);
        bootstrap = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_HCZ, 0);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var findings = new com.openggf.mods.ModRuntimeFindingStore();
        List<java.util.Set<String>> disabled = new ArrayList<>();
        var boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new com.openggf.mods.ModStateSaveResult.Saved(), disabled::add);
        List<String> factoryOwners = new ArrayList<>();
        RuntimeException[] factoryFailure = {null};
        var graph = new RegisteredRestoreGraph();
        var capturedContext = new ModContext(capturedOwner, "s3k", ModAssetRoot.forTests(capturedOwner));
        capturedContext.registerObject("wall", (spawn, registry) -> {
            factoryOwners.add(OwnerCallbackScope.current());
            if (factoryFailure[0] != null) throw factoryFailure[0];
            return new com.openggf.game.sonic3k.objects.HCZ2WallObjectInstance();
        });
        capturedContext.registerRewindAdapter("graph", graph);
        GameModule capturedModule = new ModBackedGamePatch(capturedContext.freeze(), boundary)
                .apply(GameServices.module(), null);
        var outer = new RestoringController();
        var outerContext = new ModContext(outerOwner, "s3k", ModAssetRoot.forTests(outerOwner));
        outerContext.registerServiceBundle("course", () -> GameServiceBundle.builder()
                .frameController("course", outer).build());
        GameModule effective = new ModBackedGamePatch(outerContext.freeze(), boundary).apply(capturedModule, null);

        // A real registered placement, captured through the course's contributed adapter.
        // It needs neither a creator-class resolver nor ownership/slot injection.
        var camera = new com.openggf.camera.Camera();
        var spawn = new ObjectSpawn(0x100, 0x80, 0xFF, 0, 0, false, 0x80, 0,
                capturedOwner, capturedOwner + ":wall");
        var services = new com.openggf.level.objects.StubObjectServices() {
            @Override public com.openggf.level.objects.ObjectManager objectManager() { return graph.manager; }
            @Override public com.openggf.camera.Camera camera() { return camera; }
            @Override public GameModule gameModule() { return effective; }
        };
        graph.manager = new com.openggf.level.objects.ObjectManager(List.of(spawn),
                effective.createObjectRegistry(), 0, null, null, null, camera, services);
        graph.manager.reset(0);
        graph.manager.preloadInitialSpawnsForHydration();
        graph.manager.setRewindInPlaceRestoreEnabledForTest(false);
        ObjectInstance before = graph.manager.getActiveObjectForRewind(spawn);
        assertNotNull(before);
        assertEquals(capturedOwner, callbackOwner(graph.manager, before));
        var beforeSnapshot = graph.capture();
        assertEquals(spawn, beforeSnapshot.slots().getFirst().spawn());
        assertEquals(capturedOwner, factoryOwners.getLast());

        SessionManager.clear();
        GameModuleRegistry.setCurrent(effective);
        TestEnvironment.activeGameplayMode();
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        CourseControl course = CourseControlProbeAccess.open(SessionManager.getCurrentGameplayMode());
        outer.checkpoint = course.capture();
        int constructionsBeforeRestore = factoryOwners.size();
        var failure = new IllegalStateException("registered reconstruction failed");
        if (failReconstruction) factoryFailure[0] = failure;

        // Dispatch the controller published by registration, rather than seeding an owner frame.
        GameplayFrameController caller = effective.gameplayFrameController();
        if (failReconstruction) {
            var aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                    () -> caller.beforeTick(course, LogicalInputSnapshot.neutral()));
            assertSame(failure, aborted.getCause(), "the reconstruction's primary throwable survives both boundaries");
            assertEquals(capturedOwner, aborted.owner(), "the failing factory keeps its registered owner");
            assertEquals(List.of(java.util.Set.of(capturedOwner)), disabled);
            assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor(capturedOwner).getFirst().code());
            assertTrue(findings.findingsFor(outerOwner).isEmpty(), "the calling controller is not blamed twice");
        } else {
            assertFalse(caller.beforeTick(course, LogicalInputSnapshot.neutral()));
            ObjectInstance restored = graph.manager.getActiveObjectForRewind(spawn);
            assertNotNull(restored);
            assertEquals(capturedOwner, callbackOwner(graph.manager, restored));
            assertNotSame(before, restored, "the control must exercise factory reconstruction, not in-place reuse");
            assertEquals(beforeSnapshot.slots().getFirst().objectId(),
                    graph.capture().slots().getFirst().objectId());
            assertTrue(disabled.isEmpty());
        }
        assertEquals(constructionsBeforeRestore + 1, factoryOwners.size());
        assertEquals(capturedOwner, factoryOwners.getLast(),
                "reconstruction enters the captured placement owner, never the outer controller");
        assertEquals(outerOwner, outer.ownerAfterRestore, "finally reinstates the exact outer callback owner");
        assertNull(OwnerCallbackScope.current(), "the registered callback leaves no owner frame behind");
    }

    private static String callbackOwner(com.openggf.level.objects.ObjectManager manager,
            ObjectInstance object) throws Exception {
        var routerMethod = manager.getClass().getDeclaredMethod("objectCallbacks");
        routerMethod.setAccessible(true);
        Object router = routerMethod.invoke(manager);
        var owner = router.getClass().getDeclaredMethod("owner", ObjectInstance.class);
        owner.setAccessible(true);
        return (String) owner.invoke(router, object);
    }

    private static final class RegisteredRestoreGraph implements
            com.openggf.game.rewind.RewindSnapshottable<com.openggf.game.rewind.snapshot.ObjectManagerSnapshot> {
        private com.openggf.level.objects.ObjectManager manager;
        @Override public String key() { return "registered-restore-graph"; }
        @Override public com.openggf.game.rewind.snapshot.ObjectManagerSnapshot capture() {
            return manager.rewindSnapshottable().capture();
        }
        @Override public void restore(com.openggf.game.rewind.snapshot.ObjectManagerSnapshot snapshot) {
            manager.rewindSnapshottable().restore(snapshot);
        }
    }

    private static final class RestoringController implements GameplayFrameController,
            com.openggf.game.rewind.RewindSnapshottable<Integer> {
        private com.openggf.game.mode.CourseCheckpoint checkpoint;
        private String ownerAfterRestore;
        @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot input) {
            try {
                course.restore(checkpoint);
                return false;
            } finally {
                ownerAfterRestore = OwnerCallbackScope.current();
            }
        }
        @Override public void afterTick(CourseControl course, boolean advanced) { }
        @Override public String key() { return "restoring-course"; }
        @Override public Integer capture() { return 0; }
        @Override public void restore(Integer value) { }
    }
}
