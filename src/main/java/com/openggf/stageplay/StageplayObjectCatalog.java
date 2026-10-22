package com.openggf.stageplay;

import com.openggf.game.GameModule;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectRegistryCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Complete native-ID authoring inventory, shared by tools and both editor frontends.
 * Registration, resolved object identity, ROM art and directed visual readiness are
 * separate evidence. Catalog enumeration never constructs or updates an object.
 * Origin: Stageplay complete-cast task, 2026-10-22.
 */
public final class StageplayObjectCatalog {
    private StageplayObjectCatalog() { }
    private record Source(String gameId, int zone, int act) { }
    private static final java.util.concurrent.ConcurrentMap<Source, Catalog> STOCK = new java.util.concurrent.ConcurrentHashMap<>();

    public static final String NATIVE_ARENA_LABEL = "Free: native arena position, anchor ignored";
    public static final String VISUAL_ONLY_LABEL = "Puppet/Pose: visual only; native routines and child routines suspended";

    public enum Readiness { NEEDS_PREFLIGHT, UNAVAILABLE }
    public enum Animation { UNDECLARED, HELD_OR_AUTHORED_FRAME }

    public enum Reason {
        NO_LAYOUT_FACTORY("No registered layout factory; this does not exclude native parent-created or event-created routines."),
        CONTEXT_REJECTED("The registered factory rejects this native pointer-table/zone context."),
        UNKNOWN_REGISTRY("This registry does not expose internal factory metadata."),
        FACTORY_NOT_RESOLVED("Registration alone does not prove construction, subtype acceptance or native world prerequisites."),
        VISUAL_METADATA_MISSING("Visual support is unobserved. Preflight may capture a constructor-ready graph; otherwise initialize Free in the native set, then capture the complete visible graph before Puppet/Pose. Directed modes never run native initialization."),
        UNSUPPORTED_RENDER_ORDER("The native draw contains SAT controls or interleaves primitives and tiles that the directed visual template cannot preserve; Free rendering remains native."),
        ART_NOT_CHECKED("The supplied ROM art has not been checked in this source context."),
        STATE_MACHINE_ANIMATION_HELD("This appearance is owned by a native state machine, not an Animate_Sprite table; its appearance is held or explicitly authored."),
        NATIVE_ARENA_POSITION("Free: native arena position, anchor ignored");

        private final String description;
        Reason(String description) { this.description = description; }
        public String description() { return description; }
    }

    public record Capability(Readiness readiness, List<Reason> reasons) {
        public Capability {
            Objects.requireNonNull(readiness); reasons = List.copyOf(reasons);
        }
    }

    /** Source coordinates are the existing module's logical zone/act, not fabricated ROM offsets. */
    public record Context(int zone, int act, String name) {
        public Context {
            if (zone < 0 || act < 0) throw new IllegalArgumentException("Negative object source context");
            Objects.requireNonNull(name);
        }
    }

    public record Entry(int objectId, String name, String objectTable,
                        ObjectRegistryCatalog.FactoryStatus factoryStatus, String factorySelection,
                        String previewArtKey, ObjectRegistryCatalog.Role role, ObjectRegistryCatalog.PositionRule positionRule,
                        Capability free, Capability puppet, Capability pose,
                        List<String> artKeys, Animation animation, List<String> routineCitations,
                        List<String> appearanceExclusions) {
        public Entry {
            if (objectId < 0 || objectId > 255) throw new IllegalArgumentException("Object ID outside byte range");
            Objects.requireNonNull(name); Objects.requireNonNull(objectTable); Objects.requireNonNull(factoryStatus);
            Objects.requireNonNull(factorySelection); Objects.requireNonNull(previewArtKey); Objects.requireNonNull(role);
            Objects.requireNonNull(positionRule);
            Objects.requireNonNull(free); Objects.requireNonNull(puppet); Objects.requireNonNull(pose);
            artKeys = List.copyOf(artKeys); Objects.requireNonNull(animation); routineCitations = List.copyOf(routineCitations);
            appearanceExclusions = List.copyOf(appearanceExclusions);
        }

        public String freeRestriction() {
            return positionRule == ObjectRegistryCatalog.PositionRule.NATIVE_ARENA ? NATIVE_ARENA_LABEL
                    : "Free: native routine and world effects; source/arena prerequisites require preflight";
        }
        public String citation() {
            return routineCitations.isEmpty() ? "Owning native routine citation not declared" : String.join("; ", routineCitations);
        }
        public String modeSummary() {
            return "Free " + free.readiness() + "; Puppet " + puppet.readiness() + "; Pose " + pose.readiness();
        }
    }

    public record AuxiliaryEntry(String key, String name, ObjectRegistryCatalog.Role role, Capability free, Capability puppet,
                                 Capability pose, List<ObjectRegistryCatalog.AnimalVariant> animalVariants,
                                 List<String> routineCitations) {
        public AuxiliaryEntry { animalVariants = List.copyOf(animalVariants); routineCitations = List.copyOf(routineCitations); }
    }

