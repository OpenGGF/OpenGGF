package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prevents obsolete exact dispositions from silently authorizing replacement fields. */
class TestExactRewindPolicyGuard {
    @Test
    void everyExactDispositionNamesADeclaredRuntimeField() {
        List<String> stale = unresolvable(DefaultObjectRewindPolicies.exactFieldPoliciesForAudit());
        assertTrue(stale.isEmpty(), () -> "Stale exact rewind policies:\n" + String.join("\n", stale));
    }

    @ParameterizedTest
    @EnumSource(RewindFieldPolicy.class)
    void everyDispositionRejectsAFieldThatNoLongerExists(RewindFieldPolicy policy) {
        assertEquals(1, unresolvable(Map.of(key(Fields.class, "removed"), policy)).size());
    }

    @ParameterizedTest
    @EnumSource(RewindFieldPolicy.class)
    void everyDispositionAcceptsItsActualDeclaringField(RewindFieldPolicy policy) {
        assertEquals(List.of(), unresolvable(Map.of(key(Fields.class, "value"), policy)));
    }

    @Test
    void aMissingOwnerFailsRatherThanBeingSkipped() {
        var missing = new FieldKey("com.openggf.game.rewind.schema.NoSuchPolicyOwner", "value");
        assertEquals(1, unresolvable(Map.of(missing, RewindFieldPolicy.TRANSIENT)).size());
    }

    @Test
    void inheritedFieldsRequireTheirActualDeclaringOwner() {
        assertEquals(1, unresolvable(Map.of(key(Derived.class, "value"), RewindFieldPolicy.TRANSIENT)).size());
        assertEquals(List.of(), unresolvable(Map.of(key(Fields.class, "value"), RewindFieldPolicy.TRANSIENT)));
    }

    @Test
    void inspectingAFieldDoesNotInitializeItsProductionOwner() {
        assertEquals(List.of(), unresolvable(Map.of(key(NeverInitialize.class, "value"),
                RewindFieldPolicy.TRANSIENT)));
    }

    private static FieldKey key(Class<?> owner, String name) {
        return new FieldKey(owner.getName(), name);
    }

    private static List<String> unresolvable(Map<FieldKey, RewindFieldPolicy> policies) {
        List<String> errors = new ArrayList<>();
        for (FieldKey key : policies.keySet()) {
            try {
                Class.forName(key.declaringClassName(), false, TestExactRewindPolicyGuard.class.getClassLoader())
                        .getDeclaredField(key.fieldName());
            } catch (ReflectiveOperationException failure) {
                errors.add(key + " -> " + failure);
            }
        }
        return errors.stream().sorted().toList();
    }

    private static class Fields {
        private int value;
    }

    private static final class Derived extends Fields { }

    private static final class NeverInitialize {
        private static final Object INITIALIZATION_SENTINEL = failIfInitialized();
        private int value;

        private static Object failIfInitialized() {
            throw new AssertionError("A field-policy census must not initialize production owners");
        }
    }
}
