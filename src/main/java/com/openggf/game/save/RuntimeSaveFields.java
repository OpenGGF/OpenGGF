package com.openggf.game.save;

import java.util.*;

/** Engine-owned validation of a captured provider result; callers retain its owner fault boundary. */
public final class RuntimeSaveFields {
    private RuntimeSaveFields() { }
    public static Map<String,Object> freeze(Map<String,Object> fields) {
        Objects.requireNonNull(fields,"captureSaveFields returned null");
        @SuppressWarnings("unchecked") Map<String,Object> frozen=(Map<String,Object>)freeze(fields,0);
        return frozen;
    }
    private static Object freeze(Object value, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Captured save fields are cyclic or nested too deeply");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal) return value;
        if (value instanceof Float f && Float.isFinite(f) || value instanceof Double d && Double.isFinite(d)) return value;
        if (value instanceof List<?> list) {
            List<Object> copy=new ArrayList<>(list.size());
            for (Object element:list) copy.add(freeze(element,depth+1));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?,?> map) {
            Map<String,Object> copy=new LinkedHashMap<>();
            for (var entry:map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Captured save field keys must be strings");
                copy.put(key,freeze(entry.getValue(),depth+1));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Captured save fields need immutable JSON values: " + value.getClass().getName());
    }
}
