package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class RewindSchemaRegistry {
    private static final ConcurrentMap<Class<?>, RewindClassSchema> SCHEMAS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, RewindClassSchema> DEFAULT_OBJECT_SUBCLASS_SCHEMAS =
            new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SCHEMA_ID = new AtomicInteger(1);

    public static RewindClassSchema schemaFor(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return SCHEMAS.computeIfAbsent(type, RewindSchemaRegistry::buildSchema);
    }

    static RewindClassSchema defaultObjectSubclassSchemaFor(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (!AbstractObjectInstance.class.isAssignableFrom(type) || type == AbstractObjectInstance.class) {
            throw new IllegalArgumentException("Default object-subclass schema requires a concrete "
                    + "AbstractObjectInstance subclass: " + type.getName());
        }
        return DEFAULT_OBJECT_SUBCLASS_SCHEMAS.computeIfAbsent(type,
                RewindSchemaRegistry::buildDefaultObjectSubclassSchema);
    }

    static boolean supportsDefaultObjectSubclassScalars(Class<?> type) {
        return supportsDefaultObjectSubclassScalars(defaultObjectSubclassSchemaFor(type));
    }

    private static boolean supportsDefaultObjectSubclassScalars(RewindClassSchema schema) {
        return !schema.capturedFields().isEmpty() && schema.unsupportedFields().isEmpty();
    }

    /**
     * Current field selection for an ordinary concrete actor, including the badnik
     * route. Manual actors and nonactors retain their separate diagnostic contracts.
     * This describes capture now; it cannot predict selection after an annotation edit.
     * Queries initialize the same cached schemas (and schema IDs) as runtime capture.
     * Planning failures propagate, so an incomplete inventory cannot appear successful.
     * Inspected fields are freshly discovered; captured membership exposes only keys.
     */
    public static Optional<DefaultObjectFieldSelection> defaultObjectFieldSelectionForAudit(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (!GenericRewindEligibility.usesDefaultSubclassCapture(type)) {
            return Optional.empty();
        }
        RewindClassSchema schema = defaultObjectSubclassSchemaFor(type);
        boolean compact = supportsDefaultObjectSubclassScalars(schema);
        List<Field> captured = compact
                ? schema.capturedFields().stream().map(RewindFieldPlan::field).toList()
                : GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(type);
        List<Field> inspected = new ArrayList<>();
        for (Class<?> cls : defaultObjectSubclassHierarchy(type)) {
            inspected.addAll(sortedDeclaredFields(cls));
        }
        return Optional.of(new DefaultObjectFieldSelection(compact, inspected,
                captured.stream().map(FieldKey::of).collect(Collectors.toSet())));
    }

    public record DefaultObjectFieldSelection(boolean compact, List<Field> inspectedFields,
                                               Set<FieldKey> capturedFields) {
        public DefaultObjectFieldSelection {
            inspectedFields = List.copyOf(inspectedFields);
            capturedFields = Set.copyOf(capturedFields);
        }

        public boolean captures(Field field) {
            return capturedFields.contains(FieldKey.of(field));
        }
    }

    /** Authored/catalogue exclusions, independent of the selected capture projection. */
    public static boolean isExplicitDefaultObjectExclusion(Field field) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || field.isSynthetic()
                || field.isAnnotationPresent(RewindTransient.class)
                || field.isAnnotationPresent(RewindDeferred.class)) {
            return true;
        }
        RewindFieldPolicy policy = DefaultObjectRewindPolicies.policyFor(field);
        return policy == RewindFieldPolicy.TRANSIENT || policy == RewindFieldPolicy.DEFERRED;
    }

    public static void clearForTest() {
        SCHEMAS.clear();
        DEFAULT_OBJECT_SUBCLASS_SCHEMAS.clear();
        NEXT_SCHEMA_ID.set(1);
        RewindPolicyRegistry.clearForTest();
    }

    private static RewindClassSchema buildSchema(Class<?> type) {
        int schemaId = NEXT_SCHEMA_ID.getAndIncrement();
        List<RewindFieldPlan> fields = plannedFields(type);
        return new RewindClassSchema(schemaId, type, fields);
    }

    private static RewindClassSchema buildDefaultObjectSubclassSchema(Class<?> type) {
        int schemaId = NEXT_SCHEMA_ID.getAndIncrement();
        List<RewindFieldPlan> fields = defaultObjectSubclassPlannedFields(type);
        return new RewindClassSchema(schemaId, type, fields);
    }

    private static List<RewindFieldPlan> plannedFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> cls = type; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            hierarchy.add(cls);
        }
        Collections.reverse(hierarchy);

        List<RewindFieldPlan> fields = new ArrayList<>();
        for (Class<?> cls : hierarchy) {
            for (Field field : sortedDeclaredFields(cls)) {
                RewindCodec codec = RewindCodecs.codecFor(field).orElse(null);
                fields.add(new RewindFieldPlan(FieldKey.of(field), field, policyFor(field, codec), codec));
            }
        }
        return fields;
    }

    private static List<RewindFieldPlan> defaultObjectSubclassPlannedFields(Class<?> type) {
        List<RewindFieldPlan> fields = new ArrayList<>();
        for (Class<?> cls : defaultObjectSubclassHierarchy(type)) {
            for (Field field : sortedDeclaredFields(cls)) {
                RewindFieldPolicy configuredPolicy = DefaultObjectRewindPolicies.policyFor(field);
                if (configuredPolicy == RewindFieldPolicy.TRANSIENT
                        || configuredPolicy == RewindFieldPolicy.DEFERRED) {
                    continue;
                }
                if (!GenericFieldCapturer.isCapturedByDefaultObjectScalarPolicy(field)
                        && configuredPolicy != RewindFieldPolicy.CAPTURED
                        && !isObjectRefFieldAllowedInSchema(field, configuredPolicy)) {
                    continue;
                }
                RewindCodec codec = RewindCodecs.codecFor(field).orElse(null);
                fields.add(new RewindFieldPlan(
                        FieldKey.of(field),
                        field,
                        defaultObjectSubclassPolicyFor(field, codec, configuredPolicy),
                        codec));
            }
        }
        return fields;
    }

    /**
     * Derives independent scalar metadata without a schema ID. GenericFieldCapturer
     * owns the legacy runtime cache; this result does not expose its reflection
     * handles. Nonactor metadata queries retain their existing hierarchy contract.
     */
    public static List<Field> deriveDefaultObjectSubclassScalarFields(Class<?> type) {
        Objects.requireNonNull(type, "type");
        List<Field> fields = new ArrayList<>();
        for (Class<?> cls : defaultObjectSubclassHierarchy(type)) {
            // Discover separately from compact planning: accepted fields change their
            // accessibility, and admission/codec failures must stay lazy per projection.
            for (Field field : sortedDeclaredFields(cls)) {
                if (GenericFieldCapturer.isCapturedByDefaultObjectScalarPolicy(field)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return List.copyOf(fields);
    }

    private static List<Class<?>> defaultObjectSubclassHierarchy(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> cls = type;
                cls != null && cls != Object.class && cls != AbstractObjectInstance.class;
                cls = cls.getSuperclass()) {
            if (cls != AbstractBadnikInstance.class) {
                hierarchy.add(cls);
            }
        }
        Collections.reverse(hierarchy);

        return hierarchy;
    }

    /**
     * Returns {@code true} when an object-reference field that was not accepted by the
     * default generic scalar policy should still be included in the compact schema.
     *
     * <p>Specifically, a non-static, non-transient, non-final field whose declared type is
     * {@link com.openggf.level.objects.ObjectInstance} (or a subtype) AND whose configured
     * policy is not explicitly {@link RewindFieldPolicy#TRANSIENT} or
     * {@link RewindFieldPolicy#DEFERRED} is allowed through.  The field is then evaluated
     * by {@link #defaultObjectSubclassPolicyFor} which assigns {@link RewindFieldPolicy#CAPTURED}
     * (non-final, codec present, codec does not require an existing value) or
     * {@link RewindFieldPolicy#UNSUPPORTED} (final — {@code ObjectReferenceCodec} does not
     * capture finals).
     *
     * <p>Fields excluded by the policy table (e.g. entries in
     * {@link DefaultObjectRewindPolicies#STRUCTURAL_OBJECT_FIELD_NAMES}) are rejected by the
     * caller's outer gate. Annotation-only exclusions are checked here as well, because they do
     * not appear in {@code configuredPolicy} and must not be re-admitted by the object-ref path.
     */
    private static boolean isObjectRefFieldAllowedInSchema(Field field, RewindFieldPolicy configuredPolicy) {
        // Explicit TRANSIENT or DEFERRED: respect the policy and skip.
        if (configuredPolicy == RewindFieldPolicy.TRANSIENT
                || configuredPolicy == RewindFieldPolicy.DEFERRED
                || field.isAnnotationPresent(RewindTransient.class)
                || field.isAnnotationPresent(RewindDeferred.class)) {
            return false;
        }
        // Structural/synthetic fields are never captured.
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods)
                || Modifier.isTransient(mods)
                || field.isSynthetic()) {
            return false;
        }
        // Only non-final fields: ObjectReferenceCodec.capturesFinalFields() returns false,
        // so a final ref field would be UNSUPPORTED in defaultObjectSubclassPolicyFor.
        // Skipping finals here avoids adding UNSUPPORTED entries to the schema which would
        // cause CompactFieldCapturer.validateSupported to throw.
        if (Modifier.isFinal(mods)) {
            return false;
        }
        // Allow through when the field needs the identity table (object or player ref types).
        return RewindCodecs.requiresIdentityTable(field);
    }

    private static List<Field> sortedDeclaredFields(Class<?> cls) {
        List<Field> fields = new ArrayList<>(List.of(cls.getDeclaredFields()));
        fields.sort(Comparator
                .comparing(Field::getName)
                .thenComparing(field -> field.getType().getName()));
        return fields;
    }

    private static RewindFieldPolicy policyFor(Field field, RewindCodec codec) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods)
                || Modifier.isTransient(mods)
                || field.isSynthetic()
                || field.isAnnotationPresent(RewindTransient.class)) {
            return RewindFieldPolicy.TRANSIENT;
        }
        if (field.isAnnotationPresent(RewindDeferred.class)) {
            return RewindFieldPolicy.DEFERRED;
        }
        RewindFieldPolicy registeredPolicy = RewindPolicyRegistry.policyFor(field).orElse(null);
        if (registeredPolicy != null) {
            if (registeredPolicy == RewindFieldPolicy.CAPTURED && codec == null) {
                return RewindFieldPolicy.UNSUPPORTED;
            }
            return registeredPolicy;
        }
        if (Modifier.isFinal(mods) && codec != null && codec.capturesFinalFields()) {
            return RewindFieldPolicy.CAPTURED;
        }
        if (Modifier.isFinal(mods) && codec != null) {
            return RewindFieldPolicy.STRUCTURAL;
        }
        if (codec != null) {
            return RewindFieldPolicy.CAPTURED;
        }
        return RewindFieldPolicy.UNSUPPORTED;
    }

    private static RewindFieldPolicy defaultObjectSubclassPolicyFor(Field field,
                                                                    RewindCodec codec,
                                                                    RewindFieldPolicy configuredPolicy) {
        if (codec == null) {
            return RewindFieldPolicy.UNSUPPORTED;
        }
        if (configuredPolicy == RewindFieldPolicy.CAPTURED) {
            return RewindFieldPolicy.CAPTURED;
        }
        boolean finalField = Modifier.isFinal(field.getModifiers());
        if (finalField && !codec.capturesFinalFields()) {
            return RewindFieldPolicy.UNSUPPORTED;
        }
        if (!finalField && codec.requiresExistingTargetValue()
                && !RewindCodecs.supportsFreshTargetAllocation(field)) {
            return RewindFieldPolicy.UNSUPPORTED;
        }
        return RewindFieldPolicy.CAPTURED;
    }

    private RewindSchemaRegistry() {}
}
