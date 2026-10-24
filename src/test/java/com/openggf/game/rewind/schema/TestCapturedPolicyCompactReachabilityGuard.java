package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindScanSupport;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.level.objects.PlayerControlOwners;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic3k.objects.TensionBridgeObjectInstance;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossBladeImpactExplosion;
import com.openggf.game.sonic3k.objects.bosses.HczEndBossEggCapsuleButton;
import com.openggf.game.sonic3k.objects.MGZTopPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.FbzEndBossInstance;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the compact-schema reachability of explicit {@code CAPTURED} collection
 * policies. A collection/map field (e.g. a per-player grab-state map) is only
 * capturable through the compact schema path; if any other field of the class is
 * unsupported, the whole class silently falls back to the generic scalar path and
 * the policy does nothing — the MGZ spinning-top grab state was lost across
 * rewind exactly this way.
 */
class TestCapturedPolicyCompactReachabilityGuard {

    @Test
    void optionalNativeLinksHaveClosedExactCapturedMetadata() throws Exception {
        var expected = java.util.Set.of(
                new FieldKey("com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance", "parent"),
                new FieldKey("com.openggf.game.sonic2.objects.badniks.RexonHeadObjectInstance", "linkedHead"),
                new FieldKey("com.openggf.game.sonic2.objects.badniks.SolFireballObjectInstance", "parent"),
                new FieldKey("com.openggf.game.sonic3k.objects.CutsceneKnucklesLbz2Instance$SwingChild", "parent"));
        var actual = new java.util.HashSet<FieldKey>();
        for (var root : RewindScanSupport.discoverRuntimeOwnerClasses()) {
            for (var type : RewindScanSupport.withNestedRuntimeOwnerClasses(root)) {
                for (var field : type.getDeclaredFields()) {
                    if (!DefaultObjectRewindPolicies.isOptionalObjectReference(field)) continue;
                    var key = FieldKey.of(field);
                    actual.add(key);
                    assertEquals(RewindFieldPolicy.CAPTURED,
                            DefaultObjectRewindPolicies.exactFieldPoliciesForAudit().get(key));
                    assertTrue(RewindCodecs.requiresIdentityTable(field), "closure diagnostics still inspect optional links");
                    assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(type));
                    assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(type));
                    assertTrue(RewindSchemaRegistry.defaultObjectSubclassSchemaFor(type).capturedFields().stream()
                            .anyMatch(plan -> plan.field().equals(field)), field.toString());
                }
            }
        }
        assertEquals(expected, actual);
    }

    @Test
    void acquiredPlayerFieldsAlwaysReachTheCentralIdentityCodec() throws Exception {
        var failures = new ArrayList<String>();
        int audited = 0;
        var roots = new ArrayList<>(RewindScanSupport.discoverRuntimeOwnerClasses());
        // Ending controllers live outside the ordinary object source roots.
        var endingRoot = java.nio.file.Path.of("src/main/java/com/openggf/game/sonic3k/ending");
        try (var sources = java.nio.file.Files.walk(endingRoot)) {
            for (var path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = path.getFileName().toString().replaceFirst("\\.java$", "");
                roots.add(Class.forName("com.openggf.game.sonic3k.ending." + name, false,
                        getClass().getClassLoader()));
            }
        }
        for (var root : roots) {
            for (var type : RewindScanSupport.withNestedRuntimeOwnerClasses(root)) {
                for (var field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !containsPlayerReference(field.getGenericType())) continue;
                    boolean ownershipSet = field.getType() == PlayerControlOwners.class;
                    // Bespoke snapshots and affirmative exclusions have their own behavioral checks.
                    // Acquired-player sets are never structural or excluded.
                    if (!ownershipSet && (!GenericRewindEligibility.usesCompactDefaultSubclassCapture(type)
                            && !GenericRewindEligibility.usesDefaultObjectSubclassCapture(type)
                            && !GenericRewindEligibility.usesDefaultBadnikSubclassCapture(type))) continue;
                    var policy = DefaultObjectRewindPolicies.policyFor(field);
                    if (!ownershipSet && (Modifier.isTransient(field.getModifiers())
                            || field.isAnnotationPresent(RewindTransient.class)
                            || field.isAnnotationPresent(com.openggf.game.rewind.RewindDeferred.class)
                            || policy == RewindFieldPolicy.TRANSIENT || policy == RewindFieldPolicy.DEFERRED)) continue;
                    audited++;
                    failures.addAll(ownershipFieldProblems(type, field));
                }
            }
        }
        assertTrue(audited >= 50, "Audit the complete holder population, including nested owners");
        System.out.println("Central acquired-player codec audit: " + audited + " fields");
        assertEquals(List.of(), failures,
                "Acquired-player references must reach a central identity-aware codec, never scalar fallback");
    }

    @Test
    void ownershipAuditRejectsExcludedAndRawPlayerFields() throws Exception {
        assertFalse(ownershipFieldProblems(ExcludedOwner.class,
                ExcludedOwner.class.getDeclaredField("owners")).isEmpty());
        assertFalse(ownershipFieldProblems(RawOwners.class,
                RawOwners.class.getDeclaredField("owners")).isEmpty());
    }

    private static boolean containsPlayerReference(java.lang.reflect.Type type) {
        if (type instanceof Class<?> cls)
            return cls == PlayerControlOwners.class || com.openggf.game.PlayableEntity.class.isAssignableFrom(cls)
                    || (cls.isArray() && containsPlayerReference(cls.getComponentType()));
        if (type instanceof java.lang.reflect.ParameterizedType parameterized)
            return java.util.Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(TestCapturedPolicyCompactReachabilityGuard::containsPlayerReference);
        return false;
    }

    private static List<String> ownershipFieldProblems(Class<?> type, Field field) {
        boolean defaultCapture = AbstractBadnikInstance.class.isAssignableFrom(type)
                ? GenericRewindEligibility.usesDefaultBadnikSubclassCapture(type)
                : GenericRewindEligibility.usesDefaultObjectSubclassCapture(type);
        var schema = defaultCapture ? RewindSchemaRegistry.defaultObjectSubclassSchemaFor(type)
                : RewindSchemaRegistry.schemaFor(type);
        var plan = schema.capturedFields().stream().filter(p -> p.field().equals(field)).findFirst();
        if (plan.isEmpty() || !plan.get().codec().requiresIdentityTable()
                || (defaultCapture && !GenericRewindEligibility.usesCompactDefaultSubclassCapture(type)))
            return List.of(type.getName() + "#" + field.getName());
        return List.of();
    }

    private static final class ExcludedOwner extends AbstractObjectInstance {
        @RewindTransient(reason = "Deliberately invalid ownership exclusion for the guard regression") private final PlayerControlOwners owners = new PlayerControlOwners();
        ExcludedOwner() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "excluded-owner"); }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    private static final class RawOwners extends AbstractObjectInstance {
        private final List<AbstractPlayableSprite> owners = new ArrayList<>();
        RawOwners() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "raw-owners"); }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }

    @Test
    void hczResultAndBossReferencesUseExactCapturedPolicies() {
        Map<FieldKey, RewindFieldPolicy> policies =
                DefaultObjectRewindPolicies.exactFieldPoliciesForAudit();

        assertEquals(RewindFieldPolicy.CAPTURED, policies.get(new FieldKey(
                HczEndBossEggCapsuleButton.class.getName(), "parent")));
        assertEquals(RewindFieldPolicy.CAPTURED, policies.get(new FieldKey(
                HczEndBossBladeImpactExplosion.class.getName(), "boss")));
        assertFalse(policies.containsKey(new FieldKey(
                TensionBridgeObjectInstance.class.getName(), "playerAtCollapse")),
                "deleted bridge fields must not leave unreachable exact policies behind");
    }

    @Test
    void fbzEndBossDerivedCollectionsAreDeferredAndFamilyReferencesCaptured() {
        Map<FieldKey, RewindFieldPolicy> policies =
                DefaultObjectRewindPolicies.exactFieldPoliciesForAudit();
        for (String field : List.of("arms", "joints", "chainLinks")) {
            assertEquals(RewindFieldPolicy.DEFERRED,
                    policies.get(new FieldKey(FbzEndBossInstance.class.getName(), field)));
        }
        assertEquals(RewindFieldPolicy.CAPTURED, policies.get(new FieldKey(
                "com.openggf.game.sonic3k.objects.AbstractFbzEndBossChild", "boss")));
    }

    @Test
    void mgzTopPlatformUsesCompactCapture() {
        assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(MGZTopPlatformObjectInstance.class),
                "MGZ top platform must stay on the default capture path");
        assertTrue(CompactFieldCapturer.supportsDefaultObjectSubclassScalars(MGZTopPlatformObjectInstance.class),
                "MGZ top platform must be compact-schema capturable so playerStates (grab map) rides keyframes");
    }

    @Test
    void capturedPoliciesNeedingCompactPathAreReachable() {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<FieldKey, RewindFieldPolicy> entry
                : DefaultObjectRewindPolicies.exactFieldPoliciesForAudit().entrySet()) {
            if (entry.getValue() != RewindFieldPolicy.CAPTURED) {
                continue;
            }
            Class<?> declaring;
            Field field;
            try {
                declaring = Class.forName(entry.getKey().declaringClassName());
                field = declaring.getDeclaredField(entry.getKey().fieldName());
            } catch (ReflectiveOperationException e) {
                failures.add(entry.getKey() + " -> unresolvable: " + e);
                continue;
            }
            if (com.openggf.game.rewind.GenericFieldCapturer.isCapturedByDefaultObjectScalarPolicy(field)) {
                continue; // Also captured by the generic fallback path, so reachability is moot.
            }
            if (!AbstractObjectInstance.class.isAssignableFrom(declaring)
                    || AbstractBadnikInstance.class.isAssignableFrom(declaring)
                    || Modifier.isAbstract(declaring.getModifiers())
                    || !GenericRewindEligibility.usesDefaultObjectSubclassCapture(declaring)) {
                continue; // Concrete capture overrides own their coverage explicitly.
            }
            if (!CompactFieldCapturer.supportsDefaultObjectSubclassScalars(declaring)) {
                RewindClassSchema schema = RewindSchemaRegistry.defaultObjectSubclassSchemaFor(declaring);
                List<String> unsupported = schema.unsupportedFields().stream()
                        .map(plan -> plan.key().toString())
                        .toList();
                failures.add(entry.getKey() + " policy CAPTURED is unreachable: class falls back to the "
                        + "generic scalar path, which cannot capture this field. Unsupported fields: "
                        + unsupported);
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }
}
