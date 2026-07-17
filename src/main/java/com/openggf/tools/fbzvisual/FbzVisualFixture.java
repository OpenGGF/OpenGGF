package com.openggf.tools.fbzvisual;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Applies declared fixture mutations with mandatory precondition and readback proof. */
public final class FbzVisualFixture {

    private final FbzVisualFixturePort port;

    public FbzVisualFixture(FbzVisualFixturePort port) {
        this.port = Objects.requireNonNull(port, "port");
    }

    public FbzVisualStateProbe.Snapshot applyVerified(Mutation mutation) {
        Objects.requireNonNull(mutation, "mutation");
        FbzVisualStateProbe.Snapshot before = port.snapshot();
        requireMatches("precondition", before.values(), mutation.expectedPreState());
        for (Map.Entry<String, Object> write : mutation.writes().entrySet()) {
            port.write(write.getKey(), write.getValue());
            Object actual = port.snapshot().values().get(write.getKey());
            if (!Objects.equals(write.getValue(), actual)) {
                throw new IllegalStateException("FBZ fixture readback mismatch for " + write.getKey()
                        + ": expected " + write.getValue() + ", got " + actual);
            }
        }
        return port.snapshot();
    }

    private static void requireMatches(String phase, Map<String, Object> actual,
                                       Map<String, Object> expected) {
        for (Map.Entry<String, Object> entry : expected.entrySet()) {
            Object value = actual.get(entry.getKey());
            if (!Objects.equals(entry.getValue(), value)) {
                throw new IllegalStateException("FBZ fixture " + phase + " mismatch for "
                        + entry.getKey() + ": expected " + entry.getValue() + ", got " + value);
            }
        }
    }

    public record Mutation(Map<String, Object> expectedPreState, Map<String, Object> writes) {
        public Mutation {
            expectedPreState = immutable(expectedPreState, "expectedPreState");
            writes = immutable(writes, "writes");
        }

        private static Map<String, Object> immutable(Map<String, Object> source, String name) {
            Objects.requireNonNull(source, name);
            return Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }
}
