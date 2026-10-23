package com.openggf.editor.project;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/** Imported tiles remain owned even when transparent or not yet used by terrain. */
record EditorProjectAssets(List<Integer> importedPatterns) {
    static final EditorProjectAssets EMPTY = new EditorProjectAssets(List.of());

    EditorProjectAssets {
        importedPatterns = List.copyOf(Objects.requireNonNull(importedPatterns, "importedPatterns"));
        int previous = 0;
        for (int slot : importedPatterns) {
            if (slot <= previous || slot >= 2048)
                throw new IllegalArgumentException("Imported pattern slots must be unique, sorted and within 1..2047");
            previous = slot;
        }
    }

    static void validateJson(com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 1
                || !value.path("importedPatterns").isArray() || value.path("importedPatterns").size() > 2047)
            throw new IllegalArgumentException("Project assets require an importedPatterns array");
        for (var slot : value.path("importedPatterns")) {
            if (!slot.isIntegralNumber() || !slot.canConvertToInt())
                throw new IllegalArgumentException("Imported pattern slots must be integer JSON numbers");
        }
    }

    void validateAgainst(int count) {
        if (!importedPatterns.isEmpty() && importedPatterns.getLast() >= count)
            throw new IllegalArgumentException("Imported pattern slot is outside the project library");
    }

    BitSet slots() {
        BitSet slots = new BitSet();
        importedPatterns.forEach(slots::set);
        return slots;
    }

    EditorProjectAssets plus(List<Integer> added) {
        BitSet result = slots();
        for (int slot : added) {
            if (result.get(slot)) throw new IllegalStateException("Imported pattern slot is already reserved: " + slot);
            result.set(slot);
        }
        return new EditorProjectAssets(result.stream().boxed().toList());
    }
}