    public record Catalog(String gameId, Context context, List<Entry> entries, List<AuxiliaryEntry> auxiliaryEntries) {
        public Catalog {
            Objects.requireNonNull(gameId); Objects.requireNonNull(context); entries = List.copyOf(entries);
            auxiliaryEntries = List.copyOf(auxiliaryEntries);
            if (entries.size() != 256) throw new IllegalArgumentException("Native object catalog must cover all 256 IDs");
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).objectId() != i)
                throw new IllegalArgumentException("Native object catalog must be ordered by byte ID");
        }
    }

    /** Every context exposed by the native zone registry, including bonus and ending contexts. */
    public static List<Catalog> inspect(GameModule module) {
        Objects.requireNonNull(module);
        var registry = module.createObjectRegistry();
        var zones = module.getZoneRegistry();
        var result = new ArrayList<Catalog>();
        for (int zone = 0; zone < zones.getZoneCount(); zone++) {
            for (int act = 0; act < zones.getActCount(zone); act++) {
                result.add(inspect(module.getGameId().code(), registry, new Context(zone, act, zones.getZoneName(zone))));
            }
        }
        return List.copyOf(result);
    }

    public static Catalog inspect(String gameId, ObjectRegistry registry, Context context) {
        var entries = ObjectRegistryCatalog.entries(registry, new ObjectRegistryCatalog.Context(context.zone(), context.act()))
                .stream().map(StageplayObjectCatalog::registration).toList();
        var auxiliary = ObjectRegistryCatalog.releasedAnimalFactory(registry,
                new ObjectRegistryCatalog.Context(context.zone(), context.act())).stream().map(factory -> {
            var free = capability(Readiness.NEEDS_PREFLIGHT, Reason.FACTORY_NOT_RESOLVED);
            var visual = capability(Readiness.NEEDS_PREFLIGHT, Reason.VISUAL_METADATA_MISSING, Reason.ART_NOT_CHECKED,
                    Reason.STATE_MACHINE_ANIMATION_HELD);
            return new AuxiliaryEntry(factory.key(), factory.name(), ObjectRegistryCatalog.Role.ANIMAL, free, visual, visual,
                    factory.animalVariants(), factory.routineCitations());
        }).toList();
        return new Catalog(gameId, context, entries, auxiliary);
    }

    /** Convenient ROM-free source browser for either editor. No active world is consulted. */
    public static Catalog inspect(String gameId, int zone, int act) {
        return STOCK.computeIfAbsent(new Source(gameId, zone, act), source -> {
            var module = GameModuleRegistry.createStockModule(GameId.fromCode(source.gameId()));
            var zones = module.getZoneRegistry();
            if (source.zone() < 0 || source.zone() >= zones.getZoneCount() || source.act() < 0 || source.act() >= zones.getActCount(source.zone()))
                throw new IllegalArgumentException("Object catalog source context is outside the native zone registry");
            return inspect(module.getGameId().code(), module.createObjectRegistry(),
                    new Context(source.zone(), source.act(), zones.getZoneName(source.zone())));
        });
    }

    public static Entry entry(String gameId, int zone, int act, int objectId) {
        if (objectId < 0 || objectId > 255) throw new IllegalArgumentException("Object ID outside byte range");
        return inspect(gameId, zone, act).entries().get(objectId);
    }

    private static Entry registration(ObjectRegistryCatalog.Entry entry) {
        var free = switch (entry.status()) {
            case ABSENT -> capability(Readiness.UNAVAILABLE, Reason.NO_LAYOUT_FACTORY);
            case CONTEXT_REJECTED -> capability(Readiness.UNAVAILABLE, Reason.CONTEXT_REJECTED);
            case UNKNOWN_REGISTRY -> capability(Readiness.NEEDS_PREFLIGHT, Reason.UNKNOWN_REGISTRY);
            case REGISTERED -> capability(Readiness.NEEDS_PREFLIGHT, Reason.FACTORY_NOT_RESOLVED);
        };
        var visual = switch (entry.status()) {
            case ABSENT -> capability(Readiness.UNAVAILABLE, Reason.NO_LAYOUT_FACTORY);
            case CONTEXT_REJECTED -> capability(Readiness.UNAVAILABLE, Reason.CONTEXT_REJECTED);
            case REGISTERED, UNKNOWN_REGISTRY -> capability(Readiness.NEEDS_PREFLIGHT,
                    Reason.VISUAL_METADATA_MISSING, Reason.ART_NOT_CHECKED);
        };
        var semantics = entry.semantics();
        if (!semantics.heldAppearanceRestrictions().isEmpty())
            visual = capability(visual.readiness(), Reason.VISUAL_METADATA_MISSING, Reason.ART_NOT_CHECKED,
                    Reason.STATE_MACHINE_ANIMATION_HELD);
        if (!semantics.unsupportedAppearanceRestrictions().isEmpty()) {
            var reasons = new ArrayList<>(visual.reasons()); reasons.add(Reason.UNSUPPORTED_RENDER_ORDER);
            visual = new Capability(visual.readiness(), reasons);
        }
        if (semantics.positionRule() == ObjectRegistryCatalog.PositionRule.NATIVE_ARENA) {
            var reasons = new ArrayList<>(free.reasons()); reasons.add(Reason.NATIVE_ARENA_POSITION);
            free = new Capability(free.readiness(), reasons);
        }
        var exclusions = new ArrayList<>(semantics.heldAppearanceRestrictions());
        exclusions.addAll(semantics.unsupportedAppearanceRestrictions());
        return new Entry(entry.objectId(), entry.name(), entry.objectTable(), entry.status(), entry.selection(),
                entry.previewArtKey(), semantics.role(), semantics.positionRule(),
                free, visual, visual, List.of(), semantics.heldAppearanceRestrictions().isEmpty()
                        ? Animation.UNDECLARED : Animation.HELD_OR_AUTHORED_FRAME,
                semantics.routineCitations(), exclusions);
    }

    private static Capability capability(Readiness readiness, Reason... reasons) { return new Capability(readiness, List.of(reasons)); }
}
