package com.openggf.mods.code;

import com.openggf.game.ModKeySyntax;
import com.openggf.game.session.PatternWindowState;
import com.openggf.mods.EffectiveModCatalog;
import com.openggf.mods.ModDescriptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic immutable pattern-window allocation in effective mod order. */
public final class ModPatternWindowAllocator implements PatternWindowState {
    public static final int WINDOW_SIZE = 0x8000;
    public static final int MAX_WINDOWS = 128;
    private static final int RANGE_ALIGNMENT = 0x1000;

    private final List<Assignment> assignments;

    public ModPatternWindowAllocator(EffectiveModCatalog effective, int firstFreeEndExclusive) {
        this(requests(Objects.requireNonNull(effective, "effective")), firstFreeEndExclusive);
    }

    public ModPatternWindowAllocator(List<Request> requests, int firstFreeEndExclusive) {
        Objects.requireNonNull(requests, "requests");
        if (firstFreeEndExclusive < 0 || firstFreeEndExclusive % RANGE_ALIGNMENT != 0) {
            throw new IllegalArgumentException("firstFreeEndExclusive must be a nonnegative 0x"
                    + Integer.toHexString(RANGE_ALIGNMENT) + "-aligned pattern id");
        }
        List<Assignment> allocated = new ArrayList<>(requests.size());
        Set<String> owners = new HashSet<>();
        int nextBase = firstFreeEndExclusive;
        int total = 0;
        for (Request request : requests) {
            Objects.requireNonNull(request, "request");
            if (!owners.add(request.owner())) {
                throw new IllegalArgumentException("Duplicate pattern-window owner: " + request.owner());
            }
            total = Math.addExact(total, request.windows());
            if (total > MAX_WINDOWS) {
                throw new IllegalArgumentException("Pattern-window budget exceeded by " + request.owner());
            }
            Assignment assignment;
            try {
                assignment = new Assignment(request.owner(), nextBase,
                        request.windows(), WINDOW_SIZE);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(
                        "Pattern-window address space overflow for " + request.owner(), overflow);
            }
            allocated.add(assignment);
            nextBase = assignment.endExclusive();
        }
        assignments = List.copyOf(allocated);
    }

    @Override public List<Assignment> assignments() { return assignments; }

    private static List<Request> requests(EffectiveModCatalog effective) {
        return effective.orderedEnabled().stream().map(ModPatternWindowAllocator::request).toList();
    }

    private static Request request(ModDescriptor descriptor) {
        return new Request(descriptor.manifest().id(),
                descriptor.manifest().patternWindows().orElse(1));
    }

    public record Request(String owner, int windows) {
        public Request {
            owner = ModKeySyntax.requireManifestId(Objects.requireNonNull(owner, "owner"));
            if (windows < 1 || windows > 16) {
                throw new IllegalArgumentException("pattern windows must be in 1..16");
            }
        }
    }
}
