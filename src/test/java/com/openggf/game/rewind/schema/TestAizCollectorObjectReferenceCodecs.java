package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic3k.objects.AizEmeraldScatterInstance;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz1Instance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.OwnedMocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Missing context is compatible; supplied managed identity information is authoritative and strict. */
class TestAizCollectorObjectReferenceCodecs {
    private static final ObjectRefId ID = ObjectRefId.dynamic(3, 4, 101);
    private final OwnedMocks mocks = new OwnedMocks();

    @AfterEach void closeMocks() { mocks.close(); }

    static Stream<Field> collectorLinks() throws ReflectiveOperationException {
        return Stream.of(AizPlaneIntroInstance.class.getDeclaredField("knuckles"),
                AizEmeraldScatterInstance.class.getDeclaredField("knuckles"));
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void absentCaptureContextPreservesADivergentCurrentCollector(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = assertDoesNotThrow(() -> capture(fixture, RewindCaptureContext.none()));
        ObjectInstance divergent = mocks.mock(CutsceneKnucklesAiz1Instance.class);
        field.set(fixture.target(), divergent);
        restore(fixture, saved, RewindCaptureContext.none());
        assertSame(divergent, field.get(fixture.target()));
        restore(fixture, saved, emptyContext());
        assertSame(divergent, field.get(fixture.target()), "an uncaptured link must not consult a later table");
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void absentCaptureContextDoesNotMakeInitiallyNullAuthoritative(Field field) throws Exception {
        Fixture fixture = fixture(field);
        field.set(fixture.target(), null);
        Saved saved = capture(fixture, RewindCaptureContext.none());
        field.set(fixture.target(), fixture.reference());
        restore(fixture, saved, context(fixture.reference()));
        assertSame(fixture.reference(), field.get(fixture.target()));
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void suppliedCaptureTableRejectsAnUnregisteredNonnullCollector(Field field) throws Exception {
        Fixture fixture = fixture(field);
        assertThrows(IllegalStateException.class, () -> capture(fixture, emptyContext()));
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void capturedNonnullCollectorStillRequiresARestoreTableAndExactTarget(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = capture(fixture, context(fixture.reference()));
        assertThrows(IllegalStateException.class, () -> restore(fixture, saved, RewindCaptureContext.none()));
        assertThrows(IllegalStateException.class, () -> restore(fixture, saved, emptyContext()));
        ObjectInstance wrongType = mocks.mock(ObjectInstance.class);
        assertThrows(IllegalStateException.class, () -> restore(fixture, saved, context(wrongType)));
        assertSame(fixture.reference(), field.get(fixture.target()), "failed resolution must not clear the current collector");
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void suppliedTableRestoresTheExactFreshCollector(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = capture(fixture, context(fixture.reference()));
        ObjectInstance replacement = mocks.mock(CutsceneKnucklesAiz1Instance.class);
        field.set(fixture.target(), mocks.mock(CutsceneKnucklesAiz1Instance.class));
        restore(fixture, saved, context(replacement));
        assertSame(replacement, field.get(fixture.target()));
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void tableCapturedNullClearsTheCurrentCollector(Field field) throws Exception {
        Fixture fixture = fixture(field);
        field.set(fixture.target(), null);
        Saved saved = capture(fixture, emptyContext());
        field.set(fixture.target(), fixture.reference());
        restore(fixture, saved, emptyContext());
        assertNull(field.get(fixture.target()));
        field.set(fixture.target(), fixture.reference());
        restore(fixture, saved, RewindCaptureContext.none());
        assertNull(field.get(fixture.target()), "an authoritative captured null needs no target resolution");
    }

    @ParameterizedTest @MethodSource("collectorLinks")
    void classOnlyLookupRemainsStrict(Field field) throws Exception {
        Fixture selected = fixture(field);
        Fixture strict = new Fixture(field, selected.target(), selected.reference(),
                RewindCodecs.codecFor(field.getType()).orElseThrow());
        assertThrows(IllegalStateException.class, () -> capture(strict, RewindCaptureContext.none()));
    }

    private Fixture fixture(Field field) throws Exception {
        field.setAccessible(true);
        Object target = field.getDeclaringClass() == AizPlaneIntroInstance.class
                ? new AizPlaneIntroInstance(spawn()) : new AizEmeraldScatterInstance(spawn());
        ObjectInstance reference = mocks.mock(CutsceneKnucklesAiz1Instance.class);
        field.set(target, reference);
        return new Fixture(field, target, reference, RewindCodecs.codecFor(field).orElseThrow());
    }
    private static ObjectSpawn spawn() { return new ObjectSpawn(0x100, 0x400, 0, 0, 0, false, 0); }
    private static Saved capture(Fixture fixture, RewindCaptureContext context) {
        RewindStateBuffer data = new RewindStateBuffer();
        ArrayList<Object> opaque = new ArrayList<>();
        fixture.codec().capture(fixture.field(), fixture.target(), data, opaque, context);
        assertTrue(opaque.isEmpty(), "collector links contain identity bytes, never live object handles");
        return new Saved(data.toByteArray(), opaque.toArray());
    }
    private static void restore(Fixture fixture, Saved saved, RewindCaptureContext context) {
        fixture.codec().restore(fixture.field(), fixture.target(), RewindStateBuffer.reader(saved.data()),
                saved.opaque(), new RewindCodec.OpaqueIndex(), context);
    }
    private static RewindCaptureContext context(ObjectInstance reference) {
        RewindIdentityTable table = new RewindIdentityTable();
        table.registerObject(reference, ID);
        return RewindCaptureContext.withIdentityTable(table);
    }
    private static RewindCaptureContext emptyContext() {
        return RewindCaptureContext.withIdentityTable(new RewindIdentityTable());
    }
    private record Fixture(Field field, Object target, ObjectInstance reference, RewindCodec codec) { }
    private record Saved(byte[] data, Object[] opaque) { }
}
