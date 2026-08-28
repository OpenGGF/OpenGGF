package com.openggf.audio.smps;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDacDataImmutability {

    @Test
    void constructorOwnsSampleMapAndBytes() {
        byte[] sourceBytes = { 0x12, 0x34 };
        Map<Integer, byte[]> sourceSamples = new HashMap<>();
        sourceSamples.put(1, sourceBytes);
        Map<Integer, DacData.DacEntry> sourceMapping = new HashMap<>();
        sourceMapping.put(0x81, new DacData.DacEntry(1, 4));

        DacData data = new DacData(sourceSamples, sourceMapping, 295);
        sourceBytes[0] = 0x55;
        sourceSamples.clear();
        sourceMapping.clear();

        DacData.Sample sample = data.sample(1);
        assertEquals(2, sample.length());
        assertEquals((byte) 0x12, sample.byteAt(0));
        assertNull(data.sample(2));
        assertEquals(1, data.sampleCount());
        assertEquals(1, data.mappingCount());
        assertTrue(data.hasSample(1));
        assertFalse(data.hasSample(2));
        assertEquals(295, data.baseCycles());
        assertEquals(1, data.mappingForNote(0x81).sampleId());
    }

    @Test
    void missingLookupsReturnNullAndSampleUsesNormalIndexedReadBounds() {
        DacData data = new DacData(
                Map.of(1, new byte[] { 0x12 }),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295);

        DacData.Sample sample = data.sample(1);
        assertNull(data.sample(2));
        assertNull(data.mappingForNote(0x82));
        assertThrows(IndexOutOfBoundsException.class,
                () -> sample.byteAt(1));
    }

    @Test
    void publicApiDoesNotExposeMutableDacStorage() {
        assertTrue(Modifier.isFinal(DacData.class.getModifiers()));

        Field[] publicFields = DacData.class.getFields();
        assertEquals(Set.of("samples", "mapping", "baseCycles"),
                Arrays.stream(publicFields)
                        .map(Field::getName)
                        .collect(Collectors.toSet()),
                "only the frozen Mod API compatibility snapshot may be public");
        assertTrue(Arrays.stream(publicFields)
                        .allMatch(field -> Modifier.isFinal(field.getModifiers())),
                "compatibility fields must remain final");

        for (Method method : DacData.class.getMethods()) {
            assertFalse(exposesRawSamples(method.getGenericReturnType()),
                    method + " exposes mutable DAC sample storage");
        }
        for (Field field : publicFields) {
            if (exposesRawSamples(field.getGenericType())) {
                assertEquals("samples", field.getName(),
                        field + " is an unreviewed raw DAC compatibility field");
            }
        }

        byte[] sourceBytes = { 0x12, 0x34 };
        DacData data = new DacData(
                Map.of(1, sourceBytes),
                Map.of(0x81, new DacData.DacEntry(1, 4)),
                295);
        byte[] compatibilityBytes = data.samples.get(1);
        assertNotSame(sourceBytes, compatibilityBytes,
                "the compatibility snapshot must clone caller-owned bytes");
        sourceBytes[0] = 0x55;
        assertEquals((byte) 0x12, compatibilityBytes[0]);
        compatibilityBytes[0] = 0x66;
        assertEquals((byte) 0x12, data.sample(1).byteAt(0),
                "compatibility bytes must not be runtime storage");
        assertThrows(UnsupportedOperationException.class,
                () -> data.samples.put(2, new byte[] { 0x01 }));
        assertThrows(UnsupportedOperationException.class,
                () -> data.mapping.put(0x82, new DacData.DacEntry(1, 4)));

        Set<String> sampleMethods = Arrays.stream(
                        DacData.Sample.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("length", "byteAt"), sampleMethods);
    }

    private static boolean exposesRawSamples(Type type) {
        if (type == byte[].class) {
            return true;
        }
        if (!(type instanceof ParameterizedType parameterized)
                || parameterized.getRawType() != Map.class) {
            return false;
        }
        return Arrays.equals(parameterized.getActualTypeArguments(),
                new Type[] { Integer.class, byte[].class });
    }

}
