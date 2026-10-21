package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindCollectionCodecs {
    @AfterEach
    void clearRegistry() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void restoresValueCollectionsAndMaps() {
        CollectionFixture fixture = new CollectionFixture();

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture);
        fixture.mutate();
        CompactFieldCapturer.restore(fixture, blob);

        assertEquals(List.of(3, 1, 4), fixture.values);
        assertEquals(Arrays.asList(Mode.ALPHA, null, Mode.GAMMA), new ArrayList<>(fixture.modes));
        assertEquals(List.of("left", "right", "missing"), new ArrayList<>(fixture.counts.keySet()));
        assertEquals(7, fixture.counts.get("left"));
        assertEquals(9, fixture.counts.get("right"));
        assertEquals(null, fixture.counts.get("missing"));
    }

    @Test
    void restoresFinalCollectionsInPlace() {
        FinalCollectionFixture fixture = new FinalCollectionFixture();
        List<Integer> original = fixture.values;

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture);
        fixture.values.clear();
        fixture.values.add(99);
        CompactFieldCapturer.restore(fixture, blob);

        assertSame(original, fixture.values);
        assertEquals(List.of(1, 2, 3), fixture.values);
    }

    @Test
    void restoresNonFinalIdentityHashMapsWithIdentitySemantics() {
        IdentityMapFixture fixture = new IdentityMapFixture();
        Object key = fixture.key;

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture);
        fixture.values = new IdentityHashMap<>();
        CompactFieldCapturer.restore(fixture, blob);

        assertInstanceOf(IdentityHashMap.class, fixture.values);
        assertEquals(11, fixture.values.get(key));
        assertEquals(null, fixture.values.get(new String("key")));
    }

    @Test
    void distinguishesValueCollectionsFromReferenceCollectionsForIdentityContext() throws Exception {
        Field valueField = CollectionFixture.class.getDeclaredField("counts");
        Field referenceField = ReferenceCollectionFixture.class.getDeclaredField("riders");

        assertFalse(RewindCodecs.requiresIdentityTable(valueField));
        assertTrue(RewindCodecs.requiresIdentityTable(referenceField));
    }

    @Test
    void stateHoldersWithPlayerReferencesAreIdentityAware() {
        assertTrue(RewindCodecs.supportsInPlaceStateHolder(ReferenceState.class));
    }

    @Test
    void capturesMapWithPlayerKeysAndPlainStateValues() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(PlayerStateMapFixture.class);

        assertEquals(1, schema.capturedFields().size());
        assertTrue(RewindCodecs.requiresIdentityTable(
                schema.capturedFields().getFirst().field()));
        assertTrue(schema.unsupportedFields().isEmpty());
    }

    @Test
    void capturesListOfPrimitiveArrays() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(PrimitiveArrayListFixture.class);

        assertEquals(1, schema.capturedFields().size());
        assertTrue(schema.unsupportedFields().isEmpty());
    }

    @Test
    void rejectsCollectionWithUnsupportedElementType() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(UnsupportedCollectionFixture.class);

        assertEquals(1, schema.unsupportedFields().size());
        assertEquals("objects", schema.unsupportedFields().getFirst().field().getName());
        assertThrows(IllegalStateException.class,
                () -> CompactFieldCapturer.capture(new UnsupportedCollectionFixture()));
    }

    @Test
    void classKeyedOrdinalMapPreservesIndependentValuesAndExactClassIdentity() throws Exception {
        Field field = ClassOrdinalFixture.class.getDeclaredField("ordinals");
        assertTrue(RewindCodecs.codecFor(field).isPresent(), "typed Class<?> ordinal map must be captured");
        byte[] bytes;
        try (var input = ClassKey.class.getResourceAsStream("TestRewindCollectionCodecs$ClassKey.class")) {
            assertTrue(input != null);
            bytes = input.readAllBytes();
        }
        class IsolatedLoader extends ClassLoader {
            IsolatedLoader() { super(null); }
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }
        Class<?> first = new IsolatedLoader().define();
        Class<?> second = new IsolatedLoader().define();
        assertEquals(first.getName(), second.getName());
        assertNotSame(first, second);
        ClassOrdinalFixture fixture = new ClassOrdinalFixture();
        fixture.ordinals.put(first, 4);
        fixture.ordinals.put(second, 9);
        fixture.ordinals.put(ClassKey.class, 12);
        var originalMap = fixture.ordinals;
        RewindObjectStateBlob saved = CompactFieldCapturer.capture(fixture);
        var genericSaved = GenericFieldCapturer.capture(fixture);
        assertEquals(saved, CompactFieldCapturer.capture(fixture), "ordered encoding is repeatable");
        for (int round = 1; round <= 2; round++) {
            fixture.ordinals.clear();
            fixture.ordinals.put(String.class, 99);
            CompactFieldCapturer.restore(fixture, saved);
            assertSame(originalMap, fixture.ordinals);
            var keys = new ArrayList<>(fixture.ordinals.keySet());
            assertSame(first, keys.get(0));
            assertSame(second, keys.get(1));
            assertSame(ClassKey.class, keys.get(2));
            assertEquals(List.of(4, 9, 12), new ArrayList<>(fixture.ordinals.values()));
            fixture.ordinals.clear();
            fixture.ordinals.put(Integer.class, -1);
            GenericFieldCapturer.restore(fixture, genericSaved);
            assertSame(originalMap, fixture.ordinals);
            var genericKeys = new ArrayList<>(fixture.ordinals.keySet());
            assertSame(first, genericKeys.get(0));
            assertSame(second, genericKeys.get(1));
            assertSame(ClassKey.class, genericKeys.get(2));
            assertEquals(List.of(4, 9, 12), new ArrayList<>(fixture.ordinals.values()));
        }
    }

    @Test
    void classOrdinalCodecDoesNotAcceptErasedOrDifferentMapShapes() throws Exception {
        for (String name : List.of("erased", "rawClassKeys", "nonOrdinalValues", "boundedClassKeys")) {
            assertTrue(RewindCodecs.codecFor(UnsupportedClassMaps.class.getDeclaredField(name)).isEmpty(), name);
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class UnsupportedClassMaps {
        Map erased;
        Map<Class, Integer> rawClassKeys;
        Map<Class<?>, String> nonOrdinalValues;
        Map<Class<? extends Number>, Integer> boundedClassKeys;
    }

    private static final class ClassOrdinalFixture {
        final Map<Class<?>, Integer> ordinals = new LinkedHashMap<>();
    }

    private static final class ClassKey { }

    private enum Mode {
        ALPHA,
        BETA,
        GAMMA
    }

    private static final class CollectionFixture {
        List<Integer> values = new ArrayList<>(List.of(3, 1, 4));
        Set<Mode> modes = linkedSet(Mode.ALPHA, null, Mode.GAMMA);
        Map<String, Integer> counts = linkedMap();

        void mutate() {
            values = new ArrayList<>(List.of(-1));
            modes = linkedSet(Mode.BETA);
            counts = new LinkedHashMap<>();
            counts.put("mutated", -2);
        }
    }

    private static final class FinalCollectionFixture {
        final List<Integer> values = new ArrayList<>(List.of(1, 2, 3));
    }

    private static final class IdentityMapFixture {
        String key = new String("key");
        IdentityHashMap<String, Integer> values = new IdentityHashMap<>();

        IdentityMapFixture() {
            values.put(key, 11);
        }
    }

    private static final class UnsupportedCollectionFixture {
        List<Object> objects = new ArrayList<>(List.of(new Object()));
    }

    private static final class ReferenceCollectionFixture {
        Set<AbstractPlayableSprite> riders = new LinkedHashSet<>();
    }

    private static final class ReferenceState {
        AbstractPlayableSprite rider;
        int timer;
    }

    private static final class PlayerStateMapFixture {
        Map<AbstractPlayableSprite, ReferenceState> riders = new IdentityHashMap<>();
    }

    private static final class PrimitiveArrayListFixture {
        List<int[]> positions = new ArrayList<>(List.of(new int[] {1, 2}));
    }

    @SafeVarargs
    private static <T> Set<T> linkedSet(T... values) {
        Set<T> set = new LinkedHashSet<>();
        for (T value : values) {
            set.add(value);
        }
        return set;
    }

    private static Map<String, Integer> linkedMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("left", 7);
        map.put("right", 9);
        map.put("missing", null);
        return map;
    }
}
