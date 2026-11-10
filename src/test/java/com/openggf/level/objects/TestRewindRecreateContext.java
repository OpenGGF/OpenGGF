package com.openggf.level.objects;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.ConstructionFamily;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.DynamicObjectEntry;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Parent identity at the boundary between population reconstruction and object hooks. */
class TestRewindRecreateContext {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(100, 200, 1, 0, 0, false, 0);
    private static final ObjectRefId PARENT_ID = ObjectRefId.dynamic(7, 2, 11);
    private static final ConstructionFamily KNOWN_PARENT = new ConstructionFamily(true, PARENT_ID);

    @Test
    void suppliedParentDoesNotDependOnLiveMembershipOrServicesLookup() {
        var parent = new Parent(SPAWN);
        var manager = inaccessibleManager();
        var services = inaccessibleServices();
        var context = RewindRecreateContext.withResolvedParent(
                SPAWN, null, services, manager, entry(KNOWN_PARENT), parent);

        assertTrue(context.hasKnownConstructionFamily());
        assertSame(parent, context.capturedParent(Parent.class));
        verifyNoInteractions(manager, services);
    }

    @Test
    void suppliedNullDoesNotBorrowAnExactIdResultFromTheLiveQuery() {
        var decoy = new Parent(SPAWN);
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.of(decoy));
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        var services = inaccessibleServices();
        var context = RewindRecreateContext.withResolvedParent(
                SPAWN, null, services, manager, entry(KNOWN_PARENT), null);

