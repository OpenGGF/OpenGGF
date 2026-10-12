package com.openggf.mods.mutators;

import java.util.Map;

/** Frozen boundary-admitted values supplied to a creator's policy preparation callback. */
@com.openggf.game.ModApi
public record MutatorOptions(Map<String, Object> values) {
    public MutatorOptions {
        values = Map.copyOf(values);
        values.values().forEach(value -> {
            if (!(value instanceof Integer || value instanceof Boolean || value instanceof String)) {
                throw new IllegalArgumentException("Mutator values must be integer, boolean or enum token");
            }
        });
    }
    public int integer(String id) { return (Integer) required(id); }
    public boolean checkbox(String id) { return (Boolean) required(id); }
    public String choice(String id) { return (String) required(id); }
    private Object required(String id) {
        Object value = values.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown option: " + id);
        return value;
    }
}
