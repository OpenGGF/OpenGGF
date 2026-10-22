package com.openggf.stageplay;

import com.openggf.game.GameModule;
import com.openggf.game.GameId;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.mutators.ObjectHeadRole;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectRegistryCatalog;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.PlaceholderObjectInstance;

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

    public static final String NATIVE_ARENA_LABEL = "Free: native arena position, anchor ignored";
    public static final String VISUAL_ONLY_LABEL = "Puppet/Pose: visual only; native routines and child routines suspended";

    public enum Readiness { AVAILABLE, NEEDS_PREFLIGHT, UNAVAILABLE }
    public enum Role { UNRESOLVED, OBJECT, BADNIK, BOSS, ANIMAL }
    public enum Animation { UNDECLARED, ROM_SCRIPT, HELD_OR_AUTHORED_FRAME }

    public enum Reason {
        NO_LAYOUT_FACTORY("No registered layout factory; this does not exclude native parent-created or event-created routines."),
        CONTEXT_REJECTED("The registered factory rejects this native pointer-table/zone context."),
        UNKNOWN_REGISTRY("This registry does not expose internal factory metadata."),
        FACTORY_NOT_RESOLVED("Registration alone does not prove construction, subtype acceptance or native world prerequisites."),
        FACTORY_RETURNED_PLACEHOLDER("The selected factory returned a placeholder for this source context/subtype."),
        NATIVE_WORLD_EFFECTS("Free runs the real native routine, including native world, arena, player, art and audio effects."),
        VISUAL_METADATA_MISSING("Visual support is unobserved. Preflight may capture a constructor-ready graph; otherwise initialize Free in the native set, then capture the complete visible graph before Puppet/Pose. Directed modes never run native initialization."),
        VISUAL_GRAPH_INCOMPLETE("The observed native child graph is incomplete; preflight must wait for native Free initialization or supply a complete declarative visual template."),
        NO_VISUAL_OUTPUT("A verified complete native graph produces no capturable visual output."),
        UNSUPPORTED_RENDER_ORDER("The native draw contains SAT controls or interleaves primitives and tiles that the directed visual template cannot preserve; Free rendering remains native."),
        ART_NOT_CHECKED("The supplied ROM art has not been checked in this source context."),
        ART_BANK_MISSING("A referenced native object art bank is not loaded in this source context."),
        MAPPING_FRAME_MISSING("A referenced mapping frame is outside its ROM art bank."),
        ART_NOT_READY("The decoded art exists but its native renderer is not ready."),
        VISUAL_ONLY("Puppet/Pose suspend native AI and world effects; children follow the declared native visual relationships."),
        ANIMATION_NOT_DECLARED("No pure ROM animation table is declared; use a held or explicitly authored mapping frame."),
        STATE_MACHINE_ANIMATION_HELD("This appearance is owned by a native state machine, not an Animate_Sprite table; its appearance is held or explicitly authored."),
        ROUTINE_CITATION_MISSING("The native arena/appearance restriction requires its owning routine citation."),
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

    /** Child paths are deterministic native ownership paths, never SST slots. */
    public record VisualPart(List<Integer> childPath, String artKey, int mappingFrame) {
        public VisualPart {
            childPath = List.copyOf(childPath); Objects.requireNonNull(artKey);
            if (artKey.isBlank() || mappingFrame < 0 || childPath.stream().anyMatch(i -> i == null || i < 0))
                throw new IllegalArgumentException("Invalid native visual part");
        }
    }

    /**
     * Game-owned declarative metadata or an observed already-initialized Free graph.
     * This object does not authorize construction or native routine execution.
     */
    public record VisualMetadata(Role role, List<VisualPart> parts, boolean completeGraph,
                                 Animation animation, List<String> routineCitations) {
        public static final VisualMetadata NONE = new VisualMetadata(Role.UNRESOLVED, List.of(), false,
                Animation.UNDECLARED, List.of());
        public VisualMetadata {
            Objects.requireNonNull(role); parts = List.copyOf(parts); Objects.requireNonNull(animation);
            routineCitations = List.copyOf(routineCitations);
            if (routineCitations.stream().anyMatch(c -> c == null || c.isBlank()))
                throw new IllegalArgumentException("Blank native routine citation");
        }
    }

    public record Entry(int objectId, String name, String objectTable,
                        ObjectRegistryCatalog.FactoryStatus factoryStatus, String factorySelection,
                        String previewArtKey, Role role, ObjectRegistryCatalog.PositionRule positionRule,
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

    public record AuxiliaryEntry(String key, String name, Role role, Capability free, Capability puppet,
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
            return new AuxiliaryEntry(factory.key(), factory.name(), Role.ANIMAL, free, visual, visual,
                    factory.animalVariants(), factory.routineCitations());
        }).toList();
        return new Catalog(gameId, context, entries, auxiliary);
    }

    /** Convenient ROM-free source browser for either editor. No active world is consulted. */
    public static Catalog inspect(String gameId, int zone, int act) {
        var module = GameModuleRegistry.createStockModule(GameId.fromCode(gameId));
        var zones = module.getZoneRegistry();
        if (zone < 0 || zone >= zones.getZoneCount() || act < 0 || act >= zones.getActCount(zone))
            throw new IllegalArgumentException("Object catalog source context is outside the native zone registry");
        return inspect(module.getGameId().code(), module.createObjectRegistry(), new Context(zone, act, zones.getZoneName(zone)));
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
                entry.previewArtKey(), Role.valueOf(semantics.role().name()), semantics.positionRule(),
                free, visual, visual, List.of(), semantics.heldAppearanceRestrictions().isEmpty()
                        ? Animation.UNDECLARED : Animation.HELD_OR_AUTHORED_FRAME,
                semantics.routineCitations(), exclusions);
    }

    /** Enrich from an existing instance and loaded art only; this never calls update or create. */
    public static Entry observe(Entry entry, ObjectInstance instance, ObjectRenderManager art, VisualMetadata metadata) {
        Objects.requireNonNull(entry); Objects.requireNonNull(metadata);
        Role role = metadata.role() == Role.UNRESOLVED ? entry.role() : metadata.role();
        if (role == Role.UNRESOLVED && instance != null) {
            role = instance.headPresentationRole() == ObjectHeadRole.BOSS ? Role.BOSS
                    : instance instanceof AbstractBadnikInstance || instance.headPresentationRole() == ObjectHeadRole.BADNIK
                        ? Role.BADNIK : Role.OBJECT;
        }
        Capability free = entry.free();
        if (instance instanceof PlaceholderObjectInstance) {
            free = capability(Readiness.UNAVAILABLE, Reason.FACTORY_RETURNED_PLACEHOLDER);
        } else if (instance != null) {
            if (instance.getSpawn() == null || instance.getSpawn().objectId() != entry.objectId())
                throw new IllegalArgumentException("Observed object does not match catalog ID");
            free = capability(Readiness.AVAILABLE, Reason.NATIVE_WORLD_EFFECTS);
            if (entry.positionRule() == ObjectRegistryCatalog.PositionRule.NATIVE_ARENA) {
                free = entry.routineCitations().isEmpty() && metadata.routineCitations().isEmpty()
                        ? capability(Readiness.NEEDS_PREFLIGHT, Reason.NATIVE_WORLD_EFFECTS,
                            Reason.NATIVE_ARENA_POSITION, Reason.ROUTINE_CITATION_MISSING)
                        : capability(Readiness.AVAILABLE, Reason.NATIVE_WORLD_EFFECTS, Reason.NATIVE_ARENA_POSITION);
            }
        }
        var citations = new ArrayList<>(entry.routineCitations());
        for (var citation : metadata.routineCitations()) if (!citations.contains(citation)) citations.add(citation);
        Animation animation = entry.animation() == Animation.HELD_OR_AUTHORED_FRAME
                ? Animation.HELD_OR_AUTHORED_FRAME : metadata.animation();
        Capability visual;
        if (instance instanceof PlaceholderObjectInstance) {
            visual = capability(Readiness.UNAVAILABLE, Reason.FACTORY_RETURNED_PLACEHOLDER);
        } else if (metadata.parts().isEmpty() && !metadata.completeGraph() && instance == null
                && entry.pose().readiness() == Readiness.UNAVAILABLE) {
            visual = entry.pose();
        } else {
            visual = visual(new VisualMetadata(role, metadata.parts(), metadata.completeGraph(), animation, citations), art);
        }
        return new Entry(entry.objectId(), entry.name(), entry.objectTable(), entry.factoryStatus(), entry.factorySelection(),
                entry.previewArtKey(), role, entry.positionRule(), free, visual, visual,
                metadata.parts().stream().map(VisualPart::artKey).distinct().toList(), animation, citations,
                entry.appearanceExclusions());
    }

    private static Capability visual(VisualMetadata metadata, ObjectRenderManager art) {
        if (metadata.parts().isEmpty()) return metadata.completeGraph()
                ? capability(Readiness.UNAVAILABLE, Reason.NO_VISUAL_OUTPUT)
                : capability(Readiness.NEEDS_PREFLIGHT, Reason.VISUAL_METADATA_MISSING);
        if (!metadata.completeGraph()) return capability(Readiness.NEEDS_PREFLIGHT, Reason.VISUAL_GRAPH_INCOMPLETE);
        var reasons = new ArrayList<Reason>();
        Readiness readiness = Readiness.AVAILABLE;
        if (art == null) {
            readiness = Readiness.NEEDS_PREFLIGHT; reasons.add(Reason.ART_NOT_CHECKED);
        } else {
            for (var part : metadata.parts()) {
                var sheet = art.getSheet(part.artKey());
                if (sheet == null) { readiness = Readiness.UNAVAILABLE; add(reasons, Reason.ART_BANK_MISSING); }
                else if (part.mappingFrame() >= sheet.getFrameCount()) {
                    readiness = Readiness.UNAVAILABLE; add(reasons, Reason.MAPPING_FRAME_MISSING);
                } else if (art.getRenderer(part.artKey()) == null || !art.getRenderer(part.artKey()).isReady()) {
                    if (readiness != Readiness.UNAVAILABLE) readiness = Readiness.NEEDS_PREFLIGHT;
                    add(reasons, Reason.ART_NOT_READY);
                }
            }
        }
        reasons.add(Reason.VISUAL_ONLY);
        if (metadata.animation() == Animation.UNDECLARED) reasons.add(Reason.ANIMATION_NOT_DECLARED);
        if (metadata.animation() == Animation.HELD_OR_AUTHORED_FRAME) {
            reasons.add(Reason.STATE_MACHINE_ANIMATION_HELD);
            if (metadata.routineCitations().isEmpty()) {
                if (readiness != Readiness.UNAVAILABLE) readiness = Readiness.NEEDS_PREFLIGHT;
                reasons.add(Reason.ROUTINE_CITATION_MISSING);
            }
        }
        return new Capability(readiness, reasons);
    }

    private static void add(List<Reason> reasons, Reason reason) { if (!reasons.contains(reason)) reasons.add(reason); }
    private static Capability capability(Readiness readiness, Reason... reasons) { return new Capability(readiness, List.of(reasons)); }
}