        assertTrue(context.hasKnownConstructionFamily());
        assertNull(context.capturedParent(Parent.class));
        verifyNoInteractions(manager, query, services);
    }

    @Test
    void suppliedParentMustMatchTheHooksExpectedType() {
        var context = RewindRecreateContext.withResolvedParent(
                SPAWN, null, null, inaccessibleManager(), entry(KNOWN_PARENT), new OtherParent(SPAWN));

        assertNull(context.capturedParent(Parent.class));
    }

    @Test
    void suppliedDestroyedParentStillResolvesBeforeItsFieldsAreRestored() {
        var parent = new Parent(SPAWN);
        parent.setDestroyed(true);
        var context = RewindRecreateContext.withResolvedParent(
                SPAWN, null, inaccessibleServices(), inaccessibleManager(), entry(KNOWN_PARENT), parent);

        assertSame(parent, context.capturedParent(Parent.class));
    }

    @Test
    void knownNullParentIdentityNeverTriggersCompatibilityLookup() {
        var manager = inaccessibleManager();
        var services = inaccessibleServices();
        var context = new RewindRecreateContext(
                SPAWN, null, services, manager, entry(new ConstructionFamily(true, null)));

        assertTrue(context.hasKnownConstructionFamily());
        assertNull(context.capturedParent(Parent.class));
        assertNull(RewindRecreateContext.withResolvedParent(SPAWN, null, services, manager,
                entry(new ConstructionFamily(true, null)), null).capturedParent(Parent.class));
        verifyNoInteractions(manager, services);
    }

    @ParameterizedTest
    @EnumSource(UnknownFamily.class)
    void unknownAndLegacyMetadataDoNotAuthorizeAParentLookup(UnknownFamily family) {
        var manager = inaccessibleManager();
        var services = inaccessibleServices();
        var context = new RewindRecreateContext(SPAWN, null, services, manager, entry(family.metadata()));

        assertFalse(context.hasKnownConstructionFamily());
        assertNull(context.capturedParent(Parent.class));
        var supplied = RewindRecreateContext.withResolvedParent(
                SPAWN, null, services, manager, entry(family.metadata()), null);
        assertFalse(supplied.hasKnownConstructionFamily());
        assertNull(supplied.capturedParent(Parent.class));
        verifyNoInteractions(manager, services);
    }

    @Test
    void oldFiveArgumentConstructorDefersExactLookupAndPrefersItsExplicitManager() {
        var parent = new Parent(SPAWN);
        parent.setDestroyed(true);
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.of(parent));
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        var services = inaccessibleServices();

        var context = new RewindRecreateContext(SPAWN, null, services, manager, entry(KNOWN_PARENT));

        verifyNoInteractions(manager, query, services);
        assertSame(parent, context.capturedParent(Parent.class));
        verify(manager).objectQuery();
        verify(query).resolve(PARENT_ID);
        verifyNoInteractions(services);
    }

    @Test
    void oldFourArgumentConstructorDefersItsServicesManagerLookup() {
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
    void compatibilityLookupPreservesMissingAndWrongTypeResults() {
        var query = mock(ObjectQuery.class);
        when(query.resolve(PARENT_ID)).thenReturn(Optional.empty(), Optional.of(new OtherParent(SPAWN)));
        var manager = mock(ObjectManager.class);
        when(manager.objectQuery()).thenReturn(query);
        var context = new RewindRecreateContext(SPAWN, null, null, manager, entry(KNOWN_PARENT));

        assertNull(context.capturedParent(Parent.class));
        assertNull(context.capturedParent(Parent.class));
        assertNull(new RewindRecreateContext(SPAWN, null, null, entry(KNOWN_PARENT))
                .capturedParent(Parent.class), "an unavailable compatibility manager is not another parent");
    }

    @Test
    void oldConstructorDescriptorsAndAccessorsRemainAvailable() throws NoSuchMethodException {
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
        assertFalse(context.parentResolutionProvided());
        assertFalse(new RewindRecreateContext(SPAWN, null, services).hasKnownConstructionFamily());
        var dynamic = new DynamicObjectRecreateContext(manager);
        assertSame(manager, dynamic.objectManager());
        assertSame(RewindClassResolver.ENGINE_ONLY, dynamic.classResolver());
        assertFalse(dynamic.parentResolutionProvided());
        assertFalse(new DynamicObjectRecreateContext(manager, RewindClassResolver.ENGINE_ONLY)
                .parentResolutionProvided());
        verifyNoInteractions(manager, services);
    }

    @Test
    void unprovidedContextsCannotCarryAResolvedParent() {
        var parent = new Parent(SPAWN);
        var manager = inaccessibleManager();

        assertThrows(IllegalArgumentException.class, () -> new DynamicObjectRecreateContext(
                manager, RewindClassResolver.ENGINE_ONLY, parent, false));
        assertThrows(IllegalArgumentException.class, () -> new RewindRecreateContext(
                SPAWN, null, null, manager, entry(KNOWN_PARENT), parent, false));
    }

    @ParameterizedTest
    @EnumSource(InvalidParentFamily.class)
    void suppliedParentRequiresAKnownNonNullCapturedParentId(InvalidParentFamily family) {
        assertThrows(IllegalArgumentException.class, () -> RewindRecreateContext.withResolvedParent(
                SPAWN, null, null, null, entry(family.metadata()), new Parent(SPAWN)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void genericRecreateForwardsAuthoritativeParentAndAbsenceIntoTheRealHook(boolean present) {
        var parent = present ? new Parent(SPAWN) : null;
        var services = inaccessibleServices();
        var manager = mock(ObjectManager.class);
        when(manager.objectServicesForRewind()).thenReturn(services);
        if (present) {
            doThrow(new AssertionError("the restore-table parent need not belong to the live query"))
                    .when(manager).objectQuery();
        } else {
            var query = mock(ObjectQuery.class);
            when(query.resolve(PARENT_ID)).thenReturn(Optional.of(new Parent(SPAWN)));
            when(manager.objectQuery()).thenReturn(query);
        }
        var dynamic = DynamicObjectRecreateContext.withResolvedParent(
                manager, RewindClassResolver.ENGINE_ONLY, parent);

        var result = assertInstanceOf(HookProbe.class,
                ObjectRewindDynamicCodecs.genericRecreate(entry(KNOWN_PARENT), dynamic));

        assertSame(parent, result.parent, "the hook must see the supplied result, including authoritative null");
        assertTrue(result.knownFamily, "the real recreate hook must receive family metadata");
        assertEquals(0xCAFE, result.marker, "the hook constructs the result; the probe is not returned");
        verify(manager, never()).objectQuery();
        verify(services, never()).objectManager();
    }

    private static DynamicObjectEntry entry(ConstructionFamily family) {
        return new DynamicObjectEntry(HookProbe.class.getName(), SPAWN, -1, null, null,
                ObjectRefId.dynamic(8, 2, 12), null, false, null, family);
    }

    private static ObjectManager inaccessibleManager() {
        var manager = mock(ObjectManager.class);
        doThrow(new AssertionError("live membership is not the authoritative restore table"))
                .when(manager).objectQuery();
        return manager;
    }

    private static ObjectServices inaccessibleServices() {
        var services = mock(ObjectServices.class);
        doThrow(new AssertionError("supplied parent must not ask services for another manager"))
                .when(services).objectManager();
        return services;
    }

    private enum UnknownFamily {
        UNKNOWN, LEGACY;

        ConstructionFamily metadata() {
            return this == UNKNOWN ? new ConstructionFamily(false, null) : null;
        }
    }

    private enum InvalidParentFamily {
        KNOWN_WITHOUT_ID, UNKNOWN, LEGACY;

        ConstructionFamily metadata() {
            return switch (this) {
                case KNOWN_WITHOUT_ID -> new ConstructionFamily(true, null);
                case UNKNOWN -> new ConstructionFamily(false, null);
                case LEGACY -> null;
            };
        }
    }

    private static final class Parent extends AbstractObjectInstance {
        Parent(ObjectSpawn spawn) { super(spawn, "captured-parent"); }
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

        private HookProbe(ObjectSpawn spawn) {
            this(spawn, null, false, 0);
        }

        private HookProbe(ObjectSpawn spawn, Parent parent, boolean knownFamily, int marker) {
            super(spawn, "parent-forwarding-probe");
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
