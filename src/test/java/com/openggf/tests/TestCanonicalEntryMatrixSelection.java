package com.openggf.tests;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** ROM-free coverage contract for replacing repeated Cartesian boots with supported pairs. */
class TestCanonicalEntryMatrixSelection {
    @Test
    void routineRowsCoverEverySupportedPairAndRosterBoundaryWithFarFewerBoots() {
        var exhaustive = CanonicalEntryMatrixSupport.configurations().toList();
        var routine = CanonicalEntryMatrixSupport.smokeConfigurations().toList();
        assertEquals(140, exhaustive.size());
        assertTrue(routine.size() <= 40, "pair coverage must not grow back into a product: " + routine.size());
        assertEquals(routine.size(), new HashSet<>(routine).size());
        assertTrue(exhaustive.containsAll(routine), "every routine row must be a supported product");
        assertEquals(routine, CanonicalEntryMatrixSupport.smokeConfigurations().toList(),
                "selection is deterministic");

        List<Function<CanonicalEntryMatrixSupport.Row, Object>> axes = List.of(
                CanonicalEntryMatrixSupport.Row::donor,
                CanonicalEntryMatrixSupport.Row::main,
                CanonicalEntryMatrixSupport.Row::aspect,
                row -> {
                    var members = row.followers().isEmpty() ? List.<String>of()
                            : List.of(row.followers().split(","));
                    if (members.isEmpty()) return "solo";
                    if (members.size() == 1) return "single";
                    if (members.size() > 2) return "large";
                    return Set.copyOf(members).size() == 1 ? "duplicate" : "mixed";
                });
        for (int first = 0; first < axes.size(); first++) {
            for (int second = first + 1; second < axes.size(); second++) {
                var a = axes.get(first);
                var b = axes.get(second);
                assertEquals(pairs(exhaustive, a, b), pairs(routine, a, b),
                        "supported axis pair " + first + "/" + second);
            }
        }
        assertEquals(Set.of("solo", "single", "mixed", "duplicate", "large"),
                routine.stream().map(axes.getLast()).collect(Collectors.toSet()));
    }

    private static Set<List<Object>> pairs(List<CanonicalEntryMatrixSupport.Row> rows,
            Function<CanonicalEntryMatrixSupport.Row, Object> first,
            Function<CanonicalEntryMatrixSupport.Row, Object> second) {
        return rows.stream().map(row -> List.of(first.apply(row), second.apply(row)))
                .collect(Collectors.toSet());
    }
}
