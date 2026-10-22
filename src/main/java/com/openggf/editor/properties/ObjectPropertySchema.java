package com.openggf.editor.properties;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Lossless views over the native subtype byte. Unnamed bits are never rewritten. */
public record ObjectPropertySchema(List<Field> fields, String reference) {
    public static final Field RAW = new Field("raw", "Raw subtype", 255, Kind.RANGE, Map.of());
    public static final ObjectPropertySchema FALLBACK = new ObjectPropertySchema(List.of(), "Native subtype byte");
    public enum Kind { BOOLEAN, ENUM, RANGE }

    public ObjectPropertySchema {
        fields = List.copyOf(fields);
        Objects.requireNonNull(reference);
        int used = 0;
        var ids = new java.util.HashSet<String>();
        for (Field field : fields) {
            if (!ids.add(field.id()) || (used & field.mask()) != 0)
                throw new IllegalArgumentException("Duplicate or overlapping property field");
            used |= field.mask();
        }
    }

    public List<Field> editableFields() {
        var all = new java.util.ArrayList<>(fields);
        all.add(RAW);
        return List.copyOf(all);
    }

    public record Field(String id, String label, int mask, Kind kind, Map<Integer, String> choices) {
        public Field {
            Objects.requireNonNull(id); Objects.requireNonNull(label); Objects.requireNonNull(kind);
            choices = Map.copyOf(choices);
            if (mask <= 0 || mask > 255) throw new IllegalArgumentException("Mask must fit a byte");
            int max = mask >>> Integer.numberOfTrailingZeros(mask);
            if ((max & (max + 1)) != 0) throw new IllegalArgumentException("Mask must be contiguous");
            if (kind == Kind.BOOLEAN && max != 1) throw new IllegalArgumentException("Boolean needs one bit");
            for (var choice : choices.entrySet()) {
                if (choice.getKey() < 0 || choice.getKey() > max || choice.getValue() == null)
                    throw new IllegalArgumentException("Choice exceeds field");
            }
        }
        public int maximum() { return mask >>> Integer.numberOfTrailingZeros(mask); }
        public int decode(int subtype) { requireByte(subtype); return (subtype & mask) >>> Integer.numberOfTrailingZeros(mask); }
        public int encode(int subtype, int value) {
            requireByte(subtype);
            if (value < 0 || value > maximum()) throw new IllegalArgumentException(label + " exceeds its native field");
            return (subtype & ~mask) | (value << Integer.numberOfTrailingZeros(mask));
        }
        public String display(int subtype) {
            int value = decode(subtype);
            if (kind == Kind.BOOLEAN) return value == 0 ? "Off" : "On";
            return choices.getOrDefault(value, kind == Kind.ENUM ? "Unknown ($%02X)".formatted(value) : Integer.toString(value));
        }
        /** Enums step through named choices, while an imported unknown value remains intact until edited. */
        public int step(int subtype, int delta) {
            int current = decode(subtype);
            if (kind != Kind.ENUM || choices.isEmpty()) return encode(subtype, Math.floorMod(current + delta, maximum() + 1));
            var values = choices.keySet().stream().sorted().toList();
            int index = values.indexOf(current);
            int next = index < 0 ? (delta < 0 ? values.size() - 1 : 0) : Math.floorMod(index + delta, values.size());
            return encode(subtype, values.get(next));
        }
        private static void requireByte(int value) {
            if (value < 0 || value > 255) throw new IllegalArgumentException("Subtype must be 0..255");
        }
    }

    public static Field enumeration(String id, String label, int mask, String... labels) {
        var choices = new java.util.LinkedHashMap<Integer, String>();
        for (int i = 0; i < labels.length; i++) choices.put(i, labels[i]);
        return new Field(id, label, mask, Kind.ENUM, choices);
    }
    public static Field bool(String id, String label, int mask) { return new Field(id, label, mask, Kind.BOOLEAN, Map.of()); }
}
