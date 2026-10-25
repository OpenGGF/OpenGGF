package com.openggf.game.rewind;

import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorFactory;
import com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSlotLayout;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3kMonitorGraphRewind {
    private static final String CONTENTS_CLASS =
            "com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance$MonitorContentsSlot";
    private static final String MONITOR_TYPE_CLASS =
            "com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance$MonitorType";
    private static final ObjectSpawn MONITOR_SPAWN =
            new ObjectSpawn(0x0580, 0x0520, Sonic3kObjectIds.MONITOR, 0x03, 0, false, 0x0520, 11);
    private static final ObjectSpawn DIVERGENT_CONTENTS_SPAWN =
            new ObjectSpawn(0x05e0, 0x0528, Sonic3kObjectIds.MONITOR, 0x00, 0, false, 19);

    @BeforeEach
    void initHeadless() {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void independentMonitorContentsRestoreTheirOwnExactState() throws Exception {
        Harness harness = Harness.create();
        ObjectManager objectManager = harness.objectManager();
        objectManager.setRewindInPlaceRestoreEnabledForTest(false);
        Sonic3kMonitorObjectInstance sourceMonitor = only(objectManager, Sonic3kMonitorObjectInstance.class);
        writeBooleanField(sourceMonitor, "broken", true);
        writeIntField(sourceMonitor, "mappingFrame", 0x0B);
        spawnMonitorContents(sourceMonitor, objectManager);
        ObjectInstance sourceContents = onlyContents(objectManager);
        writeIntField(sourceContents, "iconSubY", 0x0500 << 8);
        writeIntField(sourceContents, "iconVelY", -0x240);
        writeIntField(sourceContents, "iconWaitFrames", 9);
        writeBooleanField(sourceContents, "effectApplied", false);
        ObjectRefId monitorId = objectId(objectManager, sourceMonitor);
        ObjectRefId contentsId = objectId(objectManager, sourceContents);

        RewindRegistry rewindRegistry = registryFor(objectManager);
        CompositeSnapshot snapshot = rewindRegistry.capture();

        objectManager.removeDynamicObject(sourceContents);
        ObjectInstance divergentContents = objectManager.createDynamicObject(
                () -> constructContents(DIVERGENT_CONTENTS_SPAWN, "EGGMAN"));
        assertEquals(1, liveContents(objectManager).size(),
                "diverge step should leave one unrelated S3K monitor contents object before restore");

        rewindRegistry.restore(snapshot);

        Sonic3kMonitorObjectInstance restoredMonitor =
                objectById(objectManager, Sonic3kMonitorObjectInstance.class, monitorId);
        ObjectInstance restoredContents = objectByIdRaw(objectManager, Class.forName(CONTENTS_CLASS), contentsId);
        assertEquals(1, liveContents(objectManager).size(),
                "restore must keep exactly the captured S3K monitor contents object");
        assertNotSame(sourceMonitor, restoredMonitor,
                "restore must recreate the S3K monitor shell");
        assertNotSame(sourceContents, restoredContents,
                "restore must recreate the S3K monitor contents object");
        assertNotSame(divergentContents, restoredContents,
                "restore must drop the divergent S3K monitor contents object");
        assertTrue(readBooleanField(restoredMonitor, "broken"),
                "restored monitor must keep captured broken-shell state");
        assertEquals(0x0B, readIntField(restoredMonitor, "mappingFrame"),
                "restored monitor must keep captured mapping frame");
        assertFalse(readBooleanField(restoredMonitor, "iconActive"),
                "a managed shell does not run the contents routine itself");
        assertTrue(readBooleanField(restoredContents, "iconActive"),
                "restored contents must keep captured active state");
        assertEquals(0x0500 << 8, readIntField(restoredContents, "iconSubY"),
                "restored contents must keep captured subpixel y");
        assertEquals(-0x240, readIntField(restoredContents, "iconVelY"),
                "restored contents must keep captured velocity");
        assertEquals(9, readIntField(restoredContents, "iconWaitFrames"),
                "restored contents must keep captured wait frames");
        assertEquals("RINGS", readObjectField(restoredContents, "type").toString(),
                "restored contents must keep the captured content type");
        assertEquals(MONITOR_SPAWN.x(), restoredContents.getX(),
                "restored contents keep their own x");
        assertEquals(0x0500, restoredContents.getY(),
                "restored contents read y from their own rise state");
    }

    @Test
    void monitorFamilyUsesGenericRecreateWithoutExplicitS3kCodecs() throws Exception {
        assertTrue(RewindRecreatable.class.isAssignableFrom(Sonic3kMonitorObjectInstance.class),
                "S3K monitor must restore through generic recreate");
        assertTrue(RewindRecreatable.class.isAssignableFrom(Class.forName(CONTENTS_CLASS)),
                "S3K monitor contents slot must restore through generic graph recreate");
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,false", "2,false", "0,true", "1,true", "2,true"})
    void nativeAdmissionCapturesBeforeDispatchAndReplaysExactGraph(int freeTailSlots, boolean eggman)
            throws Exception {
        AdmissionHarness h = AdmissionHarness.create(freeTailSlots, eggman);
        var manager = h.manager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var monitor = only(manager, Sonic3kMonitorObjectInstance.class);
        ObjectRefId monitorId = objectId(manager, monitor);
        var adapter = manager.rewindSnapshottable();
        var intact = adapter.capture();
        long rngSeed = h.services().rng().getSeed();

        breakMonitor(monitor, h.player());
        // Capture before either independent sibling has its first object pass.
        var broken = assertDoesNotThrow(adapter::capture);
        assertDoesNotThrow(manager::validateRewindReferenceClosure);
        assertTrue(manager.isRemembered(MONITOR_SPAWN));
        assertEquals(List.of(CONTENTS_CLASS, ExplosionObjectInstance.class.getName()),
                manager.admissions.stream().map(a -> a.object().getClass().getName()).toList());
        assertTrue(manager.admissions.stream().allMatch(a -> a.anchor() == monitor.getSlotIndex()));
        assertEquals(List.of(freeTailSlots > 0, freeTailSlots > 1),
                manager.admissions.stream().map(a -> !a.object().isDestroyed()).toList());
        for (int i = 0; i < 2; i++) {
            ObjectInstance child = manager.admissions.get(i).object();
            boolean admitted = freeTailSlots > i;
            assertEquals(admitted ? monitor.getSlotIndex() + 1 + i * 2 : -1,
                    ((AbstractObjectInstance) child).getSlotIndex());
            assertEquals(admitted, manager.captureIdentityContext().requireIdentityTable().idFor(child) != null);
            if (admitted) {
                ObjectRefId childId = manager.admissions.get(i).id();
                var entry = broken.dynamicObjects().stream()
                        .filter(candidate -> childId.equals(candidate.objectId()))
                        .findFirst().orElseThrow();
                assertEquals(child.getClass().getName(), entry.className());
                assertEquals(child.getSpawn(), entry.spawn());
                assertEquals(((AbstractObjectInstance) child).getSlotIndex(), entry.slotIndex());
                assertSame(child, manager.captureIdentityContext().requireIdentityTable().resolve(entry.objectId()));
            }
        }
        assertFalse(readBooleanField(monitor, "iconActive"), "a managed shell never runs the contents routine");
        assertNull(readObjectField(monitor, "effectTarget"), "the contents slot owns the breaking player");
        assertEquals(0, monitor.getCollisionFlags());
        assertEquals(0x0B, readIntField(monitor, "mappingFrame"));
        assertEquals(List.of("sfx:" + Sonic3kSfx.BREAK.id), h.events(),
                "the existing parent-owned break SFX request remains unchanged even on allocation failure");
        ObjectInstance contents = manager.admissions.getFirst().object();
        assertEquals(MONITOR_SPAWN, contents.getSpawn(),
                "content overrides retain the canonical placement metadata");
        assertEquals(eggman ? "EGGMAN" : "RINGS", readObjectField(contents, "type").toString(),
                "Obj_MonitorSpawnIcon copies the selected animation, not just the placement subtype");
        assertEquals(freeTailSlots > 0, readBooleanField(contents, "iconActive"));
        assertEquals(freeTailSlots > 0 ? 1 : 0,
                broken.dynamicObjects().stream().filter(entry -> CONTENTS_CLASS.equals(entry.className())).count(),
                "only admitted contents may enter the captured graph");
        if (freeTailSlots == 0) {
            assertTrue(liveContents(manager).isEmpty());
            assertNull(readObjectField(contents, "effectTarget"));
        } else {
            assertSame(contents, onlyContents(manager));
            assertSame(h.player(), readObjectField(contents, "effectTarget"));
        }
        // Neither sibling retains a managed shell link. The contents' existing
        // monitor extra owns the recipient code; strict object links are checked
        // on the real touch callback below.
        var noTable = assertDoesNotThrow(() -> { return monitor.captureRewindState(); });
        monitor.restoreRewindState(noTable);
        assertFalse(readBooleanField(monitor, "iconActive"));
        assertNull(readObjectField(monitor, "effectTarget"));
        assertEquals(new PerObjectRewindSnapshot.MonitorRewindExtra(null), noTable.objectSubclassExtra());
        var contentsState = assertDoesNotThrow(() -> { return ((AbstractObjectInstance) contents).captureRewindState(); });
        assertEquals(new PerObjectRewindSnapshot.MonitorRewindExtra(freeTailSlots > 0 ? "sonic" : null),
                contentsState.objectSubclassExtra());
        if (freeTailSlots > 0) {
            var capturedContents = broken.dynamicObjects().stream()
                    .filter(entry -> CONTENTS_CLASS.equals(entry.className())).findFirst().orElseThrow();
            assertEquals(contentsState.objectSubclassExtra(), capturedContents.state().objectSubclassExtra());
            assertNull(capturedContents.playerOwner(),
                    "the contents recipient is captured as monitor metadata, never a live player reference");
        }
        var outcomes = new ArrayList<ObjectManagerSnapshot>();
        int eventsAtBreak = h.events().size();
        for (int tick = 1; tick <= 65; tick++) {
            manager.update(0x0500, h.player(), List.of(), tick);
            if (tick == 1) {
                assertEquals((MONITOR_SPAWN.y() << 8) - (freeTailSlots > 0 ? 0x300 : 0),
                        readIntField(contents, "iconSubY"),
                        "only admitted contents execute their first native rise step");
                assertFalse(readBooleanField(monitor, "iconActive"));
            }
            outcomes.add(adapter.capture());
        }
        assertEquals(freeTailSlots == 0 ? 0 : 1,
                h.events().stream().filter("reward"::equals).count());
        verify(h.player(), times(freeTailSlots > 0 && !eggman ? 1 : 0)).addRings(10);
        verify(h.player(), times(freeTailSlots > 0 && eggman ? 1 : 0)).applyHurtOrDeath(0, false, true);
        assertEquals(rngSeed, h.services().rng().getSeed(), "monitor admission consumes no RNG");
        List<String> replayEvents = List.copyOf(h.events().subList(eventsAtBreak, h.events().size()));

        for (ObjectInstance object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        adapter.restore(broken);
        var restored = objectById(manager, Sonic3kMonitorObjectInstance.class, monitorId);
        assertNotSame(monitor, restored);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", broken, adapter.capture()));
        if (freeTailSlots > 0) {
            var restoredContents = onlyContents(manager);
            assertNotSame(contents, restoredContents);
            assertEquals(manager.admissions.getFirst().id(), objectId(manager, restoredContents));
            assertEquals(MONITOR_SPAWN, restoredContents.getSpawn());
            assertEquals(eggman ? "EGGMAN" : "RINGS", readObjectField(restoredContents, "type").toString());
            assertTrue(readBooleanField(restoredContents, "iconActive"));
            assertSame(h.player(), readObjectField(restoredContents, "effectTarget"));
            assertEquals(contentsState.objectSubclassExtra(),
                    ((AbstractObjectInstance) restoredContents).captureRewindState().objectSubclassExtra());
        } else {
            assertTrue(liveContents(manager).isEmpty(), "restore cannot reconstruct a refused contents slot");
        }
        assertFalse(readBooleanField(restored, "iconActive"));
        assertNull(readObjectField(restored, "effectTarget"));
        assertDoesNotThrow(manager::validateRewindReferenceClosure);
        h.events().clear();
        int callsAtRestore = manager.admissions.size();
        for (int tick = 1; tick <= 65; tick++) {
            manager.update(0x0500, h.player(), List.of(), tick);
            assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", outcomes.get(tick - 1), adapter.capture()),
                    "exact IDs, slots, occupant state and deletion at tick " + tick);
        }
        assertEquals(replayEvents, h.events());
        assertEquals(callsAtRestore, manager.admissions.size(), "restore cannot retry a rejected allocation");
        assertEquals(rngSeed, h.services().rng().getSeed());

        // Rewind before the break, then remint exactly the same graph and allocation ordinals.
        adapter.restore(intact);
        h.events().clear();
        manager.admissions.clear();
        breakMonitor(objectById(manager, Sonic3kMonitorObjectInstance.class, monitorId), h.player());
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", broken, adapter.capture()));
        assertEquals(2, manager.admissions.size());

        var persistence = manager.capturePersistentRespawn();
        manager.reset(0x0500, persistence);
        manager.update(0x0500, h.player(), List.of(), 66);
        var rememberedShell = only(manager, Sonic3kMonitorObjectInstance.class);
        assertTrue(readBooleanField(rememberedShell, "broken"));
        assertFalse(readBooleanField(rememberedShell, "iconActive"));
        assertNull(readObjectField(rememberedShell, "effectTarget"));
        assertEquals(0x0B, readIntField(rememberedShell, "mappingFrame"));
        assertTrue(manager.isRemembered(MONITOR_SPAWN));
        assertTrue(liveContents(manager).isEmpty(), "a remembered shell has no pending contents routine");
        breakMonitor(rememberedShell, h.player());
        assertEquals(2, manager.admissions.size(), "remembered respawn cannot retry either allocation");
    }

    @Test
    void rejectedContentsNeverAwardEvenWithoutAnImmediateCapture() throws Exception {
        AdmissionHarness h = AdmissionHarness.create(0, false);
        var monitor = only(h.manager(), Sonic3kMonitorObjectInstance.class);
        breakMonitor(monitor, h.player());
        var refused = h.manager().admissions.getFirst().object();
        assertTrue(refused.isDestroyed());
        assertFalse(readBooleanField(refused, "iconActive"));
        assertNull(readObjectField(refused, "effectTarget"));
        for (int tick = 1; tick <= 65; tick++) h.manager().update(0x0500, h.player(), List.of(), tick);
        assertFalse(h.events().contains("reward"), "rejected native contents have no reward routine");
        verify(h.player(), never()).addRings(10);
        verify(h.player(), never()).applyHurtOrDeath(0, false, true);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 80})
    void touchDuringAnotherSlotKeepsMonitorAnchorAndDispatchPhase(int callbackSlot) throws Exception {
        AdmissionHarness h = AdmissionHarness.create(2, false);
        var manager = h.manager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        var monitor = only(manager, Sonic3kMonitorObjectInstance.class);
        manager.getActiveObjects().stream()
                .filter(o -> o instanceof InertOccupant a && a.getSlotIndex() == callbackSlot)
                .findFirst().ifPresent(manager::removeDynamicObject);
        var trigger = new BreakTrigger(MONITOR_SPAWN, monitor);
        manager.addDynamicObjectAtSlot(trigger, callbackSlot);
        assertThrows(IllegalStateException.class, trigger::captureRewindState,
                "the actual callback's non-null monitor link still requires an identity table");
        assertDoesNotThrow(() -> trigger.captureRewindState(manager.captureIdentityContext()));

        manager.update(0x0500, h.player(), List.of(), 1);
        assertEquals(2, manager.admissions.size());
        assertTrue(manager.admissions.stream().allMatch(a -> a.anchor() == monitor.getSlotIndex()));
        assertEquals(List.of(monitor.getSlotIndex() + 1, monitor.getSlotIndex() + 3),
                manager.admissions.stream().map(a -> ((AbstractObjectInstance) a.object()).getSlotIndex()).toList());
        var contents = onlyContents(manager);
        int contentsSlot = ((AbstractObjectInstance) contents).getSlotIndex();
        boolean runsThisPass = contentsSlot > callbackSlot;
        int firstY = (MONITOR_SPAWN.y() << 8) - (runsThisPass ? 0x300 : 0);
        int firstVelocity = -0x300 + (runsThisPass ? 0x18 : 0);
        assertEquals(firstY, readIntField(contents, "iconSubY"),
                "a contents slot ahead of the touch callback executes this pass; a passed slot waits");
        assertEquals(firstVelocity, readIntField(contents, "iconVelY"));
        assertFalse(readBooleanField(monitor, "iconActive"));
        assertSame(h.player(), readObjectField(contents, "effectTarget"));
        var adapter = manager.rewindSnapshottable();
        var pending = adapter.capture();
        manager.update(0x0500, h.player(), List.of(), 2);
        assertEquals(firstY + firstVelocity, readIntField(contents, "iconSubY"));
        var dispatched = adapter.capture();
        for (var object : List.copyOf(manager.getActiveObjects())) manager.removeDynamicObject(object);
        adapter.restore(pending);
        manager.update(0x0500, h.player(), List.of(), 2);
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", dispatched, adapter.capture()));
        assertEquals(2, manager.admissions.size(), "replaying a fired callback cannot allocate another child pair");
    }

    private static void breakMonitor(Sonic3kMonitorObjectInstance monitor, AbstractPlayableSprite player) {
        monitor.onTouchResponse(player, new TouchResponseResult(0, 14, 14, TouchCategory.SPECIAL), 1);
    }

    private record Admission(ObjectInstance object, int anchor, ObjectRefId id) { }

    /** Records the actual calls after running the production allocator and identity registration. */
    private static final class AdmissionManager extends ObjectManager {
        private final List<Admission> admissions = new ArrayList<>();

        private AdmissionManager(Camera camera, ObjectServices services, boolean eggman) {
            super(List.of(MONITOR_SPAWN), new Sonic3kObjectRegistry() {
                @Override public ObjectInstance create(ObjectSpawn spawn) {
                    return eggman && spawn.objectId() == Sonic3kObjectIds.MONITOR
                            ? Sonic3kMonitorFactory.eggman(spawn) : super.create(spawn);
                }
            }, 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
        }

        @Override public void addDynamicObjectAfterSlot(ObjectInstance object, int anchor) {
            super.addDynamicObjectAfterSlot(object, anchor);
            admissions.add(new Admission(object, anchor, captureIdentityContext().requireIdentityTable().idFor(object)));
        }
    }

    private record AdmissionHarness(AdmissionManager manager, AbstractPlayableSprite player,
                                    ObjectServices services, List<String> events) {
        static AdmissionHarness create(int freeTailSlots, boolean eggman) {
            var player = mock(AbstractPlayableSprite.class);
            when(player.getCode()).thenReturn("sonic");
            when(player.getAnimationId()).thenReturn(Sonic3kAnimationIds.ROLL.id());
            when(player.getRingCount()).thenReturn(1);
            var events = new ArrayList<String>();
            doAnswer(call -> { events.add("reward"); return null; }).when(player).addRings(10);
            doAnswer(call -> { events.add("reward"); return null; }).when(player).applyHurtOrDeath(0, false, true);
            var sprites = mock(SpriteManager.class);
            when(sprites.getSprite("sonic")).thenReturn(player);
            var render = mock(ObjectRenderManager.class);
            when(render.getExplosionRenderer()).thenReturn(mock(PatternSpriteRenderer.class));
            var holder = new AdmissionManager[1];
            Camera camera = mockCameraAtMonitor();
            var services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public SpriteManager spriteManager() { return sprites; }
                @Override public ObjectRenderManager renderManager() { return render; }
                @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player, List::of); }
                @Override public void playSfx(int id) { events.add("sfx:" + id); }
                @Override public void playSfx(GameSound sound) { events.add("sound:" + sound); }
            };
            var manager = new AdmissionManager(camera, services, eggman);
            holder[0] = manager;
            manager.reset(camera.getX());
            var monitor = only(manager, Sonic3kMonitorObjectInstance.class);
            manager.update(camera.getX(), player, List.of(), 0);
            int firstHole = monitor.getSlotIndex() + 1;
            int end = ObjectSlotLayout.SONIC_3K.firstDynamicSlot() + ObjectSlotLayout.SONIC_3K.dynamicSlotCount();
            for (int slot = firstHole; slot < end; slot++) {
                if ((freeTailSlots >= 1 && slot == firstHole) || (freeTailSlots == 2 && slot == firstHole + 2)) continue;
                manager.addDynamicObjectAtSlot(new InertOccupant(
                        new ObjectSpawn(MONITOR_SPAWN.x(), MONITOR_SPAWN.y(), 0xFE, 0, 0, false, slot)), slot);
            }
            return new AdmissionHarness(manager, player, services, events);
        }
    }

    private static final class InertOccupant extends AbstractObjectInstance implements RewindRecreatable {
        private int updates;
        private InertOccupant(ObjectSpawn spawn) { super(spawn, "admission-occupant"); }
        @Override public InertOccupant recreateForRewind(RewindRecreateContext context) {
            return new InertOccupant(context.spawn());
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) { updates++; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class BreakTrigger extends AbstractObjectInstance implements RewindRecreatable {
        private Sonic3kMonitorObjectInstance monitor;
        private boolean fired;
        private BreakTrigger(ObjectSpawn spawn, Sonic3kMonitorObjectInstance monitor) {
            super(spawn, "monitor-touch-callback");
            this.monitor = monitor;
        }
        @Override public BreakTrigger recreateForRewind(RewindRecreateContext context) {
            return new BreakTrigger(context.spawn(), null);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            if (!fired) {
                fired = true;
                breakMonitor(monitor, (AbstractPlayableSprite) player);
            }
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private record Harness(ObjectManager objectManager, Camera camera, ObjectServices services) {
        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCameraAtMonitor();
            ObjectPlayerQuery playerQuery = new ObjectPlayerQuery(() -> null, List::of);
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
                @Override public ObjectPlayerQuery playerQuery() { return playerQuery; }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(MONITOR_SPAWN),
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(camera.getX());
            return new Harness(objectManager, camera, services);
        }
    }

    private static RewindRegistry registryFor(ObjectManager objectManager) {
        RewindRegistry rewindRegistry = new RewindRegistry();
        rewindRegistry.register(objectManager.rewindSnapshottable());
        return rewindRegistry;
    }

    private static void spawnMonitorContents(
            Sonic3kMonitorObjectInstance monitor,
            ObjectManager objectManager) throws Exception {
        Method method = Sonic3kMonitorObjectInstance.class.getDeclaredMethod(
                "spawnMonitorContents", ObjectManager.class, AbstractPlayableSprite.class);
        method.setAccessible(true);
        method.invoke(monitor, objectManager, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ObjectInstance constructContents(ObjectSpawn spawn, String typeName) {
        try {
            Class<?> type = Class.forName(CONTENTS_CLASS);
            Class<? extends Enum> monitorType = (Class<? extends Enum>) Class.forName(MONITOR_TYPE_CLASS);
            Constructor<?> ctor = type.getDeclaredConstructor(ObjectSpawn.class, PlayableEntity.class);
            ctor.setAccessible(true);
            ObjectInstance contents = (ObjectInstance) ctor.newInstance(spawn, null);
            Field field = field(contents, "type");
            field.set(contents, Enum.valueOf(monitorType, typeName));
            return contents;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to construct S3K monitor contents", e);
        }
    }

    private static ObjectRefId objectId(ObjectManager objectManager, ObjectInstance object) {
        ObjectRefId id = objectManager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id, "ObjectManager identity table must register " + object.getClass());
        return id;
    }

    private static <T extends ObjectInstance> T objectById(
            ObjectManager objectManager,
            Class<T> type,
            ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectInstance objectByIdRaw(
            ObjectManager objectManager,
            Class<?> type,
            ObjectRefId id) {
        return objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .filter(object -> id.equals(objectManager.captureIdentityContext()
                        .requireIdentityTable()
                        .idFor(object)))
                .findFirst()
                .orElseThrow();
    }

    private static ObjectInstance onlyContents(ObjectManager objectManager) {
        List<ObjectInstance> contents = liveContents(objectManager);
        assertEquals(1, contents.size(), "expected exactly one live S3K monitor contents slot");
        return contents.getFirst();
    }

    private static <T extends ObjectInstance> T only(ObjectManager objectManager, Class<T> type) {
        List<T> matches = objectManager.getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(object -> !object.isDestroyed())
                .toList();
        assertEquals(1, matches.size(), "expected exactly one live " + type.getSimpleName());
        return matches.getFirst();
    }

    private static List<ObjectInstance> liveContents(ObjectManager objectManager) {
        return objectManager.getActiveObjects().stream()
                .filter(object -> object.getClass().getName().equals(CONTENTS_CLASS))
                .filter(object -> !object.isDestroyed())
                .toList();
    }

    private static Camera mockCameraAtMonitor() {
        return new Camera() {
            @Override public short getX() { return 0x0500; }
            @Override public short getY() { return 0x04c0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }

    private static Object readObjectField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.get(target);
    }

    private static int readIntField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.getInt(target);
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        Field field = field(target, name);
        return field.getBoolean(target);
    }

    private static void writeIntField(Object target, String name, int value) throws Exception {
        Field field = field(target, name);
        field.setInt(target, value);
    }

    private static void writeBooleanField(Object target, String name, boolean value) throws Exception {
        Field field = field(target, name);
        field.setBoolean(target, value);
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
