package com.openggf.level.objects;

import com.openggf.game.RuntimeManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.game.sonic2.objects.CheckpointDongleInstance;
import com.openggf.game.sonic2.objects.CheckpointObjectInstance;
import com.openggf.game.sonic2.objects.CheckpointStarInstance;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestObjectManagerRewindDynamicClassification {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        ObjectManager.clearRewindDynamicObjectCodecsForTest();
        GenericRewindEligibility.clearForTest();
        RuntimeManager.destroyCurrent();
    }

    @Test
    void registeredDynamicRewindCodecCapturesAndRecreatesDynamicObject() {
        GenericRewindEligibility.registerForTestOrMigration(TestDynamicObject.class);
        ObjectManager.registerRewindDynamicObjectCodecForTest(new ObjectManager.RewindDynamicObjectCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof TestDynamicObject;
            }

            @Override
            public String className() {
                return TestDynamicObject.class.getName();
            }

            @Override
            public ObjectInstance recreate(ObjectManager.DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                return new TestDynamicObject(entry.spawn());
            }
        });

        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0);
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        TestDynamicObject object = new TestDynamicObject(spawn);
        object.phase = 7;
        object.moveTo(0x120, 0x1A0);
        manager.addDynamicObject(object);

        var snapshottable = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = snapshottable.capture();

        assertEquals(1, snapshot.dynamicObjects().size());

        object.phase = 2;
        object.moveTo(0x300, 0x400);
        snapshottable.restore(snapshot);

        TestDynamicObject restored = manager.getActiveObjects().stream()
                .filter(TestDynamicObject.class::isInstance)
                .map(TestDynamicObject.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(restored);
        assertEquals(7, restored.phase);
        assertEquals(0x120, restored.getX());
        assertEquals(0x1A0, restored.getY());
    }

    @Test
    void checkpointDynamicChildrenAreRewindRestorable() {
        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x180, 0x79, 0, 0, false, 0);
        CheckpointObjectInstance parent = new CheckpointObjectInstance(spawn, "Checkpoint");

        assertTrue(ObjectManager.isRewindRestorableDynamicObject(new CheckpointDongleInstance(parent)));
        assertTrue(ObjectManager.isRewindRestorableDynamicObject(new CheckpointStarInstance(parent, 0x40)));
    }

    @Test
    void dynamicSnapshotHonorsLegacyNoArgSubclassCaptureOverride() {
        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0);
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        LegacyOverrideDynamicObject object = new LegacyOverrideDynamicObject(spawn);
        object.phase = 9;
        manager.addDynamicObject(object);

        ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();

        assertEquals(1, snapshot.dynamicObjects().size());
        LegacyOverrideExtra extra = assertInstanceOf(
                LegacyOverrideExtra.class,
                snapshot.dynamicObjects().get(0).state().objectSubclassExtra());
        assertEquals(9, extra.phase());
    }

    @Test
    void dynamicRestoreHonorsLegacySingleArgumentSubclassRestoreOverride() {
        ObjectManager.registerRewindDynamicObjectCodecForTest(new ObjectManager.RewindDynamicObjectCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof LegacyOverrideDynamicObject;
            }

            @Override
            public String className() {
                return LegacyOverrideDynamicObject.class.getName();
            }

            @Override
            public ObjectInstance recreate(ObjectManager.DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                return new LegacyOverrideDynamicObject(entry.spawn());
            }
        });

        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0);
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        LegacyOverrideDynamicObject object = new LegacyOverrideDynamicObject(spawn);
        object.phase = 9;
        manager.addDynamicObject(object);
        ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();

        object.phase = 2;
        manager.rewindSnapshottable().restore(snapshot);

        LegacyOverrideDynamicObject restored = manager.getActiveObjects().stream()
                .filter(LegacyOverrideDynamicObject.class::isInstance)
                .map(LegacyOverrideDynamicObject.class::cast)
                .findFirst()
                .orElse(null);
        assertNotNull(restored);
        assertEquals(9, restored.phase);
    }

    @Test
    void dynamicSnapshotHonorsInheritedLegacyBadnikCaptureOverride() {
        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0);
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        InheritedLegacyBadnik badnik = new InheritedLegacyBadnik(spawn);
        badnik.currentX = 0x140;
        badnik.currentY = 0x1C0;
        manager.addDynamicObject(badnik);

        ObjectManagerSnapshot snapshot = manager.rewindSnapshottable().capture();

        assertEquals(1, snapshot.dynamicObjects().size());
        PerObjectRewindSnapshot.BadnikRewindExtra extra =
                snapshot.dynamicObjects().get(0).state().badnikExtra();
        assertNotNull(extra);
        assertEquals(0x140, extra.currentX());
        assertEquals(0x1C0, extra.currentY());
    }

    @Test
    void shieldDynamicSnapshotTreatsFinalAnimationConfigAsStructural() {
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        manager.addDynamicObject(new ShieldObjectInstance(null));

        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
    }

    @Test
    void monitorDynamicSnapshotUsesInheritedMonitorRewindExtraInsteadOfGenericEffectTargetCapture() {
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        manager.addDynamicObject(new InheritedLegacyMonitor(new ObjectSpawn(0x100, 0x180, 0x26, 0, 0, false, 0)));

        ObjectManagerSnapshot snapshot = assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());

        assertEquals(1, snapshot.dynamicObjects().size());
        assertInstanceOf(PerObjectRewindSnapshot.MonitorRewindExtra.class,
                snapshot.dynamicObjects().get(0).state().objectSubclassExtra());
    }

    @Test
    void defaultObjectSnapshotTreatsFinalPrimitiveConstructorConfigAsStructural() {
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        manager.addDynamicObject(new BoxObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0),
                "Box", 8, 8, 1.0f, 0.5f, 0.25f, false));

        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
    }

    @Test
    void defaultObjectSnapshotTreatsFinalObjectReferenceCollectionsAsStructural() {
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        ObjectSpawn parentSpawn = new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0);
        ObjectSpawn childSpawn = new ObjectSpawn(0x120, 0x180, 0x02, 0, 0, false, 0);
        ObjectReferenceCollectionOwner parent = new ObjectReferenceCollectionOwner(parentSpawn);
        parent.children.add(new TestDynamicObject(childSpawn));
        manager.addDynamicObject(parent);

        assertDoesNotThrow(() -> manager.rewindSnapshottable().capture());
    }

    @Test
    void skidDustDynamicObjectIsRewindRestorable() {
        assertTrue(ObjectManager.isRewindRestorableDynamicObject(
                new SkidDustObjectInstance(0x120, 0x1A0, null, true)));
    }

    private static final class TestDynamicObject extends AbstractObjectInstance {
        private int phase;

        TestDynamicObject(ObjectSpawn spawn) {
            super(spawn, "TestDynamicObject");
        }

        void moveTo(int x, int y) {
            updateDynamicSpawn(x, y);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    private static final class LegacyOverrideDynamicObject extends AbstractObjectInstance {
        private int phase;

        LegacyOverrideDynamicObject(ObjectSpawn spawn) {
            super(spawn, "LegacyOverrideDynamicObject");
        }

        @Override
        public PerObjectRewindSnapshot captureRewindState() {
            return super.captureRewindState().withObjectSubclassExtra(
                    new LegacyOverrideExtra(phase));
        }

        @Override
        public void restoreRewindState(PerObjectRewindSnapshot snapshot) {
            super.restoreRewindState(snapshot);
            if (snapshot.objectSubclassExtra() instanceof LegacyOverrideExtra extra) {
                phase = extra.phase();
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    private record LegacyOverrideExtra(int phase)
            implements PerObjectRewindSnapshot.ObjectSubclassRewindExtra {}

    private static final class InheritedLegacyBadnik extends AbstractBadnikInstance {
        InheritedLegacyBadnik(ObjectSpawn spawn) {
            super(spawn, "InheritedLegacyBadnik");
        }

        @Override
        protected void updateMovement(int frameCounter, PlayableEntity player) {
            // no-op
        }

        @Override
        protected int getCollisionSizeIndex() {
            return 0;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    private static final class InheritedLegacyMonitor extends AbstractMonitorObjectInstance {
        InheritedLegacyMonitor(ObjectSpawn spawn) {
            super(spawn, "InheritedLegacyMonitor");
        }

        @Override
        protected void applyPowerup(PlayableEntity player) {
            // no-op
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    private static final class ObjectReferenceCollectionOwner extends AbstractObjectInstance {
        private final ArrayList<TestDynamicObject> children = new ArrayList<>();

        ObjectReferenceCollectionOwner(ObjectSpawn spawn) {
            super(spawn, "ObjectReferenceCollectionOwner");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }
}
