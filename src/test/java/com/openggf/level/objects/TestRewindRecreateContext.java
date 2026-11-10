package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ConstructionFamily;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exact captured-parent lookup through the existing admitted-population boundary. */
class TestRewindRecreateContext {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(100, 200, 1, 0, 0, false, 0);
    private static final ObjectRefId PARENT_ID = ObjectRefId.dynamic(7, 2, 11);
    private static final ConstructionFamily KNOWN_PARENT = new ConstructionFamily(true, PARENT_ID);

    @BeforeEach
    void initHeadlessGraphics() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void capturedParentUsesTheExactIdentityAndPrefersTheExplicitManager() {
        var parent = new Parent(SPAWN);
        // A reused admitted parent's flag may precede its phase-2 field restore.
        parent.setDestroyed(true);
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.of(parent));
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        var services = inaccessibleServices();
        var context = new RewindRecreateContext(SPAWN, null, services, manager, entry(KNOWN_PARENT));

        verifyNoInteractions(manager, query, services);
        assertTrue(context.hasKnownConstructionFamily());
        assertSame(parent, context.capturedParent(Parent.class));
        verify(query).resolve(PARENT_ID);
        verifyNoInteractions(services);
    }

    @Test
    void knownNullParentIdentityNeverTriggersLookup() {
        var manager = inaccessibleManager();
        var services = inaccessibleServices();
        var context = new RewindRecreateContext(
                SPAWN, null, services, manager, entry(new ConstructionFamily(true, null)));

        assertTrue(context.hasKnownConstructionFamily());
        assertNull(context.capturedParent(Parent.class));
        verifyNoInteractions(manager, services);
    }

    @ParameterizedTest
    @EnumSource(UnknownFamily.class)
    void unknownAndLegacyMetadataDoNotAuthorizeLookup(UnknownFamily family) {
        var manager = inaccessibleManager();
        var services = inaccessibleServices();
        var context = new RewindRecreateContext(SPAWN, null, services, manager, entry(family.metadata()));

        assertFalse(context.hasKnownConstructionFamily());
        assertNull(context.capturedParent(Parent.class));
        verifyNoInteractions(manager, services);
    }

    @Test
    void servicesManagerIsALazyFallback() {
        var parent = new Parent(SPAWN);
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.of(parent));
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        var services = mock(ObjectServices.class);
        when(services.objectManager()).thenReturn(manager);
        var context = new RewindRecreateContext(SPAWN, null, services, entry(KNOWN_PARENT));

        verifyNoInteractions(manager, query, services);
        assertSame(parent, context.capturedParent(Parent.class));
        verify(services).objectManager();
        verify(query).resolve(PARENT_ID);
    }

    @Test
    void missingMembershipAndWrongTypeRemainUnavailable() {
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.empty(), Optional.of(new OtherParent(SPAWN)));
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        var context = new RewindRecreateContext(SPAWN, null, null, manager, entry(KNOWN_PARENT));

        assertNull(context.capturedParent(Parent.class));
        assertNull(context.capturedParent(Parent.class));
        assertNull(new RewindRecreateContext(SPAWN, null, null, entry(KNOWN_PARENT))
                .capturedParent(Parent.class));
    }

    @Test
    void existingConstructorDescriptorsAndAccessorsRemainAvailable() throws NoSuchMethodException {
        assertNotNull(RewindRecreateContext.class.getConstructor(
                ObjectSpawn.class, PerObjectRewindSnapshot.class, ObjectServices.class));
        assertNotNull(RewindRecreateContext.class.getConstructor(
                ObjectSpawn.class, PerObjectRewindSnapshot.class, ObjectServices.class, DynamicObjectEntry.class));
        assertNotNull(RewindRecreateContext.class.getConstructor(
                ObjectSpawn.class, PerObjectRewindSnapshot.class, ObjectServices.class,
                ObjectManager.class, DynamicObjectEntry.class));
        assertNotNull(DynamicObjectRecreateContext.class.getConstructor(ObjectManager.class));
        assertNotNull(DynamicObjectRecreateContext.class.getConstructor(
                ObjectManager.class, RewindClassResolver.class));
        var manager = inaccessibleManager();
        var services = inaccessibleServices();
        var captured = entry(KNOWN_PARENT);
        var context = new RewindRecreateContext(SPAWN, null, services, manager, captured);

        assertSame(SPAWN, context.spawn());
        assertNull(context.state());
        assertSame(services, context.objectServices());
        assertSame(manager, context.objectManager());
        assertSame(captured, context.dynamicEntry());
        assertFalse(new RewindRecreateContext(SPAWN, null, services).hasKnownConstructionFamily());
        var dynamic = new DynamicObjectRecreateContext(manager);
        assertSame(manager, dynamic.objectManager());
        assertSame(RewindClassResolver.ENGINE_ONLY, dynamic.classResolver());
        verifyNoInteractions(manager, services);
    }

    @Test
    void genericRecreateMakesTheCapturedIdentityAvailableToTheRealHook() {
        var parent = new Parent(SPAWN);
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.of(parent));
        var services = inaccessibleServices();
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        when(manager.objectServicesForRewind()).thenReturn(services);

        var result = assertInstanceOf(HookProbe.class, ObjectRewindDynamicCodecs.genericRecreate(
                entry(KNOWN_PARENT), new DynamicObjectRecreateContext(manager)));

        assertSame(parent, result.parent);
        assertTrue(result.knownFamily);
        assertEquals(0xCAFE, result.marker, "the real hook constructs the result; the probe is discarded");
        verify(query).resolve(PARENT_ID);
        verifyNoInteractions(services);
    }

    @Test
    void realLifecycleKeepsTheRoutineChildWithItsCapturedParentAmongSamePositionPeers() {
        ObjectManager manager = createHarness();
        Parent sourceParent = manager.createDynamicObject(() -> new Parent(SPAWN));
        Parent sourceDecoy = manager.createDynamicObject(() -> new Parent(
                new ObjectSpawn(SPAWN.x(), SPAWN.y(), 1, 1, 0, false, 1)));
        HookProbe sourceChild = sourceParent.spawnHookChild();
        var identities = manager.captureIdentityContext().requireIdentityTable();
        ObjectRefId parentId = identities.idFor(sourceParent);
        ObjectRefId decoyId = identities.idFor(sourceDecoy);
        ObjectRefId childId = identities.idFor(sourceChild);
        assertNotEquals(parentId, decoyId);
        var snapshot = manager.rewindSnapshottable().capture();
        var capturedChild = snapshot.dynamicObjects().stream()
                .filter(entry -> childId.equals(entry.objectId())).findFirst().orElseThrow();
        assertEquals(new ConstructionFamily(true, parentId), capturedChild.constructionFamily());

        manager.rewindSnapshottable().restore(snapshot);

        Parent restoredParent = assertInstanceOf(Parent.class,
                manager.objectQuery().resolve(parentId).orElseThrow());
        Parent restoredDecoy = assertInstanceOf(Parent.class,
                manager.objectQuery().resolve(decoyId).orElseThrow());
        HookProbe restoredChild = assertInstanceOf(HookProbe.class,
                manager.objectQuery().resolve(childId).orElseThrow());
        assertNotSame(sourceParent, restoredParent);
        assertNotSame(sourceChild, restoredChild);
        assertNotSame(restoredParent, restoredDecoy);
        assertEquals(restoredParent.getX(), restoredDecoy.getX());
        assertEquals(restoredParent.getY(), restoredDecoy.getY());
        assertSame(restoredParent, restoredChild.parent);
        assertTrue(restoredChild.knownFamily);
        assertEquals(capturedChild.slotIndex(), restoredChild.getSlotIndex());
    }

    private static DynamicObjectEntry entry(ConstructionFamily family) {
        return new DynamicObjectEntry(HookProbe.class.getName(), SPAWN, -1, null, null,
                ObjectRefId.dynamic(8, 2, 12), null, false, null, family);
    }

    private static ObjectManager inaccessibleManager() {
        var manager = mock(ObjectManager.class);
        doThrow(new AssertionError("this metadata must not authorize a parent lookup"))
                .when(manager).objectQuery();
        return manager;
    }

    private static ObjectServices inaccessibleServices() {
        var services = mock(ObjectServices.class);
        doThrow(new AssertionError("explicit manager must take precedence"))
                .when(services).objectManager();
        return services;
    }

    private static ObjectManager createHarness() {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
            @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
        };
        holder[0] = new ObjectManager(List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0].reset(0);
        return holder[0];
    }

    private enum UnknownFamily {
        UNKNOWN, LEGACY;
        ConstructionFamily metadata() {
            return this == UNKNOWN ? new ConstructionFamily(false, null) : null;
        }
    }

    private static final class Parent extends AbstractObjectInstance implements SpawnRewindRecreatable {
        Parent(ObjectSpawn spawn) { super(spawn, "captured-parent"); }
        HookProbe spawnHookChild() {
            return spawnChild(() -> new HookProbe(getSpawn(), this, true, 0));
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class OtherParent extends AbstractObjectInstance {
        OtherParent(ObjectSpawn spawn) { super(spawn, "other-parent"); }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class HookProbe extends AbstractObjectInstance implements RewindRecreatable {
        private final Parent parent;
        private final boolean knownFamily;
        private final int marker;
        private HookProbe(ObjectSpawn spawn) { this(spawn, null, false, 0); }
        private HookProbe(ObjectSpawn spawn, Parent parent, boolean knownFamily, int marker) {
            super(spawn, "parent-lookup-probe");
            this.parent = parent;
            this.knownFamily = knownFamily;
            this.marker = marker;
        }
        @Override
        public AbstractObjectInstance recreateForRewind(RewindRecreateContext context) {
            return new HookProbe(context.spawn(), context.capturedParent(Parent.class),
                    context.hasKnownConstructionFamily(), 0xCAFE);
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
