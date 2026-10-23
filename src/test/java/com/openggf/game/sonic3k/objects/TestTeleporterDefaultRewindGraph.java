package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Generic teleporter scalar/helper capture and exact beam-owner fixups, without loading a route. */
class TestTeleporterDefaultRewindGraph {
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x3640, 0x670, 0, 0, 0, false, 0);

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recreatedEqualPositionGraphsKeepExactBeamsAndSwingState(boolean icz) {
        ObjectManager manager = new StubObjectServices().withIsolatedObjectManager().objectManager();
        manager.setRewindInPlaceRestoreEnabledForTest(false);
        AbstractObjectInstance first = teleporter(icz), second = teleporter(icz);
        TeleporterBeamObjectInstance firstBeam = beam(icz, first), secondBeam = beam(icz, second);
        for (AbstractObjectInstance object : List.of(first, firstBeam, second, secondBeam)) {
            manager.addDynamicObject(object);
        }
        set(first, "beam", firstBeam);
        set(second, "beam", secondBeam);
        seedState(first, icz, 19);
        seedState(second, icz, 7);
        ObjectRefId firstId = id(manager, first), secondId = id(manager, second);
        ObjectRefId firstBeamId = id(manager, firstBeam), secondBeamId = id(manager, secondBeam);
        assertTrue(GenericRewindEligibility.usesCompactDefaultSubclassCapture(first.getClass()));
        var adapter = manager.rewindSnapshottable();
        ObjectManagerSnapshot before = adapter.capture();
        var parentState = first.captureRewindState(manager.captureIdentityContext());
        assertNotNull(parentState.compactGenericState(), "the production default schema owns this state");
        assertNull(parentState.objectSubclassExtra(), "the parent needs no duplicate typed extra");
        if (icz) advanceSwing(first, 11);
        ObjectManagerSnapshot after = adapter.capture();

        for (int cycle = 0; cycle < 2; cycle++) {
            adapter.restore(before);
            AbstractObjectInstance restoredFirst = resolve(manager, firstId);
            AbstractObjectInstance restoredSecond = resolve(manager, secondId);
            TeleporterBeamObjectInstance restoredFirstBeam =
                    (TeleporterBeamObjectInstance) resolve(manager, firstBeamId);
            TeleporterBeamObjectInstance restoredSecondBeam =
                    (TeleporterBeamObjectInstance) resolve(manager, secondBeamId);
            assertNotSame(first, restoredFirst);
            assertNotSame(second, restoredSecond);
            assertNotSame(firstBeam, restoredFirstBeam);
            assertNotSame(secondBeam, restoredSecondBeam);
            assertSame(restoredFirstBeam, get(restoredFirst, "beam"));
            assertSame(restoredSecondBeam, get(restoredSecond, "beam"));
            assertSame(restoredFirst, get(restoredFirstBeam, "parent"));
            assertSame(restoredSecond, get(restoredSecondBeam, "parent"));
            assertSnapshot(before, adapter.capture());
            manager.validateRewindReferenceClosure();
            if (icz) {
                advanceSwing(restoredFirst, 11);
                assertSnapshot(after, adapter.capture());
                ((IczTeleporterInstance) restoredFirst).onBeamFinished();
                assertNull(get(restoredFirst, "beam"));
                assertSame(restoredSecondBeam, get(restoredSecond, "beam"),
                        "the other equal-position graph retains its own beam");
            }
            first = restoredFirst;
            second = restoredSecond;
            firstBeam = restoredFirstBeam;
            secondBeam = restoredSecondBeam;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void genericBeamCaptureStillRejectsAnUnregisteredLiveChild(boolean icz) {
        ObjectManager manager = new StubObjectServices().withIsolatedObjectManager().objectManager();
        AbstractObjectInstance parent = teleporter(icz);
        manager.addDynamicObject(parent);
        set(parent, "beam", beam(icz, parent));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.rewindSnapshottable().capture());
        assertTrue(failure.getMessage().contains("beam"));
        assertTrue(failure.getMessage().contains("no registered id for object reference"));
    }

    private static AbstractObjectInstance teleporter(boolean icz) {
        return icz ? new IczTeleporterInstance(SPAWN) : new CnzTeleporterInstance(SPAWN);
    }

    private static TeleporterBeamObjectInstance beam(boolean icz, AbstractObjectInstance parent) {
        return icz ? TeleporterBeamObjectInstance.iczArrivalBeam(SPAWN, (TeleporterBeamOwner) parent)
                : TeleporterBeamObjectInstance.cnzTransportBeam(SPAWN, (TeleporterBeamOwner) parent);
    }

    private static void seedState(AbstractObjectInstance parent, boolean icz, int swingEntries) {
        if (icz) {
            set(parent, "initialized", true);
            set(parent, "released", swingEntries == 7);
            set(parent, "playerBaseY", 0x654);
            advanceSwing(parent, swingEntries);
        } else {
            set(parent, "centreX", 0x4A40);
            set(parent, "centreY", 0xA38);
            for (String flag : List.of("armed", "paletteLine2Patched", "teleportArtQueued",
                    "beamSpawned", "playerHidden", "transitionRequested")) {
                set(parent, flag, true);
            }
        }
    }

    private static void advanceSwing(AbstractObjectInstance parent, int entries) {
        S3kGradualSwing swing = (S3kGradualSwing) get(parent, "swing");
        for (int entry = 0; entry < entries; entry++) swing.step(0x20000, 0x800);
    }

    private static ObjectRefId id(ObjectManager manager, ObjectInstance object) {
        ObjectRefId id = manager.captureIdentityContext().requireIdentityTable().idFor(object);
        assertNotNull(id);
        return id;
    }

    private static AbstractObjectInstance resolve(ObjectManager manager, ObjectRefId id) {
        return (AbstractObjectInstance) manager.captureIdentityContext().requireIdentityTable()
                .resolveObject(id, true);
    }

    private static void assertSnapshot(ObjectManagerSnapshot expected, ObjectManagerSnapshot actual) {
        assertEquals(List.of(), RewindSnapshotDiff.diffKey("object-manager", expected, actual));
    }

    private static Object get(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
