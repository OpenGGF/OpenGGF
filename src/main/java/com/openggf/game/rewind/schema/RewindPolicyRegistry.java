package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameModule;
import com.openggf.game.InstaShieldHandle;
import com.openggf.game.PowerUpObject;
import com.openggf.game.PowerUpSpawner;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.physics.Sensor;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class RewindPolicyRegistry {
    // Renderer references are replaceable caches; the other built-in handles
    // require a final identity for ordinary-actor structural disposition.
    private static final Set<Class<?>> RENDERER_HANDLE_TYPES = Set.of(
            PatternSpriteRenderer.class, PlayerSpriteRenderer.class);
    private static final Map<Class<?>, RewindFieldPolicy> DEFAULT_DECLARED_TYPE_POLICIES = Map.ofEntries(
            Map.entry(GameModule.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(AbstractLevelEventManager.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(GraphicsManager.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(SonicConfigurationService.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(ObjectRenderManager.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(ObjectServices.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(Pattern.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(PatternDesc.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(InstaShieldHandle.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(PowerUpObject.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(PowerUpSpawner.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(Sensor.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(SpriteAnimationProfile.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(SpriteAnimationSet.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(SpriteMappingPiece.class, RewindFieldPolicy.TRANSIENT),
            Map.entry(SpritePieceRenderer.class, RewindFieldPolicy.TRANSIENT)
    );

    private static final Map<FieldKey, RewindFieldPolicy> FIELD_POLICIES = new LinkedHashMap<>();
    private static final Map<Class<?>, RewindFieldPolicy> DECLARED_TYPE_POLICIES = new LinkedHashMap<>();
    private static final Map<Class<?>, RewindFieldPolicy> ASSIGNABLE_TYPE_POLICIES = new LinkedHashMap<>();
    private static final List<PackagePolicy> PACKAGE_POLICIES = new ArrayList<>();

    public static synchronized void registerFieldPolicy(FieldKey field, RewindFieldPolicy policy) {
        FIELD_POLICIES.put(Objects.requireNonNull(field, "field"), requirePolicy(policy));
    }

    public static synchronized void registerDeclaredTypePolicy(Class<?> type, RewindFieldPolicy policy) {
        DECLARED_TYPE_POLICIES.put(Objects.requireNonNull(type, "type"), requirePolicy(policy));
    }

    public static synchronized void registerAssignableTypePolicy(Class<?> type, RewindFieldPolicy policy) {
        ASSIGNABLE_TYPE_POLICIES.put(Objects.requireNonNull(type, "type"), requirePolicy(policy));
    }

    public static synchronized void registerPackagePolicy(String packagePrefix, RewindFieldPolicy policy) {
        PACKAGE_POLICIES.add(new PackagePolicy(
                normalizePackagePrefix(packagePrefix),
                requirePolicy(policy)));
    }

    static synchronized Optional<RewindFieldPolicy> policyFor(Field field) {
        Objects.requireNonNull(field, "field");

        if (isKnownStructuralField(field)) {
            return Optional.of(RewindFieldPolicy.TRANSIENT);
        }

        RewindFieldPolicy fieldPolicy = FIELD_POLICIES.get(FieldKey.of(field));
        if (fieldPolicy != null) {
            return Optional.of(fieldPolicy);
        }

        Class<?> declaredType = field.getType();
        RewindFieldPolicy declaredTypePolicy = DECLARED_TYPE_POLICIES.get(declaredType);
        if (declaredTypePolicy != null) {
            return Optional.of(declaredTypePolicy);
        }
        if (declaredType.isArray()) {
            RewindFieldPolicy componentTypePolicy = DECLARED_TYPE_POLICIES.get(declaredType.getComponentType());
            if (componentTypePolicy != null) {
                return Optional.of(componentTypePolicy);
            }
            RewindFieldPolicy defaultComponentTypePolicy =
                    defaultDeclaredTypePolicy(declaredType.getComponentType());
            if (defaultComponentTypePolicy != null) {
                return Optional.of(defaultComponentTypePolicy);
            }
        }

        for (Map.Entry<Class<?>, RewindFieldPolicy> entry : ASSIGNABLE_TYPE_POLICIES.entrySet()) {
            if (entry.getKey().isAssignableFrom(declaredType)) {
                return Optional.of(entry.getValue());
            }
        }

        String packageName = declaredType.getPackageName();
        for (PackagePolicy policy : PACKAGE_POLICIES) {
            if (packageName.equals(policy.packagePrefix())
                    || packageName.startsWith(policy.packagePrefix() + ".")) {
                return Optional.of(policy.policy());
            }
        }

        RewindFieldPolicy defaultPolicy = defaultDeclaredTypePolicy(declaredType);
        if (defaultPolicy != null) {
            return Optional.of(defaultPolicy);
        }
        if (isStructuralCallbackType(declaredType)) {
            return Optional.of(RewindFieldPolicy.TRANSIENT);
        }

        RewindFieldPolicy defaultObjectPolicy = DefaultObjectRewindPolicies.policyFor(field);
        if (defaultObjectPolicy != null) {
            return Optional.of(defaultObjectPolicy);
        }
        if (isFinalStructuralList(field)) {
            return Optional.of(RewindFieldPolicy.TRANSIENT);
        }

        return Optional.empty();
    }

    /**
     * Built-in engine handles and stateless callbacks whose identities are
     * reconstructed, rather than gameplay values to capture. Replaceable renderer
     * caches retain their existing structural role; other handles require a final
     * identity. General policy registrations cannot extend this rule. A replaceable
     * tile descriptor still needs captured state or an explicit exclusion.
     */
    public static boolean isBuiltInStructuralHandle(Field field) {
        Class<?> type = field.getType();
        return RENDERER_HANDLE_TYPES.contains(type)
                || (Modifier.isFinal(field.getModifiers())
                        && (defaultDeclaredTypePolicy(type) == RewindFieldPolicy.TRANSIENT
                                || isStructuralCallbackType(type)));
    }

    private static RewindFieldPolicy defaultDeclaredTypePolicy(Class<?> type) {
        return RENDERER_HANDLE_TYPES.contains(type)
                ? RewindFieldPolicy.TRANSIENT : DEFAULT_DECLARED_TYPE_POLICIES.get(type);
    }

    private static boolean isStructuralCallbackType(Class<?> type) {
        return (type.isInterface() && type.getSimpleName().endsWith("RendererRef"))
                || type == BooleanSupplier.class || type == IntSupplier.class
                || (type.getPackageName().contains(".events")
                        && type.getSimpleName().endsWith("Events"))
                || (type.getSimpleName().endsWith("Handler")
                        && type.getEnclosingClass() != null && hasNoStateFields(type));
    }

    private static boolean isKnownStructuralField(Field field) {
        String declaringClass = field.getDeclaringClass().getName();
        String fieldName = field.getName();
        return declaringClass.equals("com.openggf.level.objects.boss.AbstractBossInstance")
                && fieldName.equals("childComponents")
                || field.getType().getSimpleName().equals("CaterkillerParentState");
    }

    private static boolean hasNoStateFields(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (!Modifier.isStatic(mods) && !field.isSynthetic()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFinalStructuralList(Field field) {
        if (!Modifier.isFinal(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
            return false;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        Type[] args = parameterizedType.getActualTypeArguments();
        if (args.length != 1 || !(args[0] instanceof Class<?> elementType)) {
            return false;
        }
        return defaultDeclaredTypePolicy(elementType) != null
                || (elementType.isRecord() && RewindCodecs.supports(elementType));
    }

    public static synchronized Optional<RewindFieldPolicy> policyForAudit(Field field) {
        return policyFor(field);
    }

    static synchronized void clearForTest() {
        FIELD_POLICIES.clear();
        DECLARED_TYPE_POLICIES.clear();
        ASSIGNABLE_TYPE_POLICIES.clear();
        PACKAGE_POLICIES.clear();
    }

    private static RewindFieldPolicy requirePolicy(RewindFieldPolicy policy) {
        return Objects.requireNonNull(policy, "policy");
    }

    private static String normalizePackagePrefix(String packagePrefix) {
        String normalized = Objects.requireNonNull(packagePrefix, "packagePrefix").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("packagePrefix must not be empty");
        }
        return normalized;
    }

    private record PackagePolicy(String packagePrefix, RewindFieldPolicy policy) {
    }

    private RewindPolicyRegistry() {}
}
