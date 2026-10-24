package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance;
import com.openggf.game.sonic2.objects.badniks.SolFireballObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.tests.OwnedMocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TestOptionalObjectReferenceCodecs {
    private static final ObjectRefId ID = ObjectRefId.dynamic(3, 4, 101);
    private final OwnedMocks mocks = new OwnedMocks();

    @AfterEach
    void closeMocks() {
        mocks.close();
    }

    static Stream<Field> optionalLinks() throws ReflectiveOperationException {
        return Stream.of(RexonHeadObjectInstance.class.getDeclaredField("parent"),
                RexonHeadObjectInstance.class.getDeclaredField("linkedHead"),
                SolFireballObjectInstance.class.getDeclaredField("parent"));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void absentCaptureContextPreservesTheExistingLink(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = assertDoesNotThrow(() -> capture(fixture, RewindCaptureContext.none()));
        restore(fixture, saved, RewindCaptureContext.none());
        assertSame(fixture.reference(), field.get(fixture.target()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void uncapturedLinkPreservesADivergentLinkEvenWithARestoreTable(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = capture(fixture, RewindCaptureContext.none());
        ObjectInstance divergent = (ObjectInstance) mocks.mock(field.getType());
        field.set(fixture.target(), divergent);
        restore(fixture, saved, context(fixture.reference()));
        assertSame(divergent, field.get(fixture.target()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void classOnlyLookupRetainsStrictContextIdentityAndTargetRequirements(Field field) throws Exception {
        Fixture optional = fixture(field);
        Fixture strict = new Fixture(field, optional.target(), optional.reference(),
                RewindCodecs.codecFor(field.getType()).orElseThrow());
        assertThrows(IllegalStateException.class, () -> capture(strict, RewindCaptureContext.none()));
        assertThrows(IllegalStateException.class, () -> capture(strict, emptyContext()));
        Saved saved = capture(strict, context(strict.reference()));
        assertThrows(IllegalStateException.class, () -> restore(strict, saved, RewindCaptureContext.none()));
        assertThrows(IllegalStateException.class, () -> restore(strict, saved, emptyContext()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void capturedIdentityResolvesToTheReplacementInstance(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = capture(fixture, context(fixture.reference()));
        ObjectInstance replacement = (ObjectInstance) mocks.mock(field.getType());
        restore(fixture, saved, context(replacement));
        assertSame(replacement, field.get(fixture.target()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void unregisteredRetiredTargetCapturesAnAuthoritativeNull(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = assertDoesNotThrow(() -> capture(fixture, emptyContext()));
        restore(fixture, saved, RewindCaptureContext.none());
        assertNull(field.get(fixture.target()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void missingRestoreTargetResolvesToNull(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = capture(fixture, context(fixture.reference()));
        assertDoesNotThrow(() -> restore(fixture, saved, emptyContext()));
        assertNull(field.get(fixture.target()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void capturedNullClearsADivergentLinkWithoutARestoreTable(Field field) throws Exception {
        Fixture fixture = fixture(field);
        field.set(fixture.target(), null);
        Saved saved = capture(fixture, emptyContext());
        field.set(fixture.target(), fixture.reference());
        restore(fixture, saved, RewindCaptureContext.none());
        assertNull(field.get(fixture.target()));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void resolvedWrongTypeStillFails(Field field) throws Exception {
        Fixture fixture = fixture(field);
        Saved saved = capture(fixture, context(fixture.reference()));
        ObjectInstance wrongType = mocks.mock(ObjectInstance.class);
        assertThrows(IllegalStateException.class, () -> restore(fixture, saved, context(wrongType)));
    }

    @ParameterizedTest
    @MethodSource("optionalLinks")
    void optionalNativeWireKeepsItsExistingCapturedAndNullMarkers(Field field) throws Exception {
        Fixture fixture = fixture(field);
        assertArrayEquals(new byte[]{0}, capture(fixture, RewindCaptureContext.none()).data());
        assertArrayEquals(new byte[]{1, 0}, capture(fixture, emptyContext()).data());
        // The original optional encoding: captured marker, present marker, then five identity ints.
        var expected = java.nio.ByteBuffer.allocate(22).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put((byte) 1).put((byte) 1)
                .putInt(ID.kind().ordinal()).putInt(ID.slotIndex()).putInt(ID.generation())
                .putInt(ID.spawnId()).putInt(ID.dynamicId()).array();
        assertArrayEquals(expected, capture(fixture, context(fixture.reference())).data());
    }

    private Fixture fixture(Field field) throws Exception {
        field.setAccessible(true);
        Object target = mocks.mock(field.getDeclaringClass());
        ObjectInstance reference = (ObjectInstance) mocks.mock(field.getType());
        field.set(target, reference);
        return new Fixture(field, target, reference, RewindCodecs.codecFor(field).orElseThrow());
    }

    private static Saved capture(Fixture fixture, RewindCaptureContext context) {
        RewindStateBuffer data = new RewindStateBuffer();
        ArrayList<Object> opaque = new ArrayList<>();
        fixture.codec().capture(fixture.field(), fixture.target(), data, opaque, context);
        assertTrue(opaque.isEmpty(), "object links must contain only encoded identity bytes");
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

    private record Fixture(Field field, Object target, ObjectInstance reference, RewindCodec codec) {}
    private record Saved(byte[] data, Object[] opaque) {}
}
