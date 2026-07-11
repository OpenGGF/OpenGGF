package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestControlCodecPolymorphism {
    @Test
    void everySealedControlSubtypeRoundTripsWithItsStableDiscriminator() throws Exception {
        Set<Class<?>> permitted = Set.of(ControlMessage.class.getPermittedSubclasses());
        assertTrue(permitted.size() > 40, "Unexpectedly small protocol surface");
        for (Class<?> subtype : permitted) {
            ControlMessage message = (ControlMessage) instantiate(subtype);
            String encoded = ControlCodec.encode("token", message);
            assertTrue(encoded.contains("\"type\":\"" + subtype.getSimpleName() + "\""), subtype::getName);
            ControlCodec.DecodedControl decoded = ControlCodec.decode(encoded);
            assertEquals("token", decoded.token());
            assertEquals(message, decoded.message(), subtype::getName);
        }
    }

    @Test
    void unknownAndMissingDiscriminatorsAreRejected() {
        assertThrows(ProtocolViolationException.class,
                () -> ControlCodec.decode("{\"v\":1,\"token\":null,\"msg\":{}}"));
        assertThrows(ProtocolViolationException.class,
                () -> ControlCodec.decode("{\"v\":1,\"token\":null,\"msg\":{\"type\":\"FutureType\"}}"));
    }

    private static Object instantiate(Class<?> type) throws Exception {
        if (type == String.class) return "s3k:0:0";
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == byte.class || type == Byte.class) return (byte) 0;
        if (type == short.class || type == Short.class) return (short) 0;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == float.class || type == Float.class) return 0F;
        if (type == double.class || type == Double.class) return 0D;
        if (type == char.class || type == Character.class) return '\0';
        if (type == List.class) return List.of();
        if (type == Set.class) return Set.of();
        if (type == Map.class) return Map.of();
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isArray()) return Array.newInstance(type.getComponentType(), 0);
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] arguments = new Object[components.length];
            for (int index = 0; index < components.length; index++) {
                parameterTypes[index] = components[index].getType();
                arguments[index] = instantiate(parameterTypes[index]);
            }
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(arguments);
        }
        throw new IllegalArgumentException("No fixture for " + type.getName());
    }
}
