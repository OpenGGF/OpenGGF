package com.openggf.level.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal read-only registry metadata. Enumeration never calls a factory: even a
 * probe constructor can publish native art, palette or hardware work.
 */
public final class ObjectRegistryCatalog {
    private ObjectRegistryCatalog() { }

    /** Logical source coordinates used by a game's existing zone registry. */
    public record Context(int zone, int act) {
        public Context {
            if (zone < 0 || act < 0)
                throw new IllegalArgumentException("Negative object catalog context");
        }
    }

    public enum FactoryStatus { REGISTERED, CONTEXT_REJECTED, ABSENT, UNKNOWN_REGISTRY }
    public enum Role { UNRESOLVED, OBJECT, BADNIK, BOSS, ANIMAL }
    public enum PositionRule { UNVERIFIED, SPAWN_ANCHOR, NATIVE_ARENA }
    public record AnimalVariant(int nativeZone, int artVariant, AnimalType type) {
        public AnimalVariant { Objects.requireNonNull(type); }
    }
    public record AuxiliaryFactory(String key, String name, List<String> routineCitations,
                                   List<AnimalVariant> animalVariants, DestructionEffects.AnimalFactory factory) {
        public AuxiliaryFactory {
            Objects.requireNonNull(key); Objects.requireNonNull(name); Objects.requireNonNull(factory);
            routineCitations = List.copyOf(routineCitations); animalVariants = List.copyOf(animalVariants);
        }
    }

    /** Provider-owned semantic declarations, independent of factory names or implementation classes. */
    public record Semantics(Role role, PositionRule positionRule, List<String> routineCitations,
                            List<String> heldAppearanceRestrictions, List<String> unsupportedAppearanceRestrictions) {
        public static final Semantics NONE = new Semantics(Role.UNRESOLVED, PositionRule.UNVERIFIED, List.of());
        public Semantics(Role role, PositionRule positionRule, List<String> routineCitations) {
            this(role, positionRule, routineCitations, List.of());
        }
        public Semantics(Role role, PositionRule positionRule, List<String> routineCitations,
                         List<String> heldAppearanceRestrictions) {
            this(role, positionRule, routineCitations, heldAppearanceRestrictions, List.of());
        }
        public Semantics {
            Objects.requireNonNull(role); Objects.requireNonNull(positionRule);
            routineCitations = List.copyOf(routineCitations);
            heldAppearanceRestrictions = List.copyOf(heldAppearanceRestrictions);
            unsupportedAppearanceRestrictions = List.copyOf(unsupportedAppearanceRestrictions);
            if (heldAppearanceRestrictions.stream().anyMatch(String::isBlank)
                    || unsupportedAppearanceRestrictions.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Blank native appearance restriction");
            if (routineCitations.stream().anyMatch(String::isBlank)
                    || positionRule != PositionRule.UNVERIFIED && routineCitations.isEmpty())
                throw new IllegalArgumentException(
                        "Native position declarations require routine citations");
        }
    }

    /** Registration is an admission candidate, not proof that its factory returns a concrete object. */
    public record Entry(int objectId, String name, String objectTable, FactoryStatus status,
                        String selection, String previewArtKey, boolean constructorContextChecks, Semantics semantics) {
        public Entry {
            if (objectId < 0 || objectId > 255)
                throw new IllegalArgumentException("Object ID outside byte range");
            Objects.requireNonNull(name); Objects.requireNonNull(objectTable); Objects.requireNonNull(status);
            Objects.requireNonNull(selection); Objects.requireNonNull(previewArtKey);
            Objects.requireNonNull(semantics);
        }
    }

    /** Complete byte-ID domain, including unregistered and context-rejected IDs. */
    public static List<Entry> entries(ObjectRegistry registry, Context context) {
        Objects.requireNonNull(registry); Objects.requireNonNull(context);
        var result = new ArrayList<Entry>(256);
        for (int id = 0; id <= 255; id++) {
            result.add(entry(registry, context, id));
        }
        return List.copyOf(result);
    }

    /** Single native byte-ID lookup; never enumerates the table or invokes a factory. */
    public static Entry entry(ObjectRegistry registry, Context context, int objectId) {
        Objects.requireNonNull(registry);
        Objects.requireNonNull(context);
        if (objectId < 0 || objectId > 255) {
            throw new IllegalArgumentException("Object ID outside byte range");
        }
        return registry instanceof AbstractObjectRegistry nativeRegistry
                ? nativeRegistry.catalogEntry(objectId, context)
                : new Entry(
                        objectId,
                        registry.getPrimaryName(objectId),
                        "unknown",
                        FactoryStatus.UNKNOWN_REGISTRY,
                        "unknown",
                        registry.editorPreviewArtKey(objectId).orElse(""),
                        true,
                        Semantics.NONE);
    }

    /** Native non-layout animal allocation, deliberately separate from the byte-ID table. */
    public static Optional<AuxiliaryFactory> releasedAnimalFactory(
            ObjectRegistry registry, Context context) {
        Objects.requireNonNull(registry);
        Objects.requireNonNull(context);
        return registry instanceof AbstractObjectRegistry nativeRegistry
                ? nativeRegistry.releasedAnimalFactory(context)
                : Optional.empty();
    }
}
